package de.sgollmer.solvismax.xml.jaxb;

import java.util.LinkedHashMap;
import java.util.Map;

import de.sgollmer.solvismax.connection.mqtt.Mqtt;
import de.sgollmer.solvismax.connection.mqtt.MqttConnectionConfig;
import de.sgollmer.solvismax.connection.mqtt.MqttTopicConfig;
import de.sgollmer.solvismax.crypt.CryptAes;
import de.sgollmer.solvismax.crypt.Ssl;
import de.sgollmer.solvismax.error.CryptException;

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

	private Mapper() {
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
		if (features != null && features.feature != null) {
			for (final BaseDataDto.FeatureDto feature : features.feature) {
				map.put(feature.id, feature.value);
			}
		}
		return java.util.Collections.unmodifiableMap(map);
	}

	/**
	 * Abgeleitete Sicht: das Mess-Intervall in <b>Millisekunden</b> aus dem
	 * kanonisch gebundenen Sekunden-Rohwert {@code measurementsInterval_s}.
	 *
	 * <p>
	 * Weg B (DTOs als Config-Modell): Die Einheitenumrechnung (×1000) — die der
	 * alte Parser inline vornahm — wird hier als explizite, testbare Sicht-Methode
	 * auf dem DTO bereitgestellt, statt sie in die Bindung zu ziehen.
	 * </p>
	 */
	public static int measurementsIntervalMs(final BaseDataDto.UnitDto unit) {
		return unit.measurementsInterval_s * 1000;
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
		final CryptAes passwordCrypt = new CryptAes();
		if (dto.passwordCrypt != null) {
			try {
				passwordCrypt.decrypt(dto.passwordCrypt);
			} catch (final CryptException e) {
				// Wie der alte Parser: ungueltiges passwordCrypt bleibt ungesetzt.
			}
		}
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

	/** Abgeleitete Sicht: schnelles Mess-Intervall in Millisekunden (×1000). */
	public static int measurementsIntervalFastMs(final BaseDataDto.UnitDto unit) {
		return unit.measurementsIntervalFast_s * 1000;
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
		final CryptAes passwordCrypt = new CryptAes();
		boolean enable = dto.enable;
		if (dto.passwordCrypt != null) {
			try {
				passwordCrypt.decrypt(dto.passwordCrypt);
			} catch (final CryptException e) {
				// Wie der alte Parser: ungültiges passwordCrypt -> MQTT aus.
				enable = false;
			}
		}
		return Mqtt.of(enable, dto.brokerUrl, dto.port, dto.userName, passwordCrypt, dto.topicPrefix,
				dto.idPrefix, dto.smartHomeId, dto.publishQoS, dto.subscribeQoS, toSsl(dto.ssl));
	}
}
