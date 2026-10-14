package dev.umutaslan.lagerscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
public class Article {
    @Id private UUID id;
    @Column(name = "workspace_id") private UUID workspaceId;
    private String number;
    private String ean;
    private String name;
    @Column(name = "article_group") private String group;
    private String unit;
    @Column(name = "min_stock") private BigDecimal minStock;
    @Column(name = "photo_id") private UUID photoId;

    protected Article() {}

    public Article(UUID workspaceId, String number, String ean, String name, String group, String unit, BigDecimal minStock) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.number = number;
        this.ean = ean;
        this.name = name;
        this.group = group;
        this.unit = unit;
        this.minStock = minStock;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getNumber() { return number; }
    public String getEan() { return ean; }
    public String getName() { return name; }
    public String getGroup() { return group; }
    public String getUnit() { return unit; }
    public BigDecimal getMinStock() { return minStock; }
    public UUID getPhotoId() { return photoId; }
    public void setPhotoId(UUID photoId) { this.photoId = photoId; }
}
