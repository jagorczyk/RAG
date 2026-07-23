"use client";

import { useState, useEffect, useRef, useMemo } from "react";
import Link from "next/link";
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
  Users,
  Settings,
} from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { getChats, createChat, Chat, renameChat } from "@/lib/api";
import { getStoredUser } from "@/lib/auth";
import { CognifaceLogo } from "@/components/brand/CognifaceLogo";
import { Avatar } from "@/components/ui/Avatar";
import { SearchField } from "@/components/ui/SearchField";

function groupChats(chats: Chat[]) {
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const weekAgo = today - 6 * 24 * 60 * 60 * 1000;
  const groups: { title: string; items: Chat[] }[] = [
    { title: "Dzisiaj", items: [] },
    { title: "Ostatnie 7 dni", items: [] },
    { title: "Starsze", items: [] },
  ];
  chats.forEach((chat) => {
    const t = chat.updatedAt ? new Date(chat.updatedAt).getTime() : NaN;
    if (Number.isNaN(t)) groups[2].items.push(chat);
    else if (t >= today) groups[0].items.push(chat);
    else if (t >= weekAgo) groups[1].items.push(chat);
    else groups[2].items.push(chat);
  });
  return groups.filter((g) => g.items.length > 0);
}

export function Sidebar() {
  const [isOpen, setIsOpen] = useState(false);
  const [chats, setChats] = useState<Chat[]>([]);
  const [isCreating, setIsCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [isLoadingChats, setIsLoadingChats] = useState(true);
  const [editingChatId, setEditingChatId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const [chatQuery, setChatQuery] = useState("");
  const editInputRef = useRef<HTMLInputElement>(null);

  const pathname = usePathname();
  const router = useRouter();
  const user = getStoredUser();

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
    const mq = window.matchMedia("(min-width: 768px)");
    const syncToViewport = () => setIsOpen(mq.matches);
    syncToViewport();
    mq.addEventListener("change", syncToViewport);
    return () => mq.removeEventListener("change", syncToViewport);
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") return;
    if (window.matchMedia("(max-width: 767px)").matches) {
      setIsOpen(false);
    }
  }, [pathname]);

  useEffect(() => {
    if (editingChatId && editInputRef.current) {
      editInputRef.current.focus();
      editInputRef.current.select();
    }
  }, [editingChatId]);

  const handleCreateChat = async () => {
    setIsCreating(true);
    setCreateError(null);
    try {
      const newChat = await createChat();
      setChats((prev) => [newChat, ...prev]);
      setIsOpen(false);
      router.push(`/chat/${newChat.id}`);
    } catch (error) {
      console.error("Failed to create chat", error);
      setCreateError("Nie można utworzyć rozmowy. Sprawdź połączenie z serwerem.");
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
        prev.map((c) => (c.id === editingChatId ? { ...c, title: trimmedValue } : c))
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
  const isKnowledgeActive = pathname.startsWith("/knowledge");
  const isSettingsActive = pathname.startsWith("/settings");
  const groups = useMemo(() => {
    const needle = chatQuery.trim().toLocaleLowerCase("pl");
    const filtered = needle
      ? chats.filter((c) => c.title.toLocaleLowerCase("pl").includes(needle))
      : chats;
    return groupChats(filtered);
  }, [chats, chatQuery]);
  const filteredChats = useMemo(() => {
    const needle = chatQuery.trim().toLocaleLowerCase("pl");
    if (!needle) return chats;
    return chats.filter((c) => c.title.toLocaleLowerCase("pl").includes(needle));
  }, [chats, chatQuery]);
  const userSeed = user?.email || "cogniface-user";

  return (
    <>
      {isOpen && (
        <button
          type="button"
          className="fixed inset-0 z-[calc(var(--z-sticky)-1)] bg-scrim md:hidden"
          aria-label="Zamknij panel boczny"
          onClick={() => setIsOpen(false)}
        />
      )}
      <aside
        className={`absolute inset-y-0 left-0 z-[var(--z-sticky)] flex h-full shrink-0 flex-col border-r border-border bg-sidebar transition-[width,transform] duration-200 md:relative ${
          isOpen ? "w-64 translate-x-0" : "w-20 -translate-x-full md:translate-x-0"
        }`}
        style={{ transitionTimingFunction: "var(--ease-out)" }}
        aria-label="Nawigacja boczna"
      >
        <div className="flex h-12 items-center border-b border-border px-2">
          <Link
            href="/folders"
            className={`flex min-w-0 items-center gap-2 ${isOpen ? "flex-1" : "mx-auto justify-center"}`}
            title="Cogniface"
          >
            <CognifaceLogo className="h-6 w-6 shrink-0 text-accent" />
            <AnimatePresence>
              {isOpen && (
                <motion.p
                  initial={{ opacity: 0, x: -6 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -6 }}
                  transition={{ duration: 0.15 }}
                  className="font-display truncate text-sm font-bold tracking-tight text-ink"
                >
                  Cogniface
                </motion.p>
              )}
            </AnimatePresence>
          </Link>
          {isOpen && (
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              className="touch-target btn-ghost shrink-0"
              aria-label="Zwiń panel"
            >
              <PanelLeftClose size={18} aria-hidden />
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

        <nav className="flex min-h-0 flex-1 flex-col p-2.5">
          <ul className="space-y-0.5">
            <li>
              <Link
                href="/folders"
                className={`nav-item ${isFoldersActive ? "nav-item-active" : ""} ${!isOpen ? "justify-center px-0" : ""}`}
                title="Biblioteka"
              >
                <FolderOpen size={18} className="shrink-0" />
                {isOpen && <span className="truncate">Biblioteka</span>}
              </Link>
            </li>
            <li>
              <Link
                href="/knowledge"
                className={`nav-item ${isKnowledgeActive ? "nav-item-active" : ""} ${!isOpen ? "justify-center px-0" : ""}`}
                title="Osoby"
              >
                <Users size={18} className="shrink-0" />
                {isOpen && <span className="truncate">Osoby</span>}
              </Link>
            </li>
            <li className="md:hidden">
              <Link
                href="/chats"
                className={`nav-item ${pathname.startsWith("/chats") || pathname.startsWith("/chat") ? "nav-item-active" : ""} ${!isOpen ? "justify-center px-0" : ""}`}
                title="Rozmowy"
              >
                <MessageSquare size={18} className="shrink-0" />
                {isOpen && <span className="truncate">Rozmowy</span>}
              </Link>
            </li>
          </ul>

          <div className="my-3 border-t border-border" />

          {isOpen && (
            <div className="mb-2 flex items-center gap-1 px-1">
              <p className="section-caption min-w-0 flex-1 px-1">Rozmowy</p>
              <button
                type="button"
                onClick={handleCreateChat}
                disabled={isCreating}
                className="touch-target btn-ghost shrink-0 rounded-full p-1.5"
                title="Nowa rozmowa"
                aria-label="Nowa rozmowa"
              >
                {isCreating ? (
                  <Loader2 size={18} className="animate-spin" />
                ) : (
                  <Plus size={18} />
                )}
              </button>
            </div>
          )}
          {!isOpen && (
            <div className="mb-2">
              <button
                type="button"
                onClick={handleCreateChat}
                disabled={isCreating}
                className="nav-item w-full justify-center px-0"
                title="Nowa rozmowa"
                aria-label="Nowa rozmowa"
              >
                {isCreating ? (
                  <Loader2 size={18} className="animate-spin" />
                ) : (
                  <Plus size={18} />
                )}
              </button>
            </div>
          )}
          {createError && isOpen && (
            <p className="mb-2 px-2 text-xs text-error" role="alert">
              {createError}
            </p>
          )}

          {isOpen && (
            <SearchField
              value={chatQuery}
              onChange={setChatQuery}
              placeholder="Szukaj rozmów"
              aria-label="Szukaj rozmów"
              className="mb-2"
            />
          )}

          <div className="sidebar-scroll min-h-0 flex-1 overflow-y-auto pe-0.5">
            <ul className="space-y-0.5">
              {isLoadingChats && isOpen && (
                <>
                  <li className="px-1 py-1">
                    <div className="skeleton h-9 w-full" />
                  </li>
                  <li className="px-1 py-1">
                    <div className="skeleton h-9 w-full" />
                  </li>
                </>
              )}
              {!isLoadingChats && filteredChats.length === 0 && isOpen && (
                <li className="px-2 py-3 text-xs text-ink-muted">
                  {chatQuery.trim() ? "Brak wyników" : "Brak rozmów"}
                </li>
              )}
              {isOpen
                ? groups.map((group) => (
                    <li key={group.title} className="mb-2">
                      <p className="section-caption px-2 pt-2">{group.title}</p>
                      <ul className="space-y-0.5">
                        {group.items.map((chat) => (
                          <ChatRow
                            key={chat.id}
                            chat={chat}
                            isOpen={isOpen}
                            isActive={isChatActive(chat.id)}
                            editing={editingChatId === chat.id}
                            editValue={editValue}
                            editInputRef={editInputRef}
                            onEditChange={setEditValue}
                            onKeyDown={handleKeyDown}
                            onBlur={saveRename}
                            onSave={saveRename}
                            onCancel={() => setEditingChatId(null)}
                            onStartEdit={startEditing}
                          />
                        ))}
                      </ul>
                    </li>
                  ))
                : filteredChats.map((chat) => (
                    <ChatRow
                      key={chat.id}
                      chat={chat}
                      isOpen={isOpen}
                      isActive={isChatActive(chat.id)}
                      editing={false}
                      editValue=""
                      editInputRef={editInputRef}
                      onEditChange={setEditValue}
                      onKeyDown={handleKeyDown}
                      onBlur={saveRename}
                      onSave={saveRename}
                      onCancel={() => setEditingChatId(null)}
                      onStartEdit={startEditing}
                    />
                  ))}
            </ul>
          </div>

          <div className="mt-2 border-t border-border pt-2">
            {isOpen && user && (
              <div className="mb-1 flex items-center gap-2.5 rounded-[var(--radius-md)] px-2 py-2">
                <Avatar seed={userSeed} fallbackLabel={user.email} size="sm" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-ink" title={user.email}>
                    {user.email}
                  </p>
                </div>
              </div>
            )}
            {!isOpen && (
              <div className="mx-auto mb-1 flex justify-center" title={user?.email || "Profil"}>
                <Avatar seed={userSeed} fallbackLabel={user?.email || "?"} size="sm" />
              </div>
            )}
            <Link
              href="/settings"
              className={`nav-item w-full ${isSettingsActive ? "nav-item-active" : ""} ${!isOpen ? "justify-center px-0" : ""}`}
              title="Ustawienia"
            >
              <Settings size={18} className="shrink-0" />
              {isOpen && <span className="truncate">Ustawienia</span>}
            </Link>
          </div>
        </nav>
      </aside>
      {!isOpen && (
        <button
          type="button"
          onClick={() => setIsOpen(true)}
          className="mobile-drawer-toggle icon-button fixed z-40 border border-border md:hidden"
          aria-label="Otwórz panel rozmów"
        >
          <PanelLeftOpen size={18} />
        </button>
      )}
    </>
  );
}

function ChatRow({
  chat,
  isOpen,
  isActive,
  editing,
  editValue,
  editInputRef,
  onEditChange,
  onKeyDown,
  onBlur,
  onSave,
  onCancel,
  onStartEdit,
}: {
  chat: Chat;
  isOpen: boolean;
  isActive: boolean;
  editing: boolean;
  editValue: string;
  editInputRef: React.RefObject<HTMLInputElement | null>;
  onEditChange: (v: string) => void;
  onKeyDown: (e: React.KeyboardEvent) => void;
  onBlur: () => void;
  onSave: () => void;
  onCancel: () => void;
  onStartEdit: (e: React.MouseEvent, chat: Chat) => void;
}) {
  return (
    <li className="group relative">
      {editing ? (
        <div className="flex h-9 w-full items-center rounded-[10px] border border-border-strong bg-surface-raised px-2">
          <input
            ref={editInputRef}
            type="text"
            value={editValue}
            onChange={(e) => onEditChange(e.target.value)}
            onKeyDown={onKeyDown}
            onBlur={onBlur}
            className="min-w-0 flex-1 bg-transparent text-sm text-ink outline-none"
          />
          <div className="ml-1 flex shrink-0 items-center gap-0.5">
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={onSave}
              className="rounded p-1 text-ink-muted hover:bg-soft hover:text-ink"
              title="Zapisz"
            >
              <Check size={14} />
            </button>
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={onCancel}
              className="rounded p-1 text-ink-muted hover:bg-soft hover:text-error"
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
            className={`nav-item ${isActive ? "nav-item-active" : ""} ${!isOpen ? "justify-center px-0" : "pr-8"}`}
            title={chat.title}
          >
            <MessageSquare size={18} className="shrink-0" />
            {isOpen && <span className="truncate text-sm">{chat.title}</span>}
          </Link>
          {isOpen && (
            <div className="absolute right-1.5 top-1/2 flex -translate-y-1/2 items-center opacity-0 transition-opacity duration-150 group-hover:opacity-100 group-focus-within:opacity-100">
              <button
                type="button"
                onClick={(e) => onStartEdit(e, chat)}
                className="rounded-md p-1.5 text-ink-muted hover:bg-soft hover:text-ink"
                title="Zmień nazwę"
                aria-label={`Zmień nazwę: ${chat.title}`}
              >
                <Edit2 size={13} />
              </button>
            </div>
          )}
        </>
      )}
    </li>
  );
}
