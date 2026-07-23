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
      className={`sheet-action ${destructive ? "sheet-action-destructive" : ""}`}
    >
      <span className="sheet-action-icon" aria-hidden>
        {icon}
      </span>
      <span className="sheet-action-label">{label}</span>
    </button>
  );
}
