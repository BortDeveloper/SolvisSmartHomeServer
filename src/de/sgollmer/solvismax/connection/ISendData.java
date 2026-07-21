package de.sgollmer.solvismax.connection;

import java.util.Collection;

import de.sgollmer.solvismax.connection.mqtt.MqttData;
import de.sgollmer.solvismax.connection.transfer.JsonPackage;
import de.sgollmer.solvismax.error.TypeException;

public interface ISendData {
	public JsonPackage createJsonPackage();

	public Collection<MqttData> createMqttData() throws TypeException;
}
