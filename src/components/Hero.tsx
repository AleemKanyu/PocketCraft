import { motion } from "framer-motion";

export default function Hero() {
  const apkUrl = "/api/apk/download";

  return (
    <section className="pt-40 pb-20 px-6 relative overflow-hidden min-h-[90vh] flex items-center">
      {/* Animated Background Elements */}
      <div className="absolute inset-0 z-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-[#58CC02]/10 rounded-full blur-[100px]" />
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-[#1CB0F6]/10 rounded-full blur-[100px]" />
        
        {/* Floating Pixels */}
        <motion.div
          animate={{ y: [0, -20, 0], rotate: [0, 10, 0] }}
          transition={{ duration: 5, repeat: Infinity, ease: "easeInOut" }}
          className="absolute top-1/3 left-1/5 w-12 h-12 bg-[#58CC02] rounded-2xl border-b-4 border-[#58A700] shadow-sm"
        />
        <motion.div
          animate={{ y: [0, 30, 0], rotate: [0, -15, 0] }}
          transition={{ duration: 7, repeat: Infinity, ease: "easeInOut", delay: 1 }}
          className="absolute bottom-1/3 right-1/5 w-16 h-16 bg-[#FFC800] rounded-2xl border-b-4 border-[#D9A900] shadow-sm"
        />
      </div>

      <div className="max-w-4xl mx-auto relative z-10 text-center">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
        >
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-2xl bg-white border-2 border-[#E5E5E5] text-[#58CC02] font-bold mb-8 shadow-[0_4px_0_0_#E5E5E5]">
            <span className="relative flex h-3 w-3">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#58CC02] opacity-75"></span>
              <span className="relative inline-flex rounded-full h-3 w-3 bg-[#58CC02]"></span>
            </span>
            COMING SOON TO ANDROID
          </div>
          
          <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight mb-6 leading-tight text-[#4B4B4B]">
            Host a Minecraft Server <br className="hidden md:block" />
            <span className="text-[#58CC02]">
              From Your Phone
            </span>
          </h1>
          
          <p className="text-xl text-[#777777] font-medium mb-12 max-w-2xl mx-auto leading-relaxed">
            No PC. No paid hosting. Just tap, start, and play with friends.
            Run a real Java Edition server directly on your Android device.
          </p>

          <div className="max-w-md mx-auto">
            <a
              href={apkUrl}
              download
              className="btn-21st inline-flex items-center justify-center px-10 py-5 text-xs uppercase tracking-[0.2em]"
            >
              Download APK
            </a>
            <p className="mt-6 text-sm font-bold text-[#AFB0B3] uppercase tracking-wider flex items-center justify-center gap-2">
              <span className="text-xl">🔥</span> Android release available now
            </p>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
