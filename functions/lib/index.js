"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.forwardFeedbackEmail = void 0;
const app_1 = require("firebase-admin/app");
const firestore_1 = require("firebase-admin/firestore");
const logger = __importStar(require("firebase-functions/logger"));
const params_1 = require("firebase-functions/params");
const firestore_2 = require("firebase-functions/v2/firestore");
(0, app_1.initializeApp)();
const RESEND_API_KEY = (0, params_1.defineSecret)("RESEND_API_KEY");
const EMAIL_FROM = (0, params_1.defineSecret)("EMAIL_FROM");
const SUPPORT_EMAIL = "support@pocketcraft.online";
exports.forwardFeedbackEmail = (0, firestore_2.onDocumentCreated)({
    document: "beta_feedback/{feedbackId}",
    region: "us-central1",
    retry: false,
    secrets: [RESEND_API_KEY, EMAIL_FROM],
    memory: "256MiB",
    timeoutSeconds: 60
}, async (event) => {
    const snap = event.data;
    if (!snap) {
        logger.warn("No document snapshot in feedback event.");
        return;
    }
    const feedbackId = event.params.feedbackId;
    const data = (snap.data() || {});
    const message = (data.message || "").trim();
    if (!message) {
        await snap.ref.set({
            emailStatus: "skipped",
            emailError: "Feedback message is empty.",
            emailCheckedAt: firestore_1.FieldValue.serverTimestamp()
        }, { merge: true });
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
        await snap.ref.set({
            emailStatus: "sent",
            emailProvider: "resend",
            emailProviderMessageId: providerMessageId,
            emailSentAt: firestore_1.FieldValue.serverTimestamp(),
            emailError: firestore_1.FieldValue.delete()
        }, { merge: true });
        logger.info("Feedback email sent.", { feedbackId, providerMessageId });
    }
    catch (error) {
        const message = error instanceof Error ? error.message : "Unknown email forwarding error";
        await snap.ref.set({
            emailStatus: "failed",
            emailError: message,
            emailCheckedAt: firestore_1.FieldValue.serverTimestamp()
        }, { merge: true });
        logger.error("Failed to forward feedback email.", { feedbackId, error: message });
    }
});
function safeParseJson(value) {
    try {
        return JSON.parse(value);
    }
    catch {
        return null;
    }
}
function escapeHtml(value) {
    return value
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
//# sourceMappingURL=index.js.map