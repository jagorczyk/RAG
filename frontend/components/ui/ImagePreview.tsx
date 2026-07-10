"use client";

import { X, Edit2, Check } from "lucide-react";
import { FilePreview } from "@/lib/api";
import { useState, useEffect } from "react";
import { getMentionsForFile, EntityMention, renameMention } from "@/lib/knowledge-api";

interface FilePreviewModalProps {
  preview: FilePreview;
  onClose: () => void;
}

export function ImagePreview({ preview, onClose }: FilePreviewModalProps) {
  const [mentions, setMentions] = useState<EntityMention[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");

  useEffect(() => {
    if (preview.path) {
      loadMentions();
    }
  }, [preview.path]);

  const loadMentions = async () => {
    if (!preview.path) return;
    try {
      const data = await getMentionsForFile(preview.path);
      setMentions(data);
    } catch (e) {
      console.error(e);
    }
  };

  const startEdit = (mention: EntityMention) => {
    setEditingId(mention.id);
    setEditValue(mention.entity?.displayName || mention.label);
  };

  const saveEdit = async () => {
    if (!editingId) return;
    try {
      await renameMention(editingId, editValue);
      setEditingId(null);
      loadMentions();
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/85 p-4"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Podgląd pliku"
    >
      <button
        type="button"
        className="btn-ghost absolute right-4 top-4 text-surface-raised hover:bg-surface-raised/10 hover:text-surface-raised"
        onClick={onClose}
        aria-label="Zamknij podgląd"
      >
        <X size={28} />
      </button>
      <div
        className="w-full max-w-5xl rounded-[10px] border border-border bg-surface-raised shadow-md flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="flex items-center justify-between border-b border-border px-4 py-3 shrink-0">
          <h3 className="truncate text-sm font-semibold text-ink">{preview.title}</h3>
          <span className="font-mono text-[11px] text-ink-muted">{preview.mimeType}</span>
        </header>
        <div className="flex flex-col md:flex-row max-h-[78vh] overflow-hidden">
          <div className="flex-1 overflow-auto p-4 bg-surface-raised">
            {preview.kind === "image" ? (
              <img
                src={preview.content}
                alt={`Podgląd ${preview.title}`}
                className="max-h-[70vh] mx-auto rounded-[8px] object-contain"
              />
            ) : (
              <pre className="whitespace-pre-wrap text-sm leading-relaxed text-ink">
                {preview.content}
              </pre>
            )}
          </div>
          {mentions.length > 0 && (
            <div className="w-full md:w-80 shrink-0 border-t md:border-t-0 md:border-l border-border bg-surface p-4 overflow-y-auto">
              <h4 className="text-sm font-medium text-ink mb-3">Rozpoznane na tym zdjęciu:</h4>
              <div className="space-y-3">
                {mentions.map((m) => (
                  <div key={m.id} className="rounded border border-border bg-surface-raised p-2 text-sm">
                    {editingId === m.id ? (
                      <div className="flex items-center gap-1">
                        <input
                          type="text"
                          value={editValue}
                          onChange={(e) => setEditValue(e.target.value)}
                          className="flex-1 w-full rounded border border-border px-1.5 py-0.5 text-xs text-ink outline-none"
                          autoFocus
                        />
                        <button onClick={saveEdit} className="btn-primary p-1 h-auto"><Check size={12}/></button>
                        <button onClick={() => setEditingId(null)} className="btn-secondary p-1 h-auto text-error"><X size={12}/></button>
                      </div>
                    ) : (
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <p className="font-medium text-ink break-words">{m.entity?.displayName || m.label}</p>
                          <p className="text-[10px] text-ink-muted mt-1 leading-tight">
                            Wizualnie: {m.visualCues ? m.visualCues.substring(0, 40) + "..." : "brak"}
                          </p>
                        </div>
                        <button onClick={() => startEdit(m)} className="p-1 text-ink-muted hover:text-ink shrink-0">
                          <Edit2 size={12} />
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
