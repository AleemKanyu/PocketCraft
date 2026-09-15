import React, { useEffect, useRef } from "react";

interface GridCell {
  col: number;
  row: number;
  intensity: number; // 0.0 to 1.0
  birth: number;     // timestamp in ms
}

export const CubicPixelTrail: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    // Only enable on desktop pointer devices
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

    // 12px grid cell (matches the Minecraft pixel brush size in the reference)
    const GRID_SIZE = 12;
    const TRAIL_DURATION = 550; // ms lifetime

    const activeCells = new Map<string, GridCell>();
    let lastX: number | null = null;
    let lastY: number | null = null;

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };

    window.addEventListener("resize", handleResize);

    const isDarkMode = () =>
      document.documentElement.classList.contains("dark") ||
      window.matchMedia("(prefers-color-scheme: dark)").matches;

    const touchCell = (col: number, row: number, intensity: number, now: number) => {
      const key = `${col},${row}`;
      const existing = activeCells.get(key);
      if (existing) {
        existing.intensity = Math.max(existing.intensity, intensity);
        existing.birth = now;
      } else {
        activeCells.set(key, { col, row, intensity, birth: now });
      }
    };

    // Brush stamp: 1 core cell + 4 direct neighbors at medium + 4 diagonals at low
    const stampBrush = (col: number, row: number, now: number) => {
      touchCell(col, row, 1.0, now);

      // Direct neighbors (cardinal)
      touchCell(col + 1, row, 0.55, now);
      touchCell(col - 1, row, 0.55, now);
      touchCell(col, row + 1, 0.55, now);
      touchCell(col, row - 1, 0.55, now);

      // Diagonal neighbors
      touchCell(col + 1, row + 1, 0.28, now);
      touchCell(col - 1, row + 1, 0.28, now);
      touchCell(col + 1, row - 1, 0.28, now);
      touchCell(col - 1, row - 1, 0.28, now);
    };

    const handlePointerMove = (e: PointerEvent) => {
      const x = e.clientX;
      const y = e.clientY;
      const now = performance.now();

      if (lastX === null || lastY === null) {
        lastX = x;
        lastY = y;
        stampBrush(Math.floor(x / GRID_SIZE), Math.floor(y / GRID_SIZE), now);
        return;
      }

      const dx = x - lastX;
      const dy = y - lastY;
      const dist = Math.sqrt(dx * dx + dy * dy);

      // Interpolate points along the cursor trajectory to prevent gaps during fast movement
      const stepSize = GRID_SIZE * 0.45;
      const steps = Math.max(1, Math.ceil(dist / stepSize));

      for (let i = 1; i <= steps; i++) {
        const interpX = lastX + (dx * i) / steps;
        const interpY = lastY + (dy * i) / steps;
        const col = Math.floor(interpX / GRID_SIZE);
        const row = Math.floor(interpY / GRID_SIZE);
        stampBrush(col, row, now);
      }

      lastX = x;
      lastY = y;
    };

    const handlePointerLeave = () => {
      lastX = null;
      lastY = null;
    };

    const handlePointerDown = (e: MouseEvent) => {
      const now = performance.now();
      const col = Math.floor(e.clientX / GRID_SIZE);
      const row = Math.floor(e.clientY / GRID_SIZE);

      // Mini click burst
      for (let dc = -2; dc <= 2; dc++) {
        for (let dr = -2; dr <= 2; dr++) {
          const dist = Math.sqrt(dc * dc + dr * dr);
          if (dist <= 2.2) {
            const intensity = Math.max(0.2, 1 - dist / 2.5);
            touchCell(col + dc, row + dr, intensity, now);
          }
        }
      }
    };

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    window.addEventListener("pointerleave", handlePointerLeave, { passive: true });
    window.addEventListener("mousedown", handlePointerDown, { passive: true });

    // Render loop
    const render = (time: number) => {
      ctx.clearRect(0, 0, width, height);
      const dark = isDarkMode();

      activeCells.forEach((cell, key) => {
        const age = time - cell.birth;

        if (age >= TRAIL_DURATION) {
          activeCells.delete(key);
          return;
        }

        const life = 1 - age / TRAIL_DURATION;
        const alpha = Math.max(0, life * cell.intensity);

        const x = cell.col * GRID_SIZE;
        const y = cell.row * GRID_SIZE;
        // 1px gap leaves the authentic dark grid outline seen in the screenshot
        const w = GRID_SIZE - 1;
        const h = GRID_SIZE - 1;

        if (dark) {
          // Dark mode: stone / parchment pixel tones from reference image
          // Center: rgb(110, 103, 85), Edges: rgb(58, 52, 42), Dim: rgb(35, 31, 25)
          let r: number, g: number, b: number;
          if (cell.intensity > 0.8) {
            r = 115; g = 108; b = 88;
          } else if (cell.intensity > 0.4) {
            r = 65; g = 59; b = 48;
          } else {
            r = 38; g = 34; b = 28;
          }

          ctx.fillStyle = `rgba(${r}, ${g}, ${b}, ${alpha * 0.95})`;
          ctx.fillRect(x + 0.5, y + 0.5, w, h);
        } else {
          // Light mode: clean matching subtle pencil/stone pixel tones
          let r: number, g: number, b: number;
          if (cell.intensity > 0.8) {
            r = 80; g = 75; b = 65;
          } else if (cell.intensity > 0.4) {
            r = 130; g = 125; b = 115;
          } else {
            r = 180; g = 175; b = 165;
          }

          ctx.fillStyle = `rgba(${r}, ${g}, ${b}, ${alpha * 0.75})`;
          ctx.fillRect(x + 0.5, y + 0.5, w, h);
        }
      });

      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animId);
      window.removeEventListener("resize", handleResize);
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerleave", handlePointerLeave);
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
