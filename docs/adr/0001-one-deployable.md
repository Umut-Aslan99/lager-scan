# 0001 · Ein Container: Spring Boot liefert API, Live-Events und Angular-Build aus

**Status:** angenommen

## Kontext
Zwei Oberflächen (Scanner am Handy, Leitstand am Laptop), aber ein Client-Code, keine externen API-Konsumenten und keine
Benutzerkonten – der Cookie identifiziert nur das Demo-Lager. Der Server hat begrenzten Arbeitsspeicher.

## Entscheidung
Der Angular-Build wird beim Docker-Build nach `src/main/resources/static` kopiert. Ein Prozess, ein Origin: kein CORS,
Cookie mit `SameSite=Strict`, Service Worker und API unter derselben Adresse. Client-Routen (`/scan`, `/etiketten`,
`/artikel/**`) werden auf `index.html` weitergeleitet. Dateien mit Hash im Namen werden ein Jahr gecacht, alles andere
(`index.html`, `ngsw.json`, `ngsw-worker.js`) immer neu geprüft – sonst kämen Updates nie beim Handy an.

## Abgrenzung zu Einsatz (svelte.umut-aslan.dev)
Dort gibt es eine echte Anmeldung und zwei sehr unterschiedliche Clients – dort lohnt sich ein Backend-for-Frontend.

## Konsequenzen
+ Ein Image (JRE 25 Alpine), ein Deployment.
− Frontend und Backend werden gemeinsam ausgeliefert (für dieses Produkt gewollt).
