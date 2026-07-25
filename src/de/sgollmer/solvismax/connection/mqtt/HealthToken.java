package de.sgollmer.solvismax.connection.mqtt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dateibasiertes Ready-Token (Auflage A-4 / sre SR-1).
 *
 * <p>
 * Der Connector ist funktional nur dann gesund, wenn er tatsaechlich mit dem
 * Broker verbunden ist und {@code solvis/#}-Daten publiziert. Die Restart-
 * Policy {@code unless-stopped} deckt nur einen Prozess-Crash, nicht den
 * Silent-Failure eines Endlos-Retry-Loops (SR-1). Dieses Token bildet den
 * <em>tatsaechlichen</em> Verbindungs-/Publish-Zustand ab: es wird bei
 * erfolgreichem Connect und bei laufender Publish-Aktivitaet frisch
 * geschrieben und bei Verbindungsverlust/Shutdown entfernt. Ein HEALTHCHECK
 * (Dockerfile) prueft die <em>Frische</em> ({@code now - mtime}), nicht die
 * PID — Symptom- statt Lebendigkeitspruefung (Google SRE Kap. 6; CNCF
 * Observability: Liveness != Readiness != Funktion).
 *
 * <p>
 * Transport-unabhaengig: kommt ohne lokalen Broker aus und traegt damit in
 * jeder Variante. Der Pfad ist ueber die System-Property
 * {@code solvis.health.tokenPath} bzw. die Umgebungsvariable
 * {@code SOLVIS_HEALTH_TOKEN_PATH} ueberschreibbar; der Default
 * {@code /data/health/ready} passt zum persistenten Container-Volume
 * ({@code writablePathLinux="/data"}).
 */
final class HealthToken {

	private static final Logger logger = LoggerFactory.getLogger(HealthToken.class);

	static final String PATH_PROPERTY = "solvis.health.tokenPath";
	static final String PATH_ENV = "SOLVIS_HEALTH_TOKEN_PATH";
	static final String DEFAULT_PATH = "/data/health/ready";

	/**
	 * Touch-Drosselung: {@link #touch()} wird auch bei jedem Publish gerufen; die
	 * Datei soll aber nicht bei jedem Datensatz beschrieben werden. 30 s liegt
	 * deutlich unter der HEALTHCHECK-Frischeschwelle (300 s), so dass ein
	 * gesunder Connector das Token stets frisch haelt.
	 */
	private static final long MIN_TOUCH_INTERVAL_MS = 30_000L;

	private final Path tokenPath;
	private volatile long lastTouch = 0L;
	private volatile boolean writeFailureLogged = false;

	HealthToken() {
		this(resolvePath());
	}

	HealthToken(final Path tokenPath) {
		this.tokenPath = tokenPath;
	}

	private static Path resolvePath() {
		String p = System.getProperty(PATH_PROPERTY);
		if (p == null || p.isEmpty()) {
			p = System.getenv(PATH_ENV);
		}
		if (p == null || p.isEmpty()) {
			p = DEFAULT_PATH;
		}
		return Paths.get(p);
	}

	/**
	 * Frische schreiben. Der erste Aufruf schreibt immer; Folgeaufrufe innerhalb
	 * des Drossel-Intervalls sind No-Ops. Schreibfehler brechen den Betrieb nie
	 * ab (das Token ist ein Beobachtungs-, kein Steuersignal) und werden nur
	 * einmal pro Fehlerphase auf WARN geloggt.
	 */
	void touch() {
		long now = System.currentTimeMillis();
		if (this.lastTouch != 0L && now - this.lastTouch < MIN_TOUCH_INTERVAL_MS) {
			return;
		}
		this.lastTouch = now;
		try {
			Path parent = this.tokenPath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.write(this.tokenPath, Long.toString(now).getBytes(StandardCharsets.UTF_8));
			this.writeFailureLogged = false;
		} catch (IOException | RuntimeException e) {
			if (!this.writeFailureLogged) {
				logger.warn("Health-Ready-Token konnte nicht geschrieben werden (" + this.tokenPath + "): "
						+ e.getMessage());
				this.writeFailureLogged = true;
			}
		}
	}

	/**
	 * Token entfernen (Verbindungsverlust / Shutdown) — der Zustand wird dadurch
	 * sofort (statt erst nach Ablauf der Frischeschwelle) als ungesund sichtbar.
	 */
	void clear() {
		this.lastTouch = 0L;
		try {
			Files.deleteIfExists(this.tokenPath);
		} catch (IOException | RuntimeException e) {
			logger.debug("Health-Ready-Token konnte nicht entfernt werden (" + this.tokenPath + "): "
					+ e.getMessage());
		}
	}
}
