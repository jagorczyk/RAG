import { useState, useEffect } from "react";
import { getAllEntities, KnowledgeEntity, renameEntity } from "@/lib/knowledge-api";
import { Check, Edit2, ImageOff, X } from "lucide-react";

const COLLAGE_SLOTS = 4;

function photoUrl(photo: NonNullable<KnowledgeEntity["photos"]>[number]) {
  return `data:${photo.fileType};base64,${photo.imageBase64}`;
}

function entityTypeLabel(type: string) {
  return type === "PERSON" ? "Osoba" : "Zwierzę";
}

function EntityPhotoCollage({ photos }: { photos: KnowledgeEntity["photos"] }) {
  const items = photos?.slice(0, COLLAGE_SLOTS) ?? [];
  const hasPhotos = items.length > 0;

  return (
    <div
      className="entity-collage mt-3 grid aspect-square w-full grid-cols-2 grid-rows-2 gap-px overflow-hidden rounded-[8px] border border-border bg-border"
      aria-label={hasPhotos ? `Kolaż ${items.length} zdjęć` : "Brak zdjęć"}
    >
      {Array.from({ length: COLLAGE_SLOTS }, (_, index) => {
        const photo = items[index];

        if (!hasPhotos) {
          if (index !== 0) {
            return null;
          }
          return (
            <div
              key="empty"
              className="col-span-2 row-span-2 flex flex-col items-center justify-center gap-1.5 bg-surface text-ink-muted"
            >
              <ImageOff size={18} strokeWidth={1.75} aria-hidden />
              <span className="text-xs">Brak zdjęć</span>
            </div>
          );
        }

        return (
          <div
            key={photo?.path ?? `slot-${index}`}
            className="relative min-h-0 min-w-0 bg-surface-raised"
          >
            {photo ? (
              <img
                src={photoUrl(photo)}
                alt=""
                className="absolute inset-0 h-full w-full object-cover"
                loading="lazy"
              />
            ) : (
              <div className="absolute inset-0 bg-surface" aria-hidden />
            )}
          </div>
        );
      })}
    </div>
  );
}

export function EntitiesPanel() {
  const [entities, setEntities] = useState<KnowledgeEntity[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");

  useEffect(() => {
    loadEntities();
  }, []);

  const loadEntities = async () => {
    try {
      const data = await getAllEntities();
      setEntities(data);
    } catch (e) {
      console.error(e);
    }
  };

  const startEdit = (entity: KnowledgeEntity) => {
    setEditingId(entity.id);
    setEditValue(entity.displayName);
  };

  const saveEdit = async () => {
    if (!editingId) return;
    try {
      await renameEntity(editingId, editValue);
      setEditingId(null);
      loadEntities();
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <section className="rounded-[10px] border border-border bg-surface p-4 sm:p-5">
      <div className="mb-4 flex items-end justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold text-ink text-balance">
            Rozpoznane postacie i zwierzęta
          </h3>
          <p className="mt-0.5 text-sm text-ink-muted">
            {entities.length > 0
              ? `${entities.length} ${entities.length === 1 ? "wpis" : entities.length < 5 ? "wpisy" : "wpisów"}`
              : "Encje wykryte na zdjęciach"}
          </p>
        </div>
      </div>

      {entities.length === 0 ? (
        <p className="text-sm text-ink-muted">Brak encji w bazie.</p>
      ) : (
        <ul className="entity-grid m-0 list-none p-0">
          {entities.map((entity) => {
            const photoCount = entity.photos?.length ?? 0;

            return (
              <li key={entity.id}>
                <article className="entity-card flex h-full flex-col rounded-[10px] border border-border bg-surface-raised p-3">
                  {editingId === entity.id ? (
                    <div className="flex items-center gap-2">
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
                            {photoCount > 0 && (
                              <span className="text-ink-muted">
                                {" · "}
                                {photoCount}{" "}
                                {photoCount === 1
                                  ? "zdjęcie"
                                  : photoCount < 5
                                    ? "zdjęcia"
                                    : "zdjęć"}
                              </span>
                            )}
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={() => startEdit(entity)}
                          className="btn-ghost h-8 w-8 shrink-0 p-0 text-ink-muted hover:text-ink"
                          aria-label={`Edytuj ${entity.displayName}`}
                        >
                          <Edit2 size={15} />
                        </button>
                      </div>
                      <EntityPhotoCollage photos={entity.photos} />
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
