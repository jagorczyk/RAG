import type { UploadProgress } from "@/lib/api";
import type { CSSProperties } from "react";

interface LoadingBarProps {
  progress: UploadProgress;
}

export function LoadingBar({ progress }: LoadingBarProps) {
  const percent = Math.max(0, Math.min(100, progress.percent));
  const fillStyle = {
    ["--progress" as string]: String(percent / 100),
  } as CSSProperties;

  return (
    <div className="mb-4" role="status" aria-live="polite">
      <div className="mb-2 flex items-center justify-between gap-4">
        <p
          className="min-w-0 truncate text-sm text-ink-muted"
          title={progress.fileName}
        >
          {progress.fileName}
        </p>
        <span className="shrink-0 font-mono text-sm tabular-nums text-accent">
          {percent}%
        </span>
      </div>

      <div
        className="progress-track !mt-0"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-label={`Postęp wgrywania ${progress.fileName}: ${percent}%`}
      >
        <div className="progress-fill" style={fillStyle} />
      </div>
    </div>
  );
}
