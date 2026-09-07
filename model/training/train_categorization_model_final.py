import os
import joblib
import pandas as pd
import numpy as np
from pathlib import Path

from sklearn.model_selection import train_test_split
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import StandardScaler
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, accuracy_score

# Resolve paths relative to script location
SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parents[1]
ARTIFACTS_DIR = PROJECT_ROOT / "model" / "artifacts"
ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)

# Locate dataset file
DATASET_PATH = SCRIPT_DIR / "Daily Household Transactions.csv"
if not DATASET_PATH.exists():
    DATASET_PATH = PROJECT_ROOT / "database" / "raw" / "Daily Household Transactions.csv"

if not DATASET_PATH.exists():
    raise FileNotFoundError(f"Dataset not found at '{DATASET_PATH}'. Please ensure 'Daily Household Transactions.csv' exists.")

print(f"Loading dataset from: {DATASET_PATH}")
df = pd.read_csv(DATASET_PATH)

# Check if the loaded file is a Git LFS pointer
if len(df.columns) == 1 and 'git-lfs' in str(df.columns[0]):
    raise ValueError(
        f"File at '{DATASET_PATH}' is a Git LFS pointer file! "
        f"Please replace it with the actual CSV dataset."
    )

# Clean column names (strip trailing/leading whitespace)
df.columns = df.columns.str.strip()

# Map capitalized column names to standard keys
if 'Category' in df.columns and 'category' not in df.columns:
    df = df.rename(columns={'Category': 'category'})

if 'Amount' in df.columns and 'amount' not in df.columns:
    df = df.rename(columns={'Amount': 'amount'})

# Filter strictly for expenses if 'Income/Expense' column exists
if 'Income/Expense' in df.columns:
    df = df[df['Income/Expense'].astype(str).str.strip().str.lower() == 'expense'].copy()

# Construct merchant_text feature from available string fields
if 'merchant_text' not in df.columns:
    sub_col = df['Subcategory'].fillna('') if 'Subcategory' in df.columns else ''
    note_col = df['Note'].fillna('') if 'Note' in df.columns else ''
    mode_col = df['Mode'].fillna('') if 'Mode' in df.columns else ''
    df['merchant_text'] = (sub_col.astype(str) + " " + note_col.astype(str) + " " + mode_col.astype(str)).str.strip()

# Format fields and handle missing values
df['merchant_text'] = df['merchant_text'].fillna('').astype(str)
df['amount'] = pd.to_numeric(df['amount'], errors='coerce').fillna(0)
df['category'] = df['category'].astype(str).str.strip()

# Remove empty or invalid entries
df = df[(df['merchant_text'] != '') & (df['category'] != '')]

# Remove rare categories with fewer than 2 samples to allow stratified split
category_counts = df['category'].value_counts()
valid_categories = category_counts[category_counts >= 2].index
df = df[df['category'].isin(valid_categories)].reset_index(drop=True)

if len(df) == 0:
    raise ValueError("Real rows parsed: 0. Ensure your CSV file contains valid transaction records and headers.")

print(f"Successfully loaded {len(df)} real transaction rows.")
print("\nCategory Distribution:\n", df['category'].value_counts())

X = df[['merchant_text', 'amount']]
y = df['category']

# 1. Stratified Train / Test Split
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.25, random_state=42, stratify=y
)

# 2. Preprocessing: TF-IDF for Text + StandardScaler for Numeric Amount
preprocessor = ColumnTransformer(
    transformers=[
        (
            'text',
            TfidfVectorizer(
                analyzer='char_wb',
                ngram_range=(3, 5),
                sublinear_tf=True
            ),
            'merchant_text'
        ),
        (
            'num',
            StandardScaler(),
            ['amount']
        )
    ]
)

# 3. Model Pipeline
model = Pipeline([
    ('preprocessor', preprocessor),
    ('clf', LogisticRegression(max_iter=1000, C=2.0, class_weight='balanced'))
])

# 4. Train & Evaluate
model.fit(X_train, y_train)
y_pred = model.predict(X_test)
acc = accuracy_score(y_test, y_pred)

print("\n" + "=" * 60)
print("=== EXPENSE CATEGORIZER MODEL RESULTS ===")
print("=" * 60)
print(f"Test Accuracy: {acc:.4f}\n")
print(classification_report(y_test, y_pred, zero_division=0))

# 5. Save Model Artifact
model_output_path = ARTIFACTS_DIR / "expense_category_model.joblib"
joblib.dump(model, model_output_path)
print(f"Saved trained model artifact -> {model_output_path}")