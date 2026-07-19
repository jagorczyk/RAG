"use client";

import { Search, X } from "lucide-react";

type SearchFieldProps = {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  "aria-label"?: string;
};

export function SearchField({
  value,
  onChange,
  placeholder = "Szukaj",
  className = "",
  "aria-label": ariaLabel = "Szukaj",
}: SearchFieldProps) {
  return (
    <div className={`search-field ${className}`} role="search">
      <Search size={17} className="shrink-0 text-ink-muted" aria-hidden />
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={ariaLabel}
        autoCorrect="off"
        autoComplete="off"
        type="search"
        enterKeyHint="search"
      />
      {!!value && (
        <button
          type="button"
          onClick={() => onChange("")}
          className="touch-target shrink-0 text-ink-muted transition-opacity hover:opacity-70"
          aria-label="Wyczyść wyszukiwanie"
        >
          <X size={18} aria-hidden />
        </button>
      )}
    </div>
  );
}
