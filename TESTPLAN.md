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
| Anlagen-Adresse (diese Installation) | SolvisRemote = **`192.168.1.35`** (feste Fritzbox-DHCP-Reservierung, Stand 2026-08-12). ⚠️ **Nicht** `solvis.fritz.box` verwenden: der DNS-Name zeigt noch auf `192.168.1.49`, wo ein unbekanntes Gerät antwortet (F-119) — Hostname erst nach der DNS-Bereinigung. |
| Exklusivität (diese Installation) | **Nur ein OCR-Client** darf gegen die Anlage laufen (Fehlsteuerungsrisiko). Vor Phase 4–8: Cutover-Checkliste [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md) Phase 5 abhaken; Verifikation: Phase 9 unten. |

## Referenz-Baseline (in diesem Fork bereits verifiziert)

Damit klar ist, worauf aufgesetzt wird:

- Geprüft am **2026-07-21** auf OpenJDK 21 / Debian 13 / arm64:
  - **T1.1** Build aus Quellen → erfolgreich (`target/SolvisSmartHomeServer.jar`).
  - **T2.1** Laufzeit-Smoke `--string-to-crypt` → erfolgreich.
- Nachverifiziert am **2026-08-12** auf OpenJDK 17.0.5 / Windows 11 / x64
  (Maven 3.9.16), HEAD des Branches `feature/modernisierung`:
  - **T1.1** `mvnw`/Maven `clean package` → `BUILD SUCCESS`, **91/91 Tests grün**.
  - **T1.2** Uber-Jar geprüft → logback-classic **1.5.13** (CVE-Fix-Stand)
    gebündelt; keine tinylog-/log4j-/`javax.mail`-**Klassen** (siehe Hinweis
    in T1.2 zu benignen Treffern des groben grep-Musters).
  - **T2.1** `--string-to-crypt=probe` → verschlüsselter Wert, sauberes Ende.

Alle übrigen Tests sind in der **eigenen** Umgebung auszuführen; die
Ergebnismatrix am Ende ist dafür gedacht.

> ⚠️ **Vor jedem Deployment frisch bauen** (`./mvnw -B clean package`): ein
> liegengebliebenes `target/`-Jar kann veraltet sein (hier lag bis 2026-08-12
> ein Jar vom 22.07. mit logback 1.5.12, obwohl HEAD 1.5.13 enthielt).

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
  - NICHT vorhanden (abgelöste Bibliotheken als **Klassen**, 0 Treffer erwartet):
    `unzip -l target/SolvisSmartHomeServer.jar | grep -E 'org/tinylog|org/apache/log4j|org/apache/logging|javax/mail/'`
- **Erwartung:** Erstes Kommando findet Paho-MQTT-, Logback-, `jakarta.mail`-
  und `de/sgollmer/solvismax/Main`-Klassen; zweites Kommando liefert **keine**
  Treffer (tinylog, log4j und `javax.mail` sind im Fork ersetzt).
- **Bestanden, wenn:** erstes Kommando findet alle vier, zweites Kommando
  liefert 0 Treffer.
- **Hinweis (benigne Treffer eines gröberen Musters):** Ein grep auf die
  nackten Wörter `tinylog|log4j|javax/mail` findet vier harmlose Einträge —
  die toten Upstream-Konfig-Vorlagen `de/sgollmer/solvismax/data/log4j2.xml`
  und `…/tinylog.properties` (Ressourcen, keine Klassen) sowie
  `ch/qos/logback/classic/log4j/XMLLayout` (Teil von Logback selbst). Das
  Muster oben zielt deshalb auf die **Klassen-Pfade**.

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

### T2.2 — `check-credentials.sh` dreiwertig
- **Zweck:** Die Credential-Prüfung (F-120) beweist vor Lernphase/GO-Live,
  dass Web-Passwort, MQTT-Login und die `passwordCrypt`-Werte der `base.xml`
  zusammenpassen — mit korrekter dreiwertiger Semantik (ADR-0024).
- **Vorbedingung:** `base.xml` befüllt (Phase 2/3 der
  [Inbetriebnahme](docs/INBETRIEBNAHME.md)); Jar gebaut; Netzweg zur
  SolvisRemote und zum lokalen Broker (sonst UNVERIFIED statt PASS).
- **Schritte:** `make checkCredentials` (in `SmartHome/Linux/`) bzw.
  `./check-credentials.sh [base.xml]`; Passwörter werden unsichtbar abgefragt.
- **Erwartung:** P1–P4 melden je **PASS/FAIL/UNVERIFIED** mit Begründung;
  Exit-Code 0 nur bei durchgängig PASS (1 = mind. ein FAIL, 3 = kein FAIL,
  aber mind. ein UNVERIFIED). Das Skript ändert nichts (read-only, kein
  Publish). Hinweis: Die SolvisRemote beantwortet HEAD mit 403 — das Skript
  prüft deshalb per GET (Messbefund 2026-08-13).
- **Bestanden, wenn:** Semantik wie beschrieben; bei korrekten Zugangsdaten
  Gesamt-PASS.
- **Anmerkung:** Manueller Vorläufer-Lauf der vier Prüfschritte auf dem
  Referenzhost `ransible` am 2026-08-13: **alle 4 Prüfungen PASS** (Web-Login
  200, Unit-Crypt-Match, MQTT-Login `solvis-local`, Mqtt-Crypt-Match). Das
  Skript selbst steht bis zum nächsten Live-Lauf auf **UNVERIFIED**.

### T2.3 — E-Mail-Benachrichtigung (optional)
- **Zweck:** Die Fehler-Mail-Funktion ist grundsätzlich konfigurierbar.
- **Vorbedingung:** `ExceptionMail`-Block in `base.xml` mit gültigem SMTP.
- **Schritte:** `--test-mail` (bzw. `make testmail`).
- **Erwartung:** Testmail kommt an.
- **Bestanden, wenn:** Zustellung erfolgt. *(Überspringbar, wenn E-Mail nicht
  gewünscht.)*

## Phase 3 — Konfiguration (ohne Anlage)

### T3.1 — `base.xml` wird eingelesen und validiert
- **Zweck:** Konfiguration ist syntaktisch/schematisch korrekt.
- **Vorbedingung:** `base.xml` aus der Vorlage
  `rsc/de/sgollmer/solvismax/data/base.xml` erstellt (nativ: `make prepare`
  in `SmartHome/Linux/`); darin gesetzt: `Unit` (`id`, `type`,
  `url="192.168.1.35"` — IP pinnen, **nicht** `solvis.fritz.box` (F-119),
  `account`, `passwordCrypt`=Wert aus T2.1), `Mqtt` (`enable="true"`,
  `brokerUrl="127.0.0.1"`, `port="1883"`, `topicPrefix="solvis"`),
  `ExecutionData` (`port`, `writablePathLinux`).
- **Schritte:** Server im Vordergrund starten (`make foreground` bzw.
  `java -jar …`) und Log beobachten.
- **Erwartung:** Kein XSD-/Parse-Fehler; Server meldet Start und den Beginn
  der Anlagen-/Lernphase.
- **Bestanden, wenn:** sauberer Start ohne Konfigurationsfehler.

## Phase 4 — Anbindung an die Solvis-Anlage (mit Anlage)

> ⚠️ **Vorbedingung für alle Tests mit Anlage (Phase 4–8):**
> Cutover-Checkliste aus [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md)
> Phase 5 abgehakt — insbesondere darf **kein zweiter OCR-Client** (Alt-Dienst
> auf `mon-dg`) gegen dieselbe Anlage laufen (Fehlsteuerungsrisiko), und Tests
> gegen die Live-Anlage erfolgen nur mit explizitem Betreiber-Auftrag.

### T4.1 — SolvisRemote erreichbar
- **Zweck:** Netzweg zur grafischen Oberfläche steht.
- **Schritte:** Vom Fork-Host
  `curl -s -o /dev/null -w '%{http_code}' http://192.168.1.35/` bzw. Web-UI
  im Browser öffnen. **Per GET testen, nicht `curl -I`** — die SolvisRemote
  beantwortet HEAD-Requests mit 403 (Messbefund 2026-08-13). **Gegen die IP
  testen**, nicht gegen `solvis.fritz.box` (F-119: DNS zeigt noch auf `.49`,
  unbekanntes Gerät).
- **Erwartung:** HTTP-Antwort (401 ohne Login = gesund) / Login-Oberfläche
  der SolvisRemote.
- **Bestanden, wenn:** Oberfläche erreichbar. *(Bekannter Stolperstein: manche
  SolvisRemote-Geräte zeigen den Web-Port erst nach Reset/Re-Login — hier
  zuletzt nach Stromreset am 2026-08-12 wieder erreichbar.)*

### T4.2 — Lernphase (OCR-Bildschirmerkennung)
- **Zweck:** Die grafische Erkennung ist auf die Sprache/Version der Anlage
  angelernt.
- **Vorbedingung:** T4.1 grün gegen `192.168.1.35`; Cutover-Checkliste
  abgehakt (die Lernphase klickt real auf der Anlagen-GUI).
- **Schritte:** nativ `sudo make learn` (in `SmartHome/Linux/`) bzw.
  `--server-learn` direkt (dann `-Dhttp.auth.digest.reEnabledAlgorithms=MD5`
  mitgeben, siehe Anmerkung).
- **Erwartung:** Lernlauf ohne Abbruch; erkannte Screens werden abgelegt
  (`LearnedImages` unter `<writablePathLinux>/SolvisServerData/`).
- **Bestanden, wenn:** Lernphase abgeschlossen, keine Erkennungsfehler.
- **Anmerkung (MD5-Digest-Befund 2026-08-13, ransible, Java 21):** Der
  Lernphasen-Erststart scheiterte trotz korrekter Credentials (curl-200-Beweis
  lag vor) mit 401 auf `display.bmp`; JDK-Meldung „Rejecting digest
  authentication with insecure algorithm: MD5". Ursache: Die SolvisRemote
  kann Digest-Auth nur mit MD5, Java 14+ verweigert das per Default. Fix:
  System-Property `http.auth.digest.reEnabledAlgorithms=MD5` — seither fest
  in den Unit-Templates und make-Startkommandos verdrahtet
  ([docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md), Troubleshooting). Bei
  T4.2-Fehlschlägen mit diesem Symptom zuerst prüfen, ob das Flag im
  verwendeten Startweg ankommt.

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
- **Schritte (Variante A, zwei Ebenen):**
  - Lokaler Broker (Loopback-Hop des Connectors):
    `mosquitto_sub -h 127.0.0.1 -p 1883 -t 'solvis/#' -v`
  - Ende-zu-Ende hinter der mTLS-Bridge (zentraler Primär-Broker, mit
    Client-Zertifikat): `mosquitto_sub -h <PRIMAER_BROKER> -p 8883 --cafile … --cert … --key … -t 'solvis/#' -v`
- **Erwartung:** `.../data`-Topics mit Messwerten; `.../meta` mit
  Kanal-Metadaten; `solvis/server/online` = `true` — auf **beiden** Ebenen.
- **Bestanden, wenn:** Datentopics laufend aktualisiert (lokal und zentral).

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

## Phase 9 — Cutover-Verifikation (Ablösung des Alt-Pfads, mit Anlage)

Belegt, dass nach dem Cutover ([docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md)
Phase 5) **genau ein** Publisher den Vertrag `solvis/#` bedient.

### T9.1 — Alt-Dienst still und deaktiviert
- **Zweck:** Kein zweiter OCR-Client / Publisher (Topic-Kollision,
  Fehlsteuerungsrisiko).
- **Schritte:** Auf `mon-dg` (`192.168.1.60`):
  `systemctl is-active SolvisSmartHomeServer.service` und
  `systemctl is-enabled SolvisSmartHomeServer.service`.
- **Erwartung:** `inactive` und `disabled` (Ist-Stand seit 2026-08-12).
- **Bestanden, wenn:** beide Ausgaben wie erwartet; kein Prozess der
  Alt-Software läuft.

### T9.2 — `solvis/server/online` nur noch vom Fork, keine Doppel-Publikationen
- **Zweck:** Publisher-Eindeutigkeit auf dem Vertrag `solvis/#`.
- **Schritte:**
  1. Fork stoppen (`systemctl stop …`) → `solvis/server/online` muss auf
     `false` gehen und **bleiben** (würde ein zweiter Publisher leben, käme
     erneut `true`).
  2. Fork starten → `online=true`; einige Minuten `solvis/#` am zentralen
     Broker mitlesen: jedes `.../data`-Topic aktualisiert sich in **einem**
     konsistenten Rhythmus, keine widersprüchlichen/flappenden Doppelwerte,
     keine stale Zeitstempel des Alt-Pfads.
  3. Optional Gegenprobe der Broker-Seite: der `solvis/#`-Übergangsbestand
     (mon-dg-ACL, `bridge.conf`) ist entzogen — Nachweis liegt im Repo
     `ccu2mqtt` (`docs/broker-acl.md`).
- **Erwartung/Bestanden, wenn:** Online-Status folgt ausschließlich dem Fork;
  keine Doppel-Publikationen beobachtbar.

---

## Ergebnismatrix (zum Ausfüllen)

Umgebung: JRE-Version ⟶ ______  · OS/Arch ⟶ ______  · Anlage/Regler ⟶ ______
· Broker ⟶ ______  · Datum ⟶ ______

| Test | Kurzbeschreibung | Hardware nötig | Ergebnis (✅/❌/–) | Notiz |
|---|---|:--:|:--:|---|
| T1.1 | Build aus Quellen | nein | | |
| T1.2 | Uber-Jar vollständig | nein | | |
| T2.1 | Start + `--string-to-crypt` | nein | | |
| T2.2 | `check-credentials.sh` dreiwertig | nein¹ | | UNVERIFIED bis zum nächsten Live-Lauf; Vorläufer-Lauf ransible 2026-08-13: 4/4 PASS |
| T2.3 | Test-Mail (optional) | nein | | |
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
| T9.1 | Alt-Dienst inactive+disabled | ja | | |
| T9.2 | Ein Publisher auf `solvis/#` | ja | | |

¹ T2.2 läuft ohne Anlage an, liefert dann aber für P1 (und ggf. P3)
**UNVERIFIED** statt PASS — ein Gesamt-PASS braucht den Netzweg zur
SolvisRemote und zum lokalen Broker.

**Schnell-Fazit für Interessierte:** Sind **T1.1–T3.1** grün, baut und startet
das Produkt in der eigenen Umgebung sauber. Sind zusätzlich **T4.x–T6.x** grün,
arbeitet es wie beschrieben mit der eigenen Anlage. **T7/T8** belegen die
Integrations- und Dauerbetriebstauglichkeit, **T9** die saubere Ablösung des
Alt-Pfads.

## Hinweise zu CLI-Optionen

Die genutzten Optionen stammen aus `SmartHome/Linux/Makefile` und dem Code
(u. a. `--string-to-crypt=`, `--server-learn`, `--server-terminate`,
`--test-mail`, `--documentation --csvSemicolon`, `--iobroker`). In diesem Fork
**verifiziert** sind bislang der Build (T1.1, zuletzt 2026-08-12), das
Uber-Jar (T1.2, 2026-08-12) und `--string-to-crypt` (T2.1, zuletzt
2026-08-12); die übrigen sind laut Upstream-Doku vorgesehen und in der
eigenen Umgebung zu bestätigen — genau dafür ist dieser Plan da. Die Tests
mit Anlage (T4.x–T9.x, insbesondere OCR-Lernphase und MQTT-Kette) stehen bis
zur realen Inbetriebnahme auf **UNVERIFIED**.
