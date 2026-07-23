"use client";

import { useState, useEffect, useMemo, useCallback } from "react";
import { useRouter } from "next/navigation";
import { getAllEntities, KnowledgeEntity, renameEntity } from "@/lib/knowledge-api";
import { Check, ChevronRight, Edit2, UserRound, X } from "lucide-react";
import { EmptyState } from "@/components/ui/EmptyState";
import { SearchField } from "@/components/ui/SearchField";
import { AnimatedItem } from "@/components/ui/AnimatedList";
import { ViewModeToggle } from "@/components/ui/ViewModeToggle";
import { useViewMode } from "@/hooks/useViewMode";

function photoUrl(photo: NonNullable<KnowledgeEntity["photos"]>[number]) {
  return `data:${photo.fileType};base64,${photo.imageBase64}`;
}

function photoCountLabel(photoCount: number) {
  if (photoCount === 1) return "1 zdjęcie";
  if (photoCount < 5) return `${photoCount} zdjęcia`;
  return `${photoCount} zdjęć`;
}

export function EntitiesPanel() {
  const router = useRouter();
  const [entities, setEntities] = useState<KnowledgeEntity[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const { viewMode, setViewMode } = useViewMode("rag-people-view-mode", "grid");

  const visibleEntities = useMemo(() => {
    const phrase = search.trim().toLocaleLowerCase("pl");
    return entities
      .filter(
        (entity) =>
          (entity.photos?.length ?? 0) > 0 &&
          (!phrase || entity.displayName.toLocaleLowerCase("pl").includes(phrase))
      )
      .sort((a, b) => a.displayName.localeCompare(b.displayName, "pl"));
  }, [entities, search]);

  const loadEntities = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getAllEntities();
      setEntities(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadEntities();
  }, [loadEntities]);

  const startEdit = (entity: KnowledgeEntity, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingId(entity.id);
    setEditValue(entity.displayName);
  };

  const saveEdit = async () => {
    if (!editingId) return;
    try {
      await renameEntity(editingId, editValue);
      setEditingId(null);
      void loadEntities();
    } catch (e) {
      console.error(e);
    }
  };

  const openEntityAlbum = (entityId: string) => {
    if (editingId) return;
    router.push(`/knowledge/${entityId}`);
  };

  return (
    <section>
      <div className="mb-4 flex items-center gap-2">
        <SearchField
          value={search}
          onChange={setSearch}
          placeholder="Szukaj osoby"
          aria-label="Szukaj osoby"
          className="min-w-0 flex-1"
        />
        <ViewModeToggle value={viewMode} onChange={setViewMode} />
      </div>

      {loading && viewMode === "list" && (
        <div className="list-panel">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="list-row">
              <div className="skeleton h-11 w-11 !rounded-full !mb-0" />
              <div className="min-w-0 flex-1 space-y-2">
                <div className="skeleton h-4 w-1/3 !mb-0" />
                <div className="skeleton h-3 w-1/5 !mb-0" />
              </div>
            </div>
          ))}
        </div>
      )}

      {loading && viewMode === "grid" && (
        <div className="library-grid">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div key={i} className="skeleton aspect-square w-full !rounded-[var(--radius-lg)]" />
          ))}
        </div>
      )}

      {!loading && visibleEntities.length === 0 && (
        <EmptyState
          icon={<UserRound size={22} aria-hidden />}
          title={search ? "Nie znaleziono osoby" : "Brak rozpoznanych osób"}
          description={
            search
              ? "Spróbuj wyszukać inną nazwę."
              : "Dodaj zdjęcia do folderów i potwierdź tożsamości — osoby pojawią się tutaj."
          }
          action={
            !search ? (
              <button
                type="button"
                className="btn-primary"
                onClick={() => router.push("/folders")}
              >
                Przejdź do biblioteki
              </button>
            ) : undefined
          }
        />
      )}

      {!loading && visibleEntities.length > 0 && viewMode === "list" && (
        <ul className="list-panel m-0 list-none p-0">
          {visibleEntities.map((entity, index) => {
            const photos = entity.photos ?? [];
            const cover = photos[0];
            const photoCount = photos.length;

            return (
              <AnimatedItem key={entity.id} index={index}>
                <li>
                  <div
                    className="list-row group cursor-pointer"
                    onClick={() => openEntityAlbum(entity.id)}
                    onKeyDown={(ev) => {
                      if (ev.key === "Enter" || ev.key === " ") {
                        ev.preventDefault();
                        openEntityAlbum(entity.id);
                      }
                    }}
                    role="link"
                    tabIndex={0}
                    aria-label={`Pokaż zdjęcia osoby ${entity.displayName}`}
                  >
                    <span className="avatar h-11 w-11 shrink-0 overflow-hidden rounded-full border border-border bg-soft">
                      {cover ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          src={photoUrl(cover)}
                          alt=""
                          className="h-full w-full object-cover"
                          loading="lazy"
                        />
                      ) : (
                        <UserRound size={18} className="m-auto text-ink-muted" aria-hidden />
                      )}
                    </span>

                    {editingId === entity.id ? (
                      <div
                        className="flex min-w-0 flex-1 items-center gap-1.5"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <input
                          type="text"
                          value={editValue}
                          onChange={(ev) => setEditValue(ev.target.value)}
                          className="input-field !min-h-9 flex-1 !px-2.5 !text-sm"
                          autoFocus
                          onKeyDown={(ev) => {
                            if (ev.key === "Enter") void saveEdit();
                            if (ev.key === "Escape") setEditingId(null);
                          }}
                        />
                        <button
                          type="button"
                          onClick={saveEdit}
                          className="btn-primary !min-h-9 !px-2.5"
                          aria-label="Zapisz"
                        >
                          <Check size={14} />
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditingId(null)}
                          className="btn-secondary !min-h-9 !px-2.5"
                          aria-label="Anuluj"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    ) : (
                      <>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm font-semibold text-ink">
                            {entity.displayName}
                          </span>
                          <span className="mt-0.5 block text-xs text-ink-muted">
                            {photoCountLabel(photoCount)}
                          </span>
                        </span>
                        <button
                          type="button"
                          onClick={(e) => startEdit(entity, e)}
                          className="touch-target shrink-0 rounded-md text-ink-muted opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100 hover:text-ink"
                          aria-label={`Edytuj ${entity.displayName}`}
                        >
                          <Edit2 size={15} aria-hidden />
                        </button>
                        <ChevronRight
                          size={16}
                          className="shrink-0 text-ink-muted"
                          aria-hidden
                        />
                      </>
                    )}
                  </div>
                </li>
              </AnimatedItem>
            );
          })}
        </ul>
      )}

      {!loading && visibleEntities.length > 0 && viewMode === "grid" && (
        <ul className="library-grid m-0 list-none p-0">
          {visibleEntities.map((entity, index) => {
            const photos = entity.photos ?? [];
            const cover = photos[0];
            const photoCount = photos.length;

            return (
              <AnimatedItem key={entity.id} index={index}>
                <li className="group relative">
                  <div
                    className="library-grid-card cursor-pointer"
                    onClick={() => openEntityAlbum(entity.id)}
                    onKeyDown={(ev) => {
                      if (ev.key === "Enter" || ev.key === " ") {
                        ev.preventDefault();
                        openEntityAlbum(entity.id);
                      }
                    }}
                    role="link"
                    tabIndex={0}
                    aria-label={`Pokaż zdjęcia osoby ${entity.displayName}`}
                  >
                    <span className="library-grid-thumb !bg-soft overflow-hidden p-0">
                      {cover ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          src={photoUrl(cover)}
                          alt=""
                          className="h-full w-full object-cover"
                          loading="lazy"
                        />
                      ) : (
                        <UserRound size={32} className="text-ink-muted" aria-hidden />
                      )}
                    </span>
                    {editingId === entity.id ? (
                      <div
                        className="mt-2 flex items-center gap-1"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <input
                          type="text"
                          value={editValue}
                          onChange={(ev) => setEditValue(ev.target.value)}
                          className="input-field !min-h-8 flex-1 !px-2 !text-sm"
                          autoFocus
                          onKeyDown={(ev) => {
                            if (ev.key === "Enter") void saveEdit();
                            if (ev.key === "Escape") setEditingId(null);
                          }}
                        />
                        <button
                          type="button"
                          onClick={saveEdit}
                          className="btn-primary !min-h-8 !px-2"
                          aria-label="Zapisz"
                        >
                          <Check size={14} />
                        </button>
                      </div>
                    ) : (
                      <>
                        <span className="mt-2 block truncate text-sm font-semibold text-ink">
                          {entity.displayName}
                        </span>
                        <span className="mt-0.5 block truncate text-xs text-ink-muted">
                          {photoCountLabel(photoCount)}
                        </span>
                      </>
                    )}
                  </div>
                  {editingId !== entity.id && (
                    <button
                      type="button"
                      onClick={(e) => startEdit(entity, e)}
                      className="absolute right-1.5 top-1.5 flex h-8 w-8 items-center justify-center rounded-full border border-border bg-surface-raised text-ink-muted opacity-0 shadow-sm transition-opacity group-hover:opacity-100 group-focus-within:opacity-100 hover:text-ink"
                      aria-label={`Edytuj ${entity.displayName}`}
                    >
                      <Edit2 size={14} aria-hidden />
                    </button>
                  )}
                </li>
              </AnimatedItem>
            );
          })}
        </ul>
      )}
    </section>
  );
}
