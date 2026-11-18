# MT Clone

File manager dua jendela ala MT Manager + penjelajah APK (smali / manifest / teks / hex).
UI mengikuti rekaman layar referensi: dua jendela independen, menu ⋮, dialog sortir, layar Preferensi.

## Struktur kode (`app/src/main/java/com/test/mtclone/`)
```
core/
  file/    FileOps (listing, sortir, format ukuran), FileKind
  apk/     ZipTree (isi APK sebagai folder), RepackUtil (susun ulang + align ala zipalign)
  dex/     DexBrowser (dexlib2 + baksmali)
  axml/    AxmlDecoder (AndroidManifest biner -> teks)
  sign/    ApkSigning (v1+v2+v3, testkey AOSP, apksig)
  prefs/   AppPrefs (pengaturan, bookmark, sortir per jendela/folder)
ui/
  common/  AppBar, ikon, editor kode
  main/    MainActivity, PaneView, PaneState, FileRowAdapter, MainDialogs
  apk/     ApkOpenActivity, ApkEntryViewerActivity
  viewer/  LocalFileViewer
  settings/PreferencesActivity
res/       values/strings.xml (semua teks), colors, themes, drawable (ikon + logo), menu, mipmap (logo launcher)
assets/keys/  testkey.pk8 + testkey.x509.pem (testkey AOSP, untuk fitur tanda tangan di dalam app)
keystore/     testkey.p12 (untuk Gradle menandatangani build debug/release)
```

## Tanda tangan (testkey AOSP)
- **Build Gradle** (debug & release) ditandatangani `keystore/testkey.p12` (alias `testkey`, sandi `android`).
- **Di dalam app**: "Buka dengan... > Tanda tangani file" pada .apk menghasilkan `<nama>_sign.apk`;
  "Simpan sbg APK" di editor entry APK otomatis menandatangani hasilnya.
- SHA-1 sertifikat: `61:ED:37:7E:85:D3:86:A8:DF:EE:6B:86:4B:D8:5B:0B:FA:A5:AF:81`.
- testkey AOSP itu PUBLIK: cocok untuk uji/mod pribadi, bukan untuk rilis Play Store.

## Yang sudah ada
- Buka APK lewat file picker sistem (tidak perlu izin storage luas / root).
- Telusuri isi APK sebagai folder (dari struktur zip).
- classes*.dex -> daftar class -> smali per class (pakai dexlib2 + baksmali), **read-only**.
- AndroidManifest.xml -> didekode dari binary XML jadi teks (parser ditulis sendiri,
  **best-effort**: menangani struktur umum string pool + start/end namespace/element +
  tipe atribut umum; belum tentu menangani semua variasi chunk yang jarang dipakai).
  **Read-only.**
- File teks lain (res/*.xml non-manifest, assets teks, dll) -> bisa diedit, lalu
  "Simpan sbg APK" menulis ulang APK baru dengan entry itu diganti isinya.
- File lain -> hex dump (preview, read-only).

## Yang belum ada (di luar cakupan v1)
- **Edit & repack smali kembali ke dex.** Baksmali cuma dipakai untuk membaca
  (dex -> teks). Merakit smali -> dex lagi (assembler) belum diimplementasikan.
- Tanda tangan untuk **.apks** (split bundle): belum; ekstrak dulu lalu tanda tangani tiap .apk.
- resources.arsc (string/resource terkompilasi untuk file XML di /res selain manifest)
  belum didekode -- file XML lain di dalam APK juga dalam format binary yang sama,
  tapi kode saat ini cuma otomatis mendekode `AndroidManifest.xml`. Bisa diperluas.
- Root explorer / file manager untuk seluruh penyimpanan (bukan cuma isi 1 APK) --
  ini scope "file manager" yang di chat diputuskan terpisah dari "viewer APK" ini.

## Status pengujian
- Logika decoder AndroidManifest.xml (AxmlDecoder.kt) sudah diverifikasi lewat port
  1:1 ke Java yang dikompilasi & dijalankan terhadap data binary AXML yang disusun
  manual sesuai spesifikasi format -- hasilnya XML yang benar.
- Seluruh kode Kotlin lolos kompilasi `kotlinc` 1.9.24 terhadap android.jar 34 (AndroidX, apksig,
  dexlib2/baksmali diganti stub), tapi **belum di-build dengan Gradle/AGP** dan **belum dijalankan di perangkat**.
  Build penuh terjadi di GitHub Actions; dependensi `apksig:8.5.2` diambil dari repo Google.
- `RepackUtil` (kompresi STORED dipertahankan, align 4/4096) dan `FileOps` sudah diuji jalan.
- Penandatanganan dengan apksig **belum diuji sampai APK terpasang**; kunci/sertifikat sudah dicek
  (cocok pasangan, tanda tangan SHA256withRSA terverifikasi).
