# Roadmap — RAG / GraphRAG

Żywy plan rozwoju projektu. Uzupełnia [`graphrag-plan.md`](graphrag-plan.md) (architektura MVP) oraz [`issues-backlog.md`](issues-backlog.md) (konkretne issue).

**Ostatnia aktualizacja:** 2026-07-11  
**Branch:** `feature/graphrag`

---

## Stan obecny

| Obszar | Status |
|--------|--------|
| Vector RAG (txt/pdf/obrazy) | ✅ Działa |
| Structured vision → graf (encje, fakty, relacje) | ✅ Działa |
| Face-service (InsightFace) | ✅ Działa |
| QueryRouter (regex PL) | ✅ Rozbudowany |
| GraphQueryService (aktywności, relacje, współwystępowania, pliki) | ✅ Działa |
| Panel Osoby + review tożsamości | ✅ Działa |
| Kontekst grafu per plik + follow-up „co to za mężczyzna?” | ✅ `09b9b0d` |
| Odmiany imion PL (Bartka → Bartek) | ✅ `037f762` |
| Relacje „obok” dwukierunkowe + źródła tylko z grafu | ✅ `617552e` |

---

## Zrobione (2026-07-11)

| Commit | Co |
|--------|-----|
| `9f1584a` | Rozszerzone zwroty PL w `QueryRouter` + fix SQL co-occurrence |
| `70a3d84` | Fix `entityTag`, auto-merge, sprzątanie ingest, `.gitignore` |
| `617552e` | Relacje „obok” dwukierunkowe, źródła z grafu |
| `037f762` | Odmiany imion (`Bartka` → `Bartek`) |
| `09b9b0d` | Kontekst osób na pliku + follow-up tożsamości (Igor) |

**Zamknięte issue GitHub:** #5, #6, #13, #14, #16, #22

---

## Roadmap — fazy jakości GraphRAG

### Faza A — Tożsamość i pewność (najwyższy ROI)

Cel: każda podpisana osoba w grafie jest widoczna w czacie, nie tylko jako „mężczyzna”.

| # | Zadanie | Priorytet | Issue | Status |
|---|---------|-----------|-------|--------|
| A.1 | `entityTag` przy uploadzie działa end-to-end | 🔴 | #22 | ✅ Done |
| A.2 | Auto-merge tylko z potwierdzeniem twarzy / bez agresywnego label-match | 🔴 | #6 | ✅ Done |
| A.3 | Kontekst `[Osoby z grafu wiedzy na pliku]` dla `@plik` i follow-up | 🔴 | — | ✅ Done |
| A.4 | Face-match → CONFIRMED + alias przy ingestii | 🔴 | #24 | ✅ Done |
| A.5 | UI: wymuszony tag lub potwierdzenie twarzy przy uploadzie | 🟠 | #25 | ⬜ Todo |
| A.6 | Banner niepewności + typ źródła `GRAPH_FACT` | 🟠 | #18 | ✅ Done |

### Faza B — Routing i kontekst (graf first)

Cel: pytania o osoby/fakty idą do grafu, nie do vector RAG.

| # | Zadanie | Priorytet | Issue | Status |
|---|---------|-----------|-------|--------|
| B.1 | Pytania z `@plik` → **graf first**, vector jako uzupełnienie | 🔴 | #23 | ✅ Done |
| B.2 | Router LLM jako fallback gdy regex → DOCUMENT | 🟠 | #3 | ⬜ Todo |
| B.3 | Fallback gdy encja nie znaleziona w grafie | 🟠 | #12 | ⬜ Todo |
| B.4 | Filtrowanie HYBRID po folderze/ścieżce | 🟡 | #20 | ⬜ Todo |
| B.5 | Plik konfiguracyjny fraz PL zamiast hardcoded regex | 🟡 | — | ⬜ Todo |

### Faza C — Wydajność i jakość danych

| # | Zadanie | Priorytet | Issue | Status |
|---|---------|-----------|-------|--------|
| C.1 | `IdentityResolutionService` — pre-filtr + cache LLM | 🔴 | #4 | ⬜ Todo |
| C.2 | GET `/entities` bez side-effect merge | 🟠 | #5 | ✅ Done |
| C.3 | Testy: extractor, graph query, identity, E2E delete | 🔴 | #8 | 🟡 Częściowo |
| C.4 | Czyszczenie osieroconych encji po delete pliku | 🟡 | #2 | ⬜ Todo |
| C.5 | Ujednolicenie progów face-service | 🟢 | #7 | ⬜ Todo |

### Faza D — UX i dokumentacja

| # | Zadanie | Priorytet | Issue | Status |
|---|---------|-----------|-------|--------|
| D.1 | README: GraphRAG, face-service, knowledge API | 🟠 | #9 | ⬜ Todo |
| D.2 | Edycja aliasów encji w UI | 🟡 | #21 | ⬜ Todo |
| D.3 | Health face-service w panelu Osoby | 🟡 | #19 | ⬜ Todo |
| D.4 | Wizualizacja grafu relacji | 🟢 | #1 | ⬜ Todo |

### Faza E — Sprzątanie techniczne

| # | Zadanie | Priorytet | Issue | Status |
|---|---------|-----------|-------|--------|
| E.1 | Martwy kod `visionModel` / `VISION_PROMPT` | 🟠 | #13 | ✅ Done |
| E.2 | Zdublowane importy `IngestionService` | 🟢 | #14 | ✅ Done |
| E.3 | `.gitignore` artefaktów build / LaTeX | 🔴 | #16 | ✅ Done |
| E.4 | Endpoint `DELETE /api/data/clear` vs `clear-all` | 🟠 | #15 | ⬜ Todo |
| E.5 | Usunąć przypadkowe pliki z repo | 🟡 | #17 | ⬜ Todo |

---

## Sprinty (propozycja)

### Sprint 5 — Graf first (następny)
- **B.1** Graf first dla `@plik`
- **A.4** Face-match → CONFIRMED przy ingestii
- **A.6** GRAPH_FACT w UI (#18)
- **C.1** Wydajność identity (#4)

### Sprint 6 — Pewność danych
- **A.5** UI tag/twarz przy uploadzie
- **C.3** Testy GraphQueryService + E2E
- **C.4** Osierocone encje (#2)
- **D.1** README (#9)

### Sprint 7 — Routing i UX
- **B.2** LLM router fallback (#3)
- **B.3** Fallback bez encji (#12)
- **D.2** Aliasy w UI (#21)
- **D.3** Health face-service (#19)

### Backlog długi
- **B.4** Filtr HYBRID po folderze (#20)
- **D.4** Wizualizacja grafu (#1)
- **B.5** Konfig fraz PL

---

## Metryki sukcesu (kryteria akceptacyjne)

1. Upload z tagiem „Igor" → pytanie o czynności Igora → odpowiedź ze wszystkich jego zdjęć
2. „Co to za mężczyzna?" po `@zdjęciu` → **Igor**, nie „inna osoba"
3. „Kto siedzi obok Bartka?" → imiona z grafu, jedno trafne źródło
4. Podobne osoby bez tagu → sugestia PENDING, nie auto-merge
5. Usunięcie pliku → cascade mentions + facts + embeddings

---

## Powiązane dokumenty

| Plik | Opis |
|------|------|
| [`docs/graphrag-plan.md`](graphrag-plan.md) | Architektura MVP, model danych, fazy 0–7 |
| [`docs/issues-backlog.md`](issues-backlog.md) | Tabela issue ADD/IMPROVE/REMOVE |
| [GitHub Issues](https://github.com/jagorczyk/RAG/issues) | Śledzenie zadań |
| `scripts/create-github-issues.ps1` | Masowe tworzenie issue |
| `scripts/create-roadmap-issues.ps1` | Issue roadmap Sprint 5–6 (#23–#25) |

---

## Jak aktualizować roadmap

1. Po zakończeniu zadania: zmień status na ✅ w tabeli + dopisz commit w sekcji „Zrobione"
2. Zamknij issue na GitHubie z linkiem do commita
3. Nowe epiki dopisuj w odpowiedniej fazie (A–E)
