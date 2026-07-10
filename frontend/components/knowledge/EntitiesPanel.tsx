import { useState, useEffect } from "react";
import { getAllEntities, KnowledgeEntity, renameEntity } from "@/lib/knowledge-api";
import { Check, Edit2, X } from "lucide-react";

function photoUrl(photo: NonNullable<KnowledgeEntity["photos"]>[number]) {
  return `data:${photo.fileType};base64,${photo.imageBase64}`;
}

function EntityPhotoCollage({ photos }: { photos: KnowledgeEntity["photos"] }) {
  if (!photos || photos.length === 0) {
    return (
      <div className="mt-3 flex h-20 items-center justify-center rounded-[6px] border border-dashed border-border bg-surface text-xs text-ink-muted">
        Brak zdjęć
      </div>
    );
  }

  const count = Math.min(photos.length, 4);

  return (
    <div
      className={`mt-3 grid gap-1 overflow-hidden rounded-[6px] ${
        count === 1 ? "grid-cols-1" : "grid-cols-2"
      }`}
    >
      {photos.slice(0, 4).map((photo) => (
        <div
          key={photo.path}
          className={`relative overflow-hidden bg-surface ${
            count === 1 ? "aspect-[16/10]" : "aspect-square"
          }`}
        >
          <img
            src={photoUrl(photo)}
            alt={photo.fileName}
            className="h-full w-full object-cover"
            loading="lazy"
          />
        </div>
      ))}
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
    <div className="rounded-[10px] border border-border bg-surface p-4">
      <h3 className="mb-4 text-lg font-semibold text-ink">Rozpoznane postacie i zwierzęta</h3>
      {entities.length === 0 ? (
        <p className="text-sm text-ink-muted">Brak encji w bazie.</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-3">
          {entities.map((entity) => (
            <div
              key={entity.id}
              className="rounded-[8px] border border-border bg-surface-raised p-3"
            >
              {editingId === entity.id ? (
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={editValue}
                    onChange={(ev) => setEditValue(ev.target.value)}
                    className="w-full flex-1 rounded-[6px] border border-border bg-surface px-2 py-1 text-sm text-ink outline-none"
                    autoFocus
                  />
                  <button onClick={saveEdit} className="btn-primary h-auto p-1 text-xs">
                    <Check size={14} />
                  </button>
                  <button
                    onClick={() => setEditingId(null)}
                    className="btn-secondary h-auto p-1 text-xs text-error"
                  >
                    <X size={14} />
                  </button>
                </div>
              ) : (
                <>
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-ink">
                        {entity.displayName}
                      </p>
                      <p className="text-xs text-ink-muted">
                        Typ: {entity.type === "PERSON" ? "Osoba" : "Zwierzę"}
                      </p>
                    </div>
                    <button
                      onClick={() => startEdit(entity)}
                      className="btn-ghost h-auto shrink-0 p-1.5 text-ink-muted hover:text-ink"
                    >
                      <Edit2 size={16} />
                    </button>
                  </div>
                  <EntityPhotoCollage photos={entity.photos} />
                </>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
