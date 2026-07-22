"use client";

import Image from "next/image";
import { AnimatePresence, motion } from "motion/react";
import { X } from "lucide-react";
import type { GalleryPhoto } from "./galleryData";

type GalleryLightboxProps = {
  photo: GalleryPhoto | null;
  onClose: () => void;
  layoutPrefix?: string;
};

export function GalleryLightbox({
  photo,
  onClose,
  layoutPrefix = "main",
}: GalleryLightboxProps) {
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
          role="dialog"
          aria-modal="true"
          aria-label={`Podgląd: ${photo.title}`}
        >
          <motion.button
            type="button"
            aria-label="Zamknij podgląd"
            className="absolute inset-0 bg-[#112D4E]/48 backdrop-blur-[14px]"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />

          <motion.figure
            layoutId={`gallery-frame-${layoutPrefix}-${photo.id}`}
            className="relative z-10 w-full max-w-3xl overflow-hidden rounded-[1.5rem] bg-[#0b1524] shadow-[0_40px_100px_rgba(17,45,78,0.45),0_0_72px_rgba(63,114,175,0.22)]"
            transition={{
              type: "spring",
              stiffness: 220,
              damping: 28,
              mass: 0.85,
            }}
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
                    "linear-gradient(180deg, rgba(255,255,255,0.12) 0%, transparent 28%, rgba(17,45,78,0.38) 100%)",
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
