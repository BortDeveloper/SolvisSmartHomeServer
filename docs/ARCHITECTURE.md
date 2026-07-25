# Architektur (Fork-Sicht)

Konsolidierte Architektur-Übersicht dieses Forks: Lineage, Laufzeit- und
Deployment-Bild, Betriebsmodell und bewusste Grenzen — an **einer** Stelle,
statt verstreut über [FORK.md](../FORK.md), [MODERNISIERUNG.md](../MODERNISIERUNG.md)
und [TESTPLAN.md](../TESTPLAN.md). Diese Dokumente bleiben die Detailquellen;
hier steht die Gesamtsicht.

> ⚠️ **Abgrenzung zum Verzeichnis `docu/`:** `docu/` ist **Upstream-Stand**
> (Original-Dokumentation von GollmerSt, u. a. Word/PDF, Screenshots,
> Modbus-Spezifikationen) und beschreibt **nicht** den Zustand dieses Forks
> (z. B. noch Ant-Build, tinylog, unverschlüsseltes MQTT). Für den Fork gelten
> **dieses Dokument**, [FORK.md](../FORK.md) und
> [CHANGELOG-fork.md](../CHANGELOG-fork.md). `docu/` wird bewusst nicht
> umgeschrieben (Fork-Etikette: Upstream-Artefakte bleiben erhalten).

## 1. Fork-Lineage

```text
GollmerSt/SolvisSmartHomeServer  (Upstream, MIT; unmaintained seit Anfang 2023,
        │                         letztes Release v01.05.01, Dez 2021)
        ▼
BortDeveloper/SolvisSmartHomeServer  (dieser Fork)
        master                → folgt dem Upstream
        feature/modernisierung → betriebsspezifische Arbeit (dieser Stand)
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
| Build | Ant + eingecheckte `lib/`-Jars | Maven (`./mvnw -B clean package`, Uber-Jar via shade), CI mit JDK-Matrix 17/21 |
| Java-Baseline | Java 8 | **17** (läuft auf 17/21; Zielhost OpenJDK 21) |
| MQTT-Transport | nur unverschlüsselt | **natives mTLS** implementiert (`<Ssl>` in `base.xml`, PKCS#8-Client-Key); zusätzlich dokumentiertes Bridge-Betriebsmodell (§4) |
| Logging | Eigenfassade + tinylog/log4j | SLF4J + Logback |
| Mail | `javax.mail`, `ssl.trust="*"` | `jakarta.mail` (angus-mail), Zertifikatsprüfung aktiv |
| XML-Binding | proprietäre `XMLLibrary` + 67 Creator-Klassen | schrittweise JAXB (base.xml/measurements.xml/graficData.xml fertig; control.xml: alle 13 Zweige gebunden, Reader-Umstieg offen) |
| Deployment | Zip-Release Linux/Windows | zusätzlich **Docker** (Zwei-Stufen-Build, headless, arm64/amd64 — [DOCKER.md](DOCKER.md), [INBETRIEBNAHME.md](INBETRIEBNAHME.md)) |

## 2. System-Kontext (Laufzeit-Bild)

```text
┌──────────────────────┐   HTTP (Web-GUI,        ┌─────────────────────────────────┐
│ SolvisMax 6/7        │   Pixelbild + Klicks)   │ SolvisSmartHomeServer (Fork)    │
│ SolvisControl 2      │◀───────────────────────▶│ Java-17-Prozess (Jar/Container) │
│ + SolvisRemote       │                         │ · OCR-Screen-Erkennung          │
└──────────────────────┘                         │ · Steuerung + OCR-Verifikation  │
                                                 │ · MQTT-Client (Paho)            │
                                                 └───────────────┬─────────────────┘
                                                                 │ nur solvis/#
                                             Variante A          │          Variante B
                                     ┌───────────────────────────┴───────────────┐
                                     ▼                                           ▼
                       ┌──────────────────────────┐                 (natives mTLS direkt,
                       │ lokaler Mosquitto,       │                  Fork-Feature <Ssl>)
                       │ 127.0.0.1 (gleicher Host)│                              │
                       └────────────┬─────────────┘                              │
                                    │ mTLS-Bridge (Login solvis-bridge)          │
                                    ▼                                            ▼
                       ┌──────────────────────────────────────────────────────────┐
                       │ zentraler Haus-Broker (Mosquitto, mTLS + ACL)            │
                       │ ACL für diesen Connector: readwrite solvis/#             │
                       └────────────┬─────────────────────────────────────────────┘
                                    ▼
                       Konsumenten: Home Assistant / Node-RED (Automationsebene)
```

- **Anlagen-Seite:** Die SolvisRemote liefert eine Pixelkopie der
  SolvisControl-2-Oberfläche per HTTP. Der Server liest Werte per OCR
  (reines Java, kein Tesseract), steuert per simulierten Klicks und
  verifiziert Änderungen wieder per OCR. Einmalige **Lernphase**
  (`--server-learn`) erzeugt `LearnedImages` im Schreibpfad
  (`writablePathLinux`, im Container das `/data`-Volume).
- **MQTT-Seite (Vertrag):** Dieser Connector publiziert und konsumiert
  **ausschließlich den Namensraum `solvis/#`** (`topicPrefix="solvis"` in
  `base.xml`). Topic-Schema: Status `solvis/<unit>/<kanal>/data`, Kommandos
  `solvis/<client>/<unit>/<kanal>/cmnd`, Metadaten `…/meta`, Serverstatus
  `solvis/server/online` (mit Last Will). Andere Topics, andere Projekte,
  Broker-Interna: kennt dieser Kontext nicht. Die Gegenseite (Broker-ACL,
  Bridge-Konfiguration, Login `solvis-bridge`) ist im Vertragspartner-Repo
  `ccu2mqtt` dokumentiert (`docs/solvis.md`, dort §7 Umsetzungsplan).
- **Nebenschnittstelle (Upstream-Erbe):** ein proprietärer TCP-Server
  (JSON, Default-Port 10735) für FHEM-/ioBroker-Clients. Im Zielbetrieb
  dieses Stacks ungenutzt (siehe offene Fragen, §6).

## 3. Baustein-Sicht (Quellcode)

Wurzelpaket `de.sgollmer.solvismax` (unter `src/`):

| Baustein | Verantwortung |
|---|---|
| `imagepatternrecognition/` (`image`, `ocr`, `pattern`) | **OCR-Kern** — isoliert, minimale Abhängigkeiten (nur JDK-Bildklassen), Modulgrenze in `package-info.java` dokumentiert, durch 31 Golden-Tests gepinnt. Wird bewusst **nicht** umgeschrieben (Alleinstellungsmerkmal, Urheber GollmerSt). |
| `model/` | Anlagen-/Domänenmodell: Screens, Kanäle, Messwerte, Strategien; Konfiguration aus `base.xml`/`control.xml` |
| `connection/` (`mqtt`, `transfer`) | MQTT-Client (Paho v3) inkl. `MqttThread`/Topic-Aufbau; proprietärer TCP-Server |
| `xml/` + JAXB-DTOs | Konfigurations-Parsing: Übergang `XMLLibrary`-Creators → JAXB („Weg B": DTOs werden das Config-Modell, Sichten wie `MqttConnectionConfig`, `UnitConfig`, `ExecutionConfig`), dual-parse-abgesichert |
| `crypt/` | `CryptAes` für `passwordCrypt`-Werte — **Obfuskation, kein echter Schutz** (hartkodierter Schlüssel, ECB); ehrliche Behandlung ist Roadmap-Punkt 4.6 |
| `mail/` | optionale Fehler-Mail (jakarta.mail, Screenshot-Anhang, TLS-Zertifikatsprüfung aktiv) |
| `smarthome/` | Integrations-Helfer (u. a. ioBroker-Generierung) |
| `windows/` | optionale, JDK-only Windows-Hilfe (`--create-task-xml`); auf anderen Plattformen ungenutzt |

Build: Maven-Wrapper, JDK 17+ → `target/SolvisSmartHomeServer.jar` (Uber-Jar).
Die proprietäre `XMLLibrary` ist bis zur vollständigen JAXB-Ablösung in
`local-maven-repo/` vendored.

## 4. Betriebsmodell

Zielumgebung ist ein Smart-Home-Stack, dessen MQTT-Verkehr ausschließlich
über einen **mTLS-gehärteten zentralen Mosquitto** läuft (kein
Klartext-Listener, Client-Zertifikate + Broker-ACL). Zwei Varianten:

- **Variante A — lokaler Broker + mTLS-Bridge (Zielbild lt. FORK.md):**
  Der Server publiziert unverschlüsselt auf einen **lokalen, auf
  `127.0.0.1` gebundenen** Mosquitto auf demselben Host; dieser koppelt per
  mTLS-Bridge (`topic solvis/# both`, Login `solvis-bridge`) an den
  zentralen Broker. Dasselbe Muster nutzt der Gesamt-Stack für CCU-Jack-
  und FHEM-Anbindung. TLS im Server selbst ist hier nicht erforderlich.
- **Variante B — natives mTLS (Fork-Feature):** Der Server verbindet sich
  direkt per mTLS (`<Ssl>` im `<Mqtt>`-Element: CA-, Client-Zertifikat,
  PKCS#8-Key; bei Zertifikatsfehlern bricht die Verbindung bewusst ab —
  kein Klartext-Fallback). Handshake-verifiziert (CHANGELOG-fork.md);
  Container-Nutzung in [INBETRIEBNAHME.md](INBETRIEBNAHME.md).

Deployment: headless (Dienst per systemd oder **Docker-Container**;
`java.desktop`-Modul erforderlich, `/data`-Volume für Lernphase/Laufzeitdaten,
`/certs` read-only für Zertifikate). Zielhost-Referenz: arm64/Debian,
OpenJDK 21. Secrets (Zertifikate, Schlüssel, Anlagen-Zugangsdaten) liegen
**nie im Repo** — nur als gemountete Dateien bzw. `passwordCrypt`-Werte in
der lokalen `base.xml` (die selbst nicht eingecheckt wird).

Qualitätssicherung: [TESTPLAN.md](../TESTPLAN.md) (Phasen 1–3 ohne Anlage,
4–8 mit Anlage); CI baut/testet auf JDK 17 und 21; OCR per Golden-Tests,
Config-Parsing per Dual-Parse-Vergleich abgesichert. Tests gegen die
**Live-Anlage** nur mit explizitem Betreiber-Auftrag — die Steuerung klickt
real auf der Anlagen-Oberfläche.

## 5. Grenzen — was dieser Fork bewusst NICHT tut

- **Keine Topics außerhalb `solvis/#`.** Andere Namensräume zu belegen wäre
  ein Vertragsbruch gegenüber der Haus-MQTT-Architektur (Topic-Schema-Owner
  ist das Repo `ccu2mqtt`).
- **Kein Broker-Betrieb, keine ACL-Definition.** Broker und ACL-SSOT sind
  eigene Projekte; dieser Connector ist reiner MQTT-Client.
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

## 6. Offene Fragen (bewusst nicht geraten)

1. **Produktive Betriebsvariante A oder B?** FORK.md nennt Variante A
   (lokaler Broker + Bridge) als Zielbild, natives mTLS (B) ist implementiert
   und verifiziert. Der Entscheid fällt bei der Bridge-Inbetriebnahme
   (Umsetzungsplan liegt auf der ccu2mqtt-Seite, dort §7) und wird dann hier
   nachgetragen.
2. **Proprietärer TCP-Server (Port 10735):** bleibt er im Zielbetrieb
   aktiv/exponiert oder wird er deaktiviert? (Im Stack ungenutzt; Upstream
   bietet keinen Config-Schalter-Überblick — zu klären, bevor der Dienst
   dauerhaft läuft.)
3. **control.xml-Reader-Umstieg** (inkl. Hash-Frage) ist der letzte offene
   3.3-Schritt — erst danach kann die `XMLLibrary` vollständig entfallen
   (Status: MODERNISIERUNG.md 3.3, docs/jaxb-exploration.md).
4. **Upstream-Wiki-Abhängigkeit:** Kanal-Listen und MQTT-Schnittstellen-Doku
   liegen im Upstream-Wiki (GollmerSt). Ob/was davon in den Fork gespiegelt
   werden muss (Verfügbarkeitsrisiko bei unmaintained Upstream), ist offen.
