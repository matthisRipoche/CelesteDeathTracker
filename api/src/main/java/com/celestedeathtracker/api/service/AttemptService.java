package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.dto.AttemptEventRequest;
import com.celestedeathtracker.api.model.Attempt;
import com.celestedeathtracker.api.model.RoomEntry;
import com.celestedeathtracker.api.repository.AttemptRepository;
import com.celestedeathtracker.api.repository.RoomEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Reconstruit les tentatives à partir des événements du mod. Le mod envoie sans file d'attente :
 * un événement peut se perdre ou arriver dans le désordre. La tentative est donc créée par
 * n'importe quel événement qui la porte (get-or-create), puis complétée par les suivants.
 */
@Service
public class AttemptService {

    private final AttemptRepository attempts;
    private final RoomEntryRepository roomEntries;

    public AttemptService(AttemptRepository attempts, RoomEntryRepository roomEntries) {
        this.attempts = attempts;
        this.roomEntries = roomEntries;
    }

    /**
     * Renvoie la tentative du joueur portant cet identifiant client, en la créant si besoin.
     * Pas de @Transactional : deux événements simultanés peuvent tenter de la créer ; le second
     * échoue sur la contrainte unique (user_id, client_id) et relit alors la ligne du premier.
     */
    public Attempt getOrCreate(Long userId, String clientId, String chapter, String side,
                               Integer saveSlot, long timestamp, Long sessionTimeMs) {
        // Si l'événement de départ s'est perdu, on estime le départ : l'instant de l'événement moins le
        // temps de partie déjà écoulé (sinon la tentative semblerait commencer à son premier événement reçu).
        long startedAt = Math.max(0, timestamp - (sessionTimeMs == null ? 0 : sessionTimeMs));
        return attempts.findByUserIdAndClientId(userId, clientId).orElseGet(() -> {
            try {
                return attempts.save(new Attempt(userId, clientId, chapter, side == null ? "A" : side,
                        saveSlot, startedAt));
            } catch (DataIntegrityViolationException concurrent) {
                return attempts.findByUserIdAndClientId(userId, clientId).orElseThrow(() -> concurrent);
            }
        });
    }

    /** Applique un événement de tentative (attempt_start, room_enter ou attempt_end). */
    public void apply(Long userId, AttemptEventRequest e) {
        Attempt a = getOrCreate(userId, e.attemptId(), e.chapter(), e.side(), e.saveSlot(),
                e.timestamp(), e.sessionTimeMs());
        switch (e.type()) {
            case "attempt_start" -> {
                a.start(e.timestamp(), e.mapName(), e.startedFromBeginning(), e.room());
                attempts.save(a);
            }
            case "room_enter" -> {
                if (e.room() != null && !e.room().isBlank()) {
                    roomEntries.save(new RoomEntry(a.getId(), e.room(), e.timestamp(), e.sessionTimeMs()));
                }
            }
            case "attempt_end" -> {
                a.end(e.timestamp(), e.reason(), e.room(), e.sessionTimeMs());
                attempts.save(a);
            }
            default -> throw new IllegalArgumentException("type inconnu : " + e.type());
        }
    }
}
