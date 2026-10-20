package com.celestedeathtracker.api.dto;

import java.util.List;

/**
 * Le « live de session » : la tentative en cours et les statistiques de la session de jeu en cours.
 * Une session est une suite de tentatives sans pause de plus de 30 minutes ; elle reste ouverte tant
 * que le joueur est dans la map, même en pause (voir LiveService).
 */
public final class LiveDtos {

    private LiveDtos() {
    }

    /**
     * L'état envoyé au navigateur. session = null quand rien n'a été joué récemment. active = la session
     * continue (sinon c'est un résumé de la dernière). serverTime sert à recaler les chronos du navigateur.
     */
    public record LiveSnapshot(boolean active, SessionStats session, CurrentAttempt current, long serverTime) {
    }

    /** La tentative ouverte : où en est le joueur. lastEventAt = dernier événement reçu du jeu. */
    public record CurrentAttempt(
            long id,
            String sid,
            String side,
            String mapName,
            String room,
            long startedAt,
            long lastEventAt,
            int deaths,
            int roomsVisited) {
    }

    /** Les chiffres de la session. activeMs = temps de jeu actif (hors pauses entre les tentatives). */
    public record SessionStats(
            long startedAt,
            long lastActivityAt,
            long activeMs,
            int attempts,
            int clears,
            int deaths,
            Long bestClearMs,
            Long longestMs,
            String longestEndRoom,
            CruxRoom crux,
            List<FaceCount> faces,
            List<LiveAttempt> recent) {
    }

    /** La room qui concentre le plus d'arrêts pendant la session (au moins deux). */
    public record CruxRoom(String sid, String side, String room, int endings) {
    }

    public record FaceCount(String sid, String side, String mapName, int attempts, int clears) {
    }

    /** Une tentative récente de la session, avec sa face. result : success | death | abandoned | ongoing. */
    public record LiveAttempt(
            long id,
            String sid,
            String side,
            String mapName,
            long startedAt,
            String result,
            Long durationMs,
            String endRoom,
            int deaths) {
    }
}
