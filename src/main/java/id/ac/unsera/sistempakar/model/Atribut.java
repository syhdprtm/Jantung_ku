package id.ac.unsera.sistempakar.model;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Atribut data klinis yang dipakai rule base, sama dengan kolom dataset
 * Heart Failure Prediction.
 *
 * @param kolom nama kolom pada file CSV
 */
public enum Atribut {
    AGE("Age", "Usia", "tahun", null, "1", "120",
            "Usia pasien saat pemeriksaan."),
    SEX("Sex", "Jenis kelamin", null, List.of("M", "F"), null, null,
            "M = laki-laki, F = perempuan."),
    CHEST_PAIN("ChestPainType", "Tipe nyeri dada", null, List.of("ASY", "ATA", "NAP", "TA"), null, null,
            "ASY = tanpa gejala, ATA = angina atipikal, NAP = nyeri bukan angina, TA = angina tipikal."),
    RESTING_BP("RestingBP", "Tekanan darah istirahat", "mmHg", null, "0", "250",
            "Tekanan sistolik (angka atas) saat istirahat."),
    CHOLESTEROL("Cholesterol", "Kolesterol", "mg/dl", null, "0", "700",
            "Kolesterol total serum dari hasil laboratorium."),
    FASTING_BS("FastingBS", "Gula darah puasa > 120 mg/dl", null, null, "0", "1",
            "Pilih Ya bila gula darah puasa lebih dari 120 mg/dl."),
    RESTING_ECG("RestingECG", "EKG istirahat", null, List.of("Normal", "ST", "LVH"), null, null,
            "ST = kelainan gelombang ST-T, LVH = pembesaran bilik kiri."),
    MAX_HR("MaxHR", "Detak jantung maksimum", "bpm", null, "50", "220",
            "Detak jantung tertinggi yang dicapai saat tes latihan (treadmill)."),
    EXERCISE_ANGINA("ExerciseAngina", "Nyeri dada saat olahraga", null, List.of("Y", "N"), null, null,
            "Y = muncul nyeri dada saat tes latihan, N = tidak."),
    OLDPEAK("Oldpeak", "Oldpeak (depresi ST)", "mm", null, "-3", "7",
            "Penurunan segmen ST saat latihan dibanding istirahat, dari tes treadmill."),
    ST_SLOPE("ST_Slope", "Kemiringan segmen ST", null, List.of("Up", "Flat", "Down"), null, null,
            "Arah segmen ST pada puncak latihan: naik, datar, atau turun.");

    private final String kolom;
    private final String label;
    private final String satuan;
    private final List<String> pilihan;
    private final BigDecimal min;
    private final BigDecimal max;
    private final String bantuan;

    Atribut(String kolom, String label, String satuan, List<String> pilihan, String min, String max, String bantuan) {
        this.kolom = kolom;
        this.label = label;
        this.satuan = satuan;
        this.pilihan = pilihan;
        this.min = min == null ? null : new BigDecimal(min);
        this.max = max == null ? null : new BigDecimal(max);
        this.bantuan = bantuan;
    }

    public String getKolom() { return kolom; }
    public String getLabel() { return label; }
    public String getSatuan() { return satuan; }
    public List<String> getPilihan() { return pilihan; }
    public BigDecimal getMin() { return min; }
    public BigDecimal getMax() { return max; }
    public String getBantuan() { return bantuan; }

    public boolean isKategori() {
        return pilihan != null;
    }

    /** Nilai kategori yang dinormalkan (mis. "flat" -> "Flat"), atau empty bila tidak dikenal. */
    public Optional<String> normalkanPilihan(String v) {
        if (pilihan == null || v == null) return Optional.empty();
        return pilihan.stream().filter(p -> p.equalsIgnoreCase(v.trim())).findFirst();
    }

    private static String kunci(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    /**
     * Cari atribut dari nama di teks rule, mis. "Max HR", "St.Slope", "Excercise Angina" (salah ketik
     * di laporan), "ChestPainType", "Resting BP".
     */
    public static Optional<Atribut> dariNama(String nama) {
        String k = kunci(nama);
        return Optional.ofNullable(switch (k) {
            case "age", "usia", "umur" -> AGE;
            case "sex", "jeniskelamin" -> SEX;
            case "chestpaintype", "chestpain" -> CHEST_PAIN;
            case "restingbp" -> RESTING_BP;
            case "cholesterol", "kolesterol" -> CHOLESTEROL;
            case "fastingbs" -> FASTING_BS;
            case "restingecg" -> RESTING_ECG;
            case "maxhr" -> MAX_HR;
            case "exerciseangina", "excerciseangina" -> EXERCISE_ANGINA;
            case "oldpeak" -> OLDPEAK;
            case "stslope", "slope" -> ST_SLOPE;
            default -> Arrays.stream(values()).filter(a -> kunci(a.kolom).equals(k)).findFirst().orElse(null);
        });
    }
}
