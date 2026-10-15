package com.celestedeathtracker.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Une demande d'appairage entre un jeu (le mod) et un compte. Vit 10 minutes. */
@Entity
@Table(name = "pairing_requests")
public class PairingRequest {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String DENIED = "DENIED";
    public static final String CONSUMED = "CONSUMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code_hash", nullable = false, unique = true, updatable = false)
    private String deviceCodeHash;

    @Column(name = "user_code", nullable = false, unique = true, updatable = false)
    private String userCode;

    @Column(nullable = false)
    private String status;

    @Column(name = "device_name", nullable = false, updatable = false)
    private String deviceName;

    @Column(name = "requester_ip", updatable = false)
    private String requesterIp;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PairingRequest() {
    }

    public PairingRequest(String deviceCodeHash, String userCode, String deviceName,
                          String requesterIp, Instant expiresAt) {
        this.deviceCodeHash = deviceCodeHash;
        this.userCode = userCode;
        this.deviceName = deviceName;
        this.requesterIp = requesterIp;
        this.status = PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }

    public Long getId() { return id; }
    public String getDeviceCodeHash() { return deviceCodeHash; }
    public String getUserCode() { return userCode; }
    public String getStatus() { return status; }
    public String getDeviceName() { return deviceName; }
    public String getRequesterIp() { return requesterIp; }
    public Long getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
