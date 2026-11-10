'use strict';

/** Format angka CF (maks. 8 desimal, tanpa nol berlebih). */
function fmt(x) {
  if (x === null || x === undefined || Number.isNaN(Number(x))) return '-';
  return String(Number(Number(x).toFixed(8)));
}

/** Format persen gaya Indonesia, mis. 0.98329956 -> "98,33%". */
function persen(cf) {
  return (
    (Number(cf) * 100).toLocaleString('id-ID', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + '%'
  );
}

/** Parse nilai CF dari input form (menerima koma atau titik). Kosong = 0. */
function parseCf(value) {
  const s = String(value ?? '').trim().replace(',', '.');
  if (s === '') return 0;
  const n = Number(s);
  return Number.isFinite(n) ? n : NaN;
}

function validCf(n) {
  return Number.isFinite(n) && n >= 0 && n <= 1;
}

module.exports = { fmt, persen, parseCf, validCf };
