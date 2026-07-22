"use client";

import Image from "next/image";
import { useCallback, useEffect, useState } from "react";
import {
  AnimatePresence,
  LayoutGroup,
  motion,
  useReducedMotion,
} from "motion/react";
import { X } from "lucide-react";
import { GALLERY_PHOTOS, type GalleryPhoto } from "./galleryData";
import { GalleryItem } from "./GalleryItem";
import { useGalleryAnimation } from "./useGalleryAnimation";

type GalleryProps = {
  photos?: GalleryPhoto[];
  className?: string;
  /** When true, fills parent (landing panel). */
  embedded?: boolean;
};

export function Gallery({
  photos = GALLERY_PHOTOS,
  className = "",
  embedded = true,
}: GalleryProps) {
  const reduced = !!useReducedMotion();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = photos.find((p) => p.id === selectedId) ?? null;

  const animation = useGalleryAnimation({
    itemCount: photos.length,
    enabled: selectedId === null,
    reducedMotion: reduced,
  });

  const close = useCallback(() => setSelectedId(null), []);

  useEffect(() => {
    if (!selectedId) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") close();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close, selectedId]);

  useEffect(() => {
    if (!selectedId) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [selectedId]);

  if (reduced) {
    return (
      <div
        className={`relative h-full min-h-[220px] w-full overflow-y-auto bg-[#F4F1EE] ${className}`}
      >
        <div className="grid grid-cols-2 gap-3 p-4 sm:grid-cols-3 lg:grid-cols-2 xl:grid-cols-3">
          {photos.map((photo, index) => (
            <GalleryItem
              key={photo.id}
              photo={photo}
              index={index}
              count={photos.length}
              time={animation.time}
              pointerX={animation.pointerX}
              pointerY={animation.pointerY}
              selectedId={selectedId}
              reducedMotion
              onSelect={setSelectedId}
            />
          ))}
        </div>
        <Lightbox photo={selected} onClose={close} />
      </div>
    );
  }

  return (
    <LayoutGroup>
      <div
        className={`relative h-full min-h-[220px] w-full overflow-hidden bg-[#F4F1EE] touch-none select-none ${className}`}
        ref={animation.bindStage}
        role="region"
        aria-label="Galeria zdjęć 3D. Przewiń lub przeciągnij, aby przechodzić między zdjęciami. Kliknij, aby powiększyć."
      >
        {/* Atmosphere: glass wash + bloom */}
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              "radial-gradient(ellipse 55% 45% at 50% 42%, rgba(231,238,247,0.95) 0%, transparent 70%), linear-gradient(180deg, #F9F7F7 0%, #EDE8E3 100%)",
          }}
        />
        <div
          className="pointer-events-none absolute left-1/2 top-[38%] h-[55%] w-[70%] -translate-x-1/2 -translate-y-1/2 rounded-full opacity-50 blur-3xl"
          style={{
            background:
              "radial-gradient(circle, rgba(63,114,175,0.35) 0%, rgba(63,114,175,0.08) 45%, transparent 70%)",
          }}
        />
        <div
          className="pointer-events-none absolute inset-0 opacity-40"
          style={{
            background:
              "radial-gradient(circle at 30% 20%, rgba(255,255,255,0.55), transparent 40%)",
          }}
        />

        {/* 3D stage */}
        <div
          className="absolute inset-0 flex items-center justify-center"
          style={{
            perspective: embedded ? "1200px" : "1400px",
            perspectiveOrigin: "50% 45%",
          }}
        >
          <div
            className="relative h-full w-full"
            style={{ transformStyle: "preserve-3d" }}
          >
            {photos.map((photo, index) => (
              <GalleryItem
                key={photo.id}
                photo={photo}
                index={index}
                progress={animation.smoothProgress}
                pointerX={animation.pointerX}
                pointerY={animation.pointerY}
                selectedId={selectedId}
                reducedMotion={false}
                consumeDragGuard={animation.consumeDragGuard}
                onSelect={(id) => {
                  animation.jumpTo(index);
                  setSelectedId(id);
                }}
              />
            ))}
          </div>
        </div>

        {/* Soft vignette / glass rails */}
        <div className="pointer-events-none absolute inset-y-0 left-0 w-16 bg-gradient-to-r from-[#F4F1EE] to-transparent lg:w-24" />
        <div className="pointer-events-none absolute inset-y-0 right-0 w-16 bg-gradient-to-l from-[#F4F1EE] to-transparent lg:w-24" />
        <div className="pointer-events-none absolute inset-x-0 top-0 h-16 bg-gradient-to-b from-[#F4F1EE] to-transparent" />
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-20 bg-gradient-to-t from-[#F4F1EE] to-transparent" />

        <div
          data-gallery-chrome
          className="pointer-events-none absolute bottom-5 left-0 right-0 z-20 flex flex-col items-center gap-2"
        >
          <p className="rounded-full border border-white/50 bg-white/35 px-3.5 py-1.5 text-[0.68rem] font-semibold tracking-[0.06em] text-[#4A6B8A] shadow-[0_8px_24px_rgba(17,45,78,0.08)] backdrop-blur-md">
            Przewiń · przeciągnij · kliknij
          </p>
        </div>

        <Lightbox photo={selected} onClose={close} />
      </div>
    </LayoutGroup>
  );
}

function Lightbox({
  photo,
  onClose,
}: {
  photo: GalleryPhoto | null;
  onClose: () => void;
}) {
  return (
    <AnimatePresence>
      {photo ? (
        <motion.div
          key="lightbox"
          className="fixed inset-0 z-[80] flex items-center justify-center p-4 sm:p-8"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
        >
          <motion.button
            type="button"
            aria-label="Zamknij podgląd"
            className="absolute inset-0 bg-[#112D4E]/45 backdrop-blur-md"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />

          <motion.figure
            layoutId={`gallery-frame-${photo.id}`}
            className="relative z-10 w-full max-w-3xl overflow-hidden rounded-[1.5rem] bg-[#0b1524] shadow-[0_40px_100px_rgba(17,45,78,0.45),0_0_80px_rgba(63,114,175,0.25)]"
            transition={{ type: "spring", stiffness: 220, damping: 28, mass: 0.85 }}
          >
            <div className="relative aspect-[4/5] w-full sm:aspect-[16/11]">
              <Image
                src={photo.src}
                alt={photo.alt}
                fill
                sizes="(max-width: 768px) 100vw, 768px"
                className="object-cover"
                priority
              />
              <div
                className="pointer-events-none absolute inset-0"
                style={{
                  background:
                    "linear-gradient(180deg, rgba(255,255,255,0.12) 0%, transparent 28%, rgba(17,45,78,0.35) 100%)",
                  boxShadow: "inset 0 1px 0 rgba(255,255,255,0.35)",
                }}
              />
            </div>
            <figcaption className="absolute inset-x-0 bottom-0 flex items-end justify-between gap-3 p-5 sm:p-6">
              <div>
                <p className="text-[0.65rem] font-semibold uppercase tracking-[0.14em] text-white/70">
                  Cogniface
                </p>
                <p className="mt-1 font-display text-xl font-bold tracking-tight text-white sm:text-2xl">
                  {photo.title}
                </p>
              </div>
              <button
                type="button"
                data-gallery-chrome
                onClick={onClose}
                className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/35 bg-white/15 text-white shadow-lg backdrop-blur-md transition hover:bg-white/25 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                aria-label="Zamknij"
              >
                <X size={18} strokeWidth={2.2} />
              </button>
            </figcaption>
          </motion.figure>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
