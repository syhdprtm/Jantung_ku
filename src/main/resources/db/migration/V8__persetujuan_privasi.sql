-- Waktu pengguna menyetujui kebijakan privasi & pernyataan bahwa hasil bukan diagnosis dokter.
-- NULL untuk akun lama / akun yang dibuat administrator.
ALTER TABLE users ADD COLUMN setuju_privasi_at DATETIME NULL AFTER role;
