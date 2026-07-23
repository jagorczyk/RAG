/** Initials for avatar fallbacks (no external cartoon generators). */
export function initialsFrom(label: string, max = 2): string {
  const parts = label
    .trim()
    .split(/[\s@._-]+/)
    .filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, max).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}
