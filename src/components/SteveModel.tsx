import React, { useEffect, useState, useRef } from "react";

// Minecraft Steve 3D model built entirely with CSS 3D transforms
export default function SteveModel() {
  const [autoRotation, setAutoRotation] = useState(0);
  const [dragRotation, setDragRotation] = useState(-20); // Initial angle
  const [bobOffset, setBobOffset] = useState(0);
  const [armSwing, setArmSwing] = useState(0);
  const [legSwing, setLegSwing] = useState(0);
  const [isHovered, setIsHovered] = useState(false);

  // Dragging state
  const [isDragging, setIsDragging] = useState(false);
  const dragStartInfo = useRef({ x: 0, initialRotation: 0 });

  useEffect(() => {
    let animationId: number;
    let startTime = performance.now();

    const animate = (time: number) => {
      const elapsed = time - startTime;
      // Use time to calculate frame perfectly smoothly regardless of refresh rate
      const frame = elapsed * 0.003; 
      
      const hoverFactor = isDragging ? 0 : 1; // Stop walking only when dragging/rotating
      
      // Continuous walk cycle
      setAutoRotation(Math.sin(frame * 0.4) * 15); // Sway side to side slowly
      
      // Bobbing syncs with leg swings (frequency is double the leg swing)
      setBobOffset(Math.abs(Math.sin(frame * 1.5)) * 10 * hoverFactor); 
      
      // Arm and leg swings opposite each other
      setArmSwing(Math.sin(frame * 1.5) * 40 * hoverFactor); 
      setLegSwing(Math.sin(frame * 1.5) * 35 * hoverFactor);
      
      animationId = requestAnimationFrame(animate);
    };
    
    animationId = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(animationId);
  }, [isDragging]);

  const handlePointerDown = (e: React.PointerEvent) => {
    setIsDragging(true);
    dragStartInfo.current = {
      x: e.clientX,
      initialRotation: dragRotation,
    };
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (isDragging) {
      const deltaX = e.clientX - dragStartInfo.current.x;
      setDragRotation(dragStartInfo.current.initialRotation + deltaX * 0.5);
    }
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    setIsDragging(false);
    (e.target as HTMLElement).releasePointerCapture(e.pointerId);
  };

  const SKIN = "#b88a68";         
  const SKIN_SHADOW = "#a37554";  
  const SKIN_TOP = "#c49875";     
  const HAIR = "#302219";         
  const HAIR_SIDE = "#201610";
  const SHIRT = "#00a298";        
  const SHIRT_SIDE = "#00847b";
  const SHIRT_TOP = "#00b5aa";
  const PANTS = "#3e3e9d";        
  const PANTS_SIDE = "#2e2e7b";
  const PANTS_TOP = "#4b4ba5";
  const SHOE = "#404040";         
  const SHOE_SIDE = "#2b2b2b";
  const SHOE_TOP = "#505050";
  const HEADPHONE = "#111111";
  const HEADPHONE_ACCENT = "#ffaa00";

  const scale = 6.5;

  // A CSS pattern that adds a subtle 1x1 pixel checkerboard noise to all blocks
  const pixelOverlay = {
    backgroundImage: `linear-gradient(45deg, rgba(0,0,0,0.06) 25%, transparent 25%, transparent 75%, rgba(0,0,0,0.06) 75%, rgba(0,0,0,0.06)), 
                      linear-gradient(45deg, rgba(0,0,0,0.06) 25%, transparent 25%, transparent 75%, rgba(0,0,0,0.06) 75%, rgba(0,0,0,0.06))`,
    backgroundPosition: `0 0, ${scale}px ${scale}px`,
    backgroundSize: `${scale * 2}px ${scale * 2}px`,
    imageRendering: "pixelated" as const,
  };

  const Block = ({
    w, h, d, colors,
    style = {},
    overlay = true
  }: {
    w: number; h: number; d: number;
    colors: { front: string; top: string; right: string; left: string; back?: string; bottom?: string };
    style?: React.CSSProperties;
    overlay?: boolean;
  }) => {
    const pw = w * scale;
    const ph = h * scale;
    const pd = d * scale;
    const ov = overlay ? pixelOverlay : {};
    return (
      <div style={{
        position: "relative",
        width: pw,
        height: ph,
        transformStyle: "preserve-3d",
        ...style
      }}>
        {/* Front */}
        <div style={{
          position: "absolute", width: pw, height: ph,
          backgroundColor: colors.front,
          transform: `translateZ(${pd / 2}px)`,
          ...ov,
        }} />
        {/* Back */}
        <div style={{
          position: "absolute", width: pw, height: ph,
          backgroundColor: colors.back || colors.front,
          transform: `rotateY(180deg) translateZ(${pd / 2}px)`,
          filter: colors.back ? "none" : "brightness(0.7)",
          ...ov,
        }} />
        {/* Top */}
        <div style={{
          position: "absolute", width: pw, height: pd,
          backgroundColor: colors.top,
          transform: `rotateX(90deg) translateZ(-${ph - pd / 2}px)`,
          ...ov,
        }} />
        {/* Bottom */}
        <div style={{
          position: "absolute", width: pw, height: pd,
          backgroundColor: colors.bottom || colors.top,
          transform: `rotateX(-90deg) translateZ(${pd / 2}px)`,
          filter: colors.bottom ? "none" : "brightness(0.7)",
          ...ov,
        }} />
        {/* Right */}
        <div style={{
          position: "absolute", width: pd, height: ph,
          backgroundColor: colors.right,
          transform: `rotateY(90deg) translateZ(${pw - pd / 2}px)`,
          ...ov,
        }} />
        {/* Left */}
        <div style={{
          position: "absolute", width: pd, height: ph,
          backgroundColor: colors.left,
          transform: `rotateY(-90deg) translateZ(${pd / 2}px)`,
          ...ov,
        }} />
      </div>
    );
  };

  // 3D Diamond Pickaxe model using CSS blocks
  const DiamondPickaxe = () => {
    return (
      <div style={{
        position: "absolute",
        top: 6 * scale, // Middle of the forearm
        left: 2 * scale, // Center of hand width
        transformOrigin: "bottom center",
        transform: `translateZ(${3 * scale}px) rotateX(-90deg) rotateY(20deg) rotateZ(30deg) translateY(${2 * scale}px)`,
        transformStyle: "preserve-3d",
      }}>
        {/* Handle - Stick */}
        <div style={{ position: "absolute", top: -8 * scale, left: -0.5 * scale, transformStyle: "preserve-3d" }}>
            <Block w={1} h={12} d={1} colors={{ front: "#8a5c33", top: "#a67141", right: "#5c3d22", left: "#5c3d22", back: "#5c3d22", bottom: "#422813" }} overlay={false} />
        </div>
        {/* Pickaxe Head Base */}
        <div style={{ position: "absolute", top: -8 * scale, left: -4 * scale, transformStyle: "preserve-3d" }}>
            <Block w={8} h={1} d={1} colors={{ front: "#33EBC2", top: "#5cffe3", right: "#21b392", left: "#21b392", back: "#33EBC2", bottom: "#21b392" }} overlay={false} />
        </div>
        {/* Pickaxe Head Left Drop */}
        <div style={{ position: "absolute", top: -7 * scale, left: -4 * scale, transformStyle: "preserve-3d" }}>
            <Block w={1} h={2} d={1} colors={{ front: "#33EBC2", top: "#5cffe3", right: "#21b392", left: "#21b392", back: "#33EBC2", bottom: "#21b392" }} overlay={false} />
        </div>
        {/* Pickaxe Head Right Drop */}
        <div style={{ position: "absolute", top: -7 * scale, left: 3 * scale, transformStyle: "preserve-3d" }}>
            <Block w={1} h={2} d={1} colors={{ front: "#33EBC2", top: "#5cffe3", right: "#21b392", left: "#21b392", back: "#33EBC2", bottom: "#21b392" }} overlay={false} />
        </div>
        {/* Head center connection detail */}
        <div style={{ position: "absolute", top: -9 * scale, left: -1 * scale, transformStyle: "preserve-3d" }}>
            <Block w={2} h={1} d={1} colors={{ front: "#33EBC2", top: "#5cffe3", right: "#21b392", left: "#21b392", back: "#33EBC2", bottom: "#21b392" }} overlay={false} />
        </div>
      </div>
    );
  };

  const Arm = ({ side, swing }: { side: "left" | "right"; swing: number }) => {
    return (
      <div style={{
        position: "absolute",
        top: 0,
        [side === "right" ? "right" : "left"]: -4 * scale,
        transformOrigin: "top center",
        transform: `rotateX(${swing}deg)`,
        transformStyle: "preserve-3d",
      }}>
        {/* Short sleeve */}
        <Block w={4} h={4} d={4} colors={{ front: SHIRT, top: SHIRT_TOP, right: SHIRT_SIDE, left: SHIRT_SIDE }} />
        {/* Forearm skin */}
        <div style={{ position: "absolute", top: 4 * scale, transformStyle: "preserve-3d" }}>
          <Block w={4} h={8} d={4} colors={{ front: SKIN, top: SKIN_TOP, right: SKIN_SHADOW, left: SKIN_SHADOW }} />
          {side === "right" && <DiamondPickaxe />}
        </div>
      </div>
    );
  };

  const Leg = ({ side, swing }: { side: "left" | "right"; swing: number }) => {
    return (
      <div style={{
        transformOrigin: "top center",
        transform: `rotateX(${swing}deg)`,
        transformStyle: "preserve-3d",
        position: "relative",
      }}>
        <Block w={4} h={9} d={4} colors={{ front: PANTS, top: PANTS_TOP, right: PANTS_SIDE, left: PANTS_SIDE }} />
        {/* Shoes */}
        <div style={{ position: "absolute", top: 9 * scale, transformStyle: "preserve-3d" }}>
          <Block w={4} h={3} d={4} colors={{ front: SHOE, top: SHOE_TOP, right: SHOE_SIDE, left: SHOE_SIDE }} />
        </div>
      </div>
    );
  };

  // 3D Headphones
  const Headphones = () => {
    return (
      <div style={{ position: "absolute", top: 0, left: 0, transformStyle: "preserve-3d" }}>
        {/* Top Band */}
        <div style={{ position: "absolute", top: -1 * scale, left: -0.5 * scale, transformStyle: "preserve-3d" }}>
           <Block w={9} h={1} d={3} colors={{ front: HEADPHONE, top: HEADPHONE, left: HEADPHONE, right: HEADPHONE }} overlay={false} />
        </div>
        {/* Left Earcup */}
        <div style={{ position: "absolute", top: -1 * scale, left: -1.5 * scale, transformStyle: "preserve-3d" }}>
           <Block w={1.5} h={5} d={3} colors={{ front: HEADPHONE, top: HEADPHONE, left: HEADPHONE_ACCENT, right: HEADPHONE }} overlay={false} />
        </div>
        {/* Right Earcup */}
        <div style={{ position: "absolute", top: -1 * scale, left: 8 * scale, transformStyle: "preserve-3d" }}>
           <Block w={1.5} h={5} d={3} colors={{ front: HEADPHONE, top: HEADPHONE, right: HEADPHONE_ACCENT, left: HEADPHONE }} overlay={false} />
        </div>
      </div>
    );
  };

  const totalRotation = dragRotation + autoRotation;

  return (
    <div 
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        height: "100%",
        minHeight: 400,
        perspective: 1000,
        perspectiveOrigin: "50% 40%",
        cursor: isDragging ? "grabbing" : "grab",
        touchAction: "none"
      }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      {/* Floating Steve */}
      <div style={{
        transformStyle: "preserve-3d",
        transform: `rotateY(${totalRotation}deg) translateY(${bobOffset}px)`,
        transition: isDragging ? "none" : "transform 0.1s linear",
        position: "relative",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
      }}>
        {/* HEAD */}
        <div style={{ position: "relative", transformStyle: "preserve-3d", marginBottom: 0 }}>
          <Block w={8} h={8} d={8} colors={{ front: HAIR, top: HAIR, right: HAIR_SIDE, left: HAIR_SIDE }} />
          {/* Face overlay */}
          <div style={{
            position: "absolute",
            top: 1.5 * scale,
            left: 0 * scale,
            width: 8 * scale,
            height: 6.5 * scale,
            transform: `translateZ(${4.1 * scale}px)`,
            display: "grid",
            gridTemplateColumns: "repeat(8, 1fr)",
            gridTemplateRows: "repeat(6, 1fr)",
            backgroundColor: SKIN,
            ...pixelOverlay, // Apply texture to face too
          }}>
            {/* Eyes */}
            <div style={{ gridColumn: "2/4", gridRow: "3/4", backgroundColor: "white" }} />
            <div style={{ gridColumn: "3/4", gridRow: "3/4", backgroundColor: "#3f3f9f" }} /> {/* Blue pupil */}
            
            <div style={{ gridColumn: "6/8", gridRow: "3/4", backgroundColor: "white" }} />
            <div style={{ gridColumn: "6/7", gridRow: "3/4", backgroundColor: "#3f3f9f" }} /> {/* Blue pupil */}

            {/* Nose */}
            <div style={{ gridColumn: "4/6", gridRow: "4/5", backgroundColor: SKIN_SHADOW }} />

            {/* Mouth / Beard */}
            <div style={{ gridColumn: "3/7", gridRow: "5/6", backgroundColor: "#5a3a29" }} />
            <div style={{ gridColumn: "4/6", gridRow: "5/6", backgroundColor: "#8a5840" }} />
          </div>
          <Headphones />
        </div>

        {/* TORSO */}
        <div style={{ position: "relative", transformStyle: "preserve-3d", marginTop: -1 }}>
          <Block w={8} h={12} d={4} colors={{ front: SHIRT, top: SHIRT_TOP, right: SHIRT_SIDE, left: SHIRT_SIDE }} />
          
          {/* Neck pixel detail */}
          <div style={{
             position: "absolute", top: 0, left: 3 * scale, width: 2 * scale, height: 1.5 * scale,
             backgroundColor: SKIN, transform: `translateZ(${2.1 * scale}px)`,
             ...pixelOverlay
          }} />

          {/* Arms attached to sides */}
          <Arm side="right" swing={armSwing} />
          <Arm side="left" swing={-armSwing} />
        </div>

        {/* LEGS */}
        <div style={{
          display: "flex",
          gap: 0,
          marginTop: -1,
          transformStyle: "preserve-3d",
        }}>
          <Leg side="left" swing={legSwing} />
          <Leg side="right" swing={-legSwing} />
        </div>
      </div>

      {/* Shadow */}
      <div style={{
        position: "absolute",
        bottom: "10%",
        left: "50%",
        transform: `translateX(-50%) translateY(${bobOffset * 0.3}px) rotateX(70deg)`,
        width: 120,
        height: 60,
        background: "radial-gradient(ellipse, rgba(0,0,0,0.3) 0%, transparent 60%)",
        pointerEvents: "none",
      }} />
    </div>
  );
}

