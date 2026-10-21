package com.celestedeathtracker.api.dto;

import java.util.List;

/**
 * Historique des tentatives : la liste paginée de « Historique » et le détail d'une tentative.
 * Une tentative n'apparaît que si elle est « significative » (voir AttemptViews).
 */
public final class AttemptDtos {

    private AttemptDtos() {
    }

    /**
     * Une ligne de l'historique. result : success | death | abandoned | ongoing. durationMs est le temps
     * tenu (à la première mort pour une tentative morte). deaths compte toutes les morts de la tentative.
     */
    public record AttemptRow(
            long id,
            long startedAt,
            String sid,
            String side,
            String mapName,
            String result,
            Long durationMs,
            String endRoom,
            int deaths) {
    }

    /** Une page de l'historique, de la plus récente à la plus ancienne. page commence à 0. */
    public record AttemptPage(List<AttemptRow> items, int page, int size, long total) {
    }

    /** Une room traversée, dans l'ordre. sessionTimeMs est vide pour la room de départ (pas d'entrée). */
    public record RouteStep(String room, long enteredAt, Long sessionTimeMs) {
    }

    /** Une mort de la tentative. x et y sont les coordonnées du monde de la map ; first = la mort qui fait perdre le deathless. */
    public record AttemptDeath(
            long deathId,
            String room,
            int x,
            int y,
            long at,
            long timeInRoomMs,
            boolean first) {
    }

    /** Le détail d'une tentative : résumé, parcours et morts. endedAt et endReason sont vides si elle est en cours. */
    public record AttemptDetail(
            long id,
            long startedAt,
            Long endedAt,
            String sid,
            String side,
            String mapName,
            String result,
            Long durationMs,
            String endReason,
            String startRoom,
            String endRoom,
            List<RouteStep> route,
            List<AttemptDeath> deaths) {
    }
}
