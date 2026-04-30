package it.univaq.testMiddleware.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Dopo il filtro JWT: se la richiesta non risulta autenticata,
 * accetta POST {@code /api/consumi} con header {@code X-Gateway-Ingest-Token} = {@code app.gateway-ingest.secret}.
 */
@Component
public class GatewayIngestTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Gateway-Ingest-Token";

    @Value("${app.gateway-ingest.secret:}")
    private String expectedSecret;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.endsWith("/api/consumi");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!StringUtils.hasText(expectedSecret)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        boolean alreadyAuthenticated = current != null
                && current.isAuthenticated()
                && !(current instanceof AnonymousAuthenticationToken);
        if (alreadyAuthenticated) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (!StringUtils.hasText(provided) || !expectedSecret.equals(provided.trim())) {
            filterChain.doFilter(request, response);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "gateway-ingest",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_GATEWAY_INGEST")));
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }
}
