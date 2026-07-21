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

	private BaseData parseMitXmlLibrary() throws Exception {
		Assumptions.assumeTrue(new File(TEMPLATE).isFile(), "Vorlage fehlt: " + TEMPLATE);
		return new BaseControlFileReader(TEMPLATE).readWithCreators();
	}

	@Test
	void kanonisierungIstDeterministischUndParserStabil() throws Exception {
		final String erste = ParseDiff.canonical(parseMitXmlLibrary());
		final String zweite = ParseDiff.canonical(parseMitXmlLibrary());
		assertEquals(erste, zweite,
				"Zwei Parse-Läufe müssen dieselbe kanonische Form ergeben (Determinismus/Stabilität)");
	}

	/**
	 * <b>Der Reader-Umstieg-Beweis:</b> Für Template, Minimal- und erweiterte
	 * Fixture muss der neue JAXB-Pfad ({@code read()}) einen Domänengraphen
	 * liefern, dessen kanonische Form <b>zeichengleich</b> mit der des alten
	 * Creator-Pfads ({@code readWithCreators()}) ist — jedes über einen
	 * öffentlichen Getter erreichbare Feld ist damit verglichen, ohne blinden
	 * Fleck durch vergessene Einzel-Assertions.
	 */
	@Test
	void readerUmstiegVollGraphIdentisch() throws Exception {
		for (final String datei : new String[] { TEMPLATE, MINIMAL, EXTENDED }) {
			Assumptions.assumeTrue(new File(datei).isFile(), "Datei fehlt: " + datei);
			final BaseData alt = new BaseControlFileReader(datei).readWithCreators();
			final BaseData neu = new BaseControlFileReader(datei).read();
			assertEquals(ParseDiff.canonical(alt), ParseDiff.canonical(neu),
					"Domänengraph weicht ab für: " + datei);
		}
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
