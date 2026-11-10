package id.ac.unsera.sistempakar.service;

import id.ac.unsera.sistempakar.model.Atribut;
import id.ac.unsera.sistempakar.model.Syarat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Memeriksa apakah syarat-syarat sebuah rule mungkin terpenuhi bersamaan.
 * Rule yang mustahil (mis. {@code Age <= 60 AND Age > 63}) tidak akan pernah terpicu.
 */
public final class RuleValidator {

    /** Atribut bernilai bilangan bulat pada dataset. */
    private static final Set<Atribut> BULAT = EnumSet.of(Atribut.AGE, Atribut.RESTING_BP, Atribut.CHOLESTEROL,
            Atribut.FASTING_BS, Atribut.MAX_HR);

    private RuleValidator() {
    }

    /** Batas rentang [lo, hi] dengan penanda inklusif. */
    private static final class Rentang {
        BigDecimal lo;
        boolean loInc = true;
        BigDecimal hi;
        boolean hiInc = true;
        final List<String> syarat = new ArrayList<>();

        void batasBawah(BigDecimal v, boolean inc) {
            if (lo == null || v.compareTo(lo) > 0 || (v.compareTo(lo) == 0 && !inc)) {
                lo = v;
                loInc = inc;
            }
        }

        void batasAtas(BigDecimal v, boolean inc) {
            if (hi == null || v.compareTo(hi) < 0 || (v.compareTo(hi) == 0 && !inc)) {
                hi = v;
                hiInc = inc;
            }
        }

        boolean mungkin(boolean bulat) {
            if (lo == null || hi == null) return true;
            if (bulat) {
                BigDecimal l = loInc ? lo.setScale(0, java.math.RoundingMode.CEILING)
                        : lo.setScale(0, java.math.RoundingMode.FLOOR).add(BigDecimal.ONE);
                BigDecimal h = hiInc ? hi.setScale(0, java.math.RoundingMode.FLOOR)
                        : hi.setScale(0, java.math.RoundingMode.CEILING).subtract(BigDecimal.ONE);
                return l.compareTo(h) <= 0;
            }
            int c = lo.compareTo(hi);
            return c < 0 || (c == 0 && loInc && hiInc);
        }
    }

    /** Dua rule berbeda kesimpulan yang dapat terpicu oleh pasien yang sama. */
    public record TumpangTindih(String ruleA, String ruleB, String hasilA, String hasilB) {
    }

    public static List<TumpangTindih> tumpangTindih(List<RuleEngine.RuleAktif> rules) {
        List<TumpangTindih> hasil = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                var a = rules.get(i);
                var b = rules.get(j);
                if (a.penyakitKode().equals(b.penyakitKode()) || a.syarat().isEmpty() || b.syarat().isEmpty()) continue;
                if (!periksa(a.syarat()).isEmpty() || !periksa(b.syarat()).isEmpty()) continue;
                List<Syarat> gabung = new ArrayList<>(a.syarat());
                gabung.addAll(b.syarat());
                if (periksa(gabung).isEmpty()) {
                    hasil.add(new TumpangTindih(a.kode(), b.kode(), a.penyakitNama(), b.penyakitNama()));
                }
            }
        }
        return hasil;
    }

    /** @return daftar masalah; kosong berarti rule valid. */
    public static List<String> periksa(List<Syarat> syarat) {
        List<String> masalah = new ArrayList<>();
        Map<Atribut, Rentang> rentang = new EnumMap<>(Atribut.class);
        Map<Atribut, Set<String>> kategori = new EnumMap<>(Atribut.class);

        for (Syarat s : syarat) {
            Atribut a = s.atribut();
            if (a.isKategori()) {
                kategori.computeIfAbsent(a, k -> new HashSet<>()).add(s.nilai());
                continue;
            }
            Rentang r = rentang.computeIfAbsent(a, k -> {
                Rentang x = new Rentang();
                if (a.getMin() != null) x.batasBawah(a.getMin(), true);
                if (a.getMax() != null) x.batasAtas(a.getMax(), true);
                return x;
            });
            r.syarat.add(s.teks());
            BigDecimal v = s.angka();
            switch (s.operator()) {
                case ">" -> r.batasBawah(v, false);
                case ">=" -> r.batasBawah(v, true);
                case "<" -> r.batasAtas(v, false);
                case "<=" -> r.batasAtas(v, true);
                default -> {
                    r.batasBawah(v, true);
                    r.batasAtas(v, true);
                }
            }
        }

        kategori.forEach((a, nilai) -> {
            if (nilai.size() > 1) masalah.add(a.getKolom() + " tidak mungkin bernilai " + String.join(" dan ", nilai) + " sekaligus.");
        });
        rentang.forEach((a, r) -> {
            if (!r.mungkin(BULAT.contains(a))) {
                String domain = a.getMin() == null ? "" : " (rentang data " + a.getMin().toPlainString() + "–" + a.getMax().toPlainString() + ")";
                masalah.add("Tidak ada nilai " + a.getKolom() + " yang memenuhi " + String.join(" dan ", r.syarat) + domain + ".");
            }
        });
        return masalah;
    }
}
