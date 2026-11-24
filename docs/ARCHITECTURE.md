# Architektur (Fork-Sicht)

> **Sprache:** Deutsch · **Status:** aktiv · **Stand:** 2026-11-20 · **Zielgruppe:** Programmierer,
> Operateure · **Bezug:** ADR-0017, [FORK.md](../FORK.md), [MODERNISIERUNG.md](../MODERNISIERUNG.md),
> [runbooks/README.md](runbooks/README.md), [INBETRIEBNAHME.md](INBETRIEBNAHME.md)

Konsolidierte Architektur-Übersicht dieses Forks an **einer** Stelle, statt verstreut über
[FORK.md](../FORK.md), [MODERNISIERUNG.md](../MODERNISIERUNG.md), [TESTPLAN.md](../TESTPLAN.md) und die
Runbooks. Diese Dokumente bleiben die Detailquellen; hier steht die Gesamtsicht. Bei Abweichungen gilt der
Code (`SmartHome/Linux/Makefile`, `SmartHome/Linux/SolvisSmartHomeServer.service`,
`rsc/de/sgollmer/solvismax/data/base.xml`, `.github/workflows/build.yml`).

Jede Perspektive hat eine Skizze als Textdiagramm (lesbar in jedem Editor, im Terminal und auf GitHub, ohne
Renderer). Adressen (IP, Hostnamen außer `ransible`) stehen bewusst nicht hier; führende Stelle dafür ist
[INBETRIEBNAHME.md](INBETRIEBNAHME.md), Abschnitt „Feste Adressen“.

| Perspektive | Frage | Abschnitt |
|---|---|---|
| Lineage | Woher kommt der Code, was trägt der Fork bei? | [1](#1-fork-lineage) |
| Kontext | Wer und was spricht mit dem Connector? | [2](#2-kontextsicht) |
| Bausteine | Woraus besteht der Code? | [3](#3-bausteinsicht-quellcode) |
| Betriebsmodell und Verteilung | Was läuft wo, über welche Netze und Protokolle? | [4](#4-betriebsmodell-und-verteilungssicht) |
| Grenzen und Sicherheit | Was tut der Fork bewusst nicht, welche Zonen und Restrisiken gibt es? | [5](#5-grenzen-und-sicherheitssicht) |
| Offene Fragen | Was ist bewusst nicht entschieden? | [6](#6-offene-fragen-bewusst-nicht-geraten) |
| Laufzeit | Wie laufen Messen, Steuern, Start/Stopp und Lernphase ab? | [7](#7-laufzeitsicht) |
| Daten | Wo liegen Code, Konfiguration, Zustand und Geheimnisse, was ist gesichert? | [8](#8-datensicht) |
| Lieferung | Wie kommt eine Änderung auf den Host? | [9](#9-lieferung-und-deploy) |
| Wiederherstellung | Was braucht man wann, um nach einem Ausfall zurückzukommen? | [10](#10-wiederherstellung) |
| Entscheidungen | Was ist festgelegt und warum? | [11](#11-entscheidungen) |

Die Nummern 1 bis 6 sind stabil, weil andere Dokumente und der Code auf sie verweisen (§3 Bausteine,
§4 Betriebsmodell, §5 Grenzen/Erbschafts-Inventar, §6 offene Fragen). Nicht zutreffend bzw. bewusst kurz:
Eine Benutzer- und Rollensicht entfällt (der Connector hat keine eigenen Benutzerkonten; die einzigen
Identitäten sind Systemnutzer `solvis`, Anlagen-Account und Broker-Logins, siehe §5.2). Die Docker-Linie
erscheint nur als Alternative für Entwicklung und Test.

> ⚠️ **Abgrenzung zum Verzeichnis `docu/`:** `docu/` ist **Upstream-Stand**
> (Original-Dokumentation von GollmerSt, u. a. Word/PDF, Screenshots,
> Modbus-Spezifikationen) und beschreibt **nicht** den Zustand dieses Forks
> (z. B. noch Ant-Build, tinylog, unverschlüsseltes MQTT). Für den Fork gelten
> **dieses Dokument**, [FORK.md](../FORK.md) und
> [CHANGELOG-fork.md](../CHANGELOG-fork.md). `docu/` wird bewusst nicht
> umgeschrieben (Fork-Etikette: Upstream-Artefakte bleiben erhalten).

## 1. Fork-Lineage

```text
  GollmerSt/SolvisSmartHomeServer   (Upstream, MIT; unmaintained seit Anfang 2023,
          │                          letztes Release v01.05.01, Dez 2021; Remote "upstream")
          │ git fetch upstream
          ▼
  BortDeveloper/SolvisSmartHomeServer   (dieser Fork; Remote "origin")
          ├── master                  folgt dem Upstream
          └── feature/modernisierung  betriebsspezifische Arbeit (dieser Stand, Produktivquelle)
```

Die **Kernidee** — eine Solvis-Anlage ohne native API über die grafische
Web-Oberfläche der SolvisRemote per **OCR** auszulesen und per simulierten
Klicks zu steuern — stammt vom Upstream-Autor **Stefan Gollmer (GollmerSt)**
und wird unverändert übernommen (Attribution: [FORK.md](../FORK.md)).

Der Fork trägt ausschließlich Betriebs-, Wartungs- und
Modernisierungsanpassungen bei. Die wichtigsten Abweichungen vom Upstream
(Motivation und Details je Änderung: [CHANGELOG-fork.md](../CHANGELOG-fork.md),
Fahrplan/Status: [MODERNISIERUNG.md](../MODERNISIERUNG.md)):

| Bereich | Upstream | Fork |
|---|---|---|
| Build | Ant + eingecheckte `lib/`-Jars | Maven (`./mvnw -B clean package`, Uber-Jar via shade), CI mit JDK-Matrix 17/21, Dependabot |
| Java-Baseline | Java 8 | **17** (läuft auf 17/21; Zielhost OpenJDK 21) |
| MQTT-Transport | nur unverschlüsselt | **lokaler Broker + mTLS-Bridge** (Variante A, unterstützt — §4). Natives `<Ssl>`-mTLS (Variante B) ist **deprecated/nicht unterstützt** (Fail-Fast-Guard beim Start; §4, [mtls-behebung-vorschlag.md](mtls-behebung-vorschlag.md)) |
| Logging | Eigenfassade + tinylog/log4j | SLF4J + Logback |
| Mail | `javax.mail`, `ssl.trust="*"` | `jakarta.mail` (angus-mail), Zertifikatsprüfung aktiv |
| XML-Binding | proprietäre `XMLLibrary` + 67 Creator-Klassen | schrittweise JAXB (base.xml/measurements.xml/graficData.xml fertig; control.xml: alle 13 Zweige gebunden, Reader-Umstieg offen) |
| Betrieb | Zip-Release Linux/Windows | **nativer, gehärteter systemd-Dienst** (Produktivweg, §4); zusätzlich **Docker** für Entwicklung/Test ([DOCKER.md](DOCKER.md)) |

## 2. Kontextsicht

```text
  Betreiber (Arbeitsplatz)               ┌────────────────────────────────┐
     │  └── Shell auf dem Host ─────────▶│ SolvisSmartHomeServer (Fork)   │
     │ git push                          │ Host ransible, systemd-Dienst  │◀── git pull + Build ── GitHub-Fork
     ▼                                   │ OCR · Klick-Steuerung · MQTT   │
  GitHub-Fork BortDeveloper/…            └────┬──────────▲─────────┬──────┘
     │ CI (JDK 17/21), Dependabot             │ Klicks   │ Bild,   │ MQTT nur solvis/#
     ▲ git fetch upstream                     ▼          │ XML     ▼ (lokaler Broker + mTLS-Bridge)
  Upstream GollmerSt (unmaintained)    ┌───────────────────┐   ┌──────────────────┐   ┌──────────────────┐
                                       │ SolvisRemote      │   │ Haus-Broker      │──▶│ Home Assistant / │
                                       │ SolvisControl 2   │   │ Mosquitto,       │◀──│ Node-RED         │
                                       │ SolvisMax 6/7     │   │ mTLS + ACL       │   │ (Konsumenten)    │
                                       └───────────────────┘   │ (Repo ccu2mqtt)  │   └──────────────────┘
                                                               └──────────────────┘
  Host-Sicherung (restic, Repo ccu2mqtt) ──▶ zwei externe Backup-Ziele  (§8)
```

| Partner | Beziehung | Verantwortet in |
|---|---|---|
| SolvisRemote + SolvisControl 2 | Pixelbild der Regler-Oberfläche, Messwert-XML, Klick-Schnittstelle per HTTP | Gerät vor Ort |
| Haus-Broker (Mosquitto, mTLS + ACL) | MQTT-Vertragspartner, Namensraum `solvis/#` | Repo `ccu2mqtt` (as-built `docs/runbooks/solvis-bridge-ransible.md`) |
| Home Assistant / Node-RED | lesen Status, senden Kommandos | außerhalb dieses Kontexts |
| GitHub-Fork `origin` | Quelle des Codes, CI, Dependabot | dieses Repo |
| Upstream `GollmerSt` | Ursprung, Wiki mit Kanal-Listen und MQTT-Schnittstellen-Doku | unmaintained (offene Frage 4, §6) |
| Host-Sicherungskette | sichert `/etc/solvis`, `/var/lib/solvis`, `/etc/default/solvissmarthomeserver` (Scope `solvis`) | Repo `ccu2mqtt` |

- **Anlagen-Seite:** Die SolvisRemote liefert eine Pixelkopie der SolvisControl-2-Oberfläche
  (`/display.bmp`), einen Messwert-Hex-String (`/sc2_val.xml`) und nimmt Klicks an (`/Touch.CGI`,
  `/Taster.CGI`; Endpunkte in `Constants.java`). Der Server liest Werte per OCR (reines Java, kein
  Tesseract), steuert per simulierten Klicks und verifiziert Änderungen wieder per OCR. Einmalige
  **Lernphase** (`--server-learn`) erzeugt `LearnedImages` im Schreibpfad.
- **MQTT-Seite (Vertrag):** Dieser Connector publiziert und konsumiert
  **ausschließlich den Namensraum `solvis/#`** (`topicPrefix="solvis"` in
  `base.xml`). Topic-Schema: Status `solvis/<unit>/<kanal>/data`, Kommandos
  `solvis/<client>/<unit>/<kanal>/cmnd`, Metadaten `…/meta`, Serverstatus
  `solvis/server/online` (mit Last Will). Andere Topics, andere Projekte,
  Broker-Interna: kennt dieser Kontext nicht. Die Gegenseite (Broker-ACL,
  Bridge-Konfiguration, Login `solvis-bridge`, PKI) ist im Vertragspartner-Repo
  `ccu2mqtt` dokumentiert; maßgeblich ist dort das as-built-Runbook
  `docs/runbooks/solvis-bridge-ransible.md` (Ist = Soll seit 2026-08-13).
- **Nebenschnittstelle (Upstream-Erbe):** ein proprietärer, unauthentifizierter
  TCP-Server (JSON, Default-Port 10735/10736) für FHEM-/ioBroker-Clients. Im
  Zielbetrieb dieses Stacks ungenutzt; er bindet daher standardmäßig **nur auf
  `127.0.0.1`** und ist vollständig abschaltbar (Konfiguration siehe §5).
- **Optional:** Fehler-Mail über SMTP (`<tns:ExceptionMail>` in `base.xml`, TLS mit Zertifikatsprüfung).
  Ob sie produktiv aktiv ist, geht aus dem Repo nicht hervor (Lücke L-8, §10.3).

## 3. Bausteinsicht (Quellcode)

```text
  Repo SolvisSmartHomeServer (Branch feature/modernisierung)
  ┌────────────────────────────────────────────────────────┐
  │ src/de/sgollmer/solvismax/                             │   ./mvnw -B clean package
  │ ┌─ Main ─────────────────────────────────────────────┐ │   ┌───────────────────────────────┐
  │ │ Start, CLI (--server-learn, --string-to-crypt …),  │ │──▶│ target/SolvisSmartHomeServer  │
  │ │ TCP-Server-Bind (S-3)                              │ │   │ .jar (Uber-Jar, shade)        │
  │ └───────────────┬───────────────────────┬────────────┘ │   │ enthält base.xsd und          │
  │                 ▼                       ▼              │   │ Steuerdaten aus rsc/          │
  │ ┌─ model/ ──────────────────┐  ┌─ connection/ ───────┐ │   └───────────────────────────────┘
  │ │ Anlage, Screens, Kanäle,  │◀▶│ mqtt: Paho v3,      │ │
  │ │ Strategien, Messwerte,    │  │ HealthToken, Guard  │ │
  │ │ backup (measurements.xml) │  │ transfer: TCP/JSON  │ │
  │ └───────┬────────────▲──────┘  └─────────────────────┘ │
  │         ▼            │                                 │
  │ ┌─ imagepattern- ────┴──────┐  ┌─ xml/ + JAXB-DTOs ──┐ │
  │ │ recognition/ (OCR-Kern,   │  │ base.xml, control.  │ │
  │ │ Upstream, Golden-Tests)   │  │ xml; XMLLibrary     │ │
  │ └───────────────────────────┘  └─────────────────────┘ │
  │ crypt/ · mail/ · smarthome/ · windows/                 │
  ├────────────────────────────────────────────────────────┤
  │ rsc/…/data/   base.xml-Vorlage, base.xsd, control.xml  │
  │ SmartHome/Linux/  Makefile, systemd-Units,             │
  │                   check-credentials.sh                 │
  │ Dockerfile, docker-compose.yml (Entwicklung/Test)      │
  │ .github/  build.yml (CI), dependabot.yml               │
  │ local-maven-repo/  vendored XMLLibrary                 │
  └────────────────────────────────────────────────────────┘
```

| Baustein | Verantwortung |
|---|---|
| `imagepatternrecognition/` (`image`, `ocr`, `pattern`) | **OCR-Kern** — isoliert, minimale Abhängigkeiten (nur JDK-Bildklassen), Modulgrenze in `package-info.java` dokumentiert, durch 31 Golden-Tests gepinnt. Wird bewusst **nicht** umgeschrieben (Alleinstellungsmerkmal, Urheber GollmerSt). |
| `model/` | Anlagen-/Domänenmodell: Screens, Kanäle, Messwerte, Strategien; Konfiguration aus `base.xml`/`control.xml`; `backup/` schreibt `measurements.xml` periodisch und beim Stopp |
| `connection/` (`mqtt`, `transfer`) | MQTT-Client (Paho v3) inkl. `MqttThread`/Topic-Aufbau, Health-Token, Fail-Fast-Guard gegen `<Ssl>`; proprietärer TCP-Server |
| `xml/` + JAXB-DTOs | Konfigurations-Parsing: Übergang `XMLLibrary`-Creators → JAXB („Weg B": DTOs werden das Config-Modell, Sichten wie `MqttConnectionConfig`, `UnitConfig`, `ExecutionConfig`), dual-parse-abgesichert |
| `crypt/` | `CryptAes` für `passwordCrypt`-Werte — **Obfuskation, kein echter Schutz** (hartkodierter Schlüssel, ECB; Schlüssel aus dem öffentlichen Quellcode ableitbar). Der einzige reale Schutz der Secrets in `base.xml` sind **Dateirechte** (Soll der installierten Datei `640 root:solvis`, bei jedem Install-/Update-Lauf per `enforceOwnership` durchgesetzt; `600` nur für die Arbeitskopie — [INBETRIEBNAHME.md](INBETRIEBNAHME.md) Phase 2 und Update-Abschnitt). Ehrliche Behandlung ist Roadmap-Punkt 4.6 |
| `mail/` | optionale Fehler-Mail (jakarta.mail, Screenshot-Anhang, TLS-Zertifikatsprüfung aktiv) |
| `smarthome/` | Integrations-Helfer (u. a. ioBroker-Generierung) |
| `windows/` | optionale, JDK-only Windows-Hilfe (`--create-task-xml`); auf anderen Plattformen ungenutzt |
| `SmartHome/Linux/` | Install-Brücke Maven → Host: `prepare`, `checkJava`, `installSolvis`/`updateSolvis`, `enforceOwnership`, `learn`, `crypt`, `checkCredentials`; Unit-Vorlagen mit Härtungsblock |

Build: Maven-Wrapper, JDK 17+ → `target/SolvisSmartHomeServer.jar` (Uber-Jar).
Die proprietäre `XMLLibrary` ist bis zur vollständigen JAXB-Ablösung in
`local-maven-repo/` vendored.

## 4. Betriebsmodell und Verteilungssicht

> **Entscheidung (2026-07-25): Variante A ist die unterstützte
> Produktivvariante; Variante B ist deprecated/nicht unterstützt.** Damit ist
> die frühere offene Frage 1 (§6) geschlossen und ADR-0017 Folgeentscheidung 5
> (konsolidierte Betriebsmodell-Sicht in **einem** Dokument) erfüllt. Grundlage:
> architect-Abstimmung „Option A, MITTRAGEN mit Auflagen A-1…A-4"
> (`stack-master:shared/audit-log/2026-07-25-architect-solvis-mtls-abstimmung.md`),
> Betreiber-Entscheid 2026-07-21 „Option A" (`ccu2mqtt:docs/solvis.md` §6),
> ADR-0017 D1 („ausschließlich `solvis/#` über lokalen Broker + mTLS-Bridge").
> Kein Shared-ADR nötig; nur ein Rückwechsel auf B wäre ADR-pflichtig.

### 4.1 Verteilung (Ist seit 2026-08-13)

```text
  Haus-LAN (SolvisRemote per DHCP-Reservierung im Router gepinnt, Adresse: INBETRIEBNAHME.md)

  ┌──────────────────────┐          ┌─ Host ransible (Raspberry Pi 4, Debian 13 arm64) ─────────────────────┐
  │ SolvisRemote         │   HTTP   │                                                                       │
  │ kein HTTPS,          │◀─────────│ SolvisSmartHomeServer.service (systemd, Nutzer solvis, OpenJDK 21)    │
  │ Digest-Auth nur MD5  │          │   /usr/local/lib/solvis/  Jar, base.xsd; /etc/solvis/base.xml         │
  └──────────┬───────────┘          │   /var/lib/solvis/SolvisServerData/   Lernbilder, XML, Logs           │
             │                      │   bindet 127.0.0.1:10735 und :10736 (TCP-Server, im Stack ungenutzt)  │
  ┌──────────┴───────────┐          │          │ MQTT Klartext 127.0.0.1:1883, Login solvis-local           │
  │ SolvisControl 2      │          │          ▼                                                            │
  │ SolvisMax 6/7        │          │ Mosquitto lokal (localhost-only)                                      │
  └──────────────────────┘          │   Bridge solvis-bridge-to-primary, topic solvis/# both                │
                                    │          │                                                            │
                                    │ ransible-backup.timer 02:30 (restic, u. a. Scope solvis)              │
                                    └──────────┼──────────────────────────────────┼─────────────────────────┘
                                               │ mTLS :8883, Login solvis-bridge  │ restic
                                               ▼                                  ▼
                                    Haus-Broker (mTLS + ACL)               zwei externe Ziele
                                               │ MQTT :8883, solvis/#      (HiDrive, all-inkl)
                                               ▼
                                    Home Assistant / Node-RED

  Alt-Installation mon-dg: Upstream-Dienst gestoppt + disabled, bleibt als Rollback-Weg installiert
  (INBETRIEBNAHME.md Phase 5). Entwicklung/Test: Docker auf einem Arbeitsrechner (DOCKER.md).
```

Lokaler Mosquitto, Bridge, mTLS-Material, ACL und Backup-Kette werden **nicht** aus diesem Repo
eingerichtet; sie gehören zum Host bzw. zum Broker und sind in `ccu2mqtt` as-built dokumentiert.

### 4.2 Varianten

Zielumgebung ist ein Smart-Home-Stack, dessen MQTT-Verkehr ausschließlich
über einen **mTLS-gehärteten zentralen Mosquitto** läuft (kein
Klartext-Listener, Client-Zertifikate + Broker-ACL).

- **Variante A — lokaler Broker + mTLS-Bridge (unterstützt, Zielbild lt.
  FORK.md):** Der Server publiziert unverschlüsselt auf einen **lokalen, auf
  `127.0.0.1` gebundenen** Mosquitto auf demselben Host; dieser koppelt per
  mTLS-Bridge (`topic solvis/# both`, Login `solvis-bridge`) an den
  zentralen Broker. Dasselbe Muster nutzt der Gesamt-Stack für CCU-Jack und
  die Feld-Gateways `mon-dg`/`eno-eg`. TLS im Server selbst ist hier nicht
  erforderlich. Die Gegenseite (Cert `solvis-bridge`, ACL `readwrite
  solvis/#`, Bridge- und Local-Broker-Konfiguration) ist bei `ccu2mqtt`
  eingerichtet und as-built dokumentiert in
  `docs/runbooks/solvis-bridge-ransible.md`; `docs/solvis.md` §7 ist nur noch
  Chronik und Herleitung.

  **Container-Netz (damit `127.0.0.1` echtes Host-Loopback bleibt):** Der Hop
  Connector → lokaler Broker rechtfertigt seinen Klartext nur bei echtem
  Loopback. Zwei konventionstreue Auflösungen:
  - **A3 (produktiv im Einsatz, Stack-Konvention):** Server nativ auf dem Host
    (wie CCU-Jack und die Feld-Gateways `mon-dg`/`eno-eg`), dortiger Mosquitto
    localhost-only. `127.0.0.1` ist Host-Loopback; die Docker-Deploymentlinie
    wird dann nicht genutzt. **Das ist der Ist-Zustand seit 2026-08-13**
    (Host `ransible`, systemd-Dienst, OpenJDK 21).
  - **A1 (Docker):** `network_mode: host` für den Connector-Container →
    `127.0.0.1` bleibt Host-Loopback. **Muss** mit dem TCP-Server-Bind aus §5
    gekoppelt werden (Default-Loopback/Abschaltung), da der proprietäre Server
    sonst auf Host-Interfaces sichtbar würde.
  - *A2 (Mosquitto-Sidecar im Bridge-Netz)* schwächt das Loopback-Argument (der
    Hop ist dann Container-Bridge-Netz) und ist **nur mit dokumentierter
    Security-Entscheidung** zulässig (Netz nachweislich nicht ans LAN
    exponiert) — Auflage A-2.
- **Variante B — natives mTLS (deprecated, nicht unterstützt):** Der direkte
  `<Ssl>`-mTLS-Pfad (`<Ssl>` im `<Mqtt>`-Element) ist **funktionsunfähig** und
  wird **nicht** repariert: Paho v3 lehnt die Kombination `tcp://`-URI +
  `SSLSocketFactory` mit `REASON_CODE_SOCKET_FACTORY_MISMATCH` (32105) ab. Statt
  des früheren Silent-Endlos-Retry bricht der Start jetzt per **Fail-Fast-Guard**
  mit klarer Ursache ab, sobald `<Ssl enable="true">` konfiguriert ist. Die
  frühere Handshake-Verifikation (CHANGELOG-fork.md) belegte nur die
  Teilkomponente, nicht den Paho-Integrationspfad. Details und Begründung:
  [mtls-behebung-vorschlag.md](mtls-behebung-vorschlag.md). Ein Rückwechsel auf B
  wäre ein Landschafts-Vertragseingriff (ADR-pflichtig, neues Connector-Cert +
  ACL-Identität bei `ccu2mqtt`).

### 4.3 Laufzeitumgebung

Deployment: headless, `java.desktop`-Modul erforderlich (kein X11). Produktiv läuft der Fork seit
**2026-08-13** als **systemd-Dienst** auf dem headless RPi4 `ransible` (arm64/Debian 13, OpenJDK 21);
Installationspfad `/usr/local/lib/solvis/`, Konfiguration `/etc/solvis/`, Schreibpfad `/var/lib/solvis` für Lernphase und
Laufzeitdaten. Die Unit setzt `-Dhttp.auth.digest.reEnabledAlgorithms=MD5` (Gerätezwang der SolvisRemote,
wirkt nur auf diesen Prozess), stoppt per SIGTERM (Last Will greift), startet bei Fehlern neu
(`Restart=on-failure`, höchstens 5 Starts in 300 s) und ist seit 2026-11-18 gehärtet (§5.2). Optionale
Betriebsparameter liest sie aus `/etc/default/solvissmarthomeserver` (`SOLVIS_HEALTH_TOKEN_PATH`,
`SOLVIS_TCPSERVER_BINDADDRESS`, `SOLVIS_TCPSERVER_ENABLE`).

Der **Docker-Weg** bleibt als Alternative für Entwicklung und Test dokumentiert
([DOCKER.md](DOCKER.md)): Zwei-Stufen-Build auf `eclipse-temurin:21`, Nutzer `solvis` (UID 10001), `tini`
als PID 1, `HEALTHCHECK` auf das Ready-Token, `/data`-Volume statt Schreibpfad, `base.xml` read-only
gemountet, TCP-Server-Ports bewusst nicht veröffentlicht. Secrets (Anlagen-Zugangsdaten, Broker-Login)
liegen **nie im Repo** — nur als `passwordCrypt`-Werte in der lokalen, nicht eingecheckten `base.xml`; das
mTLS-Material der Bridge gehört ohnehin zum Broker-Host und damit zu `ccu2mqtt`.

Qualitätssicherung: [TESTPLAN.md](../TESTPLAN.md) (Phasen 1–3 ohne Anlage,
4–9 mit Anlage); CI baut/testet auf JDK 17 und 21; OCR per Golden-Tests,
Config-Parsing per Dual-Parse-Vergleich abgesichert. Tests gegen die
**Live-Anlage** nur mit explizitem Betreiber-Auftrag — die Steuerung klickt
real auf der Anlagen-Oberfläche.

## 5. Grenzen und Sicherheitssicht

### 5.1 Grenzen — was dieser Fork bewusst NICHT tut

- **Keine Topics außerhalb `solvis/#`.** Andere Namensräume zu belegen wäre
  ein Vertragsbruch gegenüber der Haus-MQTT-Architektur (Topic-Schema-Owner
  ist das Repo `ccu2mqtt`).
- **Kein Broker-Betrieb, keine ACL-Definition.** Broker und ACL-SSOT sind
  eigene Projekte; dieser Connector ist reiner MQTT-Client.
- **Keine Sicherungslogik.** Backup ist eine Host-Eigenschaft (Repo `ccu2mqtt`, §8).
- **Keine Automations-/Geschäftslogik.** Verbrauch der Daten (Home
  Assistant, Node-RED) liegt außerhalb dieses Kontexts.
- **Kein Neubau des OCR-Kerns.** Isolierung + Golden-Tests statt Rewrite
  (MODERNISIERUNG.md, „Bewusst nicht angetastet").
- **Keine SolvisControl 3 / kein Modbus.** Der Ansatz gilt nur für
  SolvisControl 2 + SolvisRemote; bei SC3 ist Modbus der richtige Weg
  (README).
- **Kein großflächiges Gratis-Refactoring.** Upstream-Diff klein halten,
  Modernisierung nur entlang des Fahrplans — damit Upstream-Stände
  übernehmbar bleiben und Änderungen als PR zurückfließen können.
- **Kein zweiter OCR-Client.** Nie zwei Instanzen (Fork, Alt-Software, Test-Instanz) gleichzeitig gegen
  dieselbe SolvisRemote — sie verklicken sich gegenseitig bis zur realen Fehlsteuerung
  ([INBETRIEBNAHME.md](INBETRIEBNAHME.md) Phase 5).

### 5.2 Vertrauenszonen und Schutzschichten

```text
  ┌─ Zone 1: Anlagen-/IoT-Segment ─┐       ┌─ Zone 2: Host ransible ───────────────────────────────┐
  │ SolvisRemote                   │ HTTP  │ systemd-Sandbox (systemd-analyze security: 1,4 OK)    │
  │ kein HTTPS (S-8, akzeptiert)   │◀──────│   ProtectSystem=strict; schreibbar nur                │
  │ Digest-Auth nur MD5            │       │   SolvisServerData und health                         │
  │ Account + Passwort der Web-GUI │       │   keine Capabilities, Syscall-Filter @system-service  │
  └────────────────────────────────┘       │   IPAddressAllow: localhost + lokale Netze            │
                                           │   SocketBindAllow: nur tcp:10735 und tcp:10736        │
                                           │ base.xml 640 root:solvis, Jar und Ordner root-eigen   │
                                           │   (S-2/S-5: Dateirechte statt Krypto)                 │
                                           │ TCP-Server nur 127.0.0.1, abschaltbar (S-3)           │
                                           │ Loopback :1883 Klartext ──▶ lokaler Mosquitto         │
                                           └───────────────────────────┬───────────────────────────┘
                                                                       │ mTLS-Bridge, Client-Cert solvis-bridge
                                           ┌─ Zone 3: Haus-Broker ─────▼───────────────────────────┐
                                           │ mTLS + ACL readwrite solvis/# (Repo ccu2mqtt)         │
                                           └───────────────────────────┬───────────────────────────┘
                                                                       ▼
                                                   Zone 4: Automationsebene (Home Assistant, Node-RED)
```

| Identität | Wofür | Geheimnis liegt |
|---|---|---|
| Systemnutzer `solvis` | führt den Dienst aus, liest `base.xml`, schreibt nur die Zustandsordner | – (Systemkonto, Shell `/bin/false`) |
| Anlagen-Account der SolvisRemote | HTTP-Digest-Login an der Web-GUI | `passwordCrypt` im `<tns:Unit>` der `base.xml` |
| MQTT-Login `solvis-local` | Connector → lokaler Broker | `passwordCrypt` im `<tns:Mqtt>` der `base.xml`; Gegenstück im lokalen Broker (`ccu2mqtt`) |
| Bridge-Identität `solvis-bridge` | lokaler Broker → Haus-Broker (mTLS) | Client-Zertifikat + Schlüssel auf dem Host, verwaltet von `ccu2mqtt` |
| SMTP-Konto (optional) | Fehler-Mail | `passwordCrypt` im `<tns:ExceptionMail>` der `base.xml` |

Behoben 2026-11-24 durch den Umzug in die FHS-Ablage. Frühere Drift (notiert 2026-11-18, CHANGELOG-fork.md): Auf `ransible` hatten `/opt/solvis` und der Programmordner
noch den Eigentümer `solvis`, `base.xml` stand auf `600 solvis:root`. Der nächste `make installSolvis`
bzw. `updateSolvis` setzt das Soll über `enforceOwnership`; die Härtung der Unit macht den Programmordner
schon vorher für den Dienst schreibgeschützt.

### 5.3 Erbschafts-Inventar (Restrisiken aus dem Upstream, je Posten eine Entscheidung)

- **Proprietärer TCP-/JSON-Server (Port 10735/10736), unauthentifiziert &
  unverschlüsselt (S-3):** *Entscheidung: bind-beschränkt + abschaltbar.* Bindet
  standardmäßig **nur auf `127.0.0.1`** (nicht mehr `0.0.0.0`) und ist
  vollständig deaktivierbar. Konfiguration (System-Property oder Umgebungs­variable):
  - `solvis.tcpServer.bindAddress` / `SOLVIS_TCPSERVER_BINDADDRESS` — Bind-Adresse,
    Default `127.0.0.1`; `0.0.0.0` = alle Interfaces (bewusstes Opt-in).
  - `solvis.tcpServer.enable` / `SOLVIS_TCPSERVER_ENABLE` — `false` schaltet den
    Server ganz ab (kein Listen-Socket; Betrieb läuft über MQTT).
  Im Compose-Deployment die Ports **nicht** veröffentlichen (`ports:` bewusst
  auskommentiert, nicht nur vergessen). Die systemd-Unit erlaubt ohnehin nur
  Binds auf diese beiden Ports.
- **`passwordCrypt` = Obfuskation, kein Schutz (S-2):** *Entscheidung: Dateirechte
  als realer Schutz.* Der AES-Schlüssel ist im öffentlichen Quellcode ableitbar
  (ECB). Der einzige wirksame Schutz der Secrets in `base.xml` sind Dateirechte:
  Soll der installierten Datei seit der S-5-Härtung `640 root:solvis` (der
  Dienst liest, darf aber nicht schreiben — sonst könnte ein kompromittierter
  Dienst Broker-URL und Zugangsdaten umschreiben), bei jedem
  `installSolvis`-/`updateSolvis`-Lauf durch das Makefile-Ziel
  `enforceOwnership` durchgesetzt; `chmod 600` nur für die Arbeitskopie beim
  Bearbeiten. Nach einem Restore setzt der `chown`/`chmod`-Block in
  [runbooks/README.md](runbooks/README.md) (Aufgabe Restore, Schritt 3) das
  Soll wieder. Beleg: [INBETRIEBNAHME.md](INBETRIEBNAHME.md) Phase 2 und
  Update-Abschnitt. Krypto-Umbau: Roadmap 4.6.
- **Anlagen-Zugriff nur über HTTP (S-8):** *Entscheidung: akzeptiertes Restrisiko.*
  Die SolvisRemote bietet kein HTTPS; Anlagen-Credentials und OCR-/Klick-Verkehr
  gehen im Klartext durchs LAN. Kompensation: Anlagen-/IoT-Segment, kein Routing
  aus untrusted Netzen, kein Gast-WLAN.

Nachweise (gitleaks-Befund, Secret-/PII-Handhabung): [security/README.md](security/README.md).

## 6. Offene Fragen (bewusst nicht geraten)

1. ~~**Produktive Betriebsvariante A oder B?**~~ **Geschlossen (2026-07-25):
   Variante A** (lokaler Broker + mTLS-Bridge) ist die unterstützte
   Produktivvariante, Variante B ist deprecated (§4). Grundlage: architect-
   Abstimmung „Option A", Betreiber-Entscheid 2026-07-21, ADR-0017 D1. Schließt
   zugleich ADR-0017 Folgeentscheidung 5.
2. ~~**Proprietärer TCP-Server (Port 10735) aktiv/exponiert?**~~ **Geschlossen
   (S-3, §5):** Default-Bind `127.0.0.1`, abschaltbar; im Stack ungenutzt, Ports
   nicht veröffentlicht.
3. **control.xml-Reader-Umstieg** (inkl. Hash-Frage) ist der letzte offene
   3.3-Schritt — erst danach kann die `XMLLibrary` vollständig entfallen
   (Status: MODERNISIERUNG.md 3.3, docs/jaxb-exploration.md).
4. **Upstream-Wiki-Abhängigkeit:** Kanal-Listen und MQTT-Schnittstellen-Doku
   liegen im Upstream-Wiki (GollmerSt). Ob/was davon in den Fork gespiegelt
   werden muss (Verfügbarkeitsrisiko bei unmaintained Upstream), ist offen.
5. **Wiederherstellungs-Lücken:** Was für einen Neuaufbau nicht aus dem Repo hervorgeht, steht in §10.3.

## 7. Laufzeitsicht

### 7.1 Messen (Regelbetrieb)

```text
  Timer (measurementsInterval_s, Vorlage: 10 s)
        │
        ▼
  GET /sc2_val.xml  (HTTP-Digest MD5) ──▶ SolvisRemote
        │ Hex-String
        ▼
  model/: Kanäle dekodieren, Berechnungen, Änderungserkennung
        │                       │
        │ geändert / erzwungen  │ periodisch und beim Stopp
        ▼                       ▼
  MQTT publish QoS 1       SolvisServerData/measurements.xml
  solvis/<unit>/<kanal>/data
        │
        ▼
  lokaler Mosquitto ──▶ Bridge (mTLS) ──▶ Haus-Broker ──▶ Home Assistant / Node-RED
```

Werte, die nur auf der Oberfläche stehen, liest der Server per OCR aus `/display.bmp` über die angelernten
Screens.

### 7.2 Steuern (nur mit `InteractiveGUIAccess="true"`)

```text
  Konsument ── solvis/<client>/<unit>/<kanal>/cmnd ──▶ Haus-Broker ──▶ Bridge ──▶ lokaler Mosquitto
                                                                                        │ Paho subscribe
                                                                                        ▼
  ┌──────────────────────────────────────────────────────────────────────────────────────────────┐
  │ 1 passenden Screen anfahren         Touch.CGI / Taster.CGI                                   │
  │ 2 aktuellen Wert lesen              display.bmp + OCR gegen LearnedImages                    │
  │ 3 Plus/Minus klicken, bis Zielwert  Touch.CGI                                                │
  │ 4 Ergebnis verifizieren             erneut OCR; echoInhibitTime_ms dämpft Echos              │
  └──────────────────────────────────────────────┬───────────────────────────────────────────────┘
                                                 ▼
                              solvis/<unit>/<kanal>/data (neuer Ist-Wert)
```

Bei Anlagenfehlern sperrt der Server je nach Feature (`ClearErrorMessageAfterMail`) die GUI-Steuerung oder
wechselt zum Home-Screen; optional geht eine Fehler-Mail mit Screenshot raus.

### 7.3 Start, Health und Stopp

```text
  systemctl start
        │
        ▼
  base.xml lesen, gegen Jar-interne base.xsd validieren
        │ <Ssl enable="true">? ──ja──▶ Abbruch mit klarer Ursache (Fail-Fast, Variante B)
        ▼ nein
  TCP-Server binden (127.0.0.1:10735/10736) oder abgeschaltet
        │
        ▼
  MQTT verbinden, Last Will solvis/server/online=false ──▶ online=true
        │
        ▼
  Health-Token SOLVIS_HEALTH_TOKEN_PATH alle 30 s erneuern; bei Verbindungsverlust entfernen
        │
  SIGTERM (systemctl stop, TimeoutStopSec=30)
        ▼
  measurements.xml schreiben, sauber trennen ──▶ solvis/server/online=false (retained)
```

### 7.4 Lernphase

```text
  Zwei-OCR-Clients-Verbot prüfen (Alt-Dienst, Test-Instanzen aus)
        │
        ▼
  sudo make learn  =  stopServices → installSolvis → java … --server-learn (mit MD5-Flag)
        │                                                   │ klickt real durch alle Screens
        ▼                                                   ▼
  systemctl start (Regelbetrieb)               SolvisServerData/LearnedImages, control.xml
```

Auslöser ist eine erkannte GUI-Änderung, kein Zeitplan ([runbooks/README.md](runbooks/README.md)).

## 8. Datensicht

```text
  Code und Doku                    Operator-State (Host ransible)              nicht im Scope solvis
  ┌───────────────────────────┐    ┌──────────────────────────────────────┐    ┌───────────────────────────────┐
  │ Repo: src, rsc, SmartHome │    │ /etc/solvis/, /usr/local/lib/solvis/ │    │ /etc/default/solvissmart-     │
  │ docs, pom, mvnw, CI,      │    │   base.xml  Konfiguration +          │    │   homeserver (Betriebsparam.) │
  │ local-maven-repo          │    │             passwordCrypt (640)      │    │ systemd-Units (aus dem Repo   │
  │ base.xml nur als Vorlage  │    │   base.xml.old  Vorstand (600 root)  │    │   neu erzeugbar)              │
  └─────────────┬─────────────┘    │   Jar, base.xsd  aus dem Build       │    │ Make-Parameter des Hosts      │
                │ git push         │ /var/lib/solvis/SolvisServerData/    │    │ Router: DHCP-Reservierung     │
                ▼                  │   LearnedImages, control.xml,        │    │ lokaler Mosquitto, Bridge,    │
  GitHub origin (Fork)             │   measurements.xml, graficData,      │    │   mTLS-Material (ccu2mqtt)    │
  + Upstream als Zweitquelle       │   Logs, SolvisErrorImages            │    │ Klartext-Zugangsdaten         │
                                   │ /run/solvis/ready (flüchtig)         │    └───────────────────────────────┘
                                   └──────────────────┬───────────────────┘
                                                      │ restic, Scope solvis, täglich 02:30
                                                      ▼
                                   zwei Ziele: HiDrive und all-inkl, Retention 14/8/12
                                   Skript, Units, Runbook: Repo ccu2mqtt
```

| Datum | Ort | Gesichert durch | Geheim? |
|---|---|---|---|
| Quellcode, Doku, Install-Ziele, Unit-Vorlagen | Repo | Git, GitHub `origin`; Repo-Clone-Drill [g2.6-clone-drill.md](g2.6-clone-drill.md) | nein |
| Uber-Jar | `target/`, installiert unter `/usr/local/lib/solvis/` | reproduzierbar aus dem Code; CI-Artefakt `SolvisSmartHomeServer-jar` (JDK-21-Lauf) | nein |
| `base.xml` (installiert) | `/usr/local/lib/solvis/` | restic-Host-Kette, Scope `solvis`; Handexport `tar`+`age` vor Eingriffen | **ja** (Anlagen-, MQTT-, ggf. SMTP-Zugang, nur obfuskiert) |
| Arbeitskopie `base.xml` | `SmartHome/Linux/` im Checkout auf dem Host (`.gitignore`) | nicht gesichert (Arbeitsstand) | **ja** |
| Lernbilder, generierte `control.xml`, Messwert-Backup, Logs | `/var/lib/solvis/SolvisServerData/` | restic-Host-Kette, Scope `solvis` | nein |
| Health-Token | `/run/solvis/ready` | nicht nötig (flüchtig) | nein |
| Betriebsparameter | `/etc/default/solvissmarthomeserver` | nicht im Scope `solvis`; Inhalt in [INBETRIEBNAHME.md](INBETRIEBNAHME.md) 4.3 dokumentiert | nein |
| Lokaler Broker, Bridge, Zertifikate, ACL | Host bzw. Haus-Broker | Repo und Backup-Kette von `ccu2mqtt` | **ja** |
| restic-Passwort, Zugänge der Backup-Ziele | außerhalb dieses Repos (`ccu2mqtt`) | – | **ja** |

Regel: Code geht nach Git und GitHub, Konfiguration mit Geheimnissen und Zustand gehen nur in die
verschlüsselte Host-Sicherung. Ein Backup gilt erst mit geübtem Restore als Backup; der Beleg für den Scope
`solvis` ist Registereintrag F-145 (laut [runbooks/README.md](runbooks/README.md) bis zum ersten belegten
Snapshot je Ziel UNVERIFIED; aktueller Stand in `ccu2mqtt/docs/status.md`).

## 9. Lieferung und Deploy

```text
  Arbeitsplatz              GitHub                                 Host ransible (Checkout des Repos)
  ┌───────────────┐ push    ┌────────────────────────────────┐     ┌──────────────────────────────────────────┐
  │ Branch        │────────▶│ origin feature/modernisierung  │     │ 1 git pull                               │
  │ feature/      │         │ CI build.yml: mvnw clean verify│────▶│ 2 ./mvnw -B clean package (frisch!)      │
  │ modernisie-   │         │   JDK 17 + 21, Jar-Artefakt    │ pull│ 3 sudo make updateSolvis                 │
  │ rung          │         │ Dependabot: Maven + Actions,   │     │     stopServices                         │
  └───────────────┘         │   wöchentlich                  │     │     installSolvis: checkJava, Jar,       │
                            └────────────────▲───────────────┘     │       base.xml, base.xsd, Units,         │
                                             │ git fetch upstream  │       enforceOwnership                   │
                            Upstream GollmerSt (master folgt)      │     systemctl start                      │
                                                                   │ 4 systemctl status, health/ready,        │
                                                                   │   mosquitto_sub solvis/#                 │
                                                                   └──────────────────────────────────────────┘
```

- **Kein Auto-Deploy.** Der Deploy-Schritt bleibt operatorgetrieben (der Start kann reale Klick-Steuerung
  auslösen); CI sichert nur den Build ab.
- **Nie ein liegengebliebenes `target/`-Jar ausrollen** ([INBETRIEBNAHME.md](INBETRIEBNAHME.md) Phase 1).
- `make prepare` stellt Jar und `base.xsd` neben das Makefile und legt `base.xml` nur aus der Vorlage an,
  wenn keine existiert. Die installierte `base.xml` wird nur ersetzt, wenn die Arbeitskopie neuer ist; der
  Vorstand bleibt als `base.xml.old` (root, `600`).
- Rollback: vorheriges Commit bauen und erneut `make updateSolvis`; `base.xml.old` als Vergleich. Rückweg
  auf die Alt-Installation `mon-dg` nur nach Stopp des Forks (Zwei-Clients-Verbot,
  [INBETRIEBNAHME.md](INBETRIEBNAHME.md) Phase 5).
- Day-2-Aufgaben mit Automatisierungsgrad: [runbooks/README.md](runbooks/README.md).

## 10. Wiederherstellung

Maßgeblich sind die Runbooks: [runbooks/README.md](runbooks/README.md) (Aufgaben „Backup“ und „Restore“)
für den Operator-State, [INBETRIEBNAHME.md](INBETRIEBNAHME.md) für Aufbau und Lernphase und in `ccu2mqtt`
`docs/runbooks/haus-hosts-backup.md` (§1.3, R-4/R-5), `runbooks/solvis-bridge-restore.md` und
`docs/runbooks/solvis-bridge-ransible.md` für Backup-Kette, Bridge und lokalen Broker. Hier steht, wie das
zusammenhängt und **wann welcher Zugang, welches Geheimnis, welche Einstellung und welche Quelle** gebraucht
wird. Der Gesamtablauf für einen verlorenen Host ist aus diesen Quellen und dem Makefile zusammengesetzt und
als Ganzes **nicht geübt** (Lücke L-7).

Quellen:

| Kürzel | Quelle | Enthält |
|---|---|---|
| **G** | GitHub `origin` (Fork), ersatzweise Upstream | Code, Makefile, Unit-Vorlagen, Doku, `local-maven-repo` |
| **B** | restic-Host-Kette (HiDrive, all-inkl), Scope `solvis` | `base.xml` mit allen `passwordCrypt`-Werten, `SolvisServerData` (Lernbilder, `control.xml`, Messwerte) |
| **C** | Repo `ccu2mqtt` und dessen Sicherung | lokaler Broker, Bridge, mTLS-Material, ACL, Backup-Skript und -Units, Restore-Runbooks |
| **I** | öffentliche Paketquellen | Debian-Pakete (Java ≥ 17, make, git, Mosquitto-Clients, restic), Maven Central für den Build |
| **V** | vor Ort | Raspberry Pi, SolvisRemote und Anlage, Router |
| **?** | im Repo nicht belegt | Ablage der Zugänge und Klartext-Geheimnisse (§10.3) |

### 10.1 Ablauf

Marker: `Zugang` = Konto oder Zugangsweg, `Geheimnis` = Passwort, Schlüssel oder Zertifikat,
`Einstellung` = Konfiguration, `[G] [B] [C] [I] [V] [?]` = Quelle.

```text
                                  ┌───────────┐
                                  │  Ausfall  │
                                  └─────┬─────┘
                                        ▼
                                Was ist verloren?
       ┌────────────────────────────────┼────────────────────────────────────┐
       │ Konfiguration oder Zustand,    │ Lernbilder passen nicht            │ Host ransible
       │ Host läuft                     │ (GUI der Anlage geändert)          │ (Gerät, Speicherkarte)
       ▼                                │                                    ▼
  Runbook „Restore“                     │      ╔═ H: Host bereitstellen ═════════════════════════════════════════
  Dienst stoppen, restic restore  [B]   │      ║ H1 RPi 4 + Debian 13 arm64        Zugang: Gerät, Konsole oder
  --tag solvis, Rechte setzen           │      ║                                   SSH auf den Host [V] [?]
  (Schritt 3), Dienst starten           │      ║ H2 Netz zur SolvisRemote          Einstellung: Router-DHCP-
  Geheimnis: restic-Passwort,           │      ║                                   Reservierung, Zugang: Router [?]
  Ziel-Zugang  [C] [?]                  │      ║ H3 Java ≥ 17, make, git, restic,  [I]
       │                                │      ║    mosquitto-clients
       │                                │      ║ H4 lokaler Mosquitto + Bridge     Einstellung: local.conf, Bridge;
       │                                │      ║    (localhost-only, :1883)        Geheimnis: Login solvis-local,
       │                                │      ║                                   mTLS-Cert/Key solvis-bridge [C]
       │                                │      ╚═══════════════════════════╤══════════════════════════════════════
       │                                │                                  ▼
       │                                │      ╔═ R: Operator-State zurückholen ═════════════════════════════════
       │                                │      ║ R1 restic restore latest          Zugang: eines der zwei Ziele;
       │                                │      ║    --tag solvis --target /        Geheimnis: restic-Passwort,
       │                                │      ║                                   Ziel-Zugang [C] [?]
       │                                │      ║    liefert base.xml + SolvisServerData  [B]
       │                                │      ║ R2 kein Snapshot? base.xml aus    Geheimnis: Anlagen-Account +
       │                                │      ║    Vorlage neu (INBETRIEBNAHME    Passwort, MQTT-Passwort [?];
       │                                │      ║    Phase 2/3, make crypt)         Einstellung: Unit-Typ, Heizung,
       │                                │      ║                                   url = gepinnte IP [V]
       │                                │      ╚═══════════════════════════╤══════════════════════════════════════
       │                                │                                  ▼
       │                                │      ╔═ W: Dienst installieren ════════════════════════════════════════
       │                                │      ║ W1 Repo klonen, Branch            Code [G]
       │                                │      ║    feature/modernisierung
       │                                │      ║ W2 ./mvnw -B clean package        Abhängigkeiten [I], XMLLibrary [G]
       │                                │      ║ W3 make prepare, dann die zurück- Einstellung: base.xml [B]; sonst
       │                                │      ║    geholte base.xml als Arbeits-  ersetzt die frische Vorlage die
       │                                │      ║    kopie nach SmartHome/Linux/    installierte Datei (Zeitstempel-
       │                                │      ║    (chmod 600)                    regel im Makefile)
       │                                │      ║ W4 /etc/default/solvissmart-      Einstellung: SOLVIS_HEALTH_TOKEN_
       │                                │      ║    homeserver anlegen             PATH (INBETRIEBNAHME 4.3) [G]
       │                                │      ║ W5 sudo make installSolvis        Einstellung: javaPath,
       │                                │      ║    javaPath=… solvisAllowedNet=…  solvisAllowedNet (Hausnetz) [?]
       │                                │      ║ W6 Rechte wie Runbook „Restore“   chown -R solvis SolvisServerData,
       │                                │      ║    Schritt 3                      base.xml 640 root:solvis
       │                                │      ║ W7 make checkCredentials          Geheimnis: Klartext-Passwörter
       │                                │      ║    (empfohlen, read-only)         Anlage + MQTT [?]
       │                                │      ╚═══════════════════════════╤══════════════════════════════════════
       │                                ▼                                  ▼
       │                         ╔═ L: Lernphase (nur wenn LearnedImages fehlen oder nicht passen) ════════════
       │                         ║ L1 Zwei-OCR-Clients-Verbot: Alt-Dienst mon-dg aus, keine Test-Instanz
       │                         ║ L2 sudo make learn                 Zugang: Anlage betriebsbereit [V]
       │                         ║ L3 InteractiveGUIAccess bewusst wählen (false = nur Monitoring)
       │                         ╚═════════════════════════════════════╤═════════════════════════════════════════
       │                                                               ▼
       │                         ╔═ A: Abnahme ════════════════════════════════════════════════════════════════
       └────────────────────────▶║ A1 systemctl enable --now; status active (running)
                                 ║ A2 health/ready jünger als 5 min
                                 ║ A3 mosquitto_sub -t 'solvis/#' lokal   Geheimnis: Login solvis-local [?]
                                 ║ A4 Ende-zu-Ende hinter der Bridge (ccu2mqtt solvis-bridge-ransible §6c) [C]
                                 ║ A5 Ergebnis und Dauer als Restore-Beleg (F-145) in ccu2mqtt eintragen
                                 ╚═════════════════════════════════════════════════════════════════════════════
```

### 10.2 Wann was gebraucht wird

| Schritt | Zugang | Geheimnis | Einstellung | Quelle |
|---|---|---|---|---|
| H1 Host aufsetzen | Gerät vor Ort, Konsole oder SSH | Host-Konto (Ablage nicht belegt) | Debian 13 arm64 | V, ? |
| H2 Netz | Router | Router-Zugang | DHCP-Reservierung der SolvisRemote (Hostname nicht verwenden, F-119) | V, ? |
| H3 Pakete | Paketquellen | – | Java ≥ 17 (Referenz OpenJDK 21) | I |
| H4 lokaler Broker + Bridge | Host | Login `solvis-local`, mTLS-Cert/Key `solvis-bridge`, CA | `local.conf`, Bridge `solvis-bridge-to-primary` | C |
| R1 State zurückholen | eines der zwei Backup-Ziele | restic-Repository-Passwort, Ziel-Zugang | Scope/Tag `solvis` | C, B |
| R2 Neuaufbau ohne Backup | SolvisRemote im LAN | Anlagen-Account + Passwort, MQTT-Passwort | Unit-Attribute, `url` (gepinnte IP), `<tns:Mqtt>` (`127.0.0.1:1883`, `topicPrefix="solvis"`) | ?, V |
| W1–W2 Code und Build | GitHub (lesend), Maven Central | – | Branch `feature/modernisierung` | G, I |
| W3 `base.xml` bereitstellen | Host | in `base.xml` (obfuskiert) | Arbeitskopie neuer als die Vorlage | B |
| W4 Betriebsparameter | Host (root) | – | `SOLVIS_HEALTH_TOKEN_PATH=/run/solvis/ready` | G (Doku) |
| W5 Installieren | Host (root) | – | `javaPath`, `solvisAllowedNet` | G, ? |
| W6 Rechte | Host (root) | – | `640 root:solvis`, Zustandsordner dem Dienstnutzer | G (Runbook) |
| W7 Credential-Prüfung | SolvisRemote, lokaler Broker | Klartext-Passwörter Anlage + MQTT | – | ? |
| L Lernphase | Anlage betriebsbereit | (aus `base.xml`) | `InteractiveGUIAccess` | V |
| A Abnahme | Host, lokaler Broker, ggf. Haus-Broker | Login `solvis-local` | – | C |

Die Geheimnisse für den Betrieb (Anlagen-, MQTT-, ggf. SMTP-Zugang) kommen vollständig aus **B**
(`base.xml`). Neue Werte braucht es nur ohne Snapshot (R2). Die Bridge-Identität kommt aus **C**, nie aus
diesem Repo.

### 10.3 Lücken

Diese Punkte gehen aus dem Repo nicht hervor. Sie werden hier benannt, nicht geraten:

| Nr. | Lücke | Wirkung |
|---|---|---|
| L-1 | Zugangsweg und Konten für den Host `ransible` (SSH, Schlüssel, Konsole) | ohne sie kein Restore auf dem bestehenden Host |
| L-2 | Ablage des restic-Repository-Passworts und der Zugänge zu HiDrive/all-inkl; nur Verweis auf `ccu2mqtt` | ohne sie ist **B** unerreichbar |
| L-3 | Ablage der Klartext-Zugangsdaten (Anlagen-Account der SolvisRemote, MQTT `solvis-local`, ggf. SMTP) außerhalb von `base.xml` | nötig für R2 und `checkCredentials`; `passwordCrypt` ist nur Obfuskation, kein Speicherort |
| L-4 | Router-Zugang und Sicherung der DHCP-Reservierung der SolvisRemote; Bereinigung des Hostnamens offen (F-119) | ohne Reservierung wechselt die Adresse, `url` in `base.xml` passt nicht mehr |
| ~~L-5~~ | geschlossen 2026-11-24: `/etc/default/solvissmarthomeserver` ist im Scope `solvis` | gering: Inhalt ist in INBETRIEBNAHME 4.3 dokumentiert |
| L-6 | Konkreter Wert von `solvisAllowedNet` (laut CHANGELOG „das /24 des Hausnetzes“) und `javaPath` auf `ransible` | Vorgabe des Makefiles (alle privaten Netze) funktioniert, ist aber weiter als das Soll |
| L-7 | Gesamtablauf „Host verloren“ nie geübt; Restore-Beleg für Scope `solvis` (F-145) laut Runbook offen | Dauer und Vollständigkeit unbekannt |
| L-8 | Ob `ExceptionMail` produktiv aktiv ist | bestimmt, ob ein SMTP-Zugang zu den Geheimnissen gehört |
| L-9 | Upstream-Wiki (Kanal-Listen, MQTT-Schnittstelle) nicht gespiegelt (§6, Frage 4) | Wissen für Neueinrichtung hängt am unmaintained Upstream |

## 11. Entscheidungen

| Nr. | Entscheidung | Begründung / Folge | Beleg |
|---|---|---|---|
| E1 | OCR-Kern nicht neu bauen, isolieren und mit Golden-Tests pinnen | Alleinstellungsmerkmal des Upstreams; Upstream-Diff klein halten | MODERNISIERUNG.md |
| E2 | Variante A: lokaler Broker + mTLS-Bridge; Variante B deprecated mit Fail-Fast | Paho v3 kann `<Ssl>` hier nicht (32105); Stack-Muster wie CCU-Jack. Rückwechsel ADR-pflichtig | §4, 2026-07-25 |
| E3 | Nativer systemd-Dienst (A3) statt Docker im Produktivbetrieb | Echtes Host-Loopback, Stack-Konvention; Docker nur Entwicklung/Test | Pfadentscheid O1, 2026-08-12 |
| E4 | Ausschließlich Namensraum `solvis/#` | Vertrag mit der Haus-MQTT-Architektur (`ccu2mqtt` ist Schema-Owner) | ADR-0017 D1 |
| E5 | Maven, Java-17-Baseline, CI auf 17 und 21, Dependabot | wartbarer Build auf aktuellen Laufzeiten | FORK.md, Ziel 2 |
| E6 | Dateirechte statt Krypto als Schutz der `base.xml` (`640 root:solvis`, `enforceOwnership` bei jedem Lauf) | `passwordCrypt` ist Obfuskation; Dienst darf seine Konfiguration nicht umschreiben | S-2, S-5 (2026-08-22) |
| E7 | TCP-/JSON-Server nur auf Loopback, abschaltbar | unauthentifiziertes Upstream-Erbe, im Stack ungenutzt | S-3 |
| E8 | HTTP zur SolvisRemote als akzeptiertes Restrisiko; MD5-Digest nur für diesen Prozess erlaubt | Gerät kann kein HTTPS und Digest nur mit MD5 | S-8, 2026-08-13 |
| E9 | SolvisRemote per IP pinnen, nicht per Hostname | verwaister Router-Eintrag zeigt auf eine Boot-Übergangsadresse | F-119, INBETRIEBNAHME.md |
| E10 | Kein Auto-Deploy, Start bewusst manuell | Start kann reale Klicks an der Heizung auslösen | runbooks/README.md |
| E11 | Backup als Host-Eigenschaft in `ccu2mqtt` (restic, zwei Ziele), Connector ohne Sicherungslogik | gemeinsame Ziele und Retention, keine Ziel-Zugangsdaten im Connector | 2026-08-28 |
| E12 | Gehärtete systemd-Unit (`ProtectSystem=strict`, Netz- und Bind-Grenzen), bewusst ohne `MemoryDenyWriteExecute` und `ProcSubset=pid` | Fehler im Prozess kann System und Installation nicht verändern; JVM braucht JIT und `/proc` | CHANGELOG-fork.md, 2026-11-18 |
| E13 | Code → GitHub, Konfiguration mit Geheimnissen und Zustand → nur verschlüsselte Host-Sicherung | keine Secrets im Repo (`.gitignore`, gitleaks-Befund) | security/README.md |
| E14 | `docu/` bleibt Upstream-Stand | Fork-Etikette | dieses Dokument |
