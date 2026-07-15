"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { FolderOpen, Plus, Trash2, FolderPlus, Loader2, RefreshCw } from "lucide-react";
import {
  getFolders,
  createFolder,
  deleteFolder,
  clearAllData,
  Folder as FolderType,
  uploadFileToFolder,
  startContextReanalysis,
  getContextReanalysisStatus,
  ReanalysisStatus,
} from "@/lib/api";
import { ViewModeToggle } from "@/components/ui/ViewModeToggle";
import { useViewMode } from "@/hooks/useViewMode";

function formatFolderDate(value: string) {
  return value ? new Date(value).toLocaleDateString("pl-PL") : "—";
}

export default function FoldersPage() {
  const [folders, setFolders] = useState<FolderType[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isAdding, setIsAdding] = useState(false);
  const [isUploadingFolder, setIsUploadingFolder] = useState(false);
  const [isClearingAll, setIsClearingAll] = useState(false);
  const [showClearModal, setShowClearModal] = useState(false);
  const [clearConfirmText, setClearConfirmText] = useState("");
  const [newFolderName, setNewFolderName] = useState("");
  const [reanalysis, setReanalysis] = useState<ReanalysisStatus | null>(null);
  const { viewMode, setViewMode } = useViewMode();

  useEffect(() => {
    getFolders()
      .then(setFolders)
      .finally(() => setIsLoading(false));
  }, []);

  const handleCreateFolder = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFolderName.trim()) return;

    try {
      const folder = await createFolder(newFolderName.trim());
      setFolders((prev) => [...prev, folder]);
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
      const relativePath = (firstFile as unknown as { webkitRelativePath: string })
        .webkitRelativePath;
      const folderName = relativePath.split("/")[0] || "Nowy folder";

      const newFolder = await createFolder(folderName);
      setFolders((prev) => [...prev, newFolder]);

      for (let i = 0; i < e.target.files.length; i++) {
        const file = e.target.files[i];
        if (!file.name.startsWith(".")) {
          await uploadFileToFolder(newFolder.id, file);
        }
      }
    } catch (error) {
      console.error("Folder upload failed", error);
      alert("Wystąpił błąd podczas wgrywania folderu.");
    } finally {
      setIsUploadingFolder(false);
      e.target.value = "";
    }
  };

  const handleDeleteFolder = async (id: string, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    if (!confirm("Czy na pewno chcesz usunąć ten folder?")) return;

    try {
      await deleteFolder(id);
      setFolders((prev) => prev.filter((f) => f.id !== id));
    } catch (error) {
      console.error("Failed to delete folder", error);
    }
  };

  const handleClearAllData = async () => {
    if (clearConfirmText !== "WYCZYSC") return;

    setIsClearingAll(true);
    try {
      await clearAllData();
      setFolders([]);
      setShowClearModal(false);
      setClearConfirmText("");
    } catch (error) {
      console.error("Failed to clear all data", error);
      alert("Wystąpił błąd podczas czyszczenia danych.");
    } finally {
      setIsClearingAll(false);
    }
  };

  const handleReanalysis = async () => {
    if (!confirm("Przeliczyć kontekst wszystkich zapisanych zdjęć?")) return;
    try {
      let status = await startContextReanalysis();
      setReanalysis(status);
      while (status.status === "RUNNING") {
        await new Promise((resolve) => setTimeout(resolve, 1500));
        status = await getContextReanalysisStatus(status.jobId);
        setReanalysis(status);
      }
    } catch (error) {
      console.error("Context reanalysis failed", error);
      alert("Nie udało się uruchomić reanalizy kontekstu.");
    }
  };

  return (
    <div className="page-shell">
      <header className="page-header">
        <div className="mx-auto flex max-w-6xl flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="page-title">Foldery</h1>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <ViewModeToggle value={viewMode} onChange={setViewMode} />
            <label className="btn-secondary cursor-pointer">
              {isUploadingFolder ? (
                <Loader2 size={18} className="animate-spin" />
              ) : (
                <FolderPlus size={18} />
              )}
              <span>Wgraj folder</span>
              <input
                type="file"
                className="hidden"
                onChange={handleFolderUpload}
                disabled={isUploadingFolder}
                {...({
                  webkitdirectory: "",
                  directory: "",
                } as unknown as React.InputHTMLAttributes<HTMLInputElement>)}
              />
            </label>
            <button
              type="button"
              onClick={handleReanalysis}
              className="btn-secondary"
              disabled={reanalysis?.status === "RUNNING"}
              title="Uzupełnij czynności, obiekty, scenę i napisy na zdjęciach"
            >
              {reanalysis?.status === "RUNNING" ? <Loader2 size={18} className="animate-spin" /> : <RefreshCw size={18} />}
              <span>Analizuj kontekst</span>
            </button>
            <button
              type="button"
              onClick={() => setIsAdding(true)}
              className="btn-primary"
            >
              <Plus size={18} />
              <span>Nowy folder</span>
            </button>
            <button
              type="button"
              onClick={() => setShowClearModal(true)}
              className="btn-ghost text-error"
              disabled={isClearingAll}
              title="Wyczyść wszystkie foldery, pliki i embeddingi"
            >
              {isClearingAll ? (
                <Loader2 size={18} className="animate-spin" />
              ) : (
                <Trash2 size={18} />
              )}
              <span>Wyczyść wszystko</span>
            </button>
          </div>
        </div>
      </header>

      <div className="page-body">
        {reanalysis && (
          <div className="status-banner" role="status">
            <RefreshCw size={18} className={reanalysis.status === "RUNNING" ? "animate-spin" : ""} />
            <span>Analiza kontekstu: {reanalysis.completed}/{reanalysis.total}, błędy: {reanalysis.failed}</span>
          </div>
        )}
        {isUploadingFolder && (
          <div className="status-banner" role="status">
            <Loader2 size={18} className="animate-spin text-accent" />
          </div>
        )}

        {isAdding && (
          <div className="mb-4 rounded-[10px] border border-border bg-surface-raised p-4">
            <h2 className="text-sm font-semibold text-ink">Nowy folder</h2>
            <form onSubmit={handleCreateFolder} className="mt-3 flex flex-col gap-2 sm:flex-row">
              <input
                type="text"
                value={newFolderName}
                onChange={(e) => setNewFolderName(e.target.value)}
                placeholder="Nazwa folderu"
                autoFocus
                className="input-field flex-1"
              />
              <div className="flex gap-2">
                <button
                  type="submit"
                  disabled={!newFolderName.trim()}
                  className="btn-primary"
                >
                  Utwórz
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setIsAdding(false);
                    setNewFolderName("");
                  }}
                  className="btn-secondary"
                >
                  Anuluj
                </button>
              </div>
            </form>
          </div>
        )}

        {viewMode === "list" ? (
        <div className="data-table" role="region" aria-label="Lista folderów">
          <div className="data-table-head grid-cols-[minmax(0,1fr)_4rem] gap-x-4 sm:grid-cols-[minmax(0,1fr)_6.5rem_6.5rem_4rem]">
            <span>Nazwa</span>
            <span className="hidden sm:block">Utworzono</span>
            <span className="hidden sm:block">Zmodyfikowano</span>
            <span className="text-right">Akcje</span>
          </div>

          {isLoading && (
            <div className="space-y-0">
              {[1, 2, 3].map((i) => (
                <div key={i} className="border-b border-border px-4 py-3 last:border-b-0">
                  <div className="skeleton h-5 w-48" />
                </div>
              ))}
            </div>
          )}

          {!isLoading && folders.length === 0 && !isAdding && (
            <div className="px-4 py-10 text-center">
              <FolderOpen size={32} className="mx-auto text-ink-muted" />
              <p className="mt-3 text-sm text-ink-muted">Brak folderów</p>
              <button
                type="button"
                onClick={() => setIsAdding(true)}
                className="btn-primary mt-4"
              >
                <Plus size={18} />
                Nowy folder
              </button>
            </div>
          )}

          {!isLoading &&
            folders.map((folder) => (
              <div
                key={folder.id}
                className="data-table-row grid-cols-[minmax(0,1fr)_4rem] gap-x-4 sm:grid-cols-[minmax(0,1fr)_6.5rem_6.5rem_4rem] group"
              >
                <Link
                  href={`/folders/${folder.id}`}
                  className="flex min-w-0 items-center gap-2.5 text-ink hover:text-accent"
                >
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[6px] bg-accent-muted text-accent">
                    <FolderOpen size={16} />
                  </span>
                  <span className="truncate font-medium">{folder.name}</span>
                </Link>
                <span className="hidden whitespace-nowrap font-mono text-xs text-ink-muted sm:block">
                  {formatFolderDate(folder.createdAt)}
                </span>
                <span className="hidden whitespace-nowrap font-mono text-xs text-ink-muted sm:block">
                  {formatFolderDate(folder.updatedAt)}
                </span>
                <div className="flex justify-end">
                  <button
                    type="button"
                    onClick={(e) => handleDeleteFolder(folder.id, e)}
                    className="btn-ghost p-1.5 opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100"
                    title="Usuń folder"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            ))}
        </div>
        ) : (
        <div className="data-table" role="region" aria-label="Siatka folderów">
          {isLoading && (
            <div className="item-grid">
              {[1, 2, 3, 4, 5, 6].map((i) => (
                <div key={i} className="item-grid-card">
                  <div className="skeleton h-14 w-14 rounded-[8px]" />
                  <div className="skeleton h-4 w-20" />
                </div>
              ))}
            </div>
          )}

          {!isLoading && folders.length === 0 && !isAdding && (
            <div className="px-4 py-10 text-center">
              <FolderOpen size={32} className="mx-auto text-ink-muted" />
              <p className="mt-3 text-sm text-ink-muted">Brak folderów</p>
              <button
                type="button"
                onClick={() => setIsAdding(true)}
                className="btn-primary mt-4"
              >
                <Plus size={18} />
                Nowy folder
              </button>
            </div>
          )}

          {!isLoading && folders.length > 0 && (
            <div className="item-grid">
              {folders.map((folder) => (
                <div key={folder.id} className="item-grid-card group">
                  <button
                    type="button"
                    onClick={(e) => handleDeleteFolder(folder.id, e)}
                    className="btn-ghost absolute right-1 top-1 p-1 opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100"
                    title="Usuń folder"
                  >
                    <Trash2 size={14} />
                  </button>
                  <Link
                    href={`/folders/${folder.id}`}
                    className="flex w-full min-w-0 flex-col items-center gap-2"
                  >
                    <span className="item-grid-icon">
                      <FolderOpen size={28} />
                    </span>
                    <span className="item-grid-name" title={folder.name}>
                      {folder.name}
                    </span>
                    <span className="item-grid-meta">
                      {formatFolderDate(folder.updatedAt)}
                    </span>
                  </Link>
                </div>
              ))}
            </div>
          )}
        </div>
        )}
      </div>

      {showClearModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="clear-all-title"
        >
          <div className="w-full max-w-md rounded-[14px] border border-border bg-surface-raised p-5 shadow-md">
            <h3 id="clear-all-title" className="text-base font-semibold text-ink">
              Wyczyścić wszystkie dane?
            </h3>
            <p className="mt-2 text-sm text-ink-muted">
              Zostaną usunięte foldery, pliki, embeddingi i graf wiedzy. Wpisz{" "}
              <span className="font-mono font-semibold text-ink">WYCZYSC</span>, aby potwierdzić.
            </p>
            <input
              type="text"
              value={clearConfirmText}
              onChange={(e) => setClearConfirmText(e.target.value)}
              placeholder="WYCZYSC"
              className="input-field mt-4 w-full"
              autoFocus
            />
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setShowClearModal(false);
                  setClearConfirmText("");
                }}
                className="btn-secondary"
                disabled={isClearingAll}
              >
                Anuluj
              </button>
              <button
                type="button"
                onClick={handleClearAllData}
                className="btn-primary bg-error hover:bg-error/90"
                disabled={isClearingAll || clearConfirmText !== "WYCZYSC"}
              >
                {isClearingAll ? (
                  <Loader2 size={16} className="animate-spin" />
                ) : (
                  <Trash2 size={16} />
                )}
                <span>Wyczyść wszystko</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
