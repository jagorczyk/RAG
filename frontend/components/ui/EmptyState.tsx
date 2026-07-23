import type { ReactNode } from "react";

export function EmptyState({
  icon,
  title,
  description,
  action,
  className = "",
}: {
  icon: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={`flex flex-col items-center justify-center px-6 py-12 text-center ${className}`}
    >
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-[var(--radius-lg)] border border-border bg-surface-raised text-accent shadow-sm">
        {icon}
      </div>
      <h3 className="font-display text-lg font-bold tracking-tight text-ink text-balance">
        {title}
      </h3>
      <p className="mt-2 mb-5 max-w-sm text-sm leading-snug text-ink-muted text-pretty">
        {description}
      </p>
      {action}
    </div>
  );
}
