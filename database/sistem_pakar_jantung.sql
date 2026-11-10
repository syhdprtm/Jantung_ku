-- Sistem Pakar Diagnosa Penyakit Jantung (Certainty Factor)
-- Impor lewat HeidiSQL / phpMyAdmin / mysql CLI. Opsional: aplikasi juga membuat tabel & data ini otomatis saat pertama dijalankan.
-- Akun admin awal: lihat README.md


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `sistem_pakar_jantung` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `sistem_pakar_jantung`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `bobot` (
  `gejala_id` bigint NOT NULL,
  `penyakit_id` bigint NOT NULL,
  `cf_pakar` decimal(4,3) NOT NULL,
  PRIMARY KEY (`gejala_id`,`penyakit_id`),
  KEY `fk_bobot_penyakit` (`penyakit_id`),
  CONSTRAINT `fk_bobot_gejala` FOREIGN KEY (`gejala_id`) REFERENCES `gejala` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_bobot_penyakit` FOREIGN KEY (`penyakit_id`) REFERENCES `penyakit` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_bobot_cf` CHECK (((`cf_pakar` > 0) and (`cf_pakar` <= 1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `diagnosa` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `nama_pasien` varchar(100) NOT NULL,
  `umur` int DEFAULT NULL,
  `hasil_kode` varchar(15) DEFAULT NULL,
  `hasil_nama` varchar(150) DEFAULT NULL,
  `hasil_kategori` varchar(10) DEFAULT NULL,
  `hasil_cf` decimal(10,8) NOT NULL DEFAULT '0.00000000',
  `saran` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_diagnosa_user` (`user_id`),
  CONSTRAINT `fk_diagnosa_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `diagnosa_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `diagnosa_id` bigint NOT NULL,
  `urutan` int NOT NULL,
  `penyakit_kode` varchar(15) NOT NULL,
  `gejala_kode` varchar(15) NOT NULL,
  `gejala_nama` varchar(150) NOT NULL,
  `cf_user` decimal(4,3) NOT NULL,
  `cf_pakar` decimal(4,3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_detail_diagnosa` (`diagnosa_id`),
  CONSTRAINT `fk_detail_diagnosa` FOREIGN KEY (`diagnosa_id`) REFERENCES `diagnosa` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `diagnosa_hasil` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `diagnosa_id` bigint NOT NULL,
  `urutan` int NOT NULL,
  `penyakit_kode` varchar(15) NOT NULL,
  `penyakit_nama` varchar(150) NOT NULL,
  `kategori` varchar(10) NOT NULL,
  `cf` decimal(10,8) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_hasil_diagnosa` (`diagnosa_id`),
  CONSTRAINT `fk_hasil_diagnosa` FOREIGN KEY (`diagnosa_id`) REFERENCES `diagnosa` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `diagnosa_jawaban` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `diagnosa_id` bigint NOT NULL,
  `urutan` int NOT NULL,
  `gejala_kode` varchar(15) NOT NULL,
  `gejala_nama` varchar(150) NOT NULL,
  `cf_user` decimal(4,3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_jawaban_diagnosa` (`diagnosa_id`),
  CONSTRAINT `fk_jawaban_diagnosa` FOREIGN KEY (`diagnosa_id`) REFERENCES `diagnosa` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `gejala` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kode` varchar(15) NOT NULL,
  `nama` varchar(150) NOT NULL,
  `keterangan` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `kode` (`kode`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `penyakit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kode` varchar(15) NOT NULL,
  `nama` varchar(150) NOT NULL,
  `kategori` enum('RISIKO','NORMAL') NOT NULL DEFAULT 'RISIKO',
  `keterangan` text,
  `saran` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `kode` (`kode`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `rules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kode` varchar(15) NOT NULL,
  `kondisi` text NOT NULL,
  `penyakit_id` bigint DEFAULT NULL,
  `cf` decimal(4,3) NOT NULL,
  `keterangan` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `kode` (`kode`),
  KEY `fk_rules_penyakit` (`penyakit_id`),
  CONSTRAINT `fk_rules_penyakit` FOREIGN KEY (`penyakit_id`) REFERENCES `penyakit` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_rules_cf` CHECK (((`cf` >= 0) and (`cf` <= 1)))
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nama` varchar(100) NOT NULL,
  `username` varchar(30) NOT NULL,
  `password` varchar(100) NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL DEFAULT 'USER',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


USE `sistem_pakar_jantung`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` (`id`, `nama`, `username`, `password`, `role`, `created_at`) VALUES (1,'Administrator','admin','$2a$10$5yYqTqF5i5aQCPqdjzQteOKGnK7c/2g9P8dIH0p2z8aIb0gxsfwuK','ADMIN','2026-11-04 21:25:08');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `penyakit` WRITE;
/*!40000 ALTER TABLE `penyakit` DISABLE KEYS */;
INSERT INTO `penyakit` (`id`, `kode`, `nama`, `kategori`, `keterangan`, `saran`) VALUES (1,'P01','Terdiagnosa Penyakit Jantung','RISIKO','Parameter klinis yang dimasukkan mengarah pada adanya kelainan atau penyakit jantung.','Segera konsultasikan ke dokter spesialis jantung (Sp.JP) untuk pemeriksaan lanjutan seperti EKG, echocardiography, atau treadmill test. Kurangi garam dan lemak jenuh, berhenti merokok, serta kontrol tekanan darah, kolesterol, dan gula darah secara rutin.'),(2,'P02','Tidak Terdiagnosa (Normal)','NORMAL','Parameter klinis yang dimasukkan menunjukkan kondisi jantung yang cenderung normal.','Pertahankan pola hidup sehat: olahraga teratur minimal 150 menit per minggu, makan bergizi seimbang, tidur cukup, kelola stres, dan lakukan medical check-up berkala.');
/*!40000 ALTER TABLE `penyakit` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `gejala` WRITE;
/*!40000 ALTER TABLE `gejala` DISABLE KEYS */;
INSERT INTO `gejala` (`id`, `kode`, `nama`, `keterangan`) VALUES (1,'D01','Resting BP tinggi (> 120 mmHg)','Hasil tekanan darah saat pasien dalam kondisi beristirahat.'),(2,'D02','Cholesterol tinggi (> 200 mg/dl)','Sejenis lemak (lipid) yang diproduksi oleh hati dan dibutuhkan tubuh untuk membangun sel, membentuk hormon, serta memproduksi vitamin.'),(3,'D03','Heart Rate Deficit â‰¥ 40','Seberapa besar kekurangan denyut jantung pasien sehingga tidak mencapai detak jantung maksimum yang normal.'),(4,'D04','Exercise Angina (Ya)','Nyeri dada yang disebabkan atau dipicu pada saat berolahraga.'),(5,'D05','Fasting BS tinggi','Kadar gula darah puasa (tidak makan 8 jam atau lebih) berada di atas normal.'),(6,'D06','ST Slope Flat','Grafik rekam jantung (EKG) saat berolahraga dibandingkan saat istirahat bernilai datar.'),(7,'D07','Max HR rendah (< 140 bpm)','Detak jantung pada kondisi maksimal berada di bawah 140 bpm.'),(8,'D08','Resting ECG abnormal (ST)','Rekam jantung saat istirahat menunjukkan kelainan gelombang ST-T, tanda gangguan aliran darah ke otot jantung.'),(9,'D09','Resting BP normal (< 120 mmHg)','Tekanan darah saat istirahat berada pada rentang normal.'),(10,'D10','Cholesterol normal (< 200 mg/dl)','Kadar kolesterol berada pada rentang normal.'),(11,'D11','Resting ECG normal','Rekam jantung saat istirahat tidak menunjukkan kelainan.'),(12,'D12','Fasting BS normal','Kadar gula darah puasa berada pada rentang normal.'),(13,'D13','ST Slope Up','Segmen ST pada EKG saat berolahraga menanjak, umumnya menandakan respons jantung yang sehat.');
/*!40000 ALTER TABLE `gejala` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `bobot` WRITE;
/*!40000 ALTER TABLE `bobot` DISABLE KEYS */;
INSERT INTO `bobot` (`gejala_id`, `penyakit_id`, `cf_pakar`) VALUES (1,1,0.400),(2,1,0.400),(3,1,0.800),(4,1,0.800),(5,1,0.800),(6,1,0.800),(7,1,0.800),(8,1,0.600),(9,2,0.600),(10,2,0.600),(11,2,0.600),(12,2,0.600),(13,2,1.000);
/*!40000 ALTER TABLE `bobot` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `rules` WRITE;
/*!40000 ALTER TABLE `rules` DISABLE KEYS */;
INSERT INTO `rules` (`id`, `kode`, `kondisi`, `penyakit_id`, `cf`, `keterangan`) VALUES (1,'R1','ST Slope Down AND Max HR > 185',2,0.800,'Detak jantung maksimal (Max HR) yang sangat tinggi (>185) mengindikasikan fungsi jantung masih baik.'),(2,'R2','ST Slope Down AND Max HR <= 186 AND Exercise Angina N AND Resting BP > 117 AND Age > 67',1,0.800,'Usia lansia (>67) dengan tekanan darah di atas normal memperkuat risiko kelainan.'),(3,'R3','ST Slope Down AND Max HR <= 186 AND Exercise Angina N AND Resting BP > 117 AND Age <= 60 AND Age > 63',2,0.600,'Kondisi tidak terklasifikasi sebagai risiko tinggi pada rentang usia ini.'),(4,'R4','ST Slope Down AND Max HR <= 186 AND Exercise Angina N AND Resting BP > 117 AND Age <= 60 AND Age <= 63',1,0.800,'Pasien usia <= 60 tahun dengan tensi tinggi (>117) menunjukkan potensi kelainan.'),(5,'R5','ST Slope Down AND Max HR <= 186 AND Exercise Angina N AND Resting BP > 117 AND Age > 67 AND Age <= 60',2,0.600,'Kondisi paradoksikal/anomali pada cabang pohon keputusan, diasumsikan normal.'),(6,'R6','ST Slope Down AND Max HR <= 186 AND Exercise Angina N AND Resting BP <= 117',1,0.800,'Tekanan darah rendah/normal (<=117) dengan ST Slope Down tetap berisiko.'),(7,'R7','ST Slope Down AND Max HR <= 186 AND Exercise Angina Y',1,0.900,'Adanya nyeri dada saat olahraga (Exercise Angina Y) adalah indikasi kuat kelainan jantung.'),(8,'R8','ST Slope Flat AND Max HR > 183',2,0.800,'Detak jantung maksimal tinggi (>183) pada ST Slope Flat tergolong normal.'),(9,'R9','ST Slope Flat AND Max HR <= 183 AND Sex F AND Fasting BS 1',1,0.800,'Wanita dengan Gula Darah Puasa tinggi (BS = 1) rentan terhadap komplikasi kardiovaskular.'),(10,'R10','ST Slope Flat AND Max HR <= 183 AND Sex F AND Fasting BS <= 1 AND Oldpeak > 2.3',1,0.900,'Depresi ST saat olahraga (Oldpeak) sangat tinggi (>2.3) adalah bukti signifikan kelainan jantung.'),(11,'R11','ST Slope Flat AND Max HR <= 183 AND Sex F AND Fasting BS <= 1 AND Oldpeak <= 2.3 AND Age > 39 AND Exercise Angina N AND Resting BP > 148',1,0.800,'Hipertensi signifikan (>148) pada pasien wanita dewasa memperkuat diagnosis penyakit jantung.'),(12,'R12','ST Slope Flat AND Max HR <= 183 AND Sex F AND Fasting BS <= 1 AND Oldpeak <= 2.3 AND Age > 39 AND Exercise Angina N AND Resting BP <= 148',2,0.800,'Tekanan darah terkontrol pada wanita tanpa angina meniadakan indikasi kelainan.'),(13,'R13','ST Slope Flat AND Max HR <= 183 AND Sex F AND Fasting BS <= 1 AND Oldpeak <= 2.3 AND Age > 39 AND Exercise Angina Y AND Age < 47',1,0.900,'Angina akibat aktivitas pada wanita usia < 47 tahun merupakan gejala penyakit jantung koroner dini.'),(14,'R14','ST Slope Flat AND Max HR <= 183 AND Sex F AND Fasting BS <= 1 AND Oldpeak <= 2.3 AND Age > 39 AND Exercise Angina Y AND Age <= 47',2,0.800,'Cabang anomali pada usia di bawah 47 tahun, diprediksi tidak memiliki kelainan mayor.'),(15,'R15','ST Slope Flat AND Max HR <= 183 AND Sex F AND Fasting BS <= 1 AND Oldpeak <= 2.3 AND Age <= 39',1,0.800,'Usia sangat muda (<= 39) dengan ST Flat memiliki kerentanan kardiovaskular bawaan atau spesifik.'),(16,'R16','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR > 177 AND Age > 41',2,0.800,'Pria di atas 41 tahun dengan detak jantung mendekati batas atas (177-183) tergolong adaptif dan normal.'),(17,'R17','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR > 177 AND Age <= 41',1,0.800,'Respons detak jantung tinggi pada pria usia <= 41 tahun di kondisi ST Flat memicu indikasi anomali.'),(18,'R18','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR > 151 AND Oldpeak > 0.3 AND Cholesterol > 256 AND Cholesterol > 298',2,0.800,'Hiperkolesterolemia ekstrem (>298) pada kondisi Max HR moderat tidak selalu menunjukkan gagal jantung fungsional.'),(19,'R19','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR > 151 AND Oldpeak > 0.3 AND Cholesterol > 256 AND Cholesterol <= 298',1,0.800,'Rentang kolesterol 256-298 memicu plak koroner yang menyebabkan abnormalitas.'),(20,'R20','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR > 151 AND Oldpeak > 0.3 AND Cholesterol <= 256 AND Cholesterol > 234',2,0.800,'Kolesterol batas menengah-tinggi (234-256) belum cukup kuat untuk memvonis kelainan jantung.'),(21,'R21','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR > 151 AND Oldpeak > 0.3 AND Cholesterol <= 256 AND Cholesterol <= 234 AND Age > 46',1,0.800,'Pria usia > 46 tahun dengan ST Flat berisiko tinggi meskipun kolesterol normal.'),(22,'R22','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR > 151 AND Oldpeak > 0.3 AND Cholesterol <= 256 AND Cholesterol <= 234 AND Age <= 46',2,0.800,'Pria dewasa muda (<= 46) dengan kolesterol terkontrol berpotensi memiliki jantung sehat.'),(23,'R23','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR > 151 AND Oldpeak <= 0.3',1,0.800,'Nilai Oldpeak sangat rendah pada pria dengan ST Flat tetap terindikasi berisiko.'),(24,'R24','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR <= 151 AND Max HR > 96 AND Max HR > 98 AND Resting BP > 105',1,0.800,'Tekanan darah > 105 dengan laju detak jantung kurang optimal merupakan tanda abnormalitas.'),(25,'R25','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR <= 151 AND Max HR > 96 AND Max HR > 98 AND Resting BP <= 105 AND Max HR > 139',2,0.800,'Tensi rendah dengan detak jantung yang masih cukup baik (>139) dianggap normal.'),(26,'R26','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR <= 151 AND Max HR > 96 AND Max HR > 98 AND Resting BP <= 105 AND Max HR <= 139',1,0.950,'Detak jantung lambat (<=139) disertai tensi rendah (<=105) menunjukkan penurunan fungsi pompa jantung.'),(27,'R27','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR <= 151 AND Max HR > 96 AND Max HR <= 98 AND Age > 56',2,0.850,'Penurunan detak jantung pada usia lanjut (>56) merupakan kondisi degeneratif yang lumrah.'),(28,'R28','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR <= 151 AND Max HR > 96 AND Max HR <= 98 AND Age <= 56',1,0.850,'Detak jantung mendekati bradikardia (96-98) pada usia tidak terlalu tua (<= 56) terindikasi abnormal.'),(29,'R29','ST Slope Flat AND Max HR <= 183 AND Sex M AND Max HR <= 177 AND Max HR <= 151 AND Max HR <= 96',1,0.900,'Bradikardia yang nyata (Max HR <= 96) memperjelas diagnosis insufisiensi jantung.'),(30,'R30','ST Slope Up AND Oldpeak > 2.4',1,0.950,'Kombinasi ST Slope Up dengan Oldpeak yang sangat drastis (>2.4) adalah bukti kuat iskemia otot jantung.'),(31,'R31','ST Slope Up AND Oldpeak <= 2.4 AND Age > 76',1,0.900,'Pasien geriatri usia sangat tua (>76) sangat rentan terhadap penyakit jantung terlepas dari Oldpeak.'),(32,'R32','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina N AND Max HR > 194',2,0.750,'Kapasitas olahraga sempurna (Max HR > 194) tanpa nyeri membuktikan kesehatan jantung yang sangat prima.'),(33,'R33','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina N AND Max HR <= 194 AND Age > 57 AND Cholesterol > 200 AND Cholesterol > 206',2,0.750,'Lansia > 57 tahun dengan hiperkolesterolemia pada ST Slope Up diprediksi bebas dari disfungsi utama jantung.'),(34,'R34','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina N AND Max HR <= 194 AND Age > 57 AND Cholesterol > 200 AND Cholesterol <= 206',1,0.800,'Rentang spesifik kolesterol antara 200-206 memicu batas ambang abnormalitas pada rentang usia lansia ini.'),(35,'R35','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina N AND Max HR <= 194 AND Age > 57 AND Cholesterol <= 200',2,0.800,'Kolesterol terkontrol (<=200) mengurangi faktor risiko secara signifikan, menormalkan diagnosis.'),(36,'R36','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina N AND Max HR <= 194 AND Age <= 57',2,0.850,'Pasien < 57 tahun tanpa gejala Angina memiliki kemungkinan jantung sehat yang tinggi.'),(37,'R37','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina Y AND ChestPainType ASY AND Oldpeak > 1',1,0.950,'Kombinasi Angina, nyeri dada Asimtomatik (ASY), dan ST tertekan adalah gejala klasik iskemia berat.'),(38,'R38','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina Y AND ChestPainType ASY AND Oldpeak <= 1 AND Max HR > 129 AND Max HR > 163',2,0.800,'Respons takikardi positif (>163) mengimbangi risiko dari nyeri dada Asimtomatik.'),(39,'R39','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina Y AND ChestPainType ASY AND Oldpeak <= 1 AND Max HR > 129 AND Max HR <= 163',1,0.850,'Ketidakmampuan memacu jantung lebih dari 163 bpm saat terjadi angina Asimtomatik berindikasi kelainan.'),(40,'R40','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina Y AND ChestPainType ASY AND Oldpeak <= 1 AND Max HR <= 129',2,0.700,'Detak jantung lambat pada Asymptomatic Pain diprediksi sebagai anomali data (diklasifikasikan Normal).'),(41,'R41','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina Y AND ChestPainType ATA',2,0.850,'Atypical Angina (ATA) pada ST Slope Up tidak dianggap sebagai penyakit jantung koroner (indikasi lambung/otot).'),(42,'R42','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina Y AND ChestPainType NAP',2,0.850,'Nyeri non-anginal (NAP) merupakan indikasi kuat bahwa masalahnya bukan berasal dari kardiovaskular.'),(43,'R43','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol > 43 AND Exercise Angina Y AND ChestPainType TA',2,0.750,'Nyeri Typical Angina pada sub-cabang pohon ini ditafsirkan sebagai negatif / dapat diobati.'),(44,'R44','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol <= 43 AND Fasting BS > 1',1,0.900,'Kolesterol amat rendah namun Gula Darah Puasa abnormal (Diabetes) menandakan gangguan metabolik dan vaskular.'),(45,'R45','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol <= 43 AND Fasting BS <= 1 AND Max HR > 103 AND Max HR > 119 AND Age > 67',2,0.850,'Lansia (>67) dengan profil lipid terlalu rendah memiliki risiko kelemahan dan kelainan pompa jantung.'),(46,'R46','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol <= 43 AND Fasting BS <= 1 AND Max HR > 103 AND Max HR > 119 AND Age <= 67 AND Age > 59',2,0.800,'Usia peralihan (60-67 tahun) dengan gula darah normal belum masuk rentang kritis penyakit struktural jantung.'),(47,'R47','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol <= 43 AND Fasting BS <= 1 AND Max HR > 103 AND Max HR > 119 AND Age <= 67 AND Age <= 59',1,0.800,'Usia di bawah 60 tahun dengan parameter profil lipid sangat rendah memicu vonis patologis dari algoritma.'),(48,'R48','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol <= 43 AND Fasting BS <= 1 AND Max HR > 103 AND Max HR <= 119',2,0.750,'Batas Max HR 103-119 dianggap aman (Normal) bagi demografi usia ini.'),(49,'R49','ST Slope Up AND Oldpeak <= 2.4 AND Age <= 76 AND Cholesterol <= 43 AND Fasting BS <= 1 AND Max HR <= 103',1,0.850,'Detak jantung puncak yang sangat lemah (<=103) adalah tanda definitif insufisiensi/penyakit jantung.');
/*!40000 ALTER TABLE `rules` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

