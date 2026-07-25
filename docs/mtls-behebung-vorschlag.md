# mTLS-Behebung — Vorschlags-Dokument (zur architect-Abstimmung)

**Status**: VORSCHLAG (Design, kein Code-Fix). Zur Abstimmung durch
`architect` / stack-master.
**Autor**: solvis-connector-dev (Orchestrator-Delegation, User-Direktive
2026-07-25).
**Auslöser**: Audit-Findings S-1 (security), SR-1/SR-2 (sre) vom 2026-07-25.
**Auditierter Stand**: Branch `feature/modernisierung`, HEAD `b35b704`.
**Geltung**: Bounded Context solvis-connector; publiziert/konsumiert
ausschließlich `solvis/#` (ADR-0017 D1).

Dieses Dokument präzisiert die Ursache der mTLS-Störung, stellt drei
Optionen mit Trade-offs gegenüber, spricht eine Empfehlung aus und
markiert, was die Abstimmung mit stack-master klären muss. Es wird
**nichts umgesetzt** — die genannten Code-Zeilen sind Vorschlag, kein Patch.

## 1. Root-Cause-Präzisierung (am Code verifiziert)

Variante B (natives mTLS) ist über den dokumentierten Konfigurationsweg
(`base.xml` `port="8883"` + `<Ssl enable="true">`, siehe
`docs/INBETRIEBNAHME.md` Phase 3) funktionsunfähig. Ursache ist ein
Konflikt zwischen URI-Schema und gesetzter Socket-Factory in Eclipse
Paho v3:

- `Mqtt.java:104` baut die Broker-URI **hartkodiert** mit `tcp://`-Schema:

  ```java
  this.client = new MqttClient("tcp://" + this.brokerUrl + ":" + this.port,
          this.idPrefix + "_" + MqttClient.generateClientId(),
          new MemoryPersistence());
  ```

- `MqttThread.java:46-57` setzt bei aktiviertem TLS zusätzlich die
  Socket-Factory:

  ```java
  if (this.config.getSsl() != null && this.config.getSsl().isEnabled()) {
      SSLSocketFactory sslSocketFactory = this.config.getSsl().getSocketFactory();
      options.setSocketFactory(sslSocketFactory);
  }
  ```

- Paho v3 wählt das Netzwerk-Modul **am URI-Schema**: `tcp://` →
  `TCPNetworkModuleFactory`, `ssl://` → `SSLNetworkModuleFactory`. Die
  `TCPNetworkModuleFactory` lehnt eine gesetzte `SSLSocketFactory` mit
  `REASON_CODE_SOCKET_FACTORY_MISMATCH` (32105) ab. Der Bytecode-Beleg
  dazu liegt im security-Report S-1 (`javap -c` auf
  `paho.client.mqttv3-1.2.5.jar`).

- Laufzeitfolge: `MqttThread.java:78` `client.connect(options)` wirft die
  `MqttException` 32105; sie wird im Retry-Loop gefangen und auf
  **INFO-Level** mit irreführendem Text protokolliert
  (`MqttThread.java:85` „Mqtt broker not available, will be retried in
  Xs") — endlos. **Positiv/fail-closed**: kein Klartext-Fallback
  (`MqttThread.java:50-56` bricht bei TLS-Fehler bewusst ab).

**Die genau eine load-bearing Änderung, die Variante B funktionsfähig
machen WÜRDE** (Vorschlag, nicht umgesetzt): in `Mqtt.java:104` das Schema
aus `Ssl.isEnabled()` ableiten statt es zu hartkodieren — also
`ssl://` statt `tcp://`, sobald `<Ssl enable="true">` gesetzt ist. Der
Port ist bereits operator-gesetzt (`base.xml` `port="8883"`), somit ist
das Schema der einzige fehlende Baustein; ergänzend wäre eine
Default-Port-Ableitung (8883 bei SSL, sonst 1883) sauberer, aber nicht
zwingend. Alles Weitere (In-Memory-KeyStore, Cert-/PKCS#8-Handling in der
`Ssl`-Klasse) ist komponentenverifiziert (CHANGELOG-fork.md,
`HANDSHAKE_OK`) — es fehlt ausschließlich der Integrationspfad über
`client.connect()`.

**Merkposten (S-1-Lehre)**: Die Reparatur allein genügt nicht als Nachweis.
Ein Integrationstest muss denselben Einstiegspunkt prüfen, den die
Produktion nutzt — `client.connect()` gegen einen lokalen TLS-Broker /
`openssl s_server`, nicht nur `SSLSocket.startHandshake()`
(NIST SP 800-53 CA-7 / SA-11; `wiederkehrende-verifikation.md` §7).

## 2. Optionen mit Trade-offs

Bewertungsachsen je Option: Security-Impact (BSI TR-02102-2 mTLS),
Betriebs-Impact (SR-1 Liveness gehört in **jede** Option, §4),
Komplexität (Pragmatiker-Linse: Moving Parts), Vertragstreue
(`solvis/#`-only, ADR-0017), Aufwand S/M/L.

### Vertrags-Kontext (wichtig für alle Optionen)

Die Bridge-Gegenseite bei `ccu2mqtt` (`docs/solvis.md` §6/§7,
Betreiber-Entscheid 2026-07-21 „Option A, Wiederbelebung") ist bereits auf
**Variante A** provisioniert:

- Client-Zertifikat `solvis-bridge` ausgestellt (erledigt 2026-07-24) —
  ausgestellt für die **lokale Mosquitto-Bridge**, nicht für den
  Java-Connector direkt.
- ACL-SSOT um Login `solvis-bridge` mit `readwrite solvis/#` ergänzt
  (erledigt 2026-07-24).
- Zielarchitektur §7: Connector → lokaler Mosquitto (localhost-only) →
  mTLS-Bridge (`topic solvis/# both`) → Primär-Broker. Gleiches Muster wie
  CCU-Jack und die FHEM-Bridge.

ADR-0017 D1 formuliert die Vertragstopologie wörtlich als „über **lokalen
Broker + mTLS-Bridge**". Variante A deckt sich damit; jede Abkehr davon
berührt den Landschafts-Vertrag (siehe §3).

### Option A — Committen auf Variante A, Variante B deprecaten

Lokaler Broker + mTLS-Bridge als **einzige unterstützte** Produktivvariante
(FORK.md-Zielbild). Variante B wird als „nicht unterstützt" markiert; das
`<Ssl>`-Feature bleibt entweder mit Fail-Fast-Guard bestehen (statt Silent-
Retry) oder die Verdrahtung wird entfernt. Zusätzlich muss das
Container-Problem aus S-9.2 gelöst werden: im Docker-Deployment ist
`127.0.0.1` das **Container**-Loopback, nicht der Host; der lokale
Mosquitto ist so nicht erreichbar.

- **Security (TR-02102-2)**: TLS terminiert in Mosquitto (ausgereifter
  TLS-Stack, nicht die paho-v3-Config der App). Der einzige Klartext-Hop
  liegt Connector → lokaler Broker. Das Loopback-Argument trägt **nur**,
  solange dieser Hop echtes Loopback ist — bei Auflösung über ein
  Container-Bridge-Netz entfällt es (S-9.2). Auf dem LAN-querenden Hop
  (Bridge ↔ Primär-Broker) ist mTLS erfüllt.
- **Betrieb (SR-1)**: Health-Signal nach §4 zwingend. Zusätzlich zweiter
  Prozess (lokaler Mosquitto) im Betriebsbild — eigene Restart-/Log-Sicht.
- **Komplexität (Moving Parts)**: **ein Broker mehr**, dafür kein
  App-TLS-Pfad, keine paho-Cipher-Konfiguration, kein Connector-Cert. TLS-
  Komplexität liegt an der bewährten Stelle (Mosquitto), nicht in der App.
- **Vertragstreue**: `solvis/#`-only bleibt. Deckt ADR-0017 D1 **wörtlich**;
  nutzt die bei ccu2mqtt bereits provisionierte Gegenseite (Cert + ACL).
  Kein ADR nötig.
- **Aufwand**: **S–M**. Kein Code-Fix am mTLS-Pfad nötig; Compose-/Netz-
  Anpassung (S), Deprecation-Doku + optionaler Guard (S), Betriebsdoku (M).

Container-`127.0.0.1`-Auflösung (Unterentscheidung, → offene Frage 2):

- **A1 `network_mode: host`** für den Connector-Container → `127.0.0.1`
  ist Host-Loopback, lokaler Mosquitto direkt erreichbar. Einfachste
  Auflösung, Loopback-Argument bleibt intakt. Nebenwirkung: der
  proprietäre TCP-Server (S-3) bindet dann auf Host-Interfaces — muss mit
  Bind `127.0.0.1` / Abschaltung (S-3-Fix) verzahnt werden.
- **A2 Mosquitto als Sidecar** im selben Compose-Netz; Connector zielt auf
  Service-Namen (nicht `127.0.0.1`). Dann ist der Hop ein Container-Bridge-
  Netz, kein Loopback → das Klartext-Argument wird schwächer und muss als
  bewusste Entscheidung dokumentiert werden (Netz nicht ans LAN exponiert).
- **A3 systemd statt Docker** auf `ransible` (wie ccu2mqtt §7 wörtlich
  beschreibt: Server auf `.135`, dortiger Mosquitto localhost-only). Dann
  ist `127.0.0.1` echtes Host-Loopback ohne Container-Frage — aber die
  Docker-Deploymentlinie des Forks wird für diese Variante nicht genutzt.

### Option B — Variante B reparieren (natives mTLS direkt)

`ssl://`-Schema (§1) + explizite TLS-Parameter, lokaler Broker und Bridge
entfallen; der Connector verbindet sich **direkt** mTLS zum Primär-Broker.

- **Security (TR-02102-2)**: stärkstes Ergebnis — **kein Klartext-Hop**,
  Ende-zu-Ende-mTLS ab dem Connector. Aber: die paho-v3-Client-TLS-Config
  muss Protokoll/Cipher **explizit** setzen (nicht erben — dieselbe Lehre
  wie Mail-S-7); Zertifikats-/Key-Handling (CA, Client-Cert, PKCS#8) liegt
  in der App. paho v3 ist die Alt-Linie (kein v5). Heute
  **funktionsunfähig** bis zur Reparatur.
- **Betrieb (SR-1)**: Health-Signal nach §4 zwingend; hier gibt es keinen
  lokalen Broker als Beobachtungspunkt → das dateibasierte Ready-Signal (§4)
  ist hier die einzige portable Option.
- **Komplexität (Moving Parts)**: **eine Bridge weniger, ein Broker
  weniger** im Deployment — dafür TLS-Zustandsverwaltung in der App und ein
  **connector-eigenes** Client-Zertifikat, das ccu2mqtt heute **nicht**
  ausgestellt hat (das `solvis-bridge`-Cert gehört der Bridge). Neue PKI-/
  ACL-Identität nötig.
- **Vertragstreue**: `solvis/#`-only bleibt, **aber** die Topologie („über
  lokalen Broker + mTLS-Bridge", ADR-0017 D1) ändert sich → **Vertrags-
  wortlaut betroffen**, ccu2mqtt-Gegenseite (Bridge-Plan §7, Cert-Rolle,
  ACL-Identität) ändert sich. **ADR-Kandidat.**
- **Aufwand**: **L**. Code-Fix selbst S (Schema + Cipher), aber
  Integrationstest (M) + Cross-Projekt-Koordination ccu2mqtt (Connector-
  Cert, ACL-Login, Bridge-Plan verwerfen) + ADR (L) dominieren.

### Option C — Hybrid (A produktiv, B repariert und belegt als Zweitoption)

Variante A ist die Produktivvariante; Variante B wird trotzdem repariert
(`ssl://`) **und** integrationsgetestet, damit das Fork-Feature ehrlich ist
(konfiguriert = durchgesetzt) statt als kaputte Falle zu verbleiben.

- **Security**: wie A im Betrieb; die reparierte B-Fähigkeit ist belegt
  (kein Silent-Trap mehr), Fail-Fast statt Endlos-Retry.
- **Betrieb**: wie A; zusätzlich zwei getestete Pfade zu pflegen.
- **Komplexität**: höchste — **zwei** unterstützte Transportwege, doppelte
  Testfläche, größerer Upstream-Diff. Widerspricht „Upstream-Diff klein
  halten", solange B nicht wirklich gebraucht wird.
- **Vertragstreue**: A-Betrieb bleibt vertragskonform; B bleibt als
  dokumentierte, aber ungenutzte Option (kein aktiver Vertragswechsel,
  solange nicht produktiv geschaltet).
- **Aufwand**: **M–L**. A (S–M) + B-Reparatur inkl. Integrationstest (M).

## 3. Empfehlung

**Empfohlen: Option A** — Committen auf Variante A (lokaler Broker +
mTLS-Bridge), Variante B als „nicht unterstützt" deprecaten, mit
Fail-Fast-Guard (statt Silent-Retry) und dem Health-Signal aus §4.

Begründung:

- **Vertragskonform ohne Reibung.** ADR-0017 D1 nennt die Topologie
  wörtlich; die ccu2mqtt-Gegenseite ist bereits auf Variante A
  provisioniert (Cert `solvis-bridge`, ACL `readwrite solvis/#`, Bridge-
  Plan §7). Option A nutzt vorhandene Bausteine statt sie zu ändern.
- **Pragmatiker-Linse.** Der „eine Broker mehr" ist im Stack bereits
  etabliertes Muster (CCU-Jack, FHEM). Dafür entfällt die riskanteste
  Komplexität — App-seitige TLS-Konfiguration in paho v3 — und der offene
  S-1-Integrationspunkt. Kein Code-Fix am gebrochenen mTLS-Pfad nötig.
- **Kleinster Koordinations- und Diff-Aufwand.** Keine neue PKI-Identität,
  keine Cross-Projekt-Änderung an ccu2mqtt, kein ADR, kein Upstream-Diff im
  Java-Baum außer dem Deprecation-/Guard-Teil.

Konsequenz für Variante B: **nicht halb-kaputt stehen lassen.** Der
Fail-Fast-Guard (32105 sofort laut abbrechen mit klarer Ursache statt
Endlos-INFO-Retry) gehört in **jede** Option, weil er die S-1-Fehldiagnose
beseitigt. Ob Variante B darüber hinaus repariert und integrationsgetestet
als dokumentierte Zweitoption erhalten bleibt (Option C) oder die
`<Ssl>`-Verdrahtung entfernt wird, ist eine bewusste architect-Entscheidung
(offene Frage 3) — Design-Prinzip: ein beworbenes, aber ungetestetes
Feature ist eine Last (genau die S-1-Falle), kein Wert.

**Verändert die Wahl den Vertrag?**

- **Empfohlene Option A: nein.** ADR-0017 D1 und
  `shared/standards/hausautomation-landschaft.md` (Zeile „Laufzeit-Daten
  Heizung … mTLS-Bridge") bleiben unverändert gültig; die ccu2mqtt-
  Gegenseite bleibt wie provisioniert. **Kein ADR nötig** — die offene
  Frage 1 aus `docs/ARCHITECTURE.md` §6 wird lediglich zugunsten A
  entschieden und nachgetragen.
- **Nur bei Wahl von Option B: ja → ADR-Kandidat.** Direktes mTLS ändert
  die in ADR-0017 D1 wörtlich fixierte Topologie („lokaler Broker +
  mTLS-Bridge") und die ccu2mqtt-Bridge-Gegenseite (Connector-Cert statt
  Bridge-Cert, ACL-Identität, Bridge-Plan §7 hinfällig). Das ist ein
  Landschafts-Vertrags-Eingriff und erfordert Abstimmung mit
  architect/stack-master plus User-Akzeptanz.

## 4. Liveness / SR-1 als Querschnitt (gilt in jeder Option)

`restart: unless-stopped` deckt nur Prozess-Crash; der mTLS-Retry-Loop
terminiert nicht, der Container bleibt „Up" während der Connector
funktional tot ist (SR-1). `solvis/server/online` (LWT) ist im
Nie-Verbunden-Fall stumm (SR-2). Vorschlag für ein **echtes** Health-Signal,
das ohne lokalen Broker auskommt und damit in allen Optionen (A/B/C) trägt:

- **Connector-internes Ready-Token (dateibasiert).** Bei erfolgreichem
  `client.connect()` + `subscribe()` schreibt der Connector einen
  Zeitstempel nach `/data/health/mqtt-ready` (bzw. aktualisiert dessen
  `mtime`); bei Disconnect-Callback wird die Datei entfernt/veraltet
  gelassen. Deckt den Nie-Verbunden-Fall (SR-2): Datei fehlt von Anfang an
  = ungesund.
- **HEALTHCHECK** in `Dockerfile`/`docker-compose.yml`, der die **Frische**
  dieses Tokens prüft (`now - mtime < Schwellwert`), nicht die PID. Damit
  meldet Docker/systemd den Silent-Failure als `unhealthy`
  (Symptom-basiert, Google SRE Kap. 6; Liveness ≠ Readiness ≠ Funktion,
  CNCF Observability WP).
- **Log-Schärfung (SR-1-Nebenbefund):** den Connect-Fehler von INFO auf
  WARN/ERROR heben und bei 32105 die Ursache (Client-Config, nicht Broker)
  benennen. Das ist Teil des Fail-Fast-Guards aus §3.
- **Optional (nur Variante A):** ein externer Probe kann zusätzlich das
  retained `solvis/server/online` am lokalen Broker auf `== online` UND
  Frische prüfen. Für Portabilität über alle Optionen ist das Ready-Token
  die tragende Lösung, `online` die Ergänzung.

Alle drei Kernpunkte (Token, HEALTHCHECK, Log-Schärfung) sind
transport-unabhängig und damit **Vorbedingung jeder** Transport-Entscheidung,
nicht ihr Anhängsel.

## 5. Offene Fragen an architect (zur Abstimmung mit stack-master)

1. **Variantenentscheid final?** Bestätigung, dass Variante A die
   unterstützte Produktivvariante wird (offene Frage 1 aus
   `docs/ARCHITECTURE.md` §6) — konsistent mit ADR-0017 D1 und dem
   ccu2mqtt-Betreiber-Entscheid 2026-07-21 „Option A"?
2. **Container-Netz (nur Option A):** `network_mode: host` (A1),
   Mosquitto-Sidecar im Compose-Netz (A2) oder systemd-auf-`ransible`
   ohne Container (A3)? Wie lösen CCU-Jack und die FHEM-Bridge das heute —
   gibt es eine Stack-Konvention, an die sich solvis anlehnen soll? A2
   schwächt das Loopback-Klartext-Argument (S-9.2) und braucht eine
   dokumentierte Entscheidung.
3. **Variante-B-Disposition:** `<Ssl>`-Verdrahtung entfernen (kleinste
   Wartungs-/Angriffsfläche, kleinster Upstream-Diff) **oder** reparieren +
   integrationstesten als dokumentierte Zweitoption (Option C)? Der
   Fail-Fast-Guard kommt in jedem Fall.
4. **Nur falls B je gewählt wird:** Stellt ccu2mqtt ein **connector-eigenes**
   Client-Zertifikat aus (das heutige `solvis-bridge`-Cert gehört der
   Bridge)? Welche ACL-Identität/-Rechte, und wer ist PKI-Owner? (Betrifft
   `ccu2mqtt:docs/solvis.md` §7.)
5. **Vertrags-Impact bestätigen:** Einverständnis, dass Option A **keinen**
   ADR und keine Änderung an `hausautomation-landschaft.md` erfordert,
   Option B hingegen ein ADR (ADR-0017-Topologie + ccu2mqtt-Bridge-
   Gegenseite) auslöst?
6. **Health-Signal-Konvention:** Ist das dateibasierte Ready-Token (§4) als
   Cross-Cutting-Mechanismus akzeptiert, oder gibt der Stack (z. B. via
   mqtt-broker-addon) eine abweichende Probe-/HEALTHCHECK-Konvention vor,
   an die solvis sich angleichen soll?

## Referenzen

- Audit S-1 (security, 2026-07-25):
  `stack-master:shared/audit-log/2026-07-25-solvis-connector-security.md`
- Audit SR-1/SR-2 (sre, 2026-07-25):
  `stack-master:shared/audit-log/2026-07-25-solvis-connector-sre.md`
- Vertragstopologie: ADR-0017 D1/D2
  (`stack-master:shared/architecture-decisions/`),
  `shared/standards/hausautomation-landschaft.md`
- Bridge-Gegenseite: `ccu2mqtt:docs/solvis.md` §6/§7
- Fork-Zielbild: `FORK.md`, `docs/ARCHITECTURE.md` §4/§6
- Verifikations-Lehre: `stack-master:shared/standards/wiederkehrende-verifikation.md`
  §7; Standards BSI TR-02102-2, NIST SP 800-53 CA-7/SA-11, OWASP ASVS V9.1.1
