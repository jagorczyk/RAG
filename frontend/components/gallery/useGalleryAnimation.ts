"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  useMotionValue,
  useSpring,
  type MotionValue,
} from "motion/react";

export type GalleryAnimationApi = {
  progress: MotionValue<number>;
  smoothProgress: MotionValue<number>;
  pointerX: MotionValue<number>;
  pointerY: MotionValue<number>;
  isDragging: MotionValue<number>;
  bindStage: (node: HTMLElement | null) => void;
  jumpTo: (index: number) => void;
  focusIndex: () => number;
  /** Returns true if the last gesture was a drag (skip click-to-open). */
  consumeDragGuard: () => boolean;
};

type Options = {
  itemCount: number;
  enabled?: boolean;
  reducedMotion?: boolean;
};

const FRICTION = 0.92;
const WHEEL_GAIN = 0.0028;
const DRAG_GAIN = 0.0042;
const TOUCH_GAIN = 0.0055;
const MAX_VELOCITY = 0.085;
const SETTLE_EPS = 0.00035;

/**
 * RAF-driven Z-axis gallery: wheel / drag / touch → velocity → inertia.
 * MotionValues keep transforms on the compositor (no React re-render per frame).
 */
export function useGalleryAnimation({
  itemCount,
  enabled = true,
  reducedMotion = false,
}: Options): GalleryAnimationApi {
  const [stage, setStage] = useState<HTMLElement | null>(null);

  const progress = useMotionValue(0);
  const smoothProgress = useSpring(progress, {
    stiffness: reducedMotion ? 400 : 120,
    damping: reducedMotion ? 40 : 28,
    mass: 0.8,
  });
  const pointerX = useSpring(0, { stiffness: 90, damping: 22, mass: 0.45 });
  const pointerY = useSpring(0, { stiffness: 90, damping: 22, mass: 0.45 });
  const isDragging = useMotionValue(0);

  const velocityRef = useRef(0);
  const rafRef = useRef(0);
  const draggingRef = useRef(false);
  const didDragRef = useRef(false);
  const lastPointerRef = useRef<{ x: number; y: number; t: number } | null>(null);
  const maxIndex = Math.max(0, itemCount - 1);

  const consumeDragGuard = useCallback(() => {
    const moved = didDragRef.current;
    didDragRef.current = false;
    return moved;
  }, []);

  const clampProgress = useCallback(
    (value: number) => Math.min(maxIndex, Math.max(0, value)),
    [maxIndex]
  );

  const jumpTo = useCallback(
    (index: number) => {
      velocityRef.current = 0;
      progress.set(clampProgress(index));
    },
    [clampProgress, progress]
  );

  const focusIndex = useCallback(
    () => Math.round(clampProgress(progress.get())),
    [clampProgress, progress]
  );

  const applyDelta = useCallback(
    (delta: number, gain: number) => {
      const impulse = Math.max(-MAX_VELOCITY, Math.min(MAX_VELOCITY, delta * gain));
      velocityRef.current = Math.max(
        -MAX_VELOCITY,
        Math.min(MAX_VELOCITY, velocityRef.current + impulse)
      );
      progress.set(clampProgress(progress.get() + impulse * 6));
    },
    [clampProgress, progress]
  );

  useEffect(() => {
    if (!enabled || reducedMotion || itemCount < 1) {
      progress.set(0);
      velocityRef.current = 0;
      return;
    }

    let alive = true;
    const tick = () => {
      if (!alive) return;
      let v = velocityRef.current;
      if (!draggingRef.current && Math.abs(v) > SETTLE_EPS) {
        const next = clampProgress(progress.get() + v);
        progress.set(next);
        if (next <= 0 || next >= maxIndex) v *= 0.35;
        v *= FRICTION;
        if (Math.abs(v) < SETTLE_EPS) v = 0;
        velocityRef.current = v;
      } else if (!draggingRef.current && Math.abs(v) <= SETTLE_EPS) {
        const current = progress.get();
        const nearest = Math.round(current);
        const delta = nearest - current;
        if (Math.abs(delta) > 0.002) {
          progress.set(current + delta * 0.12);
        } else if (current !== nearest) {
          progress.set(nearest);
        }
      }
      rafRef.current = requestAnimationFrame(tick);
    };

    rafRef.current = requestAnimationFrame(tick);
    return () => {
      alive = false;
      cancelAnimationFrame(rafRef.current);
    };
  }, [clampProgress, enabled, itemCount, maxIndex, progress, reducedMotion]);

  useEffect(() => {
    if (!stage || !enabled) return;

    const onWheel = (event: WheelEvent) => {
      if (reducedMotion) return;
      event.preventDefault();
      applyDelta(event.deltaY + event.deltaX * 0.35, WHEEL_GAIN);
    };

    const onPointerDown = (event: PointerEvent) => {
      if (reducedMotion) return;
      if (event.button !== 0 && event.pointerType === "mouse") return;
      // Ignore clicks that start on interactive chrome (lightbox handled outside)
      const target = event.target as HTMLElement | null;
      if (target?.closest("[data-gallery-chrome]")) return;

      draggingRef.current = true;
      didDragRef.current = false;
      isDragging.set(1);
      velocityRef.current = 0;
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
      const dt = Math.max(8, now - last.t);
      const travel = dy * 0.75 + dx * 0.35;
      const gain = event.pointerType === "touch" ? TOUCH_GAIN : DRAG_GAIN;
      applyDelta(travel, gain * (16 / dt) * 12);
      lastPointerRef.current = { x: event.clientX, y: event.clientY, t: now };
    };

    const endDrag = (event: PointerEvent) => {
      if (!draggingRef.current) return;
      draggingRef.current = false;
      isDragging.set(0);
      lastPointerRef.current = null;
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
  }, [applyDelta, enabled, isDragging, pointerX, pointerY, reducedMotion, stage]);

  return {
    progress,
    smoothProgress,
    pointerX,
    pointerY,
    isDragging,
    bindStage: setStage,
    jumpTo,
    focusIndex,
    consumeDragGuard,
  };
}
