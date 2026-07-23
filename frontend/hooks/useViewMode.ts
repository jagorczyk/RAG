"use client";

import { useState } from "react";

export type ViewMode = "list" | "grid";

export function useViewMode(
  storageKey = "rag-folder-view-mode",
  defaultMode: ViewMode = "list"
) {
  const [viewMode, setViewModeState] = useState<ViewMode>(() => {
    if (typeof window === "undefined") return defaultMode;
    const stored = localStorage.getItem(storageKey);
    return stored === "list" || stored === "grid" ? stored : defaultMode;
  });

  const setViewMode = (mode: ViewMode) => {
    setViewModeState(mode);
    localStorage.setItem(storageKey, mode);
  };

  return { viewMode, setViewMode };
}
