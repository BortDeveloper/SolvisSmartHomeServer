package de.sgollmer.solvismax.xml.jaxb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.sgollmer.solvismax.ExecutionConfig;
import de.sgollmer.solvismax.connection.mqtt.Mqtt;
import de.sgollmer.solvismax.connection.mqtt.MqttConnectionConfig;
import de.sgollmer.solvismax.connection.mqtt.MqttTopicConfig;
import de.sgollmer.solvismax.crypt.CryptAes;
import de.sgollmer.solvismax.crypt.Ssl;
import de.sgollmer.solvismax.error.CryptException;
import de.sgollmer.solvismax.model.objects.AllDurations;
import de.sgollmer.solvismax.model.objects.ChannelAssignment;
import de.sgollmer.solvismax.model.objects.unit.AllChannelOptions;
import de.sgollmer.solvismax.model.objects.unit.Features;
import de.sgollmer.solvismax.model.objects.unit.UnitConfig;
import de.sgollmer.xmllibrary.XmlException;

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

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Mapper.class);

	private Mapper() {
	}

	/**
	 * Baut den kompletten Domänengraphen {@link de.sgollmer.solvismax.BaseData}
	 * aus dem DTO — der Kern des <b>Reader-Umstiegs</b> (MODERNISIERUNG.md 3.3):
	 * {@code BaseControlFileReader.read()} liefert damit dasselbe Objektmodell
	 * wie zuvor der Creator-Pfad, gespeist aus dem JAXB-Parse.
	 */
	public static de.sgollmer.solvismax.BaseData toBaseData(final BaseDataDto dto) throws XmlException {
		if (dto.debug != null) {
			// Wie BaseData.Creator.setAttribute: das DEBUG-Attribut setzt das
			// statische Flag nur, wenn es im XML vorhanden ist.
			de.sgollmer.solvismax.BaseData.DEBUG = dto.debug;
		}
		final List<de.sgollmer.solvismax.model.objects.unit.Unit> units = new ArrayList<>();
		if (dto.units != null && dto.units.unit != null) {
			for (final BaseDataDto.UnitDto unit : dto.units.unit) {
				units.add(toUnit(unit));
			}
		}
		final BaseDataDto.ExecutionDataDto exec = dto.executionData;
		final de.sgollmer.solvismax.BaseData baseData = de.sgollmer.solvismax.BaseData.of(exec.timeZone, exec.port,
				exec.writeablePathWindows, exec.writablePathLinux, exec.echoInhibitTime_ms,
				de.sgollmer.solvismax.model.objects.unit.Units.of(units), toExceptionMail(dto.exceptionMail),
				toMqtt(dto.mqtt), toIoBroker(dto.iobroker));
		// Verdrahtung wie im alten BaseData.Creator.create().
		baseData.getIoBroker().setTopicConfig(baseData.getMqtt());
		return baseData;
	}

	/**
	 * Baut das Domänen-{@link de.sgollmer.solvismax.model.objects.unit.Unit} aus
	 * dem DTO — Komposition aller bereits einzeln verifizierten Bausteine
	 * (UnitConfig-Skalare, Features, Configuration, ChannelOptions, Urls,
	 * IgnoredChannels, Assignments, Durations, passwordCrypt).
	 */
	public static de.sgollmer.solvismax.model.objects.unit.Unit toUnit(final BaseDataDto.UnitDto dto)
			throws XmlException {
		if (dto.measurementsInterval_s == null && dto.defaultReadMeasurementsInterval_ms == null) {
			// Wie Unit.Creator.create().
			throw new XmlException("<defaultMeasurementsInterval_s> is missing in base.xml");
		}
		final CryptAes password = toCryptAes(dto.passwordCrypt);
		if (password.getException() != null) {
			// Wie Unit.Creator: Fehler geloggt, Einlesen laeuft weiter.
			logger.error("base.xml error of passwordCrypt in Unit tag: "
					+ password.getException().getMessage());
		}
		if (dto.password != null) {
			// Deprecated Klartext-Attribut (Creator: password.set, nur wenn
			// nicht bereits entschluesselt).
			password.set(dto.password);
		}
		return de.sgollmer.solvismax.model.objects.unit.Unit.of(dto.id, toConfiguration(dto),
				dto.urls == null ? null : toUrls(dto.urls), dto.url, dto.account, password,
				dto.defaultAverageCount, dto.measurementHysteresisFactor, measurementsIntervalMs(dto),
				measurementsIntervalFastMs(dto), dto.forceUpdateAfterFastChangingIntervals,
				dto.forcedUpdateInterval_ms, dto.doubleUpdateInterval_ms, dto.bufferedInterval_ms,
				dto.watchDogTime_ms, dto.releaseBlockingAfterUserAccess_ms,
				dto.releaseBlockingAfterServiceAccess_ms, dto.reheatingNotRequiredActiveTime_ms,
				dto.resetErrorDelayTime_ms, dto.delayAfterSwitchingOnEnable, dto.fwLth2_21_02A,
				toFeatures(dto.features), dto.ignoredFrameThicknesScreenSaver,
				toIgnoredChannels(dto.ignoredChannels), toChannelAssignments(dto.channelAssignments),
				dto.csvUnit, dto.durations == null ? null : toDurations(dto.durations),
				dto.channelOptions == null ? null : toChannelOptions(dto.channelOptions));
	}

	/**
	 * Baut die Domänen-{@link de.sgollmer.solvismax.mail.ExceptionMail} aus dem
	 * DTO ({@code null}, wenn das Element fehlt). Die Empfänger laufen über die
	 * DTO-neutralen {@code RecipientData}; das {@code type}-Enum-Mapping liefert
	 * {@link #recipientType}.
	 */
	public static de.sgollmer.solvismax.mail.ExceptionMail toExceptionMail(final BaseDataDto.ExceptionMailDto dto) {
		if (dto == null) {
			return null;
		}
		List<de.sgollmer.solvismax.mail.ExceptionMail.RecipientData> recipients = null;
		if (dto.recipients != null && dto.recipients.recipient != null) {
			recipients = new ArrayList<>();
			for (final BaseDataDto.RecipientDto recipient : dto.recipients.recipient) {
				recipients.add(new de.sgollmer.solvismax.mail.ExceptionMail.RecipientData(recipient.name,
						recipient.address, recipientType(recipient)));
			}
		}
		return de.sgollmer.solvismax.mail.ExceptionMail.of(dto.name, dto.from, toCryptAes(dto.passwordCrypt),
				dto.securityType, dto.provider, dto.port, recipients, toProxy(dto.proxy));
	}

	/**
	 * Baut den Mail-{@link de.sgollmer.solvismax.mail.Proxy} aus dem DTO.
	 * Wie der alte Creator: schlägt die {@code passwordCrypt}-Entschlüsselung
	 * fehl, wird das Passwort verworfen ({@code null}) und gewarnt.
	 */
	public static de.sgollmer.solvismax.mail.Proxy toProxy(final BaseDataDto.ProxyDto dto) {
		if (dto == null) {
			return null;
		}
		CryptAes password = null;
		if (dto.passwordCrypt != null) {
			password = toCryptAes(dto.passwordCrypt);
			if (password.getException() != null) {
				logger.warn("base.xml error of passwordCrypt in proxy tag, mail password not used");
				password = null;
			}
		}
		return de.sgollmer.solvismax.mail.Proxy.of(dto.host, dto.port, dto.user, password);
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
		if (features != null) {
			if (features.feature != null) {
				for (final BaseDataDto.FeatureDto feature : features.feature) {
					map.put(feature.id, feature.value);
				}
			}
			// Benannte Alt-Form (<ClockTuning>true</ClockTuning>) — gleiche Map-
			// Schluessel wie die generische Form; bei (praktisch nicht
			// vorkommender) Doppelung gewinnt hier die benannte Form.
			putIfSet(map, "ClockTuning", features.clockTuning);
			putIfSet(map, "EquipmentTimeSynchronisation", features.equipmentTimeSynchronisation);
			putIfSet(map, "UpdateAfterUserAccess", features.updateAfterUserAccess);
			putIfSet(map, "DetectServiceAccess", features.detectServiceAccess);
			putIfSet(map, "EndOfUserInterventionDetectionThroughScreenSaver",
					features.endOfUserInterventionDetectionThroughScreenSaver);
			putIfSet(map, "PowerOffIsServiceAccess", features.powerOffIsServiceAccess);
			putIfSet(map, "SendMailOnError", features.sendMailOnError);
			putIfSet(map, "SendMailOnErrorsCleared", features.sendMailOnErrorsCleared);
			putIfSet(map, "ClearErrorMessageAfterMail", features.clearErrorMessageAfterMail);
			putIfSet(map, "OnlyMeasurements", features.onlyMeasurements);
			putIfSet(map, "InteractiveGUIAccess", features.interactiveGUIAccess);
			putIfSet(map, "Admin", features.admin);
		}
		return java.util.Collections.unmodifiableMap(map);
	}

	private static void putIfSet(final Map<String, Boolean> map, final String id, final Boolean value) {
		if (value != null) {
			map.put(id, value);
		}
	}

	/**
	 * Abgeleitete Sicht: das Mess-Intervall in <b>Millisekunden</b> aus dem
	 * kanonisch gebundenen Sekunden-Rohwert {@code measurementsInterval_s}.
	 *
	 * <p>
	 * Weg B (DTOs als Config-Modell): Die Einheitenumrechnung (×1000) — die der
	 * alte Parser inline vornahm — wird hier als explizite, testbare Sicht-Methode
	 * auf dem DTO bereitgestellt, statt sie in die Bindung zu ziehen. Alternativ
	 * erlaubt das Schema das deprecated, bereits in ms vorliegende Attribut
	 * {@code defaultReadMeasurementsInterval_ms}; sind beide gesetzt, gewinnt die
	 * nicht-deprecated Sekunden-Form. Fehlen beide, wirft die Sicht — der alte
	 * Parser lehnte solche Dateien mit {@code XmlException} ab.
	 * </p>
	 */
	public static int measurementsIntervalMs(final BaseDataDto.UnitDto unit) {
		if (unit.measurementsInterval_s != null) {
			return unit.measurementsInterval_s * 1000;
		}
		if (unit.defaultReadMeasurementsInterval_ms != null) {
			return unit.defaultReadMeasurementsInterval_ms;
		}
		throw new IllegalStateException("<defaultMeasurementsInterval_s> is missing in base.xml");
	}

	/**
	 * DTO-gestützte {@link MqttTopicConfig}-Sicht (Weg B, Konsumenten-Pilot):
	 * liefert die für den Topic-Aufbau nötige Config direkt aus dem
	 * {@link BaseDataDto.MqttDto} — ohne die laufzeitgekoppelte Domänenklasse
	 * {@code Mqtt}. Der Konsument {@code TopicType.getTopicParts} kann damit von
	 * dieser Sicht ODER vom Domänenobjekt gespeist werden (beide erfüllen das
	 * Interface, verifiziert im Dual-Parse-Test).
	 */
	public static MqttTopicConfig topicConfig(final BaseDataDto.MqttDto dto) {
		return new MqttTopicConfig() {
			@Override
			public String getTopicPrefix() {
				return dto.topicPrefix;
			}

			@Override
			public String getSmartHomeId() {
				return dto.smartHomeId;
			}
		};
	}

	/**
	 * DTO-gestützte {@link ExecutionConfig}-Sicht (Weg B, Konsumenten-Migration
	 * nach dem Pilotmuster): liefert die flachen Ausführungswerte direkt aus dem
	 * {@link BaseDataDto.ExecutionDataDto} — ohne die Aggregat-Klasse
	 * {@code BaseData}. Konsumenten ({@code Main}, {@code Instances}) können damit
	 * von dieser Sicht ODER vom Domänenobjekt gespeist werden.
	 *
	 * <p>
	 * {@code getWritablePath()} ist eine <b>abgeleitete</b> Sicht: die OS-Weiche
	 * zwischen {@code writeablePathWindows} und {@code writablePathLinux}, die die
	 * Domäne in {@code BaseData.getWritablePath()} inline vornimmt — hier explizit
	 * und testbar.
	 * </p>
	 */
	public static ExecutionConfig executionConfig(final BaseDataDto.ExecutionDataDto dto) {
		return new ExecutionConfig() {
			@Override
			public String getTimeZone() {
				return dto.timeZone;
			}

			@Override
			public int getPort() {
				return dto.port;
			}

			@Override
			public String getWritablePath() {
				final boolean windows = System.getProperty("os.name").startsWith("Windows");
				return windows ? dto.writeablePathWindows : dto.writablePathLinux;
			}

			@Override
			public int getEchoInhibitTime_ms() {
				return dto.echoInhibitTime_ms;
			}
		};
	}

	/**
	 * DTO-gestützte {@link MqttConnectionConfig}-Sicht (Weg B,
	 * Konsumenten-Migration nach dem Pilotmuster): liefert die für den
	 * Broker-Verbindungsaufbau nötige Config direkt aus dem
	 * {@link BaseDataDto.MqttDto} — ohne die laufzeitgekoppelte Domänenklasse
	 * {@code Mqtt}. Der Konsument {@code MqttThread} kann damit von dieser Sicht
	 * ODER vom Domänenobjekt gespeist werden (beide erfüllen das Interface,
	 * verifiziert im Dual-Parse-Test).
	 *
	 * <p>
	 * {@code passwordCrypt} wird — wie in {@link #toMqtt} — hier explizit
	 * entschlüsselt. Schlägt das fehl, bleibt die {@link CryptAes} ungesetzt
	 * ({@code cP() == null}); im alten Fluss wird MQTT in diesem Fall ohnehin
	 * deaktiviert, sodass der Verbindungsaufbau nie stattfindet.
	 * </p>
	 */
	public static MqttConnectionConfig connectionConfig(final BaseDataDto.MqttDto dto) {
		final CryptAes passwordCrypt = toCryptAes(dto.passwordCrypt);
		final Ssl ssl = toSsl(dto.ssl);
		return new MqttConnectionConfig() {
			@Override
			public String getUserName() {
				return dto.userName;
			}

			@Override
			public CryptAes getPasswordCrypt() {
				return passwordCrypt;
			}

			@Override
			public Ssl getSsl() {
				return ssl;
			}

			@Override
			public String getTopicPrefix() {
				return dto.topicPrefix;
			}

			@Override
			public int getPublishQoS() {
				return dto.publishQoS;
			}

			@Override
			public int getSubscribeQoS() {
				return dto.subscribeQoS;
			}
		};
	}

	/**
	 * DTO-gestützte {@link UnitConfig}-Sicht (Weg B, Konsumenten-Migration nach
	 * dem Pilotmuster): liefert die flachen Unit-Skalarwerte direkt aus dem
	 * {@link BaseDataDto.UnitDto} — ohne das tiefe Domänen-Aggregat {@code Unit}.
	 * Enthält die abgeleiteten Sichten Sekunden→Millisekunden (×1000, siehe
	 * {@link #measurementsIntervalMs}) und {@code isBuffered()}
	 * ({@code bufferedInterval_ms > 0}, wie {@code Unit.isBuffered()}).
	 */
	public static UnitConfig unitConfig(final BaseDataDto.UnitDto dto) {
		return new UnitConfig() {
			@Override
			public String getId() {
				return dto.id;
			}

			@Override
			public int getDefaultAverageCount() {
				return dto.defaultAverageCount;
			}

			@Override
			public int getMeasurementHysteresisFactor() {
				return dto.measurementHysteresisFactor;
			}

			@Override
			public int getMeasurementsInterval_ms() {
				return measurementsIntervalMs(dto);
			}

			@Override
			public int getMeasurementsIntervalFast_ms() {
				return measurementsIntervalFastMs(dto);
			}

			@Override
			public int getForceUpdateAfterFastChangingIntervals() {
				return dto.forceUpdateAfterFastChangingIntervals;
			}

			@Override
			public int getForcedUpdateInterval_ms() {
				return dto.forcedUpdateInterval_ms;
			}

			@Override
			public int getDoubleUpdateInterval_ms() {
				return dto.doubleUpdateInterval_ms;
			}

			@Override
			public int getBufferedInterval_ms() {
				return dto.bufferedInterval_ms;
			}

			@Override
			public boolean isBuffered() {
				return dto.bufferedInterval_ms > 0;
			}

			@Override
			public int getWatchDogTime_ms() {
				return dto.watchDogTime_ms;
			}

			@Override
			public int getReleaseBlockingAfterUserAccess_ms() {
				return dto.releaseBlockingAfterUserAccess_ms;
			}

			@Override
			public int getReleaseBlockingAfterServiceAccess_ms() {
				return dto.releaseBlockingAfterServiceAccess_ms;
			}

			@Override
			public int getReheatingNotRequiredActiveTime_ms() {
				return dto.reheatingNotRequiredActiveTime_ms;
			}

			@Override
			public int getResetErrorDelayTime() {
				return dto.resetErrorDelayTime_ms;
			}

			@Override
			public boolean isDelayAfterSwitchingOnEnable() {
				return dto.delayAfterSwitchingOnEnable;
			}

			@Override
			public boolean isFwLth2_21_02A() {
				return dto.fwLth2_21_02A;
			}

			@Override
			public int getIgnoredFrameThicknesScreenSaver() {
				return dto.ignoredFrameThicknesScreenSaver;
			}
		};
	}

	/**
	 * Abgeleitete Sicht: schnelles Mess-Intervall in Millisekunden (×1000).
	 *
	 * <p>
	 * Default-Semantik des alten Parsers gespiegelt: Fehlt
	 * {@code measurementsIntervalFast_s} (DTO-Feld {@code null}), gilt das
	 * normale Mess-Intervall ({@code Unit.Creator.create()} setzte in dem Fall
	 * {@code fast = defaultMeasurementsInterval_ms}).
	 * </p>
	 */
	public static int measurementsIntervalFastMs(final BaseDataDto.UnitDto unit) {
		if (unit.measurementsIntervalFast_s != null) {
			return unit.measurementsIntervalFast_s * 1000;
		}
		return measurementsIntervalMs(unit);
	}

	/**
	 * Abgeleitete Sicht: das gebundene {@code type}-Attribut ({@code TO|CC|BCC})
	 * eines Empfängers als {@link jakarta.mail.Message.RecipientType}.
	 *
	 * <p>
	 * Weg B: Das Enum-Mapping — das der alte Parser inline vornahm — wird hier als
	 * explizite Sicht bereitgestellt. {@code null} bei fehlendem/unbekanntem Typ
	 * (wie die frühere {@code recipientTypeMap.get(...)}-Semantik).
	 * </p>
	 */
	public static jakarta.mail.Message.RecipientType recipientType(final BaseDataDto.RecipientDto recipient) {
		if (recipient == null || recipient.type == null) {
			return null;
		}
		switch (recipient.type) {
			case "TO":
				return jakarta.mail.Message.RecipientType.TO;
			case "CC":
				return jakarta.mail.Message.RecipientType.CC;
			case "BCC":
				return jakarta.mail.Message.RecipientType.BCC;
			default:
				return null;
		}
	}

	/**
	 * Abgeleitete Sicht: entschlüsselt ein {@code passwordCrypt}-Attribut in eine
	 * {@link CryptAes} — die gemeinsame Ableitung für Mqtt, Unit und
	 * ExceptionMail (alle drei Creators taten dasselbe inline). Schlägt die
	 * Entschlüsselung fehl, bleibt das Passwort ungesetzt; der Fehler ist am
	 * Objekt abfragbar ({@code getException()}) — die konsumentenspezifische
	 * Reaktion (MQTT deaktivieren, Mail deaktivieren, Fehler loggen) liegt beim
	 * jeweiligen Aufrufer.
	 */
	public static CryptAes toCryptAes(final String passwordCrypt) {
		final CryptAes crypt = new CryptAes();
		if (passwordCrypt != null) {
			try {
				crypt.decrypt(passwordCrypt);
			} catch (final CryptException e) {
				// Zustand am Objekt (getException()); Reaktion beim Aufrufer.
			}
		}
		return crypt;
	}

	/**
	 * Baut den Wert-Typ {@link de.sgollmer.solvismax.smarthome.IoBroker} aus dem
	 * DTO (Weg B). <b>Element-Default:</b> Fehlt das ganze {@code <Iobroker>}-
	 * Element ({@code dto == null}), entsteht — wie beim alten Parser — eine
	 * Default-Instanz mit den Standard-Interfaces.
	 */
	public static de.sgollmer.solvismax.smarthome.IoBroker toIoBroker(final BaseDataDto.IobrokerDto dto) {
		if (dto == null) {
			return new de.sgollmer.solvismax.smarthome.IoBroker();
		}
		return de.sgollmer.solvismax.smarthome.IoBroker.of(dto.mqttInterface, dto.javascriptInterface);
	}

	/** Kanonische Sicht: {@code <Urls>}-Liste (leer, wenn Element fehlt). */
	public static List<String> toUrls(final BaseDataDto.UrlsDto dto) {
		if (dto == null || dto.url == null) {
			return java.util.Collections.emptyList();
		}
		return java.util.Collections.unmodifiableList(dto.url);
	}

	/**
	 * Abgeleitete Sicht: {@code <IgnoredChannels>}-RegEx-Liste als kompilierte
	 * {@link Pattern} (Creator-Semantik: ungültige Ausdrücke → XmlException).
	 */
	public static List<Pattern> toIgnoredChannels(final BaseDataDto.IgnoredChannelsDto dto) throws XmlException {
		final List<Pattern> patterns = new ArrayList<>();
		if (dto != null && dto.regEx != null) {
			for (final String regEx : dto.regEx) {
				try {
					patterns.add(Pattern.compile(regEx));
				} catch (final PatternSyntaxException e) {
					throw new XmlException("Regular expression error on expression: " + regEx);
				}
			}
		}
		return patterns;
	}

	/** Baut den Wert-Typ {@link AllDurations} aus dem DTO (via Factories). */
	public static AllDurations toDurations(final BaseDataDto.DurationsDto dto) {
		final List<de.sgollmer.solvismax.model.objects.Duration> durations = new ArrayList<>();
		if (dto != null && dto.duration != null) {
			for (final BaseDataDto.DurationDto d : dto.duration) {
				durations.add(de.sgollmer.solvismax.model.objects.Duration.of(d.id, d.time_ms));
			}
		}
		return AllDurations.of(durations);
	}

	/**
	 * Bildet die {@code <ChannelAssignments>} auf die Domänen-Map
	 * ({@code Assignment-Id → ChannelAssignment}) ab. Wie beim alten Parser:
	 * Schlüssel ist {@code getName()} (= die Id), Dubletten sind ein Fehler.
	 * Die von der base.xsd nicht erlaubten Creator-Felder (alias, booleanValue,
	 * Configuration) bleiben {@code null}.
	 */
	public static Map<String, ChannelAssignment> toChannelAssignments(final BaseDataDto.ChannelAssignmentsDto dto)
			throws XmlException {
		if (dto == null || dto.assignment == null) {
			return null; // wie der Creator: kein Element -> null-Map
		}
		final Map<String, ChannelAssignment> assignments = new LinkedHashMap<>();
		for (final BaseDataDto.AssignmentDto a : dto.assignment) {
			final ChannelAssignment assignment = new ChannelAssignment(a.id, a.name, null, a.unit, null, null);
			final ChannelAssignment former = assignments.put(assignment.getName(), assignment);
			if (former != null) {
				throw new XmlException("base.xml error, <" + assignment.getName() + "> isn't unique.");
			}
		}
		return assignments;
	}

	/**
	 * Baut den Wert-Typ {@link de.sgollmer.solvismax.model.objects.unit.Configuration}
	 * aus den Unit-Attributen + {@code <Extensions>} (Weg B). {@code solarType}
	 * ist in der base.xsd nicht deklariert (toter Creator-Pfad) → {@code null}.
	 */
	public static de.sgollmer.solvismax.model.objects.unit.Configuration toConfiguration(
			final BaseDataDto.UnitDto unit) {
		// Wie der alte ExtensionsCreator: ein VORHANDENES <Extensions>-Element
		// ergibt eine (ggf. leere) Liste; nur ein fehlendes Element ergibt null.
		List<String> extensions = null;
		if (unit.extensions != null) {
			extensions = new ArrayList<>();
			if (unit.extensions.extension != null) {
				for (final BaseDataDto.ExtensionDto e : unit.extensions.extension) {
					extensions.add(e.id);
				}
			}
		}
		return de.sgollmer.solvismax.model.objects.unit.Configuration.of(unit.type, unit.mainHeating,
				unit.heatingCircuits, null, extensions);
	}

	/**
	 * Baut den Wert-Typ {@link Features} aus dem DTO (Weg B, Aggregat-Zweige):
	 * {@code Features} bleibt als reiner Wert-Typ erhalten, damit die
	 * Feature-Semantik (Defaults je Feature, Regel „genau eines von
	 * InteractiveGUIAccess/OnlyMeasurements", abgeleitete Abfragen wie
	 * {@code isSendMailOnErrorsCleared}) nur an <b>einer</b> Stelle lebt. Der
	 * Mapper liefert nur die kanonische Map; die Validierung wirft — wie der
	 * alte Parser — bei Regelverletzung.
	 */
	public static Features toFeatures(final BaseDataDto.FeaturesDto dto) throws XmlException {
		return Features.of(featuresToMap(dto));
	}

	/**
	 * Abgeleitete Sicht: {@code powerOnDelay_s} → Millisekunden (×1000), Default
	 * −1 bei fehlendem Attribut (Creator-Semantik von {@code ChannelOption}).
	 */
	public static int powerOnDelayMs(final BaseDataDto.ChannelDto channel) {
		return channel.powerOnDelay_s != null ? channel.powerOnDelay_s * 1000 : -1;
	}

	/**
	 * Bildet die kanonisch gebundene {@code <Channel>}-Liste auf
	 * {@link AllChannelOptions.ChannelOption}-Wert-Objekte ab (Weg B,
	 * Aggregat-Zweige). {@code fix}/{@code factor}/{@code offset} sind nullable
	 * ({@code null} = nicht gesetzt — steuert die {@code modify()}-Semantik),
	 * {@code powerOnDelay_s} wird über {@link #powerOnDelayMs} abgeleitet.
	 */
	public static List<AllChannelOptions.ChannelOption> toChannelOptionList(final BaseDataDto.ChannelOptionsDto dto) {
		final List<AllChannelOptions.ChannelOption> options = new ArrayList<>();
		if (dto != null && dto.channel != null) {
			for (final BaseDataDto.ChannelDto c : dto.channel) {
				// 5. Parameter wie im alten Creator (dort ungenutzt uebergeben).
				options.add(new AllChannelOptions.ChannelOption(c.id, c.fix, c.factor, c.offset, c.factor,
						powerOnDelayMs(c)));
			}
		}
		return options;
	}

	/** Baut den Wert-Typ {@link AllChannelOptions} aus dem DTO (via Factory). */
	public static AllChannelOptions toChannelOptions(final BaseDataDto.ChannelOptionsDto dto) {
		return AllChannelOptions.of(toChannelOptionList(dto));
	}

	/**
	 * Bildet die kanonisch gebundene {@code <Ssl>}-Struktur auf das Domänenobjekt
	 * {@link Ssl} ab (mTLS-Konfiguration). {@code null}, wenn kein Ssl-Element
	 * vorhanden ist.
	 */
	public static Ssl toSsl(final BaseDataDto.SslDto dto) {
		if (dto == null) {
			return null;
		}
		return Ssl.create(dto.enable, dto.caFilePath, dto.clientCrtFilePath, dto.clientKeyFilePath);
	}

	/**
	 * Bildet die kanonisch gebundene {@code <Mqtt>}-Struktur auf das
	 * Domänenobjekt {@link Mqtt} ab.
	 *
	 * <p>
	 * Beispiel für „kanonisch binden + explizit ableiten": {@code passwordCrypt}
	 * wird von JAXB als roher String gebunden; hier erfolgt — sichtbar und
	 * testbar — die Entschlüsselung in {@link CryptAes}. Schlägt sie fehl, wird
	 * MQTT deaktiviert; das reproduziert exakt das Verhalten des bisherigen
	 * Parsers (der Creator fing {@code CryptException} und setzte
	 * {@code enable=false}).
	 * </p>
	 */
	public static Mqtt toMqtt(final BaseDataDto.MqttDto dto) {
		if (dto == null) {
			return null;
		}
		final CryptAes passwordCrypt = toCryptAes(dto.passwordCrypt);
		// Wie der alte Parser: ungültiges passwordCrypt -> MQTT aus.
		final boolean enable = dto.enable && passwordCrypt.getException() == null;
		return Mqtt.of(enable, dto.brokerUrl, dto.port, dto.userName, passwordCrypt, dto.topicPrefix,
				dto.idPrefix, dto.smartHomeId, dto.publishQoS, dto.subscribeQoS, toSsl(dto.ssl));
	}
}
