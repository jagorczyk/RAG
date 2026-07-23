"use client";

import Link from "next/link";
import { BookOpen, ChevronDown } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import type { Message, QueryEvidence, Source } from "@/lib/api";
import type { MentionedPerson } from "@/lib/mentioned-people";
import { CognifaceLogo } from "@/components/brand/CognifaceLogo";
import { Avatar } from "@/components/ui/Avatar";
import { getStoredUser } from "@/lib/auth";

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
  const user = getStoredUser();
  const userSeed = user?.email || "you";

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
      className={`mb-4 flex flex-col ${isUser ? "items-end" : "items-start"}`}
      initial={reduced ? { opacity: 0 } : { opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{
        duration: reduced ? 0.01 : 0.2,
        delay: reduced ? 0 : Math.min(index, 8) * 0.025,
        ease: [0.22, 1, 0.36, 1],
      }}
    >
      {isUser ? (
        <div className="flex max-w-[min(86%,32rem)] items-end gap-2">
          <div className="rounded-[var(--radius-lg)] rounded-br-md bg-soft px-3.5 py-2.5 text-[0.9375rem] leading-[1.45] text-ink shadow-sm">
            <div className="whitespace-pre-wrap text-pretty">{children}</div>
          </div>
          <Avatar seed={userSeed} fallbackLabel={user?.email || "Ty"} size="xs" className="mb-0.5" />
        </div>
      ) : (
        <div className="flex max-w-[min(94%,40rem)] gap-2.5 py-0.5 text-[0.9375rem] leading-[1.45] text-ink">
          <span
            className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-border bg-surface-raised shadow-sm"
            aria-hidden
          >
            <CognifaceLogo className="h-4 w-4 text-accent" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="whitespace-pre-wrap text-pretty">{children}</div>
            {uncertain && (
              <p className="mt-2 text-xs font-semibold text-warning" role="status">
                Odpowiedź może być niepewna — sprawdź źródła.
              </p>
            )}
          </div>
        </div>
      )}

      {(sourceCount > 0 || peopleCount > 0) && (
        <div className={`mt-2 flex flex-wrap gap-2 ${isUser ? "" : "pl-9"}`}>
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
          {peopleCount > 0 &&
            (onPeopleOpen ? (
              <button
                type="button"
                onClick={() => onPeopleOpen(mentionedPeople)}
                className="chip min-h-[var(--touch-min)]"
                aria-label={`Pokaż ${peopleLabel}`}
              >
                <span className="flex -space-x-1.5" aria-hidden>
                  {mentionedPeople.slice(0, 3).map((person) => (
                    <Avatar
                      key={person.id}
                      seed={person.id}
                      src={
                        person.photoBase64
                          ? `data:image/jpeg;base64,${person.photoBase64}`
                          : null
                      }
                      fallbackLabel={person.displayName}
                      size="xs"
                      className="!h-5 !w-5 ring-1 ring-surface-raised"
                    />
                  ))}
                </span>
                {peopleLabel}
                <ChevronDown size={14} aria-hidden />
              </button>
            ) : (
              mentionedPeople.map((person) => (
                <Link
                  key={person.id}
                  href={`/knowledge/${person.id}`}
                  className="chip min-h-[var(--touch-min)]"
                >
                  <Avatar
                    seed={person.id}
                    src={
                      person.photoBase64
                        ? `data:image/jpeg;base64,${person.photoBase64}`
                        : null
                    }
                    fallbackLabel={person.displayName}
                    size="xs"
                    className="!h-5 !w-5"
                  />
                  {person.displayName}
                </Link>
              ))
            ))}
        </div>
      )}
    </motion.article>
  );
}

export function TypingIndicator() {
  return (
    <div className="mb-4 flex items-start gap-2.5 pl-0" role="status" aria-live="polite">
      <span
        className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-border bg-surface-raised shadow-sm"
        aria-hidden
      >
        <CognifaceLogo className="h-4 w-4 text-accent" />
      </span>
      <span className="sr-only">Asystent pisze</span>
      <div className="flex gap-1 py-2" aria-hidden>
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
