"use client";

import { useState, useEffect, useRef } from "react";
import {
  Loader2,
  FileText,
  Image as ImageIcon,
  FolderOpen,
  AtSign,
  SendHorizonal,
} from "lucide-react";
import {
  getMessagesForChat,
  sendMessage,
  Message,
  Source,
  getFolders,
  getAllFiles,
  Folder,
  FileItem,
  getFilePreview,
  FilePreview,
} from "@/lib/api";
import { useRouter } from "next/navigation";
import { ImagePreview } from "@/components/ui/ImagePreview";
import { ChatMessageBubble } from "@/components/chat/ChatMessageBubble";

interface ChatInterfaceProps {
  chatId?: string;
}

type Suggestion = {
  id: string;
  name: string;
  type: "folder" | "file";
  url?: string;
};

export function ChatInterface({ chatId }: ChatInterfaceProps) {
  const router = useRouter();
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState("");
  const [isInitialLoading, setIsInitialLoading] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [previewFile, setPreviewFile] = useState<FilePreview | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const [showSuggestions, setShowSuggestions] = useState(false);
  const [suggestionFilter, setSuggestionFilter] = useState("");
  const [allFolders, setAllFolders] = useState<Folder[]>([]);
  const [allFiles, setAllFiles] = useState<FileItem[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const inputRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (chatId) {
      setIsInitialLoading(true);
      setHistoryIndex(-1);
      getMessagesForChat(chatId)
        .then((msgs) => {
          setMessages(msgs);
          setIsInitialLoading(false);
        })
        .catch((err) => {
          console.error("Failed to fetch messages", err);
          setIsInitialLoading(false);
        });
    } else {
      setMessages([]);
    }
  }, [chatId]);

  useEffect(() => {
    Promise.all([getFolders(), getAllFiles()])
      .then(([folders, files]) => {
        setAllFolders(folders);
        setAllFiles(files);
      })
      .catch((err) => console.error("Failed to fetch suggestions data", err));
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isSending]);

  const handleInput = (e: React.FormEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    const value = target.innerText;
    const normalizedValue = value.replace(/\n/g, "").trim();

    if (normalizedValue === "") {
      setInputValue("");
      if (target.innerText !== "") target.innerText = "";
    } else {
      setInputValue(value);
    }

    setSelectedIndex(0);

    const lastAtIdx = value.lastIndexOf("@");
    if (lastAtIdx !== -1) {
      const query = value.slice(lastAtIdx + 1);
      if (!query.includes(" ")) {
        setSuggestionFilter(query);
        setShowSuggestions(true);
        return;
      }
    }
    setShowSuggestions(false);
  };

  const filteredSuggestions = (() => {
    const filter = suggestionFilter.toLowerCase();
    const matchedFolders = allFolders.filter((f) =>
      f.name.toLowerCase().includes(filter)
    );
    const folderSuggestions = matchedFolders.map((f) => ({
      id: f.id,
      name: f.name,
      type: "folder" as const,
    }));
    const fileSuggestions = allFiles
      .filter((f) => {
        const fileName = f.name.toLowerCase();
        if (fileName.includes(filter)) return true;
        const pathParts = f.id.split("/");
        if (pathParts.length >= 3) {
          const folderName = pathParts[2].toLowerCase();
          if (folderName.includes(filter)) return true;
        }
        return false;
      })
      .map((f) => ({
        id: f.id,
        name: f.name,
        type: "file" as const,
        url: f.url,
      }));
    return [...folderSuggestions, ...fileSuggestions].slice(0, 15);
  })();

  const selectSuggestion = (suggestion: Suggestion) => {
    const lastAtIdx = inputValue.lastIndexOf("@");
    const textBeforeAt = inputValue.slice(0, lastAtIdx);
    const newValue = `${textBeforeAt}@${suggestion.name} `;
    setInputValue(newValue);
    setShowSuggestions(false);

    if (inputRef.current) {
      inputRef.current.innerText = newValue;
      const range = document.createRange();
      const sel = window.getSelection();
      range.selectNodeContents(inputRef.current);
      range.collapse(false);
      sel?.removeAllRanges();
      sel?.addRange(range);
      inputRef.current.focus();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (showSuggestions && filteredSuggestions.length > 0) {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        setSelectedIndex((prev) => (prev + 1) % filteredSuggestions.length);
        return;
      }
      if (e.key === "ArrowUp") {
        e.preventDefault();
        setSelectedIndex(
          (prev) =>
            (prev - 1 + filteredSuggestions.length) % filteredSuggestions.length
        );
        return;
      }
      if (e.key === "Enter" || e.key === "Tab") {
        e.preventDefault();
        selectSuggestion(filteredSuggestions[selectedIndex]);
        return;
      }
    }

    if (!showSuggestions) {
      const userMessages = messages.filter((m) => m.role === "user");
      if (e.key === "ArrowUp" && (inputValue === "" || historyIndex !== -1)) {
        e.preventDefault();
        const nextIndex = historyIndex + 1;
        if (nextIndex < userMessages.length) {
          const msg =
            userMessages[userMessages.length - 1 - nextIndex].content;
          setHistoryIndex(nextIndex);
          setInputValue(msg);
          if (inputRef.current) {
            inputRef.current.innerText = msg;
            const range = document.createRange();
            const sel = window.getSelection();
            range.selectNodeContents(inputRef.current);
            range.collapse(false);
            sel?.removeAllRanges();
            sel?.addRange(range);
          }
        }
        return;
      }

      if (e.key === "ArrowDown" && historyIndex !== -1) {
        e.preventDefault();
        const nextIndex = historyIndex - 1;
        if (nextIndex >= 0) {
          const msg =
            userMessages[userMessages.length - 1 - nextIndex].content;
          setHistoryIndex(nextIndex);
          setInputValue(msg);
          if (inputRef.current) inputRef.current.innerText = msg;
        } else {
          setHistoryIndex(-1);
          setInputValue("");
          if (inputRef.current) inputRef.current.innerText = "";
        }
        if (inputRef.current) {
          const range = document.createRange();
          const sel = window.getSelection();
          range.selectNodeContents(inputRef.current);
          range.collapse(false);
          sel?.removeAllRanges();
          sel?.addRange(range);
        }
        return;
      }
    }

    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e as unknown as React.FormEvent);
    }
    if (e.key === "Escape") setShowSuggestions(false);
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    e.preventDefault();
    const text = e.clipboardData.getData("text/plain");
    const selection = window.getSelection();
    if (!selection?.rangeCount) return;
    selection.deleteFromDocument();
    selection.getRangeAt(0).insertNode(document.createTextNode(text));
    selection.collapseToEnd();
    if (inputRef.current) {
      handleInput({
        currentTarget: inputRef.current,
      } as unknown as React.FormEvent<HTMLDivElement>);
    }
  };

  const handleSubmit = async (e: React.FormEvent, overrideMsg?: string) => {
    e.preventDefault();
    const userMsg = overrideMsg || inputValue.trim();
    if (!userMsg || !chatId) return;

    const optimisticMsg: Message = {
      id: `temp-${Date.now()}`,
      role: "user",
      content: userMsg,
    };
    setMessages((prev) => [...prev, optimisticMsg]);
    setInputValue("");
    setHistoryIndex(-1);
    if (inputRef.current) inputRef.current.innerText = "";
    setIsSending(true);

    try {
      const response = await sendMessage(chatId, userMsg);
      setMessages((prev) => [
        ...prev.filter((m) => m.id !== optimisticMsg.id),
        optimisticMsg,
        response,
      ]);
    } catch (error) {
      console.error("Failed to send message", error);
    } finally {
      setIsSending(false);
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

  const openSourcePreview = async (source: Source) => {
    const imageUrl = getSourceImageUrl(source);
    if (imageUrl) {
      setPreviewFile({
        kind: "image",
        title: source.fileName,
        mimeType: source.fileName.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg",
        content: imageUrl,
        path: source.path,
      });
      return;
    }

    if (!source.path) return;

    try {
      const preview = await getFilePreview(source.path);
      setPreviewFile(preview);
    } catch (error) {
      console.error("Failed to open source preview", error);
      alert("Nie udało się pobrać podglądu pliku.");
    }
  };

  const renderMention = (part: string, key: number) => {
    if (!part.startsWith("@")) {
      return (
        <span key={key}>
          {part.split(/(\*\*.*?\*\*)/g).map((subPart, subIdx) => {
            if (subPart.startsWith("**") && subPart.endsWith("**")) {
              return (
                <strong key={subIdx} className="font-semibold text-ink">
                  {subPart.slice(2, -2)}
                </strong>
              );
            }
            return subPart;
          })}
        </span>
      );
    }

    let mentionName = part.slice(1);
    let trailingPunctuation = "";
    const match = mentionName.match(/[:,\.\?\!]+$/);
    if (match) {
      trailingPunctuation = match[0];
      mentionName = mentionName.slice(0, -trailingPunctuation.length);
    }

    const folderMatch = allFolders.find((f) => f.name === mentionName);
    const fileMatch = allFiles.find((f) => f.name === mentionName);

    if (folderMatch || fileMatch) {
      return (
        <span key={key}>
          <button
            type="button"
            onClick={() => {
              if (folderMatch) {
                router.push(`/folders/${folderMatch.id}`);
              } else if (fileMatch) {
                const folderName = fileMatch.id.split("/")[2];
                const folder = allFolders.find((f) => f.name === folderName);
                if (folder) router.push(`/folders/${folder.id}`);
              }
            }}
            className="chip chip-accent mx-0.5 cursor-pointer px-1.5 py-0.5 font-medium"
          >
            @{mentionName}
          </button>
          {trailingPunctuation}
        </span>
      );
    }

    return <span key={key}>{part}</span>;
  };

  return (
    <div className="relative flex h-full flex-col bg-surface">
      <header className="shrink-0 border-b border-border bg-surface-raised px-6 py-4">
        <h1 className="text-2xl font-semibold tracking-[-0.02em] text-ink">Czat</h1>
      </header>

      <div className="flex-1 overflow-y-auto px-4 py-4 md:px-6">
        {isInitialLoading && (
          <div className="mx-auto max-w-3xl space-y-3">
            <div className="skeleton h-16 w-2/3" />
            <div className="skeleton ml-auto h-12 w-1/2" />
            <div className="skeleton h-20 w-3/4" />
          </div>
        )}

        {!isInitialLoading && messages.length === 0 && chatId && (
          <div className="mx-auto flex h-full max-w-xl items-center justify-center py-12 text-sm text-ink-muted">
            <p>
              <kbd className="kbd">@folder</kbd>{" "}
              <kbd className="kbd">@plik</kbd>
            </p>
          </div>
        )}

        {!isInitialLoading && (
          <div className="mx-auto max-w-4xl space-y-5">
            {messages.map((msg) => (
              <ChatMessageBubble
                key={msg.id}
                message={msg}
                sources={msg.sources}
                onSourceClick={openSourcePreview}
              >
                {msg.content
                  .split(/(@[\w\-\.\/\u00C0-\u017F]+)/g)
                  .map((part, i) => renderMention(part, i))}
              </ChatMessageBubble>
            ))}

            {isSending && (
              <ChatMessageBubble
                message={{ id: "pending", role: "assistant", content: "" }}
              >
                <span className="inline-flex items-center gap-2 text-xs text-ink-muted">
                  <Loader2 size={16} className="animate-spin text-accent" />
                </span>
              </ChatMessageBubble>
            )}
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <div className="relative shrink-0 border-t border-border bg-surface-raised px-4 py-3 md:px-6">
        {showSuggestions && filteredSuggestions.length > 0 && (
          <div className="absolute bottom-full left-4 right-4 z-20 mb-2 max-h-56 overflow-y-auto rounded-[10px] border border-border bg-surface-raised shadow-md md:left-6 md:right-6">
            <div className="flex items-center gap-2 border-b border-border bg-sidebar px-3 py-2 text-xs text-ink-muted">
              <AtSign size={14} />
            </div>
            {filteredSuggestions.map((suggestion, index) => (
              <button
                key={`${suggestion.type}-${suggestion.id}`}
                type="button"
                onClick={() => selectSuggestion(suggestion)}
                onMouseEnter={() => setSelectedIndex(index)}
                className={`flex w-full items-center gap-3 px-3 py-2.5 text-left text-sm transition-colors ${
                  index === selectedIndex
                    ? "bg-accent-subtle text-ink"
                    : "hover:bg-accent-subtle/40"
                }`}
              >
                <span className="flex h-7 w-7 items-center justify-center rounded-[6px] bg-accent-muted text-accent">
                  {suggestion.type === "folder" ? (
                    <FolderOpen size={15} />
                  ) : suggestion.url ? (
                    <span className="h-4 w-4 overflow-hidden rounded-sm">
                      <img
                        src={suggestion.url}
                        alt=""
                        className="h-full w-full object-cover"
                      />
                    </span>
                  ) : (
                    <FileText size={15} />
                  )}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{suggestion.name}</span>
                </span>
              </button>
            ))}
          </div>
        )}

        <form onSubmit={handleSubmit} className="relative mx-auto flex max-w-4xl items-end gap-2">
          <div className="relative min-h-[52px] flex-1 rounded-[14px] border border-border bg-sidebar/80 focus-within:border-accent focus-within:ring-2 focus-within:ring-accent/20">
            {!inputValue && (
              <div className="pointer-events-none absolute left-4 top-3.5 z-20 text-sm text-ink-muted">
                {chatId ? "Wiadomość…" : "Wybierz rozmowę"}
              </div>
            )}

            <div className="pointer-events-none absolute inset-0 z-10 whitespace-pre-wrap break-words px-4 py-3.5 text-sm">
              {inputValue
                .split(/(@[\w\-\.\/\u00C0-\u017F]+)/g)
                .map((part, i) => {
                  const isMention = part.startsWith("@");
                  const mentionName = isMention ? part.slice(1) : "";
                  const exists =
                    isMention &&
                    (allFolders.some((f) => f.name === mentionName) ||
                      allFiles.some((f) => f.name === mentionName));
                  if (exists) {
                    return (
                      <span key={i} className="chip-accent rounded px-1">
                        {part}
                      </span>
                    );
                  }
                  return (
                    <span key={i} className="text-ink">
                      {part}
                    </span>
                  );
                })}
              {inputValue.endsWith(" ") && <span>&nbsp;</span>}
            </div>

            <div
              ref={inputRef}
              contentEditable={!isSending && !!chatId}
              onInput={handleInput}
              onKeyDown={handleKeyDown}
              onPaste={handlePaste}
              className="relative z-30 block min-h-[52px] w-full bg-transparent px-4 py-3.5 text-sm text-transparent caret-ink outline-none"
              spellCheck={false}
              role="textbox"
              aria-multiline="false"
              aria-label="Wiadomość do asystenta"
            />
          </div>

          <button
            type="submit"
            disabled={!inputValue.trim() || isSending || !chatId}
            className="btn-primary shrink-0 px-3 py-3 disabled:opacity-45"
            aria-label="Wyślij wiadomość"
          >
            <SendHorizonal size={18} />
          </button>
        </form>
      </div>

      {previewFile && (
        <ImagePreview preview={previewFile} onClose={() => setPreviewFile(null)} />
      )}
    </div>
  );
}
