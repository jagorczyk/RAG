"use client";

import { LayoutGrid, List } from "lucide-react";
import type { ViewMode } from "@/hooks/useViewMode";

interface ViewModeToggleProps {
  value: ViewMode;
  onChange: (mode: ViewMode) => void;
}

export function ViewModeToggle({ value, onChange }: ViewModeToggleProps) {
  return (
    <div className="view-mode-toggle" role="group" aria-label="Tryb widoku">
      <button
        type="button"
        className={value === "list" ? "is-active" : ""}
        onClick={() => onChange("list")}
        title="Lista"
        aria-pressed={value === "list"}
      >
        <List size={18} />
      </button>
      <button
        type="button"
        className={value === "grid" ? "is-active" : ""}
        onClick={() => onChange("grid")}
        title="Siatka"
        aria-pressed={value === "grid"}
      >
        <LayoutGrid size={18} />
      </button>
    </div>
  );
}
