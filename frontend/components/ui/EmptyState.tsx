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
    <div className={`flex flex-col items-center justify-center px-8 py-14 text-center ${className}`}>
      <div className="mb-4 flex h-[62px] w-[62px] items-center justify-center rounded-[22px] bg-soft text-[1.7rem]">
        {icon}
      </div>
      <h3 className="text-lg font-extrabold text-ink">{title}</h3>
      <p className="mt-1.5 mb-4 max-w-sm text-sm leading-5 text-ink-muted">{description}</p>
      {action}
    </div>
  );
}
