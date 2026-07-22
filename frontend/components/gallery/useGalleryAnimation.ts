"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useMotionValue, type MotionValue } from "motion/react";
import { GALLERY_TUNNEL } from "./galleryData";
import { useMouseParallax } from "./useMouseParallax";

export type GalleryAnimationApi = {
  /** Camera Z along the tunnel (world units, wraps). */
  cameraZ: MotionValue<number>;
  /** Instantaneous flight velocity (world units / s). */
  velocity: MotionValue<number>;
  pointerX: MotionValue<number>;
  pointerY: MotionValue<number>;
  /** 1 = desktop, ~0.7 tablet, ~0.45 mobile — scales spatial depth. */
  depthScale: MotionValue<number>;
  isDragging: MotionValue<number>;
  bindStage: (node: HTMLElement | null) => void;
  consumeDragGuard: () => boolean;
};

type Options = {
  itemCount: number;
  enabled?: boolean;
  reducedMotion?: boolean;
  /** Cruise speed in world units / second (positive = fly toward viewer). */
  cruiseSpeed?: number;
};

const FRICTION = 0.925;
const WHEEL_GAIN = 1.35;
const DRAG_GAIN = 2.4;
const MAX_SPEED = 2200;
const AUTO_RESUME_MS = 1600;

function wrap(value: number, length: number): number {
  const l = Math.max(1, length);
  return ((value % l) + l) % l;
}

/**
 * RAF flight through 3D gallery space: cruise + inertia from wheel/drag + mouse parallax.
 */
export function useGalleryAnimation({
  itemCount,
  enabled = true,
  reducedMotion = false,
  cruiseSpeed = 72,
}: Options): GalleryAnimationApi {
  const [stage, setStage] = useState<HTMLElement | null>(null);

  const cameraZ = useMotionValue(0);
  const velocity = useMotionValue(cruiseSpeed);
  const depthScale = useMotionValue(1);
  const isDragging = useMotionValue(0);

  const {
    pointerX,
    pointerY,
    bindTarget: bindParallax,
  } = useMouseParallax({
    enabled: enabled && !reducedMotion,
  });

  const rafRef = useRef(0);
  const lastTsRef = useRef(0);
  const draggingRef = useRef(false);
  const didDragRef = useRef(false);
  const lastPointerRef = useRef<{ x: number; y: number; t: number } | null>(
    null
  );
  const userUntilRef = useRef(0);
  const velRef = useRef(cruiseSpeed);
  const camRef = useRef(0);

  const consumeDragGuard = useCallback(() => {
    const moved = didDragRef.current;
    didDragRef.current = false;
    return moved;
  }, []);

  const bindStage = useCallback(
    (node: HTMLElement | null) => {
      setStage(node);
      bindParallax(node);
    },
    [bindParallax]
  );

  useEffect(() => {
    if (typeof window === "undefined") return;

    const updateDepth = () => {
      const w = window.innerWidth;
      if (w < 640) depthScale.set(0.45);
      else if (w < 1024) depthScale.set(0.7);
      else depthScale.set(1);
    };

    updateDepth();
    window.addEventListener("resize", updateDepth);
    return () => window.removeEventListener("resize", updateDepth);
  }, [depthScale]);

  useEffect(() => {
    if (!enabled || reducedMotion || itemCount < 1) {
      camRef.current = 0;
      cameraZ.set(0);
      velRef.current = 0;
      velocity.set(0);
      return;
    }

    let alive = true;
    lastTsRef.current = performance.now();
    velRef.current = cruiseSpeed;
    velocity.set(cruiseSpeed);

    const tick = (now: number) => {
      if (!alive) return;
      const dt = Math.min(0.05, (now - lastTsRef.current) / 1000);
      lastTsRef.current = now;

      const userActive = draggingRef.current || now < userUntilRef.current;

      if (!userActive) {
        // Ease velocity back toward cruise (spring-like lerp)
        const target = cruiseSpeed;
        velRef.current += (target - velRef.current) * Math.min(1, dt * 2.4);
      } else if (!draggingRef.current) {
        // Coast with friction after gesture
        velRef.current *= Math.pow(FRICTION, dt * 60);
        if (Math.abs(velRef.current) < 8) velRef.current = 0;
      }

      velRef.current = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, velRef.current));
      camRef.current = wrap(camRef.current + velRef.current * dt, GALLERY_TUNNEL);
      cameraZ.set(camRef.current);
      velocity.set(velRef.current);

      rafRef.current = requestAnimationFrame(tick);
    };

    rafRef.current = requestAnimationFrame(tick);
    return () => {
      alive = false;
      cancelAnimationFrame(rafRef.current);
    };
  }, [cameraZ, cruiseSpeed, enabled, itemCount, reducedMotion, velocity]);

  useEffect(() => {
    if (!stage || !enabled || reducedMotion) return;

    const markUser = () => {
      userUntilRef.current = performance.now() + AUTO_RESUME_MS;
    };

    const applyImpulse = (delta: number) => {
      markUser();
      velRef.current = Math.max(
        -MAX_SPEED,
        Math.min(MAX_SPEED, velRef.current + delta)
      );
      velocity.set(velRef.current);
    };

    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      // Scroll “into” the scene: wheel down / trackpad swipe up → positive Z flight
      const impulse = (event.deltaY + event.deltaX * 0.35) * WHEEL_GAIN;
      applyImpulse(impulse);
    };

    const onPointerDown = (event: PointerEvent) => {
      if (event.button !== 0 && event.pointerType === "mouse") return;
      const target = event.target as HTMLElement | null;
      if (target?.closest("[data-gallery-chrome]")) return;

      draggingRef.current = true;
      didDragRef.current = false;
      isDragging.set(1);
      markUser();
      lastPointerRef.current = {
        x: event.clientX,
        y: event.clientY,
        t: performance.now(),
      };
      stage.setPointerCapture(event.pointerId);
    };

    const onPointerMove = (event: PointerEvent) => {
      if (!draggingRef.current || !lastPointerRef.current) return;
      const now = performance.now();
      const last = lastPointerRef.current;
      const dx = event.clientX - last.x;
      const dy = event.clientY - last.y;
      const dist = Math.hypot(dx, dy);
      if (dist > 5) didDragRef.current = true;

      const dtMs = Math.max(8, now - last.t);
      // Vertical drag primary (fly), slight horizontal contribution
      const impulse = (-dy + dx * 0.12) * DRAG_GAIN * (16 / dtMs);
      applyImpulse(impulse);
      lastPointerRef.current = { x: event.clientX, y: event.clientY, t: now };
    };

    const endDrag = (event: PointerEvent) => {
      if (!draggingRef.current) return;
      draggingRef.current = false;
      isDragging.set(0);
      lastPointerRef.current = null;
      markUser();
      try {
        stage.releasePointerCapture(event.pointerId);
      } catch {
        /* already released */
      }
    };

    stage.addEventListener("wheel", onWheel, { passive: false });
    stage.addEventListener("pointerdown", onPointerDown);
    stage.addEventListener("pointermove", onPointerMove);
    stage.addEventListener("pointerup", endDrag);
    stage.addEventListener("pointercancel", endDrag);

    return () => {
      stage.removeEventListener("wheel", onWheel);
      stage.removeEventListener("pointerdown", onPointerDown);
      stage.removeEventListener("pointermove", onPointerMove);
      stage.removeEventListener("pointerup", endDrag);
      stage.removeEventListener("pointercancel", endDrag);
    };
  }, [enabled, isDragging, reducedMotion, stage, velocity]);

  return {
    cameraZ,
    velocity,
    pointerX,
    pointerY,
    depthScale,
    isDragging,
    bindStage,
    consumeDragGuard,
  };
}
