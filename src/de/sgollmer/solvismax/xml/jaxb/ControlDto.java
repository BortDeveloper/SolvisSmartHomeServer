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
}
