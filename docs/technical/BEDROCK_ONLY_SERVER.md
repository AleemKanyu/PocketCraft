# Bedrock-Only Servers (PowerNukkitX)

PocketHost can host a **native Bedrock server** — a server that Bedrock Edition players
(mobile, console, Windows) join directly, with no Java server and no Geyser translation in the
middle. This is a separate server type from the existing Java types; a Bedrock server cannot be
joined by Java players, and vice versa.

The server software is [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX) (LGPL),
downloaded at runtime the same way the Paper/Purpur/Fabric JARs are.

---

## How it differs from Java hosting

| | Java (Paper/Purpur/Fabric/Vanilla) | Bedrock (PowerNukkitX) |
|---|---|---|
| Transport | TCP, port 25565 | RakNet over **UDP**, port 19132 |
| Configuration | `server.properties` | `pnx.yml` only |
| Level format | NBT `level.dat` + region files in `<serverDir>/<level>` | LevelDB in `<serverDir>/worlds/<level>` |
| Console access | RCON (port 25575), falling back to stdin | stdin only — PowerNukkitX has no RCON |
| Plugins | Bukkit/Paper JARs | Nukkit plugins |
| Java runtime | Per Minecraft version (17 / 21 / 25) | Always **Java 21** (PowerNukkitX 3.x is class-file 65) |
| Geyser / Floodgate | Optional, for Bedrock crossplay | Not applicable — hidden in settings |

---

## Configuration model

PowerNukkitX 3.x does not read `server.properties` at all. Rather than fork the app's whole
settings layer, PocketHost keeps `server.properties` as the single source of truth for **every**
world (so the settings screens, relay status publishing and world management stay identical
across server types) and **projects** the managed values into `pnx.yml` immediately before each
launch. That projection lives in `NukkitLaunchManager.prepareNukkitServer`.

`pnx.yml` is edited by `PnxYaml`, a comment-preserving line editor for the file's flat two-level
structure (`section:` followed by two-space indented keys). PowerNukkitX rewrites the file on
boot with a full set of localized explanatory comments; a YAML serializer would strip all of them
and reorder the document on every launch, so only the specific keys PocketHost manages are
rewritten in place.

Two values are set for Android rather than mirrored from the user's settings:

- `network-settings.zlibProvider: 1` — the default `3` is hardware-accelerated libdeflate, whose
  JNI library ships only for `linux/amd64`. There is no ARM native for it in the JAR.
- `misc-settings.enableMetrics: false` — PocketHost declares no third-party analytics, so the
  bundled server must not send bStats telemetry.

### The setup wizard

A first-run PowerNukkitX prints an interactive setup wizard and reads answers from stdin. On a
server whose stdin is a pipe, that can hang forever. Two independent guards prevent it:

1. `pnx.yml` is written **before** the first launch, and its presence skips the wizard entirely.
2. The launch command passes `--skip-setup --accept-license --language eng`.

---

## Launch path

`ServerLauncher` branches on the world's server type (`isBedrockServer`) and skips every
Java-only preparation step: EULA, `bukkit.yml` / `spigot.yml` / `paper-global.yml`, the Paperclip
Java-version patch, `level.dat` validation and NBT maintenance, the legacy `players/` cleanup,
Floodgate key preservation and the Bedrock bridge config. JNA patching is kept — PowerNukkitX
bundles JNA, whose native library needs the same Android ELF fix as Paper's.

Bedrock **forces the out-of-process JVM path**. PowerNukkitX has no RCON, so writing to the
server process's stdin is the only way to reach its console; the in-process JNI JVM inherits the
app's own stdin and would leave the server unable to receive `stop`, kicks, or any command. On a
device whose `filesDir` is mounted `noexec` the launcher still falls back in-process, and on that
path the server runs but console commands do not reach it.

Program arguments:

```
-jar powernukkitx.jar --language eng --skip-setup --accept-license \
  --disable-ansi --disable-auto-bug-report
```

`nogui` is **not** passed — that is a Java-server argument. `--port` is not passed either:
PowerNukkitX accepts the flag but ignores it, binding whatever `pnx.yml` says, so the port is set
there and nowhere else. The same branch exists in `app/src/main/cpp/launcher.c` for the
in-process path.

### Console commands

The server JVM is a child of the `:server` process, so `ServerLauncher.sendCommand` reaches it
only from inside that process — calling it from the UI process is a silent no-op. Java servers do
not notice, because the app's console talks to them over RCON. PowerNukkitX has no RCON, so the
UI sends Bedrock commands to `ServerHostService` as an `ACTION_CONSOLE_COMMAND` intent and the
service writes them to the server's stdin. Output comes back on the normal console log stream.

---

## Readiness and liveness

A Bedrock server binds UDP only, so the TCP `connect()` used for Java servers can never succeed
against one. `BedrockPortProbe` sends RakNet's `UNCONNECTED_PING` to `127.0.0.1:<port>` and waits
for `UNCONNECTED_PONG` — the same handshake a Bedrock client performs when listing a server, so a
pong proves the server is genuinely accepting players rather than merely holding a socket. This
backs the startup port probe, the relay health check and the shutdown wait.

Console readiness is unchanged: PowerNukkitX prints `Done (13.908s)! For help, type "help" or
"?"`, which the existing `ConsoleParser.isDone` already matches once the Nukkit log prefix is
stripped.

Player tracking uses two extra patterns, because PowerNukkitX logs Nukkit's own connection lines
rather than Minecraft's:

```
Steve[/203.0.113.7:51234] logged in with entity id 12 at (world, 0, 64, 0)
Steve[/203.0.113.7:51234] logged out due to Session disconnected
```

Logs are tailed from `logs/server.log` (PowerNukkitX) instead of `logs/latest.log`.

---

## Relay

**No relay-side changes are required for traffic.** The relay already multiplexes Bedrock UDP
frames (opcode `0x02`) over the same pooled phone→relay TCP sockets it uses for Java players, and
`startUserUdpSocket` binds the session's assigned port for UDP as well as TCP. `BedrockUdpBridge`
forwards those frames to `127.0.0.1:19132`, which is exactly where PowerNukkitX listens — the
bridge was written for Geyser and needs no modification.

One relay-side change *was* made: `relay/bedrock-ping.js` answers RakNet server-list pings from a
cached status the phone posts, and its version→protocol table stopped at 1.22.0. Protocol `2169`
(Bedrock 1.26.45, what PowerNukkitX 3.0.4 advertises) and the 1.21.6–1.21.100 range were added.
**This requires redeploying the relay to take effect.** Until then, a Bedrock server may show as
"outdated" in the in-game server list; joining by address still works, because connection packets
are forwarded regardless of the ping response.

---

## Downloading the server JAR

PowerNukkitX ships a single self-contained fat JAR per release (~60 MB, `powernukkitx.jar`) that
serves whatever Bedrock client version that release supports — there is no per-version download.
So the JAR is cached per **PowerNukkitX release** (`servers/binaries/powernukkitx/bedrock-<rel>.jar`)
rather than per Minecraft version; changing the advertised Bedrock version does not re-download
60 MB of identical bytes.

`ServerTypeDownloadUrls` resolves the newest GitHub release, but only accepts it if that release
still publishes an asset named `powernukkitx.jar`; anything else falls back to the pinned release
in `NukkitVersions.PNX_RELEASE`, which is the build PocketHost is tested against.

---

## Creating a Bedrock server

Through the normal flow: **Change Version → Bedrock Edition → pick a version → download the
JAR**. There is no separate Bedrock creation screen; the earlier prototype one was removed
because it wrote the world config without downloading the server JAR, leaving a world that could
not start.

## Known limitations

- Java worlds cannot be converted to Bedrock worlds (different level formats). Switching an
  existing world's type leaves the old Java level files in place, unused.
- World import/export and the world map render Java region files, so they do not apply to a
  Bedrock world's LevelDB level.
- On a `noexec` `filesDir` device the server runs in-process, where it has no stdin pipe, so
  console commands cannot be delivered.
- `resolveStableWorldName`'s Java world discovery is skipped for Bedrock worlds; their
  `level-name` is taken at face value, since a LevelDB level under `worlds/` is invisible to a
  search for `level.dat` or `region/`.
