package de.sgollmer.solvismax.connection.mqtt;

import de.sgollmer.solvismax.crypt.CryptAes;
import de.sgollmer.solvismax.crypt.Ssl;

/**
 * Schmale <b>Config-Sicht</b> auf die für den Broker-Verbindungsaufbau nötigen
 * MQTT-Konfigurationswerte — Fortsetzung der Konsumenten-Entkopplung nach dem
 * Pilotmuster (Weg B, MODERNISIERUNG.md 3.3).
 *
 * <p>
 * Konsument ist {@link MqttThread}: Er baut die {@code MqttConnectOptions}
 * (Benutzer/Passwort, TLS, Last-Will-QoS) und die Subscribe-Topic-Filter
 * (Topic-Präfix, Subscribe-QoS) ausschließlich aus diesen Werten. Seine
 * <em>Laufzeit</em>-Abhängigkeiten (Client, Callback, Last-Will-Daten) bleiben
 * bewusst an {@link Mqtt} — nur das Config-<em>Lesen</em> läuft über diese
 * Sicht.
 * </p>
 *
 * <p>
 * <b>Zwei Quellen, ein Konsument:</b> Sowohl das Domänenobjekt {@link Mqtt} als
 * auch eine DTO-gestützte Sicht (aus dem JAXB-Parser) erfüllen diese
 * Schnittstelle — die Voraussetzung dafür, die versiegelten
 * Domänen-Config-Klassen später abzulösen (vgl. {@link MqttTopicConfig}).
 * </p>
 */
public interface MqttConnectionConfig {

	/** Benutzername für die Broker-Anmeldung ({@code null} = anonym). */
	String getUserName();

	/** Entschlüsseltes Broker-Passwort (Zustand „nicht gesetzt", wenn die Entschlüsselung fehlschlug). */
	CryptAes getPasswordCrypt();

	/** TLS-Konfiguration ({@code null} = unverschlüsselt). */
	Ssl getSsl();

	/** MQTT-Topic-Präfix (Basis der Subscribe-Topic-Filter). */
	String getTopicPrefix();

	/** Default-QoS beim Publizieren (u. a. für den Last-Will). */
	int getPublishQoS();

	/** QoS für die Kommando-Subscriptions. */
	int getSubscribeQoS();
}
