# Backlog GitHub — RAG / GraphRAG

Plan zadań pogrupowany na **DODAJ** (nowe funkcje), **POPRAW** (bugi i usprawnienia), **USUŃ** (sprzątanie).

Stan na podstawie kodu i `docs/graphrag-plan.md`. Większość faz GraphRAG (1–5) jest wdrożona; największe luki to tag encji przy uploadzie, testy, README i sprzątanie techniczne.

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
| P1 | **BUG: `entityTag` przy uploadzie nie działa** | **Wysoki** | Frontend wysyła `?entityTag=`, UI ma pole, ale `FolderController` nie odbiera parametru, `IngestionService` nie ustawia `FileEntity.entityTag`. Ścieżka L1 w `IdentityResolutionService` nigdy się nie wykonuje. |
| P2 | Wydajność `IdentityResolutionService` (O(n²) + LLM) | Wysoki | `findAll()` mentions + LLM per para. Dodać pre-filtr kandydatów i cache wyników matchera (plan Faza 3). |
| P3 | Efekt uboczny na GET `/api/knowledge/entities` | Średni | `consolidateDuplicateEntities()` przy każdym odczycie + N+1. Przenieść merge tylko do `/entities/consolidate-duplicates`. |
| P4 | Ryzyko błędnego auto-merge po samym labelu | Wysoki | Identyczny label → score 0.95 bez weryfikacji twarzy. Narusza kryterium akceptacyjne #2 planu. Wymagać potwierdzenia gdy brak zgodności twarzy. |
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
| R1 | Martwy kod w `IngestionService` | Średni | Nieużywane `VISION_PROMPT`, `visionModel` — zastąpione przez `StructuredVisionExtractor`. Usunąć pole, import, property `vision.prompt`. |
| R2 | Zdublowane importy w `IngestionService` | Niski | Duplikaty importów (~linie 45–71). |
| R3 | Ujednolicić endpoint `DELETE /api/data/clear` | Średni | Tylko `TRUNCATE embeddings`, zostawia osierocone `files` i graf. Realne czyszczenie: `/api/data/clear-all`. Usunąć lub rozgraniczyć. |
| R4 | Rozszerzyć `.gitignore` | Wysoki | `backend/target/`, `frontend/.next/`, artefakty LaTeX, PDF-y lokalne, `.impeccable/`. |
| R5 | Usunąć przypadkowe pliki z repo | Średni | `fix_listings.py`, `revert_listings.py`, `test-data/img.b64`, duplikaty PDF — zweryfikować i wyrzucić lub przenieść poza repo. |
| R6 | `FaceRecognitionClient.isHealthy()` — użyć lub usunąć | Niski | Martwa metoda; powiązać z A3. |

---

## Sugerowana kolejność (sprinty)

### Sprint 1 — krytyczne bugi
- P1 entityTag
- P4 auto-merge
- P6 testy krytycznej ścieżki
- R4 .gitignore

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
