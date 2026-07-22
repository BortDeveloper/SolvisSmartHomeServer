package de.sgollmer.solvismax.xml.jaxb;

import java.io.InputStream;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

/**
 * Liest {@code control.xml} über JAXB in {@link ControlDto} ein — der neue,
 * standardbasierte Parser für den dritten Baum (MODERNISIERUNG.md 3.3).
 * Quelle ist ein {@link InputStream}, weil die Datei sowohl als
 * Klassenpfad-Ressource als auch aus dem Schreibverzeichnis gelesen wird
 * (vgl. {@code ControlFileReader}).
 */
public final class JaxbControlReader {

	private JaxbControlReader() {
	}

	public static ControlDto read(final InputStream source) throws JAXBException {
		final JAXBContext context = JAXBContext.newInstance(ControlDto.class);
		return (ControlDto) context.createUnmarshaller().unmarshal(source);
	}
}
