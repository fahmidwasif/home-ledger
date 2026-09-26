# A&F Home — Anika & Fahmid's private household ledger

An Android app for the realme GT Master that reads shop receipts, keeps a home inventory (with the room each
item lives in), predicts the grocery list, and analyses spending: all **on the phone, with no internet access**.

## Install on the phone

1. Copy `release/AF-Home.apk` to the phone (USB, or upload it to your own Google Drive and open it there).
2. Open it. Android will ask to allow installing from that app (Files / Drive): allow it once.
3. Open **A&F Home**, and allow notifications (used for WoF / rego / insurance and expiry reminders).

## First-time setup (5 minutes)

1. **Settings (hat icon) → Gringotts vault**
   - *Set PIN*: at least 6 digits. **Write it down somewhere safe.** Without it the backup can't be opened.
   - *Choose where to save*: in the picker, tap ☰ and choose **Google Drive**, then Save.
   From then on the app backs up automatically shortly after changes (and every 12 hours). The Drive app does the uploading.
2. **Settings → Household**: who mostly uses this phone, and your lunch costs (defaults: $20 bought vs $5 packed).
3. **Home → Car**: add the car with WoF, rego and insurance dates.
4. *(Optional)* **Settings → On-device AI**: install a Gemma model (see below).

## Moving to a new phone

Install the APK → Settings → **Restore…** → pick the backup file in Google Drive → enter the PIN.
Everything comes back, and backups continue into the same file.

## The on-device AI (optional)

Gemini Nano isn't available on the GT Master, so the app runs Google's open **Gemma** models with LiteRT-LM.
Download one `.litertlm` file from huggingface.co/litert-community (accept the Gemma licence first):

| Model | File | Size | Notes |
|---|---|---|---|
| Gemma 4 E2B | `gemma-4-E2B-it.litertlm` (repo `gemma-4-E2B-it-litert-lm`) | 2.6 GB | Smarter; slower; ~1.7 GB RAM while answering |
| Gemma 3 1B | `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` (repo `Gemma3-1B-IT`) | 584 MB | Faster, simpler answers |

Then Settings → *Install model file…* and delete the downloaded copy. Leave **Use GPU** off unless you want to experiment.

Without a model everything else still works: receipt reading, stock, shopping list, insights, and the Owl
answering "where is…/do we have…" questions from the stock list.

## What's inside

| Area | What it does |
|---|---|
| Scan ("Accio receipt!") | ML Kit OCR on the phone → NZ receipt parser (Woolworths, PAK'nSAVE, New World, fuel, Bangladeshi grocers…). The photo is deleted straight after reading. Asks when unsure: who bought it, which account paid (Fahmid ANZ, Anika ANZ, Anika ASB, Joint ANZ, Joint ASB, Cash), missing date/total, totals that don't add up, fuel litres. Long receipts: add a 2nd photo. |
| Stock | Items auto-added from grocery/household receipts, grouped by room (Pantry, Fridge, Freezer, Bathroom…) with an exact spot. **Use** records what for: home cooking, **packed lunch for work**, wasted… |
| List | Used-up and running-low items, plus "usually due" predictions from how often you buy things; shows the cheapest shop you've paid. |
| Insights | 12-month trend, spending by type and category (groceries, bills, subscriptions, transport, wellbeing, travel…), Anika vs Fahmid, accounts, shops, Bangladeshi & South Asian groceries, packed vs bought lunch, gifts (by person and occasion), Zakat/donations/family support, car costs and a yearly estimate, weekday pattern, top-up shops, price rises, food waste, budgets. |
| Quick spend | "Spent without a receipt?" shortcuts: AT HOP top-up, coffee, bought lunch, gadget, subscription, Uber, parking, bills. |
| Subscriptions | Recurring payments (streaming, phone, gym, insurance, rent…) recorded automatically on each due date, with a reminder 3 days before. |
| Starting stock | Add what's already at home (atta, rice, oil, dal…) at $0, so stock and shopping lists are right from day one. |
| Owl | Chat with the on-device model; it is given only the household data relevant to each question. |
| Car | Honda Civic preset; WoF / rego / insurance reminders; fuel log (auto from fuel receipts), L/100km; fuel-gauge bars → litres left, range and cost to fill. |

Auckland reference figures used (Sept 2026): couple grocery spend $160–220/week; petrol-car rego $181.45/yr;
WoF ≈ $76–91; diesel/EV RUC $76 per 1,000 km; no Auckland regional fuel tax since July 2024.

## Privacy

The app has **no INTERNET permission**; Android itself blocks any network access. The only thing that leaves
the phone is the AES-256-GCM-encrypted backup file, which you place in your own Google Drive.

## Building from source

Requires JDK 17+ and the Android SDK (platform 36).

```bash
./gradlew testDebugUnitTest assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.
