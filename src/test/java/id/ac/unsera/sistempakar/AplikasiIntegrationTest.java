package id.ac.unsera.sistempakar;

import id.ac.unsera.sistempakar.model.Gejala;
import id.ac.unsera.sistempakar.repository.GejalaRepository;
import id.ac.unsera.sistempakar.repository.PenyakitRepository;
import id.ac.unsera.sistempakar.repository.RuleRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tes menyeluruh memakai MySQL lokal dengan database terpisah
 * {@code sistem_pakar_jantung_test} yang dibuat ulang setiap kali tes dijalankan.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:mysql://localhost:3306/sistem_pakar_jantung_test"
        + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jakarta")
class AplikasiIntegrationTest {

    @Autowired WebApplicationContext ctx;
    @Autowired GejalaRepository gejalaRepo;
    @Autowired PenyakitRepository penyakitRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired JdbcClient jdbc;

    MockMvc mvc;

    @BeforeAll
    static void databaseBersih() throws Exception {
        String user = System.getenv().getOrDefault("DB_USERNAME", "root");
        String pass = System.getenv().getOrDefault("DB_PASSWORD", "");
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true", user, pass);
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS sistem_pakar_jantung_test");
        }
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private static MockHttpServletRequestBuilder sebagai(MockHttpServletRequestBuilder b, String username, String role) {
        return b.with(user(username).roles(role));
    }

    /** POST /diagnosa dengan 9 isian data diri ala MCU yang valid (gejala ditambahkan pemanggil). */
    private static MockHttpServletRequestBuilder diagnosaPost(String nama, String... ganti) {
        Map<String, String> v = new java.util.LinkedHashMap<>(Map.of("namaPasien", nama, "jenisKelamin", "L",
                "tanggalLahir", "1976-03-15", "alamat", "Jl. Uji No. 1, Serang", "telepon", "081234567890",
                "tinggiCm", "170", "beratKg", "65"));
        for (int i = 0; i + 1 < ganti.length; i += 2) v.put(ganti[i], ganti[i + 1]);
        MockHttpServletRequestBuilder b = post("/diagnosa").with(csrf());
        v.forEach(b::param);
        return b;
    }

    private void daftar(String nama, String username) throws Exception {
        mvc.perform(post("/daftar").with(csrf()).param("nama", nama).param("username", username)
                        .param("password", "rahasia1").param("konfirmasi", "rahasia1").param("setuju", "ya"))
                .andExpect(redirectedUrl("/login"));
    }

    private Map<String, Long> idGejala() {
        return gejalaRepo.findAll().stream().collect(Collectors.toMap(Gejala::kode, Gejala::id));
    }

    @Test
    void seedSesuaiLaporan() {
        assertThat(gejalaRepo.findAll()).extracting(Gejala::kode).contains("D01", "D08", "D13");
        assertThat(penyakitRepo.count()).isEqualTo(2);
        assertThat(ruleRepo.count()).isEqualTo(49);
    }

    @Test
    void tamuDiarahkanKeLogin() throws Exception {
        mvc.perform(get("/diagnosa")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin")).andExpect(status().isNotFound()); // area petugas tersembunyi
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Kenali risiko jantung")));
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/daftar")).andExpect(status().isOk());
    }

    @Test
    void loginAdminDanPasswordSalah() throws Exception {
        mvc.perform(formLogin("/petugas/masuk").user("admin").password("salah")).andExpect(redirectedUrl("/petugas/masuk?gagal"));
        mvc.perform(formLogin("/petugas/masuk").user("admin").password("admin123")).andExpect(redirectedUrl("/admin"));
    }

    @Test
    void alurUserDiagnosaDanHakAkses() throws Exception {
        daftar("Budi Santoso", "budi");
        mvc.perform(formLogin("/login").user("budi").password("rahasia1")).andExpect(redirectedUrl("/dashboard"));

        // User tidak boleh membuka halaman admin
        mvc.perform(sebagai(get("/admin/users"), "budi", "USER")).andExpect(status().isNotFound());

        // Diagnosa dengan nilai contoh laporan (Tabel 5.5)
        Map<String, Long> g = idGejala();
        var req = diagnosaPost("Budi");
        Map<String, String> cfUser = Map.of("D01", "0.6", "D02", "0.4", "D03", "0.6", "D04", "0.8",
                "D05", "0.4", "D06", "0.6", "D07", "0.6", "D08", "0.4");
        cfUser.forEach((kode, v) -> req.param("g_" + g.get(kode), v));
        MvcResult r = mvc.perform(sebagai(req, "budi", "USER"))
                .andExpect(redirectedUrlPattern("/hasil/*")).andReturn();
        String url = r.getResponse().getRedirectedUrl();

        String html = mvc.perform(sebagai(get(url), "budi", "USER")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("98,33").contains("0.98329956").contains("Terdiagnosa Penyakit Jantung");

        // Halaman user lainnya
        for (String p : new String[]{"/dashboard", "/riwayat", "/profil", "/diagnosa"}) {
            mvc.perform(sebagai(get(p), "budi", "USER")).andExpect(status().isOk());
        }

        // User lain tidak boleh melihat hasil milik Budi
        daftar("Ani", "ani");
        mvc.perform(sebagai(get(url), "ani", "USER")).andExpect(status().isNotFound());

        // Admin boleh melihat semua
        mvc.perform(sebagai(get(url), "admin", "ADMIN")).andExpect(status().isOk());
        String riwayat = mvc.perform(sebagai(get("/admin/riwayat"), "admin", "ADMIN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(riwayat).contains("@budi");

        // Validasi: tanpa gejala dipilih
        mvc.perform(sebagai(diagnosaPost("X"), "budi", "USER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dataDiriMcuDisimpanDivalidasiDanTercetak() throws Exception {
        daftar("Lina", "lina");
        Map<String, Long> g = idGejala();
        var req = diagnosaPost("Lina", "jenisKelamin", "P", "tinggiCm", "165", "beratKg", "70,0")
                .param("g_" + g.get("D04"), "0.8");
        String url = mvc.perform(sebagai(req, "lina", "USER")).andExpect(redirectedUrlPattern("/hasil/*"))
                .andReturn().getResponse().getRedirectedUrl();
        long id = Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
        var row = jdbc.sql("SELECT jenis_kelamin, tanggal_lahir, tinggi_cm, berat_kg, sistolik, umur FROM diagnosa WHERE id = ?")
                .param(id).query().singleRow();
        assertThat(row.get("jenis_kelamin")).isEqualTo("P");
        assertThat(row.get("tanggal_lahir").toString()).isEqualTo("1976-03-15");
        assertThat(((Number) row.get("tinggi_cm")).doubleValue()).isEqualTo(165.0);
        assertThat(((Number) row.get("berat_kg")).doubleValue()).isEqualTo(70.0);
        assertThat(row.get("sistolik")).isNull();   // tekanan darah tidak lagi ditanyakan di data diri
        assertThat(row.get("umur")).isNotNull();

        // halaman hasil menampilkan IMT (70 / 1,65^2 = 25,7 -> gemuk)
        String html = mvc.perform(sebagai(get(url), "lina", "USER")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("25,7").contains("Gemuk").contains("Perempuan");

        // formulir berikutnya diisi ulang dari data terakhir; kuesioner memakai 9 tingkat keyakinan
        String form = mvc.perform(sebagai(get("/diagnosa"), "lina", "USER")).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("value=\"1976-03-15\"").contains("value=\"165\"")
                .contains("Pasti tidak").contains("Hampir pasti tidak").contains("Kemungkinan besar").contains("Tidak tahu")
                .doesNotContain("name=\"sistolik\"");

        // laporan PDF bergaya surat laporan MCU dengan blok tanda tangan pakar
        byte[] pdf = mvc.perform(sebagai(get(url + "/pdf"), "lina", "USER")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        var reader = new org.openpdf.text.pdf.PdfReader(pdf);
        var ekstrak = new org.openpdf.text.pdf.parser.PdfTextExtractor(reader);
        StringBuilder semua = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) semua.append(ekstrak.getTextFromPage(i)).append('\n');
        String pakar = jdbc.sql("SELECT nama FROM users WHERE role = 'PAKAR' ORDER BY id LIMIT 1").query(String.class).single();
        assertThat(semua.toString()).contains("LAPORAN PEMERIKSAAN KESEHATAN").contains("MEDICAL CHECK UP REPORT")
                .contains("Nomor Rekam Medis").contains("KESIMPULAN").contains("REKOMENDASI").contains("Pakar Pemeriksa")
                .contains(pakar).contains("BUKAN DIAGNOSIS DOKTER");

        // jawaban negatif (Pasti tidak) diterima dan menurunkan keyakinan risiko
        String urlNeg = mvc.perform(sebagai(diagnosaPost("Lina").param("g_" + g.get("D04"), "-1"), "lina", "USER"))
                .andExpect(redirectedUrlPattern("/hasil/*")).andReturn().getResponse().getRedirectedUrl();
        long idNeg = Long.parseLong(urlNeg.substring(urlNeg.lastIndexOf('/') + 1));
        String katNeg = jdbc.sql("SELECT COALESCE(hasil_kategori, '-') FROM diagnosa WHERE id = ?").param(idNeg).query(String.class).single();
        assertThat(katNeg).isNotEqualTo("RISIKO");
        BigDecimal cfUserNeg = jdbc.sql("SELECT cf_user FROM diagnosa_jawaban WHERE diagnosa_id = ?").param(idNeg).query(BigDecimal.class).single();
        assertThat(cfUserNeg).isEqualByComparingTo("-1");
        assertThat(mvc.perform(sebagai(get(urlNeg), "lina", "USER")).andReturn().getResponse().getContentAsString()).contains("Pasti tidak");

        // nilai di luar skala ditolak
        mvc.perform(sebagai(diagnosaPost("Lina").param("g_" + g.get("D04"), "0.2"), "lina", "USER"))
                .andExpect(status().isBadRequest());

        // validasi: tanggal lahir di masa depan, telepon salah, jenis kelamin tidak valid
        var buruk = diagnosaPost("Lina", "jenisKelamin", "X", "tanggalLahir", "2999-01-01", "telepon", "12ab")
                .param("g_" + g.get("D04"), "0.8");
        String err = mvc.perform(sebagai(buruk, "lina", "USER")).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(err).contains("Pilih jenis kelamin").contains("Tanggal lahir tidak boleh setelah hari ini")
                .contains("Nomor telepon harus");
        // data wajib: tanpa tinggi badan
        var tanpaTinggi = diagnosaPost("Lina", "tinggiCm", "").param("g_" + g.get("D04"), "0.8");
        assertThat(mvc.perform(sebagai(tanpaTinggi, "lina", "USER")).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString()).contains("Tinggi badan wajib diisi");
    }

    @Test
    void pendaftaranPublikTidakBisaMembuatAdmin() throws Exception {
        mvc.perform(post("/daftar").with(csrf()).param("nama", "X").param("username", "xadmin")
                .param("password", "rahasia1").param("konfirmasi", "rahasia1").param("setuju", "ya").param("role", "ADMIN"));
        String role = jdbc.sql("SELECT role FROM users WHERE username = 'xadmin'").query(String.class).single();
        assertThat(role).isEqualTo("USER");
    }

    @Test
    void semuaHalamanAdminTampil() throws Exception {
        long uid = jdbc.sql("SELECT id FROM users WHERE username = 'admin'").query(Long.class).single();
        String[] halaman = {"/admin", "/admin/users", "/admin/users/tambah", "/admin/users/" + uid + "/edit",
                "/admin/riwayat", "/admin/riwayat?q=x&hasil=RISIKO&metode=GEJALA&dari=2026-01-01&sampai=2026-12-31&halaman=2",
                "/diagnosa", "/profil", "/diagnosa/klinis"};
        for (String p : halaman) {
            mvc.perform(sebagai(get(p), "admin", "ADMIN")).andExpect(status().isOk());
        }
    }

    @Test
    void semuaHalamanPakarTampil() throws Exception {
        long gid = gejalaRepo.findAll().getFirst().id();
        long pid = penyakitRepo.findAll().getFirst().id();
        long rid = ruleRepo.findAll().getFirst().id();
        String[] halaman = {"/pakar", "/pakar/gejala", "/pakar/gejala/tambah", "/pakar/gejala/" + gid + "/edit",
                "/pakar/penyakit", "/pakar/penyakit/tambah", "/pakar/penyakit/" + pid + "/edit",
                "/pakar/rules", "/pakar/rules/tambah", "/pakar/rules/" + rid + "/edit",
                "/pakar/validasi", "/pakar/evaluasi", "/diagnosa", "/diagnosa/klinis", "/profil"};
        for (String p : halaman) {
            mvc.perform(sebagai(get(p), "pakar", "PAKAR")).andExpect(status().isOk());
        }
    }

    @Test
    void pemisahanHakAksesTigaRole() throws Exception {
        mvc.perform(formLogin("/petugas/masuk").user("pakar").password("pakar123")).andExpect(redirectedUrl("/pakar"));
        // admin tidak mengelola basis pengetahuan; pakar tidak mengelola pengguna
        mvc.perform(sebagai(get("/pakar/rules"), "admin", "ADMIN")).andExpect(status().isForbidden());
        mvc.perform(sebagai(get("/admin/users"), "pakar", "PAKAR")).andExpect(status().isForbidden());
        mvc.perform(sebagai(get("/pakar/gejala"), "user-tamu", "USER")).andExpect(status().isNotFound());
        // dashboard user mengalihkan petugas ke berandanya
        mvc.perform(sebagai(get("/dashboard"), "pakar", "PAKAR")).andExpect(redirectedUrl("/pakar"));
    }

    @Test
    void unduhPdfDanExcel() throws Exception {
        daftar("Dewi", "dewi");
        Map<String, Long> g = idGejala();
        var req = diagnosaPost("Dewi").param("g_" + g.get("D04"), "0.8");
        String url = mvc.perform(sebagai(req, "dewi", "USER")).andReturn().getResponse().getRedirectedUrl();

        var pdf = mvc.perform(sebagai(get(url + "/pdf"), "dewi", "USER")).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf")).andReturn().getResponse();
        assertThat(new String(pdf.getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.getHeader("Content-Disposition")).contains("attachment").contains(".pdf");
        // orang lain tidak boleh mengunduh PDF milik Dewi
        daftar("Fajar", "fajar");
        mvc.perform(sebagai(get(url + "/pdf"), "fajar", "USER")).andExpect(status().isNotFound());

        byte[] xlsx = mvc.perform(sebagai(get("/riwayat/excel?metode=GEJALA"), "dewi", "USER")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK"); // file .xlsx adalah arsip zip
        mvc.perform(sebagai(get("/admin/riwayat/excel"), "admin", "ADMIN")).andExpect(status().isOk());
    }

    @Test
    void migrasiSesiDanAuditTersedia() {
        int versi = jdbc.sql("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history").query(Integer.class).single();
        assertThat(versi).isGreaterThanOrEqualTo(9);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM SPRING_SESSION").query(Long.class).single()).isNotNull();
    }

    @Test
    void auditMencatatPerubahanBasisPengetahuan() throws Exception {
        long p01 = penyakitRepo.findAll().stream().filter(p -> p.kode().equals("P01")).findFirst().orElseThrow().id();
        mvc.perform(sebagai(post("/pakar/gejala/tambah").with(csrf()).param("kode", "D20").param("nama", "Gejala audit")
                .param("cf_" + p01, "0.4"), "pakar", "PAKAR")).andExpect(redirectedUrl("/pakar/gejala"));
        long gid = gejalaRepo.findAll().stream().filter(x -> x.kode().equals("D20")).findFirst().orElseThrow().id();
        mvc.perform(sebagai(post("/pakar/gejala/" + gid + "/edit").with(csrf()).param("kode", "D20").param("nama", "Gejala audit diubah")
                .param("cf_" + p01, "0.6"), "pakar", "PAKAR")).andExpect(redirectedUrl("/pakar/gejala"));
        mvc.perform(sebagai(post("/pakar/gejala/" + gid + "/hapus").with(csrf()), "pakar", "PAKAR"));

        var log = jdbc.sql("SELECT aksi, username, role, sebelum, sesudah FROM audit_log WHERE entitas = 'GEJALA' AND kode = 'D20' ORDER BY id")
                .query((rs, i) -> rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|" + rs.getString(4) + "|" + rs.getString(5)).list();
        assertThat(log).hasSize(3);
        assertThat(log.get(0)).startsWith("TAMBAH|pakar|PAKAR|null|D20 · Gejala audit · CF pakar: P01=0.4");
        assertThat(log.get(1)).startsWith("UBAH|pakar|PAKAR|D20 · Gejala audit · CF pakar: P01=0.4|D20 · Gejala audit diubah · CF pakar: P01=0.6");
        assertThat(log.get(2)).startsWith("HAPUS|pakar|PAKAR|D20 · Gejala audit diubah");

        String html = mvc.perform(sebagai(get("/admin/audit?entitas=GEJALA&q=D20"), "admin", "ADMIN"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Gejala audit diubah").contains("@pakar");
        mvc.perform(sebagai(get("/admin/audit"), "admin", "ADMIN")).andExpect(status().isOk()); // tanpa filter
        mvc.perform(sebagai(get("/admin/audit?halaman=2"), "admin", "ADMIN")).andExpect(status().isOk());
        mvc.perform(sebagai(get("/admin/audit"), "pakar", "PAKAR")).andExpect(status().isForbidden());
        mvc.perform(sebagai(get("/pakar"), "pakar", "PAKAR")).andExpect(status().isOk());
    }

    @Test
    void beanValidationPadaForm() throws Exception {
        String html = mvc.perform(sebagai(post("/pakar/rules/tambah").with(csrf()).param("kode", "R 9!").param("kondisi", "")
                        .param("cf", "2"), "pakar", "PAKAR"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Kode rule hanya boleh").contains("Kondisi IF wajib diisi.")
                .contains("Pilih kesimpulan THEN.").contains("CF rule harus angka 0 sampai 1.");
    }

    @Test
    void halamanErrorRapi() throws Exception {
        mvc.perform(sebagai(get("/halaman-yang-tidak-ada"), "admin", "ADMIN"))
                .andExpect(status().isNotFound()).andExpect(content().string(org.hamcrest.Matchers.containsString("Halaman tidak ditemukan")))
                // pengguna yang login tetap melihat menu aplikasi di halaman error
                .andExpect(content().string(org.hamcrest.Matchers.containsString("logout-desktop")));
        mvc.perform(sebagai(get("/hasil/abc"), "admin", "ADMIN"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("Permintaan tidak valid")));
    }

    /** Hanya bagian tabel riwayat (grafik perkembangan sengaja menampilkan semua diagnosa terakhir). */
    private static String tabel(String html) {
        int i = html.indexOf("id=\"tbl-riwayat\"");
        return i < 0 ? "" : html.substring(i);
    }

    @Test
    void filterRiwayat() throws Exception {
        daftar("Eko", "eko");
        Map<String, Long> g = idGejala();
        mvc.perform(sebagai(diagnosaPost("Pasien Risiko").param("g_" + g.get("D04"), "1"), "eko", "USER"));
        mvc.perform(sebagai(diagnosaPost("Pasien Sehat").param("g_" + g.get("D13"), "1"), "eko", "USER"));
        String semua = mvc.perform(sebagai(get("/riwayat"), "eko", "USER")).andReturn().getResponse().getContentAsString();
        assertThat(semua).contains("Pasien Risiko").contains("Pasien Sehat");
        String normal = tabel(mvc.perform(sebagai(get("/riwayat?hasil=NORMAL"), "eko", "USER")).andReturn().getResponse().getContentAsString());
        assertThat(normal).contains("Pasien Sehat").doesNotContain("Pasien Risiko");
        String cari = tabel(mvc.perform(sebagai(get("/riwayat?q=risiko"), "eko", "USER")).andReturn().getResponse().getContentAsString());
        assertThat(cari).contains("Pasien Risiko").doesNotContain("Pasien Sehat");
    }

    @Test
    void ruleBaseTersinkronMenjadiSyaratTerstruktur() {
        long tanpaSyarat = jdbc.sql("SELECT COUNT(*) FROM rules r WHERE NOT EXISTS (SELECT 1 FROM rule_syarat s WHERE s.rule_id = r.id)")
                .query(Long.class).single();
        assertThat(tanpaSyarat).isZero();
        assertThat(ruleRepo.findAktif()).hasSize(49);
    }

    @Test
    void diagnosaDataKlinisDenganPenjelasan() throws Exception {
        daftar("Citra", "citra");
        var req = post("/diagnosa/klinis").with(csrf()).param("namaPasien", "Citra")
                .param("Age", "55").param("Sex", "M").param("ChestPainType", "ASY").param("RestingBP", "140")
                .param("Cholesterol", "260").param("FastingBS", "0").param("RestingECG", "ST").param("MaxHR", "150")
                .param("ExerciseAngina", "Y").param("Oldpeak", "3").param("ST_Slope", "Up");
        MvcResult r = mvc.perform(sebagai(req, "citra", "USER")).andExpect(redirectedUrlPattern("/hasil/*")).andReturn();
        String html = mvc.perform(sebagai(get(r.getResponse().getRedirectedUrl()), "citra", "USER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // R30: ST Slope Up AND Oldpeak > 2.4 -> Terdiagnosis, CF 0.95
        assertThat(html).contains("Aturan pakar yang terpenuhi").contains("R30").contains("95,00")
                .contains("Sebagai pembanding: hasil kuesioner gejala").contains("Pemeriksaan klinis");

        // validasi input
        mvc.perform(sebagai(post("/diagnosa/klinis").with(csrf()).param("namaPasien", "X").param("Age", "abc"), "citra", "USER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validasiRuleMenandaiR3R5R44() throws Exception {
        String html = mvc.perform(sebagai(get("/pakar/validasi"), "pakar", "PAKAR"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Rule yang perlu diperbaiki").contains("R3").contains("R44");
    }

    @Test
    void evaluasiDataset() throws Exception {
        String csv = EvaluatorTest.HEADER
                + "50,M,ATA,130,220,0,Normal,190,N,0.5,Down,0\n"
                + "55,M,ASY,140,260,0,ST,150,Y,3,Up,1\n";
        var file = new org.springframework.mock.web.MockMultipartFile("file", "uji.csv", "text/csv", csv.getBytes());
        MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/pakar/evaluasi").file(file).with(csrf()).with(user("pakar").roles("PAKAR")))
                .andExpect(redirectedUrlPattern("/pakar/evaluasi/*")).andReturn();
        String evalUrl = r.getResponse().getRedirectedUrl();
        String html = mvc.perform(sebagai(get(evalUrl), "pakar", "PAKAR"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(sebagai(get(evalUrl + "/excel"), "pakar", "PAKAR")).andExpect(status().isOk());
        assertThat(html).contains("uji.csv").contains("Pemeriksaan klinis (rule base)").contains("100,00");

        // user biasa tidak boleh
        mvc.perform(sebagai(get("/pakar/evaluasi"), "budi2", "USER")).andExpect(status().isNotFound());
    }

    @Test
    void pakarMenambahGejalaDenganBobot() throws Exception {
        long p01 = penyakitRepo.findAll().stream().filter(p -> p.kode().equals("P01")).findFirst().orElseThrow().id();
        long p02 = penyakitRepo.findAll().stream().filter(p -> p.kode().equals("P02")).findFirst().orElseThrow().id();
        mvc.perform(sebagai(post("/pakar/gejala/tambah").with(csrf()).param("kode", "d14").param("nama", "Nyeri dada ASY")
                        .param("cf_" + p01, "0,6").param("cf_" + p02, "0"), "pakar", "PAKAR"))
                .andExpect(redirectedUrl("/pakar/gejala"));
        Gejala d14 = gejalaRepo.findAll().stream().filter(x -> x.kode().equals("D14")).findFirst().orElseThrow();
        assertThat(d14.bobot()).hasSize(1);
        assertThat(d14.bobotUntuk(p01)).isEqualByComparingTo("0.6");

        mvc.perform(sebagai(post("/pakar/gejala/tambah").with(csrf()).param("kode", "D15").param("nama", "X")
                        .param("cf_" + p01, "1.5"), "pakar", "PAKAR"))
                .andExpect(status().isBadRequest());
        // kode duplikat
        mvc.perform(sebagai(post("/pakar/gejala/tambah").with(csrf()).param("kode", "D01").param("nama", "X"), "pakar", "PAKAR"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ kesiapan production

    @Test
    void halamanTentangDanPrivasiPublik() throws Exception {
        mvc.perform(get("/tentang")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CF(H,E) = CF user × CF pakar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bukan diagnosis dokter")));
        mvc.perform(get("/privasi")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Siapa yang dapat melihat")));
        // halaman masuk & daftar punya jalan kembali ke beranda
        for (String p : new String[]{"/login", "/daftar"}) {
            mvc.perform(get(p)).andExpect(content().string(org.hamcrest.Matchers.containsString("Kembali ke beranda")));
        }
    }

    @Test
    void daftarWajibMenyetujuiPrivasi() throws Exception {
        mvc.perform(post("/daftar").with(csrf()).param("nama", "Tanpa Setuju").param("username", "tanpasetuju")
                        .param("password", "rahasia1").param("konfirmasi", "rahasia1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Centang persetujuan")));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM users WHERE username = 'tanpasetuju'").query(Long.class).single()).isZero();

        daftar("Hana", "hana");
        assertThat(jdbc.sql("SELECT setuju_privasi_at FROM users WHERE username = 'hana'").query(java.sql.Timestamp.class).single())
                .isNotNull();
    }

    @Test
    void penggunaMenghapusAkunSendiri() throws Exception {
        daftar("Gita", "gita");
        Map<String, Long> g = idGejala();
        mvc.perform(sebagai(diagnosaPost("Gita").param("g_" + g.get("D04"), "0.8"), "gita", "USER"));
        long uid = jdbc.sql("SELECT id FROM users WHERE username = 'gita'").query(Long.class).single();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM diagnosa WHERE user_id = ?").param(uid).query(Long.class).single()).isEqualTo(1);

        // password salah: tidak terjadi apa-apa
        mvc.perform(sebagai(post("/profil/hapus").with(csrf()).param("password", "salah"), "gita", "USER"))
                .andExpect(redirectedUrl("/profil"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM users WHERE id = ?").param(uid).query(Long.class).single()).isEqualTo(1);

        // password benar: akun & riwayat terhapus, tercatat di audit
        mvc.perform(sebagai(post("/profil/hapus").with(csrf()).param("password", "rahasia1"), "gita", "USER"))
                .andExpect(redirectedUrl("/login?dihapus"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM users WHERE id = ?").param(uid).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM diagnosa WHERE user_id = ?").param(uid).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE entitas = 'PENGGUNA' AND kode = '@gita' AND aksi = 'HAPUS'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void adminDanPakarTidakBisaMenghapusAkunSendiri() throws Exception {
        mvc.perform(sebagai(post("/profil/hapus").with(csrf()).param("password", "pakar123"), "pakar", "PAKAR"))
                .andExpect(redirectedUrl("/profil"));
        mvc.perform(sebagai(post("/profil/hapus").with(csrf()).param("password", "admin123"), "admin", "ADMIN"))
                .andExpect(redirectedUrl("/profil"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM users WHERE username IN ('admin', 'pakar')").query(Long.class).single()).isEqualTo(2);
    }

    /** Frasa prototipe/akademik yang tidak boleh muncul di halaman untuk pengguna umum. */
    private static final String[] FRASA_PROTOTIPE = {"Tabel 5", "pada laporan", "Contoh hasil laporan", "Isi contoh",
            "Ketuk", "forward chaining", "Laragon", "Isi data uji"};

    @Test
    void halamanPenggunaBebasFrasaPrototipe() throws Exception {
        daftar("Indra", "indra");
        Map<String, Long> g = idGejala();
        String url = mvc.perform(sebagai(diagnosaPost("Indra").param("g_" + g.get("D04"), "0.8"), "indra", "USER"))
                .andReturn().getResponse().getRedirectedUrl();
        String[] publik = {"/", "/login", "/daftar", "/tentang", "/privasi"};
        String[] pengguna = {"/dashboard", "/diagnosa", "/diagnosa/klinis", "/riwayat", "/profil", url};
        for (String p : publik) periksaFrasa(p, mvc.perform(get(p)).andReturn().getResponse().getContentAsString());
        for (String p : pengguna) {
            String html = mvc.perform(sebagai(get(p), "indra", "USER")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            int teknis = html.indexOf("<details class=\"teknis\"");
            periksaFrasa(p, teknis < 0 ? html : html.substring(0, teknis));
        }
        // hasil: detail teknis tertutup untuk pengguna, terbuka untuk pakar
        String milikUser = mvc.perform(sebagai(get(url), "indra", "USER")).andReturn().getResponse().getContentAsString();
        assertThat(milikUser).contains("<details class=\"teknis\">").contains("Langkah selanjutnya").contains("bukan diagnosis dokter");
        String olehPakar = mvc.perform(sebagai(get(url), "pakar", "PAKAR")).andReturn().getResponse().getContentAsString();
        assertThat(olehPakar).contains("<details class=\"teknis\" open");
        // data uji klinis hanya untuk petugas
        String klinisPakar = mvc.perform(sebagai(get("/diagnosa/klinis"), "pakar", "PAKAR")).andReturn().getResponse().getContentAsString();
        assertThat(klinisPakar).contains("Isi data uji");
    }

    private static void periksaFrasa(String halaman, String html) {
        for (String f : FRASA_PROTOTIPE) {
            assertThat(html).as("halaman %s memuat \"%s\"", halaman, f).doesNotContain(f);
        }
    }

    // ------------------------------------------------------------ pemisahan login & keamanan

    private long jumlahAudit(String aksi, String username) {
        return jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE entitas = 'SESI' AND aksi = ? AND username = ?")
                .params(aksi, username).query(Long.class).single();
    }

    @Test
    void pintuMasukPetugasDanPenggunaTerpisah() throws Exception {
        daftar("Joko", "joko");
        // akun petugas ditolak di /login walau password benar; pengguna ditolak di /petugas/masuk
        mvc.perform(formLogin("/login").user("admin").password("admin123")).andExpect(redirectedUrl("/login?gagal"));
        mvc.perform(formLogin("/login").user("pakar").password("pakar123")).andExpect(redirectedUrl("/login?gagal"));
        mvc.perform(formLogin("/petugas/masuk").user("joko").password("rahasia1")).andExpect(redirectedUrl("/petugas/masuk?gagal"));
        mvc.perform(formLogin("/login").user("joko").password("rahasia1")).andExpect(redirectedUrl("/dashboard"));

        // tamu tidak diarahkan ke login petugas: area petugas tampak tidak ada
        for (String p : new String[]{"/admin", "/admin/users", "/pakar", "/pakar/rules"}) {
            mvc.perform(get(p)).andExpect(status().isNotFound());
        }
        // pakar membuka area admin: 403 (bukan 404, karena ia petugas)
        mvc.perform(sebagai(get("/admin/users"), "pakar", "PAKAR")).andExpect(status().isForbidden());

        // halaman masuk petugas tidak diindeks dan tidak ditautkan dari halaman publik
        mvc.perform(get("/petugas/masuk")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("noindex")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Masuk petugas")));
        for (String p : new String[]{"/", "/login", "/daftar", "/tentang", "/privasi"}) {
            assertThat(mvc.perform(get(p)).andReturn().getResponse().getContentAsString())
                    .as("halaman %s menautkan login petugas", p).doesNotContain("/petugas/masuk");
        }
    }

    @Test
    void akunTerkunciSetelahLimaKaliGagal() throws Exception {
        daftar("Kiki", "kiki");
        for (int i = 1; i <= 4; i++) {
            mvc.perform(formLogin("/login").user("kiki").password("salah" + i)).andExpect(redirectedUrl("/login?gagal"));
        }
        mvc.perform(formLogin("/login").user("kiki").password("salah5")).andExpect(redirectedUrl("/login?terkunci"));
        // password benar pun ditolak selama terkunci
        mvc.perform(formLogin("/login").user("kiki").password("rahasia1")).andExpect(redirectedUrl("/login?terkunci"));
        assertThat(jumlahAudit("TERKUNCI", "kiki")).isEqualTo(1);

        // admin membuka kunci, lalu pengguna bisa masuk lagi
        long uid = jdbc.sql("SELECT id FROM users WHERE username = 'kiki'").query(Long.class).single();
        assertThat(mvc.perform(sebagai(get("/admin/users"), "admin", "ADMIN")).andReturn().getResponse().getContentAsString())
                .contains("Buka kunci");
        mvc.perform(sebagai(post("/admin/users/" + uid + "/buka-kunci").with(csrf()), "admin", "ADMIN"))
                .andExpect(redirectedUrl("/admin/users"));
        mvc.perform(formLogin("/login").user("kiki").password("rahasia1")).andExpect(redirectedUrl("/dashboard"));
        assertThat(jdbc.sql("SELECT gagal_login FROM users WHERE id = ?").param(uid).query(Integer.class).single()).isZero();
    }

    @Test
    void loginPetugasDicatatDanSesinyaSingkat() throws Exception {
        long gagalSebelum = jumlahAudit("GAGAL_MASUK", "pakar");
        long masukSebelum = jumlahAudit("MASUK", "pakar");
        mvc.perform(formLogin("/petugas/masuk").user("pakar").password("salah")).andExpect(redirectedUrl("/petugas/masuk?gagal"));
        mvc.perform(formLogin("/petugas/masuk").user("tidak-ada").password("x")).andExpect(redirectedUrl("/petugas/masuk?gagal"));
        MvcResult r = mvc.perform(formLogin("/petugas/masuk").user("pakar").password("pakar123"))
                .andExpect(redirectedUrl("/pakar")).andReturn();
        assertThat(r.getRequest().getSession().getMaxInactiveInterval()).isEqualTo(30 * 60);
        assertThat(jumlahAudit("GAGAL_MASUK", "pakar")).isEqualTo(gagalSebelum + 1);
        assertThat(jumlahAudit("GAGAL_MASUK", "tidak-ada")).isGreaterThanOrEqualTo(1);
        assertThat(jumlahAudit("MASUK", "pakar")).isEqualTo(masukSebelum + 1);

        // keluar: petugas kembali ke halaman masuk petugas dan tercatat; pengguna ke /login
        mvc.perform(post("/logout").with(csrf()).with(user("pakar").roles("PAKAR"))).andExpect(redirectedUrl("/petugas/masuk?keluar"));
        assertThat(jumlahAudit("KELUAR", "pakar")).isGreaterThanOrEqualTo(1);
        mvc.perform(post("/logout").with(csrf()).with(user("budi3").roles("USER"))).andExpect(redirectedUrl("/login?keluar"));

        // filter audit "Sesi login"
        assertThat(mvc.perform(sebagai(get("/admin/audit?entitas=SESI"), "admin", "ADMIN")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).contains("Sesi login").contains("GAGAL MASUK");
    }
}