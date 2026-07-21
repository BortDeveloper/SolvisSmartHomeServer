# Changelog dieses Forks

Dokumentiert alle Abweichungen vom Upstream
([GollmerSt/SolvisSmartHomeServer](https://github.com/GollmerSt/SolvisSmartHomeServer)).
Hintergrund und Ziel des Forks: siehe [FORK.md](FORK.md).

Format: neueste Änderung oben. Jede Änderung nennt Motivation, Ursache und
konkrete Anpassung, damit sie nachvollziehbar und ggf. als Upstream-PR
aufbereitbar ist.

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
