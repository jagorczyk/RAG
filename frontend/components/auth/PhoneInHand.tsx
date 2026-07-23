"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import {
  BookMarked,
  FolderOpen,
  MessageCircle,
  Users,
} from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";

const PHOTOS = [
  "/landing-page-photos/abdullah-ali-_bIyIshKuY8-unsplash.jpg",
  "/landing-page-photos/emma-swoboda-DXEhDakyt8E-unsplash.jpg",
  "/landing-page-photos/aharon-luria-lefB7o-UQWE-unsplash.jpg",
  "/landing-page-photos/johannes-andersson-UCd78vfC8vU-unsplash.jpg",
  "/landing-page-photos/vahid-kanani-3rgm22SpNJU-unsplash.jpg",
  "/landing-page-photos/mircea-solomiea-6HfHU2WoDDQ-unsplash.jpg",
] as const;

/** Figma iPhone 14 Pro Space Black (1736×3528) — transparent screen punch. */
const FRAME_SRC = "/iphone-mockup/iphone-14-pro-space-black.png";
const FRAME_W = 1736;
const FRAME_H = 3528;

/**
 * Screen hole measured from the Figma frame PNG (alpha < 10).
 * Dynamic Island is painted into the frame and overlays the top of the punch.
 */
const SCREEN = {
  left: "5.184%",
  top: "2.183%",
  width: "89.631%",
  height: "95.635%",
  radius: "12.5% / 5.8%",
} as const;

type PhoneScreen = "chat" | "library";

type PhoneInHandProps = {
  className?: string;
};

/** Front-facing iPhone 14 Pro (Figma) — Cogniface UI in the screen punch. */
export function PhoneInHand({ className = "" }: PhoneInHandProps) {
  const reducedMotionPref = useReducedMotion();
  const reduced = reducedMotionPref === true;

  return (
    <div
      className={`relative mx-auto flex h-full w-full max-w-[360px] items-center justify-center ${className}`}
      aria-hidden
    >
      <div
        className="relative w-[min(310px,74vw)]"
        style={{
          aspectRatio: `${FRAME_W} / ${FRAME_H}`,
          filter: "drop-shadow(16px 24px 44px rgba(17, 45, 78, 0.2))",
        }}
      >
        {/* Screen content sits under the frame PNG. */}
        <div
          className="absolute z-0 overflow-hidden bg-[#F2F2F7]"
          style={{
            left: SCREEN.left,
            top: SCREEN.top,
            width: SCREEN.width,
            height: SCREEN.height,
            borderRadius: SCREEN.radius,
          }}
        >
          <CognifacePhoneScreen reduced={reduced} />
        </div>

        {/* Figma hardware frame overlays the screen (transparent punch). */}
        <Image
          src={FRAME_SRC}
          alt=""
          fill
          priority
          sizes="300px"
          className="pointer-events-none z-10 object-contain"
          draggable={false}
        />
      </div>
    </div>
  );
}

function CognifacePhoneScreen({ reduced }: { reduced: boolean }) {
  const [screen, setScreen] = useState<PhoneScreen>("chat");

  useEffect(() => {
    if (reduced) {
      setScreen("chat");
      return;
    }
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const loop = (next: PhoneScreen, delay: number) => {
      timer = setTimeout(() => {
        if (cancelled) return;
        setScreen(next);
        loop(next === "chat" ? "library" : "chat", 3200);
      }, delay);
    };
    loop("library", 2800);
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [reduced]);

  return (
    <div className="relative flex h-full min-h-0 flex-col bg-[#F2F2F7] text-black">
      {/* Top inset clears Dynamic Island from the Figma frame (no CSS status bar). */}
      <div className="shrink-0 pt-[8%]" />

      <div className="shrink-0 px-[7%] pb-[2%]">
        <p className="text-[0.55rem] font-semibold uppercase tracking-[0.12em] text-[#3F72AF]">
          Cogniface
        </p>
        <AnimatePresence mode="wait" initial={false}>
          <motion.h2
            key={screen}
            className="mt-[0.15rem] text-[1.15rem] font-bold leading-none tracking-tight"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.22 }}
          >
            {screen === "chat" ? "Rozmowa" : "Biblioteka"}
          </motion.h2>
        </AnimatePresence>
      </div>

      <div className="relative min-h-0 flex-1 overflow-hidden px-[6%]">
        <AnimatePresence mode="wait" initial={false}>
          {screen === "chat" ? (
            <motion.div
              key="chat"
              className="absolute inset-x-[6%] top-0 bottom-0 flex flex-col"
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -12 }}
              transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            >
              <p className="mb-[3%] text-[0.55rem] text-[#8E8E93]">Twoja baza wiedzy</p>

              <div className="mb-[2.5%] ml-auto max-w-[88%] rounded-[0.85rem] rounded-br-[0.25rem] bg-[#112D4E] px-[8%] py-[5%] text-[0.58rem] leading-snug text-white">
                Kto jest na zdjęciu z wakacji przy stole?
              </div>

              <div className="mb-[2%] max-w-[92%] text-[0.58rem] leading-snug text-[#112D4E]">
                Na zdjęciu widać Annę w czerwonej sukience — siedzi przy stole z rodziną.
              </div>

              <div className="mb-[2.5%] inline-flex w-fit items-center gap-[0.3rem] rounded-full bg-[#DBE2EF] px-[6%] py-[2%] text-[0.48rem] font-semibold text-[#3F72AF]">
                <BookMarked size={10} strokeWidth={2.2} />
                1 źródło
              </div>

              <div className="relative mb-[2%] aspect-[4/3] w-[72%] overflow-hidden rounded-[0.55rem] bg-[#D1D1D6] ring-1 ring-black/5">
                <Image
                  src={PHOTOS[2]}
                  alt=""
                  fill
                  sizes="140px"
                  className="object-cover"
                />
              </div>
            </motion.div>
          ) : (
            <motion.div
              key="library"
              className="absolute inset-x-[6%] top-0 bottom-0"
              initial={{ opacity: 0, x: 12 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 12 }}
              transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            >
              <div className="mb-[3%] rounded-[0.55rem] bg-[#E3E3E8] px-[6%] py-[3.5%] text-[0.55rem] text-[#8E8E93]">
                Szukaj folderów i rozmów
              </div>
              <div className="grid grid-cols-2 gap-[4%]">
                {PHOTOS.slice(0, 4).map((src) => (
                  <div
                    key={src}
                    className="relative aspect-square overflow-hidden rounded-[0.5rem] bg-[#D1D1D6]"
                  >
                    <Image src={src} alt="" fill sizes="120px" className="object-cover" />
                  </div>
                ))}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <div className="shrink-0 border-t border-black/5 bg-[#F9F9F9]/95">
        <div className="flex px-[2%] pb-[1%] pt-[2%]">
          {(
            [
              { label: "Biblioteka", Icon: FolderOpen, active: screen === "library" },
              { label: "Osoby", Icon: Users, active: false },
              { label: "Rozmowy", Icon: MessageCircle, active: screen === "chat" },
            ] as const
          ).map(({ label, Icon, active }) => (
            <div
              key={label}
              className={`flex flex-1 flex-col items-center gap-[0.15rem] py-[1%] text-[0.45rem] font-medium ${
                active ? "text-[#3F72AF]" : "text-[#8E8E93]"
              }`}
            >
              <Icon size={16} strokeWidth={active ? 2.1 : 1.7} />
              {label}
            </div>
          ))}
        </div>
        <div className="mx-auto mb-[3%] mt-[0.5%] h-[0.22rem] w-[36%] rounded-full bg-black/80" />
      </div>
    </div>
  );
}
