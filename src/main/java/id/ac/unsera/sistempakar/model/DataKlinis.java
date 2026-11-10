package id.ac.unsera.sistempakar.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Data klinis seorang pasien (11 atribut, sama dengan kolom dataset). */
public record DataKlinis(int age, String sex, String chestPain, int restingBp, int cholesterol, int fastingBs,
                         String restingEcg, int maxHr, String exerciseAngina, BigDecimal oldpeak, String stSlope) {

    /** Nilai atribut: BigDecimal untuk numerik, String untuk kategori. */
    public Object nilai(Atribut a) {
        return switch (a) {
            case AGE -> BigDecimal.valueOf(age);
            case SEX -> sex;
            case CHEST_PAIN -> chestPain;
            case RESTING_BP -> BigDecimal.valueOf(restingBp);
            case CHOLESTEROL -> BigDecimal.valueOf(cholesterol);
            case FASTING_BS -> BigDecimal.valueOf(fastingBs);
            case RESTING_ECG -> restingEcg;
            case MAX_HR -> BigDecimal.valueOf(maxHr);
            case EXERCISE_ANGINA -> exerciseAngina;
            case OLDPEAK -> oldpeak;
            case ST_SLOPE -> stSlope;
        };
    }

    public String teks(Atribut a) {
        Object v = nilai(a);
        return v instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : String.valueOf(v);
    }

    /** Untuk ditampilkan: label -> nilai + satuan. */
    public Map<Atribut, String> tampil() {
        Map<Atribut, String> m = new LinkedHashMap<>();
        for (Atribut a : Atribut.values()) {
            m.put(a, teks(a) + (a.getSatuan() == null ? "" : " " + a.getSatuan()));
        }
        return m;
    }

    /** Ringkasan satu baris, mis. untuk tabel evaluasi. */
    public String ringkas() {
        return "Age=%d Sex=%s CP=%s BP=%d Chol=%d FBS=%d ECG=%s MaxHR=%d Angina=%s Oldpeak=%s Slope=%s".formatted(
                age, sex, chestPain, restingBp, cholesterol, fastingBs, restingEcg, maxHr, exerciseAngina,
                oldpeak.stripTrailingZeros().toPlainString(), stSlope);
    }
}
