import { useState, useEffect } from "react";
import { getPendingSuggestions, IdentitySuggestion, mergeSuggestion, splitSuggestion } from "@/lib/knowledge-api";
import { Check, X } from "lucide-react";

export function IdentityReviewPanel() {
  const [suggestions, setSuggestions] = useState<IdentitySuggestion[]>([]);

  useEffect(() => {
    loadSuggestions();
  }, []);

  const loadSuggestions = async () => {
    try {
      const data = await getPendingSuggestions();
      setSuggestions(data);
    } catch (e) {
      console.error(e);
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
    <div className="rounded-[10px] border border-border bg-surface p-4">
      <h3 className="mb-4 text-lg font-semibold text-ink">Oczekujące potwierdzenia tożsamości</h3>
      {suggestions.length === 0 ? (
        <p className="text-sm text-ink-muted">Brak sugestii do weryfikacji.</p>
      ) : (
        <div className="space-y-4">
          {suggestions.map((s) => (
            <div key={s.id} className="flex flex-col gap-3 rounded-[8px] bg-surface-raised p-3 border border-border sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-medium text-ink">Czy {s.mentionA.label} i {s.mentionB.label} to ta sama osoba?</p>
                <p className="text-xs text-ink-muted">Podobieństwo: {(s.similarityScore * 100).toFixed(0)}%</p>
                <p className="text-xs text-ink-muted mt-1">Pliki: {s.mentionA.filePath} oraz {s.mentionB.filePath}</p>
              </div>
              <div className="flex gap-2">
                <button onClick={() => handleMerge(s.id)} className="btn-primary text-xs px-3 py-1.5 h-auto">
                  <Check size={14} className="mr-1"/> Tak
                </button>
                <button onClick={() => handleSplit(s.id)} className="btn-secondary text-xs px-3 py-1.5 h-auto text-error">
                  <X size={14} className="mr-1"/> Nie
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
