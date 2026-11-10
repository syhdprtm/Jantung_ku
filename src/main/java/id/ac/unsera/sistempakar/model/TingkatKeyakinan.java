package id.ac.unsera.sistempakar.model;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * Pilihan CF User pada kuesioner: 9 tingkat interpretasi nilai Certainty Theory
 * (Definitely not -1,0 ... Unknown 0 ... Definitely 1,0). Nilai negatif berarti pengguna yakin
 * gejala itu TIDAK dialami, sehingga menurunkan keyakinan hipotesis yang ditunjuk gejala tersebut.
 */
public enum TingkatKeyakinan {
    PASTI_TIDAK("-1", "Pasti tidak", "Pasti tidak"),
    HAMPIR_PASTI_TIDAK("-0.8", "Hampir pasti tidak", "Hampir pasti tidak"),
    KEMUNGKINAN_BESAR_TIDAK("-0.6", "Kemungkinan besar tidak", "Kemungkinan besar tidak"),
    MUNGKIN_TIDAK("-0.4", "Mungkin tidak", "Mungkin tidak"),
    TIDAK_TAHU("0", "Tidak tahu", "Tidak tahu"),
    MUNGKIN("0.4", "Mungkin", "Mungkin"),
    KEMUNGKINAN_BESAR("0.6", "Kemungkinan besar", "Kemungkinan besar"),
    HAMPIR_PASTI("0.8", "Hampir pasti", "Hampir pasti"),
    PASTI("1", "Pasti", "Pasti");

    /** Rentang "Tidak tahu" pada tabel interpretasi: -0,2 sampai 0,2. */
    private static final BigDecimal BATAS_TIDAK_TAHU = new BigDecimal("0.2");

    private final BigDecimal nilai;
    private final String label;
    private final String labelPendek;

    TingkatKeyakinan(String nilai, String label, String labelPendek) {
        this.nilai = new BigDecimal(nilai);
        this.label = label;
        this.labelPendek = labelPendek;
    }

    public BigDecimal getNilai() {
        return nilai;
    }

    public String getNilaiTeks() {
        return nilai.toPlainString();
    }

    /** Angka untuk ditampilkan di bawah pilihan, mis. "-0,8" atau "1,0". */
    public String getNilaiTampil() {
        if (nilai.signum() == 0) return "0";
        return nilai.setScale(1, java.math.RoundingMode.UNNECESSARY).toPlainString().replace('.', ',');
    }

    public String getLabel() {
        return label;
    }

    public String getLabelPendek() {
        return labelPendek;
    }

    /** Arah jawaban: "tidak" (negatif), "tahu" (nol), atau "ya" (positif) — dipakai untuk warna pilihan. */
    public String getArah() {
        return nilai.signum() < 0 ? "tidak" : nilai.signum() == 0 ? "tahu" : "ya";
    }

    public static Optional<TingkatKeyakinan> dariNilai(String teks) {
        if (teks == null || teks.isBlank()) return Optional.of(TIDAK_TAHU);
        try {
            BigDecimal v = new BigDecimal(teks.trim());
            return Arrays.stream(values()).filter(t -> t.nilai.compareTo(v) == 0).findFirst();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Label untuk nilai tersimpan; nilai skala lama (mis. 0,2) dipetakan ke rentang tabel interpretasi. */
    public static String labelDari(BigDecimal v) {
        if (v == null) return "-";
        if (v.abs().compareTo(BATAS_TIDAK_TAHU) <= 0) return TIDAK_TAHU.label;
        return Arrays.stream(values()).filter(t -> t.nilai.compareTo(v) == 0)
                .map(TingkatKeyakinan::getLabel).findFirst().orElse(v.toPlainString());
    }
}
