package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.Atribut;
import id.ac.unsera.sistempakar.model.Diagnosa;
import id.ac.unsera.sistempakar.service.CertaintyFactor.HasilHipotesis;
import id.ac.unsera.sistempakar.service.CertaintyFactor.Langkah;
import id.ac.unsera.sistempakar.service.DiagnosaService.HasilLengkap;
import id.ac.unsera.sistempakar.web.Fmt;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Laporan PDF hasil pemeriksaan bergaya surat laporan medical check up (MCU):
 * <ul>
 *   <li>halaman 1: kop surat (logo, nama sistem, garis ganda), judul "LAPORAN PEMERIKSAAN KESEHATAN /
 *       MEDICAL CHECK UP REPORT", identitas (label : nilai), hasil bernomor (pemeriksaan fisik, kuesioner/data klinis,
 *       analisis sistem pakar, catatan khusus), KESIMPULAN, REKOMENDASI, pengingat medis, dan tanda tangan pakar;</li>
 *   <li>lampiran: rincian jawaban/data klinis dan perhitungan Certainty Factor (kop ringkas di setiap halaman);</li>
 *   <li>kaki "Halaman x dari y" di setiap halaman.</li>
 * </ul>
 */
@Service
public class LaporanPdfService {

    // palet mengikuti tema aplikasi (teal klinis), dipilih kontras tinggi untuk cetak
    private static final Color INK = new Color(0x10, 0x26, 0x20);
    private static final Color INK2 = new Color(0x34, 0x4a, 0x43);
    private static final Color MUTED = new Color(0x66, 0x79, 0x6f);
    private static final Color LINE = new Color(0xd9, 0xe2, 0xdd);
    private static final Color SURFACE = new Color(0xf3, 0xf6, 0xf4);
    private static final Color TEAL = new Color(0x1f, 0x6b, 0x5c);
    private static final Color TEAL_2 = new Color(0x16, 0x4d, 0x42);
    private static final Color TEAL_SOFT = new Color(0xe2, 0xef, 0xea);
    // tanpa oranye: risiko merah tua, normal hijau (sama dengan tema aplikasi)
    private static final Color RISK = new Color(0x9c, 0x26, 0x38);
    private static final Color RISK_BAR = RISK;
    private static final Color RISK_BG = new Color(0xf7, 0xe9, 0xeb);
    private static final Color OK = new Color(0x1a, 0x7a, 0x62);
    private static final Color OK_BAR = new Color(0x22, 0xa3, 0x83);
    private static final Color OK_BG = new Color(0xdc, 0xef, 0xe6);

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final DateTimeFormatter CETAK = DateTimeFormatter.ofPattern("d MMMM yyyy, HH.mm 'WIB'", Locale.forLanguageTag("id-ID"));

    private final Fmt fmt;
    private final id.ac.unsera.sistempakar.repository.PenggunaRepository penggunaRepo;

    public LaporanPdfService(Fmt fmt, id.ac.unsera.sistempakar.repository.PenggunaRepository penggunaRepo) {
        this.fmt = fmt;
        this.penggunaRepo = penggunaRepo;
    }

    private static Font f(float size, int style, Color c) {
        return FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED, size, style, c);
    }

    private static final Font JUDUL = f(18, Font.BOLD, INK);
    private static final Font LABEL = f(7.5f, Font.BOLD, TEAL);
    private static final Font H2 = f(11, Font.BOLD, INK);
    private static final Font BODY = f(9.5f, Font.NORMAL, INK);
    private static final Font BODY2 = f(9.5f, Font.NORMAL, INK2);
    private static final Font BOLD = f(9.5f, Font.BOLD, INK);
    private static final Font SMALL = f(8, Font.NORMAL, MUTED);
    private static final Font SMALL_B = f(8, Font.BOLD, INK2);
    private static final Font TH = f(7.5f, Font.BOLD, MUTED);
    private static final Font MONO = f(9, Font.NORMAL, INK);   // syarat aturan: Helvetica agar tetap jelas saat dicetak

    /**
     * Helvetica standar hanya memuat aksara Windows-1252; simbol di luar itu diganti padanan ASCII.
     * Ditulis dengan escape Unicode agar tidak bergantung pada encoding berkas sumber.
     */
    static String aman(String s) {
        if (s == null || s.isBlank()) return "-";
        return s.replace("−", "-").replace("≥", ">=").replace("≤", "<=")
                .replace("→", "->").replace("←", "<-").replace("✓", "v")
                .replace("–", "-").replace("—", "-").replace("…", "...");
    }

    private static Paragraph p(String s, Font font) {
        return new Paragraph(aman(s), font);
    }

    /** Nomor laporan yang mudah dirujuk, mis. JK-2026-000012. */
    static String nomor(Diagnosa d) {
        int tahun = d.createdAt() == null ? LocalDateTime.now(WIB).getYear() : d.createdAt().getYear();
        return String.format("JK-%d-%06d", tahun, d.id());
    }

    // ------------------------------------------------------------ kop & kaki di setiap halaman
    private static final class KopKaki extends PdfPageEventHelper {
        private final String nomor;
        private final String dicetak;
        private PdfTemplate total;
        private BaseFont bf;

        KopKaki(String nomor, String dicetak) {
            this.nomor = nomor;
            this.dicetak = dicetak;
        }

        @Override
        public void onOpenDocument(PdfWriter w, Document doc) {
            total = w.getDirectContent().createTemplate(30, 12);
            try {
                bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter w, Document doc) {
            PdfContentByte cb = w.getDirectContent();
            float kiri = doc.left(), kanan = doc.right();
            float atas = doc.getPageSize().getHeight() - 30;
            if (w.getPageNumber() > 1) kopRingkas(cb, kiri, kanan, atas);
            kaki(w, cb, doc, kiri, kanan);
        }

        private void kopRingkas(PdfContentByte cb, float kiri, float kanan, float atas) {

            // logo: kotak teal membulat dengan hati putih
            cb.saveState();
            cb.setColorFill(TEAL);
            cb.roundRectangle(kiri, atas - 22, 22, 22, 5);
            cb.fill();
            cb.setColorFill(Color.WHITE);
            float hx = kiri + 11, hy = atas - 11.5f;
            cb.moveTo(hx, hy - 5.5f);
            cb.curveTo(hx - 9, hy + 0.5f, hx - 4.5f, hy + 7, hx, hy + 2.6f);
            cb.curveTo(hx + 4.5f, hy + 7, hx + 9, hy + 0.5f, hx, hy - 5.5f);
            cb.fill();
            cb.restoreState();

            teks(cb, "Jantungku", kiri + 30, atas - 9, 11.5f, INK, true, Element.ALIGN_LEFT);
            teks(cb, "Hasil pemeriksaan kesehatan · skrining jantung", kiri + 30, atas - 20, 7.5f, MUTED, false, Element.ALIGN_LEFT);
            teks(cb, "No. RM  " + nomor, kanan, atas - 9, 8, INK, true, Element.ALIGN_RIGHT);
            teks(cb, "Dicetak " + dicetak, kanan, atas - 20, 7.5f, MUTED, false, Element.ALIGN_RIGHT);

            cb.saveState();
            cb.setColorStroke(TEAL);
            cb.setLineWidth(1.2f);
            cb.moveTo(kiri, atas - 30);
            cb.lineTo(kanan, atas - 30);
            cb.stroke();
            cb.restoreState();
        }

        private void kaki(PdfWriter w, PdfContentByte cb, Document doc, float kiri, float kanan) {
            cb.saveState();
            float bawah = doc.bottom() - 22;
            cb.setColorStroke(LINE);
            cb.setLineWidth(.6f);
            cb.moveTo(kiri, bawah + 12);
            cb.lineTo(kanan, bawah + 12);
            cb.stroke();
            cb.restoreState();
            teks(cb, "Hasil skrining ini BUKAN diagnosis dokter. Konsultasikan ke tenaga kesehatan; darurat hubungi 119.",
                    kiri, bawah, 7.5f, MUTED, false, Element.ALIGN_LEFT);
            String hal = "Halaman " + w.getPageNumber() + " dari ";
            float lebarHal = bf.getWidthPoint(hal, 7.5f);
            teks(cb, hal, kanan - 14 - lebarHal, bawah, 7.5f, MUTED, false, Element.ALIGN_LEFT);
            cb.addTemplate(total, kanan - 14, bawah);
        }

        @Override
        public void onCloseDocument(PdfWriter w, Document doc) {
            total.beginText();
            total.setFontAndSize(bf, 7.5f);
            total.setColorFill(MUTED);
            total.setTextMatrix(0, 0);
            // onCloseDocument dipanggil setelah nomor halaman maju satu; total halaman = nomor - 1
            total.showText(String.valueOf(w.getPageNumber() - 1));
            total.endText();
        }

        private void teks(PdfContentByte cb, String s, float x, float y, float ukuran, Color warna, boolean tebal, int rata) {
            try {
                BaseFont b = tebal ? BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED) : bf;
                cb.beginText();
                cb.setFontAndSize(b, ukuran);
                cb.setColorFill(warna);
                cb.showTextAligned(rata, aman(s), x, y, 0);
                cb.endText();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // ------------------------------------------------------------ blok bantu
    private static Paragraph judulBagian(String nomor, String judul) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(18);
        p.setSpacingAfter(9);   // jarak cukup agar tabel di bawahnya tidak menimpa judul
        p.add(new Chunk(nomor + "   ", f(11, Font.BOLD, TEAL)));
        p.add(new Chunk(aman(judul), H2));
        p.setKeepTogether(true);
        return p;
    }

    private static PdfPCell kosong() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static PdfPCell sel(String s, Font font, int rata, Color latar) {
        PdfPCell c = new PdfPCell(new Phrase(aman(s), font));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setBorderWidth(.6f);
        c.setPaddingTop(6);
        c.setPaddingBottom(7);
        c.setPaddingLeft(6);
        c.setPaddingRight(6);
        c.setHorizontalAlignment(rata);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (latar != null) c.setBackgroundColor(latar);
        return c;
    }

    /** Tabel data: kepala abu muda, baris berselang-seling, kolom angka rata kanan. */
    private static final class Tabel {
        final PdfPTable t;
        final int[] rata;
        int baris = 0;

        Tabel(float[] lebar, String[] kepala, int[] rata) {
            this.rata = rata;
            t = new PdfPTable(lebar);
            t.setWidthPercentage(100);
            t.setSpacingBefore(4);
            t.setSpacingAfter(4);
            t.setHeaderRows(1);
            t.setSplitLate(false);
            for (int i = 0; i < kepala.length; i++) {
                PdfPCell c = sel(kepala[i].toUpperCase(Locale.ROOT), TH, rata[i], SURFACE);
                c.setBorderColor(LINE);
                t.addCell(c);
            }
        }

        void baris(Font[] font, String... isi) {
            Color latar = baris++ % 2 == 1 ? new Color(0xfa, 0xfb, 0xfa) : null;
            for (int i = 0; i < isi.length; i++) t.addCell(sel(isi[i], font[i], rata[i], latar));
        }

        void tambah(PdfPCell c) {
            t.addCell(c);
        }
    }

    /** Batang proporsi horizontal (0..1) sebagai tabel dua sel berwarna. */
    private static PdfPTable batang(double nilai, Color isi, float tinggi) {
        double v = Math.max(0.002, Math.min(0.998, nilai));
        PdfPTable b = new PdfPTable(new float[]{(float) v, (float) (1 - v)});
        b.setWidthPercentage(100);
        PdfPCell a = new PdfPCell();
        a.setBorder(Rectangle.NO_BORDER);
        a.setBackgroundColor(isi);
        a.setFixedHeight(tinggi);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setBackgroundColor(new Color(0xe6, 0xec, 0xe9));
        c.setFixedHeight(tinggi);
        b.addCell(a);
        b.addCell(c);
        return b;
    }

    private static PdfPCell selIsi(Color latar, float padding) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(padding);
        if (latar != null) c.setBackgroundColor(latar);
        return c;
    }

    // ------------------------------------------------------------ blok gaya surat laporan MCU

    private static final Font S_BODY = f(9.5f, Font.NORMAL, INK);
    private static final Font S_BOLD = f(9.5f, Font.BOLD, INK);
    private static final Font S_SMALL = f(8.5f, Font.NORMAL, MUTED);

    private static Font fTimes(float size, int style, Color c) {
        return FontFactory.getFont(FontFactory.TIMES_ROMAN, BaseFont.CP1252, BaseFont.NOT_EMBEDDED, size, style, c);
    }

    /** Sel teks polos tanpa garis (untuk tata letak surat). */
    private static PdfPCell polos(String s, Font font, int rata) {
        PdfPCell c = new PdfPCell(new Phrase(aman(s), font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(1.4f);
        c.setPaddingBottom(1.6f);
        c.setPaddingLeft(0);
        c.setPaddingRight(4);
        c.setHorizontalAlignment(rata);
        c.setLeading(0, 1.25f);
        return c;
    }

    private static PdfPCell polos(PdfPTable isi) {
        PdfPCell c = new PdfPCell(isi);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(0);
        c.setPaddingBottom(2);
        return c;
    }

    /** Kop surat halaman pertama: logo, nama sistem, keterangan, lalu garis ganda. */
    private PdfPTable kopSurat(PdfWriter w) {
        PdfTemplate logo = w.getDirectContent().createTemplate(42, 42);
        logo.setColorFill(TEAL);
        logo.roundRectangle(0, 0, 42, 42, 9);
        logo.fill();
        logo.setColorFill(Color.WHITE);
        float hx = 21, hy = 20;
        logo.moveTo(hx, hy - 11);
        logo.curveTo(hx - 18, hy + 1, hx - 9, hy + 14, hx, hy + 5.2f);
        logo.curveTo(hx + 9, hy + 14, hx + 18, hy + 1, hx, hy - 11);
        logo.fill();

        PdfPTable kop = new PdfPTable(new float[]{1f, 6.2f, 1f});
        kop.setWidthPercentage(100);
        PdfPCell kiri;
        try {
            kiri = new PdfPCell(org.openpdf.text.Image.getInstance(logo), false);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        kiri.setBorder(Rectangle.NO_BORDER);
        kiri.setVerticalAlignment(Element.ALIGN_MIDDLE);
        kop.addCell(kiri);

        PdfPCell tengah = new PdfPCell();
        tengah.setBorder(Rectangle.NO_BORDER);
        tengah.setPaddingTop(0);
        Paragraph a = p("S I S T E M   P A K A R   K E S E H A T A N   J A N T U N G", f(7.5f, Font.BOLD, TEAL));
        a.setAlignment(Element.ALIGN_CENTER);
        tengah.addElement(a);
        Paragraph nama = p("JANTUNGKU", fTimes(23, Font.BOLD, TEAL_2));
        nama.setAlignment(Element.ALIGN_CENTER);
        nama.setLeading(0, 1.05f);
        tengah.addElement(nama);
        Paragraph b = p("Skrining risiko penyakit jantung dengan metode Certainty Factor  ·  Layanan darurat 119", S_SMALL);
        b.setAlignment(Element.ALIGN_CENTER);
        b.setSpacingBefore(1);
        tengah.addElement(b);
        kop.addCell(tengah);
        PdfPCell kanan = new PdfPCell(new Phrase(" "));
        kanan.setBorder(Rectangle.NO_BORDER);
        kop.addCell(kanan);

        // garis ganda: tebal lalu tipis
        PdfPCell g1 = new PdfPCell();
        g1.setColspan(3);
        g1.setBorder(Rectangle.BOTTOM);
        g1.setBorderWidthBottom(2.2f);
        g1.setBorderColorBottom(INK);
        g1.setFixedHeight(6);
        kop.addCell(g1);
        PdfPCell g2 = new PdfPCell();
        g2.setColspan(3);
        g2.setBorder(Rectangle.BOTTOM);
        g2.setBorderWidthBottom(.6f);
        g2.setBorderColorBottom(INK);
        g2.setFixedHeight(2.5f);
        kop.addCell(g2);
        return kop;
    }

    /** Baris "Label : nilai" ala surat. */
    private static PdfPTable daftarLabel(float[] lebar, String[][] baris) {
        PdfPTable t = new PdfPTable(lebar);
        t.setWidthPercentage(100);
        for (String[] r : baris) {
            t.addCell(polos(r[0], S_BODY, Element.ALIGN_LEFT));
            t.addCell(polos(":", S_BODY, Element.ALIGN_LEFT));
            t.addCell(polos(r[1], S_BOLD, Element.ALIGN_LEFT));
        }
        return t;
    }

    /** Daftar berbutir (•) di dalam sel isi. */
    private static PdfPTable butir(List<String> isi) {
        PdfPTable t = new PdfPTable(new float[]{.32f, 9.68f});
        t.setWidthPercentage(100);
        for (String s : isi) {
            t.addCell(polos("•", S_BOLD, Element.ALIGN_LEFT));
            t.addCell(polos(s, S_BODY, Element.ALIGN_LEFT));
        }
        return t;
    }

    /** Daftar bernomor 1., 2., ... dengan teks berindentasi gantung. */
    private static PdfPTable daftarBernomor(List<String> isi) {
        PdfPTable t = new PdfPTable(new float[]{.45f, 9.55f});
        t.setWidthPercentage(100);
        int n = 1;
        for (String s : isi) {
            t.addCell(polos(n++ + ".", S_BODY, Element.ALIGN_LEFT));
            t.addCell(polos(s, S_BODY, Element.ALIGN_LEFT));
        }
        return t;
    }

    private String tanggalLahirUmur(Diagnosa d) {
        if (d.tanggalLahir() == null) return d.umur() == null ? "-" : d.umur() + " tahun";
        return fmt.tanggalPanjang(d.tanggalLahir()) + "  /  " + (d.umur() == null ? "-" : d.umur() + " tahun");
    }

    private static Paragraph judulLampiran(String teks) {
        Paragraph p = p(teks, f(11, Font.BOLD, INK));
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        p.setKeepTogether(true);
        return p;
    }

    // ------------------------------------------------------------ laporan
    public byte[] buat(HasilLengkap h) {
        Diagnosa d = h.diagnosa();
        HasilHipotesis k = h.kesimpulan();
        String no = nomor(d);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // halaman 1 memakai kop surat di dalam isi (margin atas kecil); halaman berikutnya memakai kop ringkas
        Document doc = new Document(PageSize.A4, 56, 56, 34, 64);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new KopKaki(no, LocalDateTime.now(WIB).format(CETAK)));
        doc.addTitle("Laporan Pemeriksaan Kesehatan " + no + " - " + aman(d.namaPasien()));
        doc.addAuthor("Jantungku");
        doc.addSubject("Medical check up report - skrining risiko penyakit jantung");
        doc.addCreator("Jantungku - sistem pakar Certainty Factor");
        doc.open();
        doc.setMargins(56, 56, 86, 64);   // berlaku mulai halaman 2

        doc.add(kopSurat(writer));

        // ---------- judul surat
        Paragraph judul = new Paragraph();
        judul.add(new Chunk("LAPORAN PEMERIKSAAN KESEHATAN", f(12.5f, Font.BOLD, INK)).setUnderline(.8f, -2.2f));
        judul.setAlignment(Element.ALIGN_CENTER);
        judul.setSpacingBefore(10);
        doc.add(judul);
        Paragraph sub = p("MEDICAL CHECK UP REPORT", f(10, Font.ITALIC, INK2));
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingBefore(1);
        doc.add(sub);

        Paragraph salam = p("Dengan hormat,", S_BODY);
        salam.setSpacingBefore(10);
        doc.add(salam);
        Paragraph pembuka = p("Bersama ini kami sampaikan hasil skrining kesehatan jantung:", S_BODY);
        pembuka.setSpacingBefore(2);
        pembuka.setSpacingAfter(4);
        doc.add(pembuka);

        // ---------- identitas
        List<String[]> ident = new ArrayList<>();
        ident.add(new String[]{"Nama", d.namaPasien()});
        ident.add(new String[]{"Nomor Rekam Medis", no});
        if (d.getJenisKelaminLabel() != null) ident.add(new String[]{"Jenis Kelamin", d.getJenisKelaminLabel()});
        ident.add(new String[]{d.tanggalLahir() == null ? "Umur" : "Tanggal Lahir / Umur", tanggalLahirUmur(d)});
        if (d.alamat() != null) ident.add(new String[]{"Alamat", d.alamat()});
        if (d.telepon() != null) ident.add(new String[]{"No. Telepon", d.telepon()});
        PdfPTable idt = daftarLabel(new float[]{2.45f, .22f, 7.33f}, ident.toArray(new String[0][]));
        PdfPTable idtBungkus = new PdfPTable(new float[]{.45f, 9.55f});
        idtBungkus.setWidthPercentage(100);
        PdfPCell indent = new PdfPCell();
        indent.setBorder(Rectangle.NO_BORDER);
        idtBungkus.addCell(indent);
        idtBungkus.addCell(polos(idt));
        doc.add(idtBungkus);

        Paragraph telah = p("Telah diperiksa melalui " + (d.isKlinis() ? "data pemeriksaan klinis" : "kuesioner gejala")
                + " sistem pakar Jantungku pada tanggal " + fmt.tanggalPanjang(d.createdAt())
                + ", pukul " + d.createdAt().format(DateTimeFormatter.ofPattern("HH.mm")) + " WIB, dengan hasil sebagai berikut:", S_BODY);
        telah.setSpacingBefore(7);
        telah.setSpacingAfter(4);
        telah.setLeading(0, 1.3f);
        telah.setAlignment(Element.ALIGN_JUSTIFIED);
        doc.add(telah);

        // ---------- hasil bernomor (No | Pemeriksaan | : | isi)
        PdfPTable hasil = new PdfPTable(new float[]{.45f, 2.33f, .22f, 7f});
        hasil.setWidthPercentage(100);
        int n = 1;
        List<String> rekomendasiFisik = new ArrayList<>();

        // 1. pemeriksaan fisik
        List<String> fisik = new ArrayList<>();
        if (d.tinggiCm() != null || d.beratKg() != null) {
            fisik.add("Tinggi badan = " + fmt.angka(d.tinggiCm()) + " cm;  berat badan = " + fmt.angka(d.beratKg()) + " kg");
        }
        if (d.getImt() != null) {
            fisik.add("Indeks massa tubuh = " + fmt.angka(d.getImt()) + " kg/m² = " + d.getKategoriImt().toUpperCase(Locale.ROOT)
                    + "  (normal 18,5 - 25,0)");
            if (!d.isImtNormal()) {
                rekomendasiFisik.add(d.getImt().doubleValue() < 18.5
                        ? "Tingkatkan asupan gizi seimbang hingga IMT mencapai 18,5 - 25,0; konsultasikan ke ahli gizi bila perlu."
                        : "Turunkan berat badan bertahap dengan pola makan seimbang dan aktivitas fisik minimal 150 menit per minggu hingga IMT 18,5 - 25,0.");
            }
        }
        if (d.getTekananTeks() != null) fisik.add("Tekanan darah = " + d.getTekananTeks() + " mmHg = " + d.getKategoriTekanan().toUpperCase(Locale.ROOT));
        if (!fisik.isEmpty() || !d.isKlinis()) {
            hasil.addCell(polos(n++ + ".", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(polos("Pemeriksaan Fisik", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(polos(":", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(fisik.isEmpty() ? polos("TIDAK DICATAT", S_BOLD, Element.ALIGN_LEFT) : polos(butir(fisik)));
        }

        // 2. kuesioner gejala / data klinis
        if (d.isKlinis() && h.klinis() != null) {
            List<String> kl = new ArrayList<>();
            for (Map.Entry<Atribut, String> e : h.klinis().tampil().entrySet()) kl.add(e.getKey().getLabel() + " = " + e.getValue());
            hasil.addCell(polos(n++ + ".", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(polos("Data Klinis", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(polos(":", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(polos(butir(kl)));
            hasil.addCell(polos(n++ + ".", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(polos("Aturan Pakar", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(polos(":", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(h.terpicu().isEmpty() ? polos("TIDAK ADA ATURAN YANG TERPENUHI", S_BOLD, Element.ALIGN_LEFT)
                    : polos(butir(h.terpicu().stream().map(t -> t.rule().kode() + " terpenuhi -> " + t.rule().penyakitKode()
                    + " (CF aturan " + fmt.cf(t.rule().cf()) + ")").toList())));
        } else {
            List<String> gj = h.jawaban().stream()
                    .map(j -> j.gejalaKode() + "  " + j.gejalaNama() + " = " + j.getLabel().toUpperCase(Locale.ROOT)).toList();
            hasil.addCell(polos(n++ + ".", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(polos("Kuesioner Gejala", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(polos(":", S_BODY, Element.ALIGN_LEFT));
            hasil.addCell(gj.isEmpty() ? polos("SEMUA DIJAWAB TIDAK TAHU", S_BOLD, Element.ALIGN_LEFT) : polos(butir(gj)));
        }

        // 3. analisis sistem pakar
        List<String> an = h.hasil().stream()
                .map(x -> x.kode() + "  " + x.nama() + " = " + fmt.persen(x.cf()) + " (" + x.getInterpretasi().toLowerCase() + ")").toList();
        hasil.addCell(polos(n++ + ".", S_BODY, Element.ALIGN_LEFT));
        hasil.addCell(polos("Analisis Sistem Pakar", S_BODY, Element.ALIGN_LEFT));
        hasil.addCell(polos(":", S_BODY, Element.ALIGN_LEFT));
        hasil.addCell(polos(butir(an)));

        // 4. catatan khusus (diisi tangan oleh dokter)
        hasil.addCell(polos(n + ".", S_BODY, Element.ALIGN_LEFT));
        hasil.addCell(polos("Catatan Khusus", S_BODY, Element.ALIGN_LEFT));
        hasil.addCell(polos(":", S_BODY, Element.ALIGN_LEFT));
        PdfPTable garis = new PdfPTable(1);
        garis.setWidthPercentage(100);
        for (int i = 0; i < 1; i++) {
            PdfPCell g = new PdfPCell(new Phrase(" ", S_BODY));
            g.setBorder(Rectangle.BOTTOM);
            g.setBorderColor(LINE);
            g.setBorderWidthBottom(.8f);
            g.setFixedHeight(15);
            garis.addCell(g);
        }
        hasil.addCell(polos(garis));
        doc.add(hasil);

        // ---------- kesimpulan & rekomendasi
        List<String> rekomendasi = new ArrayList<>();
        if (k != null && k.saran() != null && !k.saran().isBlank()) rekomendasi.add(k.saran());
        if (k != null && k.isRisiko()) {
            rekomendasi.add("Bila nyeri dada hebat, sesak napas, atau keringat dingin, segera ke IGD atau hubungi 119.");
        } else if (k != null) {
            rekomendasi.add("Pertahankan pola hidup sehat: aktif bergerak, makan seimbang, tidak merokok, dan tidur cukup.");
        } else {
            rekomendasi.add("Bila merasa tidak sehat atau ada keluhan di dada, tetap periksakan diri ke dokter.");
        }
        rekomendasi.addAll(rekomendasiFisik);
        if (k == null || !k.isRisiko()) rekomendasi.add("Lakukan pemeriksaan kesehatan berkala: tekanan darah, gula darah, dan kolesterol.");

        String kesimpulan = k == null ? "BELUM DAPAT DISIMPULKAN"
                : k.nama().toUpperCase(Locale.ROOT) + "  (KEYAKINAN " + fmt.persen(k.cf()) + " - " + k.getInterpretasi().toUpperCase(Locale.ROOT) + ")";
        PdfPTable kr = new PdfPTable(new float[]{2.78f, .22f, 7f});
        kr.setWidthPercentage(100);
        kr.setSpacingBefore(8);
        kr.addCell(polos("KESIMPULAN", S_BOLD, Element.ALIGN_LEFT));
        kr.addCell(polos(":", S_BOLD, Element.ALIGN_LEFT));
        kr.addCell(polos(kesimpulan, f(10, Font.BOLD, k == null ? INK : (k.isRisiko() ? RISK : OK)), Element.ALIGN_LEFT));
        PdfPCell jarak = new PdfPCell(new Phrase(" ", f(4, Font.NORMAL, INK)));
        jarak.setColspan(3);
        jarak.setBorder(Rectangle.NO_BORDER);
        jarak.setFixedHeight(3);
        kr.addCell(jarak);
        kr.addCell(polos("REKOMENDASI", S_BOLD, Element.ALIGN_LEFT));
        kr.addCell(polos(":", S_BOLD, Element.ALIGN_LEFT));
        kr.addCell(polos(daftarBernomor(rekomendasi)));
        doc.add(kr);

        Paragraph penutup = p("Demikian laporan ini dibuat berdasarkan " + (d.isKlinis() ? "data klinis" : "jawaban kuesioner")
                + " yang diisikan. Bawa laporan ini saat berkonsultasi ke dokter; rincian jawaban dan perhitungan "
                + "Certainty Factor terlampir.", S_BODY);
        penutup.setSpacingBefore(8);
        penutup.setLeading(0, 1.3f);
        penutup.setAlignment(Element.ALIGN_JUSTIFIED);
        doc.add(penutup);

        // ---------- pengingat medis (kiri) & tanda tangan pakar (kanan), seperti penutup surat MCU
        PdfPTable ttd = new PdfPTable(new float[]{1.2f, .08f, 1f});
        ttd.setWidthPercentage(100);
        ttd.setSpacingBefore(12);
        ttd.setKeepTogether(true);
        PdfPCell sl = new PdfPCell();
        sl.setBorderColor(TEAL);
        sl.setBorderWidth(1f);
        sl.setBackgroundColor(TEAL_SOFT);
        sl.setPadding(9);
        sl.setPaddingTop(6);
        sl.setVerticalAlignment(Element.ALIGN_MIDDLE);
        sl.addElement(p("HASIL INI BUKAN DIAGNOSIS DOKTER", f(11, Font.BOLD, TEAL_2)));
        Paragraph ip = p("Jantungku memperkirakan risiko berdasarkan data yang dimasukkan. Keputusan medis tetap harus diambil "
                + "oleh dokter atau tenaga kesehatan. Dalam keadaan darurat, segera hubungi 119.", f(8.8f, Font.NORMAL, INK));
        ip.setSpacingBefore(2);
        ip.setLeading(0, 1.3f);
        sl.addElement(ip);
        ttd.addCell(sl);
        PdfPCell sela = new PdfPCell();
        sela.setBorder(Rectangle.NO_BORDER);
        ttd.addCell(sela);
        PdfPCell tc = new PdfPCell();
        tc.setBorder(Rectangle.NO_BORDER);
        tc.setPaddingTop(0);
        Paragraph tempat = p("Jantungku, " + fmt.tanggalPanjang(d.createdAt()), S_BODY);
        tempat.setAlignment(Element.ALIGN_CENTER);
        tc.addElement(tempat);
        Paragraph peran = p("Pakar Pemeriksa,", S_BODY);
        peran.setAlignment(Element.ALIGN_CENTER);
        peran.setSpacingBefore(2);
        tc.addElement(peran);
        Paragraph ruang = new Paragraph(" ", S_BODY);
        ruang.setSpacingBefore(18);
        tc.addElement(ruang);
        Paragraph nm = p("( " + namaPakar() + " )", S_BOLD);
        nm.setAlignment(Element.ALIGN_CENTER);
        tc.addElement(nm);
        Paragraph ket = p("Pakar sistem Jantungku - bukan dokter pemeriksa", S_SMALL);
        ket.setAlignment(Element.ALIGN_CENTER);
        ket.setSpacingBefore(1);
        tc.addElement(ket);
        ttd.addCell(tc);
        doc.add(ttd);

        // ---------- lampiran 1: rincian jawaban / data
        doc.newPage();
        doc.add(p("LAMPIRAN", LABEL));
        Paragraph lj = p("Rincian pemeriksaan " + no, f(14, Font.BOLD, INK));
        lj.setSpacingBefore(3);
        doc.add(lj);
        if (d.isKlinis() && h.klinis() != null) {
            doc.add(judulLampiran("Lampiran 1. Data pemeriksaan klinis & aturan pakar"));
            Tabel kt = new Tabel(new float[]{2.6f, 1.4f, 2.6f, 1.4f}, new String[]{"Parameter", "Hasil", "Parameter", "Hasil"},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
            var es = new ArrayList<>(h.klinis().tampil().entrySet());
            for (int i = 0; i < es.size(); i += 2) {
                Map.Entry<Atribut, String> a = es.get(i);
                Map.Entry<Atribut, String> b = i + 1 < es.size() ? es.get(i + 1) : null;
                kt.baris(new Font[]{BODY2, BOLD, BODY2, BOLD}, a.getKey().getLabel(), a.getValue(),
                        b == null ? "" : b.getKey().getLabel(), b == null ? "" : b.getValue());
            }
            kt.t.setKeepTogether(true);
            doc.add(kt.t);
            for (var t : h.terpicu()) {
                Tabel st = new Tabel(new float[]{4.4f, 1.6f, 1.2f},
                        new String[]{t.rule().kode() + "  ->  " + t.rule().penyakitKode() + "   (CF aturan " + fmt.cf(t.rule().cf()) + ")", "Nilai pasien", "Terpenuhi"},
                        new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
                for (var c : t.cek()) {
                    st.baris(new Font[]{MONO, BODY, f(9, Font.BOLD, c.cocok() ? OK : RISK)},
                            c.syarat().teks(), c.nilaiPasien(), c.cocok() ? "Ya" : "Tidak");
                }
                st.t.setKeepTogether(true);
                st.t.setSpacingBefore(8);
                doc.add(st.t);
            }
        } else {
            doc.add(judulLampiran("Lampiran 1. Jawaban kuesioner gejala"));
            if (h.jawaban().isEmpty()) {
                doc.add(p("Semua gejala dijawab \"Tidak tahu\" sehingga tidak ikut dihitung.", BODY2));
            } else {
                Tabel gt = new Tabel(new float[]{.45f, .75f, 4.3f, 1.9f, .8f}, new String[]{"No", "Kode", "Gejala", "Jawaban", "CF user"},
                        new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                int i = 1;
                for (var j : h.jawaban()) {
                    gt.baris(new Font[]{SMALL_B, SMALL_B, BODY, BODY2, BOLD}, i++ + "", j.gejalaKode(), j.gejalaNama(), j.getLabel(), fmt.cf(j.cfUser()));
                }
                doc.add(gt.t);
            }
            Paragraph sk = p("Skala jawaban (interpretasi Certainty Theory): Pasti tidak -1,0 · Hampir pasti tidak -0,8 · "
                    + "Kemungkinan besar tidak -0,6 · Mungkin tidak -0,4 · Tidak tahu 0 · Mungkin 0,4 · Kemungkinan besar 0,6 · "
                    + "Hampir pasti 0,8 · Pasti 1,0. Jawaban \"Tidak tahu\" tidak ikut dihitung.", SMALL);
            sk.setSpacingBefore(4);
            sk.setLeading(0, 1.35f);
            doc.add(sk);
        }

        // ---------- lampiran 2: perhitungan Certainty Factor
        boolean adaLangkah = h.hasil().stream().anyMatch(x -> !x.langkah().isEmpty());
        if (adaLangkah) {
            doc.add(judulLampiran("Lampiran 2. Perhitungan Certainty Factor"));
            Paragraph rumus = p(d.isKlinis()
                    ? "CF(H,E) = CF data x CF aturan (data pengukuran dianggap pasti, CF data = 1). Nilai digabung berurutan: "
                    + "CF gabungan = CF lama + CF baru x (1 - CF lama)."
                    : "CF(H,E) = CF user x CF pakar. Nilai digabung berurutan: keduanya positif CF1 + CF2 x (1 - CF1); "
                    + "keduanya negatif CF1 + CF2 x (1 + CF1); berlawanan tanda (CF1 + CF2) / (1 - min(|CF1|, |CF2|)).", BODY2);
            rumus.setLeading(0, 1.35f);
            doc.add(rumus);

            String a = d.isKlinis() ? "CF data" : "CF user";
            String b = d.isKlinis() ? "CF aturan" : "CF pakar";
            for (HasilHipotesis x : h.hasil()) {
                if (x.langkah().isEmpty()) continue;
                doc.add(judulBagian(x.kode(), x.nama()));
                Tabel lt = new Tabel(new float[]{.75f, 1f, 1.1f, 1.1f, 4.3f, 1.35f},
                        new String[]{"Kode", a, b, "CF(H,E)", "Penggabungan", "CF kumulatif"},
                        new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                for (Langkah l : x.langkah()) {
                    lt.baris(new Font[]{SMALL_B, BODY, BODY, BOLD, f(8.5f, Font.NORMAL, INK2), BOLD},
                            l.gejalaKode(), fmt.cf(l.cfUser()), fmt.cf(l.cfPakar()), fmt.cf(l.cfHE()),
                            l.getRumus().replace("×", "x").replace("−", "-"), fmt.cf(l.cfHasil()));
                }
                doc.add(lt.t);
                Paragraph tot = new Paragraph();
                tot.add(new Chunk("Hasil akhir  ", SMALL));
                tot.add(new Chunk("CF = " + fmt.cf(x.cf()) + "  =  " + fmt.persen(x.cf()) + "  (" + x.getInterpretasi() + ")", BOLD));
                tot.setAlignment(Element.ALIGN_RIGHT);
                tot.setSpacingBefore(2);
                doc.add(tot);
            }
        }

        Paragraph tutup = p("Dokumen ini dibuat otomatis oleh Jantungku dan hanya untuk keperluan skrining awal.", SMALL);
        tutup.setSpacingBefore(18);
        tutup.setAlignment(Element.ALIGN_CENTER);
        doc.add(tutup);

        doc.close();
        return out.toByteArray();
    }

    private String namaPakar() {
        return penggunaRepo.namaPakarUtama().orElse("Pakar Jantungku");
    }

    // dipakai untuk nama berkas unduhan
    public static String namaBerkas(Diagnosa d) {
        String pasien = d.namaPasien() == null ? "pasien" : d.namaPasien().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return "Jantungku_" + nomor(d) + "_" + (pasien.isEmpty() ? "pasien" : pasien) + ".pdf";
    }
}
