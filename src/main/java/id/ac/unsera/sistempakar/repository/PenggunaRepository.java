package id.ac.unsera.sistempakar.repository;

import id.ac.unsera.sistempakar.model.Pengguna;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PenggunaRepository {

    private static final String SELECT = """
            SELECT u.id, u.nama, u.username, u.password, u.role, u.created_at,
                   (SELECT COUNT(*) FROM diagnosa d WHERE d.user_id = u.id) AS jml_diagnosa,
                   u.gagal_login, u.terkunci_sampai
            FROM users u
            """;

    private final JdbcClient jdbc;

    public PenggunaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Pengguna> findByUsername(String username) {
        return jdbc.sql(SELECT + " WHERE u.username = ?").param(username).query(Pengguna.class).optional();
    }

    public Optional<Pengguna> findById(long id) {
        return jdbc.sql(SELECT + " WHERE u.id = ?").param(id).query(Pengguna.class).optional();
    }

    public List<Pengguna> findAll() {
        return jdbc.sql(SELECT + " ORDER BY u.role, u.nama").query(Pengguna.class).list();
    }

    public boolean existsByUsername(String username) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE username = ?").param(username)
                .query(Long.class).single() > 0;
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM users").query(Long.class).single();
    }

    /** Nama akun pakar pertama (untuk blok tanda tangan laporan PDF). */
    public Optional<String> namaPakarUtama() {
        return jdbc.sql("SELECT nama FROM users WHERE role = 'PAKAR' ORDER BY id LIMIT 1").query(String.class).optional();
    }

    public long countByRole(String role) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE role = ?").param(role).query(Long.class).single();
    }

    public long insert(String nama, String username, String passwordHash, String role) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO users (nama, username, password, role) VALUES (?, ?, ?, ?)")
                .params(nama, username, passwordHash, role).update(kh);
        return kh.getKey().longValue();
    }

    /** Batas percobaan login gagal berturut-turut sebelum akun dikunci sementara. */
    public static final int BATAS_GAGAL = 5;
    /** Lama kunci sementara, dalam menit. */
    public static final int MENIT_KUNCI = 15;

    /**
     * Catat satu login gagal. Bila mencapai {@link #BATAS_GAGAL}, akun dikunci selama
     * {@link #MENIT_KUNCI} menit dan hitungan dimulai lagi dari nol.
     *
     * @return true bila percobaan ini membuat akun terkunci
     */
    public boolean catatGagal(String username) {
        int n = jdbc.sql("UPDATE users SET gagal_login = gagal_login + 1 WHERE username = ?").param(username).update();
        if (n == 0) return false;
        return jdbc.sql("UPDATE users SET gagal_login = 0, terkunci_sampai = NOW() + INTERVAL " + MENIT_KUNCI
                        + " MINUTE WHERE username = ? AND gagal_login >= ?")
                .params(username, BATAS_GAGAL).update() > 0;
    }

    public void resetGagal(String username) {
        jdbc.sql("UPDATE users SET gagal_login = 0, terkunci_sampai = NULL WHERE username = ? AND (gagal_login > 0 OR terkunci_sampai IS NOT NULL)")
                .param(username).update();
    }

    public int bukaKunci(long id) {
        return jdbc.sql("UPDATE users SET gagal_login = 0, terkunci_sampai = NULL WHERE id = ?").param(id).update();
    }

    public void catatPersetujuan(long id) {
        jdbc.sql("UPDATE users SET setuju_privasi_at = NOW() WHERE id = ?").param(id).update();
    }

    public void update(long id, String nama, String username, String role) {
        jdbc.sql("UPDATE users SET nama = ?, username = ?, role = ? WHERE id = ?")
                .params(nama, username, role, id).update();
    }

    public void updateNama(long id, String nama) {
        jdbc.sql("UPDATE users SET nama = ? WHERE id = ?").params(nama, id).update();
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.sql("UPDATE users SET password = ? WHERE id = ?").params(passwordHash, id).update();
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM users WHERE id = ?").param(id).update();
    }
}
