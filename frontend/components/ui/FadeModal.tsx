"use client";

import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { X } from "lucide-react";
import { useFocusTrap } from "@/hooks/useFocusTrap";

type FadeModalProps = {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
  className?: string;
  contentClassName?: string;
  bodyClassName?: string;
  /** fullscreen surface (photo viewer) vs centered card */
  variant?: "fullscreen" | "card";
  title?: string;
  description?: string;
  showClose?: boolean;
  footer?: React.ReactNode;
  /** Remove default body padding (lists / flush content) */
  flush?: boolean;
  "aria-label"?: string;
};

export function FadeModal({
  open,
  onClose,
  children,
  className = "",
  contentClassName = "",
  bodyClassName = "",
  variant = "fullscreen",
  title,
  description,
  showClose = true,
  footer,
  flush = false,
  "aria-label": ariaLabel,
}: FadeModalProps) {
  const reduced = useReducedMotion();
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const descriptionId = useId();

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

  const durationIn = reduced ? 0 : 0.22;
  const durationOut = reduced ? 0 : 0.16;
  const ease = [0.16, 1, 0.3, 1] as const;

  return createPortal(
    <AnimatePresence>
      {open && (
        <div
          className={`fixed inset-0 z-[var(--z-modal)] ${
            variant === "card" ? "flex items-center justify-center p-4 sm:p-6" : ""
          } ${className}`}
          role="presentation"
        >
          {variant === "card" && (
            <motion.button
              type="button"
              aria-label="Zamknij dialog"
              className="modal-scrim"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: durationOut, ease }}
              onClick={onClose}
            />
          )}
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={title ? titleId : undefined}
            aria-describedby={description ? descriptionId : undefined}
            aria-label={!title ? ariaLabel : undefined}
            tabIndex={-1}
            className={
              variant === "fullscreen"
                ? `relative z-10 flex h-full w-full flex-col bg-surface outline-none ${contentClassName}`
                : `modal-panel ${contentClassName}`
            }
            initial={
              reduced
                ? { opacity: 0 }
                : variant === "card"
                  ? { opacity: 0, y: 12, scale: 0.96 }
                  : { opacity: 0 }
            }
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={
              reduced
                ? { opacity: 0 }
                : variant === "card"
                  ? { opacity: 0, y: 8, scale: 0.97 }
                  : { opacity: 0 }
            }
            transition={{ duration: durationIn, ease }}
          >
            {variant === "card" && (title || showClose) ? (
              <>
                <div className="modal-header">
                  <div className="min-w-0 flex-1">
                    {title ? (
                      <h2 id={titleId} className="modal-title">
                        {title}
                      </h2>
                    ) : null}
                    {description ? (
                      <p id={descriptionId} className="modal-description">
                        {description}
                      </p>
                    ) : null}
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
                <div
                  className={`modal-body ${flush ? "modal-body-flush" : ""} ${bodyClassName}`}
                >
                  {children}
                </div>
                {footer ? <div className="modal-footer">{footer}</div> : null}
              </>
            ) : (
              <>
                {title ? (
                  <h2 id={titleId} className="sr-only">
                    {title}
                  </h2>
                ) : null}
                {children}
              </>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  );
}
