package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.Atribut;
import id.ac.unsera.sistempakar.model.DataKlinis;
import id.ac.unsera.sistempakar.model.Gejala;
import id.ac.unsera.sistempakar.model.Penyakit;
import id.ac.unsera.sistempakar.service.CertaintyFactor.HasilHipotesis;
import id.ac.unsera.sistempakar.service.RuleEngine.RuleAktif;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Menguji sistem pakar terhadap dataset berlabel (kolom {@code HeartDisease}: 1 = sakit, 0 = normal).
 * Kedua metode dijalankan pada setiap baris:
 * <ul>
 *   <li><b>KLINIS</b> — forward chaining rule base</li>
 *   <li><b>GEJALA</b> — CF gejala yang diturunkan otomatis dari data klinis</li>
 * </ul>
 * Kelas ini murni (tanpa database) agar mudah diuji.
 */
public final class Evaluator {

    public static final String KLINIS = "KLINIS";
    public static final String GEJALA = "GEJALA";
    public static final int MAKS_CONTOH = 300;

    private Evaluator() {
    }

    /** Hitungan confusion matrix (positif = penyakit jantung). */
    public static final class Hitung {
        public int tp, tn, fp, fn, takTercakup;

        void catat(int aktual, Integer prediksi) {
            if (prediksi == null) takTercakup++;
            else if (aktual == 1 && prediksi == 1) tp++;
            else if (aktual == 0 && prediksi == 0) tn++;
            else if (aktual == 0) fp++;
            else fn++;
        }
    }

    public static final class StatRule {
        public int terpicu, benar;
    }

    /** Baris yang salah prediksi atau tidak tercakup. */
    public record BarisSalah(int baris, String metode, int aktual, Integer prediksi, BigDecimal cf, String rule, String data) {
    }

    public record HasilEvaluasi(int jumlah, int dilewati, List<String> kesalahan, Map<String, Hitung> metrik,
                                Map<String, StatRule> rules, List<BarisSalah> salah) {
    }

    private static Integer prediksi(HasilHipotesis kesimpulan) {
        if (kesimpulan == null) return null;
        return kesimpulan.isRisiko() ? 1 : 0;
    }

    private static String kunci(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    public static HasilEvaluasi evaluasi(Reader csv, List<Penyakit> penyakit, List<RuleAktif> rules, List<Gejala> gejala)
            throws IOException {
        BufferedReader br = new BufferedReader(csv);
        String header = br.readLine();
        if (header == null) throw new IllegalArgumentException("File CSV kosong.");
        header = header.replace("﻿", "");
        String pemisah = header.contains(";") && !header.contains(",") ? ";" : ",";
        String[] kolom = header.split(pemisah, -1);

        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < kolom.length; i++) idx.put(kunci(kolom[i]), i);
        List<String> hilang = new ArrayList<>();
        Map<Atribut, Integer> posisi = new EnumMap<>(Atribut.class);
        for (Atribut a : Atribut.values()) {
            Integer p = idx.get(kunci(a.getKolom()));
            if (p == null) hilang.add(a.getKolom());
            else posisi.put(a, p);
        }
        Integer posLabel = idx.get("heartdisease");
        if (posLabel == null) hilang.add("HeartDisease");
        if (!hilang.isEmpty()) {
            throw new IllegalArgumentException("Kolom berikut tidak ditemukan di CSV: " + String.join(", ", hilang)
                    + ". Header yang dibutuhkan sama dengan dataset Heart Failure Prediction.");
        }

        Map<String, Hitung> metrik = new LinkedHashMap<>();
        metrik.put(KLINIS, new Hitung());
        metrik.put(GEJALA, new Hitung());
        Map<String, StatRule> statRule = new LinkedHashMap<>();
        rules.forEach(r -> statRule.put(r.kode(), new StatRule()));
        List<BarisSalah> salah = new ArrayList<>();
        List<String> kesalahan = new ArrayList<>();
        int jumlah = 0, dilewati = 0, baris = 1, salahK = 0, salahG = 0;

        String line;
        while ((line = br.readLine()) != null) {
            baris++;
            if (line.isBlank()) continue;
            String[] v = line.split(pemisah, -1);
            Map<Atribut, String> raw = new EnumMap<>(Atribut.class);
            posisi.forEach((a, p) -> raw.put(a, p < v.length ? v[p] : ""));
            KlinisInput.Hasil in = KlinisInput.baca(raw);
            String label = posLabel < v.length ? v[posLabel].trim() : "";
            if (!in.ok() || !(label.equals("0") || label.equals("1"))) {
                dilewati++;
                if (kesalahan.size() < 5) {
                    kesalahan.add("Baris " + baris + ": " + (in.ok() ? "HeartDisease harus 0 atau 1" : String.join(" ", in.errors())));
                }
                continue;
            }
            jumlah++;
            DataKlinis d = in.data();
            int aktual = Integer.parseInt(label);

            // --- metode KLINIS (rule base)
            var inf = RuleEngine.inferensi(penyakit, rules, d);
            Integer pk = prediksi(inf.kesimpulan());
            metrik.get(KLINIS).catat(aktual, pk);
            for (var t : inf.terpicu()) {
                StatRule s = statRule.computeIfAbsent(t.rule().kode(), k -> new StatRule());
                s.terpicu++;
                int prRule = "RISIKO".equals(t.rule().kategori()) ? 1 : 0;
                if (prRule == aktual) s.benar++;
            }
            if ((pk == null || pk != aktual) && salahK++ < MAKS_CONTOH) {
                String r = inf.terpicu().stream().map(t -> t.rule().kode()).collect(Collectors.joining(", "));
                salah.add(new BarisSalah(baris, KLINIS, aktual, pk,
                        inf.kesimpulan() == null ? BigDecimal.ZERO : inf.kesimpulan().cf(), r.isEmpty() ? null : r, d.ringkas()));
            }

            // --- metode GEJALA (CF gejala turunan)
            var hg = TurunanGejala.hitung(penyakit, gejala, d);
            Integer pg = prediksi(hg.kesimpulan());
            metrik.get(GEJALA).catat(aktual, pg);
            if ((pg == null || pg != aktual) && salahG++ < MAKS_CONTOH) {
                salah.add(new BarisSalah(baris, GEJALA, aktual, pg,
                        hg.kesimpulan() == null ? BigDecimal.ZERO : hg.kesimpulan().cf(), null, d.ringkas()));
            }
        }
        if (jumlah == 0) throw new IllegalArgumentException("Tidak ada baris data yang valid di CSV."
                + (kesalahan.isEmpty() ? "" : " Contoh: " + kesalahan.getFirst()));
        return new HasilEvaluasi(jumlah, dilewati, kesalahan, metrik, statRule, salah);
    }
}
