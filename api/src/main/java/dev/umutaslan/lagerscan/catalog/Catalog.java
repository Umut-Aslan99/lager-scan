package dev.umutaslan.lagerscan.catalog;

import dev.umutaslan.lagerscan.domain.Article;
import dev.umutaslan.lagerscan.domain.Repositories.ArticleRepo;
import dev.umutaslan.lagerscan.domain.Repositories.BinRepo;
import dev.umutaslan.lagerscan.domain.Repositories.BookingRepo;
import dev.umutaslan.lagerscan.domain.Repositories.StockRepo;
import dev.umutaslan.lagerscan.domain.Stock;
import dev.umutaslan.lagerscan.web.Dtos.*;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side: what a scan resolves to, article and bin views, the control-station dashboard. */
@Service
@Transactional(readOnly = true)
public class Catalog {
    private static final Pattern EAN = Pattern.compile("\\d{13}");

    private final ArticleRepo articles;
    private final BinRepo bins;
    private final StockRepo stock;
    private final BookingRepo bookings;

    public Catalog(ArticleRepo articles, BinRepo bins, StockRepo stock, BookingRepo bookings) {
        this.articles = articles;
        this.bins = bins;
        this.stock = stock;
        this.bookings = bookings;
    }

    /**
     * Label payloads are "ART:ET-1001" and "BIN:A-01-02"; hand scanners on product packaging deliver
     * a plain EAN; manual input may be either number. Bins win over articles for bare codes.
     */
    public Optional<ScanResult> resolve(UUID ws, String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        var code = raw.trim();
        var upper = code.toUpperCase();
        if (upper.startsWith("ART:")) return article(ws, code.substring(4)).map(a -> new ScanResult("article", a, null));
        if (upper.startsWith("BIN:")) return bin(ws, code.substring(4)).map(b -> new ScanResult("bin", null, b));
        if (EAN.matcher(code).matches())
            return articles.findByWorkspaceIdAndEan(ws, code).flatMap(a -> article(ws, a.getNumber())).map(a -> new ScanResult("article", a, null));
        var bin = bin(ws, code);
        if (bin.isPresent()) return Optional.of(new ScanResult("bin", null, bin.get()));
        return article(ws, code).map(a -> new ScanResult("article", a, null));
    }

    public Optional<ArticleView> article(UUID ws, String number) {
        return articles.findByWorkspaceIdAndNumberIgnoreCase(ws, number.trim()).map(a -> {
            var lines = stock.findByWorkspaceIdAndArticleNumberOrderByBinCode(ws, a.getNumber());
            var recent = bookings.findByWorkspaceIdAndArticleNumberOrderByBookedAtDesc(ws, a.getNumber(), Limit.of(10))
                .stream().map(b -> BookingView.of(b, a.getName())).toList();
            return new ArticleView(summary(a, lines), lines.stream().filter(s -> s.getQuantity().signum() > 0).map(s -> line(a, s)).toList(), recent);
        });
    }

    public Optional<BinView> bin(UUID ws, String code) {
        return bins.findByWorkspaceIdAndCodeIgnoreCase(ws, code.trim()).map(b -> {
            var byNumber = articlesByNumber(ws);
            var contents = stock.findByWorkspaceIdAndBinCodeOrderByArticleNumber(ws, b.getCode()).stream()
                .filter(s -> s.getQuantity().signum() > 0)
                .map(s -> line(byNumber.get(s.getArticleNumber()), s)).toList();
            return new BinView(b.getCode(), b.getZone(), contents);
        });
    }

    public List<ArticleSummary> articleList(UUID ws, String query) {
        var lines = stock.findByWorkspaceId(ws).stream().collect(Collectors.groupingBy(Stock::getArticleNumber));
        var q = query == null ? "" : query.trim().toLowerCase();
        return articles.findByWorkspaceIdOrderByNumber(ws).stream()
            .filter(a -> q.isEmpty() || (a.getNumber() + " " + a.getName() + " " + a.getGroup() + " " + a.getEan()).toLowerCase().contains(q))
            .map(a -> summary(a, lines.getOrDefault(a.getNumber(), List.of()))).toList();
    }

    public List<BinView> binList(UUID ws) {
        var byNumber = articlesByNumber(ws);
        var byBin = stock.findByWorkspaceId(ws).stream().filter(s -> s.getQuantity().signum() > 0).collect(Collectors.groupingBy(Stock::getBinCode));
        return bins.findByWorkspaceIdOrderByCode(ws).stream().map(b -> new BinView(b.getCode(), b.getZone(),
            byBin.getOrDefault(b.getCode(), List.of()).stream().map(s -> line(byNumber.get(s.getArticleNumber()), s))
                .sorted(Comparator.comparing(StockLine::articleNumber)).toList())).toList();
    }

    public Dashboard dashboard(UUID ws, java.time.Instant startOfToday, int connectedDevices) {
        var list = articleList(ws, null);
        var byNumber = articlesByNumber(ws);
        var today = bookings.findByWorkspaceIdAndBookedAtAfter(ws, startOfToday);
        java.util.function.Function<String, Integer> countType = t -> (int) today.stream().filter(b -> b.getType().name().equals(t)).count();
        var receiving = stock.findByWorkspaceId(ws).stream()
            .filter(s -> s.getBinCode().startsWith("WE-") && s.getQuantity().signum() > 0)
            .map(s -> line(byNumber.get(s.getArticleNumber()), s)).sorted(Comparator.comparing(StockLine::binCode)).toList();
        var recent = bookings.findByWorkspaceIdOrderByBookedAtDesc(ws, Limit.of(25)).stream()
            .map(b -> BookingView.of(b, Optional.ofNullable(byNumber.get(b.getArticleNumber())).map(Article::getName).orElse(b.getArticleNumber()))).toList();
        return new Dashboard(list.size(), bins.findByWorkspaceIdOrderByCode(ws).size(), today.size(),
            countType.apply("RECEIPT"), countType.apply("ISSUE"), countType.apply("TRANSFER"), countType.apply("COUNT"),
            (int) today.stream().filter(b -> b.getDifference() != null && b.getDifference().signum() != 0).count(),
            list.stream().filter(ArticleSummary::belowMin).toList(), receiving, recent, connectedDevices);
    }

    public Labels labels(UUID ws) {
        return new Labels(
            articles.findByWorkspaceIdOrderByNumber(ws).stream().map(a -> new ArticleLabel(a.getNumber(), a.getName(), a.getEan(), "ART:" + a.getNumber())).toList(),
            bins.findByWorkspaceIdOrderByCode(ws).stream().map(b -> new BinLabel(b.getCode(), b.getZone(), "BIN:" + b.getCode())).toList());
    }

    private Map<String, Article> articlesByNumber(UUID ws) {
        return articles.findByWorkspaceIdOrderByNumber(ws).stream().collect(Collectors.toMap(Article::getNumber, Function.identity()));
    }

    private static ArticleSummary summary(Article a, List<Stock> lines) {
        var total = lines.stream().map(Stock::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        var binCodes = lines.stream().filter(s -> s.getQuantity().signum() > 0).map(Stock::getBinCode).sorted().toList();
        return new ArticleSummary(a.getNumber(), a.getEan(), a.getName(), a.getGroup(), a.getUnit(), total, a.getMinStock(),
            total.compareTo(a.getMinStock()) < 0, binCodes, a.getPhotoId() == null ? null : "/api/photos/" + a.getPhotoId());
    }

    private static StockLine line(Article a, Stock s) {
        return new StockLine(s.getArticleNumber(), a == null ? s.getArticleNumber() : a.getName(), a == null ? "" : a.getUnit(), s.getBinCode(), s.getQuantity());
    }
}
