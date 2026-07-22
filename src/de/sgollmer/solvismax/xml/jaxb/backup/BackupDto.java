package de.sgollmer.solvismax.xml.jaxb.backup;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * JAXB-DTO für die Mess-/Backup-Datei {@code measurements.xml}
 * (MODERNISIERUNG.md 3.3 — zweiter umgestellter Baum).
 *
 * <p>
 * Zwei Wurzel-/Element-Generationen: aktuell {@code <SolvisBackup>}/
 * {@code <SystemBackup>}, legacy {@code <SolvisMeasurements>}/
 * {@code <SystemMeasurements>}. Gelesen wird per {@code declaredType}
 * (Wurzelname egal), beide Kind-Formen sind gebunden; geschrieben wird immer
 * die aktuelle Form (Wurzelname aus {@code @XmlRootElement}) — wie beim alten
 * Writer.
 * </p>
 */
@XmlRootElement(name = "SolvisBackup")
@XmlAccessorType(XmlAccessType.FIELD)
public class BackupDto {

	@XmlElement(name = "SystemBackup")
	public List<SystemBackupDto> systemBackup;

	/** Legacy-Form (nur lesend, wird nie geschrieben). */
	@XmlElement(name = "SystemMeasurements")
	public List<SystemBackupDto> systemMeasurements;

	/** Beide Generationen in Dokumentgruppen-Reihenfolge (aktuell, dann legacy). */
	public List<SystemBackupDto> alle() {
		final List<SystemBackupDto> alle = new ArrayList<>();
		if (this.systemBackup != null) {
			alle.addAll(this.systemBackup);
		}
		if (this.systemMeasurements != null) {
			alle.addAll(this.systemMeasurements);
		}
		return alle;
	}

	/** JAXB-Bean für {@code <SystemBackup id="...">} bzw. {@code <SystemMeasurements>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class SystemBackupDto {
		@XmlAttribute(name = "id") public String id;

		@XmlElement(name = "Measurement")
		public List<MeasurementDto> measurement;
	}

	/**
	 * JAXB-Bean für {@code <Measurement id="...">} mit genau einem
	 * Wert-Kindelement (XSD-choice). Die Werte sind als Roh-Strings gebunden;
	 * das Parsen (Boolean/Integer/Mode/String, Zeitstempel −1) liegt — wie
	 * beim alten {@code ValueCreator} — in der Domänen-Abbildung.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class MeasurementDto {
		@XmlAttribute(name = "id") public String id;

		@XmlElement(name = "BooleanValue") public String booleanValue;
		@XmlElement(name = "IntegerValue") public String integerValue;
		@XmlElement(name = "StringValue") public String stringValue;
		@XmlElement(name = "ModeValue") public String modeValue;
	}
}
