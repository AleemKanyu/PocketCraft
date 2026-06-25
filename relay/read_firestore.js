const pm2 = require('pm2');
const admin = require('firebase-admin');

pm2.connect((err) => {
  if (err) {
    console.error('Error connecting to PM2:', err);
    process.exit(2);
  }

  pm2.describe(1, async (err, list) => {
    pm2.disconnect();
    if (err || !list || list.length === 0) {
      console.error('Error getting process list from PM2:', err);
      process.exit(1);
    }

    const env = list[0].pm2_env;
    const serviceAccountRaw = env.FIREBASE_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountRaw) {
      console.error('FIREBASE_SERVICE_ACCOUNT_JSON not found in PM2 environment!');
      process.exit(1);
    }

    admin.initializeApp({
      credential: admin.credential.cert(JSON.parse(serviceAccountRaw))
    });

    const db = admin.firestore();

    try {
      console.log('Fetching subdomains...');
      const snap = await db.collection('subdomains').get();
      snap.forEach(doc => {
        console.log(doc.id, '=>', doc.data());
      });

      console.log('\nFetching active premium users...');
      const usersSnap = await db.collection('users').get();
      usersSnap.forEach(doc => {
        const data = doc.data();
        if (data.premiumTier && data.premiumTier !== 'none') {
          console.log(doc.id, '=>', {
            premiumTier: data.premiumTier,
            premiumExpiry: data.premiumExpiry?.toDate?.() || data.premiumExpiry,
            customSubdomain: data.customSubdomain
          });
        }
      });
    } catch (e) {
      console.error('Firestore query failed:', e);
    }
  });
});
