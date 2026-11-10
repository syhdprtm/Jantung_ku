package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.Gejala;
import id.ac.unsera.sistempakar.model.Penyakit;
import id.ac.unsera.sistempakar.model.Rule;
import id.ac.unsera.sistempakar.model.Syarat;
import id.ac.unsera.sistempakar.repository.GejalaRepository;
import id.ac.unsera.sistempakar.repository.PenyakitRepository;
import id.ac.unsera.sistempakar.repository.RuleRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Logika pengelolaan basis pengetahuan (gejala + bobot CF pakar, hipotesis, rule base).
 * Validasi struktur memakai Bean Validation; aturan bisnis (kode unik, parsing & kelayakan rule)
 * diperiksa di sini. Setiap perubahan dicatat ke audit log dalam transaksi yang sama.
 */
@Service
public class BasisPengetahuanService {

    /** Kosong diizinkan oleh pola (pesan "wajib diisi" datang dari @NotBlank). */
    static final String KODE_RE = "^$|[A-Z0-9_.-]{1,15}";
    private static final String KODE_PESAN = " hanya boleh huruf kapital, angka, titik, - atau _ (maks. 15 karakter).";

    // ------------------------------------------------------------ form

    public record GejalaForm(
            @NotBlank(message = "Kode gejala wajib diisi.")
            @Pattern(regexp = KODE_RE, message = "Kode gejala" + KODE_PESAN) String kode,
            @NotBlank(message = "Nama gejala wajib diisi.")
            @Size(max = 150, message = "Nama gejala maksimal 150 karakter.") String nama,
            @Size(max = 2000, message = "Keterangan maksimal 2000 karakter.") String keterangan,
            /* id penyakit -> teks CF pakar */ Map<Long, String> bobot) {
    }

    public record PenyakitForm(
            @NotBlank(message = "Kode hipotesis wajib diisi.")
            @Pattern(regexp = KODE_RE, message = "Kode hipotesis" + KODE_PESAN) String kode,
            @NotBlank(message = "Nama hipotesis wajib diisi.")
            @Size(max = 150, message = "Nama hipotesis maksimal 150 karakter.") String nama,
            @Pattern(regexp = "RISIKO|NORMAL", message = "Kategori harus RISIKO atau NORMAL.") String kategori,
            @Size(max = 2000, message = "Keterangan maksimal 2000 karakter.") String keterangan,
            @Size(max = 4000, message = "Saran maksimal 4000 karakter.") String saran) {
    }

    public record RuleForm(
            @NotBlank(message = "Kode rule wajib diisi.")
            @Pattern(regexp = KODE_RE, message = "Kode rule" + KODE_PESAN) String kode,
            @NotBlank(message = "Kondisi IF wajib diisi.")
            @Size(max = 2000, message = "Kondisi IF maksimal 2000 karakter.") String kondisi,
            String penyakitId,
            String cf,
            @Size(max = 2000, message = "Keterangan maksimal 2000 karakter.") String keterangan) {
    }

    /** Hasil penyimpanan: daftar kesalahan kosong berarti berhasil. */
    public record Hasil(List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }

        static Hasil sukses() {
            return new Hasil(List.of());
        }
    }

    private final GejalaRepository gejalaRepo;
    private final PenyakitRepository penyakitRepo;
    private final RuleRepository ruleRepo;
    private final AuditService audit;
    private final Validator validator;
    private final TransactionTemplate tx;

    public BasisPengetahuanService(GejalaRepository gejalaRepo, PenyakitRepository penyakitRepo, RuleRepository ruleRepo,
                                   AuditService audit, Validator validator, TransactionTemplate tx) {
        this.gejalaRepo = gejalaRepo;
        this.penyakitRepo = penyakitRepo;
        this.ruleRepo = ruleRepo;
        this.audit = audit;
        this.validator = validator;
        this.tx = tx;
    }

    // ------------------------------------------------------------ util

    /** Pesan Bean Validation, diurutkan sesuai urutan field pada form. */
    private <T> List<String> validasi(T form, String... urutan) {
        List<String> field = List.of(urutan);
        return validator.validate(form).stream()
                .sorted(Comparator.<ConstraintViolation<T>>comparingInt(v -> field.indexOf(v.getPropertyPath().toString()))
                        .thenComparing(ConstraintViolation::getMessage))
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** CF dari teks (koma atau titik). Kosong = 0. null bila tidak valid / di luar 0..1. */
    public static BigDecimal parseCf(String raw) {
        String t = raw == null ? "" : raw.trim().replace(',', '.');
        if (t.isEmpty()) return BigDecimal.ZERO;
        try {
            BigDecimal v = new BigDecimal(t);
            return v.signum() < 0 || v.compareTo(BigDecimal.ONE) > 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cf(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private String ringkas(Gejala g) {
        Map<Long, String> kodeP = penyakitRepo.findAll().stream().collect(Collectors.toMap(Penyakit::id, Penyakit::kode));
        String bobot = g.bobot().entrySet().stream()
                .map(e -> kodeP.getOrDefault(e.getKey(), "?") + "=" + cf(e.getValue()))
                .sorted().collect(Collectors.joining(", "));
        return g.kode() + " · " + g.nama() + " · CF pakar: " + (bobot.isEmpty() ? "-" : bobot);
    }

    private static String ringkas(Penyakit p) {
        return p.kode() + " · " + p.nama() + " · " + p.kategori();
    }

    private static String ringkas(Rule r) {
        return r.kode() + " · IF " + r.kondisi() + " THEN " + (r.penyakitKode() == null ? "-" : r.penyakitKode())
                + " · CF " + cf(r.cf());
    }

    // ------------------------------------------------------------ gejala

    public Hasil simpanGejala(Long id, GejalaForm f) {
        List<String> errors = validasi(f, "kode", "nama", "keterangan");
        Map<Long, BigDecimal> bobot = new LinkedHashMap<>();
        for (Penyakit p : penyakitRepo.findAll()) {
            BigDecimal v = parseCf(f.bobot().get(p.id()));
            if (v == null) errors.add("CF pakar untuk " + p.kode() + " harus angka 0 sampai 1.");
            else bobot.put(p.id(), v);
        }
        if (!errors.isEmpty()) return new Hasil(errors);
        try {
            tx.executeWithoutResult(st -> {
                String sebelum = id == null ? null : gejalaRepo.findById(id).map(this::ringkas).orElse(null);
                long gid = id == null ? gejalaRepo.insert(f.kode(), f.nama(), f.keterangan()) : id;
                if (id != null) gejalaRepo.update(id, f.kode(), f.nama(), f.keterangan());
                gejalaRepo.simpanBobot(gid, bobot);
                audit.catat(id == null ? AuditService.TAMBAH : AuditService.UBAH, "GEJALA", f.kode(), sebelum,
                        gejalaRepo.findById(gid).map(this::ringkas).orElse(null));
            });
            return Hasil.sukses();
        } catch (DuplicateKeyException e) {
            return new Hasil(List.of("Kode " + f.kode() + " sudah digunakan."));
        }
    }

    public boolean hapusGejala(long id) {
        Boolean ok = tx.execute(st -> {
            Optional<Gejala> g = gejalaRepo.findById(id);
            if (g.isEmpty() || gejalaRepo.delete(id) == 0) return false;
            audit.catat(AuditService.HAPUS, "GEJALA", g.get().kode(), ringkas(g.get()), null);
            return true;
        });
        return Boolean.TRUE.equals(ok);
    }

    // ------------------------------------------------------------ hipotesis

    public Hasil simpanPenyakit(Long id, PenyakitForm f) {
        List<String> errors = validasi(f, "kode", "nama", "kategori", "keterangan", "saran");
        if (!errors.isEmpty()) return new Hasil(errors);
        try {
            tx.executeWithoutResult(st -> {
                String sebelum = id == null ? null : penyakitRepo.findById(id).map(BasisPengetahuanService::ringkas).orElse(null);
                long pid = id == null
                        ? penyakitRepo.insert(f.kode(), f.nama(), f.kategori(), f.keterangan(), f.saran())
                        : id;
                if (id != null) penyakitRepo.update(id, f.kode(), f.nama(), f.kategori(), f.keterangan(), f.saran());
                audit.catat(id == null ? AuditService.TAMBAH : AuditService.UBAH, "PENYAKIT", f.kode(), sebelum,
                        penyakitRepo.findById(pid).map(BasisPengetahuanService::ringkas).orElse(null));
            });
            return Hasil.sukses();
        } catch (DuplicateKeyException e) {
            return new Hasil(List.of("Kode " + f.kode() + " sudah digunakan."));
        }
    }

    public boolean hapusPenyakit(long id) {
        Boolean ok = tx.execute(st -> {
            Optional<Penyakit> p = penyakitRepo.findById(id);
            if (p.isEmpty() || penyakitRepo.delete(id) == 0) return false;
            audit.catat(AuditService.HAPUS, "PENYAKIT", p.get().kode(), ringkas(p.get()), null);
            return true;
        });
        return Boolean.TRUE.equals(ok);
    }

    // ------------------------------------------------------------ rule

    public Hasil simpanRule(Long id, RuleForm f) {
        List<String> errors = validasi(f, "kode", "kondisi", "penyakitId", "cf", "keterangan");
        Long pid = null;
        try {
            pid = Long.parseLong(f.penyakitId() == null ? "" : f.penyakitId().trim());
            if (penyakitRepo.findById(pid).isEmpty()) pid = null;
        } catch (NumberFormatException ignored) {
            // ditangani di bawah
        }
        if (pid == null) errors.add("Pilih kesimpulan THEN.");
        BigDecimal nilai = f.cf() == null || f.cf().isBlank() ? null : parseCf(f.cf());
        if (nilai == null) errors.add("CF rule harus angka 0 sampai 1.");
        List<Syarat> syarat = List.of();
        if (f.kondisi() != null && !f.kondisi().isBlank()) {
            try {
                syarat = RuleParser.parse(f.kondisi());
                RuleValidator.periksa(syarat).forEach(m -> errors.add("Kondisi tidak mungkin terpenuhi: " + m));
            } catch (RuleParser.RuleParseException e) {
                errors.add(e.getMessage());
            }
        }
        if (!errors.isEmpty()) return new Hasil(errors);
        final long fPid = pid;
        final List<Syarat> fSyarat = syarat;
        try {
            tx.executeWithoutResult(st -> {
                String sebelum = id == null ? null : ruleRepo.findById(id).map(BasisPengetahuanService::ringkas).orElse(null);
                long rid = id == null ? ruleRepo.insert(f.kode(), f.kondisi(), fPid, nilai, f.keterangan()) : id;
                if (id != null) ruleRepo.update(id, f.kode(), f.kondisi(), fPid, nilai, f.keterangan());
                ruleRepo.simpanSyarat(rid, fSyarat);
                audit.catat(id == null ? AuditService.TAMBAH : AuditService.UBAH, "RULE", f.kode(), sebelum,
                        ruleRepo.findById(rid).map(BasisPengetahuanService::ringkas).orElse(null));
            });
            return Hasil.sukses();
        } catch (DuplicateKeyException e) {
            return new Hasil(List.of("Kode " + f.kode() + " sudah digunakan."));
        }
    }

    public boolean hapusRule(long id) {
        Boolean ok = tx.execute(st -> {
            Optional<Rule> r = ruleRepo.findById(id);
            if (r.isEmpty() || ruleRepo.delete(id) == 0) return false;
            audit.catat(AuditService.HAPUS, "RULE", r.get().kode(), ringkas(r.get()), null);
            return true;
        });
        return Boolean.TRUE.equals(ok);
    }
}
