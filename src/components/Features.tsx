import { motion } from "framer-motion";
import { MINECRAFT_ICONS } from "../lib/minecraft-icons";

const features = [
  {
    icon: MINECRAFT_ICONS.features.vanilla,
    title: "Vanilla Engine",
    detail: "Full Java Edition compatibility. No compromises on performance.",
    tag: "STABLE",
    color: "bg-mc-green"
  },
  {
    icon: MINECRAFT_ICONS.features.latency,
    title: "Zero-Latency",
    detail: "Proprietary tunnel tech. No port forwarding required.",
    tag: "LIVE",
    color: "bg-red-600"
  },
  {
    icon: MINECRAFT_ICONS.features.ddos,
    title: "DDoS Hardened",
    detail: "Enterprise-grade protection for your mobile hosted server.",
    tag: "SECURE",
    color: "bg-zinc-600"
  },
  {
    icon: MINECRAFT_ICONS.features.arm64,
    title: "ARM64 Optimized",
    detail: "Custom JRE distribution tailored for mobile architecture.",
    tag: "ARM64",
    color: "bg-purple-600"
  },
  {
    icon: MINECRAFT_ICONS.features.social,
    title: "Infinite Slots",
    detail: "Invite as many friends as your hardware can handle.",
    tag: "SOCIAL",
    color: "bg-mc-green"
  },
  {
    icon: MINECRAFT_ICONS.features.plugin,
    title: "Plugin Factory",
    detail: "Drag and drop .jar files. Hot-reload without downtime.",
    tag: "EXTENSIBLE",
    color: "bg-orange-600"
  },
];

export default function Features() {
  return (
    <section id="features" className="py-32 px-6 bg-[#080808] border-t-8 border-[#1a1a1a] relative overflow-hidden">
      {/* Decorative Grid background */}
      <div className="absolute inset-0 z-0 opacity-[0.05] pointer-events-none">
        <div className="absolute inset-0 bg-[radial-gradient(#ffffff_1px,transparent_1px)] [background-size:64px_64px]" />
      </div>

      <div className="max-w-7xl mx-auto relative z-10">
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-24 gap-8">
           <div className="max-w-xl">
             <div className="flex items-center gap-4 mb-8">
                <div className="w-12 h-1 bg-[#4CAF50]" />
                <span className="text-[8px] md:text-[10px] font-minecraft uppercase tracking-wider text-[#4CAF50]">Host Infrastructure</span>
             </div>
             <h2 className="text-3xl md:text-5xl font-minecraft text-white leading-tight uppercase">
                Powerful <br/> <span className="text-white/20">Core Engine</span>
             </h2>
           </div>
           <p className="text-white/40 text-[10px] md:text-xs font-mono uppercase tracking-widest max-w-xs leading-relaxed">
             PocketCraft turns your mobile hardware into a high-performance Minecraft backbone.
           </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8 md:gap-12">
          {features.map((f, i) => {
            return (
              <motion.div
                key={i}
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6, delay: i * 0.1 }}
                className="group relative"
              >
                <div className="mc-card bg-[#c6c6c6] relative h-full flex flex-col items-start transition-all duration-75 active:translate-y-1 active:translate-x-1 shadow-[8px_8px_0px_rgba(0,0,0,0.5)] border-4 border-t-white border-l-white border-r-[#555] border-b-[#555]">
                  <div className="flex justify-between w-full mb-6 p-2">
                    {/* Item Slot (Dark) */}
                    <div className="w-16 h-16 md:w-20 md:h-20 bg-[#373737] border-4 border-t-black border-l-black border-r-[#8b8b8b] border-b-[#8b8b8b] flex items-center justify-center shadow-inner overflow-hidden">
                       <img 
                        src={f.icon} 
                        alt={f.title} 
                        className="w-10 h-10 md:w-12 md:h-12 image-pixelated group-hover:scale-110 transition-transform"
                        onError={(e) => {
                          e.currentTarget.onerror = null;
                          e.currentTarget.src = "/grass-block.png";
                        }}
                      />
                    </div>
                    <div className="text-[7px] font-minecraft uppercase border-4 border-black/10 px-2 py-1 self-start opacity-40 group-hover:opacity-100 group-hover:border-black/30 transition-all text-[#333]">
                      {f.tag}
                    </div>
                  </div>
                  
                  <div className="px-3 md:px-5 pb-4 md:pb-6 flex-1 w-full flex flex-col">
                    <h3 className="text-[11px] md:text-sm font-minecraft text-[#333] mb-4 uppercase leading-tight group-hover:text-black transition-colors min-h-[2.5em]">{f.title}</h3>
                    <p className="text-[10px] md:text-[12px] text-[#555] font-mono uppercase tracking-wider leading-relaxed mb-8 flex-1">
                      {f.detail}
                    </p>
                    
                    <div className="pt-4 border-t-4 border-black/10 w-full flex justify-between items-center mt-auto">
                      <div className="flex gap-2 text-[8px] font-minecraft text-[#4CAF50] items-center">
                         <div className="w-2.5 h-2.5 bg-mc-green border-2 border-white/20 animate-pulse shadow-[0_0_8px_#4CAF50]" />
                         <span className="opacity-40 group-hover:opacity-100 transition-all tracking-tighter">SYS_READY</span>
                      </div>
                      <button className="text-[8px] font-minecraft uppercase text-[#777] group-hover:text-black transition-all hover:bg-black/5 px-2 py-1">&gt; INFO</button>
                    </div>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
