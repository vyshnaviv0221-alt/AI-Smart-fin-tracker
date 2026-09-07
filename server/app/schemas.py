from pydantic import BaseModel


class TransactionRequest(BaseModel):
    merchant_text: str
    amount: float


class CategoryResponse(BaseModel):
    category: str
    confidence: float
    # "user_correction" when this merchant was previously corrected by hand,
    # "model" when the classifier decided. Lets the client and the logs show
    # where an answer came from.
    source: str = "model"


class AnomalyResponse(BaseModel):
    amount: float
    status: str  # "normal" | "UNUSUAL"
    category: str  # category the amount was judged against
    deviation: float  # robust z-score of log(amount) within that category


class PredictionRequest(BaseModel):
    month: int  # 1-12
    category: str


class PredictionResponse(BaseModel):
    category: str
    predicted_amount: float


class DailyForecastRequest(BaseModel):
    """
    Recent daily spend totals, oldest first.

    The model's two strongest features are yesterday's total and the 7-day
    rolling mean, so the client sends what it already has in Room rather than
    the server keeping any per-user history.
    """
    recent_daily_totals: list[float]
    days_ahead: int = 7


class DailyForecastPoint(BaseModel):
    date: str
    predicted_amount: float


class DailyForecastResponse(BaseModel):
    total: float
    days: list[DailyForecastPoint]
    # Straight from the model's evaluation, so the client can be honest about
    # how much weight to give this number.
    r2: float
    note: str


class CorrectionRequest(BaseModel):
    """A category the user fixed by hand in the app."""
    merchant_text: str
    category: str
    amount: float = 0.0


class CorrectionResponse(BaseModel):
    recorded: int          # corrections held in total
    for_this_category: int # how many for the category just submitted
    message: str
