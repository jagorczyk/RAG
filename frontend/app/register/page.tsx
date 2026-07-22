"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { register } from "@/lib/auth";
import { Button } from "@/components/ui/Button";
import { AuthLanding, GoogleSignInButton } from "@/components/auth/AuthLanding";

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
    <AuthLanding
      title="Utwórz konto"
      subtitle="Hasło minimum 8 znaków. Po rejestracji zostaniesz zalogowany."
      footer={
        <p>
          Masz już konto?{" "}
          <Link href="/login" className="font-semibold text-accent underline-offset-2 hover:underline">
            Zaloguj się
          </Link>
        </p>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4">
        <label className="block">
          <span className="mb-1 block text-sm font-semibold text-ink">E-mail</span>
          <input
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-[10px] border border-border bg-surface-raised px-3 py-2.5 text-ink outline-none focus:border-accent"
          />
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-semibold text-ink">Nazwa wyświetlana (opcjonalnie)</span>
          <input
            type="text"
            autoComplete="name"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="w-full rounded-[10px] border border-border bg-surface-raised px-3 py-2.5 text-ink outline-none focus:border-accent"
          />
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-semibold text-ink">Hasło</span>
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded-[10px] border border-border bg-surface-raised px-3 py-2.5 text-ink outline-none focus:border-accent"
          />
        </label>

        {error && (
          <p className="rounded-[10px] bg-[var(--error-soft)] px-3 py-2 text-sm text-[var(--error)]" role="alert">
            {error}
          </p>
        )}

        <Button label={loading ? "Tworzenie…" : "Utwórz konto"} type="submit" disabled={loading} />
      </form>

      <div className="my-5 flex items-center gap-3" aria-hidden>
        <span className="h-px flex-1 bg-border" />
        <span className="text-xs font-semibold uppercase tracking-wide text-ink-muted">lub</span>
        <span className="h-px flex-1 bg-border" />
      </div>

      <GoogleSignInButton disabled />
    </AuthLanding>
  );
}
