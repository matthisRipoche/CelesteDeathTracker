package com.celestedeathtracker.api.dto;

import com.celestedeathtracker.api.model.Death;

/** Ce que l'API renvoie pour une mort. */
public record DeathResponse(
        long id,
        long timestamp,
        String chapter,
        String room,
        int deathX,
        int deathY,
        int totalDeaths,
        int sessionDeaths,
        long timeInRoomMs,
        Long attemptId,
        String side,
        Integer saveSlot) {

    public static DeathResponse from(Death d) {
        return new DeathResponse(d.getId(), d.getTimestamp(), d.getChapter(), d.getRoom(),
                d.getDeathX(), d.getDeathY(), d.getTotalDeaths(), d.getSessionDeaths(),
                d.getTimeInRoomMs(), d.getAttemptId(), d.getSide(), d.getSaveSlot());
    }
}
