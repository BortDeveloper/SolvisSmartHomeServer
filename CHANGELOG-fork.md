# Changelog dieses Forks

Dokumentiert alle Abweichungen vom Upstream
([GollmerSt/SolvisSmartHomeServer](https://github.com/GollmerSt/SolvisSmartHomeServer)).
Hintergrund und Ziel des Forks: siehe [FORK.md](FORK.md).

Format: neueste Änderung oben. Jede Änderung nennt Motivation, Ursache und
konkrete Anpassung, damit sie nachvollziehbar und ggf. als Upstream-PR
aufbereitbar ist.

## Inbetriebnahme-Vorarbeit: nativer systemd-Pfad erstklassig, Cutover-Kapitel, Adress-Pinning, Build-Verify nachgeholt

Umsetzung der Betreiber-Entscheide vom 2026-08-12 (stack-master):
Pfadentscheid **O1 gestaffelt** — der Fork wird auf dem Zielhost zunächst
**nativ (systemd)** aufgebaut; Docker folgt erst nach bewiesener Funktion als
separater Entscheid.

- **Nativer Deploy-Pfad fork-fertig gemacht.** Bisher führte
  [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md) ausschließlich durch die
  Docker-Linie, obwohl sie A3 (nativ) selbst als bevorzugt nannte; der
  Upstream-Install unter `SmartHome/Linux/` passte nicht zum Fork-Build
  (Makefile erwartete Jar/`base.xml`/`base.xsd` im Makefile-Verzeichnis, der
  Maven-Build schreibt nach `target/`, die `base.xsd` ist Jar-Ressource;
  `/usr/bin/java` ohne Versions-Guard). Anpassungen (rückwärtskompatibel):
  - `SmartHome/Linux/Makefile`: neues Ziel **`prepare`** (Brückenschritt
    Maven-Build → Install: kopiert `target/`-Jar und `base.xsd` aus `rsc/…`,
    legt die `base.xml`-Vorlage an, überschreibt eine vorhandene `base.xml`
    nie) und neues Ziel **`checkJava`** (bricht ab, wenn `javaPath` kein
    Java ≥ 17 ist; in `installSolvis` eingehängt). Zur Laufzeit validiert der
    Fork gegen die **Jar-interne** `base.xsd` — die installierte Kopie ist
    nur Referenz.
  - `SmartHome/Linux/SolvisSmartHomeServer.service` (Fork-Unit):
    `After=network-online.target`, `Restart=on-failure` + `RestartSec`,
    Stop über **SIGTERM** (sauberer Disconnect + LWT) statt `ExecStop` mit
    `--server-terminate` (das den abschaltbaren TCP-Port 10735 bräuchte),
    optionales `EnvironmentFile=/etc/default/solvissmarthomeserver` (u. a.
    `SOLVIS_HEALTH_TOKEN_PATH` — der Container-Default `/data/health/ready`
    ist nativ meist nicht beschreibbar), `NoNewPrivileges`; `javaPath` bleibt
    Makefile-substituiert (kein nacktes `/usr/bin/java` mehr vorausgesetzt).
  - [docs/INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md) neu gegliedert: native
    Schritte in jeder Phase zuerst, Docker als gekennzeichnete Alternative;
    neue Phase 4 (systemd-Install inkl. Brückenschritt und
    Betriebsparameter). Zielhost-OS der Referenz-Installation: UNVERIFIED,
    Schritte parametrisiert für Debian-artige Systeme.
- **Cutover-Kapitel (HIGH-Lücke geschlossen).** Neue Phase 5 in
  INBETRIEBNAHME.md: Checkliste vor Lernphase/GO-Live — Alt-Dienst auf
  `mon-dg` gestoppt+disabled (Ist-Stand 2026-08-12; Verifikation
  `systemctl is-active/is-enabled`), Entzug des `solvis/#`-Übergangsbestands
  (mon-dg-ACL, `bridge.conf`) als Operator-Schritt im Repo `ccu2mqtt`
  referenziert, **Zwei-OCR-Clients-Verbot** (GUI-Kollision =
  Fehlsteuerungsrisiko an der realen Heizung) und dokumentierter
  Rollback-Weg (`systemctl enable --now` der noch installierten
  Alt-Software). Dazu [TESTPLAN.md](TESTPLAN.md) **Phase 9**
  (Cutover-Verifikation: T9.1 Alt-Dienst still, T9.2 genau ein Publisher auf
  `solvis/#`).
- **Adress-Pinning (F-119-Klasse).** SolvisRemote hat eine feste Adresse
  **`192.168.1.35`** (Fritzbox-DHCP-Reservierung, bestätigt 2026-08-12).
  Alle Beispiel-/Vorlagen-Konfigurationen (INBETRIEBNAHME-Snippets,
  TESTPLAN-Vorbedingungen/T4.1, Kommentar in der versionierten
  `base.xml`-Vorlage) pinnen die IP; `solvis.fritz.box` wird überall mit
  Warnung geführt (DNS zeigt noch auf `.49`, dort antwortet ein unbekanntes
  Gerät — Hostname erst nach DNS-Bereinigung). Die von den
  Charakterisierungstests gepinnten Vorlagen-**Werte** blieben unverändert
  (nur XML-Kommentare ergänzt).
- **Build-Verify nachgeholt (Stale-target-Befund behoben).** Im Repo lag ein
  `target/`-Jar vom 22.07. mit logback 1.5.12, obwohl HEAD seit dem
  CVE-Sprint 1.5.13 deklariert. HEAD am **2026-08-12** frisch gebaut
  (OpenJDK 17.0.5 / Windows 11 / Maven 3.9.16): `BUILD SUCCESS`,
  **91/91 Tests grün**, im Uber-Jar **logback-classic 1.5.13** bestätigt;
  Laufzeit-Smoke `--string-to-crypt=probe` ok. TESTPLAN-Referenz-Baseline
  entsprechend nachgeführt; „vor Deployment frisch bauen" ist jetzt
  Pflichthinweis in INBETRIEBNAHME Phase 1. T1.2-Prüfkommando auf
  Klassen-Pfade geschärft (das grobe Wort-Muster traf vier benigne
  Ressourcen-/Logback-Einträge).

## logback-classic auf 1.5.13 (CVE-2024-12798 / CVE-2024-12801) + G2.6-Clone-Drill

Sicherheits- und Verifikations-Sprint (stack-master, 2026-07-25).

- **logback-classic 1.5.12 → 1.5.13 (CVE-Fix, G2.1-CVE-Teil).** 1.5.12 ist
  betroffen von CVE-2024-12798 (JaninoEventEvaluator: Arbitrary Code
  Execution über manipulierte Logback-Konfiguration) und CVE-2024-12801
  (SaxEventRecorder: SSRF/XXE über externe Entities). 1.5.13 ist die
  fixende Version. Die eigene `rsc/logback.xml` nutzt weder Janino-Conditionals
  noch externe Entities (nur `ConsoleAppender` + Pattern-Encoder), die
  Angriffsfläche war also nie aktiv — der Bump schließt die transitive
  CVE-Exposition dennoch. `logback.version`-Property angehoben, der
  `dependencyManagement`-Pin bleibt: 1.5.13 zieht `slf4j-api` 2.0.16, was
  der direkt deklarierten Version entspricht (Enforcer-Konvergenz eindeutig).
  Dependency-Baum-Konsistenz mangels lokal verfügbarem Maven über
  pom-Konsistenz + Changelog-Beleg statt `mvn dependency:tree` verifiziert.
  Die Dependabot-Reaktivierung bleibt separater Operator-Akt (F-34).
- **G2.6-Clone-Drill real ausgeführt.** Bundle → Temp-Clone → Verifikation
  von HEAD und Commit-Zahl (618) — beide PASS, Temp gelöscht. Protokoll nach
  Standard §3: [docs/g2.6-clone-drill.md](docs/g2.6-clone-drill.md). Der
  Drill bestätigt zugleich: `base.xml` ist eine versionierte Vorlage; die
  realen Zugangsdaten sind nicht-versionierter Operator-State im Schreibpfad.

## Betriebshärtung: Variante A festgeschrieben, Variante B deprecated, Health-Signal, TCP-Server abschaltbar

Umsetzung der architect-Abstimmung „Option A" (Auflagen A-1…A-4) und der
Security-/SRE-Audit-Findings S-1/S-2/S-3, SR-1/SR-2 (stack-master,
2026-07-25). Ziel: den mTLS-Betrieb auf die tatsächlich unterstützte Topologie
festlegen und die geerbten Upstream-Restrisiken je Posten entscheiden.

- **Variante B (natives `<Ssl>`-mTLS direkt zum Broker) deprecatet statt
  halb-repariert (A-3):** Ursache der Störung ist ein Konflikt in Eclipse
  Paho v3 — die hartkodierte `tcp://`-URI (`Mqtt.java`) plus gesetzte
  `SSLSocketFactory` (`MqttThread.java`) wird mit
  `REASON_CODE_SOCKET_FACTORY_MISMATCH` (32105) abgelehnt und lief bisher in
  einen **Silent-Endlos-Retry** mit der irreführenden Log-Zeile „broker not
  available" (INFO). Statt den zweiten mTLS-Transportpfad zu reparieren
  (größerer Upstream-Diff, zweite Testfläche), wird er entfernt: die
  `setSocketFactory`-Verdrahtung ist raus, und `Mqtt.connect()` bricht bei
  `<Ssl enable="true">` sofort per **Fail-Fast-Guard** mit klarer Ursache ab
  (Exit statt Endlos-Retry). Unterstützt ist damit **Variante A** (lokaler
  Mosquitto auf `127.0.0.1` + mTLS-Bridge, Login `solvis-bridge`) — dieselbe
  Topologie wie CCU-Jack/FHEM-Bridge; die Bridge-Gegenseite (Cert/ACL) liegt im
  Repo `ccu2mqtt`. Der Connect-Fehler wird jetzt auf **WARN** (statt INFO) mit
  korrekter Ursache geloggt. Details/Begründung:
  [docs/mtls-behebung-vorschlag.md](docs/mtls-behebung-vorschlag.md),
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) §4.
- **Dateibasiertes Health-Signal (A-4 / SR-1):** Neu `HealthToken` schreibt bei
  erfolgreichem Connect + laufendem Publish einen Zeitstempel nach
  `/data/health/ready` (überschreibbar via `-Dsolvis.health.tokenPath` /
  `SOLVIS_HEALTH_TOKEN_PATH`, gedrosselt auf 30 s) und entfernt ihn bei
  Verbindungsverlust/Shutdown. Der neue `HEALTHCHECK` im `Dockerfile` prüft die
  **Frische** dieses Tokens (nicht die PID) — damit meldet Docker den
  Silent-Failure des Retry-Loops als `unhealthy` (Google SRE Kap. 6: Symptom
  statt Lebendigkeit). Deckt auch den Nie-Verbunden-Fall (SR-2): fehlendes
  Token von Anfang an = ungesund.
- **Proprietärer TCP-/JSON-Server bind-beschränkt + abschaltbar (S-3):** Der
  unauthentifizierte Server (Port 10735/10736) band bisher bedingungslos auf
  `0.0.0.0`. Jetzt Default-Bind **`127.0.0.1`** und vollständig deaktivierbar —
  konfigurierbar über `solvis.tcpServer.bindAddress` /
  `SOLVIS_TCPSERVER_BINDADDRESS` bzw. `solvis.tcpServer.enable` /
  `SOLVIS_TCPSERVER_ENABLE` (`Main.java`, mit Null-Guards an Server-Konstruktion
  und `closeSocket`). Kleiner, upstream-tauglicher Patch.
- **`passwordCrypt`-Dateirechte dokumentiert (S-2):** `CryptAes` ist Obfuskation
  mit öffentlich ableitbarem Schlüssel (ECB) — kein Krypto-Umbau (out of scope,
  Roadmap 4.6), aber [INBETRIEBNAHME.md](docs/INBETRIEBNAHME.md) verlangt jetzt
  `chmod 600 base.xml` (Owner = Dienstnutzer) als Pflichtschritt, und
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) §3/§5 benennt Dateirechte als den
  einzigen realen Schutz.
- **Doku (A-1):** `docs/ARCHITECTURE.md` schreibt Variante A als unterstützt
  fest, markiert Variante B als deprecated, schließt die offenen Fragen 1
  (Variante) und 2 (TCP-Server) und erfüllt damit ADR-0017 Folgeentscheidung 5
  (konsolidierte Betriebsmodell-Sicht). `docs/INBETRIEBNAHME.md` Phase 3 führt
  den Operator auf Variante A (lokaler Broker + Bridge, Container-Netz A3/A1/A2).
- **Verifikation:** statische Änderung, minimal am geprüften Code; kein
  Build-/Live-Lauf in dieser Umgebung (Maven-Wrapper-Download im Sandbox nicht
  verfügbar). Integrationstest des Connect-Pfads bleibt Operator-/Test-Schritt.

## Modernisierung Stufe 4.1/4.2: Mail-TLS-Prüfung + Abhängigkeits-Hygiene

- **Sicherheitskorrektur Mail-Versand:** Bisher setzte der Mail-Versand
  `mail.smtp.ssl.trust="*"` — damit wurde **jedem** SMTP-Server-Zertifikat
  vertraut (anfällig für Man-in-the-Middle; das Mail-Passwort ging an jeden,
  der sich als Provider ausgibt). Jetzt gilt die JSSE-Standardprüfung gegen
  die System-Truststore-CAs. **Auswirkung:** Öffentliche Provider (z. B.
  securesmtp.t-online.de) funktionieren unverändert. Wer einen privaten
  SMTP-Server mit selbstsigniertem Zertifikat nutzt, muss dessen Zertifikat
  in den Java-Truststore importieren
  (`keytool -importcert -cacerts -alias meinsmtp -file server.crt`).
- **Dependabot** (Maven + GitHub-Actions, wöchentlich) hält Abhängigkeiten
  aktuell; die CI prüft jeden Update-PR mit Build + Testsuite (JDK 17/21).
- **maven-enforcer-plugin:** Build erzwingt Java ≥ 17 und
  Dependency-Konvergenz (widersprüchliche transitive Versionen brechen den
  Build, statt still zu „gewinnen").

## Modernisierung Stufe 3.4: jakarta.mail + Windows-Ballast isoliert

- **`javax.mail` → `jakarta.mail`:** Das EOL-gegangene `com.sun.mail` 1.6.2
  (mailapi + smtp) und `com.sun.activation:javax.activation` durch
  **`org.eclipse.angus:angus-mail:2.0.3`** ersetzt (Referenzimplementierung,
  bringt `jakarta.mail-api` + `jakarta.activation` transitiv). Alle Importe in
  `mail/Mail.java`, `mail/ExceptionMail.java` u. a. auf `jakarta.*` umgestellt
  (die JDK-Zeichenkette `javax.net.ssl.SSLSocketFactory` blieb korrekt
  unberührt). Im Uber-Jar jetzt `jakarta.mail`/`jakarta.activation`, kein
  `javax.mail` mehr.
- **Windows-Ballast isoliert:** Das `windows`-Paket (`Task` erzeugt eine
  Windows-Task-Scheduler-XML über `--create-task-xml`) per `package-info.java`
  als **optionale, JDK-only Windows-Hilfe** dokumentiert und klar abgegrenzt.
  Bewusst **nicht gelöscht** (bräche die CLI-Option; kein Windows-natives API,
  auf anderen Plattformen einfach ungenutzt). Der InnoSetup-Installer bleibt
  unter `SmartHome/Windows/`, außerhalb des Maven-Builds.
- **Verifikation (JDK 17):** BUILD SUCCESS, 41 Tests grün.

## Modernisierung Stufe 3.1: OCR-Kern isoliert

- **Schichtungs-Leck entfernt:** Das Bildmodul (`MyImage`) hing über
  `getByteArrayDataSource()` an **`javax.mail`** — fachlich fehl am Platz
  (Bilderkennung braucht keine Mail-Bibliothek). `MyImage` liefert jetzt
  `getImageBytes()` (`byte[]`); das Einpacken in den Mail-Anhang macht die
  Mail-Schicht (`mail.Mail`). Der `javax.mail`-Import ist aus dem OCR-Modul
  verschwunden.
- **Modulgrenze dokumentiert:** `package-info.java` für `ocr`, `image` und
  `pattern` (Zweck, öffentliche Schnittstelle
  `new Ocr(new MyImage(img)).toChar()`, minimale Abhängigkeitsgrenze — nur
  JDK-Bildklassen, geteilte Geometrie-Typen, Logging —, Golden-Test-Schutz und
  die Zuschreibung der OCR-Idee an Stefan Gollmer (GollmerSt)).
- Der OCR-Kern wird bewusst **nicht** umgeschrieben; die 31 Golden-Tests
  sichern die Erkennung ab. BUILD SUCCESS, 37 Tests grün.

## Modernisierung Stufe 2.2: Logging auf SLF4J umgestellt

Die eigene Logger-Fassade wurde durch den Standard **SLF4J** (+ **Logback**)
ersetzt — an allen Aufrufstellen (Betreiber-Entscheid: volle Umstellung).

- **Ausgangslage:** `LogManager`/`ILogger` war eine Eigenabstraktion über
  64 Dateien / ~348 Aufrufe, die zwei Dinge vermischte: Logging **und**
  App-Lebenszyklus (Vor-Init-Pufferung, **Exit-Code-Kopplung**, eigene Level
  `FATAL`/`LEARN`) mit zwei Backends (tinylog aktiv, log4j als toter Pfad).
- **Anpassung:**
  - **Logging → SLF4J:** `LoggerFactory.getLogger(X.class)` statt der eigenen
    Fassade; `error/info/warn/debug(msg[,t])` sind SLF4J-nativ (~266 Fälle
    unverändert), `fatal→error`, `learn→info`, `*Ext(msg,t)→<level>(msg,t)`
    (SLF4J loggt den Stacktrace ohnehin), `log(Level.X,…)`/`log(level,…)`
    gemappt.
  - **App-Logik extrahiert:** neue Klasse `Diagnostics` trägt die
    Exit-Code-Kopplung (`record(...)`, `exit(code)`), die Level-Abbildung und
    die Helfer (`log`, `out`, `debugOrInfo`). Die tinylog-bedingte
    Vor-Init-**Pufferung entfällt** (Logback ist von der ersten Meldung an
    ausgabebereit — bewusste Vereinfachung).
  - **Abhängigkeiten:** `slf4j-api` + `logback-classic` neu; **tinylog und
    log4j vollständig entfernt**. Logausgabe auf die Konsole
    (`rsc/logback.xml`) — passend für Container/Dienst; früher schrieb tinylog
    in den Schreibpfad aus `base.xml`.
  - `LogManager.java`, `TinyLog.java`, `Logger4j2.java` gelöscht; der
    Paho-Log-Adapter (`connection/mqtt/Logger.java`) auf SLF4J umgestellt.
- **Verifikation (2026-07-21, JDK 17):** `mvn clean package` → BUILD SUCCESS,
  **37 Tests grün**; im Uber-Jar sind SLF4J + Logback enthalten, **tinylog und
  log4j nicht mehr** (0 Klassen); Laufzeit-Smoke ohne Binding-Warnung.

## Modernisierung Stufe 1: Maven-Build, CI, Aufräumen

Fundament für langfristige Wartbarkeit. Fahrplan aller Stufen und Status:
[MODERNISIERUNG.md](MODERNISIERUNG.md).

- **Build auf Maven umgestellt** (`pom.xml`): Abhängigkeiten deklarativ von
  Maven Central statt der von Hand in `lib/` eingecheckten Jars; Uber-Jar via
  `maven-shade-plugin` (inkl. `ServicesResourceTransformer` — führt
  `META-INF/services` korrekt zusammen, u. a. für den SMTP-Provider). Die
  Eigenbibliothek `XMLLibrary` (nicht auf Central) ist reproduzierbar in
  `local-maven-repo/` **vendored**. **Maven-Wrapper** (`mvnw`) ergänzt →
  Build ohne vorinstalliertes Maven. Java-Sprachniveau bleibt in dieser Phase
  bewusst 8 (Anhebung auf 17 ist ein separater, späterer Schritt).
- **Ant-Build entfernt**: `build.xml`, `build-user.xml` und das komplette
  `lib/`-Verzeichnis (14 Jars) gelöscht; das `Dockerfile` baut jetzt über
  `./mvnw` statt Ant.
- **CI (GitHub Actions)** unter `.github/workflows/build.yml`: Build + Test bei
  jedem Push/PR, JDK-Matrix **17/21**, Uber-Jar als Artefakt. Ersetzt die
  frühere manuelle Build-Verifikation auf einem Zielhost.
- **Aufräumen:** 178 tote `$Id$`-SVN-Header aus den Quelldateien entfernt
  (Git liefert die Historie); `.editorconfig` und `.gitattributes` erzwingen
  künftig UTF-8/LF (beenden die CRLF-Vermischung dauerhaft).
- **Verifikation (2026-07-21, JDK 17):** `mvn clean package` →
  `target/SolvisSmartHomeServer.jar` (~4,16 MB), alle Kernklassen + `base.xsd`
  enthalten, Laufzeit-Smoke `--string-to-crypt` ok.

## log4j auf aktuelle Version 2.26.1 gehoben

Ziel: statt der (im vorigen Schritt aus dem Bundle ausgeschlossenen)
verwundbaren `log4j 2.13.2` eine **aktuelle, gepatchte** Version einsetzen.

- **Anpassung:**
  - `lib/log4j-api-2.13.2.jar` und `lib/log4j-core-2.13.2.jar` durch
    **2.26.1** ersetzt (aus Maven Central, SHA-1-geprüft). 2.26.1 ist die
    aktuelle stabile Version der 2.x-Reihe; **3.x** ist noch Beta und ein
    Major-Umbau (nicht drop-in — der Code nutzt
    `org.apache.logging.log4j.core.config.*`), daher bewusst nicht.
  - Da 2.26.1 nicht mehr von Log4Shell betroffen ist, entfällt der
    Sicherheitsgrund für den Bundle-Ausschluss: In `build.xml` (Target
    `Build complete`) wurde `excludes="log4j-*.jar"` wieder **entfernt** —
    log4j ist damit aktuell **und** wieder im Uber-Jar (einsatzbereit für den
    optionalen Log4j2-Backend). Aktiver Logger bleibt tinylog.
  - `build-user.xml`: Versionsverweise (Compile-Classpath + `createLog4jJar`)
    auf 2.26.1 gehoben.
- **Verifikation (2026-07-21, `ransible`/OpenJDK 21):** `ant clean && ant` →
  BUILD SUCCESSFUL; im Jar ist log4j **2.26.1** enthalten
  (`Implementation-Version` in `META-INF/…/log4j-core/pom.properties` bzw.
  Klassenpräsenz), Laufzeit-Smoke `--string-to-crypt` weiterhin ok.

## Log4Shell-Altlast aus dem Uber-Jar entfernt

> **Hinweis:** Dieser Schritt (Ausschluss aus dem Bundle) wurde durch die
> Versionsanhebung oben **überholt** — die Altlast ist jetzt durch die aktuelle
> Version 2.26.1 ersetzt und wieder im Bundle. Der Abschnitt bleibt zur
> Nachvollziehbarkeit erhalten.


Ziel: Die für Log4Shell (CVE-2021-44228) anfällige `log4j-core 2.13.2` nicht
mehr ausliefern.

- **Ausgangslage:** Aktiver Logger ist **tinylog**
  (`LogManager.loggerName="TinyLog"`); der Log4j2-Backend `Logger4j2` ist zur
  Laufzeit **inaktiv** (wird nur bei `loggerName=="Log4j2"` geladen). Der
  Fork-Build bündelte bisher via `zipgroupfileset lib/*.jar` trotzdem **alle**
  log4j-Jars — inkl. der verwundbaren `log4j-core 2.13.2` (~1,7 MB tote,
  scanbare Altlast).
- **Anpassung:**
  - `build.xml`, Target `Build complete`: Bündelung auf
    `zipgroupfileset … excludes="log4j-*.jar"` umgestellt → **keine**
    log4j-Klassen mehr im ausgelieferten Jar.
  - `Logger4j2.java` importiert `org.apache.logging.log4j.core.config.*`,
    braucht `log4j-core` also zum **Kompilieren**. Daher bleiben
    `log4j-api`/`-core` im **Compile-Classpath** (`SolvisMax.classpath`
    referenziert weiterhin `lib/*.jar`) — nur das Laufzeit-Bundle ist bereinigt.
  - `lib/log4j-1.2-api-2.13.2.jar` **ganz entfernt** (im Code nirgends
    referenziert); zugehörige Verweise in `build-user.xml` (Compile-Classpath
    und `createLog4jJar`) entfernt.
- **Konsequenz:** Wer den Log4j2-Backend tatsächlich nutzen will, muss
  `log4j-core` wieder mitbündeln — dann bitte auf eine gepatchte Version
  **≥ 2.17.1** heben.
- **Verifikation (2026-07-21, `ransible`/OpenJDK 21):** `ant clean && ant` →
  BUILD SUCCESSFUL; im Jar `jar tf … | grep 'org/apache/logging/log4j/core'`
  liefert **nichts**; Laufzeit-Smoke `--string-to-crypt` weiterhin ok
  (tinylog).

## Container-Betrieb (Docker)

Neue Artefakte: [`Dockerfile`](Dockerfile) (Zwei-Stufen-Build JDK+Ant →
temurin-JRE), [`docker-compose.yml`](docker-compose.yml),
[`.dockerignore`](.dockerignore), [`docs/DOCKER.md`](docs/DOCKER.md).

- **Begründung/Fallstricke:** reine Java-App, headless-tauglich; das
  Runtime-Image braucht das JDK-Modul **`java.desktop`** (OCR nutzt
  `java.awt.image`/`ImageIO`) — im vollen temurin-JRE enthalten, in
  jlink-/distroless-Minimalimages nicht. Persistentes **`/data`-Volume** für
  `LearnedImages`/`writablePathLinux`, `--init`/tini für sauberes SIGTERM,
  armhf-Alternative dokumentiert.
- **Verifikation (2026-07-21, `ransible`/arm64, Docker 26.1.5):** `docker build`
  → Image `solvissmarthomeserver:fork` (**348 MB**); Container-Smoke
  `docker run … --string-to-crypt=probe` liefert den AES-Wert; `--list-modules`
  bestätigt `java.desktop@21.0.11` im Runtime-Image.

## MQTT über TLS/mTLS nativ implementiert

Ziel: Der Server verbindet sich direkt mit einem mTLS-gehärteten Broker, ohne
den Umweg über einen lokalen Klartext-Broker + Bridge.

- **Ursache:** Das Gerüst für TLS war vorhanden (Config-Element `<Ssl>` mit
  `caFilePath`/`clientCrtFilePath`/`clientKeyFilePath`, Verdrahtung in
  `MqttThread` via `options.setSocketFactory(...)`), aber
  `Ssl.getSocketFactory()` war ein **Stub** (`// TODO not yet implemented`,
  Rückgabe `null`). Zusätzlich fehlte das `<Ssl>`-Element im Schema
  `base.xsd`, sodass eine `<Ssl>`-Konfiguration die (zur Laufzeit in
  `BaseControlFileReader` durchgeführte) XSD-Validierung nicht bestanden hätte.
- **Anpassung:**
  - `Ssl.getSocketFactory()` **implementiert**: baut aus den PEM-Dateien einen
    `SSLContext` (TrustStore aus dem CA-Zertifikat, KeyStore aus
    Client-Zertifikatskette + privatem Schlüssel) und liefert dessen
    `SSLSocketFactory`. Der private Schlüssel wird im **PKCS#8**-Format
    erwartet (`-----BEGIN PRIVATE KEY-----`), Algorithmus (RSA/EC/DSA) wird
    automatisch erkannt; PKCS#1-Schlüssel werden mit klarer Fehlermeldung
    abgelehnt. Neue statische Factory `Ssl.create(...)` für programmatische
    Nutzung/Tests; `isEnabled()`-Getter.
  - `MqttThread`: Aufrufstelle abgesichert — bei aktiviertem TLS
    (`ssl.isEnabled()`) und Fehler in der Zertifikats-/Schlüsselkonfiguration
    wird die Verbindung **abgebrochen** statt still unverschlüsselt
    fortzufahren.
  - `base.xsd`: `<Ssl>`-Kindelement im `Mqtt`-Typ ergänzt und neuen
    `Ssl`-complexType (Attribute `enable`, `caFilePath`, `clientCrtFilePath`,
    `clientKeyFilePath`) definiert. Rückwärtskompatibel (`minOccurs="0"`).
- **Konfigurationsbeispiel** (in `base.xml`, innerhalb `<Mqtt …>`):
  ```xml
  <Mqtt enable="true" brokerUrl="broker.example" port="8883" …>
      <Ssl enable="true"
           caFilePath="/data/ssl/ca.crt"
           clientCrtFilePath="/data/ssl/client.crt"
           clientKeyFilePath="/data/ssl/client.key" />
  </Mqtt>
  ```

### Verifikation mTLS (2026-07-21)

Self-contained getestet auf `ransible` (OpenJDK 21): Wegwerf-PKI (Test-CA,
Server- und Client-Zertifikat mit PKCS#8-Key), ein lokaler
`openssl s_server … -Verify 1` mit **Client-Zertifikats-Pflicht**, und der neue
Code als Client (`Ssl.create(...).getSocketFactory()` → `SSLSocket` →
`startHandshake()`). Ergebnis: **`HANDSHAKE_OK`**, ausgehandelte Cipher-Suite
`TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`. Da der Server ein Client-Zertifikat
erzwang, belegt der erfolgreiche Handshake beide Richtungen (Server- **und**
Client-Authentisierung).

## Build unter aktueller JDK/JRE (JDK 21) lauffähig gemacht

Ziel: `ant` (Default-Target `Build complete`) baut das Uber-Jar
`dist/SolvisSmartHomeServer.jar` fehlerfrei auf einer aktuellen Laufzeit
(getestet mit OpenJDK 21 auf Debian 13 / arm64). Der Upstream-Stand baute in
dieser Umgebung nicht.

### 1. Quell-Encoding auf UTF-8 vereinheitlicht

- **Ursache:** Die Java-Quellen lagen in **Windows-1252** vor (deutsche
  Umlaute/`ß` in Kommentaren und String-Literalen; der Upstream-Build
  `build-user.xml` deklarierte entsprechend `encoding="Cp1252"`). Das
  auto-generierte `build.xml` gab **gar kein** Encoding an. Seit
  [JEP 400](https://openjdk.org/jeps/400) verwendet `javac` ab **JDK 18**
  standardmäßig **UTF-8** als Quell-Encoding. Dadurch brach der Build unter
  JDK 21 mit `error: unmappable character (0xDF/0xE4) for encoding UTF-8`
  (12 Dateien, 33 Fehler).
- **Anpassung:**
  - Die 12 betroffenen Quelldateien von Windows-1252 nach **UTF-8**
    konvertiert (`iconv -f CP1252 -t UTF-8`). Verlustfrei, da keine der
    Dateien Bytes im Bereich 0x80–0x9F enthielt (dort unterscheiden sich
    CP1252 und ISO-8859-1 — hier irrelevant). Betroffen:
    `Main.java`, `connection/Server.java`, `connection/mqtt/TopicType.java`,
    `connection/transfer/Command.java`,
    `imagepatternrecognition/image/MyImage.java`,
    `imagepatternrecognition/ocr/Ocr.java`,
    `model/objects/data/Average.java`,
    `model/objects/measure/Measurement.java`,
    `model/objects/screen/Screen.java`,
    `model/objects/screen/ScreenSaver.java`,
    `smarthome/IoBroker.java`, `xml/ControlFileReader.java`.
  - In **`build.xml`** dem `<javac>`-Task `encoding="UTF-8"` hinzugefügt.
  - In **`build-user.xml`** `encoding="Cp1252"` → `encoding="UTF-8"` geändert
    (sonst würde dieser Build-Pfad die nun UTF-8-kodierten Dateien falsch
    lesen).

### 2. Compile-Classpath im `build.xml` korrigiert

- **Ursache:** Die `SolvisMax.classpath` im auto-generierten `build.xml`
  verwies auf **nicht vorhandene** Bibliotheken (`slf4j-*-1.7.29.jar`,
  `log4j-*-2.12.1.jar`), während `lib/` tatsächlich log4j **2.13.2**,
  tinylog 2.4.1, Paho-MQTT 1.2.5, `mailapi`/`smtp` (javax.mail),
  `javax.activation` und `XMLLibrary` enthält. slf4j wird vom Code gar nicht
  genutzt. (Der Upstream baute real über `build-user.xml`, dessen Classpath
  korrekt ist; das `build.xml` war verwaist.)
- **Anpassung:** Die Einzelverweise durch
  `<fileset dir="lib" includes="*.jar" />` ersetzt — bindet alle
  mitgelieferten Bibliotheken ein und bleibt bei Versionswechseln stabil.
  `lib/alt/` (ältere Duplikate von Paho/tinylog) bleibt bewusst außen vor.

### 3. Uber-Jar-Bundling im Target `Build complete` korrigiert

- **Ursache:** Das Target bündelte per `zipfileset` die nicht existierenden
  `log4j-*-2.12.1.jar` → der `jar`-Task wäre an den fehlenden Dateien
  gescheitert; zudem fehlten Paho, tinylog, javax.mail und XMLLibrary im
  Jar, sodass `java -jar` zur Laufzeit `NoClassDefFoundError` geworfen hätte.
- **Anpassung:** Bündelung auf `<zipgroupfileset dir="lib" includes="*.jar" />`
  umgestellt — alle Laufzeit-Bibliotheken landen im Uber-Jar. Der
  `Main-Class`-Manifest-Eintrag bleibt unverändert.

### Verifikation (2026-07-21, OpenJDK 21 / Debian 13 / arm64)

- `ant clean && ant` → **BUILD SUCCESSFUL**, erzeugt
  `dist/SolvisSmartHomeServer.jar` (≈ 3,99 MB).
- Kernklassen im Jar geprüft: Paho-MQTT, tinylog, log4j, `javax.mail.Session`,
  `de.sgollmer.solvismax.Main` — alle vorhanden.
- Laufzeit-Smoke-Test:
  `java -jar dist/SolvisSmartHomeServer.jar --string-to-crypt=probe` liefert
  einen AES-Wert und terminiert sauber → Main lädt, Abhängigkeiten sind zur
  Laufzeit auflösbar.
- **Verbleibende Warnungen (kein Handlungsbedarf für den Build):** `source`/
  `target` 1.8 sind unter JDK 21 als „obsolete" markiert; einzelne
  deprecated-API-Aufrufe. Ein Anheben auf ein aktuell unterstütztes
  Sprachniveau (z. B. 11/17) ist ein optionaler Folgeschritt und wird
  separat bewertet.

### Umgebung (nicht im Repo, auf dem Zielhost `ransible`)

Zum Bauen aus dem Quellcode wurden dort nachinstalliert:
`default-jdk-headless` (liefert `javac` 21) und `ant` 1.10.15. Laufzeit
(`java` 21) war bereits vorhanden. Es bestehen **keine** nativen
Abhängigkeiten — die Bildschirm-/OCR-Erkennung ist reines Java
(`imagepatternrecognition/…`), kein Tesseract o. Ä.
