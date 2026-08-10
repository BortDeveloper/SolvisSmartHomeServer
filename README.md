# SolvisSmartHomeServer

> **Sprache:** Deutsch · **Status:** Fork, in Modernisierung
> ([MODERNISIERUNG.md](MODERNISIERUNG.md)) · **Build:** `./mvnw -B clean package`
> (Maven-Wrapper, JDK 17+) → `target/SolvisSmartHomeServer.jar`

Bindet eine **SolvisMax 6/7 mit SolvisControl 2 + SolvisRemote** ohne native
API über deren grafische Web-Oberfläche (OCR + simulierte Klicks) an
Smart-Home-Systeme an — lesend (Monitoring) und schreibend (Steuerung).

> **Fork-Hinweis:** Dies ist ein Fork von
> [GollmerSt/SolvisSmartHomeServer](https://github.com/GollmerSt/SolvisSmartHomeServer)
> mit betriebsspezifischen Anpassungen für ein mTLS-gehärtetes Smart-Home-Setup.
> Grund und Fahrplan: [FORK.md](FORK.md). Urheberschaft der OCR-Kernidee: Stefan
> Gollmer (GollmerSt) — Attribution bewusst erhalten.

## Wegweiser (drei Zielgruppen)

| Zielgruppe | Interesse | Einstiegsfrage | Primär-Dokument |
|---|---|---|---|
| Technisch Interessierte | Überblick | Was tut das Projekt, warum? | Dieses README (Abschnitte [Überblick](#überblick), [Features](#features)) |
| Operateure | Betrieb | Wie nehme ich es in Betrieb? | [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md) (Schritt für Schritt) |
| Programmierer | Erweiterung | Wie ist es gebaut? | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [MODERNISIERUNG.md](MODERNISIERUNG.md) |

### Ich möchte …

| Ich möchte … | Dokument |
|---|---|
| in Minuten bauen und smoke-testen | [Quick Start](#quick-start) |
| als Container produktiv aufsetzen | [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md) |
| die Architektur / das Betriebsmodell verstehen | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| prüfen, ob es in meiner Umgebung läuft | [TESTPLAN.md](TESTPLAN.md) |
| wissen, warum dieser Fork existiert | [FORK.md](FORK.md) |
| ein Problem im Betrieb einordnen | [Troubleshooting](docs/INBETRIEBNAHME.md#troubleshooting) |
| eine externe KI-Zweitmeinung einholen | [Externe KI-Analysen](#externe-ki-analysen) |

## Überblick

Der SolvisSmartHomeServer bindet die SolvisMax 6 und 7 mit SolvisControl 2 in
Kombination mit der SolvisRemote an Smart-Home-Systeme wie FHEM, ioBroker,
OpenHAB oder Indigo an. Er ist ein eigenständiges Java-Programm, das als
Service/Daemon im Hintergrund läuft.

Er eignet sich **nicht** für die neueren Anlagen SolvisMax 7 und SolvisBen mit
SolvisControl 3. Dort ist für die Smart-Home-Anbindung das Modbus-Interface zu
nutzen.

Ziel ist nicht nur das Monitoring der Anlage, sondern auch das Einstellen der
wichtigsten Anlagenparameter wie Soll-Temperaturen, Raumabhängigkeit und
Anlagenmodus. Anders als reine Monitoring-Lösungen, die nur den über das
Web-Interface der SolvisRemote abgefragten XML-String interpretieren, steuert
dieser Server die Anlage auch aktiv.

Bei älteren Anlagen (vor SolvisControl 2 Reglerversion MA205, ab der Solvis das
Modbus-Interface anbietet) gibt es zur Steuerung nur die Web-Oberfläche der
SolvisRemote. Diese arbeitet rein grafisch — sie ist eine Pixelkopie der
SolvisControl-2-Oberfläche. Steuerung erfolgt über Maus-Klicks auf bestimmte
Koordinaten, die Rückmeldung ebenfalls nur grafisch.

Dank an sgollmer für die Implementierung dieser Idee, die dieser Fork übernimmt.

### Funktionsweise der Parameter-Einstellung

Bei einer Sollwert-Änderung ermittelt ein OCR zunächst den aktuellen Wert
grafisch aus der Web-Oberfläche. Anschließend wird der Wert per simulierten
Maus-Klicks geändert und die Änderung zum Abschluss erneut per OCR verifiziert.

![Gui](https://raw.githubusercontent.com/GollmerSt/SolvisSmartHomeServer/master/docu/images/Hierarchie/1%20Heizung.png)

So wird bei einer Feineinstellung die 0 in den eckigen Klammern im
„Home-Screen" per OCR erkannt und die Plus/Minus-Buttons werden betätigt, bis
der Zielwert zwischen den Klammern erscheint. Liegt der zu ändernde Wert nicht
auf dem Home-Screen, fährt der Server den passenden Screen automatisch an.

## Features

- **Messwerte auslesen** — Sensoren/Zustände der Anlage lesen.
- **Parameter einstellen** — Temperatur-Sollwerte, Raumabhängigkeiten,
  Anlagenmodus über OCR + simulierte Klicks setzen.
- **Uhr-Monitoring** — Solvis-Uhr überwachen und nachjustieren.
- **Fehlererkennung** — Fehlerzustände erkennen; optional Mail (bei
  Fehlerscreen inkl. Hardcopy als Anhang).
- **Manuelle Eingriffe erkennen** — Anwender-/Service-Zugriffe am Touchscreen
  werden erkannt; danach werden veränderte Parameter neu gelesen.
- **MQTT-Anbindung** — Integration in Smart-Home-Systeme ohne speziellen Client
  (nur MQTT-Broker + MQTT-Erweiterung nötig).
- **Proprietäre Server-Client-Schnittstelle** — JSON, bis zu 50 Clients (nur
  FHEM-Client existiert). Upstream-Erbe, im mTLS-Setup ungenutzt.
- **Anlagen-Anpassung über XML** — mitgelieferte XML-Schemata; ab Version
  1.03.00 Kanal-Generierung passend zur Anlagenkonfiguration.

Beispiel einer per Mail versandten Fehlermeldung:

![Fehlermeldung](https://raw.githubusercontent.com/GollmerSt/SolvisSmartHomeServer/master/testFiles/images/Stoerung%205.png)

Eine Kanal-Liste findet sich im
[MQTT-Schnittstelle-Wiki](https://github.com/GollmerSt/SolvisSmartHomeServer/wiki/MQTT-Schnittstelle#aktuell-definierte-channels)
bzw. wird ab Version 1.03.00 vom Programm generiert
([Doku der Kanalbelegung](https://github.com/GollmerSt/SolvisSmartHomeServer/wiki/Dokumentation-der-Kanalbelegung)).

## Voraussetzungen

- **Anlage:** SolvisMax 6/7 mit SolvisControl **2** und SolvisRemote. Bei
  SolvisControl **3** nicht möglich (Web-Interface durch Solvis-Portal ersetzt
  → Modbus verwenden).
- **Laufzeit:** PC/Raspberry o. Ä. mit Linux oder Windows, **JRE ≥ 17**
  (Fork-Baseline; verifiziert mit OpenJDK 21). Der Upstream lief noch unter
  Java 8; siehe [MODERNISIERUNG.md](MODERNISIERUNG.md) 3.2.
- **Smart-Home-System:** FHEM, ioBroker, OpenHAB o. Ä. mit MQTT-Schnittstelle.

## Smart-Home-Anbindung

Zwei gleichwertige Interfaces stehen zur Verfügung (kein Funktionsunterschied):

| Interface | Client nötig? | Beschreibung |
|---|---|---|
| MQTT | nein (Standard-Modul genügt) | Empfohlen; über beliebigen MQTT-Broker. |
| Server-Client (proprietär) | ja (nur FHEM) | JSON-basiert, historisch zuerst da. |

Vorhandene Anpassungen je Smart-Home-System:

| System | Beschreibung |
|---|---|
| FHEM | Modul auf Basis der Server-Client-Schnittstelle (Teil des Pakets). |
| ioBroker | Objektliste + Pairing-Script für das Modul „MQTT Client" (konfigurationsspezifisch generiert). |

Weitere sind in Vorbereitung.

## Quick Start

Voraussetzung: JDK 17+ (der mitgelieferte Maven-Wrapper lädt Maven selbst) und
ein ausgechecktes Repository.

```bash
./mvnw -B clean package                                             # → target/SolvisSmartHomeServer.jar
java -jar target/SolvisSmartHomeServer.jar --string-to-crypt=probe  # Laufzeit-Smoke-Test
```

Erwartet: `BUILD SUCCESS` und beim zweiten Befehl ein Base64-artiger
verschlüsselter String. Vollständige Erstinbetriebnahme (Anlage anbinden,
OCR-Lernphase, MQTT) als Container: [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md).

## Betrieb (Day 2)

Produktivbetrieb erfolgt headless als systemd-Dienst oder Docker-Container
hinter einem lokalen Broker + mTLS-Bridge (Betriebsmodell:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) §4). Betriebsprobleme einordnen:
[Troubleshooting](docs/INBETRIEBNAHME.md#troubleshooting).

## Verzeichnisstruktur

```text
.
├── src/                # Java-Quellen (Paket de.sgollmer.solvismax)
├── rsc/                # eingebettete Ressourcen (u. a. base.xml-Vorlage, XSD)
├── test/, testFiles/   # JUnit-Tests + Golden-Referenzen (OCR, XML)
├── local-maven-repo/   # vendored XMLLibrary (bis JAXB-Ablösung, MODERNISIERUNG 3.3)
├── SmartHome/          # Installations-/Service-Dateien (Linux-Makefile, Windows)
├── docs/               # Fork-Doku (Architektur, Inbetriebnahme, Docker …)
├── docu/               # UPSTREAM-Doku (GollmerSt) — nicht Fork-Stand, siehe ARCHITECTURE.md
├── Dockerfile          # Zwei-Stufen-Build (Maven → schlankes JRE-Image)
└── docker-compose.yml  # Container-Deployment
```

## Dokumentation

**Einstieg & Hintergrund**

- [FORK.md](FORK.md) — warum dieser Fork existiert, geplante Änderungen.
- [MODERNISIERUNG.md](MODERNISIERUNG.md) — Umbau-Roadmap (Status/Reihenfolge).
- [CHANGELOG-fork.md](CHANGELOG-fork.md) — durchgeführte Anpassungen im Detail.

**Betrieb**

- [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md) — Erstinbetriebnahme Schritt
  für Schritt (Container).
- [docs/DOCKER.md](docs/DOCKER.md) — Container-Grundlagen und Fallstricke.

**Architektur**

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — konsolidierte Fork-Sicht
  (Lineage, System-Kontext, Betriebsmodell, Grenzen).
- [docs/jaxb-exploration.md](docs/jaxb-exploration.md) — XML-Binding-Ablösung
  (Explorations-Erkenntnisse).
- [docs/mtls-behebung-vorschlag.md](docs/mtls-behebung-vorschlag.md) — Analyse
  des nativen mTLS-Pfads (Variante B).

**Qualität**

- [TESTPLAN.md](TESTPLAN.md) — phasenweiser Prüfplan (mit/ohne Anlage).

**Ausführliche Upstream-Dokumentation:** Installation, Interfaces und
Arbeitsweise im
[Upstream-Wiki](https://github.com/GollmerSt/SolvisSmartHomeServer/wiki).

## Externe KI-Analysen

Externe Assistenten (Copilot u. a.) mit Repo-Lesezugriff liefern brauchbare
Zweitmeinungen — wenn sie den lebenden **Fork**-Stand lesen statt der
Upstream-Doku oder eines veralteten Snapshots. Prompt-Vorlage zum direkten
Mitgeben:

```text
Analysiere den Branch feature/modernisierung — master folgt nur dem
Upstream. Lies vor jeder Aussage in dieser Reihenfolge:
1. README.md — Projektzweck, Wegweiser, Fork-Hinweis
2. FORK.md — warum der Fork existiert, Upstream-/Branch-Modell
3. docs/ARCHITECTURE.md — konsolidierte Fork-Sicht: System-Kontext,
   Betriebsmodell (lokaler Broker + mTLS-Bridge, Topic-Raum solvis/#),
   Grenzen
4. MODERNISIERUNG.md — Umbau-Roadmap mit Status (geplant vs. umgesetzt)
5. CHANGELOG-fork.md — tatsächlich durchgeführte Abweichungen, neueste
   oben
6. TESTPLAN.md — was verifiziert ist und was (noch) nicht

Achtung: docu/ ist unveränderte UPSTREAM-Doku (Stand 2021) und
beschreibt NICHT den Fork — nicht als Beleg für den Fork-Stand nutzen.

Regeln für deinen Bericht:
- Nenne Branch und Commit-Stand (SHA + Datum) deiner Analyse.
- Jede Aussage ohne selbst gelesene Quelle markierst du als
  UNVERIFIED — Enthaltung schlägt Erfindung.
- Aussagen über die reale Anlage (SolvisMax/SolvisRemote), Broker,
  Bridge oder laufende Container nur bei tatsächlichem Zugriff; sonst
  explizit "kein Zugriff, nicht geprüft".
```

Zertifikate, Keys und Anlagen-Zugangsdaten liegen nicht im Repo
(nicht-versionierter Operator-State) und gehören in keinen externen
Assistenten-Kontext.

## Lizenz

MIT — siehe [LICENSE](LICENSE). Urheber der OCR-Kernidee und des Upstream-Codes:
Stefan Gollmer (GollmerSt); dieser Fork trägt Betriebs-, Wartungs- und
Modernisierungsanpassungen bei ([FORK.md](FORK.md)).
</content>
</invoke>
