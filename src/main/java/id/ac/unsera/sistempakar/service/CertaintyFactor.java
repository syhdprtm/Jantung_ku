package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.Gejala;
import id.ac.unsera.sistempakar.model.Penyakit;
import id.ac.unsera.sistempakar.model.TingkatKeyakinan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Mesin inferensi Certainty Factor:
 * <ol>
 *   <li>{@code CF(H,E) = CF(User) x CF(Pakar)} untuk setiap gejala; CF User -1..1 (9 tingkat interpretasi)</li>
 *   <li>digabung berurutan: kedua positif {@code CF1 + CF2 x (1 - CF1)}; kedua negatif {@code CF1 + CF2 x (1 + CF1)};
 *       berlawanan tanda {@code (CF1 + CF2) / (1 - min(|CF1|, |CF2|))}</li>
 * </ol>
 * Jawaban "Tidak tahu" (CF User 0) tidak ikut dihitung.
 * Perhitungan memakai BigDecimal dan dibulatkan 8 angka di belakang koma pada setiap langkah,
 * sama seperti perhitungan manual.
 */
public final class CertaintyFactor {

    public static final int PRESISI = 8;

    private CertaintyFactor() {
    }

    /** Satu pasangan CF User dan CF Pakar untuk satu gejala. */
    public record Masukan(String gejalaKode, String gejalaNama, BigDecimal cfUser, BigDecimal cfPakar) {
    }

    /** Satu baris langkah perhitungan; cfLama null pada gejala pertama. */
    public record Langkah(String gejalaKode, String gejalaNama, BigDecimal cfUser, BigDecimal cfPakar,
                          BigDecimal cfHE, BigDecimal cfLama, BigDecimal cfHasil) {

        /** Rumus penggabungan yang dipakai pada langkah ini (sesuai tanda kedua nilai), untuk ditampilkan. */
        public String getRumus() {
            if (cfLama == null) return "nilai awal";
            String a = teks(cfLama), b = teks(cfHE);
            if (cfLama.signum() >= 0 && cfHE.signum() >= 0) return a + " + " + b + " × (1 − " + a + ")";
            if (cfLama.signum() < 0 && cfHE.signum() < 0) return a + " + (" + b + ") × (1 + (" + a + "))";
            return "(" + a + " + (" + b + ")) / (1 − min(|" + a + "|, |" + b + "|))";
        }

        private static String teks(BigDecimal v) {
            return v.setScale(PRESISI, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
    }

    public record HasilHipotesis(String kode, String nama, String kategori, String saran,
                                 BigDecimal cf, List<Langkah> langkah) {

        public boolean isRisiko() {
            return "RISIKO".equals(kategori);
        }

        public String getInterpretasi() {
            return interpretasi(cf);
        }

        /** Arti hasil dalam satu kalimat berbahasa awam. */
        public String getArti() {
            // mengikuti istilah kepastian yang sama dengan label, jadi kalimat & label tidak pernah bertentangan
            String yakin = switch (interpretasi(cf)) {
                case "Pasti" -> "Berdasarkan data yang dimasukkan, sistem pasti bahwa";
                case "Hampir pasti" -> "Sistem sangat yakin bahwa";
                case "Kemungkinan besar" -> "Sistem cukup yakin bahwa";
                case "Mungkin" -> "Sistem menilai ada kemungkinan bahwa";
                default -> "Sistem hanya menemukan sedikit petunjuk bahwa";
            };
            return yakin + (isRisiko()
                    ? " data ini mengarah ke risiko penyakit jantung."
                    : " kondisi jantung pada data ini tergolong normal.");
        }
    }

    public record Jawaban(String gejalaKode, String gejalaNama, BigDecimal cfUser) {
        public String getLabel() {
            return TingkatKeyakinan.labelDari(cfUser);
        }
    }

    /**
     * @param hasil      semua hipotesis, urut dari CF terbesar
     * @param kesimpulan hipotesis dengan CF terbesar, atau null bila semua 0
     */
    public record HasilDiagnosa(List<HasilHipotesis> hasil, HasilHipotesis kesimpulan, List<Jawaban> jawaban) {
    }

    static BigDecimal bulat(BigDecimal x) {
        return x.setScale(PRESISI, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** Rumus CF combine (termasuk kasus nilai negatif untuk kelengkapan). */
    public static BigDecimal combine(BigDecimal lama, BigDecimal baru) {
        BigDecimal satu = BigDecimal.ONE;
        if (lama.signum() >= 0 && baru.signum() >= 0) {
            return lama.add(baru.multiply(satu.subtract(lama)));
        }
        if (lama.signum() < 0 && baru.signum() < 0) {
            return lama.add(baru.multiply(satu.add(lama)));
        }
        BigDecimal pembagi = satu.subtract(lama.abs().min(baru.abs()));
        if (pembagi.signum() == 0) return BigDecimal.ZERO;
        return lama.add(baru).divide(pembagi, PRESISI + 4, RoundingMode.HALF_UP);
    }

    /** Hitung langkah CF combine dari daftar masukan (sesuai urutan). */
    public static List<Langkah> hitungLangkah(List<Masukan> masukan) {
        List<Langkah> langkah = new ArrayList<>();
        BigDecimal cf = null;
        for (Masukan m : masukan) {
            if (m.cfUser().signum() == 0 || m.cfPakar().signum() <= 0) continue;   // "Tidak tahu" / tanpa bobot
            BigDecimal cfHE = bulat(m.cfUser().multiply(m.cfPakar()));
            BigDecimal lama = cf;
            cf = lama == null ? cfHE : bulat(combine(lama, cfHE));
            langkah.add(new Langkah(m.gejalaKode(), m.gejalaNama(), m.cfUser(), m.cfPakar(), cfHE, lama, cf));
        }
        return langkah;
    }

    public static BigDecimal cfAkhir(List<Langkah> langkah) {
        return langkah.isEmpty() ? BigDecimal.ZERO : langkah.getLast().cfHasil();
    }

    /**
     * Hitung CF untuk setiap hipotesis.
     *
     * @param jawaban CF User per id gejala
     */
    public static HasilDiagnosa hitung(List<Penyakit> penyakit, List<Gejala> gejala, Map<Long, BigDecimal> jawaban) {
        List<HasilHipotesis> hasil = new ArrayList<>();
        for (Penyakit p : penyakit) {
            List<Masukan> masukan = gejala.stream()
                    .map(g -> new Masukan(g.kode(), g.nama(),
                            jawaban.getOrDefault(g.id(), BigDecimal.ZERO), g.bobotUntuk(p.id())))
                    .toList();
            List<Langkah> langkah = hitungLangkah(masukan);
            hasil.add(new HasilHipotesis(p.kode(), p.nama(), p.kategori(), p.saran(), cfAkhir(langkah), langkah));
        }
        hasil.sort(Comparator.comparing(HasilHipotesis::cf).reversed());

        HasilHipotesis kesimpulan = !hasil.isEmpty() && hasil.getFirst().cf().signum() > 0 ? hasil.getFirst() : null;

        List<Jawaban> dipilih = gejala.stream()
                .filter(g -> jawaban.getOrDefault(g.id(), BigDecimal.ZERO).signum() != 0)
                .map(g -> new Jawaban(g.kode(), g.nama(), jawaban.get(g.id())))
                .toList();

        return new HasilDiagnosa(hasil, kesimpulan, dipilih);
    }

    /**
     * Persentase seperti yang ditampilkan ke pengguna (dibulatkan 2 desimal, HALF_UP).
     * Istilah kepastian dihitung dari angka ini, bukan dari CF mentah, agar label selalu
     * cocok dengan angka di layar (mis. CF 0,99996 tampil "100,00%" sehingga harus "Pasti").
     */
    public static BigDecimal persenTampil(BigDecimal cf) {
        return cf.multiply(BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Istilah kepastian mengikuti tabel interpretasi Certainty Theory, simetris untuk nilai negatif:
     * Pasti (100%), Hampir pasti (>=80%), Kemungkinan besar (>=60%), Mungkin (>=40%), lalu "... tidak" untuk sisi negatif.
     */
    public static String interpretasi(BigDecimal cf) {
        BigDecimal p = persenTampil(cf);
        if (p.compareTo(BigDecimal.valueOf(100)) >= 0) return "Pasti";
        if (p.compareTo(BigDecimal.valueOf(80)) >= 0) return "Hampir pasti";
        if (p.compareTo(BigDecimal.valueOf(60)) >= 0) return "Kemungkinan besar";
        if (p.compareTo(BigDecimal.valueOf(40)) >= 0) return "Mungkin";
        if (p.signum() > 0) return "Kemungkinan kecil";
        if (p.compareTo(BigDecimal.valueOf(-100)) <= 0) return "Pasti tidak";
        if (p.compareTo(BigDecimal.valueOf(-80)) <= 0) return "Hampir pasti tidak";
        if (p.compareTo(BigDecimal.valueOf(-60)) <= 0) return "Kemungkinan besar tidak";
        if (p.compareTo(BigDecimal.valueOf(-40)) <= 0) return "Mungkin tidak";
        return "Tidak ada indikasi";
    }
}
