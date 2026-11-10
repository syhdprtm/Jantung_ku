package id.ac.unsera.sistempakar.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AuditRepository {

    public static final int UKURAN = 25;

    private final JdbcClient jdbc;

    public AuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Audit(Long id, LocalDateTime waktu, String username, String role, String aksi, String entitas,
                        String kode, String sebelum, String sesudah) {
        public boolean isAdaPerubahan() {
            return sebelum != null || sesudah != null;
        }
    }

    public void insert(String username, String role, String aksi, String entitas, String kode, String sebelum, String sesudah) {
        jdbc.sql("INSERT INTO audit_log (username, role, aksi, entitas, kode, sebelum, sesudah) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(username, role, aksi, entitas, kode, sebelum, sesudah).update();
    }

    /** Cari jejak audit (terbaru dulu) dengan filter entitas & kata kunci, berhalaman. */
    public FilterRiwayat.Halaman<Audit> cari(String entitas, String q, int halaman) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> p = new ArrayList<>();
        if (entitas != null && !entitas.isBlank()) {
            where.append(" AND entitas = ?");
            p.add(entitas);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (username LIKE ? OR kode LIKE ? OR sebelum LIKE ? OR sesudah LIKE ?)");
            String like = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            for (int i = 0; i < 4; i++) p.add(like);
        }
        long total = jdbc.sql("SELECT COUNT(*) FROM audit_log" + where).params(p).query(Long.class).single();
        List<Object> pp = new ArrayList<>(p);
        pp.add(UKURAN);
        pp.add((Math.max(halaman, 1) - 1) * UKURAN);
        List<Audit> isi = jdbc.sql("SELECT * FROM audit_log" + where + " ORDER BY id DESC LIMIT ? OFFSET ?")
                .params(pp).query(Audit.class).list();
        return new FilterRiwayat.Halaman<>(isi, total, Math.max(halaman, 1), UKURAN);
    }

    /** Jejak terbaru untuk entitas tertentu (mis. basis pengetahuan di dashboard pakar). */
    public List<Audit> terbaru(int n, String... entitas) {
        String in = String.join(",", java.util.Collections.nCopies(entitas.length, "?"));
        List<Object> p = new ArrayList<>(List.of((Object[]) entitas));
        p.add(n);
        return jdbc.sql("SELECT * FROM audit_log WHERE entitas IN (" + in + ") ORDER BY id DESC LIMIT ?")
                .params(p).query(Audit.class).list();
    }
}
