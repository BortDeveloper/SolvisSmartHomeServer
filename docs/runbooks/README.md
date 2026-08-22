# Runbooks (OPERATEUR-Lens)

> **Sprache:** Deutsch · **Status:** aktiv · **Zielgruppe:** Operateure ·
> **Bezug:** [../INBETRIEBNAHME.md](../INBETRIEBNAHME.md) (Erstinbetriebnahme),
> [../ARCHITECTURE.md](../ARCHITECTURE.md) §4 (Betriebsmodell),
> [../DOCKER.md](../DOCKER.md) (Container-Alternative für Entwicklung/Test),
> [../../TESTPLAN.md](../../TESTPLAN.md) (Prüfschritte)

Day-2-Standardaufgaben für den laufenden Betrieb. Die **Erstinbetriebnahme**
(bauen → Anlage anbinden → MQTT → Cutover → Lernphase → erster Start) steht
Schritt für Schritt in [../INBETRIEBNAHME.md](../INBETRIEBNAHME.md) und wird
hier **nicht** dupliziert, sondern vorausgesetzt.

## Produktivbild (Stand 2026-08-13)

Der Fork läuft **nativ als systemd-Dienst** auf dem headless Raspberry Pi 4
`ransible` (`192.168.1.135`, Debian 13 aarch64, OpenJDK 21) und ist seit dem
Cutover am 2026-08-13 der einzige Publisher auf `solvis/#`. Es gibt im
Produktivbetrieb **keinen Container** — `docker compose`, `docker inspect` und
die Container-UID 10001 kommen dort nicht vor. Alle Kommandos dieses Runbooks
sind deshalb die nativen; die Container-Variante steht jeweils darunter als
klar gekennzeichnete Alternative für **Entwicklung und Test**.

Feste Werte der Referenz-Installation:

| Gegenstand | Wert |
|---|---|
| Dienst | `SolvisSmartHomeServer.service` (Systemnutzer `solvis`) |
| Installationspfad | `/opt/solvis/SolvisSmartHomeServer/` (Jar, `base.xml`, `base.xsd`) |
| Schreibpfad (`writablePathLinux`) | `/opt/solvis` → Laufzeitdaten unter `/opt/solvis/SolvisServerData/` |
| Health-Token | `/opt/solvis/health/ready` (Pfad aus `/etc/default/solvissmarthomeserver`) |
| MQTT | Klartext auf `127.0.0.1:1883` (lokaler Mosquitto, Login `solvis-local`) |
| Anlage | SolvisRemote `192.168.1.35` (IP gepinnt, siehe [../INBETRIEBNAHME.md](../INBETRIEBNAHME.md)) |

Die **Broker- und Bridge-Gegenseite** (lokale `local.conf`, Bridge
`solvis-bridge-to-primary`, Login `solvis-bridge`, Zertifikate, ACL) gehört
nicht in dieses Repo. Wer dort etwas ändern oder prüfen muss, arbeitet nach dem
as-built-Runbook `ccu2mqtt:docs/runbooks/solvis-bridge-ransible.md`.

Jede Aufgabe nennt einen **Automatisierung**-Wert aus dem geschlossenen
Vokabular des Cockpit-Doku-Standards (`systemd-timer` · `cron` · `CI-Job` ·
`Script (on-demand)` · `bewusst manuell`).

> **Platzhalter** in spitzen Klammern (`<…>`) durch eigene Werte ersetzen. Die
> nativen Kommandos laufen auf dem Zielhost (`ransible`), die
> `make`-Ziele im ausgecheckten Repo unter `SmartHome/Linux/`; die
> Container-Kommandos der Alternative aus der Repo-Wurzel.

## Übersicht

| Aufgabe | Automatisierung |
|---|---|
| [Deploy / Start](#aufgabe-deploy--start) | bewusst manuell |
| [Update ausrollen](#aufgabe-update-ausrollen) | Script (on-demand) |
| [OCR-Lernphase erneuern](#aufgabe-ocr-lernphase-erneuern) | Script (on-demand) |
| [Backup (Operator-State)](#aufgabe-backup-operator-state) | bewusst manuell |
| [Restore](#aufgabe-restore) | bewusst manuell |
| [Stoppen / Restart](#aufgabe-stoppen--restart) | Script (on-demand) |

---

## Aufgabe: Deploy / Start

Regelbetrieb (Monitoring, optional Steuerung) starten.

1. Prüfen, dass die installierte `base.xml` existiert und dateirechtegeschützt
   ist — `stat -c '%a %U:%G' /opt/solvis/SolvisSmartHomeServer/base.xml`
   → erwartet: `640 root:solvis` (Stand seit der S-5-Härtung 2026-08-22;
   Bestandsinstallationen zeigen bis zum nächsten `make installSolvis`
   noch `600 solvis:…` — beides ist dateirechtegeschützt).
2. Steuer-Modus bewusst wählen: in derselben `base.xml`
   `<tns:Feature id="InteractiveGUIAccess" value="false"/>` für reines
   Monitoring, `true` erst nach Freigabe (Steuerung klickt real an der Anlage).
3. Dienst starten — `sudo systemctl start SolvisSmartHomeServer.service`
   → erwartet: `systemctl status SolvisSmartHomeServer.service` meldet
   `active (running)`.
4. Logs beobachten — `journalctl -u SolvisSmartHomeServer.service -f`
   → erwartet: Verbindungsaufbau ohne wiederholtes WARN „broker not reachable".
5. Health prüfen — `stat -c '%y' /opt/solvis/health/ready`
   → erwartet: Zeitstempel jünger als 5 Minuten (der Token wird bei
   verbundenem MQTT alle 30 s aufgefrischt und bei Verbindungsverlust
   entfernt).
6. Datenfluss prüfen — `mosquitto_sub -h 127.0.0.1 -p 1883 -u solvis-local -P
   <passwort> -t 'solvis/#' -v`
   → erwartet: `solvis/server/online true` plus `solvis/<unit>/…/data`-Topics.
   Ende-zu-Ende hinter der Bridge: `ccu2mqtt:docs/runbooks/solvis-bridge-ransible.md` §6c.

*Alternative für Entwicklung/Test (Container, nicht der Produktivweg):*
`docker compose up -d`, Logs mit `docker compose logs -f solvis`, Health mit
`docker inspect --format '{{.State.Health.Status}}' solvissmarthomeserver`.

**Automatisierung**: `bewusst manuell` — Auslöser: Operator; Grund: Der Start kann
(bei `InteractiveGUIAccess="true"`) reale Klick-Steuerung an der Anlage
auslösen — bewusste Freigabe statt Auto-Deploy (Least Privilege). Der
Dauerlauf danach läuft unbeaufsichtigt: die systemd-Unit ist `enabled`
(Autostart nach Reboot) und startet bei Fehlern neu (`Restart=on-failure`);
das Health-Token fängt Silent-Failure ab (im Container-Fall der
`HEALTHCHECK` aus dem `Dockerfile`).

---

## Aufgabe: Update ausrollen

Neuen Fork-Stand (Jar) in Betrieb nehmen.

1. Quellstand aktualisieren — `git pull` (Branch `feature/modernisierung`)
   → erwartet: `Already up to date` oder neue Commits.
2. Frisch bauen — `./mvnw -B clean package` (Repo-Wurzel)
   → erwartet: `BUILD SUCCESS`, `target/SolvisSmartHomeServer.jar` neu.
   Nie ein liegengebliebenes `target/`-Jar ausrollen.
3. Ausrollen — `cd SmartHome/Linux && sudo make updateSolvis`
   → erwartet: Dienst wird gestoppt, Jar/`base.xml`/`base.xsd` installiert,
   Dienst wieder gestartet (`stopServices` → `installSolvis` →
   `systemctl start`). Das Ziel `update` täte dasselbe, zieht aber zusätzlich
   die Upstream-FHEM-Modulinstallation mit — hier ungenutzt.
4. Health nach Update — `systemctl status SolvisSmartHomeServer.service` und
   `stat -c '%y' /opt/solvis/health/ready`
   → erwartet: `active (running)`, Token frisch.

*Alternative für Entwicklung/Test:* `docker compose build && docker compose up -d`,
Health per `docker inspect --format '{{.State.Health.Status}}' solvissmarthomeserver`.

**Automatisierung**: `Script (on-demand)` — `SmartHome/Linux/Makefile`
Target `updateSolvis`. Der Build-Teil ist zusätzlich durch den `CI-Job` `build`
(`.github/workflows/build.yml`) abgesichert; der Deploy-Schritt bleibt bewusst
operatorgetrieben (kein Auto-Deploy).

---

## Aufgabe: OCR-Lernphase erneuern

Nach Änderungen an der SolvisRemote-Oberfläche die angelernten Screens neu
erzeugen (schreibt `LearnedImages` nach `/opt/solvis/SolvisServerData/`).

> ⚠️ **Vor jeder Lernphase gilt die Cutover-Checkliste aus
> [../INBETRIEBNAHME.md](../INBETRIEBNAHME.md) Phase 5:** Die Lernphase klickt
> real auf der Anlagen-GUI. Es darf zu keinem Zeitpunkt ein **zweiter
> OCR-Client** gegen `192.168.1.35` laufen — weder der laufende Dienst selbst,
> noch die Alt-Software auf `mon-dg`, noch eine Test-/Debug-Instanz auf einem
> Arbeitsrechner.

1. Laufenden Dienst stoppen — `sudo systemctl stop SolvisSmartHomeServer.service`
   → erwartet: `inactive (dead)`, `solvis/server/online` geht auf `false`.
2. Lernlauf starten — `cd SmartHome/Linux && sudo make learn`
   → erwartet: Durchlauf ohne Abbruch; danach neue Bilddaten unter
   `/opt/solvis/SolvisServerData/LearnedImages`. (`make learn` startet den
   Dienst anschließend selbst wieder und bringt das MD5-Digest-Flag mit.)
3. Regelbetrieb prüfen — `systemctl status SolvisSmartHomeServer.service` und
   `mosquitto_sub … -t 'solvis/#' -v`
   → erwartet: `active (running)`, `solvis/<unit>/…/data`-Topics erscheinen.

Ohne `make` (manueller Aufruf) muss das Digest-Flag explizit mit, sonst
scheitert die Anmeldung an der SolvisRemote mit 401 (Gerätezwang MD5, siehe
[../INBETRIEBNAHME.md](../INBETRIEBNAHME.md) Troubleshooting):

```bash
sudo -u solvis java -Dhttp.auth.digest.reEnabledAlgorithms=MD5 \
  -jar /opt/solvis/SolvisSmartHomeServer/SolvisSmartHomeServer.jar --server-learn
```

*Alternative für Entwicklung/Test:* `docker compose stop solvis`,
`docker compose run --rm solvis --server-learn`, `docker compose up -d`.

**Automatisierung**: `Script (on-demand)` — `SmartHome/Linux/Makefile`
Target `learn`. Bewusst nicht periodisch: Auslöser ist eine erkannte
GUI-Änderung, kein Zeitplan.

---

## Aufgabe: Backup (Operator-State)

Nicht-versionierten Operator-State sichern: die installierte `base.xml`
(Konfiguration + `passwordCrypt`-Werte) und den Schreibpfad
`/opt/solvis/SolvisServerData/` (angelernte Bilder, generierte
`control.xml`/Messwerte, Logs).

1. Dienst kurz anhalten (konsistenter Snapshot) —
   `sudo systemctl stop SolvisSmartHomeServer.service`
   → erwartet: `inactive (dead)`.
2. Verschlüsseltes Archiv erzeugen (Secrets nie im Klartext ablegen) —

   ```bash
   sudo tar czf - -C /opt/solvis SolvisSmartHomeServer/base.xml SolvisServerData \
     | age -r <age-empfaenger-key> > backup-$(date +%F).tar.gz.age
   ```

   → erwartet: Datei `backup-<datum>.tar.gz.age` entsteht.
3. Dienst wieder starten — `sudo systemctl start SolvisSmartHomeServer.service`
   → erwartet: `active (running)`, Health-Token frisch.
4. Backup prüfbar hinterlegen (Prüfsumme) — `sha256sum backup-*.tar.gz.age`
   → erwartet: Hash notiert/mitgesichert.

*Alternative für Entwicklung/Test:* dieselbe Kette mit `docker compose stop
solvis` / `docker compose up -d` und den Repo-Pfaden `base.xml` und `data/`.

**Automatisierung**: `bewusst manuell` — Auslöser: Operator; Grund: `base.xml`
enthält nur dateirechtegeschützte Secrets (`passwordCrypt` = Obfuskation) — kein
unverschlüsselter Auto-Export (Least Privilege / Separation of Duties). Der
Schreibpfad ist zudem aus der Lernphase reproduzierbar, sodass ein
automatischer Scheduler im Connector-Repo bewusst entfällt.

---

## Aufgabe: Restore

Operator-State aus einem Backup wiederherstellen.

1. Dienst stoppen — `sudo systemctl stop SolvisSmartHomeServer.service`
   → erwartet: `inactive (dead)`.
2. Archiv entschlüsseln und auspacken —
   `age -d -i <age-identity> backup-<datum>.tar.gz.age | sudo tar xzf - -C /opt/solvis`
   → erwartet: `/opt/solvis/SolvisSmartHomeServer/base.xml` und
   `/opt/solvis/SolvisServerData/` sind wiederhergestellt.
3. Eigentümer und Dateirechte neu setzen (Pflicht; Soll-Bild der
   S-5-Härtung: Programmpfad root-eigen, Zustand dem Dienstkonto) —

   ```bash
   sudo chown root:root /opt/solvis /opt/solvis/SolvisSmartHomeServer
   sudo chown -R solvis:solvis /opt/solvis/SolvisServerData
   sudo chown root:solvis /opt/solvis/SolvisSmartHomeServer/base.xml
   sudo chmod 640 /opt/solvis/SolvisSmartHomeServer/base.xml
   ```

   → erwartet: `stat -c '%a %U:%G' /opt/solvis/SolvisSmartHomeServer/base.xml`
   = `640 root:solvis`; Jar und beide Verzeichnisse gehören `root`.
4. Dienst starten und prüfen — `sudo systemctl start SolvisSmartHomeServer.service`
   → erwartet: `active (running)`, Health-Token frisch, `solvis/#`-Topics
   erscheinen wie vor dem Restore.

*Alternative für Entwicklung/Test:* Entpacken neben die `docker-compose.yml`,
Eigentümer auf die Container-UID (`chown 10001:10001 base.xml`), dann
`docker compose up -d`.

**Automatisierung**: `bewusst manuell` — Auslöser: Operator (DR-Fall); Grund:
Restore fasst Secret-Material an und erfordert manuelles Setzen der Dateirechte
(Vier-Augen / Separation of Duties) — kein unbeaufsichtigter Automatismus.

---

## Aufgabe: Stoppen / Restart

Sauber herunterfahren, damit Last Will / Disconnect greifen.

1. Stoppen — `sudo systemctl stop SolvisSmartHomeServer.service`
   → erwartet: SIGTERM durch systemd → sauberer Disconnect,
   `solvis/server/online` geht auf `false` (retained, LWT).
2. Neustart — `sudo systemctl restart SolvisSmartHomeServer.service`
   → erwartet: `active (running)`, Health-Token wieder frisch,
   `solvis/server/online` = `true`.
3. Dauerhaft abschalten (z. B. für den Rollback auf `mon-dg`) —
   `sudo systemctl disable --now SolvisSmartHomeServer.service`
   → erwartet: `inactive` und `disabled`.

Über die `make`-Ziele: `sudo make -C SmartHome/Linux stopServices` bzw.
`terminate` (ruft `--server-terminate`, braucht den TCP-Port 10735).

*Alternative für Entwicklung/Test:* `docker compose down`, Neustart mit
`docker compose up -d`.

**Automatisierung**: `Script (on-demand)` — `systemctl`-Aufrufe bzw.
`SmartHome/Linux/Makefile` Target `stopServices`/`terminate`.
