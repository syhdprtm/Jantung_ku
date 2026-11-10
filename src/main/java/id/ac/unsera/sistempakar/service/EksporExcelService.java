package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.Diagnosa;
import id.ac.unsera.sistempakar.model.Metrik;
import id.ac.unsera.sistempakar.repository.EvaluasiRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/** Ekspor data ke Excel (.xlsx) untuk lampiran skripsi. */
@Service
public class EksporExcelService {

    /** Gaya sel yang dipakai bersama dalam satu workbook. */
    private static final class Gaya {
        final CellStyle header, tanggal, persen, desimal;

        Gaya(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            tanggal = wb.createCellStyle();
            tanggal.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
            persen = wb.createCellStyle();
            persen.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("0.00%"));
            desimal = wb.createCellStyle();
            desimal.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("0.00000000"));
        }
    }

    private static void header(Sheet s, Gaya g, String... kolom) {
        Row r = s.createRow(0);
        for (int i = 0; i < kolom.length; i++) {
            var c = r.createCell(i);
            c.setCellValue(kolom[i]);
            c.setCellStyle(g.header);
        }
        s.createFreezePane(0, 1);
    }

    private static void angka(Row r, int i, Number v, CellStyle st) {
        var c = r.createCell(i);
        if (v == null) {
            c.setBlank();
            return;
        }
        c.setCellValue(v.doubleValue());
        if (st != null) c.setCellStyle(st);
    }

    private static void teks(Row r, int i, String v) {
        r.createCell(i).setCellValue(v == null ? "" : v);
    }

    private static void tanggal(Row r, int i, LocalDateTime t, Gaya g) {
        var c = r.createCell(i);
        if (t == null) return;
        c.setCellValue(Date.from(t.atZone(java.time.ZoneId.systemDefault()).toInstant()));
        c.setCellStyle(g.tanggal);
    }

    private static void rapikan(Sheet s, int kolom) {
        for (int i = 0; i < kolom; i++) {
            s.autoSizeColumn(i);
            s.setColumnWidth(i, Math.min(s.getColumnWidth(i) + 512, 60 * 256));
        }
    }

    private static byte[] tulis(Workbook wb) {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membuat file Excel", e);
        }
    }

    // ------------------------------------------------------------ riwayat

    public byte[] riwayat(List<Diagnosa> rows, boolean adminView) {
        Workbook wb = new XSSFWorkbook();
        Gaya g = new Gaya(wb);
        Sheet s = wb.createSheet("Riwayat diagnosa");
        String[] kolom = adminView
                ? new String[]{"No", "Tanggal", "Pasien", "Umur", "L/P", "IMT", "Tekanan darah", "Metode", "Akun", "Hasil", "Kategori", "CF", "Persen"}
                : new String[]{"No", "Tanggal", "Pasien", "Umur", "L/P", "IMT", "Tekanan darah", "Metode", "Hasil", "Kategori", "CF", "Persen"};
        header(s, g, kolom);
        int n = 1;
        for (Diagnosa d : rows) {
            Row r = s.createRow(n);
            int i = 0;
            angka(r, i++, n, null);
            tanggal(r, i++, d.createdAt(), g);
            teks(r, i++, d.namaPasien());
            angka(r, i++, d.umur(), null);
            teks(r, i++, d.jenisKelamin() == null ? "-" : d.jenisKelamin());
            angka(r, i++, d.getImt(), null);
            teks(r, i++, d.getTekananTeks() == null ? "-" : d.getTekananTeks());
            teks(r, i++, d.getMetodeLabel());
            if (adminView) teks(r, i++, "@" + d.username());
            teks(r, i++, d.isTersimpulkan() ? d.hasilNama() : "Tidak dapat disimpulkan");
            teks(r, i++, d.hasilKategori() == null ? "-" : d.hasilKategori());
            angka(r, i++, d.hasilCf(), g.desimal);
            angka(r, i, d.hasilCf(), g.persen);
            n++;
        }
        s.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, n - 1), 0, kolom.length - 1));
        rapikan(s, kolom.length);
        return tulis(wb);
    }

    // ------------------------------------------------------------ evaluasi

    public byte[] evaluasi(EvaluasiRepository.Evaluasi e, List<Metrik> metrik, List<EvaluasiRepository.RuleStat> rules,
                           List<EvaluasiRepository.Baris> baris) {
        Workbook wb = new XSSFWorkbook();
        Gaya g = new Gaya(wb);

        Sheet s = wb.createSheet("Ringkasan");
        header(s, g, "Metrik", "Pemeriksaan klinis (rule base)", "Kuesioner gejala (CF combine)");
        Metrik a = metrik.stream().filter(m -> "KLINIS".equals(m.metode())).findFirst().orElse(null);
        Metrik b = metrik.stream().filter(m -> "GEJALA".equals(m.metode())).findFirst().orElse(null);
        Object[][] isi = {
                {"File", e.namaFile(), e.namaFile()},
                {"Baris diuji", e.jumlah(), e.jumlah()},
                {"Baris dilewati (tidak valid)", e.dilewati(), e.dilewati()},
                {"True positive (TP)", a == null ? null : a.tp(), b == null ? null : b.tp()},
                {"True negative (TN)", a == null ? null : a.tn(), b == null ? null : b.tn()},
                {"False positive (FP)", a == null ? null : a.fp(), b == null ? null : b.fp()},
                {"False negative (FN)", a == null ? null : a.fn(), b == null ? null : b.fn()},
                {"Tak tercakup", a == null ? null : a.takTercakup(), b == null ? null : b.takTercakup()},
                {"Akurasi", a == null ? null : a.getAkurasi(), b == null ? null : b.getAkurasi()},
                {"Akurasi (baris tercakup)", a == null ? null : a.getAkurasiTercakup(), b == null ? null : b.getAkurasiTercakup()},
                {"Presisi", a == null ? null : a.getPresisi(), b == null ? null : b.getPresisi()},
                {"Recall (sensitivitas)", a == null ? null : a.getRecall(), b == null ? null : b.getRecall()},
                {"Spesifisitas", a == null ? null : a.getSpesifisitas(), b == null ? null : b.getSpesifisitas()},
                {"F1-score", a == null ? null : a.getF1(), b == null ? null : b.getF1()},
                {"Cakupan data", a == null ? null : a.getCakupan(), b == null ? null : b.getCakupan()}};
        int n = 1;
        for (Object[] baru : isi) {
            Row r = s.createRow(n++);
            teks(r, 0, (String) baru[0]);
            for (int i = 1; i <= 2; i++) {
                Object v = baru[i];
                if (v instanceof BigDecimal bd) angka(r, i, bd, g.persen);
                else if (v instanceof Number num) angka(r, i, num, null);
                else teks(r, i, v == null ? "-" : v.toString());
            }
        }
        rapikan(s, 3);

        Sheet sr = wb.createSheet("Per rule");
        header(sr, g, "Rule", "THEN", "CF rule", "Terpicu", "Benar", "Presisi rule");
        n = 1;
        for (var r0 : rules) {
            Row r = sr.createRow(n++);
            teks(r, 0, r0.ruleKode());
            teks(r, 1, r0.penyakitKode() == null ? "-" : r0.penyakitKode());
            angka(r, 2, r0.cf(), null);
            angka(r, 3, r0.terpicu(), null);
            angka(r, 4, r0.benar(), null);
            if (r0.terpicu() > 0) angka(r, 5, r0.getPresisi(), g.persen);
            else teks(r, 5, "tidak pernah terpicu");
        }
        rapikan(sr, 6);

        Sheet sb = wb.createSheet("Salah prediksi");
        header(sb, g, "Baris CSV", "Metode", "Aktual", "Prediksi", "CF", "Rule terpicu", "Data");
        n = 1;
        for (var x : baris) {
            Row r = sb.createRow(n++);
            angka(r, 0, x.baris(), null);
            teks(r, 1, id.ac.unsera.sistempakar.model.Diagnosa.labelMetode(x.metode()));
            teks(r, 2, x.aktual() == 1 ? "Sakit" : "Normal");
            teks(r, 3, x.prediksi() == null ? "Tak tercakup" : (x.prediksi() == 1 ? "Sakit" : "Normal"));
            angka(r, 4, x.cf(), g.desimal);
            teks(r, 5, x.ruleKode());
            teks(r, 6, x.data());
        }
        rapikan(sb, 7);
        return tulis(wb);
    }
}
