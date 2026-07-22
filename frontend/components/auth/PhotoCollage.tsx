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
 * Round tiles on a Z-axis: some retreat (back), some advance (forward).
 * Perspective makes forward look larger and back look smaller.
 * Cursor is only a light scene nudge.
 */
const TILES: {
  src: string;
  x: string;
  y: string;
  size: string;
  z: number;
  /** Z keyframes in px — negative = farther (smaller), positive = closer (larger) */
  depth: number[];
  driftX: number[];
  driftY: number[];
  duration: number;
  delay: number;
}[] = [
  {
    src: PHOTOS[0],
    x: "10%",
    y: "14%",
    size: "min(210px, 28%)",
    z: 3,
    depth: [20, -80, 20],
    driftX: [0, 10, 0],
    driftY: [0, -12, 0],
    duration: 7.5,
    delay: 0,
  },
  {
    src: PHOTOS[1],
    x: "42%",
    y: "8%",
    size: "min(240px, 32%)",
    z: 4,
    depth: [-40, 70, -40],
    driftX: [0, -14, 0],
    driftY: [0, 8, 0],
    duration: 8.8,
    delay: 0.5,
  },
  {
    src: PHOTOS[2],
    x: "72%",
    y: "16%",
    size: "min(180px, 24%)",
    z: 2,
    depth: [50, -90, 50],
    driftX: [0, 8, 0],
    driftY: [0, -10, 0],
    duration: 6.8,
    delay: 1.0,
  },
  {
    src: PHOTOS[3],
    x: "8%",
    y: "52%",
    size: "min(200px, 26%)",
    z: 5,
    depth: [-70, 55, -70],
    driftX: [0, 12, 0],
    driftY: [0, 6, 0],
    duration: 9.2,
    delay: 0.3,
  },
  {
    src: PHOTOS[4],
    x: "40%",
    y: "46%",
    size: "min(250px, 34%)",
    z: 6,
    depth: [30, -100, 30],
    driftX: [0, -10, 0],
    driftY: [0, -8, 0],
    duration: 7.9,
    delay: 0.7,
  },
  {
    src: PHOTOS[5],
    x: "74%",
    y: "54%",
    size: "min(170px, 22%)",
    z: 3,
    depth: [-30, 85, -30],
    driftX: [0, -8, 0],
    driftY: [0, 12, 0],
    duration: 6.5,
    delay: 1.3,
  },
  {
    src: PHOTOS[6],
    x: "62%",
    y: "32%",
    size: "min(130px, 17%)",
    z: 7,
    depth: [80, -50, 80],
    driftX: [0, 14, 0],
    driftY: [0, -6, 0],
    duration: 5.6,
    delay: 0.2,
  },
  {
    src: PHOTOS[7],
    x: "28%",
    y: "30%",
    size: "min(120px, 15%)",
    z: 1,
    depth: [-90, 40, -90],
    driftX: [0, -6, 0],
    driftY: [0, 10, 0],
    duration: 8.1,
    delay: 1.6,
  },
];

type Phase = "collage" | "phone";

export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [phase, setPhase] = useState<Phase>("collage");
  const [finePointer, setFinePointer] = useState(false);

  const mx = useMotionValue(0);
  const my = useMotionValue(0);
  const springX = useSpring(mx, { stiffness: 55, damping: 20, mass: 0.5 });
  const springY = useSpring(my, { stiffness: 55, damping: 20, mass: 0.5 });
  const nudgeX = useTransform(springX, (v) => (reduced || !finePointer ? 0 : v * 12));
  const nudgeY = useTransform(springY, (v) => (reduced || !finePointer ? 0 : v * 8));

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
    loop("phone", 7500);
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
            exit={{ opacity: 0, scale: 0.92, filter: "blur(8px)" }}
            transition={{ duration: 0.75, ease: [0.22, 1, 0.36, 1] }}
          >
            <motion.div
              className="absolute inset-0"
              style={{
                x: nudgeX,
                y: nudgeY,
                perspective: 900,
                transformStyle: "preserve-3d",
              }}
            >
              {TILES.map((tile, index) => (
                <motion.div
                  key={tile.src}
                  className="absolute overflow-hidden rounded-full bg-white"
                  style={{
                    left: tile.x,
                    top: tile.y,
                    width: tile.size,
                    aspectRatio: "1 / 1",
                    zIndex: tile.z,
                    boxShadow:
                      "0 20px 50px rgba(17,45,78,0.14), 0 0 0 3px rgba(255,255,255,0.65)",
                    transformStyle: "preserve-3d",
                  }}
                  initial={{ opacity: 0, z: 0 }}
                  animate={
                    reduced
                      ? { opacity: 1, x: 0, y: 0, z: 0 }
                      : {
                          opacity: 1,
                          x: tile.driftX,
                          y: tile.driftY,
                          z: tile.depth,
                        }
                  }
                  transition={
                    reduced
                      ? { duration: 0.4, delay: index * 0.05 }
                      : {
                          opacity: { duration: 0.5, delay: index * 0.06 },
                          x: {
                            duration: tile.duration,
                            repeat: Infinity,
                            ease: "easeInOut",
                            delay: tile.delay,
                          },
                          y: {
                            duration: tile.duration * 1.08,
                            repeat: Infinity,
                            ease: "easeInOut",
                            delay: tile.delay,
                          },
                          z: {
                            duration: tile.duration,
                            repeat: Infinity,
                            ease: "easeInOut",
                            delay: tile.delay,
                          },
                        }
                  }
                >
                  <Image
                    src={tile.src}
                    alt=""
                    fill
                    sizes="240px"
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
