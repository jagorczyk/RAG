# Backlog GitHub — RAG / GraphRAG

Plan zadań pogrupowany na **DODAJ** (nowe funkcje), **POPRAW** (bugi i usprawnienia), **USUŃ** (sprzątanie).

**Roadmap:** [`docs/roadmap.md`](roadmap.md) — fazy A–E, sprinty, metryki sukcesu.

Stan na podstawie kodu i `docs/graphrag-plan.md`. Większość faz GraphRAG (1–5) jest wdrożona.

---

## Zrobione (2026-07-11)

| Issue | Opis | Commit |
|-------|------|--------|
| #22 | `entityTag` przy uploadzie | `70a3d84` |
| #6 | Auto-merge po labelu | `70a3d84` |
| #5 | GET `/entities` side-effect | `70a3d84` |
| #13, #14 | Martwy kod IngestionService | `70a3d84` |
| #16 | `.gitignore` | `70a3d84` |
| — | Relacje „obok” + źródła z grafu | `617552e` |
| — | Odmiany imion PL (Bartka→Bartek) | `037f762` |
| — | Kontekst osób na pliku + follow-up Igor | `09b9b0d` |
| — | Rozszerzone zwroty QueryRouter | `9f1584a` |

---

## NOWE — Roadmap Faza A/B

| Issue | Tytuł | Priorytet | Opis |
|-------|-------|-----------|------|
| #23 | Graf first dla pytań z `@plik` | **Wysoki** | Pytanie `@zdjęcie` idzie przez graf (osoby, fakty) + vector jako uzupełnienie, nie sam vector RAG. |
| #24 | Face-match → CONFIRMED przy ingestii | **Wysoki** | Po rozpoznaniu twarzy auto-link do encji ze statusem CONFIRMED i aliasem. |
| #25 | UI wymuszonego tagu/twarzy przy uploadzie | Średni | Upload bez tożsamości → prompt „kto to?" lub auto-sugestia z face-service. |

---

## DODAJ — nowe funkcje

| # | Tytuł | Priorytet | Opis |
|---|-------|-----------|------|
| A1 | Typ źródła `GRAPH_FACT` i banner niepewności w czacie | Średni | Plan Faza 6d. `SourceDto` ma tylko IMAGE/PDF/TEXT. Dodać `GRAPH_FACT`, przekazywać `confidence` z faktów grafu, pokazać banner „Odpowiedź może być niepełna" gdy pewność niska. Pliki: `SourceDto.java`, `ChatInteractionService.java`, `ChatMessageBubble.tsx`. |
| A2 | Wizualizacja grafu wiedzy w UI | Niski | Panel/strona z encjami i relacjami (współwystępowanie, NEXT_TO, LEFT_OF, RIGHT_OF). Dane z `GraphQueryService`. Plan v2. |
| A3 | Status zdrowia face-service w UI | Średni | `FaceRecognitionClient.isHealthy()` istnieje, ale nie jest używany. Pokazać w panelu „Osoby" lub Spring Actuator `HealthIndicator`. |
| A4 | Czyszczenie osieroconych encji po usunięciu plików | Średni | `deleteFiles()` usuwa mentions/facts, ale `KnowledgeEntity` bez mentions zostają. Dodać sprzątanie pustych encji. |
| A5 | LLM fallback dla `QueryRouter` | Niski | Router oparty wyłącznie na regexach PL — kruchy. Opcjonalny fallback LLM dla zapytań nietrafionych heurystykami. |
| A6 | Filtrowanie HYBRID po folderze/ścieżce | Średni | Plan: „co robi A w folderze Wakacje?" — graf + filtr `path`. Rozszerzyć `GraphQueryService` i router. |
| A7 | Edycja aliasów encji w UI | Średni | API `POST /api/knowledge/entities/{id}/aliases` — sprawdzić czy jest; dodać UI do zarządzania aliasami w `EntitiesPanel`. |

---

## POPRAW — bugi i usprawnienia

| # | Tytuł | Priorytet | Opis |
|---|-------|-----------|------|
| P1 | **BUG: `entityTag` przy uploadzie nie działa** | **Wysoki** | ✅ Done `70a3d84` |
| P2 | Wydajność `IdentityResolutionService` (O(n²) + LLM) | Wysoki | `findAll()` mentions + LLM per para. Dodać pre-filtr kandydatów i cache wyników matchera (plan Faza 3). |
| P3 | Efekt uboczny na GET `/api/knowledge/entities` | Średni | ✅ Done `70a3d84` |
| P4 | Ryzyko błędnego auto-merge po samym labelu | Wysoki | ✅ Done `70a3d84` |
| P5 | Ujednolicić progi face-service i dokumentację | Niski | `face-service/README.md` vs `application.properties` (`0.42` vs `0.55`). |
| P6 | Brakujące testy jednostkowe (Faza 7) | Wysoki | Brak testów: `StructuredVisionExtractor`, `IdentityResolutionService`, `GraphQueryService`, `ChatEntityReferenceService`, `PolishNameMatcher`. E2E: delete pliku → cascade. |
| P7 | Zaktualizować `README.md` | Średni | Rozjazdy: modele (Groq vs Ollama), brak GraphRAG/face-service/knowledge API, błędny endpoint `clear` vs `clear-all`. |
| P8 | Zduplikowany `REFERENCE_PATTERN` | Niski | Ten sam regex w `QueryRouter` i `ChatEntityReferenceService` — wydzielić do wspólnej stałej. |
| P9 | Niespójna konfiguracja `maxResults` | Niski | Default 20 w kodzie, 5 w properties, hardcoded 40/15 w `RetrievalConfiguration`. |
| P10 | Rozszerzyć router o brakujące zwroty PL | Średni | Ciągłe utrzymanie regexów — rozważyć plik konfiguracyjny fraz lub test regresji z pliku CSV. |
| P11 | Obsługa pytań bez znalezionej encji w grafie | Średni | Gdy router trafia w ENTITY_*, ale encja nie istnieje — lepszy fallback do vector RAG z komunikatem. |

---

## USUŃ — sprzątanie

| # | Tytuł | Priorytet | Opis |
|---|-------|-----------|------|
| R1 | Martwy kod w `IngestionService` | Średni | ✅ Done `70a3d84` |
| R2 | Zdublowane importy w `IngestionService` | Niski | ✅ Done `70a3d84` |
| R3 | Ujednolicić endpoint `DELETE /api/data/clear` | Średni | Tylko `TRUNCATE embeddings`, zostawia osierocone `files` i graf. Realne czyszczenie: `/api/data/clear-all`. Usunąć lub rozgraniczyć. |
| R4 | Rozszerzyć `.gitignore` | Wysoki | ✅ Done `70a3d84` |
| R5 | Usunąć przypadkowe pliki z repo | Średni | `fix_listings.py`, `revert_listings.py`, `test-data/img.b64`, duplikaty PDF — zweryfikować i wyrzucić lub przenieść poza repo. |
| R6 | `FaceRecognitionClient.isHealthy()` — użyć lub usunąć | Niski | Martwa metoda; powiązać z A3. |

---

## Sugerowana kolejność (sprinty)

### Sprint 5 — graf first (następny, patrz roadmap.md)
- #23 graf first dla `@plik`
- #24 face-match → CONFIRMED
- A1 GRAPH_FACT w UI (#18)
- P2 wydajność identity (#4)

### Sprint 6 — pewność danych
- #25 UI tag/twarz przy uploadzie
- P6 testy GraphQueryService + E2E
- A4 osierocone encje (#2)
- P7 README (#9)

### Sprint 1 — krytyczne bugi ✅ DONE
- P1 entityTag ✅
- P4 auto-merge ✅
- P6 testy krytycznej ścieżki (częściowo)
- R4 .gitignore ✅

### Sprint 2 — jakość GraphRAG
- P2 wydajność identity
- P3 GET entities
- P11 fallback bez encji
- A1 GRAPH_FACT w UI

### Sprint 3 — dokumentacja i sprzątanie
- P7 README
- R1, R2, R3 martwy kod / endpointy
- P8, P9 drobne refaktory

### Sprint 4 — rozszerzenia
- A3 health face-service
- A4 osierocone encje
- A6 filtr HYBRID po folderze
- A7 aliasy w UI

### Backlog długi
- A2 wizualizacja grafu
- A5 LLM router fallback

---

## Utworzenie issue na GitHubie

```powershell
gh auth login
.\scripts\create-github-issues.ps1
```

Skrypt tworzy etykiety (`add`, `improve`, `remove`, `graphrag`, `bug`, `tech-debt`) i wszystkie issue z tego backlogu.

Dla zadań roadmap Sprint 5–6:

```powershell
.\scripts\create-roadmap-issues.ps1
```

(Utworzone: #23, #24, #25)
