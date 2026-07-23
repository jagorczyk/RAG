"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { register } from "@/lib/auth";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { AuthLanding, GoogleSignInButton } from "@/components/auth/AuthLanding";

export default function RegisterPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await register(email.trim(), password);
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
          autoComplete="new-password"
          required
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          hint="Minimum 8 znaków."
        />

        {error && (
          <p className="status-banner status-banner-error !mb-0" role="alert">
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
