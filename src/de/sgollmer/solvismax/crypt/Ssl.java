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

	/**
	 * Baut die {@link SSLSocketFactory} fuer die MQTT-Verbindung auf.
	 *
	 * <p>
	 * Ablauf (klassisches JSSE-Muster fuer gegenseitiges TLS):
	 * </p>
	 * <ol>
	 * <li><b>TrustStore</b> aus dem CA-PEM — legt fest, welchem Broker-Zertifikat
	 * vertraut wird (Server-Authentisierung).</li>
	 * <li><b>KeyStore</b> aus Client-Zertifikatskette + privatem Schluessel —
	 * dies ist unsere eigene Identitaet, die der Broker bei
	 * {@code require_certificate} prueft (Client-Authentisierung = das "mutual"
	 * in mTLS).</li>
	 * <li><b>SSLContext</b> aus beidem; dessen {@link SSLSocketFactory} wird
	 * Paho via {@code MqttConnectOptions.setSocketFactory(...)} uebergeben.</li>
	 * </ol>
	 *
	 * <p>
	 * Bewusst nur JDK-Bordmittel (JSSE) — keine zusaetzliche Krypto-Bibliothek
	 * wie BouncyCastle noetig. Es werden keine Passwoerter/Keystores auf Platte
	 * angelegt; alles bleibt im Speicher (In-Memory-KeyStores mit leerem
	 * Passwort).
	 * </p>
	 *
	 * @return eine {@link SSLSocketFactory} fuer mTLS, oder {@code null}, wenn TLS
	 *         nicht aktiviert ist ({@code enable="false"}) — dann verbindet der
	 *         Client unverschluesselt. Der Aufrufer ({@code MqttThread}) bricht
	 *         bei aktiviertem TLS im Fehlerfall bewusst ab, statt auf Klartext
	 *         zurueckzufallen.
	 * @throws GeneralSecurityException bei fehlerhaften/nicht ladbaren
	 *                                  Zertifikaten oder Schluesseln
	 * @throws IOException              wenn eine der PEM-Dateien nicht lesbar ist
	 */
	public SSLSocketFactory getSocketFactory() throws GeneralSecurityException, IOException {
		// TLS deaktiviert: null signalisiert Paho "Standard-(Klartext-)Socket".
		if (!this.enable) {
			return null;
		}

		// CertificateFactory liest X.509 direkt aus PEM (erkennt die
		// -----BEGIN CERTIFICATE-----/-----END CERTIFICATE------Rahmen selbst).
		final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

		// --- (1) Vertrauensanker (CA) -> TrustStore ---
		// Leeren In-Memory-KeyStore anlegen (load(null, null)) und ausschliesslich
		// das/die CA-Zertifikat(e) eintragen. Dadurch vertraut der Client GENAU
		// dieser CA (und nicht den System-Trust-Anchors) — passend fuer eine
		// private PKI. generateCertificates() erlaubt auch ein Bundle mehrerer
		// CAs in einer Datei.
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

		// --- (2) Eigene Identitaet: Client-Zertifikatskette + privater Schluessel ---
		// Die Kette kann Client-Zertifikat plus Zwischen-CAs enthalten (in
		// Reihenfolge Blatt -> Wurzel). Der private Schluessel muss zum
		// oeffentlichen Schluessel des Blatt-Zertifikats passen.
		final Collection<? extends Certificate> chain;
		try (InputStream in = Files.newInputStream(Paths.get(this.clientCrtFilePath))) {
			chain = certFactory.generateCertificates(in);
		}
		if (chain.isEmpty()) {
			throw new GeneralSecurityException("No client certificate found in " + this.clientCrtFilePath);
		}
		final PrivateKey privateKey = loadPrivateKeyPkcs8(this.clientKeyFilePath);

		// KeyStore-Eintrag "client" = privater Schluessel + zugehoerige Kette.
		// Leeres Passwort, da der Store nur im Speicher lebt und nie persistiert
		// wird; dasselbe leere Passwort muss an KeyManagerFactory.init() gehen.
		final char[] emptyPassword = new char[0];
		final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		keyStore.setKeyEntry("client", privateKey, emptyPassword, chain.toArray(new Certificate[0]));
		final KeyManagerFactory kmf = KeyManagerFactory
				.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(keyStore, emptyPassword);

		// --- (3) SSLContext = TrustManager (wem vertraue ich) + KeyManager (wer
		// bin ich). Protokoll "TLS" laesst JSSE die hoechste gemeinsame Version
		// aushandeln (TLS 1.2/1.3). Der dritte Parameter (SecureRandom) ist null
		// -> JDK-Standard.
		final SSLContext context = SSLContext.getInstance("TLS");
		context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		return context.getSocketFactory();
	}

	/**
	 * Liest einen privaten Schluessel im PEM-/PKCS#8-Format
	 * ({@code -----BEGIN PRIVATE KEY-----}) und gibt ihn als {@link PrivateKey}
	 * zurueck.
	 *
	 * <p>
	 * Warum nur PKCS#8: Die JDK-Bordmittel ({@link PKCS8EncodedKeySpec}) lesen
	 * ausschliesslich PKCS#8-DER. PKCS#1 ({@code BEGIN RSA PRIVATE KEY}) und das
	 * SEC1-EC-Format ({@code BEGIN EC PRIVATE KEY}) haetten eine eigene
	 * ASN.1-Behandlung oder BouncyCastle noetig — bewusst nicht eingefuehrt.
	 * Solche Schluessel lassen sich einmalig konvertieren:
	 * {@code openssl pkcs8 -topk8 -nocrypt -in alt.key -out neu.key}. Der Fall
	 * wird darum frueh mit klarer Meldung abgewiesen statt kryptisch zu
	 * scheitern.
	 * </p>
	 *
	 * <p>
	 * Der Algorithmus (RSA/EC/DSA) steht zwar im PKCS#8-Header, ihn dort
	 * auszulesen waere aber eigener ASN.1-Code. Stattdessen wird die passende
	 * {@link KeyFactory} schlicht durchprobiert — der erste Treffer gewinnt.
	 * </p>
	 *
	 * @param path Pfad zur PEM-Datei
	 * @return der geladene private Schluessel
	 * @throws GeneralSecurityException bei falschem Format oder unbekanntem
	 *                                  Algorithmus
	 * @throws IOException              wenn die Datei nicht lesbar ist
	 */
	// package-sichtbar (statt private) zur direkten Testbarkeit in SslTest.
	static PrivateKey loadPrivateKeyPkcs8(final String path)
			throws GeneralSecurityException, IOException {
		// PEM ist reiner ASCII-Text (Base64 + Rahmenzeilen).
		final String pem = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.US_ASCII);

		// Nicht unterstuetzte Formate frueh und verstaendlich abweisen.
		if (pem.contains("BEGIN RSA PRIVATE KEY") || pem.contains("BEGIN EC PRIVATE KEY")) {
			throw new GeneralSecurityException("Private key " + path + " is in PKCS#1 format; "
					+ "please convert to PKCS#8 (openssl pkcs8 -topk8 -nocrypt).");
		}

		// Rahmenzeilen und alle Whitespaces entfernen -> reines Base64 des
		// DER-kodierten PKCS#8-Blocks.
		final String base64 = pem
				.replaceAll("-----BEGIN (.*)-----", "")
				.replaceAll("-----END (.*)-----", "")
				.replaceAll("\\s", "");
		if (base64.isEmpty()) {
			throw new GeneralSecurityException("No private key found in " + path);
		}
		final byte[] der = Base64.getDecoder().decode(base64);
		final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(der);

		// Algorithmus durchprobieren; die zum DER unpassenden KeyFactorys werfen
		// InvalidKeySpecException (Unterklasse von GeneralSecurityException) und
		// werden uebersprungen. last haelt den letzten Fehler fuer die Diagnose.
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
