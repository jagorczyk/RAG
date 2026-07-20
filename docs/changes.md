# Plan wdrożenia: RAG → junior-ready Java backend

**Cel:** rozwijać **RAG** (nie inne projekty portfolio), domykając luki rekrutacyjne junior Java / junior backend.  
**Zakres:** backend Spring Boot + frontend (token auth) + infra (Docker, CI, demo).  
**Powiązane:** produktowy GraphRAG może iść równolegle później; ten plan = warstwa backend „jak w firmie”.

---

## Zasada

Każda zmiana ma 2 cele:

1. Lepszy produkt (multi-user, bezpieczny, odporny ingest)
2. Sygnał rekrutacyjny (Security, Flyway, testy, OpenAPI, async, Redis, CI, demo)

**Nie robić na razie**

- wizualizacja grafu wiedzy w UI
- przepisywanie frontu od zera
- full microservices (wiele serwisów Java)
- płatności / multi-tenant SaaS w stylu Gymlos
- kolejny framework agentowy / nowy model LLM

**Priorytet:** foundation backend, potem polish GraphRAG.

---

## Mapa luk rekrutera → praca w RAG

| Luka junior backend | Co dodać w RAG | Sprint |
|---------------------|----------------|--------|
| Security / JWT | Auth + ownership folderów, chatów, plików | 1 |
| REST produkcyjny | Walidacja DTO, spójne błędy JSON | 1 |
| Migracje DB | Flyway (users + owner_id; potem reszta schema) | 1 |
| OpenAPI | springdoc + Swagger UI + Bearer | 1 |
| Testy | unit auth + izolacja user A vs B; później Testcontainers | 1–2 |
| Async / broker | RabbitMQ (lub Kafka) na ingest | 2 |
| Cache | Redis (rate limit, cache identity) | 3 |
| CI + demo | GitHub Actions + publiczny deploy | 4 |
| Cloud storage | MinIO / S3 na pliki | 4 |
| Observability | Actuator + structured logs + health checks | 3–4 |

---

## Stan wyjściowy (typowe RAG)

Założenia o projekcie przed startem planu:

- Spring Boot + JPA + PostgreSQL / PGVector
- REST: chat, folders, upload, knowledge, data
- Brak (lub słaba) autoryzacji na API
- `ddl-auto=update` zamiast migracji
- Ingest synchroniczny w requestcie
- Brak brokera, Redis, OpenAPI, pełnego CI
- Frontend woła API bez tokena

Dostosuj checklisty do realnego kodu w swoim repo.

---

## Sprint 1 — „to nie jest open API” (P0)

**Cel:** multi-user + JWT + ownership + Flyway (auth) + OpenAPI + error handling + frontend z tokenem.

### 1. Zależności (Maven)

Dodać m.in.:

- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `spring-boot-starter-flyway` + `flyway-database-postgresql`
- JJWT (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
- `springdoc-openapi-starter-webmvc-ui` (dopasuj wersję do Spring Boot)
- testy: `spring-security-test`, ewentualnie H2 / Testcontainers

### 2. Auth backend

| Element | Opis |
|---------|------|
| `User` entity | id (UUID), email (unique), passwordHash, displayName, createdAt |
| `UserRepository` | `findByEmailIgnoreCase`, `existsByEmailIgnoreCase` |
| DTO | RegisterRequest, LoginRequest, AuthResponse, UserResponse + Bean Validation |
| `PasswordEncoder` | BCrypt |
| `UserPrincipal` + `UserDetailsService` | load by email |
| `JwtService` | generate / parse; claim `uid`; secret min 32 znaki |
| `JwtAuthenticationFilter` | `Authorization: Bearer …` |
| `AuthService` | register, login, me |
| `AuthController` | public register/login; `/me` secured |
| `CurrentUserService` | `requireUserId()` / `requirePrincipal()` |
| `SecurityConfig` | stateless JWT; public: `/api/auth/register`, `/api/auth/login`, swagger, `/error`; reszta authenticated |
| CORS | frontend origin (localhost:3000 itd.) |

### 3. API auth

| Method | Path | Auth | Opis |
|--------|------|------|------|
| POST | `/api/auth/register` | public | email, password (min 8), displayName? |
| POST | `/api/auth/login` | public | → accessToken + user |
| GET | `/api/auth/me` | Bearer | current user |

### 4. Ownership

- `Folder`: `ownerId` (UUID)
- `Conversation` / chat: `ownerId`
- `File`: `ownerId` (ustawiany przy upload/ingest)
- Repo: `findAllByOwnerId…`, `findByIdAndOwnerId…` (lub ekwiwalent)
- Kontrolery:
  - create → zawsze current user
  - list → tylko swoje
  - get/update/delete/upload → 404 jeśli nie owner (nie ujawniać cudzych id)
- `IngestionService`: przy tworzeniu `FileEntity` ustawiać `ownerId`
- Knowledge API: Sprint 1 minimum = wymaga auth; pełna izolacja grafu po userze = later (filtr po paths plików usera lub `owner_id` na encjach)

### 5. Flyway

Przykładowa migracja startowa:

- tabela `users`
- `owner_id` na folders / conversations / files (`ADD COLUMN IF NOT EXISTS` jeśli DB już żyje)
- indeksy na `owner_id`, unique na email

Konfiguracja:

- `spring.flyway.enabled=true`
- `spring.flyway.baseline-on-migrate=true` (istniejące środowiska)
- na czas przejścia: `ddl-auto=update` dla tabel AI/embedding **albo** stopniowo przenieść schema do Flyway i `validate`

### 6. Błędy i walidacja

- `ApiException(status, code, message)`
- `RestExceptionHandler` → JSON: timestamp, status, error, code, message, path, fieldErrors
- `@Valid` na body auth i kluczowych DTO (folder name, message)

### 7. OpenAPI

- springdoc UI
- security scheme Bearer JWT
- publiczne ścieżki swagger w `SecurityConfig`
- link w README

### 8. application.properties / env

```properties
app.jwt.secret=${JWT_SECRET:change-me-to-a-long-secret-at-least-32-chars}
app.jwt.expiration-minutes=${JWT_EXPIRATION_MINUTES:10080}
```

Secret nigdy w git (tylko placeholder / env).

### 9. Frontend

- `auth` helper: login, register, me, save/load/clear token (`localStorage`)
- wspólny `fetch` / wrapper: header `Authorization: Bearer <token>`
- podpiąć **wszystkie** wywołania API (chat, folders, data, knowledge)
- strona login + register
- 401 → wylogowanie + redirect do login
- layout: bez app shell na ekranie auth
- po zalogowaniu: dotychczasowy flow (chat / folders)

### 10. Testy Sprint 1

- unit: hash hasła, generate/parse JWT, register conflict email
- izolacja: user A tworzy folder → user B dostaje 404/pustą listę (MockMvc + `@WithMockUser` / real JWT **lub** lekki test serwisowy)
- stare testy unit GraphRAG nie mogą paść przez brak contextu security (mock / `@Import` / wyłączenie full context gdzie niepotrzebne)

### 11. README

- jak zarejestrować użytkownika (curl)
- jak podać Bearer
- Swagger URL
- zmienne env (JWT, DB)

### Checklist Sprint 1

```text
[ ] Zależności Maven
[ ] User + JWT + SecurityConfig
[ ] AuthController register/login/me
[ ] ownerId na Folder / Chat / File + repo
[ ] FolderController ownership
[ ] ChatController ownership
[ ] Ingestion / data ownership
[ ] Flyway V1 + properties JWT
[ ] ApiException + RestExceptionHandler + @Valid
[ ] OpenAPI + swagger public
[ ] Frontend token + login UI
[ ] Testy auth + izolacja A/B
[ ] README
[ ] mvn test green
[ ] commit & push
```

### Kryteria akceptacji Sprint 1

1. Bez tokena → 401 na API biznesowym  
2. Register + login zwraca JWT  
3. User A nie widzi folderów/chatów/plików usera B  
4. Swagger działa; Authorize = Bearer  
5. Flyway przechodzi na czystej i istniejącej DB  
6. Frontend po loginie działa end-to-end  
7. Testy przechodzą  

---

## Sprint 2 — „ingest jak w produkcji” (P1)

**Cel:** async + message broker + status machine + solidniejsze testy.

1. **Status ingestu**  
   Upload → `PENDING` → worker → `READY` / `FAILED` (wykorzystaj istniejący enum statusu jeśli jest)

2. **RabbitMQ** (preferowane na junior) **lub Kafka**  
   - event np. `DocumentUploaded` (fileId / path / ownerId)  
   - consumer: embedding + vision + graph extract  
   - retry + dead-letter (prosto)

3. **Idempotency**  
   Ten sam event / contentHash nie dubluje embeddingów i faktów

4. **Testcontainers**  
   Postgres (+ pgvector jeśli możliwe) + RabbitMQ  
   Test: upload → message → status READY

5. **docker-compose**  
   Dodać `rabbitmq` (management UI opcjonalnie)

### Kryteria Sprint 2

- ciężki ingest nie blokuje HTTP na minuty w happy path (accepted + polling status)
- restart workera nie gubi semantyki jobów (kolejka, nie tylko RAM)
- test integracyjny z brokerem przechodzi

---

## Sprint 3 — cache, limity, jakość danych (P1)

1. **Redis**  
   - rate limit na `/api/chat/**/send` i upload  
   - cache wyników identity match / drogich odczytów

2. **Lifecycle danych**  
   - po delete pliku: embeddingi, mentions, facts, face data, osierocone encje  
   - test: upload → delete → brak orphanów

3. **Testy GraphRAG krytyczne** (integracyjne tam gdzie warto)  
   - query grafu, identity merge/suggest, delete E2E

4. **Health**  
   - Actuator: DB, Redis, broker, face-service  
   - opcjonalnie status w UI

5. **(Opcjonalnie)** izolacja knowledge graph per user

### Kryteria Sprint 3

- limit requestów działa i zwraca czytelny błąd  
- delete nie zostawia śmieci w grafie  
- health endpoint pokazuje zależności  

---

## Sprint 4 — „da się pokazać” (P1/P2)

1. **CI** — GitHub Actions: `mvn test` (i opcjonalnie frontend lint/build) na PR  
2. **Pełny docker-compose** — backend, frontend, pgvector, redis, rabbitmq, face-service  
3. **Deploy demo** — publiczny URL + konto demo w README (bez sekretów w repo)  
4. **Object storage** — MinIO lub S3 na bajty plików (nowe uploady)  
5. **Portfolio** — RAG jako #1: diagram architektury, Live demo, Swagger, Repo  
6. **Opis projektu (3 decyzje)** — np. JWT ownership, async ingest, hybrid vector+graph  

### Kryteria Sprint 4

- rekruter klika demo bez klonowania  
- CI zielone na main  
- stack w CV da się uzasadnić kodem  

---

## Docelowa architektura

```text
[Next.js]
    │  JWT Bearer
    ▼
[Spring Boot API] ── OpenAPI / Swagger
    │
    ├── PostgreSQL + PGVector (+ Flyway)
    ├── Redis (rate limit / cache)
    ├── RabbitMQ (ingest jobs)
    ├── face-service
    └── S3 / MinIO (pliki)
```

---

## Kolejność realizacji (globalna)

```text
Sprint 1.1  User + JWT + SecurityConfig
Sprint 1.2  ownerId + kontrolery + ingest owner
Sprint 1.3  Flyway + properties
Sprint 1.4  errors + validation + OpenAPI
Sprint 1.5  Frontend auth
Sprint 1.6  Testy + README + push
Sprint 2    Async ingest + RabbitMQ + Testcontainers
Sprint 3    Redis + orphan cleanup + health + testy grafu
Sprint 4    CI + compose full + deploy + S3/MinIO + portfolio
```

---

## Produkt GraphRAG (po foundation)

Rób **po** Sprint 1 (najlepiej w okolicy Sprint 3):

- sprzątanie osieroconych encji po delete
- fallback do vector RAG gdy graf nie ma encji
- edycja aliasów, health face-service w UI
- gęstsze testy routera / identity

Niski priorytet rekrutacyjny: wizualizacja grafu, rozbudowa agentów.

---

## Tekst pod CV / portfolio (po planie)

> Multi-user Spring Boot service: JWT auth, resource ownership, Flyway, async document ingestion (RabbitMQ), PGVector + knowledge graph, Redis rate limiting, OpenAPI, Testcontainers, Docker Compose demo.

---

## Prompt startowy (nowe repo / nowa sesja)

```text
Wdróż plan z docs/junior-backend-rollout.md w tym repo RAG.
Zacznij od Sprint 1: JWT auth, ownership folder/chat/file, Flyway,
OpenAPI, error handling, frontend z Bearer token i stroną login.
Potem mvn test, zaktualizuj README, commit i push.
Nie rozpraszaj się Gymlos ani UI graph visualization.
```

### Audit na starcie

```text
1. Przejrzyj pom.xml, Security (czy jest), encje Folder/Chat/File
2. Sprawdź jak frontend woła API (jeden client vs wiele fetchy)
3. Sprawdź docker-compose i application.properties
4. Odpal istniejące testy jako baseline
5. Implementuj Sprint 1 po checklistie
```

---

## Uwagi techniczne

- **JWT secret** tylko z env w prod; min 32 znaki  
- **Legacy rows** z `owner_id = NULL` — niewidoczne dla userów albo jednorazowy assign  
- **clear-all / reanalyze** po auth: tylko dane current user, nie globalny truncate cudzych danych  
- **springdoc vs Spring Boot** — dopasuj wersję; przy konflikcie najpierw API docs JSON, potem UI  
- **Idempotency i status** przy async — dokumentuj w README (polling endpoint)  
- **Nie commituj** `.env`, kluczy API LLM, haseł DB  

---

## Definicja „plan done” (całość)

| Milestone | Done when |
|-----------|-----------|
| Sprint 1 | Multi-user JWT + ownership + FE login + OpenAPI + test izolacji |
| Sprint 2 | Ingest przez kolejkę + test z brokerem |
| Sprint 3 | Redis + cleanup + health |
| Sprint 4 | CI + demo publiczne + storage + portfolio update |

Po Sprint 1 projekt już nadaje się do pokazywania rekruterom jako „prawdziwy” backend; Sprint 2–4 domykają checklistę junior/mid-ready.
