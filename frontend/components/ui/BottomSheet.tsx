"use client";

import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { X } from "lucide-react";
import { useFocusTrap } from "@/hooks/useFocusTrap";

type BottomSheetProps = {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
  title?: string;
  description?: string;
  className?: string;
  bodyClassName?: string;
  showClose?: boolean;
  footer?: React.ReactNode;
  flush?: boolean;
};

export function BottomSheet({
  open,
  onClose,
  children,
  title,
  description,
  className = "",
  bodyClassName = "",
  showClose = true,
  footer,
  flush = false,
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

  const durationIn = reduced ? 0 : 0.26;
  const durationOut = reduced ? 0 : 0.18;
  const ease = [0.16, 1, 0.3, 1] as const;

  return createPortal(
    <AnimatePresence>
      {open && (
        <div
          className="fixed inset-0 z-[var(--z-modal)] flex items-end justify-center sm:items-center sm:p-6"
          role="presentation"
        >
          <motion.button
            type="button"
            aria-label="Zamknij panel"
            className="modal-scrim"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: durationOut, ease }}
            onClick={onClose}
          />
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={title ? titleId : undefined}
            aria-describedby={description ? descriptionId : undefined}
            tabIndex={-1}
            className={`modal-panel modal-panel-sheet ${className}`}
            initial={reduced ? { opacity: 0 } : { opacity: 0, y: 36 }}
            animate={reduced ? { opacity: 1 } : { opacity: 1, y: 0 }}
            exit={reduced ? { opacity: 0 } : { opacity: 0, y: 24 }}
            transition={{ duration: durationIn, ease }}
          >
            <div className="sheet-handle sm:hidden" aria-hidden />
            {(title || description || showClose) && (
              <div className="modal-header !border-t-0 sm:!rounded-t-[var(--radius-xl)]">
                <div className="min-w-0 flex-1">
                  {title && (
                    <h2 id={titleId} className="modal-title">
                      {title}
                    </h2>
                  )}
                  {description && (
                    <p id={descriptionId} className="modal-description">
                      {description}
                    </p>
                  )}
                </div>
                {showClose ? (
                  <button
                    type="button"
                    onClick={onClose}
                    className="modal-close"
                    aria-label="Zamknij"
                  >
                    <X size={16} aria-hidden />
                  </button>
                ) : null}
              </div>
            )}
            <div className={`modal-body ${flush ? "modal-body-flush" : ""} ${bodyClassName}`}>
              {children}
            </div>
            {footer ? <div className="modal-footer">{footer}</div> : null}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  );
}
