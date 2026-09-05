"""
Loads the trained model artifacts once at server startup and exposes
simple inference functions. Keeping this separate from main.py means the
endpoint handlers stay thin, and models are only ever loaded once
(loading a joblib file is slow — you don't want to do it per-request).
"""

import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"

_categorizer = None
_anomaly_model = None
_anomaly_stats = None
_forecaster = None
_forecaster_columns = None


class ModelLoadError(RuntimeError):
    """Raised when a model file is missing or fails to unpickle."""


def load_all_models() -> None:
    """Call once at server startup (see main.py's startup event)."""
    global _categorizer, _anomaly_model, _anomaly_stats, _forecaster, _forecaster_columns

    try:
        _categorizer = joblib.load(MODELS_DIR / "categorizer.joblib")
    except FileNotFoundError as e:
        raise ModelLoadError(
            "categorizer.joblib not found. Run train_server_models.py first."
        ) from e
    except Exception as e:
        raise ModelLoadError(
            f"Failed to load categorizer.joblib ({e}). This usually means it "
            "was saved with a different scikit-learn version — retrain it "
            "with train_server_models.py using your current environment."
        ) from e

    try:
        _anomaly_model = joblib.load(MODELS_DIR / "anomaly.joblib")
        with open(MODELS_DIR / "anomaly_category_stats.json") as f:
            _anomaly_stats = json.load(f)
    except FileNotFoundError as e:
        raise ModelLoadError(
            "anomaly.joblib / anomaly_category_stats.json not found. "
            "Run train_server_models.py first."
        ) from e

    try:
        _forecaster = joblib.load(MODELS_DIR / "forecaster.joblib")
        with open(MODELS_DIR / "forecaster_columns.json") as f:
            _forecaster_columns = json.load(f)
    except FileNotFoundError as e:
        raise ModelLoadError(
            "forecaster.joblib / forecaster_columns.json not found. "
            "Run train_server_models.py first."
        ) from e


def models_ready() -> bool:
    return all(
        item is not None
        for item in (
            _categorizer,
            _anomaly_model,
            _anomaly_stats,
            _forecaster,
            _forecaster_columns,
        )
    )


def predict_category(merchant_text: str) -> tuple[str, float]:
    """Returns (category, confidence 0-1)."""
    category = _categorizer.predict([merchant_text])[0]
    proba = _categorizer.predict_proba([merchant_text])[0]
    confidence = float(np.max(proba))
    return category, confidence


def predict_anomaly(amount: float, merchant_text: str = "") -> tuple[str, str, float]:
    """
    Returns (status, category_used, deviation).

    The category matters: an amount is only unusual relative to what that
    category normally costs. Rent at 15,000 is normal; Food at 15,000 is not.
    When merchant_text is empty the global distribution is used instead.
    """
    category = "__global__"
    if merchant_text.strip():
        try:
            category = _categorizer.predict([merchant_text])[0]
        except Exception:
            category = "__global__"

    stats = _anomaly_stats.get(category, _anomaly_stats["__global__"])
    deviation = (float(np.log1p(float(amount))) - stats["median"]) / stats["scale"]

    flag = _anomaly_model.predict([[deviation]])[0]  # -1 = anomaly, 1 = normal
    return ("UNUSUAL" if flag == -1 else "normal"), category, round(float(deviation), 2)


def predict_monthly_amount(month: int, category: str) -> float:
    """Returns the predicted spend for a given month + category."""
    input_row = pd.DataFrame(0, index=[0], columns=_forecaster_columns)
    if "Month" in input_row.columns:
        input_row["Month"] = month

    cat_col = f"Category_{category}"
    if cat_col not in input_row.columns:
        known = sorted(
            c.replace("Category_", "") for c in _forecaster_columns if c.startswith("Category_")
        )
        raise ValueError(f"Unknown category '{category}'. Known categories: {known}")
    input_row[cat_col] = 1

    pred_log = _forecaster.predict(input_row)[0]
    return float(np.expm1(pred_log))
