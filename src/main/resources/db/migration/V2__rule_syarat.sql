-- Kondisi IF setiap rule dalam bentuk terstruktur agar bisa dieksekusi mesin inferensi.
-- Contoh: R1 "ST Slope Down AND Max HR > 185" menjadi 2 baris:
--   (ST_SLOPE, =, Down) dan (MAX_HR, >, 185)
CREATE TABLE rule_syarat (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id  BIGINT      NOT NULL,
    urutan   INT         NOT NULL,
    atribut  VARCHAR(20) NOT NULL,
    operator VARCHAR(2)  NOT NULL,
    nilai    VARCHAR(20) NOT NULL,
    CONSTRAINT fk_syarat_rule FOREIGN KEY (rule_id) REFERENCES rules (id) ON DELETE CASCADE,
    INDEX idx_syarat_rule (rule_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
