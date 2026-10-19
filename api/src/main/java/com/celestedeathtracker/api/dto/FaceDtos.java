package com.celestedeathtracker.api.dto;

import java.util.List;

/**
 * Statistiques par « face » (une map + A, B ou C), ce que montrent « Mes maps » et le détail d'une face.
 * Une tentative y compte seulement si elle est « significative » (voir FaceStatsService).
 */
public final class FaceDtos {

    private FaceDtos() {
    }

    /** Une ligne de « Mes maps ». */
    public record FaceSummary(
            String sid,
            String side,
            String mapName,
            int attempts,
            int clears,
            long lastPlayedAt) {
    }

    /** Une room, dans l'ordre de la map : combien de tentatives y entrent, s'y arrêtent, combien de morts. */
    public record RoomStat(String room, int entries, int endings, int deaths) {
    }

    /** Une tentative dans le graphique d'évolution. result : success | death | abandoned | ongoing. */
    public record EvolutionPoint(long attemptId, long startedAt, Long durationMs, String result) {
    }

    /** Une ligne de « Dernières tentatives ». roomsCleared = rooms franchies avant l'arrêt. */
    public record RecentAttempt(
            long id,
            long startedAt,
            String result,
            Long durationMs,
            String endRoom,
            int roomsCleared,
            int deaths) {
    }

    /** Le détail d'une face. */
    public record FaceDetail(
            String sid,
            String side,
            String mapName,
            int attempts,
            int clears,
            double clearRate,
            Long bestClearMs,
            Long longestMs,
            String longestEndRoom,
            List<RoomStat> rooms,
            List<EvolutionPoint> evolution,
            List<RecentAttempt> recent) {
    }
}
