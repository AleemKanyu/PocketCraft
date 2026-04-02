import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { defineSecret } from "firebase-functions/params";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

initializeApp();

const RESEND_API_KEY = defineSecret("RESEND_API_KEY");
const EMAIL_FROM = defineSecret("EMAIL_FROM");
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
};

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
      message
    ].join("\n");

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
