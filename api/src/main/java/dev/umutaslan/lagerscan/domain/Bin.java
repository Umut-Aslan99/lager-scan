package dev.umutaslan.lagerscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** A storage location (Lagerplatz), e.g. "A-02-03" = rack A, level 2, compartment 3. */
@Entity
public class Bin {
    @Id private UUID id;
    @Column(name = "workspace_id") private UUID workspaceId;
    private String code;
    private String zone;

    protected Bin() {}

    public Bin(UUID workspaceId, String code, String zone) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.code = code;
        this.zone = zone;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getZone() { return zone; }
}
