# Changelog dieses Forks

Dokumentiert alle Abweichungen vom Upstream
([GollmerSt/SolvisSmartHomeServer](https://github.com/GollmerSt/SolvisSmartHomeServer)).
Hintergrund und Ziel des Forks: siehe [FORK.md](FORK.md).

Format: neueste Änderung oben. Jede Änderung nennt Motivation, Ursache und
konkrete Anpassung, damit sie nachvollziehbar und ggf. als Upstream-PR
aufbereitbar ist.

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

### Umgebung (nicht im Repo, auf dem Zielhost `ransible`)

Zum Bauen aus dem Quellcode wurden dort nachinstalliert:
`default-jdk-headless` (liefert `javac` 21) und `ant` 1.10.15. Laufzeit
(`java` 21) war bereits vorhanden. Es bestehen **keine** nativen
Abhängigkeiten — die Bildschirm-/OCR-Erkennung ist reines Java
(`imagepatternrecognition/…`), kein Tesseract o. Ä.
