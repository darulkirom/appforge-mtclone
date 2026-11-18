testkey.p12  -> keystore PKCS12 (alias "testkey", password "android") yang dibuat dari
                testkey AOSP resmi (build/make/target/product/security/testkey.pk8 + testkey.x509.pem).
                Dipakai Gradle untuk menandatangani build debug & release MT Clone.

SHA-1 sertifikat : 61:ED:37:7E:85:D3:86:A8:DF:EE:6B:86:4B:D8:5B:0B:FA:A5:AF:81
SHA-256          : A4:0D:A8:0A:59:D1:70:CA:A9:50:CF:15:C1:8C:45:4D:47:A3:9B:26:98:9D:8B:64:0E:CD:74:5B:A7:1B:F5:DC

PERHATIAN: testkey AOSP bersifat PUBLIK (siapa pun punya kunci privatnya). Cocok untuk
build uji / mod pribadi / ROM, TIDAK cocok untuk rilis ke Play Store atau app yang
butuh identitas penanda tangan yang tepercaya.
Kunci yang sama ada di src/main/assets/keys/ (format asli pk8 + pem) untuk fitur
"Tanda tangani file" di dalam aplikasi.
