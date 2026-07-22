"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { FolderOpen, MessageCircle, Users } from "lucide-react";
import {
  AnimatePresence,
  motion,
  useReducedMotion,
} from "motion/react";

const PHOTOS = [
  "/collage/01.jpg",
  "/collage/02.jpg",
  "/collage/03.jpg",
  "/collage/04.jpg",
  "/collage/05.jpg",
  "/collage/06.jpg",
  "/collage/07.jpg",
  "/collage/08.jpg",
] as const;

/** Elegant scattered collage positions (percent of stage) */
const COLLAGE_LAYOUT = [
  { src: PHOTOS[0], left: "6%", top: "10%", w: "34%", h: "42%", rotate: -4, z: 2 },
  { src: PHOTOS[1], left: "38%", top: "6%", w: "36%", h: "38%", rotate: 3, z: 3 },
  { src: PHOTOS[2], left: "68%", top: "14%", w: "26%", h: "36%", rotate: -2, z: 2 },
  { src: PHOTOS[3], left: "10%", top: "48%", w: "28%", h: "40%", rotate: 2.5, z: 4 },
  { src: PHOTOS[4], left: "40%", top: "42%", w: "32%", h: "44%", rotate: -3, z: 5 },
  { src: PHOTOS[5], left: "70%", top: "48%", w: "24%", h: "34%", rotate: 4, z: 3 },
  { src: PHOTOS[6], left: "58%", top: "28%", w: "18%", h: "22%", rotate: -5, z: 6 },
  { src: PHOTOS[7], left: "28%", top: "32%", w: "16%", h: "20%", rotate: 5, z: 1 },
] as const;

type Phase = "collage" | "phone";

/**
 * Landing visual: elegant photo collage → morph into mobile app preview.
 * Loops: collage (hold) → phone (hold) → collage…
 */
export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [phase, setPhase] = useState<Phase>("collage");

  useEffect(() => {
    if (reduced) {
      setPhase("phone");
      return;
    }
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;

    const loop = (next: Phase, delay: number) => {
      timer = setTimeout(() => {
        if (cancelled) return;
        setPhase(next);
        loop(next === "collage" ? "phone" : "collage", next === "collage" ? 5200 : 4800);
      }, delay);
    };

    loop("phone", 4200);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [reduced]);

  return (
    <div className="relative h-full min-h-[220px] w-full overflow-hidden bg-[#F9F7F7]">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 80% 60% at 30% 40%, color-mix(in srgb, #DBE2EF 70%, transparent), transparent 70%), radial-gradient(ellipse 50% 40% at 80% 70%, color-mix(in srgb, #3F72AF 10%, transparent), transparent 65%)",
        }}
      />

      <AnimatePresence mode="wait">
        {phase === "collage" ? (
          <motion.div
            key="collage"
            className="absolute inset-0"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, scale: 0.92, filter: "blur(6px)" }}
            transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
          >
            <ElegantCollage reduced={!!reduced} />
            <Caption label="Twoja biblioteka zdjęć" />
          </motion.div>
        ) : (
          <motion.div
            key="phone"
            className="absolute inset-0 flex items-center justify-center"
            initial={{ opacity: 0, scale: 0.88, y: 24 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 1.04, y: -12 }}
            transition={{ duration: 0.75, ease: [0.22, 1, 0.36, 1] }}
          >
            <PhonePreview />
            <Caption label="Tak działa Cogniface na telefonie" />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function Caption({ label }: { label: string }) {
  return (
    <motion.p
      className="pointer-events-none absolute bottom-6 left-0 right-0 text-center text-xs font-semibold tracking-wide text-ink-muted lg:bottom-8"
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.35, duration: 0.45 }}
    >
      {label}
    </motion.p>
  );
}

function ElegantCollage({ reduced }: { reduced: boolean }) {
  return (
    <div className="absolute inset-0 px-8 py-10 lg:px-14 lg:py-14">
      <div className="relative h-full w-full">
        {COLLAGE_LAYOUT.map((tile, index) => (
          <motion.div
            key={tile.src}
            className="absolute overflow-hidden rounded-[4px] bg-white shadow-[0_12px_40px_rgba(17,45,78,0.14)] ring-1 ring-black/5"
            style={{
              left: tile.left,
              top: tile.top,
              width: tile.w,
              height: tile.h,
              zIndex: tile.z,
            }}
            initial={{ opacity: 0, y: 18, rotate: tile.rotate - 4 }}
            animate={
              reduced
                ? { opacity: 1, y: 0, rotate: tile.rotate }
                : {
                    opacity: 1,
                    y: [0, index % 2 === 0 ? -6 : 5, 0],
                    rotate: tile.rotate,
                    transition: {
                      opacity: { duration: 0.5, delay: index * 0.06 },
                      y: {
                        duration: 5.5 + index * 0.35,
                        repeat: Infinity,
                        ease: "easeInOut",
                        delay: index * 0.2,
                      },
                      rotate: { duration: 0.6, delay: index * 0.06 },
                    },
                  }
            }
          >
            <Image
              src={tile.src}
              alt=""
              fill
              sizes="30vw"
              className="object-cover"
              priority={index < 4}
            />
          </motion.div>
        ))}
      </div>
    </div>
  );
}

function PhonePreview() {
  return (
    <div className="relative">
      {/* Soft glow behind device */}
      <div
        className="pointer-events-none absolute left-1/2 top-1/2 h-[120%] w-[90%] -translate-x-1/2 -translate-y-1/2 rounded-full opacity-50 blur-3xl"
        style={{ background: "color-mix(in srgb, #3F72AF 35%, transparent)" }}
      />

      <motion.div
        className="relative mx-auto w-[min(280px,72vw)] overflow-hidden rounded-[2rem] border border-[#1a2f4a] bg-[#0b1728] p-[10px] shadow-[0_30px_80px_rgba(7,16,28,0.45)]"
        initial={{ rotateY: -12 }}
        animate={{ rotateY: 0 }}
        transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
        style={{ transformStyle: "preserve-3d", perspective: 900 }}
      >
        {/* Dynamic Island */}
        <div className="absolute left-1/2 top-[18px] z-20 h-[22px] w-[92px] -translate-x-1/2 rounded-full bg-black" />

        <div className="relative flex h-[min(560px,72vh)] flex-col overflow-hidden rounded-[1.45rem] bg-[#F9F7F7]">
          <header className="shrink-0 border-b border-[#DBE2EF] px-4 pb-3 pt-10">
            <p className="font-display text-[0.65rem] font-bold uppercase tracking-[0.14em] text-[#3F72AF]">
              Cogniface
            </p>
            <h2 className="font-display mt-0.5 text-xl font-bold tracking-tight text-[#112D4E]">
              Biblioteka
            </h2>
          </header>

          <div className="min-h-0 flex-1 overflow-hidden p-3">
            <div className="grid grid-cols-2 gap-2">
              {PHOTOS.slice(0, 6).map((src, i) => (
                <motion.div
                  key={src}
                  className="relative aspect-square overflow-hidden rounded-md bg-[#DBE2EF]"
                  initial={{ opacity: 0, scale: 0.92 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: 0.15 + i * 0.05, duration: 0.35 }}
                >
                  <Image src={src} alt="" fill sizes="140px" className="object-cover" />
                </motion.div>
              ))}
            </div>

            <motion.div
              className="mt-3 rounded-xl border border-[#DBE2EF] bg-white px-3 py-2.5 shadow-sm"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.45, duration: 0.4 }}
            >
              <p className="text-[0.65rem] font-semibold uppercase tracking-wide text-[#4A6B8A]">
                Ostatnia rozmowa
              </p>
              <p className="mt-1 text-[0.8rem] leading-snug text-[#112D4E]">
                Na zdjęciu widać dwie osoby obok siebie — w ogrodzie.
              </p>
            </motion.div>
          </div>

          <nav className="flex shrink-0 border-t border-[#DBE2EF] bg-white/90 px-2 pb-3 pt-2 backdrop-blur-sm">
            {[
              { label: "Biblioteka", Icon: FolderOpen, active: true },
              { label: "Osoby", Icon: Users, active: false },
              { label: "Rozmowy", Icon: MessageCircle, active: false },
            ].map(({ label, Icon, active }) => (
              <div
                key={label}
                className={`flex flex-1 flex-col items-center gap-0.5 rounded-xl py-1.5 text-[0.65rem] font-semibold ${
                  active ? "bg-[#E8EEF6] text-[#3F72AF]" : "text-[#4A6B8A]"
                }`}
              >
                <Icon size={16} strokeWidth={active ? 2.3 : 1.9} />
                {label}
              </div>
            ))}
          </nav>
        </div>
      </motion.div>
    </div>
  );
}
