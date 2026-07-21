package de.sgollmer.solvismax.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import de.sgollmer.solvismax.BaseData;
import de.sgollmer.solvismax.model.objects.unit.Unit;
import de.sgollmer.solvismax.xml.jaxb.BaseDataDto;
import de.sgollmer.solvismax.xml.jaxb.JaxbBaseReader;

/**
 * <b>Dual-Parse-Differenztest</b> für {@code base.xml}: dieselbe Datei wird mit
 * dem bisherigen {@code XMLLibrary}-Parser (Domänengraph) <b>und</b> mit dem
 * neuen JAXB-Parser (DTO-Graph) eingelesen; die Werte müssen übereinstimmen.
 *
 * <p>
 * Dies ist der Nachweis, dass die JAXB-Umstellung <b>verhaltensidentisch</b>
 * parst. Der DTO-Baum deckt zunächst die Kernstruktur ab (ExecutionData, Units/
 * Unit/Features, Mqtt/Ssl) und wächst schrittweise; jedes hinzugenommene Feld
 * wird hier gegen den alten Parser abgesichert.
 * </p>
 */
class DualParseBaseTest {

	private static final String TEMPLATE = "rsc/de/sgollmer/solvismax/data/base.xml";

	private void assumeTemplate() {
		Assumptions.assumeTrue(new File(TEMPLATE).isFile(), "Vorlage fehlt: " + TEMPLATE);
	}

	@Test
	void ausfuehrungsdatenUndMqttIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		// ExecutionData
		assertNotNull(neu.executionData);
		assertEquals(alt.getTimeZone(), neu.executionData.timeZone);
		assertEquals(alt.getEchoInhibitTime_ms(), neu.executionData.echoInhibitTime_ms);

		// Mqtt (+ Ssl-Element, in der Vorlage nicht vorhanden -> null in beiden)
		assertNotNull(neu.mqtt);
		assertEquals(alt.getMqtt().isEnable(), neu.mqtt.enable);
		assertEquals(alt.getMqtt().getTopicPrefix(), neu.mqtt.topicPrefix);
		assertEquals(alt.getMqtt().getSmartHomeId(), neu.mqtt.smartHomeId);
	}

	@Test
	void unitKernattributeIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final Unit u = alt.getUnits().getUnits().iterator().next();
		assertNotNull(neu.units);
		assertEquals(1, neu.units.unit.size());
		final BaseDataDto.UnitDto ud = neu.units.unit.get(0);

		assertEquals(u.getId(), ud.id);
		assertEquals(u.getUrl(), ud.url);
		assertEquals(u.getAccount(), ud.account);
		assertEquals(u.getDefaultAverageCount(), ud.defaultAverageCount);
		assertEquals(u.getMeasurementHysteresisFactor(), ud.measurementHysteresisFactor);
		assertEquals(u.getWatchDogTime_ms(), ud.watchDogTime_ms);
		assertEquals(u.isFwLth2_21_02A(), ud.fwLth2_21_02A);
		assertEquals(u.getIgnoredFrameThicknesScreenSaver(), ud.ignoredFrameThicknesScreenSaver);
	}

	@Test
	void featuresIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final Unit u = alt.getUnits().getUnits().iterator().next();
		final BaseDataDto.UnitDto ud = neu.units.unit.get(0);
		assertNotNull(ud.features);
		assertNotNull(ud.features.feature);

		// Die Domäne wandelt die Feature-Liste in eine Map (id -> boolean). Wir
		// vergleichen daher konkrete Feature-Werte: In der Vorlage ist genau
		// "SendMailOnErrorsCleared" true, "InteractiveGUIAccess" false.
		assertEquals(u.getFeatures().get("SendMailOnErrorsCleared", false),
				dtoFeature(ud, "SendMailOnErrorsCleared"));
		assertEquals(u.getFeatures().get("InteractiveGUIAccess", true),
				dtoFeature(ud, "InteractiveGUIAccess"));
	}

	private static boolean dtoFeature(final BaseDataDto.UnitDto unit, final String id) {
		return unit.features.feature.stream()
				.filter(f -> id.equals(f.id))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Feature fehlt im DTO: " + id))
				.value;
	}

	/**
	 * Prüft den {@link de.sgollmer.solvismax.xml.jaxb.Mapper}: Die aus der
	 * kanonischen Feature-Liste abgeleitete Map muss für <b>jedes</b> Feature
	 * denselben Wert liefern wie die Domäne (die dieselbe Ableitung noch im
	 * Parser vornimmt). Das sichert das Prinzip „kanonisch binden + explizit
	 * ableiten" verhaltensidentisch ab.
	 */
	@Test
	void featureMapAbleitungIdentischZuDomaene() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final Unit u = alt.getUnits().getUnits().iterator().next();
		final Map<String, Boolean> abgeleitet =
				de.sgollmer.solvismax.xml.jaxb.Mapper.featuresToMap(neu.units.unit.get(0).features);

		assertFalse(abgeleitet.isEmpty(), "Die Vorlage enthält Features");
		// Jeder abgeleitete Feature-Wert muss dem der Domäne entsprechen.
		for (final Map.Entry<String, Boolean> e : abgeleitet.entrySet()) {
			assertEquals(u.getFeatures().get(e.getKey(), !e.getValue()), e.getValue(),
					"Feature-Wert weicht ab: " + e.getKey());
		}
	}
}
