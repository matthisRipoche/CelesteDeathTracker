package com.celestedeathtracker.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security génère le token CSRF "paresseusement" : sans ce filtre, le cookie XSRF-TOKEN
 * n'est posé que si du code lit le token, donc jamais sur un simple GET. Or Angular a besoin de
 * ce cookie dès la première réponse pour pouvoir envoyer ensuite X-XSRF-TOKEN sur ses POST.
 * Lire le token ici force son écriture dans le cookie à chaque requête.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
