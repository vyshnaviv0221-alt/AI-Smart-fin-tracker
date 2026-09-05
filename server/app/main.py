"""
AI Smart Finance Tracker — FastAPI Backend
============================================
Loads the trained models from ../models/ at startup and serves three
endpoints the Android app calls: categorize, anomaly check, and forecast.

Run locally:
    uvicorn app.main:app --reload --port 8000

Then check http://127.0.0.1:8000/docs for interactive Swagger UI.
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from app import model_loader
from app.schemas import (
    AnomalyResponse,
    CategoryResponse,
    PredictionRequest,
    PredictionResponse,
    TransactionRequest,
)

logger = logging.getLogger(__name__)

# Set when startup fails, so / can explain *why* it is degraded instead of
# just reporting that it is.
_startup_error: str | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Load all models once, before the first request.

    A model failure does NOT abort startup. Previously an exception here killed
    the process, which meant the "degraded" branch in / could never be reached
    -- the health check could only ever be observed as healthy. The server now
    starts either way and reports the problem, so `curl /` tells you what is
    wrong instead of the client seeing a connection refused.
    """
    global _startup_error
    try:
        model_loader.load_all_models()
        logger.info("All models loaded.")
    except Exception as e:  # noqa: BLE001 - surfaced via /, not swallowed
        _startup_error = str(e)
        logger.error("Model loading failed: %s", e)
    yield


app = FastAPI(
    title="AI Smart Finance Tracker API",
    description="Backend server for the AI Smart Finance Tracker Android app.",
    version="0.3.0",
    lifespan=lifespan,
)


def _require_models() -> None:
    """Every prediction endpoint needs the models; fail with 503, not 500."""
    if not model_loader.models_ready():
        raise HTTPException(
            status_code=503,
            detail=(
                f"Models are not loaded: {_startup_error or 'unknown error'}. "
                "Run train_server_models.py, then restart the server."
            ),
        )


@app.get("/")
async def health():
    """Health check — also reports whether the models loaded correctly."""
    ready = model_loader.models_ready()
    return {
        "status": "ok" if ready else "degraded",
        "message": "AI Smart Finance Tracker API is running.",
        "models_ready": ready,
        "error": None if ready else _startup_error,
    }


@app.post("/categorize", response_model=CategoryResponse)
async def categorize_transaction(req: TransactionRequest):
    """Classifies merchant_text into one of the known spending categories."""
    _require_models()
    if not req.merchant_text.strip():
        raise HTTPException(status_code=400, detail="merchant_text cannot be empty")

    try:
        category, confidence = model_loader.predict_category(req.merchant_text)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Categorization failed: {e}")

    return CategoryResponse(category=category, confidence=confidence)


@app.post("/anomaly", response_model=AnomalyResponse)
async def detect_anomaly(req: TransactionRequest):
    """
    Flags whether an amount is unusual *for its category*.

    merchant_text is not decorative: it is categorised first, then the amount
    is scored against that category's own distribution. Only amounts ABOVE the
    normal range are flagged -- see model_loader.predict_anomaly.
    """
    _require_models()
    if req.amount < 0:
        raise HTTPException(status_code=400, detail="amount cannot be negative")

    try:
        status, category, deviation = model_loader.predict_anomaly(
            req.amount, req.merchant_text
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Anomaly check failed: {e}")

    return AnomalyResponse(
        amount=req.amount, status=status, category=category, deviation=deviation
    )


@app.post("/predict", response_model=PredictionResponse)
async def predict_expense(req: PredictionRequest):
    """Predicts expected spend for a given category."""
    _require_models()
    if not 1 <= req.month <= 12:
        raise HTTPException(status_code=400, detail="month must be between 1 and 12")

    try:
        predicted_amount = model_loader.predict_monthly_amount(req.month, req.category)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Prediction failed: {e}")

    return PredictionResponse(category=req.category, predicted_amount=predicted_amount)
