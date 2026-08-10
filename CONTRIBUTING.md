# Mitwirken (PROGRAMMIERER-Lens)

> **Sprache:** Deutsch · **Status:** aktiv · **Zielgruppe:** Programmierer ·
> **Bezug:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (Baustein-Sicht),
> [MODERNISIERUNG.md](MODERNISIERUNG.md) (Roadmap), [TESTPLAN.md](TESTPLAN.md)
> (Prüfplan), [FORK.md](FORK.md) (Fork-Etikette)

Diese Seite ist der Einstieg zum **Bauen, Testen und Beitragen**. Die
*verstehende* Sicht (System-Kontext, Datenfluss, Betriebsmodell) steht in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); hier geht es um das *Machen*.

## Build in 2 Befehlen

Voraussetzung: JDK 17+ (der mitgelieferte Maven-Wrapper lädt Maven selbst).
Kein separates Maven nötig.

```bash
./mvnw -B clean package                                             # → target/SolvisSmartHomeServer.jar (Uber-Jar via shade)
java -jar target/SolvisSmartHomeServer.jar --string-to-crypt=probe  # Laufzeit-Smoke-Test
```

Erwartet: `BUILD SUCCESS` und beim zweiten Befehl ein Base64-artiger
verschlüsselter String. `verify` (statt `package`) schließt die Tests ein:

```bash
./mvnw -B -ntp clean verify
```

## Tests

- **Framework:** JUnit 5 (`junit-jupiter` 5.11.3), ausgeführt über
  `maven-surefire-plugin`. Testquellen unter `test/` (gespiegeltes Paketlayout
  `de.sgollmer.solvismax`).
- **OCR-Golden-Tests:** 31 Referenzbilder aus `testFiles/images/` → erwartetes
  Zeichen (`test/de/sgollmer/solvismax/imagepatternrecognition/ocr/`). Sie pinnen
  den OCR-Kern und sind der **grüne Nachweis** für jede Screen-/Pattern-Änderung.
- **Krypto-Tests:** `CryptAes`-Round-Trip und Schlüssel-Stabilität, `Ssl`-PKCS#8
  (Laden/Ablehnung) unter `test/de/sgollmer/solvismax/crypt/`.
- **Config-Parsing:** Der JAXB-Umstieg ist **dual-parse-abgesichert** (alter
  `XMLLibrary`-Pfad vs. JAXB-DTOs im Vergleich) — siehe
  [docs/jaxb-exploration.md](docs/jaxb-exploration.md).

Nur eine Testklasse gezielt laufen lassen:

```bash
./mvnw -B -ntp test -Dtest=CryptAesTest
```

> ⚠️ **Nie ohne grünen Nachweis committen**, wenn OCR-/Screen-Parsing berührt ist
> (control.xml, Dual-Parse). Und **nie gegen die Live-Anlage/SolvisRemote testen
> ohne expliziten Betreiber-Auftrag** — die Steuerung klickt real auf der
> Anlagen-Oberfläche ([TESTPLAN.md](TESTPLAN.md), Phasen 4–8).

## Code-Layout

Wurzelpaket `de.sgollmer.solvismax` (unter `src/`). Kurzfassung — die
vollständige Baustein-Sicht steht in
[docs/ARCHITECTURE.md §3](docs/ARCHITECTURE.md):

| Baustein | Verantwortung |
|---|---|
| `imagepatternrecognition/` | **OCR-Kern** (image/ocr/pattern), isoliert, nur JDK-Bildklassen, durch Golden-Tests gepinnt. **Nicht neu bauen.** |
| `model/` | Anlagen-/Domänenmodell: Screens, Kanäle, Messwerte, Strategien. |
| `connection/` | MQTT-Client (Paho v3) + proprietärer TCP-/JSON-Server. |
| `xml/` + `xml/jaxb/` | Konfig-Parsing: Übergang `XMLLibrary`-Creators → JAXB-DTOs. |
| `crypt/` | `CryptAes` für `passwordCrypt` — Obfuskation, kein Schutz. |
| `mail/`, `smarthome/`, `windows/` | Fehler-Mail, Integrations-Helfer, JDK-only Windows-Hilfe. |

Weitere Verzeichnisse: `rsc/` (eingebettete Ressourcen inkl. `base.xml`-Vorlage
und XSD), `local-maven-repo/` (vendored `XMLLibrary` bis zur JAXB-Ablösung),
`SmartHome/` (Installations-/Service-Dateien).

## Lokale Entwicklung — nützliche Programm-Argumente

Das Jar akzeptiert Kommandos, die auch die Runbooks nutzen (real, aus `Main`):

```bash
java -jar target/SolvisSmartHomeServer.jar --string-to-crypt='<PASSWORT>'  # passwordCrypt erzeugen
java -jar target/SolvisSmartHomeServer.jar --server-learn                  # OCR-Lernphase (braucht Anlage)
java -jar target/SolvisSmartHomeServer.jar --server-terminate             # laufende Instanz sauber beenden
```

## Beitrags-Regeln (Fork-Etikette)

- **Upstream-Diff klein halten.** Modernisierung nur entlang
  [MODERNISIERUNG.md](MODERNISIERUNG.md); keine Gratis-Refactorings quer durch
  den Java-Baum. Ziel: Upstream-Stände übernehmbar halten, Änderungen als PR
  zurückgeben können.
- **OCR-Kern nicht anfassen.** Isolierung + Golden-Tests statt Rewrite; die
  Urheberschaft der OCR-Kernidee liegt beim Upstream-Autor
  (Stefan Gollmer, GollmerSt) — Attribution erhalten ([FORK.md](FORK.md)).
- **Nur `solvis/#`.** Kein anderer MQTT-Topic-Namensraum (Vertrag ADR-0017).
- **Secrets nie ins Repo.** Zertifikate/Keys der mTLS-Bridge und
  Anlagen-Zugangsdaten bleiben Operator-State.
- **Doku pflegen.** Fork-Abweichungen in [CHANGELOG-fork.md](CHANGELOG-fork.md);
  Branch `feature/modernisierung` (der `master` folgt nur dem Upstream).
- **Zeilenenden:** LF (`.gitattributes` erzwingt `eol=lf`; nur `mvnw.cmd` CRLF).

## CI

`./.github/workflows/build.yml` baut und testet bei jedem Push/PR auf der
JDK-Matrix **17 und 21** (`./mvnw -B -ntp clean verify`) und sichert das Uber-Jar
als Artefakt (nur JDK 21). Ein PR ist erst mergefähig, wenn diese Pipeline grün
ist.
