'use strict';

const express = require('express');
const { hashPassword, verifyPassword } = require('../auth');

function homeFor(user) {
  return user.role === 'admin' ? '/admin' : '/dashboard';
}

module.exports = function authRoutes(db) {
  const router = express.Router();

  router.get('/', (req, res) => {
    if (req.session.user) return res.redirect(homeFor(req.session.user));
    const stat = {
      gejala: db.prepare('SELECT COUNT(*) AS n FROM gejala').get().n,
      penyakit: db.prepare('SELECT COUNT(*) AS n FROM penyakit').get().n,
      rules: db.prepare('SELECT COUNT(*) AS n FROM rules').get().n,
    };
    res.render('index', { title: 'Beranda', stat });
  });

  router.get('/login', (req, res) => {
    if (req.session.user) return res.redirect(homeFor(req.session.user));
    res.render('login', { title: 'Login', form: {}, error: null });
  });

  router.post('/login', (req, res, next) => {
    const username = String(req.body.username || '').trim();
    const password = String(req.body.password || '');
    const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
    if (!user || !verifyPassword(password, user.password_hash)) {
      return res.status(401).render('login', {
        title: 'Login',
        form: { username },
        error: 'Username atau password salah.',
      });
    }
    req.session.regenerate((err) => {
      if (err) return next(err);
      req.session.user = { id: user.id, nama: user.nama, username: user.username, role: user.role };
      req.session.flash = { type: 'success', msg: `Selamat datang, ${user.nama}!` };
      res.redirect(homeFor(user));
    });
  });

  router.get('/register', (req, res) => {
    if (req.session.user) return res.redirect(homeFor(req.session.user));
    res.render('register', { title: 'Daftar Akun', form: {}, errors: [] });
  });

  router.post('/register', (req, res) => {
    const form = {
      nama: String(req.body.nama || '').trim(),
      username: String(req.body.username || '').trim(),
    };
    const password = String(req.body.password || '');
    const konfirmasi = String(req.body.konfirmasi || '');
    const errors = [];
    if (!form.nama) errors.push('Nama wajib diisi.');
    if (!/^[A-Za-z0-9_.]{3,30}$/.test(form.username))
      errors.push('Username 3-30 karakter (huruf, angka, titik, garis bawah).');
    if (password.length < 6) errors.push('Password minimal 6 karakter.');
    if (password !== konfirmasi) errors.push('Konfirmasi password tidak sama.');
    if (!errors.length && db.prepare('SELECT 1 FROM users WHERE username = ?').get(form.username))
      errors.push('Username sudah dipakai.');
    if (errors.length) return res.status(400).render('register', { title: 'Daftar Akun', form, errors });

    // Registrasi publik selalu ber-role "user"; akun admin hanya dibuat oleh admin.
    db.prepare("INSERT INTO users (nama, username, password_hash, role) VALUES (?, ?, ?, 'user')").run(
      form.nama,
      form.username,
      hashPassword(password),
    );
    req.session.flash = { type: 'success', msg: 'Registrasi berhasil. Silakan login.' };
    res.redirect('/login');
  });

  router.post('/logout', (req, res) => {
    req.session.destroy(() => {
      res.clearCookie('spcf.sid');
      res.redirect('/login');
    });
  });

  return router;
};
