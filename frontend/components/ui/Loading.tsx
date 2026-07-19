"use client";

import { Loader2 } from "lucide-react";

export function Loading({
  label = "Ładowanie",
  className = "",
}: {
  label?: string;
  className?: string;
}) {
  return (
    <div
      className={`flex flex-col items-center justify-center p-8 ${className}`}
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      <Loader2 size={22} className="animate-spin text-ink" aria-hidden />
      <p className="mt-2.5 text-[13px] text-ink-muted">{label}</p>
    </div>
  );
}

export function Skeleton({
  className = "",
  height = 70,
}: {
  className?: string;
  height?: number | string;
}) {
  return (
    <div
      className={`skeleton mb-2.5 w-full ${className}`}
      style={{ height: typeof height === "number" ? `${height}px` : height }}
      aria-hidden
    />
  );
}
