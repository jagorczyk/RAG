"use client";

import { X, Edit2, Check } from "lucide-react";
import { FilePreview } from "@/lib/api";
import { useState, useEffect, useMemo, useCallback } from "react";
import {
  getMentionsForFile,
  detectFacesForFile,
  EntityMention,
  hasFaceBbox,
  IdentityConflictError,
  renameMention,
} from "@/lib/knowledge-api";
import { FaceAnnotatedImage } from "@/components/ui/FaceAnnotatedImage";
import { getFaceColor } from "@/lib/face-colors";
import { FadeModal } from "@/components/ui/FadeModal";
import { motion, useReducedMotion } from "motion/react";

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
  const reduced = useReducedMotion();

  const loadMentions = useCallback(async () => {
    if (!preview.path) return;
    try {
      let data = await getMentionsForFile(preview.path);
      const needsFaceDetection = !data.some(hasFaceBbox);
      if (needsFaceDetection) {
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
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void loadMentions();
    }
  }, [preview.path, loadMentions]);

  const displayedMentions = useMemo(
    () => mentions.filter(hasFaceBbox),
    [mentions]
  );

  const annotatedFaces = useMemo(
    () =>
      displayedMentions.map((mention, index) => ({
          id: mention.id,
          bbox: mention.bbox as number[],
          colorIndex: index,
          label: String(index + 1),
        })),
    [displayedMentions]
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
      if (e instanceof IdentityConflictError) {
        const confirmed = window.confirm(
          "Ta osoba jest już przypisana do innej twarzy na tym zdjęciu. Czy to celowy wyjątek, np. odbicie lub kolaż?"
        );
        if (confirmed) {
          await renameMention(editingId, editValue, true);
          setEditingId(null);
          void loadMentions();
          return;
        }
      }
      console.error(e);
    }
  };

  return (
    <FadeModal
      open
      onClose={onClose}
      variant="fullscreen"
      contentClassName="bg-surface"
      aria-label={preview.title || "Podgląd pliku"}
    >
      <header className="flex min-h-[3.4rem] shrink-0 items-center gap-3 border-b border-border px-4">
        <button
          type="button"
          onClick={onClose}
          className="text-[17px] font-semibold text-ink"
        >
          Gotowe
        </button>
        <h3 className="min-w-0 flex-1 truncate text-center text-[17px] font-bold text-ink">
          {preview.title}
        </h3>
        <button
          type="button"
          onClick={onClose}
          className="icon-button shadow-none"
          aria-label="Zamknij podgląd"
        >
          <X size={22} />
        </button>
      </header>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden md:flex-row">
        <motion.div
          className="flex flex-1 items-center justify-center overflow-auto bg-soft p-4"
          initial={reduced ? false : { opacity: 0, scale: 0.985 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.18 }}
        >
          {preview.kind === "image" ? (
            <FaceAnnotatedImage
              src={preview.content}
              alt={`Podgląd ${preview.title}`}
              faces={annotatedFaces}
            />
          ) : (
            <pre className="w-full max-w-3xl whitespace-pre-wrap text-[15px] leading-6 text-ink">
              {preview.content}
            </pre>
          )}
        </motion.div>

        {displayedMentions.length > 0 && (
          <aside className="max-h-[40vh] w-full shrink-0 overflow-y-auto border-t border-border bg-surface p-4 md:max-h-none md:w-80 md:border-l md:border-t-0">
            <h4 className="mb-3 text-sm font-extrabold text-ink">Osoby na zdjęciu</h4>
            <div className="list-panel">
              {displayedMentions.map((mention, index) => {
                const color = getFaceColor(index);
                return (
                <div key={mention.id} className="list-row !min-h-[3.8rem]">
                  {editingId === mention.id ? (
                    <div className="flex w-full items-center gap-2">
                      <input
                        type="text"
                        value={editValue}
                        onChange={(e) => setEditValue(e.target.value)}
                        className="input-field !min-h-9 flex-1 !text-sm"
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === "Enter") void saveEdit();
                          if (e.key === "Escape") setEditingId(null);
                        }}
                      />
                      <button type="button" onClick={saveEdit} className="btn-primary !min-h-9 px-2">
                        <Check size={14} />
                      </button>
                      <button
                        type="button"
                        onClick={() => setEditingId(null)}
                        className="btn-secondary !min-h-9 px-2"
                      >
                        <X size={14} />
                      </button>
                    </div>
                  ) : (
                    <>
                      <span
                        className="flex h-6 min-w-6 shrink-0 items-center justify-center rounded-md text-xs font-extrabold"
                        style={{ backgroundColor: color.border, color: color.text }}
                        aria-hidden="true"
                      >
                        {index + 1}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-bold text-ink">
                          {mentionDisplayName(mention) || `Osoba ${index + 1}`}
                        </p>
                        <p className="mt-0.5 truncate text-xs text-ink-muted">
                          {mention.visualCues || "Dotknij, aby przypisać nazwę"}
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => startEdit(mention)}
                        className="p-1.5 text-ink-muted hover:text-ink"
                        aria-label="Edytuj nazwę"
                      >
                        <Edit2 size={15} />
                      </button>
                    </>
                  )}
                </div>
                );
              })}
            </div>
          </aside>
        )}
      </div>
    </FadeModal>
  );
}
