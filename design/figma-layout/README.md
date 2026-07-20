# RAG · Quiet Archive · Layout v2

Interaktywna tablica layoutów (zamiennik pliku Figmy) dla **RAG** (web) i **rag-mobile**.

> Brak podłączonego Figma API / CLI w tym środowisku — deliverable to high-fidelity board HTML + ta specyfikacja. Import do Figmy: otwórz `index.html` → zrzuty frame’ów lub wtyczka **html.to.design**.

## Otwórz tablicę

```bash
# Windows
start RAG/design/figma-layout/index.html

# albo
npx --yes serve RAG/design/figma-layout
```

## Frame’y

| ID | Platforma | Rozmiar | Ekran |
|----|-----------|---------|--------|
| D-01 | Desktop | 1440×900 | Biblioteka (home) |
| D-02 | Desktop | 1440×900 | Osoby |
| D-03 | Desktop | 1440×900 | Chat + rail źródeł |
| D-04 | Desktop | 1440×900 | Folder (siatka) |
| D-05 | Desktop | 1440×900 | Review tożsamości |
| M-01 | Mobile | 390×844 | Biblioteka |
| M-02 | Mobile | 390×844 | Osoby |
| M-03 | Mobile | 390×844 | Rozmowy |
| M-04 | Mobile | 390×844 | Chat (deep) |
| M-05 | Mobile | 390×844 | Profil osoby |
| M-06 | Mobile | 390×844 | Sheet · Źródła |
| M-07 | Mobile | 390×844 | Sheet · Opcje |
| M-08 | Mobile | 390×844 | Folder detail |

## Co jest nowe w v2 (vs obecny UI)

1. **Photo-first foldery** — karty / wiersze z okładką ze zdjęć z folderu (3-tile cover desktop, 2×2 mobile).
2. **Osoby = siatka twarzy** — crop kołowy + badge **Potwierdzone** (zielony) / **Do potwierdzenia** (amber).
3. **Desktop chat: rail źródeł** (≥1280px) — ścieżki i thumb tylko w panelu, nigdy w prozie odpowiedzi.
4. **Bbox monochromatyczny** — czarna ramka + biała linia wewnętrzna (zamiast niebieskiego).
5. **Mobile tab bar** — floating pill, 3 destynacje, ukryty na deep routes; bottom ≥ safe area + 8.
6. **Jedna rodzina sheetów** — scrim 16%, handle, 220 ms ease-out; bez zagnieżdżonych kart.

## Tokeny (z `DESIGN.md`)

| Token | Wartość |
|-------|---------|
| ink / accent | `#000000` |
| ink-muted | `#595959` |
| surface | `#ffffff` |
| soft / sidebar | `#f7f7f8` |
| border | `#e5e5e5` |
| success | `#1a7a4c` / soft `#e8f5ee` |
| warning | `#b45309` / soft `#fef3e7` |
| error | `#c54444` |
| radius | 8–16px, pill 16–20 |
| touch min (mobile) | 44px |
| type | Inter 400–800 |

## Mapowanie na kod

### Web — `RAG/frontend`

| Frame | Plik |
|-------|------|
| Shell | `components/layout/AppShell.tsx`, `Sidebar.tsx`, `MobileTabBar.tsx` |
| D-01 | `app/folders/page.tsx` |
| D-02 | `app/knowledge/page.tsx` |
| D-03 | `app/chat/[id]/page.tsx` + `components/chat/*` |
| D-04 | `app/folders/[id]/page.tsx` |
| D-05 | review modal w folder/file viewer |

### Mobile — `rag-mobile`

| Frame | Plik |
|-------|------|
| Tabs | `app/(tabs)/_layout.tsx` |
| M-01 | `app/(tabs)/folders.tsx` |
| M-02 | `app/(tabs)/people.tsx` |
| M-03 | `app/(tabs)/chats.tsx` |
| M-04 | `app/chat/[id].tsx` |
| M-05 | profil w `people.tsx` |
| Sheets | `src/ui.tsx` → `AnimatedBottomSheet` |

## Import do Figmy (krok po kroku)

1. Otwórz `index.html` w Chrome przy zoom **100%**.
2. Dla każdego frame: zrzut obszaru 1440×900 lub 390×844 (DevTools device mode / rozszerzenie).
3. W Figmie: **File → Place image** lub wtyczka **html.to.design** na URL / pliku lokalnym.
4. Ustaw frame names jak w tabeli (`D-01 · Biblioteka` …).
5. Skopiuj color styles z sekcji 00 · Tokens.

## Zasady UX (nie łamać)

- Certainty before decoration — suggested ≠ confirmed.
- Answer simple; sources aside.
- Photos and faces first-class.
- Brak gradient text, glassmorphism, side-stripe >1px, SaaS metric cards.
- `prefers-reduced-motion` honorowane w implementacji.
