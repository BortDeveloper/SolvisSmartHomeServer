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

## Phase 3 — MQTT-Broker anbinden

Das `<tns:Mqtt>`-Element aktivieren und auf den eigenen Broker zeigen. Für einen
**mTLS-gehärteten Broker** das `<tns:Ssl>`-Kindelement ergänzen (Fork-Feature):

```xml
<tns:Mqtt enable="true"
    brokerUrl="<BROKER_HOST>"        <!-- z. B. 192.168.1.24 -->
    port="<BROKER_PORT>"             <!-- 1883 (Klartext) oder 8883 (TLS) -->
    userName="<BROKER_USER>"         <!-- optional; weglassen, wenn nur mTLS -->
    passwordCrypt="<WERT>"           <!-- optional; via --string-to-crypt -->
    idPrefix="solvis"
    topicPrefix="<PREFIX>"           <!-- z. B. "solvis" -> Topics solvis/… -->
    smartHomeId="HomeAssistant"
    publishQoS="1" subscribeQoS="1">
    <tns:Ssl enable="true"
        caFilePath="/certs/ca.crt"
        clientCrtFilePath="/certs/client.crt"
        clientKeyFilePath="/certs/client.key" />
</tns:Mqtt>
```

Hinweise:

- `topicPrefix` bestimmt alle Topics: Status unter `<PREFIX>/<unit>/<kanal>/data`,
  Kommandos unter `<PREFIX>/<client>/<unit>/<kanal>/cmnd`, Server-Status
  `<PREFIX>/server/online`.
- **Ohne** `<tns:Ssl>` verbindet der Client unverschlüsselt (nur für
  Klartext-Broker/`1883`).
- Der private Schlüssel muss **PKCS#8** sein. Umwandeln falls nötig:
  `openssl pkcs8 -topk8 -nocrypt -in alt.key -out ssl/client.key`.
- Zertifikate nach `./ssl` legen (wird read-only nach `/certs` gemountet).

Falls der Broker zusätzlich User/Passwort verlangt, den Wert wie in 2.2 mit
`--string-to-crypt` erzeugen und als `passwordCrypt` im `<tns:Mqtt>` eintragen.

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

Auf dem Broker mitlesen (mit denselben Zertifikaten wie der Server):

```bash
# TLS/mTLS-Broker:
mosquitto_sub -h <BROKER_HOST> -p 8883 \
  --cafile ssl/ca.crt --cert ssl/client.crt --key ssl/client.key \
  -t '<PREFIX>/#' -v

# Klartext-Broker:
# mosquitto_sub -h <BROKER_HOST> -p 1883 -t '<PREFIX>/#' -v
```

**Bestanden, wenn:**

- `<PREFIX>/server/online` = `true`,
- `<PREFIX>/<unit>/…/data`-Topics erscheinen und sich aktualisieren
  (Temperaturen, Zustände),
- `<PREFIX>/<unit>/…/meta` liefert Kanal-Metadaten.

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
| `base.xml couldn't be read` | XSD-Validierung fehlgeschlagen — Struktur/Attribute prüfen; `<tns:Ssl>` muss **innerhalb** von `<tns:Mqtt>` stehen. |
| MQTT verbindet nicht (Log: „broker not available") | `brokerUrl`/`port` falsch, oder TLS-Konfig: bei aktivem `<tns:Ssl>` bricht der Client bei Zertifikatsfehlern bewusst ab (kein Klartext-Fallback). Cert-Pfade unter `/certs` prüfen. |
| `… PKCS#1 format …` im Log | Client-Schlüssel in PKCS#1; mit `openssl pkcs8 -topk8 -nocrypt` nach PKCS#8 wandeln. |
| Lernphase geht bei jedem Neustart verloren | `writablePathLinux` zeigt nicht auf `/data`, oder das `./data`-Volume fehlt/ist nicht schreibbar. |
| Keine `…/data`-Topics, aber `server/online=true` | Kanäle evtl. per `IgnoredChannels` gefiltert, oder Anlage liefert (noch) keine Werte; Logs prüfen. |
