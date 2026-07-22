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

export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [finePointer, setFinePointer] = useState(false);
  const mx = useMotionValue(0);
  const my = useMotionValue(0);
  const springX = useSpring(mx, { stiffness: 140, damping: 24, mass: 0.35 });
  const springY = useSpring(my, { stiffness: 140, damping: 24, mass: 0.35 });
  const shiftX = useTransform(springX, (v) => (reduced || !finePointer ? 0 : v * 18));
  const shiftY = useTransform(springY, (v) => (reduced || !finePointer ? 0 : v * 14));

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
      className="relative h-full min-h-[220px] w-full overflow-hidden bg-ink"
      onMouseMove={onMove}
      onMouseLeave={onLeave}
      aria-hidden
    >
      <motion.div
        className="absolute inset-[-3%] grid h-[106%] w-[106%] grid-cols-4 grid-rows-2"
        style={{ x: shiftX, y: shiftY }}
      >
        {PHOTOS.map((src, index) => (
          <div key={src} className="relative min-h-0 min-w-0 overflow-hidden">
            <Image
              src={src}
              alt=""
              fill
              sizes="(max-width: 1024px) 50vw, 25vw"
              className="object-cover"
              priority={index < 4}
            />
          </div>
        ))}
      </motion.div>
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "linear-gradient(90deg, transparent 70%, color-mix(in srgb, #112D4E 28%, transparent)), linear-gradient(180deg, color-mix(in srgb, #112D4E 18%, transparent), transparent 35%, color-mix(in srgb, #112D4E 22%, transparent))",
        }}
      />
    </div>
  );
}
