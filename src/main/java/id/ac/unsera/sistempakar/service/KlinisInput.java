package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.Atribut;
import id.ac.unsera.sistempakar.model.DataKlinis;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validasi & konversi data klinis dari teks (form web maupun baris CSV) menjadi {@link DataKlinis}.
 */
public final class KlinisInput {

    private KlinisInput() {
    }

    /** @param data null bila ada kesalahan */
    public record Hasil(DataKlinis data, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** @param raw nilai teks per atribut (kunci = Atribut) */
    public static Hasil baca(Map<Atribut, String> raw) {
        List<String> errors = new ArrayList<>();
        Object[] v = new Object[Atribut.values().length];
        for (Atribut a : Atribut.values()) {
            String s = raw.getOrDefault(a, "");
            s = s == null ? "" : s.trim();
            if (s.isEmpty()) {
                errors.add(a.getLabel() + " (" + a.getKolom() + ") wajib diisi.");
                continue;
            }
            if (a.isKategori()) {
                var n = a.normalkanPilihan(s);
                if (n.isEmpty()) errors.add(a.getLabel() + ": nilai “" + s + "” tidak dikenal (pilihan: " + String.join(", ", a.getPilihan()) + ").");
                else v[a.ordinal()] = n.get();
                continue;
            }
            try {
                BigDecimal b = new BigDecimal(s.replace(',', '.'));
                if (a.getMin() != null && (b.compareTo(a.getMin()) < 0 || b.compareTo(a.getMax()) > 0)) {
                    errors.add(a.getLabel() + " harus antara " + a.getMin().toPlainString() + " dan " + a.getMax().toPlainString() + ".");
                } else if (a != Atribut.OLDPEAK && b.stripTrailingZeros().scale() > 0) {
                    errors.add(a.getLabel() + " harus bilangan bulat.");
                } else {
                    v[a.ordinal()] = b;
                }
            } catch (NumberFormatException e) {
                errors.add(a.getLabel() + " harus berupa angka.");
            }
        }
        if (!errors.isEmpty()) return new Hasil(null, errors);
        return new Hasil(new DataKlinis(
                ((BigDecimal) v[Atribut.AGE.ordinal()]).intValue(),
                (String) v[Atribut.SEX.ordinal()],
                (String) v[Atribut.CHEST_PAIN.ordinal()],
                ((BigDecimal) v[Atribut.RESTING_BP.ordinal()]).intValue(),
                ((BigDecimal) v[Atribut.CHOLESTEROL.ordinal()]).intValue(),
                ((BigDecimal) v[Atribut.FASTING_BS.ordinal()]).intValue(),
                (String) v[Atribut.RESTING_ECG.ordinal()],
                ((BigDecimal) v[Atribut.MAX_HR.ordinal()]).intValue(),
                (String) v[Atribut.EXERCISE_ANGINA.ordinal()],
                (BigDecimal) v[Atribut.OLDPEAK.ordinal()],
                (String) v[Atribut.ST_SLOPE.ordinal()]), List.of());
    }
}
