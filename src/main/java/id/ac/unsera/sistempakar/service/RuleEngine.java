package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.DataKlinis;
import id.ac.unsera.sistempakar.model.Penyakit;
import id.ac.unsera.sistempakar.model.Syarat;
import id.ac.unsera.sistempakar.service.CertaintyFactor.HasilHipotesis;
import id.ac.unsera.sistempakar.service.CertaintyFactor.Masukan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mesin inferensi rule base (forward chaining, data-driven).
 *
 * <ol>
 *   <li>Fakta = data klinis pasien.</li>
 *   <li>Setiap rule yang seluruh syarat IF-nya terpenuhi oleh fakta akan <b>terpicu</b>.</li>
 *   <li>CF(H,E) = CF(E) x CF(rule), dengan CF(E) = 1 karena data klinis adalah hasil pengukuran.</li>
 *   <li>Bila lebih dari satu rule terpicu untuk hipotesis yang sama, digabung dengan CF combine.</li>
 * </ol>
 */
public final class RuleEngine {

    private RuleEngine() {
    }

    /** Rule yang siap dieksekusi (rule + syarat terstruktur + hipotesis kesimpulannya). */
    public record RuleAktif(Long id, String kode, String kondisi, String penyakitKode, String penyakitNama,
                            String kategori, String saran, BigDecimal cf, String keterangan, List<Syarat> syarat) {
    }

    /** Hasil pengecekan satu syarat terhadap data pasien (untuk fasilitas penjelasan). */
    public record CekSyarat(Syarat syarat, String nilaiPasien, boolean cocok) {
    }

    public record RuleTerpicu(RuleAktif rule, List<CekSyarat> cek) {
    }

    public record HasilInferensi(List<RuleTerpicu> terpicu, List<HasilHipotesis> hasil, HasilHipotesis kesimpulan) {
        public boolean isTercakup() {
            return !terpicu.isEmpty();
        }
    }

    public static List<CekSyarat> cek(List<Syarat> syarat, DataKlinis d) {
        return syarat.stream().map(s -> new CekSyarat(s, d.teks(s.atribut()), s.cocok(d))).toList();
    }

    public static boolean terpenuhi(RuleAktif r, DataKlinis d) {
        return !r.syarat().isEmpty() && r.syarat().stream().allMatch(s -> s.cocok(d));
    }

    public static HasilInferensi inferensi(List<Penyakit> penyakit, List<RuleAktif> rules, DataKlinis d) {
        List<RuleTerpicu> terpicu = rules.stream()
                .filter(r -> terpenuhi(r, d))
                .map(r -> new RuleTerpicu(r, cek(r.syarat(), d)))
                .toList();

        List<HasilHipotesis> hasil = new ArrayList<>();
        for (Penyakit p : penyakit) {
            List<Masukan> masukan = terpicu.stream()
                    .filter(t -> p.kode().equals(t.rule().penyakitKode()))
                    .map(t -> new Masukan(t.rule().kode(), t.rule().keterangan(), BigDecimal.ONE, t.rule().cf()))
                    .toList();
            var langkah = CertaintyFactor.hitungLangkah(masukan);
            hasil.add(new HasilHipotesis(p.kode(), p.nama(), p.kategori(), p.saran(), CertaintyFactor.cfAkhir(langkah), langkah));
        }
        hasil.sort(Comparator.comparing(HasilHipotesis::cf).reversed());
        HasilHipotesis kesimpulan = !hasil.isEmpty() && hasil.getFirst().cf().signum() > 0 ? hasil.getFirst() : null;
        return new HasilInferensi(terpicu, hasil, kesimpulan);
    }
}
