"use client";

import Image from "next/image";
import { useInView } from "react-intersection-observer";
import {
  motion,
  useMotionTemplate,
  useMotionValue,
  useTransform,
  type MotionValue,
} from "motion/react";
import type { GalleryPhoto } from "./galleryData";

/** Horizontal spacing between cards in the looping ribbon. */
const SLOT = 300;
/** How far the ribbon travels per second of `time`. */
const SPEED = 48;

type GalleryItemProps = {
  photo: GalleryPhoto;
  index: number;
  count: number;
  time: MotionValue<number>;
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
  count,
  time,
  pointerX,
  pointerY,
  selectedId,
  reducedMotion,
  onSelect,
  consumeDragGuard,
}: GalleryItemProps) {
  const { ref: inViewRef, inView } = useInView({
    rootMargin: "240px",
    triggerOnce: true,
    threshold: 0.01,
  });
  const hover = useMotionValue(0);

  const loopWidth = Math.max(1, count) * SLOT;

  // Auto L→R: each card loops across the stage with 3D depth.
  const x = useTransform([time, pointerX], ([t, px]) => {
    const raw = index * SLOT + (t as number) * SPEED;
    // Wrap into (-loopWidth/2 … +loopWidth/2) then shift so travel is L→R visually
    let local = ((raw % loopWidth) + loopWidth) % loopWidth;
    // Start off the left, exit to the right
    local = local - loopWidth * 0.55;
    const parallax = (px as number) * (22 + Math.abs(photo.driftX) * 16);
    return local + photo.driftX * 28 + parallax;
  });

  const y = useTransform(pointerY, (py) => photo.driftY * 56 + py * 18);
  const z = useTransform(time, (t) => {
    // Gentle bobbing depth so cards feel layered while drifting
    const phase = t * 0.7 + index * 1.1;
    return photo.driftX * -40 + Math.sin(phase) * 36 + photo.yaw * 2;
  });
  const rotateY = useTransform([time, hover], ([t, h]) => {
    return photo.yaw + Math.sin((t as number) * 0.55 + index) * 4 + (h as number) * 6;
  });
  const rotateX = useTransform(hover, (h) => photo.pitch + h * -4);
  const scale = useTransform(hover, (h) => 1 + h * 0.04);

  // Fade in from left, fade out on right (edge of stage ~ ±420px)
  const opacity = useTransform(x, (vx) => {
    const edge = 460;
    if (vx < -edge) return 0;
    if (vx > edge) return 0;
    if (vx < -edge + 120) return (vx + edge) / 120;
    if (vx > edge - 120) return (edge - vx) / 120;
    return 1;
  });
  const blurPx = useTransform(z, (vz) => {
    const depth = Math.max(0, -vz);
    return Math.min(10, depth * 0.04);
  });
  const filter = useMotionTemplate`blur(${blurPx}px)`;
  const transform = useMotionTemplate`translate3d(${x}px, ${y}px, ${z}px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) scale(${scale})`;

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
        width: "min(38vw, 210px)",
        aspectRatio: "4 / 5",
        marginLeft: "calc(min(38vw, 210px) / -2)",
        marginTop: "calc(min(38vw, 210px) * -0.625)",
        transform,
        opacity: isSelected ? 0 : isDimmed ? 0.2 : opacity,
        filter: isDimmed ? "blur(14px)" : filter,
        zIndex: isSelected ? 0 : Math.round(40 + photo.driftX * 10 + index),
        boxShadow:
          "0 24px 56px rgba(17,45,78,0.22), 0 0 40px rgba(63,114,175,0.12)",
        pointerEvents: isDimmed || isSelected ? "none" : "auto",
        transformStyle: "preserve-3d",
      }}
      transition={{ type: "spring", stiffness: 260, damping: 28, mass: 0.75 }}
      onHoverStart={() => hover.set(1)}
      onHoverEnd={() => hover.set(0)}
      onClick={() => {
        if (consumeDragGuard?.()) return;
        onSelect(photo.id);
      }}
      aria-label={`Otwórz zdjęcie: ${photo.title}`}
    >
      <div className="absolute inset-0">
        {inView ? (
          <Image
            src={photo.src}
            alt={photo.alt}
            fill
            sizes="(max-width: 1024px) 50vw, 240px"
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
            background:
              "linear-gradient(160deg, rgba(255,255,255,0.16) 0%, transparent 42%, rgba(63,114,175,0.08) 100%)",
          }}
        />
      </div>
    </motion.button>
  );
}
