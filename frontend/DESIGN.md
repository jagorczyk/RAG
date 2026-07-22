# Frontend DESIGN — Cogniface

Dokument wiążący dla każdego agenta i każdej zmiany UI w `frontend/`.
Przed edycją layoutu, stylów, routingu lub ekranów **przeczytaj ten plik w całości**.
Kontrakt produktowy (retrieval, źródła, język odpowiedzi) pozostaje w root [`AGENTS.md`](../AGENTS.md).
Konwencje Next.js: [`AGENTS.md`](./AGENTS.md).

Ten dokument zastępuje Quiet Archive (monochrome). Nie wracaj do `#000` / `#fff` jako accent/surface bez jawnej decyzji użytkownika.

Nazwa produktu w UI: **Cogniface**.

---

## Cel produktu (UI)

Aplikacja to prywatna biblioteka zdjęć z czatem GraphRAG: użytkownik przegląda foldery i osoby, pyta po polsku o treść zdjęć i dostaje krótką, pewną odpowiedź ze źródłami — bez halucynacji tożsamości i bez ścieżek plików w treści wiadomości.

Sukces UX: spokojny, czytelny interfejs w palecie navy/blue; landing z żywym kolażem; sidebar z jasną hierarchią; konwersacja z panelem źródeł i osób przytoczonych.

---

## Stack (nie zmieniać bez powodu)

| Warstwa | Wybór |
|---------|--------|
| Framework | Next.js 16 App Router, React 19 |
| Style | Tailwind CSS 4 + tokeny CSS w `app/globals.css` |
| Motions | `motion` (`motion/react`) |
| Ikony | `lucide-react` |
| UI kit | Własne komponenty w `components/ui/` — **bez** shadcn/Radix |
| Auth klient | JWT w `localStorage` (`lib/auth.ts`) |

Czytaj lokalne docs Next w `node_modules/next/dist/docs/` przed nietypowymi API.

---

## Brand i tokeny

### Paleta główna

| Rola | Hex | Token CSS (mapowanie) |
|------|-----|------------------------|
| Tło app / soft | `#F9F7F7` | `--surface`, `--soft`, `--on-accent` |
| Sidebar / miękkie ramki | `#DBE2EF` | `--sidebar`, delikatne bordery / chipy |
| Accent (CTA, aktywny nav, focus) | `#3F72AF` | `--accent`, `--accent-hover` (ciemniejszy navy-blue) |
| Tekst / ikony | `#112D4E` | `--ink` |

### Tokeny pochodne (sugerowane)

```
--ink:            #112D4E
--ink-muted:      #3F72AF @ ~70% mieszane z #112D4E  → ok. #4A6B8A
--surface:        #F9F7F7
--surface-raised: #FFFFFF          /* karty / panele uniesione nad soft */
--sidebar:        #DBE2EF
--soft:           #F9F7F7
--border:         #DBE2EF
--border-strong:  #3F72AF @ 35%
--accent:         #3F72AF
--accent-hover:   #2E5A8F
--accent-subtle:  #DBE2EF
--accent-muted:   #E8EEF6
--on-accent:      #F9F7F7
--success:        #1a7a4c
--warning:        #b45309
--error:          #c54444
--info:           #3F72AF
```

Semantyczne success/warning/error mogą zostać; info i accent spójne z `#3F72AF`.

### Motyw

- **Light-only.** Brak dark mode. `ThemeContext` / `data-theme` pozostaje `"light"`.
- Unikać: purple-on-white, glow, wielowarstwowe cienie, zaokrąglone „pill clusters”, emoji w UI.

### Typografia

Nie używać Inter jako display. Dwie rodziny (Google Fonts przez `next/font`):

| Rola | Rodzina | Użycie |
|------|---------|--------|
| Display / headlines | **Bricolage Grotesque** | brand, landing headline, tytuły stron |
| Body / UI | **Manrope** | treść, formularze, nav, chat |

Skala orientacyjna: display ~2rem / 700; headline ~1.25rem / 650; title ~1.0625rem / 600; body ~0.9375rem / 400; label ~0.75rem / 600.

### Motion

- Landing kolaż: parallax kursora (desktop), delikatny float (mobile).
- Mikrointerakcje: hover nav, pojawianie wiadomości, sheet — krótkie (`~150–280ms`), ease-out.
- Nie animować wszystkiego; max 2–3 intentional motions na ekran landing.

### Logo / marka

Hero-level brand na landingu (nazwa produktu + logo z `public/`). W app shell logo w górze sidebara, bez przejmowania hierarchii treści.

---

## Information architecture

```
Niezalogowany
 ├─ /login          Landing auth (kolaż + panel)
 ├─ /register       Ten sam layout, formularz rejestracji
 └─ /privacy        Polityka prywatności (publiczna)

Zalogowany (AppShell + Sidebar)
 ├─ /folders        Biblioteka
 ├─ /folders/[id]   Zawartość folderu
 ├─ /knowledge      Osoby
 ├─ /knowledge/[id] Album osoby
 ├─ /knowledge/graph Graf relacji (entry dodatkowy)
 ├─ /chats          Lista rozmów (mobile)
 ├─ /chat/[id]      Konwersacja
 └─ /settings       Ustawienia / profil
```

`/` — gate: niezalogowany → `/login`; zalogowany → ostatni chat lub `/folders`.

---

## Landing / auth

### Layout

Split desktop:

| Lewa (~55–60%) | Prawa (~40–45%) |
|----------------|-----------------|
| Animowany kolaż zdjęć | Panel logowania / rejestracji |

Mobile: kolaż jako krótki header / tło pod panelem albo ukryty za panelem — priorytet ma formularz. Parallax kursora tylko desktop (`pointer: fine`).

### Kolaż (lewa)

- **Animowany kolaż**: warstwy zdjęć z parallaxem kursora (perspective / translateZ), float + wolne powiększanie.
- Potem przejście do **mockupu iPhone (iOS)**: Dynamic Island, status bar 9:41, large title, search, tab bar, home indicator.
- Pętla kolaż ↔ telefon. `prefers-reduced-motion`: od razu iPhone.
- Assets w `public/collage/`. Obrazy dekoracyjne — nie są źródłami użytkownika.

### Panel (prawa)

- Brand + krótki podtytuł po polsku.
- Formularz email + hasło (jak obecne API `login` / `register`).
- Primary CTA: „Zaloguj się” / „Utwórz konto”.
- Secondary: **„Kontynuuj z Google”** (OAuth).
- Linki: przełączanie login ↔ register; **Polityka prywatności** → `/privacy`.
- Błędy walidacji pod polami; loading na przycisku.

### Google OAuth

Wymagany element UX. Backend obecnie **nie** ma OAuth — przy implementacji:

1. Najpierw endpoint / flow OAuth po stronie Spring.
2. Potem podpięcie CTA (redirect lub token exchange).
3. **Zakaz** fake „sukcesu” logowania bez prawdziwego flow.

Do czasu backendu: przycisk widoczny, disabled z krótkim hintem albo pełny flow jeśli endpoint już istnieje.

---

## App shell — Sidebar

Hierarchia (desktop rail):

```
┌─────────────────────┐
│ Logo                │
├─────────────────────┤
│ GÓRA                │
│  Biblioteka         │  → /folders
│  Osoby              │  → /knowledge
├─────────────────────┤
│ ŚRODEK              │
│  [+ Nowa rozmowa]   │
│  Dzisiaj            │
│  Ostatnie 7 dni     │
│  Starsze            │  (lista /chat/[id])
├─────────────────────┤
│ DÓŁ                 │
│  Profil (avatar+mail)│
│  Ustawienia         │  → /settings
└─────────────────────┘
```

- Aktywny item: accent `#3F72AF` (tło subtle lub indicator), nie czarny pasek.
- Grupowanie czatów: Dzisiaj / Ostatnie 7 dni / Starsze (zachować logikę z `Sidebar.tsx`).
- Wylogowanie: w `/settings` lub menu profilu na dole — nie jako główny nav u góry.
- Collapse sidebara na desktop OK; na mobile drawer + **MobileTabBar**: Biblioteka | Osoby | Rozmowy. Profil/ustawienia z dołu sidebara / overflow.

---

## Ekrany

### Biblioteka (`/folders`, `/folders/[id]`)

- Lista folderów użytkownika; wejście w folder → pliki / zdjęcia.
- Tryby widoku: `list` | `grid` (`useViewMode`, persist w `localStorage`).
- Upload, statusy ingestu, akcje pliku — bez zmiany kontraktu backendu.
- Pusty stan: krótki copy PL + CTA dodania folderu / uploadu.

### Osoby (`/knowledge`, `/knowledge/[id]`)

- Podgląd osób: karty / avatary / kanoniczne imiona z grafu.
- `[id]`: album / wzmianki osoby.
- `/knowledge/graph`: mapa relacji — **dodatkowy** widok, nie główny entry z sidebara.
- Identity review / tagowanie: istniejące panele; UI nie zgaduje imion.

### Konwersacja (`/chat/[id]`)

- Wątki wiadomości po polsku; composer z `@plik` jeśli już wspierane.
- Przy odpowiedzi asystenta:
  - **Źródła** — sheet/panel (ścieżki, miniatury, evidence reasons).
  - **Osoby przytoczone** — osoby faktycznie wspierające treść odpowiedzi (z evidence / atrybucji), **nie** cały katalog `/knowledge`.
- Ścieżki i nazwy plików **tylko** w UI źródeł — usuwać z bubble jeśli model je wstawi (`removeTechnicalReferences` po stronie API / klient).
- `uncertain` → jasny komunikat, puste źródła OK.

### Ustawienia (`/settings`) — do rozwinięcia

Minimalny zakres v1:

- Email (i nazwa, jeśli API `me` zwraca)
- Link do `/privacy`
- Wylogowanie
- Ewentualnie wersja / język UI (PL)

Bez: billing, dark mode, zaawansowanych flag eksperymentalnych — dopóki nie będą w produkcie.

---

## Polityka prywatności

### UX

- Trasa publiczna: `/privacy` (poza AppShell auth gate — dostępna bez logowania).
- Link obowiązkowy: landing login/register + ustawienia.

### Szkic treści (PL) — roboczy, do weryfikacji prawnej

> **Disclaimer:** poniższy tekst jest szkicem produktowym, **nie stanowi porady prawnej** ani finalnej polityki RODO. Przed produkcją wymaga przeglądu prawnego.

**1. Administrator**  
Administratorem danych jest operator aplikacji Cogniface [uzupełnić: nazwa, adres, e-mail kontaktowy].

**2. Zakres danych**  
- Dane konta: adres e-mail, hasło (hash) lub identyfikator konta Google przy OAuth.  
- Treści użytkownika: przesłane zdjęcia i dokumenty, foldery, tagi osób, konwersacje.  
- Dane techniczne: token JWT w `localStorage`, logi techniczne niezbędne do działania usługi.  
- Dane biometryczne / wizerunek: zdjęcia twarzy służą do detekcji, face-match i budowy grafu wiedzy **wyłącznie w bibliotece użytkownika**.

**3. Cele przetwarzania**  
Świadczenie usługi biblioteki i czatu opartego o graf; logowanie; bezpieczeństwo; poprawa jakości rozpoznawania wyłącznie w zakresie konta użytkownika.

**4. Podstawy**  
Wykonanie umowy (świadczenie usługi), zgoda (tam gdzie wymagana, np. marketing — domyślnie brak), prawnie uzasadniony interes (bezpieczeństwo, nadużycia) — do doprecyzowania prawnie.

**5. Retencja**  
Dane konta i biblioteki do usunięcia konta / żądania usunięcia. Kopie zapasowe według polityki operatora [uzupełnić TTL].

**6. Odbiorcy**  
Dostawcy infrastruktury (hosting, LLM / vision API) wyłącznie w zakresie niezbędnym do działania funkcji. Brak sprzedaży danych.

**7. Prawa osoby**  
Dostęp, sprostowanie, usunięcie, ograniczenie, przenoszenie, sprzeciw, skarga do PUODO — [kontakt].

**8. Pliki cookie / storage**  
Aplikacja używa `localStorage` na token dostępu i preferencje UI (np. tryb widoku). Brak zbędnych tracking cookies w v1.

**9. Kontakt**  
[uzupełnić adres e-mail / formularz].

Implementacja strony: static / MDX / zwykły `page.tsx` z sekcjami zgodnymi z powyższym szkicem.

---

## Mapowanie tras (kontrakt)

| Obszar | Route | Auth |
|--------|-------|------|
| Login landing | `/login` | public |
| Rejestracja | `/register` | public |
| Privacy | `/privacy` | public |
| Biblioteka | `/folders`, `/folders/[id]` | required |
| Osoby | `/knowledge`, `/knowledge/[id]`, `/knowledge/graph` | required |
| Chat lista | `/chats` | required |
| Chat wątek | `/chat/[id]` | required |
| Ustawienia | `/settings` | required |

Istniejące klienty API: `lib/auth.ts`, `lib/api.ts`, `lib/knowledge-api.ts`. DESIGN nie wymyśla nowych endpointów poza:

- Google OAuth (wymagane do CTA),
- ewentualnie rozszerzenie `me` pod ustawienia (nazwa wyświetlana).

---

## Stany wspólne

- **Loading:** skeleton lub `Loading` / `LoadingBar` — bez pustego migania layoutu.
- **Empty:** `EmptyState` z jednym CTA.
- **Error:** krótki komunikat PL; retry gdy ma sens.
- **Touch:** min. 44px na mobile.

---

## Wolno / nie wolno

**Wolno**

- Mapować kolory na istniejące nazwy tokenów (`--ink`, `--accent`, …).
- Używać `motion/react` do kolażu i mikrointerakcji.
- Dodawać `/settings`, `/privacy`, CTA Google zgodnie z tym dokumentem.
- Rozszerzać panel konwersacji o osoby przytoczone obok źródeł.

**Nie wolno**

- Wracać do Quiet Archive (czarny accent, Inter-only monochrome) bez decyzji użytkownika.
- Hardcodować list imion, kolorów ubrań ani fraz do „inteligentnego” UI routingu pytań.
- Wstawiać ścieżek / nazw plików do treści bubble odpowiedzi.
- Traktować niepewne źródła jako pewne w UI (puste źródła + `uncertain` OK).
- Ruszać face pipeline, progów bbox, cache ani kolejności detekcji przy czysto UI pracach.
- Instalować shadcn/Radix „przy okazji”.
- Fake’ować Google login bez backendu OAuth.
- Budować dashboardowy clutter na landingu (statystyki, chipy, promo overlays na kolażu).

---

## Mapowanie na kod (stan docelowy)

| Obszar | Główne miejsca |
|--------|----------------|
| Tokeny | `app/globals.css`, ten plik |
| Shell | `components/layout/AppShell.tsx`, `Sidebar.tsx`, `MobileTabBar.tsx` |
| Landing | `app/login/page.tsx`, `app/register/page.tsx` (+ wspólny layout kolażu) |
| Privacy | `app/privacy/page.tsx` (do dodania) |
| Biblioteka | `app/folders/**`, `hooks/useViewMode.ts` |
| Osoby | `app/knowledge/**`, `components/knowledge/**` |
| Chat | `components/chat/ChatInterface.tsx`, `ChatMessageBubble.tsx` |
| Ustawienia | `app/settings/page.tsx` (do dodania) |
| Auth API | `lib/auth.ts` |

---

## Kolejność wdrożenia (dla agenta implementującego)

1. Tokeny w `globals.css` + fonty w `layout.tsx`.
2. Landing split + kolaż parallax + link privacy.
3. Strona `/privacy` ze szkicu.
4. Sidebar: góra Biblioteka/Osoby, środek czaty, dół profil/ustawienia.
5. `/settings` v1.
6. Chat: UI osób przytoczonych obok źródeł.
7. Google OAuth — po gotowości backendu.
