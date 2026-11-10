package id.ac.unsera.sistempakar.model;

/**
 * Hipotesis/kesimpulan sistem pakar.
 *
 * @param kategori RISIKO (indikasi penyakit) atau NORMAL (kondisi sehat)
 */
public record Penyakit(Long id, String kode, String nama, String kategori, String keterangan, String saran,
                       Integer jmlGejala, Integer jmlRule) {

    public boolean isRisiko() {
        return "RISIKO".equals(kategori);
    }
}
