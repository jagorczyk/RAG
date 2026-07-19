import type { ReactNode } from "react";

export function SectionTitle({
  children,
  action,
  className = "",
}: {
  children: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={`mt-4 mb-2 flex items-center justify-between gap-2 ${className}`}>
      <h2 className="text-base font-extrabold tracking-tight text-ink">{children}</h2>
      {action}
    </div>
  );
}
