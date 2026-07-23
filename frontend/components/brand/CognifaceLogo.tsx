type CognifaceLogoProps = {
  className?: string;
  title?: string;
};

/** Greek stoic full-face mask, frontal outline — `currentColor` / `text-accent`. */
export function CognifaceLogo({
  className = "h-10 w-10 text-accent",
  title = "Cogniface",
}: CognifaceLogoProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 64 64"
      fill="none"
      className={className}
      role="img"
      aria-label={title}
    >
      <title>{title}</title>
      {/* Classical oval — frontal Greek mask */}
      <path
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M32 5c-12.5 0-22 9.2-22 23.5S19.5 59 32 59s22-16 22-30.5S44.5 5 32 5Z"
      />
      {/* Brow line */}
      <path
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        d="M15 24h34"
      />
      {/* Almond eyes — calm, open, symmetrical */}
      <path
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M17.5 29.5c3.2-2.2 8.8-2.2 12 0 1.2.8 1.2 2.2 0 3-3.2 2.2-8.8 2.2-12 0-1.2-.8-1.2-2.2 0-3Z"
      />
      <path
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M34.5 29.5c3.2-2.2 8.8-2.2 12 0 1.2.8 1.2 2.2 0 3-3.2 2.2-8.8 2.2-12 0-1.2-.8-1.2-2.2 0-3Z"
      />
      {/* Straight nose — classical bridge + base */}
      <path
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M32 24v14.5M26.5 38.5h11"
      />
      {/* Stoic mouth — closed, serene arc */}
      <path
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        d="M23 46.5c2.8 2.2 6.4 3.2 9 3.2s6.2-1 9-3.2"
      />
    </svg>
  );
}
