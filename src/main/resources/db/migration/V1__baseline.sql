-- Skema basis data Sistem Pakar Diagnosa Penyakit Jantung (MySQL 8)

CREATE TABLE IF NOT EXISTS users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    nama       VARCHAR(100) NOT NULL,
    username   VARCHAR(30)  NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    role       ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Hipotesis kesimpulan. kategori menentukan warna/arti hasil di UI.
CREATE TABLE IF NOT EXISTS penyakit (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    kode       VARCHAR(15)  NOT NULL UNIQUE,
    nama       VARCHAR(150) NOT NULL,
    kategori   ENUM('RISIKO', 'NORMAL') NOT NULL DEFAULT 'RISIKO',
    keterangan TEXT,
    saran      TEXT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS gejala (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    kode       VARCHAR(15)  NOT NULL UNIQUE,
    nama       VARCHAR(150) NOT NULL,
    keterangan TEXT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Basis pengetahuan: CF Pakar tiap gejala terhadap tiap hipotesis
CREATE TABLE IF NOT EXISTS bobot (
    gejala_id   BIGINT NOT NULL,
    penyakit_id BIGINT NOT NULL,
    cf_pakar    DECIMAL(4, 3) NOT NULL,
    PRIMARY KEY (gejala_id, penyakit_id),
    CONSTRAINT fk_bobot_gejala   FOREIGN KEY (gejala_id)   REFERENCES gejala (id)   ON DELETE CASCADE,
    CONSTRAINT fk_bobot_penyakit FOREIGN KEY (penyakit_id) REFERENCES penyakit (id) ON DELETE CASCADE,
    CONSTRAINT ck_bobot_cf CHECK (cf_pakar > 0 AND cf_pakar <= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Rule base hasil decision tree (Tabel 5.4 laporan)
CREATE TABLE IF NOT EXISTS rules (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kode        VARCHAR(15) NOT NULL UNIQUE,
    kondisi     TEXT NOT NULL,
    penyakit_id BIGINT NULL,
    cf          DECIMAL(4, 3) NOT NULL,
    keterangan  TEXT,
    CONSTRAINT fk_rules_penyakit FOREIGN KEY (penyakit_id) REFERENCES penyakit (id) ON DELETE SET NULL,
    CONSTRAINT ck_rules_cf CHECK (cf >= 0 AND cf <= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Riwayat diagnosa. Nilai disimpan sebagai snapshot agar hasil lama tidak
-- berubah ketika admin mengubah bobot di kemudian hari.
CREATE TABLE IF NOT EXISTS diagnosa (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    nama_pasien   VARCHAR(100) NOT NULL,
    umur          INT NULL,
    hasil_kode    VARCHAR(15)  NULL,
    hasil_nama    VARCHAR(150) NULL,
    hasil_kategori VARCHAR(10) NULL,
    hasil_cf      DECIMAL(10, 8) NOT NULL DEFAULT 0,
    saran         TEXT,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_diagnosa_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_diagnosa_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Gejala yang dipilih beserta CF User
CREATE TABLE IF NOT EXISTS diagnosa_jawaban (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    diagnosa_id BIGINT NOT NULL,
    urutan      INT NOT NULL,
    gejala_kode VARCHAR(15)  NOT NULL,
    gejala_nama VARCHAR(150) NOT NULL,
    cf_user     DECIMAL(4, 3) NOT NULL,
    CONSTRAINT fk_jawaban_diagnosa FOREIGN KEY (diagnosa_id) REFERENCES diagnosa (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Nilai CF akhir per hipotesis
CREATE TABLE IF NOT EXISTS diagnosa_hasil (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    diagnosa_id   BIGINT NOT NULL,
    urutan        INT NOT NULL,
    penyakit_kode VARCHAR(15)  NOT NULL,
    penyakit_nama VARCHAR(150) NOT NULL,
    kategori      VARCHAR(10)  NOT NULL,
    cf            DECIMAL(10, 8) NOT NULL,
    CONSTRAINT fk_hasil_diagnosa FOREIGN KEY (diagnosa_id) REFERENCES diagnosa (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Rincian CF User x CF Pakar per gejala per hipotesis (untuk menampilkan langkah perhitungan)
CREATE TABLE IF NOT EXISTS diagnosa_detail (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    diagnosa_id   BIGINT NOT NULL,
    urutan        INT NOT NULL,
    penyakit_kode VARCHAR(15)  NOT NULL,
    gejala_kode   VARCHAR(15)  NOT NULL,
    gejala_nama   VARCHAR(150) NOT NULL,
    cf_user       DECIMAL(4, 3) NOT NULL,
    cf_pakar      DECIMAL(4, 3) NOT NULL,
    CONSTRAINT fk_detail_diagnosa FOREIGN KEY (diagnosa_id) REFERENCES diagnosa (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
