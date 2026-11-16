# MT Clone (v1: APK / Java / smali viewer)

Proyek terpisah dari ApkForge. Fokus v1: buka file .apk, telusuri isinya, dan baca
classes.dex sebagai smali, AndroidManifest.xml sebagai teks, file teks lain, dan
hex dump untuk sisanya.

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
- **Tanda tangan ulang APK.** "Simpan sbg APK" menghasilkan APK **belum ditandatangani**.
  Untuk menandatangani, pakai proyek **ApkForge** (sudah direncanakan terpisah), atau
  `apksigner` manual.
- resources.arsc (string/resource terkompilasi untuk file XML di /res selain manifest)
  belum didekode -- file XML lain di dalam APK juga dalam format binary yang sama,
  tapi kode saat ini cuma otomatis mendekode `AndroidManifest.xml`. Bisa diperluas.
- Root explorer / file manager untuk seluruh penyimpanan (bukan cuma isi 1 APK) --
  ini scope "file manager" yang di chat diputuskan terpisah dari "viewer APK" ini.

## Status pengujian
- Logika decoder AndroidManifest.xml (AxmlDecoder.kt) sudah diverifikasi lewat port
  1:1 ke Java yang dikompilasi & dijalankan terhadap data binary AXML yang disusun
  manual sesuai spesifikasi format -- hasilnya XML yang benar.
- Kode Android (Kotlin, dexlib2/baksmali, RecyclerView, dst) **belum pernah di-build**
  di environment ini (tidak ada Android SDK/Gradle dengan akses jaringan di sini).
  Build pertama akan terjadi di GitHub Actions. Kemungkinan ada error kompilasi kecil
  yang baru ketahuan di sana (nama API dexlib2/baksmali yang meleset, dll).
