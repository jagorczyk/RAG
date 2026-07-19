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
    <div className={`flex flex-col items-center justify-center px-6 py-10 text-center ${className}`}>
      <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-[14px] bg-soft text-xl">
        {icon}
      </div>
      <h3 className="text-base font-extrabold text-ink">{title}</h3>
      <p className="mt-1 mb-3 max-w-sm text-sm leading-snug text-ink-muted">{description}</p>
      {action}
    </div>
  );
}
