package de.sgollmer.solvismax.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import de.sgollmer.solvismax.BaseData;
import de.sgollmer.solvismax.model.objects.unit.Unit;

/**
 * Charakterisierungstest fuer das Einlesen der {@code base.xml}.
 *
 * <p>
 * Zweck: Diese Tests pinnen das Ergebnis des <b>aktuellen</b> Konfig-Parsers
 * (die Eigenbibliothek {@code XMLLibrary} + die {@code CreatorByXML}-Klassen).
 * Sie sind das Sicherheitsnetz fuer die geplante Ablösung des XML-Bindings
 * durch JAXB (MODERNISIERUNG.md 3.3): Der neue Parser muss dieselben Werte
 * liefern, sonst schlagen diese Tests an.
 * </p>
 *
 * <p>
 * Geparst wird die mitgelieferte Vorlage
 * {@code rsc/de/sgollmer/solvismax/data/base.xml} (relativ zum Projekt-
 * verzeichnis, dem Arbeitsverzeichnis von Maven/Surefire). Die zugehoerige
 * {@code base.xsd} wird vom Reader als Klassenpfad-Ressource geladen.
 * </p>
 */
class BaseConfigParsingTest {

	private static final String TEMPLATE = "rsc/de/sgollmer/solvismax/data/base.xml";

	private BaseData parse() throws Exception {
		Assumptions.assumeTrue(new File(TEMPLATE).isFile(), "Vorlage fehlt: " + TEMPLATE);
		return new BaseControlFileReader(TEMPLATE).read();
	}

	@Test
	void vorlageWirdEingelesenUndValidiert() throws Exception {
		final BaseData baseData = parse();
		assertNotNull(baseData, "base.xml sollte einlesbar und XSD-valide sein");
	}

	@Test
	void ausfuehrungsdatenGelesen() throws Exception {
		final BaseData baseData = parse();
		assertEquals("Europe/Berlin", baseData.getTimeZone());
	}

	/**
	 * Charakterisiert die OS-Pfad-Weiche von {@code getWritablePath()} — bewusst
	 * hier statt im Golden-Snapshot (der plattformunabhängig bleiben muss und
	 * diesen Getter daher ausnimmt, siehe {@code ParseDiff}).
	 */
	@Test
	void writablePathFolgtOsWeiche() throws Exception {
		final BaseData baseData = parse();
		final boolean windows = System.getProperty("os.name").startsWith("Windows");
		assertEquals(windows ? "C:\\JavaPgms\\SolvisSmartHomeServer\\log" : "/opt/solvis",
				baseData.getWritablePath());
	}

	@Test
	void mqttAttributeGelesen() throws Exception {
		final BaseData baseData = parse();
		assertNotNull(baseData.getMqtt(), "Mqtt-Element sollte vorhanden sein");
		// Werte der Vorlage (Default-Konfiguration):
		assertFalse(baseData.getMqtt().isEnable(), "In der Vorlage ist Mqtt deaktiviert");
		assertEquals("SolvisSmartHomeServer", baseData.getMqtt().getTopicPrefix());
		assertEquals("IoBroker", baseData.getMqtt().getSmartHomeId());
	}

	@Test
	void einheitVorhanden() throws Exception {
		final BaseData baseData = parse();
		assertNotNull(baseData.getUnits(), "Units-Element sollte vorhanden sein");
	}

	@Test
	void ausfuehrungsdatenDetails() throws Exception {
		final BaseData baseData = parse();
		assertEquals(2000, baseData.getEchoInhibitTime_ms());
	}

	/**
	 * Tiefe Attribut-Werte der {@code <Unit>} — bewusst breit, damit die
	 * JAXB-Umstellung Attribut-Bindung UND Typkonvertierung (int/boolean)
	 * verhaltensidentisch reproduziert.
	 */
	@Test
	void einheitAttributeGelesen() throws Exception {
		final BaseData baseData = parse();
		final Collection<Unit> units = baseData.getUnits().getUnits();
		assertEquals(1, units.size(), "Die Vorlage enthaelt genau eine Unit");

		final Unit unit = units.iterator().next();
		assertEquals("mySolvis", unit.getId());
		assertEquals("aaa.bbb.ccc.ddd", unit.getUrl());
		assertEquals("account", unit.getAccount());
		assertEquals(12, unit.getDefaultAverageCount());
		assertEquals(4, unit.getMeasurementHysteresisFactor());
		assertEquals(30000, unit.getWatchDogTime_ms());
		assertEquals(3, unit.getIgnoredFrameThicknesScreenSaver());
		assertTrue(unit.isFwLth2_21_02A());
	}
}
