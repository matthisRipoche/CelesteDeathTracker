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

    /**
     * Une mort dans une room. x et y sont les coordonnées du monde de la map (Y vers le bas), pas celles
     * de la room. first = première mort de sa tentative, celle qui fait perdre le deathless.
     */
    public record DeathPoint(long deathId, long attemptId, long at, int x, int y, boolean first) {
    }

    /**
     * Ce qui est arrivé à une tentative qui est entrée dans la room. outcome : cleared (room franchie),
     * died (au moins une mort ici) ou left (tentative terminée ici sans mourir : abandon, recommencer).
     */
    public record RoomOutcome(long attemptId, long startedAt, String outcome) {
    }

    /**
     * Le détail d'une room d'une face. stops = tentatives qui s'arrêtent ici (mort ou abandon, comme
     * RoomStat.endings) et stopRate = stops / entries, toujours entre 0 et 1. deathsTotal compte toutes
     * les morts, plusieurs par tentative possibles. position commence à 1.
     */
    public record RoomDetail(
            String sid,
            String side,
            String mapName,
            String room,
            int position,
            int roomCount,
            String previousRoom,
            String nextRoom,
            int entries,
            int stops,
            double stopRate,
            int deathsTotal,
            Long lastDeathAt,
            List<DeathPoint> points,
            List<RoomOutcome> outcomes) {
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
