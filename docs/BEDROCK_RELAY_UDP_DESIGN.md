# Bedrock Over Relay (UDP-Capable) - Reference Design

## Current Reality
Your current relay path already supports Java over TCP tunnels.

Bedrock over internet requires UDP forwarding for RakNet (default port 19132). Without UDP forwarding, Bedrock clients stay on `Pinging...`.

## Minimum Relay API Contract

### 1) Register
`POST /register`

Request:
```json
{"userId":"<uuid>"}
```

Response:
```json
{"ip":"mine.pocketcraft.online","port":29216}
```

Notes:
- Keep port stable per `userId`.
- The same numeric port can be opened for both TCP and UDP.

### 2) Phone Ready
`POST /phone-ready`

Request:
```json
{"userId":"<uuid>","host":"10.71.225.194","port":25565,"bedrockPort":19132}
```

Response:
```json
{"ok":true}
```

Notes:
- Store readiness by `userId`.
- Include `bedrockPort` if app provides it.

### 3) Unregister
`POST /unregister`

Request:
```json
{"userId":"<uuid>"}
```

Response:
```json
{"ok":true}
```

## Node Reference (Express + net + dgram)

This is a reference skeleton for relay behavior.

```js
const express = require('express');
const net = require('net');
const dgram = require('dgram');

const CONTROL_PORT = 8080;
const PHONE_TUNNEL_PORT = 9000;
const PUBLIC_HOST = process.env.PUBLIC_HOST || 'mine.pocketcraft.online';
const BASE_PUBLIC_PORT = Number(process.env.BASE_PUBLIC_PORT || 29000);
const MAX_PORTS = Number(process.env.MAX_PORTS || 5000);

const app = express();
app.use(express.json());

// userId -> assigned public port
const assignedPortByUser = new Map();
// public port -> userId
const userByAssignedPort = new Map();
// userId -> { host, port, bedrockPort, at }
const readyByUser = new Map();
// userId -> Array<Socket> (persistent phone sockets)
const poolByUser = new Map();

function hashUser(userId) {
  let h = 2166136261;
  for (let i = 0; i < userId.length; i++) h = (h ^ userId.charCodeAt(i)) * 16777619;
  return Math.abs(h >>> 0);
}

function getOrAssignPort(userId) {
  const existing = assignedPortByUser.get(userId);
  if (existing) return existing;

  for (let i = 0; i < MAX_PORTS; i++) {
    const port = BASE_PUBLIC_PORT + ((hashUser(userId) + i) % MAX_PORTS);
    if (!userByAssignedPort.has(port)) {
      assignedPortByUser.set(userId, port);
      userByAssignedPort.set(port, userId);
      return port;
    }
  }
  throw new Error('No free relay ports');
}

function cleanupUser(userId) {
  const arr = poolByUser.get(userId) || [];
  arr.forEach((s) => { try { s.destroy(); } catch (_) {} });
  poolByUser.delete(userId);
  readyByUser.delete(userId);

  const p = assignedPortByUser.get(userId);
  if (p) {
    assignedPortByUser.delete(userId);
    userByAssignedPort.delete(p);
    closeIngress(p);
  }
}

app.post('/register', (req, res) => {
  const { userId } = req.body || {};
  if (!userId) return res.status(400).json({ error: 'missing userId' });

  const port = getOrAssignPort(userId);
  ensureIngress(port);
  res.json({ ip: PUBLIC_HOST, port });
});

app.post('/phone-ready', (req, res) => {
  const { userId, host, port, bedrockPort } = req.body || {};
  if (!userId) return res.status(400).json({ ok: false, error: 'missing userId' });

  readyByUser.set(userId, {
    host: host || null,
    port: Number(port) || 25565,
    bedrockPort: Number(bedrockPort) || 19132,
    at: Date.now()
  });

  res.json({ ok: true });
});

app.post('/unregister', (req, res) => {
  const { userId } = req.body || {};
  if (!userId) return res.status(400).json({ ok: false, error: 'missing userId' });
  cleanupUser(userId);
  res.json({ ok: true });
});

app.listen(CONTROL_PORT, '0.0.0.0', () => {
  console.log(`[control] listening on :${CONTROL_PORT}`);
});

// Phone opens persistent TCP sockets to relay:9000 and sends "userId\n" first.
const phoneTunnelServer = net.createServer((socket) => {
  socket.setNoDelay(true);
  socket.setKeepAlive(true);
  socket.once('data', (firstChunk) => {
    const line = firstChunk.toString('utf8').split('\n')[0].trim();
    const userId = line;
    if (!userId) return socket.destroy();

    const arr = poolByUser.get(userId) || [];
    arr.push(socket);
    poolByUser.set(userId, arr);

    socket.on('close', () => {
      const list = poolByUser.get(userId) || [];
      poolByUser.set(userId, list.filter((s) => s !== socket));
    });
    socket.on('error', () => {});
  });
});
phoneTunnelServer.listen(PHONE_TUNNEL_PORT, '0.0.0.0', () => {
  console.log(`[phone-tunnel] listening on :${PHONE_TUNNEL_PORT}`);
});

const ingress = new Map(); // port -> { tcpServer, udpServer }

function ensureIngress(port) {
  if (ingress.has(port)) return;

  const tcpServer = net.createServer((client) => {
    const userId = userByAssignedPort.get(port);
    const ready = userId ? readyByUser.get(userId) : null;
    const pool = userId ? (poolByUser.get(userId) || []) : [];
    if (!userId || !ready || pool.length === 0) {
      client.destroy();
      return;
    }

    const phoneSocket = pool.shift();
    if (!phoneSocket || phoneSocket.destroyed) {
      client.destroy();
      return;
    }

    // push consumed socket out of pool; phone app replenishes pool automatically
    poolByUser.set(userId, pool);

    client.pipe(phoneSocket).pipe(client);

    const end = () => {
      try { client.destroy(); } catch (_) {}
      try { phoneSocket.destroy(); } catch (_) {}
    };
    client.on('error', end);
    phoneSocket.on('error', end);
    client.on('close', end);
    phoneSocket.on('close', end);
  });

  tcpServer.listen(port, '0.0.0.0');

  // UDP side for Bedrock status + gameplay packets.
  // IMPORTANT: this requires app support for UDP tunneling to phone.
  // If app cannot receive UDP over relay tunnel yet, this server can answer
  // MOTD ping only (best-effort) but full gameplay will not work.
  const udpServer = dgram.createSocket('udp4');
  udpServer.on('message', (msg, rinfo) => {
    const userId = userByAssignedPort.get(port);
    const ready = userId ? readyByUser.get(userId) : null;
    if (!userId || !ready) return;

    // Placeholder behavior:
    // 1) Implement UDP-over-TCP encapsulation to phone tunnel sockets, or
    // 2) Implement full UDP path to phone if network permits (usually NAT blocks it).
    // For now we intentionally do nothing to avoid false-positive behavior.
  });
  udpServer.bind(port, '0.0.0.0');

  ingress.set(port, { tcpServer, udpServer });
}

function closeIngress(port) {
  const x = ingress.get(port);
  if (!x) return;
  try { x.tcpServer.close(); } catch (_) {}
  try { x.udpServer.close(); } catch (_) {}
  ingress.delete(port);
}
```

## Critical Note About Bedrock Over Internet

The code above gives you the relay structure and API contract, but full Bedrock gameplay over internet still needs one of these:

1. UDP-over-TCP encapsulation support in the app tunnel protocol.
2. Native UDP tunnel support end-to-end between relay and phone.
3. Run Geyser on relay/VPS side (not phone side) and route Java traffic from relay to phone.

If none of these is implemented, you may get Java relay success but Bedrock still stuck on pinging.

## Ports/Security Group

Open these inbound ports on your relay host:

- `8080/tcp` control API
- `9000/tcp` phone persistent tunnel
- `29000-33999/tcp` player ingress (Java)
- `29000-33999/udp` player ingress (Bedrock)

## Validation Checklist

1. `curl -X POST http://localhost:8080/register ...` returns host+port.
2. `curl -X POST http://localhost:8080/phone-ready ...` returns `{"ok":true}`.
3. Relay logs show phone sockets joining pool on `:9000`.
4. Java client can connect to returned `host:port`.
5. Bedrock ping/gameplay works only after UDP data path is implemented end-to-end.

## Fast Temporary Mitigation

Until UDP path is complete, show in-app guidance:

- Java over internet: use relay address.
- Bedrock over internet: not available yet.
- Bedrock over LAN: use phone LAN IP + `19132`.
