package com.celestedeathtracker.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Ce que le mod envoie (JSON camelCase, cf. docs/data-model.md).
 * On sépare le DTO de l'entité : le format de l'API ne doit pas dépendre de la base.
 * Les annotations de validation sont vérifiées grâce à @Valid dans le controller.
 *
 * attemptId, side et saveSlot sont facultatifs : l'ancien mod ne les envoie pas.
 */
public record DeathRequest(
        @Positive long timestamp,
        @NotBlank String chapter,
        @NotBlank String room,
        int deathX,
        int deathY,
        int totalDeaths,
        int sessionDeaths,
        long timeInRoomMs,
        @Size(max = 36) String attemptId,
        @Pattern(regexp = "[ABC]") String side,
        Integer saveSlot) {
}
