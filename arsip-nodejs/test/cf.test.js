'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { hitungCF, combine } = require('../src/cf');
const { openDb, getGejalaDenganBobot } = require('../src/db');

test('combine() rumus CFlama + CFbaru(1 - CFlama)', () => {
  assert.equal(Number(combine(0.24, 0.16).toFixed(8)), 0.3616);
  assert.equal(combine(0, 0.5), 0.5);
  assert.equal(combine(1, 0.3), 1);
});

test('hasil sama dengan perhitungan manual laporan (Tabel 5.5 & 5.6) = 0.98329956', () => {
  const db = openDb(':memory:');
  const penyakit = db.prepare('SELECT * FROM penyakit ORDER BY kode').all();
  const gejala = getGejalaDenganBobot(db);
  const id = (kode) => gejala.find((g) => g.kode === kode).id;

  // Tabel 5.5 CF User
  const jawaban = {
    [id('D01')]: 0.6,
    [id('D02')]: 0.4,
    [id('D03')]: 0.6,
    [id('D04')]: 0.8,
    [id('D05')]: 0.4,
    [id('D06')]: 0.6,
    [id('D07')]: 0.6,
    [id('D08')]: 0.4,
  };
  const out = hitungCF(penyakit, gejala, jawaban);
  const jantung = out.hasil.find((h) => h.kode === 'P01');

  // CF(User) x CF(Pakar) — kolom terakhir Tabel 5.6
  assert.deepEqual(
    jantung.langkah.map((l) => l.cfHE),
    [0.24, 0.16, 0.48, 0.64, 0.32, 0.48, 0.48, 0.24],
  );
  // CF gabungan bertahap sesuai uraian laporan
  assert.deepEqual(
    jantung.langkah.map((l) => l.cfHasil),
    [0.24, 0.3616, 0.668032, 0.88049152, 0.91873423, 0.9577418, 0.97802574, 0.98329956],
  );
  assert.equal(jantung.cf, 0.98329956);
  assert.equal(out.kesimpulan.kode, 'P01');

  const normal = out.hasil.find((h) => h.kode === 'P02');
  assert.equal(normal.cf, 0);
});

test('indikator normal mengarah ke hipotesis Normal', () => {
  const db = openDb(':memory:');
  const penyakit = db.prepare('SELECT * FROM penyakit ORDER BY kode').all();
  const gejala = getGejalaDenganBobot(db);
  const id = (kode) => gejala.find((g) => g.kode === kode).id;
  const out = hitungCF(penyakit, gejala, { [id('D13')]: 0.8, [id('D09')]: 0.6, [id('D01')]: 0.4 });
  assert.equal(out.kesimpulan.kode, 'P02');
  // 0.8*1.0 = 0.8 ; 0.6*0.6 = 0.36 -> 0.8 + 0.36*(0.2) = 0.872
  assert.equal(out.kesimpulan.cf, 0.872);
});

test('tanpa gejala -> tidak dapat disimpulkan', () => {
  const db = openDb(':memory:');
  const penyakit = db.prepare('SELECT * FROM penyakit ORDER BY kode').all();
  const out = hitungCF(penyakit, getGejalaDenganBobot(db), {});
  assert.equal(out.kesimpulan, null);
});

test('seed: 13 gejala, 2 hipotesis, 49 rule, 1 admin', () => {
  const db = openDb(':memory:');
  const n = (t) => db.prepare(`SELECT COUNT(*) AS n FROM ${t}`).get().n;
  assert.equal(n('gejala'), 13);
  assert.equal(n('penyakit'), 2);
  assert.equal(n('rules'), 49);
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM users WHERE role='admin'").get().n, 1);
});
