package id.ac.unsera.sistempakar.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Gejala / parameter klinis.
 *
 * @param bobot CF Pakar per id penyakit (hanya yang &gt; 0)
 */
public record Gejala(Long id, String kode, String nama, String keterangan, Map<Long, BigDecimal> bobot) {

    public BigDecimal bobotUntuk(Long penyakitId) {
        return bobot.getOrDefault(penyakitId, BigDecimal.ZERO);
    }
}
