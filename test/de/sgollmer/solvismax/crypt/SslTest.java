package de.sgollmer.solvismax.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests fuer die mTLS-Unterstuetzung ({@link Ssl}).
 *
 * <p>
 * Geprueft werden die im Fork ergaenzten, sicherheitsrelevanten Zusicherungen:
 * kein TLS bei deaktivierter Konfiguration, korrektes Laden eines
 * PKCS#8-Schluessels und die bewusste Ablehnung des nicht unterstuetzten
 * PKCS#1-Formats (statt eines kryptischen Folgefehlers).
 * </p>
 */
class SslTest {

	@Test
	void ohneAktivierungKeineSocketFactory() throws Exception {
		// enable=false -> null signalisiert Paho "Standard-(Klartext-)Socket".
		assertNull(Ssl.create(false, null, null, null).getSocketFactory());
	}

	@Test
	void ladePkcs8Schluessel(@TempDir final Path dir) throws Exception {
		final Path key = dir.resolve("client.key");
		Files.write(key, pkcs8Pem().getBytes(StandardCharsets.US_ASCII));

		final PrivateKey privateKey = Ssl.loadPrivateKeyPkcs8(key.toString());

		assertNotNull(privateKey);
		assertEquals("RSA", privateKey.getAlgorithm());
	}

	@Test
	void pkcs1SchluesselWirdMitKlarerMeldungAbgelehnt(@TempDir final Path dir) throws Exception {
		final Path key = dir.resolve("legacy.key");
		Files.write(key, ("-----BEGIN RSA PRIVATE KEY-----\nMIIBderMock==\n"
				+ "-----END RSA PRIVATE KEY-----\n").getBytes(StandardCharsets.US_ASCII));

		final GeneralSecurityException ex = assertThrows(GeneralSecurityException.class,
				() -> Ssl.loadPrivateKeyPkcs8(key.toString()));
		assertTrue(ex.getMessage().contains("PKCS#1"),
				"Meldung soll auf das PKCS#1-Format hinweisen: " + ex.getMessage());
	}

	@Test
	void leereDateiWirdAbgelehnt(@TempDir final Path dir) throws Exception {
		final Path key = dir.resolve("empty.key");
		Files.write(key, new byte[0]);

		assertThrows(GeneralSecurityException.class, () -> Ssl.loadPrivateKeyPkcs8(key.toString()));
	}

	/** Erzeugt einen frischen RSA-Schluessel als PEM/PKCS#8-Text. */
	private static String pkcs8Pem() throws Exception {
		// getPrivate().getEncoded() liefert bereits PKCS#8-DER.
		final byte[] der = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate().getEncoded();
		final String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
				.encodeToString(der);
		return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
	}
}
