"use client";

import { X, Edit2, Check } from "lucide-react";
import { FilePreview } from "@/lib/api";
import { useState, useEffect, useMemo, useCallback } from "react";
import { getMentionsForFile, detectFacesForFile, EntityMention, renameMention } from "@/lib/knowledge-api";
import { FaceAnnotatedImage } from "@/components/ui/FaceAnnotatedImage";
import { getFaceColor } from "@/lib/face-colors";

interface FilePreviewModalProps {
  preview: FilePreview;
  onClose: () => void;
}

function mentionDisplayName(mention: EntityMention): string {
  return mention.entityDisplayName || mention.entity?.displayName || mention.label;
}

export function ImagePreview({ preview, onClose }: FilePreviewModalProps) {
  const [mentions, setMentions] = useState<EntityMention[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");

  const loadMentions = useCallback(async () => {
    if (!preview.path) return;
    try {
      let data = await getMentionsForFile(preview.path);
      const needsFaceDetection = data.some((mention) => !mention.bbox || mention.bbox.length < 4);
      if (data.length > 0 && needsFaceDetection) {
        try {
          data = await detectFacesForFile(preview.path);
        } catch (detectError) {
          console.warn("Face detection unavailable:", detectError);
        }
      }
      setMentions(data);
    } catch (e) {
      console.error(e);
    }
  }, [preview.path]);

  useEffect(() => {
    if (preview.path) {
      // Data loading is the external synchronization this effect is responsible for.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void loadMentions();
    }
  }, [preview.path, loadMentions]);

  const annotatedFaces = useMemo(
    () =>
      mentions
        .map((mention, index) => ({ mention, index }))
        .filter(({ mention }) => mention.bbox && mention.bbox.length >= 4)
        .map(({ mention, index }) => ({
          id: mention.id,
          bbox: mention.bbox as number[],
          colorIndex: index,
        })),
    [mentions]
  );

  const startEdit = (mention: EntityMention) => {
    setEditingId(mention.id);
    setEditValue(mentionDisplayName(mention));
  };

  const saveEdit = async () => {
    if (!editingId) return;
    try {
      await renameMention(editingId, editValue);
      setEditingId(null);
      void loadMentions();
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
              <FaceAnnotatedImage
                src={preview.content}
                alt={`Podgląd ${preview.title}`}
                faces={annotatedFaces}
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
                {mentions.map((mention, index) => {
                  const color = getFaceColor(index);
                  return (
                    <div
                      key={mention.id}
                      className="rounded bg-surface-raised p-2 text-sm"
                      style={{ border: `2px solid ${color.border}` }}
                    >
                      <div className="mb-1.5 flex items-center gap-1.5">
                        <span
                          className="inline-block h-3 w-3 shrink-0 rounded-sm"
                          style={{ backgroundColor: color.border }}
                          aria-hidden="true"
                        />
                        <span className="text-[10px] font-medium uppercase tracking-wide" style={{ color: color.text }}>
                          Osoba {index + 1}
                        </span>
                      </div>
                      {editingId === mention.id ? (
                        <div className="flex items-center gap-1">
                          <input
                            type="text"
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
                            className="flex-1 w-full rounded border border-border px-1.5 py-0.5 text-xs text-ink outline-none"
                            style={{ borderColor: color.border }}
                            autoFocus
                          />
                          <button onClick={saveEdit} className="btn-primary p-1 h-auto">
                            <Check size={12} />
                          </button>
                          <button onClick={() => setEditingId(null)} className="btn-secondary p-1 h-auto text-error">
                            <X size={12} />
                          </button>
                        </div>
                      ) : (
                        <div className="flex items-start justify-between gap-2">
                          <div>
                            <p className="font-medium text-ink break-words">{mentionDisplayName(mention)}</p>
                            <p className="text-[10px] text-ink-muted mt-1 leading-tight">
                              Wizualnie:{" "}
                              {mention.visualCues ? mention.visualCues.substring(0, 40) + "..." : "brak"}
                            </p>
                          </div>
                          <button
                            onClick={() => startEdit(mention)}
                            className="p-1 text-ink-muted hover:text-ink shrink-0"
                          >
                            <Edit2 size={12} />
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
