package com.celestedeathtracker.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Une tentative : une partie sur une face de map, de l'entrée à la sortie. Pour un deathless, elle
 * réussit si elle se termine par "completed" sans aucune mort rattachée.
 */
@Entity
@Table(name = "attempts")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // UUID fabriqué par le mod, porté par tous les événements de la tentative
    @Column(name = "client_id", nullable = false, updatable = false)
    private String clientId;

    @Column(nullable = false, updatable = false)
    private String chapter;

    // "A", "B" ou "C"
    @Column(nullable = false, updatable = false)
    private String side;

    @Column(name = "save_slot")
    private Integer saveSlot;

    @Column(name = "map_name")
    private String mapName;

    @Column(name = "started_at", nullable = false)
    private long startedAt;

    @Column(name = "started_from_beginning")
    private Boolean startedFromBeginning;

    @Column(name = "start_room")
    private String startRoom;

    @Column(name = "ended_at")
    private Long endedAt;

    // completed | restart | give_up | save_and_quit | golden_restart | switched
    @Column(name = "end_reason")
    private String endReason;

    @Column(name = "end_room")
    private String endRoom;

    @Column(name = "session_time_ms")
    private Long sessionTimeMs;

    protected Attempt() {
    }

    public Attempt(Long userId, String clientId, String chapter, String side, Integer saveSlot, long startedAt) {
        this.userId = userId;
        this.clientId = clientId;
        this.chapter = chapter;
        this.side = side;
        this.saveSlot = saveSlot;
        this.startedAt = startedAt;
    }

    /** Applique l'événement attempt_start (il peut arriver après une mort si son envoi a été retardé). */
    public void start(long startedAt, String mapName, Boolean startedFromBeginning, String startRoom) {
        this.startedAt = startedAt;
        this.mapName = mapName;
        this.startedFromBeginning = startedFromBeginning;
        this.startRoom = startRoom;
    }

    /** Applique l'événement attempt_end. */
    public void end(long endedAt, String reason, String room, Long sessionTimeMs) {
        this.endedAt = endedAt;
        this.endReason = reason;
        this.endRoom = room;
        this.sessionTimeMs = sessionTimeMs;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getClientId() { return clientId; }
    public String getChapter() { return chapter; }
    public String getSide() { return side; }
    public Integer getSaveSlot() { return saveSlot; }
    public String getMapName() { return mapName; }
    public long getStartedAt() { return startedAt; }
    public Boolean getStartedFromBeginning() { return startedFromBeginning; }
    public String getStartRoom() { return startRoom; }
    public Long getEndedAt() { return endedAt; }
    public String getEndReason() { return endReason; }
    public String getEndRoom() { return endRoom; }
    public Long getSessionTimeMs() { return sessionTimeMs; }
}
