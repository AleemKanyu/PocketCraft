import React, { useEffect, useRef, useState } from "react";

const H = 12; // Grid cell size (12px)

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  life: number;
}

export const CubicPixelTrail: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [isDark, setIsDark] = useState(() =>
    typeof document !== "undefined"
      ? document.documentElement.classList.contains("dark") ||
        window.matchMedia("(prefers-color-scheme: dark)").matches
      : true
  );

  useEffect(() => {
    // Only enable on desktop pointer devices with motion allowed
    if (!window.matchMedia?.("(pointer: fine)").matches) return;
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d", { alpha: true });
    if (!ctx) return;

    const getIsDark = () =>
      document.documentElement.classList.contains("dark") ||
      window.matchMedia("(prefers-color-scheme: dark)").matches;

    // Subtle, balanced lighter palette (not too dark or light)
    const getColors = (dark: boolean) => ({
      grays: dark
        ? [
            "rgba(235, 230, 218, 0.18)",
            "rgba(238, 233, 222, 0.32)",
            "rgba(242, 238, 228, 0.50)",
            "rgba(248, 245, 236, 0.68)",
          ]
        : [
            "rgba(180, 175, 162, 0.16)",
            "rgba(160, 155, 142, 0.28)",
            "rgba(140, 135, 122, 0.42)",
            "rgba(120, 115, 102, 0.56)",
          ],
      hot: dark
        ? [
            "rgba(246, 214, 74, 0.65)",
            "rgba(232, 160, 60, 0.50)",
            "rgba(240, 235, 225, 0.40)",
          ]
        : [
            "rgba(225, 175, 45, 0.60)",
            "rgba(210, 140, 50, 0.48)",
            "rgba(150, 145, 132, 0.35)",
          ],
    });

    let currentIsDark = getIsDark();
    let colors = getColors(currentIsDark);

    const observer = new MutationObserver(() => {
      const dark = getIsDark();
      currentIsDark = dark;
      setIsDark(dark);
      colors = getColors(dark);
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["class"],
    });

    let totalCols = 0;
    let totalRows = 0;
    let gridBuffer = new Float32Array(0);
    let activeMinRow = 0;
    let activeMaxRow = -1;

    const resize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
      totalCols = Math.ceil(canvas.width / H);
      totalRows = Math.ceil(document.documentElement.scrollHeight / H);
      gridBuffer = new Float32Array(totalCols * totalRows);
      activeMinRow = 0;
      activeMaxRow = -1;
    };
    resize();

    // Smooth continuous radial brush with Euclidean distance (supports any angle smoothly)
    const stampBrush = (
      px: number,
      py: number,
      radius: number = 2.3,
      intensity: number = 1.1
    ) => {
      const cellX = px / H;
      const cellY = py / H;
      const minRow = Math.max(0, Math.floor(cellY - radius));
      const maxRow = Math.min(totalRows - 1, Math.ceil(cellY + radius));

      if (maxRow >= minRow) {
        if (activeMaxRow < activeMinRow) {
          activeMinRow = minRow;
          activeMaxRow = maxRow;
        } else {
          activeMinRow = Math.min(activeMinRow, minRow);
          activeMaxRow = Math.max(activeMaxRow, maxRow);
        }
      }

      const minCol = Math.max(0, Math.floor(cellX - radius));
      const maxCol = Math.min(totalCols - 1, Math.ceil(cellX + radius));

      for (let r = minRow; r <= maxRow; r++) {
        for (let c = minCol; c <= maxCol; c++) {
          const dist = Math.hypot(c + 0.5 - cellX, r + 0.5 - cellY);
          if (dist <= radius) {
            const idx = r * totalCols + c;
            const val = intensity * (1 - dist / (radius + 0.001));
            if (val > gridBuffer[idx]) {
              gridBuffer[idx] = val;
            }
          }
        }
      }
    };

    let lastX: number | null = null;
    let lastY: number | null = null;
    let lastClientY: number | null = null;

    const handlePointerMove = (e: PointerEvent) => {
      const curX = e.clientX;
      const curY = e.clientY + window.scrollY;

      if (lastX !== null && lastY !== null) {
        const dist = Math.hypot(curX - lastX, curY - lastY);
        // Interpolate continuously along any motion vector
        if (dist < H * 16) {
          const steps = Math.max(1, Math.ceil(dist / (H * 0.4)));
          for (let s = 0; s <= steps; s++) {
            const ix = lastX + ((curX - lastX) * s) / steps;
            const iy = lastY + ((curY - lastY) * s) / steps;
            stampBrush(ix, iy, 2.3, 1.1);
          }
        } else {
          stampBrush(curX, curY, 2.3, 1.1);
        }
      } else {
        stampBrush(curX, curY, 2.3, 1.1);
      }

      lastX = curX;
      lastY = curY;
      lastClientY = e.clientY;
    };

    const handleScroll = () => {
      if (lastClientY !== null) {
        lastY = lastClientY + window.scrollY;
      }
    };

    const handlePointerLeave = () => {
      lastX = null;
      lastY = null;
      lastClientY = null;
    };

    // Subtle blast particles on click
    const particles: Particle[] = [];

    const handlePointerDown = (e: PointerEvent) => {
      if ((e.target as HTMLElement)?.closest("a, button, input, select, textarea, [role='button'], iframe")) {
        return;
      }
      if (e.button !== 0) return;

      const clickX = e.clientX;
      const clickY = e.clientY + window.scrollY;
      const cx = clickX / H;
      const cy = clickY / H;

      // Spawn subtle localized mini blast sparks (8 small particles)
      const numParticles = 8;
      for (let i = 0; i < numParticles; i++) {
        const angle = (Math.PI * 2 * i) / numParticles + (Math.random() * 0.4 - 0.2);
        const speed = 7 + Math.random() * 9;
        particles.push({
          x: cx,
          y: cy,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          life: 0.35 + Math.random() * 0.2, // ~350ms lifespan
        });
      }

      // Small subtle brush stamp at click point
      stampBrush(clickX, clickY, 2.6, 1.25);
    };

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    window.addEventListener("scroll", handleScroll, { passive: true });
    window.addEventListener("pointerleave", handlePointerLeave, { passive: true });
    window.addEventListener("pointerdown", handlePointerDown, { passive: true });
    window.addEventListener("resize", resize);

    const heightCheckInterval = setInterval(() => {
      const docHeight = document.documentElement.scrollHeight;
      if (Math.abs(docHeight - totalRows * H) > H * 3) {
        resize();
      }
    }, 800);

    let animId: number;
    let lastFrameTime = performance.now();

    const render = (time: number) => {
      animId = requestAnimationFrame(render);

      const dt = Math.min(0.05, (time - lastFrameTime) / 1000);
      lastFrameTime = time;

      if (activeMaxRow < activeMinRow && particles.length === 0) {
        return;
      }

      const curScrollY = window.scrollY;
      const visibleMinRow = Math.max(0, Math.floor(curScrollY / H) - 1);
      const visibleMaxRow = Math.min(totalRows - 1, Math.ceil((curScrollY + canvas.height) / H) + 1);

      ctx.clearRect(0, 0, canvas.width, canvas.height);

      const decayFactor = Math.pow(0.006, dt);
      let newMinRow = 0;
      let newMaxRow = -1;

      // Render grid cells
      for (let r = activeMinRow; r <= activeMaxRow; r++) {
        let hasActiveInRow = false;
        for (let c = 0; c < totalCols; c++) {
          const idx = r * totalCols + c;
          const val = gridBuffer[idx];
          if (val < 0.07) {
            gridBuffer[idx] = 0;
            continue;
          }

          hasActiveInRow = true;
          gridBuffer[idx] = val * decayFactor;

          // Viewport culling
          if (r < visibleMinRow || r > visibleMaxRow) continue;

          // 4-tier palette: tuned for difference inversion over text
          const color =
            val > 1.15
              ? colors.grays[3]
              : val > 0.6
              ? colors.grays[2]
              : val > 0.3
              ? colors.grays[1]
              : colors.grays[0];

          ctx.fillStyle = color;
          ctx.fillRect(c * H + 1, r * H + 1 - curScrollY, H - 1, H - 1);
        }

        if (hasActiveInRow) {
          if (newMaxRow < newMinRow) newMinRow = r;
          newMaxRow = r;
        }
      }

      activeMinRow = newMinRow;
      activeMaxRow = newMaxRow;

      // Render subtle mini blast particles
      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.life -= dt * 2.2;
        p.vx *= Math.pow(0.2, dt);
        p.vy *= Math.pow(0.2, dt);

        if (p.life <= 0) {
          particles.splice(i, 1);
          continue;
        }

        const cellX = Math.floor(p.x);
        const cellY = Math.floor(p.y);
        if (cellY < visibleMinRow || cellY > visibleMaxRow) continue;

        const particleColor =
          p.life > 0.5 ? colors.hot[0] : p.life > 0.25 ? colors.hot[1] : colors.grays[2];
        ctx.fillStyle = particleColor;
        ctx.fillRect(cellX * H + 1, cellY * H + 1 - curScrollY, H - 1, H - 1);
      }
    };

    animId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animId);
      clearInterval(heightCheckInterval);
      observer.disconnect();
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("scroll", handleScroll);
      window.removeEventListener("pointerleave", handlePointerLeave);
      window.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("resize", resize);

      if (canvas.parentElement) {
        for (const child of Array.from(canvas.parentElement.children)) {
          if (child !== canvas) {
            (child as HTMLElement).style.transform = "";
          }
        }
      }
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 z-20 select-none"
      style={{
        mixBlendMode: "normal",
      }}
    />
  );
};

export default CubicPixelTrail;
