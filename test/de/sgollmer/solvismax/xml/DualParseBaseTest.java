package de.sgollmer.solvismax.xml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import de.sgollmer.solvismax.BaseData;
import de.sgollmer.solvismax.ExecutionConfig;
import de.sgollmer.solvismax.connection.mqtt.Mqtt;
import de.sgollmer.solvismax.connection.mqtt.MqttConnectionConfig;
import de.sgollmer.solvismax.connection.mqtt.MqttTopicConfig;
import de.sgollmer.solvismax.connection.mqtt.TopicType;
import de.sgollmer.solvismax.model.objects.unit.Unit;
import de.sgollmer.solvismax.model.objects.unit.UnitConfig;
import de.sgollmer.solvismax.xml.jaxb.BaseDataDto;
import de.sgollmer.solvismax.xml.jaxb.JaxbBaseReader;
import de.sgollmer.solvismax.xml.jaxb.Mapper;

/**
 * Konsistenztests für den JAXB-Pfad der {@code base.xml}: die DTO-Bindung, die
 * Sicht-/Mapper-Ableitungen und der über {@code read()} gebaute Domänengraph
 * müssen zueinander passen (z. B. muss eine Config-Sicht aus dem DTO dieselben
 * Werte liefern wie der daraus konstruierte Domänengraph — das prüft die
 * Verdrahtung von {@code Mapper.toUnit}/{@code toBaseData}).
 *
 * <p>
 * <b>Historie:</b> Diese Tests entstanden als <b>Dual-Parse-Differenztests</b>
 * gegen den alten {@code XMLLibrary}-Creator-Pfad und haben dessen
 * Verhaltensgleichheit mit dem JAXB-Pfad belegt. Nach der Entfernung der
 * base.xml-Creators ist die „alte" Seite der über {@code read()} gebaute
 * Domänengraph; das Original-Verhalten bleibt zusätzlich dauerhaft über die
 * Golden-Snapshots in {@code ParseDiffTest} gepinnt (erzeugt aus dem
 * Creator-Pfad vor dessen Entfernung).
 * </p>
 */
class DualParseBaseTest {

	private static final String TEMPLATE = "rsc/de/sgollmer/solvismax/data/base.xml";
	private static final String MINIMAL = "testFiles/xml/base-minimal.xml";
	private static final String EXTENDED = "testFiles/xml/base-extended.xml";

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

		// Weitere Intervall-/Verzögerungsattribute (1:1, keine Umrechnung).
		assertEquals(u.getForcedUpdateInterval_ms(), ud.forcedUpdateInterval_ms);
		assertEquals(u.getDoubleUpdateInterval_ms(), ud.doubleUpdateInterval_ms);
		assertEquals(u.getBufferedInterval_ms(), ud.bufferedInterval_ms);
		assertEquals(u.getReleaseBlockingAfterUserAccess_ms(), ud.releaseBlockingAfterUserAccess_ms);
		assertEquals(u.getReleaseBlockingAfterServiceAccess_ms(), ud.releaseBlockingAfterServiceAccess_ms);
		assertEquals(u.getReheatingNotRequiredActiveTime_ms(), ud.reheatingNotRequiredActiveTime_ms);
		assertEquals(u.isDelayAfterSwitchingOnEnable(), ud.delayAfterSwitchingOnEnable);

		// forceUpdateAfterFastChangingIntervals: nach der Korrektur des
		// Creator-Tippfehlers liest der alte Parser den Template-Wert (3) —
		// vorher wurde er ignoriert. Jetzt 1:1-vergleichbar.
		assertEquals(3, ud.forceUpdateAfterFastChangingIntervals, "Template setzt den Wert 3");
		assertEquals(u.getForceUpdateAfterFastChangingIntervals(), ud.forceUpdateAfterFastChangingIntervals);
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

	/**
	 * Charakterisierung von {@code <ChannelOptions>}: der einzige im Template real
	 * befüllte Unit-Kindzweig (7 {@code <Channel>}-Einträge mit
	 * {@code fix}/{@code offset}/{@code powerOnDelay_s}). Die Domäne kapselt das in
	 * {@code AllChannelOptions} ohne einfachen Getter, daher Charakterisierung der
	 * JAXB-Bindung gegen die Vorlagenwerte.
	 */
	@Test
	void channelOptionsGebunden() throws Exception {
		assumeTemplate();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);
		final BaseDataDto.UnitDto ud = neu.units.unit.get(0);

		assertNotNull(ud.channelOptions);
		assertNotNull(ud.channelOptions.channel);
		assertEquals(7, ud.channelOptions.channel.size());

		final BaseDataDto.ChannelDto erster = ud.channelOptions.channel.get(0);
		assertEquals("C47.Puffer_dT_Start", erster.id);
		// Nullable Bindung (wie der alte Parser: Double, null = nicht gesetzt).
		assertEquals(Double.valueOf(12), erster.fix);
		assertNull(erster.offset, "nur fix gesetzt -> offset nicht gebunden");
		assertNull(erster.powerOnDelay_s, "nur fix gesetzt -> powerOnDelay nicht gebunden");
		// Kanal mit powerOnDelay_s (Aussentemperatur = 900).
		final boolean hatPowerOnDelay = ud.channelOptions.channel.stream()
				.anyMatch(c -> "S10.Aussentemperatur".equals(c.id) && Integer.valueOf(900).equals(c.powerOnDelay_s));
		org.junit.jupiter.api.Assertions.assertTrue(hatPowerOnDelay,
				"Kanal S10.Aussentemperatur mit powerOnDelay_s=900 erwartet");
	}

	/**
	 * <b>Aggregat-Zweig als Wert-Typ (Weg B):</b> {@code Features} bleibt als
	 * reiner Wert-Typ erhalten (die Feature-Semantik lebt nur dort);
	 * {@code Mapper.toFeatures} baut ihn über die neue Factory
	 * {@code Features.of} aus dem DTO. Der Test vergleicht ALLE semantischen
	 * Abfragen des gemappten Objekts mit dem des alten Parsers.
	 */
	@Test
	void featuresWertTypAusDtoIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final de.sgollmer.solvismax.model.objects.unit.Features ausDomaene =
				alt.getUnits().getUnits().iterator().next().getFeatures();
		final de.sgollmer.solvismax.model.objects.unit.Features ausDto =
				Mapper.toFeatures(neu.units.unit.get(0).features);

		assertEquals(ausDomaene.isClockTuning(), ausDto.isClockTuning());
		assertEquals(ausDomaene.isEquipmentTimeSynchronisation(), ausDto.isEquipmentTimeSynchronisation());
		assertEquals(ausDomaene.isUpdateAfterUserAccess(), ausDto.isUpdateAfterUserAccess());
		assertEquals(ausDomaene.isDetectServiceAccess(), ausDto.isDetectServiceAccess());
		assertEquals(ausDomaene.isClearErrorMessageAfterMail(), ausDto.isClearErrorMessageAfterMail());
		assertEquals(ausDomaene.isPowerOffIsServiceAccess(), ausDto.isPowerOffIsServiceAccess());
		assertEquals(ausDomaene.isSendMailOnError(), ausDto.isSendMailOnError());
		assertEquals(ausDomaene.isSendMailOnErrorsCleared(), ausDto.isSendMailOnErrorsCleared());
		assertEquals(ausDomaene.isEndOfUserByScreenSaver(), ausDto.isEndOfUserByScreenSaver());
		assertEquals(ausDomaene.isAdmin(), ausDto.isAdmin());
		assertEquals(ausDomaene.isInteractiveGUIAccess(), ausDto.isInteractiveGUIAccess());
		assertEquals(ausDomaene.getMap(), ausDto.getMap());
	}

	/**
	 * Die fachliche Regel „genau eines von InteractiveGUIAccess/OnlyMeasurements"
	 * (Validierung außerhalb der XSD) greift auch beim DTO-Weg: {@code Features.of}
	 * wirft — wie der alte Parser — bei Verletzung.
	 */
	@Test
	void featuresRegelVerletzungWirft() {
		org.junit.jupiter.api.Assertions.assertThrows(de.sgollmer.xmllibrary.XmlException.class,
				() -> de.sgollmer.solvismax.model.objects.unit.Features.of(java.util.Map.of()));
	}

	/**
	 * <b>Aggregat-Zweig als Wert-Typ (Weg B):</b> {@code ChannelOption}-Liste aus
	 * dem DTO. Charakterisierung (die Domäne legt fix/factor/offset nicht offen):
	 * 7 Optionen aus dem Template; die ×1000-Ableitung von {@code powerOnDelay_s}
	 * und der Default −1 (Attribut fehlt) entsprechen der Creator-Semantik.
	 */
	@Test
	void channelOptionsWertTypGemappt() throws Exception {
		assumeTemplate();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final java.util.List<de.sgollmer.solvismax.model.objects.unit.AllChannelOptions.ChannelOption> optionen =
				Mapper.toChannelOptionList(neu.units.unit.get(0).channelOptions);

		assertEquals(7, optionen.size());
		// S10.Aussentemperatur: powerOnDelay_s=900 -> 900000 ms (x1000).
		org.junit.jupiter.api.Assertions.assertTrue(
				optionen.stream().anyMatch(o -> o.getPowerOnDelay() == 900_000),
				"Kanal mit powerOnDelay 900 s -> 900000 ms erwartet");
		// Erster Kanal (nur fix gesetzt): Default -1 wie im alten Creator.
		assertEquals(-1, optionen.get(0).getPowerOnDelay());
	}

	/**
	 * Charakterisierung von {@code <ExceptionMail>} und {@code <Iobroker>}: Die
	 * Domäne legt diese Werte nicht über Getter offen, daher wird die
	 * JAXB-Bindung gegen die bekannten Vorlagenwerte geprüft (statt Dual-Parse
	 * gegen die Domäne). Sichert ab, dass JAXB diese Elemente korrekt einliest.
	 */
	@Test
	void exceptionMailUndIobrokerGebunden() throws Exception {
		assumeTemplate();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		assertNotNull(neu.exceptionMail);
		assertEquals("Vorname Nachname", neu.exceptionMail.name);
		assertEquals("securesmtp.t-online.de", neu.exceptionMail.provider);
		assertEquals("TLS", neu.exceptionMail.securityType);
		assertEquals(5870, neu.exceptionMail.port);
		assertNotNull(neu.exceptionMail.recipients);
		assertEquals(2, neu.exceptionMail.recipients.recipient.size());
		assertEquals("TO", neu.exceptionMail.recipients.recipient.get(0).type);

		assertNotNull(neu.iobroker);
		assertEquals("mqtt-client.0", neu.iobroker.mqttInterface);
		assertEquals("javascript.0", neu.iobroker.javascriptInterface);
	}

	/**
	 * Abgeleitete Sicht (Weg B): {@code Mapper.measurementsIntervalMs} rechnet den
	 * gebundenen Sekunden-Rohwert nach Millisekunden um und muss dem entsprechen,
	 * was die Domäne (die dieselbe Umrechnung im Parser macht) liefert.
	 */
	@Test
	void measurementsIntervalAbleitungIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final Unit u = alt.getUnits().getUnits().iterator().next();
		assertEquals(u.getMeasurementsInterval_ms(), Mapper.measurementsIntervalMs(neu.units.unit.get(0)));
	}

	/** Abgeleitete Sicht (Weg B): schnelles Mess-Intervall ×1000 gegen die Domäne. */
	@Test
	void measurementsIntervalFastAbleitungIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);
		final Unit u = alt.getUnits().getUnits().iterator().next();
		assertEquals(u.getMeasurementsIntervalFast_ms(), Mapper.measurementsIntervalFastMs(neu.units.unit.get(0)));
	}

	/**
	 * <b>Konsumenten-Pilot (Weg B):</b> Der Topic-Aufbau (`TopicType.getTopicParts`)
	 * hängt jetzt an der schmalen Sicht {@link MqttTopicConfig} statt an der
	 * konkreten, laufzeitgekoppelten {@code Mqtt}. Dieser Test belegt, dass der
	 * Konsument aus <b>beiden</b> Quellen — dem Domänenobjekt (das die Sicht
	 * erfüllt) und der DTO-gestützten Sicht — <b>identische</b> Config erhält. Die
	 * spätere Ablösung der Domänen-Config ändert das Verhalten also nicht.
	 */
	@Test
	void mqttTopicConfigAusDomaeneUndDtoIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final MqttTopicConfig ausDomaene = alt.getMqtt();          // Mqtt implements MqttTopicConfig
		final MqttTopicConfig ausDto = Mapper.topicConfig(neu.mqtt); // DTO-gestützt

		assertEquals(ausDomaene.getTopicPrefix(), ausDto.getTopicPrefix());
		assertEquals(ausDomaene.getSmartHomeId(), ausDto.getSmartHomeId());
	}

	/**
	 * <b>Konsumenten-Migration nach dem Pilotmuster (Weg B):</b> Der
	 * Broker-Verbindungsaufbau ({@code MqttThread}) liest seine Config jetzt über
	 * die schmale Sicht {@link MqttConnectionConfig}. Dieser Test belegt, dass der
	 * Konsument aus <b>beiden</b> Quellen — dem Domänenobjekt (das die Sicht
	 * erfüllt) und der DTO-gestützten Sicht — <b>identische</b> Config erhält.
	 *
	 * <p>
	 * {@code passwordCrypt}: Die Vorlage enthält den Platzhalter
	 * {@code "AES-coded"}, dessen Entschlüsselung in beiden Parsern fehlschlägt —
	 * beide Sichten liefern daher eine ungesetzte {@link
	 * de.sgollmer.solvismax.crypt.CryptAes} ({@code cP() == null}).
	 * </p>
	 */
	@Test
	void mqttConnectionConfigAusDomaeneUndDtoIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final MqttConnectionConfig ausDomaene = alt.getMqtt();               // Mqtt implements MqttConnectionConfig
		final MqttConnectionConfig ausDto = Mapper.connectionConfig(neu.mqtt); // DTO-gestützt

		assertEquals(ausDomaene.getUserName(), ausDto.getUserName());
		assertEquals(ausDomaene.getTopicPrefix(), ausDto.getTopicPrefix());
		assertEquals(ausDomaene.getPublishQoS(), ausDto.getPublishQoS());
		assertEquals(ausDomaene.getSubscribeQoS(), ausDto.getSubscribeQoS());
		// Ssl-Element in der Vorlage nicht vorhanden -> beide Sichten null.
		assertEquals(ausDomaene.getSsl() == null, ausDto.getSsl() == null);
		assertNull(ausDto.getSsl());
		// Entschluesseltes Passwort (hier: beidseitig ungesetzt, s. Javadoc).
		assertArrayEquals(ausDomaene.getPasswordCrypt().cP(), ausDto.getPasswordCrypt().cP());
	}

	/**
	 * Konsumenten-Nachweis auf Verhaltensebene: {@code TopicType.getTopicData}
	 * (jetzt an {@link MqttTopicConfig} verschmälert) baut aus Domänen- und
	 * DTO-Sicht <b>identische Topics</b> — inklusive eines Typs mit ClientId-Teil
	 * ({@code CLIENT_ONLINE} nutzt {@code getSmartHomeId()}).
	 */
	@Test
	void topicAufbauAusDomaeneUndDtoIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final MqttTopicConfig ausDomaene = alt.getMqtt();
		final MqttTopicConfig ausDto = Mapper.topicConfig(neu.mqtt);

		for (final TopicType type : new TopicType[] { TopicType.SERVER_META, TopicType.CLIENT_ONLINE }) {
			final TopicType.TopicData vonDomaene = type.getTopicData(ausDomaene, null, null);
			final TopicType.TopicData vonDto = type.getTopicData(ausDto, null, null);
			assertNotNull(vonDomaene);
			assertNotNull(vonDto);
			assertEquals(vonDomaene.getTopic(), vonDto.getTopic(), "Topic weicht ab: " + type);
		}
	}

	/**
	 * <b>Konsumenten-Migration nach dem Pilotmuster (Weg B):</b> Die flachen
	 * Ausführungswerte werden jetzt über die schmale Sicht {@link ExecutionConfig}
	 * gelesen (Konsumenten: {@code Main} für Port/Pfad, {@code Instances} für
	 * Pfad/Zeitzone/Echo-Sperrzeit). Dieser Test belegt, dass beide Quellen —
	 * Domänenobjekt und DTO-gestützte Sicht — identische Config liefern;
	 * {@code getWritablePath()} enthält dabei die abgeleitete OS-Weiche.
	 */
	@Test
	void executionConfigAusDomaeneUndDtoIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final ExecutionConfig ausDomaene = alt;                                    // BaseData implements ExecutionConfig
		final ExecutionConfig ausDto = Mapper.executionConfig(neu.executionData);  // DTO-gestützt

		assertEquals(ausDomaene.getTimeZone(), ausDto.getTimeZone());
		assertEquals(ausDomaene.getPort(), ausDto.getPort());
		assertEquals(ausDomaene.getEchoInhibitTime_ms(), ausDto.getEchoInhibitTime_ms());
		// Abgeleitete Sicht: OS-Weiche Windows/Linux — auf derselben Maschine
		// muessen beide Quellen denselben Pfad waehlen.
		assertEquals(ausDomaene.getWritablePath(), ausDto.getWritablePath());
		assertNotNull(ausDto.getWritablePath());
	}

	/**
	 * <b>Konsumenten-Migration nach dem Pilotmuster (Weg B):</b> Die flachen
	 * Unit-Skalarwerte werden jetzt über die schmale Sicht {@link UnitConfig}
	 * gelesen (Zugang: {@code Solvis.getUnitConfig()}; Konsumenten: WatchDog,
	 * Distributor, HumanAccess, SolvisWorkers, Measurement, StrategyReheat,
	 * SolvisData, ScreenSaver, ErrorState). Dieser Test belegt, dass beide
	 * Quellen — Domänen-Aggregat und DTO-gestützte Sicht (inkl. der
	 * ×1000-Ableitungen und {@code isBuffered}) — identische Config liefern.
	 */
	@Test
	void unitConfigAusDomaeneUndDtoIdentisch() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final UnitConfig ausDomaene = alt.getUnits().getUnits().iterator().next(); // Unit implements UnitConfig
		final UnitConfig ausDto = Mapper.unitConfig(neu.units.unit.get(0));        // DTO-gestützt

		assertUnitConfigIdentisch(ausDomaene, ausDto);
	}

	private static void assertUnitConfigIdentisch(final UnitConfig ausDomaene, final UnitConfig ausDto) {
		assertEquals(ausDomaene.getId(), ausDto.getId());
		assertEquals(ausDomaene.getDefaultAverageCount(), ausDto.getDefaultAverageCount());
		assertEquals(ausDomaene.getMeasurementHysteresisFactor(), ausDto.getMeasurementHysteresisFactor());
		assertEquals(ausDomaene.getMeasurementsInterval_ms(), ausDto.getMeasurementsInterval_ms());
		assertEquals(ausDomaene.getMeasurementsIntervalFast_ms(), ausDto.getMeasurementsIntervalFast_ms());
		assertEquals(ausDomaene.getForceUpdateAfterFastChangingIntervals(),
				ausDto.getForceUpdateAfterFastChangingIntervals());
		assertEquals(ausDomaene.getForcedUpdateInterval_ms(), ausDto.getForcedUpdateInterval_ms());
		assertEquals(ausDomaene.getDoubleUpdateInterval_ms(), ausDto.getDoubleUpdateInterval_ms());
		assertEquals(ausDomaene.getBufferedInterval_ms(), ausDto.getBufferedInterval_ms());
		assertEquals(ausDomaene.isBuffered(), ausDto.isBuffered());
		assertEquals(ausDomaene.getWatchDogTime_ms(), ausDto.getWatchDogTime_ms());
		assertEquals(ausDomaene.getReleaseBlockingAfterUserAccess_ms(),
				ausDto.getReleaseBlockingAfterUserAccess_ms());
		assertEquals(ausDomaene.getReleaseBlockingAfterServiceAccess_ms(),
				ausDto.getReleaseBlockingAfterServiceAccess_ms());
		assertEquals(ausDomaene.getReheatingNotRequiredActiveTime_ms(),
				ausDto.getReheatingNotRequiredActiveTime_ms());
		assertEquals(ausDomaene.getResetErrorDelayTime(), ausDto.getResetErrorDelayTime());
		assertEquals(ausDomaene.isDelayAfterSwitchingOnEnable(), ausDto.isDelayAfterSwitchingOnEnable());
		assertEquals(ausDomaene.isFwLth2_21_02A(), ausDto.isFwLth2_21_02A());
		assertEquals(ausDomaene.getIgnoredFrameThicknesScreenSaver(), ausDto.getIgnoredFrameThicknesScreenSaver());
	}

	/**
	 * <b>Default-Behandlung fehlender Attribute (Standard-Mechanismus):</b> Der
	 * JAXB-Unmarshaller lässt Felder unangetastet, deren Attribut im XML fehlt —
	 * die Creator-Defaults des alten Parsers sind daher als
	 * <b>Feld-Initialisierer</b> im DTO gespiegelt; der Fallback
	 * {@code measurementsIntervalFast_s → measurementsInterval_s} liegt (als
	 * feldübergreifende Regel) in der Sicht {@code Mapper.measurementsIntervalFastMs}.
	 *
	 * <p>
	 * Beweis an einer Minimal-Fixture, die nur die XSD-Pflichtattribute enthält:
	 * beide Parser liefern für ALLE {@code UnitConfig}-Werte identische Defaults —
	 * charakterisiert: {@code forceUpdateAfterFastChangingIntervals=3},
	 * {@code reheatingNotRequiredActiveTime_ms=30000}, schnelles Intervall fällt
	 * auf das normale zurück (10 s → 10000 ms).
	 * </p>
	 */
	@Test
	void defaultsBeiFehlendenAttributenIdentisch() throws Exception {
		Assumptions.assumeTrue(new File(MINIMAL).isFile(), "Fixture fehlt: " + MINIMAL);
		final BaseData alt = new BaseControlFileReader(MINIMAL).read();
		assertNotNull(alt, "alter Parser muss die Minimal-Fixture akzeptieren (XSD-valide)");
		final BaseDataDto neu = JaxbBaseReader.read(MINIMAL);

		final UnitConfig ausDomaene = alt.getUnits().getUnits().iterator().next();
		final UnitConfig ausDto = Mapper.unitConfig(neu.units.unit.get(0));

		// Charakterisierung der Default-Werte (aus den Creator-Feldern).
		assertEquals(3, ausDto.getForceUpdateAfterFastChangingIntervals(),
				"Default Constants.FORCE_UPDATE_AFTER_N_INTERVALS");
		assertEquals(30000, ausDto.getReheatingNotRequiredActiveTime_ms(),
				"Default Constants.Defaults.REHEATING_NOT_REQUIRED_ACTIVE_TIME");
		assertEquals(10_000, ausDto.getMeasurementsIntervalFast_ms(),
				"fehlendes measurementsIntervalFast_s faellt aufs normale Intervall zurueck");
		assertEquals(0, ausDto.getDoubleUpdateInterval_ms());
		assertEquals(0, ausDto.getResetErrorDelayTime());
		assertFalse(ausDto.isFwLth2_21_02A());

		// 1:1 gegen den alten Parser — Defaults beider Seiten identisch.
		assertUnitConfigIdentisch(ausDomaene, ausDto);

		// Auch die per-Feature-Defaults (missingValue je Feature) sind identisch:
		// fast alle Features fehlen in der Fixture, z. B. defaultet
		// ClearErrorMessageAfterMail auf true, SendMailOnError auf false.
		final de.sgollmer.solvismax.model.objects.unit.Features featuresDomaene =
				alt.getUnits().getUnits().iterator().next().getFeatures();
		final de.sgollmer.solvismax.model.objects.unit.Features featuresDto =
				Mapper.toFeatures(neu.units.unit.get(0).features);
		org.junit.jupiter.api.Assertions.assertTrue(featuresDto.isClearErrorMessageAfterMail());
		assertFalse(featuresDto.isSendMailOnError());
		assertEquals(featuresDomaene.isClearErrorMessageAfterMail(), featuresDto.isClearErrorMessageAfterMail());
		assertEquals(featuresDomaene.isSendMailOnError(), featuresDto.isSendMailOnError());
		assertEquals(featuresDomaene.isInteractiveGUIAccess(), featuresDto.isInteractiveGUIAccess());
		assertEquals(featuresDomaene.isAdmin(), featuresDto.isAdmin());
	}

	/**
	 * Abgeleitete Sicht (Weg B): {@code Recipient.type} → {@code RecipientType}.
	 * Charakterisierung (die Domäne legt die Empfänger nicht offen): In der Vorlage
	 * sind beide Empfänger vom Typ {@code TO}.
	 */
	@Test
	void recipientTypeAbleitung() throws Exception {
		assumeTemplate();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);
		final BaseDataDto.RecipientDto erster = neu.exceptionMail.recipients.recipient.get(0);
		assertEquals(jakarta.mail.Message.RecipientType.TO, Mapper.recipientType(erster));
	}

	/**
	 * Voller DTO→Domäne-Mapper am Mqtt-Teilbaum: {@code Mapper.toMqtt} baut aus
	 * dem DTO ein echtes Domänen-{@link Mqtt} (inkl. {@code passwordCrypt}-
	 * Entschlüsselung und {@code Ssl}-Abbildung). Das Ergebnis muss dem des alten
	 * Parsers entsprechen — hier über die von der Domäne offengelegten Getter.
	 */
	@Test
	void mqttMapperErgibtDomaeneWieAlterParser() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);

		final Mqtt gemappt = Mapper.toMqtt(neu.mqtt);
		assertNotNull(gemappt);
		assertEquals(alt.getMqtt().isEnable(), gemappt.isEnable());
		assertEquals(alt.getMqtt().getTopicPrefix(), gemappt.getTopicPrefix());
		assertEquals(alt.getMqtt().getSmartHomeId(), gemappt.getSmartHomeId());
	}

	/**
	 * <b>Erweiterte Fixture — die bisher unbefüllten Unit-Kindzweige:</b> Urls,
	 * Extensions (via Configuration), benannte Feature-Alt-Form
	 * ({@code <ClockTuning>true</ClockTuning>}), IgnoredChannels,
	 * ChannelAssignments, Durations sowie die deprecated ms-Form
	 * {@code defaultReadMeasurementsInterval_ms}. Alle Zweige werden dual-parse-
	 * verglichen — über die Domänen-Getter, wo vorhanden, sonst über die
	 * Fach-Semantik (isChannelIgnored-Verhalten, Configuration-Kommentar).
	 */
	@Test
	void erweiterteZweigeAusDomaeneUndDtoIdentisch() throws Exception {
		Assumptions.assumeTrue(new File(EXTENDED).isFile(), "Fixture fehlt: " + EXTENDED);
		final BaseData alt = new BaseControlFileReader(EXTENDED).read();
		assertNotNull(alt, "alter Parser muss die erweiterte Fixture akzeptieren (XSD-valide)");
		final BaseDataDto neu = JaxbBaseReader.read(EXTENDED);

		final Unit u = alt.getUnits().getUnits().iterator().next();
		final BaseDataDto.UnitDto ud = neu.units.unit.get(0);

		// Urls (Text-Content-Elemente)
		assertEquals(new java.util.ArrayList<>(u.getUrls()), Mapper.toUrls(ud.urls));
		assertEquals(2, Mapper.toUrls(ud.urls).size());

		// IgnoredChannels: Verhaltensvergleich der kompilierten Muster
		final java.util.List<java.util.regex.Pattern> muster = Mapper.toIgnoredChannels(ud.ignoredChannels);
		for (final String kanal : new String[] { "P1.Pumpe", "X99Test", "S10.Aussentemperatur" }) {
			final boolean ausDto = muster.stream().anyMatch(p -> p.matcher(kanal).matches());
			assertEquals(u.isChannelIgnored(kanal), ausDto, "isChannelIgnored weicht ab: " + kanal);
		}
		org.junit.jupiter.api.Assertions.assertTrue(u.isChannelIgnored("P1.Pumpe"),
				"Fixture-Muster P1\\..* muss greifen");

		// Durations (Wert-Typ via Factories)
		final de.sgollmer.solvismax.model.objects.AllDurations durations = Mapper.toDurations(ud.durations);
		assertEquals(u.getDuration("Standard").getTime_ms(), durations.get("Standard").getTime_ms());
		assertEquals(u.getDuration("Long").getTime_ms(), durations.get("Long").getTime_ms());
		assertNull(durations.get("ValueChange"));

		// ChannelAssignments (Map, Schluessel = Assignment-Id)
		final java.util.Map<String, de.sgollmer.solvismax.model.objects.ChannelAssignment> assignments =
				Mapper.toChannelAssignments(ud.channelAssignments);
		final de.sgollmer.solvismax.model.objects.ChannelAssignment altA1 = u.getChannelAssignment("A1");
		assertNotNull(altA1);
		assertEquals(altA1.getChannelName(), assignments.get("A1").getChannelName());
		assertEquals(altA1.getUnit(), assignments.get("A1").getUnit());
		assertEquals(u.getChannelAssignment("A2").getChannelName(), assignments.get("A2").getChannelName());

		// Configuration inkl. Extensions: Kommentar-Sicht vergleicht Typ,
		// Heizung, Kreise und Extensions in einem Schritt.
		assertEquals(u.getComment(), Mapper.toConfiguration(ud).getComment());

		// Mess-Intervall ueber die deprecated ms-Form (kein measurementsInterval_s)
		assertNull(ud.measurementsInterval_s);
		assertEquals(u.getMeasurementsInterval_ms(), Mapper.measurementsIntervalMs(ud));
		assertEquals(10_000, Mapper.measurementsIntervalMs(ud));
		assertEquals(u.getMeasurementsIntervalFast_ms(), Mapper.measurementsIntervalFastMs(ud));

		// Benannte Feature-Alt-Form: ClockTuning=true neben generischem Feature.
		final de.sgollmer.solvismax.model.objects.unit.Features features = Mapper.toFeatures(ud.features);
		org.junit.jupiter.api.Assertions.assertTrue(features.isClockTuning(), "Alt-Form gebunden");
		assertEquals(u.getFeatures().isClockTuning(), features.isClockTuning());
		assertEquals(u.getFeatures().isInteractiveGUIAccess(), features.isInteractiveGUIAccess());
		assertEquals(u.getFeatures().getMap(), features.getMap());
	}

	/**
	 * IoBroker als Wert-Typ (Weg B): identische Interfaces aus Domäne und DTO —
	 * und der <b>Element-Default</b> (fehlendes {@code <Iobroker>} in der
	 * Minimal-Fixture → Default-Interfaces) verhält sich wie beim alten Parser.
	 */
	@Test
	void ioBrokerAusDomaeneUndDtoIdentischInklusiveElementDefault() throws Exception {
		assumeTemplate();
		final BaseData alt = new BaseControlFileReader(TEMPLATE).read();
		final BaseDataDto neu = JaxbBaseReader.read(TEMPLATE);
		final de.sgollmer.solvismax.smarthome.IoBroker ausDto = Mapper.toIoBroker(neu.iobroker);
		assertEquals(alt.getIoBroker().getMqttInterface(), ausDto.getMqttInterface());
		assertEquals(alt.getIoBroker().getJavascriptInterface(), ausDto.getJavascriptInterface());

		// Minimal-Fixture: <Iobroker> fehlt -> beide Parser liefern die Defaults.
		Assumptions.assumeTrue(new File(MINIMAL).isFile(), "Fixture fehlt: " + MINIMAL);
		final BaseData altMin = new BaseControlFileReader(MINIMAL).read();
		final BaseDataDto neuMin = JaxbBaseReader.read(MINIMAL);
		assertNull(neuMin.iobroker, "Element fehlt in der Fixture");
		final de.sgollmer.solvismax.smarthome.IoBroker defaultDto = Mapper.toIoBroker(neuMin.iobroker);
		assertEquals(altMin.getIoBroker().getMqttInterface(), defaultDto.getMqttInterface());
		assertEquals(altMin.getIoBroker().getJavascriptInterface(), defaultDto.getJavascriptInterface());
		assertEquals("mqtt-client.0", defaultDto.getMqttInterface());
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
