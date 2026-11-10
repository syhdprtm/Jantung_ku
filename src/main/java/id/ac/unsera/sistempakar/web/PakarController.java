package id.ac.unsera.sistempakar.web;

import id.ac.unsera.sistempakar.model.Gejala;
import id.ac.unsera.sistempakar.model.Metrik;
import id.ac.unsera.sistempakar.model.Penyakit;
import id.ac.unsera.sistempakar.repository.AuditRepository;
import id.ac.unsera.sistempakar.repository.DiagnosaRepository;
import id.ac.unsera.sistempakar.repository.EvaluasiRepository;
import id.ac.unsera.sistempakar.repository.GejalaRepository;
import id.ac.unsera.sistempakar.repository.PenyakitRepository;
import id.ac.unsera.sistempakar.repository.RuleRepository;
import id.ac.unsera.sistempakar.service.BasisPengetahuanService;
import id.ac.unsera.sistempakar.service.BasisPengetahuanService.GejalaForm;
import id.ac.unsera.sistempakar.service.BasisPengetahuanService.Hasil;
import id.ac.unsera.sistempakar.service.BasisPengetahuanService.PenyakitForm;
import id.ac.unsera.sistempakar.service.BasisPengetahuanService.RuleForm;
import id.ac.unsera.sistempakar.service.RuleValidator;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Halaman role PAKAR: dashboard dan pengelolaan basis pengetahuan (dibatasi di SecurityConfig: /pakar/**).
 * Validasi, penyimpanan, dan audit ada di {@link BasisPengetahuanService}; controller hanya mengurus HTTP.
 */
@Controller
@RequestMapping("/pakar")
public class PakarController {

    private final BasisPengetahuanService basis;
    private final PenyakitRepository penyakitRepo;
    private final GejalaRepository gejalaRepo;
    private final RuleRepository ruleRepo;
    private final DiagnosaRepository diagnosaRepo;
    private final EvaluasiRepository evaluasiRepo;
    private final AuditRepository auditRepo;

    public PakarController(BasisPengetahuanService basis, PenyakitRepository penyakitRepo, GejalaRepository gejalaRepo,
                           RuleRepository ruleRepo, DiagnosaRepository diagnosaRepo, EvaluasiRepository evaluasiRepo,
                           AuditRepository auditRepo) {
        this.basis = basis;
        this.penyakitRepo = penyakitRepo;
        this.gejalaRepo = gejalaRepo;
        this.ruleRepo = ruleRepo;
        this.diagnosaRepo = diagnosaRepo;
        this.evaluasiRepo = evaluasiRepo;
        this.auditRepo = auditRepo;
    }

    private static String s(String v) {
        return v == null ? "" : v.trim();
    }

    private static String tidakDitemukan(Model model, HttpServletResponse res) {
        res.setStatus(404);
        model.addAttribute("judul", "Data tidak ditemukan");
        model.addAttribute("pesan", "Data yang Anda cari tidak ada atau sudah dihapus.");
        return "pesan";
    }

    private static void flashHapus(RedirectAttributes ra, boolean ok, String label) {
        ra.addFlashAttribute(ok ? "sukses" : "gagal", ok ? label + " dihapus." : "Data tidak ditemukan atau sudah dihapus.");
    }

    // ------------------------------------------------------------ dashboard

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("jmlGejala", gejalaRepo.count());
        model.addAttribute("jmlPenyakit", penyakitRepo.count());
        model.addAttribute("jmlRule", ruleRepo.count());
        model.addAttribute("jmlDiagnosa", diagnosaRepo.count());
        model.addAttribute("topGejala", diagnosaRepo.gejalaTerbanyak(8));
        model.addAttribute("topRule", diagnosaRepo.ruleTerbanyak(8));
        long bermasalah = ruleRepo.semuaSyarat().values().stream()
                .filter(x -> !RuleValidator.periksa(x).isEmpty()).count()
                + ruleRepo.findTanpaSyarat().size();
        model.addAttribute("jmlBermasalah", bermasalah);
        var daftar = evaluasiRepo.findAll();
        if (!daftar.isEmpty()) {
            var e = daftar.getFirst();
            model.addAttribute("evaluasi", e);
            model.addAttribute("banding", Metrik.bandingkan(evaluasiRepo.metrik(e.id())));
        }
        model.addAttribute("perubahan", auditRepo.terbaru(6, "GEJALA", "PENYAKIT", "RULE"));
        return "pakar/dashboard";
    }

    // ------------------------------------------------------------ gejala + bobot

    @GetMapping("/gejala")
    public String gejala(Model model) {
        model.addAttribute("gejala", gejalaRepo.findAll());
        model.addAttribute("penyakit", penyakitRepo.findAll());
        return "pakar/gejala";
    }

    private String formGejala(Model model, Long id, Map<String, Object> form, List<String> errors) {
        model.addAttribute("judul", id == null ? "Tambah gejala" : "Edit gejala " + form.getOrDefault("kode", ""));
        model.addAttribute("action", id == null ? "/pakar/gejala/tambah" : "/pakar/gejala/" + id + "/edit");
        model.addAttribute("form", form);
        model.addAttribute("penyakit", penyakitRepo.findAll());
        model.addAttribute("errors", errors);
        return "pakar/gejala-form";
    }

    @GetMapping("/gejala/tambah")
    public String tambahGejala(Model model) {
        return formGejala(model, null, Map.of("bobot", Map.of()), List.of());
    }

    @GetMapping("/gejala/{id}/edit")
    public String editGejala(@PathVariable long id, Model model, HttpServletResponse res) {
        return gejalaRepo.findById(id).map(g -> {
            Map<Long, String> bobot = new HashMap<>();
            g.bobot().forEach((k, v) -> bobot.put(k, v.stripTrailingZeros().toPlainString()));
            Map<String, Object> f = new HashMap<>(Map.of("kode", g.kode(), "nama", g.nama(), "bobot", bobot));
            f.put("keterangan", g.keterangan());
            return formGejala(model, id, f, List.of());
        }).orElseGet(() -> tidakDitemukan(model, res));
    }

    @PostMapping({"/gejala/tambah", "/gejala/{id}/edit"})
    public String simpanGejala(@PathVariable(required = false) Long id, @RequestParam Map<String, String> body,
                               Model model, RedirectAttributes ra, HttpServletResponse res) {
        if (id != null && !gejalaRepo.exists(id)) return tidakDitemukan(model, res);
        Map<Long, String> bobot = new HashMap<>();
        for (Penyakit p : penyakitRepo.findAll()) bobot.put(p.id(), s(body.get("cf_" + p.id())));
        GejalaForm f = new GejalaForm(s(body.get("kode")).toUpperCase(), s(body.get("nama")), s(body.get("keterangan")), bobot);
        Hasil h = basis.simpanGejala(id, f);
        if (h.ok()) {
            ra.addFlashAttribute("sukses", "Gejala " + f.kode() + (id == null ? " ditambahkan." : " diperbarui."));
            return "redirect:/pakar/gejala";
        }
        res.setStatus(400);
        return formGejala(model, id, Map.of("kode", f.kode(), "nama", f.nama(), "keterangan", f.keterangan(), "bobot", bobot),
                h.errors());
    }

    @PostMapping("/gejala/{id}/hapus")
    public String hapusGejala(@PathVariable long id, RedirectAttributes ra) {
        flashHapus(ra, basis.hapusGejala(id), "Gejala");
        return "redirect:/pakar/gejala";
    }

    // ------------------------------------------------------------ penyakit / hipotesis

    @GetMapping("/penyakit")
    public String penyakit(Model model) {
        model.addAttribute("penyakit", penyakitRepo.findAll());
        return "pakar/penyakit";
    }

    private String formPenyakit(Model model, Long id, Map<String, Object> form, List<String> errors) {
        model.addAttribute("judul", id == null ? "Tambah hipotesis" : "Edit " + form.getOrDefault("kode", ""));
        model.addAttribute("action", id == null ? "/pakar/penyakit/tambah" : "/pakar/penyakit/" + id + "/edit");
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        return "pakar/penyakit-form";
    }

    private static Map<String, Object> formPenyakit(PenyakitForm f) {
        Map<String, Object> m = new HashMap<>();
        m.put("kode", f.kode());
        m.put("nama", f.nama());
        m.put("kategori", f.kategori());
        m.put("keterangan", f.keterangan());
        m.put("saran", f.saran());
        return m;
    }

    @GetMapping("/penyakit/tambah")
    public String tambahPenyakit(Model model) {
        return formPenyakit(model, null, Map.of("kategori", "RISIKO"), List.of());
    }

    @GetMapping("/penyakit/{id}/edit")
    public String editPenyakit(@PathVariable long id, Model model, HttpServletResponse res) {
        return penyakitRepo.findById(id)
                .map(p -> formPenyakit(model, id, formPenyakit(new PenyakitForm(p.kode(), p.nama(), p.kategori(),
                        p.keterangan(), p.saran())), List.of()))
                .orElseGet(() -> tidakDitemukan(model, res));
    }

    @PostMapping({"/penyakit/tambah", "/penyakit/{id}/edit"})
    public String simpanPenyakit(@PathVariable(required = false) Long id,
                                 @RequestParam(defaultValue = "") String kode,
                                 @RequestParam(defaultValue = "") String nama,
                                 @RequestParam(defaultValue = "RISIKO") String kategori,
                                 @RequestParam(defaultValue = "") String keterangan,
                                 @RequestParam(defaultValue = "") String saran,
                                 Model model, RedirectAttributes ra, HttpServletResponse res) {
        if (id != null && penyakitRepo.findById(id).isEmpty()) return tidakDitemukan(model, res);
        PenyakitForm f = new PenyakitForm(s(kode).toUpperCase(), s(nama), s(kategori), s(keterangan), s(saran));
        Hasil h = basis.simpanPenyakit(id, f);
        if (h.ok()) {
            ra.addFlashAttribute("sukses", "Hipotesis " + f.kode() + (id == null
                    ? " ditambahkan. Atur bobot CF pakar-nya di menu Gejala." : " diperbarui."));
            return "redirect:/pakar/penyakit";
        }
        res.setStatus(400);
        return formPenyakit(model, id, formPenyakit(f), h.errors());
    }

    @PostMapping("/penyakit/{id}/hapus")
    public String hapusPenyakit(@PathVariable long id, RedirectAttributes ra) {
        flashHapus(ra, basis.hapusPenyakit(id), "Hipotesis");
        return "redirect:/pakar/penyakit";
    }

    // ------------------------------------------------------------ rule base

    @GetMapping("/rules")
    public String rules(Model model) {
        model.addAttribute("rules", ruleRepo.findAll());
        return "pakar/rules";
    }

    private String formRule(Model model, Long id, Map<String, Object> form, List<String> errors) {
        model.addAttribute("judul", id == null ? "Tambah rule" : "Edit rule " + form.getOrDefault("kode", ""));
        model.addAttribute("action", id == null ? "/pakar/rules/tambah" : "/pakar/rules/" + id + "/edit");
        model.addAttribute("form", form);
        model.addAttribute("penyakit", penyakitRepo.findAll());
        model.addAttribute("errors", errors);
        return "pakar/rule-form";
    }

    private static Map<String, Object> formRule(RuleForm f) {
        Map<String, Object> m = new HashMap<>();
        m.put("kode", f.kode());
        m.put("kondisi", f.kondisi());
        m.put("penyakitId", f.penyakitId());
        m.put("cf", f.cf());
        m.put("keterangan", f.keterangan());
        return m;
    }

    @GetMapping("/rules/tambah")
    public String tambahRule(Model model) {
        return formRule(model, null, Map.of(), List.of());
    }

    @GetMapping("/rules/{id}/edit")
    public String editRule(@PathVariable long id, Model model, HttpServletResponse res) {
        return ruleRepo.findById(id)
                .map(r -> formRule(model, id, formRule(new RuleForm(r.kode(), r.kondisi(),
                        r.penyakitId() == null ? "" : r.penyakitId().toString(),
                        r.cf().stripTrailingZeros().toPlainString(), r.keterangan())), List.of()))
                .orElseGet(() -> tidakDitemukan(model, res));
    }

    @PostMapping({"/rules/tambah", "/rules/{id}/edit"})
    public String simpanRule(@PathVariable(required = false) Long id,
                             @RequestParam(defaultValue = "") String kode,
                             @RequestParam(defaultValue = "") String kondisi,
                             @RequestParam(defaultValue = "") String penyakitId,
                             @RequestParam(defaultValue = "") String cf,
                             @RequestParam(defaultValue = "") String keterangan,
                             Model model, RedirectAttributes ra, HttpServletResponse res) {
        if (id != null && ruleRepo.findById(id).isEmpty()) return tidakDitemukan(model, res);
        RuleForm f = new RuleForm(s(kode).toUpperCase(), s(kondisi), s(penyakitId), s(cf), s(keterangan));
        Hasil h = basis.simpanRule(id, f);
        if (h.ok()) {
            ra.addFlashAttribute("sukses", "Rule " + f.kode() + (id == null ? " ditambahkan." : " diperbarui."));
            return "redirect:/pakar/rules";
        }
        res.setStatus(400);
        return formRule(model, id, formRule(f), h.errors());
    }

    @PostMapping("/rules/{id}/hapus")
    public String hapusRule(@PathVariable long id, RedirectAttributes ra) {
        flashHapus(ra, basis.hapusRule(id), "Rule");
        return "redirect:/pakar/rules";
    }
}
