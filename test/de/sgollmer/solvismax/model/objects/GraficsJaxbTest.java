package de.sgollmer.solvismax.model.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.sgollmer.solvismax.imagepatternrecognition.image.MyImage;
import de.sgollmer.solvismax.model.objects.screen.ScreenGraficData;
import de.sgollmer.solvismax.model.objects.unit.Feature;
import de.sgollmer.solvismax.xml.ControlFileReader.Hashes;
import de.sgollmer.solvismax.xml.GraficFileHandler;

/**
 * Charakterisierung des JAXB-Pfads der {@code graficData.xml}
 * (MODERNISIERUNG.md 3.3 — vierter Baum, Lesen UND Schreiben): der
 * Schreib-/Wieder-Lese-Roundtrip über den echten {@code GraficFileHandler}
 * muss Masken, Feature-Stand, Hashes und die Bild-Daten (Base64-PNG)
 * verlustfrei erhalten; Alt-Dateien mit graficData-Namespace bleiben lesbar;
 * defekte Dateien führen — wie zuvor — zu leeren Lerndaten statt zum Abbruch.
 * Der Test liegt im model.objects-Paket (paketprivater Zugriff auf
 * {@code getSystems} u. Ä. wird nicht gebraucht, aber die Nähe zur Domäne
 * dokumentiert die Zugehörigkeit).
 */
class GraficsJaxbTest {

	/**
	 * Deterministisches 4-Bit-Palettenbild (16 Farben) — exakt das Farbmodell,
	 * das die Solvis-Anzeige liefert. {@code MyImage.createBufferdImage()} (und
	 * damit der Schreibpfad, wie schon beim alten Writer) setzt dieses Modell
	 * voraus; ein RGB-/ARGB-Bild wäre nicht repräsentativ und würde beim
	 * Serialisieren scheitern.
	 */
	private static BufferedImage testBild() {
		final byte[] r = new byte[16];
		final byte[] g = new byte[16];
		final byte[] b = new byte[16];
		for (int i = 0; i < 16; i++) {
			r[i] = (byte) (i * 17);
			g[i] = (byte) (255 - i * 17);
			b[i] = (byte) (i * 8);
		}
		final java.awt.image.IndexColorModel palette = new java.awt.image.IndexColorModel(4, 16, r, g, b);
		final BufferedImage image = new BufferedImage(8, 4, BufferedImage.TYPE_BYTE_BINARY, palette);
		for (int x = 0; x < image.getWidth(); x++) {
			for (int y = 0; y < image.getHeight(); y++) {
				image.setRGB(x, y, palette.getRGB((x + y * 3) % 16));
			}
		}
		return image;
	}

	private static void assertBildGleich(final BufferedImage erwartet, final String base64Png) throws Exception {
		final BufferedImage gelesen = ImageIO
				.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64Png)));
		assertEquals(erwartet.getWidth(), gelesen.getWidth());
		assertEquals(erwartet.getHeight(), gelesen.getHeight());
		for (int x = 0; x < erwartet.getWidth(); x++) {
			for (int y = 0; y < erwartet.getHeight(); y++) {
				// Alpha ausblenden: MyImage arbeitet intern mit 24-Bit-RGB.
				assertEquals(erwartet.getRGB(x, y) & 0xFFFFFF, gelesen.getRGB(x, y) & 0xFFFFFF,
						"Pixel (" + x + "," + y + ")");
			}
		}
	}

	@Test
	void roundTripUeberGraficFileHandlerVerlustfrei(@TempDir final File tempDir) throws Exception {
		final Hashes hashes = new Hashes(1234L, 5678L);

		// Domänengraph aufbauen, wie es das Lernen tut.
		final AllSolvisGrafics original = new AllSolvisGrafics();
		final SystemGrafics system = original.get("mySolvis", hashes);
		system.setConfigurationMask(0x01000010L);
		system.setBaseConfigurationMask(3L);
		system.add(new Feature("Admin", true));
		system.add(new Feature("ClockTuning", false));
		final BufferedImage bild = testBild();
		system.put("grafik1", new MyImage(bild));

		// Schreiben + Wieder-Lesen ueber den echten Handler.
		final GraficFileHandler handler = new GraficFileHandler(tempDir);
		handler.write(original);
		final AllSolvisGrafics gelesen = new GraficFileHandler(tempDir).read();

		// Hashes identisch -> get() verwirft nichts.
		assertEquals(Long.valueOf(1234L), gelesen.getControlHashCodes().getResourceHash());
		assertEquals(Long.valueOf(5678L), gelesen.getControlHashCodes().getFileHash());
		final SystemGrafics wieder = gelesen.get("mySolvis", hashes);
		assertEquals(0x01000010L, wieder.getConfigurationMask());
		assertEquals(3L, wieder.getBaseConfigurationMask());
		// Feature-Stand identisch (Admin true, ClockTuning false).
		assertTrue(wieder.areRelevantFeaturesEqual(Map.of("Admin", true, "ClockTuning", false)));
		final ScreenGraficData grafik = wieder.get("grafik1");
		assertNotNull(grafik);
		assertBildGleich(bild, grafik.toBase64Png());
		assertNull(wieder.get("gibtEsNicht"));
	}

	/** Alt-Dateien mit graficData-Namespace bleiben lesbar (SAX-Stripper). */
	@Test
	void namespaceVarianteLesbar(@TempDir final File tempDir) throws Exception {
		// Base64 eines echten kleinen PNG zur Laufzeit erzeugen.
		final ScreenGraficData quelle = new ScreenGraficData("g1", new MyImage(testBild()));
		final String base64 = quelle.toBase64Png();

		final File dataDir = new File(tempDir, "SolvisServerData");
		assertTrue(dataDir.mkdirs());
		final String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<tns:SolvisGrafics xmlns:tns="http://www.example.org/graficData"
					controlResourceHashCode="1" controlFileHashCode="2">
					<tns:System id="mySolvis" configurationMask="16" baseConfigurationMask="0">
						<tns:Features>
							<tns:Feature id="Admin" value="false"/>
						</tns:Features>
						<tns:ScreenGrafic id="g1" isPattern="false">%s</tns:ScreenGrafic>
					</tns:System>
				</tns:SolvisGrafics>
				""".formatted(base64);
		Files.writeString(new File(dataDir, "graficData.xml").toPath(), xml);

		final AllSolvisGrafics gelesen = new GraficFileHandler(tempDir).read();
		assertEquals(Long.valueOf(1L), gelesen.getControlHashCodes().getResourceHash());
		final SystemGrafics system = gelesen.get("mySolvis", new Hashes(1L, 2L));
		assertEquals(16L, system.getConfigurationMask());
		assertNotNull(system.get("g1"));
		assertBildGleich(testBild(), system.get("g1").toBase64Png());
	}

	/** Defekte Datei: wie zuvor leere Lerndaten (Neu-Lernen), kein Abbruch. */
	@Test
	void defekteDateiErgibtLeereLerndaten(@TempDir final File tempDir) throws Exception {
		final File dataDir = new File(tempDir, "SolvisServerData");
		assertTrue(dataDir.mkdirs());
		Files.writeString(new File(dataDir, "graficData.xml").toPath(), "kein xml");

		final AllSolvisGrafics gelesen = new GraficFileHandler(tempDir).read();
		assertNull(gelesen.getControlHashCodes().getResourceHash());
		assertTrue(gelesen.get("egal", new Hashes(1L, 2L)).isEmpty());
	}
}
