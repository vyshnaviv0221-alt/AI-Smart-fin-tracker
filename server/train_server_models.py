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

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest, RandomForestClassifier, RandomForestRegressor
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score
from sklearn.model_selection import train_test_split
from sklearn.naive_bayes import MultinomialNB
from sklearn.pipeline import Pipeline

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

N_PER_CATEGORY = 80


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


rows = []
for category, brands in CATEGORY_BRANDS.items():
    lo, hi = AMOUNT_RANGES[category]
    for _ in range(N_PER_CATEGORY):
        brand = random.choice(brands)
        rows.append({
            "merchant_text": make_text(brand),
            "amount": random.randint(lo, hi),
            "category": category,
        })
df = pd.DataFrame(rows).sample(frac=1, random_state=42).reset_index(drop=True)

print(f"Generated {len(df)} synthetic transactions across {len(CATEGORY_BRANDS)} categories")

# =====================================================================
# 1) Categorization model — merchant_text -> category
# =====================================================================
X_train, X_test, y_train, y_test = train_test_split(
    df["merchant_text"], df["category"], test_size=0.2, random_state=42, stratify=df["category"]
)

pipelines = {
    "naive_bayes": Pipeline([
        ("tfidf", TfidfVectorizer(ngram_range=(1, 1), min_df=1, max_df=0.9, sublinear_tf=True)),
        ("clf", MultinomialNB(alpha=0.5)),
    ]),
    "logistic_regression": Pipeline([
        ("tfidf", TfidfVectorizer(ngram_range=(1, 1), min_df=1, max_df=0.9, sublinear_tf=True)),
        ("clf", LogisticRegression(max_iter=1000, C=1.0)),
    ]),
    "random_forest": Pipeline([
        ("tfidf", TfidfVectorizer(ngram_range=(1, 1), min_df=1, max_df=0.9, sublinear_tf=True)),
        ("clf", RandomForestClassifier(n_estimators=200, max_depth=15, random_state=42)),
    ]),
}

best_model, best_score, best_name = None, 0, None
for name, pipe in pipelines.items():
    pipe.fit(X_train, y_train)
    acc = accuracy_score(y_test, pipe.predict(X_test))
    print(f"  categorizer [{name}] test accuracy: {acc:.3f}")
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

print("\nAll models saved to", OUT_DIR)
print("NOTE: these are demo-quality models trained on synthetic data.")
print("Replace with your real datasets by re-running your original scripts")
print("in model/training/ and pointing train_server_models.py at the real CSVs.")
