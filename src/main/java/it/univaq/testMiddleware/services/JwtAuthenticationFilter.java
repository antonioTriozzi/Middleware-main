package it.univaq.testMiddleware.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String INGEST_HEADER = "X-Gateway-Ingest-Token";
    private static final String DEFAULT_DEV_INGEST_SECRET = "dev-gateway-ingest-secret";

    private final TokenService tokenService;
    private final String gatewayIngestSecret;

    // Chiave JWT middleware (almeno 256 bit)
    private static final String SECRET_KEY = "01234567890123456789012345678901";

    public JwtAuthenticationFilter(
            TokenService tokenService,
            @Value("${app.gateway-ingest.secret:}") String gatewayIngestSecretRaw) {
        this.tokenService = tokenService;
        String t = gatewayIngestSecretRaw != null ? gatewayIngestSecretRaw.trim() : "";
        if (!StringUtils.hasText(t)) {
            t = DEFAULT_DEV_INGEST_SECRET;
            log.warn(
                    "app.gateway-ingest.secret vuoto: uso '{}'. Rimuovere APP_GATEWAY_INGEST_SECRET vuota dalla Run Configuration se non intenzionale.",
                    DEFAULT_DEV_INGEST_SECRET);
        }
        this.gatewayIngestSecret = t;
    }

    @PostConstruct
    void logIngest() {
        log.info("Ingest /api/consumi: segreto condiviso (lunghezza={}) + Bearer JWT middleware.", gatewayIngestSecret.length());
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private static boolean isPostApiConsumi(@NonNull HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = normalizedPathAfterContext(request);
        if ("/api/consumi".equals(path)) {
            return true;
        }
        String sp = request.getServletPath();
        if (StringUtils.hasText(sp)) {
            String pi = request.getPathInfo() != null ? request.getPathInfo() : "";
            String combined = sp.endsWith("/") ? sp.substring(0, sp.length() - 1) : sp;
            combined = combined + (pi.isEmpty() ? "" : (pi.startsWith("/") ? pi : "/" + pi));
            if (combined.endsWith("/") && combined.length() > 1) {
                combined = combined.substring(0, combined.length() - 1);
            }
            return "/api/consumi".equals(combined);
        }
        return false;
    }

    private static String normalizedPathAfterContext(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        int q = uri.indexOf('?');
        if (q >= 0) {
            uri = uri.substring(0, q);
        }
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        if (uri.endsWith("/") && uri.length() > 1) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri.isEmpty() ? "/" : uri;
    }

    private static boolean hasBearer(@NonNull HttpServletRequest request) {
        String authz = request.getHeader("Authorization");
        return StringUtils.hasText(authz)
                && authz.startsWith("Bearer ")
                && authz.length() > "Bearer ".length();
    }

    private static void setGatewayIngestAuthentication() {
        // Spring Security 6: il costruttore con authorities imposta già authenticated; non chiamare setAuthenticated(true).
        var auth = new UsernamePasswordAuthenticationToken(
                "gateway-ingest",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_GATEWAY_INGEST")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        // POST /api/consumi: un solo punto — evita ordine ambiguo tra più filtri Spring Security.
        if (isPostApiConsumi(request)) {
            String provided = request.getHeader(INGEST_HEADER);
            if (StringUtils.hasText(provided) && gatewayIngestSecret.equals(provided.trim())) {
                setGatewayIngestAuthentication();
                chain.doFilter(request, response);
                return;
            }
            if (!hasBearer(request)) {
                response.setHeader("WWW-Authenticate", "Gateway-Ingest error=\"invalid_token\"");
                response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "POST /api/consumi: usare header " + INGEST_HEADER + " oppure Authorization: Bearer (JWT da /auth/login).");
                return;
            }
            // Bearer presente → validazione sotto
        }

        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null
                && current.isAuthenticated()
                && !(current instanceof AnonymousAuthenticationToken)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (tokenService.findValidToken(token).isEmpty()) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inesistente o revocato.");
            return;
        }

        Claims claims;
        try {
            claims = extractClaims(token);

        } catch (ExpiredJwtException e) {
            tokenService.deleteToken(token);
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token scaduto. Effettua il login.");
            return;

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token non valido.");
            return;
        }

        String username = claims.getSubject();

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(username, null, null);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        chain.doFilter(request, response);
    }
}
