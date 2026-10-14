package dev.umutaslan.lagerscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Booking {
    @Id private UUID id;
    @Column(name = "workspace_id") private UUID workspaceId;
    @Column(name = "client_id") private UUID clientId;
    @Enumerated(EnumType.STRING) private BookingType type;
    @Column(name = "article_number") private String articleNumber;
    @Column(name = "from_bin") private String fromBin;
    @Column(name = "to_bin") private String toBin;
    private BigDecimal quantity;
    private BigDecimal counted;
    private BigDecimal difference;
    private String note;
    @Column(name = "photo_id") private UUID photoId;
    private String device;
    @Column(name = "booked_at") private Instant bookedAt;
    @Column(name = "created_at") private Instant createdAt;

    protected Booking() {}

    public Booking(UUID workspaceId, UUID clientId, BookingType type, String articleNumber, String fromBin, String toBin,
                   BigDecimal quantity, BigDecimal counted, BigDecimal difference, String note, UUID photoId,
                   String device, Instant bookedAt, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.clientId = clientId;
        this.type = type;
        this.articleNumber = articleNumber;
        this.fromBin = fromBin;
        this.toBin = toBin;
        this.quantity = quantity;
        this.counted = counted;
        this.difference = difference;
        this.note = note;
        this.photoId = photoId;
        this.device = device;
        this.bookedAt = bookedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public BookingType getType() { return type; }
    public String getArticleNumber() { return articleNumber; }
    public String getFromBin() { return fromBin; }
    public String getToBin() { return toBin; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getCounted() { return counted; }
    public BigDecimal getDifference() { return difference; }
    public String getNote() { return note; }
    public UUID getPhotoId() { return photoId; }
    public String getDevice() { return device; }
    public Instant getBookedAt() { return bookedAt; }
}
