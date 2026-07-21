package de.sgollmer.solvismax.model.objects.unit;

/**
 * Schmale <b>Config-Sicht</b> auf die flachen Skalarwerte einer Solvis-Einheit
 * ({@code <Unit>}-Attribute der {@code base.xml}) — Fortsetzung der
 * Konsumenten-Entkopplung nach dem Pilotmuster (Weg B, MODERNISIERUNG.md 3.3).
 *
 * <p>
 * Hintergrund: {@link Unit} ist ein tiefes Aggregat (Configuration, Features,
 * ChannelOptions, …). Die meisten Konsumenten — Timing-Threads, Messwert- und
 * Bildschirm-Logik — lesen aber nur einzelne Skalarwerte. Sie hängen über diese
 * Sicht nicht mehr am ganzen Aggregat; Zugang im laufenden System über
 * {@code Solvis.getUnitConfig()}.
 * </p>
 *
 * <p>
 * <b>Zwei Quellen, ein Konsument:</b> Sowohl das Domänenobjekt {@link Unit} als
 * auch eine DTO-gestützte Sicht (aus dem JAXB-Parser, inkl. der
 * {@code _s}→{@code _ms}-Ableitungen) erfüllen diese Schnittstelle. Die
 * Aggregat-Zweige (Features, ChannelOptions, Assignments, Durations,
 * IgnoredChannels) bleiben bewusst außen vor — sie folgen mit eigenen Sichten.
 * </p>
 */
public interface UnitConfig {

	/** Eindeutige Id der Einheit (Topic-Bestandteil, Log-Kontext, Backup-Zuordnung). */
	String getId();

	/** Anzahl der Messwerte für die Mittelwertbildung. */
	int getDefaultAverageCount();

	/** Hysterese-Faktor der Messwertübernahme. */
	int getMeasurementHysteresisFactor();

	/** Mess-Intervall in ms (abgeleitet aus {@code measurementsInterval_s} ×1000). */
	int getMeasurementsInterval_ms();

	/** Schnelles Mess-Intervall in ms (abgeleitet, ×1000). */
	int getMeasurementsIntervalFast_ms();

	/** Anzahl Intervalle, nach denen ein schneller Wechsel ein Update erzwingt. */
	int getForceUpdateAfterFastChangingIntervals();

	/** Intervall erzwungener Updates in ms. */
	int getForcedUpdateInterval_ms();

	/** Intervall des Doppel-Updates in ms. */
	int getDoubleUpdateInterval_ms();

	/** Puffer-Intervall in ms ({@code 0} = ungepuffert). */
	int getBufferedInterval_ms();

	/** Abgeleitete Sicht: gepuffert, wenn {@link #getBufferedInterval_ms()} &gt; 0. */
	boolean isBuffered();

	/** Watchdog-Zyklus in ms. */
	int getWatchDogTime_ms();

	/** Sperrzeit nach GUI-Zugriff eines Benutzers in ms. */
	int getReleaseBlockingAfterUserAccess_ms();

	/** Sperrzeit nach Service-Zugriff in ms. */
	int getReleaseBlockingAfterServiceAccess_ms();

	/** Aktivzeit der „Nachheizen nicht nötig"-Anzeige in ms. */
	int getReheatingNotRequiredActiveTime_ms();

	/** Verzögerung des Fehler-Resets in ms. */
	int getResetErrorDelayTime();

	/** Verzögertes Anlaufen nach dem Einschalten der Anlage. */
	boolean isDelayAfterSwitchingOnEnable();

	/** Firmware-Variante LTH 2.21.02A (beeinflusst die Verbindung). */
	boolean isFwLth2_21_02A();

	/** Ignorierte Randstärke bei der Bildschirmschoner-Erkennung in Pixeln. */
	int getIgnoredFrameThicknesScreenSaver();
}
