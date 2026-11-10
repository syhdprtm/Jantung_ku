package id.ac.unsera.sistempakar.web;

import id.ac.unsera.sistempakar.model.Metrik;
import id.ac.unsera.sistempakar.model.Pengguna;
import id.ac.unsera.sistempakar.model.Rule;
import id.ac.unsera.sistempakar.model.Syarat;
import id.ac.unsera.sistempakar.repository.EvaluasiRepository;
import id.ac.unsera.sistempakar.repository.RuleRepository;
import id.ac.unsera.sistempakar.service.EvaluasiService;
import id.ac.unsera.sistempakar.service.RuleParser;
import id.ac.unsera.sistempakar.service.RuleValidator;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Analisis basis pengetahuan: validasi rule dan evaluasi akurasi terhadap dataset. */
@Controller
@RequestMapping("/pakar")
public class AnalisisController {

    private final RuleRepository ruleRepo;
    private final EvaluasiRepository evaluasiRepo;
    private final EvaluasiService evaluasiService;
    private final id.ac.unsera.sistempakar.service.EksporExcelService excelService;

    public AnalisisController(RuleRepository ruleRepo, EvaluasiRepository evaluasiRepo, EvaluasiService evaluasiService,
                              id.ac.unsera.sistempakar.service.EksporExcelService excelService) {
        this.excelService = excelService;
        this.ruleRepo = ruleRepo;
        this.evaluasiRepo = evaluasiRepo;
        this.evaluasiService = evaluasiService;
    }

    /** Hasil pemeriksaan satu rule. */
    public record CekRule(Rule rule, List<Syarat> syarat, List<String> masalah) {
        public boolean isValid() {
            return masalah.isEmpty();
        }
    }

    // ------------------------------------------------------------ validasi

    @GetMapping("/validasi")
    public String validasi(Model model) {
        Map<Long, List<Syarat>> syarat = ruleRepo.semuaSyarat();
        List<CekRule> hasil = new ArrayList<>();
        for (Rule r : ruleRepo.findAll()) {
            List<Syarat> s = syarat.getOrDefault(r.id(), List.of());
            List<String> masalah = new ArrayList<>();
            if (s.isEmpty()) {
                try {
                    RuleParser.parse(r.kondisi());
                    masalah.add("Syarat belum tersinkron; simpan ulang rule ini.");
                } catch (RuleParser.RuleParseException e) {
                    masalah.add(e.getMessage());
                }
            } else {
                masalah.addAll(RuleValidator.periksa(s));
            }
            if (r.penyakitId() == null) masalah.add("Rule tidak memiliki kesimpulan (THEN).");
            hasil.add(new CekRule(r, s, masalah));
        }
        model.addAttribute("hasil", hasil);
        model.addAttribute("jmlBermasalah", hasil.stream().filter(c -> !c.isValid()).count());
        model.addAttribute("tumpangTindih", RuleValidator.tumpangTindih(ruleRepo.findAktif()));
        return "pakar/validasi";
    }

    // ------------------------------------------------------------ evaluasi

    @GetMapping("/evaluasi")
    public String evaluasi(Model model) {
        model.addAttribute("daftar", evaluasiRepo.findAll());
        Map<Long, Map<String, id.ac.unsera.sistempakar.model.Metrik>> ringkas = new java.util.HashMap<>();
        for (var m : evaluasiRepo.metrikSemua()) {
            ringkas.computeIfAbsent(m.evaluasiId(), k -> new java.util.HashMap<>()).put(m.metode(), m);
        }
        model.addAttribute("ringkas", ringkas);
        return "pakar/evaluasi";
    }

    @PostMapping("/evaluasi")
    public String unggah(@RequestParam("file") MultipartFile file,
                         @ModelAttribute(name = "me", binding = false) Pengguna me, RedirectAttributes ra) {
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("gagal", "Pilih file CSV terlebih dahulu.");
            return "redirect:/pakar/evaluasi";
        }
        String nama = file.getOriginalFilename() == null ? "dataset.csv" : file.getOriginalFilename();
        if (!nama.toLowerCase().endsWith(".csv")) {
            ra.addFlashAttribute("gagal", "File harus berformat .csv.");
            return "redirect:/pakar/evaluasi";
        }
        try (Reader in = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            long id = evaluasiService.jalankan(nama.length() > 200 ? nama.substring(0, 200) : nama, me.id(), in);
            ra.addFlashAttribute("sukses", "Evaluasi selesai.");
            return "redirect:/pakar/evaluasi/" + id;
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("gagal", e.getMessage());
        } catch (java.io.IOException e) {
            ra.addFlashAttribute("gagal", "File tidak dapat dibaca: " + e.getMessage());
        }
        return "redirect:/pakar/evaluasi";
    }

    @GetMapping("/evaluasi/{id}")
    public String detail(@PathVariable long id, Model model, HttpServletResponse res) {
        var e = evaluasiRepo.findById(id);
        if (e.isEmpty()) {
            res.setStatus(404);
            model.addAttribute("judul", "Data tidak ditemukan");
            model.addAttribute("pesan", "Evaluasi yang Anda cari tidak ada.");
            return "pesan";
        }
        model.addAttribute("e", e.get());
        var metrik = evaluasiRepo.metrik(id);
        model.addAttribute("metrik", metrik);
        model.addAttribute("banding", Metrik.bandingkan(metrik));
        model.addAttribute("rules", evaluasiRepo.rules(id));
        model.addAttribute("baris", evaluasiRepo.baris(id));
        return "pakar/evaluasi-detail";
    }

    @GetMapping("/evaluasi/{id}/excel")
    public org.springframework.http.ResponseEntity<byte[]> excel(@PathVariable long id) {
        var e = evaluasiRepo.findById(id);
        if (e.isEmpty()) return org.springframework.http.ResponseEntity.notFound().build();
        byte[] isi = excelService.evaluasi(e.get(), evaluasiRepo.metrik(id), evaluasiRepo.rules(id), evaluasiRepo.baris(id));
        return Unduhan.file(isi, "evaluasi-" + id + "-" + e.get().namaFile().replaceAll("[^A-Za-z0-9.]+", "-").replaceAll("\\.csv$", "") + ".xlsx", Unduhan.XLSX);
    }

    @PostMapping("/evaluasi/{id}/hapus")
    public String hapus(@PathVariable long id, RedirectAttributes ra) {
        boolean n = evaluasiService.hapus(id);
        ra.addFlashAttribute(n ? "sukses" : "gagal", n ? "Hasil evaluasi dihapus." : "Data tidak ditemukan atau sudah dihapus.");
        return "redirect:/pakar/evaluasi";
    }
}
