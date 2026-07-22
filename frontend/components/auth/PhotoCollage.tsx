"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { FolderOpen, MessageCircle, Signal, Users, Wifi, BatteryFull } from "lucide-react";
import {
  AnimatePresence,
  motion,
  useMotionTemplate,
  useMotionValue,
  useReducedMotion,
  useSpring,
  useTransform,
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

const COLLAGE_TILES = [
  { src: PHOTOS[0], left: "4%", top: "8%", w: "36%", h: "44%", rotate: -5, depth: 34, breathe: true, phase: 0 },
  { src: PHOTOS[1], left: "38%", top: "4%", w: "34%", h: "36%", rotate: 4, depth: 22, breathe: true, phase: 0.8 },
  { src: PHOTOS[2], left: "68%", top: "12%", w: "28%", h: "38%", rotate: -3, depth: 40, breathe: false, phase: 0.3 },
  { src: PHOTOS[3], left: "8%", top: "48%", w: "30%", h: "42%", rotate: 3, depth: 28, breathe: true, phase: 1.4 },
  { src: PHOTOS[4], left: "40%", top: "40%", w: "34%", h: "48%", rotate: -4, depth: 48, breathe: true, phase: 0.5 },
  { src: PHOTOS[5], left: "72%", top: "48%", w: "24%", h: "36%", rotate: 5, depth: 18, breathe: false, phase: 1.1 },
  { src: PHOTOS[6], left: "58%", top: "26%", w: "20%", h: "24%", rotate: -6, depth: 55, breathe: true, phase: 2 },
  { src: PHOTOS[7], left: "26%", top: "30%", w: "18%", h: "22%", rotate: 6, depth: 16, breathe: true, phase: 2.4 },
] as const;

type Phase = "collage" | "phone";

export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [phase, setPhase] = useState<Phase>("collage");
  const [finePointer, setFinePointer] = useState(false);

  const mx = useMotionValue(0);
  const my = useMotionValue(0);
  const springX = useSpring(mx, { stiffness: 90, damping: 18, mass: 0.4 });
  const springY = useSpring(my, { stiffness: 90, damping: 18, mass: 0.4 });

  useEffect(() => {
    const mq = window.matchMedia("(pointer: fine)");
    const sync = () => setFinePointer(mq.matches);
    sync();
    mq.addEventListener("change", sync);
    return () => mq.removeEventListener("change", sync);
  }, []);

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
        loop(next === "collage" ? "phone" : "collage", next === "collage" ? 5600 : 5000);
      }, delay);
    };

    loop("phone", 4800);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [reduced]);

  const onMove = (event: React.MouseEvent<HTMLDivElement>) => {
    if (reduced || !finePointer || phase !== "collage") return;
    const rect = event.currentTarget.getBoundingClientRect();
    mx.set((event.clientX - rect.left) / rect.width - 0.5);
    my.set((event.clientY - rect.top) / rect.height - 0.5);
  };

  const onLeave = () => {
    mx.set(0);
    my.set(0);
  };

  return (
    <div
      className="relative h-full min-h-[220px] w-full overflow-hidden bg-[#F9F7F7]"
      onMouseMove={onMove}
      onMouseLeave={onLeave}
      aria-hidden
    >
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 70% 55% at 28% 35%, #DBE2EF 0%, transparent 68%), radial-gradient(ellipse 45% 35% at 78% 72%, color-mix(in srgb, #3F72AF 14%, transparent), transparent 70%)",
        }}
      />

      <AnimatePresence mode="wait">
        {phase === "collage" ? (
          <motion.div
            key="collage"
            className="absolute inset-0"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{
              opacity: 0,
              scale: 0.78,
              filter: "blur(8px)",
              transition: { duration: 0.85, ease: [0.22, 1, 0.36, 1] },
            }}
            transition={{ duration: 0.55 }}
          >
            <AnimatedCollage
              springX={springX}
              springY={springY}
              reduced={!!reduced || !finePointer}
            />
            <HeroCaption>Animowany kolaż Twojej biblioteki</HeroCaption>
          </motion.div>
        ) : (
          <motion.div
            key="phone"
            className="absolute inset-0 flex items-center justify-center px-4"
            initial={{ opacity: 0, scale: 0.82, y: 40 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 1.06, y: -16 }}
            transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
          >
            <IPhoneMockup />
            <HeroCaption>Cogniface na iOS</HeroCaption>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function HeroCaption({ children }: { children: React.ReactNode }) {
  return (
    <motion.p
      className="pointer-events-none absolute bottom-5 left-0 right-0 z-30 text-center text-[0.7rem] font-semibold tracking-[0.04em] text-[#4A6B8A] lg:bottom-7"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.25, duration: 0.4 }}
    >
      {children}
    </motion.p>
  );
}

function AnimatedCollage({
  springX,
  springY,
  reduced,
}: {
  springX: ReturnType<typeof useSpring>;
  springY: ReturnType<typeof useSpring>;
  reduced: boolean;
}) {
  const sceneX = useTransform(springX, (v) => (reduced ? 0 : v * 18));
  const sceneY = useTransform(springY, (v) => (reduced ? 0 : v * 12));
  const sceneRotY = useTransform(springX, (v) => (reduced ? 0 : v * 10));
  const sceneRotX = useTransform(springY, (v) => (reduced ? 0 : v * -7));
  const sceneTransform = useMotionTemplate`perspective(1200px) translate3d(${sceneX}px, ${sceneY}px, 0) rotateX(${sceneRotX}deg) rotateY(${sceneRotY}deg)`;

  return (
    <motion.div
      className="absolute inset-0 px-6 py-8 lg:px-12 lg:py-12"
      style={{ transform: sceneTransform, transformStyle: "preserve-3d" }}
    >
      <div className="relative h-full w-full" style={{ transformStyle: "preserve-3d" }}>
        {COLLAGE_TILES.map((tile, index) => (
          <CollageTile
            key={tile.src}
            tile={tile}
            index={index}
            springX={springX}
            springY={springY}
            reduced={reduced}
          />
        ))}
      </div>
    </motion.div>
  );
}

function CollageTile({
  tile,
  index,
  springX,
  springY,
  reduced,
}: {
  tile: (typeof COLLAGE_TILES)[number];
  index: number;
  springX: ReturnType<typeof useSpring>;
  springY: ReturnType<typeof useSpring>;
  reduced: boolean;
}) {
  const x = useTransform(springX, (v) => (reduced ? 0 : v * tile.depth));
  const y = useTransform(springY, (v) => (reduced ? 0 : v * tile.depth * 0.8));
  const z = useTransform(springX, (v) => (reduced ? 0 : tile.depth * 0.9 + Math.abs(v) * 20));
  const transform = useMotionTemplate`translate3d(${x}px, ${y}px, ${z}px) rotate(${tile.rotate}deg)`;

  return (
    <motion.div
      className="absolute overflow-hidden rounded-[3px] bg-white shadow-[0_16px_48px_rgba(17,45,78,0.16)] ring-1 ring-black/[0.06]"
      style={{
        left: tile.left,
        top: tile.top,
        width: tile.w,
        height: tile.h,
        zIndex: Math.round(tile.depth),
        transformStyle: "preserve-3d",
        transform,
      }}
      initial={{ opacity: 0, y: 24, scale: 1 }}
      animate={{
        opacity: 1,
        y: reduced ? 0 : [0, index % 2 ? 5 : -7, 0],
        scale: reduced || !tile.breathe ? 1 : [1, 1.08, 1],
      }}
      transition={{
        opacity: { duration: 0.45, delay: index * 0.05 },
        y: {
          duration: 4.8 + index * 0.25,
          repeat: Infinity,
          ease: "easeInOut",
          delay: index * 0.15,
        },
        scale: {
          duration: 5.5 + tile.phase,
          repeat: Infinity,
          ease: "easeInOut",
          delay: tile.phase,
        },
      }}
    >
      <Image
        src={tile.src}
        alt=""
        fill
        sizes="32vw"
        className="object-cover"
        priority={index < 4}
      />
    </motion.div>
  );
}

function IPhoneMockup() {
  return (
    <div className="relative" style={{ perspective: "1200px" }}>
      <div
        className="pointer-events-none absolute left-1/2 top-1/2 h-[105%] w-[78%] -translate-x-1/2 -translate-y-1/2 rounded-full opacity-40 blur-3xl"
        style={{ background: "color-mix(in srgb, #3F72AF 40%, transparent)" }}
      />

      <motion.div
        className="relative mx-auto w-[min(292px,78vw)]"
        initial={{ rotateY: -16, rotateX: 6 }}
        animate={{ rotateY: 0, rotateX: 0 }}
        transition={{ duration: 0.9, ease: [0.22, 1, 0.36, 1] }}
        style={{ transformStyle: "preserve-3d" }}
      >
        {/* Titanium / black hardware frame */}
        <div
          className="relative rounded-[3rem] p-[11px]"
          style={{
            background:
              "linear-gradient(145deg, #3a3a3c 0%, #1c1c1e 42%, #2c2c2e 70%, #0a0a0a 100%)",
            boxShadow:
              "0 40px 90px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,255,255,0.18), inset 0 -1px 0 rgba(0,0,0,0.5)",
          }}
        >
          {/* Side buttons hint */}
          <div className="absolute -left-[3px] top-[118px] h-[28px] w-[3px] rounded-l-sm bg-[#2a2a2c]" />
          <div className="absolute -left-[3px] top-[160px] h-[56px] w-[3px] rounded-l-sm bg-[#2a2a2c]" />
          <div className="absolute -left-[3px] top-[228px] h-[56px] w-[3px] rounded-l-sm bg-[#2a2a2c]" />
          <div className="absolute -right-[3px] top-[180px] h-[78px] w-[3px] rounded-r-sm bg-[#2a2a2c]" />

          <div className="relative overflow-hidden rounded-[2.35rem] bg-black">
            {/* Screen */}
            <div className="relative flex h-[min(600px,74vh)] flex-col bg-[#F2F2F7]">
              {/* iOS status bar */}
              <div className="relative z-20 flex items-end justify-between px-7 pb-1 pt-3 text-[12px] font-semibold text-black">
                <span className="min-w-[54px] tracking-tight">9:41</span>
                <div className="absolute left-1/2 top-3 h-[26px] w-[100px] -translate-x-1/2 rounded-full bg-black" />
                <div className="flex min-w-[68px] items-center justify-end gap-[4px]">
                  <Signal size={13} strokeWidth={2.4} />
                  <Wifi size={14} strokeWidth={2.4} />
                  <BatteryFull size={18} strokeWidth={2} />
                </div>
              </div>

              {/* Large title (iOS) */}
              <div className="px-4 pb-2 pt-2">
                <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[#3F72AF]">
                  Cogniface
                </p>
                <h2 className="mt-0.5 text-[28px] font-bold leading-none tracking-tight text-black">
                  Biblioteka
                </h2>
              </div>

              {/* Search pill — iOS style */}
              <div className="mx-4 mb-3 flex items-center rounded-xl bg-[#E3E3E8] px-3 py-2 text-[14px] text-[#8E8E93]">
                Szukaj
              </div>

              <div className="min-h-0 flex-1 overflow-hidden px-4">
                <div className="grid grid-cols-2 gap-[6px]">
                  {PHOTOS.slice(0, 6).map((src, i) => (
                    <motion.div
                      key={src}
                      className="relative aspect-square overflow-hidden rounded-[10px] bg-[#D1D1D6]"
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.2 + i * 0.05, duration: 0.35 }}
                    >
                      <Image src={src} alt="" fill sizes="140px" className="object-cover" />
                    </motion.div>
                  ))}
                </div>

                <motion.div
                  className="mt-3 rounded-[14px] bg-white px-3.5 py-3 shadow-sm"
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.5, duration: 0.4 }}
                >
                  <p className="text-[11px] font-semibold text-[#8E8E93]">Rozmowy</p>
                  <p className="mt-1 text-[14px] leading-snug text-black">
                    Na zdjęciu widać dwie osoby obok siebie — w ogrodzie.
                  </p>
                </motion.div>
              </div>

              {/* iOS tab bar */}
              <div className="relative border-t border-black/5 bg-[#F9F9F9]/92 backdrop-blur-xl">
                <div className="flex px-1 pb-1 pt-1.5">
                  {[
                    { label: "Biblioteka", Icon: FolderOpen, active: true },
                    { label: "Osoby", Icon: Users, active: false },
                    { label: "Rozmowy", Icon: MessageCircle, active: false },
                  ].map(({ label, Icon, active }) => (
                    <div
                      key={label}
                      className={`flex flex-1 flex-col items-center gap-0.5 py-1 text-[10px] font-medium ${
                        active ? "text-[#3F72AF]" : "text-[#8E8E93]"
                      }`}
                    >
                      <Icon size={22} strokeWidth={active ? 2.1 : 1.7} />
                      {label}
                    </div>
                  ))}
                </div>
                {/* Home indicator */}
                <div className="mx-auto mb-2 mt-1 h-[5px] w-[120px] rounded-full bg-black/80" />
              </div>
            </div>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
