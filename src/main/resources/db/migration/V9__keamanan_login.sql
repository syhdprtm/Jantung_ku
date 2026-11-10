-- Pembatasan percobaan login: jumlah gagal berturut-turut dan batas waktu kunci sementara.
ALTER TABLE users
    ADD COLUMN gagal_login INT NOT NULL DEFAULT 0 AFTER setuju_privasi_at,
    ADD COLUMN terkunci_sampai DATETIME NULL AFTER gagal_login;

-- Aktivitas login ikut diaudit (entitas SESI: MASUK, GAGAL_MASUK, KELUAR, TERKUNCI).
ALTER TABLE audit_log MODIFY aksi VARCHAR(20) NOT NULL;
