-- Evaluasi akurasi sistem terhadap dataset berlabel (kolom HeartDisease).
CREATE TABLE evaluasi (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nama_file   VARCHAR(200) NOT NULL,
    user_id     BIGINT NULL,
    jumlah      INT NOT NULL,
    dilewati    INT NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evaluasi_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Confusion matrix per metode (positif = terdiagnosa penyakit jantung)
CREATE TABLE evaluasi_metrik (
    evaluasi_id  BIGINT NOT NULL,
    metode       VARCHAR(10) NOT NULL,
    tp           INT NOT NULL,
    tn           INT NOT NULL,
    fp           INT NOT NULL,
    fn           INT NOT NULL,
    tak_tercakup INT NOT NULL,
    PRIMARY KEY (evaluasi_id, metode),
    CONSTRAINT fk_metrik_evaluasi FOREIGN KEY (evaluasi_id) REFERENCES evaluasi (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Seberapa sering tiap rule terpicu dan berapa yang benar
CREATE TABLE evaluasi_rule (
    evaluasi_id BIGINT NOT NULL,
    rule_kode   VARCHAR(15) NOT NULL,
    terpicu     INT NOT NULL,
    benar       INT NOT NULL,
    PRIMARY KEY (evaluasi_id, rule_kode),
    CONSTRAINT fk_erule_evaluasi FOREIGN KEY (evaluasi_id) REFERENCES evaluasi (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Contoh baris yang salah prediksi / tidak tercakup (dibatasi saat disimpan)
CREATE TABLE evaluasi_baris (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    evaluasi_id BIGINT NOT NULL,
    baris       INT NOT NULL,
    metode      VARCHAR(10) NOT NULL,
    aktual      TINYINT NOT NULL,
    prediksi    TINYINT NULL,
    cf          DECIMAL(10, 8) NOT NULL,
    rule_kode   VARCHAR(100) NULL,
    data        VARCHAR(255) NOT NULL,
    CONSTRAINT fk_ebaris_evaluasi FOREIGN KEY (evaluasi_id) REFERENCES evaluasi (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
