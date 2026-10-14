# 0002 · Offline buchen: Outbox im Gerät, idempotenter Server

**Status:** angenommen

## Kontext
In Lagerhallen gibt es Funklöcher. Eine Buchung, die verloren geht oder doppelt ankommt, ist schlimmer als eine, die
eine Minute später ankommt.

## Entscheidung
- Jede Buchung bekommt **im Gerät** eine `clientId` (UUID) und landet zuerst in IndexedDB (`outbox`), dann wird gesendet.
- Gesendet wird nacheinander in Buchungsreihenfolge (`bookedAt`); bei Netzfehler bleibt der Rest liegen. Neuer Versuch bei
  `online`-Event und alle 15 s. Sendevorgänge laufen strikt nacheinander (Promise-Kette) – nie zwei parallel.
- Der Server ist **idempotent**: `clientId` ist je Lager eindeutig (Unique-Index). Ein zweites Senden liefert `200` mit der
  vorhandenen Buchung statt `201`.
- Lehnt der Server fachlich ab (`422`, z. B. „Auf A-01-01 liegen nur 5 Stk.“), wird die Buchung aus der Outbox entfernt
  und dem Nutzer mit Grund angezeigt – nachträglich abgelehnte bleiben sichtbar, bis er sie bestätigt.
- Die Gerätezeit wird als Buchungszeit übernommen, wenn sie höchstens 24 h von der Serverzeit abweicht.
- Offline lösen Scans über den zuletzt geladenen Katalog auf (localStorage); der Scanner sagt dann ausdrücklich,
  dass der Bestand veraltet sein kann. Der Service Worker liefert die App-Shell auch ohne Netz.

## Konsequenzen
+ Keine verlorenen oder doppelten Buchungen, auch bei Wiederholungen.
− Offline gebuchte Entnahmen können später abgelehnt werden (Bestand inzwischen weg) – das wird sichtbar gemeldet,
  nicht still verworfen.
