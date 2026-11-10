'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { openDb } = require('../src/db');
const { createApp } = require('../src/app');
const { ADMIN_DEFAULT } = require('../src/seed');

let server;
let base;
let db;

test.before(async () => {
  db = openDb(':memory:');
  const app = createApp(db, { sessionSecret: 'test-secret' });
  await new Promise((resolve) => {
    server = app.listen(0, resolve);
  });
  base = `http://127.0.0.1:${server.address().port}`;
});

test.after(() => server.close());

/** Klien kecil dengan cookie jar sederhana. */
function client() {
  let cookie = '';
  return async function req(method, path, form) {
    const res = await fetch(base + path, {
      method,
      redirect: 'manual',
      headers: {
        ...(cookie ? { cookie } : {}),
        ...(form ? { 'content-type': 'application/x-www-form-urlencoded' } : {}),
      },
      body: form ? new URLSearchParams(form).toString() : undefined,
    });
    const set = res.headers.get('set-cookie');
    if (set) cookie = set.split(';')[0];
    return { status: res.status, location: res.headers.get('location'), text: await res.text() };
  };
}

async function login(username, password) {
  const c = client();
  const r = await c('POST', '/login', { username, password });
  return { c, r };
}

test('tamu diarahkan ke login saat membuka halaman terproteksi', async () => {
  const c = client();
  const r = await c('GET', '/diagnosa');
  assert.equal(r.status, 302);
  assert.equal(r.location, '/login');
  const r2 = await c('GET', '/admin');
  assert.equal(r2.status, 302);
});

test('login admin -> dashboard admin', async () => {
  const { c, r } = await login(ADMIN_DEFAULT.username, ADMIN_DEFAULT.password);
  assert.equal(r.status, 302);
  assert.equal(r.location, '/admin');
  const dash = await c('GET', '/admin');
  assert.equal(dash.status, 200);
  assert.match(dash.text, /Dashboard Administrator/);
  const rules = await c('GET', '/admin/rules');
  assert.match(rules.text, /R49/);
});

test('password salah ditolak', async () => {
  const { r } = await login(ADMIN_DEFAULT.username, 'salah-password');
  assert.equal(r.status, 401);
});

test('registrasi user, diagnosa, dan pembatasan akses role', async () => {
  const c = client();
  const reg = await c('POST', '/register', { nama: 'Budi', username: 'budi', password: 'rahasia1', konfirmasi: 'rahasia1' });
  assert.equal(reg.status, 302);

  const lg = await c('POST', '/login', { username: 'budi', password: 'rahasia1' });
  assert.equal(lg.location, '/dashboard');

  // user tidak boleh akses halaman admin
  const adm = await c('GET', '/admin/users');
  assert.equal(adm.status, 403);

  // isi diagnosa sesuai contoh laporan
  const g = Object.fromEntries(db.prepare('SELECT kode, id FROM gejala').all().map((x) => [x.kode, x.id]));
  const form = { nama_pasien: 'Budi', umur: '50' };
  const cfUser = { D01: 0.6, D02: 0.4, D03: 0.6, D04: 0.8, D05: 0.4, D06: 0.6, D07: 0.6, D08: 0.4 };
  for (const [kode, v] of Object.entries(cfUser)) form[`g_${g[kode]}`] = String(v);
  const post = await c('POST', '/diagnosa', form);
  assert.equal(post.status, 302);
  assert.match(post.location, /^\/hasil\/\d+$/);

  const hasil = await c('GET', post.location);
  assert.equal(hasil.status, 200);
  assert.match(hasil.text, /98,33%/);
  assert.match(hasil.text, /0\.98329956/);

  // user lain tidak boleh melihat hasil milik Budi
  const c2 = client();
  await c2('POST', '/register', { nama: 'Ani', username: 'ani', password: 'rahasia2', konfirmasi: 'rahasia2' });
  await c2('POST', '/login', { username: 'ani', password: 'rahasia2' });
  const intip = await c2('GET', post.location);
  assert.equal(intip.status, 404);

  // admin boleh melihat semua riwayat
  const { c: ca } = await login(ADMIN_DEFAULT.username, ADMIN_DEFAULT.password);
  const lihat = await ca('GET', post.location);
  assert.equal(lihat.status, 200);
  const riwayat = await ca('GET', '/admin/riwayat');
  assert.match(riwayat.text, /budi/);
});

test('registrasi publik tidak bisa membuat admin', async () => {
  const c = client();
  await c('POST', '/register', { nama: 'X', username: 'xadmin', password: 'rahasia1', konfirmasi: 'rahasia1', role: 'admin' });
  const row = db.prepare('SELECT role FROM users WHERE username = ?').get('xadmin');
  assert.equal(row.role, 'user');
});

test('admin dapat menambah gejala dengan bobot CF', async () => {
  const { c } = await login(ADMIN_DEFAULT.username, ADMIN_DEFAULT.password);
  const p = Object.fromEntries(db.prepare('SELECT kode, id FROM penyakit').all().map((x) => [x.kode, x.id]));
  const r = await c('POST', '/admin/gejala/tambah', {
    kode: 'D14', nama: 'Nyeri dada (Chest Pain ASY)', keterangan: '', [`cf_${p.P01}`]: '0,6', [`cf_${p.P02}`]: '0',
  });
  assert.equal(r.status, 302);
  const b = db.prepare("SELECT b.cf_pakar FROM bobot b JOIN gejala g ON g.id = b.gejala_id WHERE g.kode = 'D14'").all();
  assert.deepEqual(b.map((x) => x.cf_pakar), [0.6]);

  const bad = await c('POST', '/admin/gejala/tambah', { kode: 'D15', nama: 'X', [`cf_${p.P01}`]: '1.5' });
  assert.equal(bad.status, 400);
});
