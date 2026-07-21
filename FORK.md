# Fork-Hinweis

Dies ist ein Fork von
[GollmerSt/SolvisSmartHomeServer](https://github.com/GollmerSt/SolvisSmartHomeServer)
(MIT-Lizenz). Der Upstream ist unverändert das Original; dieser Fork sammelt
betriebsspezifische Anpassungen für die Einbindung in ein gehärtetes,
MQTT-über-mTLS-basiertes Smart-Home-Setup.

**Urheberschaft der Kernidee:** Der eigentliche Einfall, eine Solvis-Anlage
ohne native Schnittstelle über ihre **grafische Web-Oberfläche per OCR**
auszulesen und zu steuern, stammt vom ursprünglichen Autor **Stefan Gollmer
(GollmerSt)**. Dieser Fork übernimmt diesen Ansatz unverändert und trägt
lediglich Betriebs-, Wartungs- und Modernisierungsanpassungen bei — die
inhaltliche Leistung der OCR-basierten Anlagenanbindung gebührt dem Upstream.

## Motivation

Der SolvisSmartHomeServer bindet eine **SolvisMax mit SolvisControl 2 +
SolvisRemote** an (die Anlage ist zu alt für das Modbus-Interface, das Solvis
erst ab Reglerversion MA205 bzw. der SolvisControl 3 anbietet — für unsere
Anlage bleibt also nur der von Stefan Gollmer (GollmerSt) ersonnene Weg über
die grafische Web-Oberfläche mit OCR). Er ist damit die einzige praktikable
Anbindung dieser Anlage.

Er wird in einem privaten Smart-Home-Stack eingesetzt, dessen MQTT-Verkehr
ausschließlich über einen **mit gegenseitigem TLS (mTLS) abgesicherten
Mosquitto-Broker** läuft — kein Klartext-Listener, jeder Client mit eigenem
Zertifikat und Broker-ACL. In diese Umgebung passt der Upstream nicht ohne
Weiteres, und er wird zudem **seit Anfang 2023 nicht mehr gepflegt** (letztes
Release v01.05.01, Dezember 2021). Der Fork existiert, um eine wartbare Kopie
zu haben und die folgenden Anpassungen vorzunehmen.

### Geplante Änderungen

Die Punkte sind Vorhaben, nicht fertige Features; der Stand wird hier
fortgeschrieben.

1. **Betrieb hinter dem mTLS-Broker.** Der Upstream unterstützt für die
   MQTT-Anbindung ausdrücklich **nur unverschlüsseltes MQTT** (siehe Wiki
   „MQTT-Schnittstelle"). Im Zielsetup verbindet sich der Server daher nicht
   direkt mit dem Primär-Broker, sondern publiziert auf einen **lokalen,
   auf `127.0.0.1` gebundenen Mosquitto** auf demselben Host, der seinerseits
   per mTLS-Bridge zum Primär-Broker koppelt (dasselbe Muster, das im
   Gesamt-Stack bereits für die CCU-Jack- und die FHEM-Anbindung genutzt
   wird). In diesem Betriebsmodell ist TLS im Server selbst **nicht
   erforderlich**. Der Fork dokumentiert diese Betriebsart und stellt eine
   passende Beispiel-Konfiguration bereit; **nativer TLS-Support im
   MQTT-Client** wird als Option geprüft, ist aber nachrangig.

2. **Kompatibilität mit aktuellen Java-Laufzeiten.** Der Upstream ist gegen
   Java 8 gebaut (2021). Der Zielhost läuft auf **OpenJDK 21**. Geprüft und
   — soweit nötig — angepasst wird, dass Build und Laufzeit unter einer
   modernen JRE fehlerfrei durchlaufen.

3. **Headless-Raspberry-Deployment.** Konfiguration, Start-/Service-Dateien
   und Doku für einen unbeaufsichtigten Dauerbetrieb auf einem
   ARM-Raspberry ohne grafische Oberfläche (die OCR-Bildschirmerkennung
   arbeitet auf dem vom SolvisRemote gelieferten Web-GUI, nicht auf einem
   lokalen Display). Container-Betrieb: [docs/DOCKER.md](docs/DOCKER.md).

### Nachvollziehbarkeit

Ob das Produkt in einer fremden Umgebung wie beschrieben baut, startet,
Messwerte liefert, steuert und sich integrieren lässt, prüft der phasenweise
aufgebaute [Testplan](TESTPLAN.md) (frühe Phasen ohne Anlage, spätere mit
realer Solvis). Durchgeführte Anpassungen: [CHANGELOG-fork.md](CHANGELOG-fork.md).

### Beziehung zum Upstream

`master` folgt dem Upstream; betriebsspezifische Arbeit passiert auf
Feature-Branches, damit spätere Upstream-Stände sauber übernommen werden
können. Der Upstream-Remote ist als `upstream` eingerichtet
(`git fetch upstream`). Änderungen, die auch für andere nützlich sind, sollen
als Pull Request an den Upstream zurückfließen.
