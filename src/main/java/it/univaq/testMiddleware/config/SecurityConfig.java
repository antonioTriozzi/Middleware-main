package it.univaq.testMiddleware.config;

import it.univaq.testMiddleware.services.JwtAuthenticationFilter;
import it.univaq.testMiddleware.services.TokenService;
import it.univaq.testMiddleware.services.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Il filtro JWT non è un {@code @Bean} di tipo {@code Filter}: Spring Boot registrerebbe anche sulla catena servlet
     * (prima di Security), con effetti duplicati e 401 su POST /api/consumi nonostante X-Gateway-Ingest-Token valido.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TokenService tokenService,
            @Value("${app.gateway-ingest.secret:}") String gatewayIngestSecretRaw,
            @Value("${app.mobile-api.secret:}") String mobileApiSecretRaw,
            @Value("${app.web-sync.trigger-secret:}") String webSyncTriggerSecretRaw,
            @Value("${app.web-app.jwt.secret:}") String webAppJwtSecretRaw)
            throws Exception {
        JwtAuthenticationFilter jwtAuthFilter = new JwtAuthenticationFilter(
                tokenService,
                gatewayIngestSecretRaw,
                mobileApiSecretRaw,
                webSyncTriggerSecretRaw,
                webAppJwtSecretRaw);

        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(b -> b.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/health",
                                "/auth/login",
                                "/auth/register",
                                "/auth/otp/request",
                                "/auth/otp/verify"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/mobile/v1/health").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "Non autenticato o token non valido."))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserService userService) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userService);
        authProvider.setPasswordEncoder(new BCryptPasswordEncoder());
        return new ProviderManager(List.of(authProvider));
    }
}
