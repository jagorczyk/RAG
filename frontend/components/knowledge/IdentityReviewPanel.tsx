import { useState, useEffect } from "react";
import { getPendingSuggestions, IdentitySuggestion, mergeSuggestion, splitSuggestion, SuggestionMention } from "@/lib/knowledge-api";
import { Check, X, User } from "lucide-react";

function FaceSnippet({ mention }: { mention: SuggestionMention }) {
  return (
    <div className="flex flex-col items-center gap-1.5 min-w-[88px]">
      <div className="h-20 w-20 overflow-hidden rounded-[8px] border-2 border-border bg-surface">
        {mention.faceCropBase64 ? (
          <img
            src={`data:image/jpeg;base64,${mention.faceCropBase64}`}
            alt={`Twarz: ${mention.label}`}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-ink-muted">
            <User size={28} strokeWidth={1.5} />
          </div>
        )}
      </div>
      <p className="max-w-[96px] truncate text-center text-xs font-medium text-ink">{mention.label}</p>
      <p className="max-w-[96px] truncate text-center text-[10px] text-ink-muted">{mention.fileName}</p>
    </div>
  );
}

export function IdentityReviewPanel() {
  const [suggestions, setSuggestions] = useState<IdentitySuggestion[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadSuggestions();
  }, []);

  const loadSuggestions = async () => {
    setLoading(true);
    try {
      const data = await getPendingSuggestions();
      setSuggestions(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const handleMerge = async (id: string) => {
    await mergeSuggestion(id);
    loadSuggestions();
  };

  const handleSplit = async (id: string) => {
    await splitSuggestion(id);
    loadSuggestions();
  };

  return (
    <div className="panel p-4 sm:p-5">
      <h3 className="mb-1 text-xl font-extrabold tracking-tight text-ink">
        Oczekujące potwierdzenia
      </h3>
      <p className="mb-4 text-sm text-ink-muted">
        Potwierdź lub rozdziel sugerowane tożsamości na zdjęciach.
      </p>
      {loading ? (
        <p className="text-sm text-ink-muted">Ładowanie sugestii...</p>
      ) : suggestions.length === 0 ? (
        <p className="text-sm text-ink-muted">Brak sugestii do weryfikacji.</p>
      ) : (
        <div className="space-y-4">
          {suggestions.map((s) => (
            <div
              key={s.id}
              className="flex flex-col gap-4 rounded-[8px] border border-border bg-surface-raised p-4 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="flex-1 space-y-3">
                <p className="text-sm font-medium text-ink">
                  Czy {s.mentionA.label} i {s.mentionB.label} to ta sama osoba?
                </p>
                <div className="flex flex-wrap items-center gap-4">
                  <FaceSnippet mention={s.mentionA} />
                  <div className="flex flex-col items-center px-2">
                    <span className="text-[10px] uppercase tracking-wide text-ink-muted">Podobieństwo</span>
                    <span className="text-lg font-semibold text-ink">{(s.similarityScore * 100).toFixed(0)}%</span>
                  </div>
                  <FaceSnippet mention={s.mentionB} />
                </div>
              </div>
              <div className="flex shrink-0 gap-2">
                <button onClick={() => handleMerge(s.id)} className="btn-primary h-auto px-3 py-1.5 text-xs">
                  <Check size={14} className="mr-1" /> Tak
                </button>
                <button onClick={() => handleSplit(s.id)} className="btn-secondary h-auto px-3 py-1.5 text-xs text-error">
                  <X size={14} className="mr-1" /> Nie
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
