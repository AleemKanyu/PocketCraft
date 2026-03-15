export default function Footer() {
  return (
    <footer className="py-20 px-4 sm:px-6 border-t border-white/5 bg-[#080808]">
      <div className="max-w-4xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-8">
        <div className="flex items-center gap-2.5">
          <div className="w-7 h-7 rounded-lg bg-white flex items-center justify-center">
            <div className="w-2.5 h-2.5 bg-black rounded-sm" />
          </div>
          <span className="font-bold text-base text-white tracking-tight">PocketCraft</span>
        </div>
        
        <p className="text-white/20 text-[10px] uppercase font-bold tracking-[0.2em] text-center sm:text-left">
          Made for players, by players
        </p>
        
        <div className="flex items-center gap-8 text-[10px] font-bold uppercase tracking-widest text-white/20">
          <a href="#" className="hover:text-white transition-colors">Privacy</a>
          <a href="#" className="hover:text-white transition-colors">Contact</a>
        </div>
      </div>
      <div className="max-w-4xl mx-auto mt-12 pt-8 border-t border-white/[0.02] text-center text-white/10 text-[9px] font-bold uppercase tracking-[0.3em]">
        © {new Date().getFullYear()} POCKETCRAFT. THE FUTURE IS MOBILE.
      </div>
    </footer>
  );
}
