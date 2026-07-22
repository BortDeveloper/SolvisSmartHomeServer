package de.sgollmer.solvismax.xml.jaxb;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * SAX-Filter, der beim Einlesen sämtliche Namespaces entfernt — die
 * <b>Namespace-Toleranz des alten Parsers</b> für JAXB nachgebildet
 * (MODERNISIERUNG.md 3.3).
 *
 * <p>
 * Hintergrund: Der bisherige {@code XmlStreamReader} verglich Elemente nur über
 * ihren {@code localPart} und akzeptierte daher Dateien <em>mit</em> beliebigem
 * und <em>ohne</em> Namespace gleichermaßen. Vom Server geschriebene Dateien
 * ({@code measurements.xml}, {@code graficData.xml}) sind namespace-los,
 * während Alt-/Handbestände den jeweiligen XSD-Namespace tragen können. JAXB
 * dagegen matcht strikt über QNames — dieser Filter normalisiert deshalb alle
 * Elemente und Attribute auf „kein Namespace", bevor sie den Unmarshaller
 * erreichen. Die zugehörigen DTO-Pakete sind entsprechend unqualifiziert
 * gebunden (kein {@code @XmlSchema}).
 * </p>
 */
public final class SaxNamespaceStripper {

	private SaxNamespaceStripper() {
	}

	/** Namespace-strippender {@link XMLReader} für eine {@code SAXSource}. */
	public static XMLReader create() throws ParserConfigurationException, SAXException {
		final SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		final XMLReader reader = factory.newSAXParser().getXMLReader();
		return new XMLFilterImpl(reader) {
			@Override
			public void startElement(final String uri, final String localName, final String qName,
					final Attributes atts) throws SAXException {
				super.startElement("", localName, localName, stripNamespaces(atts));
			}

			@Override
			public void endElement(final String uri, final String localName, final String qName)
					throws SAXException {
				super.endElement("", localName, localName);
			}

			private Attributes stripNamespaces(final Attributes atts) {
				final AttributesImpl stripped = new AttributesImpl();
				for (int i = 0; i < atts.getLength(); i++) {
					// Namespace-Deklarationen (xmlns...) nicht durchreichen.
					if (atts.getQName(i).startsWith("xmlns")) {
						continue;
					}
					stripped.addAttribute("", atts.getLocalName(i), atts.getLocalName(i), atts.getType(i),
							atts.getValue(i));
				}
				return stripped;
			}
		};
	}
}
