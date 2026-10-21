package com.celestedeathtracker.api.security;

import com.celestedeathtracker.api.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Dit à Spring Security comment retrouver un compte à partir de ce que l'utilisateur saisit (l'email).
 * Spring Security compare ensuite lui-même le mot de passe saisi au hash, via le PasswordEncoder.
 *
 * L'identité gardée dans la session est l'ID du compte, pas l'email : un compte Discord n'a pas
 * forcément d'email, et l'ID ne change jamais.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(email)
                // Un compte créé par Discord n'a pas de mot de passe : il ne peut pas se connecter ici
                .filter(u -> u.getPasswordHash() != null)
                .map(u -> User.withUsername(String.valueOf(u.getId()))
                        .password(u.getPasswordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Compte introuvable"));
    }
}
