import Navbar from "../components/Navbar";
import Footer from "../components/Footer";

export default function Terms() {
  return (
    <div className="min-h-screen bg-white text-black selection:bg-[#7FE620] selection:text-black font-sans">
      <Navbar />

      <main className="relative overflow-hidden">
        <div className="absolute top-0 right-0 w-80 h-80 bg-[#FFD900]/20 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute top-1/3 left-0 w-[24rem] h-[24rem] bg-[#7FE620]/10 rounded-full blur-3xl pointer-events-none" />

        <section className="max-w-4xl mx-auto px-6 pt-24 pb-16 relative z-10">
          <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight mb-3">Terms of Service</h1>
          <p className="text-black/60 mb-1">Last updated: April 6, 2026</p>
          <p className="text-black/50 mb-8">Policy version: 2026-04-06</p>

          <div className="rounded-xl border-2 border-black/10 p-6 bg-white space-y-6 leading-relaxed">
            <p className="text-black/70">By downloading or using PocketCraft ("the app"), you agree to these Terms of Service. If you do not agree, do not use the app.</p>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">1. What PocketCraft Does</h2>
              <p className="text-black/70">PocketCraft helps you host and manage a Minecraft server from a mobile device and related services.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">2. Acceptable Use</h2>
              <p className="text-black/70 mb-2">You agree not to use PocketCraft to:</p>
              <ul className="list-disc pl-6 text-black/70 space-y-1">
                <li>Distribute illegal content</li>
                <li>Abuse service infrastructure</li>
                <li>Attempt unauthorized access to systems or servers</li>
                <li>Violate applicable law</li>
                <li>Harass, abuse, or harm others</li>
              </ul>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">3. Third-Party Platforms</h2>
              <p className="text-black/70">PocketCraft may integrate with third-party services. Those services are subject to their own terms and policies.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">4. Availability</h2>
              <p className="text-black/70">We do not guarantee uninterrupted availability, uptime, or latency. Service features can change over time.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">5. Disclaimer of Warranties</h2>
              <p className="text-black/70">PocketCraft is provided on an "as is" and "as available" basis, without warranties of any kind to the extent permitted by law.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">6. Limitation of Liability</h2>
              <p className="text-black/70">To the maximum extent permitted by law, PocketCraft and its developer are not liable for indirect, incidental, or consequential damages arising from use of the app.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">7. Suspension and Termination</h2>
              <p className="text-black/70">We may suspend or limit service access where misuse, abuse, or legal risk is detected.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">8. Changes to Terms</h2>
              <p className="text-black/70">We may update these terms over time. Continued use after updates means you accept the revised terms.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">9. Children</h2>
              <p className="text-black/70">PocketCraft is not intended for children under 13 without parental involvement.</p>
            </section>

            <section>
              <h2 className="text-2xl font-extrabold mb-2">10. Contact</h2>
              <p className="text-black/70">For privacy requests: <a className="underline decoration-[#7FE620] underline-offset-4" href="mailto:contact@pocketcraft.online">contact@pocketcraft.online</a></p>
              <p className="text-black/70">For support: <a className="underline decoration-[#7FE620] underline-offset-4" href="mailto:support@pocketcraft.online">support@pocketcraft.online</a></p>
            </section>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}