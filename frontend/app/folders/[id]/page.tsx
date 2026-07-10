"use client";

import { useState, useEffect, use, useRef } from "react";
import {
  ArrowLeft,
  FileText,
  Image as ImageIcon,
  Upload,
  Loader2,
  X,
  Check,
  FolderPlus,
  FolderOpen,
  ChevronRight,
  Trash2,
  MoveHorizontal,
} from "lucide-react";
import {
  getFilesInFolder,
  uploadFileToFolderWithProgress,
  getFolders,
  Folder,
  renameFile,
  moveFiles,
  deleteFiles,
  getFilePreview,
  getFileEmbeddings,
  FileItem,
  FilePreview,
  type UploadProgress,
} from "@/lib/api";
import { useRouter } from "next/navigation";
import { ImagePreview } from "@/components/ui/ImagePreview";
import { LoadingBar } from "@/components/ui/LoadingBar";
import { ViewModeToggle } from "@/components/ui/ViewModeToggle";
import { FileItemActions } from "@/components/folders/FileItemActions";
import { useViewMode } from "@/hooks/useViewMode";

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
  const [entityTag, setEntityTag] = useState("");
  const [ingestionProgress, setIngestionProgress] = useState<UploadProgress | null>(null);
  const [dragActive, setDragActive] = useState(false);
  const [previewFile, setPreviewFile] = useState<FilePreview | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [showMoveModal, setShowMoveModal] = useState(false);
  const [editingFileId, setEditingFileId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const editInputRef = useRef<HTMLInputElement>(null);
  const { viewMode, setViewMode } = useViewMode();

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
        const currentFolder = folders.find((f) => f.id === id);

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
      setSelectedIds(new Set(files.map((f) => f.id)));
    }
  };

  const toggleSelect = (fileId: string) => {
    const newSelected = new Set(selectedIds);
    if (newSelected.has(fileId)) newSelected.delete(fileId);
    else newSelected.add(fileId);
    setSelectedIds(newSelected);
  };

  const uploadFiles = async (fileList: FileList | File[]) => {
    if (!folder) return;

    const filesToUpload = Array.from(fileList).filter(
      (file) => !file.name.startsWith(".")
    );
    if (filesToUpload.length === 0) return;

    setIsUploading(true);
    setIngestionProgress({
      percent: 0,
      fileIndex: 1,
      fileTotal: filesToUpload.length,
      fileName: filesToUpload[0].name,
      phase: "uploading",
    });

    try {
      for (let i = 0; i < filesToUpload.length; i++) {
        await uploadFileToFolderWithProgress(
          id,
          filesToUpload[i],
          i,
          filesToUpload.length,
          setIngestionProgress,
          entityTag
        );
      }

      setIngestionProgress({
        percent: 100,
        fileIndex: filesToUpload.length,
        fileTotal: filesToUpload.length,
        fileName: filesToUpload[filesToUpload.length - 1].name,
        phase: "processing",
      });

      const folderFiles = await getFilesInFolder(folder.name);
      setFiles(folderFiles);
    } catch (error) {
      console.error("Upload failed", error);
      alert("Wystąpił błąd podczas wgrywania pliku.");
    } finally {
      setIsUploading(false);
      setTimeout(() => setIngestionProgress(null), 600);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files || e.target.files.length === 0) return;
    await uploadFiles(e.target.files);
    e.target.value = "";
  };

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") setDragActive(true);
    else if (e.type === "dragleave") setDragActive(false);
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files?.length) {
      await uploadFiles(e.dataTransfer.files);
    }
  };

  const startEditingFile = (file: FileItem) => {
    setEditingFileId(file.id);
    setEditValue(file.name);
  };

  const saveFileRename = async () => {
    if (!editingFileId) return;
    const trimmedValue = editValue.trim();
    const originalFile = files.find((f) => f.id === editingFileId);
    if (!trimmedValue || trimmedValue === originalFile?.name) {
      setEditingFileId(null);
      return;
    }
    try {
      await renameFile(editingFileId, trimmedValue);
      setFiles((prev) =>
        prev.map((f) => {
          if (f.id === editingFileId) {
            const folderPart = editingFileId.substring(
              0,
              editingFileId.lastIndexOf("/") + 1
            );
            return { ...f, id: folderPart + trimmedValue, name: trimmedValue };
          }
          return f;
        })
      );
    } catch (error) {
      console.error("Failed to rename file", error);
      alert("Wystąpił błąd podczas zmiany nazwy pliku.");
    } finally {
      setEditingFileId(null);
    }
  };

  const handleFileKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") saveFileRename();
    else if (e.key === "Escape") setEditingFileId(null);
  };

  const handleMoveFiles = async (targetFolder: Folder) => {
    try {
      await moveFiles(Array.from(selectedIds), targetFolder.id);
      setFiles((prev) => prev.filter((f) => !selectedIds.has(f.id)));
      setSelectedIds(new Set());
      setShowMoveModal(false);
    } catch (error) {
      console.error("Failed to move files", error);
      alert("Wystąpił błąd podczas przenoszenia plików.");
    }
  };

  const handleDeleteFiles = async () => {
    if (selectedIds.size === 0) return;

    const count = selectedIds.size;
    const confirmed = window.confirm(
      count === 1
        ? "Czy na pewno chcesz usunąć zaznaczony plik?"
        : `Czy na pewno chcesz usunąć ${count} zaznaczone pliki?`
    );
    if (!confirmed) return;

    try {
      await deleteFiles(Array.from(selectedIds));
      setFiles((prev) => prev.filter((f) => !selectedIds.has(f.id)));
      setSelectedIds(new Set());
    } catch (error) {
      console.error("Failed to delete files", error);
      alert("Wystąpił błąd podczas usuwania plików.");
    }
  };

  const openFilePreview = async (file: FileItem) => {
    if (file.type.includes("image") && file.url) {
      setPreviewFile({
        kind: "image",
        title: file.name,
        mimeType: file.type,
        content: file.url,
        path: file.id,
      });
      return;
    }

    try {
      const preview = await getFilePreview(file.id);
      setPreviewFile(preview);
    } catch (error) {
      console.error("Failed to open file preview", error);
      alert("Nie udało się pobrać podglądu pliku.");
    }
  };

  const openFileEmbeddings = async (file: FileItem) => {
    try {
      const data = await getFileEmbeddings(file.id);
      setPreviewFile({
        kind: "text",
        title: `${data.title} — embeddingi (${data.chunkCount})`,
        mimeType: "text/plain",
        content: data.content,
      });
    } catch (error) {
      console.error("Failed to open file embeddings", error);
      alert("Nie udało się pobrać embeddingów pliku.");
    }
  };

  if (isLoading) {
    return (
      <div className="page-shell">
        <div className="page-body space-y-3">
          <div className="skeleton h-8 w-48" />
          <div className="skeleton h-64 w-full" />
        </div>
      </div>
    );
  }

  if (!folder) return null;

  return (
    <div
      className="page-shell"
      onDragEnter={handleDrag}
      onDragLeave={handleDrag}
      onDragOver={handleDrag}
      onDrop={handleDrop}
    >
      <header className="page-header">
        <div className="mx-auto max-w-6xl">
          <button
            type="button"
            onClick={() => router.push("/folders")}
            className="btn-ghost -ml-2 mb-2 px-2"
          >
            <ArrowLeft size={16} />
            <span>Powrót do folderów</span>
          </button>
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h1 className="page-title">{folder.name}</h1>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <ViewModeToggle value={viewMode} onChange={setViewMode} />
              <input
                type="text"
                placeholder="Tag (np. Osoba A)"
                value={entityTag}
                onChange={(e) => setEntityTag(e.target.value)}
                className="rounded-[6px] border border-border bg-surface px-3 py-1.5 text-sm text-ink outline-none focus:border-accent"
                disabled={isUploading}
              />
              <label className="btn-secondary cursor-pointer">
                <FolderPlus size={18} />
                <span>Wgraj folder</span>
                <input
                  type="file"
                  className="hidden"
                  onChange={handleFileUpload}
                  disabled={isUploading}
                  {...({
                    webkitdirectory: "",
                    directory: "",
                  } as unknown as React.InputHTMLAttributes<HTMLInputElement>)}
                />
              </label>
              <label className="btn-primary cursor-pointer">
                {isUploading ? (
                  <Loader2 size={18} className="animate-spin" />
                ) : (
                  <Upload size={18} />
                )}
                <span>Wgraj plik</span>
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
        </div>
      </header>

      <div className="page-body">
        {ingestionProgress && (
          <LoadingBar progress={ingestionProgress} />
        )}

        {dragActive && (
          <div className="mb-4 rounded-[10px] border border-dashed border-accent bg-accent-subtle/60 px-4 py-3 text-sm text-ink-muted">
            Upuść pliki
          </div>
        )}

        <div className="data-table" role="region" aria-label={`Pliki w folderze ${folder.name}`}>
          {viewMode === "list" ? (
            <div className="data-table-head grid-cols-[52px_1fr_auto_auto] md:grid-cols-[52px_1fr_140px_auto]">
              <span className="flex items-center justify-center">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
                  checked={files.length > 0 && selectedIds.size === files.length}
                  onChange={toggleSelectAll}
                  aria-label="Zaznacz wszystkie"
                />
              </span>
              <span>Nazwa pliku</span>
              <span className="hidden md:block">Typ</span>
              <span className="text-right">Akcje</span>
            </div>
          ) : files.length > 0 ? (
            <div className="flex items-center gap-2 border-b border-border px-4 py-2.5">
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
                checked={files.length > 0 && selectedIds.size === files.length}
                onChange={toggleSelectAll}
                aria-label="Zaznacz wszystkie"
              />
              <span className="text-xs text-ink-muted">Zaznacz wszystkie</span>
            </div>
          ) : null}

          {files.length === 0 ? (
            <div className="px-4 py-10 text-center">
              <Upload size={28} className="mx-auto text-ink-muted" />
              <p className="mt-3 text-sm text-ink-muted">Pusty folder</p>
            </div>
          ) : viewMode === "list" ? (
            files.map((file) => (
              <div
                key={file.id}
                className={`data-table-row grid-cols-[52px_1fr_auto_auto] md:grid-cols-[52px_1fr_140px_auto] ${
                  selectedIds.has(file.id) ? "bg-accent-subtle/40" : ""
                }`}
              >
                <span className="flex items-center justify-center">
                  <input
                    type="checkbox"
                    className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
                    checked={selectedIds.has(file.id)}
                    onChange={() => toggleSelect(file.id)}
                    aria-label={`Zaznacz ${file.name}`}
                  />
                </span>

                <div className="flex min-w-0 items-center gap-3">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-[6px] bg-accent-muted text-accent">
                    {file.type.includes("image") && file.url ? (
                      <img
                        src={file.url}
                        alt=""
                        className="h-full w-full cursor-zoom-in object-cover"
                        onClick={() => openFilePreview(file)}
                      />
                    ) : file.type.includes("image") ? (
                      <ImageIcon size={16} />
                    ) : (
                      <FileText size={16} />
                    )}
                  </span>
                  <div className="min-w-0 flex-1">
                    {editingFileId === file.id ? (
                      <div className="flex max-w-md items-center gap-1 rounded-[6px] border border-border-strong bg-surface-raised px-2 py-1">
                        <input
                          ref={editInputRef}
                          type="text"
                          value={editValue}
                          onChange={(e) => setEditValue(e.target.value)}
                          onKeyDown={handleFileKeyDown}
                          onBlur={saveFileRename}
                          className="min-w-0 flex-1 bg-transparent text-sm text-ink outline-none"
                        />
                        <button
                          type="button"
                          onMouseDown={(e) => e.preventDefault()}
                          onClick={saveFileRename}
                          className="rounded p-1 text-ink-muted hover:text-success"
                        >
                          <Check size={14} />
                        </button>
                        <button
                          type="button"
                          onMouseDown={(e) => e.preventDefault()}
                          onClick={() => setEditingFileId(null)}
                          className="rounded p-1 text-ink-muted hover:text-error"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    ) : (
                      <>
                        <p className="truncate text-sm font-medium text-ink" title={file.name}>
                          {file.name}
                        </p>
                        {file.type.includes("image") && file.extractedText && (
                          <p
                            className="mt-0.5 line-clamp-2 text-xs leading-relaxed text-ink-muted"
                            title={file.extractedText}
                          >
                            {file.extractedText}
                          </p>
                        )}
                        <p className="truncate font-mono text-[10px] text-ink-muted md:hidden">
                          {file.type}
                        </p>
                      </>
                    )}
                  </div>
                </div>

                <span className="hidden truncate font-mono text-xs text-ink-muted md:block">
                  {file.type}
                </span>

                <div className="flex justify-end gap-0.5">
                  <FileItemActions
                    file={file}
                    onRename={startEditingFile}
                    onEmbeddings={openFileEmbeddings}
                    onPreview={openFilePreview}
                  />
                </div>
              </div>
            ))
          ) : (
            <div className="item-grid">
              {files.map((file) => (
                <div
                  key={file.id}
                  className={`item-grid-card group ${
                    selectedIds.has(file.id) ? "is-selected" : ""
                  }`}
                >
                  <input
                    type="checkbox"
                    className="item-grid-checkbox h-4 w-4 rounded border-border text-accent focus:ring-accent"
                    checked={selectedIds.has(file.id)}
                    onChange={() => toggleSelect(file.id)}
                    aria-label={`Zaznacz ${file.name}`}
                  />
                  <button
                    type="button"
                    className="item-grid-icon"
                    onClick={() => {
                      if (file.type.includes("image") && file.url) {
                        openFilePreview(file);
                      }
                    }}
                    title={file.name}
                  >
                    {file.type.includes("image") && file.url ? (
                      <img
                        src={file.url}
                        alt=""
                        className="h-full w-full object-cover"
                      />
                    ) : file.type.includes("image") ? (
                      <ImageIcon size={24} />
                    ) : (
                      <FileText size={24} />
                    )}
                  </button>
                  {editingFileId === file.id ? (
                    <div className="flex w-full items-center gap-1 rounded-[6px] border border-border-strong bg-surface-raised px-2 py-1">
                      <input
                        ref={editInputRef}
                        type="text"
                        value={editValue}
                        onChange={(e) => setEditValue(e.target.value)}
                        onKeyDown={handleFileKeyDown}
                        onBlur={saveFileRename}
                        className="min-w-0 flex-1 bg-transparent text-xs text-ink outline-none"
                      />
                    </div>
                  ) : (
                    <span className="item-grid-name" title={file.name}>
                      {file.name}
                    </span>
                  )}
                  <div className="item-grid-actions">
                    <FileItemActions
                      file={file}
                      onRename={startEditingFile}
                      onEmbeddings={openFileEmbeddings}
                      onPreview={openFilePreview}
                      compact
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {selectedIds.size > 0 && (
        <div className="fixed bottom-6 left-1/2 z-40 flex -translate-x-1/2 items-center gap-4 rounded-[10px] border border-border bg-sidebar px-4 py-2.5 shadow-md">
          <span className="flex items-center gap-2 border-r border-border pr-4 text-sm font-medium text-ink">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-accent-muted font-mono text-xs text-accent">
              {selectedIds.size}
            </span>
            Zaznaczono
          </span>
          <button
            type="button"
            onClick={() => setShowMoveModal(true)}
            className="btn-ghost px-2 py-1 text-sm"
          >
            <MoveHorizontal size={16} />
            Przenieś
          </button>
          <button
            type="button"
            onClick={handleDeleteFiles}
            className="btn-ghost px-2 py-1 text-sm text-error"
          >
            <Trash2 size={16} />
            Usuń
          </button>
          <button
            type="button"
            onClick={() => setSelectedIds(new Set())}
            className="btn-ghost p-1"
            aria-label="Odznacz wszystko"
          >
            <X size={16} />
          </button>
        </div>
      )}

      {showMoveModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="move-dialog-title"
        >
          <div className="w-full max-w-md overflow-hidden rounded-[14px] border border-border bg-surface-raised shadow-md">
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <h3 id="move-dialog-title" className="text-base font-semibold text-ink">
                Przenieś do folderu
              </h3>
              <button
                type="button"
                onClick={() => setShowMoveModal(false)}
                className="btn-ghost p-1"
              >
                <X size={18} />
              </button>
            </div>
            <div className="max-h-[60vh] overflow-y-auto p-2">
              {allFolders
                .filter((f) => f.id !== id)
                .map((f) => (
                  <button
                    key={f.id}
                    type="button"
                    onClick={() => handleMoveFiles(f)}
                    className="flex w-full items-center justify-between rounded-[10px] px-3 py-2.5 text-left transition-colors hover:bg-accent-subtle/50"
                  >
                    <span className="flex items-center gap-2.5">
                      <span className="flex h-8 w-8 items-center justify-center rounded-[6px] bg-accent-muted text-accent">
                        <FolderOpen size={16} />
                      </span>
                      <span className="font-medium text-ink">{f.name}</span>
                    </span>
                    <ChevronRight size={16} className="text-ink-muted" />
                  </button>
                ))}
              {allFolders.length <= 1 && (
                <p className="px-4 py-8 text-center text-sm text-ink-muted">
                  Brak innych folderów docelowych.
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      {previewFile && (
        <ImagePreview preview={previewFile} onClose={() => setPreviewFile(null)} />
      )}
    </div>
  );
}
