import { useState, useEffect } from "react";
import confetti from "canvas-confetti";
import {
  Download,
  Check,
  Copy,
  ArrowRight,
  Lock,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Eye,
  Package,
  Gamepad2,
  Palette,
  Clock,
  ShieldCheck,
  Sparkles
} from "lucide-react";

declare global {
  interface Window {
    Razorpay: any;
  }
}

const WORLD_PRICE_INR = 199;
const WORLD_PRICE_PAISE = WORLD_PRICE_INR * 100;

interface PaymentResult {
  order_id: string;
  payment_id: string;
  downloadUrl: string;
}

// Curated high-res shots from creator world
const FEATURED_SHOTS = [
  {
    src: "/world-shots/shot-19.webp",
    title: "Mountain Crater Base (Daytime)",
    tag: "MAIN BASE",
    desc: "The massive mountain crater estate with multi-tier manor, stone fortress walls, bamboo garden, and Steve cliff monument."
  },
  {
    src: "/world-shots/shot-18.webp",
    title: "Mountain Crater Base (Night Shaders)",
    tag: "NIGHT LIGHTING",
    desc: "How the world looks at night with the included shader configuration and lantern lighting."
  },
  {
    src: "/world-shots/shot-23.webp",
    title: "The Sun Portal & Fortress",
    tag: "FORTRESS",
    desc: "Massive glowing Sun crest cut into the mountain face, grand staircase, and covered bridge entrance."
  },
  {
    src: "/world-shots/shot-12.webp",
    title: "Village Courtyard & Beacon",
    tag: "COURTYARD",
    desc: "Full central village base with wheat fields, market stalls, beacon beam, and second estate."
  },
  {
    src: "/world-shots/shot-20.webp",
    title: "Full Aerial Map Layout",
    tag: "OVERVIEW",
    desc: "Complete top-down bird-eye view of the entire circular crater base and surrounding mountain rim."
  },
  {
    src: "/world-shots/shot-03.webp",
    title: "The Hobbit Hillside Base",
    tag: "SECRET BASE",
    desc: "Custom underground hobbit dwellings built directly into the terraced hillside with flower pathways."
  },
  {
    src: "/world-shots/shot-21.webp",
    title: "Cliffside Hanging Living Room",
    tag: "INTERIOR",
    desc: "Modern cliff-carved living quarters with hanging vines, chandelier lighting, and panoramic canyon views."
  },
  {
    src: "/world-shots/shot-11.webp",
    title: "Garden Wall & Lanterns",
    tag: "GARDENS",
    desc: "Detailed Japanese style boundary walls, hanging lamps, and manicured flora."
  }
];

// Grid gallery shots
const GALLERY_SHOTS = [
  { src: "/world-shots/shot-01.webp", label: "Mountain Rim Approach" },
  { src: "/world-shots/shot-02.webp", label: "Terraced Hillside Steps" },
  { src: "/world-shots/shot-04.webp", label: "Valley Entryway" },
  { src: "/world-shots/shot-05.webp", label: "Upper Mountain Ridge" },
  { src: "/world-shots/shot-06.webp", label: "Canyon Walkways" },
  { src: "/world-shots/shot-08.webp", label: "Fortress Ramparts" },
  { src: "/world-shots/shot-09.webp", label: "Courtyard Market Stalls" },
  { src: "/world-shots/shot-10.webp", label: "Night Lantern Walkways" },
  { src: "/world-shots/shot-16.webp", label: "Cherry Blossom Outpost" },
  { src: "/world-shots/shot-17.webp", label: "Deep Canyon Bridge" },
  { src: "/world-shots/shot-22.webp", label: "Cliff Carving Entrance" },
  { src: "/world-shots/shot-24.webp", label: "Beacon Valley Horizon" }
];

export default function WorldDrop() {
  const [buyerEmail, setBuyerEmail] = useState("");
  const [buyerName, setBuyerName] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [paymentSuccess, setPaymentSuccess] = useState<PaymentResult | null>(null);
  const [copied, setCopied] = useState(false);
  const [activeShotIndex, setActiveShotIndex] = useState(0);
  const [lightboxShot, setLightboxShot] = useState<string | null>(null);
  const [openFaq, setOpenFaq] = useState<number | null>(0);

  useEffect(() => {
    document.title = "Soulspeedmc World - The Viral Minecraft World";
  }, []);

  const handleCopyLink = () => {
    navigator.clipboard.writeText(window.location.href);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  const playVictorySound = () => {
    try {
      const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
      const notes = [523.25, 659.25, 783.99, 1046.5];
      notes.forEach((freq, idx) => {
        const osc = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        osc.type = "triangle";
        osc.frequency.setValueAtTime(freq, audioCtx.currentTime + idx * 0.12);
        gain.gain.setValueAtTime(0.25, audioCtx.currentTime + idx * 0.12);
        gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + idx * 0.12 + 0.35);
        osc.connect(gain);
        gain.connect(audioCtx.destination);
        osc.start(audioCtx.currentTime + idx * 0.12);
        osc.stop(audioCtx.currentTime + idx * 0.12 + 0.35);
      });
    } catch {
      // Audio context might be restricted before interaction
    }
  };

  const triggerCelebration = () => {
    playVictorySound();
    confetti({
      particleCount: 120,
      spread: 80,
      origin: { y: 0.6 },
      colors: ["#7FE620", "#ffffff", "#FFE600", "#FF0055"]
    });
  };

  const handleBuyNow = async () => {
    setErrorMessage(null);
    setStatusMessage(null);

    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!buyerEmail.trim() || !emailPattern.test(buyerEmail.trim())) {
      setErrorMessage("Please enter a valid email address so we can deliver your world files.");
      return;
    }

    if (typeof window.Razorpay === "undefined") {
      setErrorMessage("Payment gateway is loading. Please refresh the page and try again.");
      return;
    }

    setIsLoading(true);
    setStatusMessage("Generating secure checkout order...");

    try {
      const createRes = await fetch("/api/create-order", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          amount: WORLD_PRICE_PAISE,
          currency: "INR",
          receipt: `rcpt_${Date.now()}`
        })
      });

      if (!createRes.ok) {
        const errData = await createRes.json().catch(() => ({}));
        throw new Error(errData.error || `Server returned error ${createRes.status}`);
      }

      const orderData = await createRes.json();
      const keyId = orderData.key_id || (import.meta as any).env?.VITE_RAZORPAY_KEY_ID || "";
      if (!keyId) {
        throw new Error("Razorpay gateway key not configured.");
      }

      setStatusMessage("Opening payment modal...");

      const options = {
        key: keyId,
        amount: orderData.amount,
        currency: orderData.currency,
        name: "Soulspeedmc World",
        description: "World Save + Mods + Resource Packs",
        image: "/world-shots/shot-19.webp",
        order_id: orderData.order_id,
        prefill: {
          email: buyerEmail.trim(),
          name: buyerName.trim() || "Minecraft Player"
        },
        theme: {
          color: "#7FE620"
        },
        modal: {
          ondismiss: () => {
            setIsLoading(false);
            setStatusMessage(null);
          }
        },
        handler: async (response: any) => {
          setStatusMessage("Verifying payment security...");
          try {
            const verifyRes = await fetch("/api/verify-payment", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                razorpay_order_id: response.razorpay_order_id,
                razorpay_payment_id: response.razorpay_payment_id,
                razorpay_signature: response.razorpay_signature
              })
            });

            const verifyData = await verifyRes.json();

            if (!verifyRes.ok || !verifyData.success) {
              throw new Error(verifyData.error || "Payment verification failed");
            }

            const downloadUrl = verifyData.downloadUrl || "/downloads/minecraft-world.zip";

            setPaymentSuccess({
              order_id: response.razorpay_order_id,
              payment_id: response.razorpay_payment_id,
              downloadUrl
            });

            setIsLoading(false);
            setStatusMessage(null);
            triggerCelebration();

            // Auto-trigger download
            setTimeout(() => {
              const link = document.createElement("a");
              link.href = downloadUrl;
              link.setAttribute("download", "soulspeedmc-world-pack.zip");
              document.body.appendChild(link);
              link.click();
              document.body.removeChild(link);
            }, 800);
          } catch (verifyErr: any) {
            setIsLoading(false);
            setStatusMessage(null);
            setErrorMessage(verifyErr.message || "Could not verify payment signature.");
          }
        }
      };

      const rzp = new window.Razorpay(options);
      rzp.on("payment.failed", (resp: any) => {
        setIsLoading(false);
        setStatusMessage(null);
        setErrorMessage(resp.error?.description || "Payment was not completed.");
      });

      rzp.open();
    } catch (err: any) {
      setIsLoading(false);
      setStatusMessage(null);
      setErrorMessage(err.message || "Failed to initiate payment. Please try again.");
    }
  };

  const currentShot = FEATURED_SHOTS[activeShotIndex];

  return (
    <div className="min-h-screen bg-[#0a0a0a] text-white font-sans selection:bg-[#7FE620] selection:text-black">
      {/* Top Neo-Brutalist Ticker / Navigation */}
      <header className="sticky top-0 z-40 bg-[#000000] border-b-2 border-white px-4 md:px-8 py-3.5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="w-3 h-3 bg-[#7FE620] border border-black inline-block animate-pulse" />
          <span className="font-mono text-xs md:text-sm font-black tracking-widest uppercase">
            SOULSPEEDMC // WORLD ARCHIVE
          </span>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleCopyLink}
            className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 border-2 border-white bg-[#1a1a1a] text-xs font-mono font-bold hover:bg-white hover:text-black transition-all cursor-pointer"
          >
            {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copied ? "COPIED" : "BIO LINK"}</span>
          </button>

          <a
            href="#checkout"
            className="px-4 py-1.5 border-2 border-white bg-[#7FE620] text-black text-xs md:text-sm font-black tracking-wider uppercase shadow-[3px_3px_0px_#ffffff] hover:shadow-[1px_1px_0px_#ffffff] hover:translate-x-[2px] hover:translate-y-[2px] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-all cursor-pointer"
          >
            GET WORLD • ₹199
          </a>
        </div>
      </header>

      {/* Hero Section */}
      <section className="px-4 md:px-8 pt-10 md:pt-16 pb-12 max-w-6xl mx-auto">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          <div className="lg:col-span-7 space-y-6">
            <div className="inline-block border-2 border-white bg-black px-3 py-1 font-mono text-xs font-bold text-[#7FE620] shadow-[3px_3px_0px_#7FE620]">
              ★ OFFICIAL CREATOR WORLD DROP
            </div>

            <h1 className="text-4xl sm:text-6xl md:text-7xl font-black tracking-tighter uppercase leading-[0.95]">
              SOULSPEEDMC <br />
              <span className="text-[#7FE620] bg-black border-2 border-white px-2 inline-block mt-1 shadow-[5px_5px_0px_#ffffff]">
                WORLD SAVE
              </span>
            </h1>

            <p className="text-base sm:text-lg md:text-xl text-neutral-300 font-medium leading-relaxed max-w-xl">
              This is the <strong className="text-white">same viral world you see online</strong>.
              Now you can play in it yourself. Every base, mountain cliff build, and farm is here.
            </p>

            <div className="p-4 border-2 border-white bg-[#141414] shadow-[5px_5px_0px_#7FE620] space-y-2 max-w-xl">
              <div className="font-mono text-xs font-black text-[#7FE620] uppercase flex items-center gap-2">
                <Sparkles className="w-4 h-4" />
                <span>MODS &amp; RESOURCE PACKS INCLUDED</span>
              </div>
              <p className="text-xs sm:text-sm text-neutral-300">
                You get my exact modpack and custom resource packs alongside the world file so your game looks <strong>100% identical to mine</strong> from the moment you load in.
              </p>
            </div>

            {/* Quick CTA row */}
            <div className="flex flex-wrap items-center gap-4 pt-2">
              <a
                href="#checkout"
                className="px-6 py-4 border-2 border-white bg-[#7FE620] text-black font-black text-base md:text-lg uppercase tracking-wider shadow-[6px_6px_0px_#ffffff] hover:shadow-[2px_2px_0px_#ffffff] hover:translate-x-[4px] hover:translate-y-[4px] active:translate-x-[6px] active:translate-y-[6px] active:shadow-none transition-all flex items-center gap-2 cursor-pointer"
              >
                <span>DOWNLOAD WORLD &amp; MODS</span>
                <ArrowRight className="w-5 h-5" />
              </a>

              <a
                href="#gallery"
                className="px-6 py-4 border-2 border-white bg-black text-white font-mono font-bold text-sm uppercase tracking-wider shadow-[4px_4px_0px_#ffffff] hover:bg-white hover:text-black hover:shadow-[1px_1px_0px_#ffffff] hover:translate-x-[3px] hover:translate-y-[3px] transition-all flex items-center gap-2"
              >
                <Eye className="w-4 h-4" />
                <span>SEE SCREENSHOTS</span>
              </a>
            </div>

            {/* Neo-brutalist feature bar */}
            <div className="grid grid-cols-3 gap-2 pt-4 font-mono text-xs max-w-xl">
              <div className="border border-white/40 bg-black p-2.5 text-center">
                <div className="text-[#7FE620] font-black">100%</div>
                <div className="text-neutral-400 text-[11px]">ALL BUILDS INTACT</div>
              </div>
              <div className="border border-white/40 bg-black p-2.5 text-center">
                <div className="text-[#7FE620] font-black">FULL PACK</div>
                <div className="text-neutral-400 text-[11px]">MODS &amp; SHADERS</div>
              </div>
              <div className="border border-white/40 bg-black p-2.5 text-center">
                <div className="text-[#7FE620] font-black">INSTANT</div>
                <div className="text-neutral-400 text-[11px]">DIRECT .ZIP</div>
              </div>
            </div>
          </div>

          {/* Hero Image Showcase */}
          <div className="lg:col-span-5">
            <div className="border-4 border-white bg-black shadow-[8px_8px_0px_#7FE620] overflow-hidden">
              <div className="border-b-2 border-white bg-[#1c1c1c] px-4 py-2 flex items-center justify-between font-mono text-xs">
                <span className="font-bold text-[#7FE620]">ACTUAL IN-GAME SCREENSHOT</span>
                <span className="text-neutral-400">JAVA 1.20+ / 1.21+</span>
              </div>
              <div className="relative aspect-[16/10] overflow-hidden bg-neutral-900 group">
                <img
                  src={currentShot.src}
                  alt={currentShot.title}
                  className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                />
                <div className="absolute top-3 left-3 border-2 border-black bg-[#7FE620] text-black font-mono text-[11px] font-black px-2 py-0.5 shadow-[2px_2px_0px_#000]">
                  {currentShot.tag}
                </div>
              </div>
              <div className="p-4 border-t-2 border-white bg-black space-y-2">
                <div className="flex items-center justify-between">
                  <div className="font-bold text-sm uppercase">{currentShot.title}</div>
                  <div className="font-mono text-xs text-neutral-400">
                    {activeShotIndex + 1} / {FEATURED_SHOTS.length}
                  </div>
                </div>
                <p className="text-xs text-neutral-400">{currentShot.desc}</p>

                {/* Switcher Buttons */}
                <div className="flex items-center gap-2 pt-2">
                  <button
                    onClick={() =>
                      setActiveShotIndex((prev) => (prev === 0 ? FEATURED_SHOTS.length - 1 : prev - 1))
                    }
                    className="flex-1 py-2 border-2 border-white bg-black font-mono text-xs font-bold hover:bg-white hover:text-black flex items-center justify-center gap-1 cursor-pointer"
                  >
                    <ChevronLeft className="w-4 h-4" /> PREV SHOT
                  </button>
                  <button
                    onClick={() =>
                      setActiveShotIndex((prev) => (prev === FEATURED_SHOTS.length - 1 ? 0 : prev + 1))
                    }
                    className="flex-1 py-2 border-2 border-white bg-[#7FE620] text-black font-mono text-xs font-black hover:bg-white flex items-center justify-center gap-1 cursor-pointer"
                  >
                    NEXT SHOT <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Interactive Main Screenshot Gallery */}
      <section id="gallery" className="border-t-2 border-b-2 border-white bg-[#111111] py-14 px-4 md:px-8">
        <div className="max-w-6xl mx-auto space-y-8">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
            <div>
              <div className="font-mono text-xs font-black text-[#7FE620] uppercase tracking-wider mb-1">
                // WORLD SHOWCASE
              </div>
              <h2 className="text-3xl sm:text-4xl md:text-5xl font-black uppercase tracking-tight">
                INSIDE THE SOULSPEEDMC WORLD
              </h2>
            </div>
            <p className="text-xs sm:text-sm font-mono text-neutral-400 max-w-sm">
              All screenshots captured directly inside the world with the included shader &amp; texture pack.
            </p>
          </div>

          {/* Big Viewer with thumbnails */}
          <div className="border-4 border-white bg-black shadow-[10px_10px_0px_#ffffff]">
            <div className="relative aspect-[16/9] w-full overflow-hidden bg-neutral-950">
              <img
                src={FEATURED_SHOTS[activeShotIndex].src}
                alt={FEATURED_SHOTS[activeShotIndex].title}
                className="w-full h-full object-cover"
              />

              <div className="absolute top-4 left-4 border-2 border-black bg-white text-black font-mono text-xs font-black px-3 py-1 shadow-[3px_3px_0px_#000]">
                {FEATURED_SHOTS[activeShotIndex].title}
              </div>

              <button
                onClick={() => setLightboxShot(FEATURED_SHOTS[activeShotIndex].src)}
                className="absolute top-4 right-4 border-2 border-black bg-[#7FE620] text-black font-mono text-xs font-black px-3 py-1.5 shadow-[3px_3px_0px_#000] hover:bg-white flex items-center gap-1 cursor-pointer"
              >
                <Eye className="w-3.5 h-3.5" /> FULLSCREEN
              </button>

              <button
                onClick={() =>
                  setActiveShotIndex((prev) => (prev === 0 ? FEATURED_SHOTS.length - 1 : prev - 1))
                }
                className="absolute left-4 top-1/2 -translate-y-1/2 w-10 h-10 border-2 border-black bg-white text-black flex items-center justify-center font-black shadow-[3px_3px_0px_#000] hover:bg-[#7FE620] cursor-pointer"
              >
                <ChevronLeft className="w-6 h-6" />
              </button>

              <button
                onClick={() =>
                  setActiveShotIndex((prev) => (prev === FEATURED_SHOTS.length - 1 ? 0 : prev + 1))
                }
                className="absolute right-4 top-1/2 -translate-y-1/2 w-10 h-10 border-2 border-black bg-white text-black flex items-center justify-center font-black shadow-[3px_3px_0px_#000] hover:bg-[#7FE620] cursor-pointer"
              >
                <ChevronRight className="w-6 h-6" />
              </button>
            </div>

            {/* Thumbnail Row */}
            <div className="p-3 border-t-2 border-white bg-[#1a1a1a] flex gap-2 overflow-x-auto">
              {FEATURED_SHOTS.map((shot, idx) => {
                const isActive = activeShotIndex === idx;
                return (
                  <button
                    key={idx}
                    onClick={() => setActiveShotIndex(idx)}
                    className={`relative flex-shrink-0 w-24 h-16 border-2 overflow-hidden cursor-pointer transition-all ${
                      isActive ? "border-[#7FE620] scale-105 shadow-[2px_2px_0px_#7FE620]" : "border-white/40 opacity-70 hover:opacity-100"
                    }`}
                  >
                    <img src={shot.src} alt={shot.title} className="w-full h-full object-cover" />
                  </button>
                );
              })}
            </div>
          </div>

          {/* Additional Gallery Grid */}
          <div className="pt-6">
            <h3 className="font-mono text-sm font-bold uppercase tracking-wider text-neutral-400 mb-4">
              // MORE ANGLES &amp; DETAILS
            </h3>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
              {GALLERY_SHOTS.map((g, i) => (
                <div
                  key={i}
                  onClick={() => setLightboxShot(g.src)}
                  className="border-2 border-white bg-black shadow-[4px_4px_0px_#7FE620] overflow-hidden group cursor-pointer hover:translate-x-[-2px] hover:translate-y-[-2px] hover:shadow-[6px_6px_0px_#7FE620] transition-all"
                >
                  <div className="aspect-[16/10] overflow-hidden bg-neutral-900">
                    <img
                      src={g.src}
                      alt={g.label}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                    />
                  </div>
                  <div className="p-2 border-t border-white/20 font-mono text-[11px] font-bold text-neutral-300 truncate">
                    {g.label}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* What You Get Breakdown */}
      <section className="py-16 px-4 md:px-8 max-w-6xl mx-auto">
        <div className="text-center space-y-3 mb-12">
          <div className="inline-block border-2 border-white bg-black px-3 py-1 font-mono text-xs font-bold text-[#7FE620]">
            COMPLETE PACKAGE
          </div>
          <h2 className="text-3xl sm:text-5xl font-black tracking-tight uppercase">
            EVERYTHING INCLUDED IN YOUR DOWNLOAD
          </h2>
          <p className="text-sm sm:text-base text-neutral-400 font-mono max-w-xl mx-auto">
            You don not just get a blank world — you get the complete creator kit so your Minecraft plays and looks identical to my videos.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Card 1: World File */}
          <div className="border-3 border-white bg-[#141414] p-6 shadow-[6px_6px_0px_#7FE620] space-y-4">
            <div className="flex items-center justify-between border-b-2 border-white/20 pb-3">
              <div className="w-10 h-10 border-2 border-white bg-[#7FE620] text-black flex items-center justify-center font-black">
                <Gamepad2 className="w-5 h-5" />
              </div>
              <span className="font-mono text-xs font-bold text-[#7FE620]">ITEM 01 // WORLD</span>
            </div>
            <h3 className="text-xl font-black uppercase">THE OFFICIAL SOULSPEEDMC WORLD</h3>
            <p className="text-xs sm:text-sm text-neutral-300 leading-relaxed">
              The full survival world save file. Explore the giant circular mountain crater, the 3-story timber manor, the Sun portal mountain fortress, the terraced hobbit homes, and every hidden room and farm exactly as built.
            </p>
            <ul className="font-mono text-xs space-y-1.5 text-neutral-400 pt-2">
              <li>✓ All chests, items, and farms intact</li>
              <li>✓ Fully explored survival progression</li>
              <li>✓ Compatible with Java 1.20+ and 1.21+</li>
            </ul>
          </div>

          {/* Card 2: Modpack */}
          <div className="border-3 border-white bg-[#141414] p-6 shadow-[6px_6px_0px_#ffffff] space-y-4">
            <div className="flex items-center justify-between border-b-2 border-white/20 pb-3">
              <div className="w-10 h-10 border-2 border-white bg-white text-black flex items-center justify-center font-black">
                <Package className="w-5 h-5" />
              </div>
              <span className="font-mono text-xs font-bold text-white">ITEM 02 // MODS</span>
            </div>
            <h3 className="text-xl font-black uppercase">THE EXACT PERFORMANCE MODPACK</h3>
            <p className="text-xs sm:text-sm text-neutral-300 leading-relaxed">
              The exact Fabric and Iris optimization mod collection I run in every single video. Keeps your framerate silky smooth (even on modest PCs/laptops) while rendering maximum visual fidelity.
            </p>
            <ul className="font-mono text-xs space-y-1.5 text-neutral-400 pt-2">
              <li>✓ Sodium + Iris shader support pre-configured</li>
              <li>✓ Smooth camera and lighting tweaks</li>
              <li>✓ 100% free, open mods curated together</li>
            </ul>
          </div>

          {/* Card 3: Resource Packs */}
          <div className="border-3 border-white bg-[#141414] p-6 shadow-[6px_6px_0px_#ffffff] space-y-4">
            <div className="flex items-center justify-between border-b-2 border-white/20 pb-3">
              <div className="w-10 h-10 border-2 border-white bg-[#FFE600] text-black flex items-center justify-center font-black">
                <Palette className="w-5 h-5" />
              </div>
              <span className="font-mono text-xs font-bold text-[#FFE600]">ITEM 03 // TEXTURES</span>
            </div>
            <h3 className="text-xl font-black uppercase">CUSTOM RESOURCE PACKS &amp; SHADERS</h3>
            <p className="text-xs sm:text-sm text-neutral-300 leading-relaxed">
              The exact shader presets, leaves bushiness, skybox, and UI texture packs I use. Stop guessing which shaders or textures were on screen — you get the exact files and configuration ready to load.
            </p>
            <ul className="font-mono text-xs space-y-1.5 text-neutral-400 pt-2">
              <li>✓ Exact shader settings preset included</li>
              <li>✓ Custom skybox and realistic water</li>
              <li>✓ Vibrant leaves and foliage pack</li>
            </ul>
          </div>

          {/* Card 4: 2-Minute Setup */}
          <div className="border-3 border-white bg-[#141414] p-6 shadow-[6px_6px_0px_#7FE620] space-y-4">
            <div className="flex items-center justify-between border-b-2 border-white/20 pb-3">
              <div className="w-10 h-10 border-2 border-white bg-[#7FE620] text-black flex items-center justify-center font-black">
                <Clock className="w-5 h-5" />
              </div>
              <span className="font-mono text-xs font-bold text-[#7FE620]">ITEM 04 // INSTALL</span>
            </div>
            <h3 className="text-xl font-black uppercase">2-MINUTE QUICK SETUP GUIDE</h3>
            <p className="text-xs sm:text-sm text-neutral-300 leading-relaxed">
              No complicated tech knowledge needed. Follow simple copy-paste steps to drop the world into your .minecraft/saves folder and launch. Ready to play in minutes on Windows, Mac, or Linux.
            </p>
            <ul className="font-mono text-xs space-y-1.5 text-neutral-400 pt-2">
              <li>✓ Clear step-by-step instructions text file</li>
              <li>✓ Bedrock world import instructions included</li>
              <li>✓ Instant download right after payment</li>
            </ul>
          </div>
        </div>
      </section>

      {/* Checkout Station (Razorpay Standard Web Checkout) */}
      <section id="checkout" className="border-t-2 border-white bg-[#0f0f0f] py-16 px-4 md:px-8">
        <div className="max-w-2xl mx-auto">
          <div className="border-4 border-white bg-black shadow-[10px_10px_0px_#7FE620] p-6 sm:p-8">
            <div className="border-b-2 border-white pb-6 mb-6">
              <div className="flex items-center justify-between gap-4 mb-2">
                <span className="border-2 border-black bg-[#7FE620] text-black font-mono text-xs font-black px-2 py-0.5 shadow-[2px_2px_0px_#000]">
                  LIFETIME ARCHIVE
                </span>
                <span className="font-mono text-xs text-neutral-400">ONE-TIME PURCHASE</span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-black uppercase tracking-tight">
                GET SOULSPEEDMC WORLD + MODS
              </h2>
              <p className="text-xs sm:text-sm text-neutral-400 font-mono mt-1">
                Enter your email address to receive your world archive and download link immediately.
              </p>
            </div>

            {/* Price Box */}
            <div className="border-2 border-white bg-[#181818] p-4 mb-6 flex items-center justify-between">
              <div>
                <div className="font-mono text-xs text-neutral-400 uppercase">PRICE TOTAL</div>
                <div className="text-3xl sm:text-4xl font-black text-[#7FE620]">₹{WORLD_PRICE_INR}</div>
              </div>
              <div className="text-right font-mono text-xs text-neutral-300 space-y-0.5">
                <div>✓ World Archive (.zip)</div>
                <div>✓ Mods + Shaders Included</div>
                <div>✓ Lifetime Access</div>
              </div>
            </div>

            {/* Error / Status banners */}
            {errorMessage && (
              <div className="border-2 border-[#ff3366] bg-[#29000d] p-3.5 mb-4 text-xs font-mono text-[#ff8099] flex items-start gap-2">
                <span className="font-black">ERROR:</span>
                <span>{errorMessage}</span>
              </div>
            )}

            {statusMessage && (
              <div className="border-2 border-[#7FE620] bg-[#0c2400] p-3.5 mb-4 text-xs font-mono text-[#a7f3d0] flex items-center gap-2">
                <span className="animate-spin">⏳</span>
                <span>{statusMessage}</span>
              </div>
            )}

            {/* Inputs */}
            <div className="space-y-4 mb-6">
              <div>
                <label className="block font-mono text-xs font-bold uppercase text-neutral-300 mb-2">
                  YOUR EMAIL ADDRESS <span className="text-[#7FE620]">*</span> (FOR RECEIPT &amp; ACCESS)
                </label>
                <input
                  type="email"
                  placeholder="name@email.com"
                  value={buyerEmail}
                  onChange={(e) => setBuyerEmail(e.target.value)}
                  disabled={isLoading}
                  className="w-full border-2 border-white bg-black px-4 py-3.5 font-mono text-sm text-white placeholder-neutral-500 outline-none focus:border-[#7FE620] focus:shadow-[3px_3px_0px_#7FE620] transition-all"
                />
              </div>

              <div>
                <label className="block font-mono text-xs font-bold uppercase text-neutral-300 mb-2">
                  GAMERTAG / NAME (OPTIONAL)
                </label>
                <input
                  type="text"
                  placeholder="e.g. Alex"
                  value={buyerName}
                  onChange={(e) => setBuyerName(e.target.value)}
                  disabled={isLoading}
                  className="w-full border-2 border-white bg-black px-4 py-3.5 font-mono text-sm text-white placeholder-neutral-500 outline-none focus:border-[#7FE620] focus:shadow-[3px_3px_0px_#7FE620] transition-all"
                />
              </div>
            </div>

            {/* Buy Button */}
            <button
              onClick={handleBuyNow}
              disabled={isLoading}
              className="w-full py-4 border-2 border-white bg-[#7FE620] text-black font-black text-base md:text-lg uppercase tracking-wider shadow-[6px_6px_0px_#ffffff] hover:shadow-[2px_2px_0px_#ffffff] hover:translate-x-[4px] hover:translate-y-[4px] active:translate-x-[6px] active:translate-y-[6px] active:shadow-none transition-all flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isLoading ? (
                <span className="font-mono">PROCESSING PAYMENT...</span>
              ) : (
                <>
                  <span>PAY ₹{WORLD_PRICE_INR} &amp; DOWNLOAD NOW</span>
                  <ArrowRight className="w-5 h-5" />
                </>
              )}
            </button>

            {/* Trust Notice */}
            <div className="mt-6 pt-5 border-t border-white/20 font-mono text-xs text-neutral-400 space-y-2">
              <div className="flex items-center gap-2">
                <Lock className="w-3.5 h-3.5 text-[#7FE620]" />
                <span>Secured by Razorpay Standard Checkout (UPI, Cards, NetBanking)</span>
              </div>
              <div className="flex items-center gap-2">
                <Download className="w-3.5 h-3.5 text-[#7FE620]" />
                <span>Immediate direct .zip archive download upon completion</span>
              </div>
              <div className="flex items-center gap-2">
                <ShieldCheck className="w-3.5 h-3.5 text-[#7FE620]" />
                <span>Safe file archive verified for viruses &amp; malware</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* FAQ Section (Neo-Brutalist) */}
      <section className="py-14 px-4 md:px-8 max-w-4xl mx-auto">
        <div className="text-center space-y-2 mb-10">
          <div className="font-mono text-xs font-black text-[#7FE620] uppercase">
            // FREQUENTLY ASKED QUESTIONS
          </div>
          <h2 className="text-3xl sm:text-4xl font-black tracking-tight uppercase">
            COMMON QUESTIONS
          </h2>
        </div>

        <div className="space-y-3">
          {[
            {
              q: "HOW DO I GET THE WORLD FILE AFTER PAYING?",
              a: "Right after your payment completes through Razorpay, your browser will immediately trigger the direct .zip download on this screen. You will also see an instant download button."
            },
            {
              q: "WILL MY GAME REALLY LOOK EXACTLY LIKE YOUR VIDEOS?",
              a: "Yes! Alongside the world save, you get the exact modpack (Iris/Sodium for high FPS and shaders) and custom texture/foliage resource packs configured exactly as shown."
            },
            {
              q: "WHICH MINECRAFT VERSIONS ARE SUPPORTED?",
              a: "The world is tested and runs flawlessly on Minecraft Java Edition 1.20+ and 1.21+. The included guide also provides simple steps for importing into Bedrock / Windows 10 Edition."
            },
            {
              q: "IS THIS A RECURRING SUBSCRIPTION?",
              a: "No! It is a one-time ₹199 purchase. You own the files forever."
            },
            {
              q: "WHERE DO I PUT THE FILES ON PC / MAC?",
              a: "The download includes a 2-minute quick-start text guide. On Java, you simply copy the world folder into your .minecraft/saves folder and you are ready to jump in!"
            }
          ].map((item, index) => {
            const isOpen = openFaq === index;
            return (
              <div
                key={index}
                className="border-2 border-white bg-black shadow-[4px_4px_0px_#ffffff] overflow-hidden"
              >
                <button
                  onClick={() => setOpenFaq(isOpen ? null : index)}
                  className="w-full p-4 md:p-5 text-left font-mono font-bold text-xs sm:text-sm flex items-center justify-between gap-4 hover:bg-[#1a1a1a] transition-colors cursor-pointer"
                >
                  <span className="text-white">{item.q}</span>
                  <ChevronDown className={`w-4 h-4 text-[#7FE620] transition-transform ${isOpen ? "rotate-180" : ""}`} />
                </button>
                {isOpen && (
                  <div className="p-4 md:p-5 pt-0 text-xs sm:text-sm text-neutral-300 leading-relaxed border-t border-white/20 bg-[#111]">
                    {item.a}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </section>

      {/* Creator Direct Bio Link Callout */}
      <div className="max-w-4xl mx-auto px-4 md:px-8 pb-12">
        <div className="border-2 border-dashed border-[#7FE620] bg-black p-4 md:p-5 flex flex-wrap items-center justify-between gap-4 font-mono text-xs">
          <div className="text-neutral-300">
            <strong className="text-[#7FE620]">DIRECT BIO LINK:</strong> This page is hidden from website navigation and only accessible via this direct link.
          </div>
          <button
            onClick={handleCopyLink}
            className="px-4 py-2 border-2 border-[#7FE620] bg-[#7FE620] text-black font-black hover:bg-white hover:border-white transition-all flex items-center gap-1.5 cursor-pointer shadow-[3px_3px_0px_#7FE620]"
          >
            {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            <span>{copied ? "LINK COPIED" : "COPY BIO LINK"}</span>
          </button>
        </div>
      </div>

      {/* Lightbox Modal */}
      {lightboxShot && (
        <div
          onClick={() => setLightboxShot(null)}
          className="fixed inset-0 bg-black/95 z-50 p-4 flex flex-col items-center justify-center cursor-pointer"
        >
          <div className="relative max-w-6xl max-h-[85vh] border-4 border-white shadow-[12px_12px_0px_#7FE620] overflow-hidden">
            <img src={lightboxShot} alt="Enlarged screenshot" className="w-full h-full object-contain" />
          </div>
          <p className="font-mono text-xs text-neutral-400 mt-4">CLICK ANYWHERE TO CLOSE</p>
        </div>
      )}

      {/* Victory Celebration Modal */}
      {paymentSuccess && (
        <div className="fixed inset-0 bg-black/90 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-black border-4 border-[#7FE620] p-6 sm:p-8 max-w-md w-full text-center shadow-[12px_12px_0px_#7FE620] animate-in fade-in zoom-in duration-200">
            <div className="w-16 h-16 border-2 border-white bg-[#7FE620] text-black flex items-center justify-center mx-auto mb-4 text-3xl font-black">
              ✓
            </div>
            <h3 className="text-2xl sm:text-3xl font-black uppercase text-white mb-2">
              PAYMENT VERIFIED!
            </h3>
            <p className="font-mono text-xs text-[#a7f3d0] mb-6">
              YOUR SOULSPEEDMC WORLD + MODPACK IS READY TO DOWNLOAD.
            </p>

            <div className="p-4 border-2 border-white/40 bg-[#141414] font-mono text-xs text-neutral-300 text-left space-y-1.5 mb-6">
              <div><strong className="text-[#7FE620]">ORDER ID:</strong> {paymentSuccess.order_id}</div>
              <div><strong className="text-[#7FE620]">PAYMENT ID:</strong> {paymentSuccess.payment_id}</div>
              <div><strong className="text-white">STATUS:</strong> COMPLETE &amp; VERIFIED</div>
            </div>

            <a
              href={paymentSuccess.downloadUrl}
              download="soulspeedmc-world-pack.zip"
              className="w-full py-4 border-2 border-white bg-[#7FE620] text-black font-black text-sm uppercase tracking-wider block mb-3 shadow-[4px_4px_0px_#ffffff] hover:shadow-[1px_1px_0px_#ffffff] hover:translate-x-[3px] hover:translate-y-[3px] text-center"
            >
              DOWNLOAD WORLD ARCHIVE (.ZIP)
            </a>

            <button
              onClick={() => setPaymentSuccess(null)}
              className="w-full py-2.5 border border-white/40 bg-transparent font-mono text-xs font-bold text-neutral-400 hover:text-white hover:border-white"
            >
              CLOSE
            </button>
          </div>
        </div>
      )}

      {/* Dedicated Neo-Brutalist Footer */}
      <footer className="border-t-2 border-white bg-black py-8 px-4 md:px-8 font-mono text-xs text-neutral-400">
        <div className="max-w-6xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4 text-center sm:text-left">
          <div>
            <div className="font-black text-white text-sm tracking-wider uppercase">
              SOULSPEEDMC WORLD ARCHIVE
            </div>
            <div className="text-[11px] text-neutral-500 mt-0.5">
              The exact viral Minecraft world, mods, and resource packs.
            </div>
          </div>

          <div className="text-[11px] text-neutral-500 max-w-md text-center sm:text-right">
            Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft. All rights reserved.
          </div>
        </div>
      </footer>
    </div>
  );
}
