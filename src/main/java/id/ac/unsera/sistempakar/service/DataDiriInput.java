package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.DataDiri;
import id.ac.unsera.sistempakar.model.Diagnosa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Membaca & memvalidasi isian data diri ala formulir Medical Check Up:
 * nama, jenis kelamin, tanggal lahir, alamat, telepon, tinggi, dan berat badan.
 * Tekanan darah tidak ditanyakan terpisah karena sudah menjadi gejala D01 pada kuesioner.
 * Alamat dan telepon opsional (data pribadi yang tidak diperlukan untuk perhitungan).
 */
public final class DataDiriInput {

    public static final List<String> KOLOM = List.of("namaPasien", "jenisKelamin", "tanggalLahir", "alamat", "telepon",
            "tinggiCm", "beratKg");

    private static final Pattern TELEPON = Pattern.compile("\\+?[0-9]{8,15}");

    public record Hasil(DataDiri data, List<String> errors) {
    }

    private DataDiriInput() {
    }

    /** Nilai mentah form (untuk diisi ulang saat ada kesalahan). */
    public static Map<String, String> mentah(Map<String, String> body) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : KOLOM) m.put(k, body.getOrDefault(k, "").trim());
        return m;
    }

    /** Isian awal dari diagnosa terakhir pengguna (bila ada), atau hanya nama. */
    public static Map<String, String> awal(String nama, Diagnosa terakhir) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : KOLOM) m.put(k, "");
        m.put("namaPasien", nama == null ? "" : nama);
        if (terakhir != null) {
            m.put("namaPasien", terakhir.namaPasien());
            m.put("jenisKelamin", teks(terakhir.jenisKelamin()));
            m.put("tanggalLahir", terakhir.tanggalLahir() == null ? "" : terakhir.tanggalLahir().toString());
            m.put("alamat", teks(terakhir.alamat()));
            m.put("telepon", teks(terakhir.telepon()));
            m.put("tinggiCm", angka(terakhir.tinggiCm()));
            m.put("beratKg", angka(terakhir.beratKg()));
        }
        return m;
    }

    public static Hasil baca(Map<String, String> body, LocalDate hariIni) {
        Map<String, String> v = mentah(body);
        List<String> e = new ArrayList<>();

        String nama = v.get("namaPasien");
        if (nama.isEmpty()) e.add("Nama pasien wajib diisi.");
        else if (nama.length() > 100) e.add("Nama pasien maksimal 100 karakter.");

        String jk = v.get("jenisKelamin").toUpperCase();
        if (!jk.equals("L") && !jk.equals("P")) e.add("Pilih jenis kelamin.");

        LocalDate lahir = null;
        if (v.get("tanggalLahir").isEmpty()) e.add("Tanggal lahir wajib diisi.");
        else {
            try {
                lahir = LocalDate.parse(v.get("tanggalLahir"));
                if (lahir.isAfter(hariIni)) e.add("Tanggal lahir tidak boleh setelah hari ini.");
                else if (DataDiri.hitungUmur(lahir, hariIni) > 130) e.add("Tanggal lahir tidak wajar (umur lebih dari 130 tahun).");
            } catch (DateTimeParseException ex) {
                e.add("Format tanggal lahir tidak valid.");
            }
        }

        String alamat = v.get("alamat");
        if (alamat.length() > 255) e.add("Alamat maksimal 255 karakter.");

        String telepon = v.get("telepon").replaceAll("[\\s.-]", "");
        if (!telepon.isEmpty() && !TELEPON.matcher(telepon).matches())
            e.add("Nomor telepon harus 8–15 digit angka (boleh diawali +).");

        BigDecimal tinggi = desimal(v.get("tinggiCm"), "Tinggi badan", 50, 250, "cm", e);
        BigDecimal berat = desimal(v.get("beratKg"), "Berat badan", 2, 300, "kg", e);
        DataDiri d = new DataDiri(nama, jk, lahir, alamat.isEmpty() ? null : alamat, telepon.isEmpty() ? null : telepon,
                tinggi, berat, null, null);
        return new Hasil(d, e);
    }

    private static BigDecimal desimal(String s, String label, int min, int max, String satuan, List<String> e) {
        if (s.isEmpty()) {
            e.add(label + " wajib diisi.");
            return null;
        }
        try {
            BigDecimal b = new BigDecimal(s.replace(',', '.')).setScale(1, RoundingMode.HALF_UP);
            if (b.doubleValue() < min || b.doubleValue() > max) {
                e.add(label + " harus antara " + min + " dan " + max + " " + satuan + ".");
                return null;
            }
            return b;
        } catch (NumberFormatException ex) {
            e.add(label + " harus berupa angka.");
            return null;
        }
    }


    private static String teks(String s) {
        return s == null ? "" : s;
    }

    private static String angka(BigDecimal b) {
        return b == null ? "" : b.stripTrailingZeros().toPlainString();
    }
}
