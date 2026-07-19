"use client";

import { BookOpen, ChevronDown } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import type { Message, Source } from "@/lib/api";

interface ChatMessageBubbleProps {
  message: Message;
  children: React.ReactNode;
  sources?: Source[];
  uncertain?: boolean;
  onSourcesOpen?: (sources: Source[]) => void;
  index?: number;
}

export function ChatMessageBubble({
  message,
  children,
  sources,
  uncertain = false,
  onSourcesOpen,
  index = 0,
}: ChatMessageBubbleProps) {
  const isUser = message.role === "user";
  const reduced = useReducedMotion();
  const sourceCount = sources?.length ?? 0;

  const sourceLabel =
    sourceCount === 1
      ? "1 źródło"
      : sourceCount > 1 && sourceCount < 5
        ? `${sourceCount} źródła`
        : `${sourceCount} źródeł`;

  return (
    <motion.article
      className={`mb-4.5 flex flex-col ${isUser ? "items-end" : "items-start"}`}
      initial={reduced ? { opacity: 0 } : { opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{
        duration: reduced ? 0.01 : 0.18,
        delay: reduced ? 0 : Math.min(index, 8) * 0.03,
        ease: [0.22, 1, 0.36, 1],
      }}
    >
      {isUser ? (
        <div className="max-w-[86%] rounded-[20px] rounded-br-[5px] bg-ink px-3.5 py-2.5 text-[15px] leading-[1.45] text-on-accent">
          <div className="whitespace-pre-wrap text-pretty">{children}</div>
        </div>
      ) : (
        <div className="max-w-[94%] py-0.5 text-[15px] leading-[1.45] text-ink">
          <div className="whitespace-pre-wrap text-pretty">{children}</div>
          {uncertain && (
            <p className="mt-2 text-xs font-semibold text-warning" role="status">
              Odpowiedź może być niepewna — sprawdź źródła.
            </p>
          )}
        </div>
      )}

      {sourceCount > 0 && onSourcesOpen && (
        <button
          type="button"
          onClick={() => onSourcesOpen(sources!)}
          className="chip mt-2 min-h-[var(--touch-min)]"
          aria-label={`Pokaż ${sourceLabel}`}
        >
          <BookOpen size={15} aria-hidden />
          {sourceLabel}
          <ChevronDown size={14} aria-hidden />
        </button>
      )}
    </motion.article>
  );
}

export function TypingIndicator() {
  return (
    <div className="mb-4.5 flex items-start" role="status" aria-live="polite">
      <span className="sr-only">Asystent pisze</span>
      <div className="flex gap-1 py-1.5" aria-hidden>
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="h-1.5 w-1.5 rounded-full bg-ink"
            style={{
              animation: "typing-pulse 1.4s ease-in-out infinite",
              animationDelay: `${i * 0.18}s`,
            }}
          />
        ))}
      </div>
    </div>
  );
}
