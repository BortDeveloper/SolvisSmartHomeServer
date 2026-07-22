package de.sgollmer.solvismax;

import de.sgollmer.solvismax.connection.mqtt.Mqtt;
import de.sgollmer.solvismax.error.CryptException;
import de.sgollmer.solvismax.error.CryptException.Type;
import de.sgollmer.solvismax.log.Diagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.sgollmer.solvismax.log.Diagnostics.Level;
import de.sgollmer.solvismax.mail.ExceptionMail;
import de.sgollmer.solvismax.model.objects.unit.Units;
import de.sgollmer.solvismax.smarthome.IoBroker;

// Erfüllt die schmale Config-Sicht ExecutionConfig (Weg B): Konsumenten der
// flachen Ausführungswerte hängen an der Sicht, nicht an der Aggregat-Klasse.
public class BaseData implements ExecutionConfig {

	private static final Logger logger = LoggerFactory.getLogger(BaseData.class);;

	public static boolean DEBUG = false;

	private final String timeZone;

	@Override
	public int getPort() {
		return this.port;
	}

	@Override
	public String getWritablePath() {
		boolean windows = System.getProperty("os.name").startsWith("Windows");
		return windows ? this.writeablePathWindows : this.writablePathLinux;

	}

	private final int port;
	private final String writeablePathWindows;
	private final String writablePathLinux;
	private final int echoInhibitTime_ms;
	private final Units units;
	private final ExceptionMail exceptionMail;
	private final Mqtt mqtt;
	private final IoBroker ioBroker;

	@Override
	public String getTimeZone() {
		return this.timeZone;
	}

	private BaseData(final String timeZone, final int port, final String writeablePathWindows,
			final String writablePathLinux, final int echoInhibitTime_ms, final Units units,
			final ExceptionMail exceptionMail, final Mqtt mqtt, final IoBroker ioBroker) {
		this.timeZone = timeZone;
		this.port = port;
		this.writeablePathWindows = writeablePathWindows;
		this.writablePathLinux = writablePathLinux;
		this.units = units;
		this.exceptionMail = exceptionMail;
		this.echoInhibitTime_ms = echoInhibitTime_ms;
		this.mqtt = mqtt;
		this.ioBroker = ioBroker;

		String message = null;
		Level level = Level.ERROR;

		if (this.units.isMailEnabled()) {
			if (this.exceptionMail == null) {
				message = "base.xml error, SendMailOnError is activated, but ExceptionMail tag is missed. Mail disabled.";
			} else {
				CryptException e = this.exceptionMail.getException();
				if (e != null) {
					message = "base.xml error of passwordCrypt in ExceptionMail tag. Mail disabled: " + e.getMessage();
					if (e.getType() == Type.DEFAULT) {
						level = Level.WARN;
					}
				}
			}
			if (message != null) {
				Diagnostics.log(logger, level, message);
			}
		}
	}

	/**
	 * Öffentliche Konstruktions-Factory für den JAXB-Mapper (MODERNISIERUNG.md
	 * 3.3, Weg B). Nutzt den (privaten) Konstruktor inklusive dessen
	 * Mail-Konsistenzprüfung.
	 */
	public static BaseData of(final String timeZone, final int port, final String writeablePathWindows,
			final String writablePathLinux, final int echoInhibitTime_ms, final Units units,
			final ExceptionMail exceptionMail, final Mqtt mqtt, final IoBroker ioBroker) {
		return new BaseData(timeZone, port, writeablePathWindows, writablePathLinux, echoInhibitTime_ms, units,
				exceptionMail, mqtt, ioBroker);
	}

	public Units getUnits() {
		return this.units;
	}

	public ExceptionMail getExceptionMail() {
		return this.exceptionMail;
	}

	@Override
	public int getEchoInhibitTime_ms() {
		return this.echoInhibitTime_ms;
	}

	public Mqtt getMqtt() {
		return this.mqtt;
	}

	public IoBroker getIoBroker() {
		return this.ioBroker;
	}

}
