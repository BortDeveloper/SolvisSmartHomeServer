package de.sgollmer.solvismax.connection.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

import de.sgollmer.solvismax.Constants;
import de.sgollmer.solvismax.helper.Helper;
import de.sgollmer.solvismax.log.Diagnostics;

public class MqttThread extends Helper.Runnable {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MqttThread.class);
	/**
	 * 
	 */
	private final Mqtt mqtt;
	// Config-Lesen laeuft ueber die schmale Sicht (Weg B); an this.mqtt bleiben
	// nur die Laufzeit-Zugriffe (Client, Callback, Last-Will).
	private final MqttConnectionConfig config;
	private boolean abort = false;

	MqttThread(final Mqtt mqtt) {
		super("Mqtt");
		this.mqtt = mqtt;
		this.config = mqtt;
	}

	@Override
	public void run() {

		try {

			if (!this.mqtt.client.isConnected()) {
				MqttConnectOptions options = new MqttConnectOptions();
				if (this.config.getUserName() != null && this.config.getPasswordCrypt() != null) {
					options.setUserName(this.config.getUserName());
					options.setPassword(this.config.getPasswordCrypt().cP());
				}
				options.setAutomaticReconnect(true);
				options.setCleanSession(false);
				// Variante B (natives mTLS direkt zum Broker) ist deprecatet und wird
				// vor dem Thread-Start in Mqtt.connect() per Fail-Fast-Guard abgewiesen
				// (Auflage A-3). Hier ankommender Verkehr ist daher immer Variante A
				// (Klartext zum lokalen, auf 127.0.0.1 gebundenen Broker + mTLS-Bridge).
				// Die fruehere options.setSocketFactory(...)-Verdrahtung wurde entfernt:
				// tcp://-URI + SSLSocketFactory ergibt in Paho v3 den Fehler 32105
				// (REASON_CODE_SOCKET_FACTORY_MISMATCH). Siehe docs/mtls-behebung-vorschlag.md.
				MqttData lastWill = this.mqtt.getLastWill();
				String topic = lastWill.getTopic(this.mqtt);
				options.setWill(topic, lastWill.getPayLoad(), lastWill.getQoS(this.config.getPublishQoS()),
						lastWill.isRetained());
				options.setMaxInflight(Constants.Mqtt.MAX_INFLIGHT);
				options.setAutomaticReconnect(true);
				this.mqtt.client.setCallback(this.mqtt.callback);
				int length = Constants.Mqtt.CMND_SUFFIXES.length;
				String[] topicFilters = new String[length];
				int[] qoSs = new int[length];
				for (int i = 0; i < length; ++i) {
					topicFilters[i] = this.config.getTopicPrefix() + Constants.Mqtt.CMND_SUFFIXES[i];
					qoSs[i] = this.config.getSubscribeQoS();
				}

				boolean connected = false;
				boolean subscribed = false;
				int waitTime = Constants.Mqtt.MIN_CONNECTION_REPEAT_TIME;
				while ((!connected || !subscribed) && !this.abort) {
					try {
						this.mqtt.client.connect(options);
						connected = true;
						this.mqtt.client.subscribe(topicFilters, qoSs);
						subscribed = true;
					} catch (MqttException e) {
						if (!connected) {
							// SR-1/A-4: WARN statt INFO, damit der Nie-Verbunden-Zustand
							// nicht im Log-Rauschen untergeht. Ursache hier ist Variante A
							// (lokaler Broker nicht erreichbar) — der 32105-Fall (Variante B)
							// wird bereits in Mqtt.connect() ausgeschlossen.
							Mqtt.logger.warn("MQTT broker (Variante A, lokaler Broker) not reachable, will be retried in "
									+ waitTime / 1000 + " s.");
						} else if (!subscribed) {
							Mqtt.logger.error("Error on subscription, will be retried in " + waitTime / 1000 + " s.");
							try {
								synchronized (this.mqtt) {
									this.mqtt.client.disconnect();
								}
							} catch (MqttException e1) {
							}
						}
						synchronized (this) {
							if (!this.abort) {
								try {
									this.wait(waitTime);
								} catch (InterruptedException e1) {
								}
								waitTime *= 2;
								if (waitTime > Constants.Mqtt.MAX_CONNECTION_REPEAT_TIME) {
									waitTime = Constants.Mqtt.MAX_CONNECTION_REPEAT_TIME;
								}
							}
						}
					}
				}
			}
		} catch (Throwable t) {
			logger.error("Unexpected throw", t);
		}

	}

	synchronized void abort() {
		this.abort = true;
		this.notifyAll();
	}
}