package dev.umutaslan.lagerscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Photo {
    @Id private UUID id;
    @Column(name = "workspace_id") private UUID workspaceId;
    @Column(name = "article_number") private String articleNumber;
    @Column(name = "content_type") private String contentType;
    private byte[] data;
    @Column(name = "created_at") private Instant createdAt;

    protected Photo() {}

    public Photo(UUID workspaceId, String articleNumber, String contentType, byte[] data, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.articleNumber = articleNumber;
        this.contentType = contentType;
        this.data = data;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getArticleNumber() { return articleNumber; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
}
