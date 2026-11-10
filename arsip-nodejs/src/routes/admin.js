'use strict';

const express = require('express');
const { requireRole, hashPassword } = require('../auth');
const { transaction, getGejalaDenganBobot } = require('../db');
const { parseCf, validCf } = require('../format');

const isUnique = (err) => /UNIQUE constraint failed/i.test(String(err && err.message));
const str = (v) => String(v ?? '').trim();
const KODE_RE = /^[A-Z0-9_.-]{1,15}$/;
const cekKode = (kode, label, errors) => {
  if (!kode) errors.push(`Kode ${label} wajib diisi.`);
  else if (!KODE_RE.test(kode)) errors.push(`Kode ${label} hanya boleh huruf, angka, titik, - atau _ (maks. 15 karakter).`);
};

module.exports = function adminRoutes(db) {
  const router = express.Router();
  router.use(requireRole('admin'));

  const flash = (req, type, msg) => {
    req.session.flash = { type, msg };
  };
  const notFound = (res) =>
    res.status(404).render('error', { title: 'Tidak Ditemukan', message: 'Data tidak ditemukan.' });
  const allPenyakit = () => db.prepare('SELECT * FROM penyakit ORDER BY kode').all();

  // ================= DASHBOARD =================
  router.get('/', (req, res) => {
    const count = (sql) => db.prepare(sql).get().n;
    const stat = {
      user: count("SELECT COUNT(*) AS n FROM users WHERE role = 'user'"),
      admin: count("SELECT COUNT(*) AS n FROM users WHERE role = 'admin'"),
      gejala: count('SELECT COUNT(*) AS n FROM gejala'),
      penyakit: count('SELECT COUNT(*) AS n FROM penyakit'),
      rules: count('SELECT COUNT(*) AS n FROM rules'),
      diagnosa: count('SELECT COUNT(*) AS n FROM diagnosa'),
    };
    const sebaran = db
      .prepare(
        `SELECT COALESCE(hasil_penyakit, 'Tidak dapat disimpulkan') AS nama, COUNT(*) AS n
         FROM diagnosa GROUP BY hasil_penyakit ORDER BY n DESC`,
      )
      .all();
    const terakhir = db
      .prepare(
        `SELECT d.*, u.nama AS nama_user FROM diagnosa d JOIN users u ON u.id = d.user_id
         ORDER BY d.id DESC LIMIT 5`,
      )
      .all();
    res.render('admin/dashboard', { title: 'Dashboard Admin', stat, sebaran, terakhir });
  });

  // ================= GEJALA + BOBOT CF PAKAR =================
  router.get('/gejala', (req, res) => {
    res.render('admin/gejala', { title: 'Data Gejala', gejala: getGejalaDenganBobot(db), penyakit: allPenyakit() });
  });

  function readGejala(req, penyakit) {
    const form = { kode: str(req.body.kode).toUpperCase(), nama: str(req.body.nama), keterangan: str(req.body.keterangan), bobot: {} };
    const errors = [];
    cekKode(form.kode, 'gejala', errors);
    if (!form.nama) errors.push('Nama gejala wajib diisi.');
    for (const p of penyakit) {
      const raw = req.body[`cf_${p.id}`];
      const n = parseCf(raw);
      form.bobot[p.id] = Number.isNaN(n) ? raw : n;
      if (!validCf(n)) errors.push(`CF Pakar untuk ${p.kode} harus angka 0 sampai 1.`);
    }
    return { form, errors };
  }

  function simpanBobot(gejalaId, bobot) {
    db.prepare('DELETE FROM bobot WHERE gejala_id = ?').run(gejalaId);
    const ins = db.prepare('INSERT INTO bobot (gejala_id, penyakit_id, cf_pakar) VALUES (?, ?, ?)');
    for (const [pid, cf] of Object.entries(bobot)) if (cf > 0) ins.run(gejalaId, Number(pid), cf);
  }

  router.get('/gejala/tambah', (req, res) => {
    res.render('admin/gejala_form', { title: 'Tambah Gejala', form: { bobot: {} }, penyakit: allPenyakit(), errors: [], action: '/admin/gejala/tambah' });
  });

  router.post('/gejala/tambah', (req, res) => {
    const penyakit = allPenyakit();
    const { form, errors } = readGejala(req, penyakit);
    if (!errors.length) {
      try {
        transaction(db, () => {
          const id = db.prepare('INSERT INTO gejala (kode, nama, keterangan) VALUES (?, ?, ?)').run(form.kode, form.nama, form.keterangan).lastInsertRowid;
          simpanBobot(id, form.bobot);
        });
        flash(req, 'success', `Gejala ${form.kode} berhasil ditambahkan.`);
        return res.redirect('/admin/gejala');
      } catch (err) {
        if (!isUnique(err)) throw err;
        errors.push(`Kode ${form.kode} sudah digunakan.`);
      }
    }
    res.status(400).render('admin/gejala_form', { title: 'Tambah Gejala', form, penyakit, errors, action: '/admin/gejala/tambah' });
  });

  router.get('/gejala/:id/edit', (req, res) => {
    const g = getGejalaDenganBobot(db).find((x) => x.id === Number(req.params.id));
    if (!g) return notFound(res);
    res.render('admin/gejala_form', { title: `Edit Gejala ${g.kode}`, form: g, penyakit: allPenyakit(), errors: [], action: `/admin/gejala/${g.id}/edit` });
  });

  router.post('/gejala/:id/edit', (req, res) => {
    const id = Number(req.params.id);
    if (!db.prepare('SELECT 1 FROM gejala WHERE id = ?').get(id)) return notFound(res);
    const penyakit = allPenyakit();
    const { form, errors } = readGejala(req, penyakit);
    if (!errors.length) {
      try {
        transaction(db, () => {
          db.prepare('UPDATE gejala SET kode = ?, nama = ?, keterangan = ? WHERE id = ?').run(form.kode, form.nama, form.keterangan, id);
          simpanBobot(id, form.bobot);
        });
        flash(req, 'success', `Gejala ${form.kode} berhasil diperbarui.`);
        return res.redirect('/admin/gejala');
      } catch (err) {
        if (!isUnique(err)) throw err;
        errors.push(`Kode ${form.kode} sudah digunakan.`);
      }
    }
    res.status(400).render('admin/gejala_form', { title: 'Edit Gejala', form, penyakit, errors, action: `/admin/gejala/${id}/edit` });
  });

  router.post('/gejala/:id/hapus', (req, res) => {
    const r = db.prepare('DELETE FROM gejala WHERE id = ?').run(Number(req.params.id));
    flash(req, r.changes ? 'success' : 'error', r.changes ? 'Gejala dihapus.' : 'Data tidak ditemukan.');
    res.redirect('/admin/gejala');
  });

  // ================= PENYAKIT / HIPOTESIS =================
  router.get('/penyakit', (req, res) => {
    const penyakit = db
      .prepare(
        `SELECT p.*, (SELECT COUNT(*) FROM bobot b WHERE b.penyakit_id = p.id) AS jml_gejala,
                (SELECT COUNT(*) FROM rules r WHERE r.penyakit_id = p.id) AS jml_rule
         FROM penyakit p ORDER BY p.kode`,
      )
      .all();
    res.render('admin/penyakit', { title: 'Data Penyakit', penyakit });
  });

  function readPenyakit(req) {
    const form = { kode: str(req.body.kode).toUpperCase(), nama: str(req.body.nama), keterangan: str(req.body.keterangan), saran: str(req.body.saran) };
    const errors = [];
    cekKode(form.kode, 'penyakit', errors);
    if (!form.nama) errors.push('Nama wajib diisi.');
    return { form, errors };
  }

  router.get('/penyakit/tambah', (req, res) => {
    res.render('admin/penyakit_form', { title: 'Tambah Penyakit', form: {}, errors: [], action: '/admin/penyakit/tambah' });
  });

  router.post('/penyakit/tambah', (req, res) => {
    const { form, errors } = readPenyakit(req);
    if (!errors.length) {
      try {
        db.prepare('INSERT INTO penyakit (kode, nama, keterangan, saran) VALUES (?, ?, ?, ?)').run(form.kode, form.nama, form.keterangan, form.saran);
        flash(req, 'success', `Penyakit ${form.kode} ditambahkan. Atur bobot CF pakarnya di menu Gejala.`);
        return res.redirect('/admin/penyakit');
      } catch (err) {
        if (!isUnique(err)) throw err;
        errors.push(`Kode ${form.kode} sudah digunakan.`);
      }
    }
    res.status(400).render('admin/penyakit_form', { title: 'Tambah Penyakit', form, errors, action: '/admin/penyakit/tambah' });
  });

  router.get('/penyakit/:id/edit', (req, res) => {
    const p = db.prepare('SELECT * FROM penyakit WHERE id = ?').get(Number(req.params.id));
    if (!p) return notFound(res);
    res.render('admin/penyakit_form', { title: `Edit ${p.kode}`, form: p, errors: [], action: `/admin/penyakit/${p.id}/edit` });
  });

  router.post('/penyakit/:id/edit', (req, res) => {
    const id = Number(req.params.id);
    if (!db.prepare('SELECT 1 FROM penyakit WHERE id = ?').get(id)) return notFound(res);
    const { form, errors } = readPenyakit(req);
    if (!errors.length) {
      try {
        db.prepare('UPDATE penyakit SET kode = ?, nama = ?, keterangan = ?, saran = ? WHERE id = ?').run(form.kode, form.nama, form.keterangan, form.saran, id);
        flash(req, 'success', `Penyakit ${form.kode} diperbarui.`);
        return res.redirect('/admin/penyakit');
      } catch (err) {
        if (!isUnique(err)) throw err;
        errors.push(`Kode ${form.kode} sudah digunakan.`);
      }
    }
    res.status(400).render('admin/penyakit_form', { title: 'Edit Penyakit', form, errors, action: `/admin/penyakit/${id}/edit` });
  });

  router.post('/penyakit/:id/hapus', (req, res) => {
    const r = db.prepare('DELETE FROM penyakit WHERE id = ?').run(Number(req.params.id));
    flash(req, r.changes ? 'success' : 'error', r.changes ? 'Penyakit dihapus.' : 'Data tidak ditemukan.');
    res.redirect('/admin/penyakit');
  });

  // ================= RULE BASE =================
  router.get('/rules', (req, res) => {
    const rules = db
      .prepare(
        `SELECT r.*, p.kode AS kode_penyakit, p.nama AS nama_penyakit FROM rules r
         LEFT JOIN penyakit p ON p.id = r.penyakit_id ORDER BY r.id`,
      )
      .all();
    res.render('admin/rules', { title: 'Rule Base', rules });
  });

  function readRule(req) {
    const cf = parseCf(req.body.cf);
    const pid = Number(req.body.penyakit_id);
    const form = { kode: str(req.body.kode).toUpperCase(), kondisi: str(req.body.kondisi), penyakit_id: pid, cf: Number.isNaN(cf) ? req.body.cf : cf, keterangan: str(req.body.keterangan) };
    const errors = [];
    cekKode(form.kode, 'rule', errors);
    if (!form.kondisi) errors.push('Kondisi (IF) wajib diisi.');
    if (!db.prepare('SELECT 1 FROM penyakit WHERE id = ?').get(pid)) errors.push('Pilih kesimpulan (THEN).');
    if (!validCf(cf)) errors.push('CF Rule harus angka 0 sampai 1.');
    return { form, errors };
  }

  router.get('/rules/tambah', (req, res) => {
    res.render('admin/rule_form', { title: 'Tambah Rule', form: {}, penyakit: allPenyakit(), errors: [], action: '/admin/rules/tambah' });
  });

  router.post('/rules/tambah', (req, res) => {
    const { form, errors } = readRule(req);
    if (!errors.length) {
      try {
        db.prepare('INSERT INTO rules (kode, kondisi, penyakit_id, cf, keterangan) VALUES (?, ?, ?, ?, ?)').run(form.kode, form.kondisi, form.penyakit_id, form.cf, form.keterangan);
        flash(req, 'success', `Rule ${form.kode} ditambahkan.`);
        return res.redirect('/admin/rules');
      } catch (err) {
        if (!isUnique(err)) throw err;
        errors.push(`Kode ${form.kode} sudah digunakan.`);
      }
    }
    res.status(400).render('admin/rule_form', { title: 'Tambah Rule', form, penyakit: allPenyakit(), errors, action: '/admin/rules/tambah' });
  });

  router.get('/rules/:id/edit', (req, res) => {
    const r = db.prepare('SELECT * FROM rules WHERE id = ?').get(Number(req.params.id));
    if (!r) return notFound(res);
    res.render('admin/rule_form', { title: `Edit Rule ${r.kode}`, form: r, penyakit: allPenyakit(), errors: [], action: `/admin/rules/${r.id}/edit` });
  });

  router.post('/rules/:id/edit', (req, res) => {
    const id = Number(req.params.id);
    if (!db.prepare('SELECT 1 FROM rules WHERE id = ?').get(id)) return notFound(res);
    const { form, errors } = readRule(req);
    if (!errors.length) {
      try {
        db.prepare('UPDATE rules SET kode = ?, kondisi = ?, penyakit_id = ?, cf = ?, keterangan = ? WHERE id = ?').run(form.kode, form.kondisi, form.penyakit_id, form.cf, form.keterangan, id);
        flash(req, 'success', `Rule ${form.kode} diperbarui.`);
        return res.redirect('/admin/rules');
      } catch (err) {
        if (!isUnique(err)) throw err;
        errors.push(`Kode ${form.kode} sudah digunakan.`);
      }
    }
    res.status(400).render('admin/rule_form', { title: 'Edit Rule', form, penyakit: allPenyakit(), errors, action: `/admin/rules/${id}/edit` });
  });

  router.post('/rules/:id/hapus', (req, res) => {
    const r = db.prepare('DELETE FROM rules WHERE id = ?').run(Number(req.params.id));
    flash(req, r.changes ? 'success' : 'error', r.changes ? 'Rule dihapus.' : 'Data tidak ditemukan.');
    res.redirect('/admin/rules');
  });

  // ================= MANAJEMEN USER =================
  router.get('/users', (req, res) => {
    const users = db
      .prepare(
        `SELECT u.id, u.nama, u.username, u.role, u.created_at,
                (SELECT COUNT(*) FROM diagnosa d WHERE d.user_id = u.id) AS jml_diagnosa
         FROM users u ORDER BY u.role, u.nama`,
      )
      .all();
    res.render('admin/users', { title: 'Manajemen User', users });
  });

  function readUser(req, { isNew, selfId, targetId }) {
    const form = { nama: str(req.body.nama), username: str(req.body.username), role: req.body.role === 'admin' ? 'admin' : 'user' };
    const password = String(req.body.password || '');
    const errors = [];
    if (!form.nama) errors.push('Nama wajib diisi.');
    if (!/^[A-Za-z0-9_.]{3,30}$/.test(form.username)) errors.push('Username 3-30 karakter (huruf, angka, titik, garis bawah).');
    if (isNew && password.length < 6) errors.push('Password minimal 6 karakter.');
    if (!isNew && password && password.length < 6) errors.push('Password baru minimal 6 karakter.');
    if (!isNew && targetId === selfId && form.role !== 'admin') errors.push('Anda tidak dapat menurunkan role akun Anda sendiri.');
    return { form, password, errors };
  }

  router.get('/users/tambah', (req, res) => {
    res.render('admin/user_form', { title: 'Tambah User', form: { role: 'user' }, errors: [], isNew: true, action: '/admin/users/tambah' });
  });

  router.post('/users/tambah', (req, res) => {
    const { form, password, errors } = readUser(req, { isNew: true });
    if (!errors.length) {
      try {
        db.prepare('INSERT INTO users (nama, username, password_hash, role) VALUES (?, ?, ?, ?)').run(form.nama, form.username, hashPassword(password), form.role);
        flash(req, 'success', `User ${form.username} ditambahkan.`);
        return res.redirect('/admin/users');
      } catch (err) {
        if (!isUnique(err)) throw err;
        errors.push('Username sudah dipakai.');
      }
    }
    res.status(400).render('admin/user_form', { title: 'Tambah User', form, errors, isNew: true, action: '/admin/users/tambah' });
  });

  router.get('/users/:id/edit', (req, res) => {
    const u = db.prepare('SELECT id, nama, username, role FROM users WHERE id = ?').get(Number(req.params.id));
    if (!u) return notFound(res);
    res.render('admin/user_form', { title: `Edit User ${u.username}`, form: u, errors: [], isNew: false, action: `/admin/users/${u.id}/edit` });
  });

  router.post('/users/:id/edit', (req, res) => {
    const id = Number(req.params.id);
    if (!db.prepare('SELECT 1 FROM users WHERE id = ?').get(id)) return notFound(res);
    const { form, password, errors } = readUser(req, { isNew: false, selfId: req.session.user.id, targetId: id });
    if (!errors.length) {
      try {
        db.prepare('UPDATE users SET nama = ?, username = ?, role = ? WHERE id = ?').run(form.nama, form.username, form.role, id);
        if (password) db.prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(hashPassword(password), id);
        if (id === req.session.user.id) Object.assign(req.session.user, { nama: form.nama, username: form.username });
        flash(req, 'success', `User ${form.username} diperbarui.`);
        return res.redirect('/admin/users');
      } catch (err) {
        if (!isUnique(err)) throw err;
        errors.push('Username sudah dipakai.');
      }
    }
    res.status(400).render('admin/user_form', { title: 'Edit User', form: { ...form, id }, errors, isNew: false, action: `/admin/users/${id}/edit` });
  });

  router.post('/users/:id/hapus', (req, res) => {
    const id = Number(req.params.id);
    if (id === req.session.user.id) {
      flash(req, 'error', 'Anda tidak dapat menghapus akun Anda sendiri.');
      return res.redirect('/admin/users');
    }
    const r = db.prepare('DELETE FROM users WHERE id = ?').run(id);
    flash(req, r.changes ? 'success' : 'error', r.changes ? 'User dihapus beserta riwayat diagnosanya.' : 'Data tidak ditemukan.');
    res.redirect('/admin/users');
  });

  // ================= RIWAYAT SEMUA DIAGNOSA =================
  router.get('/riwayat', (req, res) => {
    const rows = db
      .prepare(
        `SELECT d.*, u.nama AS nama_user, u.username FROM diagnosa d
         JOIN users u ON u.id = d.user_id ORDER BY d.id DESC`,
      )
      .all();
    res.render('riwayat', { title: 'Riwayat Diagnosa (Semua User)', rows, adminView: true });
  });

  router.post('/riwayat/:id/hapus', (req, res) => {
    const r = db.prepare('DELETE FROM diagnosa WHERE id = ?').run(Number(req.params.id));
    flash(req, r.changes ? 'success' : 'error', r.changes ? 'Riwayat diagnosa dihapus.' : 'Data tidak ditemukan.');
    res.redirect('/admin/riwayat');
  });

  return router;
};
