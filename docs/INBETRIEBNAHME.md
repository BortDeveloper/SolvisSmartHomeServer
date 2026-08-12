# Inbetriebnahme (Schritt für Schritt)

> **Sprache:** Deutsch · **Status:** aktiv · **Zielgruppe:** Operateure ·
> **Bezug:** [TESTPLAN.md](../TESTPLAN.md) (Prüfschritte),
> [ARCHITECTURE.md](ARCHITECTURE.md) §4 (Betriebsmodell),
> [docs/DOCKER.md](DOCKER.md) (Container-Alternative)

Diese Anleitung führt vollständig durch die Erstinbetriebnahme:
Bauen → Zugriff auf die Solvis-Anlage einrichten → MQTT-Broker anbinden →
als Dienst installieren → **Cutover vom Alt-Pfad** → Lernphase → Test.

**Zwei Deploymentlinien — der native Pfad ist der Standard:**

- **Nativ (systemd) — Standardpfad.** Betreiber-Entscheid 2026-08-12
  (Pfadentscheid O1, gestaffelt): Der Fork wird auf dem Zielhost zunächst
  **nativ als systemd-Dienst** aufgebaut (Stack-Konvention A3, wie
  CCU-Jack/FHEM-Bridge). Zielhost der Referenz-Installation: `ransible`
  (`192.168.1.135`). Die nativen Schritte stehen in jeder Phase **zuerst**.
- **Docker — erst nach bewiesener Funktion, separater Entscheid.** Die
  Container-Schritte bleiben als gekennzeichnete Alternative erhalten und
  bauen auf [docs/DOCKER.md](DOCKER.md) auf.

> **Platzhalter** in spitzen Klammern (`<…>`) durch die eigenen Werte ersetzen.
> Beispielwerte in Klammern zeigen eine typische Belegung.

## Feste Adressen dieser Installation (Stand 2026-08-12)

| Gerät/Host | Adresse | Anmerkung |
|---|---|---|
| SolvisRemote | **`192.168.1.35`** | Fest per Fritzbox-DHCP-Reservierung. **Immer die IP eintragen.** |
| Zielhost Fork (Referenz) | `ransible` = `192.168.1.135` | Nativer systemd-Betrieb (Standardpfad). |
| Alt-Installation (stillgelegt) | `mon-dg` = `192.168.1.60` | Alt-Dienst seit 2026-08-12 gestoppt + disabled (Phase 5). |

> ⚠️ **F-119: `solvis.fritz.box` NICHT verwenden.** Der DNS-Name zeigt noch
> auf `192.168.1.49`, wo ein **unbekanntes Gerät** antwortet — wer den Hostnamen
> konfiguriert, spricht das falsche Gerät an. Bis zur DNS-Bereinigung in allen
> Konfigurationen die IP `192.168.1.35` pinnen.

---

## Phase 0 — Voraussetzungen

**Nativ (Standard):**

- Debian-artiger Host (Referenz: `ransible`; das dortige OS ist zum
  Redaktionsstand **UNVERIFIED** — die Schritte sind parametrisiert und auf
  jedem Debian-artigen System mit systemd nachvollziehbar).
- **Java ≥ 17** (headless reicht; OCR nutzt nur `java.desktop` aus dem JDK,
  kein X11): `java -version` prüfen. Bei mehreren JDKs den vollen Pfad
  notieren (wird unten als `javaPath` übergeben).
- `make` (für die Install-Ziele unter `SmartHome/Linux/`).
- Lokaler **Mosquitto** auf demselben Host, gebunden auf `127.0.0.1`
  (Variante A, Phase 3); die mTLS-Bridge-Gegenseite ist im Repo `ccu2mqtt`
  provisioniert.

**Docker (Alternative):** Docker installiert (`docker --version`), Dienst
aktiv; Verzeichnislayout wie in [docs/DOCKER.md](DOCKER.md).

**Beide Linien:**

- Dieses Repository ausgecheckt; Arbeitsverzeichnis = Repo-Wurzel.
- Netzweg zur **SolvisRemote** (`http://192.168.1.35/`, HTTP 401 ohne Login
  ist der gesunde Zustand) und zum **MQTT-Broker**.
- Anlage: SolvisMax 6/7 mit **SolvisControl 2 + SolvisRemote**.
- **SolvisRemote-Zugangsdaten** (Account + Passwort der Web-Oberfläche).

---

## Phase 1 — Bauen

> ⚠️ **Vor jedem Deployment frisch bauen.** Ein liegengebliebenes
> `target/`-Jar kann veraltet sein (so lag hier bis 2026-08-12 ein Jar mit
> logback 1.5.12 vom 22.07., obwohl HEAD bereits 1.5.13 enthielt). Der
> Build ist schnell; `clean` erzwingt den frischen Stand.

**Nativ (Standard):**

```bash
./mvnw -B clean package
```

Erfolg = `BUILD SUCCESS`, alle Tests grün (Stand 2026-08-12: 91/91), Artefakt
`target/SolvisSmartHomeServer.jar` (~4,3 MB). Kurzcheck ohne Anlage/Broker:

```bash
java -jar target/SolvisSmartHomeServer.jar --string-to-crypt=probe
# Erwartet: ein Base64-artiger, verschlüsselter String (z. B. P7cD…==)
```

**Docker (Alternative):**

```bash
docker compose build          # Image solvissmarthomeserver:fork
docker compose run --rm solvis --string-to-crypt=probe
```

---

## Phase 2 — Zugriff auf die Solvis-Anlage einrichten

### 2.1 `base.xml` anlegen

Vorlage aus dem Repo kopieren:

```bash
# Nativ (Standard) — die Install-Ziele erwarten base.xml in SmartHome/Linux/:
cd SmartHome/Linux && make prepare        # holt Jar, base.xsd und (falls fehlend) base.xml-Vorlage
# Docker (Alternative) — base.xml neben docker-compose.yml:
cp rsc/de/sgollmer/solvismax/data/base.xml ./base.xml
```

> ⚠️ **Dateirechte sind der einzige reale Schutz der Secrets (Pflichtschritt).**
> `passwordCrypt` in `base.xml` ist nur **Obfuskation, kein Schutz** — der
> AES-Schlüssel ist aus dem öffentlichen Quellcode ableitbar (ECB). Anlagen-,
> MQTT- und SMTP-Zugangsdaten sind daher nur durch Dateirechte geschützt.
> Direkt nach dem Anlegen/Bearbeiten:
>
> ```bash
> chmod 600 base.xml                      # und Owner = Dienstnutzer:
> chown solvis:solvis base.xml            # nativ (User "solvis", Phase 4)
> # chown 10001:10001 base.xml            # Docker (UID 10001)
> ```
>
> `base.xml` **niemals** ins Repo/Backup im Klartext ohne Zugriffsschutz.

### 2.2 Anlagen-Passwort verschlüsseln

Passwörter stehen in `base.xml` **nie im Klartext**, sondern als
`passwordCrypt`. Den Wert erzeugt der Server selbst:

```bash
# Nativ:
java -jar target/SolvisSmartHomeServer.jar --string-to-crypt='<SOLVIS_WEB_PASSWORT>'
# Docker:
# docker compose run --rm solvis --string-to-crypt='<SOLVIS_WEB_PASSWORT>'
# Ausgabe -> als passwordCrypt in <tns:Unit …> eintragen (Schritt 2.3)
```

### 2.3 `ExecutionData` und `Unit` anpassen

In `base.xml`:

```xml
<tns:ExecutionData timeZone="Europe/Berlin"
    port="10735"
    writablePathLinux="/opt/solvis"      <!-- nativ: Schreibpfad des Dienstnutzers -->
                                         <!-- Docker: "/data" (Volume) -->
    echoInhibitTime_ms="2000" />
```

```xml
<tns:Unit id="<UNIT_ID>"             <!-- frei wählbar, z. B. "bortfeld" -->
    type="<ANLAGENTYP>"              <!-- z. B. SolvisMax6PurSolo -->
    mainHeating="<HEIZUNG>"          <!-- z. B. OelBW -->
    heatingCircuits="1"
    account="<SOLVIS_ACCOUNT>"
    passwordCrypt="<WERT_AUS_2.2>"
    url="192.168.1.35"               <!-- SolvisRemote: IP pinnen! NICHT
                                          solvis.fritz.box — zeigt noch auf .49,
                                          unbekanntes Gerät (F-119) -->
    …restliche Attribute unverändert lassen… >
```

**Steuerung vs. nur Monitoring:** Im `<tns:Features>`-Block bestimmt
`InteractiveGUIAccess`, ob der Server aktiv Werte an der Anlage setzen darf:

```xml
<tns:Feature id="InteractiveGUIAccess" value="false" />
```

- `false` = **nur lesen** (Monitoring). Für die erste Inbetriebnahme empfohlen.
- `true` = **Steuerung erlaubt** (für den Kommando-Test in Phase 7.2).

---

## Phase 3 — MQTT anbinden (Variante A: lokaler Broker + mTLS-Bridge)

**Unterstützte Produktivvariante ist Variante A** (siehe
[ARCHITECTURE.md](ARCHITECTURE.md) §4): Der Connector publiziert **unverschlüsselt
auf einen lokalen, auf `127.0.0.1` gebundenen Mosquitto** auf demselben Host;
dieser koppelt per **mTLS-Bridge** (Login `solvis-bridge`, `topic solvis/# both`)
an den zentralen Haus-Broker. Dasselbe Muster nutzen CCU-Jack und die FHEM-Bridge.
Die Bridge-/Zertifikats-/ACL-Seite liegt im Repo `ccu2mqtt` (`docs/solvis.md` §7)
und ist dort bereits provisioniert — **hier ist kein Client-Zertifikat nötig**.

> ⛔ **Variante B (natives mTLS direkt, `<tns:Ssl enable="true">`) ist deprecated
> und wird nicht unterstützt.** Der Start bricht dann per Fail-Fast-Guard mit
> klarer Meldung ab (Ursache: Paho-v3-Fehler 32105). Begründung:
> [mtls-behebung-vorschlag.md](mtls-behebung-vorschlag.md). **Kein `<tns:Ssl>` im
> `<tns:Mqtt>`-Element konfigurieren.**

### 3.1 `<tns:Mqtt>` auf den lokalen Broker zeigen

```xml
<tns:Mqtt enable="true"
    brokerUrl="127.0.0.1"            <!-- lokaler Mosquitto, Loopback -->
    port="1883"                      <!-- Klartext zum lokalen Broker -->
    userName="<BROKER_USER>"         <!-- optional; nur falls der lokale Broker Auth verlangt -->
    passwordCrypt="<WERT>"           <!-- optional; via --string-to-crypt -->
    idPrefix="solvis"
    topicPrefix="solvis"             <!-- Vertrag: ausschließlich solvis/# -->
    smartHomeId="HomeAssistant"
    publishQoS="1" subscribeQoS="1" />
```

Hinweise:

- `topicPrefix="solvis"` ist vertraglich fixiert: Status unter
  `solvis/<unit>/<kanal>/data`, Kommandos unter
  `solvis/<client>/<unit>/<kanal>/cmnd`, Server-Status `solvis/server/online`.
  **Keine Topics außerhalb `solvis/#`.**
- Das mTLS-Material (CA, Client-Cert, Key) gehört zur **Bridge** (Mosquitto,
  Repo `ccu2mqtt`), **nicht** zum Java-Connector.

### 3.2 Netz-Topologie: `127.0.0.1` muss der lokale Broker sein

- **Nativ/A3 (Standardpfad, Stack-Konvention):** Server nativ auf dem Host
  (systemd, wie CCU-Jack / FHEM-Bridge), lokaler Mosquitto localhost-only.
  `127.0.0.1` ist echtes Host-Loopback — keine weitere Maßnahme nötig.
- **A1 (Docker):** `network_mode: host` für den Connector-Container. Dann bindet
  auch der proprietäre TCP-Server (Port 10735) auf Host-Interfaces — deshalb
  **zwingend** mit dem Bind-/Abschalt-Schalter aus [ARCHITECTURE.md](ARCHITECTURE.md)
  §5 koppeln (`SOLVIS_TCPSERVER_BINDADDRESS=127.0.0.1` bzw.
  `SOLVIS_TCPSERVER_ENABLE=false`).
- *A2 (Mosquitto-Sidecar im Compose-Netz):* nur mit dokumentierter
  Security-Entscheidung (Netz nachweislich nicht ans LAN exponiert), da der Hop
  dann kein echtes Loopback mehr ist.

Falls der lokale Broker zusätzlich User/Passwort verlangt, den Wert wie in 2.2
mit `--string-to-crypt` erzeugen und als `passwordCrypt` im `<tns:Mqtt>` eintragen.

---

## Phase 4 — Installation als systemd-Dienst (nativer Standardpfad)

Die Install-Ziele liegen in `SmartHome/Linux/` (Upstream-Makefile, im Fork um
`prepare` und einen Java-≥17-Guard ergänzt). Alle Schritte auf dem Zielhost
(Referenz: `ransible`), im ausgecheckten Repo.

> Docker-Linie? Dann diese Phase überspringen und stattdessen
> [docs/DOCKER.md](DOCKER.md) verwenden — Cutover (Phase 5) gilt trotzdem.

### 4.1 Artefakte bereitstellen (Brückenschritt Maven → Install)

Der Fork-Build legt das Jar unter `target/` ab; die `base.xsd` ist im Jar als
Klassenpfad-Ressource enthalten (der Server validiert zur Laufzeit **gegen die
Jar-interne XSD** — die installierte `base.xsd`-Kopie ist nur Referenz).
`make prepare` stellt bereit, was die Install-Ziele im Makefile-Verzeichnis
erwarten:

```bash
./mvnw -B clean package                  # frisch bauen (Phase 1)
cd SmartHome/Linux
make prepare                             # kopiert target/-Jar + base.xsd; legt base.xml-Vorlage an (überschreibt nie)
```

Danach `base.xml` in `SmartHome/Linux/` gemäß Phase 2/3 befüllen
(`chmod 600` nicht vergessen).

### 4.2 Java-Version prüfen und installieren

Der Fork braucht **Java ≥ 17**; das Makefile bricht sonst ab (`checkJava`).
Zeigt `/usr/bin/java` auf ein zu altes Java, den Pfad eines Java ≥ 17 als
`javaPath` übergeben — er wird auch in die systemd-Unit substituiert:

```bash
make checkJava                           # Guard einzeln ausführen (optional)
sudo make installSolvis                  # Default: javaPath=/usr/bin/java
# oder mit explizitem Java:
# sudo make installSolvis javaPath=/usr/lib/jvm/temurin-21-jre/bin/java
```

`installSolvis` legt (falls nötig) den Systemnutzer **`solvis`** an, kopiert
Jar/`base.xml`/`base.xsd` nach `/opt/solvis/SolvisSmartHomeServer/`
(`base.xml` mit `chmod 600`, Owner `solvis`) und installiert die
systemd-Units. Der Dienst wird dabei **noch nicht gestartet** — erst Cutover
(Phase 5) und Lernphase (Phase 6) abschließen.

### 4.3 Betriebsparameter (optional): `/etc/default/solvissmarthomeserver`

Die Unit liest optional `/etc/default/solvissmarthomeserver`
(`EnvironmentFile=-…`). Relevant im nativen Betrieb:

```sh
# Health-Token: Container-Default ist /data/health/ready — auf dem nativen
# Host auf den Schreibpfad des Dienstnutzers legen:
SOLVIS_HEALTH_TOKEN_PATH=/opt/solvis/health/ready
# TCP-Server (Default bindet bereits nur 127.0.0.1); ganz abschalten:
# SOLVIS_TCPSERVER_ENABLE=false
```

Das Health-Token (Frische-Zeitstempel bei verbundenem MQTT) ist das
Symptom-Signal für Monitoring — ohne die Variable versucht der Server
`/data/health/ready` (auf nativen Hosts meist nicht beschreibbar; der Server
läuft trotzdem, nur das Signal fehlt).

### 4.4 Dienststeuerung (Referenz)

```bash
sudo systemctl start SolvisSmartHomeServer.service     # erst NACH Phase 5+6!
sudo systemctl enable SolvisSmartHomeServer.service    # Autostart
journalctl -u SolvisSmartHomeServer.service -f         # Logs (Logback -> stdout)
```

Die Fork-Unit stoppt über **SIGTERM** (sauberer Disconnect + LWT
`solvis/server/online=false`), startet bei Fehlern neu (`Restart=on-failure`)
und wartet auf `network-online.target`.

---

## Phase 5 — Cutover vom Alt-Pfad (Pflicht, vor Lernphase und GO-Live)

Die Alt-Installation des Upstream-Servers auf **`mon-dg` (`192.168.1.60`)**
bediente bis 2026-08-12 denselben Vertrag. Zwei parallel laufende Instanzen
sind **ausgeschlossen**, aus zwei Gründen:

1. **Topic-Kollision:** Beide publizieren unter `solvis/#` (inkl.
   `solvis/server/online` mit LWT) — Konsumenten sähen widersprüchliche,
   flappende Werte.
2. ⚠️ **Zwei-OCR-Clients-Verbot (Fehlsteuerungsrisiko):** Der Server bedient
   die SolvisRemote-GUI per OCR + **simulierten Klicks**. Zwei Clients auf
   derselben GUI verklicken sich gegenseitig — bis hin zu realen
   Fehlsteuerungen an der Heizung. **Niemals** Fork und Alt-Software
   gleichzeitig gegen `192.168.1.35` laufen lassen — auch nicht „nur kurz",
   auch nicht für die Lernphase.

### Cutover-Checkliste (vor dem ersten Start des Forks abhaken)

- [ ] **Alt-Dienst auf `mon-dg` gestoppt + disabled.**
      Ist-Stand 2026-08-12: erledigt (der Alt-Dienst publizierte ohnehin seit
      2025-07-17 nur noch stale Daten; Port 10735 zu). Verifikation auf
      `mon-dg`:

      ```bash
      systemctl is-active SolvisSmartHomeServer.service    # erwartet: inactive
      systemctl is-enabled SolvisSmartHomeServer.service   # erwartet: disabled
      ```

- [ ] **SolvisRemote unter `192.168.1.35` erreichbar** (`curl -sI
      http://192.168.1.35/` → HTTP 401 = gesund). Nicht über
      `solvis.fritz.box` prüfen (F-119, siehe Adress-Kasten oben).
- [ ] **Kein zweiter OCR-Client aktiv** (siehe Verbot oben) — auch keine
      Test-/Debug-Instanz auf einem Arbeitsrechner.
- [ ] **Übergangsbestand `solvis/#` auf der Broker-Seite entziehen (beim
      GO-Live des Direkt-Pfads):** In der mon-dg-ACL und der `bridge.conf`
      des zentralen Brokers besteht noch der Übergangsbestand für den
      Alt-Pfad. Der Operator entzieht ihn beim GO-Live des Fork-Direktpfads.
      Diese Änderung liegt **im Repo `ccu2mqtt`** (`docs/broker-acl.md` und
      Bridge-Konfiguration) — sie wird von dort aus durchgeführt, nicht aus
      diesem Repo.
- [ ] **Konsumenten informiert/geprüft:** Abnehmer von `solvis/#` (z. B.
      Home Assistant) verkraften den Publisher-Wechsel (Topics bleiben
      vertragsgleich; ggf. Retained-Altwerte beachten).

### Verifikation nach dem GO-Live

Siehe [TESTPLAN.md](../TESTPLAN.md) Phase 9 (Cutover-Verifikation):
`solvis/server/online` kommt **nur noch vom Fork**, keine Doppel-Publikationen
auf `solvis/#`.

### Rollback-Weg

Solange die Alt-Software auf `mon-dg` **installiert** bleibt (sie ist nur
gestoppt + disabled), ist der Rückweg ein Kommando — vorher zwingend den
Fork stoppen (Zwei-Clients-Verbot gilt in beide Richtungen):

```bash
# Auf dem Fork-Host:
sudo systemctl disable --now SolvisSmartHomeServer.service
# Danach auf mon-dg:
sudo systemctl enable --now SolvisSmartHomeServer.service
```

Die Alt-Software daher **erst deinstallieren**, wenn der Fork-Betrieb
nachweislich stabil läuft (TESTPLAN Phase 8 bestanden).

---

## Phase 6 — OCR-Lernphase (einmalig)

Der Server erkennt die grafische Oberfläche per OCR und muss sie einmalig
anlernen (schreibt `LearnedImages` in den Schreibpfad). Vorbedingung:
Cutover-Checkliste (Phase 5) abgehakt — die Lernphase klickt real auf der
Anlagen-GUI.

```bash
# Nativ (Standard) — stoppt laufende Dienste, lernt, startet den Dienst:
cd SmartHome/Linux && sudo make learn
# oder manuell, ohne automatischen Dienststart:
sudo -u solvis java -jar /opt/solvis/SolvisSmartHomeServer/SolvisSmartHomeServer.jar --server-learn

# Docker (Alternative):
# docker compose run --rm solvis --server-learn
```

Erwartet: Durchlauf ohne Abbruch; danach liegen unter
`<writablePathLinux>/SolvisServerData/LearnedImages` (nativ z. B.
`/opt/solvis/…`) bzw. `./data` (Docker) angelernte Bilddaten. (Setzt die
erreichbare SolvisRemote `192.168.1.35` voraus — siehe Troubleshooting.)

---

## Phase 7 — Test starten

### 7.1 Dauerbetrieb starten und Statuswerte prüfen (Monitoring)

```bash
# Nativ (Standard):
sudo systemctl enable --now SolvisSmartHomeServer.service
journalctl -u SolvisSmartHomeServer.service -f          # Start/Verbindungsaufbau beobachten

# Docker (Alternative):
# docker compose up -d && docker compose logs -f solvis
```

Mitlesen — zwei Ebenen (Variante A):

```bash
# 1) Lokal (Connector -> lokaler Broker), Klartext-Loopback:
mosquitto_sub -h 127.0.0.1 -p 1883 -t 'solvis/#' -v

# 2) Ende-zu-Ende hinter der mTLS-Bridge (zentraler Broker):
mosquitto_sub -h <PRIMAER_BROKER_HOST> -p 8883 \
  --cafile <ca.crt> --cert <client.crt> --key <client.key> \
  -t 'solvis/#' -v
```

**Bestanden, wenn:**

- `solvis/server/online` = `true`,
- `solvis/<unit>/…/data`-Topics erscheinen und sich aktualisieren
  (Temperaturen, Zustände),
- `solvis/<unit>/…/meta` liefert Kanal-Metadaten,
- nativ: das Health-Token (Phase 4.3) wird periodisch aktualisiert
  (`stat /opt/solvis/health/ready`), bzw. Docker: HEALTHCHECK `healthy`
  (`docker inspect --format '{{.State.Health.Status}}' solvissmarthomeserver`),
- Cutover-Verifikation ([TESTPLAN.md](../TESTPLAN.md) Phase 9): nur **ein**
  Publisher auf `solvis/#`.

### 7.2 Kommando-Test (nur mit `InteractiveGUIAccess="true"`)

Einen steuerbaren Kanal setzen und die OCR-Rückmeldung prüfen:

```bash
mosquitto_pub -h <PRIMAER_BROKER_HOST> -p 8883 \
  --cafile <ca.crt> --cert <client.crt> --key <client.key> \
  -t 'solvis/<client>/<unit>/<kanal>/cmnd' -m '<wert>'
```

**Bestanden, wenn:** Die Anlage übernimmt den Wert und meldet ihn auf dem
zugehörigen `…/data`-Topic zurück.

### 7.3 Sauberes Stoppen

```bash
# Nativ: SIGTERM durch systemd -> sauberer Disconnect + LWT:
sudo systemctl stop SolvisSmartHomeServer.service

# Docker:
# docker compose down
```

---

## Troubleshooting

| Symptom | Ursache / Prüfung |
|---|---|
| Lernphase/Start bricht mit Verbindungsfehler zur Anlage ab | SolvisRemote nicht erreichbar. `curl -I http://192.168.1.35/` testen (401 = gesund); manche SolvisRemote zeigen den Web-Port erst nach Neustart/Re-Login. **Nicht** `solvis.fritz.box` verwenden (F-119: zeigt noch auf `.49`, unbekanntes Gerät). |
| `make installSolvis` bricht mit „kein Java >= 17" ab | `/usr/bin/java` ist zu alt. Java ≥ 17 installieren oder `make … javaPath=/pfad/zu/java-17+` übergeben. |
| `make installSolvis`: Jar/base.xml fehlt | Brückenschritt vergessen: erst `./mvnw -B clean package` (Repo-Wurzel), dann `make prepare` (in `SmartHome/Linux/`). |
| `base.xml couldn't be read` | XSD-Validierung fehlgeschlagen — Struktur/Attribute prüfen (validiert wird gegen die Jar-interne `base.xsd`). |
| Start bricht ab: „MQTT-Konfiguration nicht unterstuetzt … (Variante B) … 32105" | `<tns:Ssl enable="true">` konfiguriert — Variante B ist deprecated. `<tns:Ssl>` aus `base.xml` entfernen und Variante A verwenden (Phase 3). |
| MQTT verbindet nicht (Log: „broker … not reachable") | Variante A: lokaler Mosquitto (`127.0.0.1:1883`) nicht erreichbar. Läuft der lokale Broker? Docker: erreicht der Container das Host-Loopback (A1/A2, Phase 3.2)? |
| Prozess läuft, aber keine Daten | Silent-Failure: kein MQTT-Publish. Nativ: Health-Token-Frische prüfen (Phase 4.3); Docker: HEALTHCHECK `unhealthy`. Logs auf WARN „broker not reachable" prüfen. |
| Kein Health-Token im nativen Betrieb | `SOLVIS_HEALTH_TOKEN_PATH` nicht gesetzt (Default `/data/health/ready` ist nativ meist nicht beschreibbar) — Phase 4.3. |
| Lernphase geht bei jedem Neustart verloren | `writablePathLinux` zeigt auf einen nicht (für den Dienstnutzer) schreibbaren Pfad, bzw. Docker: `./data`-Volume fehlt. |
| Keine `…/data`-Topics, aber `server/online=true` | Kanäle evtl. per `IgnoredChannels` gefiltert, oder Anlage liefert (noch) keine Werte; Logs prüfen. |
| Werte „flappen" / doppelte Publikationen auf `solvis/#` | Zweiter Publisher aktiv — Cutover-Checkliste (Phase 5) prüfen: Alt-Dienst auf `mon-dg` wirklich `inactive`/`disabled`? |
