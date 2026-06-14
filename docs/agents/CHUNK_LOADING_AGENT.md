# Agent Handoff: Bedrock Chunk Loading Fix

## Status
The Bedrock relay connection is now **perfectly stable**. The TCP socket bouncing issue is fixed, and MTU clamping successfully bypasses Jio's CGNAT dropping fragments. The player can connect and stay connected without timing out.

## The New Issue
The user reports that **"chunks don't load properly"**. This means the player loads into the world, but terrain is missing, extremely slow to load, or fails to render entirely.

## The Root Cause
During chunk loading, Geyser attempts to burst megabytes of chunk data to the Bedrock client instantly. 
In the Android app's `RelayManager.kt` and `BedrockUdpBridge.kt`, we use Kotlin `Channel`s to shuttle packets between the UDP bridge and the TCP tunnel.

Currently, these channels have a hardcoded capacity of **512 frames**:
* `RelayManager.kt`: `private val bedrockTxChannel = Channel<ByteArray>(capacity = 512)`
* `BedrockUdpBridge.kt`: `private const val INBOUND_CHANNEL_CAPACITY = 512`

At an MTU of 1300 bytes, 512 frames is only **~650 KB** of buffer. Geyser easily bursts more than 650KB when a player joins or flies. When the channel fills up, `trySend` returns a failure, and the app **silently drops the Bedrock chunk frames**!
RakNet tries to recover from dropped packets, but because the buffer is so small, retransmissions just hit the same capacity limit and get dropped again. Chunk loading stalls indefinitely.

## Your Task
1. **Increase Buffer Capacities**: 
   Modify `RelayManager.kt` and `BedrockUdpBridge.kt` in the Android app (`app/src/main/kotlin/com/pocketcraft/server/...`).
   Change the `bedrockTxChannel` and `inboundFrames` capacities from `512` to `Channel.UNLIMITED` (or a massive number like `8192` if UNLIMITED causes memory issues, but `UNLIMITED` is usually safe for short bursts and allows TCP backpressure to naturally drain it).
2. **Check for Dropped Logs**:
   Look at `RelayManager.kt` where `bedrockTxChannel.trySend(frame)` is called. If you switch to `UNLIMITED`, `trySend` will never fail due to capacity. If you use a bounded size, consider launching a coroutine to use `.send(frame)` instead of `trySend` so it suspends instead of dropping packets (creating natural backpressure all the way to Geyser's UDP socket).
3. **Geyser Configuration**:
   Ensure `chunk-caching` is set appropriately in `Geyser-Spigot/config.yml` if necessary, though the channel drop is the primary suspect.
4. **Build and Install**:
   Run `./build_and_install.sh` and ask the user to test if chunks load as fast as Java now!