package de.sgollmer.solvismax.xml.jaxb;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Überführt die kanonisch von JAXB gebundenen {@link BaseDataDto DTO-Strukturen}
 * in die von der Anwendung genutzten <b>abgeleiteten Sichten</b> — die zweite
 * Schicht der JAXB-Umstellung (MODERNISIERUNG.md 3.3).
 *
 * <h2>Warum es diese Schicht gibt (Design-Grundsatz)</h2>
 * Der bisherige {@code XMLLibrary}-Parser hat <b>zwei Dinge vermischt</b>: das
 * Binden der XML-Struktur <em>und</em> deren Transformation in domänennähere
 * Formen (z. B. die {@code <Feature>}-Liste in eine {@code Map id -> boolean},
 * das {@code passwordCrypt}-Attribut in einen entschlüsselten Wert, den
 * Recipient-{@code type}-String in ein Enum).
 *
 * <p>
 * Der standardnahe, nachhaltige Weg trennt beides sauber:
 * </p>
 * <ol>
 * <li><b>JAXB bindet die rohe XML-Gestalt kanonisch</b> — wiederholte Elemente
 * werden zu {@code List}, Attribute zu Feldern (siehe {@link BaseDataDto}). Kein
 * Custom-Adapter, kein in die Bindung gezwungenes Domänenmodell.</li>
 * <li><b>Dieser Mapper leitet die Domänen-Sichten explizit ab</b> — in kleinen,
 * für sich testbaren Schritten. Die Transformationslogik ist damit sichtbar und
 * prüfbar, statt im Parser verborgen.</li>
 * </ol>
 *
 * <p>
 * Der Dual-Parse-Differenztest sichert ab, dass die abgeleiteten Sichten mit dem
 * Ergebnis des alten Parsers identisch sind.
 * </p>
 */
public final class Mapper {

	private Mapper() {
	}

	/**
	 * Leitet aus der kanonisch gebundenen {@code <Features>}-Liste die von der
	 * Domäne genutzte Index-Sicht {@code id -> Wert} ab.
	 *
	 * <p>
	 * Beispiel für den Grundsatz oben: JAXB liefert eine
	 * {@code List<FeatureDto>} (id/value-Paare) — die XML-Gestalt. Die
	 * {@code Map<String, Boolean>}, mit der die Anwendung ein Feature nachschlägt,
	 * ist eine <em>abgeleitete</em> Sicht und entsteht hier, nicht im Parser.
	 * </p>
	 *
	 * <p>
	 * Reihenfolge-erhaltend ({@link LinkedHashMap}) und null-tolerant (fehlt das
	 * {@code <Features>}-Element oder ist es leer, ergibt sich eine leere Map).
	 * Bei doppelten Feature-Ids gewinnt — wie beim Aufbau einer Map üblich — der
	 * letzte Eintrag.
	 * </p>
	 *
	 * @param features die von JAXB gebundene Features-Struktur (darf {@code null}
	 *                 sein)
	 * @return unveränderliche Zuordnung Feature-Id → Wert
	 */
	public static Map<String, Boolean> featuresToMap(final BaseDataDto.FeaturesDto features) {
		final Map<String, Boolean> map = new LinkedHashMap<>();
		if (features != null && features.feature != null) {
			for (final BaseDataDto.FeatureDto feature : features.feature) {
				map.put(feature.id, feature.value);
			}
		}
		return java.util.Collections.unmodifiableMap(map);
	}
}
