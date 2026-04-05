import Navbar from "../components/Navbar";
import Footer from "../components/Footer";

export default function Privacy() {
  return (
    <div className="min-h-screen bg-white text-black selection:bg-[#7FE620] selection:text-black font-sans">
      <Navbar />

      <main className="relative overflow-hidden">
        <div className="absolute top-0 left-0 w-80 h-80 bg-[#7FE620]/15 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute top-1/3 right-0 w-[24rem] h-[24rem] bg-[#1CB0F6]/10 rounded-full blur-3xl pointer-events-none" />

        <section className="max-w-4xl mx-auto px-6 pt-24 pb-16 relative z-10">
          <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight mb-3">Privacy Policy</h1>
          <p className="text-black/60 mb-1">Effective date: April 6, 2026</p>
          <p className="text-black/50 mb-8">Version: 2026-04-06</p>

          <div className="rounded-xl border-2 border-black/10 p-6 bg-white space-y-6 leading-relaxed">
            <p className="text-black/70">PocketCraft ("we", "us", "our") provides a mobile app for hosting and managing a Minecraft server. This policy describes what data we collect, how we use it, and your choices.</p>
            <p className="text-black/70">This draft should be reviewed by qualified counsel before release.</p>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">1. Data We Collect</h2>
              <h3 className="text-lg font-bold mb-2">1.1 Data you provide</h3>
              <ul className="list-disc pl-6 text-black/70 space-y-1 mb-4">
                <li>Feedback text you submit in the app.</li>
                <li>Optional social and community actions, for example opening Discord or Instagram links.</li>
              </ul>

              <h3 className="text-lg font-bold mb-2">1.2 Data collected automatically</h3>
              <ul className="list-disc pl-6 text-black/70 space-y-1 mb-4">
                <li>App and device metadata: app version and version code, Android SDK version, device manufacturer and model.</li>
                <li>Operational identifiers: a generated app user ID stored locally on device and relay session identifier derived from that local ID.</li>
                <li>Server and hosting operational data: relay host selection, relay registration payload fields (for example userId, local host and port), and server telemetry events you trigger in-app (for example start and stop, settings changes).</li>
              </ul>

              <h3 className="text-lg font-bold mb-2">1.3 Firebase and Google SDK data</h3>
              <p className="text-black/70 mb-2">PocketCraft includes Google Firebase and Google Ads SDKs. Depending on your consent settings, these SDKs may process:</p>
              <ul className="list-disc pl-6 text-black/70 space-y-1 mb-4">
                <li>Firebase Analytics: event names and parameters related to app and server usage, plus Firebase identifiers and device or app metadata.</li>
                <li>Firebase Cloud Messaging (FCM): push token and topic subscriptions used to deliver announcements.</li>
                <li>Firebase Remote Config: config fetch requests and app context used to serve remote values.</li>
                <li>Firebase Firestore: user-submitted feedback payloads and metadata.</li>
                <li>Google Mobile Ads (AdMob): ad request metadata and identifiers required to serve ads.</li>
              </ul>

              <h3 className="text-lg font-bold mb-2">1.4 Crash and diagnostics data</h3>
              <ul className="list-disc pl-6 text-black/70 space-y-1">
                <li>PocketCraft includes optional Firebase Crashlytics integration for crash diagnostics.</li>
                <li>When enabled by user consent, crash reports can include stack traces, app state metadata, and technical diagnostics needed to investigate bugs.</li>
                <li>PocketCraft can also process diagnostics through in-app feedback log excerpts (for example latest server log snippets) and error information included in analytics-style events where enabled.</li>
              </ul>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">2. How We Use Data</h2>
              <ul className="list-disc pl-6 text-black/70 space-y-1">
                <li>Provide core app functionality, including hosting workflow, relay registration, and updates.</li>
                <li>Improve reliability and performance through diagnostics, troubleshooting, and product analytics when consented.</li>
                <li>Deliver push announcements and configuration updates.</li>
                <li>Operate advertising when ad consent is enabled.</li>
                <li>Respond to user support and feedback.</li>
              </ul>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">3. Legal Bases (GDPR)</h2>
              <p className="text-black/70 mb-2">Where GDPR applies, we process data under one or more legal bases:</p>
              <ul className="list-disc pl-6 text-black/70 space-y-1">
                <li>Contract and service delivery: data needed to run requested app features.</li>
                <li>Legitimate interests: service security, abuse prevention, and reliability.</li>
                <li>Consent: analytics, ads, and similar optional tracking where required.</li>
                <li>Legal obligation: compliance with applicable law.</li>
              </ul>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">4. Consent and Controls</h2>
              <p className="text-black/70 mb-2">PocketCraft provides settings to control optional processing:</p>
              <ul className="list-disc pl-6 text-black/70 space-y-1">
                <li>Crash diagnostics consent toggle</li>
                <li>Usage analytics consent toggle</li>
                <li>Personalized ads consent toggle</li>
                <li>Push announcements toggle</li>
              </ul>
              <p className="text-black/70 mt-3">You can change these anytime in Settings under Privacy and Legal.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">5. Sharing and Processors</h2>
              <p className="text-black/70 mb-2">We may share or process data with:</p>
              <ul className="list-disc pl-6 text-black/70 space-y-1">
                <li>Google Firebase services (Analytics, Firestore, Messaging, Remote Config)</li>
                <li>Google Mobile Ads and AdMob</li>
                <li>Infrastructure providers needed for app operation and delivery</li>
              </ul>
              <p className="text-black/70 mt-3">We do not sell personal information for money. If your jurisdiction defines sharing broadly, for example cross-context advertising, we provide controls to reduce such processing.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">6. Data Retention</h2>
              <ul className="list-disc pl-6 text-black/70 space-y-1">
                <li>Feedback and operational records are retained only as long as reasonably needed for support, quality, and security.</li>
                <li>Local files and logs on your device remain under your device storage until removed by app logic or user action.</li>
              </ul>
              <p className="text-black/70 mt-3">A final retention schedule should be defined before production launch.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">7. Security</h2>
              <p className="text-black/70">We use reasonable safeguards appropriate to the data and risk, including transport security where available, access controls, and least-privilege practices. No method of transmission or storage is perfectly secure.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">8. International Transfers</h2>
              <p className="text-black/70">Your data may be processed in countries other than your own. Where required, we rely on legally valid transfer mechanisms.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">9. Your Rights</h2>
              <p className="text-black/70 mb-2">Depending on your location, you may have rights to:</p>
              <ul className="list-disc pl-6 text-black/70 space-y-1">
                <li>Access personal data</li>
                <li>Correct inaccurate data</li>
                <li>Delete data</li>
                <li>Restrict or object to processing</li>
                <li>Data portability</li>
                <li>Withdraw consent at any time</li>
              </ul>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">10. Children</h2>
              <p className="text-black/70">PocketCraft is not intended for children under 13 without parental involvement.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">11. Contact</h2>
              <p className="text-black/70">Privacy contact: <a className="underline decoration-[#7FE620] underline-offset-4" href="mailto:privacy@pocketcraft.online">privacy@pocketcraft.online</a></p>
              <p className="text-black/70">Support contact: <a className="underline decoration-[#7FE620] underline-offset-4" href="mailto:support@pocketcraft.online">support@pocketcraft.online</a></p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">12. Changes to This Policy</h2>
              <p className="text-black/70">We may update this policy from time to time. We will update the effective date and version and provide additional notice where required.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">Implementation Notes</h2>
              <ul className="list-disc pl-6 text-black/70 space-y-1">
                <li>In-app legal links are configurable via build constants in app/build.gradle.kts.</li>
                <li>Consent toggles are available in Settings under Privacy and Legal.</li>
                <li>Analytics and ad loading are consent-gated in code.</li>
              </ul>
            </section>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}