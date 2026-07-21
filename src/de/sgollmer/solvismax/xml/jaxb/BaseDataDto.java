package de.sgollmer.solvismax.xml.jaxb;

import java.util.List;

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
		@XmlAttribute(name = "reheatingNotRequiredActiveTime_ms") public int reheatingNotRequiredActiveTime_ms;
		@XmlAttribute(name = "delayAfterSwitchingOnEnable") public boolean delayAfterSwitchingOnEnable;
		// Hinweis: measurementsInterval_s (Sekunden) wird von der Domaene nach
		// _ms umgerechnet und resetErrorDelayTime_ms hat keinen Getter -> beide
		// werden spaeter ueber den Mapper bzw. Charakterisierung abgesichert.

		@XmlElement(name = "Features")
		public FeaturesDto features;
	}

	/** JAXB-Bean für {@code <Features>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class FeaturesDto {
		@XmlElement(name = "Feature")
		public List<FeatureDto> feature;
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

	/** JAXB-Bean für {@code <Iobroker>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class IobrokerDto {
		@XmlAttribute(name = "mqttInterface") public String mqttInterface;
		@XmlAttribute(name = "javascriptInterface") public String javascriptInterface;
	}
}
