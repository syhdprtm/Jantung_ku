-- Jejak audit: siapa mengubah apa dan kapan (basis pengetahuan, akun, riwayat, evaluasi).
-- Nama & username disalin (bukan hanya FK) agar jejak tetap terbaca walau akunnya dihapus.
CREATE TABLE audit_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    waktu      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username   VARCHAR(30)  NOT NULL,
    role       VARCHAR(10)  NOT NULL,
    aksi       VARCHAR(10)  NOT NULL,   -- TAMBAH, UBAH, HAPUS, JALANKAN
    entitas    VARCHAR(20)  NOT NULL,   -- GEJALA, PENYAKIT, RULE, PENGGUNA, DIAGNOSA, EVALUASI
    kode       VARCHAR(60)  NOT NULL,   -- kode / username / id objek
    sebelum    TEXT NULL,               -- ringkasan nilai sebelum diubah
    sesudah    TEXT NULL,               -- ringkasan nilai sesudah diubah
    INDEX idx_audit_waktu (waktu),
    INDEX idx_audit_entitas (entitas)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
