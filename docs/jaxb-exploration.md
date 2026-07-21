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
`ExceptionMail` inkl. `Recipients`, `Iobroker`.

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

**Offene Entscheidung** (dokumentiert, noch nicht getroffen): Ob sich der
Aufwand des vollständigen Mappers zur sealed-Domäne lohnt oder ob langfristig die
**DTOs selbst das Config-Modell** werden (mit abgeleiteten Sicht-Methoden), was
Mapper + versiegelte-Domäne-Reibung + Duplikation beseitigen würde. Diese
Abwägung sollte vor dem `Unit`-Aggregat-Mapper bewusst getroffen werden.
