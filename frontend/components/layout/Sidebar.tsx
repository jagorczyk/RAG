"use client";

import { useState, useEffect, useRef } from "react";
import Link from "next/link";
import Image from "next/image";
import { usePathname, useRouter } from "next/navigation";
import {
  MessageSquare,
  FolderOpen,
  Plus,
  PanelLeftClose,
  PanelLeftOpen,
  Loader2,
  Edit2,
  Check,
  X,
  Sun,
  Moon,
  Users,
} from "lucide-react";
import { getChats, createChat, Chat, renameChat } from "@/lib/api";
import { useTheme } from "@/lib/ThemeContext";

export function Sidebar() {
  const [isOpen, setIsOpen] = useState(true);
  const [chats, setChats] = useState<Chat[]>([]);
  const [isCreating, setIsCreating] = useState(false);
  const [isLoadingChats, setIsLoadingChats] = useState(true);
  const [editingChatId, setEditingChatId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const editInputRef = useRef<HTMLInputElement>(null);

  const pathname = usePathname();
  const router = useRouter();
  const { theme, toggleTheme } = useTheme();

  const fetchChats = async () => {
    try {
      const data = await getChats();
      setChats(data);
    } catch (error) {
      console.error("Failed to fetch chats", error);
    } finally {
      setIsLoadingChats(false);
    }
  };

  useEffect(() => {
    fetchChats();
  }, []);

  useEffect(() => {
    if (editingChatId && editInputRef.current) {
      editInputRef.current.focus();
      editInputRef.current.select();
    }
  }, [editingChatId]);

  const handleCreateChat = async () => {
    setIsCreating(true);
    try {
      const newChat = await createChat();
      setChats((prev) => [newChat, ...prev]);
      router.push(`/chat/${newChat.id}`);
    } catch (error) {
      console.error("Failed to create chat", error);
    } finally {
      setIsCreating(false);
    }
  };

  const startEditing = (e: React.MouseEvent, chat: Chat) => {
    e.preventDefault();
    e.stopPropagation();
    setEditingChatId(chat.id);
    setEditValue(chat.title);
  };

  const saveRename = async () => {
    if (!editingChatId) return;

    const trimmedValue = editValue.trim();
    const originalChat = chats.find((c) => c.id === editingChatId);

    if (!trimmedValue || trimmedValue === originalChat?.title) {
      setEditingChatId(null);
      return;
    }

    try {
      await renameChat(editingChatId, trimmedValue);
      setChats((prev) =>
        prev.map((c) =>
          c.id === editingChatId ? { ...c, title: trimmedValue } : c
        )
      );
    } catch (error) {
      console.error("Failed to rename chat", error);
      alert("Wystąpił błąd podczas zmiany nazwy.");
    } finally {
      setEditingChatId(null);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") saveRename();
    else if (e.key === "Escape") setEditingChatId(null);
  };

  const isChatActive = (id: string) => pathname === `/chat/${id}`;
  const isFoldersActive = pathname.startsWith("/folders");

  return (
    <aside
      className={`relative flex h-full shrink-0 flex-col border-r border-border bg-sidebar transition-[width] duration-200 ${
        isOpen ? "w-72" : "w-16"
      }`}
      style={{ transitionTimingFunction: "var(--ease-out)" }}
    >
      <div className="flex h-16 items-center border-b border-border px-3">
        <Link
          href="/folders"
          className={`flex min-w-0 items-center gap-2.5 ${isOpen ? "flex-1" : "mx-auto justify-center"}`}
          title="RAG"
        >
          <Image src="/logo_rag.png" alt="RAG" width={30} height={30} priority />
          {isOpen && (
            <p className="truncate text-sm font-semibold text-ink">RAG</p>
          )}
        </Link>
        {isOpen && (
          <button
            type="button"
            onClick={() => setIsOpen(false)}
            className="btn-ghost shrink-0 p-1.5"
            aria-label="Zwiń panel"
          >
            <PanelLeftClose size={18} />
          </button>
        )}
        {!isOpen && (
          <button
            type="button"
            onClick={() => setIsOpen(true)}
            className="btn-ghost absolute -right-3 top-5 z-20 rounded-full border border-border bg-surface-raised p-1.5 shadow-sm"
            aria-label="Rozwiń panel"
          >
            <PanelLeftOpen size={16} />
          </button>
        )}
      </div>

      <div className="border-b border-border p-3">
        <button
          type="button"
          onClick={handleCreateChat}
          disabled={isCreating}
          className={`btn-primary w-full ${!isOpen ? "px-0 py-2.5" : "py-2.5"}`}
          title="Nowa konwersacja"
        >
          {isCreating ? (
            <Loader2 size={18} className="animate-spin" />
          ) : (
            <Plus size={18} />
          )}
          {isOpen && <span>Nowa konwersacja</span>}
        </button>
      </div>

      <nav className="flex flex-1 flex-col gap-4 overflow-y-auto p-3">
        <ul className="space-y-0.5">
          <li>
            <Link
              href="/folders"
              className={`nav-item ${isFoldersActive ? "nav-item-active" : ""} ${!isOpen ? "justify-center px-0" : ""}`}
              title="Foldery"
            >
              <FolderOpen size={18} className="shrink-0" />
              {isOpen && <span className="truncate">Foldery</span>}
            </Link>
          </li>
          <li>
            <Link
              href="/knowledge"
              className={`nav-item ${pathname.startsWith("/knowledge") ? "nav-item-active" : ""} ${!isOpen ? "justify-center px-0" : ""}`}
              title="Osoby"
            >
              <Users size={18} className="shrink-0" />
              {isOpen && <span className="truncate">Osoby</span>}
            </Link>
          </li>
        </ul>

        <div className="border-t border-border" />

        <div className="min-h-0 flex-1">
          <ul className="space-y-0.5">
            {isLoadingChats && isOpen && (
              <>
                <li className="px-2 py-2">
                  <div className="skeleton h-8 w-full" />
                </li>
                <li className="px-2 py-2">
                  <div className="skeleton h-8 w-full" />
                </li>
              </>
            )}
            {!isLoadingChats && chats.length === 0 && isOpen && (
              <li className="px-2 py-3 text-xs text-ink-muted">
                Brak rozmów
              </li>
            )}
            {chats.map((chat) => (
              <li key={chat.id} className="group relative">
                {editingChatId === chat.id ? (
                  <div className="flex h-9 w-full items-center rounded-[6px] border border-border-strong bg-surface-raised px-2">
                    <input
                      ref={editInputRef}
                      type="text"
                      value={editValue}
                      onChange={(e) => setEditValue(e.target.value)}
                      onKeyDown={handleKeyDown}
                      onBlur={saveRename}
                      className="min-w-0 flex-1 bg-transparent text-sm text-ink outline-none"
                    />
                    <div className="ml-1 flex shrink-0 items-center gap-0.5">
                      <button
                        type="button"
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={saveRename}
                        className="rounded p-1 text-ink-muted hover:bg-accent-subtle hover:text-success"
                        title="Zapisz"
                      >
                        <Check size={14} />
                      </button>
                      <button
                        type="button"
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={() => setEditingChatId(null)}
                        className="rounded p-1 text-ink-muted hover:bg-accent-subtle hover:text-error"
                        title="Anuluj"
                      >
                        <X size={14} />
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    <Link
                      href={`/chat/${chat.id}`}
                      className={`nav-item ${isChatActive(chat.id) ? "nav-item-active" : ""} ${!isOpen ? "justify-center px-0" : "pr-8"}`}
                      title={chat.title}
                    >
                      <MessageSquare size={18} className="shrink-0" />
                      {isOpen && (
                        <span className="truncate text-sm">{chat.title}</span>
                      )}
                    </Link>
                    {isOpen && (
                      <div className="absolute right-1.5 top-1/2 flex -translate-y-1/2 items-center opacity-0 transition-opacity group-hover:opacity-100">
                        <button
                          type="button"
                          onClick={(e) => startEditing(e, chat)}
                          className="rounded p-1 text-ink-muted hover:bg-accent-subtle hover:text-ink"
                          title="Zmień nazwę"
                        >
                          <Edit2 size={13} />
                        </button>
                      </div>
                    )}
                  </>
                )}
              </li>
            ))}
          </ul>
        </div>
      </nav>

      <div className="border-t border-border p-2">
        <button
          type="button"
          onClick={toggleTheme}
          className={`nav-item w-full ${!isOpen ? "justify-center px-0" : ""}`}
          title={theme === "light" ? "Tryb ciemny" : "Tryb jasny"}
        >
          {theme === "light" ? <Moon size={18} /> : <Sun size={18} />}
          {isOpen && (
            <span>{theme === "light" ? "Ciemny" : "Jasny"}</span>
          )}
        </button>
      </div>
    </aside>
  );
}
