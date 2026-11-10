'use strict';

/**
 * Data awal basis pengetahuan, diambil dari laporan
 * "Sistem Pakar Diagnosa Penyakit Jantung dengan Pendekatan Metode Certainty Factor".
 *
 * - penyakit : hipotesis (Tabel 5.3: Terdiagnosa / Tidak Terdiagnosa)
 * - gejala   : Tabel 5.1, 5.2 dan 5.3
 * - bobot    : CF Pakar per gejala per hipotesis (Tabel 5.3)
 * - rules    : Rule Base hasil Decision Tree (Tabel 5.4)
 */

// Akun administrator default (ubah password setelah login pertama!).
// Bisa juga diatur lewat environment variable ADMIN_USERNAME / ADMIN_PASSWORD.
const ADMIN_DEFAULT = {
  nama: 'Administrator',
  username: process.env.ADMIN_USERNAME || 'admin',
  password: process.env.ADMIN_PASSWORD || 'admin123',
};

const PENYAKIT = [
  {
    kode: 'P01',
    nama: 'Terdiagnosa Penyakit Jantung',
    keterangan:
      'Parameter klinis yang dimasukkan mengarah pada adanya kelainan/penyakit jantung.',
    saran:
      'Segera konsultasikan ke dokter spesialis jantung (Sp.JP) untuk pemeriksaan lanjutan seperti EKG, echocardiography, atau treadmill test. Kurangi konsumsi garam dan lemak jenuh, hentikan merokok, kontrol tekanan darah, kolesterol, dan gula darah secara rutin.',
  },
  {
    kode: 'P02',
    nama: 'Tidak Terdiagnosa Penyakit Jantung (Normal)',
    keterangan:
      'Parameter klinis yang dimasukkan menunjukkan kondisi jantung yang cenderung normal/sehat.',
    saran:
      'Pertahankan pola hidup sehat: olahraga teratur minimal 150 menit per minggu, makan makanan bergizi seimbang, tidur cukup, kelola stres, dan lakukan medical check-up berkala.',
  },
];

// bobot: { P01: cfPakar, P02: cfPakar } — nilai 0 berarti tidak berpengaruh.
const GEJALA = [
  {
    kode: 'D01',
    nama: 'Resting BP tinggi (> 120 mmHg)',
    keterangan: 'Hasil tekanan darah saat pasien dalam kondisi beristirahat.',
    bobot: { P01: 0.4, P02: 0 },
  },
  {
    kode: 'D02',
    nama: 'Cholesterol tinggi (> 200 mg/dl)',
    keterangan:
      'Sejenis lemak (lipid) yang diproduksi oleh hati dan dibutuhkan tubuh untuk membangun sel, membentuk hormon, serta memproduksi vitamin.',
    bobot: { P01: 0.4, P02: 0 },
  },
  {
    kode: 'D03',
    nama: 'Heart Rate Deficit >= 40',
    keterangan:
      'Menunjukkan seberapa besar kekurangan denyut jantung pasien sehingga tidak mencapai detak jantung maksimum yang normal.',
    bobot: { P01: 0.8, P02: 0 },
  },
  {
    kode: 'D04',
    nama: 'Exercise Angina (Ya)',
    keterangan: 'Nyeri dada yang disebabkan atau dipicu pada saat berolahraga.',
    bobot: { P01: 0.8, P02: 0 },
  },
  {
    kode: 'D05',
    nama: 'Fasting BS tinggi (Higher BS)',
    keterangan:
      'Kadar gula darah saat tubuh tidak makan selama beberapa jam (biasanya 8 jam atau lebih) berada di atas normal.',
    bobot: { P01: 0.8, P02: 0 },
  },
  {
    kode: 'D06',
    nama: 'ST Slope Flat',
    keterangan:
      'Tingkat penurunan grafik rekam jantung (EKG) saat pasien berolahraga dibandingkan saat beristirahat bernilai datar (flat).',
    bobot: { P01: 0.8, P02: 0 },
  },
  {
    kode: 'D07',
    nama: 'Max HR rendah (< 140 bpm)',
    keterangan: 'Detak jantung seseorang pada kondisi maksimal berada di bawah 140 bpm.',
    bobot: { P01: 0.8, P02: 0 },
  },
  {
    kode: 'D08',
    nama: 'Resting ECG abnormal (ST)',
    keterangan:
      'Rekam jantung (EKG) saat istirahat menunjukkan kelainan gelombang ST-T yang mengindikasikan gangguan aliran darah ke otot jantung.',
    bobot: { P01: 0.6, P02: 0 },
  },
  {
    kode: 'D09',
    nama: 'Resting BP normal (< 120 mmHg)',
    keterangan: 'Tekanan darah saat istirahat berada pada rentang normal.',
    bobot: { P01: 0, P02: 0.6 },
  },
  {
    kode: 'D10',
    nama: 'Cholesterol normal (< 200 mg/dl)',
    keterangan: 'Kadar kolesterol berada pada rentang normal.',
    bobot: { P01: 0, P02: 0.6 },
  },
  {
    kode: 'D11',
    nama: 'Resting ECG Normal',
    keterangan: 'Rekam jantung (EKG) saat istirahat tidak menunjukkan kelainan.',
    bobot: { P01: 0, P02: 0.6 },
  },
  {
    kode: 'D12',
    nama: 'Fasting BS normal (Normal BS)',
    keterangan: 'Kadar gula darah puasa berada pada rentang normal.',
    bobot: { P01: 0, P02: 0.6 },
  },
  {
    kode: 'D13',
    nama: 'ST Slope Up',
    keterangan:
      'Grafik segmen ST pada EKG saat berolahraga menanjak (up-sloping), umumnya menandakan respons jantung yang sehat.',
    bobot: { P01: 0, P02: 1.0 },
  },
];

// Tabel 5.4 Rule Base. hasil: 'P01' = Terdiagnosis, 'P02' = Normal.
const DOWN_A = 'ST Slope Down AND Max HR <= 186 AND Exercise Angina N AND Resting BP > 117';
const FLAT_F = 'ST Slope Flat AND Max HR <= 183 AND Sex F';
const FLAT_F2 = `${FLAT_F} AND Fasting BS <= 1 AND Oldpeak <= 2.3 AND Age > 39`;
const FLAT_M = 'ST Slope Flat AND Max HR <= 183 AND Sex M';
const FLAT_M1 = `${FLAT_M} AND Max HR <= 177 AND Max HR > 151 AND Oldpeak > 0.3`;
const FLAT_M2 = `${FLAT_M} AND Max HR <= 177 AND Max HR <= 151 AND Max HR > 96`;
const UP_A = 'ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76';
const UP_N = `${UP_A} AND Cholesterol > 43 AND Exercise Angina N`;
const UP_Y = `${UP_A} AND Cholesterol > 43 AND Exercise Angina Y`;
const UP_ASY = `${UP_Y} AND ChestPainType ASY AND Oldpeak <= 1`;
const UP_L = `${UP_A} AND Cholesterol <= 43 AND Fasting BS <= 1`;

const RULES = [
  ['R1', 'ST Slope Down AND Max HR > 185', 'P02', 0.8, 'Detak jantung maksimal (Max HR) yang sangat tinggi (>185) mengindikasikan fungsi jantung masih baik.'],
  ['R2', `${DOWN_A} AND Age > 67`, 'P01', 0.8, 'Usia lansia (>67) dengan tekanan darah di atas normal memperkuat risiko kelainan.'],
  ['R3', `${DOWN_A} AND Age <= 60 AND Age > 63`, 'P02', 0.6, 'Kondisi tidak terklasifikasi sebagai risiko tinggi pada rentang usia ini.'],
  ['R4', `${DOWN_A} AND Age <= 60 AND Age <= 63`, 'P01', 0.8, 'Pasien usia <= 60 tahun dengan tensi tinggi (>117) menunjukkan potensi kelainan.'],
  ['R5', `${DOWN_A} AND Age > 67 AND Age <= 60`, 'P02', 0.6, 'Kondisi paradoksikal/anomali pada cabang pohon keputusan, diasumsikan normal.'],
  ['R6', 'ST Slope Down AND Max HR <= 186 AND Exercise Angina N AND Resting BP <= 117', 'P01', 0.8, 'Tekanan darah rendah/normal (<=117) dengan ST Slope Down tetap berisiko.'],
  ['R7', 'ST Slope Down AND Max HR <= 186 AND Exercise Angina Y', 'P01', 0.9, 'Adanya nyeri dada saat olahraga (Exercise Angina Y) adalah indikasi kuat kelainan jantung.'],
  ['R8', 'ST Slope Flat AND Max HR > 183', 'P02', 0.8, 'Detak jantung maksimal tinggi (>183) pada ST Slope Flat tergolong normal.'],
  ['R9', `${FLAT_F} AND Fasting BS 1`, 'P01', 0.8, 'Wanita dengan Gula Darah Puasa tinggi (BS = 1) rentan terhadap komplikasi kardiovaskular.'],
  ['R10', `${FLAT_F} AND Fasting BS <= 1 AND Oldpeak > 2.3`, 'P01', 0.9, 'Depresi ST saat olahraga (Oldpeak) sangat tinggi (>2.3) adalah bukti signifikan kelainan jantung.'],
  ['R11', `${FLAT_F2} AND Exercise Angina N AND Resting BP > 148`, 'P01', 0.8, 'Hipertensi signifikan (>148) pada pasien wanita dewasa memperkuat diagnosis penyakit jantung.'],
  ['R12', `${FLAT_F2} AND Exercise Angina N AND Resting BP <= 148`, 'P02', 0.8, 'Tekanan darah terkontrol pada wanita tanpa angina meniadakan indikasi kelainan.'],
  ['R13', `${FLAT_F2} AND Exercise Angina Y AND Age < 47`, 'P01', 0.9, 'Angina akibat aktivitas pada wanita usia < 47 tahun merupakan gejala penyakit jantung koroner dini.'],
  ['R14', `${FLAT_F2} AND Exercise Angina Y AND Age <= 47`, 'P02', 0.8, 'Cabang anomali pada usia di bawah 47 tahun, diprediksi tidak memiliki kelainan mayor.'],
  ['R15', `${FLAT_F} AND Fasting BS <= 1 AND Oldpeak <= 2.3 AND Age <= 39`, 'P01', 0.8, 'Usia sangat muda (<= 39) dengan ST Flat memiliki kerentanan kardiovaskular bawaan atau spesifik.'],
  ['R16', `${FLAT_M} AND Max HR > 177 AND Age > 41`, 'P02', 0.8, 'Pria di atas 41 tahun dengan detak jantung mendekati batas atas (177-183) tergolong adaptif dan normal.'],
  ['R17', `${FLAT_M} AND Max HR > 177 AND Age <= 41`, 'P01', 0.8, 'Respons detak jantung tinggi pada pria usia <= 41 tahun di kondisi ST Flat memicu indikasi anomali.'],
  ['R18', `${FLAT_M1} AND Cholesterol > 256 AND Cholesterol > 298`, 'P02', 0.8, 'Hiperkolesterolemia ekstrem (>298) pada kondisi Max HR moderat tidak selalu menunjukkan gagal jantung fungsional.'],
  ['R19', `${FLAT_M1} AND Cholesterol > 256 AND Cholesterol <= 298`, 'P01', 0.8, 'Rentang kolesterol 256-298 memicu plak koroner yang menyebabkan abnormalitas.'],
  ['R20', `${FLAT_M1} AND Cholesterol <= 256 AND Cholesterol > 234`, 'P02', 0.8, 'Kolesterol batas menengah-tinggi (234-256) belum cukup kuat untuk memvonis kelainan jantung.'],
  ['R21', `${FLAT_M1} AND Cholesterol <= 256 AND Cholesterol <= 234 AND Age > 46`, 'P01', 0.8, 'Pria usia > 46 tahun dengan ST Flat berisiko tinggi meskipun kolesterol normal.'],
  ['R22', `${FLAT_M1} AND Cholesterol <= 256 AND Cholesterol <= 234 AND Age <= 46`, 'P02', 0.8, 'Pria dewasa muda (<= 46) dengan kolesterol terkontrol berpotensi memiliki jantung sehat.'],
  ['R23', `${FLAT_M} AND Max HR <= 177 AND Max HR > 151 AND Oldpeak <= 0.3`, 'P01', 0.8, 'Nilai Oldpeak sangat rendah pada pria dengan ST Flat tetap terindikasi berisiko.'],
  ['R24', `${FLAT_M2} AND Max HR > 98 AND Resting BP > 105`, 'P01', 0.8, 'Tekanan darah > 105 dengan laju detak jantung kurang optimal merupakan tanda abnormalitas.'],
  ['R25', `${FLAT_M2} AND Max HR > 98 AND Resting BP <= 105 AND Max HR > 139`, 'P02', 0.8, 'Tensi rendah dengan detak jantung yang masih cukup baik (>139) dianggap normal.'],
  ['R26', `${FLAT_M2} AND Max HR > 98 AND Resting BP <= 105 AND Max HR <= 139`, 'P01', 0.95, 'Detak jantung lambat (<=139) disertai tensi rendah (<=105) menunjukkan penurunan fungsi pompa jantung.'],
  ['R27', `${FLAT_M2} AND Max HR <= 98 AND Age > 56`, 'P02', 0.85, 'Penurunan detak jantung pada usia lanjut (>56) merupakan kondisi degeneratif yang lumrah.'],
  ['R28', `${FLAT_M2} AND Max HR <= 98 AND Age <= 56`, 'P01', 0.85, 'Detak jantung mendekati bradikardia (96-98) pada usia tidak terlalu tua (<= 56) terindikasi abnormal.'],
  ['R29', `${FLAT_M} AND Max HR <= 177 AND Max HR <= 151 AND Max HR <= 96`, 'P01', 0.9, 'Bradikardia yang nyata (Max HR <= 96) memperjelas diagnosis insufisiensi jantung.'],
  ['R30', 'ST Slope Up AND Oldpeak > 2.4', 'P01', 0.95, 'Kombinasi ST Slope Up dengan Oldpeak yang sangat drastis (>2.4) adalah bukti kuat iskemia otot jantung.'],
  ['R31', 'ST Slope Up AND Oldpeak <= 2.4 AND Age > 76', 'P01', 0.9, 'Pasien geriatri usia sangat tua (>76) sangat rentan terhadap penyakit jantung terlepas dari Oldpeak.'],
  ['R32', `${UP_N} AND Max HR > 194`, 'P02', 0.75, 'Kapasitas olahraga sempurna (Max HR > 194) tanpa nyeri membuktikan kesehatan jantung yang sangat prima.'],
  ['R33', `${UP_N} AND Max HR <= 194 AND Age > 57 AND Cholesterol > 200 AND Cholesterol > 206`, 'P02', 0.75, 'Lansia > 57 tahun dengan hiperkolesterolemia pada ST Slope Up diprediksi bebas dari disfungsi utama jantung.'],
  ['R34', `${UP_N} AND Max HR <= 194 AND Age > 57 AND Cholesterol > 200 AND Cholesterol <= 206`, 'P01', 0.8, 'Rentang spesifik kolesterol antara 200-206 memicu batas ambang abnormalitas pada rentang usia lansia ini.'],
  ['R35', `${UP_N} AND Max HR <= 194 AND Age > 57 AND Cholesterol <= 200`, 'P02', 0.8, 'Kolesterol terkontrol (<=200) mengurangi faktor risiko secara signifikan, menormalkan diagnosis.'],
  ['R36', `${UP_N} AND Max HR <= 194 AND Age <= 57`, 'P02', 0.85, 'Pasien < 57 tahun tanpa gejala Angina memiliki kemungkinan jantung sehat yang tinggi.'],
  ['R37', `${UP_Y} AND ChestPainType ASY AND Oldpeak > 1`, 'P01', 0.95, 'Kombinasi Angina, nyeri dada Asimtomatik (ASY), dan ST tertekan adalah gejala klasik iskemia berat.'],
  ['R38', `${UP_ASY} AND Max HR > 129 AND Max HR > 163`, 'P02', 0.8, 'Respons takikardi positif (>163) mengimbangi risiko dari nyeri dada Asimtomatik.'],
  ['R39', `${UP_ASY} AND Max HR > 129 AND Max HR <= 163`, 'P01', 0.85, 'Ketidakmampuan memacu jantung lebih dari 163 bpm saat terjadi angina Asimtomatik berindikasi kelainan.'],
  ['R40', `${UP_ASY} AND Max HR <= 129`, 'P02', 0.7, 'Detak jantung lambat pada Asymptomatic Pain diprediksi sebagai anomali data (diklasifikasikan Normal).'],
  ['R41', `${UP_Y} AND ChestPainType ATA`, 'P02', 0.85, 'Atypical Angina (ATA) pada ST Slope Up tidak dianggap sebagai penyakit jantung koroner (indikasi lambung/otot).'],
  ['R42', `${UP_Y} AND ChestPainType NAP`, 'P02', 0.85, 'Nyeri non-anginal (NAP) merupakan indikasi kuat bahwa masalahnya bukan berasal dari kardiovaskular.'],
  ['R43', `${UP_Y} AND ChestPainType TA`, 'P02', 0.75, 'Nyeri Typical Angina pada sub-cabang pohon ini ditafsirkan sebagai negatif / dapat diobati.'],
  ['R44', `${UP_A} AND Cholesterol <= 43 AND Fasting BS > 1`, 'P01', 0.9, 'Kolesterol amat rendah namun Gula Darah Puasa abnormal (Diabetes) menandakan gangguan metabolik dan vaskular.'],
  ['R45', `${UP_L} AND Max HR > 103 AND Max HR > 119 AND Age > 67`, 'P02', 0.85, 'Lansia (>67) dengan profil lipid terlalu rendah memiliki risiko kelemahan dan kelainan pompa jantung.'],
  ['R46', `${UP_L} AND Max HR > 103 AND Max HR > 119 AND Age <= 67 AND Age > 59`, 'P02', 0.8, 'Usia peralihan (60-67 tahun) dengan gula darah normal belum masuk rentang kritis penyakit struktural jantung.'],
  ['R47', `${UP_L} AND Max HR > 103 AND Max HR > 119 AND Age <= 67 AND Age <= 59`, 'P01', 0.8, 'Usia di bawah 60 tahun dengan parameter profil lipid sangat rendah memicu vonis patologis dari algoritma.'],
  ['R48', `${UP_L} AND Max HR > 103 AND Max HR <= 119`, 'P02', 0.75, 'Batas Max HR 103-119 dianggap aman (Normal) bagi demografi usia ini.'],
  ['R49', `${UP_L} AND Max HR <= 103`, 'P01', 0.85, 'Detak jantung puncak yang sangat lemah (<=103) adalah tanda definitif insufisiensi/penyakit jantung.'],
].map(([kode, kondisi, hasil, cf, keterangan]) => ({ kode, kondisi, hasil, cf, keterangan }));

module.exports = { ADMIN_DEFAULT, PENYAKIT, GEJALA, RULES };
