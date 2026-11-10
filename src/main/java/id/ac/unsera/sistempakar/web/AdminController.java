package id.ac.unsera.sistempakar.web;

import id.ac.unsera.sistempakar.model.Gejala;
import id.ac.unsera.sistempakar.model.Pengguna;
import id.ac.unsera.sistempakar.service.AuditService;
import id.ac.unsera.sistempakar.model.Penyakit;
import id.ac.unsera.sistempakar.model.Syarat;
import id.ac.unsera.sistempakar.service.RuleParser;
import id.ac.unsera.sistempakar.service.RuleValidator;
import id.ac.unsera.sistempakar.repository.DiagnosaRepository;
import id.ac.unsera.sistempakar.repository.GejalaRepository;
import id.ac.unsera.sistempakar.repository.PenggunaRepository;
import id.ac.unsera.sistempakar.repository.PenyakitRepository;
import id.ac.unsera.sistempakar.repository.RuleRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Semua halaman role ADMIN (dibatasi di SecurityConfig: /admin/**). */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String KODE_RE = "[A-Z0-9_.-]{1,15}";

    private final PenggunaRepository penggunaRepo;
    private final PenyakitRepository penyakitRepo;
    private final GejalaRepository gejalaRepo;
    private final RuleRepository ruleRepo;
    private final DiagnosaRepository diagnosaRepo;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final id.ac.unsera.sistempakar.service.EksporExcelService excelService;
    private final AuditService audit;
    private final id.ac.unsera.sistempakar.repository.AuditRepository auditRepo;

    private static String ringkas(Pengguna u) {
        return "@" + u.username() + " · " + u.nama() + " · " + u.role();
    }

    public AdminController(PenggunaRepository penggunaRepo, PenyakitRepository penyakitRepo,
                           GejalaRepository gejalaRepo, RuleRepository ruleRepo, DiagnosaRepository diagnosaRepo,
                           PasswordEncoder encoder, TransactionTemplate tx,
                           id.ac.unsera.sistempakar.service.EksporExcelService excelService,
                           AuditService audit, id.ac.unsera.sistempakar.repository.AuditRepository auditRepo) {
        this.excelService = excelService;
        this.audit = audit;
        this.auditRepo = auditRepo;
        this.penggunaRepo = penggunaRepo;
        this.penyakitRepo = penyakitRepo;
        this.gejalaRepo = gejalaRepo;
        this.ruleRepo = ruleRepo;
        this.diagnosaRepo = diagnosaRepo;
        this.encoder = encoder;
        this.tx = tx;
    }

    // ------------------------------------------------------------ util

    private static String s(String v) {
        return v == null ? "" : v.trim();
    }

    private static void cekKode(String kode, String label, List<String> errors) {
        if (kode.isEmpty()) errors.add("Kode " + label + " wajib diisi.");
        else if (!kode.matches(KODE_RE)) errors.add("Kode " + label + " hanya huruf kapital, angka, titik, - atau _ (maks. 15).");
    }

    /** Parse CF dari input (koma atau titik). Kosong = 0. null bila tidak valid / di luar 0..1. */
    static BigDecimal parseCf(String raw) {
        String t = s(raw).replace(',', '.');
        if (t.isEmpty()) return BigDecimal.ZERO;
        try {
            BigDecimal v = new BigDecimal(t);
            return v.signum() < 0 || v.compareTo(BigDecimal.ONE) > 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String tidakDitemukan(Model model, HttpServletResponse res) {
        res.setStatus(404);
        model.addAttribute("judul", "Data tidak ditemukan");
        model.addAttribute("pesan", "Data yang Anda cari tidak ada atau sudah dihapus.");
        return "pesan";
    }

    // ------------------------------------------------------------ dashboard

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("jmlUser", penggunaRepo.countByRole("USER"));
        model.addAttribute("jmlAdmin", penggunaRepo.countByRole("ADMIN"));
        model.addAttribute("jmlPakar", penggunaRepo.countByRole("PAKAR"));
        var tren = diagnosaRepo.trenMingguan(8);
        long maks = tren.stream().mapToLong(DiagnosaRepository.Tren::total).max().orElse(0);
        model.addAttribute("tren", tren);
        model.addAttribute("trenMax", Math.max(maks, 1));
        model.addAttribute("trenAda", maks > 0);
        model.addAttribute("jmlGejala", gejalaRepo.count());
        model.addAttribute("jmlPenyakit", penyakitRepo.count());
        model.addAttribute("jmlRule", ruleRepo.count());
        long total = diagnosaRepo.count();
        model.addAttribute("jmlDiagnosa", total);
        model.addAttribute("sebaran", diagnosaRepo.sebaran());
        model.addAttribute("terakhir", diagnosaRepo.findAll(6));
        return "admin/dashboard";
    }

    // ------------------------------------------------------------ manajemen user

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", penggunaRepo.findAll());
        return "admin/users";
    }

    private String formUser(Model model, String judul, String action, boolean baru, boolean diriSendiri,
                            Map<String, Object> form, List<String> errors) {
        model.addAttribute("judul", judul);
        model.addAttribute("action", action);
        model.addAttribute("baru", baru);
        model.addAttribute("diriSendiri", diriSendiri);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        return "admin/user-form";
    }

    @GetMapping("/users/tambah")
    public String tambahUser(Model model) {
        return formUser(model, "Tambah pengguna", "/admin/users/tambah", true, false, Map.of("role", "USER"), List.of());
    }

    @GetMapping("/users/{id}/edit")
    public String editUser(@PathVariable long id, @ModelAttribute(name = "me", binding = false) Pengguna me,
                           Model model, HttpServletResponse res) {
        return penggunaRepo.findById(id).map(u -> formUser(model, "Edit @" + u.username(), "/admin/users/" + id + "/edit",
                        false, u.id().equals(me.id()),
                        Map.of("nama", u.nama(), "username", u.username(), "role", u.role()), List.of()))
                .orElseGet(() -> tidakDitemukan(model, res));
    }

    @PostMapping({"/users/tambah", "/users/{id}/edit"})
    public String simpanUser(@PathVariable(required = false) Long id,
                             @ModelAttribute(name = "me", binding = false) Pengguna me,
                             @RequestParam(defaultValue = "") String nama,
                             @RequestParam(defaultValue = "") String username,
                             @RequestParam(defaultValue = "USER") String role,
                             @RequestParam(defaultValue = "") String password,
                             Model model, RedirectAttributes ra, HttpServletResponse res) {
        Pengguna target = null;
        if (id != null) {
            target = penggunaRepo.findById(id).orElse(null);
            if (target == null) return tidakDitemukan(model, res);
        }
        boolean diriSendiri = target != null && target.id().equals(me.id());
        nama = s(nama);
        username = diriSendiri ? target.username() : s(username); // username sendiri dikunci agar sesi tetap valid
        role = diriSendiri ? "ADMIN" : (java.util.Set.of("ADMIN", "PAKAR").contains(role) ? role : "USER"); // admin tidak bisa menurunkan dirinya
        List<String> errors = new ArrayList<>();
        if (nama.isEmpty()) errors.add("Nama lengkap wajib diisi.");
        if (!username.matches(PublicController.USERNAME_RE)) errors.add("Username harus 3–30 karakter dan hanya berisi huruf, angka, titik, atau garis bawah.");
        if (id == null && password.length() < 6) errors.add("Password minimal 6 karakter.");
        if (id != null && !password.isEmpty() && password.length() < 6) errors.add("Password baru minimal 6 karakter.");
        if (errors.isEmpty()) {
            try {
                final String fNama = nama, fUser = username, fRole = role, fPass = password;
                tx.executeWithoutResult(st -> {
                    String sebelum = id == null ? null : penggunaRepo.findById(id).map(AdminController::ringkas).orElse(null);
                    if (id == null) {
                        penggunaRepo.insert(fNama, fUser, encoder.encode(fPass), fRole);
                    } else {
                        penggunaRepo.update(id, fNama, fUser, fRole);
                        if (!fPass.isEmpty()) penggunaRepo.updatePassword(id, encoder.encode(fPass));
                    }
                    audit.catat(id == null ? AuditService.TAMBAH : AuditService.UBAH, "PENGGUNA", "@" + fUser, sebelum,
                            "@" + fUser + " · " + fNama + " · " + fRole + (id != null && !fPass.isEmpty() ? " · password direset" : ""));
                });
                ra.addFlashAttribute("sukses", "Pengguna @" + username + (id == null ? " ditambahkan." : " diperbarui."));
                return "redirect:/admin/users";
            } catch (DuplicateKeyException e) {
                errors.add("Username sudah dipakai.");
            }
        }
        res.setStatus(400);
        return formUser(model, id == null ? "Tambah pengguna" : "Edit pengguna",
                id == null ? "/admin/users/tambah" : "/admin/users/" + id + "/edit", id == null, diriSendiri,
                Map.of("nama", nama, "username", username, "role", role), errors);
    }

    @PostMapping("/users/{id}/hapus")
    public String hapusUser(@PathVariable long id, @ModelAttribute(name = "me", binding = false) Pengguna me,
                            RedirectAttributes ra) {
        if (me.id() == id) {
            ra.addFlashAttribute("gagal", "Anda tidak dapat menghapus akun Anda sendiri.");
            return "redirect:/admin/users";
        }
        Boolean ok = tx.execute(st -> {
            var u = penggunaRepo.findById(id);
            if (u.isEmpty() || penggunaRepo.delete(id) == 0) return false;
            audit.catat(AuditService.HAPUS, "PENGGUNA", "@" + u.get().username(), ringkas(u.get())
                    + " · " + u.get().jmlDiagnosa() + " diagnosa ikut terhapus", null);
            return true;
        });
        boolean n = Boolean.TRUE.equals(ok);
        ra.addFlashAttribute(n ? "sukses" : "gagal", n ? "Pengguna dihapus beserta seluruh riwayat pemeriksaannya." : "Data tidak ditemukan atau sudah dihapus.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/buka-kunci")
    public String bukaKunci(@PathVariable long id, RedirectAttributes ra) {
        var u = penggunaRepo.findById(id);
        boolean ok = u.isPresent() && penggunaRepo.bukaKunci(id) > 0;
        if (ok) audit.catat(AuditService.UBAH, "PENGGUNA", "@" + u.get().username(), "terkunci", "kunci dibuka oleh administrator");
        ra.addFlashAttribute(ok ? "sukses" : "gagal", ok ? "Kunci akun @" + u.get().username() + " dibuka. Pengguna dapat masuk kembali."
                : "Data tidak ditemukan atau sudah dihapus.");
        return "redirect:/admin/users";
    }

    // ------------------------------------------------------------ riwayat semua user

    @GetMapping("/riwayat")
    public String riwayat(@RequestParam(required = false) String q, @RequestParam(required = false) String hasil,
                          @RequestParam(required = false) String metode, @RequestParam(required = false) String dari,
                          @RequestParam(required = false) String sampai, @RequestParam(required = false) Integer halaman,
                          Model model) {
        var f = id.ac.unsera.sistempakar.repository.FilterRiwayat.dari(q, hasil, metode, dari, sampai, halaman);
        model.addAttribute("f", f);
        model.addAttribute("hal", diagnosaRepo.cari(f, null, false));
        model.addAttribute("adminView", true);
        model.addAttribute("basis", "/admin/riwayat");
        return "riwayat";
    }

    // ------------------------------------------------------------ audit log

    @GetMapping("/audit")
    public String audit(@RequestParam(required = false) String entitas, @RequestParam(required = false) String q,
                        @RequestParam(required = false) Integer halaman, Model model) {
        String e = entitas != null && java.util.Set.of("GEJALA", "PENYAKIT", "RULE", "PENGGUNA", "DIAGNOSA", "EVALUASI", "SESI").contains(entitas) ? entitas : "";
        String kata = q == null ? "" : q.trim();
        model.addAttribute("entitas", e);
        model.addAttribute("daftarEntitas", java.util.List.of("GEJALA", "PENYAKIT", "RULE", "PENGGUNA", "DIAGNOSA", "EVALUASI", "SESI"));
        model.addAttribute("q", kata);
        model.addAttribute("hal", auditRepo.cari(e, kata, halaman == null ? 1 : halaman));
        StringBuilder qs = new StringBuilder();
        if (!e.isEmpty()) qs.append("entitas=").append(e);
        if (!kata.isEmpty()) qs.append(qs.isEmpty() ? "" : "&").append("q=")
                .append(java.net.URLEncoder.encode(kata, java.nio.charset.StandardCharsets.UTF_8));
        model.addAttribute("qs", qs.toString());
        return "admin/audit";
    }

    @GetMapping("/riwayat/excel")
    public org.springframework.http.ResponseEntity<byte[]> riwayatExcel(@RequestParam(required = false) String q,
                                                                        @RequestParam(required = false) String hasil,
                                                                        @RequestParam(required = false) String metode,
                                                                        @RequestParam(required = false) String dari,
                                                                        @RequestParam(required = false) String sampai) {
        var f = id.ac.unsera.sistempakar.repository.FilterRiwayat.dari(q, hasil, metode, dari, sampai, 1);
        var rows = diagnosaRepo.cari(f, null, true).isi();
        return Unduhan.file(excelService.riwayat(rows, true), "riwayat-semua-" + Unduhan.tanggal() + ".xlsx", Unduhan.XLSX);
    }

    @PostMapping("/riwayat/{id}/hapus")
    public String hapusRiwayat(@PathVariable long id, RedirectAttributes ra) {
        boolean n = Boolean.TRUE.equals(tx.execute(st -> {
            var d = diagnosaRepo.findById(id);
            if (d.isEmpty() || diagnosaRepo.delete(id) == 0) return false;
            audit.catat(AuditService.HAPUS, "DIAGNOSA", "#" + id, AuditService.ringkas(d.get()), null);
            return true;
        }));
        ra.addFlashAttribute(n ? "sukses" : "gagal", n ? "Hasil pemeriksaan dihapus dari riwayat." : "Data tidak ditemukan atau sudah dihapus.");
        return "redirect:/admin/riwayat";
    }
}
