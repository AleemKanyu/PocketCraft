import React, { useEffect, useRef } from "react";

const H = 12; // Grid cell size (12px)

interface Ripple {
  cx: number;
  cy: number;
  t0: number;
  power: number;
}

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  life: number;
}

export const CubicPixelTrail: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

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

    // Exact Lodeway authentic Minecraft color thresholds
    const getColors = (dark: boolean) => ({
      grays: dark
        ? ["#221e19", "#39332a", "#6a6350", "#ece7da"]
        : ["#e9e9e5", "#c9c9c2", "#8f8f86", "#191613"],
      hot: ["#f6d64a", "#e8842a", "#b52f10"],
    });

    let colors = getColors(getIsDark());

    const observer = new MutationObserver(() => {
      colors = getColors(getIsDark());
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
        // If movement is within reasonable limit, interpolate continuously
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

    // Click shockwave and spark particles
    let clickState: { cx: number; cy: number; t0: number } | null = null;
    const ripples: Ripple[] = [];
    const particles: Particle[] = [];

    const handlePointerDown = (e: PointerEvent) => {
      if ((e.target as HTMLElement)?.closest("a, button, input, select, textarea, [role='button'], iframe")) {
        return;
      }
      if (e.button !== 0) return;
      clickState = {
        cx: e.clientX / H,
        cy: (e.clientY + window.scrollY) / H,
        t0: performance.now(),
      };
    };

    const handlePointerUp = () => {
      if (!clickState) return;
      const now = performance.now();
      const charge = 2.5 + ((now - clickState.t0) / 1000) * 5;
      const { cx, cy } = clickState;
      clickState = null;

      ripples.push({ cx, cy, t0: now, power: charge });
      if (ripples.length > 6) ripples.shift();

      const numParticles = Math.round(12 + charge * 2);
      for (let i = 0; i < numParticles; i++) {
        const angle = Math.random() * Math.PI * 2;
        const speed = 15 + Math.random() * (18 + charge * 1.5);
        particles.push({
          x: cx,
          y: cy,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          life: 0.85 + Math.random() * 0.45,
        });
      }

      stampBrush(cx * H, cy * H, Math.min(10, 2 + charge * 0.5), 1.4);
    };

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    window.addEventListener("scroll", handleScroll, { passive: true });
    window.addEventListener("pointerleave", handlePointerLeave, { passive: true });
    window.addEventListener("pointerdown", handlePointerDown, { passive: true });
    window.addEventListener("pointerup", handlePointerUp, { passive: true });
    window.addEventListener("resize", resize);

    // Check periodically if document height changed
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

      if (activeMaxRow < activeMinRow && !clickState && ripples.length === 0 && particles.length === 0) {
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

          // Authentic Lodeway 4-tier palette
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

      // Render expanding ripples
      for (let i = ripples.length - 1; i >= 0; i--) {
        const rip = ripples[i];
        const age = (time - rip.t0) / 1000;
        const maxRadius = rip.power * 10 + 26;
        const speed = 62 + rip.power * 1.6;
        const currentRadius = rip.power + age * speed;
        const life = 1 - currentRadius / maxRadius;

        if (life <= 0) {
          ripples.splice(i, 1);
          continue;
        }

        const minR = Math.max(visibleMinRow, Math.floor(rip.cy - currentRadius));
        const maxR = Math.min(visibleMaxRow, Math.ceil(rip.cy + currentRadius));
        const minC = Math.max(0, Math.floor(rip.cx - currentRadius));
        const maxC = Math.min(totalCols - 1, Math.ceil(rip.cx + currentRadius));

        for (let r = minR; r <= maxR; r++) {
          for (let c = minC; c <= maxC; c++) {
            const dist = Math.hypot(c + 0.5 - rip.cx, r + 0.5 - rip.cy);
            if (Math.abs(dist - currentRadius) < 1.4) {
              const ringColor =
                dist < currentRadius * 0.4
                  ? "#fff6cf"
                  : dist < currentRadius * 0.75
                  ? colors.hot[0]
                  : colors.hot[1];
              ctx.fillStyle = ringColor;
              ctx.fillRect(c * H + 1, r * H + 1 - curScrollY, H - 1, H - 1);
            }
          }
        }
      }

      // Render spark particles
      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.life -= dt * 1.1;
        p.vx *= Math.pow(0.35, dt);
        p.vy *= Math.pow(0.35, dt);

        if (p.life <= 0) {
          particles.splice(i, 1);
          continue;
        }

        const cellX = Math.floor(p.x);
        const cellY = Math.floor(p.y);
        if (cellY < visibleMinRow || cellY > visibleMaxRow) continue;

        const particleColor =
          p.life > 0.55 ? colors.hot[2] : p.life > 0.25 ? colors.hot[1] : colors.grays[2];
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
      window.removeEventListener("pointerup", handlePointerUp);
      window.removeEventListener("resize", resize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 -z-10 select-none"
    />
  );
};

export default CubicPixelTrail;
