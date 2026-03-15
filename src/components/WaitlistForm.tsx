import { useState, FormEvent } from "react";
import { motion } from "motion/react";
import { Copy, Check, Share2, Loader2 } from "lucide-react";

export default function WaitlistForm() {
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");
  const [data, setData] = useState<{ rank: number; referral_code: string; referral_count: number } | null>(null);
  const [copied, setCopied] = useState(false);
  const [showCheckStatus, setShowCheckStatus] = useState(false);

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

  const handleCheckStatus = async (e: FormEvent) => {
    e.preventDefault();
    if (!email) return;

    setLoading(true);
    setError("");

    try {
      const res = await fetch(`/api/waitlist?email=${encodeURIComponent(email)}`);
      const json = await res.json();

      if (!res.ok) {
        throw new Error(json.error || "User not found");
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
        initial={{ opacity: 0, scale: 0.97 }}
        animate={{ opacity: 1, scale: 1 }}
        className="bg-[#0f0f0f] border border-white/5 rounded-2xl p-7 text-center"
      >
        <div className="w-12 h-12 bg-white/5 border border-white/10 rounded-full flex items-center justify-center mx-auto mb-5">
          <span className="text-xl">✨</span>
        </div>
        
        <h3 className="text-lg font-bold text-white mb-1 tracking-tight uppercase">You're on the list!</h3>
        <div className="flex items-center justify-center gap-2 mb-5">
          <div className="bg-black border border-white/10 text-white rounded-full px-5 py-1.5 text-xs font-black tracking-widest uppercase">
            RANK #{data.rank}
          </div>
          <div className="bg-white/5 border border-white/10 text-white/50 rounded-full px-5 py-1.5 text-xs font-black tracking-widest uppercase">
            {data.referral_count} REFERS
          </div>
        </div>
        
        <p className="text-white/30 text-[10px] mb-6 max-w-xs mx-auto leading-relaxed font-bold uppercase tracking-wider">
          Invite friends to climb the list. Each referral moves you up by 1 spot instantly.
        </p>

        <div className="bg-black border border-white/5 rounded-xl p-3 flex items-center justify-between gap-2 mb-4">
          <code className="text-white/30 text-[10px] truncate flex-1 text-left select-all px-1 font-mono">
            {referralLink}
          </code>
          <button
            onClick={copyToClipboard}
            className="p-1.5 bg-white/5 hover:bg-white/10 border border-white/5 rounded-lg transition-colors text-white/40"
            title="Copy link"
          >
            {copied ? <Check size={14} className="text-white" /> : <Copy size={14} />}
          </button>
        </div>

        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={shareOnWhatsApp}
          className="w-full bg-white text-black py-3 rounded-full text-[10px] font-black uppercase tracking-widest flex items-center justify-center gap-2 transition-all mb-3"
        >
          <Share2 size={13} />
          Share on WhatsApp
        </motion.button>

        <p className="text-[10px] text-white/10 font-black uppercase tracking-widest mt-5">
          {10 - (data.referral_count % 10)} more to skip 10 spots
        </p>
      </motion.div>
    );
  }

  return (
    <div className="w-full max-w-sm mx-auto relative flex flex-col gap-3">
      <form onSubmit={showCheckStatus ? handleCheckStatus : handleSubmit} className="flex flex-col gap-3">
        {!showCheckStatus && (
          <input
            type="text"
            required
            placeholder="CHOOSE A USERNAME"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            disabled={loading}
            className="w-full bg-[#0f0f0f] border border-white/10 rounded-full px-5 py-3.5 text-[10px] font-bold tracking-widest text-white placeholder:text-white/20 focus:outline-none focus:border-white/30 transition-all disabled:opacity-40 uppercase"
          />
        )}
        <input
          type="email"
          required
          placeholder="ENTER YOUR EMAIL"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={loading}
          className="w-full bg-[#0f0f0f] border border-white/10 rounded-full px-5 py-3.5 text-[10px] font-bold tracking-widest text-white placeholder:text-white/20 focus:outline-none focus:border-white/30 transition-all disabled:opacity-40 uppercase"
        />
        <div className="relative group mt-2">
          <div className="absolute inset-0 bg-white/20 blur-xl rounded-full opacity-0 group-hover:opacity-100 transition-opacity" />
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            type="submit"
            disabled={loading}
            className="relative w-full bg-white text-black py-4 rounded-full text-[10px] font-black uppercase tracking-[0.2em] transition-all disabled:opacity-40 flex items-center justify-center gap-2"
          >
            {loading ? <Loader2 className="animate-spin" size={14} /> : (showCheckStatus ? "Check My Rank" : "Join Waitlist")}
          </motion.button>
        </div>
      </form>

      <button
        onClick={() => {
          setShowCheckStatus(!showCheckStatus);
          setError("");
        }}
        className="text-[9px] font-black text-white/20 uppercase tracking-[0.2em] hover:text-white/40 transition-colors mt-2"
      >
        {showCheckStatus ? "← Back to signup" : "Already joined? Check your rank"}
      </button>

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
