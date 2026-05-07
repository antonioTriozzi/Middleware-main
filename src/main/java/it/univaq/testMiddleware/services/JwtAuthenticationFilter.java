package it.univaq.testMiddleware.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String INGEST_HEADER = "X-Gateway-Ingest-Token";
    private static final String DEFAULT_DEV_INGEST_SECRET = "dev-gateway-ingest-secret";

    private final TokenService tokenService;
    private final String gatewayIngestSecret;
    private final String mobileApiSecret;
    private final String webSyncTriggerSecret;
    private final String webAppJwtSecret;

    // Chiave JWT middleware (almeno 256 bit)
    private static final String SECRET_KEY = "01234567890123456789012345678901";

    /** BOM / trattini Unicode (export Word, ecc.) vs '-' ASCII in .properties / .env */
    private static String normalizeIngestToken(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String t = raw.trim().replace("\uFEFF", "");
        t = t.replace('\u2011', '-').replace('\u2010', '-').replace('\u2212', '-');
        t = t.replace("\u00AD", "");
        return t;
    }

    public JwtAuthenticationFilter(
            TokenService tokenService,
            String gatewayIngestSecretRaw,
            String mobileApiSecretRaw,
            String webSyncTriggerSecretRaw,
            String webAppJwtSecretRaw) {
        this.tokenService = tokenService;
        String t = normalizeIngestToken(gatewayIngestSecretRaw != null ? gatewayIngestSecretRaw : "");
        if (!StringUtils.hasText(t)) {
            t = DEFAULT_DEV_INGEST_SECRET;
            log.warn(
                    "app.gateway-ingest.secret vuoto: uso '{}'. Rimuovere APP_GATEWAY_INGEST_SECRET vuota dalla Run Configuration se non intenzionale.",
                    DEFAULT_DEV_INGEST_SECRET);
        }
        this.gatewayIngestSecret = t;
        this.mobileApiSecret = mobileApiSecretRaw != null ? mobileApiSecretRaw.trim() : "";
        this.webSyncTriggerSecret = webSyncTriggerSecretRaw != null ? webSyncTriggerSecretRaw.trim() : "";
        this.webAppJwtSecret = webAppJwtSecretRaw != null ? webAppJwtSecretRaw.trim() : "";
        log.info(
                "Ingest /api/consumi: segreto condiviso (lunghezza={}) + Bearer JWT middleware o, se app.web-app.jwt.secret è impostato, JWT admin web (stesso jwt.secret della web).",
                gatewayIngestSecret.length());
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    private Claims extractClaims(String token) {
        JwtParser parser = Jwts.parser()
                .verifyWith(getSigningKey())
                .build();
        return parser.parseSignedClaims(token).getPayload();
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
            while (combined.contains("//")) {
                combined = combined.replace("//", "/");
            }
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
        while (uri.contains("//")) {
            uri = uri.replace("//", "/");
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

    private static void setMobileApiAuthentication() {
        var auth = new UsernamePasswordAuthenticationToken(
                "mobile-api",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MOBILE_API")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static void setWebSyncTriggerAuthentication() {
        var auth = new UsernamePasswordAuthenticationToken(
                "web-sync-trigger",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WEB_SYNC_TRIGGER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static boolean isGetMobileV1Health(@NonNull HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && "/api/mobile/v1/health".equals(normalizedPathAfterContext(request));
    }

    private static boolean isUnderMobileV1(@NonNull HttpServletRequest request) {
        String p = normalizedPathAfterContext(request);
        return p.startsWith("/api/mobile/v1");
    }

    private static boolean isWebSyncFlush(@NonNull HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/integration/web-sync/flush".equals(normalizedPathAfterContext(request));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        // POST /api/consumi: un solo punto — evita ordine ambiguo tra più filtri Spring Security.
        boolean postConsumi = isPostApiConsumi(request);
        if (postConsumi) {
            String provided = request.getHeader(INGEST_HEADER);
            String providedNorm = normalizeIngestToken(provided != null ? provided : "");
            if (StringUtils.hasText(providedNorm) && gatewayIngestSecret.equals(providedNorm)) {
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
            // Bearer verso ingest: prima JWT web (REMOTE_CONFIG_TOKEN), se configurato, prima degli altri rami.
            String early = extractBearerToken(request);
            if (StringUtils.hasText(early)
                    && tokenService.findValidToken(early).isEmpty()
                    && StringUtils.hasText(webAppJwtSecret)) {
                ConsumiWebJwtOutcome o = verifyWebAdminJwtForConsumi(early, response);
                if (o == ConsumiWebJwtOutcome.AUTHENTICATED) {
                    chain.doFilter(request, response);
                    return;
                }
                if (o == ConsumiWebJwtOutcome.RESPONSE_WRITTEN) {
                    return;
                }
            }
        } else if ("POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI() != null
                && request.getRequestURI().contains("/consumi")) {
            log.warn(
                    "POST con 'consumi' nell'URI ma path normalizzato diverso da /api/consumi — ingest non applicato. "
                            + "normPath={} servletPath={} pathInfo={} requestUri={}",
                    normalizedPathAfterContext(request),
                    request.getServletPath(),
                    request.getPathInfo(),
                    request.getRequestURI());
        }

        if (isGetMobileV1Health(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (isUnderMobileV1(request)) {
            String mobileTok = request.getHeader("X-Mobile-Api-Token");
            if (StringUtils.hasText(mobileApiSecret)
                    && StringUtils.hasText(mobileTok)
                    && mobileApiSecret.equals(mobileTok.trim())) {
                setMobileApiAuthentication();
                chain.doFilter(request, response);
                return;
            }
        }

        if (isWebSyncFlush(request)) {
            String trig = request.getHeader("X-Web-Sync-Trigger-Token");
            if (StringUtils.hasText(webSyncTriggerSecret)
                    && StringUtils.hasText(trig)
                    && webSyncTriggerSecret.equals(trig.trim())) {
                setWebSyncTriggerAuthentication();
                chain.doFilter(request, response);
                return;
            }
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

        String token = extractBearerToken(request);
        if (!StringUtils.hasText(token)) {
            chain.doFilter(request, response);
            return;
        }

        if (tokenService.findValidToken(token).isPresent()) {
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
            return;
        }

        if (isPostApiConsumi(request) && StringUtils.hasText(webAppJwtSecret)) {
            ConsumiWebJwtOutcome o2 = verifyWebAdminJwtForConsumi(token, response);
            if (o2 == ConsumiWebJwtOutcome.AUTHENTICATED) {
                chain.doFilter(request, response);
                return;
            }
            if (o2 == ConsumiWebJwtOutcome.RESPONSE_WRITTEN) {
                return;
            }
        }

        SecurityContextHolder.clearContext();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inesistente o revocato.");
        return;
    }

    /**
     * Bearer senza prefisso; trim (spazi/newline da .env).
     */
    private static String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "";
        }
        return authHeader.substring(7).trim();
    }

    private enum ConsumiWebJwtOutcome {
        /** Continua con JWT middleware o altri schemi */
        NOT_APPLICABLE,
        /** SecurityContext impostato: invocare chain.doFilter */
        AUTHENTICATED,
        /** 401 già inviata (es. token scaduto) */
        RESPONSE_WRITTEN
    }

    /**
     * Verifica JWT emesso dalla web (REMOTE_CONFIG_TOKEN) per POST /api/consumi.
     */
    private ConsumiWebJwtOutcome verifyWebAdminJwtForConsumi(String token, HttpServletResponse response)
            throws IOException {
        if (!StringUtils.hasText(webAppJwtSecret)) {
            log.warn("JWT web ingest: app.web-app.jwt.secret vuoto sul middleware — impossibile verificare REMOTE_CONFIG_TOKEN.");
            return ConsumiWebJwtOutcome.NOT_APPLICABLE;
        }
        try {
            JwtParser wp = Jwts.parser()
                    .verifyWith(webAppJwtSigningKey())
                    .build();
            Claims wc = wp.parseSignedClaims(token).getPayload();
            String roleStr = wc.get("role", String.class);
            if (roleStr == null && wc.get("role") != null) {
                roleStr = String.valueOf(wc.get("role"));
            }
            if (!"ROLE_ADMIN".equals(roleStr)) {
                log.warn("JWT web ingest: role={} (atteso ROLE_ADMIN), sub={}", roleStr, wc.getSubject());
                return ConsumiWebJwtOutcome.NOT_APPLICABLE;
            }
            String sub = wc.getSubject();
            if (sub == null) {
                log.warn("JWT web ingest: subject assente");
                return ConsumiWebJwtOutcome.NOT_APPLICABLE;
            }
            var authToken = new UsernamePasswordAuthenticationToken(
                    sub, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("JWT web ingest OK per sub={}", sub);
            return ConsumiWebJwtOutcome.AUTHENTICATED;
        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            log.warn("JWT web ingest: token scaduto (sub={})", e.getClaims() != null ? e.getClaims().getSubject() : "?");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token web scaduto. Rinnovare REMOTE_CONFIG_TOKEN.");
            return ConsumiWebJwtOutcome.RESPONSE_WRITTEN;
        } catch (Exception e) {
            log.warn(
                    "JWT web ingest: firma/chiave non valida o token malformato (allinea app.web-app.jwt.secret con jwt.secret della web). {}: {}",
                    e.getClass().getSimpleName(),
                    e.getMessage());
            return ConsumiWebJwtOutcome.NOT_APPLICABLE;
        }
    }

    private SecretKey webAppJwtSigningKey() {
        return Keys.hmacShaKeyFor(webAppJwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
