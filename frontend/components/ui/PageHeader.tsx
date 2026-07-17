import type { ReactNode } from "react";

export function PageHeader({
  title,
  subtitle,
  action,
  onBack,
  className = "",
  border = true,
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  onBack?: () => void;
  className?: string;
  border?: boolean;
}) {
  return (
    <header
      className={`flex min-h-[4rem] items-center justify-between gap-3 px-5 pt-3.5 pb-3 ${
        border ? "border-b border-border" : ""
      } ${className}`}
    >
      <div className="flex min-w-0 flex-1 items-center gap-1">
        {onBack && (
          <button
            type="button"
            onClick={onBack}
            className="icon-button -ml-2 mr-1 shadow-none"
            aria-label="Wróć"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </button>
        )}
        <div className="min-w-0">
          <h1 className="page-title truncate text-[1.875rem] md:text-[2rem]">{title}</h1>
          {subtitle && <p className="page-subtitle truncate">{subtitle}</p>}
        </div>
      </div>
      {action && <div className="flex shrink-0 items-center gap-2">{action}</div>}
    </header>
  );
}
