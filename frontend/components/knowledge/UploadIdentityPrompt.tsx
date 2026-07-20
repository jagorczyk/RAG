"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Trash2, User, X } from "lucide-react";
import { FaceAnnotatedImage } from "@/components/ui/FaceAnnotatedImage";
import { getFaceColor } from "@/lib/face-colors";
import {
  confirmMention,
  detectFacesForFile,
  EntityMention,
  getAllEntities,
  getMentionsForFile,
  hasFaceBbox,
  IdentitySuggestion,
  IdentityConflictError,
  KnowledgeEntity,
  mergeSuggestion,
  rejectMention,
  renameMention,
  splitSuggestion,
} from "@/lib/knowledge-api";

export interface IdentityReviewFile {
  path: string;
  fileName: string;
  imageUrl: string;
}

interface UploadIdentityPromptProps {
  files: IdentityReviewFile[];
  onClose: () => void;
  onComplete: () => void;
}

function isGenericName(name: string): boolean {
  const lower = name.toLowerCase().trim();
  return (
    lower.startsWith("nieznana") ||
    lower.startsWith("nieznany") ||
    lower.includes("mężczyzna") ||
    lower.includes("mezczyzna") ||
    lower.includes("kobieta") ||
    lower.includes("dziewczyn") ||
    lower.includes("chłopak") ||
    lower.includes("chlopak") ||
    lower.startsWith("osoba ") ||
    lower === "osoba" ||
    lower === "postać" ||
    lower === "postac"
  );
}

function mentionNeedsReview(mention: EntityMention): boolean {
  const name = (mention.entityDisplayName ?? mention.label ?? "").trim().toLowerCase();
  return name.startsWith("osoba ") || name === "osoba" || mention.status !== "CONFIRMED";
}

/** Stable face index for color + label (must match bbox overlay order). */
function faceColorIndex(faceMentions: EntityMention[], mentionId: string): number {
  const index = faceMentions.findIndex((item) => item.id === mentionId);
  return index >= 0 ? index : 0;
}

export function UploadIdentityPrompt({ files, onClose, onComplete }: UploadIdentityPromptProps) {
  const [fileIndex, setFileIndex] = useState(0);
  const [mentions, setMentions] = useState<EntityMention[]>([]);
  const [suggestions, setSuggestions] = useState<IdentitySuggestion[]>([]);
  const [entities, setEntities] = useState<KnowledgeEntity[]>([]);
  const [nameInputs, setNameInputs] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const currentFile = files[fileIndex];

  const loadFileData = useCallback(async () => {
    if (!currentFile) {
      return;
    }
    setLoading(true);
    try {
      let mentionData = await getMentionsForFile(currentFile.path);
      const needsFaceDetection = !mentionData.some(hasFaceBbox);
      if (needsFaceDetection) {
        try {
          mentionData = await detectFacesForFile(currentFile.path);
        } catch (detectError) {
          console.warn("Face detection unavailable:", detectError);
        }
      }

      const fileSuggestions: IdentitySuggestion[] = [];

      setMentions(mentionData);
      setSuggestions(fileSuggestions);

      const defaults: Record<string, string> = {};
      for (const mention of mentionData) {
        if (mentionNeedsReview(mention)) {
          const suggested = mention.entityDisplayName;
          defaults[mention.id] =
            suggested && !isGenericName(suggested) ? suggested : "";
        }
      }
      setNameInputs(defaults);
    } catch (error) {
      console.error("Failed to load identity review data", error);
    } finally {
      setLoading(false);
    }
  }, [currentFile]);

  useEffect(() => {
    getAllEntities()
      .then(setEntities)
      .catch((error) => console.error(error));
  }, []);

  useEffect(() => {
    loadFileData();
  }, [loadFileData]);

  const faceMentions = useMemo(
    () => mentions.filter(hasFaceBbox),
    [mentions]
  );

  const reviewMentions = useMemo(
    () => faceMentions.filter(mentionNeedsReview),
    [faceMentions]
  );

  const annotatedFaces = useMemo(
    () =>
      faceMentions.map((mention, index) => ({
          id: mention.id,
          bbox: mention.bbox as number[],
          colorIndex: index,
          label: String(index + 1),
        })),
    [faceMentions]
  );

  const personEntities = useMemo(
    () =>
      entities.filter(
        (entity) =>
          entity.type === "PERSON" &&
          entity.displayName &&
          !isGenericName(entity.displayName)
      ),
    [entities]
  );

  const goNext = useCallback(() => {
    if (fileIndex + 1 < files.length) {
      setFileIndex((prev) => prev + 1);
      return;
    }
    onComplete();
  }, [fileIndex, files.length, onComplete]);

  const saveMentionName = async (
    mentionId: string,
    name: string,
    allowDuplicateOnFile = false
  ): Promise<boolean> => {
    const trimmed = name.trim();
    if (!trimmed) {
      return true;
    }
    const existing = personEntities.find(
      (entity) => entity.displayName.toLowerCase() === trimmed.toLowerCase()
    );
    try {
      if (existing) {
        await confirmMention(mentionId, existing.id, allowDuplicateOnFile);
      } else {
        await renameMention(mentionId, trimmed, allowDuplicateOnFile);
      }
      return true;
    } catch (error) {
      if (error instanceof IdentityConflictError) {
        if (
          !allowDuplicateOnFile &&
          window.confirm(
            "Ta osoba jest już przypisana do innej twarzy na tym zdjęciu. Potwierdzić celowy wyjątek?"
          )
        ) {
          return saveMentionName(mentionId, trimmed, true);
        }
        return false;
      }
      console.error("Failed to save mention identity", error);
      throw error;
    }
  };

  /** Apply filled name inputs, then advance (Dalej / Gotowe). */
  const processFilledNamesAndGoNext = async () => {
    setSaving(true);
    try {
      for (const mention of reviewMentions) {
        const name = nameInputs[mention.id]?.trim();
        if (!name) {
          continue;
        }
        const ok = await saveMentionName(mention.id, name);
        if (!ok) {
          return;
        }
      }
      // Refresh entity list so subsequent files see newly created people.
      try {
        setEntities(await getAllEntities());
      } catch {
        /* non-fatal */
      }
      goNext();
    } catch (error) {
      console.error("Failed to process identities", error);
      alert("Nie udało się zapisać tożsamości.");
    } finally {
      setSaving(false);
    }
  };

  const handleRemove = async (mentionId: string) => {
    setRemovingId(mentionId);
    try {
      await rejectMention(mentionId);
      await loadFileData();
    } catch (error) {
      console.error(error);
      alert("Nie udało się usunąć detekcji.");
    } finally {
      setRemovingId(null);
    }
  };

  const handleMerge = async (suggestionId: string) => {
    try {
      await mergeSuggestion(suggestionId);
      await loadFileData();
    } catch (error) {
      if (error instanceof IdentityConflictError && window.confirm(
        "Ta sama osoba pojawi się dwa razy na jednym zdjęciu. Potwierdzić celowy wyjątek?"
      )) {
        await mergeSuggestion(suggestionId, true);
        await loadFileData();
        return;
      }
      console.error(error);
      alert("Nie udało się połączyć tożsamości.");
    }
  };

  const handleSplit = async (suggestionId: string) => {
    try {
      await splitSuggestion(suggestionId);
      await loadFileData();
    } catch (error) {
      console.error(error);
      alert("Nie udało się odrzucić sugestii.");
    }
  };

  const nothingToReview =
    !loading && reviewMentions.length === 0 && suggestions.length === 0;

  // Brak twarzy/sugestii do potwierdzenia — przejdź dalej bez pokazywania pustego UI.
  useEffect(() => {
    if (!loading && nothingToReview && currentFile) {
      goNext();
    }
  }, [loading, nothingToReview, currentFile, goNext]);

  if (!currentFile) {
    return null;
  }

  // Ukryj modal na czas auto-skipu (puste potwierdzenia).
  if (loading || nothingToReview) {
    return (
      <div
        className="fixed inset-0 z-50 flex items-center justify-center bg-scrim p-4"
        role="status"
        aria-live="polite"
        aria-busy="true"
      >
        <div className="rounded-[14px] border border-border bg-surface-raised px-5 py-4 text-sm text-ink-muted shadow-md">
          {loading ? "Sprawdzam tożsamości na zdjęciu…" : "Przechodzę dalej…"}
        </div>
      </div>
    );
  }

  const busy = saving || removingId !== null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="upload-identity-title"
    >
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-[14px] border border-border bg-surface-raised shadow-md">
        <header className="flex items-start justify-between gap-3 border-b border-border px-4 py-3">
          <div>
            <h3 id="upload-identity-title" className="text-base font-semibold text-ink">
              Kto jest na zdjęciu?
            </h3>
            <p className="mt-0.5 text-sm text-ink-muted">
              {currentFile.fileName} · {fileIndex + 1} z {files.length}
            </p>
            <p className="mt-1 text-xs text-ink-muted">
              Wpisz imiona i kliknij Gotowe — wtedy zapiszą się osoby. Usuń odrzuca fałszywą detekcję.
            </p>
          </div>
          <button type="button" onClick={onClose} className="btn-ghost p-1" aria-label="Zamknij">
            <X size={18} />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto p-4">
            <div className="space-y-5">
              {currentFile.imageUrl && (
                <FaceAnnotatedImage
                  src={currentFile.imageUrl}
                  alt={`Podgląd ${currentFile.fileName}`}
                  faces={annotatedFaces}
                />
              )}

              {reviewMentions.length > 0 && (
                <div className="space-y-3">
                  <h4 className="text-sm font-medium text-ink">Podpisz wykryte osoby</h4>
                  {reviewMentions.map((mention) => {
                    const colorIndex = faceColorIndex(faceMentions, mention.id);
                    const color = getFaceColor(colorIndex);
                    const personNumber = colorIndex + 1;
                    return (
                      <div
                        key={mention.id}
                        className="rounded-[8px] border-2 bg-surface p-3"
                        style={{
                          borderColor: color.border,
                          backgroundColor: color.bg,
                        }}
                      >
                        <div className="mb-2 flex flex-wrap items-center gap-2">
                          <span
                            className="inline-flex h-6 min-w-6 items-center justify-center rounded-md text-xs font-extrabold"
                            style={{
                              backgroundColor: color.border,
                              color: color.text,
                            }}
                            aria-hidden
                          >
                            {personNumber}
                          </span>
                          <span className="text-xs font-medium text-ink">
                            Osoba {personNumber}
                          </span>
                          <span className="status-badge status-badge-suggested">
                            {mention.status === "CONFIRMED" ? "Do doprecyzowania" : "Do potwierdzenia"}
                          </span>
                        </div>
                        <div className="relative z-20 flex flex-col gap-2 sm:flex-row sm:items-center">
                          <input
                            type="text"
                            list={`upload-entity-suggestions-${mention.id}`}
                            value={nameInputs[mention.id] ?? ""}
                            onChange={(event) =>
                              setNameInputs((prev) => ({
                                ...prev,
                                [mention.id]: event.target.value,
                              }))
                            }
                            placeholder="Wpisz imię (np. Igor)"
                            disabled={busy}
                            className="relative z-20 flex-1 rounded-[6px] border-2 bg-surface-raised px-3 py-2 text-sm text-ink outline-none focus:outline focus:outline-2 focus:outline-offset-1"
                            style={{
                              borderColor: color.border,
                              outlineColor: color.border,
                            }}
                          />
                          <datalist id={`upload-entity-suggestions-${mention.id}`}>
                            {personEntities.map((entity) => (
                              <option key={entity.id} value={entity.displayName} />
                            ))}
                          </datalist>
                          <button
                            type="button"
                            onClick={() => handleRemove(mention.id)}
                            disabled={busy}
                            className="btn-secondary inline-flex h-auto items-center justify-center gap-1.5 px-3 py-2 text-sm text-error"
                            aria-label={`Usuń detekcję osoby ${personNumber}`}
                          >
                            <Trash2 size={14} />
                            {removingId === mention.id ? "Usuwam…" : "Usuń"}
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {suggestions.length > 0 && (
                <div className="space-y-3">
                  <h4 className="text-sm font-medium text-ink">Sugestie dopasowania twarzy</h4>
                  {suggestions.map((suggestion) => (
                    <div
                      key={suggestion.id}
                      className="rounded-[8px] border border-border bg-surface p-3"
                    >
                      <p className="mb-3 text-sm text-ink">
                        Czy <strong>{suggestion.mentionA.label}</strong> i{" "}
                        <strong>{suggestion.mentionB.label}</strong> to ta sama osoba? (
                        {(suggestion.similarityScore * 100).toFixed(0)}% podobieństwa)
                      </p>
                      <div className="mb-3 flex flex-wrap gap-4">
                        {[suggestion.mentionA, suggestion.mentionB].map((mention) => (
                          <div key={mention.id} className="flex flex-col items-center gap-1">
                            <div className="h-16 w-16 overflow-hidden rounded-[8px] border border-border bg-surface-raised">
                              {mention.faceCropBase64 ? (
                                <img
                                  src={`data:image/jpeg;base64,${mention.faceCropBase64}`}
                                  alt=""
                                  className="h-full w-full object-cover"
                                />
                              ) : (
                                <div className="flex h-full w-full items-center justify-center text-ink-muted">
                                  <User size={24} />
                                </div>
                              )}
                            </div>
                            <span className="max-w-[88px] truncate text-xs text-ink">
                              {mention.label}
                            </span>
                          </div>
                        ))}
                      </div>
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => handleMerge(suggestion.id)}
                          className="btn-primary h-auto px-3 py-1.5 text-xs"
                        >
                          Tak, ta sama osoba
                        </button>
                        <button
                          type="button"
                          onClick={() => handleSplit(suggestion.id)}
                          className="btn-secondary h-auto px-3 py-1.5 text-xs"
                        >
                          Nie, osobne
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
        </div>

        <footer className="flex items-center justify-between gap-2 border-t border-border px-4 py-3">
          <button type="button" onClick={onClose} className="btn-ghost text-sm" disabled={busy}>
            Zamknij
          </button>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={goNext}
              className="btn-secondary text-sm"
              disabled={busy}
            >
              {fileIndex + 1 < files.length ? "Pomiń · następne" : "Pomiń"}
            </button>
            <button
              type="button"
              onClick={() => void processFilledNamesAndGoNext()}
              className="btn-primary text-sm"
              disabled={busy || loading}
            >
              {saving
                ? "Zapisuję…"
                : fileIndex + 1 < files.length
                  ? "Dalej"
                  : "Gotowe"}
            </button>
          </div>
        </footer>
      </div>
    </div>
  );
}
