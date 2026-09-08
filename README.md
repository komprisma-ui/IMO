# IMO — Intelligent Mobile Operator

AI personal operator untuk Android — voice, perception, reasoning, execution, dan verification.

## Visi
IMO dirancang sebagai operator AI yang dapat memahami perintah bahasa alami, membaca konteks layar, menjalankan aksi pada perangkat Android, memverifikasi hasil, dan pulih ketika terjadi kegagalan.

## Arsitektur inti

`Voice → Brain → Perception → Executor → Verify → Brain`

Loop utama: **observe → think → act → observe → verify**.

## Status

🚧 Active development — fondasi Android Accessibility + voice operator sedang dibangun.

## Target kemampuan

- Percakapan suara Bahasa Indonesia
- Text-to-Speech
- Membaca UI Android melalui Accessibility
- Membuka aplikasi
- Klik elemen berdasarkan teks/content description
- Mengetik ke field input
- Back / Home
- Perencanaan multi-langkah
- Verifikasi hasil aksi
- Recovery ketika aksi gagal
- Confirmation gate untuk aksi sensitif
- Integrasi AI Brain/LLM secara aman

## Struktur awal

- `app/` — aplikasi Android IMO
- `.github/workflows/` — automated Android build
- `README.md` — dokumentasi proyek

## Prinsip keamanan

API key dan credential tidak boleh ditanam langsung ke source code atau di-commit ke repository. Gunakan secret management dan konfigurasi runtime yang aman.
