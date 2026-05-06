# 🎃 Pumpkin Server GUI

Aplikasi Android untuk mengontrol Pumpkin Minecraft Bedrock Server.

## Cara Build (Tanpa PC)

### 1. Upload ke GitHub
- Buat akun GitHub (gratis)
- Buat repository baru
- Upload semua file project ini

### 2. Build Otomatis via GitHub Actions
- Buka tab **Actions** di repository
- Klik **Build APK** → **Run workflow**
- Tunggu ~5 menit
- Download APK dari **Artifacts**

---

## Cara Pakai Setelah Install

### 1. Copy Binary Pumpkin
```bash
# Di Termux, copy binary ke folder app:
cp ~/pumpkin /data/data/com.pumpkin.gui/files/pumpkin
```
Atau tekan tombol 📁 di app untuk melihat path yang benar.

### 2. Jalankan Server
- Tekan **▶ Start Server**
- Log akan muncul di console
- Pemain yang join/leave otomatis terdeteksi

### 3. Kirim Command
- Ketik command di kolom bawah (contoh: `list`, `stop`, `say Halo!`)
- Tekan **Kirim**

---

## Kenapa targetSdk 28?

Sama seperti Termux, app ini menggunakan `targetSdkVersion = 28` agar bisa
mengeksekusi binary dari direktori internal app (`filesDir`).
Android 10+ memblokir ini untuk targetSdk >= 29 (W^X restriction).

---

## Fitur
- ✅ Start / Stop server
- ✅ Console log real-time dengan warna
- ✅ Kirim command ke server
- ✅ Deteksi pemain online otomatis
- ✅ Foreground service (server tetap jalan saat app di-minimize)
- ✅ Notifikasi status server
