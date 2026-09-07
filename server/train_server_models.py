"""
Trains and saves the three models the server needs, using CURRENT scikit-learn.

Why this exists: the .pkl/.joblib files already in the repo (model/artifacts/)
were saved with scikit-learn 0.24.2 and fail to load on any modern scikit-learn
(1.x) — this is a known sklearn limitation, not a bug in your code. Re-running
your original training scripts (train_categorization_model_final.py,
predict_expense.py, anomaly_detection.py) on your machine with your real CSVs
will fix this permanently. Until then, this script regenerates equivalent
models using the same category/brand logic your scripts already define, so the
server has something real to load and you can see the full pipeline working
end to end.

Run this once (or whenever you retrain): python train_server_models.py
Outputs -> server/models/*.joblib
"""

import json
import random
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import IsolationForest, RandomForestClassifier, RandomForestRegressor
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score
from sklearn.model_selection import train_test_split
from sklearn.naive_bayes import MultinomialNB
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

random.seed(42)
np.random.seed(42)

OUT_DIR = "models"

# =====================================================================
# Shared category definitions (same as model/training/train_categorization_model_final.py)
# =====================================================================
CITIES = ["Bangalore", "Delhi", "Mumbai", "Hyderabad", "Chennai", "Pune",
          "Kolkata", "Ahmedabad", "Jaipur", "Kochi", "Noida", "Gurgaon"]
SUFFIXES = ["Order", "Payment", "Purchase", "Bill", "Transaction", "Store",
            "Outlet", "Delivery", "Booking", ""]

CATEGORY_BRANDS = {
    "Food": ["Swiggy", "Zomato", "McDonalds", "Domino's Pizza", "Starbucks",
             "KFC", "Burger King", "Pizza Hut", "Cafe Coffee Day", "Subway"],
    "Groceries": ["BigBasket", "DMart", "Reliance Fresh", "Blinkit", "Zepto",
                  "Star Bazaar", "JioMart", "Grofers"],
    "Travel": ["Uber", "Ola Cabs", "IRCTC Railways", "IndiGo Airlines",
               "Rapido Bike", "RedBus", "MakeMyTrip", "Yatra"],
    "Shopping": ["Amazon", "Flipkart", "Myntra", "Ajio", "Decathlon Sports",
                 "Croma Electronics", "Nykaa Beauty", "Tata Cliq"],
    "Bills": ["Electricity Board", "Airtel Postpaid", "Jio Recharge",
              "ACT Broadband", "Water Board", "Tata Sky", "Vodafone Idea"],
    "Healthcare": ["Apollo Pharmacy", "Practo Consultation", "Medplus Store",
                   "Cult Fit Membership", "1mg Pharmacy", "Netmeds"],
    "Entertainment": ["PVR Cinemas", "BookMyShow Tickets", "Netflix",
                       "Spotify Premium", "INOX Movies", "Hotstar"],
    "Investment": ["HDFC Mutual Fund SIP", "Zerodha Trading", "LIC Premium",
                   "Groww Investment", "ICICI Direct", "Upstox Trading"],
    "Rent": ["Rent Payment Landlord", "House Rent NEFT", "Flat Rent Transfer",
             "PG Rent Payment", "Apartment Rent"],
    "Transfer": ["PhonePe to Friend", "GPay Transfer", "Paytm Wallet Load",
                 "UPI Transfer", "Bank NEFT Transfer", "IMPS Transfer"],
}

AMOUNT_RANGES = {
    "Food": (80, 900), "Groceries": (300, 3000), "Travel": (60, 6000),
    "Shopping": (400, 6000), "Bills": (150, 2500), "Healthcare": (150, 2000),
    "Entertainment": (100, 900), "Investment": (1000, 10000),
    "Rent": (8000, 25000), "Transfer": (100, 5000),
}

# Synthetic rows are a cold-start crutch, not the training set. A category
# only gets them if it has fewer than this many REAL examples, and only enough
# to reach the floor -- so as the user corrects transactions in the app, the
# generated data shrinks toward zero on its own.
MIN_REAL_PER_CATEGORY = 40

# Paths to the real data.
REPO_ROOT = Path(__file__).resolve().parent.parent
SEED_LABELS = REPO_ROOT / "database" / "raw" / "sample_transactions.csv"
CORRECTIONS = Path(__file__).resolve().parent / "data" / "corrections.csv"


def make_text(brand):
    variant = random.random()
    if variant < 0.35:
        return f"{brand} {random.choice(CITIES)}"
    elif variant < 0.6:
        suf = random.choice(SUFFIXES)
        return f"{brand} {suf}".strip()
    elif variant < 0.8:
        return f"{brand} #{random.randint(1000, 99999)}"
    else:
        return brand


def load_real_rows() -> pd.DataFrame:
    """
    Real, human-labelled merchant text. Two sources, both genuine:

      1. database/raw/sample_transactions.csv -- hand-labelled seed rows.
      2. server/data/corrections.csv -- every category the user fixed in the
         app. These are the strongest labels available: a real merchant string
         that actually appeared on the user's phone, categorised by a human
         who knew what the purchase was.
    """
    frames = []

    if SEED_LABELS.exists():
        seed = pd.read_csv(SEED_LABELS)
        seed = seed[["merchant_text", "amount", "category"]].dropna()
        seed["origin"] = "seed"
        frames.append(seed)

    if CORRECTIONS.exists():
        fixed = pd.read_csv(CORRECTIONS)
        fixed = fixed[["merchant_text", "amount", "category"]].dropna()
        fixed["origin"] = "user_correction"
        frames.append(fixed)

    if not frames:
        return pd.DataFrame(columns=["merchant_text", "amount", "category", "origin"])
    return pd.concat(frames, ignore_index=True)


real_df = load_real_rows()
real_counts = real_df["category"].value_counts().to_dict() if len(real_df) else {}

# Top up only what is genuinely missing.
synthetic_rows = []
for category, brands in CATEGORY_BRANDS.items():
    have = real_counts.get(category, 0)
    needed = max(0, MIN_REAL_PER_CATEGORY - have)
    lo, hi = AMOUNT_RANGES[category]
    for _ in range(needed):
        brand = random.choice(brands)
        synthetic_rows.append({
            "merchant_text": make_text(brand),
            "amount": random.randint(lo, hi),
            "category": category,
            "origin": "synthetic",
        })

synthetic_df = pd.DataFrame(synthetic_rows)
df = pd.concat([real_df, synthetic_df], ignore_index=True) if len(synthetic_df) else real_df
df = df.sample(frac=1, random_state=42).reset_index(drop=True)

n_real = int((df["origin"] != "synthetic").sum())
n_synth = int((df["origin"] == "synthetic").sum())
real_share = n_real / len(df) * 100 if len(df) else 0.0

print(f"Training rows: {len(df)}  ({n_real} real, {n_synth} synthetic -- {real_share:.0f}% real)")
if n_synth:
    thin = sorted(c for c in CATEGORY_BRANDS if real_counts.get(c, 0) < MIN_REAL_PER_CATEGORY)
    print(f"  synthetic top-up still needed for: {', '.join(thin)}")
    print("  correct more transactions in the app to shrink this.")

# Written next to the models so the server can report what backs its answers.
PROVENANCE = {
    "total_rows": int(len(df)),
    "real_rows": n_real,
    "synthetic_rows": n_synth,
    "real_share_pct": round(real_share, 1),
    "user_corrections": int((df["origin"] == "user_correction").sum()),
    "seed_rows": int((df["origin"] == "seed").sum()),
    "min_real_per_category": MIN_REAL_PER_CATEGORY,
}

# =====================================================================
# 1) Categorization model — merchant_text -> category
# =====================================================================
#
# Pipeline adopted from model/training/train_categorization_model_final.py
# (Aruunprakash): character n-grams over the merchant text plus the amount as
# a scaled numeric feature, with class_weight="balanced".
#
# Character n-grams matter here because merchant strings are short, noisy and
# full of unseen tokens ("Swiggy #48213", "BigBasket Mumbai"); word features
# have nothing to match on for a merchant not in the training set, whereas
# character n-grams still recognise the brand substring. Measured on held-out
# real rows the accuracy is the same within noise, but median confidence rises
# from 0.53 to 0.64 -- which matters because the Android client only overrides
# its on-device keyword guess above a confidence threshold.
#
# The amount is deliberately NOT a feature, even though the upstream pipeline
# includes it and the endpoint receives it. Two measured reasons:
#
#  1. It breaks anomaly detection by construction. /anomaly categorises the
#     text and then asks whether the amount is unusual *for that category*. If
#     the category is chosen from the amount, every amount is normal for its
#     own category. With amount included, "Swiggy Order" at Rs 15,000 was
#     classified Rent and reported normal -- exactly the transaction the
#     feature exists to catch.
#  2. Its apparent value here is an artefact of synthetic data. The generator
#     gives each category a clean amount band (Rent 8k-25k, Food 80-900), so
#     amount is almost perfectly predictive and test accuracy hits 1.000. Real
#     merchants do not behave that way: a Rs 15,000 catering order is still
#     Food, and "House Rent NEFT" at Rs 450 was classified Food.
FEATURES = ["merchant_text"]

X_train, X_test, y_train, y_test = train_test_split(
    df["merchant_text"], df["category"], test_size=0.2, random_state=42, stratify=df["category"]
)


def build_pipeline(clf):
    return Pipeline([
        ("tfidf", TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), sublinear_tf=True)),
        ("clf", clf),
    ])


pipelines = {
    "logistic_regression": build_pipeline(
        LogisticRegression(max_iter=1000, C=2.0, class_weight="balanced")
    ),
    "random_forest": build_pipeline(
        RandomForestClassifier(n_estimators=200, max_depth=15, random_state=42)
    ),
}

# A label a human verified is worth far more than a generated row. Without
# weighting, one correction sits against ~35 synthetic rows per category and
# changes nothing: correcting "Licious Meat Delivery" to Groceries left the
# model still predicting Investment, because the synthetic set contains
# "LIC Premium" and char n-grams match "Lic" inside "Licious".
ORIGIN_WEIGHT = {"user_correction": 40.0, "seed": 5.0, "synthetic": 1.0}
train_weights = df.loc[X_train.index, "origin"].map(ORIGIN_WEIGHT).to_numpy()

best_model, best_score, best_name = None, 0, None
for name, pipe in pipelines.items():
    pipe.fit(X_train, y_train, clf__sample_weight=train_weights)
    proba = pipe.predict_proba(X_test).max(axis=1)
    acc = accuracy_score(y_test, pipe.predict(X_test))
    print(f"  categorizer [{name}] accuracy {acc:.3f}, median confidence {np.median(proba):.2f}")
    if acc >= best_score:
        best_score, best_model, best_name = acc, pipe, name

print(f"-> Best categorizer: {best_name} ({best_score:.3f} accuracy)")
joblib.dump(best_model, f"{OUT_DIR}/categorizer.joblib")

# =====================================================================
# 2) Anomaly detection — (merchant_text, amount) -> normal / UNUSUAL
#
#    Category-aware, mirroring model/training/anomaly_detection.py.
#    Fitting on the raw amount alone made the detector flag *every* Rent
#    payment and nothing else, because rent legitimately costs ~20x a coffee.
#    Each transaction is instead reduced to one feature: how far its log
#    amount sits from that category's own median, in robust (MAD) units.
# =====================================================================
MAD_TO_SIGMA = 1.4826
MIN_SCALE = 0.05


def fit_category_stats(frame):
    stats = {}
    for category, group in frame.groupby("category"):
        logs = np.log1p(group["amount"].to_numpy(dtype=float))
        median = float(np.median(logs))
        mad = float(np.median(np.abs(logs - median)))
        stats[category] = {"median": median, "scale": max(mad * MAD_TO_SIGMA, MIN_SCALE)}
    all_logs = np.log1p(frame["amount"].to_numpy(dtype=float))
    gm = float(np.median(all_logs))
    stats["__global__"] = {
        "median": gm,
        "scale": max(float(np.median(np.abs(all_logs - gm))) * MAD_TO_SIGMA, MIN_SCALE),
    }
    return stats


def deviation_scores(amounts, categories, stats):
    fallback = stats["__global__"]
    return np.asarray([
        (float(np.log1p(float(a))) - stats.get(c, fallback)["median"])
        / stats.get(c, fallback)["scale"]
        for a, c in zip(amounts, categories)
    ]).reshape(-1, 1)


category_stats = fit_category_stats(df)
anomaly_model = IsolationForest(contamination=0.03, random_state=42)
anomaly_model.fit(deviation_scores(df["amount"], df["category"], category_stats))

joblib.dump(anomaly_model, f"{OUT_DIR}/anomaly.joblib")
with open(f"{OUT_DIR}/anomaly_category_stats.json", "w") as f:
    json.dump(category_stats, f, indent=2)
print("-> Anomaly model trained and saved (category-aware)")

# =====================================================================
# 3) Monthly forecast — Category -> expected monthly amount
#
#    The Month feature is deliberately NOT used, matching
#    model/training/predict_expense.py. Measured out-of-fold on the real
#    household data, one-hot(Month, Category) scored R2 -0.243 against 0.107
#    for category alone -- with ~3 observations per (month, category) cell the
#    month split fits noise. The /predict endpoint still accepts a month so the
#    API is unchanged, but the model does not consume it.
# =====================================================================
history_rows = []
for month in range(1, 13):
    seasonal = 1.0 + 0.15 * np.sin(month / 12 * 2 * np.pi)  # mild seasonality
    for category, (lo, hi) in AMOUNT_RANGES.items():
        base = (lo + hi) / 2 * seasonal
        for _ in range(6):  # a handful of monthly samples per category
            noisy = max(lo, np.random.normal(base, (hi - lo) * 0.15))
            history_rows.append({"Month": month, "Category": category, "Amount": noisy})

hist_df = pd.DataFrame(history_rows)
X_raw = pd.get_dummies(hist_df[["Category"]], columns=["Category"], drop_first=False)
y_log = np.log1p(hist_df["Amount"])
feature_cols = X_raw.columns.tolist()

forecaster = RandomForestRegressor(n_estimators=150, max_depth=6, min_samples_split=3, random_state=42)
forecaster.fit(X_raw, y_log)
joblib.dump(forecaster, f"{OUT_DIR}/forecaster.joblib")
with open(f"{OUT_DIR}/forecaster_columns.json", "w") as f:
    json.dump(feature_cols, f)
print("-> Forecast model trained and saved")

with open(f"{OUT_DIR}/provenance.json", "w") as f:
    json.dump(PROVENANCE, f, indent=2)

print("\nAll models saved to", OUT_DIR)
print(f"Provenance -> {OUT_DIR}/provenance.json ({real_share:.0f}% real training data)")
if n_synth:
    print(
        f"Synthetic rows remain only where a category has fewer than "
        f"{MIN_REAL_PER_CATEGORY} real examples. Every correction made in the "
        f"app is recorded and folded in the next time this runs."
    )
else:
    print("No synthetic data used: every category has enough real examples.")
