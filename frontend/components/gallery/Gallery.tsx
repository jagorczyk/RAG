"use client";

import { useCallback, useEffect, useState } from "react";
import { LayoutGroup, useReducedMotion } from "motion/react";
import { GALLERY_PHOTOS, type GalleryPhoto } from "./galleryData";
import { GalleryItem } from "./GalleryItem";
import { GalleryLightbox } from "./GalleryLightbox";
import { useGalleryAnimation } from "./useGalleryAnimation";

type GalleryProps = {
  photos?: GalleryPhoto[];
  className?: string;
  /** When true, fills parent (landing panel). */
  embedded?: boolean;
  /** Unique prefix so desktop + mobile instances don't clash on layoutId. */
  instanceId?: string;
  /** Pause RAF flight (e.g. while phone hero is visible). */
  animationPaused?: boolean;
};

const SURFACE_BG =
  "radial-gradient(ellipse 58% 48% at 50% 44%, rgba(219,226,239,0.55) 0%, transparent 72%), linear-gradient(180deg, #F9F7F7 0%, #F3F1F1 100%)";

export function Gallery({
  photos = GALLERY_PHOTOS,
  className = "",
  embedded = true,
  instanceId = "main",
  animationPaused = false,
}: GalleryProps) {
  const reducedMotionPref = useReducedMotion();
  const reduced = reducedMotionPref === true;
  const [mounted, setMounted] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = photos.find((p) => p.id === selectedId) ?? null;

  useEffect(() => {
    setMounted(true);
  }, []);

  const animation = useGalleryAnimation({
    itemCount: photos.length,
    enabled: mounted && selectedId === null && !reduced && !animationPaused,
    reducedMotion: reduced || !mounted || animationPaused,
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

  if (!mounted) {
    return (
      <div
        className={`relative h-full min-h-[220px] w-full overflow-hidden bg-surface ${className}`}
        aria-hidden
      >
        <div className="absolute inset-0" style={{ background: SURFACE_BG }} />
      </div>
    );
  }

  if (reduced) {
    return (
      <div
        className={`relative h-full min-h-[220px] w-full overflow-y-auto bg-surface ${className}`}
      >
        <div className="grid grid-cols-2 gap-3 p-4 sm:grid-cols-3 lg:grid-cols-2 xl:grid-cols-3">
          {photos.slice(0, 8).map((photo, index) => (
            <GalleryItem
              key={photo.id}
              photo={photo}
              index={index}
              cameraZ={animation.cameraZ}
              pointerX={animation.pointerX}
              pointerY={animation.pointerY}
              depthScale={animation.depthScale}
              selectedId={selectedId}
              reducedMotion
              layoutPrefix={instanceId}
              onSelect={setSelectedId}
            />
          ))}
        </div>
        <GalleryLightbox
          photo={selected}
          onClose={close}
          layoutPrefix={instanceId}
        />
      </div>
    );
  }

  return (
    <LayoutGroup id={`gallery-${instanceId}`}>
      <div
        className={`relative h-full min-h-[220px] w-full overflow-hidden bg-surface touch-none select-none ${className}`}
        ref={animation.bindStage}
        role="region"
        aria-label="Galeria zdjęć 3D. Przewiń lub przeciągnij, aby przelecieć między zdjęciami. Kliknij, aby powiększyć."
      >
        <div
          className="pointer-events-none absolute inset-0"
          style={{ background: SURFACE_BG }}
        />
        <div
          className="pointer-events-none absolute left-1/2 top-[40%] h-[58%] w-[72%] -translate-x-1/2 -translate-y-1/2 rounded-full opacity-55 blur-3xl"
          style={{
            background:
              "radial-gradient(circle, rgba(63,114,175,0.32) 0%, rgba(63,114,175,0.07) 48%, transparent 72%)",
          }}
        />
        <div
          className="pointer-events-none absolute inset-0 opacity-45"
          style={{
            background:
              "radial-gradient(circle at 28% 18%, rgba(255,255,255,0.58), transparent 42%)",
          }}
        />
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              "radial-gradient(ellipse at center, transparent 42%, rgba(17,45,78,0.07) 100%)",
          }}
        />

        <div
          className="absolute inset-0 flex items-center justify-center"
          style={{
            perspective: embedded ? "1100px" : "1400px",
            perspectiveOrigin: "50% 46%",
          }}
        >
          <div
            className="relative h-full w-full"
            style={{
              transformStyle: "preserve-3d",
              transform: "translateZ(0)",
            }}
          >
            {photos.map((photo, index) => (
              <GalleryItem
                key={photo.id}
                photo={photo}
                index={index}
                cameraZ={animation.cameraZ}
                pointerX={animation.pointerX}
                pointerY={animation.pointerY}
                depthScale={animation.depthScale}
                selectedId={selectedId}
                reducedMotion={false}
                layoutPrefix={instanceId}
                consumeDragGuard={animation.consumeDragGuard}
                onSelect={setSelectedId}
              />
            ))}
          </div>
        </div>

        <div className="pointer-events-none absolute inset-y-0 left-0 w-14 bg-gradient-to-r from-surface to-transparent lg:w-20" />
        <div className="pointer-events-none absolute inset-y-0 right-0 w-14 bg-gradient-to-l from-surface to-transparent lg:w-20" />
        <div className="pointer-events-none absolute inset-x-0 top-0 h-14 bg-gradient-to-b from-surface to-transparent" />
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-surface to-transparent" />

        <GalleryLightbox
          photo={selected}
          onClose={close}
          layoutPrefix={instanceId}
        />
      </div>
    </LayoutGroup>
  );
}
