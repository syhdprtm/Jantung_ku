'use strict';

/**
 * Mesin inferensi Certainty Factor (CF).
 *
 * Mengikuti Bab 5.2 laporan:
 *   1. CF(H,E)  = CF(User) x CF(Pakar)              -> CF tunggal tiap gejala
 *   2. CFcombine = CFlama + CFbaru x (1 - CFlama)    -> digabung berurutan
 * Hasil akhir dikonversi ke persen (CF x 100%).
 */

// Pilihan tingkat keyakinan user (CF User) yang ditampilkan pada form diagnosa.
const CF_USER_OPTIONS = [
  { value: 0, label: 'Tidak' },
  { value: 0.2, label: 'Tidak tahu' },
  { value: 0.4, label: 'Sedikit yakin' },
  { value: 0.6, label: 'Cukup yakin' },
  { value: 0.8, label: 'Yakin' },
  { value: 1, label: 'Sangat yakin' },
];

// Istilah kepastian linguistik (dipakai di laporan: Maybe 0.4, Probably 0.6, dst).
const CF_PAKAR_TERMS = [
  { value: 1, label: 'Definitely (Pasti)' },
  { value: 0.8, label: 'Almost Certainly (Hampir Pasti)' },
  { value: 0.6, label: 'Probably (Kemungkinan Besar)' },
  { value: 0.4, label: 'Maybe (Mungkin)' },
  { value: 0.2, label: 'Unknown (Tidak Tahu)' },
];

const PRESISI = 8; // laporan menampilkan hingga 8 angka di belakang koma

function bulat(x) {
  return Number(x.toFixed(PRESISI));
}

/** Gabungkan dua nilai CF (rumus CF combine, termasuk kasus nilai negatif). */
function combine(cfLama, cfBaru) {
  if (cfLama >= 0 && cfBaru >= 0) return cfLama + cfBaru * (1 - cfLama);
  if (cfLama < 0 && cfBaru < 0) return cfLama + cfBaru * (1 + cfLama);
  const pembagi = 1 - Math.min(Math.abs(cfLama), Math.abs(cfBaru));
  return pembagi === 0 ? 0 : (cfLama + cfBaru) / pembagi;
}

function labelCfUser(value) {
  const opt = CF_USER_OPTIONS.find((o) => o.value === value);
  return opt ? opt.label : String(value);
}

function interpretasi(cf) {
  if (cf >= 1) return 'Pasti';
  if (cf >= 0.8) return 'Hampir pasti';
  if (cf >= 0.6) return 'Kemungkinan besar';
  if (cf >= 0.4) return 'Mungkin';
  if (cf > 0) return 'Kemungkinan kecil';
  return 'Tidak ada indikasi';
}

/**
 * Hitung CF untuk setiap penyakit/hipotesis.
 *
 * @param {Array<{id,kode,nama,keterangan,saran}>} penyakit
 * @param {Array<{id,kode,nama,bobot:Object<penyakitId,number>}>} gejala  urut sesuai kode
 * @param {Object<gejalaId,number>} jawaban  CF User per gejala (0..1)
 */
function hitungCF(penyakit, gejala, jawaban) {
  const hasil = penyakit.map((p) => {
    const langkah = [];
    let cf = null;
    for (const g of gejala) {
      const cfUser = Number(jawaban[g.id] || 0);
      const cfPakar = Number((g.bobot && g.bobot[p.id]) || 0);
      if (cfUser <= 0 || cfPakar <= 0) continue;
      const cfHE = bulat(cfUser * cfPakar);
      const cfLama = cf;
      cf = cfLama === null ? cfHE : bulat(combine(cfLama, cfHE));
      langkah.push({ kode: g.kode, nama: g.nama, cfUser, cfPakar, cfHE, cfLama, cfHasil: cf });
    }
    const nilai = cf === null ? 0 : cf;
    return {
      kode: p.kode,
      nama: p.nama,
      keterangan: p.keterangan || '',
      saran: p.saran || '',
      cf: nilai,
      persen: Number((nilai * 100).toFixed(2)),
      interpretasi: interpretasi(nilai),
      langkah,
    };
  });

  const urut = [...hasil].sort((a, b) => b.cf - a.cf);
  const teratas = urut[0];
  const kesimpulan = teratas && teratas.cf > 0 ? teratas : null;

  const jawabanDetail = gejala
    .filter((g) => Number(jawaban[g.id] || 0) > 0)
    .map((g) => {
      const v = Number(jawaban[g.id]);
      return { kode: g.kode, nama: g.nama, cfUser: v, label: labelCfUser(v) };
    });

  return { hasil: urut, kesimpulan, jawaban: jawabanDetail };
}

module.exports = {
  CF_USER_OPTIONS,
  CF_PAKAR_TERMS,
  combine,
  hitungCF,
  interpretasi,
  labelCfUser,
};
