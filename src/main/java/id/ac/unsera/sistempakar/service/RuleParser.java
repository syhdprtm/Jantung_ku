package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.Atribut;
import id.ac.unsera.sistempakar.model.Syarat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mengubah teks kondisi IF menjadi daftar {@link Syarat} yang bisa dieksekusi.
 *
 * <pre>
 * "ST Slope Down AND Max HR > 185"          -> [ST_SLOPE = Down, MAX_HR > 185]
 * "IF St.Slope Flat AND Oldpeak > 2,3"      -> [ST_SLOPE = Flat, OLDPEAK > 2.3]
 * "Fasting BS 1"                            -> [FASTING_BS = 1]
 * </pre>
 */
public final class RuleParser {

    private static final Pattern AND = Pattern.compile("(?i)\\s+AND\\s+");
    private static final Pattern NUMERIK = Pattern.compile("^(.+?)\\s*(<=|>=|<|>|==|=)\\s*(-?\\d+(?:[.,]\\d+)?)$");
    private static final Pattern TANPA_OPERATOR = Pattern.compile("^(.+?)\\s*(?:=\\s*)?(\\S+)$");

    private RuleParser() {
    }

    /** Kesalahan parsing beserta pesan yang bisa ditampilkan ke admin. */
    public static class RuleParseException extends RuntimeException {
        public RuleParseException(String pesan) {
            super(pesan);
        }
    }

    public static List<Syarat> parse(String kondisi) {
        if (kondisi == null || kondisi.isBlank()) throw new RuleParseException("Kondisi IF kosong.");
        String teks = kondisi.trim()
                .replace("≥", ">=")
                .replace("≤", "<=")
                .replaceFirst("(?i)^IF\\s+", "")
                .replaceFirst("(?i)\\s*=\\s*(true|false)\\s*$", "");

        List<Syarat> hasil = new ArrayList<>();
        for (String bagian : AND.split(teks)) {
            hasil.add(parseSatu(bagian.trim()));
        }
        return hasil;
    }

    private static Syarat parseSatu(String s) {
        if (s.isEmpty()) throw new RuleParseException("Ada syarat kosong (periksa kata AND berurutan).");

        Matcher m = NUMERIK.matcher(s);
        if (m.matches()) {
            Atribut a = atribut(m.group(1), s);
            if (a.isKategori()) {
                throw new RuleParseException("“" + s + "”: " + a.getKolom() + " berisi kategori " + a.getPilihan() + ", bukan angka.");
            }
            String op = "==".equals(m.group(2)) ? "=" : m.group(2);
            return new Syarat(a, op, m.group(3).replace(',', '.'));
        }

        m = TANPA_OPERATOR.matcher(s);
        if (m.matches()) {
            Atribut a = atribut(m.group(1), s);
            String nilai = m.group(2);
            if (a.isKategori()) {
                String n = a.normalkanPilihan(nilai).orElseThrow(() -> new RuleParseException(
                        "“" + s + "”: nilai “" + nilai + "” tidak dikenal untuk " + a.getKolom() + " (pilihan: " + a.getPilihan() + ")."));
                return new Syarat(a, "=", n);
            }
            if (nilai.matches("-?\\d+(?:[.,]\\d+)?")) return new Syarat(a, "=", nilai.replace(',', '.'));
            throw new RuleParseException("“" + s + "”: " + a.getKolom() + " harus dibandingkan dengan angka.");
        }
        throw new RuleParseException("Syarat “" + s + "” tidak dapat dibaca.");
    }

    private static Atribut atribut(String nama, String syarat) {
        return Atribut.dariNama(nama).orElseThrow(() -> new RuleParseException(
                "“" + syarat + "”: atribut “" + nama.trim() + "” tidak dikenal. Gunakan salah satu: Age, Sex, ChestPainType, "
                        + "Resting BP, Cholesterol, Fasting BS, Resting ECG, Max HR, Exercise Angina, Oldpeak, ST Slope."));
    }
}
