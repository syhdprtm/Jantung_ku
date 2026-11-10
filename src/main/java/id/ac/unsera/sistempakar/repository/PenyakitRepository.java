package id.ac.unsera.sistempakar.repository;

import id.ac.unsera.sistempakar.model.Penyakit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PenyakitRepository {

    private static final String SELECT = """
            SELECT p.id, p.kode, p.nama, p.kategori, p.keterangan, p.saran,
                   (SELECT COUNT(*) FROM bobot b WHERE b.penyakit_id = p.id) AS jml_gejala,
                   (SELECT COUNT(*) FROM rules r WHERE r.penyakit_id = p.id) AS jml_rule
            FROM penyakit p
            """;

    private final JdbcClient jdbc;

    public PenyakitRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Penyakit> findAll() {
        return jdbc.sql(SELECT + " ORDER BY p.kode").query(Penyakit.class).list();
    }

    public Optional<Penyakit> findById(long id) {
        return jdbc.sql(SELECT + " WHERE p.id = ?").param(id).query(Penyakit.class).optional();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM penyakit").query(Long.class).single();
    }

    public long insert(String kode, String nama, String kategori, String keterangan, String saran) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO penyakit (kode, nama, kategori, keterangan, saran) VALUES (?, ?, ?, ?, ?)")
                .params(kode, nama, kategori, keterangan, saran).update(kh);
        return kh.getKey().longValue();
    }

    public void update(long id, String kode, String nama, String kategori, String keterangan, String saran) {
        jdbc.sql("UPDATE penyakit SET kode = ?, nama = ?, kategori = ?, keterangan = ?, saran = ? WHERE id = ?")
                .params(kode, nama, kategori, keterangan, saran, id).update();
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM penyakit WHERE id = ?").param(id).update();
    }
}
