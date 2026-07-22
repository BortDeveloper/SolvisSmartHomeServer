# Modernisierungs-Roadmap (Fork)

Dieses Dokument macht den Umbau des Forks hin zu langfristiger Wartbarkeit und
Verständlichkeit **transparent und nachvollziehbar**: Was ist erledigt, was ist
geplant, in welcher Reihenfolge — und warum. Jede abgeschlossene Änderung ist
zusätzlich im [CHANGELOG-fork.md](CHANGELOG-fork.md) mit Ursache/Anpassung
dokumentiert.

Leitprinzip der Reihenfolge: **erst das Fundament (reproduzierbarer Build, CI)
und ein Test-Netz, dann die großen Struktur-Refactors.** Riskante Umbauten
(Java-Anhebung, Ablösung des XML-Bindings) erfolgen bewusst zuletzt — abgesichert
durch die vorher aufgebauten Tests.

## Stufe 1 — Fundament (Build, CI, Aufräumen)

| # | Maßnahme | Status |
|---|---|---|
| 1.1 | **Build auf Maven** umgestellt (`pom.xml`, `maven-shade-plugin` für das Uber-Jar); Abhängigkeiten deklarativ statt als eingecheckte Jars. `XMLLibrary` (nicht auf Central) reproduzierbar in `local-maven-repo/` vendored. Maven-**Wrapper** (`mvnw`) → self-bootstrapping. | ✅ erledigt |
| 1.2 | **Ant-Build entfernt** (`build.xml`, `build-user.xml`) und `lib/`-Jars gelöscht; Dockerfile auf `./mvnw` umgestellt. | ✅ erledigt |
| 1.3 | **CI (GitHub Actions)**: Build + Test bei jedem Push/PR, JDK-Matrix 17/21, Jar als Artefakt. | ✅ erledigt |
| 1.4 | **Aufräumen**: 178 tote `$Id$`-SVN-Header entfernt; `.editorconfig` + `.gitattributes` (UTF-8/LF erzwingen). | ✅ erledigt |

## Stufe 2 — Test-Netz und Logging

| # | Maßnahme | Status |
|---|---|---|
| 2.1 | **JUnit 5 + erste Tests**: `CryptAes`-Round-Trip + Schlüssel-Stabilität, `Ssl`-PKCS#8 (Laden/Ablehnung), **OCR-Golden-Tests** (31 Referenzbilder aus `testFiles/images/` → erwartetes Zeichen). 37 Tests, grün (lokal + CI). | ✅ erledigt |
| 2.2 | **Logging auf SLF4J umgestellt** (volle Umstellung an allen ~348 Aufrufstellen / 64 Dateien). Eigenabstraktion `LogManager`/`ILogger`/`TinyLog`/`Logger4j2` gelöscht; Logging über **SLF4J** + **Logback**. Die App-Logik (Exit-Code-Kopplung, Level-Abbildung, Helfer) in die neue Klasse `Diagnostics` extrahiert; tinylog-bedingte Vor-Init-Pufferung entfällt (Logback sofort ausgabebereit). **tinylog + log4j vollständig entfernt.** BUILD SUCCESS, 37 Tests grün, Uber-Jar ohne tinylog/log4j. | ✅ erledigt |

> **Explorations-Erkenntnisse zur JAXB-Umstellung** (mit Sicherheitsgraden
> ✅ gesichert / ⚠️ unsicher): [docs/jaxb-exploration.md](docs/jaxb-exploration.md).

## Stufe 3 — Modernisierung (nach dem Test-Netz)

| # | Maßnahme | Status |
|---|---|---|
| 3.1 | **OCR-Kern isoliert** (`imagepatternrecognition/`): Schichtungs-Leck entfernt (das Bildmodul hing über `MyImage.getByteArrayDataSource()` an `javax.mail` — jetzt liefert es `byte[]`, die Mail-Schicht packt ein). Modulgrenze in drei `package-info.java` dokumentiert (Zweck, öffentliche API `new Ocr(new MyImage(img)).toChar()`, minimale Abhängigkeiten, GollmerSt-Zuschreibung, Golden-Test-Abdeckung). Bewusst **nicht** umgeschrieben. BUILD + 37 Tests grün. | ✅ erledigt |
| 3.2 | **Java-Baseline auf 17 gehoben** (`maven.compiler.release=17`; Bytecode major 61, läuft auf 17/21). „obsolete"-Warnungen weg, moderne Sprachmittel (Records/switch-Expressions/var) nun verfügbar. Die konkrete Boilerplate-Reduktion der Konfig-Klassen erfolgt im Zuge von 3.3 (JAXB), um Doppelarbeit zu vermeiden. BUILD + 37 Tests grün. | ✅ erledigt |
| 3.3 | **XML-Binding ablösen**: die proprietäre `XMLLibrary` + die 67 `CreatorByXML`-Klassen durch **JAXB** ersetzen, inkrementell + abgesichert. **Fortschritt:** Sicherheitsnetz vertieft (`BaseConfigParsingTest`); **Dual-Parse-Differenzwerkzeug** (`ParseDiff`) gebaut; **base.xml-Kernbaum** als JAXB-DTOs gebunden (`BaseDataDto`, `JaxbBaseReader`) und per **grünem Dual-Parse-Test** gegen den alten Parser verifiziert (ExecutionData, Mqtt/Ssl, Unit-Kernattribute, Features); erster **Mapper** (`Features → Map`) realisiert den Grundsatz „kanonisch binden + explizit ableiten", gegen die Domäne getestet. **Entscheidung Weg B (Betreiber): DTOs werden das Config-Modell** (kein invasiver Aggregat-Mapper zur sealed-Domäne). Abgeleitete Sichten als Methoden: `featuresToMap`, `measurementsIntervalMs` (×1000) — gegen die Domäne grün. `Mapper.toMqtt` bleibt als Übergangsbrücke. **Mqtt-Konsumenten komplett migriert** (Pilotmuster ausgerollt): Topic-Aufbau (`TopicType.getTopicData`, `MqttData.getTopic`) an `MqttTopicConfig`, Verbindungsaufbau (`MqttThread`) an der neuen Sicht `MqttConnectionConfig` — der gesamte Mqtt-Config-Lesepfad läuft über Sichten, DTO-gestützt dual-parse-belegt. **BaseData-Konsumenten migriert:** neue Sicht `ExecutionConfig` (flache Ausführungswerte inkl. abgeleiteter OS-Pfad-Weiche) für `Main`/`Instances`; `IoBroker` an `MqttTopicConfig`, `ExceptionMail.sendTestMail` an `Units` verschmälert. **Unit-Skalar-Konsumenten migriert:** Sicht `UnitConfig` (19 flache Werte inkl. Ableitungen) über `Solvis.getUnitConfig()`; alle 10 Skalar-Leser (WatchDog, Distributor, HumanAccess, SolvisWorkers, Measurement, StrategyReheat, SolvisData, ScreenSaver, ErrorState, MeasurementUpdateThread) umgestellt. **Defaults im Standard gelöst:** Creator-Defaults als DTO-Feld-Initialisierer gespiegelt (JAXB setzt nur vorhandene Attribute), Fallback fast→normal als Sicht; per Dual-Parse an Minimal-Fixture bewiesen. **Aggregat-Zweige Features + ChannelOptions als Wert-Typen** aus dem DTO konstruierbar (Factories `Features.of`/`AllChannelOptions.of`, Mapper `toFeatures`/`toChannelOptions`; Semantik bleibt an einer Stelle, Konsumenten unberührt; ChannelDto nullable gebunden). **Alle base.xml-Zweige komplett:** restliche Aggregat-Zweige (Urls, IgnoredChannels, Assignments, Durations, Configuration/Extensions) als Wert-Typen aus dem DTO konstruierbar, Feature-Alt-Form + deprecated ms-Intervall-Attribut gebunden, IoBroker-Element-Default und gemeinsame passwordCrypt-Sicht (`toCryptAes`) im Mapper, XSD-Doku-Fehler korrigiert — dual-parse-belegt über die erweiterte Fixture. **Reader-Umstieg base.xml vollzogen:** `read()` läuft über javax.xml.validation + JAXB + `Mapper.toBaseData`; Beweis per Voll-Graph-Vergleich (`ParseDiff`, jeder Getter rekursiv, zeichengleich über Template + 2 Fixtures); alter Creator-Pfad nur noch als Test-Referenz (`readWithCreators`, deprecated). **base.xml-Creators entfernt** (~700 Zeilen Parser-Code weg; Verhalten dauerhaft per Golden-Snapshots des Original-Parsers gepinnt, `testFiles/xml/golden/`); in XMLLibrary-Nutzung verbleiben nur noch die vom control.xml-Pfad geteilten Creators. **measurements.xml komplett auf JAXB** (Lesen inkl. Legacy-Namespace-Toleranz via SAX-Filter, Schreiben per Marshalling — etabliert das Muster für geschriebene Dateien; Backup-Creators entfernt). **Offen:** control.xml + graficData.xml ebenso umstellen → XMLLibrary raus. Details/Sicherheitsgrade: [docs/jaxb-exploration.md](docs/jaxb-exploration.md). | 🔄 Weg B, base.xml + measurements.xml auf JAXB |
| 3.4 | **`javax.mail`/`javax.activation` → `jakarta.mail`** erledigt: EOL-`com.sun.mail` 1.6.2 durch **angus-mail 2.0.3** ersetzt (bringt `jakarta.mail-api` + `jakarta.activation` mit), alle Importe in 4 Dateien umgestellt. **Windows-Ballast isoliert:** `windows/`-Paket per `package-info.java` als optionale, JDK-only Windows-Hilfe (`--create-task-xml`) dokumentiert (nicht gelöscht — bricht sonst die CLI-Option; InnoSetup-`.iss` bleibt außerhalb des Maven-Builds). BUILD + 41 Tests grün, Jar mit jakarta.mail statt javax.mail. | ✅ erledigt |

## Bewusst nicht angetastet

Die **OCR-/Screen-Scraping-Kernlogik** wird nicht „vereinfacht" oder
umgeschrieben — sie ist das Alleinstellungsmerkmal und funktional korrekt. Der
Gewinn liegt in **Isolierung + Golden-Tests** (3.1), nicht im Neubau.

## Zur `XMLLibrary`-Ablösung (häufige Frage)

Ja, die proprietäre `XMLLibrary` lässt sich durch einen Standard ersetzen
(Maßnahme 3.3). Sie ist aktuell nur **vendored**, damit der Maven-Build sofort
steht. Die Ablösung ist der letzte Schritt, weil sie 67 Parser-Klassen betrifft
und **verhaltensidentisch** sein muss — was erst mit dem Test-Netz aus Stufe 2
sicher überprüfbar ist.
