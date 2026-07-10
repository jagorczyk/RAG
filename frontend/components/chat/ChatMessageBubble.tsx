"use client";

import type { Message, Source } from "@/lib/api";

interface ChatMessageBubbleProps {
  message: Message;
  children: React.ReactNode;
  sources?: Source[];
  onSourceClick?: (source: Source) => void;
}

export function ChatMessageBubble({
  message,
  children,
  sources,
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
      </div>

      {sources && sources.length > 0 && (
        <div className="flex w-full max-w-[min(100%,46rem)] flex-wrap gap-1.5 pt-0.5">
          {sources.map((source, idx) => {
            const imageUrl = getSourceImageUrl(source);
            const clickable = !!(imageUrl || source.path) && onSourceClick;
            return (
              <button
                key={idx}
                type="button"
                onClick={() => clickable && onSourceClick?.(source)}
                className={`chip ${
                  clickable ? "cursor-pointer" : "cursor-default"
                }`}
                title={`${source.fileName} · score ${source.score.toFixed(4)}`}
              >
                {imageUrl ? (
                  <span className="h-4 w-4 overflow-hidden rounded-sm bg-border">
                    <img
                      src={imageUrl}
                      alt=""
                      className="h-full w-full object-cover"
                    />
                  </span>
                ) : (
                  <span className="text-[9px] font-mono">
                    {renderSourceIcon(source.type)}
                  </span>
                )}
                <span className="max-w-[140px] truncate font-mono text-[11px]">
                  {source.fileName}
                </span>
              </button>
            );
          })}
        </div>
      )}

    </article>
  );
}
