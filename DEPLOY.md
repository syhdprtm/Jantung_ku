# Deploy Jantungku gratis: Render + TiDB Cloud

Aplikasi berjalan di **Render** (paket Free, Docker) dan datanya disimpan di **TiDB Cloud Starter**
(database kompatibel MySQL, gratis 5 GiB). Setiap `git push` ke branch `main` otomatis ter-deploy ulang.

| Bagian | Layanan | Catatan paket gratis |
|---|---|---|
| Aplikasi | Render Web Service (Free) | Tidur setelah 15 menit tanpa pengunjung; kunjungan berikutnya menunggu ±1 menit |
| Database | TiDB Cloud Starter | Gratis permanen, 5 GiB per instance, wajib koneksi TLS (port 4000) |

File yang dipakai: [`Dockerfile`](Dockerfile), [`render.yaml`](render.yaml),
[`application-prod.properties`](src/main/resources/application-prod.properties).

---

## 1. Buat database di TiDB Cloud

1. Buka <https://tidbcloud.com> dan daftar (bisa memakai akun GitHub/Google).
2. Buat instance **Starter**. Pilih region **AWS Singapore (ap-southeast-1)** agar dekat dengan server Render.
3. Setelah aktif, klik **Connect**:
   - *Connect With*: **General**
   - klik **Generate Password** (atau *Reset Password*), lalu **salin dan simpan** password itu.
4. Catat empat nilai ini:
   - **HOST**, mis. `gateway01.ap-southeast-1.prod.aws.tidbcloud.com`
   - **PORT**: `4000`
   - **USERNAME**, mis. `xxxxxxxx.root`
   - **PASSWORD**: yang baru dibuat

Database `jantungku` dan semua tabelnya dibuat otomatis saat aplikasi pertama kali berjalan (Flyway).

## 2. Deploy aplikasi di Render

1. Buka <https://render.com> dan daftar dengan **akun GitHub** yang memiliki repo `Jantung_ku`.
2. Klik **New → Blueprint**, pilih repo **Jantung_ku**. Render membaca `render.yaml` otomatis.
3. Isi nilai yang diminta:

   | Variabel | Isi dengan |
   |---|---|
   | `DB_HOST` | HOST dari TiDB |
   | `DB_USERNAME` | USERNAME dari TiDB |
   | `DB_PASSWORD` | PASSWORD dari TiDB |
   | `ADMIN_PASSWORD` | password baru untuk akun `admin` (buat yang kuat) |
   | `PAKAR_PASSWORD` | password baru untuk akun `pakar` |

4. Klik **Apply**. Build pertama memakan waktu ±5–10 menit. Pantau di tab **Logs** sampai muncul
   `Started SistemPakarApplication`.
5. Alamat aplikasi tampil di bagian atas halaman service, mis. `https://jantungku.onrender.com`.

> Aplikasi **sengaja menolak start** bila `ADMIN_PASSWORD` atau `PAKAR_PASSWORD` kosong, sehingga
> password bawaan `admin123` / `pakar123` tidak pernah dipakai di internet.

## 3. Setelah online

- Pengguna umum: buka alamat aplikasi → **Daftar** → lakukan pemeriksaan.
- Admin & pakar: buka `/petugas/masuk` (tidak ditautkan dari halaman publik), masuk dengan
  `admin` / `pakar` dan password yang diisi di langkah 2.
- Memperbarui aplikasi: ubah kode, lalu `git push`. Render otomatis build dan deploy ulang.
- Mengganti password database atau akun: ubah di **Render → service → Environment**, lalu deploy ulang.
  (Password akun yang sudah dibuat diganti lewat menu **Profil**; `ADMIN_PASSWORD` hanya dipakai saat
  akun pertama kali dibuat.)

## Pemecahan masalah

| Gejala di Logs Render | Penyebab & solusi |
|---|---|
| `Could not resolve placeholder 'ADMIN_PASSWORD'` | Variabel belum diisi di tab Environment |
| `Access denied for user` | `DB_USERNAME` / `DB_PASSWORD` salah; buat ulang password di TiDB |
| `Communications link failure` | `DB_HOST` salah atau instance TiDB sedang dijeda; buka konsol TiDB |
| Halaman memuat lama (±1 menit) | Normal pada paket Free: server sedang bangun dari tidur |

## Catatan penting

- Aplikasi ini menyimpan **data kesehatan** pengguna (nama, tanggal lahir, alamat, jawaban gejala).
  Pastikan halaman **Kebijakan Privasi** sesuai sebelum dibagikan secara luas.
- Paket gratis cocok untuk demo, tugas akhir, dan uji coba, bukan untuk layanan kesehatan sungguhan.
