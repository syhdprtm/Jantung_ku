package id.ac.unsera.sistempakar.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Penanganan error terpusat untuk semua controller. Setiap error ditampilkan dengan halaman
 * yang sama (template "pesan"); error tak terduga diberi kode referensi yang juga dicatat di log
 * sehingga mudah ditelusuri tanpa menampilkan detail teknis kepada pengguna.
 * <p>
 * Model dari {@link GlobalModel} tidak otomatis tersedia untuk handler error, jadi untuk pengguna
 * yang login atribut menu diisi di sini agar halaman error tetap menampilkan navigasi aplikasi.
 */
@ControllerAdvice
public class PenanganError {

    private static final Logger log = LoggerFactory.getLogger(PenanganError.class);

    private final GlobalModel global;

    public PenanganError(GlobalModel global) {
        this.global = global;
    }

    private ModelAndView halaman(HttpServletRequest req, HttpStatus status, String judul, String pesan,
                                 String referensi, boolean denganMenu) {
        Map<String, Object> m = new HashMap<>();
        m.put("judul", judul);
        m.put("pesan", pesan);
        m.put("kodeStatus", status.value());
        m.put("referensi", referensi == null ? "" : referensi);
        m.put("path", req.getRequestURI());
        m.put("v", global.versiAset());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean login = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        if (login && denganMenu) {
            try {
                m.put("me", global.me(auth::getName));
                m.put("menu", global.menu(auth));
                m.put("tingkat", global.tingkat());
            } catch (RuntimeException e) {
                log.debug("Menu untuk halaman error tidak dapat dimuat: {}", e.getMessage());
            }
        }
        ModelAndView mv = new ModelAndView("pesan", m);
        mv.setStatus(status);
        return mv;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView tidakDitemukan(HttpServletRequest req) {
        return halaman(req, HttpStatus.NOT_FOUND, "Halaman tidak ditemukan",
                "Alamat yang Anda buka tidak ada. Periksa kembali tautannya atau kembali ke beranda.", null, true);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ModelAndView permintaanTidakValid(Exception e, HttpServletRequest req) {
        log.debug("Permintaan tidak valid {}: {}", req.getRequestURI(), e.getMessage());
        return halaman(req, HttpStatus.BAD_REQUEST, "Permintaan tidak valid",
                "Data pada alamat atau formulir tidak sesuai. Kembali ke halaman sebelumnya dan coba lagi.", null, true);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ModelAndView metodeTidakDidukung(HttpServletRequest req) {
        return halaman(req, HttpStatus.METHOD_NOT_ALLOWED, "Aksi tidak didukung",
                "Halaman ini tidak dapat dibuka dengan cara tersebut. Gunakan tombol yang tersedia di aplikasi.", null, true);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView fileTerlaluBesar(HttpServletRequest req) {
        return halaman(req, HttpStatus.PAYLOAD_TOO_LARGE, "File terlalu besar",
                "Ukuran file melebihi batas 10 MB. Gunakan file CSV yang lebih kecil.", null, true);
    }

    @ExceptionHandler(DataAccessException.class)
    public ModelAndView database(DataAccessException e, HttpServletRequest req) {
        String ref = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("[{}] Kesalahan database pada {}", ref, req.getRequestURI(), e);
        // Tanpa menu: memuat data pengguna justru butuh database yang sedang bermasalah.
        return halaman(req, HttpStatus.SERVICE_UNAVAILABLE, "Layanan sedang tidak tersedia",
                "Aplikasi sedang tidak dapat mengakses data. Coba lagi beberapa saat lagi; bila masalah berlanjut, "
                        + "hubungi administrator dengan menyebutkan kode referensi di bawah.", ref, false);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView umum(Exception e, HttpServletRequest req) throws Exception {
        // Biarkan Spring Security menangani akses ditolak / belum login seperti biasa.
        if (e instanceof org.springframework.security.access.AccessDeniedException
                || e instanceof org.springframework.security.core.AuthenticationException) {
            throw e;
        }
        String ref = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("[{}] Kesalahan tak terduga pada {}", ref, req.getRequestURI(), e);
        return halaman(req, HttpStatus.INTERNAL_SERVER_ERROR, "Terjadi kesalahan",
                "Maaf, terjadi kesalahan saat memproses permintaan Anda. Sebutkan kode referensi di bawah bila menghubungi administrator.", ref, true);
    }
}
