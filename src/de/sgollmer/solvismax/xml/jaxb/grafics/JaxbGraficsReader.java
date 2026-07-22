package de.sgollmer.solvismax.xml.jaxb.grafics;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.sgollmer.solvismax.xml.jaxb.SaxNamespaceStripper;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

/**
 * Liest und schreibt die {@code graficData.xml} über JAXB
 * (MODERNISIERUNG.md 3.3, vierter Baum).
 *
 * <p>
 * <b>Lesen:</b> per {@code declaredType} und namespace-strippendem SAX-Filter
 * (Toleranz des alten Parsers — vom Server geschriebene Dateien sind
 * namespace-los, Fremd-/Altbestände könnten den graficData-Namespace tragen).
 * <b>Schreiben:</b> JAXB-Marshalling der namespace-losen Wurzel
 * {@code <SolvisGrafics>}. Nebeneffekt gegenüber dem alten Writer: dessen
 * Attribut-Schreibfehler bei mehreren {@code <System>}-Einträgen (Attribute
 * wurden je Schleifendurchlauf erneut auf die Wurzel geschrieben) ist damit
 * behoben.
 * </p>
 */
public final class JaxbGraficsReader {

	private JaxbGraficsReader() {
	}

	public static GraficsDto read(final File file) throws JAXBException, IOException {
		final JAXBContext context = JAXBContext.newInstance(GraficsDto.class);
		try (InputStream input = new FileInputStream(file)) {
			final SAXSource source = new SAXSource(SaxNamespaceStripper.create(), new InputSource(input));
			return context.createUnmarshaller()
					.unmarshal(source, GraficsDto.class)
					.getValue();
		} catch (ParserConfigurationException | SAXException e) {
			throw new JAXBException("SAX setup failed", e);
		}
	}

	public static void write(final GraficsDto dto, final File file) throws JAXBException {
		final JAXBContext context = JAXBContext.newInstance(GraficsDto.class);
		final Marshaller marshaller = context.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(dto, file);
	}
}
