package id.ac.unsera.sistempakar.model;

import java.math.BigDecimal;

/**
 * Satu syarat pada bagian IF sebuah rule, mis. {@code MAX_HR > 185} atau {@code ST_SLOPE = Down}.
 *
 * @param operator salah satu dari {@code > >= < <= =}
 */
public record Syarat(Atribut atribut, String operator, String nilai) {

    public boolean isNumerik() {
        return !atribut.isKategori();
    }

    public BigDecimal angka() {
        return new BigDecimal(nilai);
    }

    /** Apakah data pasien memenuhi syarat ini. */
    public boolean cocok(DataKlinis d) {
        Object v = d.nilai(atribut);
        if (v == null) return false;
        if (atribut.isKategori()) {
            return "=".equals(operator) && nilai.equalsIgnoreCase(v.toString());
        }
        int c = ((BigDecimal) v).compareTo(angka());
        return switch (operator) {
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            case "=" -> c == 0;
            default -> false;
        };
    }

    /** Bentuk teks yang mudah dibaca, mis. "Max HR > 185" atau "ST Slope = Down". */
    public String teks() {
        return atribut.getKolom() + " " + operator + " " + nilai;
    }
}
