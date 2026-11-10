package id.ac.unsera.sistempakar;

import id.ac.unsera.sistempakar.model.Gejala;
import id.ac.unsera.sistempakar.model.Metrik;
import id.ac.unsera.sistempakar.service.Evaluator;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluatorTest {

    /** Gejala & bobot sesuai Tabel 5.3 (P01 id 1, P02 id 2). */
    static List<Gejala> gejala() {
        String[][] g = {{"D01", "0.4", "0"}, {"D02", "0.4", "0"}, {"D03", "0.8", "0"}, {"D04", "0.8", "0"},
                {"D05", "0.8", "0"}, {"D06", "0.8", "0"}, {"D07", "0.8", "0"}, {"D08", "0.6", "0"},
                {"D09", "0", "0.6"}, {"D10", "0", "0.6"}, {"D11", "0", "0.6"}, {"D12", "0", "0.6"}, {"D13", "0", "1"}};
        List<Gejala> out = new java.util.ArrayList<>();
        long id = 1;
        for (String[] x : g) {
            Map<Long, BigDecimal> b = new java.util.HashMap<>();
            if (new BigDecimal(x[1]).signum() > 0) b.put(1L, new BigDecimal(x[1]));
            if (new BigDecimal(x[2]).signum() > 0) b.put(2L, new BigDecimal(x[2]));
            out.add(new Gejala(id++, x[0], x[0], "", b));
        }
        return out;
    }

    static final String HEADER = "Age,Sex,ChestPainType,RestingBP,Cholesterol,FastingBS,RestingECG,MaxHR,ExerciseAngina,Oldpeak,ST_Slope,HeartDisease\n";

    @Test
    void confusionMatrixSesuaiHitunganManual() throws Exception {
        String csv = HEADER
                // 1. Down, MaxHR 190 -> R1 Normal; label 0 -> TN (rule base)
                + "50,M,ATA,130,220,0,Normal,190,N,0.5,Down,0\n"
                // 2. Up, Oldpeak 3 -> R30 Sakit; label 1 -> TP
                + "55,M,ASY,140,260,0,ST,150,Y,3,Up,1\n"
                // 3. Up, Oldpeak 3 -> R30 Sakit; label 0 -> FP
                + "60,F,ASY,130,210,0,Normal,140,N,3.0,Up,0\n"
                // 4. Down, MaxHR 190 -> R1 Normal; label 1 -> FN
                + "45,M,NAP,120,180,0,Normal,190,N,0,Down,1\n"
                // 5. baris rusak -> dilewati
                + "abc,M,ASY,140,260,0,ST,150,Y,3,Up,1\n";
        var h = Evaluator.evaluasi(new StringReader(csv), RuleEngineTest.PENYAKIT, RuleEngineTest.rulesLaporan(), gejala());

        assertThat(h.jumlah()).isEqualTo(4);
        assertThat(h.dilewati()).isEqualTo(1);
        var k = h.metrik().get(Evaluator.KLINIS);
        assertThat(new int[]{k.tp, k.tn, k.fp, k.fn, k.takTercakup}).containsExactly(1, 1, 1, 1, 0);
        assertThat(h.rules().get("R1").terpicu).isEqualTo(2);
        assertThat(h.rules().get("R1").benar).isEqualTo(1);
        assertThat(h.rules().get("R30").terpicu).isEqualTo(2);

        Metrik m = new Metrik(1L, "KLINIS", k.tp, k.tn, k.fp, k.fn, k.takTercakup);
        assertThat(m.getAkurasi()).isEqualByComparingTo("0.5");
        assertThat(m.getPresisi()).isEqualByComparingTo("0.5");
        assertThat(m.getRecall()).isEqualByComparingTo("0.5");
        assertThat(m.getF1()).isEqualByComparingTo("0.5");

        // metode gejala juga dijalankan untuk setiap baris
        var g = h.metrik().get(Evaluator.GEJALA);
        assertThat(g.tp + g.tn + g.fp + g.fn + g.takTercakup).isEqualTo(4);
    }

    @Test
    void mendukungPemisahTitikKomaDanDesimalKoma() throws Exception {
        String csv = HEADER.replace(',', ';') + "55;M;ASY;140;260;0;ST;150;Y;3,0;Up;1\n";
        var h = Evaluator.evaluasi(new StringReader(csv), RuleEngineTest.PENYAKIT, RuleEngineTest.rulesLaporan(), gejala());
        assertThat(h.metrik().get(Evaluator.KLINIS).tp).isEqualTo(1);
    }

    @Test
    void kolomWajibHilang() {
        assertThatThrownBy(() -> Evaluator.evaluasi(new StringReader("Age,Sex\n50,M\n"),
                RuleEngineTest.PENYAKIT, RuleEngineTest.rulesLaporan(), gejala()))
                .hasMessageContaining("ChestPainType").hasMessageContaining("HeartDisease");
    }
}
