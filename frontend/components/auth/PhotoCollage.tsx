"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import {
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

const NOISE =
  "url(\"data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.5'/%3E%3C/svg%3E\")";

type Band = {
  id: string;
  direction: "left" | "right";
  duration: number;
  photos: string[];
  size: "lg" | "md" | "sm";
};

function bandPhotos(offset: number, count: number): string[] {
  return Array.from({ length: count }, (_, i) => PHOTOS[(i + offset) % PHOTOS.length]);
}

const BANDS: Band[] = [
  {
    id: "top",
    direction: "left",
    duration: 42,
    photos: bandPhotos(0, 10),
    size: "lg",
  },
  {
    id: "mid",
    direction: "right",
    duration: 36,
    photos: bandPhotos(3, 12),
    size: "md",
  },
  {
    id: "bot",
    direction: "left",
    duration: 48,
    photos: bandPhotos(5, 10),
    size: "sm",
  },
];

const SIZE_CLASS = {
  lg: "h-[42%] min-h-[120px]",
  md: "h-[32%] min-h-[96px]",
  sm: "h-[26%] min-h-[80px]",
} as const;

const TILE_WIDTH = {
  lg: "w-[min(280px,38vw)]",
  md: "w-[min(220px,32vw)]",
  sm: "w-[min(180px,28vw)]",
} as const;

export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [finePointer, setFinePointer] = useState(false);
  const mx = useMotionValue(0);
  const my = useMotionValue(0);
  const springX = useSpring(mx, { stiffness: 80, damping: 22, mass: 0.45 });
  const springY = useSpring(my, { stiffness: 80, damping: 22, mass: 0.45 });

  const shiftX = useTransform(springX, (v) => (reduced || !finePointer ? 0 : v * 28));
  const shiftY = useTransform(springY, (v) => (reduced || !finePointer ? 0 : v * 18));
  const tilt = useTransform(springX, (v) => (reduced || !finePointer ? 0 : v * 4));

  useEffect(() => {
    const mq = window.matchMedia("(pointer: fine)");
    const sync = () => setFinePointer(mq.matches);
    sync();
    mq.addEventListener("change", sync);
    return () => mq.removeEventListener("change", sync);
  }, []);

  const onMove = (event: React.MouseEvent<HTMLDivElement>) => {
    if (reduced || !finePointer) return;
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
      className="relative h-full min-h-[220px] w-full overflow-hidden bg-[#050b14]"
      onMouseMove={onMove}
      onMouseLeave={onLeave}
      aria-hidden
    >
      <div className="absolute inset-0 scale-110 opacity-30 blur-3xl contrast-125 saturate-50">
        <Image src="/collage/07.jpg" alt="" fill className="object-cover" sizes="70vw" priority />
      </div>
      <div className="absolute inset-0 bg-[#050b14]/65" />
      <div
        className="absolute inset-0 opacity-80"
        style={{
          background:
            "linear-gradient(160deg, transparent 20%, color-mix(in srgb, #3F72AF 12%, transparent) 55%, transparent 85%)",
        }}
      />
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.18] mix-blend-soft-light"
        style={{ backgroundImage: NOISE }}
      />

      <motion.div
        className="absolute inset-0 flex flex-col justify-center gap-3 py-8 lg:gap-4 lg:py-12"
        style={{ x: shiftX, y: shiftY, rotateZ: tilt }}
      >
        {BANDS.map((band, index) => (
          <MarqueeBand
            key={band.id}
            band={band}
            reduced={!!reduced}
            parallax={reduced || !finePointer ? 0 : (index - 1) * 12}
            mouseX={springX}
          />
        ))}
      </motion.div>

      {/* Soft edge masks — cinematic, not 2015 orbs */}
      <div className="pointer-events-none absolute inset-y-0 left-0 w-16 bg-gradient-to-r from-[#050b14] to-transparent lg:w-24" />
      <div className="pointer-events-none absolute inset-y-0 right-0 w-16 bg-gradient-to-l from-[#050b14] to-transparent lg:w-24" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-16 bg-gradient-to-b from-[#050b14]/90 to-transparent" />
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-20 bg-gradient-to-t from-[#050b14] to-transparent" />
    </div>
  );
}

function MarqueeBand({
  band,
  reduced,
  parallax,
  mouseX,
}: {
  band: Band;
  reduced: boolean;
  parallax: number;
  mouseX: ReturnType<typeof useSpring>;
}) {
  const rowShift = useTransform(mouseX, (v) => v * parallax);
  const sequence = [...band.photos, ...band.photos];
  const from = band.direction === "left" ? "0%" : "-50%";
  const to = band.direction === "left" ? "-50%" : "0%";

  return (
    <motion.div
      className={`relative w-full overflow-hidden ${SIZE_CLASS[band.size]}`}
      style={{ x: rowShift }}
    >
      <motion.div
        className="flex h-full w-max"
        animate={
          reduced
            ? { x: from }
            : {
                x: [from, to],
                transition: {
                  duration: band.duration,
                  ease: "linear",
                  repeat: Infinity,
                },
              }
        }
      >
        {sequence.map((src, i) => (
          <div
            key={`${band.id}-${i}`}
            className={`relative h-full shrink-0 overflow-hidden ${TILE_WIDTH[band.size]}`}
          >
            <Image
              src={src}
              alt=""
              fill
              sizes="280px"
              className="object-cover"
              priority={band.id === "top" && i < 4}
            />
            <div className="absolute inset-0 bg-ink/10" />
          </div>
        ))}
      </motion.div>
    </motion.div>
  );
}
