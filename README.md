# Rayo & Radar Karoo Cockpit 🚲⚡

[![Hammerhead Karoo](https://img.shields.io/badge/Device-Hammerhead%20Karoo%203%20(2024)-brightgreen.svg)](#)
[![SDK](https://img.shields.io/badge/SDK-Hammerhead%20karoo--ext%201.1.8-blue.svg)](https://github.com/hammerheadnav/karoo-ext)
[![License: MIT](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)
[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Donate-yellow.svg)](https://buymeacoffee.com/02eOWKzm5W)

> **All-in-one Smart Light & Radar Cockpit Extension for Hammerhead Karoo.**  
> *Seamlessly control both your **Hauteworks Rayo (Bluetooth)** and your **Lezyne / ANT+ Radar tail lights** directly from your handlebar computer during rides!*

---

## ✨ Features

### 📱 Dedicated In-Ride Widgets (One Data Field per Light)
* **Rayo Controller Widget (`rayo_control`):**
  * **One-Tap Power Toggle:** Turn the Rayo light ON or OFF with a single tap.
  * **Profile Switcher:** Tap to cycle through all 4 mode profiles (*Pulse*, *Blink*, *Wave*, *Strobe*) on the fly.
  * **Live Battery Indicator:** Real-time battery percentage readout (e.g. `85%`).
* **Lezyne / ANT+ Radar Controller Widget (`ant_light_control`):**
  * **Mode Switcher Button:** Tap to cycle through modes in German/English (*Tagfahrlicht*, *Dauerlicht Hell*, *Dauerlicht Mittel*, *Dauerlicht Spar*, *Blinken Schnell*, *Blinken Langsam*, *Aus*).
  * **Multi-Status Indicator Badge:** Real-time status display:
    * 🟢 **`RADAR FREI`**: Road is clear behind you.
    * 🟡 **`FAHRZEUG!`**: Approaching vehicle detected (Threat Level 1).
    * 🔴 **`FAHRZEUG (SCHNELL)!`**: High-speed vehicle or platoon detected (Threat Level 2).
    * 🔴 **`BREMSE!`**: G-Force deceleration active.
    * 🔵 **`TEMPO-BOOST`**: Speed-adaptive high beam active.
  * **Quick Power Toggle:** One-tap button to instantly toggle power (`LICHT AN` / `LICHT AUS`).
* **Numeric Battery Data Fields:** Dedicated live battery percentage fields for both lights (`rayo_battery` and `ant_light_battery`).

---

### 🚗 Smart 2-Tier Radar Threat Mode
* **Level 1 (Yellow / Standard Approaching Vehicle):** Automatically switches light to active daytime warning mode (e.g. `Tagfahrlicht` or `Blinken Schnell`).
* **Level 2 (Red / Fast Approaching Vehicle or Platoon):** Triggers maximum-visibility flash mode (e.g. `Blinken Schnell`).
* **Hold Time Guard:** Configurable hold time (1s to 5s) after the vehicle passes to avoid flickering between closely trailing cars.
* **🧪 Integrated Radar Simulator:** Built-in offline testing loop in the settings menu simulating realistic FIT vehicle approaches for indoor testing without live traffic.

---

### 🛑 G-Force Smart Brake Light (Accelerometer)
* Utilizes the Karoo's internal hardware G-sensor (`TYPE_ACCELEROMETER`).
* Detects forward deceleration along the Y-axis sustained for at least 200 ms.
* Instantly switches the tail light to brake mode (e.g. `Dauerlicht Hell` or `Blinken Schnell`) for a configurable hold duration (e.g. 2 seconds) and returns to your previous riding mode automatically.
* Adjustable sensitivity: *0.15 g (Very Sensitive)*, *0.20 g (Normal / Recommended)*, *0.30 g (Sporty)*, *0.40 g (Heavy Braking Only)*.

---

### ⚡ Speed-Adaptive Lighting
* Continuously streams GPS speed from Karoo.
* When exceeding a configurable threshold (e.g. 25, 28, or 30 km/h on fast descents), automatically switches to high beam / high mode (`Dauerlicht Hell`).
* Returns to normal daytime running mode when speed drops.

---

### 🚀 Ride Automation & Pause Behavior
* **Auto-Start / Auto-Stop:** Automatically turns on all lights when you press **„Start Ride“** and shuts them off when the ride ends.
* **Configurable Pause Behavior:** Choose what happens during cafe stops or traffic lights:
  * *Leave lights on* (default)
  * *Dim to low power mode* (`Dauerlicht Spar`)
  * *Turn off during pause* (and restore automatically on resume)
* **Karoo In-Ride Alert Popups:** Displays native 3-second Karoo top banners upon mode changes (e.g. *„Lezyne: Tagfahrlicht“*).

---

### 🎨 Dark-Cockpit Design & Full Localization
* High-contrast, energy-efficient dark cockpit theme (`#121318` background, `#282A33` buttons, `#00E676` green, `#FF2A55` red).
* 100% German localization with English fallback.

---

## 📥 Installation on Hammerhead Karoo 3

Hammerhead makes sideloading extensions onto the Karoo 3 effortless via the **Hammerhead Companion App**:

1. **Download the Extension APK:**  
   Download the latest `rayo-karoo-extension-apk.zip` from GitHub Actions / Releases on your smartphone and extract the `.apk`.
2. **Share with Hammerhead Companion App:**  
   - In your smartphone's file manager, long-press the `.apk` file and tap **Share**.
   - Select the **Hammerhead Companion App** as the target.
3. **Install on your Karoo 3:**  
   Your Karoo 3 will display an installation prompt. Tap **„Install“** on the Karoo screen.
4. **Done!** Launch **Rayo & Radar Cockpit** from the Karoo app menu.

---

## ⚙️ Pairing & Setup

1. **Rayo Light (Bluetooth):**
   * Turn on your Rayo light.
   * Open **Rayo & Radar Cockpit** on your Karoo and tap **„Rayo suchen & koppeln“**.
2. **Lezyne Radar / ANT+ Light:**
   * Pair your Lezyne Radar light once in Karoo's native **Settings ➔ Sensors** menu (ensure native "Auto Control" is set to OFF to prevent control conflicts).
   * The extension detects it automatically and binds via Karoo's internal `SensorService` IPC.
3. **Add Widgets to your Ride Pages:**
   * Go to **Karoo Profile Settings ➔ Edit Ride Screen ➔ Add Data Field**.
   * Add **Rayo Steuerung** (`rayo_control`) and/or **Lezyne / ANT+ Steuerung** (`ant_light_control`).

---

## ☕ Support

If this extension makes your bike rides safer and more convenient, consider supporting the project:

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Donate-yellow?style=for-the-badge&logo=buy-me-a-coffee)](https://buymeacoffee.com/02eOWKzm5W)

---

## 🙏 Credits & Acknowledgments

- **[LightONKaroo](https://github.com/JaJaJim/LightONKaroo)**: Special thanks to Dennis Straßer, Peter Weber ([JaJaJim](https://github.com/JaJaJim)), and Tiago Fontes Dias for their pioneering reverse-engineering and IPC implementation for Karoo `SensorService` ANT+ light and radar control (MIT License).

---

## 📜 License & Disclaimer

- **License:** Distributed under the [MIT License](LICENSE).
- **Disclaimer:** Independent open-source project. Not affiliated with or endorsed by Hauteworks Inc. or Hammerhead Navigation Inc.
