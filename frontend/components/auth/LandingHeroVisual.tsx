"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { Gallery } from "@/components/gallery";
import { PhoneInHand } from "@/components/auth/PhoneInHand";

type Phase = "collage" | "phone";

const EASE = [0.22, 1, 0.36, 1] as const;
const COLLAGE_MS = 10500;
const PHONE_MS = 8000;

/**
 * Auth hero visual: 3D photo collage ↔ front-facing iPhone mockup.
 * Phases are mutually exclusive (AnimatePresence) so gallery blur/3D
 * never composites over the phone.
 */
export function LandingHeroVisual({ instanceId = "desktop" }: { instanceId?: string }) {
  const reducedMotionPref = useReducedMotion();
  const reduced = reducedMotionPref === true;
  const [phase, setPhase] = useState<Phase>("collage");

  useEffect(() => {
    if (reduced) {
      setPhase("collage");
      return;
    }

    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const tick = (current: Phase) => {
      const delay = current === "collage" ? COLLAGE_MS : PHONE_MS;
      timer = setTimeout(() => {
        if (!active) return;
        const next: Phase = current === "collage" ? "phone" : "collage";
        setPhase(next);
        tick(next);
      }, delay);
    };

    tick("collage");
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [reduced]);

  if (reduced) {
    return <Gallery embedded instanceId={instanceId} />;
  }

  return (
    <div className="relative h-full min-h-0 w-full overflow-hidden bg-surface">
      <AnimatePresence mode="sync">
        {phase === "collage" ? (
          <motion.div
            key="collage"
            className="absolute inset-0 z-0"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.35, ease: EASE }}
          >
            <Gallery embedded instanceId={instanceId} />
          </motion.div>
        ) : (
          <motion.div
            key="phone"
            className="absolute inset-0 z-10 isolate flex items-center justify-center px-6 py-4"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.55, ease: EASE }}
          >
            <PhoneInHand />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
