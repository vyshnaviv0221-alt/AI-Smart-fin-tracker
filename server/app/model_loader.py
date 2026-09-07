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

from app import feedback_store

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"

_categorizer = None
_anomaly_model = None
_anomaly_stats = None
_forecaster = None
_forecaster_columns = None
_daily_forecaster = None


class ModelLoadError(RuntimeError):
    """Raised when a model file is missing or fails to unpickle."""


def load_all_models() -> None:
    """Call once at server startup (see main.py's startup event)."""
    global _categorizer, _anomaly_model, _anomaly_stats, _forecaster, _forecaster_columns
    global _daily_forecaster

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

    # Optional: the daily forecaster trained by model/training/predict_expense.py.
    # Missing it degrades one endpoint rather than failing startup, because it
    # is copied in by hand rather than produced by train_server_models.py.
    try:
        _daily_forecaster = joblib.load(MODELS_DIR / "daily_forecaster.joblib")
    except FileNotFoundError:
        _daily_forecaster = None


def daily_forecaster_ready() -> bool:
    """The daily forecaster is optional: the other three endpoints work without it."""
    return _daily_forecaster is not None


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


def predict_category(merchant_text: str, amount: float = 0.0) -> tuple[str, float, str]:
    """
    Returns (category, confidence 0-1, source).

    A merchant the user has already corrected is returned directly, before the
    model is consulted: a human-verified label outranks a probabilistic guess,
    and re-guessing it would make the correction feel ignored.

    `amount` is accepted for API symmetry but not used as a feature: letting
    the amount decide the category makes /anomaly circular, since an amount is
    then always normal for the category its own size implies.
    """
    remembered = feedback_store.lookup(merchant_text)
    if remembered:
        return remembered, 1.0, "user_correction"

    category = _categorizer.predict([merchant_text])[0]
    confidence = float(np.max(_categorizer.predict_proba([merchant_text])[0]))
    return category, confidence, "model"


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
            category = feedback_store.lookup(merchant_text) or _categorizer.predict([merchant_text])[0]
        except Exception:
            category = "__global__"

    stats = _anomaly_stats.get(category, _anomaly_stats["__global__"])
    deviation = (float(np.log1p(float(amount))) - stats["median"]) / stats["scale"]

    # One-sided, matching model/training/anomaly_detection.py.flag_unusual():
    # an outlier is only UNUSUAL if it is ABOVE the category's normal range.
    # A two-sided detector spent 85% of its flags on transactions that were
    # unusually cheap, which is noise in a tool meant to warn about spending.
    is_outlier = _anomaly_model.predict([[deviation]])[0] == -1
    status = "UNUSUAL" if (is_outlier and deviation > 0) else "normal"
    return status, category, round(float(deviation), 2)


def predict_monthly_amount(month: int, category: str) -> float:
    """
    Expected monthly spend for a category.

    `month` is accepted and validated by the API but not consumed by the model:
    the month feature measurably hurt accuracy on the real data (R2 -0.243 vs
    0.107 for category alone), so it was dropped. The parameter is kept so the
    endpoint contract does not change if a seasonal model is reintroduced.
    """
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


# --- daily spend forecaster (model/training/predict_expense.py) --------------

# Out-of-fold R2 from that script's own evaluation. Reported to the client so
# the UI can be honest: the model explains roughly 9% of day-to-day variance,
# and its entire output range spans about 40% of one standard deviation of
# real daily spend. It is indicative, not a number to budget against.
DAILY_FORECAST_R2 = 0.09

_DAILY_FEATURES = ["Day", "DayOfWeek", "Month", "IsWeekend", "Lag_1", "Rolling_7_Mean"]


def predict_daily_spend(recent_daily_totals: list[float], days_ahead: int) -> list[tuple[str, float]]:
    """
    Projects the next `days_ahead` days of total spend.

    Each prediction is fed back in as the next day's Lag_1 and folded into the
    rolling mean, so the horizon compounds from the model's own output rather
    than repeating one number. Returns [(iso date, amount)].
    """
    import datetime as _dt

    history = [float(x) for x in recent_daily_totals if x is not None]
    if not history:
        history = [0.0]

    today = _dt.date.today()
    out: list[tuple[str, float]] = []

    for offset in range(1, days_ahead + 1):
        day = today + _dt.timedelta(days=offset)
        window = history[-7:]
        row = pd.DataFrame(
            [{
                "Day": day.day,
                "DayOfWeek": day.weekday(),
                "Month": day.month,
                "IsWeekend": int(day.weekday() >= 5),
                "Lag_1": history[-1],
                "Rolling_7_Mean": float(np.mean(window)),
            }]
        )[_DAILY_FEATURES]

        predicted = max(0.0, float(_daily_forecaster.predict(row)[0]))
        out.append((day.isoformat(), round(predicted, 2)))
        history.append(predicted)

    return out
