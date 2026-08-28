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

## Nach Zielgruppe

| Lens | Ich will wissen … | Einstieg |
|------|-------------------|----------|
| **ANWENDER** | was das Projekt tut und wie ich es nutze | [Quick Start](#quick-start) |
| **TECHNIKER** | wie es funktioniert (Komponenten, Datenfluss) | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| **PROGRAMMIERER** | Build, Code-Layout, Tests, Beiträge | [CONTRIBUTING.md](CONTRIBUTING.md) |
| **OPERATEUR** | Betrieb Schritt für Schritt + Automatisierung | [docs/runbooks/](docs/runbooks/) |
| **ARCHITEKTUR** | tragende Entscheidung/Topologie | [Abschnitt Architektur](#architektur) · [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| **COMPLIANCE** | Standards, Secrets/PII, Nachweise | [Abschnitt Compliance](#compliance) |

Die sechs Lenses folgen dem Cockpit-weiten Zielgruppen-Doku-Standard.
**TECHNIKER** will *verstehen* (Architektur/Datenfluss), **PROGRAMMIERER** will
*bauen/beitragen*. **ARCHITEKTUR** und **COMPLIANCE** sind README-Kurzsichten,
die in die Tiefe verweisen ([docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) bzw.
[docs/security/](docs/security/)).

## Ich möchte …

| Ich möchte … | Dokument |
|---|---|
| in Minuten bauen und smoke-testen | [Quick Start](#quick-start) |
| produktiv aufsetzen (nativ/systemd, Container als Alternative) | [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md) |
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
OCR-Lernphase, MQTT) nativ als systemd-Dienst (Standardpfad) oder als
Container: [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md).

## Betrieb (Day 2)

Produktivbetrieb erfolgt headless als nativer systemd-Dienst hinter einem
lokalen Broker + mTLS-Bridge; der Container ist ausschließlich die Alternative
für Entwicklung und Test, im Produktivbetrieb gibt es keinen (Betriebsmodell:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) §4). Betriebsprobleme einordnen:
[Troubleshooting](docs/INBETRIEBNAHME.md#troubleshooting).

## Architektur

Der Connector ist ein eigenständiger Java-17-Prozess, der die SolvisRemote über
deren grafische Web-Oberfläche per HTTP anspricht: Werte werden per **OCR** aus
dem Pixelbild gelesen, Änderungen per simulierten Klicks gesetzt und erneut per
OCR verifiziert. Nach außen spricht er **ausschließlich MQTT** und belegt dabei
vertraglich **nur den Topic-Namensraum `solvis/#`** (`topicPrefix="solvis"`).

Tragende Betriebsentscheidung ist **Variante A** (lokaler Mosquitto auf
`127.0.0.1` + mTLS-Bridge `solvis-bridge` zum zentralen Haus-Broker); der direkte
native `<Ssl>`-mTLS-Pfad (**Variante B**) ist deprecated und bricht beim Start
per Fail-Fast-Guard ab (Paho-v3-Fehler 32105). Grundlage ist ADR-0017
(Projektlandschaft Hausautomation, Cockpit-Repo `stack-master`) und die
architect-Abstimmung vom 2026-07-25.

Der OCR-Kern (`imagepatternrecognition/`) stammt vom Upstream-Autor und wird
bewusst **nicht** neu gebaut, sondern durch 31 Golden-Tests gepinnt. Der Fork
trägt nur Betriebs-, Wartungs- und Modernisierungsanpassungen bei (Maven-Build,
Java-17-Baseline, SLF4J/Logback, JAXB-Migration, Docker).

Vollständige Sicht — System-Kontext (mit Topologie-Diagramm), Baustein-Sicht,
Betriebsmodell und bewusste Grenzen — in
**[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**. Warum der Fork existiert:
[FORK.md](FORK.md); Umbau-Roadmap: [MODERNISIERUNG.md](MODERNISIERUNG.md).

## Compliance

**Secrets/PII im Repo: keine realen Betriebs-Secrets.** Zertifikate, private
Schlüssel und Anlagen-Zugangsdaten liegen ausschließlich als
nicht-versionierter Operator-State vor — gemountete Dateien (`/certs`) und
`passwordCrypt`-Werte in einer lokalen, **nicht eingecheckten** `base.xml`
(erstellt aus der Vorlage `rsc/de/sgollmer/solvismax/data/base.xml`). Ein
`gitleaks`-Lauf über den Fork-Stand meldet ausschließlich Upstream-Alt-Artefakte
(Muster-Daten in `control.xml`, Beispiel-`passwordCrypt` in der Template-
`base.xml`, ein PKCS#8-Testschlüssel in `SslTest.java`) — **keine** Live-Credentials.

**Krypto ehrlich benannt:** `passwordCrypt` ist **Obfuskation, kein Schutz**
(hartkodierter Schlüssel, ECB; aus dem öffentlichen Quellcode ableitbar). Der
einzige reale Schutz der Secrets in `base.xml` sind **Dateirechte**. Soll der
installierten Datei seit der S-5-Härtung: `640 root:solvis` — der Dienst liest
seine Konfiguration, darf sie aber nicht umschreiben (sonst könnte ein
kompromittierter Dienst Broker-URL und Zugangsdaten selbst ändern). Durchgesetzt
wird das bei jedem `installSolvis`-/`updateSolvis`-Lauf durch das Makefile-Ziel
`enforceOwnership`; `chmod 600` gilt nur für die Arbeitskopie beim Bearbeiten
(im Container-Fall gehört die gemountete Datei der UID 10001). Details:
[docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md) Phase 2 und Update-Abschnitt.
Krypto-Umbau ist Roadmap-Punkt 4.6.

**Berührte Standards / Restrisiken:** Transportsicherheit nach außen über die
mTLS-Bridge (Client-Zertifikate + Broker-ACL `readwrite solvis/#`; SSOT ist das
as-built-Runbook `ccu2mqtt:docs/runbooks/solvis-bridge-ransible.md` im
Vertragspartner-Repo). Drei bewusst akzeptierte Restrisiken mit je
einer dokumentierten Entscheidung: HTTP-only zur SolvisRemote (S-8),
unauthentifizierter TCP-/JSON-Server auf Loopback-Bind bzw. abschaltbar (S-3),
Obfuskations-Krypto (S-2). Details und Kompensationen:
[docs/security/](docs/security/) und [docs/ARCHITECTURE.md §5](docs/ARCHITECTURE.md).

## Verzeichnisstruktur

```text
.
├── src/                # Java-Quellen (Paket de.sgollmer.solvismax)
├── rsc/                # eingebettete Ressourcen (u. a. base.xml-Vorlage, XSD)
├── test/, testFiles/   # JUnit-Tests + Golden-Referenzen (OCR, XML)
├── local-maven-repo/   # vendored XMLLibrary (bis JAXB-Ablösung, MODERNISIERUNG 3.3)
├── SmartHome/          # Installations-/Service-Dateien (Linux-Makefile, Windows)
├── docs/               # Fork-Doku (Index, Architektur, Inbetriebnahme, runbooks/, security/)
├── docu/               # UPSTREAM-Doku (GollmerSt) — nicht Fork-Stand, siehe ARCHITECTURE.md
├── Dockerfile          # Zwei-Stufen-Build (Maven → schlankes JRE-Image)
└── docker-compose.yml  # Container-Deployment
```

## Dokumentation

Vollständiger Index: [docs/README.md](docs/README.md).

### Einstieg & Hintergrund

- [FORK.md](FORK.md) — warum dieser Fork existiert; Abschnitt „Fork-Ziele und
  Stand" (alle drei Ziele vollzogen).
- [MODERNISIERUNG.md](MODERNISIERUNG.md) — Umbau-Roadmap (Status/Reihenfolge).
- [CHANGELOG-fork.md](CHANGELOG-fork.md) — durchgeführte Anpassungen im Detail.

### Entwicklung

- [CONTRIBUTING.md](CONTRIBUTING.md) — Build, Tests, Code-Layout, Beitrags-Regeln.

### Betrieb

- [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md) — Erstinbetriebnahme Schritt
  für Schritt (nativ/systemd als Standardpfad, Container als Alternative;
  inkl. Cutover vom Alt-Pfad).
- [docs/runbooks/](docs/runbooks/) — Day-2-Standardaufgaben (Deploy, Update,
  Backup, Restore, Lernphase, Stop) mit Automatisierungs-Angabe.
- [docs/DOCKER.md](docs/DOCKER.md) — Container-Grundlagen und Fallstricke.

### Architektur & Compliance

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — konsolidierte Fork-Sicht
  (Lineage, System-Kontext, Betriebsmodell, Grenzen).
- [docs/jaxb-exploration.md](docs/jaxb-exploration.md) — XML-Binding-Ablösung
  (Explorations-Erkenntnisse).
- [docs/mtls-behebung-vorschlag.md](docs/mtls-behebung-vorschlag.md) — Analyse
  des nativen mTLS-Pfads (Variante B).
- [docs/security/](docs/security/) — Secret-/PII-Handhabung, `gitleaks`-Befund,
  Restrisiken (S-2/S-3/S-8).

### Qualität

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
