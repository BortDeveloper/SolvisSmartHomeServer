package de.sgollmer.solvismax.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import de.sgollmer.solvismax.BaseData;

/**
 * Rahmen des <b>Dual-Parse-Differenztests</b> und Selbsttest von {@link ParseDiff}.
 *
 * <p>
 * Solange nur der XMLLibrary-Parser existiert, sichert dieser Test zweierlei ab:
 * dass die kanonische Serialisierung <b>deterministisch</b> ist und der Parser
 * <b>stabil</b> denselben Graphen liefert (zweimaliges Parsen → identische
 * kanonische Form), und dass {@link ParseDiff} Unterschiede tatsächlich
 * <b>erkennt</b>. Sobald der JAXB-Parser für {@code base.xml} steht, kommt hier
 * der eigentliche Vergleich hinzu:
 * {@code assertEquals(canonical(altGeparst), canonical(jaxbGeparst))}.
 * </p>
 */
class ParseDiffTest {

	private static final String TEMPLATE = "rsc/de/sgollmer/solvismax/data/base.xml";
	private static final String MINIMAL = "testFiles/xml/base-minimal.xml";
	private static final String EXTENDED = "testFiles/xml/base-extended.xml";

	private BaseData parse() throws Exception {
		Assumptions.assumeTrue(new File(TEMPLATE).isFile(), "Vorlage fehlt: " + TEMPLATE);
		return new BaseControlFileReader(TEMPLATE).read();
	}

	@Test
	void kanonisierungIstDeterministischUndParserStabil() throws Exception {
		final String erste = ParseDiff.canonical(parse());
		final String zweite = ParseDiff.canonical(parse());
		assertEquals(erste, zweite,
				"Zwei Parse-Läufe müssen dieselbe kanonische Form ergeben (Determinismus/Stabilität)");
	}

	/**
	 * <b>Golden-Snapshot:</b> pinnt die kanonische Form des kompletten
	 * Domänengraphen je Eingabedatei ({@code testFiles/xml/golden/}). Die
	 * Snapshots wurden — vor der Entfernung des alten Creator-Pfads — aus diesem
	 * erzeugt und konservieren damit das Verhalten des Original-Parsers
	 * dauerhaft; jede Verhaltensänderung des JAXB-Pfads schlägt hier an.
	 */
	@Test
	void kanonischeFormEntsprichtGoldenSnapshot() throws Exception {
		for (final String datei : new String[] { TEMPLATE, MINIMAL, EXTENDED }) {
			Assumptions.assumeTrue(new File(datei).isFile(), "Datei fehlt: " + datei);
			final java.nio.file.Path golden = goldenPfad(datei);
			Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(golden), "Golden fehlt: " + golden);
			final String kanonisch = ParseDiff.canonical(new BaseControlFileReader(datei).read());
			assertEquals(java.nio.file.Files.readString(golden), kanonisch,
					"Kanonische Form weicht vom Golden-Snapshot ab: " + datei);
		}
	}

	private static java.nio.file.Path goldenPfad(final String datei) {
		final String name = new File(datei).getName().replace(".xml", "");
		return java.nio.file.Path.of("testFiles", "xml", "golden", name + ".canonical.txt");
	}

	@Test
	void erkenntUnterschiede() {
		assertNotEquals(
				ParseDiff.canonical(List.of(1, 2, 3)),
				ParseDiff.canonical(List.of(1, 2, 4)),
				"ParseDiff muss unterschiedliche Werte unterscheiden");
	}

	@Test
	void gleicheEingabeGleicheAusgabe() {
		assertEquals(
				ParseDiff.canonical(List.of("a", "b")),
				ParseDiff.canonical(List.of("a", "b")));
	}
}
