package de.sgollmer.solvismax.model.objects;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.sgollmer.solvismax.imagepatternrecognition.image.MyImage;
import de.sgollmer.solvismax.model.objects.screen.ScreenGraficData;
import de.sgollmer.solvismax.model.objects.unit.Feature;
import de.sgollmer.solvismax.model.objects.unit.Features;

/**
 * Gelernte Grafik-Daten einer Solvis-Anlage (ein {@code <System>}-Eintrag der
 * {@code graficData.xml}) — inklusive der Konfigurations-Masken und des
 * Feature-Stands zum Lernzeitpunkt (über den entschieden wird, ob neu gelernt
 * werden muss). Persistiert wird über den JAXB-Pfad
 * ({@code GraficsMapper}/{@code JaxbGraficsReader}, MODERNISIERUNG.md 3.3).
 */
public class SystemGrafics {

	private final String id;
	private long configurationMask;
	private long baseConfigurationMask = 0;
	private final Map<String, ScreenGraficData> graficDatas;
	private final Map<String, Boolean> features;

	SystemGrafics(String id) {
		this.id = id;
		this.configurationMask = 0;
		this.baseConfigurationMask = 0;
		this.graficDatas = new HashMap<>();
		this.features = new HashMap<>();
	}

	String getId() {
		return this.id;
	}

	public void clear() {
		this.graficDatas.clear();
	}

	public ScreenGraficData get(final String id) {
		return this.graficDatas.get(id);
	}

	public ScreenGraficData remove(final String id) {
		return this.graficDatas.remove(id);
	}

	public void put(final String id, final MyImage image) {
		ScreenGraficData data = new ScreenGraficData(id, image);
		this.graficDatas.put(id, data);
	}

	/** Paketinterner Zugang für den {@code GraficsMapper} (fertig decodierte Grafik). */
	void putData(final ScreenGraficData data) {
		this.graficDatas.put(data.getId(), data);
	}

	/** Paketinterner Zugang für den {@code GraficsMapper} (Persistenz). */
	Collection<ScreenGraficData> getGraficDatas() {
		return this.graficDatas.values();
	}

	/** Paketinterner Zugang für den {@code GraficsMapper} (Persistenz). */
	Map<String, Boolean> getFeatures() {
		return this.features;
	}

	public boolean isEmpty() {
		return this.graficDatas.isEmpty();
	}

	public long getConfigurationMask() {
		return this.configurationMask;
	}

	public void setConfigurationMask(final long configurationMask) {
		this.configurationMask = configurationMask;
	}

	public long getBaseConfigurationMask() {
		return this.baseConfigurationMask;
	}

	public void setBaseConfigurationMask(final long baseConfigurationMask) {
		this.baseConfigurationMask = baseConfigurationMask;
	}

	public void add(final Feature feature) {
		this.features.put(feature.getId(), feature.isSet());

	}

	/**
	 * Vergleicht den gelernten Feature-Stand mit dem aktuell konfigurierten:
	 * Weichen lern-relevante Features ab, müssen die Grafiken neu gelernt
	 * werden. Sonderfall Admin: nur das Hinzukommen des Admin-Features erzwingt
	 * ein Neu-Lernen (zusätzliche Admin-Screens), sein Wegfall nicht.
	 */
	public boolean areRelevantFeaturesEqual(final Map<String, Boolean> features) {
		for (Map.Entry<String, Boolean> entry : this.features.entrySet()) {
			if (Features.getAdminKey().equals(entry.getKey())) {
				if (!entry.getValue() && features.get(Features.getAdminKey())) {
					return false;
				}
			} else {
				Boolean cmp = features.get(entry.getKey());
				cmp = cmp == null ? false : cmp;
				if (entry.getValue() != cmp) {
					return false;
				}
			}
		}
		return true;
	}

}
