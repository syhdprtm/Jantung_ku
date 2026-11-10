package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.DataKlinis;
import id.ac.unsera.sistempakar.model.Gejala;
import id.ac.unsera.sistempakar.model.Penyakit;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Menurunkan gejala D01–D13 (Tabel 5.3) secara otomatis dari data klinis, agar metode
 * CF berbasis gejala bisa dibandingkan dengan metode rule base pada data yang sama.
 * Gejala hasil turunan diberi CF user = 1 (fakta terukur).
 *
 * <p>Ambang batas mengikuti laporan: Resting BP 120, Cholesterol 200, Max HR 140,
 * Heart Rate Deficit = (220 − usia) − Max HR ≥ 40. Nilai Cholesterol 0 di dataset berarti
 * tidak tercatat sehingga tidak diturunkan ke gejala mana pun.</p>
 */
public final class TurunanGejala {

    private TurunanGejala() {
    }

    /** @return kode gejala -> keterangan alasan (gejala yang tidak muncul berarti tidak dialami) */
    public static Map<String, String> dari(DataKlinis d) {
        Map<String, String> g = new LinkedHashMap<>();
        if (d.restingBp() > 120) g.put("D01", "Resting BP " + d.restingBp() + " > 120");
        if (d.cholesterol() > 200) g.put("D02", "Cholesterol " + d.cholesterol() + " > 200");
        int defisit = (220 - d.age()) - d.maxHr();
        if (defisit >= 40) g.put("D03", "(220 − " + d.age() + ") − " + d.maxHr() + " = " + defisit + " ≥ 40");
        if ("Y".equals(d.exerciseAngina())) g.put("D04", "Exercise Angina = Y");
        if (d.fastingBs() == 1) g.put("D05", "Fasting BS = 1");
        if ("Flat".equals(d.stSlope())) g.put("D06", "ST Slope = Flat");
        if (d.maxHr() < 140) g.put("D07", "Max HR " + d.maxHr() + " < 140");
        if ("ST".equals(d.restingEcg())) g.put("D08", "Resting ECG = ST");
        if (d.restingBp() > 0 && d.restingBp() < 120) g.put("D09", "Resting BP " + d.restingBp() + " < 120");
        if (d.cholesterol() > 0 && d.cholesterol() < 200) g.put("D10", "Cholesterol " + d.cholesterol() + " < 200");
        if ("Normal".equals(d.restingEcg())) g.put("D11", "Resting ECG = Normal");
        if (d.fastingBs() == 0) g.put("D12", "Fasting BS = 0");
        if ("Up".equals(d.stSlope())) g.put("D13", "ST Slope = Up");
        return g;
    }

    public static final BigDecimal CF_FAKTA = BigDecimal.ONE;

    /** Hitung metode keyakinan gejala dari data klinis (gejala turunan, CF user = 1). */
    public static CertaintyFactor.HasilDiagnosa hitung(List<Penyakit> penyakit, List<Gejala> gejala, DataKlinis data) {
        Map<String, String> turunan = dari(data);
        Map<Long, BigDecimal> jawaban = new HashMap<>();
        for (var g : gejala) {
            if (turunan.containsKey(g.kode())) jawaban.put(g.id(), CF_FAKTA);
        }
        return CertaintyFactor.hitung(penyakit, gejala, jawaban);
    }
}
