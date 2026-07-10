import { useState, useEffect } from "react";
import { getAllEntities, KnowledgeEntity, renameEntity } from "@/lib/knowledge-api";
import { Check, Edit2, X } from "lucide-react";

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
        <div className="grid gap-3 grid-cols-1 sm:grid-cols-2 md:grid-cols-3">
          {entities.map((e) => (
            <div key={e.id} className="flex items-center justify-between rounded-[8px] bg-surface-raised p-3 border border-border">
              {editingId === e.id ? (
                <div className="flex flex-1 items-center gap-2">
                  <input
                    type="text"
                    value={editValue}
                    onChange={(ev) => setEditValue(ev.target.value)}
                    className="flex-1 w-full rounded-[6px] border border-border bg-surface px-2 py-1 text-sm text-ink outline-none"
                    autoFocus
                  />
                  <button onClick={saveEdit} className="btn-primary p-1 h-auto text-xs"><Check size={14}/></button>
                  <button onClick={() => setEditingId(null)} className="btn-secondary p-1 h-auto text-xs text-error"><X size={14}/></button>
                </div>
              ) : (
                <>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-ink truncate">{e.displayName}</p>
                    <p className="text-xs text-ink-muted">Typ: {e.type === "PERSON" ? "Osoba" : "Zwierzę"}</p>
                  </div>
                  <button onClick={() => startEdit(e)} className="btn-ghost p-1.5 h-auto text-ink-muted hover:text-ink shrink-0">
                    <Edit2 size={16} />
                  </button>
                </>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
