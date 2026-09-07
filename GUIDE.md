# Running and Demonstrating the App

A practical guide: how to get it running, what to show, and what to say when
something is asked about it. The README covers architecture; this covers doing.

---

## 1. First run on a new machine

You need **Python 3.11+**, **Android Studio** (for the SDK and JDK), and a phone
with USB debugging on.

1. Plug the phone in. Accept the *Allow USB debugging?* prompt on the phone.
2. Double-click **`start-server.bat`**.

   First run takes a few minutes: it creates `server/.venv`, installs
   dependencies, trains the three models, sets up the USB bridge, and starts the
   server. Later runs take a few seconds.

   Leave the window open. The server runs inside it.

3. Build and install the app:

   ```bash
   cd client
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. On the phone: **Settings → Notifications → Notification access →
   AI Smart Expense Tracker → On.**

   Without this, nothing is captured automatically. The `+` button still works.

To stop everything: close the server window, or run **`stop-server.bat`**.

---

## 2. Checking it actually works

Run these before a demo. Each one either passes or tells you exactly what is
wrong.

```bash
# Is the server up and are the models loaded?
curl http://localhost:8081/
# expect: "models_ready": true

# Does the model classify?
curl -X POST http://localhost:8081/categorize \
  -H "Content-Type: application/json" \
  -d "{\"merchant_text\":\"Swiggy Order\",\"amount\":600}"
# expect: {"category":"Food","confidence":0.77...,"source":"model"}

# Is the phone bridged to the server?
adb reverse --list
# expect: UsbFfs tcp:8081 tcp:8081
```

If the last one is empty, the bridge is not up. Re-run `start-server.bat` with
the phone connected.

---

## 3. Does the phone need to stay plugged in?

**With the current USB setup: yes.** Cable connected, USB debugging on, server
running.

The reason is that `server.baseUrl` is `http://127.0.0.1:8081/`, and
`127.0.0.1` *on the phone* means the phone. `adb reverse` is the tunnel that
makes the phone's port 8081 come out on the laptop. Unplug it and the tunnel
dies.

**Unplugged, the app still works** — it just falls back to keyword
categorisation, with no anomaly flags and no predictions.

**If the server is hosted (Render), nothing is needed.** No cable, no laptop,
no debugging. That is the reason to deploy it.

---

## 4. The demo

A sequence that shows each part doing real work, in an order that builds.

### 4.1 Automatic capture

Make a small real UPI payment, or have someone send you one.

The notification arrives → `ExpenseNotificationListener` filters it by package
→ `ExpenseParser` extracts amount and merchant → it appears on the dashboard
with a category, and the total updates.

*If no notification arrives during the demo,* use the `+` button. Same pipeline,
same code path, minus the parser. Do not present it as an automatic capture.

### 4.2 The model doing something the keyword list cannot

Add an expense with a **deliberate typo**: `swiggu`, amount 600.

- The keyword table has no entry for `swiggu` → `Uncategorized`
- The server returns `Food` at ~0.52 confidence
- The app takes the server's answer

(Spelled correctly, `Swiggy Order` scores ~0.77. The gap between the two is the
cost of the typo, and the model still gets it right.)

This is the single clearest demonstration that the ML is load-bearing. It works
because the categoriser uses character n-grams, not whole words.

Visible in logs if asked:

```
D/ExpenseRepository: Saved 'swiggu' locally as Uncategorized
<-- 200 {"category":"Food","confidence":0.518,"source":"model"}
D/ExpenseRepository: Server refined -> Food
```

### 4.3 The confidence threshold rejecting a bad guess

Add an expense named `food`, amount 300.

The server returns `Groceries` at ~0.15 confidence. That is below the 0.30
threshold, so the app **keeps the on-device answer**. The model is not trusted
blindly.

### 4.4 Category-aware anomaly detection

Two transactions of the same size, judged differently:

| Merchant | Amount | Result |
|---|---|---|
| `House Rent NEFT` | ₹15,000 | normal |
| `Swiggy Order` | ₹15,000 | **UNUSUAL** |

₹15,000 is normal for rent and absurd for a food delivery. The detector compares
each amount against its own category's distribution, not against all spending.

### 4.5 Offline resilience — worth showing deliberately

Close the server window. Add another expense.

It still saves, still categorises (by keyword), still appears, still updates the
totals. The network is an enhancement, not a dependency.

Restart the server with `start-server.bat` before continuing.

### 4.6 Human-in-the-loop correction

Tap a transaction and change its category.

- It persists locally and re-aggregates every screen immediately
- It is sent to the server as a verified training label
- **Ask for that same merchant again — it now returns your category with
  confidence 1.0 and `"source": "user_correction"`**

This is the research contribution. It is worth spending time on.

---

## 5. Questions you should expect

**"Where is the model? Is it inside the app?"**
No. Three `.joblib` files on the server, loaded by Python at startup. The app
reaches them over REST. The APK contains no model — only a keyword table used as
a fallback. The plan permitted either on-device inference or a prediction API;
this project uses the API.

**"What happens if the server is down?"**
Demonstrate §4.5. Everything works except the model category, anomaly flag, and
predictions.

**"How much of the training data is real?"**
Ask the server — `GET /` reports it: total rows, real rows, synthetic rows, the
real percentage, and how many user corrections have been recorded. Real rows and
corrections are weighted far above synthetic ones. Synthetic rows are only
generated for categories that fall short of the minimum.

**"Why RandomForest and not an LSTM?"**
There are roughly 18 monthly aggregate points. That cannot support an LSTM. It
is documented as a data-volume limitation rather than presented as a choice.

**"Why is there no login?"**
Deliberate. The data never leaves the phone, so there is nothing to authenticate
against and no server holding anyone's transaction history. Cloud sync existed
and was removed: it was upload-only, with no way to restore, which meant it
could not answer the question it existed to answer.

**"What are the weaknesses?"**
Answer honestly — the README's *Known Limitations* section is written for this.
No backup or restore, the parser misses some bank formats, the daily forecaster
is too weak to surface, and on-device inference is not implemented. A stated
limitation lands better than a claim the code does not support.

---

## 6. When something breaks

| Symptom | Cause | Fix |
|---|---|---|
| `models_ready: false` | Models not trained | `cd server && python train_server_models.py` |
| App shows keyword categories only | Server unreachable | Check `adb reverse --list` and that the server window is open |
| `adb reverse` fails | Phone not authorised | Unplug, replug, accept the prompt, re-run `start-server.bat` |
| `Address already in use` | Old server still running | `stop-server.bat` |
| Nothing captured automatically | Notification access off | Settings → Notification access |
| Build fails on a fresh clone | Wrong Python | Needs 3.11+; `pip install -r server/requirements.txt` |
| First request after idle fails (hosted) | Cold start | Open the URL once to wake it before demoing |

---

## 7. Before you present

- [ ] `start-server.bat` running, `models_ready: true`
- [ ] `adb reverse --list` shows the bridge
- [ ] Notification access granted
- [ ] A few transactions already in the app, so no screen is empty
- [ ] Phone on Do Not Disturb — but leave payment notifications through
- [ ] If hosted: open the URL once to wake the service
- [ ] Know your fallback: if the network fails, plug in and use
      `start-server.bat`; if no notification arrives, use the `+` button
