# Server — AI Smart Finance Tracker API

FastAPI backend that serves the three ML models to the Android app.

## Why this replaces the original model/artifacts/*.pkl files
Those files were saved with scikit-learn 0.24.2 and fail to load on any
current scikit-learn (1.x) — confirmed by trying to load every one of them.
This is a known sklearn pickle-compatibility issue, not a bug in the original
training code. The fix is to retrain with your current environment.

`train_server_models.py` does that: it regenerates all three models
(categorizer, anomaly detector, forecaster) using the same category/brand
logic already defined in `model/training/train_categorization_model_final.py`,
but trained fresh so they're loadable. Right now it uses synthetic data
(no external CSV needed) so it runs immediately — swap in your real datasets
when ready (see "Using real data" below).

## Setup

```bash
pip install -r requirements.txt
python train_server_models.py      # generates models/*.joblib (only needed once, or after retraining)
uvicorn app.main:app --reload --port 8000
```

Then open http://127.0.0.1:8000/docs for interactive API docs.

## Endpoints

| Endpoint | Method | Body | Returns |
|---|---|---|---|
| `/` | GET | — | health check + whether models loaded |
| `/categorize` | POST | `{"merchant_text": "...", "amount": 420}` | `{"category": "...", "confidence": 0.xx}` |
| `/anomaly` | POST | `{"merchant_text": "...", "amount": 95000}` | `{"amount": 95000, "status": "normal"/"UNUSUAL", "category": "...", "deviation": 5.08}` |
| `/predict` | POST | `{"month": 11, "category": "Food"}` | `{"category": "Food", "predicted_amount": 508.75}` |

All three were tested locally and return real predictions (not stubs).
A 17-case endpoint test suite covering success paths, validation errors and
category-aware anomaly behaviour passes against a running server.

### `/anomaly` is category-aware
`merchant_text` is not decorative: the server categorises it first, then scores
the amount against **that category's** distribution. An amount-only detector
flagged every rent payment and nothing else. So:

    {"merchant_text": "House Rent NEFT", "amount": 15000}  -> normal   (dev -0.32)
    {"merchant_text": "Swiggy Order",    "amount": 15000}  -> UNUSUAL  (dev +5.57)
    {"merchant_text": "House Rent NEFT", "amount": 450}    -> UNUSUAL  (dev -11.27)

`deviation` is a robust z-score of log(amount) within the category, so it is
directly explainable: "4x further from the median than usual".

## Using real data instead of synthetic
1. Get `sample_transactions.csv` and `Daily Household Transactions.csv` into this folder (pull via `git lfs pull` from the main repo)
2. Edit `train_server_models.py`: replace the synthetic generation step for
   the categorizer with `pd.read_csv("sample_transactions.csv")`, and the
   forecaster's synthetic `history_rows` with your real household transactions
   file (same aggregation logic as `model/training/predict_expense.py`)
3. Re-run `python train_server_models.py`

## Connecting the Android app
Point HTTP calls (e.g. via Retrofit) at this server's base URL. On an Android
emulator, `127.0.0.1` on your machine is reachable from the emulator at
`10.0.2.2` — use that as the base URL during local development.
