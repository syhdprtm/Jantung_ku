package id.ac.unsera.sistempakar.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Respons file unduhan (PDF / Excel). */
final class Unduhan {

    static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private Unduhan() {
    }

    static ResponseEntity<byte[]> file(byte[] isi, String nama, MediaType tipe) {
        return ResponseEntity.ok()
                .contentType(tipe)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nama, StandardCharsets.UTF_8).build().toString())
                .body(isi);
    }

    static String tanggal() {
        return LocalDate.now().toString();
    }
}
