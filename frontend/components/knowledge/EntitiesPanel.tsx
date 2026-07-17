"use client";

import { useState, useEffect, useMemo, useCallback } from "react";
import { useRouter } from "next/navigation";
import { getAllEntities, KnowledgeEntity, renameEntity } from "@/lib/knowledge-api";
import { Check, Edit2, X, Search } from "lucide-react";
import { EmptyState } from "@/components/ui/EmptyState";
import { AnimatedItem } from "@/components/ui/AnimatedList";
import { motion, useReducedMotion } from "motion/react";

function photoUrl(photo: NonNullable<KnowledgeEntity["photos"]>[number]) {
  return `data:${photo.fileType};base64,${photo.imageBase64}`;
}

export function EntitiesPanel() {
  const router = useRouter();
  const [entities, setEntities] = useState<KnowledgeEntity[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const reduced = useReducedMotion();

  const visibleEntities = useMemo(() => {
    const phrase = search.trim().toLocaleLowerCase("pl");
    return entities.filter(
      (entity) =>
        (entity.photos?.length ?? 0) > 0 &&
        (!phrase || entity.displayName.toLocaleLowerCase("pl").includes(phrase))
    );
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
      <div className="search-field mb-5">
        <Search size={17} className="shrink-0 text-ink-muted" aria-hidden />
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Szukaj osoby"
          aria-label="Szukaj osoby"
        />
      </div>

      {loading && (
        <div className="entity-grid">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="skeleton aspect-square w-full rounded-[15px]" />
          ))}
        </div>
      )}

      {!loading && visibleEntities.length === 0 && (
        <EmptyState
          icon="👤"
          title={search ? "Nie znaleziono osoby" : "Brak rozpoznanych osób"}
          description={
            search
              ? "Spróbuj wyszukać inną nazwę."
              : "Dodaj zdjęcia do folderów, aby rozpocząć rozpoznawanie."
          }
        />
      )}

      {!loading && visibleEntities.length > 0 && (
        <ul className="entity-grid m-0 list-none p-0">
          {visibleEntities.map((entity, index) => {
            const photos = entity.photos ?? [];
            const cover = photos[0];
            const photoCount = photos.length;

            return (
              <AnimatedItem key={entity.id} index={index}>
                <li>
                  <motion.article
                    whileTap={reduced || editingId === entity.id ? undefined : { scale: 0.98 }}
                    className="entity-card cursor-pointer"
                    onClick={() => openEntityAlbum(entity.id)}
                    role="link"
                    tabIndex={0}
                    onKeyDown={(ev) => {
                      if (ev.key === "Enter" || ev.key === " ") {
                        ev.preventDefault();
                        openEntityAlbum(entity.id);
                      }
                    }}
                    aria-label={`Pokaż zdjęcia osoby ${entity.displayName}`}
                  >
                    <div className="relative aspect-square w-full overflow-hidden rounded-[15px] bg-soft">
                      {cover && (
                        <img
                          src={photoUrl(cover)}
                          alt=""
                          className="h-full w-full object-cover"
                          loading="lazy"
                        />
                      )}
                    </div>
                    <div className="px-0.5 pt-2">
                      {editingId === entity.id ? (
                        <div
                          className="flex items-center gap-1.5"
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
                          <button type="button" onClick={saveEdit} className="btn-primary !min-h-8 px-2">
                            <Check size={14} />
                          </button>
                          <button
                            type="button"
                            onClick={() => setEditingId(null)}
                            className="btn-secondary !min-h-8 px-2"
                          >
                            <X size={14} />
                          </button>
                        </div>
                      ) : (
                        <div className="flex items-start justify-between gap-1">
                          <div className="min-w-0">
                            <h4 className="truncate text-[15px] font-bold tracking-tight text-ink">
                              {entity.displayName}
                            </h4>
                            <p className="mt-0.5 text-[13px] text-ink-muted">
                              {photoCount}{" "}
                              {photoCount === 1
                                ? "zdjęcie"
                                : photoCount < 5
                                  ? "zdjęcia"
                                  : "zdjęć"}
                            </p>
                          </div>
                          <button
                            type="button"
                            onClick={(e) => startEdit(entity, e)}
                            className="shrink-0 p-1 text-ink-muted hover:text-ink"
                            aria-label={`Edytuj ${entity.displayName}`}
                          >
                            <Edit2 size={15} />
                          </button>
                        </div>
                      )}
                    </div>
                  </motion.article>
                </li>
              </AnimatedItem>
            );
          })}
        </ul>
      )}
    </section>
  );
}
