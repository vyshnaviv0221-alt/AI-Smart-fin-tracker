# AI Smart Finance Tracker

An AI-powered personal finance tracker that captures UPI/bank transaction
notifications on Android, categorises them, flags spending anomalies, and
forecasts future expenses.

## Project Structure

```
AI-SMART-FINANCE-TRACKER/
│
├── client/          ← Android app (Kotlin + Jetpack Compose)
├── server/          ← FastAPI REST API serving the three models
├── model/           ← ML training scripts
│   ├── training/
│   ├── artifacts/   ← trained models, git-ignored
│   └── evaluation/  ← confusion matrix, metrics JSON
├── supabase/        ← schema.sql (tables + row-level security)
└── database/        ← Datasets
    ├── raw/
    ├── processed/   ← generated
    └── upi_transactions/
```

## How it fits together

```
Notification (bank / UPI app)
  → ExpenseNotificationListener → ExpenseParser (regex)
  → ExpenseRepository.captureExpense()
      ├─ duplicate check (same merchant+amount within 60s)
      ├─ Room insert with an on-device keyword category  (always works offline)
      ├─ POST /categorize → better category, if confidence >= 0.50
      ├─ POST /anomaly    → unusual flag, judged within the category
      └─ Supabase sync (if signed in)
  → Every screen renders from the same Room Flow
```

The app is **offline-first**: Room is the source of truth, the ML server only
refines what is already saved, and Supabase sync is optional. Killing the
server mid-demo degrades categorisation to on-device keywords rather than
breaking anything.

## Components

### `client/` — Android App
Jetpack Compose app that reads **notifications** from an allowlist of payment,
banking and messaging apps, parses amount and merchant with regex, stores
transactions in Room, and enriches them by calling the server. Budgets,
analytics, predictions and recommendations are all computed from the user's own
captured data — no screen ships with sample values.

Cloud sync uses **Supabase** (GoTrue auth + PostgREST) over plain REST, reusing
the app's existing Retrofit stack. Run `supabase/schema.sql` in your project,
then set `supabase.url` and `supabase.anonKey` in `client/local.properties`
(see `client/local.properties.example`). Without them the app runs fine,
just without cloud sync.

### `server/` — Backend API
FastAPI backend exposing `/categorize`, `/anomaly`, and `/predict`.
See [server/README.md](server/README.md) for setup and endpoint details.

### `model/` — Machine Learning
- **Transaction Categorizer** — TF-IDF + Logistic Regression / Naive Bayes / Random Forest, classifying merchant text into 10 categories.
- **Anomaly Detector** — Isolation Forest over transaction amounts.
- **Expense Forecaster** — Random Forest regressor predicting monthly spend per category.

### `database/` — Datasets
Raw and processed CSVs, stored via Git LFS. Run `git lfs pull` after cloning.

## Tech Stack

| Layer | Technology |
|---|---|
| Android Client | Kotlin, Jetpack Compose, Room, Retrofit |
| Auth & cloud sync | Supabase (GoTrue + PostgREST) via REST |
| Backend Server | Python, FastAPI, Uvicorn |
| ML Models | scikit-learn, joblib, pandas, numpy |
| Local storage | Room (SQLite) on device |

## Current state

Honest status, so nothing here is a surprise:

- **The Android app has not been compiled yet.** The Gradle scaffolding is in
  place but no machine here has a JDK or Android SDK, so `./gradlew` has never
  run. Expect to iterate on the AGP/Kotlin version matrix on first sync.
- **The server runs and all 17 endpoint tests pass.** Its models are still
  trained on synthetic data by `train_server_models.py`; the real training
  scripts in `model/training/` run correctly and write to `model/artifacts/`.
- **The expense forecaster is weak and the code says so.** Out-of-fold R2 is
  ~0.11 against a per-category-mean baseline of ~0.10. Monthly household spend
  is dominated by irregular one-off purchases. `predict_expense.py` prints the
  baseline comparison on every run — quote that, not the forecast alone.
- **Do not train on `database/upi_transactions/`** — 10,000 rows but only 29
  distinct templates. See `database/README.md`.
