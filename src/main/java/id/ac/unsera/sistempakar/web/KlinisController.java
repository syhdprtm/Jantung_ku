package id.ac.unsera.sistempakar.web;

import id.ac.unsera.sistempakar.model.Atribut;
import id.ac.unsera.sistempakar.model.Pengguna;
import id.ac.unsera.sistempakar.service.DiagnosaService;
import id.ac.unsera.sistempakar.service.KlinisInput;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Diagnosa metode KLINIS: data klinis pasien -> forward chaining rule base. */
@Controller
public class KlinisController {

    private final DiagnosaService diagnosaService;

    public KlinisController(DiagnosaService diagnosaService) {
        this.diagnosaService = diagnosaService;
    }

    private String form(Model model, Map<String, String> nilai, List<String> errors) {
        model.addAttribute("atribut", Atribut.values());
        model.addAttribute("nilai", nilai);
        model.addAttribute("errors", errors);
        return "user/diagnosa-klinis";
    }

    @GetMapping("/diagnosa/klinis")
    public String form(@ModelAttribute(name = "me", binding = false) Pengguna me, Model model) {
        Map<String, String> awal = new HashMap<>();
        awal.put("namaPasien", me.nama());
        return form(model, awal, List.of());
    }

    @PostMapping("/diagnosa/klinis")
    public String proses(@ModelAttribute(name = "me", binding = false) Pengguna me,
                         @RequestParam Map<String, String> body, Model model, HttpServletResponse res) {
        String namaPasien = body.getOrDefault("namaPasien", "").trim();
        Map<Atribut, String> raw = new EnumMap<>(Atribut.class);
        for (Atribut a : Atribut.values()) raw.put(a, body.get(a.getKolom()));

        List<String> errors = new ArrayList<>();
        if (namaPasien.isEmpty()) errors.add("Nama pasien wajib diisi.");
        if (namaPasien.length() > 100) errors.add("Nama pasien maksimal 100 karakter.");
        KlinisInput.Hasil h = KlinisInput.baca(raw);
        errors.addAll(h.errors());
        if (!errors.isEmpty()) {
            res.setStatus(400);
            return form(model, body, errors);
        }
        long id = diagnosaService.prosesKlinis(me.id(), namaPasien, h.data());
        return "redirect:/hasil/" + id;
    }
}
