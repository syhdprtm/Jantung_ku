package id.ac.unsera.sistempakar.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Confusion matrix satu metode beserta metrik turunannya.
 * Positif = terdiagnosa penyakit jantung (HeartDisease = 1).
 */
public record Metrik(Long evaluasiId, String metode, int tp, int tn, int fp, int fn, int takTercakup) {

    private static BigDecimal bagi(int a, int b) {
        if (b == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), 6, RoundingMode.HALF_UP);
    }

    public int getTercakup() { return tp + tn + fp + fn; }
    public int getTotal() { return getTercakup() + takTercakup; }

    /** (TP + TN) / seluruh baris — baris tak tercakup dihitung salah. */
    public BigDecimal getAkurasi() { return bagi(tp + tn, getTotal()); }

    /** (TP + TN) / baris yang tercakup. */
    public BigDecimal getAkurasiTercakup() { return bagi(tp + tn, getTercakup()); }

    public BigDecimal getPresisi() { return bagi(tp, tp + fp); }
    public BigDecimal getRecall() { return bagi(tp, tp + fn); }
    public BigDecimal getSpesifisitas() { return bagi(tn, tn + fp); }
    public BigDecimal getCakupan() { return bagi(getTercakup(), getTotal()); }

    public BigDecimal getF1() {
        BigDecimal p = getPresisi(), r = getRecall();
        BigDecimal s = p.add(r);
        if (s.signum() == 0) return BigDecimal.ZERO;
        return p.multiply(r).multiply(BigDecimal.valueOf(2)).divide(s, 6, RoundingMode.HALF_UP);
    }

    /** Satu baris grafik perbandingan: nilai metrik untuk pemeriksaan klinis/rule base (a) dan kuesioner gejala (b). */
    public record Banding(String nama, BigDecimal a, BigDecimal b) {
        public String tipA() {
            return nama + " · pemeriksaan klinis: " + id.ac.unsera.sistempakar.web.Fmt.persenS(a);
        }

        public String tipB() {
            return nama + " · kuesioner gejala: " + id.ac.unsera.sistempakar.web.Fmt.persenS(b);
        }
    }

    /** Baris grafik perbandingan dari daftar metrik (KLINIS & GEJALA); kosong bila salah satu tidak ada. */
    public static java.util.List<Banding> bandingkan(java.util.List<Metrik> metrik) {
        Metrik a = metrik.stream().filter(m -> "KLINIS".equals(m.metode())).findFirst().orElse(null);
        Metrik b = metrik.stream().filter(m -> "GEJALA".equals(m.metode())).findFirst().orElse(null);
        if (a == null || b == null) return java.util.List.of();
        return java.util.List.of(
                new Banding("Akurasi", a.getAkurasi(), b.getAkurasi()),
                new Banding("Presisi", a.getPresisi(), b.getPresisi()),
                new Banding("Recall", a.getRecall(), b.getRecall()),
                new Banding("Spesifisitas", a.getSpesifisitas(), b.getSpesifisitas()),
                new Banding("F1-score", a.getF1(), b.getF1()),
                new Banding("Cakupan data", a.getCakupan(), b.getCakupan()));
    }

    public String getNamaMetode() {
        return "KLINIS".equals(metode) ? "Pemeriksaan klinis (rule base)" : "Kuesioner gejala (CF combine)";
    }
}
