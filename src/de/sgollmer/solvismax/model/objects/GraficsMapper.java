package de.sgollmer.solvismax.model.objects;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import de.sgollmer.solvismax.model.objects.screen.ScreenGraficData;
import de.sgollmer.solvismax.model.objects.unit.Feature;
import de.sgollmer.solvismax.xml.jaxb.grafics.GraficsDto;

/**
 * Abbildung DTO ↔ Domäne für die {@code graficData.xml} (MODERNISIERUNG.md
 * 3.3, vierter Baum). Bewusst im model.objects-Paket: die Grafik-Domäne ist
 * paketprivat verzahnt ({@code SystemGrafics}-Konstruktion, System-Liste),
 * daher lebt die Abbildung hier — analog zum {@code BackupMapper} im
 * backup-Paket.
 *
 * <p>
 * Die Bild-Codierung (Base64-PNG ↔ {@code MyImage}/{@code Pattern}) liegt in
 * {@link ScreenGraficData#of}/{@link ScreenGraficData#toBase64Png} — die
 * Semantik des alten Creators/Writers, nur als reguläre API der Klasse.
 * </p>
 */
public final class GraficsMapper {

	private GraficsMapper() {
	}

	/** Baut den Domänengraphen aus der eingelesenen Datei. */
	public static AllSolvisGrafics toDomain(final GraficsDto dto) throws IOException {
		final Collection<SystemGrafics> systems = new ArrayList<>();
		if (dto.system != null) {
			for (final GraficsDto.SystemDto systemDto : dto.system) {
				final SystemGrafics system = new SystemGrafics(systemDto.id);
				system.setConfigurationMask(systemDto.configurationMask);
				system.setBaseConfigurationMask(systemDto.baseConfigurationMask);
				if (systemDto.features != null && systemDto.features.feature != null) {
					for (final GraficsDto.FeatureDto feature : systemDto.features.feature) {
						system.add(new Feature(feature.id, feature.value));
					}
				}
				if (systemDto.screenGrafic != null) {
					for (final GraficsDto.ScreenGraficDto grafic : systemDto.screenGrafic) {
						system.putData(ScreenGraficData.of(grafic.id, grafic.base64Png, grafic.isPattern));
					}
				}
				systems.add(system);
			}
		}
		return AllSolvisGrafics.of(systems, dto.controlResourceHashCode, dto.controlFileHashCode);
	}

	/** Baut die Persistenzform aus dem Domänengraphen (fürs Schreiben). */
	public static GraficsDto toDto(final AllSolvisGrafics grafics) throws IOException {
		final GraficsDto dto = new GraficsDto();
		dto.controlResourceHashCode = grafics.getControlHashCodes().getResourceHash();
		dto.controlFileHashCode = grafics.getControlHashCodes().getFileHash();
		dto.system = new ArrayList<>();
		for (final SystemGrafics system : grafics.getSystems()) {
			final GraficsDto.SystemDto systemDto = new GraficsDto.SystemDto();
			systemDto.id = system.getId();
			systemDto.configurationMask = system.getConfigurationMask();
			systemDto.baseConfigurationMask = system.getBaseConfigurationMask();
			systemDto.features = new GraficsDto.FeaturesDto();
			systemDto.features.feature = new ArrayList<>();
			for (final Map.Entry<String, Boolean> entry : system.getFeatures().entrySet()) {
				final GraficsDto.FeatureDto feature = new GraficsDto.FeatureDto();
				feature.id = entry.getKey();
				feature.value = entry.getValue();
				systemDto.features.feature.add(feature);
			}
			systemDto.screenGrafic = new ArrayList<>();
			for (final ScreenGraficData grafic : system.getGraficDatas()) {
				final GraficsDto.ScreenGraficDto graficDto = new GraficsDto.ScreenGraficDto();
				graficDto.id = grafic.getId();
				graficDto.isPattern = grafic.isPattern();
				graficDto.base64Png = grafic.toBase64Png();
				systemDto.screenGrafic.add(graficDto);
			}
			dto.system.add(systemDto);
		}
		return dto;
	}
}
