"use client";

import { use, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Image as ImageIcon, Loader2, User, X } from "lucide-react";
import {
  EntityAppearance,
  getEntity,
  getEntityAppearances,
  KnowledgeEntity,
} from "@/lib/knowledge-api";
import { FilePreview, getFilePreview } from "@/lib/api";
import { FaceAnnotatedImage } from "@/components/ui/FaceAnnotatedImage";

interface EntityAlbumPageProps {
  params: Promise<{ id: string }>;
}

export default function EntityAlbumPage({ params }: EntityAlbumPageProps) {
  const { id } = use(params);
  const router = useRouter();
  const [entity, setEntity] = useState<KnowledgeEntity | null>(null);
  const [appearances, setAppearances] = useState<EntityAppearance[]>([]);
  const [thumbnails, setThumbnails] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<{
    file: FilePreview;
    bbox?: number[] | null;
  } | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  const loadAlbum = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [entityData, appearanceData] = await Promise.all([
        getEntity(id),
        getEntityAppearances(id),
      ]);
      setEntity(entityData);
      setAppearances(appearanceData);

      const thumbs: Record<string, string> = {};
      await Promise.all(
        appearanceData.map(async (appearance) => {
          try {
            const file = await getFilePreview(appearance.filePath);
            if (file.kind === "image" && file.content) {
              thumbs[appearance.filePath] = file.content;
            }
          } catch (thumbError) {
            console.warn("Failed to load thumbnail", appearance.filePath, thumbError);
          }
        })
      );
      setThumbnails(thumbs);
    } catch (e) {
      console.error(e);
      setError("Nie znaleziono osoby lub nie udało się wczytać albumu.");
      setEntity(null);
      setAppearances([]);
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    // Data loading is the external synchronization this effect is responsible for.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadAlbum();
  }, [loadAlbum]);

  const openPreview = async (appearance: EntityAppearance) => {
    setPreviewLoading(true);
    try {
      const file = await getFilePreview(appearance.filePath);
      setPreview({ file, bbox: appearance.bbox });
    } catch (e) {
      console.error(e);
    } finally {
      setPreviewLoading(false);
    }
  };

  const photoLabel =
    appearances.length === 1
      ? "1 zdjęcie"
      : appearances.length > 1 && appearances.length < 5
        ? `${appearances.length} zdjęcia`
        : `${appearances.length} zdjęć`;

  return (
    <div className="page-shell">
      <header className="flex min-h-[3.25rem] items-center gap-2 border-b border-border px-4">
        <button
          type="button"
          onClick={() => router.push("/knowledge")}
          className="icon-button -ml-1 shadow-none"
          aria-label="Wróć do osób"
        >
          <ArrowLeft size={20} />
        </button>
        <h1 className="min-w-0 flex-1 truncate text-center text-[17px] font-bold text-ink">
          {entity?.displayName ?? (isLoading ? "Ładowanie…" : "Album osoby")}
        </h1>
        <div className="w-9" />
      </header>
      {!isLoading && entity && (
        <div className="flex flex-col items-center px-5 pt-6 pb-2">
          <span className="mb-2.5 flex h-[76px] w-[76px] items-center justify-center rounded-full bg-soft text-ink">
            <User size={34} />
          </span>
          <h2 className="text-[25px] font-extrabold tracking-tight text-ink">
            {entity.displayName}
          </h2>
          <p className="mt-1 text-sm text-ink-muted">{photoLabel}</p>
        </div>
      )}

      <div className="page-body mx-auto max-w-6xl">
        {isLoading ? (
          <div className="flex items-center justify-center gap-2 py-16 text-ink-muted">
            <Loader2 className="animate-spin" size={20} />
            Wczytywanie albumu…
          </div>
        ) : error ? (
          <div className="rounded-[10px] border border-border bg-surface p-6 text-center">
            <p className="text-sm text-ink-muted">{error}</p>
            <button
              type="button"
              className="btn-secondary mt-4"
              onClick={() => router.push("/knowledge")}
            >
              Wróć do listy osób
            </button>
          </div>
        ) : appearances.length === 0 ? (
          <p className="text-sm text-ink-muted">Brak zdjęć z tą osobą.</p>
        ) : (
          <div>
            <h3 className="mb-3 px-1 text-xl font-extrabold text-ink">Zdjęcia</h3>
            <ul className="m-0 grid list-none grid-cols-3 gap-0.5 p-0 sm:grid-cols-4 md:grid-cols-5">
              {appearances.map((appearance) => {
                const thumb = thumbnails[appearance.filePath];
                return (
                  <li key={appearance.mentionId}>
                    <button
                      type="button"
                      onClick={() => void openPreview(appearance)}
                      className="group w-full overflow-hidden rounded-sm text-left transition-transform active:scale-[0.98]"
                      aria-label={`Otwórz ${appearance.fileName}`}
                    >
                      <div className="relative aspect-square w-full bg-soft">
                        {thumb ? (
                          <img
                            src={thumb}
                            alt={appearance.fileName}
                            className="absolute inset-0 h-full w-full object-cover"
                            loading="lazy"
                          />
                        ) : (
                          <div className="absolute inset-0 flex items-center justify-center text-ink-muted">
                            <ImageIcon size={24} />
                          </div>
                        )}
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </div>

      {(preview || previewLoading) && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-ink/85 p-4"
          onClick={() => setPreview(null)}
          role="dialog"
          aria-modal="true"
          aria-label="Podgląd zdjęcia osoby"
        >
          <button
            type="button"
            className="btn-ghost absolute right-4 top-4 text-surface-raised hover:bg-surface-raised/10 hover:text-surface-raised"
            onClick={() => setPreview(null)}
            aria-label="Zamknij podgląd"
          >
            <X size={28} />
          </button>
          <div
            className="w-full max-w-5xl rounded-[10px] border border-border bg-surface-raised shadow-md"
            onClick={(e) => e.stopPropagation()}
          >
            <header className="flex items-center justify-between border-b border-border px-4 py-3">
              <h3 className="truncate text-sm font-semibold text-ink">
                {preview?.file.title ?? "Ładowanie…"}
              </h3>
              {entity && (
                <span className="shrink-0 text-xs text-ink-muted">
                  Tylko twarz: {entity.displayName}
                </span>
              )}
            </header>
            <div className="max-h-[78vh] overflow-auto p-4">
              {previewLoading || !preview ? (
                <div className="flex items-center justify-center gap-2 py-16 text-ink-muted">
                  <Loader2 className="animate-spin" size={20} />
                  Wczytywanie podglądu…
                </div>
              ) : preview.file.kind === "image" ? (
                <FaceAnnotatedImage
                  src={preview.file.content}
                  alt={`Podgląd ${preview.file.title}`}
                  faces={
                    preview.bbox && preview.bbox.length >= 4
                      ? [
                          {
                            id: "focus-face",
                            bbox: preview.bbox,
                            colorIndex: 0,
                          },
                        ]
                      : []
                  }
                />
              ) : (
                <p className="text-sm text-ink-muted">Podgląd niedostępny dla tego pliku.</p>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
