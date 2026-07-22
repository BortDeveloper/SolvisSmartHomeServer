package de.sgollmer.solvismax.xml.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * JAXB-DTO für das Wurzelelement {@code <SolvisDescription>} der
 * {@code control.xml} — der <b>Einstieg</b> in den dritten (großen) Baum
 * (MODERNISIERUNG.md 3.3). {@code control.xsd} nutzt denselben Namespace wie
 * {@code base.xsd}; die DTOs liegen daher im selben Paket und können
 * strukturgleiche Beans wiederverwenden ({@link BaseDataDto.DurationsDto},
 * {@link BaseDataDto.ChannelAssignmentsDto}).
 *
 * <p>
 * Der Baum wächst — wie seinerzeit bei {@code base.xml} — inkrementell: nicht
 * modellierte Zweige (Screens, ChannelDescriptions, Clock, …) ignoriert JAXB;
 * jeder aufgenommene Zweig wird per Dual-Parse gegen den alten Parser
 * abgesichert.
 * </p>
 */
@XmlRootElement(name = "SolvisDescription")
@XmlAccessorType(XmlAccessType.FIELD)
public class ControlDto {

	@XmlElement(name = "Miscellaneous")
	public MiscellaneousDto miscellaneous;

	@XmlElement(name = "Durations")
	public BaseDataDto.DurationsDto durations;

	@XmlElement(name = "ChannelAssignments")
	public BaseDataDto.ChannelAssignmentsDto channelAssignments;

	/** JAXB-Bean für {@code <Miscellaneous>} (8 flache ms-/Zähler-Attribute). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class MiscellaneousDto {
		@XmlAttribute(name = "measurementsBackupTime_ms") public int measurementsBackupTime_ms;
		@XmlAttribute(name = "powerOffDetectedAfterIoErrors") public int powerOffDetectedAfterIoErrors;
		@XmlAttribute(name = "powerOffDetectedAfterTimeout_ms") public int powerOffDetectedAfterTimeout_ms;
		@XmlAttribute(name = "unsuccessfullWaitTime_ms") public int unsuccessfullWaitTime_ms;
		@XmlAttribute(name = "connectionHoldTime_ms") public int connectionHoldTime_ms;
		@XmlAttribute(name = "solvisConnectionTimeout_ms") public int solvisConnectionTimeout_ms;
		@XmlAttribute(name = "solvisReadTimeout_ms") public int solvisReadTimeout_ms;
		@XmlAttribute(name = "clientTimeoutTime_ms") public int clientTimeoutTime_ms;
	}

	@XmlElement(name = "ScreenSaver")
	public ScreenSaverDto screenSaver;

	@XmlElement(name = "Standby")
	public StandbyDto standby;

	@XmlElement(name = "ErrorDetection")
	public ErrorDetectionDto errorDetection;

	/**
	 * JAXB-Bean für {@code <ScreenSaver>}: Erkennungs-/Rücksetz-Parameter des
	 * Bildschirmschoners. {@code ResetScreenSaver} ist ein Touch-Punkt
	 * (Koordinate + Referenzen auf {@code <Duration>}-Ids für Druck-/
	 * Loslass-Dauer).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ScreenSaverDto {
		@XmlAttribute(name = "xCoordinateWithinTimedate") public int xCoordinateWithinTimedate;

		@XmlElement(name = "MaxGraficSize")
		public CoordinateDto maxGraficSize;

		@XmlElement(name = "ResetScreenSaver")
		public TouchPointDto resetScreenSaver;
	}

	/** JAXB-Bean für Koordinaten-Elemente ({@code X}/{@code Y}-Attribute). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class CoordinateDto {
		@XmlAttribute(name = "X") public int x;
		@XmlAttribute(name = "Y") public int y;
	}

	/** JAXB-Bean für Touch-Punkte ({@code <Coordinate>} + Duration-Referenzen). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class TouchPointDto {
		@XmlAttribute(name = "pushTimeRefId") public String pushTimeRefId;
		@XmlAttribute(name = "releaseTimeRefId") public String releaseTimeRefId;

		@XmlElement(name = "Coordinate")
		public CoordinateDto coordinate;
	}

	/** JAXB-Bean für {@code <Standby>} — Kanäle, deren Wert Standby anzeigt. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class StandbyDto {
		@XmlElement(name = "Channel")
		public java.util.List<StandbyChannelDto> channel;
	}

	/** JAXB-Bean für {@code <Channel id="..." value="..."/>} unter Standby. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class StandbyChannelDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "value") public String value;
	}

	/**
	 * JAXB-Bean für {@code <ErrorDetection>}: Rahmen-Grenzbereiche des
	 * Fehler-Popups, Uhrzeit-/Datums-Rechtecke und der Fehler-Kanal.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ErrorDetectionDto {
		@XmlElement(name = "LeftBorder") public RangeDto leftBorder;
		@XmlElement(name = "RightBorder") public RangeDto rightBorder;
		@XmlElement(name = "TopBorder") public RangeDto topBorder;
		@XmlElement(name = "MiddleBorder") public RangeDto middleBorder;
		@XmlElement(name = "BottomBorder") public RangeDto bottomBorder;
		@XmlElement(name = "HhMm") public RectangleDto hhMm;
		@XmlElement(name = "DdMmYy") public RectangleDto ddMmYy;
		@XmlElement(name = "ErrorCondition") public ErrorConditionDto errorCondition;
	}

	/** JAXB-Bean für Grenzbereiche ({@code lowerLimit}/{@code higherLimit}). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class RangeDto {
		@XmlAttribute(name = "lowerLimit") public int lowerLimit;
		@XmlAttribute(name = "higherLimit") public int higherLimit;
	}

	/**
	 * JAXB-Bean für Rechtecke ({@code TopLeft}/{@code BottomRight}).
	 * {@code invertFunction} entspricht dem gleichnamigen Attribut des
	 * Domänen-{@code Rectangle} (Default {@code false} wie im alten Creator);
	 * genutzt wird es nur an {@code MustBeWhite}-Vorkommen im Screens-Zweig.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class RectangleDto {
		@XmlAttribute(name = "invertFunction") public boolean invertFunction = false;

		@XmlElement(name = "TopLeft") public CoordinateDto topLeft;
		@XmlElement(name = "BottomRight") public CoordinateDto bottomRight;
	}

	/** JAXB-Bean für {@code <ErrorCondition channelId="..." value="..."/>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ErrorConditionDto {
		@XmlAttribute(name = "channelId") public String channelId;
		@XmlAttribute(name = "value") public boolean value;
	}

	@XmlElement(name = "Configurations")
	public ConfigurationsDto configurations;

	/**
	 * JAXB-Bean für {@code <Configurations>}: die Zuordnung von Anlagen-Typ,
	 * Hauptheizung, Heizkreisen, Solar-Typ und Erweiterungen zu Bits der
	 * <b>Konfigurationsmaske</b> (hex), die als ungültig definierten
	 * Kombinationen sowie die beiden <b>Erkennungs-Unterzweige</b>
	 * {@code <HeaterLoops>} und {@code <Solar>} (OCR-Bereiche, über die der
	 * Server beim Lernen die tatsächliche Anlagen-Konfiguration abliest).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ConfigurationsDto {
		@XmlElement(name = "SolvisTypes") public TypeListDto solvisTypes;
		@XmlElement(name = "MainHeatings") public TypeListDto mainHeatings;
		@XmlElement(name = "HeaterCircuits") public TypeListDto heaterCircuits;
		@XmlElement(name = "SolarTypes") public TypeListDto solarTypes;
		@XmlElement(name = "Extensions") public TypeListDto extensions;
		@XmlElement(name = "NotValid") public NotValidDto notValid;
		@XmlElement(name = "HeaterLoops") public HeaterLoopsDto heaterLoops;
		@XmlElement(name = "Solar") public SolarDto solar;
	}

	/**
	 * JAXB-Bean für {@code <HeaterLoops screenRef="...">}: die drei
	 * Heizkreis-Buttons auf dem Home-Screen, an deren Beschriftung (Ziffern
	 * 1–3 per OCR) die Anzahl der Heizkreise erkannt wird.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class HeaterLoopsDto {
		@XmlAttribute(name = "screenRef") public String screenRef;

		@XmlElement(name = "HK1Button") public RectangleDto hk1Button;
		@XmlElement(name = "HK2Button") public RectangleDto hk2Button;
		@XmlElement(name = "HK3Button") public RectangleDto hk3Button;
	}

	/**
	 * JAXB-Bean für {@code <Solar screenRef maxTemperatureX10 format>}: die
	 * beiden OCR-Temperaturbereiche des Solar-Screens, über die die
	 * Solar-Konfiguration erkannt wird. {@code format} bleibt kanonisch der
	 * Regex-String der Vorlage (die Domäne kapselt ihn in
	 * {@code Helper.Format}); {@code maxTemperatureX10} ist die
	 * Plausibilitätsgrenze in Zehntelgrad.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class SolarDto {
		@XmlAttribute(name = "screenRef") public String screenRef;
		@XmlAttribute(name = "maxTemperatureX10") public int maxTemperatureX10;
		@XmlAttribute(name = "format") public String format;

		@XmlElement(name = "ReturnTemperature") public RectangleDto returnTemperature;
		@XmlElement(name = "OutgoingTemperature") public RectangleDto outgoingTemperature;
	}

	/** JAXB-Bean für die Typ-Gruppen ({@code <Type id configuration [dontCare]/>}-Listen). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class TypeListDto {
		@XmlElement(name = "Type")
		public java.util.List<TypeDto> type;

		/** Sicht: Maske zur Id (kanonisch gebunden bleibt der Hex-String). */
		public Long configuration(final String id) {
			if (this.type == null) {
				return null;
			}
			return this.type.stream().filter(t -> id.equals(t.id)).findFirst()
					.map(TypeDto::configurationValue).orElse(null);
		}
	}

	/**
	 * JAXB-Bean für {@code <Type>}. {@code configuration} bleibt kanonisch der
	 * Hex-String der Vorlage ({@code 0x…}); die Ableitung zur Maske liefert
	 * {@link #configurationValue()} ({@code Long.decode}, wie der alte Parser).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class TypeDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "configuration") public String configuration;
		/** {@code true} = Bit zählt beim Vergleich nicht ({@code null} = Attribut fehlt). */
		@XmlAttribute(name = "dontCare") public Boolean dontCare;

		public long configurationValue() {
			return Long.decode(this.configuration);
		}
	}

	/** JAXB-Bean für {@code <NotValid>} — ungültige Konfigurations-Kombinationen. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class NotValidDto {
		@XmlElement(name = "Configuration")
		public java.util.List<ConfigurationRefDto> configuration;
	}

	/**
	 * JAXB-Bean für einen {@code <Configuration>}-Verweis (Attribute wie in der
	 * base.xsd-Configuration; {@code <Extensions>} wird aus dem base-DTO
	 * wiederverwendet — gleiche Struktur, gleicher Namespace).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ConfigurationRefDto {
		@XmlAttribute(name = "type") public String type;
		@XmlAttribute(name = "mainHeating") public String mainHeating;
		@XmlAttribute(name = "heatingCircuits") public Integer heatingCircuits;
		@XmlAttribute(name = "admin") public Boolean admin;
		@XmlAttribute(name = "comment") public String comment;

		@XmlElement(name = "Extensions")
		public BaseDataDto.ExtensionsDto extensions;
	}

	@XmlElement(name = "FallBack")
	public FallBackDto fallBack;

	@XmlElement(name = "Preparations")
	public PreparationsDto preparations;

	@XmlElement(name = "ScreenGrafics")
	public ScreenGraficsDto screenGrafics;

	/**
	 * JAXB-Bean für {@code <FallBack>}: die Tastenfolge, mit der der Server aus
	 * einem unbekannten Bildschirm zurück zum Home-Screen findet. Die Folge ist
	 * eine <b>geordnete Mischsequenz</b> aus {@code <Back/>} und
	 * {@code <ScreenRef id="..."/>} — gebunden als polymorphe Liste
	 * ({@code @XmlElements}), damit die Reihenfolge erhalten bleibt.
	 * {@code <LastChance>} ist die Eskalationsfolge mit derselben Struktur.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class FallBackDto {
		@jakarta.xml.bind.annotation.XmlElements({
				@XmlElement(name = "Back", type = BackDto.class),
				@XmlElement(name = "ScreenRef", type = ScreenRefDto.class) })
		public java.util.List<Object> step;

		@XmlElement(name = "LastChance")
		public LastChanceDto lastChance;
	}

	/** JAXB-Bean für {@code <LastChance>} (gleiche Schrittstruktur wie FallBack). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class LastChanceDto {
		@jakarta.xml.bind.annotation.XmlElements({
				@XmlElement(name = "Back", type = BackDto.class),
				@XmlElement(name = "ScreenRef", type = ScreenRefDto.class) })
		public java.util.List<Object> step;
	}

	/** JAXB-Bean für {@code <Back/>} (Zurück-Taste, keine Attribute). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class BackDto {
	}

	/** JAXB-Bean für {@code <ScreenRef id="..."/>} (Verweis auf einen Screen). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ScreenRefDto {
		@XmlAttribute(name = "id") public String id;
	}

	/** JAXB-Bean für {@code <Preparations>} — GUI-Vorbereitungs-Sequenzen. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class PreparationsDto {
		@XmlElement(name = "Preparation")
		public java.util.List<PreparationDto> preparation;
	}

	/**
	 * JAXB-Bean für {@code <Preparation id="...">}: ein Touch-Punkt plus die
	 * Grafik, an der der Erfolg der Vorbereitung erkannt wird.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class PreparationDto {
		@XmlAttribute(name = "id") public String id;

		@XmlElement(name = "TouchPoint")
		public TouchPointDto touchPoint;

		@XmlElement(name = "ScreenGrafic")
		public ScreenGraficDescriptionDto screenGrafic;
	}

	/** JAXB-Bean für {@code <ScreenGrafics>} — die Grafik-Beschreibungen. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ScreenGraficsDto {
		@XmlElement(name = "ScreenGrafic")
		public java.util.List<ScreenGraficDescriptionDto> screenGrafic;
	}

	@XmlElement(name = "Screens")
	public ScreensDto screens;

	/**
	 * JAXB-Bean für {@code <Screens homeId="...">} — der größte Zweig der
	 * control.xml. Die Kinder sind eine <b>geordnete Mischsequenz</b> aus
	 * {@code <Screen>} und {@code <ScreenSequence>}; die Reihenfolge bleibt
	 * erhalten (polymorphe {@code @XmlElements}-Liste wie bei FallBack), denn
	 * der alte Parser baut daraus in Dokumentreihenfolge die
	 * {@code OfConfigs}-Gruppen (mehrere Screens dürfen dieselbe Id tragen —
	 * z.&nbsp;B. SolvisMax6- vs. SolvisMax7-Variante, unterschieden per
	 * {@code Configuration}-Maske).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ScreensDto {
		@XmlAttribute(name = "homeId") public String homeId;

		@jakarta.xml.bind.annotation.XmlElements({
				@XmlElement(name = "Screen", type = ScreenDto.class),
				@XmlElement(name = "ScreenSequence", type = ScreenSequenceDto.class) })
		public java.util.List<Object> screen;

		/** Sicht: alle {@code <Screen>}-Vorkommen einer Id in Dokumentreihenfolge (OfConfigs-Analogon). */
		public java.util.List<ScreenDto> screens(final String id) {
			if (this.screen == null) {
				return java.util.Collections.emptyList();
			}
			return this.screen.stream().filter(s -> s instanceof ScreenDto).map(s -> (ScreenDto) s)
					.filter(s -> id.equals(s.id)).collect(java.util.stream.Collectors.toList());
		}
	}

	/**
	 * JAXB-Bean für {@code <Screen>}: Navigations-Attribute, optionale
	 * Konfigurations-Einschränkung, die Anwahl-Strategie ({@code TouchPoint}
	 * <b>oder</b> {@code UserSelection}), Blätter-Tasten einer Sequenz,
	 * Identifikations-Merkmale und Ignore-Bereiche. Die booleschen Attribute
	 * spiegeln die Creator-Defaults ({@code false}). Der alte Parser behandelt
	 * <b>leere</b> {@code previousId}/{@code backId} als nicht gesetzt —
	 * kanonisch bleibt der Vorlagen-String, die Ableitung liefern
	 * {@link #previousIdOrNull()}/{@link #backIdOrNull()}.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ScreenDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "sortId") public String sortId;
		@XmlAttribute(name = "previousId") public String previousId;
		@XmlAttribute(name = "backId") public String backId;
		@XmlAttribute(name = "ignoreChanges") public boolean ignoreChanges = false;
		@XmlAttribute(name = "mustSave") public boolean mustSave = false;
		@XmlAttribute(name = "noRestore") public boolean noRestore = false;
		@XmlAttribute(name = "service") public boolean service = false;

		@XmlElement(name = "Configuration") public ScreenConfigurationDto configuration;
		@XmlElement(name = "TouchPoint") public TouchPointDto touchPoint;
		@XmlElement(name = "UserSelection") public UserSelectionDto userSelection;
		@XmlElement(name = "SequenceUp") public TouchPointDto sequenceUp;
		@XmlElement(name = "SequenceDown") public TouchPointDto sequenceDown;

		@XmlElement(name = "Identification")
		public java.util.List<IdentificationDto> identification;

		@XmlElement(name = "IgnoreRectangle")
		public java.util.List<RectangleDto> ignoreRectangle;

		@XmlElement(name = "PreparationRef") public PreparationRefDto preparationRef;
		@XmlElement(name = "LastPreparationRef") public PreparationRefDto lastPreparationRef;

		public String previousIdOrNull() {
			return this.previousId == null || this.previousId.isEmpty() ? null : this.previousId;
		}

		public String backIdOrNull() {
			return this.backId == null || this.backId.isEmpty() ? null : this.backId;
		}
	}

	/** JAXB-Bean für {@code <PreparationRef refId/>}/{@code <LastPreparationRef refId/>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class PreparationRefDto {
		@XmlAttribute(name = "refId") public String refId;
	}

	/**
	 * JAXB-Bean für die Konfigurations-Einschränkung eines Screens
	 * ({@code <Configuration [admin]>}): Bit-Masken gegen die
	 * Konfigurationsmaske plus optionales Feature. {@code admin} bleibt
	 * kanonisch der String der Vorlage (Domäne: {@code Admin.valueOf},
	 * Default {@code NONE} bei fehlendem Attribut). Das {@code Feature}-Kind
	 * nutzt die strukturgleiche base.xml-Bean.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ScreenConfigurationDto {
		@XmlAttribute(name = "admin") public String admin;

		@XmlElement(name = "ConfigurationMask")
		public java.util.List<ConfigurationMaskDto> configurationMask;

		@XmlElement(name = "Feature") public BaseDataDto.FeatureDto feature;
	}

	/**
	 * JAXB-Bean für {@code <ConfigurationMask andMask compareMask/>}. Die
	 * Masken bleiben kanonisch Hex-Strings; die Ableitungen entsprechen dem
	 * alten Parser ({@code Long.decode}).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ConfigurationMaskDto {
		@XmlAttribute(name = "andMask") public String andMask;
		@XmlAttribute(name = "compareMask") public String compareMask;

		public long andMaskValue() {
			return Long.decode(this.andMask);
		}

		public long compareMaskValue() {
			return Long.decode(this.compareMask);
		}
	}

	/**
	 * JAXB-Bean für {@code <Identification>}: eine <b>geordnete
	 * Mischsequenz</b> der Vergleichs-Merkmale {@code Grafic},
	 * {@code GraficRef}, {@code MustBeWhite} (Rechteck, ggf. mit
	 * {@code invertFunction}) und {@code Ocr} — polymorphe
	 * {@code @XmlElements}-Liste, damit die Dokumentreihenfolge der
	 * {@code screenCompares} des alten Parsers erhalten bleibt.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class IdentificationDto {
		@jakarta.xml.bind.annotation.XmlElements({
				@XmlElement(name = "Grafic", type = ScreenGraficDescriptionDto.class),
				@XmlElement(name = "GraficRef", type = GraficRefDto.class),
				@XmlElement(name = "MustBeWhite", type = RectangleDto.class),
				@XmlElement(name = "Ocr", type = OcrDto.class) })
		public java.util.List<Object> part;
	}

	/** JAXB-Bean für Grafik-Verweise ({@code <GraficRef refId/>}, {@code <ScreenGraficRef refId/>}). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class GraficRefDto {
		@XmlAttribute(name = "refId") public String refId;
	}

	/**
	 * JAXB-Bean für {@code <Ocr value [right] [maxPixelsOfEmptyLine]>}: der
	 * OCR-Vergleich einer Identifikation (Scan-Rechteck + optionaler Verweis
	 * auf die Vergleichs-Grafik). Defaults wie im alten Creator
	 * ({@code right=false}, {@code maxPixelsOfEmptyLine=0}).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class OcrDto {
		@XmlAttribute(name = "value") public String value;
		@XmlAttribute(name = "right") public boolean right = false;
		@XmlAttribute(name = "maxPixelsOfEmptyLine") public int maxPixelsOfEmptyLine = 0;

		@XmlElement(name = "Rectangle") public RectangleDto rectangle;
		@XmlElement(name = "ScreenGraficRef") public GraficRefDto screenGraficRef;
	}

	/**
	 * JAXB-Bean für {@code <ScreenSequence>}: eine Gruppe durchblätterbarer
	 * Screens (Anlagenstatus). <b>Befund:</b> das {@code wrapArround}-Attribut
	 * liest der alte Parser über {@code Boolean.getBoolean(value)} — das ist
	 * ein System-Property-Lookup, liefert also immer {@code false}
	 * (Alt-Parser-Bug; in der Vorlage wird das Attribut nicht genutzt). Hier
	 * kanonisch als Wrapper gebunden ({@code null} = Attribut fehlt).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ScreenSequenceDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "previousId") public String previousId;
		@XmlAttribute(name = "wrapArround") public Boolean wrapArround;

		@XmlElement(name = "Configuration") public ScreenConfigurationDto configuration;
		@XmlElement(name = "TouchPoint") public TouchPointDto touchPoint;
		@XmlElement(name = "PreparationRef") public PreparationRefDto preparationRef;

		@XmlElement(name = "ScreenRef")
		public java.util.List<ScreenRefDto> screenRef;
	}

	/**
	 * JAXB-Bean für {@code <UserSelection waitTimeAfterLastDigitRefId>}: die
	 * alternative Anwahl-Strategie über eine Code-Eingabe (Installateur-Menü)
	 * — je Ziffer OCR-Rechteck plus Hoch-/Runter-Touch-Punkte.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class UserSelectionDto {
		@XmlAttribute(name = "waitTimeAfterLastDigitRefId") public String waitTimeAfterLastDigitRefId;

		@XmlElement(name = "Digit")
		public java.util.List<DigitDto> digit;
	}

	/** JAXB-Bean für {@code <Digit digit="...">} (Rectangle + Upper/Lower). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class DigitDto {
		@XmlAttribute(name = "digit") public int digit;

		@XmlElement(name = "Rectangle") public RectangleDto rectangle;
		@XmlElement(name = "Upper") public TouchPointDto upper;
		@XmlElement(name = "Lower") public TouchPointDto lower;
	}

	@XmlElement(name = "Clock")
	public ClockDto clock;

	/**
	 * JAXB-Bean für {@code <Clock>}: die Uhr-Stell-Maschinerie
	 * ({@code ClockMonitor}) — der Zeit-Kanal, der Einstell- und der
	 * Bestätigungs-Screen, die fünf Datums-Teile (Jahr…Minute, jeweils
	 * OCR-Bereich + Anwahl-Touch + Erkennungs-Grafik), die drei Stell-Tasten
	 * und die Sperrbedingungen ({@code DisableClockSetting}).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ClockDto {
		@XmlAttribute(name = "timeChannelId") public String timeChannelId;
		@XmlAttribute(name = "screenId") public String screenId;
		@XmlAttribute(name = "okScreenId") public String okScreenId;

		@XmlElement(name = "Year") public DatePartDto year;
		@XmlElement(name = "Month") public DatePartDto month;
		@XmlElement(name = "Day") public DatePartDto day;
		@XmlElement(name = "Hour") public DatePartDto hour;
		@XmlElement(name = "Minute") public DatePartDto minute;

		@XmlElement(name = "Upper") public TouchPointDto upper;
		@XmlElement(name = "Lower") public TouchPointDto lower;
		@XmlElement(name = "Ok") public TouchPointDto ok;

		@XmlElement(name = "DisableClockSetting")
		public DisableClockSettingDto disableClockSetting;
	}

	/**
	 * JAXB-Bean für die Datums-Teile ({@code Year}/{@code Month}/{@code Day}/
	 * {@code Hour}/{@code Minute}): OCR-Rechteck, Anwahl-Touch-Punkt
	 * ({@code <Touch>} — gleiche Struktur wie {@code TouchPointDto}) und die
	 * Grafik, an der die Anwahl erkannt wird. Das {@code least}-Attribut der
	 * Vorlage (nur am {@code Year}) wird vom <b>alten Parser ignoriert</b>
	 * (leeres {@code setAttribute} in {@code DatePart.Creator}) — hier wird es
	 * kanonisch mitgebunden ({@code null} = Attribut fehlt), Semantik hat es
	 * weiterhin keine.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class DatePartDto {
		@XmlAttribute(name = "least") public Integer least;

		@XmlElement(name = "Rectangle") public RectangleDto rectangle;
		@XmlElement(name = "Touch") public TouchPointDto touch;
		@XmlElement(name = "ScreenGrafic") public ScreenGraficDescriptionDto screenGrafic;
	}

	/** JAXB-Bean für {@code <DisableClockSetting burnerId hotWaterPumpId/>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class DisableClockSettingDto {
		@XmlAttribute(name = "burnerId") public String burnerId;
		@XmlAttribute(name = "hotWaterPumpId") public String hotWaterPumpId;
	}

	/**
	 * JAXB-Bean für {@code <ScreenGrafic id="..." [exact]>[Rectangle]}: die
	 * <b>Beschreibung</b> einer zu lernenden Grafik (Bereich + Vergleichsmodus);
	 * die gelernten Bilddaten selbst liegen in {@code graficData.xml}.
	 * {@code exact} als {@link Boolean}-Wrapper ({@code null} = Attribut fehlt).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ScreenGraficDescriptionDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "exact") public Boolean exact;

		@XmlElement(name = "Rectangle")
		public RectangleDto rectangle;
	}
}
