package dev.umutaslan.lagerscan.events;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events per workspace: a booking made on the phone appears on the laptop's control
 * station immediately. A comment line every 20 s keeps proxies from closing idle connections.
 */
@Component
public class LiveEvents implements SmartLifecycle {
    private final Map<UUID, Set<SseEmitter>> listeners = new ConcurrentHashMap<>();
    private volatile boolean running;

    // Stops before the web server's graceful shutdown, which would otherwise wait for open event streams.
    @Override public void start() { running = true; }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE; }

    @Override
    public void stop() {
        running = false;
        listeners.values().forEach(set -> set.forEach(SseEmitter::complete));
        listeners.clear();
    }

    public SseEmitter subscribe(UUID workspace) {
        var emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        var set = listeners.computeIfAbsent(workspace, k -> ConcurrentHashMap.newKeySet());
        set.add(emitter);
        Runnable remove = () -> set.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().comment("connected").reconnectTime(3000));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    public void publish(UUID workspace, String name, Object data) {
        for (var emitter : listeners.getOrDefault(workspace, Set.of())) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
    }

    @Scheduled(fixedRate = 20_000)
    void heartbeat() {
        listeners.values().forEach(set -> set.forEach(emitter -> {
            try { emitter.send(SseEmitter.event().comment("ping")); }
            catch (IOException | IllegalStateException e) { emitter.completeWithError(e); }
        }));
    }

    public int listenerCount(UUID workspace) {
        return listeners.getOrDefault(workspace, Set.of()).size();
    }
}
