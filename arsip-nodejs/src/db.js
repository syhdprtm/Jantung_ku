'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');
const { hashPassword } = require('./auth');
const seed = require('./seed');

const SCHEMA = `
CREATE TABLE IF NOT EXISTS users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  nama          TEXT NOT NULL,
  username      TEXT NOT NULL UNIQUE COLLATE NOCASE,
  password_hash TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('admin', 'user')),
  created_at    TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS penyakit (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  kode       TEXT NOT NULL UNIQUE,
  nama       TEXT NOT NULL,
  keterangan TEXT NOT NULL DEFAULT '',
  saran      TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS gejala (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  kode       TEXT NOT NULL UNIQUE,
  nama       TEXT NOT NULL,
  keterangan TEXT NOT NULL DEFAULT ''
);

-- Basis pengetahuan: bobot CF Pakar tiap gejala terhadap tiap penyakit/hipotesis
CREATE TABLE IF NOT EXISTS bobot (
  gejala_id   INTEGER NOT NULL REFERENCES gejala(id) ON DELETE CASCADE,
  penyakit_id INTEGER NOT NULL REFERENCES penyakit(id) ON DELETE CASCADE,
  cf_pakar    REAL NOT NULL CHECK (cf_pakar > 0 AND cf_pakar <= 1),
  PRIMARY KEY (gejala_id, penyakit_id)
);

-- Rule base hasil decision tree (Tabel 5.4)
CREATE TABLE IF NOT EXISTS rules (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  kode        TEXT NOT NULL UNIQUE,
  kondisi     TEXT NOT NULL,
  penyakit_id INTEGER REFERENCES penyakit(id) ON DELETE SET NULL,
  cf          REAL NOT NULL CHECK (cf >= 0 AND cf <= 1),
  keterangan  TEXT NOT NULL DEFAULT ''
);

-- Riwayat diagnosa; detail disimpan sebagai snapshot JSON agar hasil lama
-- tidak berubah ketika admin mengubah bobot di kemudian hari.
CREATE TABLE IF NOT EXISTS diagnosa (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id       INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  nama_pasien   TEXT NOT NULL,
  umur          INTEGER,
  hasil_penyakit TEXT,
  hasil_cf      REAL NOT NULL DEFAULT 0,
  detail        TEXT NOT NULL,
  created_at    TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);
`;

function transaction(db, fn) {
  db.exec('BEGIN');
  try {
    const out = fn();
    db.exec('COMMIT');
    return out;
  } catch (err) {
    db.exec('ROLLBACK');
    throw err;
  }
}

function seedIfEmpty(db) {
  const { n: jmlUser } = db.prepare('SELECT COUNT(*) AS n FROM users').get();
  if (jmlUser === 0) {
    db.prepare("INSERT INTO users (nama, username, password_hash, role) VALUES (?, ?, ?, 'admin')").run(
      seed.ADMIN_DEFAULT.nama,
      seed.ADMIN_DEFAULT.username,
      hashPassword(seed.ADMIN_DEFAULT.password),
    );
  }

  const { n: jmlPenyakit } = db.prepare('SELECT COUNT(*) AS n FROM penyakit').get();
  if (jmlPenyakit > 0) return;

  transaction(db, () => {
    const idPenyakit = {};
    const insP = db.prepare('INSERT INTO penyakit (kode, nama, keterangan, saran) VALUES (?, ?, ?, ?)');
    for (const p of seed.PENYAKIT) {
      idPenyakit[p.kode] = insP.run(p.kode, p.nama, p.keterangan, p.saran).lastInsertRowid;
    }

    const insG = db.prepare('INSERT INTO gejala (kode, nama, keterangan) VALUES (?, ?, ?)');
    const insB = db.prepare('INSERT INTO bobot (gejala_id, penyakit_id, cf_pakar) VALUES (?, ?, ?)');
    for (const g of seed.GEJALA) {
      const gid = insG.run(g.kode, g.nama, g.keterangan).lastInsertRowid;
      for (const [kodeP, cf] of Object.entries(g.bobot)) {
        if (cf > 0) insB.run(gid, idPenyakit[kodeP], cf);
      }
    }

    const insR = db.prepare('INSERT INTO rules (kode, kondisi, penyakit_id, cf, keterangan) VALUES (?, ?, ?, ?, ?)');
    for (const r of seed.RULES) {
      insR.run(r.kode, r.kondisi, idPenyakit[r.hasil], r.cf, r.keterangan);
    }
  });
}

function openDb(file) {
  if (file !== ':memory:') fs.mkdirSync(path.dirname(file), { recursive: true });
  const db = new DatabaseSync(file);
  db.exec('PRAGMA foreign_keys = ON;');
  db.exec(SCHEMA);
  seedIfEmpty(db);
  return db;
}

/** Ambil semua gejala beserta bobot CF pakar per penyakit: g.bobot[penyakitId] = cf */
function getGejalaDenganBobot(db) {
  const gejala = db.prepare('SELECT * FROM gejala ORDER BY kode').all();
  const bobot = db.prepare('SELECT * FROM bobot').all();
  const map = new Map(gejala.map((g) => [g.id, { ...g, bobot: {} }]));
  for (const b of bobot) {
    const g = map.get(b.gejala_id);
    if (g) g.bobot[b.penyakit_id] = b.cf_pakar;
  }
  return [...map.values()];
}

module.exports = { openDb, transaction, getGejalaDenganBobot };
