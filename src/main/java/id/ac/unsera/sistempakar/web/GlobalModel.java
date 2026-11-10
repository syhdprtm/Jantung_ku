package id.ac.unsera.sistempakar.web;

import id.ac.unsera.sistempakar.model.Pengguna;
import id.ac.unsera.sistempakar.model.TingkatKeyakinan;
import id.ac.unsera.sistempakar.repository.PenggunaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.List;

/** Atribut yang tersedia di semua template. */
@ControllerAdvice
public class GlobalModel {

    private final PenggunaRepository penggunaRepo;

    public GlobalModel(PenggunaRepository penggunaRepo) {
        this.penggunaRepo = penggunaRepo;
    }

    /** Pengguna yang sedang login (null bila tamu). */
    @ModelAttribute("me")
    public Pengguna me(Principal principal) {
        return principal == null ? null : penggunaRepo.findByUsername(principal.getName()).orElse(null);
    }

    @ModelAttribute("path")
    public String path(HttpServletRequest req) {
        return req.getRequestURI();
    }

    @ModelAttribute("menu")
    public List<Menu> menu(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return List.of();
        return switch (Peran.dari(auth)) {
            case "ADMIN" -> Menu.ADMIN;
            case "PAKAR" -> Menu.PAKAR;
            case "USER" -> Menu.USER;
            default -> List.of();
        };
    }

    /** Penanda versi aset (waktu server dinyalakan): CSS/JS baru langsung terpakai setelah pembaruan. */
    private static final String VERSI_ASET = Long.toString(System.currentTimeMillis(), 36);

    @ModelAttribute("v")
    public String versiAset() {
        return VERSI_ASET;
    }

    @ModelAttribute("tingkat")
    public TingkatKeyakinan[] tingkat() {
        return TingkatKeyakinan.values();
    }
}
