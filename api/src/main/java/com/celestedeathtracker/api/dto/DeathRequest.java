package com.celestedeathtracker.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Ce que le mod envoie (JSON camelCase, cf. docs/data-model.md).
 * On sépare le DTO de l'entité : le format de l'API ne doit pas dépendre de la base.
 * Les annotations de validation sont vérifiées grâce à @Valid dans le controller.
 */
public record DeathRequest(
        @Positive long timestamp,
        @NotBlank String chapter,
        @NotBlank String room,
        int deathX,
        int deathY,
        int totalDeaths,
        int sessionDeaths,
        long timeInRoomMs) {
}
