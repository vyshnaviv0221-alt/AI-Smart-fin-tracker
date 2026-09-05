"""
AI Smart Finance Tracker — FastAPI Backend
============================================
Loads the trained models from ../models/ at startup and serves three
endpoints the Android app calls: categorize, anomaly check, and forecast.

Run locally:
    uvicorn app.main:app --reload --port 8000

Then check http://127.0.0.1:8000/docs for interactive Swagger UI.
"""

from fastapi import FastAPI, HTTPException

from app import model_loader
from app.schemas import (
    AnomalyResponse,
    CategoryResponse,
    PredictionRequest,
    PredictionResponse,
    TransactionRequest,
)

app = FastAPI(
    title="AI Smart Finance Tracker API",
    description="Backend server for the AI Smart Finance Tracker Android app.",
    version="0.2.0",
)


@app.on_event("startup")
def startup_event():
    """Load all models once when the server boots, not per-request."""
    model_loader.load_all_models()


@app.get("/")
async def health():
    """Health check endpoint — also reports whether models loaded correctly."""
    return {
        "status": "ok" if model_loader.models_ready() else "degraded",
        "message": "AI Smart Finance Tracker API is running.",
        "models_ready": model_loader.models_ready(),
    }


@app.post("/categorize", response_model=CategoryResponse)
async def categorize_transaction(req: TransactionRequest):
    """Classifies merchant_text into one of the known spending categories."""
    if not req.merchant_text.strip():
        raise HTTPException(status_code=400, detail="merchant_text cannot be empty")

    try:
        category, confidence = model_loader.predict_category(req.merchant_text)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Categorization failed: {e}")

    return CategoryResponse(category=category, confidence=confidence)


@app.post("/anomaly", response_model=AnomalyResponse)
async def detect_anomaly(req: TransactionRequest):
    """Flags whether a transaction amount looks unusual (Isolation Forest)."""
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
    """Predicts next month's expected spend for a given category."""
    if not 1 <= req.month <= 12:
        raise HTTPException(status_code=400, detail="month must be between 1 and 12")

    try:
        predicted_amount = model_loader.predict_monthly_amount(req.month, req.category)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Prediction failed: {e}")

    return PredictionResponse(category=req.category, predicted_amount=predicted_amount)
