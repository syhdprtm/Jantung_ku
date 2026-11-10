package id.ac.unsera.sistempakar.web;

import id.ac.unsera.sistempakar.repository.GejalaRepository;
import id.ac.unsera.sistempakar.repository.PenggunaRepository;
import id.ac.unsera.sistempakar.repository.PenyakitRepository;
import id.ac.unsera.sistempakar.repository.RuleRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class PublicController {

    static final String USERNAME_RE = "[A-Za-z0-9_.]{3,30}";

    private final GejalaRepository gejalaRepo;
    private final PenyakitRepository penyakitRepo;
    private final RuleRepository ruleRepo;
    private final PenggunaRepository penggunaRepo;
    private final PasswordEncoder encoder;

    public PublicController(GejalaRepository gejalaRepo, PenyakitRepository penyakitRepo, RuleRepository ruleRepo,
                            PenggunaRepository penggunaRepo, PasswordEncoder encoder) {
        this.gejalaRepo = gejalaRepo;
        this.penyakitRepo = penyakitRepo;
        this.ruleRepo = ruleRepo;
        this.penggunaRepo = penggunaRepo;
        this.encoder = encoder;
    }

    /** Satu gejala untuk daftar di beranda; arah = kategori hipotesis dengan bobot terbesar. */
    public record Faktor(String nama, String keterangan, String arah) {
        public boolean isRisiko() {
            return "RISIKO".equals(arah);
        }
    }

    private static boolean sudahMasuk(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }

    private static String beranda(Authentication auth) {
        return "redirect:" + Peran.beranda(Peran.dari(auth));
    }

    @GetMapping("/")
    public String index(Authentication auth, Model model) {
        if (sudahMasuk(auth)) return beranda(auth);
        model.addAttribute("jmlGejala", gejalaRepo.count());
        model.addAttribute("jmlPenyakit", penyakitRepo.count());
        model.addAttribute("jmlRule", ruleRepo.count());
        // daftar gejala yang dinilai, ditampilkan di beranda beserta arahnya (risiko / normal)
        Map<Long, String> kategori = new java.util.HashMap<>();
        penyakitRepo.findAll().forEach(p -> kategori.put(p.id(), p.kategori()));
        List<Faktor> faktor = gejalaRepo.findAll().stream().map(g -> new Faktor(g.nama(), g.keterangan(),
                g.bobot().entrySet().stream().max(Map.Entry.comparingByValue())
                        .map(e -> kategori.getOrDefault(e.getKey(), "")).orElse(""))).toList();
        model.addAttribute("faktor", faktor);
        return "index";
    }

    @GetMapping("/login")
    public String login(Authentication auth) {
        return sudahMasuk(auth) ? beranda(auth) : "login";
    }

    /** Pintu masuk petugas (admin & pakar). Sengaja tidak ditautkan dari halaman publik. */
    @GetMapping(id.ac.unsera.sistempakar.config.SecurityConfig.MASUK_PETUGAS)
    public String masukPetugas(Authentication auth, Model model) {
        if (sudahMasuk(auth) && id.ac.unsera.sistempakar.config.PemantauLogin.isPetugas(auth)) return beranda(auth);
        model.addAttribute("sebagaiPengguna", sudahMasuk(auth));
        model.addAttribute("noindex", true);
        return "petugas/masuk";
    }

    @GetMapping("/daftar")
    public String daftar(Authentication auth, Model model) {
        if (sudahMasuk(auth)) return beranda(auth);
        model.addAttribute("form", Map.of());
        return "daftar";
    }

    @PostMapping("/daftar")
    public String daftar(@RequestParam(defaultValue = "") String nama,
                         @RequestParam(defaultValue = "") String username,
                         @RequestParam(defaultValue = "") String password,
                         @RequestParam(defaultValue = "") String konfirmasi,
                         @RequestParam(defaultValue = "") String setuju,
                         Model model, RedirectAttributes ra, HttpServletResponse res) {
        nama = nama.trim();
        username = username.trim();
        List<String> errors = new ArrayList<>();
        if (nama.isEmpty()) errors.add("Nama lengkap wajib diisi.");
        if (!username.matches(USERNAME_RE)) errors.add("Username harus 3–30 karakter dan hanya berisi huruf, angka, titik, atau garis bawah.");
        if (password.length() < 6) errors.add("Password minimal 6 karakter.");
        if (!password.equals(konfirmasi)) errors.add("Kedua password tidak sama. Ketik ulang password yang sama.");
        if (!"ya".equals(setuju)) errors.add("Centang persetujuan Kebijakan privasi untuk melanjutkan.");
        if (errors.isEmpty() && penggunaRepo.existsByUsername(username)) errors.add("Username sudah dipakai. Coba username lain.");
        if (!errors.isEmpty()) {
            res.setStatus(400);
            model.addAttribute("errors", errors);
            model.addAttribute("form", Map.of("nama", nama, "username", username, "setuju", setuju));
            return "daftar";
        }
        // Pendaftaran publik selalu ber-role USER; akun ADMIN/PAKAR hanya dibuat oleh admin.
        long id = penggunaRepo.insert(nama, username, encoder.encode(password), "USER");
        penggunaRepo.catatPersetujuan(id);
        ra.addFlashAttribute("sukses", "Akun berhasil dibuat. Silakan masuk dengan username dan password Anda.");
        return "redirect:/login";
    }

    @GetMapping("/tentang")
    public String tentang(Model model) {
        model.addAttribute("jmlGejala", gejalaRepo.count());
        model.addAttribute("jmlPenyakit", penyakitRepo.count());
        model.addAttribute("jmlRule", ruleRepo.count());
        return "tentang";
    }

    @GetMapping("/privasi")
    public String privasi() {
        return "privasi";
    }

    @GetMapping("/akses-ditolak")
    public String aksesDitolak(Model model, HttpServletResponse res) {
        res.setStatus(403);
        model.addAttribute("judul", "Akses ditolak");
        model.addAttribute("pesan", "Anda tidak memiliki hak akses untuk membuka halaman ini.");
        return "pesan";
    }
}
