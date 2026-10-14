# 0004 · Scannen: Browser-Barcode-API, jsQR als Rückfall, Handscanner als Tastatur

**Status:** angenommen

## Kontext
Lager haben QR-Etiketten, EAN-Codes auf Herstellerverpackungen und oft schon Bluetooth-Handscanner.
Eine native App wäre teuer in Pflege und Verteilung.

## Entscheidung
- **Kamera:** `BarcodeDetector` des Browsers, wo vorhanden (Chrome auf Android: QR, EAN-13, Code 128); sonst **jsQR**
  (QR überall, auch iPhone). Dekodiert wird etwa alle 180 ms auf einem verkleinerten Bild; derselbe Code wird 2,5 s lang
  nicht erneut gemeldet.
- **Handscanner** tippen wie eine Tastatur und senden Enter – sie landen im selben Eingabefeld wie die Handeingabe.
- **Etiketten-Inhalt:** `BIN:A-01-01` für Lagerplätze, `ART:ET-1001` für Artikel; EAN-13 wird direkt erkannt.
- Ist ein Buchungsformular offen, füllt ein gescanntes Lagerplatz-Etikett das passende Feld statt den Platz zu öffnen –
  so geht eine Umlagerung komplett ohne Tippen.

## Konsequenzen
+ Eine Web-App für alle Geräte, kein App-Store.
− Kamera braucht HTTPS und eine Freigabe; ohne Kamera bleiben Handscanner und Eingabe.
