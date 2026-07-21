package de.sgollmer.solvismax.imagepatternrecognition.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.sgollmer.solvismax.imagepatternrecognition.image.MyImage;

/**
 * Golden-Tests fuer die OCR-Zeichenerkennung ({@link Ocr#toChar()}).
 *
 * <p>
 * Die OCR ist das inhaltliche Herzstueck des Programms (die Idee stammt vom
 * Upstream-Autor GollmerSt, siehe FORK.md). Sie ist bewusst nicht umgeschrieben,
 * aber durch dieses Sicherheitsnetz gegen Regressionen abgesichert: Fuer eine
 * Reihe von Referenzbildern (Ziffern und Sonderzeichen aus den Solvis-Screens,
 * unter {@code testFiles/images/}) wird das erwartete Zeichen festgeschrieben.
 * Aendert ein Umbau die Erkennung, schlaegt der Test an.
 * </p>
 *
 * <p>
 * Die erwarteten Werte entsprechen der (korrekten) Ist-Erkennung; die
 * Dateinamen kodieren zugleich den fachlich richtigen Wert (z. B. {@code 0.png}
 * -&gt; {@code '0'}), sodass der Test auch die inhaltliche Korrektheit prueft.
 * </p>
 */
class OcrGoldenTest {

	private static final File IMAGE_DIR = new File("testFiles", "images");

	static Stream<Arguments> referenzbilder() {
		return Stream.of(
				Arguments.of("0.png", '0'),
				Arguments.of("1.png", '1'),
				Arguments.of("1 small.png", '1'),
				Arguments.of("2.png", '2'),
				Arguments.of("3.png", '3'),
				Arguments.of("3 grey.png", '3'),
				Arguments.of("3 von HK.png", '3'),
				Arguments.of("4.png", '4'),
				Arguments.of("4 black.png", '4'),
				Arguments.of("4 small.png", '4'),
				Arguments.of("4 Feineinstellung.png", '4'),
				Arguments.of("5.png", '5'),
				Arguments.of("6.png", '6'),
				Arguments.of("7.png", '7'),
				Arguments.of("8.png", '8'),
				Arguments.of("9.png", '9'),
				Arguments.of("9 grey small.png", '9'),
				Arguments.of("minus.png", '-'),
				Arguments.of("minus2.png", '-'),
				Arguments.of("plus.png", '+'),
				Arguments.of("doppelpunkt.png", ':'),
				Arguments.of("punkt.png", '.'),
				Arguments.of("punkt small.png", '.'),
				Arguments.of("h small.png", 'h'),
				Arguments.of("grad.png", '°'),
				Arguments.of("C.png", 'C'),
				Arguments.of("square bracket left.png", '['),
				Arguments.of("square bracket right.png", ']'),
				Arguments.of("slash.png", '/'),
				Arguments.of("percent.png", '%'),
				Arguments.of("percent grey.png", '%'));
	}

	@ParameterizedTest(name = "{0} -> ''{1}''")
	@MethodSource("referenzbilder")
	void erkenntZeichen(final String dateiname, final char erwartet) throws Exception {
		final File file = new File(IMAGE_DIR, dateiname);
		// Test nur ausfuehren, wenn die Referenzbilder vorliegen (Arbeitsverzeichnis
		// = Projektwurzel bei Maven/Surefire).
		Assumptions.assumeTrue(file.isFile(), "Referenzbild fehlt: " + file);

		final BufferedImage image = ImageIO.read(file);
		assertTrue(image != null, "Bild nicht lesbar: " + file);

		// Oeffentlicher Konstruktionsweg (der interne Ocr(BufferedImage) ist
		// privat): MyImage aus dem Bild, dann Ocr. Verhaltensgleich zur internen
		// Variante (beide rufen super(image) + processing(false)).
		final char erkannt = new Ocr(new MyImage(image)).toChar();

		assertEquals(erwartet, erkannt,
				"Datei " + dateiname + ": erwartet '" + erwartet + "', erkannt '" + erkannt + "'");
	}
}
