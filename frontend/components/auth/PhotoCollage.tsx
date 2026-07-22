"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { FolderOpen, MessageCircle, Signal, Users, Wifi, BatteryFull } from "lucide-react";
import {
  AnimatePresence,
  motion,
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

/**
 * Idle-first collage: each tile has its own continuous float / drift / breathe.
 * Cursor parallax is a light additive nudge only.
 */
const TILES = [
  {
    src: PHOTOS[0],
    x: "8%",
    y: "12%",
    w: "31%",
    h: "40%",
    rotate: -3,
    z: 2,
    floatY: [-10, 8, -10],
    floatX: [0, 6, 0],
    rot: [-3, -1, -3],
    scale: [1, 1.04, 1],
    duration: 7.2,
    delay: 0,
  },
  {
    src: PHOTOS[1],
    x: "42%",
    y: "8%",
    w: "33%",
    h: "34%",
    rotate: 2.5,
    z: 3,
    floatY: [6, -12, 6],
    floatX: [0, -8, 0],
    rot: [2.5, 4.5, 2.5],
    scale: [1, 1.06, 1],
    duration: 8.4,
    delay: 0.4,
  },
  {
    src: PHOTOS[2],
    x: "72%",
    y: "16%",
    w: "22%",
    h: "32%",
    rotate: -2,
    z: 2,
    floatY: [-8, 10, -8],
    floatX: [4, -4, 4],
    rot: [-2, 0, -2],
    scale: [1, 1.05, 1],
    duration: 6.6,
    delay: 0.9,
  },
  {
    src: PHOTOS[3],
    x: "6%",
    y: "52%",
    w: "28%",
    h: "36%",
    rotate: 2,
    z: 4,
    floatY: [8, -9, 8],
    floatX: [-5, 5, -5],
    rot: [2, 3.5, 2],
    scale: [1, 1.07, 1],
    duration: 9.1,
    delay: 0.2,
  },
  {
    src: PHOTOS[4],
    x: "38%",
    y: "44%",
    w: "34%",
    h: "42%",
    rotate: -2.5,
    z: 5,
    floatY: [-12, 7, -12],
    floatX: [0, 10, 0],
    rot: [-2.5, -0.5, -2.5],
    scale: [1, 1.05, 1],
    duration: 7.8,
    delay: 0.6,
  },
  {
    src: PHOTOS[5],
    x: "74%",
    y: "52%",
    w: "20%",
    h: "30%",
    rotate: 3.5,
    z: 3,
    floatY: [5, -11, 5],
    floatX: [-6, 3, -6],
    rot: [3.5, 5, 3.5],
    scale: [1, 1.08, 1],
    duration: 6.9,
    delay: 1.2,
  },
  {
    src: PHOTOS[6],
    x: "62%",
    y: "30%",
    w: "16%",
    h: "20%",
    rotate: -4,
    z: 6,
    floatY: [-6, 9, -6],
    floatX: [7, -7, 7],
    rot: [-4, -2, -4],
    scale: [1, 1.1, 1],
    duration: 5.8,
    delay: 0.3,
  },
  {
    src: PHOTOS[7],
    x: "28%",
    y: "28%",
    w: "15%",
    h: "18%",
    rotate: 4,
    z: 1,
    floatY: [7, -6, 7],
    floatX: [-4, 8, -4],
    rot: [4, 6, 4],
    scale: [1, 1.09, 1],
    duration: 8.0,
    delay: 1.5,
  },
] as const;

type Phase = "collage" | "phone";

export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [phase, setPhase] = useState<Phase>("collage");
  const [finePointer, setFinePointer] = useState(false);

  const mx = useMotionValue(0);
  const my = useMotionValue(0);
  const springX = useSpring(mx, { stiffness: 60, damping: 20, mass: 0.5 });
  const springY = useSpring(my, { stiffness: 60, damping: 20, mass: 0.5 });
  const nudgeX = useTransform(springX, (v) => (reduced || !finePointer ? 0 : v * 14));
  const nudgeY = useTransform(springY, (v) => (reduced || !finePointer ? 0 : v * 10));

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
        loop(next === "collage" ? "phone" : "collage", next === "collage" ? 6000 : 5200);
      }, delay);
    };
    loop("phone", 7000);
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
      className="relative h-full min-h-[220px] w-full overflow-hidden bg-[#F4F1EE]"
      onMouseMove={onMove}
      onMouseLeave={onLeave}
      aria-hidden
    >
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 65% 50% at 40% 40%, #E7EEF7 0%, transparent 70%), linear-gradient(180deg, #F9F7F7 0%, #EDE8E3 100%)",
        }}
      />

      <AnimatePresence mode="wait">
        {phase === "collage" ? (
          <motion.div
            key="collage"
            className="absolute inset-0"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, scale: 0.9, filter: "blur(10px)" }}
            transition={{ duration: 0.75, ease: [0.22, 1, 0.36, 1] }}
          >
            <motion.div className="absolute inset-0" style={{ x: nudgeX, y: nudgeY }}>
              {TILES.map((tile, index) => (
                <motion.div
                  key={tile.src}
                  className="absolute overflow-hidden rounded-sm bg-white"
                  style={{
                    left: tile.x,
                    top: tile.y,
                    width: tile.w,
                    height: tile.h,
                    zIndex: tile.z,
                    boxShadow: "0 18px 50px rgba(17,45,78,0.12), 0 2px 8px rgba(17,45,78,0.06)",
                  }}
                  initial={{ opacity: 0, scale: 0.92, rotate: tile.rotate }}
                  animate={
                    reduced
                      ? { opacity: 1, scale: 1, rotate: tile.rotate, x: 0, y: 0 }
                      : {
                          opacity: 1,
                          x: tile.floatX,
                          y: tile.floatY,
                          rotate: tile.rot,
                          scale: tile.scale,
                        }
                  }
                  transition={
                    reduced
                      ? { duration: 0.4, delay: index * 0.05 }
                      : {
                          opacity: { duration: 0.55, delay: index * 0.07 },
                          x: {
                            duration: tile.duration,
                            repeat: Infinity,
                            ease: "easeInOut",
                            delay: tile.delay,
                          },
                          y: {
                            duration: tile.duration * 1.05,
                            repeat: Infinity,
                            ease: "easeInOut",
                            delay: tile.delay,
                          },
                          rotate: {
                            duration: tile.duration * 1.15,
                            repeat: Infinity,
                            ease: "easeInOut",
                            delay: tile.delay,
                          },
                          scale: {
                            duration: tile.duration * 0.95,
                            repeat: Infinity,
                            ease: "easeInOut",
                            delay: tile.delay + 0.2,
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
            </motion.div>
            <Caption>Biblioteka w ruchu</Caption>
          </motion.div>
        ) : (
          <motion.div
            key="phone"
            className="absolute inset-0 flex items-center justify-center px-4"
            initial={{ opacity: 0, y: 36, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -12, scale: 1.04 }}
            transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
          >
            <IPhoneMockup />
            <Caption>Cogniface na iOS</Caption>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function Caption({ children }: { children: React.ReactNode }) {
  return (
    <motion.p
      className="pointer-events-none absolute bottom-5 left-0 right-0 z-30 text-center text-[0.7rem] font-semibold tracking-[0.06em] text-[#4A6B8A] lg:bottom-7"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ delay: 0.3 }}
    >
      {children}
    </motion.p>
  );
}

function IPhoneMockup() {
  return (
    <div className="relative">
      <div
        className="pointer-events-none absolute left-1/2 top-1/2 h-[100%] w-[70%] -translate-x-1/2 -translate-y-1/2 rounded-full opacity-35 blur-3xl"
        style={{ background: "#3F72AF" }}
      />
      <motion.div
        className="relative mx-auto w-[min(292px,78vw)]"
        initial={{ rotateY: -14, rotateX: 5 }}
        animate={{ rotateY: 0, rotateX: 0 }}
        transition={{ duration: 0.85, ease: [0.22, 1, 0.36, 1] }}
        style={{ transformStyle: "preserve-3d", perspective: 1200 }}
      >
        <div
          className="relative rounded-[3rem] p-[11px]"
          style={{
            background: "linear-gradient(145deg, #3a3a3c 0%, #1c1c1e 45%, #0a0a0a 100%)",
            boxShadow:
              "0 40px 90px rgba(0,0,0,0.32), inset 0 1px 0 rgba(255,255,255,0.16)",
          }}
        >
          <div className="absolute -left-[3px] top-[118px] h-[28px] w-[3px] rounded-l-sm bg-[#2a2a2c]" />
          <div className="absolute -left-[3px] top-[160px] h-[56px] w-[3px] rounded-l-sm bg-[#2a2a2c]" />
          <div className="absolute -left-[3px] top-[228px] h-[56px] w-[3px] rounded-l-sm bg-[#2a2a2c]" />
          <div className="absolute -right-[3px] top-[180px] h-[78px] w-[3px] rounded-r-sm bg-[#2a2a2c]" />

          <div className="relative overflow-hidden rounded-[2.35rem] bg-black">
            <div className="relative flex h-[min(600px,74vh)] flex-col bg-[#F2F2F7]">
              <div className="relative z-20 flex items-end justify-between px-7 pb-1 pt-3 text-[12px] font-semibold text-black">
                <span className="min-w-[54px]">9:41</span>
                <div className="absolute left-1/2 top-3 h-[26px] w-[100px] -translate-x-1/2 rounded-full bg-black" />
                <div className="flex min-w-[68px] items-center justify-end gap-[4px]">
                  <Signal size={13} strokeWidth={2.4} />
                  <Wifi size={14} strokeWidth={2.4} />
                  <BatteryFull size={18} strokeWidth={2} />
                </div>
              </div>

              <div className="px-4 pb-2 pt-2">
                <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[#3F72AF]">
                  Cogniface
                </p>
                <h2 className="mt-0.5 text-[28px] font-bold leading-none tracking-tight text-black">
                  Biblioteka
                </h2>
              </div>

              <div className="mx-4 mb-3 rounded-xl bg-[#E3E3E8] px-3 py-2 text-[14px] text-[#8E8E93]">
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
                      transition={{ delay: 0.15 + i * 0.05, duration: 0.35 }}
                    >
                      <Image src={src} alt="" fill sizes="140px" className="object-cover" />
                    </motion.div>
                  ))}
                </div>
                <motion.div
                  className="mt-3 rounded-[14px] bg-white px-3.5 py-3 shadow-sm"
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.45, duration: 0.4 }}
                >
                  <p className="text-[11px] font-semibold text-[#8E8E93]">Rozmowy</p>
                  <p className="mt-1 text-[14px] leading-snug text-black">
                    Na zdjęciu widać dwie osoby obok siebie — w ogrodzie.
                  </p>
                </motion.div>
              </div>

              <div className="border-t border-black/5 bg-[#F9F9F9]/92 backdrop-blur-xl">
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
                <div className="mx-auto mb-2 mt-1 h-[5px] w-[120px] rounded-full bg-black/80" />
              </div>
            </div>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
