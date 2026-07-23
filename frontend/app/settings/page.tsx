"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { LogOut, Shield } from "lucide-react";
import { getStoredUser, logout, me, type AuthUser } from "@/lib/auth";
import { PageHeader } from "@/components/ui/PageHeader";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";

export default function SettingsPage() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const profile = await me();
        if (!cancelled) setUser(profile);
      } catch {
        if (!cancelled) setUser(getStoredUser());
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const seed = user?.email || "cogniface-user";

  return (
    <div className="page-shell">
      <PageHeader title="Ustawienia" subtitle="Konto i preferencje" />
      <div className="mx-auto w-full max-w-xl space-y-6 px-4 py-6 md:px-6">
        <section className="panel p-5">
          <p className="section-caption">Profil</p>
          {loading ? (
            <div className="mt-3 flex items-center gap-3">
              <div className="skeleton h-12 w-12 !rounded-full" />
              <div className="min-w-0 flex-1 space-y-2">
                <div className="skeleton h-5 w-2/3" />
                <div className="skeleton h-4 w-1/2" />
              </div>
            </div>
          ) : (
            <div className="mt-3 flex items-start gap-3">
              <Avatar seed={seed} fallbackLabel={user?.email || "?"} size="lg" />
              <dl className="min-w-0 flex-1 space-y-3 text-sm">
                <div>
                  <dt className="text-ink-muted">E-mail</dt>
                  <dd className="mt-0.5 truncate font-semibold text-ink">
                    {user?.email ?? "—"}
                  </dd>
                </div>
                <div>
                  <dt className="text-ink-muted">Nazwa wyświetlana</dt>
                  <dd className="mt-0.5 font-semibold text-ink">
                    {user?.displayName?.trim() || "Nie ustawiono"}
                  </dd>
                </div>
              </dl>
            </div>
          )}
        </section>

        <section className="panel p-5">
          <p className="section-caption">Aplikacja</p>
          <ul className="mt-1 space-y-1 text-sm">
            <li className="flex items-center justify-between gap-3 py-2">
              <span className="text-ink-muted">Język interfejsu</span>
              <span className="font-semibold text-ink">Polski</span>
            </li>
            <li>
              <Link
                href="/privacy"
                className="flex min-h-[var(--touch-min)] items-center gap-2 font-semibold text-accent transition-opacity hover:opacity-80"
              >
                <Shield size={16} aria-hidden />
                Polityka prywatności
              </Link>
            </li>
          </ul>
          <p className="mt-3 text-xs text-ink-muted">
            Więcej opcji ustawień pojawi się w kolejnych wersjach.
          </p>
        </section>

        <Button
          label="Wyloguj"
          secondary
          onClick={() => logout()}
          icon={<LogOut size={16} aria-hidden />}
        />
      </div>
    </div>
  );
}
