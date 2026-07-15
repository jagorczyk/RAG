"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Check, User, X } from "lucide-react";
import { FaceAnnotatedImage } from "@/components/ui/FaceAnnotatedImage";
import { getFaceColor } from "@/lib/face-colors";
import {
  confirmMention,
  detectFacesForFile,
  EntityMention,
  getAllEntities,
  getMentionsForFile,
  IdentitySuggestion,
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


export function UploadIdentityPrompt({ files, onClose, onComplete }: UploadIdentityPromptProps) {
  const [fileIndex, setFileIndex] = useState(0);
  const [mentions, setMentions] = useState<EntityMention[]>([]);
  const [suggestions, setSuggestions] = useState<IdentitySuggestion[]>([]);
  const [entities, setEntities] = useState<KnowledgeEntity[]>([]);
  const [nameInputs, setNameInputs] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<string | null>(null);

  const currentFile = files[fileIndex];

  const loadFileData = useCallback(async () => {
    if (!currentFile) {
      return;
    }
    setLoading(true);
    try {
      let mentionData = await getMentionsForFile(currentFile.path);
      const needsFaceDetection = mentionData.some(
        (mention) => !mention.bbox || mention.bbox.length < 4
      );
      if (mentionData.length > 0 && needsFaceDetection) {
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

  const reviewMentions = useMemo(
    () => mentions.filter(mentionNeedsReview),
    [mentions]
  );

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

  const goNext = () => {
    if (fileIndex + 1 < files.length) {
      setFileIndex((prev) => prev + 1);
      return;
    }
    onComplete();
  };

  const saveMentionName = async (mentionId: string) => {
    const name = nameInputs[mentionId]?.trim();
    if (!name) {
      return;
    }
    setSavingId(mentionId);
    try {
      const existing = personEntities.find(
        (entity) => entity.displayName.toLowerCase() === name.toLowerCase()
      );
      if (existing) {
        await confirmMention(mentionId, existing.id);
      } else {
        await renameMention(mentionId, name);
      }
      await loadFileData();
    } catch (error) {
      console.error("Failed to save mention identity", error);
      alert("Nie udało się zapisać tożsamości.");
    } finally {
      setSavingId(null);
    }
  };

  const handleMerge = async (suggestionId: string) => {
    try {
      await mergeSuggestion(suggestionId);
      await loadFileData();
    } catch (error) {
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

  if (!currentFile) {
    return null;
  }

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
          </div>
          <button type="button" onClick={onClose} className="btn-ghost p-1" aria-label="Zamknij">
            <X size={18} />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto p-4">
          {loading ? (
            <p className="text-sm text-ink-muted">Ładowanie wykrytych osób...</p>
          ) : nothingToReview ? (
            <p className="text-sm text-ink-muted">
              Wszystkie osoby na tym zdjęciu są już potwierdzone.
            </p>
          ) : (
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
                  {reviewMentions.map((mention, index) => {
                    const color = getFaceColor(
                      mentions.findIndex((item) => item.id === mention.id)
                    );
                    return (
                      <div
                        key={mention.id}
                        className="rounded-[8px] border bg-surface p-3"
                        style={{ borderColor: color.border }}
                      >
                        <div className="mb-2 flex items-center gap-2">
                          <span
                            className="inline-block h-3 w-3 rounded-sm"
                            style={{ backgroundColor: color.border }}
                          />
                          <span className="text-xs font-medium text-ink">
                            Osoba {index + 1}
                          </span>
                          <span className="text-xs text-ink-muted">
                            wykryto: {mention.label}
                          </span>
                        </div>
                        <div className="relative z-20 flex flex-col gap-2 sm:flex-row">
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
                            className="relative z-20 flex-1 rounded-[6px] border border-border bg-surface-raised px-3 py-2 text-sm text-ink outline-none focus:border-accent"
                          />
                          <datalist id={`upload-entity-suggestions-${mention.id}`}>
                            {personEntities.map((entity) => (
                              <option key={entity.id} value={entity.displayName} />
                            ))}
                          </datalist>
                          <button
                            type="button"
                            onClick={() => saveMentionName(mention.id)}
                            disabled={!nameInputs[mention.id]?.trim() || savingId === mention.id}
                            className="btn-primary h-auto px-3 py-2 text-sm"
                          >
                            <Check size={14} className="mr-1" />
                            {savingId === mention.id ? "Zapisuję..." : "Potwierdź"}
                          </button>
                          <button
                            type="button"
                            onClick={async () => {
                              setSavingId(mention.id);
                              try {
                                await rejectMention(mention.id);
                                await loadFileData();
                              } finally {
                                setSavingId(null);
                              }
                            }}
                            disabled={savingId === mention.id}
                            className="btn-secondary h-auto px-3 py-2 text-sm text-error"
                          >
                            Odrzuć detekcję
                          </button>
                        </div>
                        {mention.entityDisplayName &&
                          !isGenericName(mention.entityDisplayName) &&
                          mention.entityId && (
                            <button
                              type="button"
                              onClick={async () => {
                                setSavingId(mention.id);
                                try {
                                  await confirmMention(mention.id, mention.entityId!);
                                  await loadFileData();
                                } finally {
                                  setSavingId(null);
                                }
                              }}
                              className="mt-2 text-xs text-accent hover:underline"
                            >
                              Potwierdź sugestię: {mention.entityDisplayName}
                            </button>
                          )}
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
          )}
        </div>

        <footer className="flex items-center justify-between gap-2 border-t border-border px-4 py-3">
          <button type="button" onClick={onClose} className="btn-ghost text-sm">
            Zamknij
          </button>
          <div className="flex gap-2">
            <button type="button" onClick={goNext} className="btn-secondary text-sm">
              {fileIndex + 1 < files.length ? "Pomiń · następne" : "Pomiń"}
            </button>
            <button
              type="button"
              onClick={goNext}
              className="btn-primary text-sm"
              disabled={loading}
            >
              {fileIndex + 1 < files.length ? "Dalej" : "Gotowe"}
            </button>
          </div>
        </footer>
      </div>
    </div>
  );
}
