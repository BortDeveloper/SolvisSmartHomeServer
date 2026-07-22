package de.sgollmer.solvismax.model.objects.backup;

import java.util.ArrayList;

import de.sgollmer.solvismax.Constants;
import de.sgollmer.solvismax.model.objects.backup.SystemBackup.IValue;
import de.sgollmer.solvismax.model.objects.data.BooleanValue;
import de.sgollmer.solvismax.model.objects.data.IntegerValue;
import de.sgollmer.solvismax.model.objects.data.ModeValue;
import de.sgollmer.solvismax.model.objects.data.SingleData;
import de.sgollmer.solvismax.model.objects.data.StringData;
import de.sgollmer.solvismax.xml.jaxb.backup.BackupDto;

/**
 * Abbildung DTO ↔ Domäne für die {@code measurements.xml} (MODERNISIERUNG.md
 * 3.3). Bewusst im backup-Paket: die Backup-Domäne ist paketprivat verzahnt
 * (Reference auf den Backup-Zeitstempel, {@code get(id)}-Anlage), daher lebt
 * die Abbildung hier statt im generischen {@code Mapper}.
 *
 * <p>
 * Wert-Semantik wie der alte {@code ValueCreator}: Roh-String → typisierter
 * {@link SingleData} mit Zeitstempel −1; beim Schreiben bestimmt
 * {@code SingleData.getXmlId()} das Wert-Element und {@code toString()} den
 * Text — identisch zum alten {@code XMLStreamWriter}-Pfad.
 * </p>
 */
final class BackupMapper {

	private BackupMapper() {
	}

	/** Ersetzt den Inhalt von {@code target} durch den der DTO-Datei (wie der alte Creator: clear + fill). */
	static void merge(final BackupDto dto, final AllSystemBackups target) {
		target.getSystemBackups().clear();
		for (final BackupDto.SystemBackupDto systemDto : dto.alle()) {
			final SystemBackup system = target.get(systemDto.id);
			if (systemDto.measurement != null) {
				for (final BackupDto.MeasurementDto measurement : systemDto.measurement) {
					final SingleData<?> data = toValue(measurement);
					if (data != null) {
						system.add(new Measurement(measurement.id, data));
					}
				}
			}
		}
	}

	static SingleData<?> toValue(final BackupDto.MeasurementDto dto) {
		if (dto.booleanValue != null) {
			return new BooleanValue(Boolean.parseBoolean(dto.booleanValue), -1L);
		}
		if (dto.integerValue != null) {
			return new IntegerValue(Integer.parseInt(dto.integerValue), -1L);
		}
		if (dto.modeValue != null) {
			return new ModeValue<>(new Measurement.Mode(dto.modeValue), -1L);
		}
		if (dto.stringValue != null) {
			return new StringData(dto.stringValue, -1L);
		}
		return null;
	}

	static BackupDto toDto(final AllSystemBackups source) {
		final BackupDto dto = new BackupDto();
		dto.systemBackup = new ArrayList<>();
		for (final SystemBackup system : source.getSystemBackups()) {
			final BackupDto.SystemBackupDto systemDto = new BackupDto.SystemBackupDto();
			systemDto.id = system.getId();
			systemDto.measurement = new ArrayList<>();
			for (final IValue value : system.getValues()) {
				if (value instanceof Measurement) {
					systemDto.measurement.add(toDto((Measurement) value));
				}
			}
			dto.systemBackup.add(systemDto);
		}
		return dto;
	}

	static BackupDto.MeasurementDto toDto(final Measurement measurement) {
		final BackupDto.MeasurementDto dto = new BackupDto.MeasurementDto();
		dto.id = measurement.getId();
		final SingleData<?> data = measurement.getData();
		final String text = data.toString();
		switch (data.getXmlId()) {
			case Constants.XmlStrings.XML_MEASUREMENT_BOOLEAN:
				dto.booleanValue = text;
				break;
			case Constants.XmlStrings.XML_MEASUREMENT_INTEGER:
				dto.integerValue = text;
				break;
			case Constants.XmlStrings.XML_MEASUREMENT_MODE:
				dto.modeValue = text;
				break;
			case Constants.XmlStrings.XML_MEASUREMENT_STRING:
			default:
				dto.stringValue = text;
				break;
		}
		return dto;
	}
}
