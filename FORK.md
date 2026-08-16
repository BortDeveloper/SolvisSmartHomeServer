# Fork-Hinweis

> **Sprache:** Deutsch · **Status:** aktiv · **Zielgruppe:** technisch
> Interessierte, Programmierer · **Siehe auch:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
> (Gesamtsicht), [CHANGELOG-fork.md](CHANGELOG-fork.md) (Umsetzung)

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

### Fork-Ziele und Stand

Die drei Ziele, für die dieser Fork angelegt wurde, sind seit dem Cutover am
**2026-08-13 produktiv erfüllt**: Der Fork läuft nativ als systemd-Dienst auf
dem headless Raspberry Pi 4 `ransible` unter OpenJDK 21 und publiziert über
einen lokalen Mosquitto plus mTLS-Bridge ausschließlich `solvis/#`.

1. **Betrieb hinter dem mTLS-Broker** — **VOLLZOGEN 2026-08-13 (Variante A).**
   Der Upstream unterstützt für die MQTT-Anbindung ausdrücklich **nur
   unverschlüsseltes MQTT** (siehe Wiki „MQTT-Schnittstelle"). Der Server
   verbindet sich deshalb nicht direkt mit dem Primär-Broker, sondern
   publiziert auf einen **lokalen, auf `127.0.0.1` gebundenen Mosquitto** auf
   demselben Host, der seinerseits per mTLS-Bridge zum Primär-Broker koppelt —
   dasselbe Muster, das der Gesamt-Stack für CCU-Jack und die Feld-Gateways
   `mon-dg`/`eno-eg` nutzt. TLS im Server selbst ist in diesem Betriebsmodell
   **nicht erforderlich**; der native `<Ssl>`-Pfad (Variante B) ist inzwischen
   ausdrücklich deprecated und bricht per Fail-Fast-Guard ab
   ([docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) §4). Die Bridge-Gegenseite
   gehört zum Repo `ccu2mqtt` (as-built:
   `docs/runbooks/solvis-bridge-ransible.md`).

2. **Kompatibilität mit aktuellen Java-Laufzeiten** — **VOLLZOGEN 2026-08-13.**
   Der Upstream ist gegen Java 8 gebaut (2021); Build und Laufzeit dieses Forks
   sind auf Java 17 als Baseline gehoben, die CI testet 17 und 21, und der
   Produktivbetrieb läuft unter **OpenJDK 21**. Einziger Gerätezwang dabei:
   Die SolvisRemote beherrscht Digest-Auth nur mit MD5, weshalb die Units
   `-Dhttp.auth.digest.reEnabledAlgorithms=MD5` setzen.

3. **Headless-Raspberry-Deployment** — **VOLLZOGEN 2026-08-13.**
   Konfiguration, Start-/Service-Dateien und Doku tragen den unbeaufsichtigten
   Dauerbetrieb auf einem ARM-Raspberry ohne grafische Oberfläche (die
   OCR-Bildschirmerkennung arbeitet auf dem vom SolvisRemote gelieferten
   Web-GUI, nicht auf einem lokalen Display). Produktivweg ist der native
   systemd-Dienst ([docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md),
   [docs/runbooks/README.md](docs/runbooks/README.md)); der Container-Betrieb
   ([docs/DOCKER.md](docs/DOCKER.md)) bleibt Alternative für Entwicklung und
   Test.

Was danach noch offen ist, steht nicht mehr hier, sondern im Fahrplan
[MODERNISIERUNG.md](MODERNISIERUNG.md) und im
[Testplan](TESTPLAN.md) (Langzeitbeweis Phase 8, Steuerpfade Phase 6).

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
