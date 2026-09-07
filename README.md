# AI Smart Finance Tracker

An offline-first personal finance tracker for Android. It captures bank and UPI
transaction notifications automatically, parses the amount and merchant,
categorises the spend with a trained model, flags unusual amounts, and forecasts
next month's spending per category.

**Everything stays on the phone.** There is no account, no login and no cloud
database. The only network call the app makes is to a machine-learning server
that returns a category and a confidence score — and when that server is
unreachable, the app falls back to on-device keyword classification and keeps
working.

---

## Architecture Overview

```
[Bank / UPI Notification]
           |
           v
[ExpenseNotificationListener]        NotificationListenerService, package allowlist
           |
           v
[ExpenseParser]                      multi-pattern regex: amount, merchant, debit/credit
           |
           v
[ExpenseRepository]
     |-- 1. 60-second sliding-window duplicate suppression
     |-- 2. Immediate insert into Room, categorised by CategoryKeywords
     |-- 3. Best-effort ML enrichment over REST:
     |        |-- POST /categorize  -> model category + confidence
     |        `-- POST /anomaly     -> category-aware deviation flag
           |
           v
[Jetpack Compose UI]                 every screen observes one Room Flow
```

Step 2 always succeeds. Step 3 is an enhancement, never a dependency:

| | ML server reachable | ML server down |
|---|---|---|
| Save + view transactions | yes | yes |
| Notification capture | yes | yes |
| Dashboard, budgets, charts | yes | yes |
| Category source | trained model | keyword table |
| Anomaly flag | yes | no |
| Predictions screen | yes | no, shows an error |

---

## Setup

### Quick start (Windows)

Double-click **`start-server.bat`** in the project root. On a machine that has
never seen this project it creates the virtual environment, installs
dependencies, trains the models and bridges a connected phone over USB. Later
runs skip whatever is already done. Leave the window open — the server runs in
it.

**`stop-server.bat`** stops it and removes the USB bridge.

Then build and install the app:

```bash
cd client
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Finally, grant **notification access** to the app in Android Settings. Nothing
is captured automatically without it.

### Ports

The server listens on **8081**, and `adb reverse` maps the phone's
`127.0.0.1:8081` to the same port on the host — one number on both sides. Port
8000 is deliberately avoided: it is heavily used on Android, and a busy device
port makes `adb reverse` fail.

### Client configuration

Copy `client/local.properties.example` to `client/local.properties`:

```properties
sdk.dir=C:/Users/you/AppData/Local/Android/Sdk
server.baseUrl=http://127.0.0.1:8081/
```

`server.baseUrl` is the only setting that matters. It is baked into
`BuildConfig` at build time, so changing it needs a rebuild:

| Setup | Value | Cable required |
|---|---|---|
| USB bridge | `http://127.0.0.1:8081/` | yes |
| Emulator | `http://10.0.2.2:8081/` | n/a |
| Same Wi-Fi | `http://<host-LAN-IP>:8081/` | no (needs a cleartext exception) |
| Hosted | `https://your-service.onrender.com/` | no |

Cleartext HTTP is permitted only for `127.0.0.1`, `localhost` and `10.0.2.2`
(see `res/xml/network_security_config.xml`). A LAN address needs adding there
explicitly; HTTPS hosts work with no change.

### Server setup by hand (macOS / Linux)

`start-server.bat` does all of this on Windows.

```bash
cd server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt      # Python 3.11+
python train_server_models.py        # models are build output, not committed
uvicorn app.main:app --host 0.0.0.0 --port 8081
adb reverse tcp:8081 tcp:8081        # for a physical phone
```

Health is served at the **root**, not `/health`:

```bash
curl http://localhost:8081/
```

### Hosting the ML server (optional)

`render.yaml` deploys the server to Render: dashboard → New → Blueprint → pick
this repository. The build trains the models, since the `.joblib` files are
git-ignored build output. Then set `server.baseUrl` to the service URL and
rebuild the app.

Hosting removes the laptop from the demo entirely — no USB, no `adb reverse`,
no Wi-Fi assumptions — and gives HTTPS instead of cleartext.

Two properties of the free plan, both documented rather than hidden:

- **It sleeps after ~15 minutes idle** and cold-starts on the next request. The
  app allows a 60-second read timeout to cover this. Open the URL once before a
  demo so the first real request is not the one that wakes it.
- **The disk is ephemeral.** `server/data/corrections.csv` is wiped on every
  restart and redeploy. Corrections still take effect immediately in a running
  instance, but they stop accumulating into future retraining.

Keep `start-server.bat` as the fallback: it needs no network at all.

---

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Health, model status, and training-data provenance |
| POST | `/categorize` | Category + confidence + source (`model` or `user_correction`) |
| POST | `/anomaly` | Flags an amount as unusual **for its own category** |
| POST | `/predict` | Next-month spend for a category |
| POST | `/feedback/correction` | Records a user-verified label as training data |
| POST | `/forecast/daily` | Daily spend forecast (not wired into the UI — see below) |

Interactive docs at `http://127.0.0.1:8081/docs`.

---

## Machine Learning

Three models, trained by `server/train_server_models.py` into `server/models/`:

| Model | Algorithm | Purpose |
|---|---|---|
| `categorizer.joblib` | TF-IDF (`char_wb`, 3–5 grams) + linear classifier | Merchant text to category |
| `anomaly.joblib` | IsolationForest over MAD-based per-category deviation | Unusual amount detection |
| `forecaster.joblib` | RandomForest | Next-month category spend |

Character n-grams are what let the model handle typos and unseen merchants: a
transaction typed as `swiggu` is still classified as Food.

### Decisions made by measurement, not assumption

- **Amount is not a categoriser feature.** Including it let the amount pick the
  category — "Swiggy Order" at ₹15,000 came back as Rent — which made
  `/anomaly` circular, since an amount is always normal for the category its own
  size implies. Pinned by a regression test.
- **Anomaly detection is one-sided.** Two-sided flagging was 85% transactions
  that were unusually *cheap*, which is not worth alerting on.
- **Corrections outrank the model.** Retraining alone did not change the
  prediction — one correction loses to ~35 generated rows sharing character
  n-grams — so a corrected merchant is recalled by exact match instead.
- **The daily forecaster is deliberately not in the UI.** It scores a
  correlation of +0.281 and is non-monotonic: it forecasts more spending after a
  ₹0 day than after a ₹1,000 day. The endpoint exists and reports its own R² so
  no caller can present it as more than it is.
- **The household-expense dataset is not used for training.** It scores 91.2% on
  its own diary-style text but 0/12 on app merchant strings ("House Rent NEFT"
  became Tourism). The mapping is kept in `model/training/category_mapping.py`
  for analysis only.

### Training data provenance

`GET /` reports where the training data came from, so "is this trained on real
data?" is checkable rather than claimed:

```json
{"training_data": {"total_rows": 400, "real_rows": 55, "synthetic_rows": 345,
                   "real_share_pct": 13.8, "user_corrections": 1}}
```

Real rows and user corrections are weighted far above synthetic top-up rows
during training (`ORIGIN_WEIGHT`), and synthetic rows are only generated for
categories that fall short of `MIN_REAL_PER_CATEGORY`.

---

## Directory Layout

```
AI-SMART-FINANCE-TRACKER/
|-- start-server.bat                # one-click: venv, deps, models, USB bridge, run
|-- stop-server.bat                 # stop the server, remove the bridge
|-- render.yaml                     # Render deployment blueprint
|
|-- client/                         # Android application
|   `-- app/src/
|       |-- main/java/com/example/aismartexpensetracker/
|       |   |-- network/            # Retrofit ML client & data models
|       |   |-- ui/                 # Compose screens, components, design system
|       |   `-- ...                 # Room entities, DAOs, parser, listener
|       |-- main/res/               # resources, icons, network security config
|       `-- test/java/              # 46 JVM unit tests
|
|-- server/                         # FastAPI microservice
|   |-- app/
|   |   |-- main.py                 # routes
|   |   |-- model_loader.py         # artifact loading + prediction
|   |   |-- feedback_store.py       # user corrections as training data
|   |   `-- schemas.py              # Pydantic request/response models
|   |-- tests/test_api.py           # 30 endpoint tests
|   |-- train_server_models.py      # builds server/models/
|   `-- requirements.txt
|
|-- model/                          # research / offline ML work
|   |-- training/                   # training & preprocessing scripts
|   |-- evaluation/                 # metrics, confusion matrices, reports
|   `-- artifacts/                  # generated models (git-ignored)
|
`-- database/                       # datasets (raw, processed, upi_transactions)
```

---

## Technology Stack

| Domain | Technology | Purpose |
|---|---|---|
| Mobile Client | Kotlin 2.4, Jetpack Compose | Declarative UI, reactive state |
| Local Database | Room (SQLite) v4, KSP 2.3.11 | Offline-first persistence, observable queries |
| Networking | Retrofit 2, OkHttp 3, Gson | REST communication with the ML server |
| Machine Learning | scikit-learn 1.9, joblib, pandas | TF-IDF categorisation, IsolationForest, RandomForest |
| Backend | FastAPI, Uvicorn, Pydantic | Async microservice serving model inference |
| Build | Gradle 9.7.1, AGP 9.4.0, JDK 25 | Android build automation |

---

## Verification and Testing

```bash
# Client -- 46 tests
cd client && ./gradlew test

# Server -- 30 tests (train the models first)
cd server && python -m pytest -q
```

The server suite pins the three measured defects above, so they cannot silently
return: categorisation must not follow the amount, anomaly flagging must be
one-sided, and a corrected merchant must never be re-guessed.

### End-to-end on a phone

1. `start-server.bat`, then install the APK and grant notification access.
2. Make a real UPI payment, or add one with the `+` button.
3. Confirm the transaction appears with a category and the total updates.
4. **Stop the server and repeat.** The transaction must still appear,
   categorised on-device. This is the offline-first guarantee, and it is worth
   demonstrating deliberately.
5. Tap a transaction and change its category. It persists, re-aggregates every
   screen, and is sent to the server as training data.

---

## Known Limitations

- **No cloud backup or restore.** Data lives only on the device. Uninstalling
  the app loses it. This is a deliberate privacy trade-off, not an oversight.
- **Real bank notification formats vary widely.** The parser will miss some.
  The human-in-the-loop correction is the designed answer to residual misses.
- **Cleartext HTTP on the local loopback** for USB/emulator development. A
  hosted server uses HTTPS.
- **Release builds are not minified.** Retrofit and Gson resolve models
  reflectively, and the R8 keep rules have not been written or tested.
- **On-device inference is not implemented.** Models are served over REST. The
  original plan permitted either, and REST was chosen.
