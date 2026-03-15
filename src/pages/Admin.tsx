import { useState, FormEvent } from "react";
import { motion } from "motion/react";
import { Download, Users, Clock, Trophy, Lock, Loader2 } from "lucide-react";

export default function Admin() {
  const [password, setPassword] = useState("");
  const [authenticated, setAuthenticated] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [stats, setStats] = useState<any>(null);

  const handleLogin = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    try {
      const res = await fetch("/api/admin/stats", {
        headers: { Authorization: `Bearer ${password}` },
      });

      if (!res.ok) {
        throw new Error("Invalid password");
      }

      const data = await res.json();
      setStats(data);
      setAuthenticated(true);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const exportCSV = () => {
    if (!stats || !stats.allUsers) return;

    const headers = ["Email", "Signup Time", "Referral Code", "Referred By", "Referral Count"];
    const csvContent = [
      headers.join(","),
      ...stats.allUsers.map((u: any) => [
        u.email,
        new Date(u.created_at).toLocaleString(),
        u.referral_code,
        u.referred_by || "",
        u.referral_count
      ].join(","))
    ].join("\n");

    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const link = document.createElement("a");
    const url = URL.createObjectURL(blob);
    link.setAttribute("href", url);
    link.setAttribute("download", `pocketcraft_waitlist_${new Date().toISOString().split("T")[0]}.csv`);
    link.style.visibility = "hidden";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  if (!authenticated) {
    return (
      <div className="min-h-screen bg-[#FAFAFA] flex items-center justify-center p-6 text-stone-900 font-sans selection:bg-emerald-500/20 selection:text-emerald-900">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-white border border-stone-200/60 shadow-sm rounded-[2rem] p-8 md:p-10 max-w-md w-full"
        >
          <div className="w-16 h-16 bg-emerald-50 rounded-2xl flex items-center justify-center mx-auto mb-6">
            <Lock className="w-8 h-8 text-emerald-500" />
          </div>
          
          <h2 className="text-2xl font-medium text-center mb-6 tracking-tight">Admin Login</h2>
          
          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <input
                type="password"
                required
                placeholder="Enter admin password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full bg-stone-50 border border-stone-200 rounded-2xl px-5 py-4 text-stone-900 placeholder:text-stone-400 focus:outline-none focus:bg-white focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 transition-all"
              />
            </div>
            
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-stone-900 hover:bg-stone-800 text-white py-4 rounded-2xl font-medium transition-all disabled:opacity-50 flex items-center justify-center gap-2 shadow-sm hover:shadow-md"
            >
              {loading ? <Loader2 className="animate-spin" size={20} /> : "Login"}
            </button>
            
            {error && <p className="text-red-500 text-sm text-center">{error}</p>}
          </form>
        </motion.div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#FAFAFA] text-stone-900 font-sans p-6 md:p-12 selection:bg-emerald-500/20 selection:text-emerald-900">
      <div className="max-w-6xl mx-auto">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6 mb-12">
          <div>
            <h1 className="text-3xl font-medium mb-2 tracking-tight">Waitlist Dashboard</h1>
            <p className="text-stone-500">Manage your PocketCraft waitlist signups.</p>
          </div>
          
          <button
            onClick={exportCSV}
            className="bg-white hover:bg-stone-50 text-stone-700 px-5 py-2.5 rounded-full font-medium transition-colors flex items-center gap-2 border border-stone-200 shadow-sm hover:shadow-md"
          >
            <Download size={18} />
            Export CSV
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-12">
          <div className="bg-white border border-stone-200/60 rounded-3xl p-6 flex items-center gap-4 shadow-sm">
            <div className="w-12 h-12 bg-blue-50 rounded-2xl flex items-center justify-center">
              <Users className="text-blue-500" size={24} />
            </div>
            <div>
              <p className="text-stone-500 text-sm font-medium">Total Signups</p>
              <p className="text-3xl font-semibold">{stats?.total || 0}</p>
            </div>
          </div>
          
          <div className="bg-white border border-stone-200/60 rounded-3xl p-6 flex items-center gap-4 shadow-sm">
            <div className="w-12 h-12 bg-emerald-50 rounded-2xl flex items-center justify-center">
              <Clock className="text-emerald-500" size={24} />
            </div>
            <div>
              <p className="text-stone-500 text-sm font-medium">Last 24 Hours</p>
              <p className="text-3xl font-semibold">{stats?.last24h || 0}</p>
            </div>
          </div>
          
          <div className="bg-white border border-stone-200/60 rounded-3xl p-6 flex items-center gap-4 shadow-sm">
            <div className="w-12 h-12 bg-amber-50 rounded-2xl flex items-center justify-center">
              <Trophy className="text-amber-500" size={24} />
            </div>
            <div>
              <p className="text-stone-500 text-sm font-medium">Top Referrer Count</p>
              <p className="text-3xl font-semibold">{stats?.topReferrers?.[0]?.referral_count || 0}</p>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-2 bg-white border border-stone-200/60 rounded-[2rem] shadow-sm overflow-hidden">
            <div className="p-6 border-b border-stone-100">
              <h2 className="text-xl font-medium tracking-tight">Recent Signups</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-stone-50/50 text-stone-500 border-b border-stone-100">
                  <tr>
                    <th className="px-6 py-4 font-medium">Email</th>
                    <th className="px-6 py-4 font-medium">Date</th>
                    <th className="px-6 py-4 font-medium">Referral Code</th>
                    <th className="px-6 py-4 font-medium">Referred By</th>
                    <th className="px-6 py-4 font-medium">Referrals</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100">
                  {stats?.allUsers?.slice(0, 20).map((user: any) => (
                    <tr key={user.id} className="hover:bg-stone-50/50 transition-colors">
                      <td className="px-6 py-4 text-stone-900 font-medium">{user.email}</td>
                      <td className="px-6 py-4 text-stone-500">{new Date(user.created_at).toLocaleDateString()}</td>
                      <td className="px-6 py-4 text-stone-500 font-mono text-xs">{user.referral_code}</td>
                      <td className="px-6 py-4 text-stone-500 font-mono text-xs">{user.referred_by || "-"}</td>
                      <td className="px-6 py-4 text-emerald-600 font-semibold">{user.referral_count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="bg-white border border-stone-200/60 rounded-[2rem] shadow-sm overflow-hidden h-fit">
            <div className="p-6 border-b border-stone-100">
              <h2 className="text-xl font-medium flex items-center gap-2 tracking-tight">
                <Trophy size={20} className="text-amber-500" />
                Leaderboard
              </h2>
            </div>
            <div className="p-6">
              <div className="space-y-4">
                {stats?.topReferrers?.map((user: any, index: number) => (
                  <div key={user.id} className="flex items-center justify-between p-3 bg-stone-50 rounded-2xl border border-stone-100">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 bg-white border border-stone-200 rounded-xl flex items-center justify-center font-medium text-sm text-stone-500">
                        {index + 1}
                      </div>
                      <div className="text-sm">
                        <p className="text-stone-900 font-medium truncate max-w-[120px]" title={user.email}>
                          {user.email.split("@")[0]}...
                        </p>
                      </div>
                    </div>
                    <div className="bg-emerald-100 text-emerald-700 px-3 py-1 font-medium text-xs rounded-full">
                      {user.referral_count} ref
                    </div>
                  </div>
                ))}
                
                {(!stats?.topReferrers || stats.topReferrers.length === 0) && (
                  <p className="text-center text-stone-500 text-sm py-4">No referrals yet.</p>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
