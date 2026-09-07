"""
Maps the household dataset's taxonomy onto the app's ten categories.

Why this exists
---------------
`Daily Household Transactions.csv` is the only sizeable source of REAL
labelled spending data in the project (2,461 rows). But its labels are a
personal expense diary's own vocabulary -- 26 of them, including "maid",
"garbage disposal" and "water (jar /tanker)" -- while the Android app knows
exactly ten. A model trained directly on those labels cannot drive the app:
it emits categories the UI has no icon, colour, budget or correction entry
for.

Mapping the labels lets that real data train the app's categorizer instead of
being unusable. Each choice below is a judgement call; they are written out
rather than hidden so they can be argued with.
"""

# Household label -> app category.
HOUSEHOLD_TO_APP = {
    # --- direct matches ---
    "Food": "Food",
    "Rent": "Rent",
    "Investment": "Investment",

    # --- transport of any kind is Travel ---
    "Transportation": "Travel",
    "Tourism": "Travel",

    # --- health and personal care ---
    "Health": "Healthcare",
    "Beauty": "Healthcare",      # salon / personal care; the app has no
    "Grooming": "Healthcare",    # "Personal care", and Healthcare is nearest

    # --- things bought ---
    "Apparel": "Shopping",
    "Gift": "Shopping",
    "Festivals": "Shopping",

    # --- household supplies read as groceries ---
    "Household": "Groceries",
    "Cook": "Groceries",

    # --- recurring services and utilities ---
    "subscription": "Bills",
    "maid": "Bills",
    "garbage disposal": "Bills",
    "water (jar /tanker)": "Bills",
    "Documents": "Bills",
    "Education": "Bills",

    # --- leisure and self-directed spending ---
    "Culture": "Entertainment",
    "Self-development": "Entertainment",

    # --- money moving rather than being spent ---
    "Money transfer": "Transfer",
    "Family": "Transfer",

    # --- savings vehicles ---
    "Public Provident Fund": "Investment",
    "Recurring Deposit": "Investment",
    "Fixed Deposit": "Investment",

    # "Other" is genuinely unknown. Mapping it to any real category would
    # teach the model to guess; rows labelled Other are dropped from training
    # instead (see load_mapped_household).
    "Other": None,
}

APP_CATEGORIES = [
    "Food", "Groceries", "Travel", "Shopping", "Bills",
    "Healthcare", "Entertainment", "Investment", "Rent", "Transfer",
]


def map_category(household_label: str) -> str | None:
    """Returns the app category, or None if the row should be dropped."""
    return HOUSEHOLD_TO_APP.get(str(household_label).strip())


def build_merchant_text(row) -> str:
    """
    Reconstructs a merchant-like string from the diary's free-text columns.

    The app sends merchant names parsed out of bank notifications
    ("Swiggy Bangalore"), whereas this dataset has Subcategory / Note / Mode
    ("snacks", "Idli medu Vada mix 2 plates", "Cash"). They are different
    kinds of text, which is why a model trained on one scores poorly on the
    other -- but the vocabulary still carries real category signal, so it is
    worth training on alongside merchant strings rather than instead of them.
    """
    parts = []
    for column in ("Subcategory", "Note"):
        value = row.get(column)
        if value is not None and str(value).strip().lower() not in ("", "nan"):
            parts.append(str(value).strip())
    return " ".join(parts)
