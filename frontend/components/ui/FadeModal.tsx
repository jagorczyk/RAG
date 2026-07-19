"use client";

import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useFocusTrap } from "@/hooks/useFocusTrap";

type FadeModalProps = {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
  className?: string;
  contentClassName?: string;
  /** fullscreen surface (photo viewer) vs centered card */
  variant?: "fullscreen" | "card";
  title?: string;
  "aria-label"?: string;
};

export function FadeModal({
  open,
  onClose,
  children,
  className = "",
  contentClassName = "",
  variant = "fullscreen",
  title,
  "aria-label": ariaLabel,
}: FadeModalProps) {
  const reduced = useReducedMotion();
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  useFocusTrap(open, panelRef, onClose);

  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [open]);

  if (typeof document === "undefined") return null;

  return createPortal(
    <AnimatePresence>
      {open && (
        <div
          className={`fixed inset-0 z-50 ${variant === "card" ? "flex items-center justify-center p-6" : ""} ${className}`}
          role="presentation"
        >
          {variant === "card" && (
            <motion.button
              type="button"
              aria-label="Zamknij dialog"
              className="absolute inset-0 bg-scrim"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: reduced ? 0 : 0.18 }}
              onClick={onClose}
            />
          )}
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={title ? titleId : undefined}
            aria-label={!title ? ariaLabel : undefined}
            tabIndex={-1}
            className={
              variant === "fullscreen"
                ? `flex h-full w-full flex-col bg-surface outline-none ${contentClassName}`
                : `relative z-10 w-full max-w-md rounded-[18px] bg-surface p-5 shadow-float outline-none ${contentClassName}`
            }
            initial={reduced ? { opacity: 0 } : { opacity: 0, scale: 0.985 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={reduced ? { opacity: 0 } : { opacity: 0, scale: 0.985 }}
            transition={{ duration: reduced ? 0 : 0.18, ease: [0.22, 1, 0.36, 1] }}
          >
            {title ? (
              <h2 id={titleId} className="sr-only">
                {title}
              </h2>
            ) : null}
            {children}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  );
}
