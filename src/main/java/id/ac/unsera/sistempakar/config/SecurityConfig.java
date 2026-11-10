package id.ac.unsera.sistempakar.config;

import id.ac.unsera.sistempakar.repository.PenggunaRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.savedrequest.NullRequestCache;

import java.util.function.Predicate;

/**
 * Dua pintu masuk yang terpisah:
 * <ul>
 *   <li><b>Petugas</b> (ADMIN, PAKAR) — {@code /petugas/masuk}, tidak ditautkan dari halaman publik.
 *       Rantai ini menjaga {@code /admin/**} dan {@code /pakar/**}; bagi tamu dan pengguna biasa
 *       area tersebut tampak seperti halaman yang tidak ada (404).</li>
 *   <li><b>Pengguna</b> (USER) — {@code /login}. Akun petugas selalu ditolak di sini.</li>
 * </ul>
 * Halaman bersama (diagnosa, hasil, riwayat, profil) cukup wajib login; sesi petugas tetap berlaku
 * karena keduanya memakai sesi yang sama. Lihat {@link PemantauLogin} untuk penguncian akun,
 * audit login, dan sesi petugas yang lebih singkat.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String MASUK_PETUGAS = "/petugas/masuk";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Bean global yang sengaja menolak semua akun. Setiap rantai memakai AuthenticationManager-nya
     * sendiri; bean ini hanya mencegah Spring Boot membuat akun bawaan in-memory.
     */
    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    /** Memuat akun dari database, hanya bila role-nya diizinkan untuk pintu tersebut. */
    static UserDetailsService layanan(PenggunaRepository repo, Predicate<String> roleBoleh) {
        return username -> repo.findByUsername(username)
                .filter(u -> roleBoleh.test(u.role()))
                .map(u -> User.withUsername(u.username()).password(u.password()).roles(u.role())
                        .accountLocked(u.isTerkunci()).build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    private static AuthenticationManager manager(UserDetailsService uds, PasswordEncoder encoder,
                                                 ApplicationEventPublisher events) {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider(uds);
        p.setPasswordEncoder(encoder);
        ProviderManager m = new ProviderManager(p);
        m.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(events));
        return m;
    }

    @Bean
    @Order(1)
    SecurityFilterChain petugasChain(HttpSecurity http, PenggunaRepository repo, PasswordEncoder encoder,
                                     ApplicationEventPublisher events, PemantauLogin pemantau) throws Exception {
        AccessDeniedHandlerImpl ditolak = new AccessDeniedHandlerImpl();
        ditolak.setErrorPage("/akses-ditolak");
        http
                .securityMatcher("/petugas/**", "/admin/**", "/pakar/**")
                .authenticationManager(manager(layanan(repo, r -> "ADMIN".equals(r) || "PAKAR".equals(r)), encoder, events))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(MASUK_PETUGAS).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/pakar/**").hasRole("PAKAR")
                        .anyRequest().authenticated())
                .formLogin(f -> f
                        .loginPage(MASUK_PETUGAS)
                        .loginProcessingUrl(MASUK_PETUGAS)
                        .successHandler(pemantau.sukses(true))
                        .failureHandler(pemantau.gagal(true))
                        .permitAll())
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                .exceptionHandling(e -> e
                        // Tamu: area petugas tidak "ada" (tidak diarahkan ke halaman login).
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(HttpServletResponse.SC_NOT_FOUND))
                        // Pengguna biasa: juga 404. Petugas dengan role lain: 403 "Akses ditolak".
                        .accessDeniedHandler((req, res, ex) -> {
                            var auth = SecurityContextHolder.getContext().getAuthentication();
                            if (auth != null && !(auth instanceof AnonymousAuthenticationToken) && PemantauLogin.isPetugas(auth)) {
                                ditolak.handle(req, res, ex);
                            } else {
                                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                            }
                        }));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain penggunaChain(HttpSecurity http, PenggunaRepository repo, PasswordEncoder encoder,
                                      ApplicationEventPublisher events, PemantauLogin pemantau) throws Exception {
        http
                .authenticationManager(manager(layanan(repo, "USER"::equals), encoder, events))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/login", "/daftar", "/tentang", "/privasi", "/error",
                                "/css/**", "/js/**", "/fonts/**", "/img/**", "/manifest.webmanifest").permitAll()
                        .anyRequest().authenticated())
                .formLogin(f -> f
                        .loginPage("/login")
                        .successHandler(pemantau.sukses(false))
                        .failureHandler(pemantau.gagal(false))
                        .permitAll())
                .logout(l -> l.logoutUrl("/logout").logoutSuccessHandler(pemantau.keluar()).permitAll())
                .exceptionHandling(e -> e.accessDeniedPage("/akses-ditolak"));
        return http.build();
    }
}
