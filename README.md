📱 Minecraft Server on Android (Powered by Pumpkin)

An experimental Android application that allows you to run a Minecraft server directly on your phone.

This project is based on the open-source Pumpkin Minecraft Server, adapted and packaged into an Android APK with a simple interface for easier control.

---

⚠️ Important Credit

This project would not be possible without:

👉 Pumpkin Minecraft Server
https://github.com/pumpkin-mc/pumpkin

All core server functionality is developed by the Pumpkin MC contributors.

---

🚀 Overview

This application wraps a precompiled server binary into an Android app, allowing users to:

- Run a Minecraft server locally on Android
- Start / Stop the server easily
- Monitor basic system usage
- Experiment with portable server hosting

---

✨ Features

- 📦 ARM64 server binary (from Pumpkin)
- ▶️ Simple start / stop controls
- 📊 Basic CPU / memory monitoring
- 📱 Fully runs on-device
- 🔧 Lightweight and experimental

---

🛠️ What This Project Adds

Compared to the original Pumpkin project:

- Android APK packaging
- Mobile-friendly interface (GUI)
- Process management (start/stop/restart)
- Basic monitoring tools

---

⚖️ License

This project is licensed under the GNU General Public License v3.0.

Because this project is based on Pumpkin (GPLv3), the entire project is also licensed under GPLv3.

You are free to:

- Use
- Modify
- Distribute

As long as you also:

- Provide source code
- Keep the same license (GPLv3)
- Give proper credit

---

⚠️ Disclaimer

- This is an experimental project
- Not intended for production servers
- Performance depends on your device
- May cause overheating or battery drain

---

## 📦 Setup Requirements
 
Before building this project, you need to download the required server binary.
 
### 🔗 Pumpkin Server Binary
 
Download the latest `pumpkin` binary from the Releases page:
 
👉 [https://github.com/supanika721-beep/PumpkinMCGui/releases](https://github.com/supanika721-beep/PumpkinMCGui/releases)
  
## 📁 Installation Steps
 
 
1. Download the latest APK source and open in Android Studio
 
2. Download the **Pumpkin binary** from Releases
 
3. Place the binary into:
 

 `app/src/main/assets/pumpkin ` 
(or if your app uses internal storage at runtime:)
 `/data/data/<your.package.name>/files/pumpkin `  
## 🚀 Build & Run
 
### Build APK:
 `./gradlew assembleDebug ` 
### Run App:
 
 
- Install APK on device
 
- Open app
 
- The app will automatically extract / load the Pumpkin binary
 
- Press **Start Server**
 

  
## ⚠️ Notes
 
 
- Make sure the Pumpkin binary is executable
 
- On first run, app may need storage permission
 
- Only ARM64 (aarch64) binaries are supported

---
📲 Installation

1. Download the APK from Releases
2. Enable "Install unknown apps"
3. Install and open the app
4. Start the server

---

📌 Source Code Availability

In compliance with the GPLv3 license, the full source code for this project is available in this repository.

If you distribute this APK, you must also provide access to the source code.

---

🤝 Contributing

Contributions are welcome!
Feel free to fork, modify, and improve the project.

---

⭐ Acknowledgment

Special thanks to the Pumpkin MC developers for creating the core server software.

---
