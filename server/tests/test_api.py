"""
Endpoint tests.

These existed only as throwaway shell scripts until now, which meant nothing
in the repository re-checked the server's behaviour. Several of the
assertions below encode defects that were found by measurement and fixed, so
they exist to stop those specific regressions returning:

  - categorisation must not follow the amount (it made /anomaly circular)
  - /anomaly must flag only the HIGH side (85% of two-sided flags were
    transactions that were unusually cheap)
  - a corrected merchant must never be re-guessed

Run with:  pytest -q        (from server/)
"""

import importlib
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

MODELS = Path(__file__).resolve().parent.parent / "models"

pytestmark = pytest.mark.skipif(
    not (MODELS / "categorizer.joblib").exists(),
    reason="models not built; run python train_server_models.py first",
)


@pytest.fixture(scope="module")
def client(tmp_path_factory):
    """
    A client whose corrections file is a temp file, so tests never append to
    the real server/data/corrections.csv or read labels a developer recorded.
    """
    from app import feedback_store

    feedback_store.CORRECTIONS_PATH = tmp_path_factory.mktemp("data") / "corrections.csv"
    feedback_store.DATA_DIR = feedback_store.CORRECTIONS_PATH.parent
    feedback_store._cache, feedback_store._cache_mtime = {}, -1.0

    from app.main import app

    with TestClient(app) as c:
        yield c


# --- health -------------------------------------------------------------

def test_health_reports_models_and_provenance(client):
    body = client.get("/").json()
    assert body["models_ready"] is True
    assert body["status"] == "ok"
    # Provenance makes "is this trained on real data?" checkable rather than claimed.
    assert "real_rows" in body["training_data"]
    assert "corrections_recorded" in body


# --- categorize ---------------------------------------------------------

@pytest.mark.parametrize(
    "merchant,expected",
    [
        ("Swiggy Bangalore", "Food"),
        ("Uber Trip", "Travel"),
        ("BigBasket Order", "Groceries"),
        ("Netflix", "Entertainment"),
        ("House Rent NEFT", "Rent"),
        ("Apollo Pharmacy", "Healthcare"),
        ("Zerodha Trading", "Investment"),
    ],
)
def test_categorizes_common_merchants(client, merchant, expected):
    body = client.post("/categorize", json={"merchant_text": merchant, "amount": 500}).json()
    assert body["category"] == expected


def test_category_does_not_follow_the_amount(client):
    """
    Regression guard. Feeding `amount` to the categorizer made the amount pick
    the category -- "Swiggy Order" at Rs 15,000 came back as Rent -- which in
    turn made /anomaly circular, because an amount is then always normal for
    the category its own size implies.
    """
    cheap = client.post("/categorize", json={"merchant_text": "Swiggy Order", "amount": 50}).json()
    dear = client.post("/categorize", json={"merchant_text": "Swiggy Order", "amount": 15000}).json()
    assert cheap["category"] == dear["category"] == "Food"

    small_rent = client.post(
        "/categorize", json={"merchant_text": "House Rent NEFT", "amount": 450}
    ).json()
    assert small_rent["category"] == "Rent"


def test_empty_merchant_rejected(client):
    assert client.post("/categorize", json={"merchant_text": "   ", "amount": 1}).status_code == 400


# --- anomaly ------------------------------------------------------------

@pytest.mark.parametrize(
    "merchant,amount,expected",
    [
        # Judged within its own category, not against all spending.
        ("House Rent NEFT", 15000, "normal"),
        ("Swiggy Order", 15000, "UNUSUAL"),
        # One-sided: cheaper than usual is not something to alert on.
        ("Cafe Coffee Day", 50, "normal"),
        ("House Rent NEFT", 450, "normal"),
        ("Rapido Bike", 60, "normal"),
        ("Amazon Shopping", 95000, "UNUSUAL"),
    ],
)
def test_anomaly_is_category_aware_and_one_sided(client, merchant, amount, expected):
    body = client.post("/anomaly", json={"merchant_text": merchant, "amount": amount}).json()
    assert body["status"] == expected, f"{merchant} {amount} -> {body}"


def test_negative_amount_rejected(client):
    assert client.post("/anomaly", json={"merchant_text": "x", "amount": -1}).status_code == 400


# --- predict ------------------------------------------------------------

def test_predict_returns_a_positive_amount(client):
    body = client.post("/predict", json={"month": 11, "category": "Food"}).json()
    assert body["predicted_amount"] > 0


def test_predict_month_is_accepted_but_unused(client):
    """The month feature was dropped: it scored worse than category alone."""
    a = client.post("/predict", json={"month": 1, "category": "Food"}).json()
    b = client.post("/predict", json={"month": 11, "category": "Food"}).json()
    assert a["predicted_amount"] == b["predicted_amount"]


@pytest.mark.parametrize(
    "payload", [{"month": 13, "category": "Food"}, {"month": 0, "category": "Food"},
                {"month": 6, "category": "NotACategory"}]
)
def test_predict_rejects_bad_input(client, payload):
    assert client.post("/predict", json=payload).status_code == 400


# --- the correction loop ------------------------------------------------

def test_correction_is_remembered_and_outranks_the_model(client):
    """
    The heart of the feedback loop: a label the user verified must win.

    Retraining alone does not achieve this -- one correction still loses to
    the generated rows -- so a corrected merchant is recalled directly.
    """
    merchant = "Licious Meat Delivery"

    before = client.post("/categorize", json={"merchant_text": merchant, "amount": 680}).json()
    assert before["source"] == "model"

    recorded = client.post(
        "/feedback/correction",
        json={"merchant_text": merchant, "category": "Groceries", "amount": 680},
    ).json()
    assert recorded["recorded"] == 1
    assert recorded["for_this_category"] == 1

    after = client.post("/categorize", json={"merchant_text": merchant, "amount": 680}).json()
    assert after["category"] == "Groceries"
    assert after["confidence"] == 1.0
    assert after["source"] == "user_correction"


def test_correction_recall_ignores_case_and_spacing(client):
    client.post(
        "/feedback/correction",
        json={"merchant_text": "Third Wave Coffee", "category": "Food", "amount": 260},
    )
    for variant in ("third wave coffee", "  THIRD   WAVE  COFFEE  "):
        body = client.post("/categorize", json={"merchant_text": variant, "amount": 260}).json()
        assert body["category"] == "Food"
        assert body["source"] == "user_correction"


def test_latest_correction_wins(client):
    """A user changing their mind is respected."""
    merchant = "Blue Tokai"
    client.post("/feedback/correction",
                json={"merchant_text": merchant, "category": "Groceries", "amount": 400})
    client.post("/feedback/correction",
                json={"merchant_text": merchant, "category": "Food", "amount": 400})
    body = client.post("/categorize", json={"merchant_text": merchant, "amount": 400}).json()
    assert body["category"] == "Food"


def test_uncorrected_merchant_still_uses_the_model(client):
    body = client.post("/categorize", json={"merchant_text": "Swiggy Bangalore", "amount": 450}).json()
    assert body["source"] == "model"
    assert 0.0 < body["confidence"] < 1.0


def test_blank_correction_rejected(client):
    assert client.post(
        "/feedback/correction", json={"merchant_text": "  ", "category": "Food", "amount": 1}
    ).status_code == 400


# --- daily forecast -----------------------------------------------------

def test_daily_forecast_shape_and_honesty(client):
    body = client.post(
        "/forecast/daily", json={"recent_daily_totals": [900] * 7, "days_ahead": 7}
    ).json()
    assert len(body["days"]) == 7
    assert body["total"] > 0
    # The response states how weak the model is, so no caller can present it
    # as more than it is.
    assert body["r2"] < 0.2


@pytest.mark.parametrize("days", [0, 99])
def test_daily_forecast_rejects_bad_horizon(client, days):
    assert client.post(
        "/forecast/daily", json={"recent_daily_totals": [100], "days_ahead": days}
    ).status_code == 400
