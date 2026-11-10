# Jantungku — Sistem Pakar Diagnosa Penyakit Jantung (Certainty Factor)

Aplikasi web **Java (Spring Boot) + MySQL** dari laporan *"Sistem Pakar Diagnosa Penyakit Jantung
dengan Pendekatan Metode Certainty Factor"* (Teknik Informatika UNSERA, 2026). Ada dua role:
**Administrator** dan **Pengguna**. Tampilannya responsif, jadi bisa dibuka di laptop maupun HP.

## Kebutuhan
- **Java JDK 21 atau lebih baru** (sudah terpasang: JDK 26)
- **MySQL 8** dari **Laragon** — klik *Start All* di Laragon sebelum menjalankan aplikasi
- Maven **tidak perlu dipasang**: proyek sudah menyertakan Maven Wrapper (`mvnw.cmd`)

## Cara menjalankan
Buka PowerShell/Terminal di folder proyek ini, lalu:

```bash
.\mvnw.cmd spring-boot:run
```

Pertama kali akan mengunduh dependensi (beberapa menit). Setelah muncul
`Started SistemPakarApplication`, buka **http://localhost:8090**.

Database `sistem_pakar_jantung` beserta tabel dan data dari laporan (13 gejala, 2 hipotesis,
49 rule) **dibuat otomatis**. Struktur tabel dikelola **Flyway** (`src/main/resources/db/migration/V*.sql`):
setiap perubahan skema ditambahkan sebagai file migrasi baru, tidak pernah mengedit file lama.
File [`database/sistem_pakar_jantung.sql`](database/sistem_pakar_jantung.sql) adalah kondisi V1
(sebelum migrasi); bila diimpor manual, aplikasi akan menjalankan V2 dan seterusnya saat start.

**Akun awal** (dibuat otomatis, segera ganti password lewat menu **Profil**):

| Role | Halaman masuk | Username | Password | Tugas |
|---|---|---|---|---|
| Administrator | `/petugas/masuk` | `admin` | `admin123` | Mengelola akun pengguna & pakar, memantau semua riwayat |
| Pakar | `/petugas/masuk` | `pakar` | `pakar123` | Mengelola gejala, bobot CF, hipotesis, rule base, validasi, evaluasi |
| Pengguna | `/login` | daftar sendiri | — | Melakukan diagnosa, melihat & mengunduh riwayat sendiri |

### Keamanan login
- **Dua pintu masuk terpisah.** Petugas (admin & pakar) hanya bisa masuk lewat
  `/petugas/masuk`, yang sengaja tidak ditautkan dari halaman publik. Pengguna hanya lewat
  `/login`. Akun petugas selalu ditolak di `/login`, dan sebaliknya.
- **Area petugas tersembunyi.** Bagi tamu dan pengguna biasa, `/admin/**` dan `/pakar/**`
  menampilkan 404 (bukan diarahkan ke halaman login).
- **Kunci sementara.** Setelah 5 kali password salah berturut-turut, akun dikunci 15 menit.
  Administrator dapat membuka kunci lebih awal di menu **Pengguna**.
- **Audit login.** Login, gagal login, dan keluar oleh petugas, serta setiap akun yang terkunci,
  tercatat di **Audit log** (filter *Sesi login*) beserta alamat IP.
- **Sesi petugas singkat.** Sesi petugas berakhir setelah 30 menit tidak aktif; sesi pengguna 8 jam.

### Membuat file .jar (opsional)
```bash
.\mvnw.cmd package
```
```bash
java -jar target\sistem-pakar-jantung.jar
```

### Membuka di HP
1. Pastikan HP dan laptop memakai WiFi yang sama.
2. Cari IP laptop dengan perintah `ipconfig` (lihat *IPv4 Address*, mis. `192.168.1.5`).
3. Di browser HP buka `http://192.168.1.5:8090`.
4. Bila tidak terbuka, jadikan WiFi berprofil *Private* dan izinkan port aplikasi di Windows
   Defender Firewall (PowerShell sebagai Administrator):
   `New-NetFirewallRule -DisplayName "Sistem Pakar" -Direction Inbound -Protocol TCP -LocalPort 8090 -Action Allow -Profile Private`

Di Chrome Android, menu **Tambahkan ke layar utama** akan memasang aplikasi seperti aplikasi biasa.

### Pengaturan (opsional)
Semua bisa diubah lewat environment variable tanpa mengubah kode:

| Variabel | Default | Keterangan |
|---|---|---|
| `PORT` | `8090` | Port aplikasi |
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` | Server MySQL |
| `DB_NAME` | `sistem_pakar_jantung` | Nama database |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / *(kosong)* | Akun MySQL (default Laragon) |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | Admin awal (hanya saat tabel users kosong) |
| `PAKAR_USERNAME` / `PAKAR_PASSWORD` | `pakar` / `pakar123` | Pakar awal (bila belum ada akun ber-role PAKAR) |

Contoh di PowerShell: `$env:PORT=9000; .\mvnw.cmd spring-boot:run`

## Fitur per role

| Administrator (`/admin`) | Pakar (`/pakar`) | Pengguna |
|---|---|---|
| Dashboard: total diagnosa, **grafik tren per minggu**, sebaran hasil | Dashboard: status validasi, evaluasi terakhir, gejala & rule paling sering | Diagnosa **keyakinan gejala** dan **data klinis** |
| Manajemen akun (Pengguna / Pakar / Administrator) | Kelola gejala + bobot CF pakar, hipotesis, rule base | Hasil + penjelasan "Bagaimana", **unduh PDF** |
| Riwayat semua pengguna: **filter, cari, paginasi, ekspor Excel** | **Validasi** rule & **evaluasi akurasi** dataset (+ ekspor Excel) | Riwayat sendiri: filter, grafik perkembangan, ekspor Excel |

Admin dan pakar juga bisa melakukan uji diagnosa dan membuka hasil diagnosa siapa pun.
Keamanan: password BCrypt, CSRF di semua form, `/admin/**` hanya ADMIN, `/pakar/**` hanya PAKAR,
pendaftaran publik selalu menjadi Pengguna, pengguna hanya bisa membuka hasilnya sendiri.

### Laporan & ekspor
- **PDF hasil diagnosa** (tombol *Unduh PDF*): identitas, kesimpulan, data/gejala, rule terpicu,
  langkah CF combine. Dibuat dengan OpenPDF.
- **Excel** (.xlsx, Apache POI): riwayat sesuai filter yang aktif; hasil evaluasi berisi sheet
  *Ringkasan*, *Per rule*, dan *Salah prediksi* — siap dilampirkan di skripsi.

### Warna grafik
Risiko `#e2533f` (koral) dan normal `#0b8fa3` (teal) — pasangan ini lolos uji buta warna
(ΔE 14,6 untuk protanopia). Setiap grafik punya legenda, tooltip, dan tabel alternatif.
## Metode perhitungan
1. `CF(H,E) = CF(User) × CF(Pakar)` untuk setiap gejala yang dipilih.
2. Digabung berurutan: `CFcombine = CFlama + CFbaru × (1 − CFlama)`.
3. Dihitung untuk setiap hipotesis; kesimpulan adalah hipotesis dengan CF tertinggi.

Perhitungan memakai `BigDecimal` dan dibulatkan 8 desimal di setiap langkah, sama seperti
Tabel 5.6. Contoh laporan menghasilkan **0.98329956 (98,33%)**. Ini diuji otomatis di
`CertaintyFactorTest`.

### Metode 2 — rule base (forward chaining)
1. Fakta = 11 data klinis pasien (Age, Sex, ChestPainType, RestingBP, Cholesterol, FastingBS,
   RestingECG, MaxHR, ExerciseAngina, Oldpeak, ST_Slope).
2. Teks kondisi IF setiap rule diparse menjadi syarat terstruktur (tabel `rule_syarat`).
3. Rule terpicu bila **semua** syaratnya terpenuhi. `CF(H,E) = CF data (1) × CF rule`.
4. Bila beberapa rule terpicu untuk hipotesis yang sama, digabung dengan CF combine.
5. Halaman hasil menampilkan rule terpicu dan setiap syarat beserta nilai pasien.

### Evaluasi dataset
Menu **Evaluasi** → unggah CSV berlabel `HeartDisease` (0/1). Kedua metode dijalankan pada setiap
baris; untuk metode gejala, gejala D01–D13 diturunkan otomatis dari data klinis (CF user = 1).
Hasil: akurasi, akurasi baris tercakup, presisi, recall, spesifisitas, F1, cakupan,
confusion matrix, presisi per rule, dan contoh baris yang salah prediksi.

### Temuan validasi pada rule base laporan
| Rule | Masalah |
|---|---|
| R3 | `Age <= 60 AND Age > 63` — tidak mungkin terpenuhi |
| R5 | `Age > 67 AND Age <= 60` — tidak mungkin terpenuhi |
| R44 | `Fasting BS > 1` — FastingBS hanya bernilai 0/1 |
| R1 vs R2, R4, R6, R7 | `Max HR > 185` vs `Max HR <= 186` — tumpang tindih di nilai 186 |
| R9 vs R12, R14 | `Fasting BS 1` vs `Fasting BS <= 1` — tumpang tindih saat FBS = 1 |
| R13 vs R14 | `Age < 47` vs `Age <= 47` — kesimpulan berlawanan |

Perbaiki lewat menu Rule base (cocokkan dengan pohon keputusan asli, Gambar 5.1), lalu jalankan
ulang Evaluasi untuk melihat pengaruhnya terhadap akurasi.

## Kualitas teknis

| Aspek | Implementasi |
|---|---|
| Migrasi database | **Flyway** `db/migration/V1`–`V7` — setiap perubahan skema adalah file baru; database lama otomatis di-*baseline* |
| Sesi login | **Spring Session JDBC** (tabel `SPRING_SESSION`) — tetap login walau server di-restart |
| Audit log | Tabel `audit_log` — siapa, kapan, aksi, data, nilai **sebelum & sesudah**. Menu *Audit log* (admin) dan kartu "Perubahan terbaru" (pakar). Dicatat dalam transaksi yang sama dengan perubahannya |
| Lapisan kode | Controller → **Service** (`BasisPengetahuanService`, `DiagnosaService`, `EvaluasiService`, `AuditService`) → Repository (JdbcClient) |
| Validasi | **Bean Validation** (`@NotBlank`, `@Pattern`, `@Size`) pada record form + aturan bisnis di service |
| Penanganan error | `PenanganError` (`@ControllerAdvice`): halaman 400/404/405/413/503/500 yang rapi; error tak terduga diberi **kode referensi** yang sama dengan di log |
| CI | `.github/workflows/ci.yml` — GitHub Actions menjalankan semua tes dengan MySQL 8 setiap push |

Untuk mengaktifkan CI: pasang Git, buat repositori di GitHub, lalu push proyek ini. Workflow
akan berjalan otomatis (tab *Actions*).
## Pengujian
```bash
.\mvnw.cmd test
```
Tes integrasi memakai MySQL Laragon dengan database terpisah `sistem_pakar_jantung_test`
(dibuat ulang setiap kali tes jalan, data utama tidak tersentuh).

## Struktur proyek
```
src/main/java/id/ac/unsera/sistempakar/
  service/CertaintyFactor.java   mesin inferensi CF
  service/RuleParser.java        teks kondisi IF -> syarat terstruktur
  service/RuleEngine.java        forward chaining rule base
  service/RuleValidator.java     deteksi rule mustahil & tumpang tindih
  service/TurunanGejala.java     data klinis -> gejala D01–D13
  service/Evaluator.java         evaluasi akurasi terhadap dataset CSV
  service/LaporanPdfService.java laporan PDF hasil diagnosa
  service/EksporExcelService.java ekspor Excel riwayat & evaluasi
  service/DiagnosaService.java   proses & simpan diagnosa
  service/BasisPengetahuanService.java  validasi + simpan gejala/hipotesis/rule + audit
  service/AuditService.java      pencatatan audit log
  web/PenanganError.java         penanganan error terpusat
  config/DataSeeder.java         data awal dari laporan
  config/SecurityConfig.java     login & hak akses role
  repository/                    akses MySQL (JdbcClient)
  web/                           controller publik, pengguna, admin, pakar
src/main/resources/
  db/migration/V*.sql            migrasi skema MySQL (Flyway)
  templates/                     halaman Thymeleaf
  static/css/app.css             sistem desain UI
database/sistem_pakar_jantung.sql  ekspor SQL (opsional)
arsip-nodejs/                    versi Node.js sebelumnya (boleh dihapus)
```

*Hasil sistem ini bersifat informatif dan tidak menggantikan diagnosis dokter.*

## Kredit foto
Foto di beranda (`src/main/resources/static/img/foto/`) berasal dari [Unsplash](https://unsplash.com)
dengan [Unsplash License](https://unsplash.com/license) (bebas dipakai, termasuk komersial):

| File | Fotografer |
|---|---|
| `dokter.webp` | [@nci](https://unsplash.com/@nci) (National Cancer Institute) |
| `konsultasi.webp` | [@silverkblack](https://unsplash.com/@silverkblack) |
| `ekg.webp` | [@jairlazarofuentes](https://unsplash.com/@jairlazarofuentes) |
| `stetoskop.webp` | [Patty Brito](https://unsplash.com/@pattybphoto) |
| `jalan.webp` | [@esdesignisms](https://unsplash.com/@esdesignisms) |
| `makanan.webp` | [@annapelzer](https://unsplash.com/@annapelzer) |

Ilustrasi jantung (`img/jantung.webp`) disediakan oleh pemilik proyek.