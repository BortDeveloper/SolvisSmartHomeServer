package de.sgollmer.solvismax.mail;

import de.sgollmer.solvismax.crypt.CryptAes;

public class Proxy {

	private final String host;
	private final int port;
	private final String user;
	private final CryptAes password;

	private Proxy(final String host, int port, final String user, final CryptAes password) {
		this.host = host;
		this.port = port;
		this.user = user;
		this.password = password;
	}

	/** Öffentliche Konstruktions-Factory für den JAXB-Mapper (MODERNISIERUNG.md 3.3, Weg B). */
	public static Proxy of(final String host, final int port, final String user, final CryptAes password) {
		return new Proxy(host, port, user, password);
	}

	public String getHost() {
		return this.host;
	}

	public int getPort() {
		return this.port;
	}

	public String getUser() {
		return this.user;
	}

	public CryptAes getPassword() {
		return this.password;
	}

}
