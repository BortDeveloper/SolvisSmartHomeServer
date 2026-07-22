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
	 * {@code Configurations}-Erkennungs-Unterzweige {@code HeaterLoops} und
	 * {@code Solar}: die Domäne hält die Werte in privaten Feldern hinter dem
	 * {@code IConfiguration}-Interface (nur {@code getConfiguration(Solvis)})
	 * — Charakterisierung der Bindung gegen die Vorlagenwerte.
	 */
	@Test
	void configurationsErkennungsZweigeGebunden() throws Exception {
		final ControlDto neu = parseNeu();

		final ControlDto.HeaterLoopsDto loops = neu.configurations.heaterLoops;
		assertNotNull(loops);
		assertEquals("Home", loops.screenRef);
		assertEquals(55, loops.hk1Button.topLeft.x);
		assertEquals(10, loops.hk1Button.topLeft.y);
		assertEquals(65, loops.hk1Button.bottomRight.x);
		assertEquals(23, loops.hk1Button.bottomRight.y);
		assertEquals(40, loops.hk2Button.topLeft.y);
		assertEquals(53, loops.hk2Button.bottomRight.y);
		assertEquals(70, loops.hk3Button.topLeft.y);
		assertEquals(83, loops.hk3Button.bottomRight.y);

		final ControlDto.SolarDto solar = neu.configurations.solar;
		assertNotNull(solar);
		assertEquals("Solar", solar.screenRef);
		assertEquals(2499, solar.maxTemperatureX10);
		assertEquals("^([+-]{0,1}\\d+).(\\d)..$", solar.format);
		assertEquals(170, solar.returnTemperature.topLeft.x);
		assertEquals(30, solar.returnTemperature.topLeft.y);
		assertEquals(235, solar.returnTemperature.bottomRight.x);
		assertEquals(40, solar.returnTemperature.bottomRight.y);
		assertEquals(45, solar.outgoingTemperature.topLeft.y);
		assertEquals(55, solar.outgoingTemperature.bottomRight.y);
	}

	/**
	 * {@code Clock}: die Uhr-Stell-Maschinerie ({@code ClockMonitor}) hält
	 * alle Werte in privaten Feldern — Charakterisierung der Bindung gegen
	 * die Vorlage. Fixiert außerdem, dass das {@code least}-Attribut (nur am
	 * {@code Year}) mitgebunden wird, obwohl der alte Parser es ignoriert
	 * (leeres {@code setAttribute} in {@code DatePart.Creator}).
	 */
	@Test
	void clockGebunden() throws Exception {
		final ControlDto neu = parseNeu();

		final ControlDto.ClockDto clock = neu.clock;
		assertNotNull(clock);
		assertEquals("X06", clock.timeChannelId);
		assertEquals("Zeiteinstellung", clock.screenId);
		assertEquals("Uhrzeit/Datum", clock.okScreenId);

		// Datums-Teile: Rechteck + Touch + Erkennungs-Grafik (Stichproben).
		assertEquals(Integer.valueOf(2008), clock.year.least);
		assertEquals(101, clock.year.rectangle.topLeft.x);
		assertEquals(76, clock.year.rectangle.bottomRight.y);
		assertEquals("Standard", clock.year.touch.pushTimeRefId);
		assertEquals("WindowChange", clock.year.touch.releaseTimeRefId);
		assertEquals(125, clock.year.touch.coordinate.x);
		assertEquals("Zeiteinstellung_YYYY", clock.year.screenGrafic.id);
		assertEquals(96, clock.year.screenGrafic.rectangle.topLeft.x);
		assertNull(clock.month.least);
		assertEquals("Zeiteinstellung_MM", clock.month.screenGrafic.id);
		assertEquals("Zeiteinstellung_DD", clock.day.screenGrafic.id);
		assertEquals("Zeiteinstellung_hh", clock.hour.screenGrafic.id);
		assertEquals("Zeiteinstellung_min", clock.minute.screenGrafic.id);
		assertEquals(101, clock.minute.rectangle.topLeft.x);
		assertEquals(51, clock.minute.rectangle.bottomRight.y);

		// Stell-Tasten: die drei Touch-Punkte samt Duration-Referenzen.
		assertEquals(185, clock.upper.coordinate.x);
		assertEquals(40, clock.upper.coordinate.y);
		assertEquals("WindowChange", clock.upper.releaseTimeRefId);
		assertEquals(100, clock.lower.coordinate.y);
		assertEquals("ValueChange", clock.lower.releaseTimeRefId);
		assertEquals(70, clock.ok.coordinate.y);
		assertEquals("WindowChange", clock.ok.releaseTimeRefId);

		assertEquals("A12", clock.disableClockSetting.burnerId);
		assertEquals("A02", clock.disableClockSetting.hotWaterPumpId);
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
	 * {@code Screens} — <b>Dual-Parse des Grundgerüsts</b>: homeId, die
	 * OfConfigs-Gruppierung (jede Id muss in der Domäne existieren und exakt
	 * gleich viele Konfigurations-Varianten haben wie das DTO
	 * {@code Screen}-Vorkommen zählt) und — wo die Anwahl-Strategie ein
	 * Touch-Punkt ist und die Id eindeutig — die Koordinaten des
	 * Select-TouchPoints gegen {@code getSelectScreenStrategy()}.
	 */
	@Test
	void screensDualParse() throws Exception {
		final SolvisDescription alt = parseAlt();
		final ControlDto neu = parseNeu();

		final ControlDto.ScreensDto screens = neu.screens;
		assertNotNull(screens);
		assertEquals(alt.getScreens().getHomeId(), screens.homeId);
		// Vorlage: 73 Screens + 1 ScreenSequence, Reihenfolge erhalten.
		assertEquals(74, screens.screen.size());

		final java.util.Map<String, Integer> anzahlJeId = new java.util.LinkedHashMap<>();
		for (final Object o : screens.screen) {
			final String id = o instanceof ControlDto.ScreenDto ? ((ControlDto.ScreenDto) o).id
					: ((ControlDto.ScreenSequenceDto) o).id;
			anzahlJeId.merge(id, 1, Integer::sum);
		}
		for (final var eintrag : anzahlJeId.entrySet()) {
			final var ofConfigs = alt.getScreens().get(eintrag.getKey());
			assertNotNull(ofConfigs, "Domäne kennt Screen-Id " + eintrag.getKey());
			assertEquals(ofConfigs.getElements().size(), eintrag.getValue(),
					"Varianten-Anzahl von " + eintrag.getKey());
		}

		// Select-TouchPoint-Koordinaten dual-parse (eindeutige Ids, Touch-Strategie):
		int verglichen = 0;
		for (final var eintrag : anzahlJeId.entrySet()) {
			if (eintrag.getValue() != 1) {
				continue;
			}
			final var ausDomaene = alt.getScreens().get(eintrag.getKey()).getIfSingle();
			final java.util.List<ControlDto.ScreenDto> ausDto = neu.screens.screens(eintrag.getKey());
			if (ausDto.size() != 1 || ausDto.get(0).touchPoint == null) {
				continue;
			}
			final var strategie = ausDomaene.getSelectScreenStrategy();
			if (strategie instanceof de.sgollmer.solvismax.model.objects.TouchPointStrategy) {
				final var koordinate = ((de.sgollmer.solvismax.model.objects.TouchPointStrategy) strategie)
						.getTouchPoint().getCoordinate();
				assertEquals(koordinate.getX(), ausDto.get(0).touchPoint.coordinate.x,
						"TouchPoint-X von " + eintrag.getKey());
				assertEquals(koordinate.getY(), ausDto.get(0).touchPoint.coordinate.y,
						"TouchPoint-Y von " + eintrag.getKey());
				++verglichen;
			}
		}
		org.junit.jupiter.api.Assertions.assertTrue(verglichen >= 50,
				"genügend TouchPoints dual-parse-verglichen: " + verglichen);
	}

	/**
	 * {@code Screens} — Charakterisierung der Bindung einzelner Vorlagen-
	 * Screens: leere Navigations-Ids (Home), Reihenfolge der polymorphen
	 * Identifications-Teile, Ocr-Identifikation, Konfigurations-Masken samt
	 * Mehrfach-Varianten derselben Id, {@code MustBeWhite} mit
	 * {@code invertFunction} und die Preparation-Verweise.
	 */
	@Test
	void screensCharakterisiert() throws Exception {
		final ControlDto neu = parseNeu();

		// Home: leere backId/previousId — kanonisch "", Sicht null; 2 Identifications.
		final ControlDto.ScreenDto home = neu.screens.screens("Home").get(0);
		assertEquals("", home.backId);
		assertNull(home.backIdOrNull());
		assertNull(home.previousIdOrNull());
		assertEquals(2, home.identification.size());
		final var home1 = (ControlDto.ScreenGraficDescriptionDto) home.identification.get(0).part.get(0);
		assertEquals("Home1", home1.id);
		assertEquals(Boolean.TRUE, home1.exact);
		assertEquals(2, home.identification.get(1).part.size());
		assertEquals(2, home.ignoreRectangle.size());
		assertEquals(190, home.ignoreRectangle.get(0).topLeft.x);

		// Sonstiges-1: Reihenfolge GraficRef vor Grafic bleibt erhalten.
		final ControlDto.ScreenDto sonstiges = neu.screens.screens("Sonstiges-1").get(0);
		final var teile = sonstiges.identification.get(0).part;
		assertEquals("Sonstiges", ((ControlDto.GraficRefDto) teile.get(0)).refId);
		assertEquals("Sonstiges-1", ((ControlDto.ScreenGraficDescriptionDto) teile.get(1)).id);

		// Heizkreis-1-1_5: sortId, ignoreChanges, Ocr-Identifikation.
		final ControlDto.ScreenDto heizkreis = neu.screens.screens("Heizkreis-1-1_5").get(0);
		assertEquals("Heizkreis-05-1_1", heizkreis.sortId);
		org.junit.jupiter.api.Assertions.assertTrue(heizkreis.ignoreChanges);
		final var ocr = (ControlDto.OcrDto) heizkreis.identification.get(0).part.get(0);
		assertEquals("11/5", ocr.value);
		org.junit.jupiter.api.Assertions.assertTrue(ocr.right);
		assertEquals(2, ocr.maxPixelsOfEmptyLine);
		assertEquals(5, ocr.rectangle.topLeft.x);
		assertEquals("Heizkreis", ocr.screenGraficRef.refId);

		// Tagestemperatur_HK1, 2. Variante: 2 Masken (Hex-Ableitung),
		// Preparation-Verweise, noRestore (1. Variante hat nur 1 Maske).
		final ControlDto.ScreenDto tagesTemp = neu.screens.screens("Tagestemperatur_HK1").get(1);
		org.junit.jupiter.api.Assertions.assertTrue(tagesTemp.noRestore);
		assertEquals(2, tagesTemp.configuration.configurationMask.size());
		assertEquals(0x02L, tagesTemp.configuration.configurationMask.get(0).andMaskValue());
		assertEquals(0x04L, tagesTemp.configuration.configurationMask.get(1).compareMaskValue());
		assertEquals("Button_HK1", tagesTemp.preparationRef.refId);
		assertEquals("Button_HK1", tagesTemp.lastPreparationRef.refId);

		// Anlagenstatus-WW: zwei Konfigurations-Varianten derselben Id.
		final java.util.List<ControlDto.ScreenDto> statusWw = neu.screens.screens("Anlagenstatus-WW");
		assertEquals(2, statusWw.size());
		assertEquals(0x01000000L, statusWw.get(0).configuration.configurationMask.get(0).compareMaskValue());
		assertEquals(2, statusWw.get(1).configuration.configurationMask.size());
		assertEquals(0x04000000L, statusWw.get(1).configuration.configurationMask.get(1).compareMaskValue());
		assertEquals(15, statusWw.get(0).sequenceUp.coordinate.x);
		assertEquals(230, statusWw.get(0).sequenceDown.coordinate.x);

		// Anlagenstatus-HK: MustBeWhite-Paar, zweites mit invertFunction.
		final ControlDto.ScreenDto statusHk = neu.screens.screens("Anlagenstatus-HK").get(0);
		final var hkTeile = statusHk.identification.get(0).part;
		assertEquals(3, hkTeile.size());
		final var weiss = (ControlDto.RectangleDto) hkTeile.get(1);
		org.junit.jupiter.api.Assertions.assertFalse(weiss.invertFunction);
		final var invertiert = (ControlDto.RectangleDto) hkTeile.get(2);
		org.junit.jupiter.api.Assertions.assertTrue(invertiert.invertFunction);
		assertEquals(45, invertiert.topLeft.x);
		assertEquals(55, invertiert.bottomRight.x);
	}

	/**
	 * {@code ScreenSequence} und {@code UserSelection}: die Blätter-Gruppe
	 * Anlagenstatus (geordnete ScreenRefs) und die Code-Eingabe-Strategie des
	 * Installateur-Menüs (admin-Konfiguration, vier Ziffern mit
	 * Hoch-/Runter-Touch-Punkten).
	 */
	@Test
	void screenSequenceUndUserSelectionGebunden() throws Exception {
		final ControlDto neu = parseNeu();

		final ControlDto.ScreenSequenceDto sequenz = (ControlDto.ScreenSequenceDto) neu.screens.screen.stream()
				.filter(s -> s instanceof ControlDto.ScreenSequenceDto).findFirst().orElseThrow();
		assertEquals("Anlagenstatus", sequenz.id);
		assertEquals("Sonstiges-1", sequenz.previousId);
		assertNull(sequenz.wrapArround);
		assertEquals(220, sequenz.touchPoint.coordinate.x);
		assertEquals(3, sequenz.screenRef.size());
		assertEquals("Anlagenstatus-Solar", sequenz.screenRef.get(0).id);
		assertEquals("Anlagenstatus-WW", sequenz.screenRef.get(1).id);
		assertEquals("Anlagenstatus-HK", sequenz.screenRef.get(2).id);

		final ControlDto.ScreenDto menue = neu.screens.screens("Installateur-Menue").get(0);
		org.junit.jupiter.api.Assertions.assertTrue(menue.service);
		assertEquals("ADMIN", menue.configuration.admin);
		assertNull(menue.configuration.configurationMask);
		assertNull(menue.touchPoint);
		final ControlDto.UserSelectionDto auswahl = menue.userSelection;
		assertNotNull(auswahl);
		assertEquals("WindowChange", auswahl.waitTimeAfterLastDigitRefId);
		assertEquals(4, auswahl.digit.size());
		assertEquals(0, auswahl.digit.get(0).digit);
		assertEquals(6, auswahl.digit.get(2).digit);
		assertEquals(4, auswahl.digit.get(3).digit);
		assertEquals(53, auswahl.digit.get(0).rectangle.topLeft.x);
		assertEquals(65, auswahl.digit.get(0).upper.coordinate.x);
		assertEquals("ValueChange", auswahl.digit.get(0).lower.releaseTimeRefId);
	}

	/**
	 * {@code ChannelDescriptions} — <b>Dual-Parse</b> des letzten Zweigs: die
	 * OfConfigs-Gruppierung (Id-Menge und Varianten-Anzahl identisch) und für
	 * alle eindeutigen Ids die flachen Kanal-Eigenschaften über die
	 * öffentlichen Domänen-Getter: unit/buffered/glitchInhibit, der
	 * Quelltyp (Control/Measurement/Calculation als XSD-choice), Divisor,
	 * Schreibbarkeit, average/fast, die Modus-Namen in Dokumentreihenfolge
	 * und die abgeleiteten {@code UpperLowerStep}-Grenzen der
	 * ReadWrite-Strategie.
	 */
	@Test
	void channelDescriptionsDualParse() throws Exception {
		final SolvisDescription alt = parseAlt();
		final ControlDto neu = parseNeu();

		final ControlDto.ChannelDescriptionsDto kanaele = neu.channelDescriptions;
		assertNotNull(kanaele);

		// OfConfigs-Gruppierung: gleiche Id-Menge, gleiche Varianten-Anzahl.
		final java.util.Map<String, Integer> anzahlJeId = new java.util.LinkedHashMap<>();
		for (final ControlDto.ChannelDescriptionDto d : kanaele.channelDescription) {
			anzahlJeId.merge(d.id, 1, Integer::sum);
		}
		assertEquals(alt.getChannelDescriptions().get().size(), anzahlJeId.size(), "Anzahl Kanal-Ids");
		for (final var eintrag : anzahlJeId.entrySet()) {
			final var ofConfigs = alt.getChannelDescriptions().get(eintrag.getKey());
			assertNotNull(ofConfigs, "Domäne kennt Kanal-Id " + eintrag.getKey());
			assertEquals(ofConfigs.getElements().size(), eintrag.getValue(),
					"Varianten-Anzahl von " + eintrag.getKey());
		}

		// Flache Eigenschaften dual-parse (eindeutige Ids -> getIfSingle):
		int verglichen = 0;
		for (final ControlDto.ChannelDescriptionDto d : kanaele.channelDescription) {
			final var ausDomaene = alt.getChannelDescriptions().get(d.id).getIfSingle();
			if (ausDomaene == null) {
				continue; // Konfigurations-Varianten: oben über die Anzahl abgedeckt
			}
			assertEquals(ausDomaene.getUnit(), d.unit, "unit von " + d.id);
			assertEquals(ausDomaene.isBuffered(), d.buffered, "buffered von " + d.id);
			assertEquals(ausDomaene.glitchInhibitScanIntervals(), d.glitchInhibitScanIntervals,
					"glitchInhibitScanIntervals von " + d.id);

			switch (ausDomaene.getType()) {
				case MEASUREMENT:
					assertNotNull(d.measurement, "Measurement-Quelle von " + d.id);
					assertNull(d.control);
					assertNull(d.calculation);
					assertEquals(ausDomaene.isAverage(), d.measurement.average, "average von " + d.id);
					assertEquals(ausDomaene.isFast(), d.measurement.fast, "fast von " + d.id);
					assertEquals(Integer.valueOf(d.measurement.divisor), ausDomaene.getDivisor(),
							"Divisor von " + d.id);
					org.junit.jupiter.api.Assertions.assertFalse(ausDomaene.isWriteable());
					break;
				case CALCULATION:
					assertNotNull(d.calculation, "Calculation-Quelle von " + d.id);
					assertNull(d.control);
					assertNull(d.measurement);
					assertEquals(Integer.valueOf(1), ausDomaene.getDivisor());
					break;
				case CONTROL:
					assertNotNull(d.control, "Control-Quelle von " + d.id);
					assertNull(d.measurement);
					assertNull(d.calculation);
					final ControlDto.ControlSourceDto quelle = d.control;
					// XSD-choice: genau eine der fünf Strategien ist gesetzt.
					assertEquals(1, java.util.stream.Stream
							.of(quelle.typeReadWrite, quelle.typeRead, quelle.typeMode, quelle.typeButton,
									quelle.typeReheat)
							.filter(java.util.Objects::nonNull).count(), "Strategie-Anzahl von " + d.id);
					// Nur TypeRead ist nicht schreibbar.
					assertEquals(quelle.typeRead == null, ausDomaene.isWriteable(), "writeable von " + d.id);
					final Integer divisorErwartet = quelle.typeReadWrite != null
							? Integer.valueOf(quelle.typeReadWrite.divisor)
							: quelle.typeRead != null ? Integer.valueOf(quelle.typeRead.divisor) : null;
					assertEquals(divisorErwartet, ausDomaene.getDivisor(), "Divisor von " + d.id);
					if (quelle.typeMode != null) {
						final var modiDomaene = new java.util.ArrayList<String>();
						ausDomaene.getModes().forEach(m -> modiDomaene.add(m.getName()));
						final var modiDto = quelle.typeMode.modeEntry.stream().map(m -> m.id)
								.collect(java.util.stream.Collectors.toList());
						assertEquals(modiDto, modiDomaene, "Modi von " + d.id);
					}
					if (quelle.typeReadWrite != null) {
						final var rw = quelle.typeReadWrite;
						final var uls = ausDomaene.getUpperLowerStep();
						assertEquals((double) rw.most / rw.divisor, uls.getUpper(), "upper von " + d.id);
						assertEquals((double) rw.least / rw.divisor, uls.getLower(), "lower von " + d.id);
						assertEquals((double) rw.increment / rw.divisor, uls.getStep(), "step von " + d.id);
						if (rw.incrementChange != null && rw.changedIncrement != null) {
							assertEquals(Double.valueOf((double) rw.incrementChange / rw.divisor),
									uls.getIncrementChange(), "incrementChange von " + d.id);
							assertEquals(Double.valueOf((double) rw.changedIncrement / rw.divisor),
									uls.getChangedIncrement(), "changedIncrement von " + d.id);
						} else {
							assertNull(uls.getIncrementChange());
						}
					}
					break;
			}
			++verglichen;
		}
		org.junit.jupiter.api.Assertions.assertTrue(verglichen >= 80,
				"genügend Kanäle dual-parse-verglichen: " + verglichen);
	}

	/**
	 * {@code ChannelDescriptions} — Charakterisierung der Bindung einzelner
	 * Vorlagen-Kanäle über alle Quelltypen und Strategien: Measurement-Felder,
	 * Calculation-Aliases, die Konfigurations-Varianten von C01, TypeRead mit
	 * EquipmentOnOff/Trigger, TypeMode mit GuiSet und MustBeWhite, TypeReadWrite
	 * mit GuiModification/Dependency, TypeButton, TypeReheat und die
	 * admin-/Feature-Configurations am Kanal.
	 */
	@Test
	void channelDescriptionsCharakterisiert() throws Exception {
		final ControlDto neu = parseNeu();
		final ControlDto.ChannelDescriptionsDto kanaele = neu.channelDescriptions;

		// X06 (Uhrzeit): Measurement type="date" mit zwei Feldern.
		final var x06 = kanaele.descriptions("X06").get(0);
		assertEquals("date", x06.measurement.type);
		assertEquals(2, x06.measurement.field.size());
		assertEquals(0, x06.measurement.field.get(0).position);
		assertEquals(6, x06.measurement.field.get(0).length);
		assertEquals(202, x06.measurement.field.get(1).position);

		// S01: signed/average/divisor 10, buffered, Einheit.
		final var s01 = kanaele.descriptions("S01").get(0);
		org.junit.jupiter.api.Assertions.assertTrue(s01.buffered);
		assertEquals("°C", s01.unit);
		assertEquals("signed", s01.measurement.type);
		org.junit.jupiter.api.Assertions.assertTrue(s01.measurement.average);
		assertEquals(10, s01.measurement.divisor);
		assertEquals(14, s01.measurement.field.get(0).position);
		assertEquals(4, s01.measurement.field.get(0).length);

		// O-1: fast-Kanal (Ausgänge).
		org.junit.jupiter.api.Assertions.assertTrue(kanaele.descriptions("O-1").get(0).measurement.fast);

		// X01/X05: Calculation-Strategien und Alias-Bindung.
		final var x01 = kanaele.descriptions("X01").get(0);
		assertEquals("starts", x01.calculation.strategy);
		assertEquals("equipmentOn", x01.calculation.alias.get(0).id);
		assertEquals("alias.Brenner", x01.calculation.alias.get(0).dataId);
		final var x05 = kanaele.descriptions("X05").get(0);
		assertEquals("burnerStatus", x05.calculation.strategy);
		assertEquals(2, x05.glitchInhibitScanIntervals);
		assertEquals(2, x05.calculation.alias.size());
		assertEquals("burnerLevel2On", x05.calculation.alias.get(1).id);

		// X03: Konfigurations-Maske am Kanal (wiederverwendete Configuration-Bean).
		final var x03 = kanaele.descriptions("X03").get(0);
		assertEquals(0x1000L, x03.configuration.configurationMask.get(0).compareMaskValue());
		assertEquals(0xf000L, x03.configuration.configurationMask.get(0).andMaskValue());

		// C01: zwei Varianten (ein bzw. zwei Zählfunktions-Screens), TypeRead,
		// EquipmentOnOff mit Trigger (factor-Default -1 = Brennerstarts).
		final var c01 = kanaele.descriptions("C01");
		assertEquals(2, c01.size());
		assertEquals("Zaehlfunktion", c01.get(0).control.guiAccess.screenId);
		assertEquals("Zaehlfunktion-2_2", c01.get(1).control.guiAccess.screenId);
		assertEquals(145, c01.get(0).control.guiAccess.currentValue.topLeft.x);
		assertEquals(75, c01.get(0).control.guiAccess.currentValue.topLeft.y);
		assertNull(c01.get(0).control.guiAccess.restoreChannelId);
		assertEquals("^(\\d+)$", c01.get(0).control.typeRead.guiRead.format);
		assertEquals(1, c01.get(0).control.typeRead.divisor);
		final var c01Update = c01.get(0).control.updateBy;
		assertEquals("alias.Brenner", c01Update.equipmentOnOff.equipmentId);
		assertEquals("X01", c01Update.equipmentOnOff.calculatedId);
		assertEquals(-1, c01Update.equipmentOnOff.factor);
		assertEquals("X02", c01Update.equipmentOnOff.trigger.get(0).id);
		assertNull(c01Update.humanAccess);

		// C02: EquipmentOnOff mit Faktor/Stunden-Synchronisation.
		final var c02 = kanaele.descriptions("C02").get(0).control.updateBy.equipmentOnOff;
		assertEquals(3600, c02.factor);
		org.junit.jupiter.api.Assertions.assertTrue(c02.hourly);
		assertEquals("checkCalculation", c02.checkIntervalId);
		assertEquals("readCalculationInterval", c02.readIntervalId);
		assertNull(c02.trigger);

		// C03: optionaler Kanal (modulierter Gasbrenner).
		org.junit.jupiter.api.Assertions.assertTrue(kanaele.descriptions("C03").get(0).control.optional);

		// C04: TypeMode (aus/an/auto) mit GuiSet + exakter Grafik; HumanAccess.
		final var c04 = kanaele.descriptions("C04").get(0).control;
		assertEquals(3, c04.typeMode.modeEntry.size());
		final var aus = c04.typeMode.modeEntry.get(0);
		assertEquals("aus", aus.id);
		assertNull(aus.handling);
		assertEquals("Standard", aus.guiSet.touch.pushTimeRefId);
		assertEquals("ModeChange", aus.guiSet.touch.releaseTimeRefId);
		assertEquals(65, aus.guiSet.touch.coordinate.x);
		assertEquals(75, aus.guiSet.touch.coordinate.y);
		assertEquals("WWPumpeAus", aus.guiSet.screenGrafic.id);
		assertEquals(Boolean.TRUE, aus.guiSet.screenGrafic.exact);
		assertNotNull(c04.updateBy.humanAccess);
		assertNull(c04.updateBy.equipmentOnOff);

		// C05: TypeReadWrite-Grenzen + GuiModification (wrapAround, Upper/Lower).
		final var c05 = kanaele.descriptions("C05").get(0).control.typeReadWrite;
		assertEquals(1, c05.increment);
		assertEquals(10, c05.least);
		assertEquals(65, c05.most);
		assertEquals(Integer.valueOf(5), c05.maxExceeding);
		assertNull(c05.incrementChange);
		org.junit.jupiter.api.Assertions.assertTrue(c05.guiModification.wrapAround);
		assertEquals("(\\d+)..$", c05.guiModification.format);
		assertEquals(225, c05.guiModification.upper.coordinate.x);
		assertEquals(20, c05.guiModification.upper.coordinate.y);
		assertEquals(50, c05.guiModification.lower.coordinate.y);

		// C06, 2. Variante: GUI-Vorbereitung über den Heizkreis-Button.
		assertEquals("Button_HK1",
				kanaele.descriptions("C06").get(1).control.guiAccess.preparationRef.refId);

		// C26: TypeButton (Zirkulations-Puls).
		final var c26 = kanaele.descriptions("C26").get(0).control.typeButton;
		assertEquals("Standard", c26.pushTimeId);
		assertEquals("WindowChange", c26.releaseTimeId);
		org.junit.jupiter.api.Assertions.assertFalse(c26.invert);

		// C28: Feature-Configuration, Dependency mit value, wechselnde Schrittweite.
		final var c28 = kanaele.descriptions("C28").get(0);
		assertEquals("WW_Pumpe_Min_Laufzeit", c28.configuration.feature.id);
		final var abhaengig = c28.control.guiAccess.dependency.get(0);
		assertEquals("C26", abhaengig.id);
		assertEquals("true", abhaengig.value);
		assertNull(abhaengig.priority);
		assertEquals(Integer.valueOf(120), c28.control.typeReadWrite.incrementChange);
		assertEquals(Integer.valueOf(30), c28.control.typeReadWrite.changedIncrement);

		// C31: ModeEntry mit MustBeWhite (Fix-Eintrag invertiert).
		final var c31 = kanaele.descriptions("C31").get(0).control.typeMode;
		final var fix = c31.modeEntry.get(1);
		assertEquals("Fix", fix.id);
		assertEquals(1, fix.mustBeWhite.size());
		org.junit.jupiter.api.Assertions.assertTrue(fix.mustBeWhite.get(0).invertFunction);
		assertEquals(130, fix.mustBeWhite.get(0).topLeft.x);
		org.junit.jupiter.api.Assertions.assertFalse(c31.modeEntry.get(0).mustBeWhite.get(0).invertFunction);

		// C32: Dependency mit priority/standby, divisor 100 (Fließkomma-Format).
		final var c32 = kanaele.descriptions("C32").get(0).control;
		assertEquals(100, c32.typeReadWrite.divisor);
		final var kurve = c32.guiAccess.dependency.get(0);
		assertEquals("C31", kurve.id);
		assertEquals("Kurve", kurve.value);
		assertEquals(Integer.valueOf(20), kurve.priority);
		assertEquals("C06", kurve.standby);

		// C46: TypeReheat (drei Kanal-Referenzen + Auslöse-Touch).
		final var c46 = kanaele.descriptions("C46").get(0).control.typeReheat;
		assertEquals("alias.WarmwassertemperaturSoll", c46.desiredId);
		assertEquals("alias.Puffertemperatur", c46.pufferId);
		assertEquals("alias.PufferdTStart", c46.deltaId);
		assertEquals(225, c46.touchPoint.coordinate.x);
		assertEquals(75, c46.touchPoint.coordinate.y);

		// C49: admin-Configuration ohne Masken.
		final var c49 = kanaele.descriptions("C49").get(0);
		assertEquals("VALUE", c49.configuration.admin);
		assertNull(c49.configuration.configurationMask);
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
