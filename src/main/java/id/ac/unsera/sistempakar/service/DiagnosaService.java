package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.DataDiri;
import id.ac.unsera.sistempakar.model.DataKlinis;
import id.ac.unsera.sistempakar.model.Diagnosa;
import id.ac.unsera.sistempakar.model.Gejala;
import id.ac.unsera.sistempakar.model.Penyakit;
import id.ac.unsera.sistempakar.repository.DiagnosaRepository;
import id.ac.unsera.sistempakar.repository.GejalaRepository;
import id.ac.unsera.sistempakar.repository.PenyakitRepository;
import id.ac.unsera.sistempakar.repository.RuleRepository;
import id.ac.unsera.sistempakar.service.CertaintyFactor.HasilDiagnosa;
import id.ac.unsera.sistempakar.service.CertaintyFactor.HasilHipotesis;
import id.ac.unsera.sistempakar.service.CertaintyFactor.Jawaban;
import id.ac.unsera.sistempakar.service.CertaintyFactor.Langkah;
import id.ac.unsera.sistempakar.service.CertaintyFactor.Masukan;
import id.ac.unsera.sistempakar.service.RuleEngine.HasilInferensi;
import id.ac.unsera.sistempakar.service.RuleEngine.RuleAktif;
import id.ac.unsera.sistempakar.service.RuleEngine.RuleTerpicu;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DiagnosaService {

    private final PenyakitRepository penyakitRepo;
    private final GejalaRepository gejalaRepo;
    private final RuleRepository ruleRepo;
    private final DiagnosaRepository diagnosaRepo;

    public DiagnosaService(PenyakitRepository penyakitRepo, GejalaRepository gejalaRepo, RuleRepository ruleRepo,
                           DiagnosaRepository diagnosaRepo) {
        this.penyakitRepo = penyakitRepo;
        this.gejalaRepo = gejalaRepo;
        this.ruleRepo = ruleRepo;
        this.diagnosaRepo = diagnosaRepo;
    }

    /**
     * Data lengkap untuk halaman hasil.
     *
     * @param klinis     data klinis (hanya metode KLINIS)
     * @param terpicu    rule yang terpicu beserta pengecekan tiap syarat (fasilitas penjelasan)
     * @param pembanding hasil metode gejala dari data klinis yang sama (hanya metode KLINIS)
     * @param turunan    gejala yang diturunkan otomatis beserta alasannya (hanya metode KLINIS)
     */
    public record HasilLengkap(Diagnosa diagnosa, List<Jawaban> jawaban, List<HasilHipotesis> hasil,
                               HasilHipotesis kesimpulan, DataKlinis klinis, List<RuleTerpicu> terpicu,
                               HasilDiagnosa pembanding, Map<String, String> turunan) {
    }

    // ------------------------------------------------------------ metode GEJALA

    /**
     * Hitung CF dari keyakinan gejala lalu simpan snapshot beserta data diri (9 isian MCU).
     * Data diri bersifat informatif: tidak mengubah perhitungan Certainty Factor. Mengembalikan id diagnosa.
     */
    @Transactional
    public long proses(long userId, DataDiri diri, Map<Long, BigDecimal> jawaban) {
        HasilDiagnosa h = CertaintyFactor.hitung(penyakitRepo.findAll(), gejalaRepo.findAll(), jawaban);
        Integer umur = diri.umur(java.time.LocalDate.now(id.ac.unsera.sistempakar.web.Fmt.WIB));
        long id = simpanHeader(userId, diri.namaPasien(), umur, "GEJALA", h.kesimpulan());
        diagnosaRepo.simpanDataDiri(id, diri);
        int i = 0;
        for (Jawaban j : h.jawaban()) {
            diagnosaRepo.insertJawaban(id, i++, j.gejalaKode(), j.gejalaNama(), j.cfUser());
        }
        simpanHasil(id, h.hasil());
        return id;
    }

    // ------------------------------------------------------------ metode KLINIS (rule base)

    /** Jalankan forward chaining rule base atas data klinis, lalu simpan snapshot. */
    @Transactional
    public long prosesKlinis(long userId, String namaPasien, DataKlinis data) {
        HasilInferensi h = RuleEngine.inferensi(penyakitRepo.findAll(), ruleRepo.findAktif(), data);
        long id = simpanHeader(userId, namaPasien, data.age(), "KLINIS", h.kesimpulan());
        diagnosaRepo.insertKlinis(id, data);
        int i = 0;
        for (RuleTerpicu t : h.terpicu()) {
            RuleAktif r = t.rule();
            diagnosaRepo.insertRule(id, i++, r.kode(), r.kondisi(), r.penyakitKode(), r.cf(), r.keterangan());
        }
        simpanHasil(id, h.hasil());
        return id;
    }

    /** Metode gejala dihitung dari data klinis: gejala diturunkan otomatis dengan CF user = 1. */
    public HasilDiagnosa hitungGejalaDariKlinis(List<Penyakit> penyakit, List<Gejala> gejala, DataKlinis data) {
        return TurunanGejala.hitung(penyakit, gejala, data);
    }

    // ------------------------------------------------------------ util simpan

    private long simpanHeader(long userId, String namaPasien, Integer umur, String metode, HasilHipotesis k) {
        return diagnosaRepo.insert(userId, namaPasien, umur, metode,
                k == null ? null : k.kode(), k == null ? null : k.nama(), k == null ? null : k.kategori(),
                k == null ? BigDecimal.ZERO : k.cf(), k == null ? null : k.saran());
    }

    private void simpanHasil(long id, List<HasilHipotesis> hasil) {
        int i = 0;
        int d = 0;
        for (HasilHipotesis hh : hasil) {
            diagnosaRepo.insertHasil(id, i++, hh.kode(), hh.nama(), hh.kategori(), hh.cf());
            for (Langkah l : hh.langkah()) {
                diagnosaRepo.insertDetail(id, d++, hh.kode(), l.gejalaKode(), l.gejalaNama(), l.cfUser(), l.cfPakar());
            }
        }
    }

    // ------------------------------------------------------------ muat

    /** Muat hasil diagnosa tersimpan; langkah perhitungan disusun ulang dari snapshot. */
    public Optional<HasilLengkap> muat(long id) {
        return diagnosaRepo.findById(id).map(d -> {
            List<Jawaban> jawaban = diagnosaRepo.jawaban(id).stream()
                    .map(r -> new Jawaban(r.gejalaKode(), r.gejalaNama(), r.cfUser())).toList();
            List<DiagnosaRepository.DetailRow> detail = diagnosaRepo.detail(id);
            List<HasilHipotesis> hasil = diagnosaRepo.hasil(id).stream().map(r -> {
                List<Masukan> masukan = detail.stream()
                        .filter(x -> x.penyakitKode().equals(r.penyakitKode()))
                        .map(x -> new Masukan(x.gejalaKode(), x.gejalaNama(), x.cfUser(), x.cfPakar()))
                        .toList();
                return new HasilHipotesis(r.penyakitKode(), r.penyakitNama(), r.kategori(), null,
                        r.cf(), CertaintyFactor.hitungLangkah(masukan));
            }).toList();
            HasilHipotesis kesimpulan = hasil.stream()
                    .filter(x -> x.kode().equals(d.hasilKode())).findFirst()
                    .map(x -> new HasilHipotesis(x.kode(), x.nama(), x.kategori(), d.saran(), x.cf(), x.langkah()))
                    .orElse(null);

            DataKlinis klinis = null;
            List<RuleTerpicu> terpicu = List.of();
            HasilDiagnosa pembanding = null;
            Map<String, String> turunan = Map.of();
            if (d.isKlinis()) {
                klinis = diagnosaRepo.klinis(id).orElse(null);
                if (klinis != null) {
                    final DataKlinis k = klinis;
                    terpicu = diagnosaRepo.rules(id).stream().map(r -> {
                        var syarat = RuleParser.parse(r.kondisi());
                        var aktif = new RuleAktif(null, r.ruleKode(), r.kondisi(), r.penyakitKode(), null, null, null,
                                r.cf(), r.keterangan(), syarat);
                        return new RuleTerpicu(aktif, RuleEngine.cek(syarat, k));
                    }).toList();
                    pembanding = hitungGejalaDariKlinis(penyakitRepo.findAll(), gejalaRepo.findAll(), k);
                    turunan = TurunanGejala.dari(k);
                }
            }
            return new HasilLengkap(d, jawaban, hasil, kesimpulan, klinis, terpicu, pembanding, turunan);
        });
    }
}
