## Relay Restoration Checklist

Goal: restore the smallest known-good relay path before reintroducing any optimizations.

### What this rollback keeps

- Android live DNS resolution for relay hosts
- Bedrock invalid port/IP guards
- `ERR_SOCKET_BAD_PORT` protection in the Node relay

### What this rollback removes

- RakNet packet rewriting in the Node relay
- rewrite-heavy Bedrock relay behavior that did not exist in the earlier working path

### Files prepared in this workspace

- Node relay rollback target: `relay-index.patched.js`
- Android DNS/fallback fix:
  - `app/src/main/kotlin/com/pocketcraft/server/RelayManager.kt`
  - `app/src/main/kotlin/com/pocketcraft/server/config/RelayServersConfig.kt`

### Deploy order

1. Deploy the rollback relay file to the VPS as `index.js`.
2. Restart the relay process.
3. Rebuild and install the Android app.
4. Stop any existing relay session in the app.
5. Start a fresh relay session.
6. Test in this order:
   - Java relay join
   - Bedrock server-list ping
   - Bedrock actual join

### Expected relay behavior after rollback

- No `Rewrote OpenConnectionReply2 ...` logs
- No `Rewrote ConnectionRequestAccepted ...` logs
- Bedrock frames should be forwarded as-is

### If Bedrock still fails after this rollback

Treat the relay restore as successful only if Java still works and Bedrock no longer regressed due to rewrite logic. If Bedrock still times out after that, debug from packet capture or targeted logging, but do not reintroduce broad RakNet payload rewriting first.
