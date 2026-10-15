package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.model.PairingRequest;
import com.celestedeathtracker.api.repository.PairingRequestRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appairage par code, sur le modèle de la connexion d'une TV (RFC 8628) :
 *
 *   1. le mod appelle start()       -> reçoit un deviceCode (secret) et un userCode (affiché)
 *   2. l'utilisateur, connecté au site, saisit le userCode et approuve -> approve()
 *   3. le mod interroge poll()      -> reçoit UNE fois un token d'API tout neuf
 *
 * Le token n'existe jamais avant l'étape 3 : il n'est donc stocké nulle part en clair.
 */
@Service
public class PairingService {

    public static final Duration LIFETIME = Duration.ofMinutes(10);
    public static final int POLL_INTERVAL_SECONDS = 3;

    // Sans 0/O, 1/I/L : ambigus à lire à l'écran
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final Pattern NORMALIZED = Pattern.compile("[" + ALPHABET + "]{" + CODE_LENGTH + "}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PairingRequestRepository requests;
    private final TokenService tokenService;

    public PairingService(PairingRequestRepository requests, TokenService tokenService) {
        this.requests = requests;
        this.tokenService = tokenService;
    }

    public record Started(String deviceCode, String userCode, int expiresInSeconds) {
    }

    public record PollResult(String status, String token) {
        static PollResult of(String status) {
            return new PollResult(status, null);
        }
    }

    @Transactional
    public Started start(String deviceName, String requesterIp) {
        String deviceCode = "cdt_dev_" + randomUrlSafe(32);
        String name = sanitizeDeviceName(deviceName);

        // Collision de userCode : quasi impossible (32^8), mais la contrainte UNIQUE tranche
        for (int attempt = 0; attempt < 5; attempt++) {
            String userCode = randomUserCode();
            try {
                requests.saveAndFlush(new PairingRequest(TokenService.hash(deviceCode), userCode, name,
                        requesterIp, Instant.now().plus(LIFETIME)));
                return new Started(deviceCode, display(userCode), (int) LIFETIME.toSeconds());
            } catch (DataIntegrityViolationException e) {
                // on réessaie avec un autre code
            }
        }
        throw new IllegalStateException("Impossible de générer un code d'appairage");
    }

    /** Ce que le site affiche avant que l'utilisateur confirme. Vide si code inconnu/expiré/déjà traité. */
    @Transactional(readOnly = true)
    public Optional<PairingRequest> lookup(String userCode) {
        return normalize(userCode)
                .flatMap(requests::findByUserCode)
                .filter(r -> PairingRequest.PENDING.equals(r.getStatus()) && !r.isExpired());
    }

    @Transactional
    public boolean approve(Long userId, String userCode) {
        return lookup(userCode)
                .map(r -> requests.approve(r.getId(), userId, Instant.now()) == 1)
                .orElse(false);
    }

    @Transactional
    public boolean deny(String userCode) {
        return lookup(userCode)
                .map(r -> requests.deny(r.getId(), Instant.now()) == 1)
                .orElse(false);
    }

    @Transactional
    public PollResult poll(String deviceCode) {
        Optional<PairingRequest> found = requests.findByDeviceCodeHash(TokenService.hash(deviceCode));
        if (found.isEmpty()) {
            return PollResult.of("expired");
        }
        PairingRequest r = found.get();

        switch (r.getStatus()) {
            case PairingRequest.PENDING:
                return PollResult.of(r.isExpired() ? "expired" : "pending");
            case PairingRequest.DENIED:
                return PollResult.of("denied");
            case PairingRequest.APPROVED:
                // consume() est atomique : une seule interrogation obtient le token.
                // Si la génération échoue (ex. limite de tokens), la transaction est annulée
                // et la demande reste approuvée : le mod pourra réessayer.
                if (requests.consume(r.getId()) == 1) {
                    var created = tokenService.create(r.getUserId(), "Celeste – " + r.getDeviceName());
                    return new PollResult("approved", created.plaintext());
                }
                return PollResult.of("expired");
            default: // CONSUMED : le token a déjà été remis, on ne le redonne jamais
                return PollResult.of("expired");
        }
    }

    /** Ménage : on garde une heure les demandes expirées puis on les supprime. */
    @Scheduled(fixedDelay = 600_000)
    @Transactional
    public void purgeExpired() {
        requests.deleteExpiredBefore(Instant.now().minus(Duration.ofHours(1)));
    }

    // ------------------------------------------------------------------

    /** "k7qm-4xrd", " K7QM 4XRD " -> "K7QM4XRD" ; vide si le format est invalide. */
    static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String s = input.toUpperCase().replaceAll("[\\s-]", "");
        return NORMALIZED.matcher(s).matches() ? Optional.of(s) : Optional.empty();
    }

    static String display(String normalized) {
        return normalized.substring(0, 4) + "-" + normalized.substring(4);
    }

    private static String randomUserCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String sanitizeDeviceName(String name) {
        if (name == null || name.isBlank()) {
            return "Appareil inconnu";
        }
        // Texte affiché tel quel à l'utilisateur : on retire tout caractère de contrôle
        String s = name.replaceAll("\\p{Cntrl}", "").trim();
        return s.length() > 60 ? s.substring(0, 60) : (s.isEmpty() ? "Appareil inconnu" : s);
    }
}
