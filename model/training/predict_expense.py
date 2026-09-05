"""
AI-Based Smart Expense Tracker
Model 2: Monthly Expense Forecaster

Pipeline: household transactions -> monthly totals per category
          -> one-hot(Category) -> RandomForest -> log1p(amount)

Why the Month feature was dropped
---------------------------------
The original design used one-hot(Month, Category). Measured out-of-fold against
the same folds, the month feature actively hurt:

    Month + Category   R2 = 0.015    MAE = Rs 2004
    Category only      R2 = 0.107    MAE = Rs 1955
    baseline (category mean)  R2 = 0.101    MAE = Rs 1961

With 45 months and 22 categories, each (month, category) cell holds ~3
observations, so the month split fits noise. Category-only is the only variant
that beats a trivial per-category-mean baseline. The comparison is recomputed
and printed on every run, so the claim stays checkable.

Lag features (previous month, 3-month rolling mean, expanding category mean)
were also tested and made it worse; they are not included.

Output is written to model/artifacts/ and model/evaluation/.
"""

import json

import numpy as np
import pandas as pd
import joblib
from sklearn.ensemble import RandomForestRegressor
from sklearn.model_selection import KFold, cross_val_predict
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

from paths import ARTIFACTS, EVALUATION, RAW, require

print("=" * 62)
print("   AI-SMART FINANCE TRACKER - EXPENSE PREDICTION ENGINE   ")
print("=" * 62)

# 1. Load dataset
df = pd.read_csv(require(RAW / "Daily Household Transactions.csv"))

# 2. Filter expenses & clean dates
if 'Income/Expense' in df.columns:
    df = df[df['Income/Expense'] == 'Expense'].copy()

df['Date'] = pd.to_datetime(df['Date'], dayfirst=True, errors='coerce')
df = df.dropna(subset=['Date'])

# 3. Aggregate monthly spending per category
cat_monthly = df.groupby([df['Date'].dt.to_period('M'), 'Category'])['Amount'].sum().reset_index()
cat_monthly['Month'] = cat_monthly['Date'].dt.month

y_log = np.log1p(cat_monthly['Amount'])
y_true = cat_monthly['Amount']

# 4. Feature encodings -- the final model uses category only (see module docstring)
X_category = pd.get_dummies(cat_monthly[['Category']], columns=['Category'], drop_first=False)
X_month_category = pd.get_dummies(cat_monthly[['Month', 'Category']], columns=['Category'], drop_first=False)
feature_cols = X_category.columns.tolist()

kf = KFold(n_splits=5, shuffle=True, random_state=42)


def make_model():
    return RandomForestRegressor(n_estimators=400, min_samples_leaf=1, random_state=42)


def score_oof(pred_log):
    """All metrics are out-of-fold; nothing here is measured on training rows."""
    pred_actual = np.expm1(pred_log)
    return {
        "r2": round(float(r2_score(y_log, pred_log)), 4),
        "mae_rupees": round(float(mean_absolute_error(y_true, pred_actual)), 2),
        "rmse_rupees": round(float(np.sqrt(mean_squared_error(y_true, pred_actual))), 2),
    }


def baseline_category_mean():
    """Predict each category's historical mean. The bar the model must clear."""
    preds = np.zeros(len(cat_monthly))
    for train_idx, test_idx in kf.split(X_category):
        train_log = y_log.iloc[train_idx]
        overall = train_log.mean()
        means = train_log.groupby(cat_monthly.iloc[train_idx]['Category']).mean()
        preds[test_idx] = (
            cat_monthly.iloc[test_idx]['Category'].map(means).fillna(overall).to_numpy()
        )
    return preds


# 5. Compare candidates on identical folds
results = {
    "randomforest_category_only": score_oof(cross_val_predict(make_model(), X_category, y_log, cv=kf)),
    "randomforest_month_category": score_oof(cross_val_predict(make_model(), X_month_category, y_log, cv=kf)),
    "baseline_category_mean": score_oof(baseline_category_mean()),
    "baseline_global_mean": score_oof(np.full(len(cat_monthly), y_log.mean())),
}

print("\n--- MODEL COMPARISON (5-fold, out-of-fold predictions) ---")
print(f"Samples (month x category) : {len(cat_monthly)}")
print(f"Distinct categories        : {cat_monthly['Category'].nunique()}")
print(f"Distinct months            : {cat_monthly['Date'].nunique()}")
print()
print(f"{'predictor':<32}{'R2':>9}{'MAE (Rs)':>12}{'RMSE (Rs)':>12}")
print("-" * 65)
for name, m in results.items():
    print(f"{name:<32}{m['r2']:>9.4f}{m['mae_rupees']:>12.2f}{m['rmse_rupees']:>12.2f}")

chosen = results["randomforest_category_only"]
baseline = results["baseline_category_mean"]
print(
    f"\nChosen model beats the per-category-mean baseline by "
    f"{chosen['r2'] - baseline['r2']:+.4f} R2 and "
    f"Rs {baseline['mae_rupees'] - chosen['mae_rupees']:.2f} MAE."
)
print(
    "\nNOTE: R2 is low in absolute terms. Monthly household spend is dominated by\n"
    "      irregular one-off purchases, so a large share of the variance is not\n"
    "      predictable from category history alone. Quote the baseline comparison\n"
    "      rather than the forecast figure on its own."
)

# 6. Fit the chosen model on all data and save
model = make_model()
model.fit(X_category, y_log)

joblib.dump(model, ARTIFACTS / "final_expense_model.pkl")
with open(ARTIFACTS / "final_expense_model_columns.json", "w") as f:
    json.dump(feature_cols, f)

with open(EVALUATION / "forecaster_metrics.json", "w") as f:
    json.dump(
        {
            "model": "RandomForestRegressor (category one-hot only)",
            "target": "log1p(monthly amount per category)",
            "samples": int(len(cat_monthly)),
            "categories": int(cat_monthly['Category'].nunique()),
            "months": int(cat_monthly['Date'].nunique()),
            "cv_folds": 5,
            "results": results,
            "month_feature_dropped_because": (
                "one-hot(Month, Category) scored worse than category alone on identical "
                "folds; ~3 observations per (month, category) cell means the month split "
                "fits noise."
            ),
            "note": "All metrics are out-of-fold (cross_val_predict), never in-sample.",
        },
        f,
        indent=2,
    )

print(f"\nModel exported   -> {ARTIFACTS / 'final_expense_model.pkl'}")
print(f"Metrics exported -> {EVALUATION / 'forecaster_metrics.json'}")

# =========================================================
# 7. Live prediction demo
# =========================================================
print("\n" + "=" * 62)
print("         EXPECTED MONTHLY SPEND BY CATEGORY        ")
print("=" * 62 + "\n")

for cat in df['Category'].value_counts().head(5).index:
    row = pd.DataFrame(0, index=[0], columns=feature_cols)
    cat_col = f"Category_{cat}"
    if cat_col in row.columns:
        row[cat_col] = 1
    print(f"  {cat:<20} : Rs {np.expm1(model.predict(row)[0]):10.2f}")

print("\n" + "=" * 62)
