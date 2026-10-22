package com.celestedeathtracker.api.dto;

import java.util.List;

/**
 * Le tableau de bord (GET /api/summary) : « où en suis-je aujourd'hui ? ». Mêmes règles que les statistiques
 * d'une face (tentative significative, deathless réussi). Les jours sont coupés à minuit dans le fuseau du
 * navigateur (paramètre tz).
 */
public final class SummaryDtos {

    private SummaryDtos() {
    }

    /**
     * Une face avec sa progression. bestRooms = rooms atteintes par la tentative qui est allée le plus loin ;
     * knownRooms = rooms vues sur toutes les tentatives (le jeu n'envoie pas le nombre de rooms d'une map).
     */
    public record FaceProgress(
            String sid,
            String side,
            String mapName,
            long lastPlayedAt,
            int attempts,
            int clears,
            int bestRooms,
            int knownRooms) {
    }

    /** La face la plus travaillée du jour. */
    public record MostPlayed(String sid, String side, String mapName, int attempts) {
    }

    /** Chiffres du jour. bestAttemptMs = la tentative qui a tenu le plus longtemps, null sans tentative. */
    public record Today(int attempts, int clears, Long bestAttemptMs, MostPlayed mostPlayed) {
    }

    /** Un jour de l'activité, au format ISO (2026-10-22) dans le fuseau demandé. */
    public record ActivityDay(String day, int attempts, int clears) {
    }

    /**
     * resume = la dernière face jouée (null si aucune) ; recent = les faces jouées juste avant (3 au plus,
     * sans celle de resume) ; activity = les 14 derniers jours, du plus ancien à aujourd'hui, jours vides compris.
     */
    public record Summary(FaceProgress resume, Today today, List<FaceProgress> recent, List<ActivityDay> activity) {
    }
}
