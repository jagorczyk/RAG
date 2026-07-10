import logging
from typing import Any

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from insightface.app import FaceAnalysis

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("face-service")

app = FastAPI(title="RAG Face Service", version="1.0.0")
face_app: FaceAnalysis | None = None


@app.on_event("startup")
def load_model() -> None:
    global face_app
    logger.info("Loading InsightFace model buffalo_l...")
    analyzer = FaceAnalysis(name="buffalo_l", providers=["CPUExecutionProvider"])
    analyzer.prepare(ctx_id=0, det_size=(640, 640))
    face_app = analyzer
    logger.info("InsightFace ready")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "model": "buffalo_l"}


@app.post("/analyze")
async def analyze(file: UploadFile = File(...)) -> dict[str, Any]:
    if face_app is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    contents = await file.read()
    if not contents:
        raise HTTPException(status_code=400, detail="Empty file")

    image_array = np.frombuffer(contents, dtype=np.uint8)
    image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="Unsupported or invalid image")

    faces = face_app.get(image)
    results = []
    for face in faces:
        results.append(
            {
                "embedding": face.embedding.astype(float).tolist(),
                "bbox": [float(v) for v in face.bbox.tolist()],
                "det_score": float(face.det_score),
            }
        )

    return {"faces": results, "count": len(results)}
