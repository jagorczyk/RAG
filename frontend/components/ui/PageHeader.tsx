import type { ReactNode } from "react";
import { ChevronLeft } from "lucide-react";

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
      className={`page-header flex min-h-[3.25rem] items-center justify-between gap-3 !pt-3 !pb-2.5 ${
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
            className="icon-button -ml-1 mr-1 shadow-none"
            aria-label="Wróć"
          >
            <ChevronLeft size={20} aria-hidden />
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
