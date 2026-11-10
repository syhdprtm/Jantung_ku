-- Data diri & pemeriksaan fisik pasien ala formulir Medical Check Up (MCU).
-- Semua kolom NULL agar riwayat lama tetap valid; umur tetap disimpan (dihitung dari tanggal lahir).
ALTER TABLE diagnosa
    ADD COLUMN jenis_kelamin CHAR(1)      NULL AFTER umur,          -- L / P
    ADD COLUMN tanggal_lahir DATE         NULL AFTER jenis_kelamin,
    ADD COLUMN alamat        VARCHAR(255) NULL AFTER tanggal_lahir,
    ADD COLUMN telepon       VARCHAR(20)  NULL AFTER alamat,
    ADD COLUMN tinggi_cm     DECIMAL(5,1) NULL AFTER telepon,
    ADD COLUMN berat_kg      DECIMAL(5,1) NULL AFTER tinggi_cm,
    ADD COLUMN sistolik      SMALLINT     NULL AFTER berat_kg,      -- mmHg
    ADD COLUMN diastolik     SMALLINT     NULL AFTER sistolik;      -- mmHg
