package com.celestedeathtracker.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** "session_start" / "session_end", envoyés par le mod à l'entrée et à la sortie d'une map. */
public record SessionEventRequest(
        @Pattern(regexp = "session_start|session_end") String type,
        @NotBlank String chapter,
        String room,
        long timestamp) {
}
