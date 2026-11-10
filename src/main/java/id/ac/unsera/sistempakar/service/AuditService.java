package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.repository.AuditRepository;
import id.ac.unsera.sistempakar.web.Peran;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Mencatat jejak audit. Dipanggil di dalam transaksi operasi yang diaudit, sehingga bila
 * operasinya gagal (rollback), catatan auditnya juga ikut batal.
 */
@Service
public class AuditService {

    public static final String TAMBAH = "TAMBAH";
    public static final String UBAH = "UBAH";
    public static final String HAPUS = "HAPUS";
    public static final String JALANKAN = "JALANKAN";

    private final AuditRepository repo;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    public void catat(String aksi, String entitas, String kode, String sebelum, String sesudah) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String user = auth == null || auth.getName() == null ? "sistem" : auth.getName();
        String role = auth == null ? "-" : Peran.dari(auth);
        repo.insert(potong(user, 30), role.isEmpty() ? "-" : role, aksi, entitas, potong(kode, 60),
                potong(sebelum, 4000), potong(sesudah, 4000));
    }

    // Aksi untuk entitas SESI (aktivitas login)
    public static final String MASUK = "MASUK";
    public static final String GAGAL_MASUK = "GAGAL_MASUK";
    public static final String KELUAR = "KELUAR";
    public static final String TERKUNCI = "TERKUNCI";
    public static final String SESI = "SESI";

    /**
     * Mencatat aktivitas login. Dipanggil saat SecurityContext belum/tidak lagi berisi pengguna,
     * jadi username dan role diberikan langsung. Kode berisi alamat IP.
     */
    public void catatSesi(String username, String role, String aksi, String ip, String keterangan) {
        repo.insert(potong(username == null || username.isBlank() ? "(kosong)" : username, 30),
                role == null || role.isBlank() ? "-" : role, aksi, SESI,
                potong(ip == null ? "-" : ip, 60), null, potong(keterangan, 4000));
    }

    /** Ringkasan satu diagnosa untuk catatan audit. */
    public static String ringkas(id.ac.unsera.sistempakar.model.Diagnosa d) {
        return "#" + d.id() + " · " + d.namaPasien() + " (@" + d.username() + ") · "
                + d.getMetodeLabel().toLowerCase() + " · "
                + (d.isTersimpulkan() ? d.hasilNama() + " " + id.ac.unsera.sistempakar.web.Fmt.persenS(d.hasilCf()) : "tak tersimpulkan");
    }

    private static String potong(String s, int maks) {
        if (s == null) return null;
        return s.length() <= maks ? s : s.substring(0, maks - 1) + "…";
    }
}
