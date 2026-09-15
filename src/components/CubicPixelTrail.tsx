import React, { useEffect, useRef } from "react";

interface VoxelColor {
  top: [number, number, number];
  left: [number, number, number];
  right: [number, number, number];
}

const PALETTES: VoxelColor[] = [
  // Emerald / XP Lime
  {
    top: [163, 230, 53],
    left: [101, 163, 13],
    right: [63, 98, 18],
  },
  // Diamond Cyan
  {
    top: [56, 189, 248],
    left: [2, 132, 199],
    right: [3, 105, 161],
  },
  // Gold / Honey
  {
    top: [250, 204, 21],
    left: [202, 138, 4],
    right: [133, 77, 14],
  },
  // Redstone
  {
    top: [248, 113, 113],
    left: [220, 38, 38],
    right: [153, 27, 27],
  },
  // Nether Portal / Amethyst Purple
  {
    top: [192, 132, 252],
    left: [147, 51, 234],
    right: [107, 33, 168],
  },
];

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  maxLife: number;
  life: number;
  color: VoxelColor;
  angle: number;
  spin: number;
}

export const CubicPixelTrail: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    // Only enable for desktop pointer devices with fine control
    const isFinePointer = window.matchMedia("(pointer: fine)").matches;
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!isFinePointer || prefersReducedMotion) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d", { alpha: true });
    if (!ctx) return;

    let animId: number;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    const particles: Particle[] = [];
    const MAX_PARTICLES = 70;
    let lastX = 0;
    let lastY = 0;
    let hasMoved = false;

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };

    window.addEventListener("resize", handleResize);

    const spawnParticle = (x: number, y: number, burst = false, angleOverride?: number) => {
      if (particles.length >= MAX_PARTICLES) {
        particles.shift(); // remove oldest to keep 60fps constant
      }

      const color = PALETTES[Math.floor(Math.random() * PALETTES.length)];
      const baseSize = burst ? 5 + Math.random() * 5 : 7 + Math.random() * 6;

      let vx: number;
      let vy: number;

      if (burst && angleOverride !== undefined) {
        const speed = 1.8 + Math.random() * 2.2;
        vx = Math.cos(angleOverride) * speed;
        vy = Math.sin(angleOverride) * speed;
      } else {
        vx = (Math.random() - 0.5) * 0.9;
        vy = -0.4 - Math.random() * 0.7; // float gently upwards
      }

      particles.push({
        x: x + (Math.random() - 0.5) * 6,
        y: y + (Math.random() - 0.5) * 6,
        vx,
        vy,
        size: baseSize,
        maxLife: burst ? 35 + Math.random() * 15 : 45 + Math.random() * 20,
        life: 0,
        color,
        angle: (Math.random() - 0.5) * 0.2,
        spin: (Math.random() - 0.5) * 0.03,
      });
    };

    const handlePointerMove = (e: PointerEvent) => {
      const x = e.clientX;
      const y = e.clientY;

      if (!hasMoved) {
        hasMoved = true;
        lastX = x;
        lastY = y;
        spawnParticle(x, y);
        return;
      }

      const dx = x - lastX;
      const dy = y - lastY;
      const dist = Math.sqrt(dx * dx + dy * dy);

      // Spawn every 12 pixels of movement
      if (dist > 12) {
        const count = Math.min(Math.floor(dist / 12), 3);
        for (let i = 0; i < count; i++) {
          const ratio = (i + 1) / count;
          spawnParticle(lastX + dx * ratio, lastY + dy * ratio);
        }
        lastX = x;
        lastY = y;
      }
    };

    const handlePointerDown = (e: MouseEvent) => {
      const count = 7;
      for (let i = 0; i < count; i++) {
        const angle = (i / count) * Math.PI * 2 + (Math.random() - 0.5) * 0.4;
        spawnParticle(e.clientX, e.clientY, true, angle);
      }
    };

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    window.addEventListener("mousedown", handlePointerDown, { passive: true });

    // Draw isometric 3D voxel block
    const drawIsometricCube = (
      cx: number,
      cy: number,
      size: number,
      alpha: number,
      color: VoxelColor,
      angle: number
    ) => {
      ctx.save();
      ctx.translate(cx, cy);
      ctx.rotate(angle);

      const s = size;
      const cos30 = 0.866025 * s;
      const sin30 = 0.5 * s;

      const [tr, tg, tb] = color.top;
      const [lr, lg, lb] = color.left;
      const [rr, rg, rb] = color.right;

      // Top Face
      ctx.beginPath();
      ctx.moveTo(0, -s);
      ctx.lineTo(cos30, -sin30);
      ctx.lineTo(0, 0);
      ctx.lineTo(-cos30, -sin30);
      ctx.closePath();
      ctx.fillStyle = `rgba(${tr}, ${tg}, ${tb}, ${alpha})`;
      ctx.fill();

      // Left Face
      ctx.beginPath();
      ctx.moveTo(-cos30, -sin30);
      ctx.lineTo(0, 0);
      ctx.lineTo(0, s);
      ctx.lineTo(-cos30, sin30);
      ctx.closePath();
      ctx.fillStyle = `rgba(${lr}, ${lg}, ${lb}, ${alpha * 0.95})`;
      ctx.fill();

      // Right Face
      ctx.beginPath();
      ctx.moveTo(0, 0);
      ctx.lineTo(cos30, -sin30);
      ctx.lineTo(cos30, sin30);
      ctx.lineTo(0, s);
      ctx.closePath();
      ctx.fillStyle = `rgba(${rr}, ${rg}, ${rb}, ${alpha * 0.85})`;
      ctx.fill();

      // Subtle edge outlines for sharp pixel definition
      ctx.strokeStyle = `rgba(0, 0, 0, ${alpha * 0.35})`;
      ctx.lineWidth = 0.8;
      ctx.stroke();

      ctx.restore();
    };

    // Main animation loop
    const render = () => {
      ctx.clearRect(0, 0, width, height);

      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.life++;

        if (p.life >= p.maxLife) {
          particles.splice(i, 1);
          continue;
        }

        p.x += p.vx;
        p.y += p.vy;
        p.angle += p.spin;
        p.vx *= 0.97;
        p.vy += 0.015; // subtle gravity

        const progress = p.life / p.maxLife;
        const alpha = Math.max(0, 1 - progress);
        const currentSize = Math.max(1, p.size * (1 - progress * 0.45));

        drawIsometricCube(p.x, p.y, currentSize, alpha, p.color, p.angle);
      }

      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animId);
      window.removeEventListener("resize", handleResize);
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("mousedown", handlePointerDown);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      className="fixed inset-0 pointer-events-none z-50 select-none"
      style={{ touchAction: "none" }}
    />
  );
};
