package com.celestedeathtracker.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class PairingDtos {

    private PairingDtos() {
    }

    public record StartRequest(@Size(max = 60) String deviceName) {
    }

    /** deviceCode : secret du mod. userCode : à afficher dans le jeu. */
    public record StartResponse(String deviceCode, String userCode, String verificationUrl,
                                int expiresInSeconds, int intervalSeconds) {
    }

    public record PollRequest(@NotBlank @Size(max = 100) String deviceCode) {
    }

    /** status : pending | approved | denied | expired. Le token n'est présent qu'avec "approved". */
    public record PollResponse(String status, String token) {
    }

    public record LookupResponse(String userCode, String deviceName, Instant requestedAt) {
    }

    public record CodeRequest(@NotBlank @Size(max = 20) String userCode) {
    }
}
