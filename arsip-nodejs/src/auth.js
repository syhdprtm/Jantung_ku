'use strict';

const crypto = require('node:crypto');

const KEYLEN = 64;

function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString('hex');
  const hash = crypto.scryptSync(String(password), salt, KEYLEN).toString('hex');
  return `scrypt$${salt}$${hash}`;
}

function verifyPassword(password, stored) {
  if (typeof stored !== 'string') return false;
  const [algo, salt, hash] = stored.split('$');
  if (algo !== 'scrypt' || !salt || !hash) return false;
  const expected = Buffer.from(hash, 'hex');
  const actual = crypto.scryptSync(String(password), salt, expected.length);
  return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
}

/** Wajib login. */
function requireLogin(req, res, next) {
  if (req.session.user) return next();
  req.session.flash = { type: 'error', msg: 'Silakan login terlebih dahulu.' };
  return res.redirect('/login');
}

/** Wajib role tertentu (mis. 'admin'). */
function requireRole(role) {
  return (req, res, next) => {
    if (!req.session.user) return requireLogin(req, res, next);
    if (req.session.user.role === role) return next();
    return res.status(403).render('error', {
      title: 'Akses Ditolak',
      message: 'Halaman ini hanya dapat diakses oleh ' + (role === 'admin' ? 'Administrator' : role) + '.',
    });
  };
}

module.exports = { hashPassword, verifyPassword, requireLogin, requireRole };
