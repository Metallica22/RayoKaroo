# Rayo Karoo Extension 🚲⚡

[![Hammerhead Karoo](https://img.shields.io/badge/Device-Hammerhead%20Karoo%202%20%26%203-brightgreen.svg)](#)
[![SDK](https://img.shields.io/badge/SDK-Hammerhead%20karoo--ext%201.1.2-blue.svg)](https://github.com/hammerheadnav/karoo-ext)
[![License: MIT](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)
[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Donate-yellow.svg)](https://buymeacoffee.com/02eOWKzm5W)

> **Official Karoo Extension for the Hauteworks Rayo Smart Bicycle Tail Light.**  
> *Control your tail light directly from your Hammerhead Karoo handlebar computer during rides!*

---

## ✨ Features

- **📱 Interactive In-Ride Data Fields (Dedicated widget per light):**  
  - **Rayo Controller Widget:**
    - **One-Tap Light Toggle:** Turn the Rayo light ON or OFF with a single tap on the Karoo screen.
    - **Profile Switcher:** Cycle through the 4 mode profiles (*Pulse*, *Blink*, *Wave*, *Strobe*) on the fly.
    - **Live Battery Indicator:** Real-time battery percentage readout (e.g. `85%`).
  - **Lezyne / ANT+ Radar Controller Widget:**
    - **Mode Switcher Button:** Tap to cycle modes with German localization (*Tagfahrlicht*, *Dauerlicht Hell*, *Dauerlicht Mittel*, *Blinken Schnell*, *Aus*).
    - **Radar Threat Warning Badge:** Real-time alert indicator (`RADAR FREI` in green or `FAHRZEUG!` in red).
    - **Quick Power Toggle:** One-tap button to turn light ON / OFF.
- **🚗 Smart Radar Threat Mode:**  
  - When an approaching vehicle is detected from behind, the Lezyne light automatically boosts or switches to fast flash to alert traffic.
  - Automatically restores your normal light mode after the vehicle has passed.
- **🚀 Smart Ride Automation (Auto-Start / Auto-Stop):**  
  - When you press **„Start Ride“** on your Karoo, all lights power on automatically with your preferred profile/mode.
  - When you finish your ride, the lights turn off automatically.
- **🔒 100% Standalone & Offline:**  
  Direct Bluetooth Low Energy (BLE) and ANT+ communication on Karoo. No phone connection required during your ride!

---

## 📥 How to Install on Karoo 3 (Sideloading)

Hammerhead makes sideloading extensions onto the Karoo 3 super easy via the **Hammerhead Companion App**:

1. **Download the Extension APK:**  
   Download the latest `rayo-karoo-extension.apk` from the [Releases](https://github.com/) section on your smartphone.
2. **Share with Hammerhead Companion App:**  
   - In your smartphone's file manager or browser downloads, **long-press the APK file** and tap **Share**.
   - Select the **Hammerhead Companion App** as the target.
3. **Install on your Karoo 3:**  
   Your Karoo 3 will display an installation prompt. Tap **„Install“** on the Karoo screen.
4. **Done!** The extension is now installed.

---

## ⚙️ Initial Pairing & Setup

1. Turn on your Rayo light.
2. On your Karoo, open the main menu and launch **Rayo Light**.
3. Tap **„Scan & Pair Rayo“**. The app will detect your light and save the connection.
4. Customize your preferences:
   - **Auto-start with ride:** Enabled by default.
   - **Default profile:** Choose Profile 1, 2, 3, or 4.
5. Go to your Karoo Profile Settings ➔ Edit Ride Screen ➔ Add Data Field ➔ Select **Rayo Controller** or **Rayo Battery**.

---

## 🛠️ Building from Source

This project uses standard Gradle with Hammerhead's official `karoo-ext` SDK:

```bash
git clone https://github.com/<your-username>/rayo-karoo-extension.git
cd rayo-karoo-extension
./gradlew assembleRelease
```

A GitHub Actions workflow (`.github/workflows/build.yml`) is included to automatically build releases on push.

---

## ☕ Support

If this extension makes your bike rides safer and more convenient, consider supporting the project:

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Donate-yellow?style=for-the-badge&logo=buy-me-a-coffee)](https://buymeacoffee.com/02eOWKzm5W)

---

## 🙏 Credits & Acknowledgments

- **[LightONKaroo](https://github.com/JaJaJim/LightONKaroo)**: Huge thanks to Dennis Straßer, Peter Weber ([JaJaJim](https://github.com/JaJaJim)), and Tiago Fontes Dias for their pioneering reverse-engineering and IPC implementation for Karoo `SensorService` ANT+ light and radar control (MIT License).

---

## 📜 License & Disclaimer

- **License:** Distributed under the [MIT License](LICENSE).
- **Disclaimer:** Independent open-source project. Not affiliated with or endorsed by Hauteworks Inc. or Hammerhead Navigation Inc.
