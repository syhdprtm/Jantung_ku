package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.repository.EvaluasiRepository;
import id.ac.unsera.sistempakar.web.Fmt;
import id.ac.unsera.sistempakar.repository.GejalaRepository;
import id.ac.unsera.sistempakar.repository.PenyakitRepository;
import id.ac.unsera.sistempakar.repository.RuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;

@Service
public class EvaluasiService {

    private final PenyakitRepository penyakitRepo;
    private final GejalaRepository gejalaRepo;
    private final RuleRepository ruleRepo;
    private final EvaluasiRepository evaluasiRepo;
    private final AuditService audit;

    public EvaluasiService(PenyakitRepository penyakitRepo, GejalaRepository gejalaRepo, RuleRepository ruleRepo,
                           EvaluasiRepository evaluasiRepo, AuditService audit) {
        this.audit = audit;
        this.penyakitRepo = penyakitRepo;
        this.gejalaRepo = gejalaRepo;
        this.ruleRepo = ruleRepo;
        this.evaluasiRepo = evaluasiRepo;
    }

    /** Hapus hasil evaluasi beserta catatan audit. */
    @Transactional
    public boolean hapus(long id) {
        var e = evaluasiRepo.findById(id);
        if (e.isEmpty() || evaluasiRepo.delete(id) == 0) return false;
        audit.catat(AuditService.HAPUS, "EVALUASI", "#" + id, e.get().namaFile() + " · " + e.get().jumlah() + " baris", null);
        return true;
    }

    /** Jalankan evaluasi lalu simpan seluruh hasilnya. Mengembalikan id evaluasi. */
    @Transactional
    public long jalankan(String namaFile, Long userId, Reader csv) throws IOException {
        Evaluator.HasilEvaluasi h = Evaluator.evaluasi(csv, penyakitRepo.findAll(), ruleRepo.findAktif(), gejalaRepo.findAll());
        long id = evaluasiRepo.insert(namaFile, userId, h.jumlah(), h.dilewati());
        h.metrik().forEach((metode, m) -> evaluasiRepo.insertMetrik(id, metode, m.tp, m.tn, m.fp, m.fn, m.takTercakup));
        h.rules().forEach((kode, s) -> evaluasiRepo.insertRule(id, kode, s.terpicu, s.benar));
        for (var b : h.salah()) {
            evaluasiRepo.insertBaris(id, b.baris(), b.metode(), b.aktual(), b.prediksi(), b.cf(), b.rule(), b.data());
        }
        StringBuilder ring = new StringBuilder(namaFile + " · " + h.jumlah() + " baris");
        evaluasiRepo.metrik(id).forEach(m -> ring.append(" · akurasi ").append("KLINIS".equals(m.metode()) ? "rule base " : "gejala ")
                .append(Fmt.persenS(m.getAkurasi())));
        audit.catat(AuditService.JALANKAN, "EVALUASI", "#" + id, null, ring.toString());
        return id;
    }
}
