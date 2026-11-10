package id.ac.unsera.sistempakar.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Helper format angka/tanggal untuk template: ${@fmt.persen(x)}. */
@Component("fmt")
public class Fmt {

    private static final Locale ID = Locale.forLanguageTag("id-ID");
    private static final DateTimeFormatter TGL = DateTimeFormatter.ofPattern("d MMM yyyy, HH.mm", ID);
    private static final DateTimeFormatter TGL_PENDEK = DateTimeFormatter.ofPattern("d MMM yyyy", ID);

    private static final DateTimeFormatter HARI = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", ID);
    public static final java.time.ZoneId WIB = java.time.ZoneId.of("Asia/Jakarta");

    /** Tanggal hari ini untuk kepala dashboard, mis. "Senin, 9 November 2026". */
    public String hariIni() {
        return LocalDateTime.now(WIB).format(HARI);
    }

    /** Sapaan sesuai jam (WIB): Selamat pagi / siang / sore / malam. */
    public String sapaan() {
        int jam = LocalDateTime.now(WIB).getHour();
        return jam < 11 ? "Selamat pagi" : jam < 15 ? "Selamat siang" : jam < 18 ? "Selamat sore" : "Selamat malam";
    }

    /** Persen bilangan bulat dari bagian/total (0 bila total 0), untuk lebar batang. */
    public long bagian(long n, long total) {
        return total <= 0 ? 0 : Math.round(n * 100.0 / total);
    }

    /** Nilai CF apa adanya, mis. 0.98329956 */
    public String cf(BigDecimal v) {
        if (v == null) return "-";
        return v.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Angka persen tanpa simbol, mis. "98,33" */
    public String angkaPersen(BigDecimal v) {
        if (v == null) return "0,00";
        DecimalFormat df = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(ID));
        return df.format(v.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
    }

    /** Persen lengkap, mis. "98,33%" */
    public String persen(BigDecimal v) {
        return angkaPersen(v) + "%";
    }

    /** Versi statis untuk dipakai dari model (mis. teks tooltip). */
    public static String persenS(BigDecimal v) {
        if (v == null) return "0,00%";
        DecimalFormat df = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(ID));
        return df.format(v.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)) + "%";
    }

    public static String tanggalS(LocalDateTime t) {
        return t == null ? "-" : t.format(TGL);
    }

    /** Persen 0-100 untuk lebar bar / gauge (titik desimal). */
    public String lebar(BigDecimal v) {
        if (v == null || v.signum() <= 0) return "0";   // CF negatif tidak punya panjang batang
        return v.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String tanggal(LocalDateTime t) {
        return t == null ? "-" : t.format(TGL);
    }

    public String tanggalPendek(LocalDateTime t) {
        return t == null ? "-" : t.format(TGL_PENDEK);
    }

    private static final DateTimeFormatter TGL_PANJANG = DateTimeFormatter.ofPattern("d MMMM yyyy", ID);

    /** Tanggal lengkap tanpa jam, mis. "10 November 2026". */
    public String tanggalPanjang(java.time.temporal.TemporalAccessor t) {
        return t == null ? "-" : TGL_PANJANG.format(t);
    }

    /** Angka desimal ringkas gaya Indonesia, mis. 165,5 atau 70 (tanpa ",0"). */
    public String angka(BigDecimal v) {
        if (v == null) return "-";
        DecimalFormat df = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(ID));
        return df.format(v);
    }
}
