package com.hk-fintech.hk.identityservice.config;

import com.hk-fintech.hk.identityservice.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Dependency Inversion Principle (DIP):
    // Somut sınıflara değil, soyutlamalara veya Bean'lere bağımlıyız.
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF Korumasını Kapatıyoruz
                // (Çünkü JWT kullanıyoruz, Browser session'ı yok, Stateless bir yapıdayız)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. URL Güvenlik Kuralları (Authorization)
                .authorizeHttpRequests(auth -> auth
                        // Beyaz Liste (Whitelist): Herkesin erişebileceği endpointler
                        .requestMatchers("/api/v1/auth/**", "/api/v1/test/**").permitAll()

                        // Diğer her şey için kimlik doğrulama ŞART
                        .anyRequest().authenticated()
                )

                // 3. Oturum Yönetimi (Session Management)
                // Sistem hiçbir şekilde sunucu tarafında oturum (State) tutmayacak. Her istek bağımsızdır.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. Kimlik Doğrulayıcıyı Tanımla (ApplicationConfig'de ayarladığımız)
                .authenticationProvider(authenticationProvider)

                // 5. Filtre Sıralaması
                // Standart UsernamePassword filtresinden ÖNCE bizim JWT filtremizi çalıştır.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}