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

	private BaseData parseMitXmlLibrary() throws Exception {
		Assumptions.assumeTrue(new File(TEMPLATE).isFile(), "Vorlage fehlt: " + TEMPLATE);
		return new BaseControlFileReader(TEMPLATE).read();
	}

	@Test
	void kanonisierungIstDeterministischUndParserStabil() throws Exception {
		final String erste = ParseDiff.canonical(parseMitXmlLibrary());
		final String zweite = ParseDiff.canonical(parseMitXmlLibrary());
		assertEquals(erste, zweite,
				"Zwei Parse-Läufe müssen dieselbe kanonische Form ergeben (Determinismus/Stabilität)");
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
