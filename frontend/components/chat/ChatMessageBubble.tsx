"use client";

import type { Message, Source } from "@/lib/api";

interface ChatMessageBubbleProps {
  message: Message;
  children: React.ReactNode;
  sources?: Source[];
  uncertain?: boolean;
  onSourceClick?: (source: Source) => void;
}

export function ChatMessageBubble({
  message,
  children,
  sources,
  uncertain = false,
  onSourceClick,
}: ChatMessageBubbleProps) {
  const isUser = message.role === "user";

  const renderSourceIcon = (type: Source["type"]) => {
    switch (type) {
      case "IMAGE":
        return "IMG";
      case "PDF":
        return "PDF";
      case "TEXT":
        return "TXT";
      case "GRAPH_FACT":
        return "GRF";
      default:
        return "SRC";
    }
  };

  const getSourceImageUrl = (source: Source) => {
    if (source.type === "IMAGE" && source.base64) {
      const mime = source.fileName.toLowerCase().endsWith(".png")
        ? "image/png"
        : "image/jpeg";
      return `data:${mime};base64,${source.base64}`;
    }
    return null;
  };

  // Visual sources are emitted only after confirmation; no provisional group is rendered.
  const suggestedPaths = new Set<string>();

  const renderSources = (items: Source[], label?: string) => {
    if (items.length === 0) return null;
    return (
      <div className="flex w-full max-w-[min(100%,46rem)] flex-wrap gap-1.5 pt-0.5">
        {label && (
          <span className="basis-full text-[11px] font-medium text-ink-muted">{label}</span>
        )}
        {items.map((source, idx) => {
          const imageUrl = getSourceImageUrl(source);
          const clickable = !!(imageUrl || source.path) && onSourceClick;
          return (
            <button
              key={`${source.path}-${idx}`}
              type="button"
              onClick={() => clickable && onSourceClick?.(source)}
              className={`chip ${clickable ? "cursor-pointer" : "cursor-default"}`}
              title={`${source.fileName} · score ${source.score.toFixed(4)}`}
            >
              {imageUrl ? (
                <span className="h-4 w-4 overflow-hidden rounded-sm bg-border">
                  <img src={imageUrl} alt="" className="h-full w-full object-cover" />
                </span>
              ) : (
                <span className="text-[9px] font-mono">{renderSourceIcon(source.type)}</span>
              )}
              <span className="max-w-[140px] truncate font-mono text-[11px]">{source.fileName}</span>
            </button>
          );
        })}
      </div>
    );
  };

  return (
    <article
      className={`flex flex-col gap-1.5 ${
        isUser ? "items-end" : "items-start"
      }`}
    >
      <div
        className={`w-full max-w-[min(100%,46rem)] rounded-[10px] border px-4 py-3 ${
          isUser
            ? "border-border bg-surface-raised"
            : "border-border bg-sidebar/70"
        }`}
      >
        <div className="max-w-[65ch] whitespace-pre-wrap text-sm leading-relaxed text-ink text-pretty">
          {children}
        </div>
        {!isUser && uncertain && (
          <p className="mt-2 rounded-md border border-amber-500/30 bg-amber-500/10 px-2.5 py-1.5 text-xs text-amber-800 dark:text-amber-200">
            Odpowiedź może być niepełna — tożsamość lub pewność danych z grafu jest niska.
          </p>
        )}
      </div>

      {sources && sources.length > 0 && (
        <>
          {renderSources(sources.filter((source) => !suggestedPaths.has(source.path)), "Potwierdzone")}
          {renderSources(sources.filter((source) => suggestedPaths.has(source.path)), "Niepewne")}
        </>
      )}

      {message.evidence && message.evidence.length > 0 && (
        <div className="w-full max-w-[min(100%,46rem)] space-y-1 text-[11px] text-ink-muted">
          {message.evidence.map((item) => (
            <p key={`${item.path}-${item.reasons.join("|")}`}>
              <span className="font-medium">Dowód:</span> {item.reasons.join("; ")}
              {` (${Math.round(item.confidence * 100)}%)`}
            </p>
          ))}
        </div>
      )}

    </article>
  );
}
