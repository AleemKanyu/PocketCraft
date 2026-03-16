import { useState, FormEvent } from "react";
import { motion } from "framer-motion";
import { Copy, Check, Share2, Loader2 } from "lucide-react";
import { Link } from "react-router-dom";

export default function WaitlistForm() {
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");
  const [data, setData] = useState<{ rank: number; referral_code: string; referral_count: number } | null>(null);
  const [copied, setCopied] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!email || !username) return;

    // Client-side email validation to catch "pattern mismatch" early
    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    if (!emailRegex.test(email)) {
      setError("Please enter a valid email address (e.g., name@example.com)");
      return;
    }

    setLoading(true);
    setError("");

    try {
      const referred_by = localStorage.getItem("pocketcraft_ref");
      
      const res = await fetch("/api/waitlist", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, username, referred_by }),
      });

      let json;
      const responseText = await res.text();
      try {
        json = JSON.parse(responseText);
      } catch (parseError) {
        const debugInfo = responseText.substring(0, 100).replace(/<[^>]*>?/gm, '');
        throw new Error(`Invalid server response: ${debugInfo || 'Empty response'}`);
      }

      if (!res.ok) {
        let message = json.error || "Failed to join waitlist";
        if (json.diagnostics) {
          message += ` (Diag: ${JSON.stringify(json.diagnostics)})`;
        }
        throw new Error(message);
      }

      setData(json);
      setSuccess(true);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };


  const referralLink = data ? `${window.location.origin}/ref/${data.referral_code}` : "";

  const copyToClipboard = () => {
    navigator.clipboard.writeText(referralLink);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const shareOnWhatsApp = () => {
    const text = encodeURIComponent(`I just joined the waitlist for PocketCraft! Host a Minecraft server from your phone. Join here: ${referralLink}`);
    window.open(`https://wa.me/?text=${text}`, "_blank");
  };

  if (success && data) {
    return (
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        className="bg-black border-2 border-white/10 p-8 text-center"
      >
        <div className="w-12 h-12 border border-white/20 flex items-center justify-center mx-auto mb-6 bg-white/5">
          <span className="text-xl">✨</span>
        </div>
        
        <h3 className="text-sm font-minecraft text-white mb-6 tracking-wider uppercase">You're on the list!</h3>
        <div className="flex flex-col items-center justify-center gap-4 mb-8 font-minecraft">
          <div className="bg-[#4CAF50] text-white px-8 py-3 text-[10px] uppercase border-4 border-t-[#81C784] border-l-[#81C784] border-r-[#2E7D32] border-b-[#2E7D32]">
            RANK #{data.rank}
          </div>
          <div className="bg-[#333] border-4 border-t-[#8b8b8b] border-l-[#8b8b8b] border-r-[#1d1d1d] border-b-[#1d1d1d] text-[#FFD700] px-8 py-3 text-[10px] uppercase">
            {data.referral_count} REFERS
          </div>
        </div>
        
        <p className="text-white/30 text-[10px] mb-8 max-w-xs mx-auto leading-relaxed font-mono uppercase tracking-widest">
          Invite friends to climb. Each referral moves you up by 1 spot instantly.
        </p>

        <div className="bg-black border-4 border-[#333] p-4 flex items-center justify-between gap-3 mb-8">
          <code className="text-white/40 text-[10px] truncate flex-1 text-left select-all px-1 font-mono uppercase tracking-widest">
            {referralLink}
          </code>
          <button
            onClick={copyToClipboard}
            className="p-2 border-4 border-white/5 hover:border-white/20 transition-colors text-white/40 active:translate-y-1"
            title="Copy link"
          >
            {copied ? <Check size={14} className="text-white" /> : <Copy size={14} />}
          </button>
        </div>

        <motion.button
          whileTap={{ scale: 0.98 }}
          onClick={shareOnWhatsApp}
          className="btn-21st w-full py-6 text-[10px] uppercase flex items-center justify-center gap-2 mb-4"
        >
          <Share2 size={13} />
          Share on WhatsApp
        </motion.button>

        <Link 
          to="/ranking"
          className="block w-full bg-white/5 border-4 border-white/10 hover:border-white/30 py-5 text-[10px] font-minecraft uppercase transition-all text-white/40 hover:text-white text-center"
        >
          &gt; View Rankings
        </Link>

        <p className="text-[10px] text-white/10 font-black uppercase tracking-widest mt-5">
          {10 - (data.referral_count % 10)} more to skip 10 spots
        </p>
      </motion.div>
    );
  }

  return (
    <div className="w-full max-w-sm mx-auto relative flex flex-col gap-3">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <input
          type="text"
          required
          placeholder="CHOOSE A USERNAME"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          disabled={loading}
          className="w-full bg-black border-2 border-white/10 px-6 py-5 text-[10px] font-black tracking-[0.2em] text-white placeholder:text-white/20 focus:outline-none focus:border-white/40 transition-all disabled:opacity-40 uppercase"
        />
        <input
          type="email"
          required
          placeholder="ENTER YOUR EMAIL"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={loading}
          className="w-full bg-black border-2 border-white/10 px-6 py-5 text-[10px] font-black tracking-[0.2em] text-white placeholder:text-white/20 focus:outline-none focus:border-white/40 transition-all disabled:opacity-40 uppercase"
        />
        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          type="submit"
          disabled={loading}
          className="btn-21st w-full py-6 text-[10px] font-black uppercase tracking-[0.3em] transition-all disabled:opacity-40 flex items-center justify-center gap-2"
        >
          {loading ? <Loader2 className="animate-spin" size={14} /> : "Join Waitlist"}
        </motion.button>
      </form>

      {error && (
        <motion.p
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          className="text-white/30 text-[9px] font-bold uppercase tracking-widest mt-2 text-center"
        >
          {error}
        </motion.p>
      )}
    </div>
  );
}
