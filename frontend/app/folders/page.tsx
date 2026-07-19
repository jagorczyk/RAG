"use client";

import { useState, useEffect, useMemo } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  FolderOpen,
  Plus,
  Trash2,
  FolderPlus,
  Loader2,
  RefreshCw,
  Sparkles,
  ArrowUp,
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
  getChats,
  createChat,
  type Chat,
} from "@/lib/api";
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

type FolderSort = "recent" | "asc" | "desc";

function formatFolderDate(value: string) {
  return value ? new Date(value).toLocaleDateString("pl-PL") : "Folder dokumentów";
}

export default function FoldersPage() {
  const router = useRouter();
  const [folders, setFolders] = useState<FolderType[]>([]);
  const [chats, setChats] = useState<Chat[]>([]);
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
  const [creatingChat, setCreatingChat] = useState(false);

  useEffect(() => {
    Promise.all([getFolders(), getChats().catch(() => [] as Chat[])])
      .then(([f, c]) => {
        setFolders(f);
        setChats(c);
      })
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

  const visibleChats = useMemo(() => {
    return chats
      .filter((chat) => !needle || chat.title.toLocaleLowerCase("pl-PL").includes(needle))
      .slice(0, 3);
  }, [chats, needle]);

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

  const handleNewChat = async () => {
    setCreatingChat(true);
    try {
      const chat = await createChat();
      router.push(`/chat/${chat.id}`);
    } catch {
      alert("Nie udało się utworzyć rozmowy.");
    } finally {
      setCreatingChat(false);
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

      <div className="page-body max-w-3xl !pt-1">
        <SearchField
          value={query}
          onChange={setQuery}
          placeholder="Szukaj folderów i rozmów"
        />

        <button
          type="button"
          onClick={handleNewChat}
          disabled={creatingChat}
          className="ask-card mt-3 w-full text-left"
        >
          <span className="ask-card-icon">
            {creatingChat ? (
              <Loader2 size={18} className="animate-spin" aria-hidden />
            ) : (
              <Sparkles size={18} aria-hidden />
            )}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-base font-extrabold text-ink">
              O co chcesz zapytać?
            </span>
            <span className="mt-0.5 block text-[13px] text-ink-muted">
              Przeszukaj swoją bazę wiedzy
            </span>
          </span>
          <ArrowUp size={18} className="shrink-0 text-ink" aria-hidden />
        </button>

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
            icon="📁"
            title={needle ? "Brak wyników" : "Dodaj pierwszy folder"}
            description={
              needle
                ? "Spróbuj innej nazwy folderu lub rozmowy."
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

        {!isLoading && visibleFolders.length > 0 && (
          <div className="list-panel">
            {visibleFolders.map((folder, index) => (
              <AnimatedItem key={folder.id} index={index}>
                <div className="list-row group">
                  <Link
                    href={`/folders/${folder.id}`}
                    className="flex min-w-0 flex-1 items-center gap-3"
                  >
                    <span className="list-row-icon">
                      <FolderOpen size={18} />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-base font-bold text-ink">
                        {folder.name}
                      </span>
                      <span className="mt-0.5 block text-xs text-ink-muted">
                        {formatFolderDate(folder.updatedAt)}
                      </span>
                    </span>
                  </Link>
                  <button
                    type="button"
                    className="p-1 text-ink-muted"
                    aria-label={`Opcje folderu ${folder.name}`}
                    onClick={() => setSelected(folder)}
                  >
                    <MoreHorizontal size={20} />
                  </button>
                </div>
              </AnimatedItem>
            ))}
          </div>
        )}

        <SectionTitle
          action={
            <button
              type="button"
              onClick={handleNewChat}
              disabled={creatingChat}
              className="text-sm font-bold text-ink"
            >
              Nowa
            </button>
          }
        >
          Ostatnie rozmowy
        </SectionTitle>
        {visibleChats.length === 0 ? (
          <div className="list-panel px-4 py-5">
            <p className="text-sm text-ink-muted">
              {needle
                ? "Brak rozmów pasujących do wyszukiwania."
                : "Nie masz jeszcze rozmów. Zacznij od pytania o bibliotekę."}
            </p>
            {!needle && (
              <button
                type="button"
                className="btn-secondary mt-3 !min-h-10"
                onClick={handleNewChat}
                disabled={creatingChat}
              >
                <Plus size={16} /> Nowa rozmowa
              </button>
            )}
          </div>
        ) : (
          <div className="list-panel">
            {visibleChats.map((chat, index) => (
              <AnimatedItem key={chat.id} index={index}>
                <Link href={`/chat/${chat.id}`} className="list-row">
                  <span className="list-row-icon">
                    <Sparkles size={16} aria-hidden />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-bold text-ink">{chat.title}</span>
                    <span className="mt-0.5 block text-xs text-ink-muted">
                      {chat.updatedAt
                        ? new Date(chat.updatedAt).toLocaleDateString("pl-PL")
                        : "Rozmowa"}
                    </span>
                  </span>
                </Link>
              </AnimatedItem>
            ))}
          </div>
        )}
      </div>

      <BottomSheet
        open={isAdding}
        onClose={() => setIsAdding(false)}
        title="Nowy folder"
        description="Nadaj nazwę swojej kolekcji."
      >
        <form onSubmit={handleCreateFolder}>
          <input
            type="text"
            value={newFolderName}
            onChange={(e) => setNewFolderName(e.target.value)}
            placeholder="Np. Umowy 2026"
            autoFocus
            className="input-field mb-3"
          />
          <div className="flex gap-2.5">
            <Button label="Anuluj" secondary onClick={() => setIsAdding(false)} />
            <Button
              label="Utwórz"
              type="submit"
              disabled={!newFolderName.trim()}
            />
          </div>
        </form>
      </BottomSheet>

      <BottomSheet
        open={!!selected}
        onClose={() => setSelected(null)}
        title="Opcje folderu"
        description={selected?.name}
      >
        <SheetAction
          icon={<Trash2 size={20} />}
          label="Usuń folder"
          destructive
          onClick={() => selected && handleDeleteFolder(selected)}
        />
      </BottomSheet>

      <BottomSheet open={sorting} onClose={() => setSorting(false)} title="Sortuj foldery">
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
            className="flex min-h-12 w-full items-center justify-between text-left text-base text-ink"
          >
            {option.label}
            {option.key === sort && <span className="font-bold">✓</span>}
          </button>
        ))}
      </BottomSheet>

      <BottomSheet open={toolsOpen} onClose={() => setToolsOpen(false)} title="Narzędzia">
        <label className="flex min-h-[3.25rem] w-full cursor-pointer items-center gap-3.5 text-left font-bold">
          <FolderPlus size={20} />
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
        <SheetAction
          icon={<RefreshCw size={20} />}
          label="Analizuj kontekst zdjęć"
          onClick={handleReanalysis}
        />
        <SheetAction
          icon={<Trash2 size={20} />}
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
      >
        <h3 className="text-lg font-extrabold text-ink">Wyczyścić wszystkie dane?</h3>
        <p className="mt-2 text-sm text-ink-muted">
          Zostaną usunięte foldery, pliki, embeddingi i graf wiedzy. Wpisz{" "}
          <span className="font-semibold text-ink">WYCZYSC</span>, aby potwierdzić.
        </p>
        <input
          type="text"
          value={clearConfirmText}
          onChange={(e) => setClearConfirmText(e.target.value)}
          placeholder="WYCZYSC"
          className="input-field mt-4"
          autoFocus
        />
        <div className="mt-4 flex gap-2">
          <Button
            label="Anuluj"
            secondary
            disabled={isClearingAll}
            onClick={() => {
              setShowClearModal(false);
              setClearConfirmText("");
            }}
          />
          <Button
            label={isClearingAll ? "Czyszczenie…" : "Wyczyść wszystko"}
            disabled={isClearingAll || clearConfirmText !== "WYCZYSC"}
            onClick={handleClearAllData}
            className="!border-error !bg-error hover:!bg-error/90"
          />
        </div>
      </FadeModal>
    </div>
  );
}
