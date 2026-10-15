package com.celestedeathtracker.api.security;

import com.celestedeathtracker.api.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentifie les requêtes du mod : "Authorization: Bearer cdt_..." -> compte propriétaire.
 *
 * Volontairement PAS un @Component : Spring Boot l'enregistrerait alors sur toutes les routes.
 * Il est instancié dans SecurityConfig et n'est branché que sur la chaîne /api/ingest/**.
 */
public class ApiTokenAuthFilter extends OncePerRequestFilter {

    public static final String AUTHORITY = "INGEST";
    private static final String BEARER = "Bearer ";

    private final TokenService tokenService;

    public ApiTokenAuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            tokenService.authenticate(header.substring(BEARER.length()).trim()).ifPresent(token -> {
                // Le "principal" est l'identifiant du compte propriétaire du token
                var auth = new UsernamePasswordAuthenticationToken(
                        token.getUserId(), null, List.of(new SimpleGrantedAuthority(AUTHORITY)));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);
            });
        }
        // Sans token valide, rien n'est authentifié : la règle d'accès répondra 401.
        chain.doFilter(request, response);
    }
}
