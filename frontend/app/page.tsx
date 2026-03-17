"use client"

import React, { useState, useEffect, useRef } from 'react';

interface VirtualFolder {
  id: string;
  name: string;
}

interface FileDto {
  path: string;
  name: string;
  imageBase64: string;
}

interface Source {
  path: string;
  fileName: string;
  score: number;
  base64?: string;
  type: 'IMAGE' | 'PDF' | 'TEXT' | 'OTHER';
}

interface Message {
  text: string;
  type: string;
  sources?: Source[];
}

export default function RagApp() {
  const [view, setView] = useState<'chat' | 'files'>('chat');
  const [currentFolder, setCurrentFolder] = useState<string | null>(null);
  const [chats, setChats] = useState<string[]>([]);
  const [selectedChat, setSelectedChat] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const [dbFiles, setDbFiles] = useState<FileDto[]>([]);
  const [virtualFolders, setVirtualFolders] = useState<VirtualFolder[]>([]);
  const [newFolderName, setNewFolderName] = useState('');
  const [uploadProgress, setUploadProgress] = useState<Record<string, number>>({});
  const [selectedImage, setSelectedImage] = useState<string | null>(null);
  const [isFolderMenuOpen, setIsFolderMenuOpen] = useState(false);

  const scrollRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsFolderMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    if (selectedChat) {
      fetch(`http://localhost:8080/api/chat/${selectedChat}/messages`)
        .then(r => r.json())
        .then(data => setMessages(data))
        .catch(err => console.error("Error fetching messages:", err));
    } else {
      setMessages([]);
    }
  }, [selectedChat]);

  const fetchData = async () => {
    try {
      const [cRes, fRes, vRes] = await Promise.all([
        fetch('http://localhost:8080/api/chat/all'),
        fetch('http://localhost:8080/api/data/files'),
        fetch('http://localhost:8080/api/folders')
      ]);
      setChats(await cRes.json());
      setDbFiles(await fRes.json());
      setVirtualFolders(await vRes.json());
    } catch (err) { 
      console.error("Fetch error:", err); 
    }
  };

  useEffect(() => { 
    fetchData(); 
  }, []);

  const getFilePlural = (n: number) => {
    if (n === 1) return 'plik';
    const lastDigit = n % 10;
    const lastTwo = n % 100;
    if (lastDigit >= 2 && lastDigit <= 4 && (lastTwo < 10 || lastTwo >= 20)) {
      return 'pliki';
    }
    return 'plików';
  };

  const handleCreateFolder = async () => {
    if (!newFolderName.trim()) return;
    try {
      await fetch('http://localhost:8080/api/folders/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newFolderName })
      });
      setNewFolderName('');
      fetchData();
    } catch (err) {
      console.error("Folder creation error:", err);
    }
  };

  const handleDeleteFolder = async (id: string, e?: React.MouseEvent) => {
    e?.stopPropagation();
    if (!confirm('Czy na pewno chcesz usunąć ten folder?')) return;
    try {
      await fetch(`http://localhost:8080/api/folders/${id}`, {
        method: 'DELETE'
      });
      setIsFolderMenuOpen(false);
      setCurrentFolder(null);
      fetchData();
    } catch (err) {
      console.error("Folder deletion error:", err);
    }
  };

  const handleFileUpload = (folderId: string, e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files?.[0]) return;
    const file = e.target.files[0];
    const formData = new FormData();
    formData.append('file', file);

    const xhr = new XMLHttpRequest();

    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        const percent = Math.round((event.loaded / event.total) * 100);
        setUploadProgress(prev => ({ ...prev, [folderId]: percent }));
      }
    });

    xhr.addEventListener('load', () => {
      setUploadProgress(prev => {
        const next = { ...prev };
        delete next[folderId];
        return next;
      });
      fetchData();
    });

    xhr.open('POST', `http://localhost:8080/api/folders/${folderId}/upload`);
    xhr.send(formData);
  };

  const getFolderName = (path: string) => {
    if (path.startsWith('dir://')) return path.split('/')[2];
    const parts = path.split(/[\\/]/);
    return parts.length >= 2 ? parts[parts.length - 2] : 'root';
  };

  const allFolderNames = Array.from(new Set([
    ...virtualFolders.map(vf => vf.name),
    ...dbFiles.map(f => getFolderName(f.path))
  ]));

  const handleSend = async () => {
    if (!input.trim() || !selectedChat) return;
    const currentInput = input;
    const userMsg: Message = { text: currentInput, type: "USER" };
    setMessages(prev => [...prev, userMsg]);
    setInput("");
    setIsLoading(true);
    try {
      const res = await fetch(`http://localhost:8080/api/chat/${selectedChat}/send`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: currentInput }),
      });
      const data = await res.json();
      setMessages(prev => [...prev, { text: data.response, type: "AI", sources: data.sources }]);
    } catch (err) {
      console.error("Chat error:", err);
    } finally { 
      setIsLoading(false); 
    }
  };

  const createNewChat = () => {
    fetch('http://localhost:8080/api/chat/create', { method: 'POST' })
      .then(r => r.json())
      .then(id => {
        setChats([id, ...chats]);
        setSelectedChat(id);
        setView('chat');
      })
      .catch(err => console.error("Create chat error:", err));
  };

  const currentVirtualFolder = virtualFolders.find(v => v.name === currentFolder);

  return (
    <div className="flex h-screen bg-[#09090b] text-zinc-100 font-sans overflow-hidden">
      {/* IMAGE MODAL */}
      {selectedImage && (
        <div 
          className="fixed inset-0 z-[100] flex items-center justify-center bg-black/90 p-10 cursor-zoom-out"
          onClick={() => setSelectedImage(null)}
        >
          <img 
            src={`data:image/jpeg;base64,${selectedImage}`} 
            alt="Preview" 
            className="max-w-full max-h-full object-contain shadow-2xl animate-in zoom-in-95 duration-200"
          />
          <button 
            className="absolute top-8 right-8 p-3 bg-white/10 hover:bg-white/20 rounded-full text-white transition-colors"
            onClick={() => setSelectedImage(null)}
          >
            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>
      )}

      <aside className="w-80 bg-[#121214] border-r border-zinc-800/50 flex flex-col">
        <div className="p-5">
          <button 
            onClick={() => { setView('files'); setCurrentFolder(null); }}
            className={`w-full flex items-center gap-4 py-4 px-5 rounded-2xl font-black transition-all active:scale-95 text-lg ${view === 'files' ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-500/20' : 'bg-zinc-800/50 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800'}`}
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"/>
            </svg>
            Pliki
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-4 space-y-2 custom-scrollbar">
          <h3 className="px-4 mt-2 mb-3 text-[11px] font-black text-zinc-600 uppercase">Ostatnie sesje</h3>
          {chats.map(id => (
            <button 
              key={id} 
              onClick={() => { setSelectedChat(id); setView('chat'); }} 
              className={`w-full text-left px-5 py-4 rounded-xl transition-all ${selectedChat === id && view === 'chat' ? 'bg-zinc-800/80 text-white shadow-lg shadow-black/40' : 'text-zinc-500 hover:text-zinc-300 hover:bg-white/5'}`}
            >
              <div className="flex items-center gap-4">
                <div className={`w-2 h-2 rounded-full ${selectedChat === id && view === 'chat' ? 'bg-indigo-500' : 'bg-zinc-700'}`} />
                <span className="text-base font-bold truncate">Sesja {id.substring(0, 8)}</span>
              </div>
            </button>
          ))}
        </nav>

        <div className="p-5 border-t border-zinc-800/50">
          <button 
            onClick={createNewChat}
            className="w-full flex items-center justify-center gap-3 py-4 px-5 bg-zinc-100 text-zinc-900 rounded-2xl font-black text-lg transition-all active:scale-95 shadow-xl shadow-white/5"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4"/>
            </svg>
            Nowy Chat
          </button>
        </div>
      </aside>

      <main className="flex-1 flex flex-col bg-[#09090b]">
        <header className="h-20 border-b border-zinc-800/50 flex items-center justify-between px-10 bg-[#09090b]/80 backdrop-blur-xl z-20">
          <div className="flex items-center gap-6">
            {view === 'files' && currentFolder && (
              <button 
                onClick={() => setCurrentFolder(null)}
                className="p-3 hover:bg-zinc-800 rounded-xl transition-colors text-zinc-400 hover:text-white"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7"/>
                </svg>
              </button>
            )}
            <h2 className="text-base font-black uppercase text-zinc-400">
              {view === 'chat' ? 'AI Assistant' : currentFolder || 'Pliki'}
            </h2>
          </div>

          {view === 'files' && currentFolder && currentVirtualFolder && (
            <div className="relative" ref={menuRef}>
              <button 
                onClick={() => setIsFolderMenuOpen(!isFolderMenuOpen)}
                className="p-3 hover:bg-zinc-800 rounded-2xl transition-colors text-zinc-400 hover:text-white"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z"/>
                </svg>
              </button>

              {isFolderMenuOpen && (
                <div className="absolute right-0 mt-2 w-56 bg-[#18181b] border border-zinc-800 rounded-2xl shadow-2xl py-2 z-50 animate-in fade-in zoom-in-95 duration-100">
                  <button 
                    onClick={() => handleDeleteFolder(currentVirtualFolder.id)}
                    className="w-full text-left px-5 py-4 text-base font-bold text-red-500 hover:bg-red-500/10 transition-colors flex items-center gap-4"
                  >
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                    </svg>
                    Usuń folder
                  </button>
                </div>
              )}
            </div>
          )}
        </header>

        <div className="flex-1 overflow-hidden">
          {view === 'chat' ? (
            <div className="h-full flex flex-col max-w-5xl mx-auto w-full">
              {selectedChat ? (
                <>
                  <div className="flex-1 overflow-y-auto p-10 space-y-10 custom-scrollbar">
                    {messages.map((m, i) => (
                      <div key={i} className={`flex ${m.type === 'USER' ? 'justify-end' : 'justify-start'} animate-in fade-in slide-in-from-bottom-4`}>
                        <div className={`max-w-[80%] flex flex-col gap-3 ${m.type === 'USER' ? 'items-end' : 'items-start'}`}>
                          <div className={`px-6 py-4 rounded-3xl text-lg leading-relaxed shadow-2xl ${m.type === 'USER' ? 'bg-indigo-600 text-white rounded-tr-none' : 'bg-[#18181b] border border-zinc-800 text-zinc-200 rounded-tl-none'}`}>
                            {m.text}
                          </div>
                          <div className="flex flex-wrap gap-4 mt-2">
                            {m.sources?.map((source, idx) => (
                              <div key={idx} className="relative group">
                                {source.type === 'IMAGE' ? (
                                  <img 
                                    src={`data:image/jpeg;base64,${source.base64}`} 
                                    alt={source.fileName}
                                    className="w-48 h-32 object-cover rounded-xl border border-zinc-800 cursor-zoom-in hover:scale-105 transition-transform"
                                    onClick={() => setSelectedImage(source.base64 || null)}
                                  />
                                ) : (
                                  <div className="w-48 h-32 bg-zinc-900 border border-zinc-800 rounded-xl flex flex-col items-center justify-center p-4 gap-2 text-center group-hover:bg-zinc-800/50 transition-colors">
                                    <div className="p-3 bg-indigo-500/10 text-indigo-500 rounded-lg">
                                      {source.type === 'PDF' ? (
                                        <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z"/>
                                        </svg>
                                      ) : (
                                        <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                                        </svg>
                                      )}
                                    </div>
                                    <span className="text-[10px] font-bold text-zinc-400 truncate w-full">{source.fileName}</span>
                                  </div>
                                )}
                                <div className="absolute -top-2 -right-2 bg-indigo-600 px-2 py-0.5 rounded-full border border-zinc-950 text-[10px] font-black text-white shadow-xl z-10">
                                  {Math.round(source.score * 100)}%
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    ))}
                    {isLoading && (
                      <div className="flex justify-start animate-in fade-in slide-in-from-bottom-4">
                        <div className="bg-[#18181b] border border-zinc-800 px-6 py-4 rounded-3xl rounded-tl-none shadow-2xl flex items-center gap-1.5">
                          <div className="w-2 h-2 bg-indigo-500 rounded-full animate-bounce [animation-delay:-0.3s]"></div>
                          <div className="w-2 h-2 bg-indigo-500 rounded-full animate-bounce [animation-delay:-0.15s]"></div>
                          <div className="w-2 h-2 bg-indigo-500 rounded-full animate-bounce"></div>
                        </div>
                      </div>
                    )}
                    <div ref={scrollRef} />
                  </div>
                  <footer className="p-10 pb-12">
                    <div className="relative group bg-[#18181b] p-3 rounded-3xl border-2 border-zinc-800 focus-within:border-indigo-500/50 transition-all shadow-2xl">
                      <input 
                        value={input} 
                        onChange={e => setInput(e.target.value)}
                        onKeyDown={e => e.key === 'Enter' && handleSend()} 
                        placeholder="Zapytaj o treść dokumentów..." 
                        className="w-full bg-transparent border-none px-6 py-4 text-lg outline-none"
                      />
                      <button 
                        onClick={handleSend} 
                        disabled={!input.trim() || isLoading} 
                        className="absolute right-4 top-1/2 -translate-y-1/2 p-3 bg-indigo-600 hover:bg-indigo-500 rounded-2xl transition-all active:scale-95 disabled:opacity-50"
                      >
                        <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 12h14M12 5l7 7-7 7" />
                        </svg>
                      </button>
                    </div>
                  </footer>
                </>
              ) : (
                <div className="h-full flex flex-col items-center justify-center text-zinc-600 gap-6">
                  <div className="p-10 bg-zinc-900/30 rounded-full border-2 border-zinc-800/50">
                    <svg className="w-16 h-16 opacity-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"/>
                    </svg>
                  </div>
                  <p className="text-lg font-black uppercase">Wybierz sesję lub zacznij nową</p>
                </div>
              )}
            </div>
          ) : (
            <div className="h-full p-10 overflow-y-auto custom-scrollbar">
              <div className="max-w-7xl mx-auto space-y-12">
                {!currentFolder ? (
                  <>
                    <div className="flex gap-4 bg-[#121214] p-5 rounded-3xl border-2 border-zinc-800 shadow-2xl max-w-2xl">
                      <input 
                        value={newFolderName} 
                        onChange={e => setNewFolderName(e.target.value)} 
                        placeholder="Nazwa nowego folderu..." 
                        className="flex-1 bg-zinc-950 border border-zinc-800 rounded-2xl px-6 py-3 text-base outline-none focus:border-indigo-500 transition-all"
                      />
                      <button 
                        onClick={handleCreateFolder} 
                        className="bg-zinc-100 text-zinc-900 px-8 py-3 rounded-2xl font-black text-sm uppercase tracking-widest hover:bg-white active:scale-95 transition-all"
                      >
                        Stwórz
                      </button>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-10">
                      {allFolderNames.map(name => {
                        const count = dbFiles.filter(f => getFolderName(f.path) === name).length;
                        return (
                          <div key={name} className="relative group">
                            <button 
                              onClick={() => setCurrentFolder(name)}
                              className="w-full flex flex-col items-center gap-4 p-6 rounded-3xl hover:bg-white/5 transition-all"
                            >
                              <div className="relative">
                                <svg className="w-24 h-24 text-indigo-500 fill-indigo-500/20 group-hover:scale-110 transition-transform duration-300" viewBox="0 0 24 24">
                                  <path d="M10 4H4a2 2 0 00-2 2v12a2 2 0 002 2h16a2 2 0 002-2V8a2 2 0 00-2-2h-8l-2-2z"/>
                                </svg>
                              </div>
                              <div className="text-center space-y-1">
                                <span className="text-base font-black text-zinc-200 group-hover:text-white truncate block w-40">
                                  {name}
                                </span>
                                <span className="text-[11px] text-zinc-600 font-black uppercase">
                                  {count} {getFilePlural(count)}
                                </span>
                              </div>
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  </>
                ) : (
                  <div className="space-y-10">
                    <div className="flex items-center justify-between bg-zinc-900/30 p-8 rounded-3xl border border-zinc-800/50">
                      <div className="flex items-center gap-6">
                        <div className="p-4 bg-indigo-500/10 text-indigo-500 rounded-2xl border border-indigo-500/20">
                          <svg className="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeWidth="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"/>
                          </svg>
                        </div>
                        <div>
                          <h3 className="text-3xl font-black text-white">{currentFolder}</h3>
                          <p className="text-base text-zinc-500 font-bold mt-1">
                            {dbFiles.filter(f => getFolderName(f.path) === currentFolder).length} {getFilePlural(dbFiles.filter(f => getFolderName(f.path) === currentFolder).length)}
                          </p>
                        </div>
                      </div>

                      <div className="flex items-center gap-6">
                        {currentVirtualFolder && (
                          <div className="flex items-center gap-6">
                            {uploadProgress[currentVirtualFolder.id] !== undefined && (
                              <div className="flex flex-col items-end gap-2">
                                <span className="text-[11px] font-black text-indigo-400 animate-pulse uppercase">Wgrywanie...</span>
                                <div className="w-40 h-1.5 bg-zinc-800 rounded-full overflow-hidden">
                                  <div 
                                    className="h-full bg-indigo-500 transition-all duration-300" 
                                    style={{ width: `${uploadProgress[currentVirtualFolder.id]}%` }}
                                  />
                                </div>
                              </div>
                            )}
                            <label className="flex items-center gap-3 px-8 py-4 bg-indigo-600 hover:bg-indigo-500 text-white rounded-2xl font-black text-sm uppercase cursor-pointer transition-all active:scale-95 shadow-2xl shadow-indigo-600/30">
                              <input 
                                type="file" 
                                className="hidden" 
                                onChange={(e) => handleFileUpload(currentVirtualFolder.id, e)} 
                              />
                              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeWidth="3" d="M12 4v16m8-8H4"/>
                              </svg>
                              Dodaj Plik
                            </label>
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-8">
                      {dbFiles.filter(f => getFolderName(f.path) === currentFolder).map((file, i) => (
                        <div key={i} className="group flex flex-col gap-4">
                          <div 
                            className="aspect-square bg-zinc-900 rounded-3xl overflow-hidden border-2 border-zinc-800 relative shadow-inner group-hover:border-indigo-500/50 transition-all cursor-zoom-in"
                            onClick={() => setSelectedImage(file.imageBase64)}
                          >
                            <img 
                              src={`data:image/jpeg;base64,${file.imageBase64}`} 
                              alt={file.name}
                              className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-700" 
                            />
                            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity p-5 flex items-end">
                              <span className="text-xs font-black text-white uppercase truncate">{file.name}</span>
                            </div>
                          </div>
                          <span className="text-sm font-black text-zinc-500 truncate px-2 group-hover:text-zinc-200 transition-colors">{file.name}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </main>

      <style jsx global>{`
        .custom-scrollbar::-webkit-scrollbar { width: 6px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background: #27272a; border-radius: 10px; }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #3f3f46; }
      `}</style>
    </div>
  );
}