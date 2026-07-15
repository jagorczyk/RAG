import logging
import os
from typing import Any

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from insightface.app import FaceAnalysis
from ultralytics import YOLO

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("face-service")

app = FastAPI(title="RAG Face Service", version="1.0.0")
face_app: FaceAnalysis | None = None
person_app: YOLO | None = None
NMS_IOU_THRESHOLD = 0.35
MIN_FACE_SIZE = 12.0
MIN_PERSON_SCORE = float(os.getenv("PERSON_MIN_SCORE", "0.45"))
FACE_PERSON_IOU = float(os.getenv("FACE_PERSON_IOU", "0.01"))


def _det_size() -> tuple[int, int]:
    raw = os.getenv("FACE_DET_SIZE", "640,640")
    try:
        width, height = [int(part.strip()) for part in raw.split(",", 1)]
        return width, height
    except Exception:
        logger.warning("Invalid FACE_DET_SIZE=%s, using 640,640", raw)
        return 640, 640


@app.on_event("startup")
def load_model() -> None:
    global face_app, person_app
    logger.info("Loading InsightFace model buffalo_l...")
    analyzer = FaceAnalysis(name="buffalo_l", providers=["CPUExecutionProvider"])
    analyzer.prepare(ctx_id=0, det_size=_det_size())
    face_app = analyzer
    person_app = YOLO(os.getenv("PERSON_DET_MODEL", "yolo11n.pt"))
    logger.info("InsightFace ready")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "model": "buffalo_l"}


def _iou(left: list[float], right: list[float]) -> float:
    x1 = max(left[0], right[0])
    y1 = max(left[1], right[1])
    x2 = min(left[2], right[2])
    y2 = min(left[3], right[3])
    intersection = max(0.0, x2 - x1) * max(0.0, y2 - y1)
    if intersection == 0.0:
        return 0.0
    left_area = max(0.0, left[2] - left[0]) * max(0.0, left[3] - left[1])
    right_area = max(0.0, right[2] - right[0]) * max(0.0, right[3] - right[1])
    union = left_area + right_area - intersection
    return intersection / union if union > 0.0 else 0.0


def _suppress_duplicate_faces(faces: list[Any]) -> list[Any]:
    candidates = [
        face for face in faces
        if face.bbox[2] - face.bbox[0] >= MIN_FACE_SIZE
        and face.bbox[3] - face.bbox[1] >= MIN_FACE_SIZE
    ]
    candidates.sort(key=lambda face: float(face.det_score), reverse=True)
    selected: list[Any] = []
    for candidate in candidates:
        if all(_iou(candidate.bbox.tolist(), kept.bbox.tolist()) < NMS_IOU_THRESHOLD for kept in selected):
            selected.append(candidate)
    return selected


def _person_boxes(image: np.ndarray) -> list[dict[str, Any]]:
    if person_app is None:
        return []
    predictions = person_app.predict(image, classes=[0], conf=MIN_PERSON_SCORE, verbose=False)
    boxes: list[dict[str, Any]] = []
    for result in predictions:
        if result.boxes is None:
            continue
        for box in result.boxes:
            coords = [float(v) for v in box.xyxy[0].tolist()]
            score = float(box.conf[0])
            boxes.append({"bbox": coords, "score": score, "type": "PERSON"})
    return boxes


def _box_area(box: list[float]) -> float:
    return max(0.0, box[2] - box[0]) * max(0.0, box[3] - box[1])


def _face_has_person(face: Any, persons: list[dict[str, Any]]) -> bool:
    bbox = [float(v) for v in face.bbox.tolist()]
    cx = (bbox[0] + bbox[2]) / 2.0
    cy = (bbox[1] + bbox[3]) / 2.0
    face_area = _box_area(bbox)
    for person in persons:
        candidate = person["bbox"]
        inside = candidate[0] <= cx <= candidate[2] and candidate[1] <= cy <= candidate[3]
        overlap = _iou(bbox, candidate)
        relative_size = face_area / max(_box_area(candidate), 1.0)
        if inside and (overlap >= FACE_PERSON_IOU or relative_size <= 0.35):
            return True
    return False


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

    image_height, image_width = image.shape[:2]
    persons = _person_boxes(image)
    faces = _suppress_duplicate_faces(face_app.get(image))
    results = []
    rejected = []
    for face in faces:
        if not _face_has_person(face, persons):
            rejected.append({
                "bbox": [float(v) for v in face.bbox.tolist()],
                "det_score": float(face.det_score),
                "reason": "NO_PERSON_BBOX",
            })
            continue
        results.append(
            {
                "embedding": face.embedding.astype(float).tolist(),
                "bbox": [float(v) for v in face.bbox.tolist()],
                "det_score": float(face.det_score),
                "image_width": image_width,
                "image_height": image_height,
            }
        )

    return {
        "faces": results,
        "count": len(results),
        "person_boxes": persons,
        "rejected_faces": rejected,
        "image_width": image_width,
        "image_height": image_height,
    }
