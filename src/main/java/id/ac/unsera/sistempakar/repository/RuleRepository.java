package id.ac.unsera.sistempakar.repository;

import id.ac.unsera.sistempakar.model.Atribut;
import id.ac.unsera.sistempakar.model.Rule;
import id.ac.unsera.sistempakar.model.Syarat;
import id.ac.unsera.sistempakar.service.RuleEngine.RuleAktif;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RuleRepository {

    private static final String SELECT = """
            SELECT r.id, r.kode, r.kondisi, r.penyakit_id, r.cf, r.keterangan,
                   p.kode AS penyakit_kode, p.nama AS penyakit_nama, p.kategori AS penyakit_kategori
            FROM rules r LEFT JOIN penyakit p ON p.id = r.penyakit_id
            """;

    private final JdbcClient jdbc;

    public RuleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Rule> findAll() {
        return jdbc.sql(SELECT + " ORDER BY r.id").query(Rule.class).list();
    }

    public Optional<Rule> findById(long id) {
        return jdbc.sql(SELECT + " WHERE r.id = ?").param(id).query(Rule.class).optional();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM rules").query(Long.class).single();
    }

    public long insert(String kode, String kondisi, long penyakitId, BigDecimal cf, String keterangan) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO rules (kode, kondisi, penyakit_id, cf, keterangan) VALUES (?, ?, ?, ?, ?)")
                .params(kode, kondisi, penyakitId, cf, keterangan).update(kh);
        return kh.getKey().longValue();
    }

    public void update(long id, String kode, String kondisi, long penyakitId, BigDecimal cf, String keterangan) {
        jdbc.sql("UPDATE rules SET kode = ?, kondisi = ?, penyakit_id = ?, cf = ?, keterangan = ? WHERE id = ?")
                .params(kode, kondisi, penyakitId, cf, keterangan, id).update();
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM rules WHERE id = ?").param(id).update();
    }

    // ------------------------------------------------------------ syarat terstruktur

    private record SyaratRow(Long ruleId, String atribut, String operator, String nilai) {
    }

    private record AktifRow(Long id, String kode, String kondisi, String penyakitKode, String penyakitNama,
                            String kategori, String saran, BigDecimal cf, String keterangan) {
    }

    /** Ganti seluruh syarat satu rule. */
    public void simpanSyarat(long ruleId, List<Syarat> syarat) {
        jdbc.sql("DELETE FROM rule_syarat WHERE rule_id = ?").param(ruleId).update();
        int i = 0;
        for (Syarat s : syarat) {
            jdbc.sql("INSERT INTO rule_syarat (rule_id, urutan, atribut, operator, nilai) VALUES (?, ?, ?, ?, ?)")
                    .params(ruleId, i++, s.atribut().name(), s.operator(), s.nilai()).update();
        }
    }

    /** Rule yang belum punya syarat terstruktur (perlu diparse). */
    public List<Rule> findTanpaSyarat() {
        return jdbc.sql(SELECT + " WHERE NOT EXISTS (SELECT 1 FROM rule_syarat s WHERE s.rule_id = r.id) ORDER BY r.id")
                .query(Rule.class).list();
    }

    /** Syarat per id rule. */
    public Map<Long, List<Syarat>> semuaSyarat() {
        Map<Long, List<Syarat>> m = new HashMap<>();
        for (SyaratRow r : jdbc.sql("SELECT rule_id, atribut, operator, nilai FROM rule_syarat ORDER BY rule_id, urutan")
                .query(SyaratRow.class).list()) {
            m.computeIfAbsent(r.ruleId(), k -> new ArrayList<>())
                    .add(new Syarat(Atribut.valueOf(r.atribut()), r.operator(), r.nilai()));
        }
        return m;
    }

    /** Semua rule yang punya kesimpulan, lengkap dengan syarat, siap untuk mesin inferensi. */
    public List<RuleAktif> findAktif() {
        Map<Long, List<Syarat>> syarat = semuaSyarat();
        return jdbc.sql("""
                        SELECT r.id, r.kode, r.kondisi, p.kode AS penyakit_kode, p.nama AS penyakit_nama,
                               p.kategori, p.saran, r.cf, r.keterangan
                        FROM rules r JOIN penyakit p ON p.id = r.penyakit_id ORDER BY r.id""")
                .query(AktifRow.class).list().stream()
                .map(b -> new RuleAktif(b.id(), b.kode(), b.kondisi(), b.penyakitKode(), b.penyakitNama(),
                        b.kategori(), b.saran(), b.cf(), b.keterangan(), syarat.getOrDefault(b.id(), List.of())))
                .toList();
    }
}
