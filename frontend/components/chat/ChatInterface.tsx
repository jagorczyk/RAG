"use client";

import { useState, useEffect, useRef } from "react";
import { Send, User, Bot, Loader2, FileText, Image as ImageIcon, ExternalLink, X, FolderOpen, AtSign, SendHorizonal } from "lucide-react";
import { getMessagesForChat, sendMessage, Message, Source, getFolders, getAllFiles, Folder, FileItem, getFilesInFolder } from "@/lib/api";
import { useRouter } from "next/navigation";
import { ImagePreview } from "@/components/ui/ImagePreview";

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
  const [previewImage, setPreviewImage] = useState<string | null>(null);
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
      getMessagesForChat(chatId).then(msgs => {
        setMessages(msgs);
        setIsInitialLoading(false);
      }).catch(err => {
        console.error("Failed to fetch messages", err);
        setIsInitialLoading(false);
      });
    } else {
      setMessages([]);
    }
  }, [chatId]);

  
  useEffect(() => {
    Promise.all([getFolders(), getAllFiles()]).then(([folders, files]) => {
      setAllFolders(folders);
      setAllFiles(files);
    }).catch(err => console.error("Failed to fetch suggestions data", err));
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

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
    
    
    const matchedFolders = allFolders.filter(f => f.name.toLowerCase().includes(filter));
    
    const folderSuggestions = matchedFolders.map(f => ({ 
      id: f.id, 
      name: f.name, 
      type: "folder" as const 
    }));
    
    const fileSuggestions = allFiles
      .filter(f => {
        const fileName = f.name.toLowerCase();
        
        if (fileName.includes(filter)) return true;
        
        
        
        const pathParts = f.id.split("/");
        if (pathParts.length >= 3) {
          const folderName = pathParts[2].toLowerCase();
          if (folderName.includes(filter)) return true;
        }
        
        return false;
      })
      .map(f => ({ 
        id: f.id, 
        name: f.name, 
        type: "file" as const, 
        url: f.url 
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
        setSelectedIndex(prev => (prev + 1) % filteredSuggestions.length);
        return;
      }
      if (e.key === "ArrowUp") {
        e.preventDefault();
        setSelectedIndex(prev => (prev - 1 + filteredSuggestions.length) % filteredSuggestions.length);
        return;
      }
      if (e.key === "Enter" || e.key === "Tab") {
        e.preventDefault();
        selectSuggestion(filteredSuggestions[selectedIndex]);
        return;
      }
    }

    
    if (!showSuggestions) {
      const userMessages = messages.filter(m => m.role === "user");
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
      handleInput({ currentTarget: inputRef.current } as unknown as React.FormEvent<HTMLDivElement>);
    }
  };

  const handleSubmit = async (e: React.FormEvent, overrideMsg?: string) => {
    e.preventDefault();
    const userMsg = overrideMsg || inputValue.trim();
    if (!userMsg || !chatId) return;
    
    
    const optimisticMsg: Message = { id: `temp-${Date.now()}`, role: "user", content: userMsg };
    setMessages(prev => [...prev, optimisticMsg]);
    setInputValue("");
    setHistoryIndex(-1); 
    if (inputRef.current) inputRef.current.innerText = "";
    setIsSending(true);

    try {
      const response = await sendMessage(chatId, userMsg);
      setMessages(prev => [...prev.filter(m => m.id !== optimisticMsg.id), optimisticMsg, response]);
    } catch (error) {
      console.error("Failed to send message", error);
    } finally {
      setIsSending(false);
    }
  };

  const renderSourceIcon = (type: Source["type"]) => {
    switch (type) {
      case "IMAGE": return <ImageIcon size={14} />;
      case "PDF":
      case "TEXT": return <FileText size={14} />;
      default: return <ExternalLink size={14} />;
    }
  };

  const getSourceImageUrl = (source: Source) => {
    if (source.type === "IMAGE" && source.base64) {
      const mime = source.fileName.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
      return `data:${mime};base64,${source.base64}`;
    }
    return null;
  };

  return (
    <div className="flex flex-col h-full bg-white relative">
      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {isInitialLoading && (
          <div className="h-full flex flex-col items-center justify-center text-gray-400">
            <Loader2 size={32} className="animate-spin text-slate-300 mb-2" />
            <p className="text-sm">Ładowanie rozmowy...</p>
          </div>
        )}

        {!isInitialLoading && messages.length === 0 && (
          <div className="h-full flex flex-col items-center justify-center text-gray-400">
            <Bot size={48} className="mb-4 text-slate-300" />
            <h2 className="text-xl font-semibold text-slate-600">Jak mogę Ci dzisiaj pomóc?</h2>
            <p className="text-sm mt-2 text-center max-w-md">
              Zadaj pytanie dotyczące Twoich dokumentów lub wpisz komendę, aby rozpocząć pracę z asystentem RAG.
              Użyj <span className="bg-gray-100 px-1 rounded text-slate-600">@</span> aby wskazać folder lub plik.
            </p>
          </div>
        )}

        {!isInitialLoading && messages.map((msg) => (
          <div key={msg.id} className={`flex flex-col ${msg.role === "user" ? "items-end" : "items-start"}`}>
            <div className={`flex gap-4 max-w-[85%] flex-row`}>
              {msg.role === "assistant" && (
                <div className="w-8 h-8 rounded-full bg-slate-900 flex items-center justify-center shrink-0 mt-1">
                  <Bot size={18} className="text-white" />
                </div>
              )}
              
              <div className={`flex flex-col gap-2 ${msg.role === "user" ? "items-end" : "items-start"}`}>
                <div className={`rounded-2xl p-4 ${
                  msg.role === "user" 
                    ? "bg-gray-100 text-gray-900 rounded-tr-sm" 
                    : "bg-white border border-gray-200 text-gray-800 rounded-tl-sm shadow-sm"
                }`}>
                  <div className="whitespace-pre-wrap leading-relaxed text-sm md:text-base">
                    {msg.content.split(/(@[\w\-\.\/\u00C0-\u017F]+)/g).map((part, i) => {
                      if (part.startsWith("@")) {
                        
                        let mentionName = part.slice(1);
                        let trailingPunctuation = "";
                        
                        const match = mentionName.match(/[:,\.\?\!]+$/);
                        if (match) {
                          trailingPunctuation = match[0];
                          mentionName = mentionName.slice(0, -trailingPunctuation.length);
                        }

                        const folderMatch = allFolders.find(f => f.name === mentionName);
                        const fileMatch = allFiles.find(f => f.name === mentionName);
                        
                        if (folderMatch || fileMatch) {
                          return (
                            <span key={i}>
                              <button
                                onClick={() => {
                                  if (folderMatch) {
                                    router.push(`/folders/${folderMatch.id}`);
                                  } else if (fileMatch) {
                                    const folderName = fileMatch.id.split("/")[2];
                                    const folder = allFolders.find(f => f.name === folderName);
                                    if (folder) router.push(`/folders/${folder.id}`);
                                  }
                                }}
                                className="font-semibold text-blue-800 bg-blue-50 border border-blue-100 px-1.5 py-0.5 rounded-md mx-0.5 inline-block hover:bg-blue-100 hover:border-blue-200 transition-colors cursor-pointer"
                              >
                                @{mentionName}
                              </button>
                              {trailingPunctuation}
                            </span>
                          );
                        }
                      }
                      
                      return (
                        <span key={i}>
                          {part.split(/(\*\*.*?\*\*)/g).map((subPart, subIdx) => {
                            if (subPart.startsWith("**") && subPart.endsWith("**")) {
                              return <strong key={subIdx} className="font-bold text-slate-900">{subPart.slice(2, -2)}</strong>;
                            }
                            return subPart;
                          })}
                        </span>
                      );
                    })}
                  </div>
                </div>

                {msg.sources && msg.sources.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-1">
                    {msg.sources.map((source, sIdx) => {
                      const imageUrl = getSourceImageUrl(source);
                      return (
                        <div 
                          key={sIdx}
                          onClick={() => imageUrl && setPreviewImage(imageUrl)}
                          className={`flex items-center gap-1.5 px-2 py-1 bg-gray-50 border border-gray-200 rounded text-[10px] text-gray-500 font-medium hover:bg-gray-100 transition-colors ${imageUrl ? "cursor-zoom-in" : "cursor-help"}`}
                          title={`${source.fileName} (score: ${source.score.toFixed(4)})`}
                        >
                          {imageUrl ? (
                            <div className="w-4 h-4 rounded-sm overflow-hidden bg-gray-200">
                              <img src={imageUrl} alt="" className="w-full h-full object-cover" />
                            </div>
                          ) : renderSourceIcon(source.type)}
                          <span className="max-w-[120px] truncate">{source.fileName}</span>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {msg.role === "user" && (
                <div className="w-8 h-8 rounded-full bg-gray-300 flex items-center justify-center shrink-0 mt-1">
                  <User size={18} className="text-gray-600" />
                </div>
              )}
            </div>
          </div>
        ))}
        {isSending && (
          <div className="flex gap-4 justify-start">
            <div className="w-8 h-8 rounded-full bg-slate-900 flex items-center justify-center shrink-0">
              <Loader2 size={18} className="text-white animate-spin" />
            </div>
            <div className="bg-white border border-gray-200 rounded-2xl rounded-tl-sm p-4 shadow-sm flex items-center">
              <Loader2 size={20} className="animate-spin text-slate-900" />
              <span className="ml-2 text-sm text-gray-500">Asystent pisze...</span>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <div className="p-4 border-t border-gray-100 bg-white relative">
        {showSuggestions && filteredSuggestions.length > 0 && (
          <div className="absolute bottom-full left-4 right-4 mb-2 bg-white border border-gray-200 rounded-xl shadow-xl overflow-hidden z-20 max-h-60 overflow-y-auto">
            <div className="px-4 py-2 bg-gray-50 border-b border-gray-100 flex items-center gap-2 text-xs font-semibold text-gray-500 uppercase tracking-wider">
              <AtSign size={14} />
              <span>Wybierz folder lub plik</span>
            </div>
            {filteredSuggestions.map((suggestion, index) => (
              <button
                key={`${suggestion.type}-${suggestion.id}`}
                onClick={() => selectSuggestion(suggestion)}
                onMouseEnter={() => setSelectedIndex(index)}
                className={`w-full flex items-center gap-3 px-4 py-3 text-left transition-colors group ${index === selectedIndex ? "bg-slate-100" : "hover:bg-slate-50"}`}
              >
                <div className={`p-1.5 rounded ${suggestion.type === "folder" ? "bg-slate-100 text-slate-600" : "bg-blue-50 text-blue-600"} ${index === selectedIndex ? "bg-white" : ""} overflow-hidden`}>
                  {suggestion.type === "folder" ? (
                    <FolderOpen size={16} />
                  ) : suggestion.url ? (
                    <div className="w-4 h-4 rounded-sm overflow-hidden bg-gray-200">
                      <img src={suggestion.url} alt="" className="w-full h-full object-cover" />
                    </div>
                  ) : (
                    <FileText size={16} />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium text-gray-900 truncate">{suggestion.name}</div>
                  <div className="text-[10px] text-gray-500 uppercase tracking-tight">
                    {suggestion.type === "folder" ? "Folder" : suggestion.url ? "Obraz" : "Plik"}
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}

        <form 
          onSubmit={handleSubmit} 
          className="max-w-4xl mx-auto relative flex items-center"
        >
          <div className="relative w-full bg-gray-50 border border-gray-300 rounded-full shadow-sm overflow-hidden focus-within:ring-2 focus-within:ring-slate-900 focus-within:border-transparent transition-all">
            {!inputValue && (
              <div className="absolute left-6 top-4 text-gray-400 pointer-events-none z-20">
                {chatId ? "Napisz wiadomość do asystenta..." : "Wybierz chat, aby rozpocząć rozmowę"}
              </div>
            )}
            
            <div className="absolute inset-0 pl-6 pr-14 py-4 pointer-events-none whitespace-pre-wrap break-words font-sans text-base z-10">
              {inputValue.split(/(@[\w\-\.\/\u00C0-\u017F]+)/g).map((part, i) => {
                const isMention = part.startsWith("@");
                const mentionName = isMention ? part.slice(1) : "";
                const exists = isMention && (
                  allFolders.some(f => f.name === mentionName) || 
                  allFiles.some(f => f.name === mentionName)
                );

                if (exists) {
                  return (
                    <span 
                      key={i} 
                      className="text-blue-800 bg-blue-50 rounded-sm"
                      style={{ 
                        boxShadow: '0 0 0 1px #dbeafe', 
                        margin: '0 -1px' 
                      }}
                    >{part}</span>
                  );
                }
                return <span key={i} className="text-gray-900">{part}</span>;
              })}
              {inputValue.endsWith(" ") && <span>&nbsp;</span>}
            </div>

            <div
              ref={inputRef}
              contentEditable={!isSending && !!chatId}
              onInput={handleInput}
              onKeyDown={handleKeyDown}
              onPaste={handlePaste}
              className="w-full pl-6 pr-14 py-4 bg-transparent text-transparent relative z-30 font-sans text-base caret-gray-900 outline-none min-h-[54px] block"
              spellCheck={false}
            />
          </div>

          <button
            type="submit"
            disabled={!inputValue.trim() || isSending || !chatId}
            className="absolute right-2 p-2 bg-slate-900 text-white rounded-full hover:bg-slate-800 disabled:opacity-50 disabled:cursor-not-allowed transition-colors z-40"
          >
            <SendHorizonal size={20} />
          </button>
        </form>
      </div>

      {previewImage && (
        <ImagePreview url={previewImage} onClose={() => setPreviewImage(null)} />
      )}
    </div>
  );
}
