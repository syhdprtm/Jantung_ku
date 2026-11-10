package id.ac.unsera.sistempakar;

import id.ac.unsera.sistempakar.config.DataSeeder;
import id.ac.unsera.sistempakar.model.Atribut;
import id.ac.unsera.sistempakar.model.DataKlinis;
import id.ac.unsera.sistempakar.model.Penyakit;
import id.ac.unsera.sistempakar.model.Syarat;
import id.ac.unsera.sistempakar.service.KlinisInput;
import id.ac.unsera.sistempakar.service.RuleEngine;
import id.ac.unsera.sistempakar.service.RuleEngine.RuleAktif;
import id.ac.unsera.sistempakar.service.RuleParser;
import id.ac.unsera.sistempakar.service.RuleValidator;
import id.ac.unsera.sistempakar.service.TurunanGejala;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineTest {

    static final List<Penyakit> PENYAKIT = List.of(
            new Penyakit(1L, "P01", "Terdiagnosa Penyakit Jantung", "RISIKO", "", "", 0, 0),
            new Penyakit(2L, "P02", "Tidak Terdiagnosa (Normal)", "NORMAL", "", "", 0, 0));

    static List<RuleAktif> rulesLaporan() {
        return DataSeeder.RULES.stream().map(r -> new RuleAktif(null, r.kode(), r.kondisi(), r.hasil(),
                r.hasil(), r.hasil().equals("P01") ? "RISIKO" : "NORMAL", "", new BigDecimal(r.cf()), r.keterangan(),
                RuleParser.parse(r.kondisi()))).toList();
    }

    static DataKlinis pasien(int age, String sex, String cp, int bp, int chol, int fbs, String ecg, int maxHr,
                             String angina, String oldpeak, String slope) {
        return new DataKlinis(age, sex, cp, bp, chol, fbs, ecg, maxHr, angina, new BigDecimal(oldpeak), slope);
    }

    @Test
    void semua49RuleLaporanBisaDiparse() {
        assertThat(DataSeeder.RULES).hasSize(49);
        for (var r : DataSeeder.RULES) {
            assertThat(RuleParser.parse(r.kondisi())).as(r.kode()).isNotEmpty();
        }
    }

    @Test
    void parserMenanganiVariasiPenulisan() {
        List<Syarat> s = RuleParser.parse("IF St.Slope Flat AND Excercise Angina N AND Oldpeak > 2,3 AND Fasting BS 1 = true");
        assertThat(s).extracting(Syarat::teks)
                .containsExactly("ST_Slope = Flat", "ExerciseAngina = N", "Oldpeak > 2.3", "FastingBS = 1");
        assertThatThrownBy(() -> RuleParser.parse("Tinggi Badan > 170")).hasMessageContaining("tidak dikenal");
        assertThatThrownBy(() -> RuleParser.parse("ST Slope Miring")).hasMessageContaining("Miring");
    }

    @Test
    void validatorMenandaiRuleMustahilDariLaporan() {
        Map<String, List<String>> masalah = new java.util.LinkedHashMap<>();
        for (var r : rulesLaporan()) {
            var m = RuleValidator.periksa(r.syarat());
            if (!m.isEmpty()) masalah.put(r.kode(), m);
        }
        // R3: Age <= 60 AND Age > 63 · R5: Age > 67 AND Age <= 60 · R44: Fasting BS > 1 (data hanya 0/1)
        assertThat(masalah).containsOnlyKeys("R3", "R5", "R44");
    }

    @Test
    void validatorMenemukanTumpangTindihR13R14() {
        var tt = RuleValidator.tumpangTindih(rulesLaporan());
        assertThat(tt).anySatisfy(t -> {
            assertThat(t.ruleA()).isEqualTo("R13");
            assertThat(t.ruleB()).isEqualTo("R14");
        });
    }

    @Test
    void forwardChainingMemicuRuleYangTepat() {
        // ST Slope Down, Max HR 190 -> R1 (Normal, CF 0.8)
        var h = RuleEngine.inferensi(PENYAKIT, rulesLaporan(), pasien(50, "M", "ATA", 130, 220, 0, "Normal", 190, "N", "0.5", "Down"));
        assertThat(h.terpicu()).extracting(t -> t.rule().kode()).containsExactly("R1");
        assertThat(h.kesimpulan().kode()).isEqualTo("P02");
        assertThat(h.kesimpulan().cf()).isEqualByComparingTo("0.8");

        // ST Slope Up, Oldpeak 3 -> R30 (Terdiagnosis, CF 0.95)
        h = RuleEngine.inferensi(PENYAKIT, rulesLaporan(), pasien(55, "M", "ASY", 140, 260, 0, "ST", 150, "Y", "3", "Up"));
        assertThat(h.terpicu()).extracting(t -> t.rule().kode()).containsExactly("R30");
        assertThat(h.kesimpulan().kode()).isEqualTo("P01");
        assertThat(h.kesimpulan().cf()).isEqualByComparingTo("0.95");
        // fasilitas penjelasan: setiap syarat dicek dengan nilai pasien
        assertThat(h.terpicu().getFirst().cek()).allMatch(RuleEngine.CekSyarat::cocok);
    }

    @Test
    void duaRuleTerpicuDigabungDenganCfCombine() {
        // Wanita, ST Flat, Max HR 150, FBS 1, Oldpeak 3 -> R9 (0.8) dan R10 (0.9) sama-sama P01
        var h = RuleEngine.inferensi(PENYAKIT, rulesLaporan(), pasien(45, "F", "ASY", 130, 220, 1, "Normal", 150, "N", "3", "Flat"));
        assertThat(h.terpicu()).extracting(t -> t.rule().kode()).containsExactly("R9", "R10");
        // 0.8 + 0.9 x (1 - 0.8) = 0.98
        assertThat(h.kesimpulan().cf()).isEqualByComparingTo("0.98");
    }

    @Test
    void dataYangTidakTercakupRuleManaPun() {
        // Tanpa rule yang cocok, sistem tidak boleh mengarang kesimpulan
        var h = RuleEngine.inferensi(PENYAKIT, List.of(), pasien(50, "M", "ATA", 130, 220, 0, "Normal", 150, "N", "0", "Up"));
        assertThat(h.isTercakup()).isFalse();
        assertThat(h.kesimpulan()).isNull();
    }

    @Test
    void turunanGejalaDariDataKlinis() {
        var g = TurunanGejala.dari(pasien(60, "M", "ASY", 140, 260, 1, "ST", 120, "Y", "2", "Flat"));
        // (220-60)-120 = 40 -> D03
        assertThat(g).containsOnlyKeys("D01", "D02", "D03", "D04", "D05", "D06", "D07", "D08");
        g = TurunanGejala.dari(pasien(40, "F", "ATA", 110, 180, 0, "Normal", 175, "N", "0", "Up"));
        assertThat(g).containsOnlyKeys("D09", "D10", "D11", "D12", "D13");
    }

    @Test
    void validasiInputKlinis() {
        Map<Atribut, String> raw = new EnumMap<>(Atribut.class);
        raw.put(Atribut.AGE, "54"); raw.put(Atribut.SEX, "m"); raw.put(Atribut.CHEST_PAIN, "asy");
        raw.put(Atribut.RESTING_BP, "140"); raw.put(Atribut.CHOLESTEROL, "239"); raw.put(Atribut.FASTING_BS, "0");
        raw.put(Atribut.RESTING_ECG, "normal"); raw.put(Atribut.MAX_HR, "160"); raw.put(Atribut.EXERCISE_ANGINA, "n");
        raw.put(Atribut.OLDPEAK, "1,2"); raw.put(Atribut.ST_SLOPE, "flat");
        var ok = KlinisInput.baca(raw);
        assertThat(ok.ok()).isTrue();
        assertThat(ok.data().sex()).isEqualTo("M");
        assertThat(ok.data().oldpeak()).isEqualByComparingTo("1.2");

        raw.put(Atribut.AGE, "abc");
        raw.put(Atribut.ST_SLOPE, "naik");
        assertThat(KlinisInput.baca(raw).errors()).hasSize(2);
    }
}
