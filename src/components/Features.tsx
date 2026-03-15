import { motion } from "motion/react";
import { Server, Zap, Users, CreditCard, Package } from "lucide-react";

const features = [
  {
    icon: Server,
    title: "Real Java Server",
    detail: "Actual PaperMC. Full plugins. On your phone.",
  },
  {
    icon: Zap,
    title: "One-Tap Launch",
    detail: "No setup. No port forwarding. Tap and go.",
  },
  {
    icon: Users,
    title: "Invite Friends Instantly",
    detail: "Send a link. They join. That's it.",
  },
  {
    icon: CreditCard,
    title: "Free Forever",
    detail: "No credit card. No hidden fees. Ever.",
  },
  {
    icon: Package,
    title: "Plugin Support",
    detail: "EssentialsX, WorldEdit, LuckPerms — all work.",
  },
];

export default function Features() {
  return (
    <section className="py-24 px-4 sm:px-6 bg-[#080808] border-t border-white/5">
      <div className="max-w-4xl mx-auto">
        <div className="mb-20">
          <div className="w-6 h-px bg-white/40 mb-5 rounded-full" />
          <h2 className="text-2xl sm:text-3xl font-black text-white tracking-tight leading-tight uppercase">Why PocketCraft?</h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-8 gap-y-12">
          {features.map((f, i) => {
            const Icon = f.icon;
            return (
              <motion.div
                key={i}
                initial={{ opacity: 0, y: 14 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.35, delay: i * 0.07 }}
                className="flex gap-5 items-start"
              >
                <div className="mt-0.5 w-9 h-9 rounded-xl bg-white/5 border border-white/5 flex items-center justify-center shrink-0">
                  <Icon className="w-4 h-4 text-white/50" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white mb-1.5 tracking-tight uppercase">{f.title}</h3>
                  <p className="text-xs text-white/30 leading-relaxed font-medium">{f.detail}</p>
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
