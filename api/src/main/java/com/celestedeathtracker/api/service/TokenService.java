package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.model.ApiToken;
import com.celestedeathtracker.api.repository.ApiTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Logique des tokens d'API : génération, vérification, révocation.
 * Voir docs/auth-and-tokens.md pour le raisonnement.
 */
@Service
public class TokenService {

    static final int MAX_ACTIVE_TOKENS_PER_USER = 10;
    private static final String PREFIX = "cdt_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiTokenRepository tokens;

    public TokenService(ApiTokenRepository tokens) {
        this.tokens = tokens;
    }

    /** Le token en clair n'est disponible qu'ici, à la création : il n'est pas stocké. */
    public record CreatedToken(ApiToken token, String plaintext) {
    }

    @Transactional
    public CreatedToken create(Long userId, String label) {
        if (tokens.countByUserIdAndRevokedFalse(userId) >= MAX_ACTIVE_TOKENS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Limite de " + MAX_ACTIVE_TOKENS_PER_USER + " tokens actifs atteinte");
        }
        // 32 octets aléatoires = 256 bits d'entropie : impossible à deviner par force brute
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String plaintext = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        ApiToken saved = tokens.save(new ApiToken(userId, hash(plaintext), label.trim()));
        return new CreatedToken(saved, plaintext);
    }

    /** Retrouve le token actif correspondant à une valeur brute reçue du mod. */
    @Transactional
    public Optional<ApiToken> authenticate(String plaintext) {
        if (plaintext == null || !plaintext.startsWith(PREFIX) || plaintext.length() > 100) {
            return Optional.empty();
        }
        Optional<ApiToken> found = tokens.findByTokenHashAndRevokedFalse(hash(plaintext));
        found.ifPresent(t -> tokens.touch(t.getId(), Instant.now()));
        return found;
    }

    public List<ApiToken> list(Long userId) {
        return tokens.findByUserIdOrderByIdDesc(userId);
    }

    /** @return false si le token n'existe pas ou n'appartient pas à cet utilisateur */
    @Transactional
    public boolean revoke(Long userId, Long tokenId) {
        // Le filtre sur userId empêche de révoquer le token d'un autre compte
        return tokens.findByIdAndUserId(tokenId, userId)
                .map(t -> {
                    t.revoke();
                    return true;
                })
                .orElse(false);
    }

    /**
     * SHA-256 suffit ici (pas bcrypt) : le token est aléatoire et long, donc non devinable,
     * et on a besoin d'une recherche directe par hash en base.
     */
    static String hash(String plaintext) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
