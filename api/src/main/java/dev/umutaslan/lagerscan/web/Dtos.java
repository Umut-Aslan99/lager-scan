package dev.umutaslan.lagerscan.web;

import dev.umutaslan.lagerscan.domain.Booking;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models for the Angular app. */
public final class Dtos {
    private Dtos() {}

    public record BookingView(UUID id, UUID clientId, String type, String typeLabel, String articleNumber, String articleName,
                              String fromBin, String toBin, BigDecimal quantity, BigDecimal counted, BigDecimal difference,
                              String note, String photoUrl, String device, Instant bookedAt) {
        public static BookingView of(Booking b, String articleName) {
            var label = switch (b.getType()) {
                case RECEIPT -> "Wareneingang";
                case ISSUE -> "Entnahme";
                case TRANSFER -> "Umlagerung";
                case COUNT -> "Inventur";
            };
            return new BookingView(b.getId(), b.getClientId(), b.getType().name(), label, b.getArticleNumber(), articleName,
                b.getFromBin(), b.getToBin(), b.getQuantity(), b.getCounted(), b.getDifference(), b.getNote(),
                b.getPhotoId() == null ? null : "/api/photos/" + b.getPhotoId(), b.getDevice(), b.getBookedAt());
        }
    }

    public record StockLine(String articleNumber, String articleName, String unit, String binCode, BigDecimal quantity) {}

    public record ArticleSummary(String number, String ean, String name, String group, String unit, BigDecimal total,
                                 BigDecimal minStock, boolean belowMin, List<String> bins, String photoUrl) {}

    public record ArticleView(ArticleSummary article, List<StockLine> stock, List<BookingView> recent) {}

    public record BinView(String code, String zone, List<StockLine> contents) {}

    public record ScanResult(String kind, ArticleView article, BinView bin) {}

    public record Dashboard(int articles, int bins, int bookingsToday, int receiptsToday, int issuesToday, int transfersToday,
                            int countsToday, int countDifferencesToday, List<ArticleSummary> belowMin, List<StockLine> receiving,
                            List<BookingView> recent, int connectedDevices) {}

    public record WorkspaceView(String joinUrl, Instant createdAt, long expiresAfterIdleHours) {}

    public record ArticleLabel(String number, String name, String ean, String payload) {}

    public record BinLabel(String code, String zone, String payload) {}

    public record Labels(List<ArticleLabel> articles, List<BinLabel> bins) {}
}
