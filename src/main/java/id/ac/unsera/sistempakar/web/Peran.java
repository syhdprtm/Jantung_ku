package id.ac.unsera.sistempakar.web;

import org.springframework.security.core.Authentication;

/** Utilitas role pengguna: ADMIN, PAKAR, USER. */
public final class Peran {

    private Peran() {
    }

    /** Role utama dari Authentication, mis. "ADMIN"; string kosong bila tamu. */
    public static String dari(Authentication auth) {
        if (auth == null) return "";
        return auth.getAuthorities().stream()
                .map(g -> g.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst().orElse("");
    }

    /** Halaman beranda sesuai role. */
    public static String beranda(String role) {
        return switch (role) {
            case "ADMIN" -> "/admin";
            case "PAKAR" -> "/pakar";
            default -> "/dashboard";
        };
    }

    public static String label(String role) {
        return switch (role) {
            case "ADMIN" -> "Administrator";
            case "PAKAR" -> "Pakar";
            default -> "Pengguna";
        };
    }
}
