"use client";

import { FormEvent, Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { login } from "@/lib/auth";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { AuthLanding, GoogleSignInButton } from "@/components/auth/AuthLanding";

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
    <AuthLanding
      title="Zaloguj się"
      subtitle="Dostęp do biblioteki zdjęć, osób i czatu Cogniface."
      footer={
        <p>
          Nie masz konta?{" "}
          <Link href="/register" className="font-semibold text-accent underline-offset-2 hover:underline">
            Zarejestruj się
          </Link>
        </p>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4">
        <Field
          label="E-mail"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        <Field
          label="Hasło"
          type="password"
          autoComplete="current-password"
          required
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        {error && (
          <p className="status-banner status-banner-error !mb-0" role="alert">
            {error}
          </p>
        )}

        <Button label={loading ? "Logowanie…" : "Zaloguj się"} type="submit" disabled={loading} />
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

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="flex h-full items-center justify-center text-sm text-ink-muted">Ładowanie…</div>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
