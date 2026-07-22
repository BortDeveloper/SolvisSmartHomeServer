package de.sgollmer.solvismax;

/**
 * Schmale <b>Config-Sicht</b> auf die flachen Ausführungswerte der
 * {@code base.xml} ({@code <ExecutionData>}) — Fortsetzung der
 * Konsumenten-Entkopplung nach dem Pilotmuster (Weg B, MODERNISIERUNG.md 3.3).
 *
 * <p>
 * Konsumenten sind {@code Main} (Server-Port für Terminate/Restart und den
 * Socket-Aufbau, beschreibbarer Pfad) und {@code Instances} (beschreibbarer
 * Pfad, Zeitzone, Echo-Sperrzeit). Sie sollen nicht von der ganzen
 * Aggregat-Klasse {@link BaseData} abhängen, wenn sie nur diese flachen Werte
 * lesen.
 * </p>
 *
 * <p>
 * <b>Zwei Quellen, ein Konsument:</b> Sowohl das Domänenobjekt {@link BaseData}
 * als auch eine DTO-gestützte Sicht (aus dem JAXB-Parser) erfüllen diese
 * Schnittstelle — die Voraussetzung dafür, die versiegelten
 * Domänen-Config-Klassen später abzulösen (vgl.
 * {@code de.sgollmer.solvismax.connection.mqtt.MqttTopicConfig}).
 * </p>
 */
public interface ExecutionConfig {

	/** Zeitzone der Solvis-Anlage (z. B. für die Uhr-Synchronisation). */
	String getTimeZone();

	/** TCP-Port des Servers (Terminate/Restart nutzen Port bzw. Port+1). */
	int getPort();

	/**
	 * Beschreibbarer Arbeitspfad — <b>abgeleitete</b> Sicht: die OS-Weiche
	 * zwischen {@code writeablePathWindows} und {@code writablePathLinux}.
	 */
	String getWritablePath();

	/** Sperrzeit, in der ein vom Server gesetzter Wert nicht zurückgemeldet wird. */
	int getEchoInhibitTime_ms();
}
