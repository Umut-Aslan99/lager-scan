package dev.umutaslan.lagerscan.workspace;

import dev.umutaslan.lagerscan.domain.Repositories.WorkspaceRepo;
import dev.umutaslan.lagerscan.domain.Workspace;
import dev.umutaslan.lagerscan.seed.DemoData;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Each visitor gets a private copy of the demo warehouse. The laptop and the phone share it through a
 * join token (shown as QR code). Copies are made inside PostgreSQL from a template that is re-seeded daily.
 */
@Service
public class WorkspaceService {
    public static final Duration IDLE_LIFETIME = Duration.ofHours(24);
    public static final int MAX_WORKSPACES = 100;
    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String[] COPY = {
        "insert into article (id, workspace_id, number, ean, name, article_group, unit, min_stock) "
            + "select gen_random_uuid(), ?, number, ean, name, article_group, unit, min_stock from article where workspace_id = ?",
        "insert into bin (id, workspace_id, code, zone) select gen_random_uuid(), ?, code, zone from bin where workspace_id = ?",
        "insert into stock (id, workspace_id, article_number, bin_code, quantity) "
            + "select gen_random_uuid(), ?, article_number, bin_code, quantity from stock where workspace_id = ?",
        "insert into booking (id, workspace_id, client_id, type, article_number, from_bin, to_bin, quantity, counted, difference, note, device, booked_at, created_at) "
            + "select gen_random_uuid(), ?, gen_random_uuid(), type, article_number, from_bin, to_bin, quantity, counted, difference, note, device, booked_at, created_at "
            + "from booking where workspace_id = ?",
    };

    private final WorkspaceRepo workspaces;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DemoData demo;
    private final Clock clock;
    private final ReentrantLock templateLock = new ReentrantLock();

    public WorkspaceService(WorkspaceRepo workspaces, JdbcTemplate jdbc, TransactionTemplate tx, DemoData demo, Clock clock) {
        this.workspaces = workspaces;
        this.jdbc = jdbc;
        this.tx = tx;
        this.demo = demo;
        this.clock = clock;
    }

    public void ensureTemplate() {
        templateLock.lock();
        try {
            var current = workspaces.findById(Workspace.TEMPLATE);
            if (current.isPresent() && current.get().getSeedDate().equals(clock.today())) return;
            tx.executeWithoutResult(s -> {
                current.ifPresent(w -> jdbc.update("delete from workspace where id = ?", Workspace.TEMPLATE));
                workspaces.saveAndFlush(new Workspace(Workspace.TEMPLATE, "template", clock.now(), clock.today()));
                demo.seed(Workspace.TEMPLATE);
            });
            log.info("Template warehouse seeded for {}", clock.today());
        } finally {
            templateLock.unlock();
        }
    }

    public Optional<Workspace> find(UUID id) {
        if (Workspace.TEMPLATE.equals(id)) return Optional.empty();
        var ws = workspaces.findById(id);
        ws.filter(w -> Duration.between(w.getLastSeenAt(), clock.now()).toMinutes() >= 5).ifPresent(w -> {
            w.touch(clock.now());
            workspaces.save(w);
        });
        return ws;
    }

    public Optional<Workspace> findByJoinToken(String token) {
        return workspaces.findByJoinToken(token).filter(w -> !Workspace.TEMPLATE.equals(w.getId()));
    }

    public Workspace create() {
        ensureTemplate();
        var bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        var token = HexFormat.of().formatHex(bytes);
        return tx.execute(s -> {
            var ws = workspaces.saveAndFlush(new Workspace(UUID.randomUUID(), token, clock.now(), clock.today()));
            copyTemplate(ws.getId());
            return ws;
        });
    }

    /** Back to the original demo warehouse; the join token stays, so a paired phone keeps working. */
    public void reset(UUID id) {
        ensureTemplate();
        tx.executeWithoutResult(s -> {
            for (var table : new String[] {"booking", "photo", "stock", "bin", "article"})
                jdbc.update("delete from " + table + " where workspace_id = ?", id);
            copyTemplate(id);
        });
    }

    private void copyTemplate(UUID target) {
        for (var sql : COPY) jdbc.update(sql, target, Workspace.TEMPLATE);
    }

    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT1M")
    public void housekeeping() {
        ensureTemplate();
        var removed = tx.execute(s -> workspaces.deleteIdle(Workspace.TEMPLATE, clock.now().minus(IDLE_LIFETIME)));
        var all = workspaces.oldestFirst(Workspace.TEMPLATE);
        if (all.size() > MAX_WORKSPACES) {
            var surplus = all.subList(0, all.size() - MAX_WORKSPACES);
            tx.executeWithoutResult(s -> workspaces.deleteAllByIdInBatch(surplus));
            removed = (removed == null ? 0 : removed) + surplus.size();
        }
        if (removed != null && removed > 0) log.info("Removed {} idle workspaces", removed);
    }
}
