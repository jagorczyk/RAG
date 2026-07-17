"use client";

import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";

type FadeModalProps = {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
  className?: string;
  contentClassName?: string;
  /** fullscreen surface (photo viewer) vs centered card */
  variant?: "fullscreen" | "card";
};

export function FadeModal({
  open,
  onClose,
  children,
  className = "",
  contentClassName = "",
  variant = "fullscreen",
}: FadeModalProps) {
  const reduced = useReducedMotion();
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  useEffect(() => {
    if (open) panelRef.current?.focus();
  }, [open]);

  if (typeof document === "undefined") return null;

  return createPortal(
    <AnimatePresence>
      {open && (
        <div
          className={`fixed inset-0 z-50 ${variant === "card" ? "flex items-center justify-center p-6" : ""} ${className}`}
        >
          {variant === "card" && (
            <motion.button
              type="button"
              aria-label="Zamknij"
              className="absolute inset-0 bg-black"
              initial={{ opacity: 0 }}
              animate={{ opacity: 0.28 }}
              exit={{ opacity: 0 }}
              transition={{ duration: reduced ? 0 : 0.18 }}
              onClick={onClose}
            />
          )}
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            tabIndex={-1}
            className={
              variant === "fullscreen"
                ? `flex h-full w-full flex-col bg-surface outline-none ${contentClassName}`
                : `relative z-10 w-full max-w-md rounded-[18px] bg-surface p-5 shadow-float outline-none ${contentClassName}`
            }
            initial={
              reduced
                ? { opacity: 0 }
                : { opacity: 0, scale: 0.985 }
            }
            animate={{ opacity: 1, scale: 1 }}
            exit={
              reduced
                ? { opacity: 0 }
                : { opacity: 0, scale: 0.985 }
            }
            transition={{ duration: reduced ? 0 : 0.18, ease: [0.22, 1, 0.36, 1] }}
          >
            {children}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  );
}
