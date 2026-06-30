import { initializeApp } from "firebase-admin/app";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { defineSecret } from "firebase-functions/params";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { google } from "googleapis";

initializeApp();

const RESEND_API_KEY = defineSecret("RESEND_API_KEY");
const EMAIL_FROM = defineSecret("EMAIL_FROM");
const GOOGLE_PLAY_SERVICE_ACCOUNT_JSON = defineSecret("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON");
const GOOGLE_PLAY_PACKAGE_NAME = defineSecret("GOOGLE_PLAY_PACKAGE_NAME");
const SUPPORT_EMAIL = "support@pocketcraft.online";

type FeedbackDoc = {
  message?: string;
  userId?: string;
  appVersion?: string;
  appVersionCode?: number;
  serverVersion?: string;
  deviceManufacturer?: string;
  deviceModel?: string;
  androidSdk?: number;
  currentConsoleLog?: string;
  serverLatestLog?: string;
  crashArtifacts?: string;
  runtimeState?: string;
};

type VerifyPurchaseRequest = {
  purchaseToken?: string;
  productId?: string;
};

type PremiumTier = "none" | "premium" | "supportive";

export const forwardFeedbackEmail = onDocumentCreated(
  {
    document: "beta_feedback/{feedbackId}",
    region: "us-central1",
    retry: false,
    secrets: [RESEND_API_KEY, EMAIL_FROM],
    memory: "256MiB",
    timeoutSeconds: 60
  },
  async (event) => {
    const snap = event.data;
    if (!snap) {
      logger.warn("No document snapshot in feedback event.");
      return;
    }

    const feedbackId = event.params.feedbackId as string;
    const data = (snap.data() || {}) as FeedbackDoc;
    const message = (data.message || "").trim();

    if (!message) {
      await snap.ref.set(
        {
          emailStatus: "skipped",
          emailError: "Feedback message is empty.",
          emailCheckedAt: FieldValue.serverTimestamp()
        },
        { merge: true }
      );
      return;
    }

    const appVersion = data.appVersion || "unknown";
    const appVersionCode = data.appVersionCode ?? "unknown";
    const serverVersion = data.serverVersion || "unknown";
    const userId = data.userId || "unknown";
    const manufacturer = data.deviceManufacturer || "unknown";
    const model = data.deviceModel || "unknown";
    const androidSdk = data.androidSdk ?? "unknown";
    const currentConsoleLog = (data.currentConsoleLog || "").trim();
    const serverLatestLog = (data.serverLatestLog || "").trim();
    const crashArtifacts = (data.crashArtifacts || "").trim();
    const runtimeState = (data.runtimeState || "").trim();

    const subject = `[PocketCraft Beta Feedback] ${appVersion} / ${serverVersion}`;
    const text = [
      "New beta feedback received.",
      "",
      `Feedback ID: ${feedbackId}`,
      `User ID: ${userId}`,
      `App Version: ${appVersion} (${appVersionCode})`,
      `Server Version: ${serverVersion}`,
      `Device: ${manufacturer} ${model}`,
      `Android SDK: ${androidSdk}`,
      "",
      "Message:",
      message,
      "",
      runtimeState ? `Runtime State:\n${runtimeState}` : "",
      currentConsoleLog ? `Current Console Log:\n${currentConsoleLog}` : "",
      serverLatestLog ? `Server latest.log:\n${serverLatestLog}` : "",
      crashArtifacts ? `Crash Artifacts:\n${crashArtifacts}` : ""
    ]
      .filter((section) => section.trim().length > 0)
      .join("\n");

    const html = `
      <h2>PocketCraft Beta Feedback</h2>
      <p><strong>Feedback ID:</strong> ${escapeHtml(feedbackId)}</p>
      <p><strong>User ID:</strong> ${escapeHtml(userId)}</p>
      <p><strong>App Version:</strong> ${escapeHtml(String(appVersion))} (${escapeHtml(String(appVersionCode))})</p>
      <p><strong>Server Version:</strong> ${escapeHtml(String(serverVersion))}</p>
      <p><strong>Device:</strong> ${escapeHtml(manufacturer)} ${escapeHtml(model)}</p>
      <p><strong>Android SDK:</strong> ${escapeHtml(String(androidSdk))}</p>
      <hr />
      <p><strong>Message:</strong></p>
      <pre style="white-space:pre-wrap;font-family:inherit">${escapeHtml(message)}</pre>
      ${runtimeState ? `<hr /><p><strong>Runtime State:</strong></p><pre style="white-space:pre-wrap;font-family:inherit">${escapeHtml(runtimeState)}</pre>` : ""}
      ${currentConsoleLog ? `<hr /><p><strong>Current Console Log:</strong></p><pre style="white-space:pre-wrap;font-family:inherit">${escapeHtml(currentConsoleLog)}</pre>` : ""}
      ${serverLatestLog ? `<hr /><p><strong>Server latest.log:</strong></p><pre style="white-space:pre-wrap;font-family:inherit">${escapeHtml(serverLatestLog)}</pre>` : ""}
      ${crashArtifacts ? `<hr /><p><strong>Crash Artifacts:</strong></p><pre style="white-space:pre-wrap;font-family:inherit">${escapeHtml(crashArtifacts)}</pre>` : ""}
    `;

    try {
      const response = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${RESEND_API_KEY.value()}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          from: EMAIL_FROM.value(),
          to: [SUPPORT_EMAIL],
          subject,
          text,
          html
        })
      });

      const responseBody = await response.text();
      if (!response.ok) {
        throw new Error(`Resend error ${response.status}: ${responseBody}`);
      }

      const parsed = safeParseJson(responseBody);
      const providerMessageId = parsed && typeof parsed.id === "string" ? parsed.id : null;

      await snap.ref.set(
        {
          emailStatus: "sent",
          emailProvider: "resend",
          emailProviderMessageId: providerMessageId,
          emailSentAt: FieldValue.serverTimestamp(),
          emailError: FieldValue.delete()
        },
        { merge: true }
      );

      logger.info("Feedback email sent.", { feedbackId, providerMessageId });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown email forwarding error";
      await snap.ref.set(
        {
          emailStatus: "failed",
          emailError: message,
          emailCheckedAt: FieldValue.serverTimestamp()
        },
        { merge: true }
      );

      logger.error("Failed to forward feedback email.", { feedbackId, error: message });
    }
  }
);

export const verifyPurchase = onCall(
  {
    region: "us-central1",
    timeoutSeconds: 60,
    memory: "256MiB",
    secrets: [GOOGLE_PLAY_SERVICE_ACCOUNT_JSON, GOOGLE_PLAY_PACKAGE_NAME]
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "You must be signed in to verify a purchase.");
    }

    const payload = (request.data || {}) as VerifyPurchaseRequest;
    const purchaseToken = payload.purchaseToken?.trim();
    const productId = payload.productId?.trim();
    if (!purchaseToken || !productId) {
      throw new HttpsError("invalid-argument", "purchaseToken and productId are required.");
    }

    const verification = await fetchSubscriptionState(productId, purchaseToken);
    if (!verification.active) {
      await applyEntitlementForUid(uid, "none", purchaseToken, false, verification);
      throw new HttpsError("failed-precondition", verification.reason || "Subscription is not active.");
    }

    const tier = productIdToTier(productId);
    await applyEntitlementForUid(uid, tier, purchaseToken, tier === "supportive", verification);

    return {
      premiumTier: tier,
      prioritySupport: tier === "supportive",
      expiryTimeMillis: verification.expiryTimeMillis ?? null
    };
  }
);

export const syncPlaySubscriptionRtdn = onMessagePublished(
  {
    topic: "play-rtdn",
    region: "us-central1",
    timeoutSeconds: 60,
    memory: "256MiB",
    secrets: [GOOGLE_PLAY_SERVICE_ACCOUNT_JSON, GOOGLE_PLAY_PACKAGE_NAME]
  },
  async (event) => {
    const rawMessage = event.data.message.json ?? safeParseJson(Buffer.from(event.data.message.data || "", "base64").toString("utf8"));
    if (!rawMessage || typeof rawMessage !== "object") {
      logger.warn("RTDN payload missing or invalid.");
      return;
    }

    const subscriptionNotification = (rawMessage as Record<string, unknown>).subscriptionNotification as Record<string, unknown> | undefined;
    const purchaseToken = typeof subscriptionNotification?.purchaseToken === "string" ? subscriptionNotification.purchaseToken : "";
    const productId = typeof subscriptionNotification?.subscriptionId === "string" ? subscriptionNotification.subscriptionId : "";

    if (!purchaseToken || !productId) {
      logger.warn("RTDN did not include purchaseToken/subscriptionId.", { rawMessage });
      return;
    }

    const users = await getFirestore()
      .collection("users")
      .where("playPurchaseToken", "==", purchaseToken)
      .limit(1)
      .get();

    const userDoc = users.docs[0];
    if (!userDoc) {
      logger.warn("RTDN token not linked to a user.", { productId });
      return;
    }

    const verification = await fetchSubscriptionState(productId, purchaseToken);
    const tier = verification.active ? productIdToTier(productId) : "none";
    await applyEntitlementForUid(userDoc.id, tier, purchaseToken, tier === "supportive", verification);
  }
);

async function fetchSubscriptionState(productId: string, purchaseToken: string) {
  const credentials = JSON.parse(GOOGLE_PLAY_SERVICE_ACCOUNT_JSON.value()) as {
    client_email: string;
    private_key: string;
  };

  const authClient = new google.auth.JWT(
    credentials.client_email,
    undefined,
    credentials.private_key,
    ["https://www.googleapis.com/auth/androidpublisher"]
  );
  await authClient.authorize();
  const androidpublisher = google.androidpublisher({
    version: "v3",
    auth: authClient
  });

  const response = await androidpublisher.purchases.subscriptions.get({
    packageName: GOOGLE_PLAY_PACKAGE_NAME.value(),
    subscriptionId: productId,
    token: purchaseToken
  });

  const expiryTimeMillis = Number(response.data.expiryTimeMillis || 0);
  const now = Date.now();
  const active = expiryTimeMillis > now;
  const reason = active ? "" : "Subscription expired or was revoked.";

  if (response.data.acknowledgementState === 0) {
    logger.info("Acknowledging subscription purchase on server-side...", { productId });
    try {
      await androidpublisher.purchases.subscriptions.acknowledge({
        packageName: GOOGLE_PLAY_PACKAGE_NAME.value(),
        subscriptionId: productId,
        token: purchaseToken
      });
    } catch (err) {
      logger.error("Failed to acknowledge subscription server-side:", err);
    }
  }

  return {
    active,
    expiryTimeMillis,
    raw: response.data,
    reason
  };
}

async function applyEntitlementForUid(
  uid: string,
  tier: PremiumTier,
  purchaseToken: string,
  prioritySupport: boolean,
  verification: {
    expiryTimeMillis?: number;
    raw?: unknown;
    reason?: string;
  }
) {
  const db = getFirestore();
  const ref = db.collection("users").doc(uid);
  const update: Record<string, unknown> = {
    premiumTier: tier,
    playPurchaseToken: purchaseToken,
    prioritySupport,
    updatedAt: FieldValue.serverTimestamp(),
    lastPlayVerification: verification.raw ?? {},
    playEntitlementStatus: tier === "none" ? "inactive" : "active",
    playEntitlementReason: verification.reason || null
  };

  if (tier === "none") {
    update.premiumSince = FieldValue.delete();
  } else if (verification.expiryTimeMillis) {
    update.premiumSince = FieldValue.serverTimestamp();
    update.premiumExpiry = Timestamp.fromMillis(verification.expiryTimeMillis);
  }

  await ref.set(update, { merge: true });
}

function productIdToTier(productId: string): PremiumTier {
  switch (productId) {
    case "pocketcraft_premium_monthly":
      return "premium";
    case "pocketcraft_supportive_monthly":
      return "supportive";
    default:
      throw new HttpsError("invalid-argument", `Unknown subscription product: ${productId}`);
  }
}

function safeParseJson(value: string): Record<string, unknown> | null {
  try {
    return JSON.parse(value) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
