// Guided tours of Lager-Scan – one for the control station (laptop), one for the scanner (phone).
// Texts here, mechanics in site-tour.js (shared by all demos of umut-aslan.dev).
import { startTours } from '/site-tour.js';

const onScanner = () => location.pathname.startsWith('/scan');

startTours([
  {
    id: 'lagerscan-scanner',
    when: onScanner,
    steps: [
      {
        title: 'Willkommen im Scanner',
        text: `Das Handy ersetzt den **PC auf dem Rollwagen**: Etikett scannen, Aktion wählen, buchen – direkt am Regal.

Ich zeige Ihnen kurz, wie es geht.`,
      },
      {
        target: '[data-tour="camera"]',
        title: 'Kamera als Barcode-Scanner',
        text: `„Kamera starten“ und ein Etikett in den Rahmen halten. **QR-Codes und EAN-Strichcodes** werden erkannt –
auf Android mit der eingebauten Erkennung des Browsers, auf dem iPhone mit einer mitgelieferten Bibliothek.`,
      },
      {
        target: '[data-tour="manual"]',
        title: 'Handscanner oder Eingabe',
        text: `Bluetooth-Handscanner tippen wie eine Tastatur – ihr Code landet hier. Ohne Kamera geht es auch von Hand,
zum Beispiel mit **ART:ET-1001** oder **BIN:WE-01**.`,
      },
      {
        target: '[data-tour="netpill"]',
        title: 'Kein Netz? Kein Problem',
        mood: 'curious',
        text: `In der hintersten Regalreihe gibt es oft kein WLAN. Buchungen werden dann **auf dem Gerät gespeichert** und
automatisch nachgesendet, sobald wieder Netz da ist – hier steht, wie viele noch warten.`,
      },
      {
        target: '[data-tour="hint"]',
        title: 'Etiketten zum Ausprobieren',
        text: `Unter „Etiketten“ gibt es QR-Codes für Lagerplätze und Artikel – ausgedruckt oder direkt vom Bildschirm des
Leitstands gescannt. Jede Buchung erscheint dort sofort.`,
      },
      {
        title: 'Warum das hilft',
        mood: 'celebrate',
        text: `- Buchen **dort, wo die Ware liegt** – kein Wagen, kein Abtippen
- funktioniert **auch ohne WLAN**
- der Leitstand sieht **jede Buchung live**

Gebaut mit Angular und Java Spring Boot. Über den runden Knopf unten links starten Sie diese Tour jederzeit neu.`,
        nextLabel: 'Los scannen',
      },
    ],
  },
  {
    id: 'lagerscan',
    when: () => !onScanner(),
    steps: [
      {
        title: 'Hallo! Kurz zum Problem',
        mood: 'worried',
        text: `Im Ersatzteillager vieler Maschinenbauer steht ein **PC auf einem Rollwagen**: Teil suchen, Wagen hinschieben,
Artikelnummer abtippen, Menge eintragen.

Langsam, fehleranfällig – und im WLAN-Funkloch zwischen den Regalen geht gar nichts.`,
        nextLabel: 'Zeig mir die Lösung',
      },
      {
        path: '/',
        target: '[data-tour="pair"]',
        title: 'Das Handy wird zum Scanner',
        text: `Diesen QR-Code mit der **Handykamera** scannen: Das Handy öffnet den Scanner in genau diesem Lager.
Keine App-Installation, kein Login.`,
      },
      {
        target: '[data-tour="tiles"]',
        title: 'Was heute passiert ist',
        text: `Buchungen und Inventuren des Tages, Artikel **unter Mindestbestand** und Ware, die im Wareneingang auf das
Einlagern wartet.`,
      },
      {
        target: '[data-tour="todo"]',
        title: 'Was zu tun ist',
        text: `Links die Ware im **Wareneingang**, rechts die Artikel, die **nachbestellt** werden müssen – ein Klick führt zum Artikel.`,
      },
      {
        target: '[data-tour="feed"]',
        title: 'Jede Buchung – sofort',
        text: `Bucht jemand am Handy, erscheint es hier **ohne Neuladen** (Server-Sent Events). Neue Zeilen leuchten kurz auf.`,
      },
      {
        target: '[data-tour="stock"]',
        title: 'Der Bestand',
        text: `Jeder Artikel mit Lagerplätzen und Mindestbestand – durchsuchbar nach Nummer, **EAN** oder Warengruppe.`,
      },
      {
        path: '/etiketten',
        target: '[data-tour="labels"]',
        title: 'Etiketten fürs Regal',
        text: `QR-Codes für **Lagerplätze und Artikel**: ausdrucken und ans Regal kleben – oder zum Ausprobieren direkt von
diesem Bildschirm mit dem verbundenen Handy scannen.`,
      },
      {
        target: '[data-tour="scanner-link"]',
        title: 'Der Scanner',
        text: `Auf dem Handy öffnet sich der Scanner. Dort zeige ich Ihnen den Rest – Kamera, Handscanner und Buchen ohne Netz.`,
      },
      {
        title: 'Warum das hilft',
        mood: 'celebrate',
        text: `- Buchen **dort, wo die Ware liegt** – kein Rollwagen, kein Abtippen
- funktioniert **auch ohne WLAN**, Buchungen werden nachgesendet
- der Leitstand sieht **alles live**

Gebaut mit Angular, Java Spring Boot und PostgreSQL, abgesichert durch automatisierte Tests – inklusive Kamera-Scan.
Tipp: Scannen Sie den QR-Code mit Ihrem Handy.`,
        nextLabel: 'Selbst ausprobieren',
      },
    ],
  },
]);
