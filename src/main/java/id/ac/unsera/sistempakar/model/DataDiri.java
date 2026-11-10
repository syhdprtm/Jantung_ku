package id.ac.unsera.sistempakar.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * Data diri & pemeriksaan fisik pasien (9 isian ala formulir Medical Check Up).
 * Turunan yang dihitung otomatis: umur (dari tanggal lahir), IMT (dari tinggi & berat),
 * dan kategori tekanan darah. Nilai ini informatif dan tidak mengubah perhitungan Certainty Factor.
 */
public record DataDiri(String namaPasien, String jenisKelamin, LocalDate tanggalLahir, String alamat, String telepon,
                       BigDecimal tinggiCm, BigDecimal beratKg, Integer sistolik, Integer diastolik) {

    public Integer umur(LocalDate pada) {
        return hitungUmur(tanggalLahir, pada);
    }

    public BigDecimal imt() {
        return hitungImt(tinggiCm, beratKg);
    }

    // ------------------------------------------------------------ rumus bersama (dipakai juga oleh Diagnosa)

    public static Integer hitungUmur(LocalDate lahir, LocalDate pada) {
        if (lahir == null || pada == null || lahir.isAfter(pada)) return null;
        return Period.between(lahir, pada).getYears();
    }

    /** Indeks massa tubuh = berat (kg) / tinggi (m)^2, satu desimal. */
    public static BigDecimal hitungImt(BigDecimal tinggiCm, BigDecimal beratKg) {
        if (tinggiCm == null || beratKg == null || tinggiCm.signum() <= 0) return null;
        BigDecimal m = tinggiCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return beratKg.divide(m.multiply(m), 1, RoundingMode.HALF_UP);
    }

    /** Kategori IMT menurut Kemenkes RI (P2PTM): kurus < 18,5; normal 18,5–25,0; gemuk > 25,0–27,0; obesitas > 27,0. */
    public static String kategoriImt(BigDecimal imt) {
        if (imt == null) return null;
        double v = imt.doubleValue();
        if (v < 17.0) return "Kurus (kekurangan berat badan berat)";
        if (v < 18.5) return "Kurus (kekurangan berat badan ringan)";
        if (v <= 25.0) return "Normal";
        if (v <= 27.0) return "Gemuk (kelebihan berat badan ringan)";
        return "Obesitas";
    }

    public static boolean imtNormal(BigDecimal imt) {
        return imt != null && imt.doubleValue() >= 18.5 && imt.doubleValue() <= 25.0;
    }

    /** Klasifikasi tekanan darah dewasa (JNC 7, dipakai Kemenkes RI); kategori tertinggi dari sistolik/diastolik. */
    public static String kategoriTekanan(Integer sistolik, Integer diastolik) {
        if (sistolik == null || diastolik == null) return null;
        if (sistolik >= 160 || diastolik >= 100) return "Hipertensi derajat 2";
        if (sistolik >= 140 || diastolik >= 90) return "Hipertensi derajat 1";
        if (sistolik >= 120 || diastolik >= 80) return "Prahipertensi";
        return "Normal";
    }

    public static boolean tekananNormal(Integer sistolik, Integer diastolik) {
        return "Normal".equals(kategoriTekanan(sistolik, diastolik));
    }

    public static String labelJenisKelamin(String jk) {
        return "L".equals(jk) ? "Laki-laki" : "P".equals(jk) ? "Perempuan" : null;
    }
}
