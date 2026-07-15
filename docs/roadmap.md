# Roadmap RAG / GraphRAG

Plan rozwoju systemu hybrydowego RAG z grafem wiedzy. Stan dokumentu: **2026-07-12**.

## Stan obecny

Rdzeń GraphRAG działa end-to-end:

- ingest tekstów, PDF-ów i obrazów z embeddingami;
- strukturalna analiza obrazów i zapis encji, wzmianek, faktów oraz relacji;
- rozpoznawanie twarzy przez `face-service`;
- tagowanie encji przy uploadzie i automatyczne potwierdzenie face-match;
- sugestie tożsamości wymagające potwierdzenia;
- router zapytań po polsku oraz routing graf-first dla pytań o `@plik`;
- pytania o aktywności, pliki, współwystępowanie i relacje są obsługiwane przez graf;
- kontekst encji dla follow-upów, odmiany polskich imion i źródła `GRAPH_FACT`;
- UI przeglądu tożsamości oraz prompt przy uploadzie bez potwierdzonej tożsamości;
- cache analizy obrazu (vision i face) oraz testy regresyjne.

Weryfikacja lokalna: `./mvnw.cmd test` — **85 testów, 0 błędów**.

## Zakończone etapy

| Etap | Zakres | Status |
|---|---|---|
| A | Model danych, ekstrakcja vision i zapis faktów | Zrobione |
| B | Identity resolution, tagi, sugestie i face-match | Zrobione |
| C | GraphQueryService i kontekst grafu w czacie | Zrobione |
| D | QueryRouter, graf-first i follow-upy | Zrobione |
| E | UI review tożsamości, `GRAPH_FACT`, prompt uploadu | Zrobione |
| F | Cache analizy i testy ścieżek krytycznych | Zrobione częściowo |

## Plan kolejnych prac

### Sprint 6 — jakość danych i odporność

Priorytet: wysoki. Celem jest bezpieczne zarządzanie cyklem życia danych.

1. **Sprzątanie osieroconych encji po usunięciu pliku**
   - po usunięciu ostatniej wzmianki usuwać encję bez aktywnych wzmianek;
   - zachować encje przypisane ręcznie, jeśli mają alias użytkownika;
   - dodać test: upload → delete file → brak mention/fact/face embedding i brak osieroconej encji.

2. **Pełne testy GraphRAG**
   - `StructuredVisionExtractor`: poprawny JSON i fallback;
   - `GraphQueryService`: agregacja, relacje, współwystępowanie i filtr pliku;
   - `IdentityResolutionService`: progi, sugestie i merge;
   - E2E rename/move/delete.

3. **Wydajność identity resolution**
   - ograniczyć kandydatów po typie, folderze i tokenach etykiety;
   - zachować limit kandydatów LLM;
   - dodać cache wyników dopasowania i pomiar czasu ingestu.

4. **Ujednolicenie konfiguracji**
   - jeden zestaw progów face-service w README, properties i kodzie;
   - jedno źródło prawdy dla `maxResults` retrieval.

### Sprint 7 — fallback i UX

Priorytet: średni.

1. Fallback do vector RAG, gdy router rozpozna encję, ale graf jej nie zawiera.
2. Filtrowanie HYBRID po folderze i ścieżce.
3. Edycja aliasów encji w panelu Osoby.
4. Status zdrowia `face-service` w UI.
5. Aktualizacja README o GraphRAG, face-service i pełne API.

### Backlog długoterminowy

- LLM fallback dla niejednoznacznych pytań routera;
- wizualizacja grafu relacji;
- konfiguracja fraz routera poza kodem;
- face embeddings dla przypadków bez wystarczającego opisu vision;
- podsumowania społeczności grafu przy dużych zbiorach.

## Kryteria akceptacji

1. Upload dwóch zdjęć z tagiem „Igor” i pytanie o jego czynności zwraca fakty z obu zdjęć.
2. Pytanie „co to za mężczyzna?” po `@zdjęciu` zwraca potwierdzoną encję, jeśli face-match ją potwierdził.
3. Pytanie o osoby obok encji zwraca wyłącznie relacje z grafu i właściwe źródła.
4. Niepewne dopasowanie tworzy sugestię `PENDING`, bez automatycznego potwierdzenia.
5. Usunięcie pliku usuwa jego embeddingi, wzmianki, fakty, face embeddings i nieużywane encje.
6. Pytania dokumentowe nadal działają przez vector RAG.

## Kolejność realizacji

```text
Sprint 6.1  orphan cleanup + test delete
Sprint 6.2  testy GraphQuery/identity/extractor
Sprint 6.3  pre-filter i cache identity
Sprint 6.4  konfiguracja progów i retrieval
Sprint 7.1  fallback bez encji + HYBRID path
Sprint 7.2  aliasy, health face-service i README
Backlog    wizualizacja, LLM router, face embeddings

```

## Etap H - trafnosc zrodel dla ogolnych zapytan

Cel: poprawic precision zrodel dla otwartych pytan, bez zamknietego slownika domenowego.

- `QueryPlanner` wyodrebnia encje, zakres plikow, relacje, warunek wizualny i oczekiwana liczbe wynikow; niejednoznaczne plany moga korzystac z fallbacku LLM.
- Recall jest oddzielony od wyboru koncowego: graf i vector RAG dostarczaja szeroka pule kandydatow, a `DynamicVisualMatcher` rozstrzyga `MATCH`, `NO_MATCH` lub `UNCERTAIN`.
- Pytania typu „na ktorym zdjeciu”, „gdzie widac” i „spelnia warunek” trafiaja do walidacji semantycznej zamiast do zwyklego `ENTITY_FILES`.
- Pelny structured vision context jest zapisywany w `FileEntity` i dostepny w kontekscie grafu obok wygladu, obiektow, czynnosci, sceny, napisow i relacji.
- Dowody rozrozniaja status dopasowania oraz confidence warunku; zrodla nie sa tworzone wylacznie na podstawie wzmianki nazwy pliku w odpowiedzi modelu.
- Progi recall, liczby kandydatow i analiz vision sa konfigurowalne w `application.properties`.

Kryteria akceptacji etapu:

1. Pytanie o dowolna ceche wizualna nie potwierdza niezwiÄ…zanych zdjec.
2. Inna encja i cecha, ktora nie wystepuje w kodowym slowniku, dziala przez ten sam planner.
3. Pytania dokumentowe i relacyjne zachowuja odpowiednio vector RAG i grafowe dowody.
4. Brak wystarczajacego dowodu zwraca `UNCERTAIN`, a nie przypadkowe top-k.
5. Testy regresyjne obejmuja routing, planner, walidacje wizualna i brak promocji niepewnosci do `MATCH`.

## Dokumenty powiązane

- [`graphrag-plan.md`](graphrag-plan.md) — architektura i założenia MVP;
- [`issues-backlog.md`](issues-backlog.md) — lista zadań i issue;
- [`../README.md`](../README.md) — uruchomienie oraz API.
