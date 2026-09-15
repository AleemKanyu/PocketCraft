import React, { useEffect, useRef } from "react";

interface PixelParticle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  maxLife: number;
  life: number;
  shade: number;
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

    const particles: PixelParticle[] = [];
    const MAX_PARTICLES = 65;
    let lastX = 0;
    let lastY = 0;
    let hasMoved = false;

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };

    window.addEventListener("resize", handleResize);

    const isDarkMode = () =>
      document.documentElement.classList.contains("dark") ||
      window.matchMedia("(prefers-color-scheme: dark)").matches;

    const spawnParticle = (x: number, y: number, burst = false, angleOverride?: number) => {
      if (particles.length >= MAX_PARTICLES) {
        particles.shift();
      }

      const baseSize = burst ? 4 + Math.random() * 5 : 6 + Math.random() * 7;

      let vx: number;
      let vy: number;

      if (burst && angleOverride !== undefined) {
        const speed = 1.4 + Math.random() * 1.8;
        vx = Math.cos(angleOverride) * speed;
        vy = Math.sin(angleOverride) * speed;
      } else {
        vx = (Math.random() - 0.5) * 0.6;
        vy = (Math.random() - 0.5) * 0.6;
      }

      particles.push({
        x: Math.round(x + (Math.random() - 0.5) * 4),
        y: Math.round(y + (Math.random() - 0.5) * 4),
        vx,
        vy,
        size: Math.round(baseSize),
        maxLife: burst ? 28 + Math.random() * 12 : 38 + Math.random() * 18,
        life: 0,
        shade: Math.random(),
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

      if (dist > 14) {
        const count = Math.min(Math.floor(dist / 14), 2);
        for (let i = 0; i < count; i++) {
          const ratio = (i + 1) / count;
          spawnParticle(lastX + dx * ratio, lastY + dy * ratio);
        }
        lastX = x;
        lastY = y;
      }
    };

    const handlePointerDown = (e: MouseEvent) => {
      const count = 6;
      for (let i = 0; i < count; i++) {
        const angle = (i / count) * Math.PI * 2 + (Math.random() - 0.5) * 0.4;
        spawnParticle(e.clientX, e.clientY, true, angle);
      }
    };

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    window.addEventListener("mousedown", handlePointerDown, { passive: true });

    // Main animation loop rendering 2D flat pixel blocks matching the background
    const render = () => {
      ctx.clearRect(0, 0, width, height);
      const dark = isDarkMode();

      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.life++;

        if (p.life >= p.maxLife) {
          particles.splice(i, 1);
          continue;
        }

        p.x += p.vx;
        p.y += p.vy;
        p.vx *= 0.96;
        p.vy *= 0.96;

        const progress = p.life / p.maxLife;
        const alpha = Math.max(0, 1 - progress);
        const currentSize = Math.max(2, Math.round(p.size * (1 - progress * 0.35)));

        const px = Math.round(p.x - currentSize / 2);
        const py = Math.round(p.y - currentSize / 2);

        // 2D flat pixel square (no vibrant colors, blends with background tone)
        if (dark) {
          // Dark theme: subtle elevated monochrome grey-whites
          const fillAlpha = (0.04 + p.shade * 0.08) * alpha;
          const strokeAlpha = (0.08 + p.shade * 0.12) * alpha;
          ctx.fillStyle = `rgba(255, 255, 255, ${fillAlpha})`;
          ctx.fillRect(px, py, currentSize, currentSize);

          ctx.strokeStyle = `rgba(255, 255, 255, ${strokeAlpha})`;
          ctx.lineWidth = 1;
          ctx.strokeRect(px, py, currentSize, currentSize);
        } else {
          // Light theme: subtle monochrome charcoal-blacks
          const fillAlpha = (0.03 + p.shade * 0.06) * alpha;
          const strokeAlpha = (0.07 + p.shade * 0.1) * alpha;
          ctx.fillStyle = `rgba(0, 0, 0, ${fillAlpha})`;
          ctx.fillRect(px, py, currentSize, currentSize);

          ctx.strokeStyle = `rgba(0, 0, 0, ${strokeAlpha})`;
          ctx.lineWidth = 1;
          ctx.strokeRect(px, py, currentSize, currentSize);
        }
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
