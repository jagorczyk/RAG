"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { Folder, Plus, Trash2, FolderOpen, FolderPlus, Loader2 } from "lucide-react";
import { getFolders, createFolder, deleteFolder, Folder as FolderType, uploadFileToFolder } from "@/lib/api";

export default function FoldersPage() {
  const [folders, setFolders] = useState<FolderType[]>([]);
  const [isAdding, setIsAdding] = useState(false);
  const [isUploadingFolder, setIsUploadingFolder] = useState(false);
  const [newFolderName, setNewFolderName] = useState("");

  useEffect(() => {
    getFolders().then(setFolders);
  }, []);

  const handleCreateFolder = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFolderName.trim()) return;
    
    try {
      const folder = await createFolder(newFolderName.trim());
      setFolders(prev => [...prev, folder]);
      setNewFolderName("");
      setIsAdding(false);
    } catch (error) {
      console.error("Failed to create folder", error);
    }
  };

  const handleFolderUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files || e.target.files.length === 0) return;

    setIsUploadingFolder(true);
    try {
      
      const firstFile = e.target.files[0];
      const relativePath = (firstFile as any).webkitRelativePath;
      const folderName = relativePath.split('/')[0] || "Nowy Folder";

      
      const newFolder = await createFolder(folderName);
      setFolders(prev => [...prev, newFolder]);

      
      for (let i = 0; i < e.target.files.length; i++) {
        const file = e.target.files[i];
        
        if (!file.name.startsWith('.')) {
          await uploadFileToFolder(newFolder.id, file);
        }
      }

      alert(`Pomyślnie utworzono folder "${folderName}" i wgrano pliki.`);
    } catch (error) {
      console.error("Folder upload failed", error);
      alert("Wystąpił błąd podczas wgrywania folderu.");
    } finally {
      setIsUploadingFolder(false);
      
      e.target.value = '';
    }
  };

  const handleDeleteFolder = async (id: string, e: React.MouseEvent) => {
    e.preventDefault(); 
    e.stopPropagation();
    
    if (!confirm("Czy na pewno chcesz usunąć ten folder?")) return;
    
    try {
      await deleteFolder(id);
      setFolders(prev => prev.filter(f => f.id !== id));
    } catch (error) {
      console.error("Failed to delete folder", error);
    }
  };

  return (
    <div className="flex flex-col h-full bg-white overflow-y-auto">
      <div className="p-8 max-w-6xl mx-auto w-full">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-bold text-slate-900 mb-2">Foldery</h1>
            {}
          </div>
          
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-2 px-4 py-2 bg-white border border-gray-300 text-slate-700 rounded-md hover:bg-gray-50 transition-colors shadow-sm cursor-pointer">
              {isUploadingFolder ? <Loader2 size={20} className="animate-spin" /> : <FolderPlus size={20} />}
              <span className="font-medium">Wgraj folder</span>
              <input 
                type="file" 
                className="hidden" 
                onChange={handleFolderUpload} 
                disabled={isUploadingFolder}
                {...{ webkitdirectory: "", directory: "" } as any}
              />
            </label>
            <button 
              onClick={() => setIsAdding(true)}
              className="flex items-center gap-2 px-4 py-2 bg-slate-900 text-white rounded-md hover:bg-slate-800 transition-colors shadow-sm"
            >
              <Plus size={20} />
              <span className="font-medium">Nowy Folder</span>
            </button>
          </div>
        </div>

        {isUploadingFolder && (
          <div className="mb-8 p-6 bg-blue-50 border border-blue-200 rounded-xl shadow-sm flex items-center gap-4">
            <Loader2 size={24} className="animate-spin text-blue-600" />
            <div>
              <h3 className="font-semibold text-blue-900">Trwa wgrywanie folderu...</h3>
              <p className="text-sm text-blue-700">Tworzymy folder i indeksujemy Twoje pliki. Proszę czekać.</p>
            </div>
          </div>
        )}

        {isAdding && (
          <div className="mb-8 p-6 bg-gray-50 border border-gray-200 rounded-xl shadow-sm">
            <h2 className="text-lg font-semibold text-slate-800 mb-4">Utwórz nowy folder</h2>
            <form onSubmit={handleCreateFolder} className="flex gap-4">
              <input 
                type="text" 
                value={newFolderName}
                onChange={(e) => setNewFolderName(e.target.value)}
                placeholder="Nazwa folderu..." 
                autoFocus
                className="flex-1 px-4 py-2 bg-white border text-gray-900 border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900"
              />
              <button 
                type="submit" 
                disabled={!newFolderName.trim()}
                className="px-6 py-2 bg-slate-900 text-white rounded-md hover:bg-slate-800 disabled:opacity-50 transition-colors"
              >
                Utwórz
              </button>
              <button 
                type="button" 
                onClick={() => { setIsAdding(false); setNewFolderName(""); }}
                className="px-6 py-2 bg-white text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50 transition-colors"
              >
                Anuluj
              </button>
            </form>
          </div>
        )}

        {folders.length === 0 && !isAdding ? (
          <div className="flex flex-col items-center justify-center p-12 text-center border-2 border-dashed border-gray-200 rounded-xl bg-gray-50">
            <FolderOpen size={48} className="text-gray-400 mb-4" />
            <h3 className="text-lg font-medium text-gray-900 mb-1">Brak folderów</h3>
            <p className="text-gray-500 max-w-sm">Nie utworzono jeszcze żadnych folderów. Kliknij przycisk "Nowy Folder", aby rozpocząć.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {folders.map(folder => (
              <Link 
                href={`/folders/${folder.id}`} 
                key={folder.id}
                className="group relative flex flex-col p-6 bg-white border border-gray-200 rounded-xl hover:border-slate-400 hover:shadow-md transition-all cursor-pointer"
              >
                <div className="absolute top-4 right-4 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button 
                    onClick={(e) => handleDeleteFolder(folder.id, e)}
                    className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-full transition-colors"
                    title="Usuń folder"
                  >
                    <Trash2 size={18} />
                  </button>
                </div>
                <Folder size={48} className="text-slate-800 mb-4 group-hover:scale-105 transition-transform" />
                <h3 className="text-lg font-semibold text-gray-900 truncate pr-8" title={folder.name}>
                  {folder.name}
                </h3>
                <p className="text-sm text-gray-500 mt-1">
                  Utworzono: {new Date(folder.createdAt).toLocaleDateString()}
                </p>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
