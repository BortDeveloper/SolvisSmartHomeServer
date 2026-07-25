# Inbetriebnahme (Schritt für Schritt)

Diese Anleitung führt vollständig durch die Erstinbetriebnahme als
**Docker-Container**: Image bauen → Zugriff auf die Solvis-Anlage einrichten →
MQTT-Broker anbinden → Test starten. Sie baut auf [docs/DOCKER.md](DOCKER.md)
(Grundlagen/Fallstricke) auf und nutzt die Prüfschritte aus
[TESTPLAN.md](../TESTPLAN.md).

> **Platzhalter** in spitzen Klammern (`<…>`) durch die eigenen Werte ersetzen.
> Beispielwerte in Klammern zeigen eine typische Belegung.

---

## Phase 0 — Voraussetzungen

- **Docker** installiert (`docker --version`), Dienst aktiv.
- Dieses Repository ausgecheckt; Arbeitsverzeichnis = Repo-Wurzel.
- Netzweg vom Container-Host zur **SolvisRemote** (HTTP) und zum
  **MQTT-Broker**.
- Anlage: SolvisMax 6/7 mit **SolvisControl 2 + SolvisRemote**.
- **SolvisRemote-Zugangsdaten** (Account + Passwort der Web-Oberfläche).
- Für TLS/mTLS: **CA-Zertifikat**, **Client-Zertifikat** und **privater
  Client-Schlüssel im PKCS#8-Format** (`-----BEGIN PRIVATE KEY-----`).

Verzeichnislayout, das wir anlegen (neben `docker-compose.yml`):

```
.
├── base.xml          # Konfiguration (erstellen wir in Phase 2/3)
├── data/             # persistente Laufzeitdaten (LearnedImages, Logs)  -> Volume /data
└── ssl/              # ca.crt, client.crt, client.key                   -> Volume /certs (ro)
```

```bash
mkdir -p data ssl
```

---

## Phase 1 — Container-Image bauen

```bash
docker compose build
# oder ohne compose:
# docker build -t solvissmarthomeserver:fork .
```

Der Zwei-Stufen-Build kompiliert im Container (Ant) und legt das Uber-Jar in ein
schlankes JRE-Image. Erfolg = `BUILD SUCCESSFUL` und ein Image
`solvissmarthomeserver:fork`.

Kurzcheck, dass das Image startet (ohne Anlage/Broker):

```bash
docker compose run --rm solvis --string-to-crypt=probe
# Erwartet: ein Base64-artiger, verschlüsselter String (z. B. P7cD…==)
```

---

## Phase 2 — Zugriff auf die Solvis-Anlage einrichten

### 2.1 `base.xml` anlegen

Vorlage kopieren (aus dem Image oder dem Repo) und im Projektverzeichnis als
`base.xml` ablegen:

```bash
docker compose run --rm --entrypoint sh solvis -c 'cat /opt/solvis/base.xml' 2>/dev/null \
  || cp rsc/de/sgollmer/solvismax/data/base.xml ./base.xml
```

> ⚠️ **Dateirechte sind der einzige reale Schutz der Secrets (Pflichtschritt).**
> `passwordCrypt` in `base.xml` ist nur **Obfuskation, kein Schutz** — der
> AES-Schlüssel ist aus dem öffentlichen Quellcode ableitbar (ECB). Anlagen-,
> MQTT- und SMTP-Zugangsdaten sind daher nur durch Dateirechte geschützt.
> Direkt nach dem Anlegen/Bearbeiten:
>
> ```bash
> chmod 600 base.xml && chown 10001:10001 base.xml   # Owner = Dienstnutzer (UID 10001)
> ```
>
> `base.xml` **niemals** ins Repo/Backup im Klartext ohne Zugriffsschutz.

### 2.2 Anlagen-Passwort verschlüsseln

Passwörter stehen in `base.xml` **nie im Klartext**, sondern als
`passwordCrypt`. Den Wert erzeugt der Server selbst:

```bash
docker compose run --rm solvis --string-to-crypt='<SOLVIS_WEB_PASSWORT>'
# Ausgabe -> als passwordCrypt in <tns:Unit …> eintragen (Schritt 2.3)
```

### 2.3 `ExecutionData` und `Unit` anpassen

In `base.xml`:

```xml
<tns:ExecutionData timeZone="Europe/Berlin"
    port="10735"
    writablePathLinux="/data"        <!-- WICHTIG: auf das Volume zeigen -->
    echoInhibitTime_ms="2000" />
```

```xml
<tns:Unit id="<UNIT_ID>"             <!-- frei wählbar, z. B. "bortfeld" -->
    type="<ANLAGENTYP>"              <!-- z. B. SolvisMax6PurSolo -->
    mainHeating="<HEIZUNG>"          <!-- z. B. OelBW -->
    heatingCircuits="1"
    account="<SOLVIS_ACCOUNT>"
    passwordCrypt="<WERT_AUS_2.2>"
    url="<SOLVIS_IP>"                <!-- IP/Host der SolvisRemote -->
    …restliche Attribute unverändert lassen… >
```

**Steuerung vs. nur Monitoring:** Im `<tns:Features>`-Block bestimmt
`InteractiveGUIAccess`, ob der Server aktiv Werte an der Anlage setzen darf:

```xml
<tns:Feature id="InteractiveGUIAccess" value="false" />
```

- `false` = **nur lesen** (Monitoring). Für die erste Inbetriebnahme empfohlen.
- `true` = **Steuerung erlaubt** (für den Kommando-Test in Phase 5.2).

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
  Repo `ccu2mqtt`), **nicht** zum Java-Connector. Das `./ssl`→`/certs`-Mount des
  Connectors wird für Variante A **nicht** benötigt.

### 3.2 Container-Netz: `127.0.0.1` muss den lokalen Broker erreichen

Im Docker-Deployment ist `127.0.0.1` das **Container**-Loopback, nicht der Host.
Damit der Connector den lokalen Mosquitto erreicht (und der Klartext-Hop echtes
Loopback bleibt), eine der konventionstreuen Auflösungen wählen:

- **A3 (bevorzugt, Stack-Konvention):** Server **nativ** auf dem Host (systemd,
  wie CCU-Jack / FHEM-Bridge), lokaler Mosquitto localhost-only. Dann ist
  `127.0.0.1` echtes Host-Loopback; die Docker-Linie wird hier nicht genutzt.
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

## Phase 4 — OCR-Lernphase (einmalig)

Der Server erkennt die grafische Oberfläche per OCR und muss sie einmalig
anlernen. Das schreibt `LearnedImages` in das `/data`-Volume:

```bash
docker compose run --rm solvis --server-learn
```

Erwartet: Durchlauf ohne Abbruch; danach liegen unter `./data` angelernte
Bilddaten. (Setzt eine erreichbare SolvisRemote voraus — siehe Troubleshooting.)

---

## Phase 5 — Test starten

### 5.1 Dauerbetrieb starten und Statuswerte prüfen (Monitoring)

```bash
docker compose up -d
docker compose logs -f solvis        # Start/Verbindungsaufbau beobachten
```

Mitlesen — zwei Ebenen (Variante A):

```bash
# 1) Lokal (Connector -> lokaler Broker), Klartext-Loopback:
mosquitto_sub -h 127.0.0.1 -p 1883 -t 'solvis/#' -v

# 2) Ende-zu-Ende hinter der mTLS-Bridge (zentraler Broker):
mosquitto_sub -h <PRIMAER_BROKER_HOST> -p 8883 \
  --cafile ssl/ca.crt --cert ssl/client.crt --key ssl/client.key \
  -t 'solvis/#' -v
```

**Bestanden, wenn:**

- `solvis/server/online` = `true`,
- `solvis/<unit>/…/data`-Topics erscheinen und sich aktualisieren
  (Temperaturen, Zustände),
- `solvis/<unit>/…/meta` liefert Kanal-Metadaten,
- und (Container) der HEALTHCHECK auf `healthy` steht
  (`docker inspect --format '{{.State.Health.Status}}' solvissmarthomeserver`).

### 5.2 Kommando-Test (nur mit `InteractiveGUIAccess="true"`)

Einen steuerbaren Kanal setzen und die OCR-Rückmeldung prüfen:

```bash
mosquitto_pub -h <BROKER_HOST> -p 8883 \
  --cafile ssl/ca.crt --cert ssl/client.crt --key ssl/client.key \
  -t '<PREFIX>/<client>/<unit>/<kanal>/cmnd' -m '<wert>'
```

**Bestanden, wenn:** Die Anlage übernimmt den Wert und meldet ihn auf dem
zugehörigen `…/data`-Topic zurück.

### 5.3 Sauberes Stoppen

```bash
docker compose down          # sendet SIGTERM; init:true sorgt fuer Disconnect/LWT
# oder gezielt:
# docker compose run --rm solvis --server-terminate
```

---

## Troubleshooting

| Symptom | Ursache / Prüfung |
|---|---|
| Lernphase/Start bricht mit Verbindungsfehler zur Anlage ab | SolvisRemote nicht erreichbar. `curl -I http://<SOLVIS_IP>/` testen; manche SolvisRemote zeigen den Web-Port erst nach Neustart/Re-Login. |
| `base.xml couldn't be read` | XSD-Validierung fehlgeschlagen — Struktur/Attribute prüfen. |
| Start bricht ab: „MQTT-Konfiguration nicht unterstuetzt … (Variante B) … 32105" | `<tns:Ssl enable="true">` konfiguriert — Variante B ist deprecated. `<tns:Ssl>` aus `base.xml` entfernen und Variante A verwenden (Phase 3). |
| MQTT verbindet nicht (Log: „broker … not reachable") | Variante A: lokaler Mosquitto (`127.0.0.1:1883`) nicht erreichbar. Läuft der lokale Broker? Erreicht der Container das Host-Loopback (Container-Netz A1/A3, Phase 3.2)? |
| Container bleibt „Up", aber keine Daten (`unhealthy`) | Silent-Failure: Prozess läuft, aber kein MQTT-Publish. Der HEALTHCHECK (Ready-Token-Frische) meldet das; Logs auf WARN „broker not reachable" prüfen. |
| Lernphase geht bei jedem Neustart verloren | `writablePathLinux` zeigt nicht auf `/data`, oder das `./data`-Volume fehlt/ist nicht schreibbar. |
| Keine `…/data`-Topics, aber `server/online=true` | Kanäle evtl. per `IgnoredChannels` gefiltert, oder Anlage liefert (noch) keine Werte; Logs prüfen. |
