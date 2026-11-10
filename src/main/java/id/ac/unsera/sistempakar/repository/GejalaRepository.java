package id.ac.unsera.sistempakar.repository;

import id.ac.unsera.sistempakar.model.Gejala;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class GejalaRepository {

    private final JdbcClient jdbc;

    public GejalaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Baris(Long id, String kode, String nama, String keterangan) {
    }

    private record Bobot(Long gejalaId, Long penyakitId, BigDecimal cfPakar) {
    }

    /** Semua gejala (urut kode) beserta bobot CF Pakar per penyakit. */
    public List<Gejala> findAll() {
        List<Baris> baris = jdbc.sql("SELECT id, kode, nama, keterangan FROM gejala ORDER BY kode")
                .query(Baris.class).list();
        Map<Long, Map<Long, BigDecimal>> bobot = new HashMap<>();
        for (Bobot b : jdbc.sql("SELECT gejala_id, penyakit_id, cf_pakar FROM bobot").query(Bobot.class).list()) {
            bobot.computeIfAbsent(b.gejalaId(), k -> new LinkedHashMap<>()).put(b.penyakitId(), b.cfPakar());
        }
        return baris.stream()
                .map(r -> new Gejala(r.id(), r.kode(), r.nama(), r.keterangan(), bobot.getOrDefault(r.id(), Map.of())))
                .toList();
    }

    public Optional<Gejala> findById(long id) {
        return findAll().stream().filter(g -> g.id() == id).findFirst();
    }

    public boolean exists(long id) {
        return jdbc.sql("SELECT COUNT(*) FROM gejala WHERE id = ?").param(id).query(Long.class).single() > 0;
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM gejala").query(Long.class).single();
    }

    public long insert(String kode, String nama, String keterangan) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO gejala (kode, nama, keterangan) VALUES (?, ?, ?)")
                .params(kode, nama, keterangan).update(kh);
        return kh.getKey().longValue();
    }

    public void update(long id, String kode, String nama, String keterangan) {
        jdbc.sql("UPDATE gejala SET kode = ?, nama = ?, keterangan = ? WHERE id = ?")
                .params(kode, nama, keterangan, id).update();
    }

    /** Ganti seluruh bobot satu gejala; nilai 0 tidak disimpan. */
    public void simpanBobot(long gejalaId, Map<Long, BigDecimal> bobot) {
        jdbc.sql("DELETE FROM bobot WHERE gejala_id = ?").param(gejalaId).update();
        bobot.forEach((penyakitId, cf) -> {
            if (cf.signum() > 0) {
                jdbc.sql("INSERT INTO bobot (gejala_id, penyakit_id, cf_pakar) VALUES (?, ?, ?)")
                        .params(gejalaId, penyakitId, cf).update();
            }
        });
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM gejala WHERE id = ?").param(id).update();
    }
}
