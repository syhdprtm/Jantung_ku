'use strict';

const express = require('express');
const { requireLogin, hashPassword, verifyPassword } = require('../auth');
const { hitungCF, CF_USER_OPTIONS } = require('../cf');
const { getGejalaDenganBobot } = require('../db');

const NILAI_VALID = new Set(CF_USER_OPTIONS.map((o) => o.value));

module.exports = function userRoutes(db) {
  const router = express.Router();

  router.get('/dashboard', requireLogin, (req, res) => {
    const uid = req.session.user.id;
    const jumlah = db.prepare('SELECT COUNT(*) AS n FROM diagnosa WHERE user_id = ?').get(uid).n;
    const terakhir = db
      .prepare('SELECT * FROM diagnosa WHERE user_id = ? ORDER BY id DESC LIMIT 5')
      .all(uid);
    res.render('user/dashboard', { title: 'Dashboard', jumlah, terakhir });
  });

  // ---------- Diagnosa ----------
  router.get('/diagnosa', requireLogin, (req, res) => {
    const gejala = getGejalaDenganBobot(db);
    res.render('user/diagnosa', {
      title: 'Diagnosa',
      gejala,
      form: { nama_pasien: req.session.user.nama, umur: '' },
      jawaban: {},
      errors: [],
    });
  });

  router.post('/diagnosa', requireLogin, (req, res) => {
    const penyakit = db.prepare('SELECT * FROM penyakit ORDER BY kode').all();
    const gejala = getGejalaDenganBobot(db);
    const form = {
      nama_pasien: String(req.body.nama_pasien || '').trim(),
      umur: String(req.body.umur || '').trim(),
    };
    const errors = [];
    const jawaban = {};
    for (const g of gejala) {
      const v = Number(req.body[`g_${g.id}`] || 0);
      if (!NILAI_VALID.has(v)) {
        errors.push(`Nilai keyakinan untuk ${g.kode} tidak valid.`);
        continue;
      }
      jawaban[g.id] = v;
    }
    if (!form.nama_pasien) errors.push('Nama pasien wajib diisi.');
    let umur = null;
    if (form.umur !== '') {
      umur = Number(form.umur);
      if (!Number.isInteger(umur) || umur < 0 || umur > 130) errors.push('Umur tidak valid.');
    }
    if (!errors.length && !Object.values(jawaban).some((v) => v > 0))
      errors.push('Pilih tingkat keyakinan minimal pada satu gejala.');
    if (errors.length) {
      return res.status(400).render('user/diagnosa', { title: 'Diagnosa', gejala, form, jawaban, errors });
    }

    const hasil = hitungCF(penyakit, gejala, jawaban);
    const info = db
      .prepare(
        'INSERT INTO diagnosa (user_id, nama_pasien, umur, hasil_penyakit, hasil_cf, detail) VALUES (?, ?, ?, ?, ?, ?)',
      )
      .run(
        req.session.user.id,
        form.nama_pasien,
        umur,
        hasil.kesimpulan ? hasil.kesimpulan.nama : null,
        hasil.kesimpulan ? hasil.kesimpulan.cf : 0,
        JSON.stringify(hasil),
      );
    res.redirect(`/hasil/${info.lastInsertRowid}`);
  });

  router.get('/hasil/:id', requireLogin, (req, res) => {
    const row = db
      .prepare(
        `SELECT d.*, u.nama AS nama_user, u.username FROM diagnosa d
         JOIN users u ON u.id = d.user_id WHERE d.id = ?`,
      )
      .get(Number(req.params.id));
    const user = req.session.user;
    // User hanya boleh melihat riwayatnya sendiri; admin boleh melihat semua.
    if (!row || (user.role !== 'admin' && row.user_id !== user.id)) {
      return res.status(404).render('error', { title: 'Tidak Ditemukan', message: 'Data diagnosa tidak ditemukan.' });
    }
    res.render('hasil', { title: 'Hasil Diagnosa', d: row, hasil: JSON.parse(row.detail) });
  });

  // ---------- Riwayat ----------
  router.get('/riwayat', requireLogin, (req, res) => {
    const rows = db
      .prepare('SELECT * FROM diagnosa WHERE user_id = ? ORDER BY id DESC')
      .all(req.session.user.id);
    res.render('riwayat', { title: 'Riwayat Diagnosa', rows, adminView: false });
  });

  router.post('/riwayat/:id/hapus', requireLogin, (req, res) => {
    const r = db
      .prepare('DELETE FROM diagnosa WHERE id = ? AND user_id = ?')
      .run(Number(req.params.id), req.session.user.id);
    req.session.flash = r.changes
      ? { type: 'success', msg: 'Riwayat diagnosa dihapus.' }
      : { type: 'error', msg: 'Data tidak ditemukan.' };
    res.redirect('/riwayat');
  });

  // ---------- Profil ----------
  router.get('/profil', requireLogin, (req, res) => {
    const u = db.prepare('SELECT id, nama, username, role FROM users WHERE id = ?').get(req.session.user.id);
    res.render('user/profil', { title: 'Profil', form: u, errors: [] });
  });

  router.post('/profil', requireLogin, (req, res) => {
    const u = db.prepare('SELECT * FROM users WHERE id = ?').get(req.session.user.id);
    const nama = String(req.body.nama || '').trim();
    const lama = String(req.body.password_lama || '');
    const baru = String(req.body.password_baru || '');
    const konfirmasi = String(req.body.konfirmasi || '');
    const errors = [];
    if (!nama) errors.push('Nama wajib diisi.');
    if (baru || lama) {
      if (!verifyPassword(lama, u.password_hash)) errors.push('Password lama salah.');
      if (baru.length < 6) errors.push('Password baru minimal 6 karakter.');
      if (baru !== konfirmasi) errors.push('Konfirmasi password baru tidak sama.');
    }
    if (errors.length) {
      return res.status(400).render('user/profil', {
        title: 'Profil',
        form: { nama, username: u.username, role: u.role },
        errors,
      });
    }
    db.prepare('UPDATE users SET nama = ? WHERE id = ?').run(nama, u.id);
    if (baru) db.prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(hashPassword(baru), u.id);
    req.session.user.nama = nama;
    req.session.flash = { type: 'success', msg: 'Profil berhasil diperbarui.' };
    res.redirect('/profil');
  });

  return router;
};
