"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  useMotionValue,
  useSpring,
  type MotionValue,
} from "motion/react";

export type GalleryAnimationApi = {
  /** Continuous time in seconds — drives auto L→R drift. */
  time: MotionValue<number>;
  pointerX: MotionValue<number>;
  pointerY: MotionValue<number>;
  isDragging: MotionValue<number>;
  bindStage: (node: HTMLElement | null) => void;
  /** Nudge sideways (px-ish); used by optional drag. */
  nudge: (dx: number) => void;
  consumeDragGuard: () => boolean;
};

type Options = {
  itemCount: number;
  enabled?: boolean;
  reducedMotion?: boolean;
  /** World units per second moving left → right. */
  speed?: number;
};

/**
 * Auto L→R gallery clock (RAF) with optional drag nudge + mouse parallax.
 */
export function useGalleryAnimation({
  itemCount,
  enabled = true,
  reducedMotion = false,
  speed = 48,
}: Options): GalleryAnimationApi {
  const [stage, setStage] = useState<HTMLElement | null>(null);

  const time = useMotionValue(0);
  const pointerX = useSpring(0, { stiffness: 90, damping: 22, mass: 0.45 });
  const pointerY = useSpring(0, { stiffness: 90, damping: 22, mass: 0.45 });
  const isDragging = useMotionValue(0);

  const offsetRef = useRef(0);
  const rafRef = useRef(0);
  const lastTsRef = useRef(0);
  const draggingRef = useRef(false);
  const didDragRef = useRef(false);
  const lastPointerRef = useRef<{ x: number; y: number; t: number } | null>(null);
  const pausedUntilRef = useRef(0);

  const consumeDragGuard = useCallback(() => {
    const moved = didDragRef.current;
    didDragRef.current = false;
    return moved;
  }, []);

  const nudge = useCallback(
    (dx: number) => {
      offsetRef.current += dx;
      time.set(time.get() + dx / Math.max(1, speed));
      pausedUntilRef.current = performance.now() + 1800;
    },
    [speed, time]
  );

  useEffect(() => {
    if (!enabled || reducedMotion || itemCount < 1) {
      time.set(0);
      return;
    }

    let alive = true;
    lastTsRef.current = performance.now();

    const tick = (now: number) => {
      if (!alive) return;
      const dt = Math.min(0.05, (now - lastTsRef.current) / 1000);
      lastTsRef.current = now;

      const paused =
        draggingRef.current || now < pausedUntilRef.current || !enabled;

      if (!paused) {
        time.set(time.get() + dt);
      }

      rafRef.current = requestAnimationFrame(tick);
    };

    rafRef.current = requestAnimationFrame(tick);
    return () => {
      alive = false;
      cancelAnimationFrame(rafRef.current);
    };
  }, [enabled, itemCount, reducedMotion, time]);

  useEffect(() => {
    if (!stage || !enabled) return;

    const onWheel = (event: WheelEvent) => {
      if (reducedMotion) return;
      event.preventDefault();
      // Horizontal intent: wheel / trackpad sideways feels natural
      nudge(-(event.deltaY + event.deltaX) * 0.55);
    };

    const onPointerDown = (event: PointerEvent) => {
      if (reducedMotion) return;
      if (event.button !== 0 && event.pointerType === "mouse") return;
      const target = event.target as HTMLElement | null;
      if (target?.closest("[data-gallery-chrome]")) return;

      draggingRef.current = true;
      didDragRef.current = false;
      isDragging.set(1);
      lastPointerRef.current = {
        x: event.clientX,
        y: event.clientY,
        t: performance.now(),
      };
      stage.setPointerCapture(event.pointerId);
    };

    const onPointerMove = (event: PointerEvent) => {
      const rect = stage.getBoundingClientRect();
      pointerX.set((event.clientX - rect.left) / rect.width - 0.5);
      pointerY.set((event.clientY - rect.top) / rect.height - 0.5);

      if (!draggingRef.current || !lastPointerRef.current) return;
      const now = performance.now();
      const last = lastPointerRef.current;
      const dx = event.clientX - last.x;
      const dy = event.clientY - last.y;
      if (Math.hypot(dx, dy) > 5) didDragRef.current = true;
      // Drag sideways (primary) with a bit of vertical → same axis
      nudge(dx + dy * 0.15);
      lastPointerRef.current = { x: event.clientX, y: event.clientY, t: now };
    };

    const endDrag = (event: PointerEvent) => {
      if (!draggingRef.current) return;
      draggingRef.current = false;
      isDragging.set(0);
      lastPointerRef.current = null;
      pausedUntilRef.current = performance.now() + 1200;
      try {
        stage.releasePointerCapture(event.pointerId);
      } catch {
        /* already released */
      }
    };

    const onLeave = () => {
      if (!draggingRef.current) {
        pointerX.set(0);
        pointerY.set(0);
      }
    };

    stage.addEventListener("wheel", onWheel, { passive: false });
    stage.addEventListener("pointerdown", onPointerDown);
    stage.addEventListener("pointermove", onPointerMove);
    stage.addEventListener("pointerup", endDrag);
    stage.addEventListener("pointercancel", endDrag);
    stage.addEventListener("pointerleave", onLeave);

    return () => {
      stage.removeEventListener("wheel", onWheel);
      stage.removeEventListener("pointerdown", onPointerDown);
      stage.removeEventListener("pointermove", onPointerMove);
      stage.removeEventListener("pointerup", endDrag);
      stage.removeEventListener("pointercancel", endDrag);
      stage.removeEventListener("pointerleave", onLeave);
    };
  }, [enabled, isDragging, nudge, pointerX, pointerY, reducedMotion, stage]);

  return {
    time,
    pointerX,
    pointerY,
    isDragging,
    bindStage: setStage,
    nudge,
    consumeDragGuard,
  };
}
