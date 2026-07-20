"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { register } from "@/lib/auth";
import { Button } from "@/components/ui/Button";

export default function RegisterPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await register(email.trim(), password, displayName.trim() || undefined);
      router.replace("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Rejestracja nie powiodła się.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-full items-center justify-center bg-surface px-4 py-10">
      <div className="w-full max-w-md rounded-xl border border-border bg-surface-raised p-6 shadow-sm">
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-semibold text-ink">Utwórz konto</h1>
          <p className="mt-1 text-sm text-ink-muted">
            Hasło minimum 8 znaków. Po rejestracji zostaniesz zalogowany.
          </p>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-ink">E-mail</span>
            <input
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-ink outline-none focus:border-ink"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-ink">Nazwa wyświetlana (opcjonalnie)</span>
            <input
              type="text"
              autoComplete="name"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-ink outline-none focus:border-ink"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-ink">Hasło</span>
            <input
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-ink outline-none focus:border-ink"
            />
          </label>

          {error && (
            <p className="rounded-lg bg-[var(--error-soft)] px-3 py-2 text-sm text-[var(--error)]" role="alert">
              {error}
            </p>
          )}

          <Button label={loading ? "Tworzenie…" : "Zarejestruj"} type="submit" disabled={loading} />
        </form>

        <p className="mt-4 text-center text-sm text-ink-muted">
          Masz już konto?{" "}
          <Link href="/login" className="font-medium text-ink underline underline-offset-2">
            Zaloguj się
          </Link>
        </p>
      </div>
    </div>
  );
}
