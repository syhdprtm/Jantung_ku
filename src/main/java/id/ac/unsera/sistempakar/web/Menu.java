package id.ac.unsera.sistempakar.web;

import java.util.List;

/**
 * Item navigasi.
 *
 * @param utama tampil di bottom-nav mobile; sisanya masuk menu "Lainnya"
 */
public record Menu(String href, String label, String ikon, boolean utama) {

    public boolean aktif(String path) {
        if (path == null) return false;
        if (path.equals(href)) return true;
        boolean beranda = "/admin".equals(href) || "/pakar".equals(href);
        return !beranda && path.startsWith(href + "/");
    }

    /** Administrator: mengelola pengguna dan memantau seluruh diagnosa. */
    static final List<Menu> ADMIN = List.of(
            new Menu("/admin", "Beranda", "home", true),
            new Menu("/admin/users", "Pengguna", "users", true),
            new Menu("/admin/riwayat", "Riwayat", "clock", true),
            new Menu("/admin/audit", "Audit log", "shield", false),
            new Menu("/diagnosa", "Uji diagnosa", "pulse", true));

    /** Pakar: mengelola basis pengetahuan, memvalidasi, dan mengevaluasi akurasi. */
    static final List<Menu> PAKAR = List.of(
            new Menu("/pakar", "Beranda", "home", true),
            new Menu("/pakar/gejala", "Gejala", "list", true),
            new Menu("/pakar/penyakit", "Hipotesis", "layers", false),
            new Menu("/pakar/rules", "Rule base", "branch", true),
            new Menu("/pakar/evaluasi", "Evaluasi", "calc", true),
            new Menu("/diagnosa", "Uji diagnosa", "pulse", false));

    static final List<Menu> USER = List.of(
            new Menu("/dashboard", "Beranda", "home", true),
            new Menu("/diagnosa", "Diagnosa", "pulse", true),
            new Menu("/riwayat", "Riwayat", "clock", true),
            new Menu("/profil", "Profil", "user", true));
}
