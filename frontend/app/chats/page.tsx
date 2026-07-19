"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { MessageCircle, Plus, MoreHorizontal, Loader2 } from "lucide-react";
import { createChat, getChats, renameChat, type Chat } from "@/lib/api";
import { SearchField } from "@/components/ui/SearchField";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { FadeModal } from "@/components/ui/FadeModal";
import { Button } from "@/components/ui/Button";
import { AnimatedItem } from "@/components/ui/AnimatedList";
import { IconButton } from "@/components/ui/IconButton";
import { PageHeader } from "@/components/ui/PageHeader";

function chatSections(chats: Chat[]) {
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const weekAgo = today - 6 * 24 * 60 * 60 * 1000;
  const groups: Record<string, Chat[]> = {
    Dzisiaj: [],
    "Ostatnie 7 dni": [],
    Starsze: [],
  };
  chats.forEach((chat) => {
    if (!chat.updatedAt || Number.isNaN(new Date(chat.updatedAt).getTime())) {
      groups.Starsze.push(chat);
    } else if (new Date(chat.updatedAt).getTime() >= today) {
      groups.Dzisiaj.push(chat);
    } else if (new Date(chat.updatedAt).getTime() >= weekAgo) {
      groups["Ostatnie 7 dni"].push(chat);
    } else {
      groups.Starsze.push(chat);
    }
  });
  return Object.entries(groups)
    .filter(([, data]) => data.length)
    .map(([title, data]) => ({ title, data }));
}

function formatChatDate(value?: string) {
  if (!value) return "Rozmowa bez wiadomości";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Rozmowa bez wiadomości";
  return new Intl.DateTimeFormat("pl-PL", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export default function ChatsPage() {
  const router = useRouter();
  const [chats, setChats] = useState<Chat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [query, setQuery] = useState("");
  const [creating, setCreating] = useState(false);
  const [renameTarget, setRenameTarget] = useState<Chat | null>(null);
  const [newName, setNewName] = useState("");
  const [renaming, setRenaming] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(false);
    try {
      setChats(await getChats());
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const sections = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase("pl");
    const filtered = chats.filter((chat) =>
      chat.title.toLocaleLowerCase("pl").includes(needle)
    );
    return chatSections(filtered);
  }, [chats, query]);

  const handleCreate = async () => {
    setCreating(true);
    try {
      const chat = await createChat();
      router.push(`/chat/${chat.id}`);
    } catch {
      alert("Nie udało się utworzyć rozmowy.");
    } finally {
      setCreating(false);
    }
  };

  const handleRename = async () => {
    if (!renameTarget || !newName.trim()) return;
    setRenaming(true);
    try {
      await renameChat(renameTarget.id, newName.trim());
      setChats((prev) =>
        prev.map((c) =>
          c.id === renameTarget.id ? { ...c, title: newName.trim() } : c
        )
      );
      setRenameTarget(null);
    } catch {
      alert("Nie udało się zmienić nazwy.");
    } finally {
      setRenaming(false);
    }
  };

  return (
    <div className="page-shell">
      <PageHeader
        title="Rozmowy"
        subtitle="Zapytaj swoją bazę wiedzy"
        action={
          <IconButton
            label="Nowa rozmowa"
            onClick={handleCreate}
            disabled={creating}
            className="!bg-accent !text-on-accent shadow-md"
          >
            {creating ? (
              <Loader2 size={20} className="animate-spin" aria-hidden />
            ) : (
              <Plus size={22} aria-hidden />
            )}
          </IconButton>
        }
      />

      <div className="page-body max-w-3xl">
        <SearchField
          value={query}
          onChange={setQuery}
          placeholder="Szukaj rozmów"
          className="mb-3"
        />

        {loading && <Loading label="Ładowanie rozmów" />}
        {error && (
          <EmptyState
            icon="☁️"
            title="Brak połączenia"
            description="Nie udało się pobrać rozmów. Sprawdź połączenie z serwerem."
            action={
              <button type="button" className="btn-primary" onClick={load}>
                Spróbuj ponownie
              </button>
            }
          />
        )}

        {!loading && !error && sections.length === 0 && (
          <EmptyState
            icon="💬"
            title={query ? "Brak wyników" : "Zacznij pierwszą rozmowę"}
            description={
              query
                ? "Spróbuj użyć innego słowa."
                : "Zadaj pytanie o osoby na zdjęciach lub dokumenty w bibliotece."
            }
            action={
              !query ? (
                <button type="button" className="btn-primary" onClick={handleCreate}>
                  <Plus size={18} /> Nowa rozmowa
                </button>
              ) : undefined
            }
          />
        )}

        {!loading &&
          !error &&
          sections.map((section) => (
            <section key={section.title} className="mb-2">
              <p className="section-caption pt-4 pb-1.5">{section.title}</p>
              <div className="list-panel">
                {section.data.map((chat, index) => (
                  <AnimatedItem key={chat.id} index={index}>
                    <div className="list-row group">
                      <button
                        type="button"
                        className="flex min-h-[var(--touch-min)] min-w-0 flex-1 items-center gap-3 text-left"
                        onClick={() => router.push(`/chat/${chat.id}`)}
                      >
                        <span className="list-row-icon" aria-hidden>
                          <MessageCircle size={18} />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-base font-bold text-ink">
                            {chat.title || "Nowa rozmowa"}
                          </span>
                          <span className="mt-0.5 block text-[13px] text-ink-muted">
                            {formatChatDate(chat.updatedAt)}
                          </span>
                        </span>
                      </button>
                      <button
                        type="button"
                        className="touch-target shrink-0 text-ink-muted opacity-70 transition-opacity hover:opacity-100"
                        aria-label={`Zmień nazwę ${chat.title || "rozmowy"}`}
                        onClick={() => {
                          setNewName(chat.title === "Nowa rozmowa" ? "" : chat.title);
                          setRenameTarget(chat);
                        }}
                      >
                        <MoreHorizontal size={20} aria-hidden />
                      </button>
                    </div>
                  </AnimatedItem>
                ))}
              </div>
            </section>
          ))}
      </div>

      <FadeModal
        open={!!renameTarget}
        onClose={() => !renaming && setRenameTarget(null)}
        variant="card"
        title="Zmień nazwę rozmowy"
      >
        <h2 className="text-lg font-extrabold text-ink">Zmień nazwę rozmowy</h2>
        <input
          autoFocus
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="Nazwa rozmowy"
          className="input-field mt-3.5"
          onKeyDown={(e) => {
            if (e.key === "Enter" && newName.trim()) handleRename();
          }}
        />
        <div className="mt-4 flex gap-2">
          <Button
            label="Anuluj"
            secondary
            disabled={renaming}
            onClick={() => setRenameTarget(null)}
          />
          <Button
            label={renaming ? "Zapisuję…" : "Zapisz"}
            disabled={!newName.trim() || renaming}
            onClick={handleRename}
          />
        </div>
      </FadeModal>
    </div>
  );
}
