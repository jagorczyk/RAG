import type { ReactNode } from "react";

export function PageHeader({
  title,
  subtitle,
  action,
  onBack,
  className = "",
  border = true,
  align = "start",
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  onBack?: () => void;
  className?: string;
  border?: boolean;
  /** start = default page headers; center = mobile chat-style */
  align?: "start" | "center";
}) {
  const isCenter = align === "center";

  return (
    <header
      className={`page-header flex min-h-[4rem] items-center justify-between gap-3 !pt-3.5 !pb-3 ${
        border ? "" : "!border-b-0"
      } ${className}`}
    >
      <div
        className={`flex min-w-0 flex-1 items-center gap-1 ${
          isCenter ? "justify-center md:justify-start" : ""
        }`}
      >
        {onBack && (
          <button
            type="button"
            onClick={onBack}
            className="icon-button -ml-2 mr-1 shadow-none"
            aria-label="Wróć"
          >
            <svg
              width="22"
              height="22"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden
            >
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </button>
        )}
        <div className={`min-w-0 ${isCenter ? "text-center md:text-left" : ""}`}>
          <h1 className="page-title truncate">{title}</h1>
          {subtitle && <p className="page-subtitle truncate">{subtitle}</p>}
        </div>
      </div>
      {action ? (
        <div className="flex shrink-0 items-center gap-2">{action}</div>
      ) : isCenter ? (
        <div className="w-9 shrink-0 md:hidden" aria-hidden />
      ) : null}
    </header>
  );
}
