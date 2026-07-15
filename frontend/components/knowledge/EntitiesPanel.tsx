import { useState, useEffect, useMemo, useCallback } from "react";
import { useRouter } from "next/navigation";
import { getAllEntities, KnowledgeEntity, renameEntity } from "@/lib/knowledge-api";
import { Check, Edit2, X } from "lucide-react";

const MAX_COLLAGE_PHOTOS = 4;

function photoUrl(photo: NonNullable<KnowledgeEntity["photos"]>[number]) {
  return `data:${photo.fileType};base64,${photo.imageBase64}`;
}

function entityTypeLabel(type: string) {
  return type === "PERSON" ? "Osoba" : "Zwierzę";
}

function collageGridClass(count: number): string {
  if (count === 1) {
    return "grid-cols-1 grid-rows-1";
  }
  if (count === 2) {
    return "grid-cols-2 grid-rows-1";
  }
  return "grid-cols-2 grid-rows-2";
}

function collageCellClass(count: number, index: number): string {
  if (count === 3 && index === 2) {
    return "col-span-2";
  }
  return "";
}

function EntityPhotoCollage({ photos }: { photos: NonNullable<KnowledgeEntity["photos"]> }) {
  const items = photos.slice(0, MAX_COLLAGE_PHOTOS);

  return (
    <div
      className={`entity-collage mt-3 grid aspect-square w-full gap-px overflow-hidden rounded-[8px] border border-border bg-border ${collageGridClass(items.length)}`}
      aria-label={`Kolaż ${items.length} zdjęć`}
    >
      {items.map((photo, index) => (
        <div
          key={photo.path}
          className={`relative min-h-0 min-w-0 bg-surface-raised ${collageCellClass(items.length, index)}`}
        >
          <img
            src={photoUrl(photo)}
            alt=""
            className="absolute inset-0 h-full w-full object-cover"
            loading="lazy"
          />
        </div>
      ))}
    </div>
  );
}

export function EntitiesPanel() {
  const router = useRouter();
  const [entities, setEntities] = useState<KnowledgeEntity[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");

  const visibleEntities = useMemo(
    () => entities.filter((entity) => (entity.photos?.length ?? 0) > 0),
    [entities]
  );

  const loadEntities = useCallback(async () => {
    try {
      const data = await getAllEntities();
      setEntities(data);
    } catch (e) {
      console.error(e);
    }
  }, []);

  useEffect(() => {
    // Data loading is the external synchronization this effect is responsible for.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadEntities();
  }, [loadEntities]);

  const startEdit = (entity: KnowledgeEntity) => {
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
    router.push(`/knowledge/${entityId}`);
  };

  return (
    <section className="rounded-[10px] border border-border bg-surface p-4 sm:p-5">
      <div className="mb-4 flex items-end justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold text-ink text-balance">
            Rozpoznane postacie i zwierzęta
          </h3>
          <p className="mt-0.5 text-sm text-ink-muted">
            {visibleEntities.length > 0
              ? `${visibleEntities.length} ${visibleEntities.length === 1 ? "wpis" : visibleEntities.length < 5 ? "wpisy" : "wpisów"} · kliknij osobę, aby zobaczyć wszystkie zdjęcia`
              : "Encje wykryte na zdjęciach"}
          </p>
        </div>
      </div>

      {visibleEntities.length === 0 ? (
        <p className="text-sm text-ink-muted">Brak encji ze zdjęciami.</p>
      ) : (
        <ul className="entity-grid m-0 list-none p-0">
          {visibleEntities.map((entity) => {
            const photos = entity.photos ?? [];
            const photoCount = photos.length;

            return (
              <li key={entity.id}>
                <article
                  className={`entity-card flex h-full flex-col rounded-[10px] border border-border bg-surface-raised p-3 transition-colors ${
                    editingId === entity.id
                      ? ""
                      : "cursor-pointer hover:border-border-strong hover:bg-surface"
                  }`}
                  onClick={() => {
                    if (editingId !== entity.id) {
                      openEntityAlbum(entity.id);
                    }
                  }}
                  onKeyDown={(ev) => {
                    if (editingId === entity.id) return;
                    if (ev.key === "Enter" || ev.key === " ") {
                      ev.preventDefault();
                      openEntityAlbum(entity.id);
                    }
                  }}
                  role={editingId === entity.id ? undefined : "link"}
                  tabIndex={editingId === entity.id ? undefined : 0}
                  aria-label={
                    editingId === entity.id
                      ? undefined
                      : `Otwórz album: ${entity.displayName}`
                  }
                >
                  {editingId === entity.id ? (
                    <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                      <input
                        type="text"
                        value={editValue}
                        onChange={(ev) => setEditValue(ev.target.value)}
                        className="w-full flex-1 rounded-[6px] border border-border bg-surface px-2 py-1.5 text-sm text-ink outline-none focus:border-border-strong"
                        autoFocus
                        onKeyDown={(ev) => {
                          if (ev.key === "Enter") saveEdit();
                          if (ev.key === "Escape") setEditingId(null);
                        }}
                      />
                      <button
                        type="button"
                        onClick={saveEdit}
                        className="btn-primary h-8 w-8 shrink-0 p-0"
                        aria-label="Zapisz nazwę"
                      >
                        <Check size={14} />
                      </button>
                      <button
                        type="button"
                        onClick={() => setEditingId(null)}
                        className="btn-secondary h-8 w-8 shrink-0 p-0 text-error"
                        aria-label="Anuluj edycję"
                      >
                        <X size={14} />
                      </button>
                    </div>
                  ) : (
                    <>
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0 flex-1">
                          <h4 className="truncate text-sm font-medium text-ink">
                            {entity.displayName}
                          </h4>
                          <p className="mt-0.5 text-xs text-ink-muted">
                            {entityTypeLabel(entity.type)}
                            <span className="text-ink-muted">
                              {" · "}
                              {photoCount}{" "}
                              {photoCount === 1
                                ? "zdjęcie"
                                : photoCount < 5
                                  ? "zdjęcia"
                                  : "zdjęć"}
                            </span>
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            startEdit(entity);
                          }}
                          className="btn-ghost h-8 w-8 shrink-0 p-0 text-ink-muted hover:text-ink"
                          aria-label={`Edytuj ${entity.displayName}`}
                        >
                          <Edit2 size={15} />
                        </button>
                      </div>
                      <EntityPhotoCollage photos={photos} />
                    </>
                  )}
                </article>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
