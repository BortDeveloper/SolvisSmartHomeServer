/************************************************************************
 *
 * $Id$
 *
 *
 ************************************************************************/

package de.sgollmer.solvismax.crypt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.namespace.QName;

import de.sgollmer.xmllibrary.BaseCreator;
import de.sgollmer.xmllibrary.CreatorByXML;
import de.sgollmer.xmllibrary.XmlException;

/**
 * Baut eine {@link SSLSocketFactory} fuer die MQTT-Verbindung aus PEM-Dateien
 * auf: CA-Zertifikat (Vertrauensanker), Client-Zertifikat und privater
 * Client-Schluessel (PKCS#8) fuer gegenseitiges TLS (mTLS).
 *
 * <p>
 * Fork-Ergaenzung: Der Upstream lieferte hier nur ein Stub
 * ({@code // TODO not yet implemented}, Rueckgabe {@code null}). Siehe
 * CHANGELOG-fork.md.
 * </p>
 */
public class Ssl {

	private final boolean enable;
	private final String caFilePath;
	private final String clientCrtFilePath;
	private final String clientKeyFilePath;

	private Ssl(final boolean enable, final String caFilePath, final String clientCrtFilePath,
			final String clientKeyFilePath) {
		this.enable = enable;
		this.caFilePath = caFilePath;
		this.clientCrtFilePath = clientCrtFilePath;
		this.clientKeyFilePath = clientKeyFilePath;
	}

	public boolean isEnabled() {
		return this.enable;
	}

	/**
	 * Programmatische Erzeugung (ausserhalb der XML-Konfiguration), u. a. fuer
	 * Tests und die Wiederverwendung der mTLS-Socket-Factory.
	 */
	public static Ssl create(final boolean enable, final String caFilePath, final String clientCrtFilePath,
			final String clientKeyFilePath) {
		return new Ssl(enable, caFilePath, clientCrtFilePath, clientKeyFilePath);
	}

	public static class Creator extends CreatorByXML<Ssl> {

		private boolean enable;
		private String caFilePath;
		private String clientCrtFilePath;
		private String clientKeyFilePath;

		public Creator(final String id, final BaseCreator<?> creator) {
			super(id, creator);
		}

		@Override
		public void setAttribute(final QName name, final String value) {
			switch (name.getLocalPart()) {
				case "enable":
					this.enable = Boolean.parseBoolean(value);
					break;
				case "caFilePath":
					this.caFilePath = value;
					break;
				case "clientCrtFilePath":
					this.clientCrtFilePath = value;
					break;
				case "clientKeyFilePath":
					this.clientKeyFilePath = value;
					break;
			}

		}

		@Override
		public Ssl create() throws XmlException, IOException {
			return new Ssl(this.enable, this.caFilePath, this.clientCrtFilePath, this.clientKeyFilePath);
		}

		@Override
		public CreatorByXML<?> getCreator(final QName name) {
			return null;
		}

		@Override
		public void created(final CreatorByXML<?> creator, final Object created) {

		}

	}

	/**
	 * @return eine {@link SSLSocketFactory} fuer mTLS, oder {@code null}, wenn TLS
	 *         nicht aktiviert ist (dann verbindet der Client unverschluesselt).
	 * @throws GeneralSecurityException bei fehlerhaften Zertifikaten/Schluesseln
	 * @throws IOException              wenn eine der PEM-Dateien nicht lesbar ist
	 */
	public SSLSocketFactory getSocketFactory() throws GeneralSecurityException, IOException {
		if (!this.enable) {
			return null;
		}

		final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

		// Vertrauensanker (CA) -> TrustStore
		final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
		trustStore.load(null, null);
		try (InputStream in = Files.newInputStream(Paths.get(this.caFilePath))) {
			int index = 0;
			for (final Certificate ca : certFactory.generateCertificates(in)) {
				trustStore.setCertificateEntry("ca-" + index++, ca);
			}
		}
		final TrustManagerFactory tmf = TrustManagerFactory
				.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(trustStore);

		// Client-Zertifikatskette + privater Schluessel -> KeyStore
		final Collection<? extends Certificate> chain;
		try (InputStream in = Files.newInputStream(Paths.get(this.clientCrtFilePath))) {
			chain = certFactory.generateCertificates(in);
		}
		if (chain.isEmpty()) {
			throw new GeneralSecurityException("No client certificate found in " + this.clientCrtFilePath);
		}
		final PrivateKey privateKey = loadPrivateKeyPkcs8(this.clientKeyFilePath);

		final char[] emptyPassword = new char[0];
		final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		keyStore.setKeyEntry("client", privateKey, emptyPassword, chain.toArray(new Certificate[0]));
		final KeyManagerFactory kmf = KeyManagerFactory
				.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(keyStore, emptyPassword);

		final SSLContext context = SSLContext.getInstance("TLS");
		context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		return context.getSocketFactory();
	}

	/**
	 * Liest einen privaten Schluessel im PEM-/PKCS#8-Format
	 * ({@code -----BEGIN PRIVATE KEY-----}). Das Schluesselformat (RSA/EC/DSA)
	 * wird automatisch erkannt.
	 */
	private static PrivateKey loadPrivateKeyPkcs8(final String path)
			throws GeneralSecurityException, IOException {
		final String pem = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.US_ASCII);
		if (pem.contains("BEGIN RSA PRIVATE KEY") || pem.contains("BEGIN EC PRIVATE KEY")) {
			throw new GeneralSecurityException("Private key " + path + " is in PKCS#1 format; "
					+ "please convert to PKCS#8 (openssl pkcs8 -topk8 -nocrypt).");
		}
		final String base64 = pem
				.replaceAll("-----BEGIN (.*)-----", "")
				.replaceAll("-----END (.*)-----", "")
				.replaceAll("\\s", "");
		if (base64.isEmpty()) {
			throw new GeneralSecurityException("No private key found in " + path);
		}
		final byte[] der = Base64.getDecoder().decode(base64);
		final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(der);
		GeneralSecurityException last = null;
		for (final String algorithm : new String[] { "RSA", "EC", "DSA" }) {
			try {
				return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
			} catch (final GeneralSecurityException e) {
				last = e;
			}
		}
		throw new GeneralSecurityException("Unsupported private key algorithm in " + path, last);
	}
}
