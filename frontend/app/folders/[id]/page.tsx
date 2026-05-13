"use client";

import { useState, useEffect, use, useRef } from "react";

import { 
  ArrowLeft, 
  FileText, 
  Image as ImageIcon, 
  Upload, 
  Loader2,
  FileIcon,
  X,
  Maximize2,
  Edit2,
  Check,
  FolderPlus,
  FolderOpen,
  ChevronRight,
  MoreVertical,
  Trash2,
  MoveHorizontal
} from "lucide-react";
import { 
  getFilesInFolder, 
  uploadFileToFolder, 
  FileItem, 
  getFolders, 
  Folder, 
  renameFile,
  moveFiles 
} from "@/lib/api";
import { useRouter } from "next/navigation";
import { ImagePreview } from "@/components/ui/ImagePreview";

interface FolderDetailPageProps {
  params: Promise<{ id: string }>;
}

export default function FolderDetailPage({ params }: FolderDetailPageProps) {
  const { id } = use(params);
  const router = useRouter();
  const [folder, setFolder] = useState<Folder | null>(null);
  const [allFolders, setAllFolders] = useState<Folder[]>([]);
  const [files, setFiles] = useState<FileItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isUploading, setIsUploading] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const [previewImage, setPreviewImage] = useState<string | null>(null);

  
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [showMoveModal, setShowMoveModal] = useState(false);

  
  const [editingFileId, setEditingFileId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const editInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (editingFileId && editInputRef.current) {
      editInputRef.current.focus();
      editInputRef.current.select();
    }
  }, [editingFileId]);

  useEffect(() => {
    const fetchData = async () => {
      setIsLoading(true);
      try {
        const folders = await getFolders();
        setAllFolders(folders);
        const currentFolder = folders.find(f => f.id === id);
        
        if (!currentFolder) {
          router.push("/folders");
          return;
        }
        
        setFolder(currentFolder);
        const folderFiles = await getFilesInFolder(currentFolder.name);
        setFiles(folderFiles);
      } catch (error) {
        console.error("Failed to fetch folder data", error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchData();
  }, [id, router]);

  const toggleSelectAll = () => {
    if (selectedIds.size === files.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(files.map(f => f.id)));
    }
  };

  const toggleSelect = (fileId: string) => {
    const newSelected = new Set(selectedIds);
    if (newSelected.has(fileId)) {
      newSelected.delete(fileId);
    } else {
      newSelected.add(fileId);
    }
    setSelectedIds(newSelected);
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files || !e.target.files[0] || !folder) return;
    
    
    for (let i = 0; i < e.target.files.length; i++) {
      await uploadFile(e.target.files[i]);
    }
  };

  const uploadFile = async (file: File) => {
    setIsUploading(true);
    try {
      await uploadFileToFolder(id, file);
      
      const folderFiles = await getFilesInFolder(folder!.name);
      setFiles(folderFiles);
    } catch (error) {
      console.error("Upload failed", error);
      
    } finally {
      setIsUploading(false);
    }
  };

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      await uploadFile(e.dataTransfer.files[0]);
    }
  };

  const startEditingFile = (file: FileItem) => {
    setEditingFileId(file.id);
    setEditValue(file.name);
  };

  const saveFileRename = async () => {
    if (!editingFileId) return;
    
    const trimmedValue = editValue.trim();
    const originalFile = files.find(f => f.id === editingFileId);
    
    if (!trimmedValue || trimmedValue === originalFile?.name) {
      setEditingFileId(null);
      return;
    }
    
    try {
      await renameFile(editingFileId, trimmedValue);
      
      setFiles(prev => prev.map(f => {
        if (f.id === editingFileId) {
          
          const folderPart = editingFileId.substring(0, editingFileId.lastIndexOf("/") + 1);
          const newId = folderPart + trimmedValue;
          return { ...f, id: newId, name: trimmedValue };
        }
        return f;
      }));
    } catch (error) {
      console.error("Failed to rename file", error);
      alert("Wystąpił błąd podczas zmiany nazwy pliku.");
    } finally {
      setEditingFileId(null);
    }
  };

  const handleFileKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      saveFileRename();
    } else if (e.key === "Escape") {
      setEditingFileId(null);
    }
  };

  const handleMoveFiles = async (targetFolder: Folder) => {
    try {
      const filePaths = Array.from(selectedIds);
      await moveFiles(filePaths, targetFolder.id);
      
      
      setFiles(prev => prev.filter(f => !selectedIds.has(f.id)));
      setSelectedIds(new Set());
      setShowMoveModal(false);
    } catch (error) {
      console.error("Failed to move files", error);
      alert("Wystąpił błąd podczas przenoszenia plików.");
    }
  };

  if (isLoading) {
    return (
      <div className="h-full flex items-center justify-center bg-white">
        <Loader2 size={40} className="animate-spin text-slate-900" />
      </div>
    );
  }

  if (!folder) return null;

  return (
    <div className="flex flex-col h-full bg-white overflow-y-auto">
      <div className="p-8 max-w-6xl mx-auto w-full">
        <div className="mb-6">
          <button 
            onClick={() => router.push("/folders")}
            className="flex items-center gap-2 text-gray-500 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft size={18} />
            <span>Powrót do folderów</span>
          </button>
        </div>

        {isUploading && (
          <div className="mb-8 p-6 bg-blue-50 border border-blue-200 rounded-xl shadow-sm flex items-center gap-4">
            <Loader2 size={24} className="animate-spin text-blue-600" />
            <div>
              <h3 className="font-semibold text-blue-900">Trwa wgrywanie i indeksowanie plików...</h3>
              <p className="text-sm text-blue-700">Przetwarzamy Twoje dokumenty. Może to potrwać chwilę, szczególnie w przypadku zdjęć.</p>
            </div>
          </div>
        )}

        <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 mb-8">
          <div>
            <h1 className="text-3xl font-bold text-slate-900 mb-2">{folder.name}</h1>
          </div>
          
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-2 px-4 py-2 bg-white border border-gray-300 text-slate-700 rounded-md hover:bg-gray-50 transition-colors shadow-sm cursor-pointer">
              <FolderPlus size={18} />
              <span className="font-medium">Wgraj folder</span>
              <input 
                type="file" 
                className="hidden" 
                onChange={handleFileUpload} 
                disabled={isUploading}
                {...{ webkitdirectory: "", directory: "" } as unknown as React.InputHTMLAttributes<HTMLInputElement>}
              />
            </label>
            <label className="flex items-center gap-2 px-4 py-2 bg-slate-900 text-white rounded-md hover:bg-slate-800 transition-colors shadow-sm cursor-pointer">
              {isUploading ? <Loader2 size={18} className="animate-spin" /> : <Upload size={18} />}
              <span className="font-medium">Wgraj plik</span>
              <input 
                type="file" 
                className="hidden" 
                onChange={handleFileUpload} 
                disabled={isUploading}
                multiple
                accept=".pdf,.txt,.png,.jpg,.jpeg"
              />
            </label>
          </div>
        </div>

        <div 
          onDragEnter={handleDrag}
          onDragLeave={handleDrag}
          onDragOver={handleDrag}
          onDrop={handleDrop}
          className={`mb-8 p-10 border-2 border-dashed rounded-xl flex flex-col items-center justify-center transition-all ${
            dragActive 
              ? "border-slate-900 bg-slate-50" 
              : "border-gray-200 bg-gray-50"
          }`}
        >
          <Upload size={32} className={`mb-3 ${dragActive ? "text-slate-900" : "text-gray-400"}`} />
          <p className="text-gray-600 font-medium">Przeciągnij i upuść pliki lub foldery tutaj</p>
          <p className="text-gray-400 text-sm mt-1">Obsługiwane formaty: PDF, TXT, PNG, JPG</p>
        </div>

        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm relative">
          <div className="grid grid-cols-12 gap-4 px-6 py-3 bg-gray-50 border-b border-gray-200 text-xs font-semibold text-gray-500 uppercase tracking-wider">
            <div className="col-span-1 flex items-center">
              <input 
                type="checkbox" 
                className="w-4 h-4 rounded border-gray-300 text-slate-900 focus:ring-slate-900 cursor-pointer"
                checked={files.length > 0 && selectedIds.size === files.length}
                onChange={toggleSelectAll}
              />
            </div>
            <div className="col-span-7 md:col-span-5">Nazwa pliku</div>
            <div className="hidden md:block col-span-3">Typ</div>
            <div className="col-span-4 md:col-span-3 text-right">Akcje</div>
          </div>
          
          <div className="divide-y divide-gray-100">
            {files.length === 0 ? (
              <div className="px-6 py-12 text-center text-gray-500">
                Ten folder jest pusty. Wgraj swój pierwszy plik, aby rozpocząć.
              </div>
            ) : (
              files.map((file) => (
                <div 
                  key={file.id} 
                  className={`grid grid-cols-12 gap-4 px-6 py-4 items-center hover:bg-gray-50 transition-colors ${selectedIds.has(file.id) ? "bg-slate-50" : ""}`}
                >
                  <div className="col-span-1 flex items-center">
                    <input 
                      type="checkbox" 
                      className="w-4 h-4 rounded border-gray-300 text-slate-900 focus:ring-slate-900 cursor-pointer"
                      checked={selectedIds.has(file.id)}
                      onChange={() => toggleSelect(file.id)}
                    />
                  </div>
                  <div className="col-span-7 md:col-span-5 flex items-center gap-3">
                    <div className={`w-10 h-10 rounded flex items-center justify-center shrink-0 overflow-hidden ${
                      file.type.includes("image") ? "bg-blue-50" : 
                      file.type.includes("pdf") ? "bg-red-50" : 
                      "bg-gray-100"
                    }`}>
                      {file.type.includes("image") && file.url ? (
                        <img 
                          src={file.url} 
                          alt={file.name} 
                          className="w-full h-full object-cover cursor-zoom-in"
                          onClick={() => setPreviewImage(file.url)}
                        />
                      ) : (
                        <div className={
                          file.type.includes("image") ? "text-blue-600" : 
                          file.type.includes("pdf") ? "text-red-600" : 
                          "text-gray-600"
                        }>
                          {file.type.includes("image") ? <ImageIcon size={20} /> : <FileText size={20} />}
                        </div>
                      )}
                    </div>
                    <div className="flex flex-col min-w-0 flex-1">
                      {editingFileId === file.id ? (
                        <div className="flex items-center gap-2 bg-white border border-slate-400 rounded px-2 py-1 shadow-sm ring-1 ring-slate-400/10 max-w-md">
                          <input
                            ref={editInputRef}
                            type="text"
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
                            onKeyDown={handleFileKeyDown}
                            onBlur={saveFileRename}
                            className="flex-1 min-w-0 text-sm font-medium text-gray-900 bg-transparent outline-none"
                          />
                          <div className="flex items-center gap-1 shrink-0">
                            <button 
                              onMouseDown={(e) => e.preventDefault()}
                              onClick={saveFileRename} 
                              className="p-1 text-slate-400 hover:text-green-600 hover:bg-green-50 rounded transition-colors"
                            >
                              <Check size={14} />
                            </button>
                            <button 
                              onMouseDown={(e) => e.preventDefault()}
                              onClick={() => setEditingFileId(null)} 
                              className="p-1 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors"
                            >
                              <X size={14} />
                            </button>
                          </div>
                        </div>
                      ) : (
                        <span className="text-sm font-medium text-gray-900 truncate" title={file.name}>
                          {file.name}
                        </span>
                      )}
                      <span className="text-xs text-gray-500 md:hidden">{file.type}</span>
                    </div>
                  </div>
                  
                  <div className="hidden md:block col-span-3 text-sm text-gray-500">
                    {file.type}
                  </div>
                  
                  <div className="col-span-4 md:col-span-3 flex justify-end gap-2">
                    <button 
                      onClick={() => startEditingFile(file)}
                      className="p-2 text-gray-400 hover:text-slate-900 hover:bg-gray-100 rounded-full transition-colors"
                      title="Zmień nazwę"
                    >
                      <Edit2 size={18} />
                    </button>
                    {file.url && file.type.includes("image") && (
                      <button 
                        onClick={() => setPreviewImage(file.url)}
                        className="p-2 text-gray-400 hover:text-slate-900 hover:bg-gray-100 rounded-full transition-colors"
                        title="Powiększ"
                      >
                        <Maximize2 size={18} />
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {selectedIds.size > 0 && (
        <div className="fixed bottom-8 left-1/2 -translate-x-1/2 bg-slate-900 text-white px-6 py-3 rounded-full shadow-2xl z-40 flex items-center gap-6 animate-in slide-in-from-bottom-4 duration-300">
          <div className="flex items-center gap-2 pr-6 border-r border-slate-700 text-sm font-medium">
            <span className="bg-slate-700 w-6 h-6 rounded-full flex items-center justify-center text-xs">
              {selectedIds.size}
            </span>
            <span>Zaznaczono</span>
          </div>
          
          <div className="flex items-center gap-4">
            <button 
              onClick={() => setShowMoveModal(true)}
              className="flex items-center gap-2 hover:text-blue-400 transition-colors text-sm font-medium"
            >
              <MoveHorizontal size={18} />
              Przenieś
            </button>
            <button className="flex items-center gap-2 hover:text-red-400 transition-colors text-sm font-medium">
              <Trash2 size={18} />
              Usuń
            </button>
            <button 
              onClick={() => setSelectedIds(new Set())}
              className="p-1 hover:bg-slate-800 rounded-full transition-colors ml-2"
            >
              <X size={18} />
            </button>
          </div>
        </div>
      )}

      {showMoveModal && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
              <h3 className="font-bold text-lg text-slate-900">Przenieś do folderu</h3>
              <button onClick={() => setShowMoveModal(false)} className="text-gray-400 hover:text-gray-600">
                <X size={20} />
              </button>
            </div>
            <div className="p-2 max-h-[60vh] overflow-y-auto">
              {allFolders
                .filter(f => f.id !== id) 
                .map(f => (
                <button 
                  key={f.id}
                  onClick={() => handleMoveFiles(f)}
                  className="w-full flex items-center justify-between p-4 hover:bg-slate-50 rounded-xl transition-colors group text-left"
                >
                  <div className="flex items-center gap-3">
                    <div className="p-2 bg-slate-100 rounded-lg text-slate-600 group-hover:bg-white group-hover:shadow-sm transition-all">
                      <FolderOpen size={20} />
                    </div>
                    <span className="font-medium text-slate-700">{f.name}</span>
                  </div>
                  <ChevronRight size={18} className="text-gray-300" />
                </button>
              ))}
              {allFolders.length <= 1 && (
                <div className="p-8 text-center text-gray-500">
                  Brak innych folderów, do których można przenieść pliki.
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {previewImage && (
        <ImagePreview url={previewImage} onClose={() => setPreviewImage(null)} />
      )}
    </div>
  );
}
