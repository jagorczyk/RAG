"use client";

import Link from "next/link";
import { BookOpen, ChevronDown, Users } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import type { Message, QueryEvidence, Source } from "@/lib/api";
import type { MentionedPerson } from "@/lib/mentioned-people";

interface ChatMessageBubbleProps {
  message: Message;
  children: React.ReactNode;
  sources?: Source[];
  evidence?: QueryEvidence[];
  uncertain?: boolean;
  mentionedPeople?: MentionedPerson[];
  onSourcesOpen?: (sources: Source[], evidence: QueryEvidence[]) => void;
  onPeopleOpen?: (people: MentionedPerson[]) => void;
  index?: number;
}

export function ChatMessageBubble({
  message,
  children,
  sources,
  evidence,
  uncertain = false,
  mentionedPeople = [],
  onSourcesOpen,
  onPeopleOpen,
  index = 0,
}: ChatMessageBubbleProps) {
  const isUser = message.role === "user";
  const reduced = useReducedMotion();
  const sourceCount = sources?.length ?? 0;
  const peopleCount = mentionedPeople.length;

  const sourceLabel =
    sourceCount === 1
      ? "1 źródło"
      : sourceCount > 1 && sourceCount < 5
        ? `${sourceCount} źródła`
        : `${sourceCount} źródeł`;

  const peopleLabel =
    peopleCount === 1
      ? "1 osoba"
      : peopleCount > 1 && peopleCount < 5
        ? `${peopleCount} osoby`
        : `${peopleCount} osób`;

  return (
    <motion.article
      className={`mb-3 flex flex-col ${isUser ? "items-end" : "items-start"}`}
      initial={reduced ? { opacity: 0 } : { opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{
        duration: reduced ? 0.01 : 0.18,
        delay: reduced ? 0 : Math.min(index, 8) * 0.03,
        ease: [0.22, 1, 0.36, 1],
      }}
    >
      {isUser ? (
        <div className="max-w-[86%] rounded-[14px] rounded-br-[4px] bg-accent px-2.5 py-1.5 text-[0.9375rem] leading-[1.4] text-on-accent">
          <div className="whitespace-pre-wrap text-pretty">{children}</div>
        </div>
      ) : (
        <div className="max-w-[94%] py-0.5 text-[0.9375rem] leading-[1.4] text-ink">
          <div className="whitespace-pre-wrap text-pretty">{children}</div>
          {uncertain && (
            <p className="mt-2 text-xs font-semibold text-warning" role="status">
              Odpowiedź może być niepewna — sprawdź źródła.
            </p>
          )}
        </div>
      )}

      {(sourceCount > 0 || peopleCount > 0) && (
        <div className="mt-2 flex flex-wrap gap-2">
          {sourceCount > 0 && onSourcesOpen && (
            <button
              type="button"
              onClick={() => onSourcesOpen(sources!, evidence ?? [])}
              className="chip min-h-[var(--touch-min)]"
              aria-label={`Pokaż ${sourceLabel}`}
            >
              <BookOpen size={15} aria-hidden />
              {sourceLabel}
              <ChevronDown size={14} aria-hidden />
            </button>
          )}
          {peopleCount > 0 && onPeopleOpen && (
            <button
              type="button"
              onClick={() => onPeopleOpen(mentionedPeople)}
              className="chip min-h-[var(--touch-min)]"
              aria-label={`Pokaż ${peopleLabel}`}
            >
              <Users size={15} aria-hidden />
              {peopleLabel}
              <ChevronDown size={14} aria-hidden />
            </button>
          )}
        </div>
      )}

      {peopleCount > 0 && !onPeopleOpen && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {mentionedPeople.map((person) => (
            <Link
              key={person.id}
              href={`/knowledge/${person.id}`}
              className="chip min-h-[var(--touch-min)]"
            >
              <Users size={14} aria-hidden />
              {person.displayName}
            </Link>
          ))}
        </div>
      )}
    </motion.article>
  );
}

export function TypingIndicator() {
  return (
    <div className="mb-3 flex items-start" role="status" aria-live="polite">
      <span className="sr-only">Asystent pisze</span>
      <div className="flex gap-1 py-1.5" aria-hidden>
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="h-1.5 w-1.5 rounded-full bg-accent"
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
