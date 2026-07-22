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

type CollageTile = {
  id: string;
  src?: string;
  tone: string;
  depth: number;
  className: string;
  rotate: number;
};

const TILES: CollageTile[] = [
  { id: "a", src: "/logo.png", tone: "#3F72AF", depth: 28, className: "left-[6%] top-[8%] h-[38%] w-[32%]", rotate: -6 },
  { id: "b", src: "/logo_rag.png", tone: "#112D4E", depth: 18, className: "right-[8%] top-[6%] h-[28%] w-[36%]", rotate: 5 },
  { id: "c", tone: "#DBE2EF", depth: 36, className: "left-[12%] top-[48%] h-[34%] w-[28%]", rotate: 3 },
  { id: "d", src: "/logo.png", tone: "#2E5A8F", depth: 22, className: "left-[42%] top-[36%] h-[40%] w-[30%]", rotate: -4 },
  { id: "e", tone: "#8FA6C4", depth: 40, className: "right-[6%] top-[42%] h-[30%] w-[26%]", rotate: 7 },
  { id: "f", src: "/logo_rag.png", tone: "#3F72AF", depth: 14, className: "left-[38%] top-[8%] h-[22%] w-[22%]", rotate: 2 },
  { id: "g", tone: "#112D4E", depth: 30, className: "right-[28%] bottom-[8%] h-[24%] w-[24%]", rotate: -5 },
  { id: "h", tone: "#DBE2EF", depth: 20, className: "left-[4%] bottom-[6%] h-[20%] w-[30%]", rotate: 4 },
];

export function PhotoCollage() {
  const reduced = useReducedMotion();
  const [finePointer, setFinePointer] = useState(false);
  const mx = useMotionValue(0);
  const my = useMotionValue(0);
  const springX = useSpring(mx, { stiffness: 120, damping: 22, mass: 0.4 });
  const springY = useSpring(my, { stiffness: 120, damping: 22, mass: 0.4 });

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
    const nx = (event.clientX - rect.left) / rect.width - 0.5;
    const ny = (event.clientY - rect.top) / rect.height - 0.5;
    mx.set(nx);
    my.set(ny);
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
        className="pointer-events-none absolute inset-0 opacity-40"
        style={{
          background:
            "radial-gradient(ellipse at 30% 20%, #3F72AF 0%, transparent 55%), radial-gradient(ellipse at 80% 70%, #DBE2EF 0%, transparent 45%)",
        }}
      />
      {TILES.map((tile) => (
        <CollageFrame
          key={tile.id}
          tile={tile}
          springX={springX}
          springY={springY}
          reduced={!!reduced || !finePointer}
        />
      ))}
    </div>
  );
}

function CollageFrame({
  tile,
  springX,
  springY,
  reduced,
}: {
  tile: CollageTile;
  springX: ReturnType<typeof useSpring>;
  springY: ReturnType<typeof useSpring>;
  reduced: boolean;
}) {
  const x = useTransform(springX, (v) => (reduced ? 0 : v * tile.depth));
  const y = useTransform(springY, (v) => (reduced ? 0 : v * tile.depth * 0.85));
  const transform = useMotionTemplate`translate3d(${x}px, ${y}px, 0) rotate(${tile.rotate}deg)`;

  const float = useMemo(
    () =>
      reduced
        ? undefined
        : {
            y: [0, -6, 0],
            transition: {
              duration: 5 + tile.depth / 20,
              repeat: Infinity,
              ease: "easeInOut" as const,
              delay: tile.depth / 40,
            },
          },
    [reduced, tile.depth]
  );

  return (
    <motion.div
      className={`absolute overflow-hidden rounded-xl border border-white/20 shadow-md ${tile.className}`}
      style={{ transform, backgroundColor: tile.tone }}
      animate={float}
    >
      {tile.src ? (
        <Image
          src={tile.src}
          alt=""
          fill
          sizes="280px"
          className="object-cover opacity-90"
          priority={tile.id === "a"}
        />
      ) : (
        <div
          className="h-full w-full"
          style={{
            background: `linear-gradient(145deg, ${tile.tone}, color-mix(in srgb, ${tile.tone} 55%, #F9F7F7))`,
          }}
        />
      )}
    </motion.div>
  );
}
