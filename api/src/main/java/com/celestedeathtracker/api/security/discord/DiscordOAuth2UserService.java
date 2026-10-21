package com.celestedeathtracker.api.security.discord;

import com.celestedeathtracker.api.model.AppUser;
import com.celestedeathtracker.api.service.DiscordAccountService;
import com.celestedeathtracker.api.service.DiscordAccountService.AlreadyLinkedException;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Appelée par Spring après l'échange du code : Discord vient de nous donner l'identité du joueur.
 * On la fait correspondre à un compte du site et on renvoie un utilisateur dont le NOM est l'ID du
 * compte, comme pour la connexion par mot de passe (voir AppUserDetailsService).
 */
public class DiscordOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    /** Attribut de session posé par POST /api/auth/discord/link : ID du compte à rattacher. */
    public static final String LINK_USER_ATTR = "discord.link.userId";
    /** Posé après un rattachement réussi, pour rediriger vers le profil plutôt que l'accueil. */
    public static final String LINKED_ATTR = "discord.linked";
    public static final String ERROR_ALREADY_LINKED = "discord_already_linked";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
    private final DiscordAccountService accounts;

    public DiscordOAuth2UserService(DiscordAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User discordUser = delegate.loadUser(request);
        Map<String, Object> attrs = discordUser.getAttributes();

        String discordId = String.valueOf(attrs.get("id"));
        // global_name = nom affiché ; username = identifiant unique, en repli
        Object display = attrs.get("global_name");
        String name = String.valueOf(display != null ? display : attrs.get("username"));
        String avatar = attrs.get("avatar") == null ? null : String.valueOf(attrs.get("avatar"));

        HttpSession session = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest().getSession();
        Long linkTo = (Long) session.getAttribute(LINK_USER_ATTR);
        session.removeAttribute(LINK_USER_ATTR); // à usage unique

        AppUser user;
        try {
            user = accounts.resolve(discordId, name, avatar, linkTo);
        } catch (AlreadyLinkedException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ERROR_ALREADY_LINKED, e.getMessage(), null));
        }
        if (linkTo != null) {
            session.setAttribute(LINKED_ATTR, true);
        }

        Map<String, Object> principalAttrs = new HashMap<>(attrs);
        principalAttrs.put("uid", String.valueOf(user.getId()));
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), principalAttrs, "uid");
    }
}
