package id.ac.unsera.sistempakar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sistem Pakar Diagnosa Penyakit Jantung dengan Metode Certainty Factor.
 * Jalankan lalu buka http://localhost:8090 (desktop) atau http://IP-LAPTOP:8090 (HP, satu jaringan WiFi).
 */
@SpringBootApplication
public class SistemPakarApplication {

    public static void main(String[] args) {
        SpringApplication.run(SistemPakarApplication.class, args);
    }
}
