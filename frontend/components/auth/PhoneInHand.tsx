"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import {
  BookMarked,
  FolderOpen,
  MessageCircle,
  Signal,
  Users,
  Wifi,
  BatteryFull,
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

type PhoneScreen = "chat" | "library";

/**
 * Screen hole in `iphone-float-mockup.png` (1024×1536).
 * Tuned for the 3/4 floating product render.
 */
const SCREEN = {
  left: "27.8%",
  top: "13.1%",
  width: "50.5%",
  height: "71%",
  radius: "10.5% / 5%",
  /** Mild roll only — 3/4 perspective is already in the photo */
  transform: "rotate(2deg)",
} as const;

type PhoneInHandProps = {
  className?: string;
};

/** Floating iPhone product mockup — Cogniface UI composited into punched screen. */
export function PhoneInHand({ className = "" }: PhoneInHandProps) {
  const reducedMotionPref = useReducedMotion();
  const reduced = reducedMotionPref === true;
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setReady(true);
  }, []);

  return (
    <div
      className={`relative mx-auto flex h-full w-full max-w-[480px] items-center justify-center ${className}`}
      aria-hidden
    >
      <div
        className="pointer-events-none absolute left-1/2 top-[48%] h-[42%] w-[58%] -translate-x-1/2 -translate-y-1/2 rounded-full opacity-30 blur-3xl"
        style={{ background: "#3F72AF" }}
      />

      <motion.div
        className="relative aspect-[1024/1536] h-[min(94%,760px)] w-auto"
        initial={reduced || !ready ? false : { opacity: 0, y: 24, scale: 0.96 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.9, ease: [0.22, 1, 0.36, 1] }}
      >
        {ready && (
          <div
            className="absolute z-0 overflow-hidden bg-[#F2F2F7]"
            style={{
              left: SCREEN.left,
              top: SCREEN.top,
              width: SCREEN.width,
              height: SCREEN.height,
              borderRadius: SCREEN.radius,
              transform: SCREEN.transform,
              transformOrigin: "50% 50%",
              boxShadow: "inset 0 0 0 1px rgba(0,0,0,0.06)",
            }}
          >
            <CognifacePhoneScreen reduced={reduced} />
          </div>
        )}

        <Image
          src="/iphone-float-mockup.png"
          alt=""
          fill
          priority
          sizes="(max-width: 1024px) 60vw, 440px"
          className="pointer-events-none z-10 object-contain"
        />
      </motion.div>
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
      <div className="relative z-20 flex shrink-0 items-end justify-between px-[9%] pb-[1%] pt-[4.5%] text-[0.55rem] font-semibold leading-none">
        <span className="min-w-[2.2rem]">9:41</span>
        <div className="absolute left-1/2 top-[18%] h-[22%] w-[34%] -translate-x-1/2 rounded-full bg-black" />
        <div className="flex min-w-[2.6rem] items-center justify-end gap-[0.15rem]">
          <Signal size={9} strokeWidth={2.4} />
          <Wifi size={10} strokeWidth={2.4} />
          <BatteryFull size={12} strokeWidth={2} />
        </div>
      </div>

      <div className="shrink-0 px-[7%] pb-[2%] pt-[1%]">
        <p className="text-[0.45rem] font-semibold uppercase tracking-[0.14em] text-[#3F72AF]">
          Cogniface
        </p>
        <AnimatePresence mode="wait" initial={false}>
          <motion.h2
            key={screen}
            className="mt-[0.15rem] text-[0.95rem] font-bold leading-none tracking-tight"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.25 }}
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
              initial={{ opacity: 0, x: -16 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
            >
              <p className="mb-[4%] text-[0.42rem] text-[#8E8E93]">Twoja baza wiedzy</p>

              <div className="mb-[3%] ml-auto max-w-[88%] rounded-[0.65rem] rounded-br-[0.2rem] bg-[#112D4E] px-[8%] py-[5%] text-[0.48rem] leading-snug text-white">
                Kto jest na zdjęciu z wakacji przy stole?
              </div>

              <div className="mb-[2%] max-w-[92%] text-[0.48rem] leading-snug text-[#112D4E]">
                Na zdjęciu widać Annę w czerwonej sukience — siedzi przy stole z rodziną.
              </div>

              <div className="mb-[3%] inline-flex w-fit items-center gap-[0.25rem] rounded-full bg-[#DBE2EF] px-[6%] py-[2%] text-[0.4rem] font-semibold text-[#3F72AF]">
                <BookMarked size={8} strokeWidth={2.2} />
                1 źródło
              </div>

              <div className="relative mb-[2%] aspect-[4/3] w-[72%] overflow-hidden rounded-[0.45rem] bg-[#D1D1D6] ring-1 ring-black/5">
                <Image
                  src={PHOTOS[2]}
                  alt=""
                  fill
                  sizes="120px"
                  className="object-cover"
                />
              </div>
            </motion.div>
          ) : (
            <motion.div
              key="library"
              className="absolute inset-x-[6%] top-0 bottom-0"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 20 }}
              transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
            >
              <div className="mb-[3%] rounded-[0.45rem] bg-[#E3E3E8] px-[6%] py-[3.5%] text-[0.45rem] text-[#8E8E93]">
                Szukaj folderów i rozmów
              </div>
              <div className="grid grid-cols-2 gap-[4%]">
                {PHOTOS.slice(0, 4).map((src, i) => (
                  <motion.div
                    key={src}
                    className="relative aspect-square overflow-hidden rounded-[0.4rem] bg-[#D1D1D6]"
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.04 + i * 0.04, duration: 0.28 }}
                  >
                    <Image src={src} alt="" fill sizes="80px" className="object-cover" />
                  </motion.div>
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
              className={`flex flex-1 flex-col items-center gap-[0.1rem] py-[1%] text-[0.38rem] font-medium ${
                active ? "text-[#3F72AF]" : "text-[#8E8E93]"
              }`}
            >
              <Icon size={11} strokeWidth={active ? 2.2 : 1.7} />
              {label}
            </div>
          ))}
        </div>
        <div className="mx-auto mb-[2.5%] mt-[0.5%] h-[0.18rem] w-[38%] rounded-full bg-black/80" />
      </div>
    </div>
  );
}
