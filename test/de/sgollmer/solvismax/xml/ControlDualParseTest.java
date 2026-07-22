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
