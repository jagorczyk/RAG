import { useState, useEffect, useCallback } from "react";
import {
  getPendingSuggestions,
  IdentitySuggestion,
  mergeSuggestion,
  splitSuggestion,
  SuggestionMention,
} from "@/lib/knowledge-api";
import { Check, X, User, ShieldAlert } from "lucide-react";
import { EmptyState } from "@/components/ui/EmptyState";

function FaceSnippet({ mention }: { mention: SuggestionMention }) {
  return (
    <div className="flex min-w-[88px] flex-col items-center gap-1.5">
      <div className="h-20 w-20 overflow-hidden rounded-[8px] border-2 border-border bg-surface">
        {mention.faceCropBase64 ? (
          <img
            src={`data:image/jpeg;base64,${mention.faceCropBase64}`}
            alt={`Twarz: ${mention.label}`}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-ink-muted">
            <User size={28} strokeWidth={1.5} aria-hidden />
          </div>
        )}
      </div>
      <p className="max-w-[96px] truncate text-center text-xs font-medium text-ink">
        {mention.label}
      </p>
      <p className="max-w-[96px] truncate text-center text-[10px] text-ink-muted">
        {mention.fileName}
      </p>
    </div>
  );
}

/**
 * Panel „czy to ta sama osoba?” — renderuje się tylko gdy są sugestie do potwierdzenia
 * (albo błąd wczytania). Przy pustej liście UI znika całkowicie.
 * Single surface (list-panel + rows) — no card-in-card nesting on mobile.
 */
export function IdentityReviewPanel() {
  const [suggestions, setSuggestions] = useState<IdentitySuggestion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const loadSuggestions = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const data = await getPendingSuggestions();
      setSuggestions(data);
    } catch (e) {
      console.error(e);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSuggestions();
  }, [loadSuggestions]);

  const handleMerge = async (id: string) => {
    setBusyId(id);
    try {
      await mergeSuggestion(id);
      await loadSuggestions();
    } catch (e) {
      console.error(e);
    } finally {
      setBusyId(null);
    }
  };

  const handleSplit = async (id: string) => {
    setBusyId(id);
    try {
      await splitSuggestion(id);
      await loadSuggestions();
    } catch (e) {
      console.error(e);
    } finally {
      setBusyId(null);
    }
  };

  // Brak pracy do zrobienia — nie zajmuj miejsca na stronie Osoby.
  if (loading) return null;
  if (!error && suggestions.length === 0) return null;

  if (error) {
    return (
      <EmptyState
        icon="☁️"
        title="Nie udało się wczytać sugestii tożsamości"
        description="Sprawdź połączenie z serwerem i spróbuj ponownie."
        action={
          <button type="button" className="btn-primary" onClick={() => void loadSuggestions()}>
            Spróbuj ponownie
          </button>
        }
        className="!py-8"
      />
    );
  }

  return (
    <section>
      <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="text-base font-extrabold tracking-tight text-ink">
            Oczekujące potwierdzenia
          </h3>
          <p className="mt-0.5 text-xs text-ink-muted">
            Potwierdź lub rozdziel sugerowane tożsamości. Dopiero potwierdzone
            trafiają do pewnych źródeł.
          </p>
        </div>
        <span className="status-badge status-badge-suggested">
          <ShieldAlert size={12} aria-hidden />
          {suggestions.length}{" "}
          {suggestions.length === 1
            ? "sugestia"
            : suggestions.length < 5
              ? "sugestie"
              : "sugestii"}
        </span>
      </div>

      <div className="list-panel">
        {suggestions.map((s) => {
          const busy = busyId === s.id;
          return (
            <div
              key={s.id}
              className="flex flex-col gap-3 border-b border-border p-3.5 last:border-b-0 sm:flex-row sm:items-center sm:justify-between sm:gap-4 sm:p-4"
            >
              <div className="min-w-0 flex-1 space-y-3">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="status-badge status-badge-suggested">Sugerowane</span>
                  <span className="text-xs font-semibold text-ink-muted">
                    Podobieństwo {(s.similarityScore * 100).toFixed(0)}% — nie potwierdzone
                  </span>
                </div>
                <p className="text-sm font-medium text-ink">
                  Czy {s.mentionA.label} i {s.mentionB.label} to ta sama osoba?
                </p>
                {/* One-axis rail for face pair comparison on narrow screens */}
                <div className="mobile-h-rail md:flex md:flex-wrap md:items-center md:gap-4 md:overflow-visible">
                  <FaceSnippet mention={s.mentionA} />
                  <div className="flex shrink-0 flex-col items-center px-2">
                    <span className="text-[10px] font-bold uppercase tracking-wide text-ink-muted">
                      Podobieństwo
                    </span>
                    <span className="text-lg font-semibold text-warning">
                      {(s.similarityScore * 100).toFixed(0)}%
                    </span>
                  </div>
                  <FaceSnippet mention={s.mentionB} />
                </div>
              </div>
              <div className="flex shrink-0 gap-2">
                <button
                  type="button"
                  onClick={() => void handleMerge(s.id)}
                  disabled={busy}
                  className="btn-primary min-h-[var(--touch-min)] flex-1 px-3 text-sm sm:flex-none"
                >
                  <Check size={16} className="mr-1" aria-hidden /> Tak — ta sama
                </button>
                <button
                  type="button"
                  onClick={() => void handleSplit(s.id)}
                  disabled={busy}
                  className="btn-secondary min-h-[var(--touch-min)] flex-1 px-3 text-sm text-error sm:flex-none"
                >
                  <X size={16} className="mr-1" aria-hidden /> Nie — rozdziel
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
