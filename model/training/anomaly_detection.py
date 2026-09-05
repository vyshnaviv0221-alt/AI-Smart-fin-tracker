"""
AI-Based Smart Expense Tracker
Model 3: Anomaly Detection Model

Pipeline: amount + category -> robust per-category deviation score
          -> Isolation Forest -> unusual flag

Two design decisions, both driven by measurements on
database/processed/sample_transactions_large.csv:

1. The category has to be part of the input.
   Fitting on the raw amount alone flagged 20 transactions and *all 20 were
   Rent* (31.7% of every rent payment), finding nothing in the other nine
   categories. It had learned "rent is expensive", so normal rent looked
   suspicious and an odd Rs 899 coffee never did.

2. The category must not be one-hot columns.
   One-hot(category) + log_amount spreads the flags out properly, but the
   Isolation Forest then picks a category column on ~10 of 11 random splits and
   barely looks at the amount, so Rs 15,000 spent on Food still scored "normal".

   Instead each transaction is reduced to ONE feature: how far its log amount
   sits from that category's own median, in robust (MAD) units. That directly
   encodes "unusual for this category", and it generalises to category/amount
   combinations never seen in training.

Flags are prompts for the user to confirm or dismiss (Human-in-the-Loop), not
verdicts.
"""

import json

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest

from paths import ARTIFACTS, EVALUATION, PROCESSED, require

CONTAMINATION = 0.03      # expect ~3% of transactions to be unusual
MAD_TO_SIGMA = 1.4826     # scales median-absolute-deviation to a std-like unit
MIN_SCALE = 0.05          # floor, so a category with near-zero spread can't divide by ~0


def fit_category_stats(frame: pd.DataFrame) -> dict:
    """Robust centre and scale of log1p(amount) for each category."""
    stats = {}
    for category, group in frame.groupby("category"):
        logs = np.log1p(group["amount"].to_numpy(dtype=float))
        median = float(np.median(logs))
        mad = float(np.median(np.abs(logs - median)))
        stats[category] = {"median": median, "scale": max(mad * MAD_TO_SIGMA, MIN_SCALE)}

    all_logs = np.log1p(frame["amount"].to_numpy(dtype=float))
    global_median = float(np.median(all_logs))
    global_mad = float(np.median(np.abs(all_logs - global_median)))
    stats["__global__"] = {
        "median": global_median,
        "scale": max(global_mad * MAD_TO_SIGMA, MIN_SCALE),
    }
    return stats


def deviation_scores(amounts, categories, stats: dict) -> np.ndarray:
    """Signed robust z-score of log amount within the transaction's category."""
    fallback = stats["__global__"]
    out = []
    for amount, category in zip(amounts, categories):
        s = stats.get(category, fallback)
        out.append((float(np.log1p(float(amount))) - s["median"]) / s["scale"])
    return np.asarray(out).reshape(-1, 1)


# 1. Load the same transaction dataset used for categorization
df = pd.read_csv(require(PROCESSED / "sample_transactions_large.csv"))
print(f"Loaded {len(df)} transactions\n")

# 2. Fit per-category stats, then the detector on the deviation score
category_stats = fit_category_stats(df)
X = deviation_scores(df["amount"], df["category"], category_stats)

model = IsolationForest(contamination=CONTAMINATION, random_state=42)
df["status"] = np.where(model.fit_predict(X) == -1, "UNUSUAL", "normal")
df["deviation"] = X.ravel().round(2)

# 3. Report
anomalies = df[df["status"] == "UNUSUAL"].reindex(
    df["deviation"].abs().sort_values(ascending=False).index
).dropna(subset=["status"])
print(f"Flagged {len(df[df['status'] == 'UNUSUAL'])} of {len(df)} transactions as unusual\n")

spread = df.groupby("category").agg(
    total=("status", "size"),
    flagged=("status", lambda s: (s == "UNUSUAL").sum()),
)
spread["pct"] = (spread["flagged"] / spread["total"] * 100).round(1)
print("Flagged per category (spread out, not concentrated in one category):")
print(spread.sort_values("flagged", ascending=False).to_string())

print("\nMost extreme flags, with how far they sit from their category's median:")
print(
    anomalies[anomalies["status"] == "UNUSUAL"][
        ["merchant_text", "amount", "category", "deviation"]
    ].head(12).to_string(index=False)
)

# 4. Save results, model, and the stats the server needs at inference time
df.to_csv(PROCESSED / "transactions_with_anomaly_flags.csv", index=False)
joblib.dump(model, ARTIFACTS / "anomaly_model.joblib")
with open(ARTIFACTS / "anomaly_category_stats.json", "w") as f:
    json.dump(category_stats, f, indent=2)
spread.to_csv(EVALUATION / "anomaly_flags_by_category.csv")

print(f"\nSaved results -> {PROCESSED / 'transactions_with_anomaly_flags.csv'}")
print(f"Saved model   -> {ARTIFACTS / 'anomaly_model.joblib'}")
print(f"Saved stats   -> {ARTIFACTS / 'anomaly_category_stats.json'}")
print(f"Saved report  -> {EVALUATION / 'anomaly_flags_by_category.csv'}")

# 5. Sanity check: the same amount must be judged differently per category
print("\n--- Same amount, different category ---")
demo = pd.DataFrame(
    [
        {"amount": 15000, "category": "Rent", "expected": "normal"},
        {"amount": 15000, "category": "Food", "expected": "UNUSUAL"},
        {"amount": 450, "category": "Food", "expected": "normal"},
        {"amount": 450, "category": "Rent", "expected": "UNUSUAL"},
        {"amount": 60000, "category": "Shopping", "expected": "UNUSUAL"},
        {"amount": 2800, "category": "Groceries", "expected": "normal"},
    ]
)
demo_X = deviation_scores(demo["amount"], demo["category"], category_stats)
demo["deviation"] = demo_X.ravel().round(2)
demo["status"] = np.where(model.predict(demo_X) == -1, "UNUSUAL", "normal")
demo["match"] = np.where(demo["status"] == demo["expected"], "ok", "MISMATCH")
print(demo[["amount", "category", "deviation", "status", "expected", "match"]].to_string(index=False))
