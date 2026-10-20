package com.celestedeathtracker.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Événement de tentative envoyé par le mod (cf. docs/data-model.md) :
 * <ul>
 *   <li>attempt_start : entrée dans une face de map (mapName, startedFromBeginning, room de départ) ;</li>
 *   <li>room_enter : transition vers une nouvelle room ;</li>
 *   <li>attempt_end : sortie, avec le motif (reason).</li>
 * </ul>
 * Chaque événement répète chapter/side/saveSlot : le serveur peut ainsi créer la tentative à
 * n'importe quel événement reçu, même si le précédent s'est perdu (le mod n'a pas de file d'attente).
 */
public record AttemptEventRequest(
        @Pattern(regexp = "attempt_start|room_enter|attempt_end") String type,
        @NotBlank @Size(max = 36) String attemptId,
        @NotBlank @Size(max = 255) String chapter,
        @Pattern(regexp = "[ABC]") String side,
        Integer saveSlot,
        @Size(max = 255) String room,
        @Positive long timestamp,
        Long sessionTimeMs,
        @Size(max = 255) String mapName,
        Boolean startedFromBeginning,
        @Pattern(regexp = "completed|restart|give_up|save_and_quit|golden_restart|switched|game_closed") String reason) {
}
