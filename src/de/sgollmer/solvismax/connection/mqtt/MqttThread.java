package de.sgollmer.solvismax.connection.mqtt;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLSocketFactory;

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
				if (this.config.getSsl() != null && this.config.getSsl().isEnabled()) {
					try {
						SSLSocketFactory sslSocketFactory = this.config.getSsl().getSocketFactory();
						options.setSocketFactory(sslSocketFactory);
					} catch (GeneralSecurityException | IOException e) {
						// Bei aktiviertem TLS niemals unverschluesselt weiterverbinden.
						logger.error("TLS/SSL configuration for MQTT failed, connection aborted: "
								+ e.getMessage(), e);
						this.abort = true;
						return;
					}
				}
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
							Mqtt.logger
									.info("Mqtt broker not available, will be retried in " + waitTime / 1000 + " s.");
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