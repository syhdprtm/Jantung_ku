# Image produksi Jantungku (dipakai Render). Dua tahap: build dengan JDK, jalankan dengan JRE.

# ---------- tahap 1: build jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# dependensi diunduh lebih dulu agar ter-cache selama pom.xml tidak berubah
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
# tes butuh MySQL; tes sudah dijalankan di GitHub Actions (CI)
RUN ./mvnw -B -q package -DskipTests

# ---------- tahap 2: runtime ----------
FROM eclipse-temurin:21-jre
# fontconfig + font: dibutuhkan Apache POI (lebar kolom otomatis di ekspor Excel)
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --create-home jantungku
WORKDIR /app
COPY --from=build /app/target/sistem-pakar-jantung.jar app.jar
USER jantungku

ENV TZ=Asia/Jakarta \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -Duser.timezone=Asia/Jakarta -Djava.awt.headless=true"
# Render mengisi PORT; aplikasi membaca server.port=${PORT:8090}
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
