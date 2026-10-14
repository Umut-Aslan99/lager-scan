package dev.umutaslan.lagerscan.seed;

import dev.umutaslan.lagerscan.domain.Article;
import dev.umutaslan.lagerscan.domain.Bin;
import dev.umutaslan.lagerscan.domain.Booking;
import dev.umutaslan.lagerscan.domain.BookingType;
import dev.umutaslan.lagerscan.domain.Repositories.ArticleRepo;
import dev.umutaslan.lagerscan.domain.Repositories.BinRepo;
import dev.umutaslan.lagerscan.domain.Repositories.BookingRepo;
import dev.umutaslan.lagerscan.domain.Repositories.StockRepo;
import dev.umutaslan.lagerscan.domain.Stock;
import dev.umutaslan.lagerscan.workspace.Clock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A fictional spare-parts store of a machine builder: 36 articles, 30 bins in three racks plus a
 * receiving area, a few articles below minimum stock and goods waiting to be put away. Deterministic.
 */
@Component
public class DemoData {
    record Item(String name, String unit, int min) {}
    record Group(String name, String rack, Item[] items) {}

    static final Group[] GROUPS = {
        new Group("Lager & Dichtungen", "A", new Item[] {
            new Item("Kugellager 6205-2RS", "Stk", 10), new Item("Kugellager 6306-2RS", "Stk", 6), new Item("Wellendichtring 40×62×7", "Stk", 12),
            new Item("O-Ring-Sortiment", "Kasten", 2), new Item("Pendelrollenlager 22210", "Stk", 2), new Item("Stehlager UCP205", "Stk", 4)}),
        new Group("Antriebstechnik", "A", new Item[] {
            new Item("Keilriemen SPZ 1250", "Stk", 8), new Item("Zahnriemen HTD 8M-30", "Stk", 4), new Item("Klauenkupplung", "Stk", 3),
            new Item("Rollenkette 1/2\" (5 m)", "Rolle", 2), new Item("Kettenrad 1/2\" Z18", "Stk", 4), new Item("Getriebemotor 0,55 kW", "Stk", 1)}),
        new Group("Hydraulik", "B", new Item[] {
            new Item("Hydraulikschlauch DN10, 1 m", "Stk", 10), new Item("Filterelement 10 µm", "Stk", 6), new Item("Wegeventil 4/3", "Stk", 2),
            new Item("Manometer 0–250 bar", "Stk", 3), new Item("Dichtungssatz Zylinder Ø50", "Satz", 4), new Item("Schnellkupplung DN10", "Stk", 6)}),
        new Group("Elektrik", "B", new Item[] {
            new Item("Schütz 24 V DC", "Stk", 5), new Item("Motorschutzschalter 4–6 A", "Stk", 3), new Item("Feinsicherung 10 A träge", "Pkg", 5),
            new Item("Relais 24 V", "Stk", 10), new Item("Not-Aus-Taster", "Stk", 2), new Item("Netzteil 24 V 5 A", "Stk", 2)}),
        new Group("Sensorik", "C", new Item[] {
            new Item("Näherungsschalter M12 PNP", "Stk", 8), new Item("Lichtschranke", "Stk", 3), new Item("Drucksensor 0–10 bar", "Stk", 2),
            new Item("Endschalter", "Stk", 6), new Item("Drehgeber 1024 Imp.", "Stk", 2), new Item("Temperaturfühler PT100", "Stk", 3)}),
        new Group("Normteile", "C", new Item[] {
            new Item("Schraube M8×30 8.8 (100 St.)", "Pkg", 4), new Item("Mutter M8 (100 St.)", "Pkg", 4), new Item("Sicherungsring 25", "Pkg", 3),
            new Item("Kabelbinder 200 mm (100 St.)", "Pkg", 5), new Item("Zylinderstift 6×20", "Pkg", 2), new Item("Unterlegscheibe 8,4 (100 St.)", "Pkg", 4)}),
    };
    static final String[] RACK_ZONES = {"Regal A – Lager & Antrieb", "Regal B – Hydraulik & Elektrik", "Regal C – Sensorik & Normteile"};

    private final ArticleRepo articles;
    private final BinRepo bins;
    private final StockRepo stock;
    private final BookingRepo bookings;
    private final Clock clock;

    public DemoData(ArticleRepo articles, BinRepo bins, StockRepo stock, BookingRepo bookings, Clock clock) {
        this.articles = articles;
        this.bins = bins;
        this.stock = stock;
        this.bookings = bookings;
        this.clock = clock;
    }

    /** In-house EAN-13 (prefix 20–29 is reserved for internal numbering) with a valid check digit. */
    public static String ean(int n) {
        var body = String.format("20%010d", n);
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return body + (10 - sum % 10) % 10;
    }

    public void seed(UUID ws) {
        var rnd = new Random(2026);
        var binList = new ArrayList<Bin>();
        for (int r = 0; r < 3; r++) {
            var rack = (char) ('A' + r);
            for (int level = 1; level <= 3; level++)
                for (int slot = 1; slot <= (r == 0 ? 4 : 3); slot++)
                    binList.add(new Bin(ws, String.format("%c-%02d-%02d", rack, level, slot), RACK_ZONES[r]));
        }
        binList.add(new Bin(ws, "WE-01", "Wareneingang"));
        binList.add(new Bin(ws, "WE-02", "Wareneingang"));
        bins.saveAll(binList);

        var articleList = new ArrayList<Article>();
        var stockList = new ArrayList<Stock>();
        int n = 1001;
        for (var group : GROUPS) {
            var rackBins = binList.stream().filter(b -> b.getCode().startsWith(group.rack())).toList();
            for (var item : group.items()) {
                var number = "ET-" + n;
                articleList.add(new Article(ws, number, ean(n), item.name(), group.name(), item.unit(), BigDecimal.valueOf(item.min())));
                var primary = rackBins.get(rnd.nextInt(rackBins.size()));
                // Every sixth article runs below its minimum stock – the control station flags it.
                var qty = n % 6 == 0 ? rnd.nextInt(Math.max(1, item.min())) : item.min() + rnd.nextInt(item.min() * 3 + 5);
                stockList.add(new Stock(ws, number, primary.getCode(), BigDecimal.valueOf(qty)));
                if (rnd.nextDouble() < 0.3) {
                    var second = rackBins.get(rnd.nextInt(rackBins.size()));
                    if (!second.getCode().equals(primary.getCode()))
                        stockList.add(new Stock(ws, number, second.getCode(), BigDecimal.valueOf(1 + rnd.nextInt(6))));
                }
                n++;
            }
        }
        // Goods received this morning, still waiting in the receiving area to be put away.
        stockList.add(new Stock(ws, "ET-1001", "WE-01", BigDecimal.valueOf(20)));
        stockList.add(new Stock(ws, "ET-1025", "WE-01", BigDecimal.valueOf(12)));
        stockList.add(new Stock(ws, "ET-1014", "WE-02", BigDecimal.valueOf(5)));
        articles.saveAll(articleList);
        stock.saveAll(stockList);
        bookings.saveAll(history(ws, rnd, articleList, stockList));
    }

    /** Plausible activity of the last three days for the control station's feed. */
    private List<Booking> history(UUID ws, Random rnd, List<Article> articleList, List<Stock> stockList) {
        var list = new ArrayList<Booking>();
        var now = clock.now();
        String[] devices = {"Handy", "Handscanner", "Leitstand"};
        for (int i = 0; i < 28; i++) {
            var s = stockList.get(rnd.nextInt(stockList.size()));
            var at = now.minusSeconds(3600L * (2 + rnd.nextInt(70)));
            var device = devices[rnd.nextInt(devices.length)];
            var type = BookingType.values()[rnd.nextInt(4)];
            var qty = BigDecimal.valueOf(1 + rnd.nextInt(5));
            list.add(switch (type) {
                case RECEIPT -> new Booking(ws, UUID.randomUUID(), type, s.getArticleNumber(), null, s.getBinCode(), qty, null, null, "Lieferschein " + (40000 + rnd.nextInt(9999)), null, device, at, at);
                case ISSUE -> new Booking(ws, UUID.randomUUID(), type, s.getArticleNumber(), s.getBinCode(), null, qty, null, null, "Auftrag FA-" + (2600 + rnd.nextInt(400)), null, device, at, at);
                case TRANSFER -> new Booking(ws, UUID.randomUUID(), type, s.getArticleNumber(), "WE-01", s.getBinCode(), qty, null, null, null, null, device, at, at);
                case COUNT -> {
                    var diff = BigDecimal.valueOf(rnd.nextInt(3) - 1);
                    yield new Booking(ws, UUID.randomUUID(), type, s.getArticleNumber(), null, s.getBinCode(), null, s.getQuantity(), diff, "Stichprobeninventur", null, device, at, at);
                }
            });
        }
        return list;
    }
}
