"use client";

import { useEffect, useState } from "react";

export type ViewMode = "list" | "grid";

const STORAGE_KEY = "rag-folder-view-mode";

export function useViewMode(defaultMode: ViewMode = "list") {
  const [viewMode, setViewModeState] = useState<ViewMode>(defaultMode);

  useEffect(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "list" || stored === "grid") {
      setViewModeState(stored);
    }
  }, []);

  const setViewMode = (mode: ViewMode) => {
    setViewModeState(mode);
    localStorage.setItem(STORAGE_KEY, mode);
  };

  return { viewMode, setViewMode };
}
