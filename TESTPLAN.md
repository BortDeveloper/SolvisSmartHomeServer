# Testplan — SolvisSmartHomeServer (Fork)

> **Sprache:** Deutsch · **Status:** aktiv · **Zielgruppe:** Operateure,
> Programmierer · **Bezug:** [README.md](README.md), [FORK.md](FORK.md),
> [CHANGELOG-fork.md](CHANGELOG-fork.md)

Dieser Plan hilft, nachvollziehbar zu prüfen, ob der
SolvisSmartHomeServer in der eigenen Umgebung so arbeitet wie beschrieben und
sich integrieren lässt. Er ist in **Phasen** aufgebaut: die frühen Phasen
brauchen **keine Heizungsanlage** (Build, Konfiguration, MQTT-Grundfunktion mit
lokalem Broker) und lassen sich in Minuten durchlaufen; die späteren Phasen
prüfen die reale Anbindung an eine Solvis-Anlage und die Smart-Home-Integration.

> Bezug: allgemeine Beschreibung → [README.md](README.md); Grund und Umfang des
> Forks → [FORK.md](FORK.md); durchgeführte Code-/Build-Anpassungen →
> [CHANGELOG-fork.md](CHANGELOG-fork.md).

## Einordnung: Was das Produkt leisten soll

Prüfkriterium ist die im README zugesagte Funktion:

1. **Monitoring** — Messwerte/Zustände der Anlage werden gelesen und
   bereitgestellt (MQTT bzw. proprietärer TCP-Server).
2. **Steuerung** — Anlageparameter (Soll-Temperaturen, Modus, Raumabhängigkeit,
   Heizwasserpumpe) werden gesetzt; die Anlage hat **keine** API, daher erfolgt
   das per OCR + simulierten Klicks auf der grafischen SolvisRemote-Oberfläche.
3. **Erkennung manueller Eingriffe** an der Anlagen-Konsole.
4. **Fehlermeldung** optional per E-Mail (inkl. Screenshot bei Fehlerbild).

## Geltungsbereich / Voraussetzungen

| Punkt | Anforderung |
|---|---|
| Anlage | SolvisMax 6/7 mit **SolvisControl 2 + SolvisRemote**. **Nicht** SC3 (dort Modbus nutzen). |
| Laufzeit | JRE ≥ 17 (Fork-Baseline, Bytecode major 61); verifiziert mit **OpenJDK 21**. |
| Build (optional) | JDK 17+ (Maven-Wrapper `mvnw` ist im Repo enthalten — kein separates Maven nötig). |
| MQTT | Ein MQTT-Broker (z. B. Mosquitto). Hinweis: Upstream-MQTT ist **unverschlüsselt** — für TLS-Umgebungen siehe Phase 6/Integrationsvariante. |
| Netz | SolvisRemote per HTTP erreichbar; TCP-Port des Servers (Default **10735**) für TCP-Clients erreichbar. |

## Referenz-Baseline (in diesem Fork bereits verifiziert)

Damit klar ist, worauf aufgesetzt wird — geprüft am 2026-07-21 auf
OpenJDK 21 / Debian 13 / arm64:

- **T1.1** Build aus Quellen → erfolgreich (`target/SolvisSmartHomeServer.jar`).
- **T2.1** Laufzeit-Smoke `--string-to-crypt` → erfolgreich.

Alle übrigen Tests sind in der **eigenen** Umgebung auszuführen; die
Ergebnismatrix am Ende ist dafür gedacht.

---

## Phase 1 — Build & Artefakt (ohne Anlage)

### T1.1 — Build aus Quellen
- **Zweck:** Der Quellstand baut auf der eigenen JDK-Version.
- **Vorbedingung:** JDK 17+; Repo ausgecheckt (Maven-Wrapper mitgeliefert).
- **Schritte:** `./mvnw -B clean package`
- **Erwartung:** `BUILD SUCCESS`; Datei `target/SolvisSmartHomeServer.jar`
  entsteht (Größenordnung ~4 MB). Keine Fehler.
- **Bestanden, wenn:** Jar erzeugt, Exit-Code 0.

### T1.2 — Uber-Jar ist vollständig
- **Zweck:** Alle Laufzeit-Bibliotheken sind gebündelt (kein späteres
  `NoClassDefFoundError`); zugleich Regressionswächter gegen einen Rückfall auf
  die abgelösten Backends (Stufen 2.2 tinylog/log4j → SLF4J/Logback, 3.4
  `javax.mail` → `jakarta.mail`).
- **Schritte:**
  - Vorhanden (aktive Laufzeit-Bibliotheken):
    `unzip -l target/SolvisSmartHomeServer.jar | grep -E 'paho|logback|jakarta/mail|solvismax/Main'`
  - NICHT vorhanden (abgelöste Bibliotheken, 0 Treffer erwartet):
    `unzip -l target/SolvisSmartHomeServer.jar | grep -E 'tinylog|log4j|javax/mail'`
- **Erwartung:** Erstes Kommando findet Paho-MQTT-, Logback-, `jakarta.mail`-
  und `de/sgollmer/solvismax/Main`-Klassen; zweites Kommando liefert **keine**
  Treffer (tinylog, log4j und `javax.mail` sind im Fork ersetzt).
- **Bestanden, wenn:** erstes Kommando findet alle vier, zweites Kommando
  liefert 0 Treffer.

## Phase 2 — Laufzeit-Smoke (ohne Anlage)

### T2.1 — Start & Passwort-Verschlüsselung
- **Zweck:** `Main` lädt, Abhängigkeiten sind zur Laufzeit auflösbar, das
  Krypto für `passwordCrypt` funktioniert.
- **Schritte:** `java -jar target/SolvisSmartHomeServer.jar --string-to-crypt=probe`
- **Erwartung:** Ein Base64-artiger, verschlüsselter String wird ausgegeben,
  Programm terminiert ohne Stacktrace.
- **Bestanden, wenn:** verschlüsselter Wert erscheint, kein Fehler.
- **Nutzen:** Der Ausgabewert ist der Wert für `passwordCrypt` in `base.xml`
  (Phase 3).

### T2.2 — E-Mail-Benachrichtigung (optional)
- **Zweck:** Die Fehler-Mail-Funktion ist grundsätzlich konfigurierbar.
- **Vorbedingung:** `ExceptionMail`-Block in `base.xml` mit gültigem SMTP.
- **Schritte:** `--test-mail` (bzw. `make testmail`).
- **Erwartung:** Testmail kommt an.
- **Bestanden, wenn:** Zustellung erfolgt. *(Überspringbar, wenn E-Mail nicht
  gewünscht.)*

## Phase 3 — Konfiguration (ohne Anlage)

### T3.1 — `base.xml` wird eingelesen und validiert
- **Zweck:** Konfiguration ist syntaktisch/schematisch korrekt.
- **Vorbedingung:** `base.xml` aus `base.xml.new` erstellt; darin gesetzt:
  `Unit` (`id`, `type`, `url`=SolvisRemote-IP, `account`,
  `passwordCrypt`=Wert aus T2.1), `Mqtt` (`enable="true"`, `brokerUrl`, `port`,
  `topicPrefix`), `ExecutionData` (`port`, `writablePathLinux`).
- **Schritte:** Server im Vordergrund starten (`make foreground` bzw.
  `java -jar …`) und Log beobachten.
- **Erwartung:** Kein XSD-/Parse-Fehler; Server meldet Start und den Beginn
  der Anlagen-/Lernphase.
- **Bestanden, wenn:** sauberer Start ohne Konfigurationsfehler.

## Phase 4 — Anbindung an die Solvis-Anlage (mit Anlage)

### T4.1 — SolvisRemote erreichbar
- **Zweck:** Netzweg zur grafischen Oberfläche steht.
- **Schritte:** Von-Host `curl -sI http://<SolvisRemote-IP>/` bzw. Web-UI im
  Browser öffnen.
- **Erwartung:** HTTP-Antwort / Login-Oberfläche der SolvisRemote.
- **Bestanden, wenn:** Oberfläche erreichbar. *(Bekannter Stolperstein: manche
  SolvisRemote-Geräte zeigen den Web-Port erst nach Reset/Re-Login.)*

### T4.2 — Lernphase (OCR-Bildschirmerkennung)
- **Zweck:** Die grafische Erkennung ist auf die Sprache/Version der Anlage
  angelernt.
- **Schritte:** `--server-learn` (bzw. `make learn`).
- **Erwartung:** Lernlauf ohne Abbruch; erkannte Screens werden abgelegt
  (`LearnedImages`).
- **Bestanden, wenn:** Lernphase abgeschlossen, keine Erkennungsfehler.

### T4.3 — Messwerte werden gelesen
- **Zweck:** Monitoring (Zusage 1) funktioniert.
- **Schritte:** Server dauerhaft starten; Log bzw. generierte Datenstruktur
  beobachten.
- **Erwartung:** Aktuelle Temperaturen/Zustände erscheinen und aktualisieren
  sich.
- **Bestanden, wenn:** plausible, sich ändernde Messwerte.

## Phase 5 — MQTT-Funktion (mit Anlage)

Topic-Schema laut Wiki: Status `prefix/unit/kanal/data`, Kommando
`prefix/client/unit/kanal/cmnd`, ferner `prefix/server/online`,
`prefix/unit/status`, `prefix/…/meta`. `prefix` = `topicPrefix` aus `base.xml`.

### T5.1 — Statuswerte erscheinen auf MQTT
- **Schritte:** `mosquitto_sub -h <broker> -t '<prefix>/#' -v`
- **Erwartung:** `.../data`-Topics mit Messwerten; `.../meta` mit
  Kanal-Metadaten; `server/online` = `true`.
- **Bestanden, wenn:** Datentopics laufend aktualisiert.

### T5.2 — Last-Will / Online-Status
- **Schritte:** Server stoppen (`--server-terminate`) und `server/online`
  beobachten.
- **Erwartung:** `server/online` wechselt auf `false` (bzw. LWT greift bei hartem
  Abbruch).
- **Bestanden, wenn:** Statuswechsel sichtbar.

## Phase 6 — Steuerung (mit Anlage)

### T6.1 — Sollwert setzen via MQTT
- **Zweck:** Steuerung (Zusage 2) inkl. OCR-Rückverifikation.
- **Schritte:** Auf ein Kommando-Topic publizieren, z. B.
  `mosquitto_pub -h <broker> -t '<prefix>/<client>/<unit>/<kanal>/cmnd' -m '<wert>'`
- **Erwartung:** Anlage übernimmt den Wert; der Server verifiziert per OCR und
  meldet den neuen Wert auf dem zugehörigen `.../data`-Topic zurück.
- **Bestanden, wenn:** gewünschter Wert an der Anlage aktiv **und** rückgemeldet.

### T6.2 — Erkennung manueller Eingriffe
- **Zweck:** Zusage 3.
- **Schritte:** Wert direkt an der Anlagen-Konsole ändern.
- **Erwartung:** Der Server erkennt die Änderung und publiziert den neuen Wert.
- **Bestanden, wenn:** externe Änderung erscheint auf MQTT.

### Integrationsvariante: Betrieb hinter mTLS-Broker
Da Upstream-MQTT unverschlüsselt ist, in gesicherten Umgebungen empfohlen:
Server publiziert auf einen **lokalen, auf `127.0.0.1` gebundenen** Broker, der
per **mTLS-Bridge** an den zentralen Broker koppelt (Muster dieses Stacks).
- **Test:** T5.1/T6.1 zusätzlich **am zentralen Broker** (mit Client-Zertifikat)
  gegenprüfen; unautorisierter Zugriff ohne Zertifikat muss scheitern.

## Phase 7 — Smart-Home-Integration (mit Anlage)

### T7.1 — Einbindung ins Zielsystem
- **Zweck:** Integrierbarkeit (Home Assistant / FHEM / ioBroker / OpenHAB).
- **Schritte:** Entitäten/Readings auf die `.../data`-Topics abbilden; ein
  Steuerelement auf ein `.../cmnd`-Topic.
- **Erwartung:** Werte erscheinen im Zielsystem; Bedienung wirkt auf die Anlage.
- **Bestanden, wenn:** bidirektionale Integration nachgewiesen.

## Phase 8 — Dauerbetrieb / Robustheit (mit Anlage)

### T8.1 — Dienstbetrieb & Neustart
- **Schritte:** Als systemd-Dienst installieren (`make installService`); Host
  bzw. Dienst neu starten.
- **Erwartung:** Dienst kommt selbstständig hoch, verbindet Broker + Anlage neu.
- **Bestanden, wenn:** automatischer Wiederanlauf ohne Handgriff.

### T8.2 — Reconnect nach Broker-/Netzunterbrechung
- **Schritte:** Broker kurz stoppen/starten bzw. Netz trennen.
- **Erwartung:** Server verbindet MQTT selbsttätig wieder; Datenfluss läuft
  weiter.
- **Bestanden, wenn:** Wiederverbindung ohne Neustart.

---

## Ergebnismatrix (zum Ausfüllen)

Umgebung: JRE-Version ⟶ ______  · OS/Arch ⟶ ______  · Anlage/Regler ⟶ ______
· Broker ⟶ ______  · Datum ⟶ ______

| Test | Kurzbeschreibung | Hardware nötig | Ergebnis (✅/❌/–) | Notiz |
|---|---|:--:|:--:|---|
| T1.1 | Build aus Quellen | nein | | |
| T1.2 | Uber-Jar vollständig | nein | | |
| T2.1 | Start + `--string-to-crypt` | nein | | |
| T2.2 | Test-Mail (optional) | nein | | |
| T3.1 | `base.xml` eingelesen | nein | | |
| T4.1 | SolvisRemote erreichbar | ja | | |
| T4.2 | Lernphase | ja | | |
| T4.3 | Messwerte gelesen | ja | | |
| T5.1 | Statuswerte auf MQTT | ja | | |
| T5.2 | Online/LWT | ja | | |
| T6.1 | Sollwert setzen | ja | | |
| T6.2 | Manueller Eingriff erkannt | ja | | |
| T7.1 | Smart-Home-Integration | ja | | |
| T8.1 | Dienst + Neustart | ja | | |
| T8.2 | Reconnect | ja | | |

**Schnell-Fazit für Interessierte:** Sind **T1.1–T3.1** grün, baut und startet
das Produkt in der eigenen Umgebung sauber. Sind zusätzlich **T4.x–T6.x** grün,
arbeitet es wie beschrieben mit der eigenen Anlage. **T7/T8** belegen die
Integrations- und Dauerbetriebstauglichkeit.

## Hinweise zu CLI-Optionen

Die genutzten Optionen stammen aus `SmartHome/Linux/Makefile` und dem Code
(u. a. `--string-to-crypt=`, `--server-learn`, `--server-terminate`,
`--test-mail`, `--documentation --csvSemicolon`, `--iobroker`). In diesem Fork
**verifiziert** sind bislang der Build (T1.1) und `--string-to-crypt` (T2.1);
die übrigen sind laut Upstream-Doku vorgesehen und in der eigenen Umgebung zu
bestätigen — genau dafür ist dieser Plan da.
