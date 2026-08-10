# Runbooks (OPERATEUR-Lens)

> **Sprache:** Deutsch · **Status:** aktiv · **Zielgruppe:** Operateure ·
> **Bezug:** [../INBETRIEBNAHME.md](../INBETRIEBNAHME.md) (Erstinbetriebnahme),
> [../DOCKER.md](../DOCKER.md) (Container-Grundlagen),
> [../ARCHITECTURE.md](../ARCHITECTURE.md) §4 (Betriebsmodell),
> [../../TESTPLAN.md](../../TESTPLAN.md) (Prüfschritte)

Day-2-Standardaufgaben für den laufenden Betrieb. Die **Erstinbetriebnahme**
(Image bauen → Anlage anbinden → MQTT → erster Start) steht Schritt für Schritt
in [../INBETRIEBNAHME.md](../INBETRIEBNAHME.md) und wird hier **nicht**
dupliziert, sondern vorausgesetzt.

Jede Aufgabe nennt einen **Automatisierung**-Wert aus dem geschlossenen
Vokabular des Cockpit-Doku-Standards (`systemd-timer` · `cron` · `CI-Job` ·
`Script (on-demand)` · `bewusst manuell`).

> **Platzhalter** in spitzen Klammern (`<…>`) durch eigene Werte ersetzen. Alle
> Kommandos laufen aus der Repo-Wurzel (Docker-Linie) bzw. via
> `SmartHome/Linux/Makefile` (native A3-Linie).

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

1. Prüfen, dass `base.xml` existiert und dateirechtegeschützt ist —
   `stat -c '%a %U' base.xml` → erwartet: `600 <dienstnutzer>` (UID 10001).
2. Steuer-Modus bewusst wählen: in `base.xml`
   `<tns:Feature id="InteractiveGUIAccess" value="false"/>` für reines
   Monitoring, `true` erst nach Freigabe (Steuerung klickt real an der Anlage).
3. Container starten — `docker compose up -d` → erwartet: Container
   `solvissmarthomeserver` läuft.
4. Logs beobachten — `docker compose logs -f solvis` → erwartet:
   Verbindungsaufbau ohne wiederholtes WARN „broker not reachable".
5. Health prüfen —
   `docker inspect --format '{{.State.Health.Status}}' solvissmarthomeserver`
   → erwartet: `healthy` (Ready-Token frisch, < 300 s).
6. Datenfluss prüfen — `mosquitto_sub -h 127.0.0.1 -p 1883 -t 'solvis/#' -v`
   → erwartet: `solvis/server/online true` plus `solvis/<unit>/…/data`-Topics.

**Automatisierung**: `bewusst manuell` — Auslöser: Operator; Grund: Der Start kann
(bei `InteractiveGUIAccess="true"`) reale Klick-Steuerung an der Anlage
auslösen — bewusste Freigabe statt Auto-Deploy (Least Privilege). Der
Dauerlauf danach läuft unbeaufsichtigt: `restart: unless-stopped`
(`docker-compose.yml`) bzw. systemd-Service
(`SmartHome/Linux/SolvisSmartHomeServer.service`, `make installService`); der
Docker-HEALTHCHECK (`Dockerfile`, `--interval=60s`) fängt Silent-Failure ab.

---

## Aufgabe: Update ausrollen

Neuen Fork-Stand (Jar/Image) in Betrieb nehmen.

1. Quellstand aktualisieren — `git pull` (Branch `feature/modernisierung`)
   → erwartet: `Already up to date` oder neue Commits.
2. Image neu bauen — `docker compose build`
   → erwartet: `BUILD SUCCESS`, Image `solvissmarthomeserver:fork` aktualisiert.
3. Neustart mit neuem Image — `docker compose up -d`
   → erwartet: Container wird neu erstellt (`Recreated`).
4. Health nach Update — `docker inspect --format '{{.State.Health.Status}}'
   solvissmarthomeserver` → erwartet: nach `start-period` wieder `healthy`.

Native A3-Linie (systemd): `make -C SmartHome/Linux update` — stoppt Dienst,
installiert Jar/`base.xml`, startet neu (`stopServices` → `install` →
`systemctl start`).

**Automatisierung**: `Script (on-demand)` — `SmartHome/Linux/Makefile`
Target `update` (native) bzw.
`docker compose build && docker compose up -d`. Der Build-Teil ist zusätzlich
durch den `CI-Job` `build` (`.github/workflows/build.yml`) abgesichert; der
Deploy-Schritt bleibt bewusst operatorgetrieben (kein Auto-Deploy).

---

## Aufgabe: OCR-Lernphase erneuern

Nach Änderungen an der SolvisRemote-Oberfläche die angelernten Screens neu
erzeugen (schreibt `LearnedImages` nach `/data`).

1. Laufende Instanz stoppen — `docker compose stop solvis`
   → erwartet: Container gestoppt.
2. Lernlauf starten — `docker compose run --rm solvis --server-learn`
   → erwartet: Durchlauf ohne Abbruch; danach neue Bilddaten unter `./data`.
3. Regelbetrieb wieder hochfahren — `docker compose up -d`
   → erwartet: `healthy`, `solvis/<unit>/…/data`-Topics erscheinen.

Native A3-Linie: `make -C SmartHome/Linux learn` (stoppt Dienst, ruft
`--server-learn`, startet Dienst neu).

**Automatisierung**: `Script (on-demand)` — `SmartHome/Linux/Makefile`
Target `learn` bzw.
`docker compose run --rm solvis --server-learn`. Bewusst nicht periodisch:
Auslöser ist eine erkannte GUI-Änderung, kein Zeitplan.

---

## Aufgabe: Backup (Operator-State)

Nicht-versionierten Operator-State sichern: `base.xml` (Konfig + `passwordCrypt`)
und `data/` (angelernte Bilder, generierte `control.xml`/Messwerte, Logs).

1. Dienst kurz anhalten (konsistenter Snapshot) — `docker compose stop solvis`
   → erwartet: Container gestoppt.
2. Verschlüsseltes Archiv erzeugen (Secrets nie im Klartext ablegen) —
   `tar czf - base.xml data | age -r <age-empfaenger-key> > backup-$(date +%F).tar.gz.age`
   → erwartet: Datei `backup-<datum>.tar.gz.age` entsteht.
3. Dienst wieder starten — `docker compose up -d` → erwartet: `healthy`.
4. Backup prüfbar hinterlegen (Prüfsumme) — `sha256sum backup-*.tar.gz.age`
   → erwartet: Hash notiert/mitgesichert.

**Automatisierung**: `bewusst manuell` — Auslöser: Operator; Grund: `base.xml`
enthält nur dateirechtegeschützte Secrets (`passwordCrypt` = Obfuskation) — kein
unverschlüsselter Auto-Export (Least Privilege / Separation of Duties). `data/`
ist zudem aus der Lernphase reproduzierbar, sodass ein automatischer Scheduler
im Connector-Repo bewusst entfällt.

---

## Aufgabe: Restore

Operator-State aus einem Backup wiederherstellen.

1. Dienst stoppen — `docker compose down` → erwartet: Container entfernt.
2. Archiv entschlüsseln und auspacken —
   `age -d -i <age-identity> backup-<datum>.tar.gz.age | tar xzf -`
   → erwartet: `base.xml` und `data/` sind wiederhergestellt.
3. Dateirechte auf die Secrets neu setzen (Pflicht) —
   `chmod 600 base.xml && chown 10001:10001 base.xml`
   → erwartet: `stat -c '%a %U' base.xml` = `600 <dienstnutzer>`.
4. Dienst starten und prüfen — `docker compose up -d` → erwartet: `healthy`,
   `solvis/#`-Topics erscheinen wie vor dem Restore.

**Automatisierung**: `bewusst manuell` — Auslöser: Operator (DR-Fall); Grund:
Restore fasst Secret-Material an und erfordert manuelles Setzen der Dateirechte
(Vier-Augen / Separation of Duties) — kein unbeaufsichtigter Automatismus.

---

## Aufgabe: Stoppen / Restart

Sauber herunterfahren, damit Last Will / Disconnect greifen.

1. Stoppen — `docker compose down`
   → erwartet: SIGTERM via `init: true` (tini) → `solvis/server/online` geht auf
   `false` (LWT).
2. Alternativ gezielt beenden — `docker compose run --rm solvis --server-terminate`
   → erwartet: laufende Instanz beendet sich selbst.
3. Neustart — `docker compose up -d` → erwartet: `healthy`.

Native A3-Linie: `make -C SmartHome/Linux stopServices` bzw. `terminate` (ruft
`--server-terminate`); der systemd-`ExecStop` nutzt ebenfalls
`--server-terminate` (`SolvisSmartHomeServer.service`).

**Automatisierung**: `Script (on-demand)` — `SmartHome/Linux/Makefile`
Target `stopServices`/`terminate` bzw.
`docker compose down`.
