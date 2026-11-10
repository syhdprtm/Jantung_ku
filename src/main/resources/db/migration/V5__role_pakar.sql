-- Role ketiga: PAKAR (mengelola basis pengetahuan, validasi, dan evaluasi).
ALTER TABLE users MODIFY role ENUM('ADMIN', 'PAKAR', 'USER') NOT NULL DEFAULT 'USER';
