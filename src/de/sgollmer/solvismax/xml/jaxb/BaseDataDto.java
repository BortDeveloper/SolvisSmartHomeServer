package de.sgollmer.solvismax.xml.jaxb;

import java.util.List;

import de.sgollmer.solvismax.Constants;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * JAXB-DTO für das Wurzelelement {@code <BaseData>} der {@code base.xml}.
 *
 * <p>
 * Mutable JAXB-Bean (im Gegensatz zu den immutablen Domänenklassen). Bildet die
 * XML-Struktur ab; die Werte werden vom {@code Unmarshaller} gesetzt. Siehe
 * {@link de.sgollmer.solvismax.xml.jaxb}.
 * </p>
 *
 * <h2>Default-Werte fehlender Attribute (Standard-Mechanismus)</h2>
 * Der JAXB-Unmarshaller setzt nur Felder, deren Attribut im XML vorhanden ist —
 * <b>Feld-Initialisierer</b> sind daher der standardkonforme Ort für Defaults
 * (exakt das Muster der alten {@code Creator}-Felder, die {@code setAttribute}
 * nur bei vorhandenem Attribut überschrieb). Die base.xsd deklariert keine
 * {@code default=}-Werte; maßgeblich sind die Creator-Defaults des alten
 * Parsers, die hier gespiegelt und per Dual-Parse-Test an einer
 * Minimal-Fixture (ohne die optionalen Attribute) verifiziert sind.
 */
@XmlRootElement(name = "BaseData")
@XmlAccessorType(XmlAccessType.FIELD)
public class BaseDataDto {

	@XmlElement(name = "ExecutionData")
	public ExecutionDataDto executionData;

	@XmlElement(name = "Units")
	public UnitsDto units;

	@XmlElement(name = "Mqtt")
	public MqttDto mqtt;

	@XmlElement(name = "ExceptionMail")
	public ExceptionMailDto exceptionMail;

	@XmlElement(name = "Iobroker")
	public IobrokerDto iobroker;

	/** JAXB-Bean für {@code <ExecutionData>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ExecutionDataDto {
		@XmlAttribute(name = "timeZone") public String timeZone;
		@XmlAttribute(name = "port") public int port;
		@XmlAttribute(name = "writeablePathWindows") public String writeablePathWindows;
		@XmlAttribute(name = "writablePathLinux") public String writablePathLinux;
		@XmlAttribute(name = "echoInhibitTime_ms") public int echoInhibitTime_ms;
	}

	/** JAXB-Bean für {@code <Units>} (Sammlung von {@code <Unit>}). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class UnitsDto {
		@XmlElement(name = "Unit")
		public List<UnitDto> unit;
	}

	/** JAXB-Bean für {@code <Unit>} (Kernattribute; erweiterbar). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class UnitDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "type") public String type;
		@XmlAttribute(name = "mainHeating") public String mainHeating;
		@XmlAttribute(name = "heatingCircuits") public int heatingCircuits;
		@XmlAttribute(name = "account") public String account;
		@XmlAttribute(name = "url") public String url;
		@XmlAttribute(name = "passwordCrypt") public String passwordCrypt;
		@XmlAttribute(name = "defaultAverageCount") public int defaultAverageCount;
		@XmlAttribute(name = "measurementHysteresisFactor") public int measurementHysteresisFactor;
		@XmlAttribute(name = "watchDogTime_ms") public int watchDogTime_ms;
		@XmlAttribute(name = "fwLth2_21_02A") public boolean fwLth2_21_02A;
		@XmlAttribute(name = "ignoredFrameThicknesScreenSaver") public int ignoredFrameThicknesScreenSaver;
		// Weitere Intervall-/Verzoegerungsattribute (Domaene liefert sie 1:1,
		// keine Einheitenumrechnung -> direkt dual-parse-vergleichbar).
		@XmlAttribute(name = "forcedUpdateInterval_ms") public int forcedUpdateInterval_ms;
		@XmlAttribute(name = "doubleUpdateInterval_ms") public int doubleUpdateInterval_ms;
		@XmlAttribute(name = "bufferedInterval_ms") public int bufferedInterval_ms;
		@XmlAttribute(name = "releaseBlockingAfterUserAccess_ms") public int releaseBlockingAfterUserAccess_ms;
		@XmlAttribute(name = "releaseBlockingAfterServiceAccess_ms") public int releaseBlockingAfterServiceAccess_ms;
		@XmlAttribute(name = "reheatingNotRequiredActiveTime_ms")
		public int reheatingNotRequiredActiveTime_ms = Constants.Defaults.REHEATING_NOT_REQUIRED_ACTIVE_TIME;
		@XmlAttribute(name = "delayAfterSwitchingOnEnable") public boolean delayAfterSwitchingOnEnable;
		// Rohwert in Sekunden — die Domäne rechnet nach Millisekunden um; diese
		// Ableitung liefert Mapper.measurementsIntervalMs (Weg B: abgeleitete
		// Sicht). Integer: Alternativ erlaubt das Schema das (deprecated, bereits
		// in ms vorliegende) Attribut defaultReadMeasurementsInterval_ms — die
		// Aufloesung beider Formen liegt im Mapper.
		@XmlAttribute(name = "measurementsInterval_s") public Integer measurementsInterval_s;
		@XmlAttribute(name = "defaultReadMeasurementsInterval_ms") public Integer defaultReadMeasurementsInterval_ms;
		// Integer statt int: Fehlt das Attribut, faellt der Wert auf
		// measurementsInterval_s zurueck (Creator-Semantik) — der Fallback braucht
		// die Unterscheidung "nicht gesetzt" (null) und liegt in
		// Mapper.measurementsIntervalFastMs.
		@XmlAttribute(name = "measurementsIntervalFast_s") public Integer measurementsIntervalFast_s;
		// Nach der Fork-Korrektur des Creator-Tippfehlers wieder 1:1-vergleichbar.
		@XmlAttribute(name = "forceUpdateAfterFastChangingIntervals")
		public int forceUpdateAfterFastChangingIntervals = Constants.FORCE_UPDATE_AFTER_N_INTERVALS;
		@XmlAttribute(name = "resetErrorDelayTime_ms") public int resetErrorDelayTime_ms;

		@XmlElement(name = "Features")
		public FeaturesDto features;

		@XmlElement(name = "ChannelOptions")
		public ChannelOptionsDto channelOptions;

		@XmlElement(name = "Urls")
		public UrlsDto urls;

		@XmlElement(name = "IgnoredChannels")
		public IgnoredChannelsDto ignoredChannels;

		@XmlElement(name = "ChannelAssignments")
		public ChannelAssignmentsDto channelAssignments;

		@XmlElement(name = "Durations")
		public DurationsDto durations;

		@XmlElement(name = "Extensions")
		public ExtensionsDto extensions;
	}

	/** JAXB-Bean für {@code <Urls>} — Liste von {@code <Url>Text</Url>}-Elementen. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class UrlsDto {
		@XmlElement(name = "Url")
		public List<String> url;
	}

	/** JAXB-Bean für {@code <IgnoredChannels>} — Liste von {@code <RegEx>Muster</RegEx>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class IgnoredChannelsDto {
		@XmlElement(name = "RegEx")
		public List<String> regEx;
	}

	/** JAXB-Bean für {@code <ChannelAssignments>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ChannelAssignmentsDto {
		@XmlElement(name = "Assignment")
		public List<AssignmentDto> assignment;
	}

	/**
	 * JAXB-Bean für {@code <Assignment id name [unit]/>}. Die base.xsd erlaubt
	 * nur diese drei Attribute; die weiteren vom alten Creator gelesenen Felder
	 * (alias, booleanValue, Configuration-Kind) sind in XSD-validen base.xml
	 * nicht möglich und daher bewusst nicht gebunden.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class AssignmentDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "name") public String name;
		@XmlAttribute(name = "unit") public String unit;
	}

	/** JAXB-Bean für {@code <Durations>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class DurationsDto {
		@XmlElement(name = "Duration")
		public List<DurationDto> duration;
	}

	/** JAXB-Bean für {@code <Duration id time_ms/>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class DurationDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "time_ms") public int time_ms;
	}

	/** JAXB-Bean für {@code <Extensions>} (Anlagen-Erweiterungen der Unit). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ExtensionsDto {
		@XmlElement(name = "Extension")
		public List<ExtensionDto> extension;
	}

	/** JAXB-Bean für {@code <Extension id="..."/>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ExtensionDto {
		@XmlAttribute(name = "id") public String id;
	}

	/** JAXB-Bean für {@code <ChannelOptions>} (Sammlung von {@code <Channel>}). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ChannelOptionsDto {
		@XmlElement(name = "Channel")
		public List<ChannelDto> channel;
	}

	/**
	 * JAXB-Bean für {@code <Channel>} — Kanal-Sonderoption. Pro Eintrag ist i. d. R.
	 * nur eines von {@code fix}/{@code offset}/{@code powerOnDelay_s} gesetzt.
	 * Nullable Wrapper-Typen ({@code null} = Attribut fehlt), weil der alte
	 * Parser {@code fix}/{@code factor}/{@code offset} als {@code Double} mit
	 * {@code null}-Semantik bindet — {@code ChannelOption.modify()} unterscheidet
	 * „nicht gesetzt" von {@code 0}. {@code powerOnDelay_s}: Sekunden-Rohwert,
	 * die ×1000-Ableitung (Default −1 bei fehlendem Attribut) liegt in
	 * {@code Mapper.powerOnDelayMs}.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ChannelDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "fix") public Double fix;
		@XmlAttribute(name = "factor") public Double factor;
		@XmlAttribute(name = "offset") public Double offset;
		@XmlAttribute(name = "powerOnDelay_s") public Integer powerOnDelay_s;
	}

	/**
	 * JAXB-Bean für {@code <Features>}. Das Schema erlaubt zwei Formen: die
	 * generische ({@code <Feature id="..." value="..."/>}) und <b>benannte
	 * Elemente</b> ({@code <ClockTuning>true</ClockTuning>}, Alt-Form). Beide
	 * sind gebunden; {@code Mapper.featuresToMap} führt sie zusammen (benannte
	 * Form überschreibt bei — praktisch nicht vorkommender — Doppelung dieselbe
	 * Id; der alte Parser entschied dort nach Dokumentreihenfolge).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class FeaturesDto {
		@XmlElement(name = "Feature")
		public List<FeatureDto> feature;

		@XmlElement(name = "ClockTuning") public Boolean clockTuning;
		@XmlElement(name = "EquipmentTimeSynchronisation") public Boolean equipmentTimeSynchronisation;
		@XmlElement(name = "UpdateAfterUserAccess") public Boolean updateAfterUserAccess;
		@XmlElement(name = "DetectServiceAccess") public Boolean detectServiceAccess;
		@XmlElement(name = "EndOfUserInterventionDetectionThroughScreenSaver")
		public Boolean endOfUserInterventionDetectionThroughScreenSaver;
		@XmlElement(name = "PowerOffIsServiceAccess") public Boolean powerOffIsServiceAccess;
		@XmlElement(name = "SendMailOnError") public Boolean sendMailOnError;
		@XmlElement(name = "SendMailOnErrorsCleared") public Boolean sendMailOnErrorsCleared;
		@XmlElement(name = "ClearErrorMessageAfterMail") public Boolean clearErrorMessageAfterMail;
		@XmlElement(name = "OnlyMeasurements") public Boolean onlyMeasurements;
		@XmlElement(name = "InteractiveGUIAccess") public Boolean interactiveGUIAccess;
		@XmlElement(name = "Admin") public Boolean admin;
	}

	/** JAXB-Bean für {@code <Feature id="..." value="..."/>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class FeatureDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "value") public boolean value;
	}

	/** JAXB-Bean für {@code <Mqtt>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class MqttDto {
		@XmlAttribute(name = "enable") public boolean enable;
		@XmlAttribute(name = "brokerUrl") public String brokerUrl;
		@XmlAttribute(name = "port") public int port;
		@XmlAttribute(name = "userName") public String userName;
		@XmlAttribute(name = "passwordCrypt") public String passwordCrypt;
		@XmlAttribute(name = "topicPrefix") public String topicPrefix;
		@XmlAttribute(name = "idPrefix") public String idPrefix;
		@XmlAttribute(name = "smartHomeId") public String smartHomeId;
		@XmlAttribute(name = "publishQoS") public int publishQoS;
		@XmlAttribute(name = "subscribeQoS") public int subscribeQoS;

		@XmlElement(name = "Ssl")
		public SslDto ssl;
	}

	/** JAXB-Bean für {@code <Ssl>} (Fork-Element für mTLS). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class SslDto {
		@XmlAttribute(name = "enable") public boolean enable;
		@XmlAttribute(name = "caFilePath") public String caFilePath;
		@XmlAttribute(name = "clientCrtFilePath") public String clientCrtFilePath;
		@XmlAttribute(name = "clientKeyFilePath") public String clientKeyFilePath;
	}

	/**
	 * JAXB-Bean für {@code <ExceptionMail>} (Fehler-Benachrichtigung per Mail).
	 *
	 * <p>
	 * Die Domänenklasse {@code mail.ExceptionMail} legt diese Werte nicht über
	 * Getter offen; die JAXB-Bindung wird daher per <em>Charakterisierung</em>
	 * gegen die Vorlagenwerte abgesichert (statt Dual-Parse gegen die Domäne).
	 * {@code passwordCrypt} wird — wie beim {@code Mqtt} — als roher String
	 * gebunden; die Entschlüsselung ist eine spätere Mapper-Ableitung.
	 * </p>
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ExceptionMailDto {
		@XmlAttribute(name = "name") public String name;
		@XmlAttribute(name = "from") public String from;
		@XmlAttribute(name = "passwordCrypt") public String passwordCrypt;
		@XmlAttribute(name = "securityType") public String securityType;
		@XmlAttribute(name = "provider") public String provider;
		@XmlAttribute(name = "port") public int port;

		@XmlElement(name = "Recipients")
		public RecipientsDto recipients;
	}

	/** JAXB-Bean für {@code <Recipients>} (Sammlung von {@code <Recipient>}). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class RecipientsDto {
		@XmlElement(name = "Recipient")
		public List<RecipientDto> recipient;
	}

	/** JAXB-Bean für {@code <Recipient name="..." address="..." type="..."/>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class RecipientDto {
		@XmlAttribute(name = "name") public String name;
		@XmlAttribute(name = "address") public String address;
		/** {@code TO|CC|BCC} — als String gebunden; das Enum-Mapping ist eine
		 *  spätere Mapper-Ableitung (kanonisch binden + explizit ableiten). */
		@XmlAttribute(name = "type") public String type;
	}

	/**
	 * JAXB-Bean für {@code <Iobroker>}. Die Initialisierer spiegeln die
	 * Creator-Defaults; fehlt das <b>ganze Element</b>, ersetzt der alte Parser
	 * es durch eine Default-Instanz — dieser Element-Default gehört beim
	 * Reader-Umstieg in die Sicht/den Mapper (DTO bleibt dann {@code null}).
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class IobrokerDto {
		@XmlAttribute(name = "mqttInterface")
		public String mqttInterface = Constants.IoBroker.DEFAULT_MQTT_INTERFACE;
		@XmlAttribute(name = "javascriptInterface")
		public String javascriptInterface = Constants.IoBroker.DEFAULT_JAVASCRIPT_INTERFACE;
	}
}
