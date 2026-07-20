"use client";

import { FormEvent, Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { login } from "@/lib/auth";
import { Button } from "@/components/ui/Button";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email.trim(), password);
      const next = searchParams.get("next");
      router.replace(next && next.startsWith("/") ? next : "/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Logowanie nie powiodło się.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-md rounded-xl border border-border bg-surface-raised p-6 shadow-sm">
      <div className="mb-6 text-center">
        <h1 className="text-2xl font-semibold text-ink">Zaloguj się</h1>
        <p className="mt-1 text-sm text-ink-muted">
          Dostęp do folderów, czatu i bazy wiedzy wymaga konta.
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
          <span className="mb-1 block text-sm font-medium text-ink">Hasło</span>
          <input
            type="password"
            autoComplete="current-password"
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

        <Button label={loading ? "Logowanie…" : "Zaloguj"} type="submit" disabled={loading} />
      </form>

      <p className="mt-4 text-center text-sm text-ink-muted">
        Nie masz konta?{" "}
        <Link href="/register" className="font-medium text-ink underline underline-offset-2">
          Zarejestruj się
        </Link>
      </p>
    </div>
  );
}

export default function LoginPage() {
  return (
    <div className="flex min-h-full items-center justify-center bg-surface px-4 py-10">
      <Suspense fallback={<div className="text-sm text-ink-muted">Ładowanie…</div>}>
        <LoginForm />
      </Suspense>
    </div>
  );
}
