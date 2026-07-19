"use client";

import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useFocusTrap } from "@/hooks/useFocusTrap";

type BottomSheetProps = {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
  title?: string;
  description?: string;
  className?: string;
};

export function BottomSheet({
  open,
  onClose,
  children,
  title,
  description,
  className = "",
}: BottomSheetProps) {
  const reduced = useReducedMotion();
  const titleId = useId();
  const descriptionId = useId();
  const panelRef = useRef<HTMLDivElement>(null);

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
          className="fixed inset-0 z-50 flex items-end justify-center sm:items-center"
          role="presentation"
        >
          <motion.button
            type="button"
            aria-label="Zamknij panel"
            className="absolute inset-0 bg-scrim"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: reduced ? 0 : 0.22, ease: [0.22, 1, 0.36, 1] }}
            onClick={onClose}
          />
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={title ? titleId : undefined}
            aria-describedby={description ? descriptionId : undefined}
            tabIndex={-1}
            className={`relative z-10 max-h-[min(92vh,720px)] w-full max-w-lg overflow-y-auto rounded-t-[26px] bg-surface-raised px-5 pb-[max(2rem,env(safe-area-inset-bottom))] pt-1 shadow-float outline-none sm:rounded-[22px] sm:pb-6 ${className}`}
            initial={reduced ? { opacity: 0 } : { opacity: 0, y: 48 }}
            animate={reduced ? { opacity: 1 } : { opacity: 1, y: 0 }}
            exit={reduced ? { opacity: 0 } : { opacity: 0, y: 48 }}
            transition={{
              duration: reduced ? 0 : 0.22,
              ease: [0.22, 1, 0.36, 1],
            }}
          >
            <div className="sheet-handle sm:hidden" aria-hidden />
            {(title || description) && (
              <div className="mb-3">
                {title && (
                  <h2 id={titleId} className="text-xl font-extrabold tracking-tight text-ink">
                    {title}
                  </h2>
                )}
                {description && (
                  <p id={descriptionId} className="mt-1 text-sm text-ink-muted">
                    {description}
                  </p>
                )}
              </div>
            )}
            {children}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  );
}
