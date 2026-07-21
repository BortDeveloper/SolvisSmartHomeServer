package de.sgollmer.solvismax.log;

import org.slf4j.Logger;

/**
 * Anwendungs-Diagnose: Exit-Code-Kopplung und ein paar Logging-Helfer, die ueber
 * das hinausgehen, was SLF4J direkt bietet.
 *
 * <p>
 * Hintergrund: Frueher uebernahm die Klasse {@code LogManager} zweierlei — eine
 * eigene Logger-Fassade UND anwendungsspezifische Ablauflogik
 * (Exit-Code-Kopplung, Vor-Init-Nachrichtenpufferung, eigene Level
 * {@code FATAL}/{@code LEARN}). Im Zuge der Modernisierung wurde das eigentliche
 * Logging auf <b>SLF4J</b> umgestellt; die verbliebene App-Logik lebt hier.
 * </p>
 *
 * <p>
 * <b>Bewusste Vereinfachung:</b> Die fruehere Pufferung von Meldungen bis zur
 * Logger-Initialisierung war ein Artefakt des tinylog-Backends (das erst nach
 * dem Einlesen einer Konfigurationsdatei ausgeben konnte). Das SLF4J-/
 * Logback-Backend ist von der ersten Meldung an ausgabebereit, daher entfaellt
 * die Pufferung; Meldungen werden sofort ausgegeben. Die Exit-Code-Semantik
 * bleibt erhalten.
 * </p>
 */
public final class Diagnostics {

	private Diagnostics() {
	}

	/**
	 * Fachliche Log-Level. SLF4J kennt {@code FATAL}/{@code LEARN} nicht; die
	 * Abbildung erfolgt in {@link #dispatch}: {@code FATAL}->error,
	 * {@code LEARN}->info.
	 */
	public enum Level {
		FATAL(10), ERROR(20), LEARN(30), WARN(40), INFO(50), DEBUG(60);

		private final int prio;

		Level(final int prio) {
			this.prio = prio;
		}

		boolean isMoreSevereThan(final Level other) {
			return this.prio < other.prio;
		}

		/** Kompatibilitaet zum frueheren LogManager.Level.getLevel(String). */
		public static Level getLevel(final String levelString) {
			return Level.valueOf(levelString);
		}
	}

	/** Von {@link #record} gemerkter, schwerwiegendster Exit-Code (0 = keiner). */
	private static volatile int pendingExitCode = 0;
	private static volatile Level pendingExitLevel = Level.DEBUG;

	/** Kompatibilitaets-Rueckgabe fuer den frueheren LogManager. */
	public enum LogErrors {
		OK, INIT, PREVIOUS
	}

	/**
	 * Frueher: Initialisierung des Logger-Backends aus einer Konfigurationsdatei.
	 * Mit Logback ist keine solche Initialisierung noetig (Konfiguration ueber
	 * {@code logback.xml} auf dem Klassenpfad). Bleibt als No-op erhalten, damit
	 * der Aufrufer ({@code Main}) unveraendert bleibt.
	 *
	 * @param path Schreibpfad (nur noch informativ, ungenutzt)
	 * @return stets {@link LogErrors#OK}
	 */
	public static LogErrors createInstance(final String path) {
		return LogErrors.OK;
	}

	/**
	 * Frueher: Meldungen bis zum Ende eines kritischen Abschnitts puffern. Mit
	 * dem sofort ausgabebereiten Backend nicht mehr noetig; No-op.
	 */
	public static void setBufferedMessages(final boolean enable) {
		// bewusst leer, siehe Klassen-Javadoc
	}

	/** Loggt {@code message} auf {@code logger} beim fachlichen {@code level}. */
	public static void log(final Logger logger, final Level level, final String message) {
		dispatch(logger, level, message, null);
	}

	/** Wie {@link #log(Logger, Level, String)}, zusaetzlich mit Ursache. */
	public static void log(final Logger logger, final Level level, final String message,
			final Throwable throwable) {
		dispatch(logger, level, message, throwable);
	}

	/**
	 * Loggt eine Meldung und merkt sich den zugehoerigen Exit-Code fuer einen
	 * spaeteren {@link #exit(int)}. Der schwerwiegendste (spezifischste) Level
	 * gewinnt.
	 */
	public static void record(final Logger logger, final Level level, final String message,
			final Throwable throwable, final Integer errorCode) {
		dispatch(logger, level, message, throwable);
		if (errorCode != null && errorCode != 0 && level.isMoreSevereThan(pendingExitLevel)) {
			pendingExitCode = errorCode;
			pendingExitLevel = level;
		}
	}

	/**
	 * Loggt {@code message} samt der Stack-Trace-Elemente (frueher
	 * {@code LogManager.out}).
	 */
	public static void out(final Logger logger, final Level level, final String message,
			final StackTraceElement[] elements) {
		final StringBuilder builder = new StringBuilder(message);
		for (final StackTraceElement element : elements) {
			builder.append('\n').append(element.toString());
		}
		dispatch(logger, level, builder.toString(), null);
	}

	/**
	 * Ersetzt die frueere {@code ILogger.debug(boolean, ...)}-Semantik: bei
	 * {@code true} wird auf INFO, sonst auf DEBUG geloggt.
	 */
	public static void debugOrInfo(final Logger logger, final boolean asInfo, final String message) {
		if (asInfo) {
			logger.info(message);
		} else {
			logger.debug(message);
		}
	}

	/** Wie {@link #debugOrInfo(Logger, boolean, String)}, mit Ursache. */
	public static void debugOrInfo(final Logger logger, final boolean asInfo, final String message,
			final Throwable throwable) {
		if (asInfo) {
			logger.info(message, throwable);
		} else {
			logger.debug(message, throwable);
		}
	}

	/**
	 * Beendet die Anwendung. Ist {@code errorCode} 0, aber wurde zuvor ueber
	 * {@link #record} ein schwerwiegenderer Code gemeldet, wird dieser verwendet.
	 */
	public static void exit(final int errorCode) {
		int code = errorCode;
		if (code == 0 && pendingExitCode != 0) {
			code = pendingExitCode;
		}
		System.exit(code);
	}

	/** Bildet die fachlichen Level auf SLF4J ab (FATAL->error, LEARN->info). */
	private static void dispatch(final Logger logger, final Level level, final String message,
			final Throwable throwable) {
		switch (level) {
			case FATAL:
			case ERROR:
				if (throwable != null) {
					logger.error(message, throwable);
				} else {
					logger.error(message);
				}
				break;
			case WARN:
				if (throwable != null) {
					logger.warn(message, throwable);
				} else {
					logger.warn(message);
				}
				break;
			case LEARN:
			case INFO:
				if (throwable != null) {
					logger.info(message, throwable);
				} else {
					logger.info(message);
				}
				break;
			case DEBUG:
			default:
				if (throwable != null) {
					logger.debug(message, throwable);
				} else {
					logger.debug(message);
				}
				break;
		}
	}
}
