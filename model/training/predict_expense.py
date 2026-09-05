import os
import joblib
import pandas as pd
import numpy as np
from pathlib import Path

from sklearn.ensemble import RandomForestRegressor
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

# Resolve path directly inside model/training/ directory
SCRIPT_DIR = Path(__file__).resolve().parent
RAW_DATA_PATH = SCRIPT_DIR / "Daily Household Transactions.csv"

# Resolve artifacts directory relative to project root
PROJECT_ROOT = SCRIPT_DIR.parents[1]
ARTIFACTS_DIR = PROJECT_ROOT / "model" / "artifacts"
ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)

# 1. Load Dataset
if not RAW_DATA_PATH.exists():
    raise FileNotFoundError(f"Dataset not found at '{RAW_DATA_PATH}'. Ensure 'Daily Household Transactions.csv' is inside model/training/")

df = pd.read_csv(RAW_DATA_PATH)
df.columns = df.columns.str.strip()

# Filter strictly for Expense records
if 'Income/Expense' in df.columns:
    df = df[df['Income/Expense'].astype(str).str.strip().str.lower() == 'expense'].copy()

# Parse 'Date' column cleanly
df['Parsed_Date'] = pd.to_datetime(df['Date'], dayfirst=True, errors='coerce')
df = df.dropna(subset=['Parsed_Date'])

# Ensure numeric Amount values
df['Amount'] = pd.to_numeric(df['Amount'], errors='coerce').fillna(0)

# Aggregate total spending per day
daily_df = df.groupby(df['Parsed_Date'].dt.date)['Amount'].sum().reset_index()
daily_df['Date'] = pd.to_datetime(daily_df['Parsed_Date'])
daily_df = daily_df.sort_values('Date').reset_index(drop=True)

# 2. Feature Engineering
daily_df['Day'] = daily_df['Date'].dt.day
daily_df['DayOfWeek'] = daily_df['Date'].dt.dayofweek
daily_df['Month'] = daily_df['Date'].dt.month
daily_df['IsWeekend'] = daily_df['DayOfWeek'].isin([5, 6]).astype(int)

# Historical rolling trend features
daily_df['Lag_1'] = daily_df['Amount'].shift(1)
daily_df['Rolling_7_Mean'] = daily_df['Amount'].shift(1).rolling(window=7, min_periods=1).mean()

# Drop initial NaNs created by shift logic
daily_df = daily_df.dropna().reset_index(drop=True)

# Feature & Target Selection
features = ['Day', 'DayOfWeek', 'Month', 'IsWeekend', 'Lag_1', 'Rolling_7_Mean']
X = daily_df[features]
y = daily_df['Amount']

# 3. Time-Sequential Train/Test Split (No shuffle to preserve temporal order)
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, shuffle=False)

# 4. Train RandomForest Model
model = RandomForestRegressor(n_estimators=100, random_state=42)
model.fit(X_train, y_train)
print("model accuracy",model.score(X_test, y_test))

# 5. Evaluate Performance
y_pred = model.predict(X_test)
mae = mean_absolute_error(y_test, y_pred)
rmse = np.sqrt(mean_squared_error(y_test, y_pred))
r2 = r2_score(y_test, y_pred)


print("=" * 60)
print("   EXPENSE FORECASTING ENGINE RESULTS (Daily Household CSV)   ")
print("=" * 60)
print(f"Total Aggregated Days Analyzed : {len(daily_df)}")
print(f"Mean Absolute Error (MAE)      : ₹{mae:.2f}")
print(f"Root Mean Squared Error (RMSE) : ₹{rmse:.2f}")
print(f"R² Score                       : {r2:.4f}")

# 6. Save Model Artifact
model_output_path = ARTIFACTS_DIR / "expense_forecaster_model.joblib"
joblib.dump(model, model_output_path)
print(f"\nSaved trained model artifact -> {model_output_path}")