package de.sgollmer.solvismax.connection.mqtt;

/**
 * Schmale <b>Config-Sicht</b> auf die für den Topic-Aufbau nötigen
 * MQTT-Konfigurationswerte — der erste Baustein der Konsumenten-Entkopplung
 * (Weg B, MODERNISIERUNG.md 3.3).
 *
 * <p>
 * Hintergrund: Die Domänenklasse {@link Mqtt} vermischt <em>Konfiguration</em>
 * und <em>Laufzeitverhalten</em> (Client, Queue). Konsumenten, die nur die
 * Konfiguration lesen, sollen aber nicht von der ganzen (laufzeitgekoppelten)
 * Klasse abhängen. Diese Schnittstelle kapselt genau die vom Topic-Aufbau
 * (`TopicType.getTopicParts`) benötigten Werte.
 * </p>
 *
 * <p>
 * <b>Zwei Quellen, ein Konsument:</b> Sowohl das Domänenobjekt {@link Mqtt} als
 * auch eine DTO-gestützte Sicht (aus dem JAXB-Parser) erfüllen diese
 * Schnittstelle. Der Konsument hängt damit nur noch an der Sicht, nicht mehr an
 * der konkreten Config-Klasse — die Voraussetzung dafür, die versiegelten
 * Domänen-Config-Klassen später abzulösen.
 * </p>
 */
public interface MqttTopicConfig {

	/** MQTT-Topic-Präfix des SmartHomeServers. */
	String getTopicPrefix();

	/** SmartHome-Id (nur für die Doku-/Meta-Topics genutzt). */
	String getSmartHomeId();
}
