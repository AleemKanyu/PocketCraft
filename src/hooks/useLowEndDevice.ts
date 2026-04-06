import { useEffect, useState } from "react";

export function useLowEndDevice() {
  const [isLowEnd, setIsLowEnd] = useState(false);

  useEffect(() => {
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const saveData = (navigator as any).connection?.saveData === true;
    const cores = navigator.hardwareConcurrency || 4;
    const memory = (navigator as any).deviceMemory || 4;
    const coarsePointer = window.matchMedia("(pointer: coarse)").matches;

    // Conservative heuristic for low-end phones and bandwidth-constrained devices.
    const lowEndHeuristic = reducedMotion || saveData || (coarsePointer && (cores <= 4 || memory <= 4));
    setIsLowEnd(lowEndHeuristic);
  }, []);

  return isLowEnd;
}
