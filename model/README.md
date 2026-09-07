# Model — AI Smart Finance Tracker

Python training scripts for the three ML models.

## Structure

```
model/
├── training/
│   ├── train_categorization_model_final.py   ← current categorizer
│   ├── train_categorization_model1.py        ← earlier iteration (see Notes)
│   ├── anomaly_detection.py                  ← Isolation Forest anomaly detector
│   └── predict_expense.py                    ← RandomForest monthly expense forecaster
│
└── artifacts/      ← generated output, git-ignored (see Notes)
```

## Models

| Model | Script | Task |
|---|---|---|
| Transaction Categorizer | `train_categorization_model_final.py` | merchant text → one of 10 categories |
| Anomaly Detector | `anomaly_detection.py` | flag amounts unusual **for their category** |
| Expense Forecaster | `predict_expense.py` | predict monthly spend per category |

## Usage

Use a Python 3.11 or 3.12 environment (`pip install -r model/requirements.txt`);
the pinned numpy/pandas have no wheels for 3.14.

```bash
cd model/training
python train_categorization_model_final.py   # categorizer + confusion matrix
python predict_expense.py                    # forecaster + RMSE/MAE
python anomaly_detection.py                  # anomaly flags + saved detector
```

Paths resolve from `paths.py`, so these work from any working directory.

## Notes

**`artifacts/` is git-ignored.** It previously held nine `.pkl`/`.joblib` files
saved with scikit-learn 0.24 that no longer load on scikit-learn 1.x, six of which
had no producing script at all. Trained models are build output — regenerate them
by running the scripts above. The old binaries remain in git history if needed.

**`train_categorization_model_final.py` is the reference for methodology.** It
augments 54 hand-labeled rows with synthetic brand data but tests only on held-out
**real** rows, specifically so the score isn't inflated by testing on the same
generator that produced the training data. Keep that property in any rewrite.

**`train_categorization_model1.py` was deleted.** It trained on
`database/upi_transactions/`, which turns out to be 29 unique templates repeated
~345 times with a deterministic template-to-label mapping -- a classifier scores
near 100% on it by memorising a lookup table. It also read that CSV with
`header=None` despite the file having a header, so the string `Transaction_Text`
was trained on as a sample labelled `Label`. See `database/README.md`.

**`predict_expense.py` now reports out-of-fold metrics.** It previously computed
MAE from predictions on the training rows, which understated the error; it now
uses `cross_val_predict` and reports both MAE and RMSE, written to
`evaluation/forecaster_metrics.json`.

**Sample size is the limitation to state in the report.** The categorizer's test
set is the held-out portion of 54 hand-labelled rows (17 rows), so quote the
count next to any percentage: "94% (16/17 held-out real transactions)".

## Measured results

Regenerate with the three commands above; everything below is written to
`evaluation/` on each run.

**Categorizer** — logistic regression, 637 training rows (37 real + 600
synthetic), tested on 17 held-out **real** rows:
accuracy 0.94, macro-F1 0.95. Both errors are one Food and one Shopping
confusion; see `evaluation/categorizer_confusion_matrix.png`.

**Forecaster** — out-of-fold over 299 month-category rows (22 categories,
45 months):

| predictor | R2 | MAE | RMSE |
|---|---|---|---|
| RandomForest, category only | **0.107** | **Rs 1955** | Rs 6762 |
| RandomForest, month + category | -0.243 | Rs 2172 | Rs 6784 |
| baseline: per-category mean | 0.101 | Rs 1961 | Rs 6767 |
| baseline: global mean | 0.000 | Rs 2037 | Rs 6845 |

The model barely beats a per-category mean, and adding the month makes it
clearly worse. That is the honest finding: monthly household spend is dominated
by irregular one-off purchases. Lag features and per-category time series were
also tested and did not help.

**Anomaly detector** — two defects were found by measuring, not by reading:

1. The amount-only version flagged 20 transactions of which **all 20 were
   Rent** (31.7% of every rent payment) and found nothing in the other nine
   categories: it had learned "rent is expensive". Scoring each transaction by
   how far its log amount sits from its own category's median (in MAD units)
   fixed that.
2. That version was still two-sided, and **17 of its 20 flags were
   transactions that were unusually *cheap*** -- a Rs 60 Rapido ride scored
   -6.66 and was reported as unusual. This is an expense tracker: alerting on
   a cheap fare is noise. Flags are now one-sided (high side only).

The result correctly separates Rs 15,000 on Rent (normal) from Rs 15,000 on
Food (unusual), and no longer flags Rs 450 rent or a Rs 50 coffee. See
`evaluation/anomaly_flags_by_category.csv`.


## Trained artifacts (tracked)

`artifacts/` now tracks the three deliverable models, ~7 MB total:

| file | what it is | trained by |
|---|---|---|
| `expense_category_model.joblib` | 26-class household categorizer, 0.912 on 544 held-out real rows | `train_categorization_model_final.py` |
| `expense_forecaster_model.joblib` | daily total-spend RandomForest, out-of-fold R2 0.09 | `predict_expense.py` |
| `anomaly_model.joblib` + `anomaly_category_stats.json` | per-category deviation detector | `anomaly_detection.py` |

A blanket `*.joblib` ignore rule used to sit here. It is why an earlier
"exported as joblib file" commit contained no joblib: git accepted the commit
and silently dropped the model, so the push looked successful.

## Which model serves what

The app does **not** use `expense_category_model.joblib`, and that is not an
oversight:

- It emits the household dataset's 26 labels (`Transportation`,
  `subscription`, `maid`, `garbage disposal`, ...). The Android app knows ten,
  and has an icon, colour, budget row and correction entry for each of those.
- It is trained on expense-diary text (`Subcategory` + `Note` + `Mode`, e.g.
  "Idli medu Vada mix 2 plates"), not merchant names. Fed the merchant strings
  the app actually sends, it scored **0 of 12** -- "House Rent NEFT" came back
  as `Tourism`.

Both facts were measured, not assumed. The model is good at its own task; the
task differs from the app's.

Two attempts were made to bridge that gap and both were rejected on evidence:

1. **Map the household labels onto the app's ten and train on both.**
   `category_mapping.py` holds that mapping (kept, because it is useful for
   analysis). Adding 1,979 mapped household rows to the merchant training set
   left accuracy unchanged at 0.947 and made individual predictions *worse* --
   "Swiggy Bangalore" confidence fell 0.70 to 0.36. The vocabularies do not
   overlap, so the extra rows dilute the merchant signal.
2. **Serve the daily forecaster to the Predictions screen.** See
   `/forecast/daily` in `server/app/main.py`. Sweeping recent spend from
   Rs 0/day to Rs 35,000/day moves the 7-day forecast with a correlation of
   only +0.281, non-monotonically: Rs 0/day forecasts Rs 9,735 while
   Rs 1,000/day forecasts Rs 7,438. It cannot be shown as "your predicted
   spend" without misleading people.

What *was* adopted from the upstream work is the categorizer's architecture --
character n-grams (`char_wb`, 3-5) rather than word TF-IDF. Merchant strings
are short and full of unseen tokens; word features have nothing to match on
for a merchant not in training, while character n-grams still find the brand.
Retraining the server's own 10-category model that way lifted median
confidence on real merchant probes from 0.53 to 0.75.
