package it.univaq.testMiddleware.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final TokenService tokenService;

    // Chiave segreta (almeno 256 bit)
    private static final String SECRET_KEY = "01234567890123456789012345678901";

    public JwtAuthenticationFilter(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Tenta di estrarre le Claims dal token.
     * Se il token è scaduto, JJWT lancerà ExpiredJwtException (che intercettiamo da qualche altra parte).
     */
    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // 1. Controlliamo la presenza dell'header Authorization
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Estraggo il token dalla stringa "Bearer <token>"
        String token = authHeader.substring(7);

        // 3. Verifico che esista nel DB e non sia revocato
        if (tokenService.findValidToken(token).isEmpty()) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inesistente o revocato.");
            return;
        }

        // 4. Proviamo a parsare le claims
        //    Se il token è scaduto, qui scatta ExpiredJwtException.
        Claims claims;
        try {
            claims = extractClaims(token);

        } catch (ExpiredJwtException e) {
            // Il token è scaduto → lo elimino dal DB
            tokenService.deleteToken(token);

            // Pulisco il contesto di sicurezza
            SecurityContextHolder.clearContext();

            // Mando 401
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token scaduto. Effettua il login.");
            return;

        } catch (Exception e) {
            // Firma non valida, token corrotto, ecc.
            // Qui decidi tu se revocarlo/eliminarlo dal DB.
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token non valido.");
            return;
        }

        // 5. Se arriviamo qui, il token non è scaduto: estraggo lo username
        String username = claims.getSubject();

        // 6. Imposto l’autenticazione se non già presente
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(username, null, null);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        // 7. Passo la richiesta alla catena di filtri successiva
        chain.doFilter(request, response);
    }
}
