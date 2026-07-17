"use client";

import { motion, useReducedMotion } from "motion/react";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type PressableProps = {
  children: ReactNode;
  className?: string;
  disabled?: boolean;
  onClick?: ButtonHTMLAttributes<HTMLButtonElement>["onClick"];
  type?: "button" | "submit" | "reset";
  "aria-label"?: string;
  title?: string;
};

export function Pressable({
  children,
  className = "",
  disabled = false,
  onClick,
  type = "button",
  "aria-label": ariaLabel,
  title,
}: PressableProps) {
  const reduced = useReducedMotion();

  return (
    <motion.button
      type={type}
      disabled={disabled}
      onClick={onClick}
      aria-label={ariaLabel}
      title={title}
      className={className}
      style={{ opacity: disabled ? 0.45 : 1 }}
      whileTap={reduced || disabled ? undefined : { scale: 0.98 }}
      transition={{ duration: 0.1, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </motion.button>
  );
}
