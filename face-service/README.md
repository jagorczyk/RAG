# InsightFace service

The service accepts and returns only validated human faces. YOLO person detection gates InsightFace results before embeddings are generated; animals and non-living objects are ignored.

Lokalny mikroserwis do wykrywania twarzy i generowania embeddingów (model `buffalo_l`).

## Uruchomienie (Docker)

```bash
docker compose up -d face-service
```

Sprawdzenie:

```bash
curl http://localhost:8001/health
```

## Uruchomienie lokalne (bez Dockera)

```bash
cd face-service
python -m venv .venv
.venv\Scripts\activate   # Windows
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

Przy pierwszym starcie InsightFace pobierze model (~300 MB).

## Integracja z backendem

Backend Spring Boot woła `POST /analyze` przy ingestii zdjęć i dopasowuje twarze do istniejących osób po podobieństwie cosine (próg domyślny: `0.42`).

Zmienne w `backend/.env` lub `application.properties`:

```
FACE_SERVICE_URL=http://localhost:8001
FACE_SERVICE_ENABLED=true
FACE_MATCH_THRESHOLD=0.50
FACE_SUGGESTION_THRESHOLD=0.45
FACE_MATCH_MIN_MARGIN=0.08
FACE_BATCH_CLUSTER_THRESHOLD=0.48
PERSON_DET_MODEL=yolo11n.pt
PERSON_MIN_SCORE=0.45
```

Jeśli serwis twarzy nie działa, ingestia zdjęć nadal przebiega (tylko bez dopasowania po twarzy).
