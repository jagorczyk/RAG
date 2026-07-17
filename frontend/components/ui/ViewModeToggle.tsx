"use client";

import { LayoutGrid, List } from "lucide-react";

type ViewMode = "list" | "grid";

export function ViewModeToggle({
  value,
  onChange,
}: {
  value: ViewMode;
  onChange: (mode: ViewMode) => void;
}) {
  return (
    <div className="view-mode-toggle" role="group" aria-label="Tryb widoku">
      <button
        type="button"
        className={value === "list" ? "is-active" : ""}
        onClick={() => onChange("list")}
        aria-pressed={value === "list"}
        aria-label="Lista"
      >
        <List size={17} />
      </button>
      <button
        type="button"
        className={value === "grid" ? "is-active" : ""}
        onClick={() => onChange("grid")}
        aria-pressed={value === "grid"}
        aria-label="Siatka"
      >
        <LayoutGrid size={17} />
      </button>
    </div>
  );
}
