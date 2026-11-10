# Sistem Pakar Diagnosa Penyakit Jantung — Metode Certainty Factor

Implementasi aplikasi web dari laporan *"Sistem Pakar Diagnosa Penyakit Jantung dengan
Pendekatan Metode Certainty Factor"* (Teknik Informatika UNSERA, 2026), dengan dua role:
**Administrator** dan **User**.

## Teknologi
- Node.js ≥ 22.13 (memakai SQLite bawaan `node:sqlite`, tanpa instal database terpisah)
- Express 5, EJS, express-session
- Password di-hash dengan `scrypt`

## Cara Menjalankan
```bash
npm install
npm start
```
Buka http://localhost:3000. Database otomatis dibuat di `data/sistem_pakar.db` dan diisi data
dari laporan saat pertama kali dijalankan.

**Akun administrator default:** username `admin`, password `admin123`
(bisa diubah lewat env `ADMIN_USERNAME` / `ADMIN_PASSWORD` sebelum start pertama).
Segera ganti password lewat menu **Profil** setelah login.

Menjalankan pengujian:
```bash
npm test
```

## Fitur per Role

| Administrator | User |
|---|---|
| Dashboard statistik & sebaran hasil diagnosa | Registrasi & login |
| CRUD **Gejala** + bobot **CF Pakar** per hipotesis | Form diagnosa (pilih tingkat keyakinan per gejala) |
| CRUD **Penyakit/Hipotesis** + saran | Hasil diagnosa lengkap dengan langkah perhitungan CF |
| CRUD **Rule Base** (R1–R49, Tabel 5.4) | Riwayat diagnosa milik sendiri (lihat, hapus, cetak) |
| Manajemen **User** (tambah, ubah role, reset password, hapus) | Ubah profil & password |
| Lihat/hapus **riwayat diagnosa semua user** | |
| Uji diagnosa | |

Registrasi publik selalu menghasilkan role **User**; akun Administrator hanya dapat dibuat oleh
Administrator. User hanya dapat melihat hasil diagnosanya sendiri.

## Basis Pengetahuan (dari laporan)

**Hipotesis:** P01 Terdiagnosa Penyakit Jantung · P02 Tidak Terdiagnosa (Normal)

| Kode | Gejala | CF P01 | CF P02 |
|---|---|---|---|
| D01 | Resting BP > 120 | 0.4 | 0 |
| D02 | Cholesterol > 200 | 0.4 | 0 |
| D03 | Heart Rate Deficit ≥ 40 | 0.8 | 0 |
| D04 | Exercise Angina (Ya) | 0.8 | 0 |
| D05 | Fasting BS tinggi | 0.8 | 0 |
| D06 | ST Slope Flat | 0.8 | 0 |
| D07 | Max HR < 140 | 0.8 | 0 |
| D08 | Resting ECG ST | 0.6 | 0 |
| D09 | Resting BP < 120 | 0 | 0.6 |
| D10 | Cholesterol < 200 | 0 | 0.6 |
| D11 | Resting ECG Normal | 0 | 0.6 |
| D12 | Fasting BS Normal | 0 | 0.6 |
| D13 | ST Slope Up | 0 | 1.0 |

**Nilai CF User:** Tidak 0 · Tidak tahu 0.2 · Sedikit yakin 0.4 · Cukup yakin 0.6 · Yakin 0.8 · Sangat yakin 1.0

## Metode Perhitungan
1. `CF(H,E) = CF(User) × CF(Pakar)` untuk setiap gejala yang dipilih.
2. Digabung berurutan: `CFcombine = CFlama + CFbaru × (1 − CFlama)`.
3. Dihitung untuk setiap hipotesis; kesimpulan = hipotesis dengan CF tertinggi.

Contoh pada laporan (Tabel 5.5–5.6) menghasilkan CF = **0.98329956 (98,33%)** untuk penyakit
jantung — nilai ini diuji otomatis di `test/cf.test.js`.

> Rule base R1–R49 disimpan sebagai basis pengetahuan yang dapat dikelola admin. Beberapa kondisi
> ditulis apa adanya sesuai laporan (mis. R3 `Age <= 60 AND Age > 63`), silakan disunting lewat
> menu **Rule Base** bila perlu.

## Struktur Proyek
```
server.js            entry point
src/cf.js            mesin inferensi Certainty Factor
src/seed.js          data awal dari laporan (gejala, bobot, rule, admin)
src/db.js            skema & koneksi SQLite
src/auth.js          hash password & middleware role
src/routes/          auth.js, user.js, admin.js
views/               template EJS (admin/, user/, partials/)
public/style.css     tampilan
test/                pengujian otomatis
```

*Hasil sistem ini bersifat informatif dan tidak menggantikan diagnosis dokter.*
