# Simple Reso

Pengubah resolusi dan kerapatan layar Android lewat **Shizuku**, tanpa root.

- Atur resolusi bebas `W × H`, atau pakai skala cepat 100% / 90% / 80% / 75% / 66% / 50%
- Menampilkan **resolusi standar pabrik** dan **resolusi yang sedang aktif** berdampingan
- dpi dihitung otomatis supaya ukuran teks dan ikon tetap wajar
- **Pemulihan otomatis 10 detik**: kalau layar jadi tidak terbaca, cukup tunggu
- Semua kontrol terkunci selama Shizuku belum siap

Minimal Android 8.0 (API 26). Diuji sampai Android 15.

---

## Cara build lewat GitHub (tanpa Android Studio)

### 1. Buat repo dan unggah isinya

Di GitHub, buat repository baru bernama `simple-reso`. Lalu dari komputer:

```bash
cd simple-reso
git init
git add .
git commit -m "Simple Reso versi awal"
git branch -M main
git remote add origin https://github.com/USERNAME/simple-reso.git
git push -u origin main
```

Ganti `USERNAME` dengan nama pengguna GitHub Anda.

Tidak punya git? Buka repo baru di browser → **Add file → Upload files** → seret seluruh isi folder ini. Pastikan folder `.github` ikut terunggah; sebagian file manager menyembunyikan folder berawalan titik.

### 2. Tunggu build jalan sendiri

Begitu push selesai, buka tab **Actions**. Workflow `Build APK` jalan otomatis, sekitar 3–6 menit untuk build pertama.

### 3. Unduh APK

Klik workflow run yang sudah hijau → bagian **Artifacts** di bawah → unduh `simple-reso-debug`. Isinya file zip berisi `simple-reso-v1.0.0.apk`.

> APK debug sudah bisa dipasang dan dipakai normal. Nama paketnya `com.simplereso.app.debug`, jadi bisa berdampingan dengan versi release.

### 4. (Opsional) Rilis bertanda tangan

Kalau ingin APK release yang ditandatangani sendiri:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias simplereso
base64 -w0 release.jks > release.jks.txt    # macOS: base64 -i release.jks -o release.jks.txt
```

Di repo GitHub → **Settings → Secrets and variables → Actions → New repository secret**, buat empat secret:

| Nama | Isi |
|---|---|
| `KEYSTORE_BASE64` | seluruh isi `release.jks.txt` |
| `KEYSTORE_PASSWORD` | kata sandi keystore |
| `KEY_ALIAS` | `simplereso` |
| `KEY_PASSWORD` | kata sandi key |

Lalu buat rilis:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Workflow `Release APK` jalan dan melampirkan APK ke halaman **Releases**.

**Jangan pernah commit file `release.jks` ke repo.** File itu sudah masuk `.gitignore`.

---

## Cara pakai di HP

1. Pasang [Shizuku](https://shizuku.rikka.app/download/)
2. Jalankan Shizuku lewat ADB nirkabel (Android 11+) atau lewat PC. Perlu diulang tiap kali HP restart
3. Buka Simple Reso → beri izin saat diminta
4. Pilih skala atau ketik resolusi → **Terapkan**
5. Muncul hitung mundur 10 detik. Tekan **Simpan** kalau tampilan baik-baik saja

Kalau layar jadi kacau dan tombol tidak kelihatan, **jangan sentuh apa pun**. Dalam 10 detik resolusi kembali sendiri.

### Kalau sampai terlanjur tersimpan dan layar tidak terpakai

Sambungkan ke PC lalu jalankan:

```bash
adb shell wm size reset
adb shell wm density reset
```

---

## Cara kerjanya

Aplikasi mengikat `IWindowManager` lewat `ShizukuBinderWrapper`, bukan menjalankan `Runtime.exec`. Empat pemanggilan yang dipakai:

| Keperluan | Method |
|---|---|
| Resolusi pabrik | `getInitialDisplaySize` / `getInitialDisplayDensity` |
| Resolusi aktif | `getBaseDisplaySize` / `getBaseDisplayDensity` |
| Terapkan | `setForcedDisplaySize` / `setForcedDisplayDensityForUser` |
| Pulihkan | `clearForcedDisplaySize` / `clearForcedDisplayDensityForUser` |

Ini penting: setelah resolusi diubah, `DisplayMetrics` melaporkan nilai hasil ubahan. Hanya `getInitialDisplaySize` yang tetap mengembalikan ukuran panel sebenarnya, sehingga tombol Pulihkan selalu benar.

Kalau refleksi gagal (sebagian ROM menyembunyikan kelasnya), aplikasi jatuh ke perintah shell `wm size` / `wm density` lewat Shizuku, dan membaca baris *Physical size* untuk resolusi pabrik.

### Pemeriksaan Shizuku

Dijalankan saat aplikasi dibuka, saat kembali dari latar belakang, dan lewat `Shizuku.addBinderReceivedListener` supaya status berubah sendiri begitu layanan menyala.

```
Shizuku.pingBinder()           layanan hidup?
Shizuku.getVersion() >= 11     server cukup baru?
Shizuku.checkSelfPermission()  izin sudah diberi?
Shizuku.getUid()               shell (2000) atau root (0)?
```

Selama belum lolos, seluruh kontrol resolusi dikunci dan kartu panduan muncul dengan tombol yang sesuai kondisinya.

---

## Catatan perangkat

Sebagian ROM (MIUI, ColorOS, dan beberapa HyperOS) mengunci `setForcedDisplayDensityForUser` di tingkat sistem. Kalau ditolak, aplikasi menampilkan pesannya apa adanya, bukan diam-diam gagal.

## Struktur project

```
app/src/main/java/com/simplereso/app/
├── MainActivity.kt        titik masuk + pendengar status Shizuku
├── MainViewModel.kt       status layar, validasi input, hitung mundur pemulihan
├── ShizukuGate.kt         lima kondisi Shizuku dan pemeriksaannya
├── DisplayController.kt   IWindowManager lewat refleksi + cadangan shell
└── ui/
    ├── MainScreen.kt      seluruh antarmuka Compose
    └── Theme.kt           warna terang dan gelap
```

## Lisensi

MIT. Silakan pakai, ubah, dan bagikan.
