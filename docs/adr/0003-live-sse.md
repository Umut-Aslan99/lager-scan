# 0003 · Live-Updates per Server-Sent Events statt WebSockets

**Status:** angenommen

## Kontext
Der Leitstand soll Buchungen vom Handy sofort sehen. Die Daten fließen nur in eine Richtung (Server → Leitstand);
gebucht wird ohnehin per REST.

## Entscheidung
`GET /api/events` als Server-Sent-Events-Stream je Lager (`SseEmitter`). Ein Ereignis wird erst **nach dem Commit**
gesendet (`TransactionSynchronization.afterCommit`) – der Leitstand zeigt nie etwas, das zurückgerollt wurde.
Herzschlag alle 20 s, damit Proxys die Verbindung nicht schließen. Beim Herunterfahren werden alle Streams beendet
(`SmartLifecycle`), sonst wartet der Graceful Shutdown auf offene Verbindungen.

## Warum nicht WebSockets
Eine Richtung reicht. SSE ist normales HTTP (läuft durch Traefik ohne Sonderkonfiguration), der Browser verbindet sich
selbst neu, und mit virtuellen Threads kostet eine offene Verbindung praktisch nichts.

## Konsequenzen
+ Wenig Code, keine zusätzliche Infrastruktur.
− Bei mehreren Server-Instanzen bräuchte es einen Verteiler (z. B. PostgreSQL `LISTEN/NOTIFY`) – für eine Instanz unnötig.
