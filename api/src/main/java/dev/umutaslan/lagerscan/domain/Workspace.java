package dev.umutaslan.lagerscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
public class Workspace {
    /** The template every visitor copy is made from. */
    public static final UUID TEMPLATE = UUID.fromString("1a6e5ca9-0000-4000-8000-000000000001");

    @Id private UUID id;
    @Column(name = "join_token") private String joinToken;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "last_seen_at") private Instant lastSeenAt;
    @Column(name = "seed_date") private LocalDate seedDate;

    protected Workspace() {}

    public Workspace(UUID id, String joinToken, Instant now, LocalDate seedDate) {
        this.id = id;
        this.joinToken = joinToken;
        this.createdAt = now;
        this.lastSeenAt = now;
        this.seedDate = seedDate;
    }

    public UUID getId() { return id; }
    public String getJoinToken() { return joinToken; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public LocalDate getSeedDate() { return seedDate; }
    public void touch(Instant now) { lastSeenAt = now; }
}
