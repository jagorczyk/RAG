"use client";

import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Pressable } from "./Pressable";

type IconButtonProps = {
  children: ReactNode;
  label: string;
  onClick?: ButtonHTMLAttributes<HTMLButtonElement>["onClick"];
  disabled?: boolean;
  className?: string;
  type?: "button" | "submit" | "reset";
};

export function IconButton({
  children,
  label,
  onClick,
  disabled = false,
  className = "",
  type = "button",
}: IconButtonProps) {
  return (
    <Pressable
      type={type}
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className={`icon-button ${className}`}
    >
      {children}
    </Pressable>
  );
}
