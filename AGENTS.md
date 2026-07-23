# Zasady systemowe RAG / GraphRAG

Dokument wiążący dla każdego agenta i każdej zmiany w repozytorium.
Definiuje cel produktu, przepływ odpowiedzi oraz normy decyzyjne.

`frontend/AGENTS.md` dotyczy wyłącznie konwencji Next.js — nie umieszczaj tu
reguł frameworka frontendu.

---

## Cel produktu

System zbiera **maksymalnie dużo faktów ze zdjęć**, klasyfikuje **ludzi po
imionach** (tagi użytkownika, face-match, identity resolution) i odpowiada na
**jak najwięcej logicznie sformułowanych pytań** wyłącznie na podstawie pewnych
dowodów z biblioteki użytkownika.

Sukces: konkretna, naturalna odpowiedź po polsku (zwykle 1–3 zdania), oparta na pewnych
źródłach — z ubioru, wyglądu, czynności i relacji, gdy są w dowodach — bez halucynacji
tożsamości i bez wymyślonych szczegółów.

---

## Przepływ zapytań

Routing trybu retrieval **wyłącznie przez LLM `QueryPlanner`** (wariant A).
Zakazane są regexy, listy fraz, list imion oraz `contains` służące do
rozpoznawania intencji pytania w kodzie aplikacji.

Dozwolone warunki techniczne: walidacja wejścia, bezpieczeństwo, MIME, limity
i progi pewności, operacje na zbiorach z wyjścia planera (`entities`,
`fileScope`, `entityMatchMode`).

```
Pytanie → QueryPlanner (LLM)
  ├─ visualCondition / VISUAL_VALIDATION → DynamicVisualMatcher → claimy
  ├─ pytanie o ludzi (PERSON) → GRAPH (osoby + relacje) → AI generuje odpowiedź
  └─ pytanie nie o ludzi → HYBRID (semantyka + leksyka) → AI generuje odpowiedź
```

### Obowiązujący przepływ danych end-to-end

Poniższa kolejność jest częścią kontraktu systemu. Nie wolno omijać etapów ani
zastępować ich skrótami opartymi na nazwach plików, pojedynczych encjach lub
sztywnych frazach.

```
OBRAZ
  → zapis pliku i statusów analizy
  → detekcja twarzy i techniczne face anchors
  → StructuredVisionExtractor
  → normalizacja UTF-8 + naprawa wyłącznie składni JSON
  → pełny VisionResultDto
  → wzmianki, identity resolution, fakty i relacje
  → kanoniczny dokument ImageEmbeddingDocumentBuilder
  → embedding tekstowy (pgvector)

PYTANIE
  → QueryPlanner
  → GRAPH / HYBRID / VISUAL_VALIDATION
  → zamknięty pakiet pewnych dowodów
  → redukcja kontekstu tylko przy przekroczeniu limitu tokenów
  → naturalna odpowiedź LLM
  → atrybucja źródeł względem GOTOWEJ odpowiedzi
  → treść + sources + evidence + uncertain
```

#### 1. Ingest obrazu i normalizacja vision

1. Oryginalny obraz, MIME, hash i statusy analizy są zapisywane przed projekcją
   wiedzy.
2. Detektor twarzy tworzy bbox i techniczne `face_anchor_id`. Anchory służą
   wyłącznie do połączenia opisu `person N` z właściwą twarzą; nie są imionami
   ani treścią odpowiedzi.
3. `StructuredVisionExtractor` otrzymuje cały obraz i zwraca możliwie pełny
   opis w języku polskim.
4. Każda odpowiedź vision musi przejść przez selektywną normalizację
   `Utf8MojibakeRepair` **przed** parsowaniem, zapisem w bazie i budową
   embeddingu. Naprawiane są tylko poprawne sekwencje bajtów UTF-8 omyłkowo
   odczytane jako ISO-8859-1/Windows-1252. Nie wolno konwertować całego napisu,
   ponieważ poprawne i uszkodzone polskie znaki mogą występować razem.
5. Dozwolona naprawa JSON dotyczy wyłącznie oczywistych błędów składniowych
   modelu, np. przedwczesnego `}` albo brakującego `{` między encjami. Naprawa
   nie może dopisywać obserwacji ani zmieniać znaczenia danych.
6. Po nieudanym pierwszym parsowaniu dozwolona jest jedna próba uzyskania
   czystego JSON. Jeżeli nadal się nie powiedzie, zapisujemy znormalizowany
   tekst jako materiał do wyszukania i oznaczamy projekcję jako niepełną.
7. `CURRENT` oznacza zakończoną projekcję strukturalną. `FAILED` lub odzyskany
   tekst nie stają się z tego powodu pewnym faktem grafowym.

#### 2. Projekcja tożsamości i grafu

1. Vision tworzy stabilne etykiety techniczne (`person 1`), bbox, wygląd,
   czynności, obiekty, napisy i relacje. Nie nadaje ludziom imion.
2. Imię pochodzi wyłącznie z tagu użytkownika, face-match lub identity
   resolution. Geometria/anchor wiąże obserwacje vision z odpowiednią
   wzmianką osoby.
3. Każdy szczegół osoby jest zachowywany zarówno w polach wzmianki, jak i —
   gdy reprezentuje samodzielne twierdzenie — jako `Fact` z `statementPl`,
   `evidenceOrigin`, `confidence` i `filePath`.
4. Relacje osoba–osoba oraz osoba–obiekt zachowujemy jako krawędzie/fakty.
   Obiekty i zwierzęta mogą wzbogacać scenę, ale nie uruchamiają trybu GRAPH.
5. Wzmianka nieobecna w aktualnym wyniku strukturalnym nie może pozostać
   pewna; musi zostać odrzucona albo ponownie potwierdzona dowodem.

#### 3. Kanoniczny dokument embeddingu

1. `ImageEmbeddingDocumentBuilder` jest jedynym formatem tekstowego dokumentu
   obrazu. Łączy scenę, pełne `vision_observations`, widoczne napisy,
   uczestników z kanonicznymi imionami, wygląd, czynności, sąsiadów, obiekty,
   relacje i pewne fakty.
2. Nie wolno tworzyć osobnego, skróconego opisu kosztem danych z vision ani
   wkładać całego JSON jako escaped string w `scene.summary`.
3. Jeśli starszy surowy payload da się bezpiecznie odzyskać, jego
   `scene_summary` zasila podsumowanie, a pełny obiekt trafia do
   `vision_observations`. Dzięki temu żaden poprawny detal nie ginie.
4. Dokument i wszystkie jego pola tekstowe muszą być znormalizowane przed
   embeddingiem. Mojibake nie może trafić ani do pgvector, ani do leksyki.
5. Zmiana imienia, naprawa kodowania lub odbudowa dokumentu uruchamia wyłącznie
   `CanonicalEmbeddingRefreshService`. Nie wolno z tego powodu ponownie
   uruchamiać vision, detekcji twarzy ani face-match.
6. `path`, `filename`, `owner_id` i `source_type` są metadanymi retrieval oraz
   mapowania źródeł. Nie wolno używać ich jako faktów opisujących zdjęcie.

#### 4. Pobranie i przekazanie grafu do modelu

1. Dla GRAPH najpierw budujemy pełny, zamknięty zbiór pewnych zdjęć i dla
   każdego z nich pobieramy wszystkich pewnych uczestników, fakty, relacje,
   wygląd, scenę i widoczne teksty. Nie wolno ograniczać grafu do pierwszej
   odnalezionej osoby ani pierwszego zdjęcia.
2. Każdy element dowodu dostaje stabilny identyfikator i zachowuje połączenie
   z `sourcePath`. Ścieżka nie jest pokazywana modelowi jako treść dowodu.
3. Jeżeli cały graf mieści się w oknie kontekstu, trafia do modelu odpowiedzi
   bez redukcji.
4. Jeżeli graf przekracza limit, `GraphContextReducer` musi przetworzyć
   **każdy** element w partiach. Nie wolno wykonać cichego `substring`, top-k
   ani odrzucić końca grafu przed selekcją.
5. Reduktor może wybierać wyłącznie identyfikatory istniejących dowodów. Do
   modelu odpowiedzi trafiają oryginalne, zweryfikowane treści serwera — nigdy
   streszczenia lub nowe fakty wymyślone przez reduktor.
6. HYBRID łączy wyniki wektorowe i leksykalne. GRAPH z kompletnym pakietem
   dowodów nie może zostać zanieczyszczony przypadkowymi hitami HYBRID;
   HYBRID jest fallbackiem dopiero dla pustego grafu albo trybu non-person.

#### 5. Odpowiedź i przypisanie źródeł

1. W GRAPH/HYBRID/DOCUMENT model dostaje pytanie oraz zamknięty kontekst i ma
   swobodę sformułowania naturalnej odpowiedzi po polsku. Sens ma wynikać z
   dowodów, ale brzmienie nie musi być deterministyczne.
2. Źródeł nie wybieramy na podstawie samego pytania, imienia osoby ani listy
   zdjęć pobranych z grafu. Atrybucja następuje **po wygenerowaniu odpowiedzi**
   i wskazuje najmniejszy zestaw dowodów bezpośrednio wspierających jej treść.
3. `GraphSourceAttributionService` przekazuje audytorowi pytanie, gotową
   odpowiedź i dowody z identyfikatorami. Model może zwrócić tylko istniejące
   identyfikatory; serwer sam mapuje je na dozwolone ścieżki.
4. Jeśli odpowiedź atrybutora jest pusta lub niepoprawna, wolno użyć ogólnego
   fallbacku leksykalnego tylko po spełnieniu minimalnego wyniku i marginesu
   nad drugim kandydatem. Progi są konfigurowalne.
5. Gdy użytkownik wskazał plik, źródła muszą pozostać w `fileScope`. Bez
   wskazanego pliku źródłem może zostać wyłącznie zdjęcie faktycznie
   potwierdzające treść odpowiedzi, a nie dowolne zdjęcie tej samej osoby.
6. Jeżeli nie da się pewnie przypisać źródła, zwracamy `uncertain=true` i pustą
   listę zamiast błędnego zdjęcia. Pewnej odpowiedzi nie wolno podpierać
   niepewną wzmianką tylko po to, aby UI pokazał źródło.
7. Nazwy plików i ścieżki występują wyłącznie w `sources`; są usuwane z treści
   odpowiedzi. `evidence` w UI musi odpowiadać dokładnie wybranym źródłom.

#### Ochrona działającego face pipeline

- Nie zmieniamy modeli, progów, bbox, cache ani kolejności face detection /
  face-match przy pracach nad odpowiedziami, grafem, źródłami, kodowaniem lub
  embeddingami.
- Backfill tekstu musi być idempotentny i może modyfikować tylko pola tekstowe
  oraz embedding tekstowy.
- Do naprawy kodowania i embeddingów nie wolno używać
  `reanalyzeExistingImage`, `completeFaceAnalysisForPath` ani endpointów
  ponownej analizy twarzy.
- Ewentualna zmiana face pipeline wymaga osobnego, jawnego zadania użytkownika.

### Ścieżka GRAPH (ludzie)

Gdy planer uzna, że pytanie dotyczy **osób (ludzi)**:

1. Wypełnia `entities` kanonicznymi imionami z grafu (gdy da się je ustalić).
2. `GraphQueryService` buduje **pełny zrzut grafu** dla pewnych zdjęć (wszyscy
   uczestnicy, relacje, `visual_cues`, fakty, scena, claimy) — nie tylko jedną
   wybraną encję.
3. Ten graf trafia do AI, które **samo decyduje**, które węzły i krawędzie
   odpowiadają na pytanie, i generuje naturalną odpowiedź po polsku.

Ścieżkę GRAPH wyzwalają wyłącznie **ludzie** (`PERSON`). Zwierzęta i obiekty
mogą być w opisie sceny / faktach, ale **nie** klasyfikują pytania jako GRAPH.

### Ścieżka HYBRID (nie o ludzi)

Gdy pytanie nie dotyczy tożsamości ani relacji ludzi (dokumenty, sceny bez
tożsamości, obiekty, ogólne fakty z plików):

1. Retrieval łączy embedding (pgvector) i leksykę.
2. Fragmenty trafiają do AI, które generuje odpowiedź.

Tryb `DOCUMENT` jest dopuszczalny rzadko; domyślnie non-person → `HYBRID`.

### Ścieżka VISUAL_VALIDATION

Gdy odpowiedź wymaga weryfikacji wyglądu, ubioru, pozy, czynności lub układu
na obrazie (`visualCondition=true`):

1. `DynamicVisualMatcher` szuka dowodów `MATCH` powyżej progu.
2. Odpowiedź buduje `VerifiedVisualAnswerService` z **immutable claimów** —
   bez swobodnej syntezy LLM nad wynikiem matchera.

Przy pustym wyniku visual: fallback do GRAPH/HYBRID tylko gdy polityka
techniczna na to pozwala (`ChatRetrievalPolicy.shouldFallbackFromEmptyVisual`).

### Generowanie odpowiedzi

| Tryb | Kto formułuje odpowiedź |
|---|---|
| `GRAPH`, `HYBRID`, `DOCUMENT` | AI (`ChatService`) z **pełnym zrzutem grafu** / retrieval — model sam formułuje naturalną wypowiedź |
| `VISUAL_VALIDATION` | Claimy z `VerifiedVisualAnswerService` |

> **Zasada obowiązująca:** short-circuit claim→prose na ścieżce GRAPH/HYBRID
> pozostaje wyłączony. LLM formułuje wypowiedź z grafu/retrieval. Post-grounding
> nadal wycina eseje encyklopedyczne, odmowy „nie widzę zdjęć” i listy
> spekulacji, a `FreeformAnswerRepairService` naprawia wyłącznie twarde błędy
> formy na podstawie tego samego zamkniętego kontekstu.

---

## Ingest i graf wiedzy

### Structured vision

Każdy obraz jest opisywany strukturalnie (`VisionResultDto`,
`vision.structured.prompt`): uczestnicy, scena, relacje przestrzenne,
`visual_cues`, obiekty, napisy, bbox.

Vision **nie zgaduje imion** — używa stabilnych etykiet (`person 1`). Imiona
pochodzą z tagów, face-match i identity resolution.

### Maksymalna szczegółowość → embeddingi i fakty

Opis ma być jak najdokładniejszy. Każdy istotny szczegół trafia do:

- tekstu kanonicznego embeddowanego przy ingeście (`IngestionService`),
- faktów i relacji w grafie.

Nie wolno skracać JSON / tekstu kosztem recall ani wymyślać niewidocznych
szczegółów.

### Rdzeń grafu dla zdjęć

- **kto** jest na zdjęciu — przede wszystkim ludzie z imionami,
- **relacje** między nimi oraz z obiektami sceny,

a nie luźny esej tekstowy.

---

## Pewne źródła

Do listy źródeł i odpowiedzi opartej na grafie / visual trafiają wyłącznie
dowody **pewne**:

- decyzja wizualna `MATCH` powyżej skonfigurowanego progu,
- wzmianki `CONFIRMED` z pewnością ≥ `rag.graph.min-mention-confidence`,
- fakty ≥ `rag.graph.min-fact-confidence`.

Niepewne wyniki: flaga `uncertain` lub komunikat o braku potwierdzenia —
**bez promocji** do pewnego źródła. Przy zmianach preferuj zaostrzanie, nie
rozluźnianie.

Lista `certainPaths` opisuje pochodzenie całego pakietu grafowego, ale nie jest
automatycznie listą źródeł gotowej odpowiedzi. `sources` powstaje dopiero po
atrybucji odpowiedzi do konkretnych identyfikatorów dowodów.

---

## Odpowiedzi UX

- Język: **polski**.
- Treść: **krótka i prosta**, bez ścieżek, nazw plików i cytowań technicznych.
- Źródła wyłącznie w `sources` (`SourceDto` / UI listy źródeł).
- Brak dowodów → jasny komunikat, bez halucynacji.
- Otwarte pytania; zamknięte dowody — bez katalogu dozwolonych fraz.

---

## Wolno / nie wolno

**Wolno**

- konfigurowalne progi i limity w `application.properties`,
- filtry statusów wzmianek i decyzji wizualnych,
- narzędzia techniczne planera (enum trybów, `entityMatchMode`, `fileScope`),
- otwarte pola w JSON vision (bez słowników domenowych),
- domykanie retrieval leksykalnego jako uzupełnienie wektorowego.

**Nie wolno**

- hardcodowanych list imion, kolorów, ubrań, czynności do routingu pytań,
- traktowania niepewnych wzmianek/dowodów jako pewnych źródeł,
- wstawiania `@plik`, ścieżek i nazw plików do treści odpowiedzi modelu,
- upraszczania JSON vision / tekstu kanonicznego kosztem szczegółów,
- zamykania obsługi pytań do sztywnego katalogu fraz,
- wyzwalania GRAPH przez zwierzęta lub obiekty zamiast ludzi.

---

## Mapowanie na kod

| Obszar | Główne miejsca |
|---|---|
| Planer trybów | `QueryPlanner`, `QueryPlan` |
| Orkiestracja | `ChatInteractionService`, `ChatRetrievalPolicy` |
| Graf osób / relacji | `GraphQueryService`, `GraphEvidenceResult` |
| Elementy i zdjęcia dowodowe | `GraphEvidenceItem`, `GraphPhotoEvidence` |
| Redukcja dużego grafu | `GraphContextReducer` |
| Atrybucja źródeł po odpowiedzi | `GraphSourceAttributionService` |
| Visual | `DynamicVisualMatcher`, `VerifiedVisualAnswerService` |
| Hybrid retrieval | `RetrievalConfiguration`, `LexicalEmbeddingSearch`, `ImageCandidateRetriever` |
| Ingest / vision | `StructuredVisionExtractor`, `IngestionService`, `vision.structured.prompt` |
| Kodowanie i backfill tekstu | `Utf8MojibakeRepair`, `StoredTextEncodingRepairService`, `TextEncodingRepairRunner` |
| Kanoniczny embedding zdjęcia | `ImageEmbeddingDocumentBuilder`, `CanonicalEmbeddingRefreshService` |
| Odpowiedź PL + źródła | `ChatService.ANSWER_INSTRUCTIONS`, `removeTechnicalReferences` |
