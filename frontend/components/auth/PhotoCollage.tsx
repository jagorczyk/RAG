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

/** Per-tile 3D depth + which ones slowly “come forward” */
const TILE_META = [
  { depth: 1.15, breathe: true, phase: 0.0 },
  { depth: 0.75, breathe: false, phase: 0.4 },
  { depth: 1.35, breathe: true, phase: 1.1 },
  { depth: 0.9, breathe: false, phase: 0.7 },
  { depth: 1.05, breathe: true, phase: 2.0 },
  { depth: 0.7, breathe: false, phase: 1.5 },
  { depth: 1.4, breathe: true, phase: 0.3 },
  { depth: 0.85, breathe: true, phase: 2.6 },
] as const;

export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [finePointer, setFinePointer] = useState(false);
  const mx = useMotionValue(0);
  const my = useMotionValue(0);
  const springX = useSpring(mx, { stiffness: 70, damping: 16, mass: 0.55 });
  const springY = useSpring(my, { stiffness: 70, damping: 16, mass: 0.55 });

  const sceneRotateY = useTransform(springX, (v) => (reduced || !finePointer ? 0 : v * 42));
  const sceneRotateX = useTransform(springY, (v) => (reduced || !finePointer ? 0 : v * -28));
  const sceneZ = useTransform(springX, (v) => (reduced || !finePointer ? 0 : Math.abs(v) * 40));
  const sceneTransform = useMotionTemplate`perspective(900px) rotateX(${sceneRotateX}deg) rotateY(${sceneRotateY}deg) translateZ(${sceneZ}px)`;

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
      className="relative h-full min-h-[220px] w-full overflow-hidden bg-[#112D4E]"
      onMouseMove={onMove}
      onMouseLeave={onLeave}
      aria-hidden
    >
      <div
        className="pointer-events-none absolute inset-0 opacity-45"
        style={{
          background:
            "radial-gradient(ellipse at 35% 25%, #3F72AF 0%, transparent 55%), radial-gradient(ellipse at 85% 75%, #DBE2EF 0%, transparent 42%)",
        }}
      />

      <div
        className="absolute inset-0 flex items-center justify-center"
        style={{ perspective: "900px" }}
      >
        <motion.div
          className="grid h-[118%] w-[118%] grid-cols-4 grid-rows-2"
          style={{
            transformStyle: "preserve-3d",
            transform: sceneTransform,
          }}
        >
          {PHOTOS.map((src, index) => (
            <CollageTile
              key={src}
              src={src}
              meta={TILE_META[index]}
              springX={springX}
              springY={springY}
              reduced={!!reduced || !finePointer}
              priority={index < 4}
            />
          ))}
        </motion.div>
      </div>
    </div>
  );
}

function CollageTile({
  src,
  meta,
  springX,
  springY,
  reduced,
  priority,
}: {
  src: string;
  meta: (typeof TILE_META)[number];
  springX: ReturnType<typeof useSpring>;
  springY: ReturnType<typeof useSpring>;
  reduced: boolean;
  priority: boolean;
}) {
  const push = meta.depth * 56;
  const x = useTransform(springX, (v) => (reduced ? 0 : v * push));
  const y = useTransform(springY, (v) => (reduced ? 0 : v * push * 0.85));
  const z = useTransform(springX, (v) => (reduced ? 0 : meta.depth * 70 + Math.abs(v) * 36));
  const rotY = useTransform(springX, (v) => (reduced ? 0 : v * 10 * meta.depth));
  const rotX = useTransform(springY, (v) => (reduced ? 0 : -v * 8 * meta.depth));
  const transform = useMotionTemplate`translate3d(${x}px, ${y}px, ${z}px) rotateX(${rotX}deg) rotateY(${rotY}deg)`;

  const breathe = useMemo(() => {
    if (reduced || !meta.breathe) return undefined;
    return {
      scale: [1, 1.14, 1],
      z: [0, 28, 0],
      transition: {
        duration: 7.5 + meta.phase,
        repeat: Infinity,
        ease: "easeInOut" as const,
        delay: meta.phase,
      },
    };
  }, [reduced, meta.breathe, meta.phase]);

  return (
    <motion.div
      className="relative min-h-0 min-w-0 overflow-hidden bg-[#DBE2EF]"
      style={{
        transformStyle: "preserve-3d",
        transform,
        zIndex: Math.round(meta.depth * 20),
      }}
      animate={breathe}
    >
      <Image
        src={src}
        alt=""
        fill
        sizes="(max-width: 1024px) 40vw, 28vw"
        className="object-cover"
        priority={priority}
      />
    </motion.div>
  );
}
