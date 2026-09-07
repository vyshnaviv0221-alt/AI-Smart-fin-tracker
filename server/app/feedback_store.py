"""
Stores the corrections users make in the app, so the model can be retrained on
real transactions instead of invented ones.

Why this exists
---------------
Every category the app shows starts as a guess. When the user opens a
transaction and changes its category, that is a human-verified label for a
merchant string that genuinely occurred on their phone -- the only source of
real, in-domain training data this project has in any quantity.

Before this, corrections updated the local Room row and stopped there. The
model kept being retrained on 800 generated transactions built from a
hardcoded brand list, so the app's categorisation could never improve no
matter how much the user used it.

Corrections are appended to a CSV rather than a database because
train_server_models.py needs to read them as a dataframe, they are small, and
a plain file is inspectable -- you can open it and see exactly what the model
learned from.
"""

import csv
import threading
from datetime import datetime, timezone
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
CORRECTIONS_PATH = DATA_DIR / "corrections.csv"

FIELDS = ["merchant_text", "category", "amount", "source", "recorded_at"]

# Appends arrive from request handlers; uvicorn serves them concurrently.
_lock = threading.Lock()


def _ensure_file() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if not CORRECTIONS_PATH.exists():
        with CORRECTIONS_PATH.open("w", newline="", encoding="utf-8") as f:
            csv.DictWriter(f, fieldnames=FIELDS).writeheader()


def record_correction(
    merchant_text: str,
    category: str,
    amount: float,
    source: str = "user_correction",
) -> int:
    """
    Appends one verified label. Returns the total number held.

    Duplicates are kept deliberately: if the same merchant is corrected to the
    same category ten times that is ten pieces of evidence, and the trainer
    uses the repetition as weight rather than us discarding it here.
    """
    merchant_text = merchant_text.strip()
    category = category.strip()
    if not merchant_text or not category:
        raise ValueError("merchant_text and category are both required")

    with _lock:
        _ensure_file()
        with CORRECTIONS_PATH.open("a", newline="", encoding="utf-8") as f:
            csv.DictWriter(f, fieldnames=FIELDS).writerow({
                "merchant_text": merchant_text,
                "category": category,
                "amount": float(amount),
                "source": source,
                "recorded_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            })
        return count_corrections()


def load_corrections() -> list[dict]:
    """Every correction recorded so far, oldest first."""
    if not CORRECTIONS_PATH.exists():
        return []
    with CORRECTIONS_PATH.open(newline="", encoding="utf-8") as f:
        return [row for row in csv.DictReader(f) if row.get("merchant_text")]


def count_corrections() -> int:
    return len(load_corrections())


def category_counts() -> dict[str, int]:
    counts: dict[str, int] = {}
    for row in load_corrections():
        counts[row["category"]] = counts.get(row["category"], 0) + 1
    return counts


# --- exact-match recall -------------------------------------------------------
#
# A merchant the user has explicitly categorised must never be re-guessed. If
# someone corrects "Licious Meat Delivery" to Groceries, the model returning
# Investment the next day makes the correction feel ignored -- and it is the
# single clearest signal the system has. Retraining alone does not achieve
# this: one weighted correction still loses to ~35 generated rows whose text
# happens to share character n-grams ("LIC" inside "Licious").
#
# The file is small and re-read only when it changes on disk.

_cache: dict[str, str] = {}
_cache_mtime: float = -1.0


def _normalise(text: str) -> str:
    return " ".join(text.strip().lower().split())


def _refresh_cache() -> None:
    global _cache, _cache_mtime
    if not CORRECTIONS_PATH.exists():
        _cache, _cache_mtime = {}, -1.0
        return
    mtime = CORRECTIONS_PATH.stat().st_mtime
    if mtime == _cache_mtime:
        return
    # Later rows win, so the most recent correction for a merchant is the one
    # that counts -- a user changing their mind is respected.
    table: dict[str, str] = {}
    for row in load_corrections():
        table[_normalise(row["merchant_text"])] = row["category"]
    _cache, _cache_mtime = table, mtime


def lookup(merchant_text: str) -> str | None:
    """The category the user last assigned to this exact merchant, if any."""
    _refresh_cache()
    return _cache.get(_normalise(merchant_text))
