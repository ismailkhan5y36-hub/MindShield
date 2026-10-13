# 🛡️ MindShield — Digital Detox, Focus Guard & Productivity Suite

**MindShield** is a comprehensive, 100% offline Android discipline and personal productivity application. It is engineered to help individuals reclaim their attention span, eliminate short-form video addiction, block explicit distractions, and build lasting daily habits.

---

## 🌟 Key Features

### ⏱️ 1. Strict Short-Form Video Quota (60 Minutes/Day)
* **Monitored Platforms:** YouTube Shorts, Instagram Reels, TikTok, Facebook Reels, and Snapchat Spotlight.
* **Daily Quota:** Strict **60-minute combined daily limit** tracked in real-time.
* **Enforcement:** Once the 60-minute allocation elapses, all short-form players are automatically closed until midnight (12:00 AM reset).

### 🧠 2. Smart On-Device Content Filter & Heuristic Engine
* **Universal Browser Shield:** Monitors URLs, search queries, and web views across Chrome, Phoenix, Samsung Internet, Opera, Firefox, Brave, and UC Browser.
* **1000+ Domain Signatures:** Deep database of explicit domains, pornographic TLDs (`.xxx`, `.adult`, `.porn`), and high-risk keywords.
* **Heuristic Contextual Classifier:** Identifies unknown suspicious websites on-device and applies instant protection.
* **In-App Streaming Guard:** Specifically blocks explicit categories within third-party media apps (e.g., MovieBox *Midnight 18+* and *NovelHub* sections).
* **VidMate & In-App WebViews:** Monitors integrated downloaders and in-app browsers with zero tolerance.
* **Silent Home Kickout:** Discretely deflects to the home screen without embarrassing alarm popups if someone is nearby.

### 🔒 3. 2-Hour Cooling-Off Anti-Cheat Delay Lock
* **Impulse Prevention:** Stops self-sabotage when dopamine cravings strike.
* **Protected Operations:**
  - Android Package Installer (`Uninstall` action)
  - HiOS / Transsion Freezer application
  - Android Settings (`Force stop`, `Clear storage`, `Disable Accessibility`)
* **Behavior:** Intercepts deletion attempts and initiates a mandatory **120-minute cooling-off countdown**. A 10-minute maintenance window unlocks only after the 2-hour timer completes.

### ⏸️ 4. 5-Second Mindful Pause (Dopamine Friction)
* **Muscle-Memory Breaker:** Displays a 5-second reflection screen upon launching trigger apps (Instagram, YouTube, TikTok).
* Prompts conscious awareness: *"Pause and reflect: Was this opened for an intentional purpose, or out of subconscious habit?"*

### 🚫 5. Incognito & Play Store Anti-Bypass Guard
* **Private Browsing Shield:** Immediately terminates Incognito / InPrivate tabs across all supported browsers.
* **Play Store Guard:** Prevents searching for VPNs, proxies, and clone apps (e.g., TikTok Lite, SnackVideo) designed to circumvent digital discipline.

### 📞 6. Emergency Whitelist (Always Safe)
* Essential tools and communication apps are **permanently exempted** from interruption:
  - Phone Calls, Contacts, SMS & Messaging
  - Banking & Financial Services (EasyPaisa, JazzCash, NayaPay, SadaPay, Banking apps)
  - Clock, Alarms, Calculator, Camera, and Navigation.

### 📊 7. Daily Screen Time & Top Apps Monitor
* Visual breakdown of total daily active screen time.
* Real-time list of top apps used with usage duration and percentage bars.
* **100% Private:** Analyzed completely on-device via Android `UsageStatsManager`.

### 📈 8. "Time Saved" Life Counter & Victory Scorecard
* Calculates cumulative hours reclaimed from mindless scrolling.
* **Daily Victory Scorecard:** Scheduled local notification at 9:30 PM summarizing daily focus percentage and streak consistency.

### ✅ 9. Daily Goals, Habits & Pomodoro Focus Timer
* **Habit Tracker:** Customizable daily checklist (Study, Exercise, Reading) with live progress percentages.
* **Deep Work Pomodoro:** 25-minute focus session / 5-minute break cycles with daily session counters.
* **SOS Mind Reset:** Guided 60-second 4-7-8 breathing circle (Inhale 4s, Hold 7s, Exhale 8s) to neutralize sudden cravings.

---

## 🏗️ Technical Architecture

* **Language:** Kotlin
* **Minimum SDK:** Android 8.0 (API Level 26)
* **Target SDK:** Android 14 (API Level 34)
* **Key Components:**
  - `ShieldAccessibilityService`: Real-time window inspection, short-video tracking, and app deflection.
  - `SmartFilterEngine`: Offline heuristic classifier for explicit signatures and streaming sections.
  - `AntiCheatManager`: 2-Hour delay countdown and maintenance window scheduler.
  - `UsageStatsHelper`: Aggregation of foreground application durations.
  - `ProductivityManager`: Local persistence for goals, streaks, and focus metrics.
  - `VictoryScorecardReceiver`: Scheduled `AlarmManager` broadcast for daily 9:30 PM summaries.
* **Privacy:** **Zero network permissions required for core functions.** Operates completely offline.

---

## 📱 Installation & Setup Guide

### 1. Requirements
* Compatible with Android 8.0 through Android 14+ (Tested on Tecno Spark 30 Pro / HiOS).

### 2. Permissions Required
1. **Accessibility Service:** Required for real-time app switching, short-video countdown, and silent deflection.
   > **Note for Android 13 & 14:** If you see a *"Restricted setting"* dialog when enabling Accessibility:
   > 1. Long-press the **MindShield** app icon on your home screen and select **App Info (ⓘ)**.
   > 2. Tap the **three dots (⋮)** in the top-right corner.
   > 3. Select **"Allow restricted settings"** and authenticate with your phone PIN/fingerprint.
   > 4. Return to **Settings > Accessibility > MindShield** and turn it **ON**.
2. **Display Over Other Apps:** Enables block screens and the 5-second mindful pause overlay.
3. **Usage Access:** Enables daily screen time calculations and top app ranking.
4. **Battery Optimization Exemption:** Recommended on aggressive OEM skins (Tecno HiOS, Infinix, Xiaomi) to ensure the service runs continuously in the background.

---

## 🔒 License & Ethics

MindShield is developed with the sole intent of fostering mental clarity, digital well-being, and personal self-mastery. All filtering logic is strictly executed on-device with utmost respect for user privacy and dignity.
