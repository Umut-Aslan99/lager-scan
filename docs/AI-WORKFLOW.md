# Wie KI in diesem Projekt eingesetzt wurde

Gebaut mit einem KI-Coding-Agenten (Claude Code). Vorgegeben waren Problem („PC auf Rollwagen“ im Ersatzteillager),
Zielgruppe (Maschinenbau-Mittelstand), Stack (Angular + Java Spring Boot + PostgreSQL) und Gestaltung (wie umut-aslan.dev).

Arbeitsweise: Domänenregeln zuerst → API mit Integrationstests gegen echte PostgreSQL → Oberfläche mit Unit-Tests →
End-to-End-Tests im Browser (inklusive künstlicher Kamera und Offline-Modus) → Auslieferung erst, wenn alles grün ist.

**Was die Tests gefunden haben:**
- **Pairing kaputt:** Beim ersten Besuch stellte der Leitstand mehrere Anfragen gleichzeitig, jede ohne Cookie – jede legte
  ein eigenes Lager an. Der QR-Code zeigte auf ein anderes Lager als das im Browser. Gefunden vom End-to-End-Test
  „Handy koppeln“. Derselbe Fehler steckte im ERP-Cockpit (2–4 Arbeitsbereiche je Besuch in Produktion) und wurde dort
  mit einem eigenen Regressionstest behoben.
- **Falsche Meldung „offline gespeichert“:** Ein Hintergrund-Sendevorgang konnte die Buchung abschicken, während das
  Formular noch auf sie wartete. Gefunden vom Vitest-Test der Outbox; Ergebnisse werden jetzt je `clientId` zugeordnet.
- **Vorausgewählter Lagerplatz unsichtbar:** Angular setzte den Wert des `<select>`, bevor die Optionen existierten.
  Intern war der Platz gewählt, angezeigt wurde „– wählen –“. Gefunden vom End-to-End-Test.
- **Server hing beim Herunterfahren:** Der Graceful Shutdown wartete auf offene Live-Verbindungen. Die Live-Events beenden
  sich jetzt selbst beim Stoppen.
- 12 px Querscrollen auf 360 px breiten Handys – gefunden vom Layout-Test.

Strenge Werkzeuge: TypeScript strict, Angular strict templates, Flyway-Migrationen mit `ddl-auto=validate`.
Jede Änderung wird gelesen und verstanden, bevor sie bleibt.
