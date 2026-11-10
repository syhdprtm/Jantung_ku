-- Dua metode diagnosa:
--   GEJALA : CF user x CF pakar per gejala (metode laporan, Bab 5.2)
--   KLINIS : data klinis pasien -> forward chaining rule base (Tabel 5.4)
ALTER TABLE diagnosa ADD COLUMN metode VARCHAR(10) NOT NULL DEFAULT 'GEJALA' AFTER umur;

-- Data klinis yang dimasukkan (snapshot)
CREATE TABLE diagnosa_klinis (
    diagnosa_id     BIGINT PRIMARY KEY,
    age             INT          NOT NULL,
    sex             CHAR(1)      NOT NULL,
    chest_pain      VARCHAR(3)   NOT NULL,
    resting_bp      INT          NOT NULL,
    cholesterol     INT          NOT NULL,
    fasting_bs      TINYINT      NOT NULL,
    resting_ecg     VARCHAR(6)   NOT NULL,
    max_hr          INT          NOT NULL,
    exercise_angina CHAR(1)      NOT NULL,
    oldpeak         DECIMAL(4, 1) NOT NULL,
    st_slope        VARCHAR(4)   NOT NULL,
    CONSTRAINT fk_klinis_diagnosa FOREIGN KEY (diagnosa_id) REFERENCES diagnosa (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Rule yang terpicu (snapshot kondisi saat diagnosa dibuat)
CREATE TABLE diagnosa_rule (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    diagnosa_id   BIGINT       NOT NULL,
    urutan        INT          NOT NULL,
    rule_kode     VARCHAR(15)  NOT NULL,
    kondisi       TEXT         NOT NULL,
    penyakit_kode VARCHAR(15)  NOT NULL,
    cf            DECIMAL(4, 3) NOT NULL,
    keterangan    TEXT,
    CONSTRAINT fk_drule_diagnosa FOREIGN KEY (diagnosa_id) REFERENCES diagnosa (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
