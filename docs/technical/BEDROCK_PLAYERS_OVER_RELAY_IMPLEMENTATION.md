# Bedrock Players Over Relay IP - Implementation Guide

Date: 2026-04-08
Owner: PocketCraft
Status: Ready to implement

## Goal

Allow Bedrock players to join using the relay address (public relay host + assigned port), not only local WLAN.

## Important Reality

Bedrock protocol uses UDP (RakNet, typically port 19132).
Your current phone-to-relay path is TCP socket pooling.

Because of that, Java over relay works now, but Bedrock over relay requires one extra data path:

1. UDP forwarding end-to-end, or
2. UDP-over-TCP encapsulation, or
3. Running Geyser on relay/VPS instead of on phone.

## Recommended Architecture (Best Fit For Current App)

Use UDP-over-TCP encapsulation between relay and phone to reuse existing phone tunnel sockets.

High-level flow:

1. Bedrock player sends UDP packet to relay public port.
2. Relay wraps packet into a small frame and pushes it through one phone tunnel TCP socket.
3. Phone app unwraps frame and forwards packet to local Geyser UDP port (19132).
4. Response from Geyser UDP is wrapped back over TCP and emitted by relay as UDP to the Bedrock client.

## Relay-Side Work

## 1) Keep current control API

You already have:

- POST /register
- POST /unregister
- GET /status
- phone-ready compatibility routes

Keep these unchanged for Java path compatibility.

## 2) Add per-user UDP ingress socket on assigned relay port

For each registered user tunnel:

- Bind dgram udp4 server on assigned relay port (same port used for Java TCP ingress).
- Maintain map of recent Bedrock client endpoint for that user:
  - key: client ip:port
  - value: lastSeen timestamp

## 3) Add framing protocol for UDP over phone TCP tunnel

Define a simple frame format over phoneSocket stream:

- 1 byte: frame type
  - 0x02 => bedrock_udp_payload
  - 0x03 => bedrock_udp_response
- 2 bytes: payload length (uint16 BE)
- 4 bytes: client IPv4 as bytes (for response routing)
- 2 bytes: client UDP source port (uint16 BE)
- N bytes: UDP payload

Relay write behavior:

- On UDP packet from Bedrock client, choose an available phone socket for user.
- Write one framed packet to phone socket.

Relay read behavior:

- Parse framed responses from phone sockets.
- Reconstruct destination client endpoint and send UDP packet back via relay UDP socket.

## 4) Keep NAT mappings alive

- Store client mapping TTL (for example 60 seconds).
- Refresh on every packet from same client.
- Cleanup stale mappings every 30 seconds.

## 5) Hardening rules

- If no phone socket available: drop packet and log sampled warning.
- If frame parse fails: close that phone socket and remove from pool.
- Set max frame size (for example 1500 bytes payload).
- Enable noDelay and keepAlive on phone sockets.

## Phone/App Work (Required)

Your app currently bridges TCP relay traffic to local Java server port.
For Bedrock relay support, add a UDP bridge component in app code.

## 1) Start a local UDP socket bridge when tunnel starts

- Open DatagramSocket on app side for local relay bridge worker.
- Forward incoming framed UDP payloads to 127.0.0.1:19132 (Geyser).
- Receive responses from 127.0.0.1:19132.
- Wrap as framed response and send back through the same phone tunnel socket.

## 2) Bedrock local target

Ensure Geyser listens on:

- local host: 0.0.0.0 or 127.0.0.1 (inside phone context)
- port: 19132

If plugin config differs, use configured port in framing metadata.

## 3) RelayManager integration

Add a frame parser/writer in relay socket lifecycle:

- Existing path (Java TCP) remains unchanged.
- New frame path for Bedrock UDP packets uses a distinct frame type.

## 4) Backward compatibility

If relay does not support UDP frames:

- keep Java relay available
- surface UI message: Bedrock online relay unavailable; LAN still works

## Infra / Network Work

## 1) Open firewall/security-group ports

Required on relay host:

- 8080/tcp (control API)
- 9000/tcp (phone tunnel)
- assigned player port range tcp (Java ingress)
- assigned player port range udp (Bedrock ingress)

Example if pool is 25500-35500:

- 25500-35500/tcp
- 25500-35500/udp

## 2) DNS

Relay hostname must resolve to public IP used by players.

## 3) Capacity limits

- Set max registered tunnels.
- Set max pending players per tunnel.
- Set max packets per second per user/client for anti-abuse.

## Testing Plan

## Stage A - Control and registration

1. Register user and confirm assigned port.
2. Verify phone-ready returns 200.
3. Verify status endpoint shows active tunnel and socket counts.

## Stage B - Java regression

1. Join with Java via relay IP.
2. Confirm no regression in chunk loading/latency.

## Stage C - Bedrock relay validation

1. Join from Bedrock client using relay host + assigned port.
2. Confirm server list ping completes.
3. Confirm world join and movement for 10+ minutes.
4. Confirm reconnect after temporary mobile data drop.

## Stage D - Soak test

1. 30-60 minute mixed Java + Bedrock session.
2. Track disconnect rate, retransmit spikes, and memory growth.

## Observability To Add

Log these counters per user tunnel:

- udpPacketsIn
- udpPacketsOut
- udpFramesToPhone
- udpFramesFromPhone
- droppedNoPhoneSocket
- frameParseErrors
- staleClientMappingsCleaned

Expose counters in GET /status for quick diagnostics.

## Rollout Strategy

1. Deploy relay changes behind feature flag: bedrockRelayEnabled false by default.
2. Enable for one test userId first.
3. Enable for all users after successful soak.
4. Keep LAN Bedrock fallback visible in UI until rollout stable.

## Common Failure Modes

1. Symptom: Bedrock stuck on Pinging.
Cause: UDP ingress port range blocked in cloud firewall.
Fix: open UDP range and confirm with ss/netstat plus external scan.

2. Symptom: Joins then drops in 5-20 seconds.
Cause: missing frame parser robustness or stale client endpoint mapping.
Fix: validate frame bounds and refresh client mapping on each packet.

3. Symptom: Java works, Bedrock never sees server.
Cause: app-side UDP bridge not implemented yet.
Fix: implement phone framing and local UDP forwarder to Geyser 19132.

## Minimal Acceptance Criteria

- Bedrock player can join via relay host and assigned port.
- Session remains connected for at least 10 minutes.
- Java relay path remains stable.
- No control API 404 for phone-ready routes.
