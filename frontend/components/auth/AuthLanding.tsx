"use client";

import Image from "next/image";
import Link from "next/link";
import { PhotoCollage } from "./PhotoCollage";

type AuthLandingProps = {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
};

export function AuthLanding({ title, subtitle, children, footer }: AuthLandingProps) {
  return (
    <div className="flex h-full min-h-0 w-full flex-col bg-surface lg:flex-row">
      <div className="relative hidden min-h-0 flex-1 lg:block">
        <PhotoCollage />
      </div>

      <div className="relative h-28 shrink-0 overflow-hidden lg:hidden">
        <PhotoCollage />
      </div>

      <div className="flex min-h-0 w-full flex-1 flex-col justify-center overflow-y-auto px-5 py-8 sm:px-8 lg:max-w-[42%] lg:px-12 xl:px-16">
        <div className="mx-auto w-full max-w-md">
          <div className="mb-8">
            <div className="mb-5 flex items-center gap-3">
              <Image
                src="/logo_rag.png"
                alt=""
                width={40}
                height={40}
                className="h-10 w-10 object-contain"
                priority
              />
              <p
                className="text-2xl font-semibold tracking-tight text-ink"
                style={{ fontFamily: "var(--font-fraunces), Georgia, serif" }}
              >
                RAG
              </p>
            </div>
            <h1
              className="text-3xl font-semibold tracking-tight text-ink text-balance"
              style={{ fontFamily: "var(--font-fraunces), Georgia, serif" }}
            >
              {title}
            </h1>
            <p className="mt-2 text-[0.9375rem] leading-relaxed text-ink-muted text-pretty">
              {subtitle}
            </p>
          </div>

          {children}

          <div className="mt-6 space-y-3 text-center text-sm text-ink-muted">
            {footer}
            <p>
              <Link
                href="/privacy"
                className="font-medium text-accent underline-offset-2 hover:underline"
              >
                Polityka prywatności
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export function GoogleSignInButton({ disabled = true }: { disabled?: boolean }) {
  return (
    <div className="space-y-1.5">
      <button
        type="button"
        disabled={disabled}
        className="btn-secondary w-full"
        title={disabled ? "Logowanie Google będzie dostępne wkrótce" : "Kontynuuj z Google"}
      >
        <GoogleGlyph />
        <span>Kontynuuj z Google</span>
      </button>
      {disabled && (
        <p className="text-center text-xs text-ink-muted">
          Logowanie Google będzie dostępne po podłączeniu OAuth.
        </p>
      )}
    </div>
  );
}

function GoogleGlyph() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden>
      <path
        fill="#FFC107"
        d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.3 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.1 8 3l5.7-5.7C34.2 6.1 29.4 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.5-.4-3.5z"
      />
      <path
        fill="#FF3D00"
        d="M6.3 14.7l6.6 4.8C14.7 16 19 12 24 12c3.1 0 5.8 1.1 8 3l5.7-5.7C34.2 6.1 29.4 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"
      />
      <path
        fill="#4CAF50"
        d="M24 44c5.2 0 10-2 13.6-5.2l-6.3-5.2C29.3 35.3 26.8 36 24 36c-5.3 0-9.7-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z"
      />
      <path
        fill="#1976D2"
        d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.3 4.1-4.2 5.5l.1.1 6.3 5.2C39.4 36.4 44 30.8 44 24c0-1.3-.1-2.5-.4-3.5z"
      />
    </svg>
  );
}
