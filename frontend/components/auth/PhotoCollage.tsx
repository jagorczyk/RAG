"use client";

import { useEffect, useState } from "react";
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

/** Depth / float bias per „page” — album spread feel */
const PAGE_META = [
  { z: 40, float: 0.0, tilt: -1.2 },
  { z: 28, float: 0.35, tilt: 0.8 },
  { z: 52, float: 0.15, tilt: -0.6 },
  { z: 22, float: 0.55, tilt: 1.1 },
  { z: 36, float: 0.25, tilt: 0.4 },
  { z: 48, float: 0.7, tilt: -0.9 },
  { z: 30, float: 0.1, tilt: 0.7 },
  { z: 44, float: 0.45, tilt: -0.5 },
] as const;

export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [finePointer, setFinePointer] = useState(false);
  const mx = useMotionValue(0);
  const my = useMotionValue(0);
  const springX = useSpring(mx, { stiffness: 100, damping: 20, mass: 0.45 });
  const springY = useSpring(my, { stiffness: 100, damping: 20, mass: 0.45 });

  const sceneRotateY = useTransform(springX, (v) => (reduced || !finePointer ? 0 : v * 28));
  const sceneRotateX = useTransform(springY, (v) => (reduced || !finePointer ? 0 : v * -18));
  const sceneTransform = useMotionTemplate`perspective(1200px) rotateX(${sceneRotateX}deg) rotateY(${sceneRotateY}deg)`;

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
        className="pointer-events-none absolute inset-0 opacity-50"
        style={{
          background:
            "radial-gradient(ellipse at 40% 30%, #3F72AF 0%, transparent 55%), radial-gradient(ellipse at 80% 80%, #DBE2EF 0%, transparent 40%)",
        }}
      />

      <div className="absolute inset-0 flex items-center justify-center px-5 py-8 lg:px-10 lg:py-12">
        <motion.div
          className="grid w-full max-w-3xl grid-cols-4 gap-3 sm:gap-4"
          style={{
            transformStyle: "preserve-3d",
            transform: sceneTransform,
          }}
        >
          {PHOTOS.map((src, index) => (
            <AlbumPage
              key={src}
              src={src}
              index={index}
              meta={PAGE_META[index]}
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

function AlbumPage({
  src,
  index,
  meta,
  springX,
  springY,
  reduced,
  priority,
}: {
  src: string;
  index: number;
  meta: (typeof PAGE_META)[number];
  springX: ReturnType<typeof useSpring>;
  springY: ReturnType<typeof useSpring>;
  reduced: boolean;
  priority: boolean;
}) {
  const localX = useTransform(springX, (v) => (reduced ? 0 : v * meta.z * 0.35));
  const localY = useTransform(springY, (v) => (reduced ? 0 : v * meta.z * 0.28));
  const localZ = useTransform(springX, (v) => (reduced ? 0 : meta.z + Math.abs(v) * 18));
  const tiltY = useTransform(springX, (v) => (reduced ? 0 : meta.tilt + v * 6));
  const tiltX = useTransform(springY, (v) => (reduced ? 0 : -v * 5));
  const transform = useMotionTemplate`translate3d(${localX}px, ${localY}px, ${localZ}px) rotateX(${tiltX}deg) rotateY(${tiltY}deg)`;

  return (
    <motion.div
      className="relative aspect-[3/4] overflow-hidden rounded-2xl border border-white/25 bg-[#DBE2EF] shadow-[0_18px_40px_rgba(17,45,78,0.45)]"
      style={{
        transformStyle: "preserve-3d",
        transform,
        zIndex: Math.round(meta.z),
      }}
      animate={
        reduced
          ? undefined
          : {
              y: [0, -8 - (index % 3) * 2, 0],
              transition: {
                duration: 4.2 + meta.float * 2.4,
                repeat: Infinity,
                ease: "easeInOut",
                delay: meta.float * 1.2,
              },
            }
      }
    >
      <Image
        src={src}
        alt=""
        fill
        sizes="(max-width: 1024px) 28vw, 18vw"
        className="object-cover"
        priority={priority}
      />
      <div
        className="pointer-events-none absolute inset-0 rounded-2xl"
        style={{
          boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.18)",
          background: "linear-gradient(145deg, rgba(255,255,255,0.18) 0%, transparent 42%)",
        }}
      />
    </motion.div>
  );
}
