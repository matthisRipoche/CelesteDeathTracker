package com.celestedeathtracker.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Une mort du joueur. @Entity = cette classe correspond à une table "death" en base,
 * JPA/Hibernate se charge de créer la table et de convertir objet <-> ligne SQL.
 */
@Entity
public class Death {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Propriétaire de la mort (le compte dont le token a servi à l'envoyer)
    @Column(name = "user_id")
    private Long userId;

    private long timestamp;
    private String chapter;
    private String room;
    // Noms de colonnes explicites : ils doivent correspondre à la migration Flyway
    // (Hibernate déduirait "deathx" de "deathX", sans le "_").
    @Column(name = "death_x")
    private int deathX;
    @Column(name = "death_y")
    private int deathY;
    @Column(name = "total_deaths")
    private int totalDeaths;
    @Column(name = "session_deaths")
    private int sessionDeaths;
    @Column(name = "time_in_room_ms")
    private long timeInRoomMs;

    // JPA exige un constructeur sans argument
    protected Death() {
    }

    public Death(Long userId, long timestamp, String chapter, String room, int deathX, int deathY,
                 int totalDeaths, int sessionDeaths, long timeInRoomMs) {
        this.userId = userId;
        this.timestamp = timestamp;
        this.chapter = chapter;
        this.room = room;
        this.deathX = deathX;
        this.deathY = deathY;
        this.totalDeaths = totalDeaths;
        this.sessionDeaths = sessionDeaths;
        this.timeInRoomMs = timeInRoomMs;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public long getTimestamp() { return timestamp; }
    public String getChapter() { return chapter; }
    public String getRoom() { return room; }
    public int getDeathX() { return deathX; }
    public int getDeathY() { return deathY; }
    public int getTotalDeaths() { return totalDeaths; }
    public int getSessionDeaths() { return sessionDeaths; }
    public long getTimeInRoomMs() { return timeInRoomMs; }
}
