"use client";

import { useState } from "react";

export type ViewMode = "list" | "grid";

const STORAGE_KEY = "rag-folder-view-mode";

export function useViewMode(defaultMode: ViewMode = "list") {
  const [viewMode, setViewModeState] = useState<ViewMode>(() => {
    if (typeof window === "undefined") return defaultMode;
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === "list" || stored === "grid" ? stored : defaultMode;
  });

  const setViewMode = (mode: ViewMode) => {
    setViewModeState(mode);
    localStorage.setItem(STORAGE_KEY, mode);
  };

  return { viewMode, setViewMode };
}
