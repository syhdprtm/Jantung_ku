package id.ac.unsera.sistempakar.model;

import id.ac.unsera.sistempakar.web.Peran;

import java.time.LocalDateTime;

public record Pengguna(Long id, String nama, String username, String password, String role,
                       LocalDateTime createdAt, Integer jmlDiagnosa, Integer gagalLogin,
                       LocalDateTime terkunciSampai) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isPakar() {
        return "PAKAR".equals(role);
    }

    /** Admin atau pakar: boleh membuka hasil diagnosa milik siapa pun. */
    public boolean isPetugas() {
        return isAdmin() || isPakar();
    }

    /** Akun sedang dikunci sementara karena terlalu banyak percobaan login gagal. */
    public boolean isTerkunci() {
        return terkunciSampai != null && terkunciSampai.isAfter(LocalDateTime.now());
    }

    public String getRoleLabel() {
        return Peran.label(role);
    }

    public String getBeranda() {
        return Peran.beranda(role);
    }

    /** Inisial untuk avatar, mis. "Budi Santoso" -> "BS". */
    public String inisial() {
        if (nama == null || nama.isBlank()) return "?";
        String[] p = nama.trim().split("\\s+");
        String s = p[0].substring(0, 1) + (p.length > 1 ? p[p.length - 1].substring(0, 1) : "");
        return s.toUpperCase();
    }

    public String namaDepan() {
        return nama == null ? "" : nama.trim().split("\\s+")[0];
    }
}
