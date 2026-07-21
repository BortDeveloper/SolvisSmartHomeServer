package de.sgollmer.solvismax.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import de.sgollmer.solvismax.BaseData;

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
}
