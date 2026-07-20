# RAG — multi-user GraphRAG

Spring Boot backend + Next.js frontend: dokumenty i zdjęcia, hybrid retrieval (vector + lexical), knowledge graph, face identity.

## Stack

- **Backend:** Java 17, Spring Boot 4, JPA, PostgreSQL + PGVector, Spring Security JWT, Flyway, springdoc OpenAPI
- **Frontend:** Next.js, token Bearer w `localStorage`
- **Infra:** Docker Compose (`pgvector`, `face-service`)

## Szybki start

### 1. Baza i face-service

```bash
docker compose up -d
```

### 2. Backend

```bash
cd backend
# opcjonalnie: plik .env z DEEPINFRA_API_KEY, JWT_SECRET, DB_*
./mvnw spring-boot:run
```

API: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

UI: `http://localhost:3000` — najpierw **rejestracja / logowanie**.

## Auth (JWT)

Publiczne:

| Method | Path | Opis |
|--------|------|------|
| POST | `/api/auth/register` | email, password (min 8), displayName? |
| POST | `/api/auth/login` | → `accessToken` + `user` |
| GET | `/api/auth/me` | wymaga `Authorization: Bearer …` |

Pozostałe `/api/**` wymagają Bearer JWT. Zasoby (foldery, czaty, pliki) są izolowane po `ownerId`.

### curl

```bash
# rejestracja
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"demo@example.com\",\"password\":\"password123\",\"displayName\":\"Demo\"}"

# logowanie
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"demo@example.com\",\"password\":\"password123\"}" \
  | jq -r .accessToken)

# przykładowe API
curl -s http://localhost:8080/api/folders -H "Authorization: Bearer $TOKEN"
```

W Swagger UI: **Authorize** → `Bearer <token>` (bez słowa „Bearer” w polu, jeśli UI je dokłada samo — wklej sam token).

## Zmienne środowiskowe

| Zmienna | Domyślnie | Opis |
|---------|-----------|------|
| `JWT_SECRET` | placeholder (min 32 znaki) | Sekret HMAC JWT — **ustaw w prod** |
| `JWT_EXPIRATION_MINUTES` | `10080` (7 dni) | Ważność access tokenu |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,...` | Origin frontendu |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | localhost:5433 / user / password | PostgreSQL |
| `DEEPINFRA_API_KEY` | — | LLM / vision |
| `FACE_SERVICE_URL` | `http://localhost:8001` | Serwis twarzy |
| `NEXT_PUBLIC_BACKEND_URL` | `http://localhost:8080` | URL API dla frontu |

**Nie commituj** `.env`, kluczy API ani haseł produkcyjnych.

## Flyway

- Migracja `V2__auth_and_ownership.sql`: tabela `users`, kolumny `owner_id` na folders / conversations / files
- `spring.flyway.baseline-on-migrate=true` — bezpieczne na istniejących bazach (baseline v1 → V2 się doda)
- Tabele AI/embedding nadal mogą powstawać przez `ddl-auto=update` (przejście)

## Testy

```bash
cd backend
./mvnw test
```

M.in.: JWT generate/parse, rejestracja (conflict email), izolacja folderów user A vs B.

## Plan rozwoju

Szczegóły: [`docs/changes.md`](docs/changes.md) (Sprint 1–4: auth → async ingest → Redis → CI/demo).

## Produkt GraphRAG

Zasady decyzyjne: [`AGENTS.md`](AGENTS.md). Roadmapa: [`docs/roadmap.md`](docs/roadmap.md).
