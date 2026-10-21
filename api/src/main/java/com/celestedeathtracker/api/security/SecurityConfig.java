package com.celestedeathtracker.api.security;

import com.celestedeathtracker.api.security.discord.DiscordOAuth2UserService;
import com.celestedeathtracker.api.security.discord.DiscordOAuthConfig;
import com.celestedeathtracker.api.service.DiscordAccountService;
import com.celestedeathtracker.api.service.TokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Deux chaînes de sécurité, deux mondes :
 *  - /api/ingest/** : le mod C#. Sans session, sans cookie, sans CSRF, token d'API en Bearer.
 *  - le reste       : le navigateur. Session par cookie + CSRF.
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** Où est stocké le contexte de sécurité entre deux requêtes : dans la session HTTP. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @Order(1)
    SecurityFilterChain ingestFilterChain(HttpSecurity http, TokenService tokenService) throws Exception {
        http
            .securityMatcher("/api/ingest/**")
            // Pas de session : chaque requête porte son token. Pas de cookie, donc pas de CSRF possible.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(new ApiTokenAuthFilter(tokenService), UsernamePasswordAuthenticationFilter.class)
            // Seul un token d'API donne l'autorité INGEST : une session de navigateur n'y suffit pas.
            .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(ApiTokenAuthFilter.AUTHORITY))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webFilterChain(HttpSecurity http, SecurityContextRepository repo,
                                        ObjectProvider<ClientRegistrationRepository> oauthClients,
                                        DiscordAccountService discordAccounts) throws Exception {
        http
            .securityContext(c -> c.securityContextRepository(repo))

            // CSRF : Spring pose un cookie XSRF-TOKEN lisible par JavaScript ; Angular le renvoie
            // automatiquement dans l'en-tête X-XSRF-TOKEN sur les POST/PUT/DELETE.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // Appelés par le mod : pas de cookie, donc rien à protéger contre le CSRF
                .ignoringRequestMatchers(
                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/pairing/start"),
                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/pairing/poll")))
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                // Les erreurs (400, 409...) sont redirigées en interne vers /error : si cette page
                // exigeait une connexion, tout code d'erreur serait transformé en 401.
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/me", "/api/auth/providers").permitAll()
                // Départ et retour du flux Discord (le navigateur y arrive par redirection, sans session)
                .requestMatchers(DiscordOAuthConfig.AUTHORIZATION_PATH + "/**").permitAll()
                // Le mod n'a pas encore de token à ce stade : ces deux routes sont publiques
                // (et limitées en débit). Rien n'est révélé tant qu'un compte n'a pas approuvé.
                .requestMatchers(HttpMethod.POST, "/api/pairing/start", "/api/pairing/poll").permitAll()
                .anyRequest().authenticated())

            // API JSON : on répond 401, jamais de redirection vers une page de login
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable()); // géré par AuthController

        // Connexion Discord : active seulement si l'application Discord est configurée
        if (oauthClients.getIfAvailable() != null) {
            http.oauth2Login(o -> o
                .loginPage("/login") // page Angular : évite la page de connexion générée par Spring
                .authorizationEndpoint(a -> a.baseUri(DiscordOAuthConfig.AUTHORIZATION_PATH))
                .redirectionEndpoint(r -> r.baseUri(DiscordOAuthConfig.CALLBACK_PATH + "*"))
                .userInfoEndpoint(u -> u.userService(new DiscordOAuth2UserService(discordAccounts)))
                .successHandler(discordSuccessHandler())
                .failureHandler(discordFailureHandler()));
        }

        return http.build();
    }

    /** Après Discord : retour au profil si c'était un rattachement, à l'accueil sinon. */
    private static AuthenticationSuccessHandler discordSuccessHandler() {
        return (request, response, authentication) -> {
            HttpSession session = request.getSession(false);
            boolean linked = session != null && session.getAttribute(DiscordOAuth2UserService.LINKED_ATTR) != null;
            if (linked) {
                session.removeAttribute(DiscordOAuth2UserService.LINKED_ATTR);
            }
            // /login : la garde des invités renvoie vers la page demandée avant la connexion (ex. /pair)
            response.sendRedirect(linked ? "/profile?discord=lie" : "/login?discord=ok");
        };
    }

    private static AuthenticationFailureHandler discordFailureHandler() {
        return (request, response, exception) -> {
            boolean alreadyLinked = exception instanceof OAuth2AuthenticationException oauth
                    && DiscordOAuth2UserService.ERROR_ALREADY_LINKED.equals(oauth.getError().getErrorCode());
            response.sendRedirect(alreadyLinked ? "/profile?discord=deja-lie" : "/login?discord=erreur");
        };
    }
}
