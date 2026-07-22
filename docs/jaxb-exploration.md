# JAXB-Umstellung — Explorations-Erkenntnisse (Config-Parsing)

Dieses Dokument hält fest, was die Erkundung des Config-Parsings ergeben hat —
als Grundlage für die schrittweise Ablösung der `XMLLibrary` durch JAXB
(MODERNISIERUNG.md 3.3). **Jede Erkenntnis ist mit ihrem Sicherheitsgrad
gekennzeichnet:**

- ✅ **gesichert** — durch Code-Beleg und/oder grünen Test bestätigt.
- ⚠️ **unsicher** — noch zu bestätigen bzw. beim Umbau bewusst zu behandeln.

---

## 1. Config-Dateien und Reader

✅ **gesichert.** Es gibt vier über `XmlStreamReader` eingelesene Bäume:

| Datei | Wurzeltyp | Reader |
|---|---|---|
| `base.xml` | `BaseData` | `BaseControlFileReader` |
| `control.xml` | `SolvisDescription` | `ControlFileReader` |
| `graficData.xml` | `AllSolvisGrafics` | `GraficFileHandler` |
| `measurements`/Backup | `AllSystemBackups` | `BackupHandler` |

✅ `XMLLibrary` fällt erst weg, wenn **alle vier** Bäume umgestellt sind (der
Reader nutzt je Datei entweder die Creators oder JAXB — kein Mischzustand pro
Datei).

## 2. Grundmuster der Bindung (bestätigt)

✅ **gesichert** (durch grüne Dual-Parse-Tests am `base.xml`-Kern): Das XML
bildet sich **kanonisch** auf JAXB-Beans ab — wiederholte Elemente → `List`,
Attribute → Felder. Die Domäne hält teils **abgeleitete** Sichten (z. B.
`Features` als `Map`), die im alten Parser **während des Parsens** entstanden.
Der nachhaltige Weg trennt beides: JAXB bindet die rohe Gestalt, ein **Mapper**
leitet die Domänen-Sichten explizit ab.

### JAXB-Fallstricke (aus der Praxis)

- ✅ **gesichert:** JAXBs Default-Namensableitung aus Feldnamen ist
  **unzuverlässig** — die `_ms`-Attribute (`echoInhibitTime_ms`,
  `watchDogTime_ms`) banden auf `0`. **Konsequenz:** `@XmlAttribute(name=…)`
  immer **explizit** angeben. (Gilt sinngemäß auch für `@XmlElement`.)
- ✅ **gesichert:** Nicht modellierte Elemente/Attribute ignoriert JAXB — der
  DTO-Baum kann schrittweise wachsen.

## 3. `base.xml`-Baum: Bindung und Verifikation

✅ **gesichert** (grün): DTO-gebunden **und** gegen den alten Parser
dual-parse-verifiziert:
`ExecutionData`, `Mqtt`/`Ssl`, viele `Unit`-Attribute (id/type/mainHeating/
account/url + Intervall-/Verzögerungs-`_ms`-Werte + Flags), `Features`.

✅ **gesichert** per Charakterisierung (die Domäne legt keine Getter offen):
`ExceptionMail` inkl. `Recipients`, `Iobroker`, `Unit/ChannelOptions` (7
`<Channel>` mit `fix`/`offset`/`powerOnDelay_s`).

✅ **gesichert:** Der base.xml-DTO-Baum ist damit **vollständig für den im
Template real befüllten Inhalt**. ✅ **Auch die im Template leeren Zweige sind
inzwischen modelliert und verifiziert** (`Unit/Urls`, `Unit/Extensions`,
`Unit/IgnoredChannels`, `Unit/ChannelAssignments`, `Unit/Durations`,
`Unit/Configuration`): die Fixture `testFiles/xml/base-extended.xml` befüllt
sie real und der Dual-Parse-Test vergleicht gegen die Domänen-Getter
(`getUrls`, `isChannelIgnored`-Verhalten, `getDuration`,
`getChannelAssignment`, Configuration-Kommentar).✅ **Parser-Inkonsistenz korrigiert:** Das Attribut heißt in **base.xsd UND
base.xml** `forceUpdateAfterFastChangingIntervals`, der `Unit.Creator`-`case`
hatte aber einen **Tippfehler** (`forceUpdateInFastChangingAfterIntervals`) —
der konfigurierte Wert wurde nie gelesen (Default griff). **Korrigiert**
(Creator an XSD/Template angeglichen), im DTO modelliert und jetzt **1:1
dual-parse-verglichen** (Template-Wert `3`, beide Parser).

### Prinzip: Dual-Parse **ist** der Omissions-/Inkonsistenz-Detektor

✅ **gesichert:** Ein Attribut, das der alte Parser auslässt oder falsch liest,
fällt beim Dual-Parse-Vergleich sofort auf (Domäne = Default ≠ DTO =
Template-Wert). So wurde `forceUpdate…` gefunden. **Vorgehen bei Fund:**
(a) den alten Parser an XSD/Template **korrigieren** und ins 1:1 aufnehmen; oder
(b) falls eine Korrektur nicht eindeutig/sicher ist, das Element **ausnahmsweise
aus dem 1:1-Vergleich nehmen** und hier begründet vermerken.

⚠️ **aus dem 1:1 genommen** (mangels Domänen-Getter, kein Auslassen): `Unit`-
`resetErrorDelayTime_ms` wird vom Parser gelesen, aber nicht über einen Getter
offengelegt → nur per Charakterisierung am DTO absicherbar.

### Abgeleitete Werte (Derivate, nicht 1:1)

- ✅ **gesichert:** `measurementsInterval_s` → `defaultMeasurementsInterval_ms`
  ist eine **×1000-Umrechnung** (Sekunden→Millisekunden; Beleg: `Unit.Creator`
  `Integer.parseInt(value) * 1000`). Gehört in den Mapper, nicht in die Bindung.
- ✅ **gesichert:** `passwordCrypt` wird als roher String gebunden und im Mapper
  über `CryptAes` entschlüsselt; schlägt das fehl, wird der jeweilige Kanal
  deaktiviert (bei `Mqtt` belegt: Creator fing `CryptException`, setzte
  `enable=false` — im `Mapper.toMqtt` reproduziert und getestet).
- ⚠️ **unsicher / zu behandeln:** `Recipient.type` (`TO|CC|BCC`) → Enum-Mapping;
  Vorgehen klar (Mapper), aber noch nicht umgesetzt/getestet.

## 4. Konstruktion der Domänenobjekte (der eigentliche Hebel)

Der Mapper muss die **immutablen** Domänenobjekte bauen (private Konstruktoren).

- ✅ **gesichert:** `BaseData`-Konstruktor nimmt die `ExecutionData`-Werte
  **flach** (timeZone/port/paths/echoInhibit) plus `Units`, `ExceptionMail`,
  `Mqtt`, `IoBroker`. **Es gibt also kein separates `ExecutionData`-
  Domänenobjekt** — diese Abbildung ist trivial.
- ✅ **gesichert:** `Mqtt` ist zur Config-Zeit **sauber baubar** (der im
  Konstruktor erzeugte `MqttQueue` ist nebenwirkungsarm — nur eine
  `LinkedList`). Über die neue öffentliche Factory `Mqtt.of(...)` + `Mapper.toMqtt`
  **umgesetzt und dual-parse-verifiziert**.
- ✅ **gesichert:** `Ssl` ist über die vorhandene Factory `Ssl.create(...)`
  baubar (`Mapper.toSsl`).

### `Unit` — die Hauptkomplexität

- ✅ **gesichert:** `Unit` ist **kein Wertehalter, sondern ein tiefer Aggregat**.
  Sein Konstruktor verlangt u. a.:
  `Configuration`, `Collection<String> urls`, `CryptAes password`,
  `Features features`, `Collection<Pattern> ignoredChannels`,
  `Map<String, ChannelAssignment> assignments`, `AllDurations durations`,
  `AllChannelOptions channelOptions` — **jeweils eigene immutable Domänentypen
  mit eigenen privaten Konstruktoren/Creators.**
- ✅ **gesichert:** `Unit` nutzt die `Configuration` aus dem **eigenen Paket**
  (`model.objects.unit.Configuration`), nicht die gleichnamige aus
  `model.objects.configuration` (kein Import → Paketgenosse).
- ✅ **gesichert:** Das Template **exerziert `ChannelOptions`** (10 aktive
  `<Channel>`-Einträge mit `fix`/`offset`/`powerOnDelay`). Ein vollständiger
  `Unit`-Mapper muss also `AllChannelOptions` real bauen — dieser Zweig ist
  **nicht** leer.
- ⚠️ **unsicher / zu klären:**
  - Ob `Configuration` im Template **null** sein darf (kein `<Configuration>`-
    Kind gesehen) oder ob der Creator ein Default baut — muss vor dem
    `Unit`-Mapper geprüft werden.
  - Konstruktion/Defaults von `AllChannelOptions`, `AllDurations`,
    `ChannelAssignment`, `Pattern` (IgnoredChannels) aus den DTOs — je Typ noch
    eigene Erkundung nötig.
  - Welche `Unit`-Getter zur **Verifikation** taugen: Die Domäne legt nur einen
    Teil offen; die tiefen Aggregate (ChannelOptions etc.) sind ggf. nur
    indirekt prüfbar. Für diese Zweige ist **Charakterisierung am DTO** (bereits
    grün) die primäre Absicherung.

## 5. Verifikations-Reichweite (wichtig für die Erwartung)

- ✅ **gesichert:** Die **Korrektheit des JAXB-Parsens** von `base.xml` ist über
  die DTO-Dual-Parse-/Charakterisierungstests bereits **breit abgesichert**.
- ⚠️ **unsicher / begrenzt:** Die Verifikation des **DTO→Domäne-Mappers** ist
  durch die **spärlichen Domänen-Getter** begrenzt (z. B. `Mqtt`: nur
  `enable`/`topicPrefix`/`smartHomeId`). Der Mapper trägt v. a. die Konstruktion
  für den späteren Reader-Umstieg bei; seine vollständige Absicherung braucht
  entweder mehr Getter oder den Dual-Parse **nach** dem Reader-Umstieg
  (dann Domäne-vs-Domäne über `ParseDiff`).

## 6. Konsequenz für die Reihenfolge

✅ Trivial/erledigt: `ExecutionData` (flach), `Mqtt`/`Ssl` (Mapper grün),
`Features → Map` (Mapper grün).
⚠️ Der **`Unit`-Aggregat-Mapper** ist der große, tiefe Block: Er zieht die
Konstruktion einer ganzen Sub-Hierarchie (`Configuration`, `AllChannelOptions`,
`AllDurations`, `ChannelAssignment`, `Pattern`) nach sich. Erst danach ist
`BaseData` vollständig baubar und `BaseControlFileReader` auf JAXB umstellbar.

## 7. Entscheidung: Weg B — DTOs werden das Config-Modell

✅ **entschieden (Betreiber, 2026-07-21): Weg B.** Statt die versiegelte Domäne
über einen invasiven Aggregat-Mapper zu rekonstruieren, werden die **JAXB-DTOs
das Config-Modell**:

- **Kanonische Bindung** (DTOs) + **explizite abgeleitete Sicht-Methoden** ersetzen
  die Domänen-Config-Sichten. Belegt und grün: `Mapper.featuresToMap`
  (`<Feature>`-Liste → `Map`), `Mapper.measurementsIntervalMs` (Sekunden → ms,
  ×1000) — beide gegen die Domäne verifiziert.
- **Der `Unit`-Aggregat-Mapper zur sealed-Domäne entfällt damit** — genau die
  tiefe, invasive Rekonstruktion (Configuration/AllChannelOptions/…) muss **nicht**
  gebaut werden.
- **Übergang ohne Big-Bang:** Der vorhandene DTO→Domäne-Mapper (`Mapper.toMqtt`
  u. Ä.) dient als **Brücke** — noch nicht migrierte Konsumenten erhalten weiter
  Domänenobjekte aus den DTOs, während neue/migrierte Konsumenten direkt die DTOs
  + Sicht-Methoden nutzen. Die versiegelten Domänen-Config-Klassen werden
  retiriert, sobald ihr letzter Konsument migriert ist.

**Fahrplan Weg B:** (1) DTO-Baum je Config-Datei vervollständigen + Sicht-Methoden
für alle Derivate (passwordCrypt-Entschlüsselung, RecipientType-Enum, …).
(2) Konsumenten inkrementell auf DTOs+Sichten umstellen (⚠️ app-weit, daher
schrittweise, jeweils gegen Tests). (3) Domänen-Config-Klassen + Creators je
Datei entfernen. (4) Zuletzt `XMLLibrary` raus.

⚠️ **unsicher / zu behandeln:** Die Konsumenten-Migration (Schritt 2) ist der
app-weite, größere Teil — sie berührt die Nutzung von `BaseData`/`Unit`/`Mqtt`
im ganzen Modell und erfolgt bewusst inkrementell, nicht auf einmal.

### Muster für Schritt 2, im Piloten validiert (✅ gesichert)

`BaseData`/`Unit`/`Mqtt` vermischen **Config und Laufzeit** (z. B. hält `Mqtt`
Client/Queue). Ein Konsument darf daher nicht durch ein DTO „ersetzt" werden;
stattdessen:

1. **Schmale Config-Sicht** als Interface definieren (nur die Werte, die der
   Konsument liest). Pilot: `MqttTopicConfig` (`getTopicPrefix`, `getSmartHomeId`)
   für den Topic-Aufbau.
2. **Domäne erfüllt die Sicht** (`Mqtt implements MqttTopicConfig`) — der laufende
   Fluss übergibt weiter das Domänenobjekt, nichts an der Laufzeit ändert sich.
3. **Konsument auf die Sicht verschmälern** (`TopicType.getTopicParts` nimmt jetzt
   `MqttTopicConfig` statt `Mqtt`) — damit hängt er nicht mehr an der konkreten,
   laufzeitgekoppelten Klasse.
4. **DTO-gestützte Sicht** ergänzen (`Mapper.topicConfig(MqttDto)`); ein
   Dual-Parse-Test belegt: der Konsument erhält aus Domäne UND DTO **identische**
   Config.

✅ **gesichert:** Dieser Pilot ist grün und **risikoarm** — nur eine
Config-Lese-Signatur verschmälert, das Laufzeitverhalten bleibt unberührt. Die
restlichen Konsumenten folgen demselben Muster; sobald ein Konsument nur noch an
der Sicht hängt und der Fluss die DTO-Sicht liefert, entfällt seine Abhängigkeit
von der Domänen-Config-Klasse.

### Ausrollung auf die restlichen Mqtt-Konsumenten (✅ erledigt, grün)

Bestandsaufnahme aller `Mqtt`-lesenden Stellen und Anwendung des Musters:

- ✅ **`TopicType.getTopicData`** und **`MqttData.getTopic`**: reine
  Topic-Aufbau-Konsumenten (reichen nur an `getTopicParts` durch) — Signaturen
  auf die vorhandene Sicht `MqttTopicConfig` verschmälert. Verhaltens-Nachweis:
  Dual-Parse-Test baut über `getTopicData` aus Domänen- UND DTO-Sicht
  **identische Topics** (inkl. `CLIENT_ONLINE` mit `smartHomeId`-Teil).
- ✅ **`MqttThread`** (Broker-Verbindungsaufbau): las Verbindungs-Config direkt
  aus Feldern (`userName`, `passwordCrypt`, `ssl`, `topicPrefix`, `publishQoS`,
  `subscribeQoS`). Neue schmale Sicht **`MqttConnectionConfig`** (Mqtt erfüllt
  sie; DTO-Sicht via `Mapper.connectionConfig`); alle Config-Lesezugriffe laufen
  jetzt über die Sicht, die Laufzeit-Zugriffe (Client, Callback, Last-Will)
  bleiben bewusst an `Mqtt`. Dual-Parse-Test: beide Quellen liefern identische
  Config (Passwort-Vergleich über `cP()`; beim Template-Platzhalter beidseitig
  ungesetzt).
- ✅ **Keine Config-Konsumenten** (nur Laufzeit: publish/subscribe, Client,
  Instances): `MqttQueue`, `Callback`, `AllSolvisData.sendMetaToMqtt`,
  `Instances.getMqtt`/`Solvis.getMqtt`-Durchreichungen — hier gibt es kein
  Config-Lesen zu verschmälern; sie wandern später mit der Laufzeitklasse.

Damit liest **kein Konsument mehr Mqtt-Config an der konkreten Klasse vorbei**:
Der gesamte Config-Lesepfad (Topic-Aufbau + Verbindungsaufbau) läuft über die
beiden Sichten und ist DTO-gestützt belegt. `Mqtt` selbst bleibt als
Laufzeitklasse (Client/Queue/Callback) bestehen und wird künftig aus den DTOs
konstruiert (`Mapper.toMqtt`).

### Ausrollung auf die BaseData-Konsumenten (✅ erledigt, grün)

Bestandsaufnahme aller `BaseData`-lesenden Stellen und Anwendung des Musters:

- ✅ **Neue schmale Sicht `ExecutionConfig`** (timeZone, port, writablePath,
  echoInhibitTime_ms — die flachen `<ExecutionData>`-Werte; `BaseData` erfüllt
  sie, DTO-Sicht via `Mapper.executionConfig`). `getWritablePath()` ist dabei
  eine **abgeleitete** Sicht (OS-Weiche Windows/Linux), jetzt explizit im
  Mapper statt nur inline in der Domäne. Konsumenten verschmälert:
  - `Main.serverTerminateAndExit` / `serverRestartAndExit` (lesen nur den Port)
    nehmen jetzt `ExecutionConfig`.
  - `Instances` liest die flachen Werte über ein `ExecutionConfig`-Feld; an
    `baseData` bleiben nur die Aggregat-Zugriffe (Units, Mqtt, ExceptionMail,
    IoBroker).
- ✅ **`IoBroker`**: hielt das ganze `BaseData`, las daraus aber nur
  `getMqtt().getTopicPrefix()` — auf die vorhandene Sicht `MqttTopicConfig`
  verschmälert (`setBaseData` → `setTopicConfig`; die Verdrahtung im
  `BaseData.Creator` übergibt `baseData.getMqtt()`).
- ✅ **`ExceptionMail.sendTestMail(BaseData)`** → `sendTestMail(Units)`: liest
  nur die Unit-Liste für den Mail-Feature-Check.
- ✅ **Kein Config-Lesen** (nur statischer `BaseData.DEBUG`-Flag): `HumanAccess`,
  `SolvisScreen`, `Constants` — der Debug-Schalter ist Laufzeit-Zustand, kein
  base.xml-Wert (wird über das XML-Attribut `DEBUG` gesetzt, bleibt vorerst).

Damit hängen an `BaseData` als Typ nur noch: der Lese-Fluss (`Main` erhält es
vom Reader und reicht Aggregate weiter), `Instances` (Aggregat-Zugriffe) und
`IoBroker`-Verdrahtung im Creator. Die Aggregat-Getter (`getUnits`, `getMqtt`,
`getExceptionMail`, `getIoBroker`) sind der verbleibende Block — sie fallen,
wenn `Unit`/`Units` selbst DTO-gestützt sind (der große `Unit`-Teil).

### Ausrollung auf die Unit-Konsumenten — flache Skalarwerte (✅ erledigt, grün)

`Unit` ist das tiefe Aggregat; die Bestandsaufnahme aller Lesestellen zeigt
aber: die **meisten Konsumenten lesen nur flache Skalarwerte** (Timing,
Messwert-Intervalle, Bildschirm-Parameter). Muster angewandt:

- ✅ **Neue schmale Sicht `UnitConfig`** (id + 17 Skalarwerte inkl. der
  Ableitungen `isBuffered` und der ×1000-Intervalle; `Unit` erfüllt sie,
  DTO-Sicht via `Mapper.unitConfig`). Zugang im laufenden System über
  **`Solvis.getUnitConfig()`** — `Solvis` bleibt der Laufzeit-Halter, gibt die
  Config aber als Sicht heraus.
- ✅ **Konsumenten umgestellt** (alle Skalar-Lesestellen): `WatchDog`,
  `Distributor`, `HumanAccess` (flacher Teil; der `Features`-Zugriff bleibt
  vorerst am Aggregat), `SolvisWorkers`, `Measurement`, `StrategyReheat`,
  `SolvisData`, `ScreenSaver.Exec`, `ErrorState`, `Solvis.MeasurementUpdateThread`
  (Konstruktor nimmt jetzt `UnitConfig`).
- ✅ **DTO ergänzt:** `resetErrorDelayTime_ms` als Attribut gebunden (Getter
  `Unit.getResetErrorDelayTime()` existiert inzwischen — damit 1:1-vergleichbar,
  der frühere Ausschluss ist obsolet).
- ✅ **Dual-Parse-Test:** alle 19 `UnitConfig`-Methoden aus Domäne UND DTO
  identisch.

### Default-Werte fehlender Attribute (✅ gelöst, im Standard)

Der alte Parser trägt seine Defaults in den **Creator-Feldern** (Initialisierer,
die `setAttribute` nur bei vorhandenem Attribut überschreibt). Der
**JAXB-Standard-Mechanismus ist identisch**: Der Unmarshaller setzt nur Felder,
deren Attribut im Dokument vorkommt — **Feld-Initialisierer im DTO** sind daher
der standardkonforme Ort für Defaults (kein Mapper-Sonderweg nötig). Die
base.xsd deklariert selbst keine `default=`-Werte (nur `use="required"`);
XSD-Defaults würden ohnehin keinen der beiden Parser erreichen (der alte liest
nach der Validierung erneut ohne Schema, JAXB unmarshallt ohne Schema).

- ✅ **Gespiegelt als DTO-Initialisierer** (dieselben Konstanten, keine
  Duplikate): `forceUpdateAfterFastChangingIntervals =
  Constants.FORCE_UPDATE_AFTER_N_INTERVALS` (3),
  `reheatingNotRequiredActiveTime_ms =
  Constants.Defaults.REHEATING_NOT_REQUIRED_ACTIVE_TIME` (30000),
  `Iobroker`-Interfaces = `Constants.IoBroker.DEFAULT_*`. Die übrigen
  Optionalen (doubleUpdate, resetErrorDelay, fwLth, …) defaulten auf 0/false —
  primitives Feld genügt.
- ✅ **Feldübergreifender Fallback:** fehlendes `measurementsIntervalFast_s` →
  Wert von `measurementsInterval_s` (Creator-Semantik). Im DTO als `Integer`
  gebunden (null = nicht gesetzt, kanonisch); der Fallback liegt als abgeleitete
  Sicht in `Mapper.measurementsIntervalFastMs` — er referenziert zwei Felder und
  gehört damit in die Sicht, nicht in die Bindung.
- ✅ **Bewiesen per Dual-Parse an einer Minimal-Fixture**
  (`testFiles/xml/base-minimal.xml`, nur XSD-Pflichtattribute): beide Parser
  liefern für alle 19 `UnitConfig`-Werte identische Defaults. Nebenbefund der
  Fixture-Erstellung: Der alte Parser erzwingt fachlich **genau eines** der
  Features `InteractiveGUIAccess`/`OnlyMeasurements` — eine Validierungsregel
  außerhalb der XSD (für den Reader-Umstieg relevant).

✅ **Nachgezogen (ehemals notierte Restpunkte):** (a) Das Alternativ-Attribut
`defaultReadMeasurementsInterval_ms` (bereits in ms, deprecated-Pfad des
Creators) ist gebunden; `Mapper.measurementsIntervalMs` löst beide Formen auf
(Sekunden-Form gewinnt bei Doppelung; fehlen beide, wirft die Sicht wie der
alte Parser) — dual-parse-belegt über `base-extended.xml`. (b) Die
XSD-Doku-Inkonsistenz bei `resetErrorDelayTime_ms` („Default: 5min" vs. real
`0`) ist in der base.xsd korrigiert (an die Implementierung angeglichen, als
Fork-Korrektur markiert). (c) Der `<Iobroker>`-**Element**-Default liegt jetzt
in `Mapper.toIoBroker` (fehlendes Element → Default-Instanz; `IoBroker.of` +
Getter ergänzt) — dual-parse-belegt an Template und Minimal-Fixture.

**Noch am Aggregat** (eigene Sichten folgen): `getFeatures` (ErrorState,
EquipmentOnOff, Solvis, HumanAccess, ExceptionMail), `getConfiguration`
(Instances, Solvis), `isChannelIgnored` (AllSolvisData,
AllChannelDescriptions), `getChannelAssignment` (ChannelInstance),
`getChannelOptions`/`getDuration` (Solvis), `getUrls`/`getUrl`/`IAccountInfo`
(SolvisConnection-Verdrahtung), Identitäts-/Sonderfälle (`isAdmin`, `isCsvUnit`,
`getComment`, `getForcedConfigMask` — Laufzeit-Zustand).

### Aggregat-Zweige als Wert-Typen (✅ Features + ChannelOptions, grün)

Erkenntnis der Bestandsaufnahme: Zweige wie `Features` und
`AllChannelOptions`/`ChannelOption` sind **reine Wert-Typen mit Fachsemantik**
(Feature-Defaults, `isInteractiveGUIAccess`-Regel, `modify()`-Arithmetik) —
keine Laufzeit-Kopplung. Für sie gilt daher ein einfacherer Schnitt als das
Sicht-Interface-Muster:

- **Der Wert-Typ bleibt erhalten** (die Semantik lebt nur an einer Stelle,
  keine Duplikation in einer DTO-Sicht), seine Konsumenten bleiben unberührt.
- **Nur der Creator fällt später weg**; der Mapper baut den Wert-Typ aus dem
  DTO über eine neue öffentliche Factory: `Features.of(Map)` →
  `Mapper.toFeatures`, `AllChannelOptions.of(Collection)` →
  `Mapper.toChannelOptions`/`toChannelOptionList`.
- ✅ **Nullable-Bindung nachgezogen:** `ChannelDto.fix/factor/offset` sind jetzt
  `Double` (null = nicht gesetzt — der alte Parser unterscheidet das in
  `modify()`), `powerOnDelay_s` als `Integer` mit ×1000-Ableitung und Default
  −1 in `Mapper.powerOnDelayMs` (Creator-Semantik).
- ✅ **Grün:** Features aus Domäne UND DTO in allen 11 semantischen Abfragen +
  Map identisch (Template und Minimal-Fixture inkl. per-Feature-Defaults); die
  Regel „genau eines von InteractiveGUIAccess/OnlyMeasurements" wirft auch am
  DTO-Weg (`Features.of`); ChannelOption-Liste charakterisiert (7 Optionen,
  ×1000, Default −1).

✅ **Feature-Alt-Form gebunden:** Die XSD erlaubt alternativ **benannte
Elemente** (`<ClockTuning>true</ClockTuning>`); das DTO bindet jetzt beide
Formen, `Mapper.featuresToMap` führt sie zusammen (bei — praktisch nicht
vorkommender — Doppelung derselben Id gewinnt die benannte Form; der alte
Parser entschied nach Dokumentreihenfolge). Dual-parse-belegt über
`base-extended.xml` (benannte + generische Form gemischt).

### Restliche Aggregat-Zweige als Wert-Typen (✅ komplett, grün)

Nach demselben Schnitt wie Features/ChannelOptions sind nun **alle**
Unit-Kindzweige aus dem DTO konstruierbar:

- **Urls** → `Mapper.toUrls` (kanonische Liste), **IgnoredChannels** →
  `Mapper.toIgnoredChannels` (kompilierte `Pattern`, ungültige → XmlException
  wie der Creator), **Durations** → `Duration.of`/`AllDurations.of` +
  `Mapper.toDurations` (Dubletten: geloggt, letzter gewinnt), **Assignments** →
  `Mapper.toChannelAssignments` (Map über Assignment-Id, Dubletten →
  XmlException; die von der base.xsd nicht erlaubten Creator-Felder
  alias/booleanValue/Configuration bleiben null), **Configuration** →
  `Configuration.of` + `Mapper.toConfiguration` (inkl. Extensions; `solarType`
  ist in der base.xsd nicht deklariert — toter Creator-Pfad → null).
- **passwordCrypt-Ableitung vereinheitlicht:** `Mapper.toCryptAes` ist die
  gemeinsame Sicht für Mqtt/Unit/ExceptionMail (Fehlschlag am Objekt abfragbar,
  Reaktion beim Aufrufer); `toMqtt`/`connectionConfig` nutzen sie.
- Damit sind **alle Bausteine für den `base.xml`-Reader-Umstieg vorhanden**:
  jeder Zweig ist gebunden, jeder Wert-Typ konstruierbar, Defaults und
  Validierungsregeln gespiegelt.

## 8. Reader-Umstieg base.xml (✅ vollzogen, grün)

**`BaseControlFileReader.read()` läuft jetzt über den Standard-Stack:**
XSD-Validierung per `javax.xml.validation` (statt `XmlStreamReader.validate`),
Parsen per JAXB (`JaxbBaseReader`), Konstruktion des Domänengraphen per
`Mapper.toBaseData` (→ `toUnit` komponiert alle einzeln verifizierten
Bausteine; neue Factories `BaseData.of`, `Units.of`, `Unit.of`,
`ExceptionMail.of` (+ `RecipientData`-Record, hält `Recipient`/`Security`
paketprivat), `Proxy.of`). Fehlerverhalten wie zuvor (fehlende XSD/invalide
Datei → FATAL + null). `Main` und alle Konsumenten sind unverändert — sie
erhalten denselben Domänengraphen.

- ✅ **Beweis:** `ParseDiffTest.readerUmstiegVollGraphIdentisch` — die
  kanonische Form des kompletten Domänengraphen (jeder öffentliche Getter,
  rekursiv) ist für Template, Minimal- und erweiterte Fixture **zeichengleich**
  zwischen altem und neuem Pfad. Zusätzlich charakterisiert
  `BaseConfigParsingTest` jetzt den neuen Pfad (gleiche Erwartungen, grün).
- ✅ **Dabei gefundene Feinheiten:** (a) Das Template enthält ein **leeres**
  `<Extensions>`-Element — der alte Parser macht daraus eine leere Liste, nicht
  `null`; `toConfiguration` bildet jetzt „Element vorhanden ⇒ (ggf. leere)
  Liste, Element fehlt ⇒ null" exakt nach (der Voll-Graph-Vergleich fand die
  Abweichung sofort). (b) `ParseDiff` kanonisiert `Throwable` als Klasse +
  Message — Stacktrace/Cause hängen vom Aufrufweg des Parsers ab und wären
  Vergleichsrauschen.
- **Vervollständigte Bindung für den Umstieg:** `DEBUG`-Root-Attribut,
  `Unit.csvUnit`, deprecated `Unit.password` (Klartext), `ExceptionMail/Proxy`.
- **Alter Pfad:** `readWithCreators()` blieb zunächst als Dual-Parse-Referenz
  erhalten und ist inzwischen — mit den base.xml-Creators — **entfernt**
  (siehe Abschnitt 9).
- ⚠️ **Bewusste Robustheits-Abweichung:** Fehlt das (laut XSD optionale)
  `<Units>`-Element komplett, lieferte der alte Parser einen NPE-Absturz im
  `BaseData`-Konstruktor; der neue Pfad liefert eine leere Units-Liste.

## 9. base.xml-Creators entfernt, Verhalten per Golden-Snapshot gepinnt (✅)

Nach dem Reader-Umstieg sind die **base.xml-Creator-Klassen entfernt**:
`BaseData.Creator` (+ inneres `ExecutionData`), `Units.Creator`, `Unit.Creator`
(+ `AssignmentsCreator`), `Features.Creator`, `AllChannelOptions`-Creators,
`Mqtt.Creator`, `Ssl.Creator`, `IoBroker.Creator`, `ExceptionMail.Creator`,
`Proxy.Creator`, `Mail.Recipient`-Creator (inkl. `ArrayXml`-Anbindung und
jetzt totem `recipientTypeMap`) sowie `readWithCreators()` selbst.

**Sicherheitsnetz danach:** Vor der Entfernung wurde die kanonische Form des
kompletten Domänengraphen (via `ParseDiff`, jeder öffentliche Getter rekursiv)
**aus dem alten Creator-Pfad** als **Golden-Snapshots** gepinnt
(`testFiles/xml/golden/*.canonical.txt`, je Template/Minimal/Extended). Der
Test `ParseDiffTest.kanonischeFormEntsprichtGoldenSnapshot` vergleicht den
JAXB-Pfad dauerhaft dagegen — das Original-Verhalten bleibt überprüfbar, auch
ohne den Original-Parser. `getWritablePath` ist von der kanonischen Form
ausgenommen (OS-Weiche, Snapshots laufen auch in der Linux-CI) und separat in
`BaseConfigParsingTest` charakterisiert. Die früheren Dual-Parse-Tests laufen
als Konsistenztests weiter (DTO-Sicht ↔ Domänenkonstruktion — prüft die
`toUnit`/`toBaseData`-Verdrahtung).

**In XMLLibrary-Nutzung verbleiben** (control.xml-/graficData-Pfad):
`unit.Configuration.Creator` (von `NotValidConfigurations` genutzt),
`Feature`/`Feature.Creator` (SystemGrafics, configuration.Configuration),
`Duration.Creator`/`AllDurations.Creator` (SolvisDescription),
`ChannelAssignment.Creator` (AllChannelAssignments) — sie fallen mit der
Umstellung der übrigen XML-Bäume.

## 10. measurements.xml auf JAXB umgestellt — Lesen UND Schreiben (✅)

Der zweite Baum ist komplett umgestellt (kleinster Baum; etabliert das
**Marshalling-Muster** für Dateien, die der Server auch schreibt):

- **DTO im eigenen Paket `xml.jaxb.backup`** — bewusst ohne `@XmlSchema`:
  Das Eltern-Paket ist auf den base.xsd-Namespace qualifiziert (dessen
  `package-info` erklärt auch, warum die base.xml-Bindung namespace-korrekt
  funktioniert); die Backup-Datei wird aber **ohne Namespace** geschrieben.
- **Namespace-Toleranz des alten Parsers nachgebildet:** Der alte,
  localPart-basierte Parser akzeptierte Dateien mit und ohne Namespace. Der
  JAXB-Leser nutzt daher einen **namespace-strippenden SAX-Filter** +
  `declaredType`-Unmarshalling — damit sind alle Generationen lesbar: aktuelle
  Wurzel `SolvisBackup` (namespace-los), Legacy `SolvisMeasurements` mit
  measurements-Namespace. Der frühere Zwei-Pass-Fallback im `BackupHandler`
  entfällt (beide Kind-Formen sind gebunden).
- **Schreiben per JAXB-Marshalling** (`JaxbBackupReader.write`), Wurzel- und
  Elementformen identisch zum alten `XMLStreamWriter`-Pfad; die
  `writeXml`-Methoden und das `IValue.writeXml`-Interface-Relikt sind entfernt.
- **Abbildung im backup-Paket** (`BackupMapper`, paketprivat verzahnt):
  Wert-Semantik wie der alte `ValueCreator` (Roh-String → typisierter
  `SingleData`, Zeitstempel −1; die `Mode`-Klasse ist aus dem ValueCreator in
  `Measurement` gewandert). Alle Backup-Creators sind entfernt.
- ✅ **Tests** (`BackupJaxbTest`, im backup-Paket): Legacy- und aktuelle Form
  liefern identische Werte (alle vier Wert-Typen), Schreib-/Wieder-Lese-
  Roundtrip verlustfrei in aktueller Wurzelform, `BackupHandler`-Integration
  (Lesen beim Konstruieren) grün.

## 11. control.xml — Einstieg in den dritten (großen) Baum (🔄 begonnen)

Bestandsaufnahme und erster dual-parse-verifizierter DTO-Kern:

- ✅ **Struktur:** 5103 Zeilen, Wurzel `SolvisDescription` mit **13 Zweigen**
  (Configurations, Standby, ScreenSaver, Screens, FallBack, ScreenGrafics,
  ChannelDescriptions, Preparations, Clock, Durations, Miscellaneous,
  ErrorDetection, ChannelAssignments). `control.xsd` nutzt **denselben
  Namespace wie base.xsd** → die DTOs liegen im selben `xml.jaxb`-Paket und
  strukturgleiche Beans werden wiederverwendet (`DurationsDto`,
  `ChannelAssignmentsDto`).
- ✅ **DTO-Kern grün** (`ControlDto`, `JaxbControlReader`,
  `ControlDualParseTest`): `Miscellaneous` (alle 8 Attribute dual-parse-
  identisch, `Miscellaneous.of` + `Mapper.toMiscellaneous`), `Durations`
  (alle 8 Vorlagen-Einträge identisch über `Mapper.toDurations` — die
  base.xml-Maschinerie trägt direkt), `ChannelAssignments` (Bindung
  charakterisiert; das Domänen-Aggregat `AllChannelAssignments` hängt an der
  OfConfigs-Maschinerie und folgt später).
- ⚠️ **Wichtiger Reader-Befund für den späteren Umstieg:** `ControlFileReader`
  nutzt `XmlStreamReader.ReadData.getHash()` — der alte Parser berechnet beim
  Lesen einen **Inhalts-Hash**, über den Resource-/Datei-Version verglichen
  und `mustLearn` entschieden wird (gekoppelt an `AllSolvisGrafics`-
  Invalidierung). Beim JAXB-Umstieg muss diese Hash-Berechnung nachgebildet
  werden — oder ein einmaliges Neu-Lernen wird bewusst in Kauf genommen
  (zu entscheiden). Dazu kommt die Resource-vs-Datei-Merge-Logik
  (modifiedByUser, renameDuplicates), die erhalten bleiben muss.
- **Fahrplan wie bei base.xml:** DTO-Baum Zweig für Zweig erweitern (nächste
  Kandidaten: die flachen/mittleren Zweige ScreenSaver, ErrorDetection,
  Standby, Configurations-Grundgerüst; zuletzt die tiefen Screens/
  ChannelDescriptions), je Zweig Dual-Parse; erst danach Reader-Umstieg +
  Creator-Abbau. Die im base.xml-Abbau verbliebenen geteilten Creators
  (`Duration`, `ChannelAssignment`, `unit.Configuration`, `Feature`) fallen
  mit diesem Baum.

## 12. graficData.xml auf JAXB umgestellt — Lesen UND Schreiben (✅)

Der vierte Baum ist komplett umgestellt (nach dem measurements-Muster):

- **DTO im eigenen Paket `xml.jaxb.grafics`** (ohne `@XmlSchema` — die Datei
  wird namespace-los geschrieben); gelesen wird mit dem jetzt **gemeinsamen**
  `SaxNamespaceStripper` (aus dem Backup-Leser extrahiert), damit auch
  Bestände mit graficData-Namespace lesbar bleiben. Die Hash-Attribute sind
  als `Long`-Wrapper gebunden (`null` = fehlt ⇒ Lerndaten verwerfen — wie
  beim alten Parser).
- **Bild-Codierung als Klassen-API:** Base64-PNG ↔ `MyImage`/`Pattern` liegt
  jetzt in `ScreenGraficData.of`/`toBase64Png`/`isPattern` (vorher im
  XML-Creator bzw. `writeXml` versteckt). Wichtiger Befund dabei:
  `MyImage.createBufferdImage()` setzt das **4-Bit-Palettenmodell** der
  Solvis-Anzeige voraus — der Schreibpfad funktioniert (wie schon der alte)
  nur für die live erfassten Gerätebilder, nicht für beliebige RGB-Bilder.
- **Abbildung als `GraficsMapper`** im model.objects-Paket (paketprivate
  Verzahnung, analog `BackupMapper`); `GraficFileHandler` liest/schreibt über
  JAXB, das Fehlerverhalten ist unverändert (defekte Datei ⇒ leere Lerndaten,
  kein Abbruch). Alle Grafik-Creators und `writeXml`-Pfade sind entfernt
  (inkl. `IXmlWriteable`).
- **Nebeneffekt/Korrektur:** Der alte Writer schrieb die Wurzel-Attribute
  je `<System>`-Schleifendurchlauf erneut — bei mehr als einer Anlage ein
  Schreibfehler (StAX-Attribut nach Kind-Element). Das JAXB-Marshalling
  schreibt die Attribute korrekt einmalig auf die Wurzel.
- ✅ **Tests** (`GraficsJaxbTest`): Roundtrip über den echten
  `GraficFileHandler` verlustfrei (Masken, Feature-Stand, Hashes,
  Bild-Pixel), Namespace-Variante lesbar, defekte Datei ⇒ leere Lerndaten.
