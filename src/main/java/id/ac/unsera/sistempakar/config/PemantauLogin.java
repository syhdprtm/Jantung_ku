package id.ac.unsera.sistempakar.config;

import id.ac.unsera.sistempakar.model.Pengguna;
import id.ac.unsera.sistempakar.repository.PenggunaRepository;
import id.ac.unsera.sistempakar.service.AuditService;
import id.ac.unsera.sistempakar.web.Peran;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Menangani hasil login dan logout untuk kedua pintu masuk (pengguna & petugas):
 * <ul>
 *   <li>menghitung percobaan gagal dan mengunci akun sementara,</li>
 *   <li>mencatat aktivitas login petugas di audit log (entitas SESI),</li>
 *   <li>memperpendek sesi petugas.</li>
 * </ul>
 * Logika ada di handler (bukan event listener) karena handler tahu dari halaman mana login dicoba.
 */
@Component
public class PemantauLogin {

    private static final Logger log = LoggerFactory.getLogger(PemantauLogin.class);

    /** Sesi petugas berakhir setelah 30 menit tanpa aktivitas. */
    public static final int SESI_PETUGAS_DETIK = 30 * 60;

    private final PenggunaRepository repo;
    private final AuditService audit;

    public PemantauLogin(PenggunaRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    private static boolean petugas(String role) {
        return "ADMIN".equals(role) || "PAKAR".equals(role);
    }

    /** Audit tidak boleh menggagalkan login; kesalahan pencatatan cukup masuk log. */
    private void catat(String username, String role, String aksi, String ip, String ket) {
        try {
            audit.catatSesi(username, role, aksi, ip, ket);
        } catch (RuntimeException e) {
            log.warn("Gagal mencatat audit sesi {} untuk {}: {}", aksi, username, e.getMessage());
        }
    }

    public AuthenticationSuccessHandler sukses(boolean pintuPetugas) {
        return (req, res, auth) -> {
            repo.resetGagal(auth.getName());
            String role = Peran.dari(auth);
            if (pintuPetugas) {
                req.getSession().setMaxInactiveInterval(SESI_PETUGAS_DETIK);
                catat(auth.getName(), role, AuditService.MASUK, req.getRemoteAddr(), "masuk lewat halaman petugas");
            }
            res.sendRedirect(req.getContextPath() + Peran.beranda(role));
        };
    }

    public AuthenticationFailureHandler gagal(boolean pintuPetugas) {
        String halaman = pintuPetugas ? SecurityConfig.MASUK_PETUGAS : "/login";
        return (req, res, ex) -> {
            String username = req.getParameter("username") == null ? "" : req.getParameter("username").trim();
            String ip = req.getRemoteAddr();
            Optional<Pengguna> akun = username.isEmpty() ? Optional.empty() : repo.findByUsername(username);
            // Hanya akun yang memang boleh masuk lewat pintu ini yang dihitung; akun petugas yang
            // mencoba /login (atau sebaliknya) selalu ditolak tanpa memeriksa password.
            Optional<Pengguna> sah = akun.filter(u -> pintuPetugas == u.isPetugas());
            String role = akun.map(Pengguna::role).orElse("-");

            if (ex instanceof LockedException) {
                if (pintuPetugas) catat(username, role, AuditService.GAGAL_MASUK, ip, "ditolak: akun sedang terkunci");
                res.sendRedirect(req.getContextPath() + halaman + "?terkunci");
                return;
            }
            boolean baruTerkunci = sah.isPresent() && repo.catatGagal(username);
            if (baruTerkunci) {
                catat(username, role, AuditService.TERKUNCI, ip, PenggunaRepository.BATAS_GAGAL
                        + " kali password salah, dikunci " + PenggunaRepository.MENIT_KUNCI + " menit");
            } else if (pintuPetugas) {
                catat(username, role, AuditService.GAGAL_MASUK, ip,
                        sah.isPresent() ? "password salah" : "akun tidak dikenal atau bukan petugas");
            }
            res.sendRedirect(req.getContextPath() + halaman + (baruTerkunci ? "?terkunci" : "?gagal"));
        };
    }

    /** Satu /logout untuk semua; petugas kembali ke halaman masuk petugas. */
    public LogoutSuccessHandler keluar() {
        return (req, res, auth) -> {
            String role = Peran.dari(auth);
            if (auth != null && petugas(role)) {
                catat(auth.getName(), role, AuditService.KELUAR, ip(req), "keluar");
                res.sendRedirect(req.getContextPath() + SecurityConfig.MASUK_PETUGAS + "?keluar");
            } else {
                res.sendRedirect(req.getContextPath() + "/login?keluar");
            }
        };
    }

    private static String ip(HttpServletRequest req) {
        return req.getRemoteAddr();
    }

    /** Dipakai halaman: apakah Authentication milik petugas. */
    public static boolean isPetugas(Authentication auth) {
        return auth != null && petugas(Peran.dari(auth));
    }
}
