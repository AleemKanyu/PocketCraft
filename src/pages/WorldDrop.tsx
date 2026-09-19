import React, { useState } from "react";
import confetti from "canvas-confetti";
import { 
  Shield, 
  Download, 
  CheckCircle2, 
  Copy, 
  Check, 
  Lock, 
  ChevronDown, 
  Sparkles, 
  Layers, 
  Clock, 
  Compass,
  ArrowRight
} from "lucide-react";
import Footer from "../components/Footer";
import { useTheme } from "../lib/ThemeContext";

const WORLD_PRICE_INR = 199;
const WORLD_PRICE_PAISE = WORLD_PRICE_INR * 100;

interface PaymentResult {
  order_id: string;
  payment_id: string;
  downloadUrl: string;
}

export default function WorldDrop() {
  const { theme } = useTheme();
  const [buyerName, setBuyerName] = useState("");
  const [buyerEmail, setBuyerEmail] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [paymentSuccess, setPaymentSuccess] = useState<PaymentResult | null>(null);
  const [activeTab, setActiveTab] = useState(0);
  const [openFaq, setOpenFaq] = useState<number | null>(null);
  const [copied, setCopied] = useState(false);

  const ensureRazorpayLoaded = (): Promise<boolean> => {
    return new Promise((resolve) => {
      if ((window as any).Razorpay) {
        resolve(true);
        return;
      }
      const script = document.createElement("script");
      script.src = "https://checkout.razorpay.com/v1/checkout.js";
      script.async = true;
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });
  };

  const handleBuyNow = async () => {
    setErrorMessage(null);
    setStatusMessage(null);

    if (!buyerEmail || !buyerEmail.includes("@")) {
      setErrorMessage("Please enter a valid email address to receive your world receipt.");
      return;
    }

    setIsLoading(true);
    setStatusMessage("Connecting to secure Razorpay gateway...");

    try {
      const isLoaded = await ensureRazorpayLoaded();
      if (!isLoaded) {
        throw new Error("Could not load Razorpay checkout script. Please check your internet connection.");
      }

      const response = await fetch("/api/create-order", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          amount: WORLD_PRICE_PAISE,
          currency: "INR",
          receipt: `mc_${Date.now()}`
        })
      });

      const orderData = await response.json();

      if (!response.ok || !orderData.order_id) {
        throw new Error(orderData.error || "Failed to initialize order with payment gateway.");
      }

      const keyId = orderData.key_id || (import.meta as any).env?.VITE_RAZORPAY_KEY_ID || "";
      if (!keyId) {
        throw new Error("Razorpay gateway key not configured.");
      }

      setStatusMessage("Opening payment modal...");

      const options = {
        key: keyId,
        amount: orderData.amount,
        currency: orderData.currency || "INR",
        name: "PocketHost World Drop",
        description: "Ancient Citadel Minecraft Survival World Map (.zip)",
        image: "/app-icon-circle-hd.png",
        order_id: orderData.order_id,
        prefill: {
          name: buyerName || "Minecraft Player",
          email: buyerEmail
        },
        theme: {
          color: "#7FE620"
        },
        modal: {
          ondismiss: () => {
            setIsLoading(false);
            setStatusMessage(null);
            setErrorMessage("Checkout dismissed. You can complete your purchase whenever you are ready.");
          }
        },
        handler: async (paymentResponse: {
          razorpay_payment_id: string;
          razorpay_order_id: string;
          razorpay_signature: string;
        }) => {
          setStatusMessage("Verifying payment security signature...");
          setIsLoading(true);

          try {
            const verifyRes = await fetch("/api/verify-payment", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                razorpay_order_id: paymentResponse.razorpay_order_id,
                razorpay_payment_id: paymentResponse.razorpay_payment_id,
                razorpay_signature: paymentResponse.razorpay_signature
              })
            });

            const verifyData = await verifyRes.json();

            if (!verifyRes.ok || !verifyData.success) {
              throw new Error(verifyData.error || "Payment signature mismatch. Verification failed.");
            }

            confetti({
              particleCount: 120,
              spread: 80,
              origin: { y: 0.6 },
              colors: ["#7FE620", "#8FEF35", "#FFD900", "#1CB0F6"]
            });

            setPaymentSuccess({
              order_id: paymentResponse.razorpay_order_id,
              payment_id: paymentResponse.razorpay_payment_id,
              downloadUrl: verifyData.downloadUrl || "/downloads/minecraft-world.zip"
            });
            setIsLoading(false);
            setStatusMessage(null);

            // Auto-trigger download
            const link = document.createElement("a");
            link.href = verifyData.downloadUrl || "/downloads/minecraft-world.zip";
            link.download = "minecraft-world.zip";
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
          } catch (verifyErr: any) {
            setIsLoading(false);
            setStatusMessage(null);
            setErrorMessage(verifyErr.message || "Payment verification failed.");
          }
        }
      };

      const rzp = new (window as any).Razorpay(options);

      rzp.on("payment.failed", (failResponse: any) => {
        setIsLoading(false);
        setStatusMessage(null);
        setErrorMessage(`Payment declined: ${failResponse.error?.description || "Transaction failed."}`);
      });

      rzp.open();
    } catch (err: any) {
      setIsLoading(false);
      setStatusMessage(null);
      setErrorMessage(err.message || "An unexpected error occurred.");
    }
  };

  const handleCopyLink = () => {
    const fullUrl = `${window.location.origin}/world`;
    navigator.clipboard.writeText(fullUrl).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    });
  };

  const galleryItems = [
    {
      title: "Ancient Citadel & Royal Keep",
      badge: "MAIN STRONGHOLD",
      desc: "Massive medieval fortress featuring a grand throne room, high watchtowers, underground vault, and automated defense gate.",
      gradient: "from-[#112211] to-[#0a110a]"
    },
    {
      title: "Automated Industrial Farm Complex",
      badge: "100% AFK FARMS",
      desc: "Fully functional economy: Quad iron golem farm, maxed villager trading market, and 30-level mob XP drop tower.",
      gradient: "from-[#131e2b] to-[#070e17]"
    },
    {
      title: "Packed Ice Nether Highway Network",
      badge: "FAST TRAVEL",
      desc: "Packed-ice highway grid connecting over 5,000 blocks in minutes, blaze rod farm, and a direct safe highway to the End Portal.",
      gradient: "from-[#291414] to-[#120707]"
    },
    {
      title: "Coordinates & Lore Guidebook",
      badge: "EXPLORER GUIDE",
      desc: "Every base, ancient city, buried treasure, and fortress is cataloged with exact X, Y, Z coordinates inside the download package.",
      gradient: "from-[#141f17] to-[#080d09]"
    }
  ];

  const faqs = [
    {
      q: "How do I download and install this world?",
      a: "Immediately after checkout, your download begins as a .zip file. For Java Edition: place the extracted folder in .minecraft/saves. For Bedrock Edition: copy the folder to com.mojang/minecraftWorlds. Complete step-by-step installation guides are included inside the download!"
    },
    {
      q: "Are achievements and advancements enabled?",
      a: "Yes! The world was built 100% in pure survival with cheats turned OFF. All trophies, Xbox achievements, and Java advancements are fully unlockable."
    },
    {
      q: "Can I host this on PocketHost or play with friends?",
      a: "Yes! You can load this world directly into PocketHost on your phone to host a 24/7 multiplayer server, upload it to PaperMC, or import it to Minecraft Realms."
    },
    {
      q: "Is the payment gateway secure?",
      a: "Payments are processed securely via Razorpay Standard Checkout using industry-standard 256-bit encryption. We never see or store your payment details."
    }
  ];

  const isDark = theme === "dark";

  return (
    <div className={`min-h-screen bg-[#0a0a0a] text-white`}>
      {/* Standalone Brand Header (NO navigation links to this secret page anywhere on site) */}
      <header className="border-b border-white/10 px-6 py-4 flex items-center justify-between max-w-7xl mx-auto">
        <div className="flex items-center gap-3">
          <img src="/app-icon-circle-hd.png" alt="PocketHost" className="w-8 h-8 rounded-full" />
          <span className="font-bold text-lg tracking-tight font-sans">PocketHost</span>
          <span className="text-xs px-2.5 py-0.5 rounded-full font-mono font-medium bg-[#7FE620]/10 text-[#7FE620] border border-[#7FE620]/30">
            Exclusive World Drop
          </span>
        </div>
        <div className="flex items-center gap-2 text-xs font-mono text-white/60">
          <span className="w-2 h-2 rounded-full bg-[#7FE620] animate-pulse"></span>
          <span>Verified World</span>
        </div>
      </header>

      {/* Hero Section */}
      <section className="pt-16 pb-12 px-6 max-w-5xl mx-auto text-center">
        <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-mono uppercase tracking-wider font-semibold bg-[#7FE620]/10 text-[#7FE620] border border-[#7FE620]/30 shadow-[0_0_20px_rgba(127,230,32,0.15)] mb-6">
          <Sparkles className="w-3.5 h-3.5 text-[#7FE620]" />
          Instant Download &bull; 100% Survival Built
        </div>

        <h1 className="text-4xl md:text-6xl font-extrabold tracking-tight mb-6 text-white">
          Ancient Citadel Survival World
        </h1>

        <p className="text-base md:text-xl text-neutral-400 max-w-2xl mx-auto leading-relaxed mb-8">
          The ultimate survival-crafted Minecraft kingdom built for Java 1.20+ and Bedrock Edition. Features mega builds, industrial automated farms, and complete coordinates documentation.
        </p>

        {/* Feature Tags */}
        <div className="flex flex-wrap items-center justify-center gap-3">
          <span className="px-4 py-2 rounded-xl text-xs md:text-sm font-medium bg-white/5 border border-white/10 flex items-center gap-2">
            <Layers className="w-4 h-4 text-[#7FE620]" /> Java 1.20+ &amp; Bedrock Crossplay
          </span>
          <span className="px-4 py-2 rounded-xl text-xs md:text-sm font-medium bg-white/5 border border-white/10 flex items-center gap-2">
            <Shield className="w-4 h-4 text-[#7FE620]" /> 100% Legit Survival (Cheats Off)
          </span>
          <span className="px-4 py-2 rounded-xl text-xs md:text-sm font-medium bg-white/5 border border-white/10 flex items-center gap-2">
            <Clock className="w-4 h-4 text-[#7FE620]" /> 500+ In-Game Days Built
          </span>
          <span className="px-4 py-2 rounded-xl text-xs md:text-sm font-medium bg-white/5 border border-white/10 flex items-center gap-2">
            <Download className="w-4 h-4 text-[#7FE620]" /> Instant Direct Download
          </span>
        </div>
      </section>

      {/* Main Two-Column Content */}
      <section className="px-6 py-8 max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Column: Showcase & Info */}
        <div className="lg:col-span-7 bg-[#111]/80 backdrop-blur-xl border border-white/10 rounded-2xl p-6 md:p-8 shadow-2xl">
          <h2 className="text-sm font-bold uppercase tracking-wider text-[#7FE620] flex items-center gap-2 mb-6">
            <Compass className="w-4 h-4" /> World Showcase
          </h2>

          {/* Interactive Feature Display */}
          <div className="rounded-xl overflow-hidden border border-white/10 mb-6 bg-black/60">
            <div className={`h-56 p-6 flex flex-col justify-end bg-gradient-to-b ${galleryItems[activeTab].gradient} border-b border-white/10 relative`}>
              <span className="absolute top-4 right-4 text-[11px] font-mono font-semibold px-2.5 py-1 rounded-md bg-black/70 border border-[#7FE620]/30 text-[#7FE620]">
                {galleryItems[activeTab].badge}
              </span>
              <h3 className="text-xl font-bold text-white mb-2">
                {galleryItems[activeTab].title}
              </h3>
              <p className="text-xs md:text-sm text-neutral-300 line-clamp-2">
                {galleryItems[activeTab].desc}
              </p>
            </div>

            <div className="grid grid-cols-4 gap-1 p-2 bg-black/40">
              {galleryItems.map((item, idx) => (
                <button
                  key={idx}
                  onClick={() => setActiveTab(idx)}
                  className={`py-2 px-1 text-xs font-semibold rounded-lg transition-all ${
                    activeTab === idx 
                      ? "bg-[#7FE620]/15 text-[#7FE620] border border-[#7FE620]/30" 
                      : "text-neutral-400 hover:text-white hover:bg-white/5"
                  }`}
                >
                  {["Citadel", "Farms", "Nether", "Guide"][idx]}
                </button>
              ))}
            </div>
          </div>

          {/* Feature Highlights */}
          <div className="space-y-3 mb-8">
            <div className="p-4 rounded-xl bg-white/[0.03] border border-white/5 flex gap-4">
              <div className="w-10 h-10 rounded-lg bg-[#7FE620]/10 border border-[#7FE620]/20 flex items-center justify-center shrink-0">
                🏰
              </div>
              <div>
                <h4 className="text-sm font-bold text-white mb-1">Medieval Mega Citadel</h4>
                <p className="text-xs text-neutral-400 leading-relaxed">
                  Fully furnished royal castle with great hall, underground bunker, treasure vault, and functional redstone defenses.
                </p>
              </div>
            </div>

            <div className="p-4 rounded-xl bg-white/[0.03] border border-white/5 flex gap-4">
              <div className="w-10 h-10 rounded-lg bg-[#7FE620]/10 border border-[#7FE620]/20 flex items-center justify-center shrink-0">
                💎
              </div>
              <div>
                <h4 className="text-sm font-bold text-white mb-1">Maxed Villager Trading Hall</h4>
                <p className="text-xs text-neutral-400 leading-relaxed">
                  Cured villagers offering 1-emerald Mending, Unbreaking III, Protection IV books and full Diamond armor sets.
                </p>
              </div>
            </div>

            <div className="p-4 rounded-xl bg-white/[0.03] border border-white/5 flex gap-4">
              <div className="w-10 h-10 rounded-lg bg-[#7FE620]/10 border border-[#7FE620]/20 flex items-center justify-center shrink-0">
                🚀
              </div>
              <div>
                <h4 className="text-sm font-bold text-white mb-1">Zero-Lag Automated Storage</h4>
                <p className="text-xs text-neutral-400 leading-relaxed">
                  Multi-item sorting system handling over 100 chest categories with bulk overflow protection.
                </p>
              </div>
            </div>
          </div>

          {/* Specifications Table */}
          <table className="w-full text-xs border-collapse">
            <tbody>
              <tr className="border-b border-white/5">
                <td className="py-3 text-neutral-400 w-1/3">Edition Compatibility</td>
                <td className="py-3 font-mono font-medium text-white">Java 1.20 - 1.21+ &amp; Bedrock</td>
              </tr>
              <tr className="border-b border-white/5">
                <td className="py-3 text-neutral-400">Game Mode</td>
                <td className="py-3 font-mono font-medium text-white">Survival (Normal Difficulty, Cheats Disabled)</td>
              </tr>
              <tr className="border-b border-white/5">
                <td className="py-3 text-neutral-400">Spawn Base Coordinates</td>
                <td className="py-3 font-mono font-medium text-white">X: 142, Y: 72, Z: -230</td>
              </tr>
              <tr>
                <td className="py-3 text-neutral-400">Package Contents</td>
                <td className="py-3 font-mono font-medium text-white">.zip World Save + Guidebook (.txt)</td>
              </tr>
            </tbody>
          </table>
        </div>

        {/* Right Column: Checkout Card */}
        <div className="lg:col-span-5 bg-[#111]/90 backdrop-blur-xl border border-[#7FE620]/30 rounded-2xl p-6 md:p-8 shadow-[0_0_40px_rgba(127,230,32,0.1)] sticky top-6">
          <h2 className="text-sm font-bold uppercase tracking-wider text-[#7FE620] flex items-center gap-2 mb-4">
            <Sparkles className="w-4 h-4 text-[#7FE620]" /> Get The World
          </h2>

          {/* Pricing Box */}
          <div className="bg-black/50 border border-white/10 rounded-xl p-5 text-center mb-6">
            <div className="flex items-center justify-center gap-3 mb-1">
              <span className="text-base text-neutral-400 line-through">₹499</span>
              <span className="text-4xl font-extrabold text-[#7FE620] tracking-tight">₹{WORLD_PRICE_INR}</span>
              <span className="text-[11px] font-bold px-2 py-0.5 rounded bg-[#7FE620]/20 text-[#7FE620]">60% OFF</span>
            </div>
            <p className="text-xs text-neutral-400">One-time payment &bull; Lifetime access &bull; Free updates</p>
          </div>

          {/* Alerts */}
          {errorMessage && (
            <div className="p-3.5 mb-4 rounded-xl text-xs bg-red-500/10 border border-red-500/30 text-red-300 flex items-start gap-2">
              <span>⚠️</span>
              <span>{errorMessage}</span>
            </div>
          )}

          {statusMessage && (
            <div className="p-3.5 mb-4 rounded-xl text-xs bg-[#7FE620]/10 border border-[#7FE620]/30 text-[#a7f3d0] flex items-start gap-2">
              <span className="animate-spin">⏳</span>
              <span>{statusMessage}</span>
            </div>
          )}

          {/* Checkout Form */}
          <div className="space-y-4 mb-6">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-300 mb-2">
                Your Email Address (For Receipt &amp; Download)
              </label>
              <input
                type="email"
                placeholder="alex@gmail.com"
                value={buyerEmail}
                onChange={(e) => setBuyerEmail(e.target.value)}
                disabled={isLoading}
                className="w-full bg-black/60 border border-white/10 focus:border-[#7FE620] focus:ring-1 focus:ring-[#7FE620] rounded-xl px-4 py-3 text-sm text-white placeholder-neutral-500 outline-none transition-all"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-300 mb-2">
                GamerTag / Name (Optional)
              </label>
              <input
                type="text"
                placeholder="e.g. Alex"
                value={buyerName}
                onChange={(e) => setBuyerName(e.target.value)}
                disabled={isLoading}
                className="w-full bg-black/60 border border-white/10 focus:border-[#7FE620] focus:ring-1 focus:ring-[#7FE620] rounded-xl px-4 py-3 text-sm text-white placeholder-neutral-500 outline-none transition-all"
              />
            </div>
          </div>

          {/* Razorpay Standard Checkout Button */}
          <button
            onClick={handleBuyNow}
            disabled={isLoading}
            className="w-full py-4 rounded-xl font-bold text-sm md:text-base text-black bg-[#7FE620] hover:bg-[#8FEF35] active:scale-[0.98] transition-all shadow-[0_0_25px_rgba(127,230,32,0.35)] flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isLoading ? (
              <span>Processing...</span>
            ) : (
              <>
                <span>Unlock &amp; Download World</span>
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>

          {/* Trust badges */}
          <div className="mt-6 pt-5 border-t border-white/10 space-y-2.5 text-xs text-neutral-400">
            <div className="flex items-center gap-2.5">
              <Lock className="w-4 h-4 text-[#7FE620]" />
              <span>Processed securely via Razorpay Standard Checkout</span>
            </div>
            <div className="flex items-center gap-2.5">
              <Download className="w-4 h-4 text-[#7FE620]" />
              <span>Instant direct .zip file download upon payment</span>
            </div>
            <div className="flex items-center gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-[#7FE620]" />
              <span>Tested on PC, Mac, Android, iOS &amp; Consoles</span>
            </div>
          </div>
        </div>
      </section>

      {/* FAQ Accordion */}
      <section className="px-6 py-12 max-w-4xl mx-auto">
        <h2 className="text-xl font-bold tracking-tight mb-6 text-center text-white">
          Frequently Asked Questions
        </h2>

        <div className="space-y-3">
          {faqs.map((faq, index) => {
            const isOpen = openFaq === index;
            return (
              <div
                key={index}
                className="rounded-xl border border-white/10 bg-[#111]/60 overflow-hidden"
              >
                <button
                  onClick={() => setOpenFaq(isOpen ? null : index)}
                  className="w-full p-4 md:p-5 text-left font-semibold text-sm flex items-center justify-between gap-4 hover:bg-white/[0.02] transition-colors"
                >
                  <span className="text-white">{faq.q}</span>
                  <ChevronDown className={`w-4 h-4 text-neutral-400 transition-transform ${isOpen ? "rotate-180" : ""}`} />
                </button>
                {isOpen && (
                  <div className="p-4 md:p-5 pt-0 text-xs md:text-sm text-neutral-400 leading-relaxed border-t border-white/5">
                    {faq.a}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </section>

      {/* Bio Link Notice for Creator */}
      <div className="max-w-4xl mx-auto px-6 pb-12">
        <div className="p-4 rounded-xl border border-dashed border-[#7FE620]/30 bg-[#7FE620]/5 flex flex-wrap items-center justify-between gap-4 text-xs">
          <div className="text-neutral-300">
            <strong className="text-[#7FE620]">Direct Bio Link:</strong> This page is hidden from PocketHost navigation and only accessible via this direct link.
          </div>
          <button
            onClick={handleCopyLink}
            className="px-3.5 py-1.5 rounded-lg font-semibold bg-[#7FE620]/20 border border-[#7FE620]/40 text-[#7FE620] hover:bg-[#7FE620]/30 transition-all flex items-center gap-1.5 cursor-pointer"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5" /> Copied!
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5" /> Copy Bio Link
              </>
            )}
          </button>
        </div>
      </div>

      {/* Victory Celebration Modal */}
      {paymentSuccess && (
        <div className="fixed inset-0 bg-black/85 backdrop-blur-md flex items-center justify-center z-50 p-4">
          <div className="bg-[#111] border border-[#7FE620]/40 rounded-2xl p-8 max-w-md w-full text-center shadow-[0_0_50px_rgba(127,230,32,0.3)] animate-in fade-in zoom-in duration-200">
            <div className="w-16 h-16 rounded-full bg-[#7FE620]/20 border border-[#7FE620] flex items-center justify-center mx-auto mb-4 text-2xl">
              🎉
            </div>
            <h3 className="text-2xl font-extrabold text-white mb-2">Payment Verified!</h3>
            <p className="text-xs text-neutral-300 mb-6">
              Your Minecraft world archive is ready to download.
            </p>

            <div className="p-4 rounded-xl bg-black/60 border border-white/10 font-mono text-xs text-neutral-400 text-left space-y-1.5 mb-6">
              <div><strong className="text-white">Order ID:</strong> {paymentSuccess.order_id}</div>
              <div><strong className="text-white">Payment ID:</strong> {paymentSuccess.payment_id}</div>
              <div><strong className="text-white">Status:</strong> Verified &amp; Delivered &check;</div>
            </div>

            <a
              href={paymentSuccess.downloadUrl}
              download="minecraft-world.zip"
              className="w-full py-3.5 rounded-xl font-bold text-sm text-black bg-[#7FE620] hover:bg-[#8FEF35] block mb-3 shadow-[0_0_20px_rgba(127,230,32,0.3)] text-center"
            >
              Download World Archive (.zip)
            </a>

            <button
              onClick={() => setPaymentSuccess(null)}
              className="w-full py-2.5 rounded-xl font-medium text-xs text-neutral-400 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10"
            >
              Close
            </button>
          </div>
        </div>
      )}

      <Footer />
    </div>
  );
}
