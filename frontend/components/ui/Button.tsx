"use client";

import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Pressable } from "./Pressable";

type ButtonProps = {
  label: string;
  onClick?: ButtonHTMLAttributes<HTMLButtonElement>["onClick"];
  secondary?: boolean;
  disabled?: boolean;
  icon?: ReactNode;
  type?: "button" | "submit" | "reset";
  className?: string;
};

export function Button({
  label,
  onClick,
  secondary = false,
  disabled = false,
  icon,
  type = "button",
  className = "",
}: ButtonProps) {
  return (
    <Pressable
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`${secondary ? "btn-secondary" : "btn-primary"} w-full ${className}`}
    >
      {icon}
      <span>{label}</span>
    </Pressable>
  );
}
