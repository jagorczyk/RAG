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
    <div className={`mt-6 mb-2.5 flex items-center justify-between gap-3 ${className}`}>
      <h2 className="text-xl font-extrabold tracking-tight text-ink">{children}</h2>
      {action}
    </div>
  );
}
