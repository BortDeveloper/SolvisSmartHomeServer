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

	/** JAXB-Bean für Rechtecke ({@code TopLeft}/{@code BottomRight}). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class RectangleDto {
		@XmlElement(name = "TopLeft") public CoordinateDto topLeft;
		@XmlElement(name = "BottomRight") public CoordinateDto bottomRight;
	}

	/** JAXB-Bean für {@code <ErrorCondition channelId="..." value="..."/>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ErrorConditionDto {
		@XmlAttribute(name = "channelId") public String channelId;
		@XmlAttribute(name = "value") public boolean value;
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
