package dev.umutaslan.lagerscan.workspace;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** "Today" means today in the warehouse's time zone, not on the server. */
@Component
public class Clock {
    private final ZoneId zone;

    public Clock(@Value("${lagerscan.zone}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public Instant now() { return Instant.now(); }
    public LocalDate today() { return LocalDate.now(zone); }
    public Instant startOfToday() { return today().atStartOfDay(zone).toInstant(); }
    public ZoneId zone() { return zone; }
}
