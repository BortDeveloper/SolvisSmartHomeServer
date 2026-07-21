package de.sgollmer.solvismax.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests fuer die AES-Passwortverschluesselung ({@link CryptAes}).
 *
 * <p>
 * {@code CryptAes} verschluesselt die {@code passwordCrypt}-Werte der
 * {@code base.xml}. Zwei Eigenschaften sind wartungskritisch:
 * </p>
 * <ol>
 * <li><b>Round-Trip:</b> Ein verschluesselter Wert muss sich wieder in den
 * Klartext entschluesseln lassen — sonst waeren bestehende Konfigurationen
 * nicht mehr lesbar.</li>
 * <li><b>Stabilitaet ueber Versionen:</b> Der Schluessel ist fest, damit ein
 * einmal in {@code base.xml} eingetragener {@code passwordCrypt} auch nach einem
 * Update noch entschluesselt werden kann. Der Charakterisierungstest pinnt einen
 * bekannten Ausgabewert und schlaegt an, falls jemand den Schluessel/Algorithmus
 * unbeabsichtigt aendert (was alle Bestandskonfigurationen brechen wuerde).</li>
 * </ol>
 */
class CryptAesTest {

	@Test
	void roundTripLiefertDenKlartextZurueck() throws Exception {
		final String klartext = "geheimesPasswort!123";

		final String verschluesselt = new CryptAes().encrypt(klartext);

		final CryptAes entschluessler = new CryptAes();
		entschluessler.decrypt(verschluesselt);
		assertEquals(klartext, new String(entschluessler.cP()));
	}

	@Test
	void verschluesselungIstDeterministischUndSchluesselStabil() throws Exception {
		// Fester Schluessel -> gleicher Klartext ergibt immer denselben Wert.
		// Dieser Wert wurde wiederholt beobachtet (u. a. --string-to-crypt=probe).
		// Aendert er sich, sind alle bestehenden passwordCrypt-Werte betroffen.
		assertEquals("P7cDQQH1pYIl1kV8oju6xg==", new CryptAes().encrypt("probe"));
	}
}
