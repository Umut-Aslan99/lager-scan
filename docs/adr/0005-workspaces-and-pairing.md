# 0005 · Ein Lager je Besucher, Handy per QR-Code koppeln

**Status:** angenommen

## Kontext
Jeder Besucher soll frei buchen können, ohne andere zu stören, und Laptop und Handy sollen dasselbe Lager sehen –
ohne Benutzerkonto.

## Entscheidung
- Beim ersten API-Aufruf ohne Cookie wird ein Lager angelegt: Kopie einer Vorlage per `INSERT … SELECT` in PostgreSQL,
  Cookie `ls_ws` (HttpOnly, `SameSite=Strict`, 7 Tage). 24 h ohne Nutzung → gelöscht, höchstens 100 Lager.
- **Erster Besuch:** Die App holt `/api/workspace` einmal, *bevor* Seiten mehrere Anfragen parallel stellen. Sonst legt jede
  cookie-lose Anfrage ein eigenes Lager an, und der QR-Code gehört zu einem anderen Lager als der Cookie
  (vom End-to-End-Test gefunden; derselbe Fehler steckte im ERP-Cockpit und wurde dort auch behoben).
- **Pairing:** Der Leitstand zeigt `/join/{token}` als QR-Code. Das Token ist zufällig (96 Bit) und nicht die Lager-ID;
  der Aufruf setzt den Cookie des Lagers und leitet zum Scanner weiter.
- **Gleichzeitige Buchungen** auf denselben Bestand sperren die Bestandszeile (`PESSIMISTIC_WRITE`) – zwei Handys können
  nicht gemeinsam mehr entnehmen, als da ist.

## Konsequenzen
+ Kein Login, trotzdem getrennte Daten je Besucher.
− Wer das Token kennt, kann im Demo-Lager buchen – für Demo-Daten ausreichend, für echte Daten bräuchte es eine Anmeldung.
