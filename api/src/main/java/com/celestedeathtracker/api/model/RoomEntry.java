package com.celestedeathtracker.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Une entrée dans une room pendant une tentative (transition d'écran réelle, pas un respawn). */
@Entity
@Table(name = "room_entries")
public class RoomEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private Long attemptId;

    @Column(nullable = false, updatable = false)
    private String room;

    @Column(name = "entered_at", nullable = false, updatable = false)
    private long enteredAt;

    @Column(name = "session_time_ms", updatable = false)
    private Long sessionTimeMs;

    protected RoomEntry() {
    }

    public RoomEntry(Long attemptId, String room, long enteredAt, Long sessionTimeMs) {
        this.attemptId = attemptId;
        this.room = room;
        this.enteredAt = enteredAt;
        this.sessionTimeMs = sessionTimeMs;
    }

    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public String getRoom() { return room; }
    public long getEnteredAt() { return enteredAt; }
    public Long getSessionTimeMs() { return sessionTimeMs; }
}
