"use client";

import { useCallback, useEffect, useState } from "react";
import { useSpring, type MotionValue } from "motion/react";

export type MouseParallaxApi = {
  pointerX: MotionValue<number>;
  pointerY: MotionValue<number>;
  bindTarget: (node: HTMLElement | null) => void;
  reset: () => void;
};

type Options = {
  enabled?: boolean;
  /** Spring stiffness for pointer follow. */
  stiffness?: number;
  damping?: number;
};

/**
 * Normalized pointer (−0.5…0.5) with spring inertia — drives subtle tilt / layer parallax.
 */
export function useMouseParallax({
  enabled = true,
  stiffness = 90,
  damping = 22,
}: Options = {}): MouseParallaxApi {
  const [target, setTarget] = useState<HTMLElement | null>(null);
  const pointerX = useSpring(0, { stiffness, damping, mass: 0.45 });
  const pointerY = useSpring(0, { stiffness, damping, mass: 0.45 });

  const reset = useCallback(() => {
    pointerX.set(0);
    pointerY.set(0);
  }, [pointerX, pointerY]);

  useEffect(() => {
    if (!target || !enabled) {
      reset();
      return;
    }

    const fine = window.matchMedia("(pointer: fine)").matches;
    if (!fine) {
      reset();
      return;
    }

    const onMove = (event: PointerEvent) => {
      const rect = target.getBoundingClientRect();
      if (rect.width < 1 || rect.height < 1) return;
      pointerX.set((event.clientX - rect.left) / rect.width - 0.5);
      pointerY.set((event.clientY - rect.top) / rect.height - 0.5);
    };

    const onLeave = () => reset();

    target.addEventListener("pointermove", onMove);
    target.addEventListener("pointerleave", onLeave);
    return () => {
      target.removeEventListener("pointermove", onMove);
      target.removeEventListener("pointerleave", onLeave);
    };
  }, [enabled, pointerX, pointerY, reset, target]);

  return {
    pointerX,
    pointerY,
    bindTarget: setTarget,
    reset,
  };
}
