import React, { useEffect, useState, useRef } from "react";

export default function PhoneModel() {
  const [autoRotation, setAutoRotation] = useState(0);
  const [dragRotation, setDragRotation] = useState(-15);
  const [bobOffset, setBobOffset] = useState(0);

  // Dragging state
  const [isDragging, setIsDragging] = useState(false);
  const dragStartInfo = useRef({ x: 0, y: 0, initialRotationX: 0, initialRotationY: 0 });
  const [rotationX, setRotationX] = useState(5);

  useEffect(() => {
    let animationId: number;
    let startTime = performance.now();

    const animate = (time: number) => {
      const elapsed = time - startTime;
      const frame = elapsed * 0.002; 
      
      const hoverFactor = isDragging ? 0 : 1;
      
      setAutoRotation(Math.sin(frame * 0.5) * 10 * hoverFactor); 
      setBobOffset(Math.sin(frame * 1.5) * 10 * hoverFactor); 
      
      animationId = requestAnimationFrame(animate);
    };
    
    animationId = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(animationId);
  }, [isDragging]);

  const handlePointerDown = (e: React.PointerEvent) => {
    setIsDragging(true);
    dragStartInfo.current = {
      x: e.clientX,
      y: e.clientY,
      initialRotationY: dragRotation,
      initialRotationX: rotationX,
    };
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (isDragging) {
      const deltaX = e.clientX - dragStartInfo.current.x;
      const deltaY = e.clientY - dragStartInfo.current.y;
      setDragRotation(dragStartInfo.current.initialRotationY + deltaX * 0.5);
      setRotationX(dragStartInfo.current.initialRotationX - deltaY * 0.5);
    }
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    setIsDragging(false);
    (e.target as HTMLElement).releasePointerCapture(e.pointerId);
  };

  const totalRotationY = dragRotation + autoRotation;

  // Phone dimensions
  const width = 280;
  const height = 580;
  const depth = 20;

  // Generating edge layers for pseudo-3D thickness (very smooth solid body)
  const layers = [];
  for (let i = 0; i <= depth; i += 1) {
    layers.push(
      <div key={i} style={{
        position: 'absolute',
        width: '100%',
        height: '100%',
        backgroundColor: i === 0 || i === depth ? '#1f241f' : '#2a312a', // Edge color
        borderRadius: '36px',
        transform: `translateZ(${(i - depth/2)}px)`,
        boxShadow: i === Math.floor(depth/2) ? '0 0 20px rgba(0,0,0,0.5)' : 'none',
        pointerEvents: 'none'
      }} />
    );
  }

  return (
    <div 
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        height: "100%",
        minHeight: 600,
        perspective: 1500,
        perspectiveOrigin: "50% 50%",
        cursor: isDragging ? "grabbing" : "grab",
        touchAction: "none"
      }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
    >
      <div style={{
        width,
        height,
        transformStyle: "preserve-3d",
        transform: `translateY(${bobOffset}px) rotateX(${rotationX}deg) rotateY(${totalRotationY}deg)`,
        transition: isDragging ? "none" : "transform 0.1s linear",
        position: "relative",
      }}>
        
        {/* Edge layers */}
        {layers}

        {/* Back Face */}
        <div style={{
          position: 'absolute',
          width: '100%',
          height: '100%',
          backgroundColor: '#111311',
          borderRadius: '36px',
          transform: `translateZ(${-depth/2 - 1}px) rotateY(180deg)`,
          border: '2px solid #2a312a',
          boxSizing: 'border-box',
          pointerEvents: 'none',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center'
        }}>
          {/* Camera Module */}
          <div className="absolute top-6 right-6 w-20 h-24 bg-[#1a1f1a] rounded-[24px] border border-[#333] shadow-inner p-2 flex flex-col gap-2">
             <div className="w-7 h-7 rounded-full bg-black border border-[#222] shadow-[inset_0_0_10px_rgba(255,255,255,0.1)] mx-auto mt-1" />
             <div className="w-7 h-7 rounded-full bg-black border border-[#222] shadow-[inset_0_0_10px_rgba(255,255,255,0.1)] mx-auto" />
             <div className="w-3 h-3 rounded-full bg-[#111] mx-auto mt-0.5 flex items-center justify-center">
                <div className="w-2 h-2 rounded-full bg-yellow-100/80" />
             </div>
          </div>
          {/* Logo on Back */}
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-[#4CAF50] to-[#81C784] flex items-center justify-center shadow-lg opacity-80 mt-12">
             <div className="w-5 h-5 bg-white rounded-md" />
          </div>
        </div>

        {/* Front Face (Screen UI) */}
        <div style={{
          position: 'absolute',
          width: '100%',
          height: '100%',
          backgroundColor: '#000000',
          borderRadius: '36px',
          transform: `translateZ(${depth/2 + 1}px)`,
          border: '10px solid #141414', // Bezel
          boxSizing: 'border-box',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          pointerEvents: 'none'
        }}>
               {/* Screen content (App UI Mockup) */}
               <div className="w-full h-full bg-[#080808] text-white flex flex-col relative select-none">
                  {/* Dynamic Island / Notch area */}
                  <div className="absolute top-3 left-1/2 -translate-x-1/2 w-28 h-7 bg-black rounded-full z-20 shadow-sm" />
                  
                  {/* App UI Header */}
                  <div className="pt-12 pb-4 px-5 border-b border-white/5 flex justify-between items-center bg-black/80 backdrop-blur-md">
                     <div className="flex items-center gap-2">
                        <div className="w-6 h-6 rounded-md bg-white flex items-center justify-center">
                           <div className="w-2 h-2 bg-black rounded-sm" />
                        </div>
                        <span className="font-bold text-xs tracking-tight">POCKETCRAFT</span>
                     </div>
                     <div className="flex items-center gap-3">
                         <div className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center border border-white/10 overflow-hidden">
                            <div className="w-full h-full bg-[url('https://api.mineatar.io/face/steve?scale=4')] bg-cover" style={{ imageRendering: 'pixelated' }} />
                         </div>
                     </div>
                  </div>
    
                  {/* App UI Body */}
                  <div className="flex-1 p-5 flex flex-col gap-5 bg-gradient-to-b from-[#080808] to-black">
                     <div>
                        <div className="text-xl font-bold tracking-tight text-white leading-tight">My Server</div>
                        <div className="text-[10px] uppercase font-bold text-white/30 mt-1 tracking-widest">1.21.1 • PaperMC</div>
                     </div>  
                                  {/* Status Card */}
                     <div className="bg-white/5 rounded-2xl p-5 border border-white/5 shadow-2xl">
                        <div className="flex justify-between items-center mb-5">
                           <div className="text-[10px] text-white/20 font-bold uppercase tracking-wider">Status</div>
                           <div className="flex items-center gap-2 px-2 py-0.5 bg-red-500/10 rounded-full border border-red-500/20">
                              <div className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
                              <span className="text-[10px] font-bold text-red-400 uppercase">Offline</span>
                           </div>
                        </div>
                        <div className="flex justify-between items-center">
                           <div className="text-[10px] text-white/20 font-bold uppercase tracking-wider">Players</div>
                           <div className="text-xs font-bold text-white">0 / 20</div>
                        </div>
                        <div className="flex justify-between items-center mt-4">
                           <div className="text-[10px] text-white/20 font-bold uppercase tracking-wider">Storage</div>
                           <div className="text-xs font-bold text-white">2.4 GB / 128 GB</div>
                        </div>
                        <div className="w-full h-1 bg-white/5 rounded-full mt-3 overflow-hidden">
                           <div className="w-[15%] h-full bg-white/40" />
                        </div>
                     </div>

                     {/* Console preview */}
                     <div className="bg-black/40 rounded-xl p-4 border border-white/5 font-mono text-[9px] text-white/20 flex flex-col gap-1.5 h-36 overflow-hidden relative">
                        <div className="flex items-center gap-1.5 text-white/10 mb-1 border-b border-white/5 pb-2">
                           <div className="w-1.5 h-1.5 rounded-full bg-white/5" />
                           <div className="w-1.5 h-1.5 rounded-full bg-white/5" />
                           <div className="w-1.5 h-1.5 rounded-full bg-white/5" />
                           <span className="ml-1 uppercase text-[8px] tracking-widest font-bold">TERMINAL</span>
                        </div>
                        <div>&gt; _</div>
                        <div className="absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-t from-black to-transparent" />
                     </div>

                     <div className="mt-auto pt-2">
                        <div className="w-full bg-white text-black py-4 rounded-full font-black text-[10px] tracking-widest text-center flex items-center justify-center gap-2">
                           <div className="w-0 h-0 border-t-[4px] border-t-transparent border-l-[6px] border-l-black border-b-[4px] border-b-transparent ml-1" />
                           START SERVER
                        </div>
                     </div>
                  </div>
    
                  {/* Bottom Nav Mockup */}
                  <div className="h-[72px] pb-2 border-t border-white/5 bg-black/90 backdrop-blur-xl flex justify-around items-center px-4">
                     {[1,2,3,4].map(i => (
                        <div key={i} className={`w-12 h-12 flex flex-col items-center justify-center gap-1.5 ${i===1 ? 'text-white' : 'text-white/20'}`}>
                           <div className={`w-4 h-4 rounded-full ${i===1 ? 'bg-white' : 'border border-white/20'}`} />
                        </div>
                     ))}
                  </div>
               </div>
        </div>

      </div>
      
      {/* Bottom Shadow for grounding */}
      <div style={{
        position: "absolute",
        bottom: "0%",
        left: "50%",
        transform: `translateX(-50%) translateY(${bobOffset * 0.4}px) rotateX(75deg)`,
        width: 220,
        height: 60,
        background: "radial-gradient(ellipse, rgba(0,0,0,0.6) 0%, transparent 70%)",
        pointerEvents: "none",
      }} />
    </div>
  );
}
