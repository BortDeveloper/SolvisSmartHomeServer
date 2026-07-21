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
| 2.1 | **JUnit 5 + erste Tests** auf die gut testbare Logik: `base.xml`-Parsing, `CryptAes`-Round-Trip, Topic-Aufbau (`TopicType`), Mess-/Mittelwertlogik, `Ssl`-PKCS#8. **Golden-Tests** für die OCR-Ziffernerkennung gegen `testFiles/images/`. | ⏳ geplant |
| 2.2 | **Logging konsolidieren** auf **SLF4J** + eine Implementierung; eigene `LogManager`/`ILogger`-Abstraktion und den ungenutzten Log4j2-Pfad ablösen → log4j-Abhängigkeit entfällt. | ⏳ geplant |

## Stufe 3 — Modernisierung (nach dem Test-Netz)

| # | Maßnahme | Status |
|---|---|---|
| 3.1 | **OCR-Kern isolieren** (`imagepatternrecognition/`) als klar abgegrenztes Modul mit dokumentierter Schnittstelle und Golden-Tests, damit es langfristig wartbar behalten und betrieben werden kann. Bewusst **nicht** umschreiben — die Idee stammt von GollmerSt (s. [FORK.md](FORK.md)) und ist funktional korrekt. | ⏳ geplant |
| 3.2 | **Java-Baseline auf 17** heben (`maven.compiler.release`); **Records** für die vielen kleinen Konfig-/Datenhalter, **Switch-Expressions** statt manueller Attribut-Switches → deutlich weniger Boilerplate. | ⏳ geplant |
| 3.3 | **XML-Binding ablösen**: die proprietäre `XMLLibrary` + die 67 `CreatorByXML`-Klassen durch einen **Standard (JAXB / Jakarta XML Binding)** ersetzen. JAXB passt, weil die Konfiguration bereits XSD-getrieben und -validiert ist (`base.xsd` u. a.). Größter Einzelgewinn für die Verständlichkeit (Standard statt Eigenbau), aber auch der größte/riskanteste Umbau — daher **zuletzt und abgesichert durch die Tests aus 2.1**. Die XSD-Validierung wird dabei neu verortet. | ⏳ geplant |
| 3.4 | **`javax.mail`/`javax.activation` → `jakarta.mail`** (EOL-Ablösung); **Windows-Ballast isolieren** (`windows/Task.java`, InnoSetup-`.iss`) für den container-/Linux-first-Kurs. | ⏳ geplant |

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
