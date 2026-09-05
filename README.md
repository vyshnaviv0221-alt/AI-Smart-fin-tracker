# AI Smart Finance Tracker

An offline-first, intelligent personal finance tracking ecosystem. The platform automatically captures bank and UPI transaction notifications on Android devices, parses transaction details, categorizes expenditures, flags statistical anomalies, forecasts future category budgets, and synchronizes data with an encrypted cloud backend.

---

## Architecture Overview

The system consists of an Android client, a FastAPI machine learning microservice, an offline-first SQLite/Room storage layer, and an optional Supabase cloud synchronization service.

```
[Bank / UPI Notification]
           |
           v
[ExpenseNotificationListener] (Android NotificationListenerService)
           |
           v
[ExpenseParser] (Multi-pattern regex engine for amount & merchant)
           |
           v
[ExpenseRepository]
     |-- 1. 60-second sliding-window duplicate suppression
     |-- 2. Immediate local insertion to Room DB (via CategoryKeywords fallback)
     |-- 3. Asynchronous ML enrichment via FastAPI:
     |        |-- POST /categorize -> High-confidence model classification
     |        `-- POST /anomaly    -> Category-aware deviation flagging
     `-- 4. Cloud synchronization to Supabase via REST (if authenticated)
           |
           v
[Reactive UI Layer] (Jetpack Compose observing unified Room Flow)
```

The application functions strictly **offline-first**:
- The on-device SQLite database (Room v3) serves as the primary source of truth.
- Local transactions are instantly saved and rendered with deterministic keyword categorization.
- Network calls to the machine learning server enrich existing records without blocking the user interface.
- If the machine learning server or cloud backend becomes unavailable, core tracking and local analytics remain fully operational.

---

## Summary of Accomplishments and Enhancements

### 1. User Interface and Interaction Design
- **Custom Design System**: Constructed a centralized token-based design system (`Tokens.kt`, `Theme.kt`, `Color.kt`, `Type.kt`) adhering to fluid interface standards with spring-based motion curves.
- **Accessibility and Reduced Motion**: Implemented `LocalReducedMotion` that reads Android system animation scale (`ANIMATOR_DURATION_SCALE`) to replace physics-based springs with immediate transitions when requested by accessibility settings.
- **Compose Typography Stability Fix**: Resolved a critical framework-level crash where `OutlinedTextField` attempted to interpolate (`TextStyle.lerp`) between `Em` and `Sp` dimensions during floating label animations, converting tracking units to uniform `Sp` values.
- **Hardware Dark Mode Handling**: Fixed contrast degradation on physical hardware where system dark mode conflicted with card surfaces, ensuring predictable contrast ratios.
- **Window Inset Normalization**: Corrected layout clipping against device status bars and display cutouts by centralizing `safeDrawingPadding` at the root surface.
- **Responsive Category Visualizations**: Replaced fixed circular distribution widgets with adaptive horizontal distribution bars capable of rendering any number of categories cleanly without label truncation.
- **Single Shared State Flow**: Centralized navigation and state management into a shared `ExpenseViewModel` across all screens, ensuring modifications in one view propagate instantaneously across Dashboard, Analytics, Transactions, Budgets, and Profile views.

### 2. Transaction Parsing and Local Intelligence
- **Notification Interception**: Configured `ExpenseNotificationListener` with granular allowlisting for payment and banking applications (SMS, Google Pay, PhonePe, Paytm, CRED, BHIM, and major Indian banking institutions).
- **Regex Extraction Pipeline**: Enhanced `ExpenseParser` with multi-tier regular expressions to extract transaction type (debit vs. credit), monetary value, currency, and merchant names.
- **Sliding-Window Deduplication**: Implemented a 60-second time-window check on merchant name and amount to eliminate duplicate records caused by redundant push notifications.
- **Local Keyword Classification**: Authored `CategoryKeywords.kt` providing deterministic, rule-based category resolution across 10 expense categories for offline operation.
- **Human-in-the-Loop Category Adjustment**: Added category modification directly from transaction cards, triggering dynamic re-aggregation across budget limits and spending summaries.
- **Contextual Recommendation Engine**: Built `InsightEngine.kt` to evaluate live database metrics, flagging budget exhaustion thresholds, category concentration risks, and recurring anomalies.

### 3. Security, Authentication, and Privacy
- **Android Network Security Configuration**: Added `network_security_config.xml` to restrict cleartext HTTP traffic strictly to local development loopbacks (`127.0.0.1` and `10.0.2.2`), completely removing application-wide `usesCleartextTraffic`.
- **Encrypted Credential Storage**: Implemented `SessionStore.kt` utilizing Android `EncryptedSharedPreferences` backed by hardware `MasterKey` encryption to secure authentication tokens, with graceful fallback.
- **Decoupled Cloud Backend**: Replaced heavy Firebase SDKs with direct, lightweight REST calls to Supabase (GoTrue authentication and PostgREST API) through existing Retrofit networking.
- **Database Row-Level Security (RLS)**: Authored `supabase/schema.sql` configuring strict PostgreSQL Row-Level Security policies ensuring authenticated users can access only their own transactions and budgets.
- **Secrets Segregation**: Configured build configurations to read endpoints and API keys from `local.properties` rather than hardcoding credentials in version control.
- **Sanitized Logging**: Configured HTTP logging interceptors at `BASIC` level to prevent logging sensitive authentication headers or payload tokens into system logcat.

### 4. Machine Learning and Data Pipelines
- **Category-Aware Anomaly Detection**: Refactored the anomaly detection pipeline to evaluate deviations relative to category-specific median and interquartile ranges (IQR). This prevents high baseline expenses (such as monthly rent) from triggering false positives while correctly isolating abnormal spikes within discretionary categories (such as food or shopping).
- **Path Resolution Module**: Added `model/training/paths.py` to dynamically locate datasets and artifact directories relative to the project root, allowing training scripts to run from any working directory.
- **Forecasting Validation and Benchmarks**: Updated `predict_expense.py` to compute out-of-fold cross-validated R^2 and root mean square error (RMSE), benchmarking predictions against category-mean baselines.
- **Evaluation Reporting**: Added automated generation of confusion matrices, precision/recall/F1 metrics, and serialized performance reports under `model/evaluation/`.
- **Artifact and Version Control Hygiene**: Untracked legacy pickled model binaries from git history, established strict `.gitignore` rules across artifact folders, and documented model reproducibility.

### 5. Backend Microservice (FastAPI)
- Exposes standard REST endpoints:
  - `POST /categorize`: Returns transaction category and confidence score.
  - `POST /anomaly`: Evaluates transaction amount and flags statistical outliers.
  - `POST /predict`: Predicts category spending for the subsequent calendar month.
  - `GET /health`: Reports model operational status.
- Implemented graceful fallback handlers inside `model_loader.py` to ensure server uptime even if individual model files are missing.
- Includes 17 automated endpoint tests verifying HTTP contracts and response payloads.

### 6. Build Engineering and Verification
- Modernized Android build toolchain:
  - Gradle 9.7.1
  - Android Gradle Plugin (AGP) 9.4.0
  - Kotlin 2.4.10
  - Kotlin Symbol Processing (KSP) 2.3.11 for Room code generation
  - Jetpack Compose Compiler Plugin (Kotlin 2.0+)
  - Android SDK Platform 37 / Target SDK 37
- Unit Test Coverage: 32 JVM unit tests covering notification parsing, keyword categorization, budget logic, and rule-based insights.
- Hardware Validation: Built and verified directly on physical hardware (Samsung Galaxy S23): clean build with zero warnings, fluid rendering, and reliable background notification interception.

---

## Directory Layout

```
AI-SMART-FINANCE-TRACKER/
|-- client/                         # Android application
|   |-- app/
|   |   |-- src/main/java/          # Kotlin source files
|   |   |   `-- com/example/aismartexpensetracker/
|   |   |       |-- cloud/          # Supabase REST client & session store
|   |   |       |-- network/        # Retrofit ML client & data models
|   |   |       |-- ui/             # Compose screens, components & design system
|   |   |       `-- ...             # Database entities, DAOs, parser & listener
|   |   |-- src/main/res/           # Android resources & security config
|   |   `-- src/test/java/          # 32 JVM unit tests
|   |-- build.gradle.kts            # Root client build script
|   `-- settings.gradle.kts         # Gradle project configuration
|
|-- server/                         # FastAPI microservice
|   |-- app/
|   |   |-- main.py                 # FastAPI application routes
|   |   |-- model_loader.py         # Artifact loaders and fallback logic
|   |   `-- schemas.py              # Pydantic request/response schemas
|   `-- test_server.py              # 17 automated endpoint tests
|
|-- model/                          # Machine learning pipelines
|   |-- training/                   # Training & preprocessing scripts
|   |-- evaluation/                 # Metrics, confusion matrices & reports
|   |-- artifacts/                  # Generated models (git-ignored)
|   `-- requirements.txt            # Python dependencies for ML
|
|-- database/                       # Datasets
|   |-- raw/                        # Source CSV transaction datasets
|   |-- processed/                  # Derived datasets
|   `-- upi_transactions/           # Labeled UPI transaction splits
|
`-- supabase/
    `-- schema.sql                  # PostgreSQL schema with Row-Level Security
```

---

## Technology Stack

| Domain | Technology | Purpose |
|---|---|---|
| Mobile Client | Kotlin 2.4, Jetpack Compose | Modern declarative UI and reactive state |
| Local Database | Room (SQLite) v3, KSP 2.3.11 | Offline-first persistence and observable queries |
| Networking | Retrofit 2, OkHttp 3, Gson | REST communication with ML server and Supabase |
| Security | AndroidX Security Crypto (1.1.0) | Hardware-backed EncryptedSharedPreferences |
| Cloud Database | Supabase (PostgreSQL 15) | Row-Level Security cloud data storage |
| Cloud Auth | Supabase GoTrue REST API | User authentication and session token issuance |
| Machine Learning | scikit-learn, joblib, pandas | TF-IDF categorization, Isolation Forest, Random Forest |
| Backend Server | FastAPI, Uvicorn, Pydantic | Asynchronous microservice delivering model inferences |
| Build Tooling | Gradle 9.7.1, AGP 9.4.0, JDK 25 | Android build automation and dependency resolution |

---

## Setup and Installation

### 1. Android Client Setup
1. Ensure Android Studio (Ladybug or newer) and JDK 17+ are installed.
2. Copy `client/local.properties.example` to `client/local.properties`.
3. Configure the parameters in `local.properties`:
   ```properties
   sdk.dir=/path/to/android/sdk
   ml.serverUrl=http://127.0.0.1:8000/
   supabase.url=https://your-project.supabase.co
   supabase.anonKey=your-supabase-anon-key
   ```
4. Build the project:
   ```bash
   cd client
   ./gradlew assembleDebug
   ```
5. If testing on physical hardware with a local machine learning server, forward the local server port via adb:
   ```bash
   adb reverse tcp:8000 tcp:8000
   ```

### 2. Backend ML Server Setup
1. Navigate to the `server/` directory:
   ```bash
   cd server
   ```
2. Create and activate a Python virtual environment:
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: .\venv\Scripts\activate
   ```
3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
4. Start the development server:
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```
5. Verify health:
   ```bash
   curl http://localhost:8000/health
   ```

### 3. Supabase Cloud Configuration
1. Create a new project in the Supabase Dashboard.
2. Open the SQL Editor and execute the statements from `supabase/schema.sql`.
3. Retrieve your project URL and public anonymous key from the API Settings tab.
4. Supply these values into `client/local.properties`.

---

## Verification and Testing

### Client Unit Tests
Run the 32 JVM automated tests covering notification parsing, keyword categorization, and budget calculation:
```bash
cd client
./gradlew test
```

### Server Endpoint Tests
Run the API endpoint test suite against the FastAPI service:
```bash
cd server
pytest test_server.py
```
