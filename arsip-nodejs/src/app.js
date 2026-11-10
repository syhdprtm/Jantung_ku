'use strict';

const path = require('node:path');
const crypto = require('node:crypto');
const express = require('express');
const session = require('express-session');
const { CF_USER_OPTIONS, CF_PAKAR_TERMS } = require('./cf');
const { fmt, persen } = require('./format');

function createApp(db, options = {}) {
  const app = express();

  app.set('view engine', 'ejs');
  app.set('views', path.join(__dirname, '..', 'views'));
  app.disable('x-powered-by');

  app.use(express.static(path.join(__dirname, '..', 'public')));
  app.use(express.urlencoded({ extended: false }));
  app.use(
    session({
      name: 'spcf.sid',
      secret: options.sessionSecret || process.env.SESSION_SECRET || crypto.randomBytes(32).toString('hex'),
      resave: false,
      saveUninitialized: false,
      cookie: { httpOnly: true, sameSite: 'lax', maxAge: 1000 * 60 * 60 * 8 },
    }),
  );

  // Variabel global untuk semua view
  app.use((req, res, next) => {
    res.locals.currentUser = req.session.user || null;
    res.locals.flash = req.session.flash || null;
    delete req.session.flash;
    res.locals.currentPath = req.path;
    res.locals.fmt = fmt;
    res.locals.persen = persen;
    res.locals.CF_USER_OPTIONS = CF_USER_OPTIONS;
    res.locals.CF_PAKAR_TERMS = CF_PAKAR_TERMS;
    next();
  });

  app.use(require('./routes/auth')(db));
  app.use(require('./routes/user')(db));
  app.use('/admin', require('./routes/admin')(db));

  app.use((req, res) => {
    res.status(404).render('error', { title: 'Tidak Ditemukan', message: 'Halaman yang Anda cari tidak ditemukan.' });
  });

  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    console.error(err);
    res.status(500).render('error', { title: 'Terjadi Kesalahan', message: 'Terjadi kesalahan pada server.' });
  });

  return app;
}

module.exports = { createApp };
