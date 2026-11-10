package id.ac.unsera.sistempakar.repository;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Filter halaman riwayat.
 *
 * @param q      cari nama pasien / username
 * @param hasil  RISIKO, NORMAL, NONE (tak tersimpulkan) atau kosong
 * @param metode GEJALA, KLINIS atau kosong
 */
public record FilterRiwayat(String q, String hasil, String metode, LocalDate dari, LocalDate sampai, int halaman) {

    public static final int UKURAN = 15;

    public static FilterRiwayat dari(String q, String hasil, String metode, String dari, String sampai, Integer halaman) {
        return new FilterRiwayat(
                q == null ? "" : q.trim(),
                hasil != null && List.of("RISIKO", "NORMAL", "NONE").contains(hasil) ? hasil : "",
                metode != null && List.of("GEJALA", "KLINIS").contains(metode) ? metode : "",
                tanggal(dari), tanggal(sampai),
                halaman == null || halaman < 1 ? 1 : halaman);
    }

    private static LocalDate tanggal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public boolean isAktif() {
        return !q.isEmpty() || !hasil.isEmpty() || !metode.isEmpty() || dari != null || sampai != null;
    }

    public FilterRiwayat keHalaman(int h) {
        return new FilterRiwayat(q, hasil, metode, dari, sampai, h);
    }

    /** Parameter query untuk tautan (paginasi, ekspor). */
    public Map<String, Object> params() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!q.isEmpty()) m.put("q", q);
        if (!hasil.isEmpty()) m.put("hasil", hasil);
        if (!metode.isEmpty()) m.put("metode", metode);
        if (dari != null) m.put("dari", dari.toString());
        if (sampai != null) m.put("sampai", sampai.toString());
        return m;
    }

    /** Query string tanpa halaman, mis. "q=budi&hasil=RISIKO". */
    public String query() {
        StringBuilder sb = new StringBuilder();
        params().forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(k).append('=').append(java.net.URLEncoder.encode(String.valueOf(v), java.nio.charset.StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    /** Satu halaman hasil pencarian. */
    public record Halaman<T>(List<T> isi, long total, int halaman, int ukuran) {
        public int getJumlahHalaman() {
            return (int) Math.max(1, (total + ukuran - 1) / ukuran);
        }

        public boolean isAdaSebelum() {
            return halaman > 1;
        }

        public boolean isAdaBerikut() {
            return halaman < getJumlahHalaman();
        }

        public long getDari() {
            return total == 0 ? 0 : (long) (halaman - 1) * ukuran + 1;
        }

        public long getSampai() {
            return Math.min(total, (long) halaman * ukuran);
        }
    }
}
