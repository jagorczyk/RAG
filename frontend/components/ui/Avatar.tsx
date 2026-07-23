"use client";

import { useState } from "react";
import { initialsFrom } from "@/lib/avatar";

const SIZE_CLASS = {
  xs: "h-6 w-6 text-[0.625rem]",
  sm: "h-8 w-8 text-[0.6875rem]",
  md: "h-10 w-10 text-xs",
  lg: "h-12 w-12 text-sm",
} as const;

type AvatarProps = {
  seed: string;
  alt?: string;
  /** Real photo / data URL — when missing, show initials. */
  src?: string | null;
  size?: keyof typeof SIZE_CLASS;
  className?: string;
  fallbackLabel?: string;
};

export function Avatar({
  seed,
  alt = "",
  src,
  size = "sm",
  className = "",
  fallbackLabel,
}: AvatarProps) {
  const [failed, setFailed] = useState(false);
  const showPhoto = !!src && !failed;

  return (
    <span
      className={`avatar avatar-${size} ${SIZE_CLASS[size]} ${className}`}
      aria-hidden={alt ? undefined : true}
    >
      {showPhoto ? (
        // eslint-disable-next-line @next/next/no-img-element -- data URLs / local previews
        <img
          src={src}
          alt={alt}
          className="h-full w-full object-cover"
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
        />
      ) : (
        <span className="font-semibold tracking-tight text-ink">
          {initialsFrom(fallbackLabel || seed)}
        </span>
      )}
    </span>
  );
}
