"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import {
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

const COLS = 8;
const ROWS = 3;

type Tile = {
  id: string;
  src: string;
  col: number;
  row: number;
  breathe: boolean;
  phase: number;
  focus: string;
};

function buildTiles(): Tile[] {
  const tiles: Tile[] = [];
  for (let row = 0; row < ROWS; row++) {
    for (let col = 0; col < COLS; col++) {
      const i = row * COLS + col;
      tiles.push({
        id: `${row}-${col}`,
        src: PHOTOS[i % PHOTOS.length],
        col,
        row,
        breathe: (i * 5) % 7 < 3,
        phase: ((i * 11) % 17) * 0.35,
        focus: ["object-center", "object-left", "object-right", "object-top"][i % 4],
      });
    }
  }
  return tiles;
}

const TILES = buildTiles();

const NOISE =
  "url(\"data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.55'/%3E%3C/svg%3E\")";

export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [finePointer, setFinePointer] = useState(false);
  const mx = useMotionValue(0);
  const my = useMotionValue(0);
  const drift = useMotionValue(0);
  const springX = useSpring(mx, { stiffness: 55, damping: 18, mass: 0.6 });
  const springY = useSpring(my, { stiffness: 55, damping: 18, mass: 0.6 });

  // Continuous left → right yaw of the wall
  useEffect(() => {
    if (reduced) {
      drift.set(0);
      return;
    }
    let raf = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const t = (now - start) / 1000;
      drift.set(Math.sin(t * 0.35) * 0.85);
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [reduced, drift]);

  const yaw = useTransform([springX, drift], ([x, d]) => {
    if (reduced || !finePointer) return (d as number) * 22;
    return (x as number) * 48 + (d as number) * 26;
  });
  const pitch = useTransform(springY, (v) => (reduced || !finePointer ? -4 : v * -16 - 4));
  const sceneTransform = useMotionTemplate`perspective(1600px) translateZ(-120px) rotateX(${pitch}deg) rotateY(${yaw}deg)`;

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
      className="relative h-full min-h-[220px] w-full overflow-hidden bg-[#07101c]"
      onMouseMove={onMove}
      onMouseLeave={onLeave}
      aria-hidden
    >
      {/* Atmospheric photo field — modern, not radial orbs */}
      <div className="absolute inset-0 scale-110 opacity-40 blur-2xl saturate-75">
        <Image src="/collage/02.jpg" alt="" fill className="object-cover" sizes="60vw" priority />
      </div>
      <div
        className="absolute inset-0"
        style={{
          background:
            "linear-gradient(105deg, #07101c 0%, color-mix(in srgb, #112D4E 72%, transparent) 42%, color-mix(in srgb, #3F72AF 18%, #07101c) 100%)",
        }}
      />
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.22] mix-blend-overlay"
        style={{ backgroundImage: NOISE }}
      />

      <div
        className="absolute inset-0 flex items-center justify-center px-3 py-6 lg:px-8 lg:py-10"
        style={{ perspective: "1600px" }}
      >
        <motion.div
          className="grid h-full w-full max-h-[640px] grid-cols-8 grid-rows-3"
          style={{
            transformStyle: "preserve-3d",
            transform: sceneTransform,
          }}
        >
          {TILES.map((tile, index) => (
            <CylinderTile
              key={tile.id}
              tile={tile}
              yaw={yaw}
              reduced={!!reduced}
              priority={index < 8}
            />
          ))}
        </motion.div>
      </div>
    </div>
  );
}

function CylinderTile({
  tile,
  yaw,
  reduced,
  priority,
}: {
  tile: Tile;
  yaw: MotionValue<number>;
  reduced: boolean;
  priority: boolean;
}) {
  const mid = (COLS - 1) / 2;
  const t = (tile.col - mid) / mid; // -1 … 1 left → right

  // Base cylinder: left pages turn toward viewer from left, right from right
  const transform = useTransform(yaw, (sceneYaw) => {
    if (reduced) {
      return "translate3d(0,0,0) rotateY(0deg)";
    }
    const localYaw = t * 38 - sceneYaw * 0.55;
    const arcZ = -Math.abs(t) * 90 + Math.cos((t * Math.PI) / 2) * 36;
    const lift = (tile.row - 1) * -6;
    return `translate3d(0px, ${lift}px, ${arcZ}px) rotateY(${localYaw}deg)`;
  });

  const breathe = useMemo(() => {
    if (reduced || !tile.breathe) return undefined;
    return {
      scale: [1, 1.1, 1],
      transition: {
        duration: 6.5 + tile.phase,
        repeat: Infinity,
        ease: "easeInOut" as const,
        delay: tile.phase,
      },
    };
  }, [reduced, tile.breathe, tile.phase]);

  return (
    <motion.div
      className="relative min-h-0 min-w-0 overflow-hidden bg-[#0d1a2c]"
      style={{
        transformStyle: "preserve-3d",
        transform,
        zIndex: Math.round((1 - Math.abs(t)) * 30),
      }}
      animate={breathe}
    >
      <Image
        src={tile.src}
        alt=""
        fill
        sizes="12vw"
        className={`object-cover ${tile.focus}`}
        priority={priority}
      />
    </motion.div>
  );
}
