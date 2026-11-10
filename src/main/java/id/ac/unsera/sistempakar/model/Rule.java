package id.ac.unsera.sistempakar.model;

import java.math.BigDecimal;

public record Rule(Long id, String kode, String kondisi, Long penyakitId, BigDecimal cf, String keterangan,
                   String penyakitKode, String penyakitNama, String penyakitKategori) {

    public boolean isRisiko() {
        return "RISIKO".equals(penyakitKategori);
    }

    /** Kondisi IF dipecah per "AND" agar mudah dibaca. */
    public java.util.List<String> syarat() {
        return java.util.Arrays.stream(kondisi.split("(?i)\\s+AND\\s+")).map(String::trim).toList();
    }
}
