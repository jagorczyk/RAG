"use client";

import { useEffect, useState } from "react";
import { motion, useReducedMotion } from "motion/react";
import { Gallery } from "@/components/gallery";
import { PhoneInHand } from "@/components/auth/PhoneInHand";

type Phase = "collage" | "phone";

const EASE = [0.22, 1, 0.36, 1] as const;
const COLLAGE_MS = 9000;
const PHONE_MS = 8000;

/**
 * Auth hero visual: 3D photo collage ↔ floating photorealistic iPhone.
 * Simultaneous crossfade (both layers stay mounted) for a fluid morph.
 * `prefers-reduced-motion`: collage only.
 */
export function LandingHeroVisual({ instanceId = "desktop" }: { instanceId?: string }) {
  const reducedMotionPref = useReducedMotion();
  const reduced = reducedMotionPref === true;
  const [phase, setPhase] = useState<Phase>("collage");
  const showPhone = !reduced && phase === "phone";

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
    <div className="relative h-full min-h-0 w-full overflow-hidden bg-[#F4F1EE]">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 70% 55% at 50% 45%, #E7EEF7 0%, transparent 68%), linear-gradient(180deg, #F9F7F7 0%, #EDE8E3 100%)",
        }}
      />

      <motion.div
        className="absolute inset-0"
        initial={false}
        animate={{
          opacity: showPhone ? 0 : 1,
          scale: showPhone ? 0.92 : 1,
          filter: showPhone ? "blur(14px)" : "blur(0px)",
        }}
        transition={{ duration: 1.05, ease: EASE }}
        style={{ pointerEvents: showPhone ? "none" : "auto" }}
      >
        <Gallery embedded instanceId={instanceId} />
      </motion.div>

      <motion.div
        className="absolute inset-0 flex items-center justify-center px-6 py-4"
        initial={false}
        animate={{
          opacity: showPhone ? 1 : 0,
          y: showPhone ? 0 : 36,
          scale: showPhone ? 1 : 0.9,
          filter: showPhone ? "blur(0px)" : "blur(10px)",
        }}
        transition={{ duration: 1.05, ease: EASE }}
        style={{ pointerEvents: "none" }}
        aria-hidden={!showPhone}
      >
        <PhoneInHand />
      </motion.div>

      <PhaseCaption visible={!showPhone}>Biblioteka w ruchu</PhaseCaption>
      <PhaseCaption visible={showPhone}>Cogniface na iPhone</PhaseCaption>
    </div>
  );
}

function PhaseCaption({
  children,
  visible,
}: {
  children: React.ReactNode;
  visible: boolean;
}) {
  return (
    <motion.p
      className="pointer-events-none absolute bottom-5 left-0 right-0 z-30 text-center text-[0.7rem] font-semibold tracking-[0.06em] text-[#4A6B8A]"
      initial={false}
      animate={{ opacity: visible ? 1 : 0 }}
      transition={{ duration: 0.45, ease: EASE }}
    >
      {children}
    </motion.p>
  );
}
