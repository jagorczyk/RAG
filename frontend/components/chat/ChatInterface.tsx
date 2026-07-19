"use client";

import { useState, useEffect, useRef } from "react";
import {
  FileText,
  FolderOpen,
  AtSign,
  ArrowUp,
  ChevronLeft,
  Image as ImageIcon,
  File,
  X,
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
import { ChatMessageBubble, TypingIndicator } from "@/components/chat/ChatMessageBubble";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Loading } from "@/components/ui/Loading";

interface ChatInterfaceProps {
  chatId?: string;
}

type Suggestion = {
  id: string;
  name: string;
  type: "folder" | "file";
  url?: string;
};

function sourceIcon(type: Source["type"]) {
  if (type === "IMAGE") return <ImageIcon size={18} />;
  if (type === "PDF") return <FileText size={18} />;
  return <File size={18} />;
}

export function ChatInterface({ chatId }: ChatInterfaceProps) {
  const router = useRouter();
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState("");
  const [isInitialLoading, setIsInitialLoading] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [failedMessage, setFailedMessage] = useState<string | null>(null);
  const [previewFile, setPreviewFile] = useState<FilePreview | null>(null);
  const [sheetSources, setSheetSources] = useState<Source[] | null>(null);
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
          const msg = userMessages[userMessages.length - 1 - nextIndex].content;
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
          const msg = userMessages[userMessages.length - 1 - nextIndex].content;
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
    setSendError(null);
    setFailedMessage(null);

    try {
      const response = await sendMessage(chatId, userMsg);
      setMessages((prev) => [
        ...prev.filter((m) => m.id !== optimisticMsg.id),
        optimisticMsg,
        response,
      ]);
    } catch (error) {
      console.error("Failed to send message", error);
      setMessages((prev) => prev.filter((message) => message.id !== optimisticMsg.id));
      setFailedMessage(userMsg);
      setSendError(
        error instanceof Error && error.message
          ? error.message
          : "Nie udało się wysłać wiadomości. Sprawdź połączenie z serwerem."
      );
      setInputValue(userMsg);
      if (inputRef.current) inputRef.current.innerText = userMsg;
    } finally {
      setIsSending(false);
    }
  };

  const handleRetrySend = (e: React.MouseEvent | React.FormEvent) => {
    e.preventDefault();
    if (failedMessage) {
      void handleSubmit(e as React.FormEvent, failedMessage);
    }
  };

  const examplePrompts = [
    "Kto jest na moich ostatnich zdjęciach?",
    "Pokaż zdjęcia, na których widać więcej niż jedną osobę.",
    "Co wiesz o dokumentach w mojej bibliotece?",
  ];

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
    setSheetSources(null);
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
                <strong key={subIdx} className="font-semibold">
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
            className="mx-0.5 inline rounded-md bg-soft px-1.5 py-0.5 font-semibold"
          >
            @{mentionName}
          </button>
          {trailingPunctuation}
        </span>
      );
    }

    return <span key={key}>{part}</span>;
  };

  const canSend = !!inputValue.trim() && !isSending && !!chatId;

  return (
    <div className="relative flex h-full flex-col bg-surface">
      <header className="flex min-h-[3.6rem] shrink-0 items-center border-b border-border px-4 md:px-5">
        <button
          type="button"
          onClick={() => router.push("/chats")}
          className="icon-button -ml-1 shadow-none md:hidden"
          aria-label="Wróć do rozmów"
        >
          <ChevronLeft size={26} />
        </button>
        <div className="min-w-0 flex-1 text-center md:pl-1 md:text-left">
          <h1 className="truncate text-[1.0625rem] font-extrabold tracking-tight text-ink md:text-xl">
            Rozmowa
          </h1>
          <p className="text-xs text-ink-muted" aria-live="polite">
            {isSending ? "Analizuję dokumenty…" : "Twoja baza wiedzy"}
          </p>
        </div>
        <div className="w-9 md:hidden" aria-hidden />
      </header>

      <div className="flex-1 overflow-y-auto px-4 py-5 md:px-6">
        {isInitialLoading && <Loading label="Ładowanie wiadomości" />}

        {!isInitialLoading && messages.length === 0 && chatId && (
          <div className="mx-auto flex h-full max-w-lg flex-col items-center justify-center px-4 pb-12 text-center">
            <div className="mb-4 flex h-[58px] w-[58px] items-center justify-center rounded-[18px] bg-soft">
              <AtSign size={26} className="text-ink" aria-hidden />
            </div>
            <h2 className="text-[1.375rem] font-extrabold tracking-tight text-ink md:text-[1.5rem]">
              Zadaj pierwsze pytanie
            </h2>
            <p className="mt-2 max-w-sm text-sm leading-5 text-ink-muted">
              Pytaj o osoby na zdjęciach, relacje i dokumenty z biblioteki. Użyj{" "}
              <kbd className="kbd">@</kbd>, aby wskazać folder lub plik.
            </p>
            <ul className="mt-6 flex w-full list-none flex-col gap-2 p-0">
              {examplePrompts.map((prompt) => (
                <li key={prompt}>
                  <button
                    type="button"
                    className="prompt-chip w-full"
                    disabled={isSending}
                    onClick={(e) => handleSubmit(e, prompt)}
                  >
                    {prompt}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        {!isInitialLoading && (
          <div className="mx-auto max-w-3xl">
            {sendError && (
              <div
                role="alert"
                className="status-banner status-banner-error mb-4 !mt-0"
              >
                <span className="flex-1">{sendError}</span>
                <button
                  type="button"
                  className="shrink-0 font-extrabold text-ink"
                  onClick={handleRetrySend}
                  disabled={isSending || !failedMessage}
                >
                  Spróbuj ponownie
                </button>
              </div>
            )}
            {messages.map((msg, index) => (
              <ChatMessageBubble
                key={msg.id}
                message={msg}
                sources={msg.sources}
                uncertain={msg.uncertain}
                onSourcesOpen={setSheetSources}
                index={index}
              >
                {msg.content
                  .split(/(@[\w\-\.\/\u00C0-\u017F]+)/g)
                  .map((part, i) => renderMention(part, i))}
              </ChatMessageBubble>
            ))}

            {isSending && <TypingIndicator />}
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <div className="relative shrink-0 border-t border-border bg-surface px-3 pb-3 pt-2.5 md:px-5">
        {showSuggestions && filteredSuggestions.length > 0 && (
          <div className="absolute bottom-full left-3 right-3 z-20 mb-2 max-h-56 overflow-y-auto rounded-2xl border border-border bg-surface-raised shadow-float md:left-5 md:right-5">
            <div className="flex items-center gap-2 border-b border-border bg-soft px-3 py-2 text-xs font-semibold text-ink-muted">
              <AtSign size={14} />
              Wybierz folder lub plik
            </div>
            {filteredSuggestions.map((suggestion, index) => (
              <button
                key={`${suggestion.type}-${suggestion.id}`}
                type="button"
                onClick={() => selectSuggestion(suggestion)}
                onMouseEnter={() => setSelectedIndex(index)}
                className={`flex w-full items-center gap-3 px-3 py-3 text-left text-sm transition-colors ${
                  index === selectedIndex ? "bg-soft" : "hover:bg-soft/70"
                }`}
              >
                <span className="flex h-9 w-9 items-center justify-center rounded-[11px] bg-soft text-ink">
                  {suggestion.type === "folder" ? (
                    <FolderOpen size={17} />
                  ) : suggestion.url ? (
                    <span className="h-7 w-7 overflow-hidden rounded-md">
                      <img src={suggestion.url} alt="" className="h-full w-full object-cover" />
                    </span>
                  ) : (
                    <FileText size={17} />
                  )}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-bold">{suggestion.name}</span>
                  <span className="text-xs text-ink-muted">
                    {suggestion.type === "folder" ? "Folder" : "Plik"}
                  </span>
                </span>
              </button>
            ))}
          </div>
        )}

        <form
          onSubmit={handleSubmit}
          className="mx-auto flex max-w-3xl items-end gap-0 rounded-3xl border border-border bg-surface-raised py-1.5 pl-4 pr-1.5"
        >
          <div className="relative min-h-[2.4rem] flex-1">
            {!inputValue && (
              <div className="pointer-events-none absolute left-0 top-2 z-20 text-[15px] text-ink-muted">
                {chatId ? "Napisz wiadomość…" : "Wybierz rozmowę"}
              </div>
            )}
            <div
              ref={inputRef}
              contentEditable={!isSending && !!chatId}
              onInput={handleInput}
              onKeyDown={handleKeyDown}
              onPaste={handlePaste}
              className="relative z-20 block max-h-28 min-h-[2.4rem] w-full overflow-y-auto bg-transparent py-2 pr-2 text-[15px] text-ink caret-ink outline-none"
              spellCheck={false}
              role="textbox"
              aria-multiline="true"
              aria-label="Wiadomość do asystenta"
            />
          </div>
          <button
            type="submit"
            disabled={!canSend}
            className={`mb-0.5 flex h-[38px] w-[38px] shrink-0 items-center justify-center rounded-full transition-colors ${
              canSend ? "bg-ink text-white" : "bg-border text-white"
            }`}
            aria-label="Wyślij wiadomość"
          >
            <ArrowUp size={20} strokeWidth={2.4} />
          </button>
        </form>
      </div>

      <BottomSheet
        open={!!sheetSources}
        onClose={() => setSheetSources(null)}
        title={sheetSources ? `Źródła (${sheetSources.length})` : "Źródła"}
      >
        <div className="-mx-1">
          {(sheetSources || []).map((item, index) => (
            <button
              key={`${item.path}-${index}`}
              type="button"
              onClick={() => openSourcePreview(item)}
              className="flex min-h-[4.1rem] w-full items-center gap-3 border-b border-border px-1 text-left last:border-b-0 transition-opacity active:opacity-55"
            >
              <span className="flex h-9.5 w-9.5 items-center justify-center rounded-[11px] bg-soft text-ink">
                {sourceIcon(item.type)}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate font-bold text-ink">{item.fileName}</span>
                <span className="mt-0.5 block text-[13px] text-ink-muted">Otwórz podgląd</span>
              </span>
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={() => setSheetSources(null)}
          className="btn-ghost mt-2 w-full"
        >
          <X size={16} /> Zamknij
        </button>
      </BottomSheet>

      {previewFile && (
        <ImagePreview preview={previewFile} onClose={() => setPreviewFile(null)} />
      )}
    </div>
  );
}
