"use client";

import { useState, useEffect, useMemo } from "react";
import Link from "next/link";
import {
  FolderOpen,
  Plus,
  Trash2,
  FolderPlus,
  Loader2,
  RefreshCw,
  MoreHorizontal,
  Settings2,
} from "lucide-react";
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
import {
  DEFAULT_UPLOAD_CONCURRENCY,
  mapWithConcurrency,
} from "@/lib/concurrency";
import { SearchField } from "@/components/ui/SearchField";
import { SectionTitle } from "@/components/ui/SectionTitle";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { SheetAction } from "@/components/ui/SheetAction";
import { Button } from "@/components/ui/Button";
import { IconButton } from "@/components/ui/IconButton";
import { AnimatedItem } from "@/components/ui/AnimatedList";
import { FadeModal } from "@/components/ui/FadeModal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ViewModeToggle } from "@/components/ui/ViewModeToggle";
import { useViewMode } from "@/hooks/useViewMode";

type FolderSort = "recent" | "asc" | "desc";

function formatFolderDate(value: string) {
  return value ? new Date(value).toLocaleDateString("pl-PL") : "Folder dokumentów";
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
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<FolderSort>("recent");
  const [sorting, setSorting] = useState(false);
  const [selected, setSelected] = useState<FolderType | null>(null);
  const [toolsOpen, setToolsOpen] = useState(false);
  const { viewMode, setViewMode } = useViewMode("rag-library-view-mode");

  useEffect(() => {
    getFolders()
      .then(setFolders)
      .finally(() => setIsLoading(false));
  }, []);

  const needle = query.trim().toLocaleLowerCase("pl-PL");
  const visibleFolders = useMemo(() => {
    return [...folders]
      .filter((folder) => !needle || folder.name.toLocaleLowerCase("pl-PL").includes(needle))
      .sort((a, b) => {
        if (sort === "asc") return a.name.localeCompare(b.name, "pl");
        if (sort === "desc") return b.name.localeCompare(a.name, "pl");
        return (
          (b.updatedAt || "").localeCompare(a.updatedAt || "") ||
          a.name.localeCompare(b.name, "pl")
        );
      });
  }, [folders, needle, sort]);

  const handleCreateFolder = async (e?: React.FormEvent) => {
    e?.preventDefault();
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
    setToolsOpen(false);
    try {
      const firstFile = e.target.files[0];
      const relativePath = (firstFile as unknown as { webkitRelativePath: string })
        .webkitRelativePath;
      const folderName = relativePath.split("/")[0] || "Nowy folder";
      const newFolder = await createFolder(folderName);
      setFolders((prev) => [...prev, newFolder]);
      const filesToUpload = Array.from(e.target.files).filter(
        (file) => !file.name.startsWith(".")
      );
      await mapWithConcurrency(
        filesToUpload,
        DEFAULT_UPLOAD_CONCURRENCY,
        async (file) => {
          await uploadFileToFolder(newFolder.id, file);
        }
      );
    } catch (error) {
      console.error("Folder upload failed", error);
      alert("Wystąpił błąd podczas wgrywania folderu.");
    } finally {
      setIsUploadingFolder(false);
      e.target.value = "";
    }
  };

  const handleDeleteFolder = async (folder: FolderType) => {
    setSelected(null);
    if (!confirm(`Usunąć folder „${folder.name}” i jego pliki?`)) return;
    try {
      await deleteFolder(folder.id);
      setFolders((prev) => prev.filter((f) => f.id !== folder.id));
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
    setToolsOpen(false);
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
      <PageHeader
        title="Biblioteka"
        subtitle="Twoje foldery, zdjęcia i dokumenty"
        border={false}
        action={
          <>
            <IconButton label="Narzędzia" onClick={() => setToolsOpen(true)}>
              <Settings2 size={18} />
            </IconButton>
            <IconButton label="Nowy folder" onClick={() => setIsAdding(true)}>
              <Plus size={20} />
            </IconButton>
          </>
        }
      />

      <div className="page-body max-w-5xl !pt-1">
        <div className="flex items-center gap-2">
          <SearchField
            value={query}
            onChange={setQuery}
            placeholder="Szukaj folderów"
            className="min-w-0 flex-1"
          />
          <ViewModeToggle value={viewMode} onChange={setViewMode} />
        </div>

        {reanalysis && (
          <div className="status-banner status-banner-info mt-4" role="status">
            <RefreshCw
              size={18}
              className={reanalysis.status === "RUNNING" ? "animate-spin" : ""}
              aria-hidden
            />
            <span>
              Analiza kontekstu: {reanalysis.completed}/{reanalysis.total}
              {reanalysis.failed > 0 ? `, błędy: ${reanalysis.failed}` : ""}
            </span>
          </div>
        )}
        {isUploadingFolder && (
          <div className="status-banner mt-4" role="status">
            <Loader2 size={18} className="animate-spin" aria-hidden />
            <span>Wgrywanie folderu…</span>
          </div>
        )}

        <SectionTitle
          className="!mt-5"
          action={
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => setSorting(true)}
                className="text-sm font-bold text-ink"
                aria-label="Sortuj foldery"
              >
                Sortuj
              </button>
              <button
                type="button"
                onClick={() => setIsAdding(true)}
                className="text-sm font-bold text-ink"
              >
                Dodaj
              </button>
            </div>
          }
        >
          Foldery
        </SectionTitle>

        {isLoading && <Loading label="Ładowanie folderów" />}

        {!isLoading && visibleFolders.length === 0 && (
          <EmptyState
            icon={<FolderOpen size={22} aria-hidden />}
            title={needle ? "Brak wyników" : "Dodaj pierwszy folder"}
            description={
              needle
                ? "Spróbuj innej nazwy folderu."
                : "Utwórz folder albo wgraj katalog ze zdjęciami — potem możesz o nie pytać w rozmowie."
            }
            action={
              !needle ? (
                <div className="flex flex-wrap items-center justify-center gap-2">
                  <button type="button" className="btn-primary" onClick={() => setIsAdding(true)}>
                    <Plus size={18} /> Nowy folder
                  </button>
                  <button type="button" className="btn-secondary" onClick={() => setToolsOpen(true)}>
                    <FolderPlus size={18} /> Wgraj folder
                  </button>
                </div>
              ) : undefined
            }
          />
        )}

        {!isLoading && visibleFolders.length > 0 && viewMode === "list" && (
          <div className="list-panel">
            {visibleFolders.map((folder, index) => (
              <AnimatedItem key={folder.id} index={index}>
                <div className="list-row group">
                  <Link
                    href={`/folders/${folder.id}`}
                    className="flex min-h-[var(--touch-min)] min-w-0 flex-1 items-center gap-3"
                  >
                    <span className="list-row-icon">
                      <FolderOpen size={18} />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-bold text-ink">
                        {folder.name}
                      </span>
                      <span className="mt-0.5 block text-[0.6875rem] text-ink-muted">
                        {formatFolderDate(folder.updatedAt)}
                      </span>
                    </span>
                  </Link>
                  <button
                    type="button"
                    className="touch-target shrink-0 text-ink-muted"
                    aria-label={`Opcje folderu ${folder.name}`}
                    onClick={() => setSelected(folder)}
                  >
                    <MoreHorizontal size={20} aria-hidden />
                  </button>
                </div>
              </AnimatedItem>
            ))}
          </div>
        )}

        {!isLoading && visibleFolders.length > 0 && viewMode === "grid" && (
          <ul className="library-grid m-0 list-none p-0">
            {visibleFolders.map((folder, index) => (
              <AnimatedItem key={folder.id} index={index}>
                <li className="group relative">
                  <Link
                    href={`/folders/${folder.id}`}
                    className="library-grid-card"
                  >
                    <span className="library-grid-thumb">
                      <FolderOpen size={36} strokeWidth={1.6} aria-hidden />
                    </span>
                    <span className="mt-2 block truncate text-sm font-semibold text-ink">
                      {folder.name}
                    </span>
                    <span className="mt-0.5 block truncate text-xs text-ink-muted">
                      {formatFolderDate(folder.updatedAt)}
                    </span>
                  </Link>
                  <button
                    type="button"
                    className="absolute right-1.5 top-1.5 flex h-8 w-8 items-center justify-center rounded-full border border-border bg-surface-raised text-ink-muted opacity-0 shadow-sm transition-opacity group-hover:opacity-100 group-focus-within:opacity-100 hover:text-ink"
                    aria-label={`Opcje folderu ${folder.name}`}
                    onClick={() => setSelected(folder)}
                  >
                    <MoreHorizontal size={16} aria-hidden />
                  </button>
                </li>
              </AnimatedItem>
            ))}
          </ul>
        )}
      </div>

      <BottomSheet
        open={isAdding}
        onClose={() => setIsAdding(false)}
        title="Nowy folder"
        description="Nadaj nazwę swojej kolekcji."
        footer={
          <>
            <Button
              label="Anuluj"
              secondary
              onClick={() => setIsAdding(false)}
              className="min-w-[7rem]"
            />
            <Button
              label="Utwórz"
              onClick={() => void handleCreateFolder()}
              disabled={!newFolderName.trim()}
              className="min-w-[7rem]"
            />
          </>
        }
      >
        <input
          type="text"
          value={newFolderName}
          onChange={(e) => setNewFolderName(e.target.value)}
          placeholder="Np. Umowy 2026"
          autoFocus
          className="input-field"
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              void handleCreateFolder();
            }
          }}
        />
      </BottomSheet>

      <BottomSheet
        open={!!selected}
        onClose={() => setSelected(null)}
        title="Opcje folderu"
        description={selected?.name}
        flush
      >
        <SheetAction
          icon={<Trash2 size={18} />}
          label="Usuń folder"
          destructive
          onClick={() => selected && handleDeleteFolder(selected)}
        />
      </BottomSheet>

      <BottomSheet
        open={sorting}
        onClose={() => setSorting(false)}
        title="Sortuj foldery"
        flush
      >
        {(
          [
            { key: "recent", label: "Ostatnio zmieniane" },
            { key: "asc", label: "Nazwa: A–Z" },
            { key: "desc", label: "Nazwa: Z–A" },
          ] as { key: FolderSort; label: string }[]
        ).map((option) => (
          <button
            key={option.key}
            type="button"
            onClick={() => {
              setSort(option.key);
              setSorting(false);
            }}
            className={`modal-option ${option.key === sort ? "is-active" : ""}`}
          >
            {option.label}
            {option.key === sort ? <span className="modal-option-check">✓</span> : null}
          </button>
        ))}
      </BottomSheet>

      <BottomSheet open={toolsOpen} onClose={() => setToolsOpen(false)} title="Narzędzia" flush>
        <label className="sheet-action cursor-pointer">
          <span className="sheet-action-icon" aria-hidden>
            <FolderPlus size={18} />
          </span>
          <span className="sheet-action-label">Wgraj folder</span>
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
        <SheetAction
          icon={<RefreshCw size={18} />}
          label="Analizuj kontekst zdjęć"
          onClick={handleReanalysis}
        />
        <SheetAction
          icon={<Trash2 size={18} />}
          label="Wyczyść wszystkie dane"
          destructive
          onClick={() => {
            setToolsOpen(false);
            setShowClearModal(true);
          }}
        />
      </BottomSheet>

      <FadeModal
        open={showClearModal}
        onClose={() => {
          if (!isClearingAll) {
            setShowClearModal(false);
            setClearConfirmText("");
          }
        }}
        variant="card"
        title="Wyczyścić wszystkie dane?"
        description="Zostaną usunięte osoby, rozmowy, wiadomości, foldery i pliki. Wpisz WYCZYSC, aby potwierdzić."
        showClose={!isClearingAll}
        footer={
          <>
            <Button
              label="Anuluj"
              secondary
              disabled={isClearingAll}
              onClick={() => {
                setShowClearModal(false);
                setClearConfirmText("");
              }}
              className="min-w-[7rem]"
              />
            <Button
              label={isClearingAll ? "Czyszczenie…" : "Wyczyść wszystko"}
              disabled={isClearingAll || clearConfirmText !== "WYCZYSC"}
              onClick={handleClearAllData}
              className="min-w-[9rem] !border-error !bg-error hover:!bg-error/90"
            />
          </>
        }
      >
        <input
          type="text"
          value={clearConfirmText}
          onChange={(e) => setClearConfirmText(e.target.value)}
          placeholder="WYCZYSC"
          className="input-field"
          autoFocus
          aria-label="Potwierdzenie usunięcia"
        />
      </FadeModal>
    </div>
  );
}
