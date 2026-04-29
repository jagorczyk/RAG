"use client";

import { useState, useEffect, useRef } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { 
  MessageSquare, 
  FolderOpen, 
  Plus, 
  PanelLeftClose, 
  PanelLeftOpen,
  LayoutDashboard,
  Loader2,
  Edit2,
  Check,
  X
} from "lucide-react";
import { getChats, createChat, Chat, renameChat } from "@/lib/api";

export function Sidebar() {
  const [isOpen, setIsOpen] = useState(true);
  const [chats, setChats] = useState<Chat[]>([]);
  const [isCreating, setIsCreating] = useState(false);
  const [editingChatId, setEditingChatId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const editInputRef = useRef<HTMLInputElement>(null);
  
  const pathname = usePathname();
  const router = useRouter();

  const fetchChats = async () => {
    try {
      const data = await getChats();
      setChats(data);
    } catch (error) {
      console.error("Failed to fetch chats", error);
    }
  };

  useEffect(() => {
    fetchChats();
  }, [pathname]);

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
      setChats(prev => [newChat, ...prev]);
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
    const originalChat = chats.find(c => c.id === editingChatId);
    
    if (!trimmedValue || trimmedValue === originalChat?.title) {
      setEditingChatId(null);
      return;
    }
    
    try {
      await renameChat(editingChatId, trimmedValue);
      setChats(prev => prev.map(c => c.id === editingChatId ? { ...c, title: trimmedValue } : c));
    } catch (error) {
      console.error("Failed to rename chat", error);
      alert("Wystąpił błąd podczas zmiany nazwy.");
    } finally {
      setEditingChatId(null);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      saveRename();
    } else if (e.key === "Escape") {
      setEditingChatId(null);
    }
  };

  return (
    <div 
      className={`relative flex flex-col h-full bg-gray-50 border-r border-gray-200 transition-all duration-300 ${
        isOpen ? "w-64" : "w-16"
      }`}
    >
      {}
      <div className="flex items-center justify-between p-4 border-b border-gray-200 h-16">
        {isOpen && <span className="font-semibold text-slate-900 truncate">Dokumenty</span>}
        <button 
          onClick={() => setIsOpen(!isOpen)} 
          className="p-1 text-gray-500 hover:text-slate-900 rounded-md hover:bg-gray-200 transition-colors"
        >
          {isOpen ? <PanelLeftClose size={20} /> : <PanelLeftOpen size={20} />}
        </button>
      </div>

      {}
      <div className="p-3">
        <button 
          onClick={handleCreateChat}
          disabled={isCreating}
          className={`flex items-center justify-center gap-2 w-full p-2 bg-slate-900 hover:bg-slate-800 text-white rounded-md transition-colors disabled:opacity-50 ${!isOpen && "px-0"}`}
          title="Utwórz nowy chat"
        >
          {isCreating ? <Loader2 size={20} className="animate-spin" /> : <Plus size={20} />}
          {isOpen && <span className="font-medium">Nowa konwersacja</span>}
        </button>
      </div>

      {}
      <nav className="flex-1 overflow-y-auto p-3 space-y-4">
        <div>
          {isOpen && <p className="text-xs font-semibold text-gray-500 mb-2 uppercase tracking-wider px-2">Menu</p>}
          <ul className="space-y-1">
            <li>
              <Link 
                href="/folders"
                className={`flex items-center gap-3 p-2 rounded-md transition-colors ${
                  pathname.startsWith("/folders") ? "bg-gray-200 text-slate-900" : "text-gray-600 hover:bg-gray-100 hover:text-slate-900"
                }`}
                title="Foldery"
              >
                <FolderOpen size={20} />
                {isOpen && <span className="truncate">Foldery</span>}
              </Link>
            </li>
          </ul>
        </div>

        <div>
          {isOpen && <p className="text-xs font-semibold text-gray-500 mb-2 uppercase tracking-wider px-2">Ostatnie rozmowy</p>}
          <ul className="space-y-1">
            {chats.map(chat => (
              <li key={chat.id} className="group relative">
                {editingChatId === chat.id ? (
                  <div className="flex items-center h-9 w-full bg-white border border-slate-400 rounded-md px-2 shadow-sm ring-1 ring-slate-400/10">
                    <input
                      ref={editInputRef}
                      type="text"
                      value={editValue}
                      onChange={(e) => setEditValue(e.target.value)}
                      onKeyDown={handleKeyDown}
                      onBlur={saveRename}
                      className="flex-1 min-w-0 text-sm text-gray-900 bg-transparent outline-none"
                    />
                    <div className="flex items-center gap-0.5 shrink-0 ml-1">
                      <button 
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={saveRename} 
                        className="p-1 text-slate-400 hover:text-green-600 hover:bg-green-50 rounded transition-colors"
                        title="Zapisz"
                      >
                        <Check size={14} />
                      </button>
                      <button 
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={() => setEditingChatId(null)} 
                        className="p-1 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors"
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
                      className={`flex items-center gap-3 p-2 pr-8 rounded-md transition-colors ${
                        pathname === `/chat/${chat.id}` ? "bg-gray-200 text-slate-900" : "text-gray-600 hover:bg-gray-100 hover:text-slate-900"
                      }`}
                      title={chat.title}
                    >
                      <MessageSquare size={20} className="shrink-0" />
                      {isOpen && <span className="truncate text-sm">{chat.title}</span>}
                    </Link>
                    {isOpen && (
                      <div className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center opacity-0 group-hover:opacity-100 transition-opacity bg-inherit pl-1">
                        <button
                          onClick={(e) => startEditing(e, chat)}
                          className="p-1 text-gray-400 hover:text-gray-700 hover:bg-gray-200 rounded transition-colors"
                          title="Zmień nazwę"
                        >
                          <Edit2 size={14} />
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
    </div>
  );
}
