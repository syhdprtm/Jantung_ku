package id.ac.unsera.sistempakar.repository;

import id.ac.unsera.sistempakar.model.DataKlinis;
import id.ac.unsera.sistempakar.model.Diagnosa;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class DiagnosaRepository {

    private static final String SELECT = """
            SELECT d.id, d.user_id, u.nama AS nama_user, u.username, d.nama_pasien, d.umur,
                   d.jenis_kelamin, d.tanggal_lahir, d.alamat, d.telepon, d.tinggi_cm, d.berat_kg, d.sistolik, d.diastolik, d.metode,
                   d.hasil_kode, d.hasil_nama, d.hasil_kategori, d.hasil_cf, d.saran, d.created_at
            FROM diagnosa d JOIN users u ON u.id = d.user_id
            """;

    private final JdbcClient jdbc;

    public DiagnosaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record JawabanRow(String gejalaKode, String gejalaNama, BigDecimal cfUser) {
    }

    public record HasilRow(String penyakitKode, String penyakitNama, String kategori, BigDecimal cf) {
    }

    public record DetailRow(String penyakitKode, String gejalaKode, String gejalaNama,
                            BigDecimal cfUser, BigDecimal cfPakar) {
    }

    public record Sebaran(String nama, String kategori, long jumlah) {
    }

    // ---------- tulis ----------

    public long insert(long userId, String namaPasien, Integer umur, String metode, String hasilKode, String hasilNama,
                       String hasilKategori, BigDecimal hasilCf, String saran) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO diagnosa (user_id, nama_pasien, umur, metode, hasil_kode, hasil_nama, hasil_kategori, hasil_cf, saran)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(userId, namaPasien, umur, metode, hasilKode, hasilNama, hasilKategori, hasilCf, saran).update(kh);
        return kh.getKey().longValue();
    }

    /** Simpan data diri & pemeriksaan fisik (9 isian MCU) pada diagnosa yang baru dibuat. */
    public void simpanDataDiri(long id, id.ac.unsera.sistempakar.model.DataDiri d) {
        jdbc.sql("""
                        UPDATE diagnosa SET jenis_kelamin = ?, tanggal_lahir = ?, alamat = ?, telepon = ?,
                               tinggi_cm = ?, berat_kg = ?, sistolik = ?, diastolik = ? WHERE id = ?""")
                .params(d.jenisKelamin(), d.tanggalLahir(), d.alamat(), d.telepon(), d.tinggiCm(), d.beratKg(),
                        d.sistolik(), d.diastolik(), id).update();
    }

    public void insertJawaban(long diagnosaId, int urutan, String kode, String nama, BigDecimal cfUser) {
        jdbc.sql("INSERT INTO diagnosa_jawaban (diagnosa_id, urutan, gejala_kode, gejala_nama, cf_user) VALUES (?, ?, ?, ?, ?)")
                .params(diagnosaId, urutan, kode, nama, cfUser).update();
    }

    public void insertHasil(long diagnosaId, int urutan, String kode, String nama, String kategori, BigDecimal cf) {
        jdbc.sql("INSERT INTO diagnosa_hasil (diagnosa_id, urutan, penyakit_kode, penyakit_nama, kategori, cf) VALUES (?, ?, ?, ?, ?, ?)")
                .params(diagnosaId, urutan, kode, nama, kategori, cf).update();
    }

    public void insertDetail(long diagnosaId, int urutan, String penyakitKode, String gejalaKode, String gejalaNama,
                             BigDecimal cfUser, BigDecimal cfPakar) {
        jdbc.sql("""
                        INSERT INTO diagnosa_detail (diagnosa_id, urutan, penyakit_kode, gejala_kode, gejala_nama, cf_user, cf_pakar)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""")
                .params(diagnosaId, urutan, penyakitKode, gejalaKode, gejalaNama, cfUser, cfPakar).update();
    }

    // ---------- pencarian riwayat ----------

    /**
     * Cari riwayat dengan filter.
     *
     * @param userId null = semua pengguna (admin)
     * @param semua  true = tanpa paginasi (untuk ekspor)
     */
    public FilterRiwayat.Halaman<Diagnosa> cari(FilterRiwayat f, Long userId, boolean semua) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> p = new java.util.ArrayList<>();
        if (userId != null) {
            where.append(" AND d.user_id = ?");
            p.add(userId);
        }
        if (!f.q().isEmpty()) {
            where.append(" AND (d.nama_pasien LIKE ? OR u.username LIKE ? OR u.nama LIKE ?)");
            String like = "%" + f.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            p.add(like);
            p.add(like);
            p.add(like);
        }
        switch (f.hasil()) {
            case "RISIKO", "NORMAL" -> {
                where.append(" AND d.hasil_kategori = ?");
                p.add(f.hasil());
            }
            case "NONE" -> where.append(" AND d.hasil_kode IS NULL");
            default -> {
            }
        }
        if (!f.metode().isEmpty()) {
            where.append(" AND d.metode = ?");
            p.add(f.metode());
        }
        if (f.dari() != null) {
            where.append(" AND d.created_at >= ?");
            p.add(f.dari().atStartOfDay());
        }
        if (f.sampai() != null) {
            where.append(" AND d.created_at < ?");
            p.add(f.sampai().plusDays(1).atStartOfDay());
        }
        long total = jdbc.sql("SELECT COUNT(*) FROM diagnosa d JOIN users u ON u.id = d.user_id" + where)
                .params(p).query(Long.class).single();
        String sql = SELECT + where + " ORDER BY d.id DESC";
        List<Object> pp = new java.util.ArrayList<>(p);
        if (!semua) {
            sql += " LIMIT ? OFFSET ?";
            pp.add(FilterRiwayat.UKURAN);
            pp.add((f.halaman() - 1) * FilterRiwayat.UKURAN);
        }
        List<Diagnosa> isi = jdbc.sql(sql).params(pp).query(Diagnosa.class).list();
        return new FilterRiwayat.Halaman<>(isi, total, f.halaman(), semua ? (int) Math.max(total, 1) : FilterRiwayat.UKURAN);
    }

    // ---------- statistik ----------

    public record Frekuensi(String kode, String nama, long jumlah) {
    }

    /** Jumlah diagnosa per minggu (n minggu terakhir) dipisah per kategori hasil. */
    public record Tren(String minggu, long risiko, long normal, long lain) {
        public long total() {
            return risiko + normal + lain;
        }

        /** Label sumbu, mis. "3 Nov". */
        public String label() {
            return java.time.LocalDate.parse(minggu)
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("id-ID")));
        }

        public String tooltip() {
            return "Minggu " + label() + "\nRisiko: " + risiko + "\nNormal: " + normal
                    + (lain > 0 ? "\nTak tersimpulkan: " + lain : "") + "\nTotal: " + total();
        }
    }

    /** Gejala yang paling sering dipilih (metode keyakinan gejala). */
    public List<Frekuensi> gejalaTerbanyak(int n) {
        return jdbc.sql("""
                        SELECT gejala_kode AS kode, MAX(gejala_nama) AS nama, COUNT(*) AS jumlah
                        FROM diagnosa_jawaban GROUP BY gejala_kode ORDER BY jumlah DESC, kode LIMIT ?""")
                .param(n).query(Frekuensi.class).list();
    }

    /** Rule yang paling sering terpicu pada diagnosa data klinis. */
    public List<Frekuensi> ruleTerbanyak(int n) {
        return jdbc.sql("""
                        SELECT rule_kode AS kode, MAX(penyakit_kode) AS nama, COUNT(*) AS jumlah
                        FROM diagnosa_rule GROUP BY rule_kode ORDER BY jumlah DESC, kode LIMIT ?""")
                .param(n).query(Frekuensi.class).list();
    }

    /** Tren n minggu terakhir; minggu tanpa diagnosa diisi 0 agar sumbu waktu tidak bolong. */
    public List<Tren> trenMingguan(int minggu) {
        java.util.Map<String, Tren> ada = new java.util.HashMap<>();
        for (Tren t : trenMentah(minggu)) ada.put(t.minggu(), t);
        java.time.LocalDate senin = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).minusWeeks(minggu - 1L);
        List<Tren> out = new java.util.ArrayList<>();
        for (int i = 0; i < minggu; i++) {
            String k = senin.plusWeeks(i).toString();
            out.add(ada.getOrDefault(k, new Tren(k, 0, 0, 0)));
        }
        return out;
    }

    private List<Tren> trenMentah(int minggu) {
        return jdbc.sql("""
                        SELECT DATE_FORMAT(DATE_SUB(DATE(created_at), INTERVAL WEEKDAY(created_at) DAY), '%Y-%m-%d') AS minggu,
                               SUM(hasil_kategori = 'RISIKO') AS risiko,
                               SUM(hasil_kategori = 'NORMAL') AS normal,
                               SUM(hasil_kategori IS NULL) AS lain
                        FROM diagnosa
                        WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? WEEK)
                        GROUP BY minggu ORDER BY minggu""")
                .param(minggu).query(Tren.class).list();
    }

    public record RuleRow(String ruleKode, String kondisi, String penyakitKode, BigDecimal cf, String keterangan) {
    }

    public void insertKlinis(long diagnosaId, DataKlinis k) {
        jdbc.sql("""
                        INSERT INTO diagnosa_klinis (diagnosa_id, age, sex, chest_pain, resting_bp, cholesterol, fasting_bs,
                                                     resting_ecg, max_hr, exercise_angina, oldpeak, st_slope)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(diagnosaId, k.age(), k.sex(), k.chestPain(), k.restingBp(), k.cholesterol(), k.fastingBs(),
                        k.restingEcg(), k.maxHr(), k.exerciseAngina(), k.oldpeak(), k.stSlope())
                .update();
    }

    public void insertRule(long diagnosaId, int urutan, String ruleKode, String kondisi, String penyakitKode,
                           BigDecimal cf, String keterangan) {
        jdbc.sql("""
                        INSERT INTO diagnosa_rule (diagnosa_id, urutan, rule_kode, kondisi, penyakit_kode, cf, keterangan)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""")
                .params(diagnosaId, urutan, ruleKode, kondisi, penyakitKode, cf, keterangan).update();
    }

    public Optional<DataKlinis> klinis(long diagnosaId) {
        return jdbc.sql("""
                        SELECT age, sex, chest_pain, resting_bp, cholesterol, fasting_bs, resting_ecg, max_hr,
                               exercise_angina, oldpeak, st_slope
                        FROM diagnosa_klinis WHERE diagnosa_id = ?""")
                .param(diagnosaId).query(DataKlinis.class).optional();
    }

    public List<RuleRow> rules(long diagnosaId) {
        return jdbc.sql("SELECT rule_kode, kondisi, penyakit_kode, cf, keterangan FROM diagnosa_rule WHERE diagnosa_id = ? ORDER BY urutan")
                .param(diagnosaId).query(RuleRow.class).list();
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM diagnosa WHERE id = ?").param(id).update();
    }

    public int deleteMilik(long id, long userId) {
        return jdbc.sql("DELETE FROM diagnosa WHERE id = ? AND user_id = ?").params(id, userId).update();
    }

    // ---------- baca ----------

    public Optional<Diagnosa> findById(long id) {
        return jdbc.sql(SELECT + " WHERE d.id = ?").param(id).query(Diagnosa.class).optional();
    }

    /** Diagnosa terakhir pengguna yang memiliki data diri (untuk mengisi ulang formulir). */
    public Optional<Diagnosa> dataDiriTerakhir(long userId) {
        return jdbc.sql(SELECT + " WHERE d.user_id = ? AND d.tanggal_lahir IS NOT NULL ORDER BY d.id DESC LIMIT 1")
                .param(userId).query(Diagnosa.class).optional();
    }

    public List<Diagnosa> findByUser(long userId, int limit) {
        return jdbc.sql(SELECT + " WHERE d.user_id = ? ORDER BY d.id DESC LIMIT ?").params(userId, limit)
                .query(Diagnosa.class).list();
    }

    public List<Diagnosa> findAll(int limit) {
        return jdbc.sql(SELECT + " ORDER BY d.id DESC LIMIT ?").param(limit).query(Diagnosa.class).list();
    }

    public List<JawabanRow> jawaban(long diagnosaId) {
        return jdbc.sql("SELECT gejala_kode, gejala_nama, cf_user FROM diagnosa_jawaban WHERE diagnosa_id = ? ORDER BY urutan")
                .param(diagnosaId).query(JawabanRow.class).list();
    }

    public List<HasilRow> hasil(long diagnosaId) {
        return jdbc.sql("SELECT penyakit_kode, penyakit_nama, kategori, cf FROM diagnosa_hasil WHERE diagnosa_id = ? ORDER BY urutan")
                .param(diagnosaId).query(HasilRow.class).list();
    }

    public List<DetailRow> detail(long diagnosaId) {
        return jdbc.sql("SELECT penyakit_kode, gejala_kode, gejala_nama, cf_user, cf_pakar FROM diagnosa_detail WHERE diagnosa_id = ? ORDER BY urutan")
                .param(diagnosaId).query(DetailRow.class).list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM diagnosa").query(Long.class).single();
    }

    public long countByUser(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM diagnosa WHERE user_id = ?").param(userId).query(Long.class).single();
    }

    public List<Sebaran> sebaran() {
        return jdbc.sql("""
                        SELECT COALESCE(hasil_nama, 'Tidak dapat disimpulkan') AS nama,
                               COALESCE(MAX(hasil_kategori), '-') AS kategori, COUNT(*) AS jumlah
                        FROM diagnosa GROUP BY hasil_nama ORDER BY jumlah DESC""")
                .query(Sebaran.class).list();
    }
}
