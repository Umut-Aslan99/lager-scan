package dev.umutaslan.lagerscan.booking;

import dev.umutaslan.lagerscan.domain.Article;
import dev.umutaslan.lagerscan.domain.Booking;
import dev.umutaslan.lagerscan.domain.BookingType;
import dev.umutaslan.lagerscan.domain.DomainException;
import dev.umutaslan.lagerscan.domain.Repositories.ArticleRepo;
import dev.umutaslan.lagerscan.domain.Repositories.BinRepo;
import dev.umutaslan.lagerscan.domain.Repositories.BookingRepo;
import dev.umutaslan.lagerscan.domain.Repositories.PhotoRepo;
import dev.umutaslan.lagerscan.domain.Repositories.StockRepo;
import dev.umutaslan.lagerscan.domain.Stock;
import dev.umutaslan.lagerscan.events.LiveEvents;
import dev.umutaslan.lagerscan.web.Dtos.BookingView;
import dev.umutaslan.lagerscan.workspace.Clock;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Applies one booking. Idempotent by the device-generated client id, so an offline queue can resend
 * safely. Stock rows are locked while they change; the live feed is notified only after the commit.
 */
@Service
public class BookingService {
    public record Request(UUID clientId, BookingType type, String articleNumber, String fromBin, String toBin,
                          BigDecimal quantity, BigDecimal counted, String note, UUID photoId, String device, Instant bookedAt) {}

    public record Result(boolean duplicate, BookingView booking) {}

    private final ArticleRepo articles;
    private final BinRepo bins;
    private final StockRepo stock;
    private final BookingRepo bookings;
    private final PhotoRepo photos;
    private final LiveEvents events;
    private final Clock clock;

    public BookingService(ArticleRepo articles, BinRepo bins, StockRepo stock, BookingRepo bookings, PhotoRepo photos, LiveEvents events, Clock clock) {
        this.articles = articles;
        this.bins = bins;
        this.stock = stock;
        this.bookings = bookings;
        this.photos = photos;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Result book(UUID ws, Request r) {
        if (r.clientId() == null || r.type() == null) throw new DomainException("Buchung unvollständig.");
        var existing = bookings.findByWorkspaceIdAndClientId(ws, r.clientId());
        if (existing.isPresent()) return new Result(true, BookingView.of(existing.get(), name(ws, existing.get().getArticleNumber())));

        var article = articles.findByWorkspaceIdAndNumberIgnoreCase(ws, trim(r.articleNumber()))
            .orElseThrow(() -> new DomainException("Artikel " + r.articleNumber() + " ist unbekannt."));
        var number = article.getNumber();
        String from = null, to = null;
        BigDecimal qty = null, counted = null, difference = null;

        switch (r.type()) {
            case RECEIPT -> {
                to = bin(ws, r.toBin());
                qty = positive(r.quantity());
                locked(ws, number, to).add(qty);
            }
            case ISSUE -> {
                from = bin(ws, r.fromBin());
                qty = positive(r.quantity());
                existingStock(ws, article, from).take(qty, article.getUnit());
            }
            case TRANSFER -> {
                from = bin(ws, r.fromBin());
                to = bin(ws, r.toBin());
                if (from.equals(to)) throw new DomainException("Quelle und Ziel sind derselbe Lagerplatz.");
                qty = positive(r.quantity());
                existingStock(ws, article, from).take(qty, article.getUnit());
                locked(ws, number, to).add(qty);
            }
            case COUNT -> {
                to = bin(ws, r.toBin() != null ? r.toBin() : r.fromBin());
                if (r.counted() == null || r.counted().signum() < 0) throw new DomainException("Bitte die gezählte Menge eingeben (0 oder mehr).");
                counted = r.counted();
                difference = locked(ws, number, to).count(counted);
            }
        }

        if (r.photoId() != null) {
            var photo = photos.findByIdAndWorkspaceId(r.photoId(), ws).orElseThrow(() -> new DomainException("Foto nicht gefunden."));
            if (article.getPhotoId() == null && photo.getArticleNumber() != null) article.setPhotoId(photo.getId());
        }

        // A device clock may be wrong; trust it only within a day, otherwise use server time.
        var now = clock.now();
        var at = r.bookedAt() != null && Duration.between(r.bookedAt(), now).abs().toHours() < 24 ? r.bookedAt() : now;
        var saved = bookings.save(new Booking(ws, r.clientId(), r.type(), number, from, to, qty, counted, difference,
            blankToNull(r.note()), r.photoId(), device(r.device()), at, now));
        var view = BookingView.of(saved, article.getName());
        afterCommit(() -> events.publish(ws, "booking", view));
        return new Result(false, view);
    }

    private Stock locked(UUID ws, String article, String bin) {
        return stock.findByWorkspaceIdAndArticleNumberAndBinCode(ws, article, bin)
            .orElseGet(() -> stock.save(new Stock(ws, article, bin, BigDecimal.ZERO)));
    }

    private Stock existingStock(UUID ws, Article article, String bin) {
        return stock.findByWorkspaceIdAndArticleNumberAndBinCode(ws, article.getNumber(), bin)
            .orElseThrow(() -> new DomainException(article.getName() + " liegt nicht auf " + bin + "."));
    }

    private String bin(UUID ws, String code) {
        if (code == null || code.isBlank()) throw new DomainException("Bitte einen Lagerplatz scannen.");
        return bins.findByWorkspaceIdAndCodeIgnoreCase(ws, trim(code)).orElseThrow(() -> new DomainException("Lagerplatz " + code + " ist unbekannt.")).getCode();
    }

    private static BigDecimal positive(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) throw new DomainException("Die Menge muss größer als 0 sein.");
        if (qty.compareTo(BigDecimal.valueOf(100_000)) > 0) throw new DomainException("Die Menge ist unrealistisch groß.");
        return qty;
    }

    private String name(UUID ws, String number) {
        return articles.findByWorkspaceIdAndNumberIgnoreCase(ws, number).map(Article::getName).orElse(number);
    }

    private static String device(String d) {
        return d == null || d.isBlank() ? "Unbekannt" : d.length() > 16 ? d.substring(0, 16) : d;
    }

    private static String trim(String s) { return s == null ? null : s.trim(); }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.length() > 500 ? s.substring(0, 500) : s.trim();
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        else action.run();
    }
}
