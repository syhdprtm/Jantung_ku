package id.ac.unsera.sistempakar;

import id.ac.unsera.sistempakar.service.CertaintyFactor;
import id.ac.unsera.sistempakar.service.CertaintyFactor.Langkah;
import id.ac.unsera.sistempakar.service.CertaintyFactor.Masukan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Mencocokkan mesin CF dengan perhitungan manual di laporan (Tabel 5.5 dan 5.6). */
class CertaintyFactorTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void combineSesuaiRumus() {
        assertThat(CertaintyFactor.combine(d("0.24"), d("0.16"))).isEqualByComparingTo("0.3616");
        assertThat(CertaintyFactor.combine(d("0"), d("0.5"))).isEqualByComparingTo("0.5");
        assertThat(CertaintyFactor.combine(d("1"), d("0.3"))).isEqualByComparingTo("1");
    }

    @Test
    void jawabanNegatifMenurunkanKeyakinan() {
        // "Hampir pasti tidak" (-0,8) x pakar 0,8 = -0,64; lalu "Mungkin" (0,4) x pakar 0,8 = 0,32
        // berlawanan tanda: (-0,64 + 0,32) / (1 - min(0,64; 0,32)) = -0,32 / 0,68 = -0,47058824
        List<Langkah> l = CertaintyFactor.hitungLangkah(List.of(
                new Masukan("D01", "a", d("-0.8"), d("0.8")),
                new Masukan("D02", "b", d("0"), d("0.6")),      // Tidak tahu: tidak dihitung
                new Masukan("D03", "c", d("0.4"), d("0.8"))));
        assertThat(l).hasSize(2);
        assertThat(l.get(0).cfHE()).isEqualByComparingTo("-0.64");
        assertThat(CertaintyFactor.cfAkhir(l)).isEqualByComparingTo("-0.47058824");
        // kedua negatif: CF1 + CF2 x (1 + CF1)
        assertThat(CertaintyFactor.combine(d("-0.5"), d("-0.4"))).isEqualByComparingTo("-0.7");
        assertThat(CertaintyFactor.interpretasi(d("-1"))).isEqualTo("Pasti tidak");
        assertThat(CertaintyFactor.interpretasi(d("-0.64"))).isEqualTo("Kemungkinan besar tidak");
        assertThat(CertaintyFactor.interpretasi(d("-0.1"))).isEqualTo("Tidak ada indikasi");
    }

    @Test
    void sembilanTingkatKeyakinan() {
        var t = id.ac.unsera.sistempakar.model.TingkatKeyakinan.values();
        assertThat(t).hasSize(9);
        assertThat(t[0].getNilai()).isEqualByComparingTo("-1");
        assertThat(t[4].getLabel()).isEqualTo("Tidak tahu");
        assertThat(t[8].getNilai()).isEqualByComparingTo("1");
        assertThat(id.ac.unsera.sistempakar.model.TingkatKeyakinan.dariNilai("-0.8")).isPresent();
        assertThat(id.ac.unsera.sistempakar.model.TingkatKeyakinan.dariNilai("0.2")).isEmpty();
        // nilai skala lama 0,2 masuk rentang "Tidak tahu" (-0,2 s.d. 0,2)
        assertThat(id.ac.unsera.sistempakar.model.TingkatKeyakinan.labelDari(d("0.2"))).isEqualTo("Tidak tahu");
    }

    @Test
    void contohLaporanMenghasilkan098329956() {
        // kode, CF user (Tabel 5.5), CF pakar (Tabel 5.2)
        List<Masukan> masukan = List.of(
                new Masukan("D01", "Resting BP", d("0.6"), d("0.4")),
                new Masukan("D02", "Cholesterol", d("0.4"), d("0.4")),
                new Masukan("D03", "Heart Rate Deficit", d("0.6"), d("0.8")),
                new Masukan("D04", "Exercise Angina", d("0.8"), d("0.8")),
                new Masukan("D05", "Fasting BS", d("0.4"), d("0.8")),
                new Masukan("D06", "ST Slope", d("0.6"), d("0.8")),
                new Masukan("D07", "Max HR", d("0.6"), d("0.8")),
                new Masukan("D08", "Resting ECG", d("0.4"), d("0.6")));

        List<Langkah> langkah = CertaintyFactor.hitungLangkah(masukan);

        // kolom CF(User) x CF(Expert) pada Tabel 5.6
        assertThat(langkah).extracting(l -> l.cfHE().toPlainString())
                .containsExactly("0.24", "0.16", "0.48", "0.64", "0.32", "0.48", "0.48", "0.24");
        // CF gabungan bertahap sesuai uraian laporan
        assertThat(langkah).extracting(l -> l.cfHasil().toPlainString())
                .containsExactly("0.24", "0.3616", "0.668032", "0.88049152", "0.91873423",
                        "0.9577418", "0.97802574", "0.98329956");
        assertThat(CertaintyFactor.cfAkhir(langkah)).isEqualByComparingTo("0.98329956");
    }

    @Test
    void gejalaDenganNilaiNolDilewati() {
        List<Langkah> langkah = CertaintyFactor.hitungLangkah(List.of(
                new Masukan("D01", "a", d("0"), d("0.4")),
                new Masukan("D13", "b", d("0.8"), d("1")),
                new Masukan("D09", "c", d("0.6"), d("0.6"))));
        assertThat(langkah).hasSize(2);
        // 0.8 + 0.36 x (1 - 0.8) = 0.872
        assertThat(CertaintyFactor.cfAkhir(langkah)).isEqualByComparingTo("0.872");
    }

    @Test
    void interpretasiLinguistik() {
        assertThat(CertaintyFactor.interpretasi(d("1"))).isEqualTo("Pasti");
        assertThat(CertaintyFactor.interpretasi(d("0.98329956"))).isEqualTo("Hampir pasti");
        assertThat(CertaintyFactor.interpretasi(d("0.6"))).isEqualTo("Kemungkinan besar");
        assertThat(CertaintyFactor.interpretasi(d("0"))).isEqualTo("Tidak ada indikasi");
    }

    /** Bug: angka tampil 100,00% tetapi label "Hampir pasti". Label harus mengikuti persen yang ditampilkan. */
    @Test
    void labelSesuaiPersenYangDitampilkan() {
        assertThat(CertaintyFactor.interpretasi(d("0.99996"))).isEqualTo("Pasti");        // tampil 100,00%
        assertThat(CertaintyFactor.interpretasi(d("0.99994"))).isEqualTo("Hampir pasti"); // tampil 99,99%
        assertThat(CertaintyFactor.interpretasi(d("0.79996"))).isEqualTo("Hampir pasti"); // tampil 80,00%
        assertThat(CertaintyFactor.interpretasi(d("0.59995"))).isEqualTo("Kemungkinan besar"); // tampil 60,00%
        assertThat(CertaintyFactor.interpretasi(d("0.00004"))).isEqualTo("Tidak ada indikasi"); // tampil 0,00%
        var h = new CertaintyFactor.HasilHipotesis("P01", "Risiko", "RISIKO", null, d("0.99996"), java.util.List.of());
        assertThat(h.getArti()).startsWith("Berdasarkan data yang dimasukkan, sistem pasti");
    }
}
