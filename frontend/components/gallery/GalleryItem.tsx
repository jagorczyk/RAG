"use client";

import Image from "next/image";
import { useInView } from "react-intersection-observer";
import {
  motion,
  useMotionTemplate,
  useMotionValue,
  useSpring,
  useTransform,
  type MotionValue,
} from "motion/react";
import { GALLERY_TUNNEL, type GalleryPhoto } from "./galleryData";

type GalleryItemProps = {
  photo: GalleryPhoto;
  index: number;
  cameraZ: MotionValue<number>;
  pointerX: MotionValue<number>;
  pointerY: MotionValue<number>;
  depthScale: MotionValue<number>;
  selectedId: string | null;
  reducedMotion: boolean;
  layoutPrefix?: string;
  onSelect: (id: string) => void;
  consumeDragGuard?: () => boolean;
};

function wrapRelative(z: number, tunnel: number): number {
  let rel = ((z % tunnel) + tunnel) % tunnel;
  if (rel > tunnel * 0.5) rel -= tunnel;
  return rel;
}

export function GalleryItem({
  photo,
  index,
  cameraZ,
  pointerX,
  pointerY,
  depthScale,
  selectedId,
  reducedMotion,
  layoutPrefix = "main",
  onSelect,
  consumeDragGuard,
}: GalleryItemProps) {
  const { ref: inViewRef, inView } = useInView({
    rootMargin: "320px",
    triggerOnce: true,
    threshold: 0.01,
  });

  const hoverRaw = useMotionValue(0);
  const hover = useSpring(hoverRaw, {
    stiffness: 280,
    damping: 26,
    mass: 0.55,
  });

  const isSelected = selectedId === photo.id;
  const isDimmed = selectedId !== null && !isSelected;
  const widthPx = Math.round(200 * photo.size);

  const x = useTransform(
    [cameraZ, pointerX, depthScale, hover],
    ([cz, px, ds, h]) => {
      const relZ = wrapRelative(photo.z - (cz as number), GALLERY_TUNNEL);
      const depthFade = 1 + Math.max(0, -relZ) * 0.00012;
      const parallax =
        (px as number) * (26 + photo.depth * 38) * (ds as number);
      return photo.x * (ds as number) * depthFade + parallax + (h as number) * (px as number) * 6;
    }
  );

  const y = useTransform(
    [cameraZ, pointerY, depthScale, hover],
    ([cz, py, ds, h]) => {
      const relZ = wrapRelative(photo.z - (cz as number), GALLERY_TUNNEL);
      const depthFade = 1 + Math.max(0, -relZ) * 0.0001;
      const parallax =
        (py as number) * (20 + photo.depth * 32) * (ds as number);
      return photo.y * (ds as number) * depthFade + parallax + (h as number) * -16;
    }
  );

  const z = useTransform([cameraZ, depthScale, hover], ([cz, ds, h]) => {
    const relZ = wrapRelative(photo.z - (cz as number), GALLERY_TUNNEL);
    // Clamp so perspective never blows cards to multi-thousand-px boxes
    const clamped = Math.max(-900, Math.min(280, relZ));
    return clamped * (ds as number) + (h as number) * 32;
  });

  const zDimmed = useTransform(z, (vz) => vz - 160);

  const rotateX = useTransform(
    [pointerY, hover, depthScale],
    ([py, h, ds]) =>
      photo.rotateX * (ds as number) +
      (py as number) * -9 * photo.depth +
      (h as number) * -4
  );

  const rotateY = useTransform(
    [pointerX, hover, depthScale],
    ([px, h, ds]) =>
      photo.rotateY * (ds as number) +
      (px as number) * 11 * photo.depth +
      (h as number) * 5
  );

  const rotateZ = useTransform(hover, (h) => photo.rotateZ + h * 1.2);
  const scale = useTransform(hover, (h) => 1 + h * 0.055);

  const opacity = useTransform(cameraZ, (cz) => {
    const relZ = wrapRelative(photo.z - cz, GALLERY_TUNNEL);
    if (relZ < -980) return 0;
    if (relZ < -560) return (relZ + 980) / 420;
    if (relZ > 420) return 0;
    if (relZ > 160) return 1 - (relZ - 160) / 260;
    return 1;
  });

  const blurPx = useTransform(cameraZ, (cz) => {
    const relZ = wrapRelative(photo.z - cz, GALLERY_TUNNEL);
    const far = Math.max(0, -relZ - 100);
    const near = Math.max(0, relZ - 50);
    return Math.min(7, far * 0.005 + near * 0.014);
  });

  const filter = useMotionTemplate`blur(${blurPx}px)`;

  const shadow = useTransform(hover, (h) => {
    const lift = 26 + h * 26;
    const glow = 0.1 + h * 0.12;
    return `0 ${lift}px ${52 + h * 36}px rgba(17,45,78,${0.18 + h * 0.1}), 0 0 ${44 + h * 32}px rgba(63,114,175,${glow})`;
  });

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
    <motion.div
      className="absolute left-1/2 top-1/2 will-change-transform"
      style={{
        width: `min(42vw, ${widthPx}px)`,
        aspectRatio: "4 / 5",
        marginLeft: `calc(min(42vw, ${widthPx}px) / -2)`,
        marginTop: `calc(min(42vw, ${widthPx}px) * -0.625)`,
        x,
        y,
        z: isDimmed ? zDimmed : z,
        rotateX,
        rotateY,
        rotateZ,
        scale,
        opacity: isSelected ? 0 : isDimmed ? 0.3 : opacity,
        filter: isDimmed ? "blur(14px)" : filter,
        zIndex: isSelected
          ? 0
          : Math.round(40 + photo.depth * 18 + (index % 8)),
        transformStyle: "preserve-3d",
        transformOrigin: "center center",
        pointerEvents: isDimmed || isSelected ? "none" : "auto",
      }}
    >
      <motion.button
        type="button"
        layoutId={
          isSelected ? undefined : `gallery-frame-${layoutPrefix}-${photo.id}`
        }
        className="relative h-full w-full overflow-hidden rounded-[1.25rem] bg-[#DBE2EF]/80 text-left focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#3F72AF]"
        style={{
          boxShadow: shadow,
          border: "1px solid rgba(255,255,255,0.42)",
        }}
        transition={{ type: "spring", stiffness: 240, damping: 28, mass: 0.8 }}
        onHoverStart={() => hoverRaw.set(1)}
        onHoverEnd={() => hoverRaw.set(0)}
        onFocus={() => hoverRaw.set(1)}
        onBlur={() => hoverRaw.set(0)}
        onClick={() => {
          if (consumeDragGuard?.()) return;
          onSelect(photo.id);
        }}
        aria-label={`Otwórz zdjęcie: ${photo.title}`}
      >
        <Image
          src={photo.src}
          alt={photo.alt}
          fill
          sizes="(max-width: 1024px) 50vw, 260px"
          className="object-cover"
          placeholder="blur"
          blurDataURL="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI4IiBoZWlnaHQ9IjEwIj48cmVjdCB3aWR0aD0iOCIgaGVpZ2h0PSIxMCIgZmlsbD0iI0RCRTJFRiIvPjwvc3ZnPg=="
          {...(index < 4
            ? { priority: true as const }
            : { loading: "lazy" as const })}
        />
        <div
          className="pointer-events-none absolute inset-0 rounded-[1.25rem]"
          style={{
            background:
              "linear-gradient(155deg, rgba(255,255,255,0.28) 0%, transparent 38%, rgba(17,45,78,0.14) 100%)",
            boxShadow:
              "inset 0 1px 0 rgba(255,255,255,0.5), inset 0 -28px 48px rgba(17,45,78,0.1)",
          }}
        />
      </motion.button>
    </motion.div>
  );
}
