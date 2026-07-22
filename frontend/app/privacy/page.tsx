import Link from "next/link";
import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = {
  title: "Polityka prywatności — Cogniface",
  description: "Informacje o przetwarzaniu danych w aplikacji Cogniface",
};

const SECTIONS: { title: string; body: ReactNode }[] = [
  {
    title: "1. Administrator",
    body: (
      <p>
        Administratorem danych jest operator aplikacji Cogniface. Dane kontaktowe zostaną
        uzupełnione przed uruchomieniem produkcyjnym.
      </p>
    ),
  },
  {
    title: "2. Zakres danych",
    body: (
      <ul className="list-disc space-y-1 pl-5">
        <li>Dane konta: adres e-mail, hasło (hash) lub identyfikator konta Google przy OAuth.</li>
        <li>Treści użytkownika: przesłane zdjęcia i dokumenty, foldery, tagi osób, konwersacje.</li>
        <li>Dane techniczne: token JWT w localStorage, logi techniczne niezbędne do działania usługi.</li>
        <li>
          Dane biometryczne / wizerunek: zdjęcia twarzy służą do detekcji, face-match i budowy grafu
          wiedzy wyłącznie w bibliotece użytkownika.
        </li>
      </ul>
    ),
  },
  {
    title: "3. Cele przetwarzania",
    body: (
      <p>
        Świadczenie usługi biblioteki i czatu opartego o graf; logowanie; bezpieczeństwo; poprawa
        jakości rozpoznawania wyłącznie w zakresie konta użytkownika.
      </p>
    ),
  },
  {
    title: "4. Podstawy",
    body: (
      <p>
        Wykonanie umowy (świadczenie usługi), zgoda (tam gdzie wymagana — domyślnie brak marketingu),
        prawnie uzasadniony interes (bezpieczeństwo, nadużycia) — do doprecyzowania prawnie.
      </p>
    ),
  },
  {
    title: "5. Retencja",
    body: (
      <p>
        Dane konta i biblioteki przechowywane są do usunięcia konta lub żądania usunięcia. Polityka
        kopii zapasowych zostanie uzupełniona przed produkcją.
      </p>
    ),
  },
  {
    title: "6. Odbiorcy",
    body: (
      <p>
        Dostawcy infrastruktury (hosting, LLM / vision API) wyłącznie w zakresie niezbędnym do
        działania funkcji. Brak sprzedaży danych.
      </p>
    ),
  },
  {
    title: "7. Prawa osoby",
    body: (
      <p>
        Dostęp, sprostowanie, usunięcie, ograniczenie, przenoszenie, sprzeciw oraz skarga do PUODO —
        kontakt z administratorem (do uzupełnienia).
      </p>
    ),
  },
  {
    title: "8. Pliki cookie / storage",
    body: (
      <p>
        Aplikacja używa localStorage na token dostępu i preferencje UI (np. tryb widoku listy / siatki).
        W wersji bieżącej nie stosujemy zbędnych tracking cookies.
      </p>
    ),
  },
  {
    title: "9. Kontakt",
    body: <p>Adres e-mail / formularz kontaktowy — do uzupełnienia.</p>,
  },
];

export default function PrivacyPage() {
  return (
    <div className="page-shell">
      <header className="page-header">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-4">
          <h1 className="page-title">Polityka prywatności</h1>
          <Link href="/" className="btn-ghost shrink-0 text-sm font-semibold text-accent">
            Wróć
          </Link>
        </div>
      </header>
      <article className="mx-auto max-w-3xl px-4 py-8 md:px-6">
        <p className="mb-8 rounded-[10px] border border-border bg-accent-muted px-4 py-3 text-sm text-ink text-pretty">
          Poniższy tekst jest szkicem produktowym i <strong>nie stanowi porady prawnej</strong> ani
          finalnej polityki RODO. Przed produkcją wymaga przeglądu prawnego.
        </p>
        <div className="space-y-8">
          {SECTIONS.map((section) => (
            <section key={section.title}>
              <h2 className="font-display mb-2 text-lg font-semibold text-ink">
                {section.title}
              </h2>
              <div className="text-[0.9375rem] leading-relaxed text-ink-muted">{section.body}</div>
            </section>
          ))}
        </div>
      </article>
    </div>
  );
}
