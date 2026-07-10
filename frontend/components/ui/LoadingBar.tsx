import type { UploadProgress } from "@/lib/api";

interface LoadingBarProps {
  progress: UploadProgress;
}

export function LoadingBar({ progress }: LoadingBarProps) {
  const percent = Math.max(0, Math.min(100, progress.percent));

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
        className="h-2 overflow-hidden rounded-full bg-border"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-label={`${percent}%`}
      >
        <div
          className="h-full rounded-full bg-accent transition-[width] duration-150 ease-out"
          style={{ width: `${percent}%` }}
        />
      </div>
    </div>
  );
}
