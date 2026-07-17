"use client";

import type { ReactNode } from "react";

export function SheetAction({
  icon,
  label,
  onClick,
  destructive = false,
}: {
  icon: ReactNode;
  label: string;
  onClick: () => void;
  destructive?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-[3.25rem] w-full items-center gap-3.5 text-left transition-opacity active:opacity-55"
    >
      <span className={destructive ? "text-error" : "text-ink"}>{icon}</span>
      <span
        className={`text-base font-bold ${destructive ? "text-error" : "text-ink"}`}
      >
        {label}
      </span>
    </button>
  );
}
