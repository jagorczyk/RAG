"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import {
  ChevronRight,
  FolderOpen,
  MessageCircle,
  Signal,
  Users,
  Wifi,
  BatteryFull,
} from "lucide-react";
import {
  AnimatePresence,
  motion,
  useMotionTemplate,
  useMotionValue,
  useReducedMotion,
  useSpring,
  useTransform,
  type MotionValue,
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

const FOLDERS = [
  { name: "Wakacje 2024", count: 128, cover: PHOTOS[0] },
  { name: "Rodzina", count: 86, cover: PHOTOS[1] },
  { name: "Praca", count: 42, cover: PHOTOS[3] },
  { name: "Wydarzenia", count: 57, cover: PHOTOS[4] },
  { name: "Archiwum", count: 203, cover: PHOTOS[6] },
] as const;

type CloudTile = {
  id: string;
  src: string;
  x: number;
  y: number;
  z: number;
  size: number;
};

/**
 * Cinema 4D–style photo cloud: planes scattered in X/Y/Z (spherical cluster).
 * Parent rotates on Y → left/right parallax from depth differences.
 */
function buildPhotoCloud(): CloudTile[] {
  const tiles: CloudTile[] = [];
  let n = 0;
  // 3 depth layers (back / mid / front), ring distribution like C4D front+top views
  const layers = [
    { z: -120, count: 7, radius: 150, size: 64 },
    { z: -20, count: 8, radius: 125, size: 72 },
    { z: 90, count: 6, radius: 95, size: 68 },
  ];

  for (const layer of layers) {
    for (let j = 0; j < layer.count; j++) {
      const t = j / layer.count;
      const angle = t * Math.PI * 2 + layer.z * 0.01;
      const jitterR = ((j * 17) % 7) - 3;
      const jitterZ = ((j * 13) % 11) - 5;
      const r = layer.radius + jitterR * 4;
      tiles.push({
        id: `t-${n}`,
        src: PHOTOS[n % PHOTOS.length],
        x: Math.cos(angle) * r,
        y: Math.sin(angle) * r * 0.62,
        z: layer.z + jitterZ * 8,
        size: layer.size,
      });
      n += 1;
    }
  }

  // A few off-ring cards for organic cloud (like C4D scatter)
  const extras = [
    { x: -40, y: -20, z: 40, size: 70 },
    { x: 55, y: 30, z: -60, size: 62 },
    { x: 10, y: -70, z: 10, size: 66 },
  ];
  for (const e of extras) {
    tiles.push({
      id: `t-${n}`,
      src: PHOTOS[n % PHOTOS.length],
      ...e,
    });
    n += 1;
  }

  return tiles;
}

const CLOUD = buildPhotoCloud();

type Phase = "collage" | "phone";
type PhoneScreen = "photos" | "folders";

export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [phase, setPhase] = useState<Phase>("collage");
  const [finePointer, setFinePointer] = useState(false);

  const mx = useMotionValue(0);
  const springX = useSpring(mx, { stiffness: 45, damping: 20, mass: 0.6 });
  const autoYaw = useMotionValue(0);
  const yaw = useTransform([autoYaw, springX], ([auto, cursor]) => {
    if (reduced) return 0;
    const cursorBoost = finePointer ? (cursor as number) * 28 : 0;
    return (auto as number) + cursorBoost;
  });
  const sceneTransform = useMotionTemplate`rotateY(${yaw}deg)`;

  useEffect(() => {
    const mq = window.matchMedia("(pointer: fine)");
    const sync = () => setFinePointer(mq.matches);
    sync();
    mq.addEventListener("change", sync);
    return () => mq.removeEventListener("change", sync);
  }, []);

  useEffect(() => {
    if (reduced) {
      autoYaw.set(0);
      return;
    }
    let raf = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const t = (now - start) / 1000;
      autoYaw.set(Math.sin(t * 0.4) * 32);
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [reduced, autoYaw]);

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
        loop(next === "collage" ? "phone" : "collage", next === "collage" ? 7000 : 5500);
      }, delay);
    };
    loop("phone", 8500);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [reduced]);

  const onMove = (event: React.MouseEvent<HTMLDivElement>) => {
    if (reduced || !finePointer || phase !== "collage") return;
    const rect = event.currentTarget.getBoundingClientRect();
    mx.set((event.clientX - rect.left) / rect.width - 0.5);
  };

  const onLeave = () => mx.set(0);

  const sortedCloud = useMemo(
    () => [...CLOUD].sort((a, b) => a.z - b.z),
    []
  );

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
            "radial-gradient(ellipse 70% 55% at 50% 45%, #E7EEF7 0%, transparent 68%), linear-gradient(180deg, #F9F7F7 0%, #EDE8E3 100%)",
        }}
      />

      <AnimatePresence mode="wait">
        {phase === "collage" ? (
          <motion.div
            key="collage"
            className="absolute inset-0"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, scale: 0.94, filter: "blur(8px)" }}
            transition={{ duration: 0.75, ease: [0.22, 1, 0.36, 1] }}
          >
            <div
              className="absolute inset-0 flex items-center justify-center"
              style={{ perspective: 1000 }}
            >
              <motion.div
                className="relative h-[420px] w-[480px]"
                style={{
                  transformStyle: "preserve-3d",
                  transform: sceneTransform,
                }}
              >
                {sortedCloud.map((tile, index) => (
                  <CloudCard
                    key={tile.id}
                    tile={tile}
                    index={index}
                    yaw={yaw}
                    reduced={!!reduced}
                  />
                ))}
              </motion.div>
            </div>

            <div className="pointer-events-none absolute inset-y-0 left-0 w-14 bg-gradient-to-r from-[#F4F1EE] to-transparent lg:w-20" />
            <div className="pointer-events-none absolute inset-y-0 right-0 w-14 bg-gradient-to-l from-[#F4F1EE] to-transparent lg:w-20" />
            <div className="pointer-events-none absolute inset-x-0 top-0 h-12 bg-gradient-to-b from-[#F4F1EE] to-transparent" />
            <div className="pointer-events-none absolute inset-x-0 bottom-0 h-14 bg-gradient-to-t from-[#F4F1EE] to-transparent" />

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
            <IPhoneMockup reduced={!!reduced} />
            <Caption>Cogniface na iOS</Caption>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function CloudCard({
  tile,
  index,
  yaw,
  reduced,
}: {
  tile: CloudTile;
  index: number;
  yaw: MotionValue<number>;
  reduced: boolean;
}) {
  // Face the camera as the cloud rotates (billboard-ish cancel of parent yaw)
  const face = useTransform(yaw, (y) => (reduced ? 0 : -y * 0.35));
  const transform = useMotionTemplate`translate3d(${tile.x}px, ${tile.y}px, ${tile.z}px) rotateY(${face}deg)`;

  return (
    <motion.div
      className="absolute overflow-hidden rounded-[10px] bg-[#DBE2EF] shadow-[0_12px_32px_rgba(17,45,78,0.16)] ring-1 ring-white/50"
      style={{
        width: tile.size,
        height: tile.size,
        left: "50%",
        top: "50%",
        marginLeft: -tile.size / 2,
        marginTop: -tile.size / 2,
        transformStyle: "preserve-3d",
        transform,
        zIndex: Math.round(tile.z + 200),
      }}
      initial={{ opacity: 0, scale: 0.85 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.45, delay: index * 0.03, ease: [0.22, 1, 0.36, 1] }}
    >
      <Image
        src={tile.src}
        alt=""
        fill
        sizes="80px"
        className="object-cover object-top"
        priority={index < 10}
      />
    </motion.div>
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

function IPhoneMockup({ reduced }: { reduced: boolean }) {
  const [screen, setScreen] = useState<PhoneScreen>("photos");

  useEffect(() => {
    if (reduced) {
      setScreen("folders");
      return;
    }
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;
    const loop = (next: PhoneScreen, delay: number) => {
      timer = setTimeout(() => {
        if (cancelled) return;
        setScreen(next);
        loop(next === "photos" ? "folders" : "photos", 2800);
      }, delay);
    };
    loop("folders", 2200);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [reduced]);

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
                <AnimatePresence mode="wait" initial={false}>
                  <motion.h2
                    key={screen}
                    className="mt-0.5 text-[28px] font-bold leading-none tracking-tight text-black"
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -6 }}
                    transition={{ duration: 0.28 }}
                  >
                    {screen === "photos" ? "Biblioteka" : "Foldery"}
                  </motion.h2>
                </AnimatePresence>
              </div>

              <div className="mx-4 mb-3 rounded-xl bg-[#E3E3E8] px-3 py-2 text-[14px] text-[#8E8E93]">
                Szukaj
              </div>

              <div className="relative min-h-0 flex-1 overflow-hidden px-4">
                <AnimatePresence mode="wait" initial={false}>
                  {screen === "photos" ? (
                    <motion.div
                      key="photos"
                      className="absolute inset-x-4 top-0 bottom-0"
                      initial={{ opacity: 0, x: -28 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: -36 }}
                      transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
                    >
                      <div className="grid grid-cols-2 gap-[6px]">
                        {PHOTOS.slice(0, 6).map((src, i) => (
                          <motion.div
                            key={src}
                            className="relative aspect-square overflow-hidden rounded-[10px] bg-[#D1D1D6]"
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: 0.05 + i * 0.04, duration: 0.3 }}
                          >
                            <Image src={src} alt="" fill sizes="140px" className="object-cover" />
                          </motion.div>
                        ))}
                      </div>
                    </motion.div>
                  ) : (
                    <motion.div
                      key="folders"
                      className="absolute inset-x-4 top-0 bottom-0 overflow-hidden"
                      initial={{ opacity: 0, x: 36 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: 36 }}
                      transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
                    >
                      <div className="space-y-[6px]">
                        {FOLDERS.map((folder, i) => (
                          <motion.div
                            key={folder.name}
                            className="flex items-center gap-3 rounded-[12px] bg-white px-2.5 py-2 shadow-sm"
                            initial={{ opacity: 0, y: 12 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: 0.08 + i * 0.06, duration: 0.32 }}
                          >
                            <div className="relative h-11 w-11 shrink-0 overflow-hidden rounded-[8px] bg-[#E3E3E8]">
                              <Image
                                src={folder.cover}
                                alt=""
                                fill
                                sizes="44px"
                                className="object-cover"
                              />
                            </div>
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-[15px] font-semibold text-black">
                                {folder.name}
                              </p>
                              <p className="text-[12px] text-[#8E8E93]">
                                {folder.count} zdjęć
                              </p>
                            </div>
                            <ChevronRight size={18} className="shrink-0 text-[#C7C7CC]" />
                          </motion.div>
                        ))}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
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
