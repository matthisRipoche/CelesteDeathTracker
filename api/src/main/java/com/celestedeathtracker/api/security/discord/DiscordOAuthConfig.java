package com.celestedeathtracker.api.security.discord;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Déclare Discord comme fournisseur OAuth2, seulement si DISCORD_CLIENT_ID est défini.
 * Sans lui (tests, dev sans application Discord), le bouton n'apparaît pas et rien d'autre ne change.
 */
@Configuration
@ConditionalOnExpression("!'${app.discord.client-id:}'.isEmpty()")
public class DiscordOAuthConfig {

    /** Adresse de retour, à déclarer à l'identique sur le portail développeur Discord. */
    public static final String CALLBACK_PATH = "/api/auth/oauth2/callback/";
    public static final String AUTHORIZATION_PATH = "/api/auth/oauth2";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.discord.client-id}") String clientId,
            @Value("${app.discord.client-secret}") String clientSecret) {
        ClientRegistration discord = ClientRegistration.withRegistrationId("discord")
                .clientName("Discord")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}" + CALLBACK_PATH + "{registrationId}")
                // identify = pseudo + avatar seulement : ni email, ni liste de serveurs
                .scope("identify")
                .authorizationUri("https://discord.com/oauth2/authorize")
                .tokenUri("https://discord.com/api/oauth2/token")
                .userInfoUri("https://discord.com/api/users/@me")
                .userNameAttributeName("id")
                .build();
        return new InMemoryClientRegistrationRepository(discord);
    }
}
