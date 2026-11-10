package id.ac.unsera.sistempakar.web;

import id.ac.unsera.sistempakar.model.Gejala;
import id.ac.unsera.sistempakar.model.Pengguna;
import id.ac.unsera.sistempakar.model.Penyakit;
import id.ac.unsera.sistempakar.model.TingkatKeyakinan;
import id.ac.unsera.sistempakar.repository.DiagnosaRepository;
import id.ac.unsera.sistempakar.repository.GejalaRepository;
import id.ac.unsera.sistempakar.repository.PenggunaRepository;
import id.ac.unsera.sistempakar.repository.PenyakitRepository;
import id.ac.unsera.sistempakar.service.AuditService;
import id.ac.unsera.sistempakar.service.DataDiriInput;
import id.ac.unsera.sistempakar.service.DiagnosaService;
import id.ac.unsera.sistempakar.service.LaporanPdfService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Halaman yang dipakai role USER (sebagian juga dipakai ADMIN untuk uji diagnosa). */
@Controller
public class UserController {

    private final DiagnosaService diagnosaService;
    private final DiagnosaRepository diagnosaRepo;
    private final GejalaRepository gejalaRepo;
    private final PenyakitRepository penyakitRepo;
    private final PenggunaRepository penggunaRepo;
    private final PasswordEncoder encoder;
    private final id.ac.unsera.sistempakar.service.LaporanPdfService pdfService;
    private final id.ac.unsera.sistempakar.service.EksporExcelService excelService;
    private final id.ac.unsera.sistempakar.service.AuditService audit;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    public UserController(DiagnosaService diagnosaService, DiagnosaRepository diagnosaRepo,
                          GejalaRepository gejalaRepo, PenyakitRepository penyakitRepo,
                          PenggunaRepository penggunaRepo, PasswordEncoder encoder,
                          id.ac.unsera.sistempakar.service.LaporanPdfService pdfService,
                          id.ac.unsera.sistempakar.service.EksporExcelService excelService,
                          id.ac.unsera.sistempakar.service.AuditService audit,
                          org.springframework.transaction.support.TransactionTemplate tx) {
        this.audit = audit;
        this.tx = tx;
        this.pdfService = pdfService;
        this.excelService = excelService;
        this.diagnosaService = diagnosaService;
        this.diagnosaRepo = diagnosaRepo;
        this.gejalaRepo = gejalaRepo;
        this.penyakitRepo = penyakitRepo;
        this.penggunaRepo = penggunaRepo;
        this.encoder = encoder;
    }

    /** Kelompok gejala pada form diagnosa, berdasarkan hipotesis yang paling dipengaruhinya. */
    public record Kelompok(String judul, String subjudul, String kategori, List<Gejala> gejala) {
    }

    @GetMapping("/dashboard")
    public String dashboard(@ModelAttribute(name = "me", binding = false) Pengguna me, Model model) {
        if (me.isPetugas()) return "redirect:" + me.getBeranda();
        model.addAttribute("jumlah", diagnosaRepo.countByUser(me.id()));
        model.addAttribute("terakhir", diagnosaRepo.findByUser(me.id(), 5));
        return "user/dashboard";
    }

    // ------------------------------------------------------------ diagnosa

    private List<Kelompok> kelompokkan(List<Gejala> gejala, List<Penyakit> penyakit) {
        Map<Long, List<Gejala>> per = new LinkedHashMap<>();
        penyakit.forEach(p -> per.put(p.id(), new ArrayList<>()));
        List<Gejala> lain = new ArrayList<>();
        for (Gejala g : gejala) {
            Optional<Map.Entry<Long, BigDecimal>> maks = g.bobot().entrySet().stream()
                    .max(Map.Entry.comparingByValue());
            if (maks.isPresent() && per.containsKey(maks.get().getKey())) per.get(maks.get().getKey()).add(g);
            else lain.add(g);
        }
        List<Kelompok> hasil = new ArrayList<>();
        for (Penyakit p : penyakit) {
            List<Gejala> isi = per.get(p.id());
            if (isi.isEmpty()) continue;
            String judul = p.isRisiko() ? "Indikator risiko" : "Indikator kondisi normal";
            hasil.add(new Kelompok(judul, "Mengarah ke: " + p.nama(), p.kategori(), isi));
        }
        if (!lain.isEmpty()) hasil.add(new Kelompok("Parameter lainnya", "Belum memiliki bobot pakar", "LAIN", lain));
        hasil.sort(Comparator.comparing(k -> "RISIKO".equals(k.kategori()) ? 0 : "NORMAL".equals(k.kategori()) ? 1 : 2));
        return hasil;
    }

    private String formDiagnosa(Model model, Map<String, String> form, Map<Long, String> pilihan, List<String> errors) {
        List<Gejala> gejala = gejalaRepo.findAll();
        model.addAttribute("kelompok", kelompokkan(gejala, penyakitRepo.findAll()));
        model.addAttribute("totalGejala", gejala.size());
        model.addAttribute("form", form);
        model.addAttribute("pilihan", pilihan);
        model.addAttribute("errors", errors);
        return "user/diagnosa";
    }

    @GetMapping("/diagnosa")
    public String diagnosa(@ModelAttribute(name = "me", binding = false) Pengguna me, Model model) {
        var terakhir = me.isPetugas() ? null : diagnosaRepo.dataDiriTerakhir(me.id()).orElse(null);
        return formDiagnosa(model, DataDiriInput.awal(me.isPetugas() ? "" : me.nama(), terakhir), Map.of(), List.of());
    }

    @PostMapping("/diagnosa")
    public String prosesDiagnosa(@ModelAttribute(name = "me", binding = false) Pengguna me, @RequestParam Map<String, String> body,
                                 Model model, HttpServletResponse res) {
        DataDiriInput.Hasil diri = DataDiriInput.baca(body, java.time.LocalDate.now(Fmt.WIB));
        List<String> errors = new ArrayList<>(diri.errors());
        Map<Long, BigDecimal> jawaban = new HashMap<>();
        Map<Long, String> pilihan = new HashMap<>();

        for (Gejala g : gejalaRepo.findAll()) {
            String raw = body.get("g_" + g.id());
            Optional<TingkatKeyakinan> t = TingkatKeyakinan.dariNilai(raw);
            if (t.isEmpty()) {
                errors.add("Jawaban untuk gejala " + g.kode() + " tidak valid. Pilih salah satu jawaban yang tersedia.");
                continue;
            }
            jawaban.put(g.id(), t.get().getNilai());
            pilihan.put(g.id(), t.get().getNilaiTeks());
        }
        if (errors.isEmpty() && jawaban.values().stream().noneMatch(v -> v.signum() != 0)) {
            errors.add("Jawab minimal satu gejala dengan pilihan selain “Tidak tahu”.");
        }
        if (!errors.isEmpty()) {
            res.setStatus(400);
            return formDiagnosa(model, DataDiriInput.mentah(body), pilihan, errors);
        }
        long id = diagnosaService.proses(me.id(), diri.data(), jawaban);
        return "redirect:/hasil/" + id;
    }

    @GetMapping("/hasil/{id}")
    public String hasil(@PathVariable long id, @ModelAttribute(name = "me", binding = false) Pengguna me, Model model,
                        HttpServletResponse res) {
        var data = diagnosaService.muat(id)
                // User hanya boleh membuka hasil miliknya; admin & pakar boleh semua.
                .filter(h -> me.isPetugas() || h.diagnosa().userId().equals(me.id()));
        if (data.isEmpty()) {
            res.setStatus(404);
            model.addAttribute("judul", "Hasil tidak ditemukan");
            model.addAttribute("pesan", "Hasil pemeriksaan ini tidak ada atau bukan milik akun Anda.");
            return "pesan";
        }
        model.addAttribute("h", data.get());
        return "hasil";
    }

    @GetMapping("/hasil/{id}/pdf")
    public org.springframework.http.ResponseEntity<byte[]> pdf(@PathVariable long id,
                                                               @ModelAttribute(name = "me", binding = false) Pengguna me) {
        var data = diagnosaService.muat(id)
                .filter(h -> me.isPetugas() || h.diagnosa().userId().equals(me.id()));
        if (data.isEmpty()) return org.springframework.http.ResponseEntity.notFound().build();
        String nama = LaporanPdfService.namaBerkas(data.get().diagnosa());
        return Unduhan.file(pdfService.buat(data.get()), nama, org.springframework.http.MediaType.APPLICATION_PDF);
    }

    @GetMapping("/riwayat/excel")
    public org.springframework.http.ResponseEntity<byte[]> riwayatExcel(@ModelAttribute(name = "me", binding = false) Pengguna me,
                                                                        @RequestParam(required = false) String q,
                                                                        @RequestParam(required = false) String hasil,
                                                                        @RequestParam(required = false) String metode,
                                                                        @RequestParam(required = false) String dari,
                                                                        @RequestParam(required = false) String sampai) {
        var f = id.ac.unsera.sistempakar.repository.FilterRiwayat.dari(q, hasil, metode, dari, sampai, 1);
        var rows = diagnosaRepo.cari(f, me.id(), true).isi();
        return Unduhan.file(excelService.riwayat(rows, false), "riwayat-pemeriksaan-" + Unduhan.tanggal() + ".xlsx", Unduhan.XLSX);
    }

    // ------------------------------------------------------------ riwayat

    @GetMapping("/riwayat")
    public String riwayat(@ModelAttribute(name = "me", binding = false) Pengguna me,
                          @RequestParam(required = false) String q, @RequestParam(required = false) String hasil,
                          @RequestParam(required = false) String metode, @RequestParam(required = false) String dari,
                          @RequestParam(required = false) String sampai, @RequestParam(required = false) Integer halaman,
                          Model model) {
        var f = id.ac.unsera.sistempakar.repository.FilterRiwayat.dari(q, hasil, metode, dari, sampai, halaman);
        model.addAttribute("f", f);
        model.addAttribute("hal", diagnosaRepo.cari(f, me.id(), false));
        model.addAttribute("adminView", false);
        model.addAttribute("basis", "/riwayat");
        // perkembangan 12 diagnosa terakhir (lama -> baru) untuk grafik
        var trend = new ArrayList<>(diagnosaRepo.findByUser(me.id(), 12));
        java.util.Collections.reverse(trend);
        model.addAttribute("perkembangan", trend);
        return "riwayat";
    }

    @PostMapping("/riwayat/{id}/hapus")
    public String hapusRiwayat(@PathVariable long id, @ModelAttribute(name = "me", binding = false) Pengguna me, RedirectAttributes ra) {
        boolean n = Boolean.TRUE.equals(tx.execute(st -> {
            var d = diagnosaRepo.findById(id).filter(x -> x.userId().equals(me.id()));
            if (d.isEmpty() || diagnosaRepo.deleteMilik(id, me.id()) == 0) return false;
            audit.catat(AuditService.HAPUS, "DIAGNOSA", "#" + id, AuditService.ringkas(d.get()), null);
            return true;
        }));
        ra.addFlashAttribute(n ? "sukses" : "gagal", n ? "Hasil pemeriksaan dihapus dari riwayat." : "Hasil pemeriksaan tidak ditemukan.");
        return "redirect:/riwayat";
    }

    // ------------------------------------------------------------ profil

    @GetMapping("/profil")
    public String profil(@ModelAttribute(name = "me", binding = false) Pengguna me, Model model) {
        model.addAttribute("nama", me.nama());
        return "user/profil";
    }

    @PostMapping("/profil")
    public String simpanProfil(@ModelAttribute(name = "me", binding = false) Pengguna me,
                               @RequestParam(defaultValue = "") String nama,
                               @RequestParam(defaultValue = "") String passwordLama,
                               @RequestParam(defaultValue = "") String passwordBaru,
                               @RequestParam(defaultValue = "") String konfirmasi,
                               Model model, RedirectAttributes ra, HttpServletResponse res) {
        nama = nama.trim();
        List<String> errors = new ArrayList<>();
        if (nama.isEmpty()) errors.add("Nama lengkap wajib diisi.");
        boolean gantiPassword = !passwordLama.isEmpty() || !passwordBaru.isEmpty();
        if (gantiPassword) {
            if (!encoder.matches(passwordLama, me.password())) errors.add("Password saat ini salah.");
            if (passwordBaru.length() < 6) errors.add("Password baru minimal 6 karakter.");
            if (!passwordBaru.equals(konfirmasi)) errors.add("Kedua password baru tidak sama. Ketik ulang password yang sama.");
        }
        if (!errors.isEmpty()) {
            res.setStatus(400);
            model.addAttribute("errors", errors);
            model.addAttribute("nama", nama);
            return "user/profil";
        }
        final String fNama = nama;
        tx.executeWithoutResult(st -> {
            penggunaRepo.updateNama(me.id(), fNama);
            if (gantiPassword) penggunaRepo.updatePassword(me.id(), encoder.encode(passwordBaru));
            if (!fNama.equals(me.nama()) || gantiPassword) {
                audit.catat(AuditService.UBAH, "PENGGUNA", "@" + me.username(),
                        "nama: " + me.nama(), "nama: " + fNama + (gantiPassword ? " · password diganti" : ""));
            }
        });
        ra.addFlashAttribute("sukses", "Profil berhasil diperbarui.");
        return "redirect:/profil";
    }

    /**
     * Pengguna menghapus akunnya sendiri beserta seluruh riwayat (FK ON DELETE CASCADE).
     * Hanya untuk role USER, agar sistem tidak kehilangan administrator atau pakar.
     */
    @PostMapping("/profil/hapus")
    public String hapusAkun(@ModelAttribute(name = "me", binding = false) Pengguna me,
                            @RequestParam(defaultValue = "") String password,
                            jakarta.servlet.http.HttpServletRequest req, HttpServletResponse res,
                            RedirectAttributes ra) {
        if (me.isPetugas()) {
            ra.addFlashAttribute("gagal", "Akun administrator dan pakar tidak dapat dihapus dari sini. Minta administrator lain untuk menghapusnya.");
            return "redirect:/profil";
        }
        if (!encoder.matches(password, me.password())) {
            ra.addFlashAttribute("gagal", "Password salah. Akun tidak dihapus.");
            return "redirect:/profil";
        }
        long jumlah = diagnosaRepo.countByUser(me.id());
        tx.executeWithoutResult(st -> {
            audit.catat(AuditService.HAPUS, "PENGGUNA", "@" + me.username(),
                    "akun dihapus sendiri oleh pengguna · " + jumlah + " riwayat", null);
            penggunaRepo.delete(me.id());
        });
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
                .logout(req, res, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/login?dihapus";
    }
}
