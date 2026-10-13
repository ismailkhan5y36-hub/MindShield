# 🛡️ MindShield - Digital Detox & Focus Guard

**MindShield** ek 100% offline Android productivity application hai jo reels aur distracting content se bachane ke liye design ki gayi hai.

---

## ⚡ Main Rules (Koshish & Discipline)

1. **🚫 Adult / Galat Videos & Keywords (Zero Tolerance):**
   - Kisi bhi browser (Chrome, Phoenix Browser, Firefox, etc.) mein galat content aate hi **0.1 second mein phone Home Screen** par chala jayega aur block screen show hogi.
2. **⏱️ Reels & Shorts (Strict 60 Minutes Daily Limit):**
   - Target Platforms: **Instagram Reels**, **YouTube Shorts**, **TikTok**, **Facebook Reels**, **Snapchat Spotlight**.
   - Daily Quota: **Total 1 Ghanta (60 minutes)**.
   - 60 minutes pure hote hi Reels auto-close ho jayengi aur agle din raat 12:00 AM tak block rahengi.
3. **🔋 100% Offline & Private:**
   - Phone ke bahar koi data nahi jata. Koi internet zaroorat nahi.

---

## 🚀 APK Kaise Download Karein (GitHub Actions se)?

### Step 1: GitHub Par Repo Banayein
1. Apne browser mein [github.com/new](https://github.com/new) par jayein.
2. Repository ka naam rakhein: `MindShield`
3. Repository ko **Public** ya **Private** select karke "Create repository" par click karein.

### Step 2: Code Push Karein
Apne computer ke terminal / PowerShell mein ye commands run karein:
```bash
cd "F:\1 New Project\MindShield"
git init
git add .
git commit -m "Initial commit for MindShield Android App"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/MindShield.git
git push -u origin main
```
*(Yahan `YOUR_USERNAME` ki jagah apna GitHub username likhein).*

### Step 3: APK Download Karein
1. GitHub par apni repository open karein aur **"Actions"** tab par click karein.
2. Wahan **"Build MindShield APK"** workflow chal raha hoga (~2 minutes lagenge).
3. Complete hone ke baad us workflow par click karein.
4. Neeche **Artifacts** section mein **`MindShield-App-Debug`** zip file par click karke download karein.
5. Zip extract karein, usme aapko **`app-debug.apk`** mil jayegi!

---

## 📱 Tecno Spark 30 Pro (HiOS) Setup Instructions

Apne Tecno Spark 30 Pro par APK install karne ke baad:
1. **Accessibility Service:** MindShield open karein, "1. Turn On Accessibility Service" par click karein aur `MindShield` ko ON karein.
2. **Display Over Apps:** "2. Allow Display Over Other Apps" permission grant karein.
3. **Battery Saver (HiOS):** "3. Tecno/HiOS Battery Exemption" par click karke Battery Optimization "Don't Optimize" ya "Unrestricted" select karein, taake Tecno ka Phone Master background service ko kill na kare.
