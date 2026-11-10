package id.ac.unsera.sistempakar.model;

import id.ac.unsera.sistempakar.web.Fmt;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Ringkasan satu kali diagnosa (header). */
public record Diagnosa(Long id, Long userId, String namaUser, String username, String namaPasien, Integer umur,
                       String jenisKelamin, java.time.LocalDate tanggalLahir, String alamat, String telepon,
                       BigDecimal tinggiCm, BigDecimal beratKg, Integer sistolik, Integer diastolik,
                       String metode, String hasilKode, String hasilNama, String hasilKategori, BigDecimal hasilCf,
                       String saran, LocalDateTime createdAt) {

    // ------------------------------------------------------------ data diri & pemeriksaan fisik (MCU)
    public String getJenisKelaminLabel() {
        return DataDiri.labelJenisKelamin(jenisKelamin);
    }

    public BigDecimal getImt() {
        return DataDiri.hitungImt(tinggiCm, beratKg);
    }

    public String getKategoriImt() {
        return DataDiri.kategoriImt(getImt());
    }

    public boolean isImtNormal() {
        return DataDiri.imtNormal(getImt());
    }

    public String getKategoriTekanan() {
        return DataDiri.kategoriTekanan(sistolik, diastolik);
    }

    public boolean isTekananNormal() {
        return DataDiri.tekananNormal(sistolik, diastolik);
    }

    public String getTekananTeks() {
        return sistolik == null || diastolik == null ? null : sistolik + "/" + diastolik;
    }

    /** Ada data pemeriksaan fisik (riwayat lama tidak punya). */
    public boolean isAdaDataFisik() {
        return tinggiCm != null || beratKg != null || sistolik != null;
    }

    public boolean isRisiko() {
        return "RISIKO".equals(hasilKategori);
    }

    public boolean isKlinis() {
        return "KLINIS".equals(metode);
    }

    public static final String LABEL_GEJALA = "Kuesioner gejala";
    public static final String LABEL_KLINIS = "Pemeriksaan klinis";

    
    public static String labelMetode(String metode) {
        return "KLINIS".equals(metode) ? LABEL_KLINIS : LABEL_GEJALA;
    }

    public String getMetodeLabel() {
        return labelMetode(metode);
    }

    /** Teks tooltip grafik perkembangan. */
    public String tooltip() {
        String hasil = !isTersimpulkan() ? "Tak tersimpulkan" : (isRisiko() ? "Risiko" : "Normal");
        return Fmt.tanggalS(createdAt) + " · " + namaPasien + " — " + hasil + " "
                + Fmt.persenS(hasilCf);
    }

    public boolean isTersimpulkan() {
        return hasilKode != null;
    }
}
