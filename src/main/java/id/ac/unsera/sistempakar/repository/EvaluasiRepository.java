package id.ac.unsera.sistempakar.repository;

import id.ac.unsera.sistempakar.model.Metrik;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class EvaluasiRepository {

    private final JdbcClient jdbc;

    public EvaluasiRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Evaluasi(Long id, String namaFile, String namaUser, int jumlah, int dilewati, LocalDateTime createdAt) {
    }

    public record RuleStat(String ruleKode, int terpicu, int benar, String penyakitKode, String kategori, BigDecimal cf) {
        public BigDecimal getPresisi() {
            return terpicu == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(benar).divide(BigDecimal.valueOf(terpicu), 6, java.math.RoundingMode.HALF_UP);
        }
    }

    public record Baris(int baris, String metode, int aktual, Integer prediksi, BigDecimal cf, String ruleKode, String data) {
    }

    private static final String SELECT = """
            SELECT e.id, e.nama_file, u.nama AS nama_user, e.jumlah, e.dilewati, e.created_at
            FROM evaluasi e LEFT JOIN users u ON u.id = e.user_id
            """;

    public long insert(String namaFile, Long userId, int jumlah, int dilewati) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO evaluasi (nama_file, user_id, jumlah, dilewati) VALUES (?, ?, ?, ?)")
                .params(namaFile, userId, jumlah, dilewati).update(kh);
        return kh.getKey().longValue();
    }

    public void insertMetrik(long id, String metode, int tp, int tn, int fp, int fn, int takTercakup) {
        jdbc.sql("INSERT INTO evaluasi_metrik (evaluasi_id, metode, tp, tn, fp, fn, tak_tercakup) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(id, metode, tp, tn, fp, fn, takTercakup).update();
    }

    public void insertRule(long id, String ruleKode, int terpicu, int benar) {
        jdbc.sql("INSERT INTO evaluasi_rule (evaluasi_id, rule_kode, terpicu, benar) VALUES (?, ?, ?, ?)")
                .params(id, ruleKode, terpicu, benar).update();
    }

    public void insertBaris(long id, int baris, String metode, int aktual, Integer prediksi, BigDecimal cf,
                            String ruleKode, String data) {
        jdbc.sql("""
                        INSERT INTO evaluasi_baris (evaluasi_id, baris, metode, aktual, prediksi, cf, rule_kode, data)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(id, baris, metode, aktual, prediksi, cf, ruleKode, data).update();
    }

    public List<Evaluasi> findAll() {
        return jdbc.sql(SELECT + " ORDER BY e.id DESC").query(Evaluasi.class).list();
    }

    public Optional<Evaluasi> findById(long id) {
        return jdbc.sql(SELECT + " WHERE e.id = ?").param(id).query(Evaluasi.class).optional();
    }

    public List<Metrik> metrik(long id) {
        return jdbc.sql("""
                        SELECT evaluasi_id, metode, tp, tn, fp, fn, tak_tercakup FROM evaluasi_metrik
                        WHERE evaluasi_id = ? ORDER BY FIELD(metode, 'KLINIS', 'GEJALA')""")
                .param(id).query(Metrik.class).list();
    }

    public List<Metrik> metrikSemua() {
        return jdbc.sql("SELECT evaluasi_id, metode, tp, tn, fp, fn, tak_tercakup FROM evaluasi_metrik")
                .query(Metrik.class).list();
    }

    public List<RuleStat> rules(long id) {
        return jdbc.sql("""
                        SELECT er.rule_kode, er.terpicu, er.benar, p.kode AS penyakit_kode, p.kategori, r.cf
                        FROM evaluasi_rule er
                        LEFT JOIN rules r ON r.kode = er.rule_kode
                        LEFT JOIN penyakit p ON p.id = r.penyakit_id
                        WHERE er.evaluasi_id = ?
                        ORDER BY CAST(SUBSTRING(er.rule_kode, 2) AS UNSIGNED), er.rule_kode""")
                .param(id).query(RuleStat.class).list();
    }

    public List<Baris> baris(long id) {
        return jdbc.sql("SELECT baris, metode, aktual, prediksi, cf, rule_kode, data FROM evaluasi_baris WHERE evaluasi_id = ? ORDER BY metode DESC, baris")
                .param(id).query(Baris.class).list();
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM evaluasi WHERE id = ?").param(id).update();
    }
}
