package de.sgollmer.solvismax.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import de.sgollmer.solvismax.Main;
import de.sgollmer.solvismax.model.objects.AllDurations;
import de.sgollmer.solvismax.model.objects.Miscellaneous;
import de.sgollmer.solvismax.model.objects.SolvisDescription;
import de.sgollmer.solvismax.xml.jaxb.ControlDto;
import de.sgollmer.solvismax.xml.jaxb.JaxbControlReader;
import de.sgollmer.solvismax.xml.jaxb.Mapper;
import de.sgollmer.xmllibrary.XmlStreamReader;

/**
 * <b>Dual-Parse-Einstieg für {@code control.xml}</b> (MODERNISIERUNG.md 3.3,
 * dritter Baum): dieselbe Ressource wird mit dem bisherigen
 * {@code XMLLibrary}-Parser (voller Domänengraph) und mit dem wachsenden
 * JAXB-DTO gelesen; die modellierten Zweige müssen übereinstimmen. Der
 * DTO-Baum startet mit {@code Miscellaneous} (flach), {@code Durations} und
 * {@code ChannelAssignments} und wächst — wie seinerzeit bei base.xml —
 * inkrementell.
 */
class ControlDualParseTest {

	private static final String RESOURCE = "data/control.xml";

	private static SolvisDescription parseAlt() throws Exception {
		final InputStream source = Main.class.getResourceAsStream(RESOURCE);
		Assumptions.assumeTrue(source != null, "Ressource fehlt: " + RESOURCE);
		return new XmlStreamReader<SolvisDescription>()
				.read(source, "SolvisDescription", new SolvisDescription.Creator("SolvisDescription"), "control.xml")
				.getObject();
	}

	private static ControlDto parseNeu() throws Exception {
		final InputStream source = Main.class.getResourceAsStream(RESOURCE);
		Assumptions.assumeTrue(source != null, "Ressource fehlt: " + RESOURCE);
		return JaxbControlReader.read(source);
	}

	@Test
	void miscellaneousIdentisch() throws Exception {
		final SolvisDescription alt = parseAlt();
		final ControlDto neu = parseNeu();

		assertNotNull(neu.miscellaneous);
		final Miscellaneous ausDomaene = alt.getMiscellaneous();
		final Miscellaneous ausDto = Mapper.toMiscellaneous(neu.miscellaneous);

		assertEquals(ausDomaene.getMeasurementsBackupTime_ms(), ausDto.getMeasurementsBackupTime_ms());
		assertEquals(ausDomaene.getPowerOffDetectedAfterIoErrors(), ausDto.getPowerOffDetectedAfterIoErrors());
		assertEquals(ausDomaene.getPowerOffDetectedAfterTimeout_ms(), ausDto.getPowerOffDetectedAfterTimeout_ms());
		assertEquals(ausDomaene.getUnsuccessfullWaitTime_ms(), ausDto.getUnsuccessfullWaitTime_ms());
		assertEquals(ausDomaene.getConnectionHoldTime(), ausDto.getConnectionHoldTime());
		assertEquals(ausDomaene.getSolvisConnectionTimeout_ms(), ausDto.getSolvisConnectionTimeout_ms());
		assertEquals(ausDomaene.getSolvisReadTimeout_ms(), ausDto.getSolvisReadTimeout_ms());
		assertEquals(ausDomaene.getClientTimeoutTime_ms(), ausDto.getClientTimeoutTime_ms());
		// Charakterisierung einzelner Vorlagenwerte (fixiert die Bindung).
		assertEquals(3, ausDto.getPowerOffDetectedAfterIoErrors());
		assertEquals(3_600_000, ausDto.getMeasurementsBackupTime_ms());
	}

	@Test
	void durationsIdentisch() throws Exception {
		final SolvisDescription alt = parseAlt();
		final ControlDto neu = parseNeu();

		final AllDurations ausDto = Mapper.toDurations(neu.durations);
		for (final String id : new String[] { "Standard", "Long", "ValueChange", "ModeChange", "WindowChange",
				"WindowChangeService", "checkCalculation", "readCalculationInterval" }) {
			assertNotNull(alt.getDuration(id), "Domäne kennt Duration " + id);
			assertEquals(alt.getDuration(id).getTime_ms(), ausDto.get(id).getTime_ms(), "Duration " + id);
		}
		assertNull(ausDto.get("gibtEsNicht"));
		// Charakterisierung einzelner Vorlagenwerte.
		assertEquals(400, ausDto.get("Standard").getTime_ms());
		assertEquals(5000, ausDto.get("Long").getTime_ms());
	}

	/**
	 * {@code ScreenSaver}: Der Rücksetz-Touch-Punkt ist über die Domäne
	 * vergleichbar ({@code getResetScreenSaver().getCoordinate()}); die übrigen
	 * Werte (x-Koordinate der Uhr, maximale Grafikgröße, Duration-Referenzen)
	 * werden gegen die Vorlagenwerte charakterisiert, weil die Domäne sie nicht
	 * über Getter offenlegt.
	 */
	@Test
	void screenSaverIdentischBzwCharakterisiert() throws Exception {
		final SolvisDescription alt = parseAlt();
		final ControlDto neu = parseNeu();

		assertNotNull(neu.screenSaver);
		// Dual-Parse, wo die Domäne Getter bietet:
		assertEquals(alt.getSaver().getResetScreenSaver().getCoordinate().getX(),
				neu.screenSaver.resetScreenSaver.coordinate.x);
		assertEquals(alt.getSaver().getResetScreenSaver().getCoordinate().getY(),
				neu.screenSaver.resetScreenSaver.coordinate.y);
		// Charakterisierung der Vorlagenwerte:
		assertEquals(80, neu.screenSaver.xCoordinateWithinTimedate);
		assertEquals(150, neu.screenSaver.maxGraficSize.x);
		assertEquals(80, neu.screenSaver.maxGraficSize.y);
		assertEquals("Standard", neu.screenSaver.resetScreenSaver.pushTimeRefId);
		assertEquals("WindowChange", neu.screenSaver.resetScreenSaver.releaseTimeRefId);
	}

	/**
	 * {@code Standby} und {@code ErrorDetection}: die Domäne legt diese Werte
	 * nicht über Getter offen — Charakterisierung der Bindung gegen die
	 * Vorlagenwerte (wie seinerzeit ExceptionMail/Iobroker bei base.xml).
	 */
	@Test
	void standbyUndErrorDetectionGebunden() throws Exception {
		final ControlDto neu = parseNeu();

		assertNotNull(neu.standby);
		assertEquals(3, neu.standby.channel.size());
		assertEquals("C06", neu.standby.channel.get(0).id);
		assertEquals("Standby", neu.standby.channel.get(0).value);

		assertNotNull(neu.errorDetection);
		assertEquals(51, neu.errorDetection.leftBorder.lowerLimit);
		assertEquals(84, neu.errorDetection.leftBorder.higherLimit);
		assertEquals(938, neu.errorDetection.rightBorder.higherLimit);
		assertEquals(190, neu.errorDetection.hhMm.topLeft.x);
		assertEquals(62, neu.errorDetection.hhMm.topLeft.y);
		assertEquals(238, neu.errorDetection.ddMmYy.bottomRight.x);
		assertEquals(91, neu.errorDetection.ddMmYy.bottomRight.y);
		assertEquals("A14", neu.errorDetection.errorCondition.channelId);
		org.junit.jupiter.api.Assertions.assertTrue(neu.errorDetection.errorCondition.value);
	}

	/**
	 * {@code Configurations}: <b>Dual-Parse</b> der Konfigurationsmasken — die
	 * Domäne löst über {@code getConfiguration(id)} dieselben Bits auf, die das
	 * DTO kanonisch als Hex-String bindet (Ableitung {@code Long.decode} in
	 * {@code TypeDto.configurationValue}). Vergleich über alle fünf Typ-Gruppen
	 * anhand der Vorlagen-Ids.
	 */
	@Test
	void configurationsMaskenIdentisch() throws Exception {
		final SolvisDescription alt = parseAlt();
		final ControlDto neu = parseNeu();

		assertNotNull(neu.configurations);
		final var cfg = alt.getConfigurations();
		for (final String id : new String[] { "SolvisMax6", "SolvisMax6PurSolo", "SolvisMax7" }) {
			assertEquals(cfg.getSolvisTypes().getConfiguration(id),
					neu.configurations.solvisTypes.configuration(id), "SolvisType " + id);
		}
		for (final String id : new String[] { "OelBW", "OelNT", "Gas", "Fern", "WaermeP", "Extern" }) {
			assertEquals(cfg.getMainHeatings().getConfiguration(id),
					neu.configurations.mainHeatings.configuration(id), "MainHeating " + id);
		}
		for (final String id : new String[] { "1", "2", "3" }) {
			assertEquals(cfg.getHeaterCircuits().getConfiguration(id),
					neu.configurations.heaterCircuits.configuration(id), "HeaterCircuits " + id);
		}
		for (final String id : new String[] { "None", "Normal", "OstWest" }) {
			assertEquals(cfg.getSolarTypes().getConfiguration(id),
					neu.configurations.solarTypes.configuration(id), "SolarType " + id);
		}
		for (final String id : new String[] { "Festbrennstoff", "Zaehlfunktion2Screens", "SolarOstWest" }) {
			assertEquals(cfg.getExtensions().getConfiguration(id),
					neu.configurations.extensions.configuration(id), "Extension " + id);
		}
		// Charakterisierung: dontCare-Flag und NotValid-Kombinationen.
		assertEquals(Boolean.TRUE, neu.configurations.extensions.type.stream()
				.filter(t -> "SolarOstWest".equals(t.id)).findFirst().orElseThrow().dontCare);
		assertNotNull(neu.configurations.notValid);
		final var ersteNotValid = neu.configurations.notValid.configuration.get(0);
		assertEquals(Integer.valueOf(3), ersteNotValid.heatingCircuits);
		assertEquals("SolarOstWest", ersteNotValid.extensions.extension.get(0).id);
	}

	/**
	 * {@code FallBack}: Die Tastenfolge ist eine <b>geordnete Mischsequenz</b>
	 * aus {@code Back}/{@code ScreenRef} — der Test fixiert, dass die
	 * polymorphe {@code @XmlElements}-Bindung die Reihenfolge der Vorlage exakt
	 * erhält (4× Back; LastChance: 4× Back, ScreenRef "Warmwasser", 2× Back).
	 */
	@Test
	void fallBackReihenfolgeGebunden() throws Exception {
		final ControlDto neu = parseNeu();

		assertNotNull(neu.fallBack);
		assertEquals(4, neu.fallBack.step.size());
		org.junit.jupiter.api.Assertions.assertTrue(
				neu.fallBack.step.stream().allMatch(s -> s instanceof ControlDto.BackDto));

		assertNotNull(neu.fallBack.lastChance);
		assertEquals(7, neu.fallBack.lastChance.step.size());
		// Position 4 (0-basiert) ist der ScreenRef - die Reihenfolge zaehlt!
		final Object fuenfter = neu.fallBack.lastChance.step.get(4);
		org.junit.jupiter.api.Assertions.assertTrue(fuenfter instanceof ControlDto.ScreenRefDto);
		assertEquals("Warmwasser", ((ControlDto.ScreenRefDto) fuenfter).id);
	}

	/**
	 * {@code Preparations} und {@code ScreenGrafics}: Charakterisierung der
	 * Bindung gegen die Vorlage (die Domäne legt die Werte nicht über Getter
	 * offen). Die wiederverwendeten Beans (TouchPointDto, RectangleDto)
	 * tragen hier erneut.
	 */
	@Test
	void preparationsUndScreenGraficsGebunden() throws Exception {
		final ControlDto neu = parseNeu();

		assertNotNull(neu.preparations);
		final ControlDto.PreparationDto erste = neu.preparations.preparation.get(0);
		assertEquals("Button_HK1", erste.id);
		assertEquals("Standard", erste.touchPoint.pushTimeRefId);
		assertEquals(60, erste.touchPoint.coordinate.x);
		assertEquals(15, erste.touchPoint.coordinate.y);
		assertEquals("Button_HK1", erste.screenGrafic.id);
		assertEquals(47, erste.screenGrafic.rectangle.topLeft.x);
		assertEquals(76, erste.screenGrafic.rectangle.bottomRight.x);

		assertNotNull(neu.screenGrafics);
		org.junit.jupiter.api.Assertions.assertTrue(neu.screenGrafics.screenGrafic.size() >= 3);
		final ControlDto.ScreenGraficDescriptionDto sonstiges = neu.screenGrafics.screenGrafic.stream()
				.filter(g -> "Sonstiges".equals(g.id)).findFirst().orElseThrow();
		assertEquals(Boolean.TRUE, sonstiges.exact);
		assertEquals(108, sonstiges.rectangle.topLeft.y);
		// "Heizkreis": weder exact-Attribut noch Rectangle -> null/null.
		final ControlDto.ScreenGraficDescriptionDto heizkreis = neu.screenGrafics.screenGrafic.stream()
				.filter(g -> "Heizkreis".equals(g.id)).findFirst().orElseThrow();
		assertNull(heizkreis.exact);
		assertNull(heizkreis.rectangle);
	}

	/**
	 * {@code ChannelAssignments}: das Domänen-Aggregat ({@code
	 * AllChannelAssignments}) hängt an der OfConfigs-Maschinerie; hier wird
	 * zunächst die <b>Bindung</b> charakterisiert (Id → SmartHome-Name).
	 */
	@Test
	void channelAssignmentsGebunden() throws Exception {
		final ControlDto neu = parseNeu();

		assertNotNull(neu.channelAssignments);
		assertNotNull(neu.channelAssignments.assignment);
		org.junit.jupiter.api.Assertions.assertTrue(neu.channelAssignments.assignment.size() >= 10,
				"Vorlage enthält viele Assignments");
		final var erste = neu.channelAssignments.assignment.stream()
				.filter(a -> "I1".equals(a.id))
				.findFirst().orElseThrow();
		assertEquals("I1.Anlagentyp", erste.name);
	}
}
