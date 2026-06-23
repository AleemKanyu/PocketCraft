'use strict';

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
  if (expiryTimestamp && typeof expiryTimestamp.toDate === 'function') {
    const expiresAt = expiryTimestamp.toDate().getTime();
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
