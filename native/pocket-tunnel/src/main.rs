//! PocketCraft's playit.gg tunnel agent.
//!
//! Runs as a child process of the Android server service. It links the user's own
//! playit.gg account through a claim link, makes sure a Java (TCP) and a Bedrock (UDP)
//! tunnel point at the local server, then carries the traffic with playit's agent core.
//!
//! Input is environment variables, output is one JSON object per line on stdout:
//!   PT_SECRET        saved agent secret key, empty on first run
//!   PT_JAVA_PORT     local Java port (default 25565)
//!   PT_BEDROCK_PORT  local Bedrock port, 0 to skip (default 19132)
//! The process exits when stdin closes, so it never outlives the app.

use std::io::{Read, Write};
use std::sync::Arc;
use std::time::Duration;

use playit_agent_core::network::origin_lookup::OriginLookup;
use playit_agent_core::network::tcp::tcp_settings::TcpSettings;
use playit_agent_core::network::udp::udp_settings::UdpSettings;
use playit_agent_core::playit_agent::{PlayitAgent, PlayitAgentSettings};
use playit_api_client::PlayitApi;
use playit_api_client::api::{
    AgentRunDataV1, AgentTunnelAttr, AgentTunnelConfig, AgentTunnelV1, ApiError, ApiErrorNoFail,
    ApiResponseError, ApiResult, ClaimAgentType, ClaimSetupResponse, ObjectId, PlayitHttpClient,
    ReqClaimExchange, ReqClaimSetup, ReqTunnelsConfigV1,
};
use rand::Rng;
use serde_json::{Value, json};

const API_BASE: &str = "https://api.playit.gg";
const JAVA_TYPE: &str = "minecraft-java";
const BEDROCK_TYPE: &str = "minecraft-bedrock";
const CLAIM_TIMEOUT: Duration = Duration::from_secs(15 * 60);

fn emit(value: Value) {
    let mut out = std::io::stdout().lock();
    let _ = writeln!(out, "{value}");
    let _ = out.flush();
}

fn fail(code: &str, message: impl Into<String>) -> ! {
    emit(json!({ "event": "error", "code": code, "message": message.into() }));
    std::process::exit(1);
}

fn env_port(name: &str, default: u16) -> u16 {
    std::env::var(name)
        .ok()
        .and_then(|v| v.trim().parse().ok())
        .unwrap_or(default)
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_writer(std::io::stderr)
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_env("PT_LOG").unwrap_or_else(|_| "warn".into()),
        )
        .init();

    // The parent holds our stdin open. When the app dies the pipe closes and we stop.
    std::thread::spawn(|| {
        let mut buffer = [0u8; 64];
        let mut stdin = std::io::stdin();
        loop {
            match stdin.read(&mut buffer) {
                Ok(0) | Err(_) => std::process::exit(0),
                Ok(_) => {}
            }
        }
    });

    let java_port = env_port("PT_JAVA_PORT", 25565);
    let bedrock_port = env_port("PT_BEDROCK_PORT", 19132);

    let secret = match std::env::var("PT_SECRET") {
        Ok(v) if !v.trim().is_empty() => v.trim().to_string(),
        _ => {
            let secret = claim().await;
            emit(json!({ "event": "secret", "key": secret }));
            secret
        }
    };

    let api = PlayitApi::create(API_BASE.to_string(), Some(secret.clone()));
    let lookup = Arc::new(OriginLookup::default());

    let agent = match PlayitAgent::new(
        PlayitAgentSettings {
            api_url: API_BASE.to_string(),
            secret_key: secret,
            tcp_settings: TcpSettings::default(),
            udp_settings: UdpSettings::default(),
        },
        lookup.clone(),
    )
    .await
    {
        Ok(agent) => agent,
        Err(error) => {
            let detail = format!("{error:?}");
            if detail.contains("AgentDisabledOverLimit") {
                fail("agent_over_limit", "This playit.gg account has too many agents. Remove unused ones at https://playit.gg/account/agents");
            }
            fail("agent_setup_failed", detail)
        }
    };

    emit(json!({ "event": "connected" }));

    // After the agent is up: connecting registers its version with playit, and tunnel
    // creation is refused ("AgentVersionTooOld") for an agent that never registered one.
    emit(json!({ "event": "status", "message": "Setting up tunnels" }));
    let mut last_report = ensure_tunnels(&api, &lookup, java_port, bedrock_port).await;

    let refresh = async {
        loop {
            tokio::time::sleep(Duration::from_secs(5)).await;
            match api.v1_agents_rundata().await {
                Ok(data) => {
                    lookup.update_from_run_data(&data).await;
                    let report = tunnel_report(&data);
                    if report != last_report {
                        emit(report.clone());
                        last_report = report;
                    }
                }
                Err(error) => check_auth(&error),
            }
        }
    };

    tokio::select! {
        _ = agent.run() => fail("agent_stopped", "The tunnel connection stopped"),
        _ = refresh => {}
    }
}

/// Shows the user a claim link and waits until they approve it on playit.gg.
async fn claim() -> String {
    let api = PlayitApi::create(API_BASE.to_string(), None);
    let mut bytes = [0u8; 5];
    rand::rng().fill(&mut bytes);
    let code = hex::encode(bytes);
    let url = format!("https://playit.gg/claim/{code}");
    let deadline = tokio::time::Instant::now() + CLAIM_TIMEOUT;
    let mut announced = false;

    loop {
        if tokio::time::Instant::now() > deadline {
            fail("claim_timeout", "The playit.gg link was not approved in time");
        }

        let setup = api
            .claim_setup(ReqClaimSetup {
                code: code.clone(),
                agent_type: ClaimAgentType::SelfManaged,
                version: format!("pocketcraft {}", env!("CARGO_PKG_VERSION")),
            })
            .await;

        match setup {
            Ok(ClaimSetupResponse::UserAccepted) => break,
            Ok(ClaimSetupResponse::UserRejected) => {
                fail("claim_rejected", "The playit.gg link was declined")
            }
            Ok(_) => {
                if !announced {
                    announced = true;
                    emit(json!({ "event": "claim", "url": url }));
                }
            }
            Err(error) => {
                emit(json!({ "event": "status", "message": format!("Waiting for playit.gg: {error:?}") }));
                tokio::time::sleep(Duration::from_secs(2)).await;
            }
        }
        tokio::time::sleep(Duration::from_millis(1000)).await;
    }

    loop {
        match api.claim_exchange(ReqClaimExchange { code: code.clone() }).await {
            Ok(res) => return res.secret_key,
            Err(ApiError::Fail(_)) => {}
            Err(error) => {
                emit(json!({ "event": "status", "message": format!("Finishing playit.gg setup: {error:?}") }));
            }
        }
        if tokio::time::Instant::now() > deadline {
            fail("claim_timeout", "playit.gg did not finish the setup in time");
        }
        tokio::time::sleep(Duration::from_secs(1)).await;
    }
}

fn local_port(tunnel: &AgentTunnelV1) -> Option<u16> {
    tunnel
        .agent_config
        .fields
        .iter()
        .find(|f| f.name == "local_port")
        .and_then(|f| f.value.trim().parse().ok())
}

fn find<'a>(data: &'a AgentRunDataV1, tunnel_type: &str) -> Option<&'a AgentTunnelV1> {
    data.tunnels
        .iter()
        .find(|t| t.tunnel_type.as_deref() == Some(tunnel_type) && t.disabled_reason.is_none())
}

fn tunnel_report(data: &AgentRunDataV1) -> Value {
    json!({
        "event": "tunnels",
        "java": find(data, JAVA_TYPE).map(|t| t.display_address.clone()),
        "bedrock": find(data, BEDROCK_TYPE).map(|t| t.display_address.clone()),
        "account": format!("{:?}", data.permissions.account_status),
        "notices": data.notices.iter().map(|n| n.message.to_string()).collect::<Vec<_>>(),
    })
}

fn origin_config(port: u16) -> AgentTunnelConfig {
    AgentTunnelConfig {
        fields: vec![
            AgentTunnelAttr { name: "local_ip".into(), value: "127.0.0.1".into() },
            AgentTunnelAttr { name: "local_port".into(), value: port.to_string() },
        ],
    }
}

fn check_auth<E: std::fmt::Debug>(error: &ApiErrorNoFail<E>) {
    if let ApiErrorNoFail::ApiError(ApiResponseError::Auth(auth)) = error {
        // The saved key was removed on playit.gg. The app clears it and links again.
        fail("secret_invalid", format!("{auth:?}"));
    }
}

async fn run_data(api: &PlayitApi) -> AgentRunDataV1 {
    let mut attempts = 0;
    loop {
        match api.v1_agents_rundata().await {
            Ok(data) => return data,
            Err(error) => {
                check_auth(&error);
                attempts += 1;
                if attempts >= 10 {
                    fail("api_unreachable", format!("{error:?}"));
                }
                tokio::time::sleep(Duration::from_secs(2)).await;
            }
        }
    }
}

/// Creates a tunnel on playit's free global network, pointed at this agent.
///
/// The request is built by hand: the live API takes `protocol` and `endpoint`, which the
/// request type in the pinned playit client (v1.0.12) does not have yet.
async fn create_tunnel(
    api: &PlayitApi,
    data: &AgentRunDataV1,
    tunnel_type: &str,
    port: u16,
    name: &str,
) -> Result<(), String> {
    let request = json!({
        "name": name,
        "protocol": { "type": "tunnel-type", "details": tunnel_type },
        "origin": { "type": "agent", "data": { "agent_id": data.agent_id, "config": origin_config(port) } },
        "endpoint": { "type": "region", "details": { "region": "global", "port": null } },
        "enabled": true,
    });
    let result: Result<ApiResult<ObjectId, Value>, _> = api
        .get_client()
        .call(std::panic::Location::caller(), "/v1/tunnels/create", request)
        .await;
    match result {
        Ok(ApiResult::Success(_)) => Ok(()),
        Ok(ApiResult::Fail(reason)) => Err(reason.to_string()),
        Ok(ApiResult::Error(error)) => Err(format!("{error:?}")),
        Err(error) => Err(format!("{error:?}")),
    }
}

/// Makes sure both tunnels exist and point at the local server, then reports their addresses.
async fn ensure_tunnels(
    api: &PlayitApi,
    lookup: &OriginLookup,
    java_port: u16,
    bedrock_port: u16,
) -> Value {
    let mut wanted = vec![(JAVA_TYPE, java_port, "PocketCraft Java")];
    if bedrock_port != 0 {
        wanted.push((BEDROCK_TYPE, bedrock_port, "PocketCraft Bedrock"));
    }

    let data = run_data(api).await;
    let mut created = false;

    for (type_name, port, name) in &wanted {
        let pending = data.pending.iter().any(|p| p.tunnel_type.as_deref() == Some(*type_name));
        match find(&data, type_name) {
            Some(tunnel) if local_port(tunnel) == Some(*port) => {}
            Some(tunnel) => {
                let res = api
                    .v1_tunnels_config(ReqTunnelsConfigV1 {
                        tunnel_id: tunnel.id,
                        new_agent_id: None,
                        new_config: Some(origin_config(*port)),
                    })
                    .await;
                if let Err(error) = res {
                    emit(json!({ "event": "warning", "code": "tunnel_config_failed", "tunnel": type_name, "message": format!("{error:?}") }));
                }
                created = true;
            }
            None if pending => created = true,
            None => {
                match create_tunnel(api, &data, type_name, *port, name).await {
                    Ok(()) => created = true,
                    Err(message) => {
                        emit(json!({ "event": "warning", "code": "tunnel_create_failed", "tunnel": type_name, "message": message }));
                    }
                }
            }
        }
    }

    // New tunnels take a moment to get a public address.
    let mut data = data;
    if created {
        for _ in 0..20 {
            tokio::time::sleep(Duration::from_secs(2)).await;
            data = run_data(api).await;
            let ready = wanted.iter().all(|(type_name, port, _)| {
                find(&data, type_name).is_some_and(|t| local_port(t) == Some(*port))
            });
            if ready {
                break;
            }
        }
    }

    if find(&data, JAVA_TYPE).is_none() {
        emit(json!({
            "event": "needs_tunnel",
            "url": "https://playit.gg/account/tunnels",
            "message": "No Minecraft Java tunnel on this playit.gg account yet",
        }));
    }

    lookup.update_from_run_data(&data).await;
    let report = tunnel_report(&data);
    emit(report.clone());
    report
}
