'use strict';

// ---------------------------------------------------------------------------
// In-memory TTL cache for subdomain → ownerId + subscription lookups.
//
// WHY THIS EXISTS:
//   resolveSubdomainRoute() makes 2 sequential Firestore network calls on
//   every new player connection. Firestore round-trips from EC2 (Mumbai/EU)
//   add 20-100ms+ each, inflating the first-packet latency the Minecraft
//   client measures as "ping". The data being fetched (subdomain ownership,
//   subscription tier, expiry timestamp) changes very rarely — at most when a
//   user subscribes, cancels, or reassigns their custom IP.
//
// SECURITY:
//   The subscription expiry timestamp is stored in the cache entry and is
//   re-evaluated against Date.now() on every connection, so a subscription
//   that expires mid-cache-window is still rejected correctly. The worst case
//   is that a cancelled subscription is usable for up to ROUTE_CACHE_TTL_MS
//   after cancellation — 60 seconds, acceptable for this use case.
//
//   Failed lookups (unknown subdomain, invalid owner, etc.) are NOT cached so
//   that newly registered subdomains are visible immediately.
// ---------------------------------------------------------------------------

const ROUTE_CACHE_TTL_MS = 60_000; // 60 seconds
const routeCache = new Map(); // subdomain → { result, cachedAt }

// Evict stale entries periodically to prevent unbounded memory growth.
setInterval(() => {
  const cutoff = Date.now() - ROUTE_CACHE_TTL_MS;
  for (const [key, entry] of routeCache) {
    if (entry.cachedAt < cutoff) routeCache.delete(key);
  }
}, 120_000);

function parseRegionalHostname(hostname, baseDomain = 'pocketcraft.online') {
  const normalized = String(hostname || '').trim().toLowerCase().replace(/\.$/, '');
  const suffix = `.${baseDomain}`;
  if (!normalized.endsWith(suffix)) {
    return null;
  }

  const labels = normalized.slice(0, -suffix.length).split('.').filter(Boolean);
  if (labels.length === 1) {
    return { subdomain: labels[0], region: null };
  }
  if (labels.length === 2 && (labels[1] === 'as' || labels[1] === 'eu')) {
    return { subdomain: labels[0], region: labels[1] };
  }
  return null;
}

async function resolveSubdomainRoute({
  hostname,
  firestore,
  baseDomain = 'pocketcraft.online'
}) {
  const parsed = parseRegionalHostname(hostname, baseDomain);
  if (!parsed) {
    return { ok: false, code: 'invalid-hostname', message: 'Hostname is not a supported PocketCraft subdomain.' };
  }

  // Check cache first (keyed on subdomain only — region is validated below).
  const cacheKey = parsed.subdomain;
  const cached = routeCache.get(cacheKey);
  if (cached && (Date.now() - cached.cachedAt) < ROUTE_CACHE_TTL_MS) {
    const r = cached.result;

    // Re-validate region against cached storedRegion.
    if (parsed.region && r.storedRegion && parsed.region !== r.storedRegion) {
      return { ok: false, code: 'region-mismatch', message: `Subdomain ${parsed.subdomain} is registered for region ${r.storedRegion}, not ${parsed.region}.`, parsed };
    }
    if (parsed.region && !r.storedRegion) {
      return { ok: false, code: 'legacy-subdomain-regioned-hostname', message: `Subdomain ${parsed.subdomain} is legacy and cannot be reached through a region-suffixed hostname.`, parsed };
    }

    // Re-check expiry against wall clock even when cache is fresh.
    if (r.expiresAt && Date.now() > r.expiresAt) {
      routeCache.delete(cacheKey); // evict immediately so next check re-fetches
      return { ok: false, code: 'inactive-subscription', message: 'Premium subscription has expired.', parsed };
    }

    return {
      ok: true,
      parsed,
      route: {
        subdomain: parsed.subdomain,
        region: parsed.region,
        serverId: r.serverId,
        ownerId: r.ownerId,
        storedRegion: r.storedRegion,
      }
    };
  }

  // --- Cache miss: hit Firestore ---

  const doc = await firestore.collection('subdomains').doc(parsed.subdomain).get();
  if (!doc.exists) {
    return { ok: false, code: 'unknown-subdomain', message: 'Subdomain does not exist.', parsed };
  }

  const data = doc.data() || {};
  const storedRegion = typeof data.region === 'string' ? data.region : null;

  if (parsed.region && storedRegion && parsed.region !== storedRegion) {
    return {
      ok: false,
      code: 'region-mismatch',
      message: `Subdomain ${parsed.subdomain} is registered for region ${storedRegion}, not ${parsed.region}.`,
      parsed,
      data
    };
  }

  if (parsed.region && !storedRegion) {
    return {
      ok: false,
      code: 'legacy-subdomain-regioned-hostname',
      message: `Subdomain ${parsed.subdomain} is legacy and cannot be reached through a region-suffixed hostname.`,
      parsed,
      data
    };
  }

  const ownerId = typeof data.ownerId === 'string' ? data.ownerId : '';
  if (!ownerId) {
    return {
      ok: false,
      code: 'invalid-owner',
      message: 'Subdomain has no registered owner.',
      parsed,
      data
    };
  }

  const userDoc = await firestore.collection('users').doc(ownerId).get();
  if (!userDoc.exists) {
    return {
      ok: false,
      code: 'inactive-subscription',
      message: 'Subdomain owner does not exist.',
      parsed,
      data
    };
  }

  const userData = userDoc.data() || {};
  const premiumTier = typeof userData.premiumTier === 'string' ? userData.premiumTier.toLowerCase() : 'none';
  if (premiumTier !== 'premium' && premiumTier !== 'supportive') {
    return {
      ok: false,
      code: 'inactive-subscription',
      message: 'Subdomain owner does not have an active premium subscription.',
      parsed,
      data
    };
  }

  const expiryTimestamp = userData.premiumUntil || userData.premiumExpiry || userData.expiresAt;
  let expiresAt = null;
  if (expiryTimestamp && typeof expiryTimestamp.toDate === 'function') {
    expiresAt = expiryTimestamp.toDate().getTime();
    if (Date.now() > expiresAt) {
      return {
        ok: false,
        code: 'inactive-subscription',
        message: 'Premium subscription has expired.',
        parsed,
        data
      };
    }
  }

  // Store resolved entry in cache (only on success).
  routeCache.set(cacheKey, {
    cachedAt: Date.now(),
    result: {
      ownerId,
      storedRegion,
      serverId: typeof data.serverId === 'string' ? data.serverId : '',
      expiresAt,
    }
  });

  return {
    ok: true,
    parsed,
    route: {
      subdomain: parsed.subdomain,
      region: parsed.region,
      serverId: typeof data.serverId === 'string' ? data.serverId : '',
      ownerId: ownerId,
      storedRegion
    }
  };
}

module.exports = {
  parseRegionalHostname,
  resolveSubdomainRoute
};
