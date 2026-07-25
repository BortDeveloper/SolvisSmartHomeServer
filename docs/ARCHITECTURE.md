# Architektur (Fork-Sicht)

> **Sprache:** Deutsch · **Status:** aktiv · **Zielgruppe:** Programmierer ·
> **Bezug:** ADR-0017, [FORK.md](../FORK.md), [MODERNISIERUNG.md](../MODERNISIERUNG.md)

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
| MQTT-Transport | nur unverschlüsselt | **lokaler Broker + mTLS-Bridge** (Variante A, unterstützt — §4). Natives `<Ssl>`-mTLS (Variante B) ist **deprecated/nicht unterstützt** (Fail-Fast-Guard beim Start; §4, [mtls-behebung-vorschlag.md](mtls-behebung-vorschlag.md)) |
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
                                        Variante A (unterstützt) │   Variante B (deprecated,
                                     ┌───────────────────────────┴───────────────┐  nicht unterstützt)
                                     ▼                                           ▼
                       ┌──────────────────────────┐                 (natives mTLS direkt <Ssl>;
                       │ lokaler Mosquitto,       │                  Start bricht per Fail-Fast-
                       │ 127.0.0.1 (gleicher Host)│                  Guard ab — siehe §4)
                       └────────────┬─────────────┘
                                    │ mTLS-Bridge (Login solvis-bridge)
                                    ▼
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
- **Nebenschnittstelle (Upstream-Erbe):** ein proprietärer, unauthentifizierter
  TCP-Server (JSON, Default-Port 10735/10736) für FHEM-/ioBroker-Clients. Im
  Zielbetrieb dieses Stacks ungenutzt; er bindet daher standardmäßig **nur auf
  `127.0.0.1`** und ist vollständig abschaltbar (Konfiguration siehe §5).

## 3. Baustein-Sicht (Quellcode)

Wurzelpaket `de.sgollmer.solvismax` (unter `src/`):

| Baustein | Verantwortung |
|---|---|
| `imagepatternrecognition/` (`image`, `ocr`, `pattern`) | **OCR-Kern** — isoliert, minimale Abhängigkeiten (nur JDK-Bildklassen), Modulgrenze in `package-info.java` dokumentiert, durch 31 Golden-Tests gepinnt. Wird bewusst **nicht** umgeschrieben (Alleinstellungsmerkmal, Urheber GollmerSt). |
| `model/` | Anlagen-/Domänenmodell: Screens, Kanäle, Messwerte, Strategien; Konfiguration aus `base.xml`/`control.xml` |
| `connection/` (`mqtt`, `transfer`) | MQTT-Client (Paho v3) inkl. `MqttThread`/Topic-Aufbau; proprietärer TCP-Server |
| `xml/` + JAXB-DTOs | Konfigurations-Parsing: Übergang `XMLLibrary`-Creators → JAXB („Weg B": DTOs werden das Config-Modell, Sichten wie `MqttConnectionConfig`, `UnitConfig`, `ExecutionConfig`), dual-parse-abgesichert |
| `crypt/` | `CryptAes` für `passwordCrypt`-Werte — **Obfuskation, kein echter Schutz** (hartkodierter Schlüssel, ECB; Schlüssel aus dem öffentlichen Quellcode ableitbar). Der einzige reale Schutz der Secrets in `base.xml` sind **Dateirechte** (`chmod 600`, Owner = Dienstnutzer — [INBETRIEBNAHME.md](INBETRIEBNAHME.md) Phase 2). Ehrliche Behandlung ist Roadmap-Punkt 4.6 |
| `mail/` | optionale Fehler-Mail (jakarta.mail, Screenshot-Anhang, TLS-Zertifikatsprüfung aktiv) |
| `smarthome/` | Integrations-Helfer (u. a. ioBroker-Generierung) |
| `windows/` | optionale, JDK-only Windows-Hilfe (`--create-task-xml`); auf anderen Plattformen ungenutzt |

Build: Maven-Wrapper, JDK 17+ → `target/SolvisSmartHomeServer.jar` (Uber-Jar).
Die proprietäre `XMLLibrary` ist bis zur vollständigen JAXB-Ablösung in
`local-maven-repo/` vendored.

## 4. Betriebsmodell

> **Entscheidung (2026-07-25): Variante A ist die unterstützte
> Produktivvariante; Variante B ist deprecated/nicht unterstützt.** Damit ist
> die frühere offene Frage 1 (§6) geschlossen und ADR-0017 Folgeentscheidung 5
> (konsolidierte Betriebsmodell-Sicht in **einem** Dokument) erfüllt. Grundlage:
> architect-Abstimmung „Option A, MITTRAGEN mit Auflagen A-1…A-4"
> (`stack-master:shared/audit-log/2026-07-25-architect-solvis-mtls-abstimmung.md`),
> Betreiber-Entscheid 2026-07-21 „Option A" (`ccu2mqtt:docs/solvis.md` §6),
> ADR-0017 D1 („ausschließlich `solvis/#` über lokalen Broker + mTLS-Bridge").
> Kein Shared-ADR nötig; nur ein Rückwechsel auf B wäre ADR-pflichtig.

Zielumgebung ist ein Smart-Home-Stack, dessen MQTT-Verkehr ausschließlich
über einen **mTLS-gehärteten zentralen Mosquitto** läuft (kein
Klartext-Listener, Client-Zertifikate + Broker-ACL).

- **Variante A — lokaler Broker + mTLS-Bridge (unterstützt, Zielbild lt.
  FORK.md):** Der Server publiziert unverschlüsselt auf einen **lokalen, auf
  `127.0.0.1` gebundenen** Mosquitto auf demselben Host; dieser koppelt per
  mTLS-Bridge (`topic solvis/# both`, Login `solvis-bridge`) an den
  zentralen Broker. Dasselbe Muster nutzt der Gesamt-Stack für CCU-Jack-
  und FHEM-Anbindung. TLS im Server selbst ist hier nicht erforderlich. Die
  Gegenseite (Cert `solvis-bridge`, ACL `readwrite solvis/#`, Bridge-Plan) ist
  bei `ccu2mqtt` (`docs/solvis.md` §7) bereits provisioniert.

  **Container-Netz (damit `127.0.0.1` echtes Host-Loopback bleibt):** Der Hop
  Connector → lokaler Broker rechtfertigt seinen Klartext nur bei echtem
  Loopback. Zwei konventionstreue Auflösungen:
  - **A3 (bevorzugt, Stack-Konvention):** Server nativ auf dem Host (wie
    CCU-Jack / FHEM-Bridge), dortiger Mosquitto localhost-only. `127.0.0.1`
    ist Host-Loopback; die Docker-Deploymentlinie wird dann nicht genutzt.
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

### Erbschafts-Inventar (Restrisiken aus dem Upstream, je Posten eine Entscheidung)

- **Proprietärer TCP-/JSON-Server (Port 10735/10736), unauthentifiziert &
  unverschlüsselt (S-3):** *Entscheidung: bind-beschränkt + abschaltbar.* Bindet
  standardmäßig **nur auf `127.0.0.1`** (nicht mehr `0.0.0.0`) und ist
  vollständig deaktivierbar. Konfiguration (System-Property oder Umgebungs­variable):
  - `solvis.tcpServer.bindAddress` / `SOLVIS_TCPSERVER_BINDADDRESS` — Bind-Adresse,
    Default `127.0.0.1`; `0.0.0.0` = alle Interfaces (bewusstes Opt-in).
  - `solvis.tcpServer.enable` / `SOLVIS_TCPSERVER_ENABLE` — `false` schaltet den
    Server ganz ab (kein Listen-Socket; Betrieb läuft über MQTT).
  Im Compose-Deployment die Ports **nicht** veröffentlichen (`ports:` bewusst
  auskommentiert, nicht nur vergessen).
- **`passwordCrypt` = Obfuskation, kein Schutz (S-2):** *Entscheidung: Dateirechte
  als realer Schutz.* Der AES-Schlüssel ist im öffentlichen Quellcode ableitbar
  (ECB). Der einzige wirksame Schutz der Secrets in `base.xml` sind Dateirechte —
  `chmod 600 base.xml`, Owner = Dienstnutzer (Pflichtschritt
  [INBETRIEBNAHME.md](INBETRIEBNAHME.md) Phase 2). Krypto-Umbau: Roadmap 4.6.
- **Anlagen-Zugriff nur über HTTP (S-8):** *Entscheidung: akzeptiertes Restrisiko.*
  Die SolvisRemote bietet kein HTTPS; Anlagen-Credentials und OCR-/Klick-Verkehr
  gehen im Klartext durchs LAN. Kompensation: Anlagen-/IoT-Segment, kein Routing
  aus untrusted Netzen, kein Gast-WLAN.

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
