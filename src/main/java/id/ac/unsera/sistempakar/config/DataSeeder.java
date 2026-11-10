package id.ac.unsera.sistempakar.config;

import id.ac.unsera.sistempakar.repository.GejalaRepository;
import id.ac.unsera.sistempakar.repository.PenggunaRepository;
import id.ac.unsera.sistempakar.repository.PenyakitRepository;
import id.ac.unsera.sistempakar.repository.RuleRepository;
import id.ac.unsera.sistempakar.service.RuleParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mengisi basis pengetahuan dari laporan "Sistem Pakar Diagnosa Penyakit Jantung dengan
 * Pendekatan Metode Certainty Factor" saat tabel masih kosong:
 * gejala (Tabel 5.1-5.3), bobot CF Pakar (Tabel 5.3), rule base (Tabel 5.4), dan akun admin awal.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PenggunaRepository penggunaRepo;
    private final PenyakitRepository penyakitRepo;
    private final GejalaRepository gejalaRepo;
    private final RuleRepository ruleRepo;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final String adminUsername;
    private final String adminPassword;
    private final String pakarUsername;
    private final String pakarPassword;

    public DataSeeder(PenggunaRepository penggunaRepo, PenyakitRepository penyakitRepo, GejalaRepository gejalaRepo,
                      RuleRepository ruleRepo, PasswordEncoder encoder, TransactionTemplate tx,
                      @Value("${app.admin.username}") String adminUsername,
                      @Value("${app.admin.password}") String adminPassword,
                      @Value("${app.pakar.username}") String pakarUsername,
                      @Value("${app.pakar.password}") String pakarPassword) {
        this.penggunaRepo = penggunaRepo;
        this.penyakitRepo = penyakitRepo;
        this.gejalaRepo = gejalaRepo;
        this.ruleRepo = ruleRepo;
        this.encoder = encoder;
        this.tx = tx;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.pakarUsername = pakarUsername;
        this.pakarPassword = pakarPassword;
    }

    private record P(String kode, String nama, String kategori, String keterangan, String saran) {
    }

    private record G(String kode, String nama, String keterangan, String cfP01, String cfP02) {
    }

    /** Rule dari Tabel 5.4 (dipakai juga oleh pengujian). */
    public record R(String kode, String kondisi, String hasil, String cf, String keterangan) {
    }

    private static final List<P> PENYAKIT = List.of(
            new P("P01", "Terdiagnosa Penyakit Jantung", "RISIKO",
                    "Parameter klinis yang dimasukkan mengarah pada adanya kelainan atau penyakit jantung.",
                    "Segera konsultasikan ke dokter spesialis jantung (Sp.JP) untuk pemeriksaan lanjutan seperti EKG, "
                            + "echocardiography, atau treadmill test. Kurangi garam dan lemak jenuh, berhenti merokok, "
                            + "serta kontrol tekanan darah, kolesterol, dan gula darah secara rutin."),
            new P("P02", "Tidak Terdiagnosa (Normal)", "NORMAL",
                    "Parameter klinis yang dimasukkan menunjukkan kondisi jantung yang cenderung normal.",
                    "Pertahankan pola hidup sehat: olahraga teratur minimal 150 menit per minggu, makan bergizi "
                            + "seimbang, tidur cukup, kelola stres, dan lakukan medical check-up berkala."));

    private static final List<G> GEJALA = List.of(
            new G("D01", "Resting BP tinggi (> 120 mmHg)", "Hasil tekanan darah saat pasien dalam kondisi beristirahat.", "0.4", "0"),
            new G("D02", "Cholesterol tinggi (> 200 mg/dl)", "Sejenis lemak (lipid) yang diproduksi oleh hati dan dibutuhkan tubuh untuk membangun sel, membentuk hormon, serta memproduksi vitamin.", "0.4", "0"),
            new G("D03", "Heart Rate Deficit ≥ 40", "Seberapa besar kekurangan denyut jantung pasien sehingga tidak mencapai detak jantung maksimum yang normal.", "0.8", "0"),
            new G("D04", "Exercise Angina (Ya)", "Nyeri dada yang disebabkan atau dipicu pada saat berolahraga.", "0.8", "0"),
            new G("D05", "Fasting BS tinggi", "Kadar gula darah puasa (tidak makan 8 jam atau lebih) berada di atas normal.", "0.8", "0"),
            new G("D06", "ST Slope Flat", "Grafik rekam jantung (EKG) saat berolahraga dibandingkan saat istirahat bernilai datar.", "0.8", "0"),
            new G("D07", "Max HR rendah (< 140 bpm)", "Detak jantung pada kondisi maksimal berada di bawah 140 bpm.", "0.8", "0"),
            new G("D08", "Resting ECG abnormal (ST)", "Rekam jantung saat istirahat menunjukkan kelainan gelombang ST-T, tanda gangguan aliran darah ke otot jantung.", "0.6", "0"),
            new G("D09", "Resting BP normal (< 120 mmHg)", "Tekanan darah saat istirahat berada pada rentang normal.", "0", "0.6"),
            new G("D10", "Cholesterol normal (< 200 mg/dl)", "Kadar kolesterol berada pada rentang normal.", "0", "0.6"),
            new G("D11", "Resting ECG normal", "Rekam jantung saat istirahat tidak menunjukkan kelainan.", "0", "0.6"),
            new G("D12", "Fasting BS normal", "Kadar gula darah puasa berada pada rentang normal.", "0", "0.6"),
            new G("D13", "ST Slope Up", "Segmen ST pada EKG saat berolahraga menanjak, umumnya menandakan respons jantung yang sehat.", "0", "1"));

    private static final String DOWN_A = "ST Slope Down AND Max HR <= 186 AND Exercise Angina N AND Resting BP > 117";
    private static final String FLAT_F = "ST Slope Flat AND Max HR <= 183 AND Sex F";
    private static final String FLAT_F2 = FLAT_F + " AND Fasting BS <= 1 AND Oldpeak <= 2.3 AND Age > 39";
    private static final String FLAT_M = "ST Slope Flat AND Max HR <= 183 AND Sex M";
    private static final String FLAT_M1 = FLAT_M + " AND Max HR <= 177 AND Max HR > 151 AND Oldpeak > 0.3";
    private static final String FLAT_M2 = FLAT_M + " AND Max HR <= 177 AND Max HR <= 151 AND Max HR > 96";
    private static final String UP_A = "ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76";
    private static final String UP_N = UP_A + " AND Cholesterol > 43 AND Exercise Angina N";
    private static final String UP_Y = UP_A + " AND Cholesterol > 43 AND Exercise Angina Y";
    private static final String UP_ASY = UP_Y + " AND ChestPainType ASY AND Oldpeak <= 1";
    private static final String UP_L = UP_A + " AND Cholesterol <= 43 AND Fasting BS <= 1";

    public static final List<R> RULES = List.of(
            new R("R1", "ST Slope Down AND Max HR > 185", "P02", "0.8", "Detak jantung maksimal (Max HR) yang sangat tinggi (>185) mengindikasikan fungsi jantung masih baik."),
            new R("R2", DOWN_A + " AND Age > 67", "P01", "0.8", "Usia lansia (>67) dengan tekanan darah di atas normal memperkuat risiko kelainan."),
            new R("R3", DOWN_A + " AND Age <= 60 AND Age > 63", "P02", "0.6", "Kondisi tidak terklasifikasi sebagai risiko tinggi pada rentang usia ini."),
            new R("R4", DOWN_A + " AND Age <= 60 AND Age <= 63", "P01", "0.8", "Pasien usia <= 60 tahun dengan tensi tinggi (>117) menunjukkan potensi kelainan."),
            new R("R5", DOWN_A + " AND Age > 67 AND Age <= 60", "P02", "0.6", "Kondisi paradoksikal/anomali pada cabang pohon keputusan, diasumsikan normal."),
            new R("R6", "ST Slope Down AND Max HR <= 186 AND Exercise Angina N AND Resting BP <= 117", "P01", "0.8", "Tekanan darah rendah/normal (<=117) dengan ST Slope Down tetap berisiko."),
            new R("R7", "ST Slope Down AND Max HR <= 186 AND Exercise Angina Y", "P01", "0.9", "Adanya nyeri dada saat olahraga (Exercise Angina Y) adalah indikasi kuat kelainan jantung."),
            new R("R8", "ST Slope Flat AND Max HR > 183", "P02", "0.8", "Detak jantung maksimal tinggi (>183) pada ST Slope Flat tergolong normal."),
            new R("R9", FLAT_F + " AND Fasting BS 1", "P01", "0.8", "Wanita dengan Gula Darah Puasa tinggi (BS = 1) rentan terhadap komplikasi kardiovaskular."),
            new R("R10", FLAT_F + " AND Fasting BS <= 1 AND Oldpeak > 2.3", "P01", "0.9", "Depresi ST saat olahraga (Oldpeak) sangat tinggi (>2.3) adalah bukti signifikan kelainan jantung."),
            new R("R11", FLAT_F2 + " AND Exercise Angina N AND Resting BP > 148", "P01", "0.8", "Hipertensi signifikan (>148) pada pasien wanita dewasa memperkuat diagnosis penyakit jantung."),
            new R("R12", FLAT_F2 + " AND Exercise Angina N AND Resting BP <= 148", "P02", "0.8", "Tekanan darah terkontrol pada wanita tanpa angina meniadakan indikasi kelainan."),
            new R("R13", FLAT_F2 + " AND Exercise Angina Y AND Age < 47", "P01", "0.9", "Angina akibat aktivitas pada wanita usia < 47 tahun merupakan gejala penyakit jantung koroner dini."),
            new R("R14", FLAT_F2 + " AND Exercise Angina Y AND Age <= 47", "P02", "0.8", "Cabang anomali pada usia di bawah 47 tahun, diprediksi tidak memiliki kelainan mayor."),
            new R("R15", FLAT_F + " AND Fasting BS <= 1 AND Oldpeak <= 2.3 AND Age <= 39", "P01", "0.8", "Usia sangat muda (<= 39) dengan ST Flat memiliki kerentanan kardiovaskular bawaan atau spesifik."),
            new R("R16", FLAT_M + " AND Max HR > 177 AND Age > 41", "P02", "0.8", "Pria di atas 41 tahun dengan detak jantung mendekati batas atas (177-183) tergolong adaptif dan normal."),
            new R("R17", FLAT_M + " AND Max HR > 177 AND Age <= 41", "P01", "0.8", "Respons detak jantung tinggi pada pria usia <= 41 tahun di kondisi ST Flat memicu indikasi anomali."),
            new R("R18", FLAT_M1 + " AND Cholesterol > 256 AND Cholesterol > 298", "P02", "0.8", "Hiperkolesterolemia ekstrem (>298) pada kondisi Max HR moderat tidak selalu menunjukkan gagal jantung fungsional."),
            new R("R19", FLAT_M1 + " AND Cholesterol > 256 AND Cholesterol <= 298", "P01", "0.8", "Rentang kolesterol 256-298 memicu plak koroner yang menyebabkan abnormalitas."),
            new R("R20", FLAT_M1 + " AND Cholesterol <= 256 AND Cholesterol > 234", "P02", "0.8", "Kolesterol batas menengah-tinggi (234-256) belum cukup kuat untuk memvonis kelainan jantung."),
            new R("R21", FLAT_M1 + " AND Cholesterol <= 256 AND Cholesterol <= 234 AND Age > 46", "P01", "0.8", "Pria usia > 46 tahun dengan ST Flat berisiko tinggi meskipun kolesterol normal."),
            new R("R22", FLAT_M1 + " AND Cholesterol <= 256 AND Cholesterol <= 234 AND Age <= 46", "P02", "0.8", "Pria dewasa muda (<= 46) dengan kolesterol terkontrol berpotensi memiliki jantung sehat."),
            new R("R23", FLAT_M + " AND Max HR <= 177 AND Max HR > 151 AND Oldpeak <= 0.3", "P01", "0.8", "Nilai Oldpeak sangat rendah pada pria dengan ST Flat tetap terindikasi berisiko."),
            new R("R24", FLAT_M2 + " AND Max HR > 98 AND Resting BP > 105", "P01", "0.8", "Tekanan darah > 105 dengan laju detak jantung kurang optimal merupakan tanda abnormalitas."),
            new R("R25", FLAT_M2 + " AND Max HR > 98 AND Resting BP <= 105 AND Max HR > 139", "P02", "0.8", "Tensi rendah dengan detak jantung yang masih cukup baik (>139) dianggap normal."),
            new R("R26", FLAT_M2 + " AND Max HR > 98 AND Resting BP <= 105 AND Max HR <= 139", "P01", "0.95", "Detak jantung lambat (<=139) disertai tensi rendah (<=105) menunjukkan penurunan fungsi pompa jantung."),
            new R("R27", FLAT_M2 + " AND Max HR <= 98 AND Age > 56", "P02", "0.85", "Penurunan detak jantung pada usia lanjut (>56) merupakan kondisi degeneratif yang lumrah."),
            new R("R28", FLAT_M2 + " AND Max HR <= 98 AND Age <= 56", "P01", "0.85", "Detak jantung mendekati bradikardia (96-98) pada usia tidak terlalu tua (<= 56) terindikasi abnormal."),
            new R("R29", FLAT_M + " AND Max HR <= 177 AND Max HR <= 151 AND Max HR <= 96", "P01", "0.9", "Bradikardia yang nyata (Max HR <= 96) memperjelas diagnosis insufisiensi jantung."),
            new R("R30", "ST Slope Up AND Oldpeak > 2.4", "P01", "0.95", "Kombinasi ST Slope Up dengan Oldpeak yang sangat drastis (>2.4) adalah bukti kuat iskemia otot jantung."),
            new R("R31", "ST Slope Up AND Oldpeak <= 2.4 AND Age > 76", "P01", "0.9", "Pasien geriatri usia sangat tua (>76) sangat rentan terhadap penyakit jantung terlepas dari Oldpeak."),
            new R("R32", UP_N + " AND Max HR > 194", "P02", "0.75", "Kapasitas olahraga sempurna (Max HR > 194) tanpa nyeri membuktikan kesehatan jantung yang sangat prima."),
            new R("R33", UP_N + " AND Max HR <= 194 AND Age > 57 AND Cholesterol > 200 AND Cholesterol > 206", "P02", "0.75", "Lansia > 57 tahun dengan hiperkolesterolemia pada ST Slope Up diprediksi bebas dari disfungsi utama jantung."),
            new R("R34", UP_N + " AND Max HR <= 194 AND Age > 57 AND Cholesterol > 200 AND Cholesterol <= 206", "P01", "0.8", "Rentang spesifik kolesterol antara 200-206 memicu batas ambang abnormalitas pada rentang usia lansia ini."),
            new R("R35", UP_N + " AND Max HR <= 194 AND Age > 57 AND Cholesterol <= 200", "P02", "0.8", "Kolesterol terkontrol (<=200) mengurangi faktor risiko secara signifikan, menormalkan diagnosis."),
            new R("R36", UP_N + " AND Max HR <= 194 AND Age <= 57", "P02", "0.85", "Pasien < 57 tahun tanpa gejala Angina memiliki kemungkinan jantung sehat yang tinggi."),
            new R("R37", UP_Y + " AND ChestPainType ASY AND Oldpeak > 1", "P01", "0.95", "Kombinasi Angina, nyeri dada Asimtomatik (ASY), dan ST tertekan adalah gejala klasik iskemia berat."),
            new R("R38", UP_ASY + " AND Max HR > 129 AND Max HR > 163", "P02", "0.8", "Respons takikardi positif (>163) mengimbangi risiko dari nyeri dada Asimtomatik."),
            new R("R39", UP_ASY + " AND Max HR > 129 AND Max HR <= 163", "P01", "0.85", "Ketidakmampuan memacu jantung lebih dari 163 bpm saat terjadi angina Asimtomatik berindikasi kelainan."),
            new R("R40", UP_ASY + " AND Max HR <= 129", "P02", "0.7", "Detak jantung lambat pada Asymptomatic Pain diprediksi sebagai anomali data (diklasifikasikan Normal)."),
            new R("R41", UP_Y + " AND ChestPainType ATA", "P02", "0.85", "Atypical Angina (ATA) pada ST Slope Up tidak dianggap sebagai penyakit jantung koroner (indikasi lambung/otot)."),
            new R("R42", UP_Y + " AND ChestPainType NAP", "P02", "0.85", "Nyeri non-anginal (NAP) merupakan indikasi kuat bahwa masalahnya bukan berasal dari kardiovaskular."),
            new R("R43", UP_Y + " AND ChestPainType TA", "P02", "0.75", "Nyeri Typical Angina pada sub-cabang pohon ini ditafsirkan sebagai negatif / dapat diobati."),
            new R("R44", UP_A + " AND Cholesterol <= 43 AND Fasting BS > 1", "P01", "0.9", "Kolesterol amat rendah namun Gula Darah Puasa abnormal (Diabetes) menandakan gangguan metabolik dan vaskular."),
            new R("R45", UP_L + " AND Max HR > 103 AND Max HR > 119 AND Age > 67", "P02", "0.85", "Lansia (>67) dengan profil lipid terlalu rendah memiliki risiko kelemahan dan kelainan pompa jantung."),
            new R("R46", UP_L + " AND Max HR > 103 AND Max HR > 119 AND Age <= 67 AND Age > 59", "P02", "0.8", "Usia peralihan (60-67 tahun) dengan gula darah normal belum masuk rentang kritis penyakit struktural jantung."),
            new R("R47", UP_L + " AND Max HR > 103 AND Max HR > 119 AND Age <= 67 AND Age <= 59", "P01", "0.8", "Usia di bawah 60 tahun dengan parameter profil lipid sangat rendah memicu vonis patologis dari algoritma."),
            new R("R48", UP_L + " AND Max HR > 103 AND Max HR <= 119", "P02", "0.75", "Batas Max HR 103-119 dianggap aman (Normal) bagi demografi usia ini."),
            new R("R49", UP_L + " AND Max HR <= 103", "P01", "0.85", "Detak jantung puncak yang sangat lemah (<=103) adalah tanda definitif insufisiensi/penyakit jantung."));

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(status -> {
            if (penggunaRepo.count() == 0) {
                penggunaRepo.insert("Administrator", adminUsername, encoder.encode(adminPassword), "ADMIN");
                log.info("Akun administrator awal dibuat: username '{}'", adminUsername);
            }
            if (penggunaRepo.countByRole("PAKAR") == 0 && !penggunaRepo.existsByUsername(pakarUsername)) {
                penggunaRepo.insert("Pakar Jantung", pakarUsername, encoder.encode(pakarPassword), "PAKAR");
                log.info("Akun pakar awal dibuat: username '{}'", pakarUsername);
            }
            if (penyakitRepo.count() == 0) {
                Map<String, Long> idPenyakit = new HashMap<>();
                for (P p : PENYAKIT) {
                    idPenyakit.put(p.kode(), penyakitRepo.insert(p.kode(), p.nama(), p.kategori(), p.keterangan(), p.saran()));
                }
                for (G g : GEJALA) {
                    long id = gejalaRepo.insert(g.kode(), g.nama(), g.keterangan());
                    gejalaRepo.simpanBobot(id, Map.of(
                            idPenyakit.get("P01"), new BigDecimal(g.cfP01()),
                            idPenyakit.get("P02"), new BigDecimal(g.cfP02())));
                }
                for (R r : RULES) {
                    ruleRepo.insert(r.kode(), r.kondisi(), idPenyakit.get(r.hasil()), new BigDecimal(r.cf()), r.keterangan());
                }
                log.info("Basis pengetahuan diisi: {} gejala, {} hipotesis, {} rule", GEJALA.size(), PENYAKIT.size(), RULES.size());
            }
            sinkronSyarat();
        });
    }

    /** Parse kondisi teks setiap rule yang belum punya syarat terstruktur (tabel rule_syarat). */
    private void sinkronSyarat() {
        int ok = 0;
        for (var r : ruleRepo.findTanpaSyarat()) {
            try {
                ruleRepo.simpanSyarat(r.id(), RuleParser.parse(r.kondisi()));
                ok++;
            } catch (RuleParser.RuleParseException e) {
                log.warn("Rule {} tidak dapat diparse: {}", r.kode(), e.getMessage());
            }
        }
        if (ok > 0) log.info("{} rule diubah menjadi syarat terstruktur", ok);
    }
}
