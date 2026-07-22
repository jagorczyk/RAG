"use client";

import Image from "next/image";
import { useInView } from "react-intersection-observer";
import {
  motion,
  useMotionTemplate,
  useMotionValue,
  useMotionValueEvent,
  useTransform,
  type MotionValue,
} from "motion/react";
import { useState } from "react";
import type { GalleryPhoto } from "./galleryData";

const Z_STEP = 220;
const XY_SPREAD = 72;

type GalleryItemProps = {
  photo: GalleryPhoto;
  index: number;
  progress: MotionValue<number>;
  pointerX: MotionValue<number>;
  pointerY: MotionValue<number>;
  selectedId: string | null;
  reducedMotion: boolean;
  onSelect: (id: string) => void;
  consumeDragGuard?: () => boolean;
};

export function GalleryItem({
  photo,
  index,
  progress,
  pointerX,
  pointerY,
  selectedId,
  reducedMotion,
  onSelect,
  consumeDragGuard,
}: GalleryItemProps) {
  const { ref: inViewRef, inView } = useInView({
    rootMargin: "200px",
    triggerOnce: true,
    threshold: 0.01,
  });
  const [nearFocus, setNearFocus] = useState(index === 0);
  const hover = useMotionValue(0);

  const depth = useTransform(progress, (p) => index - p);
  const z = useTransform(depth, (d) => d * -Z_STEP);
  const x = useTransform([depth, pointerX], ([d, px]) => {
    const base = photo.driftX * XY_SPREAD;
    const parallax = (px as number) * (28 + Math.abs(d as number) * 18);
    return base + parallax;
  });
  const y = useTransform([depth, pointerY], ([d, py]) => {
    const base = photo.driftY * XY_SPREAD * 0.85;
    const parallax = (py as number) * (22 + Math.abs(d as number) * 14);
    return base + parallax;
  });
  const rotateY = useTransform([depth, hover], ([d, h]) => {
    return photo.yaw + (d as number) * -10 + (h as number) * 6;
  });
  const rotateX = useTransform([depth, hover], ([d, h]) => {
    return photo.pitch + (d as number) * 4 + (h as number) * -4;
  });
  const scale = useTransform([depth, hover], ([d, h]) => {
    const ad = Math.abs(d as number);
    const hoverBoost = 1 + (h as number) * 0.04;
    return Math.max(0.72, 1 - ad * 0.08) * hoverBoost;
  });
  const opacity = useTransform(depth, (d) => {
    const ad = Math.abs(d);
    if (ad > 3.2) return 0;
    return Math.max(0.15, 1 - ad * 0.28);
  });
  const blurPx = useTransform(depth, (d) => {
    const ad = Math.abs(d);
    if (ad < 0.35) return 0;
    return Math.min(14, ad * 5.5);
  });
  const zIndex = useTransform(depth, (d) => Math.round(60 - Math.abs(d) * 10));
  const filter = useMotionTemplate`blur(${blurPx}px)`;
  const transform = useMotionTemplate`translate3d(${x}px, ${y}px, ${z}px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) scale(${scale})`;

  useMotionValueEvent(depth, "change", (d) => {
    setNearFocus(Math.abs(d) < 0.55);
  });

  const isSelected = selectedId === photo.id;
  const isDimmed = selectedId !== null && !isSelected;

  if (reducedMotion) {
    return (
      <button
        type="button"
        ref={inViewRef}
        onClick={() => onSelect(photo.id)}
        className="group relative aspect-[4/5] overflow-hidden rounded-2xl bg-[#DBE2EF] text-left shadow-[0_16px_40px_rgba(17,45,78,0.14)] ring-1 ring-white/60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#3F72AF]"
      >
        {inView ? (
          <Image
            src={photo.src}
            alt={photo.alt}
            fill
            sizes="(max-width: 1024px) 40vw, 220px"
            className="object-cover transition duration-300 group-hover:scale-[1.03]"
            loading="lazy"
          />
        ) : (
          <div className="absolute inset-0 animate-pulse bg-[#DBE2EF]" />
        )}
        <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-[#112D4E]/70 to-transparent px-3 pb-3 pt-8 text-[0.7rem] font-semibold tracking-wide text-white">
          {photo.title}
        </span>
      </button>
    );
  }

  return (
    <motion.button
      type="button"
      ref={inViewRef}
      layoutId={isSelected ? undefined : `gallery-frame-${photo.id}`}
      className="absolute left-1/2 top-1/2 origin-center overflow-hidden rounded-[1.15rem] bg-[#DBE2EF]/80 text-left will-change-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#3F72AF]"
      style={{
        width: "min(42vw, 260px)",
        aspectRatio: "4 / 5",
        marginLeft: "calc(min(42vw, 260px) / -2)",
        marginTop: "calc(min(42vw, 260px) * -0.625)",
        transform,
        opacity: isSelected ? 0 : isDimmed ? 0.22 : opacity,
        filter: isDimmed ? "blur(14px)" : filter,
        zIndex: isSelected ? 0 : zIndex,
        boxShadow: nearFocus
          ? "0 28px 64px rgba(17,45,78,0.28), 0 0 48px rgba(63,114,175,0.2)"
          : "0 18px 42px rgba(17,45,78,0.16)",
        pointerEvents: isDimmed || isSelected ? "none" : "auto",
        transformStyle: "preserve-3d",
      }}
      transition={{ type: "spring", stiffness: 260, damping: 28, mass: 0.75 }}
      onHoverStart={() => {
        if (nearFocus) hover.set(1);
      }}
      onHoverEnd={() => hover.set(0)}
      onClick={() => {
        if (consumeDragGuard?.()) return;
        if (Math.abs(depth.get()) > 0.65) return;
        onSelect(photo.id);
      }}
      aria-label={`Otwórz zdjęcie: ${photo.title}`}
    >
      <div className="absolute inset-0">
        {inView || nearFocus ? (
          <Image
            src={photo.src}
            alt={photo.alt}
            fill
            sizes="(max-width: 1024px) 55vw, 280px"
            className="object-cover"
            {...(index < 2
              ? { priority: true as const }
              : { loading: "lazy" as const })}
          />
        ) : (
          <div className="absolute inset-0 bg-[#DBE2EF]" />
        )}

        <div
          className="pointer-events-none absolute inset-0 rounded-[1.15rem]"
          style={{
            boxShadow:
              "inset 0 1px 0 rgba(255,255,255,0.45), inset 0 -20px 40px rgba(17,45,78,0.12)",
            background: nearFocus
              ? "linear-gradient(160deg, rgba(255,255,255,0.18) 0%, transparent 42%, rgba(63,114,175,0.08) 100%)"
              : "linear-gradient(160deg, rgba(255,255,255,0.1) 0%, transparent 50%)",
          }}
        />
      </div>
    </motion.button>
  );
}
