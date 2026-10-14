package dev.umutaslan.lagerscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

/** Quantity of one article in one bin. Optimistic locking guards against two devices booking at once. */
@Entity
public class Stock {
    @Id private UUID id;
    @Column(name = "workspace_id") private UUID workspaceId;
    @Column(name = "article_number") private String articleNumber;
    @Column(name = "bin_code") private String binCode;
    private BigDecimal quantity;
    @Version private long version;

    protected Stock() {}

    public Stock(UUID workspaceId, String articleNumber, String binCode, BigDecimal quantity) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.articleNumber = articleNumber;
        this.binCode = binCode;
        this.quantity = quantity;
    }

    public void add(BigDecimal qty) {
        quantity = quantity.add(qty);
    }

    public void take(BigDecimal qty, String unit) {
        if (qty.compareTo(quantity) > 0) {
            throw new DomainException("Auf " + binCode + " liegen nur " + quantity.stripTrailingZeros().toPlainString() + " " + unit + ".");
        }
        quantity = quantity.subtract(qty);
    }

    /** Stocktaking: the counted quantity becomes the truth; returns counted − expected. */
    public BigDecimal count(BigDecimal counted) {
        var difference = counted.subtract(quantity);
        quantity = counted;
        return difference;
    }

    public UUID getId() { return id; }
    public String getArticleNumber() { return articleNumber; }
    public String getBinCode() { return binCode; }
    public BigDecimal getQuantity() { return quantity; }
}
