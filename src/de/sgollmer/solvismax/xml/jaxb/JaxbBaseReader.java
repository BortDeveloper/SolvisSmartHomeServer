package de.sgollmer.solvismax.xml.jaxb;

import java.io.File;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

/**
 * Liest {@code base.xml} über JAXB in {@link BaseDataDto} ein — der neue,
 * standardbasierte Parser (Ersatz für die {@code XMLLibrary}, MODERNISIERUNG.md
 * 3.3).
 *
 * <p>
 * Noch nicht modellierte Elemente/Attribute werden von JAXB ignoriert; der
 * DTO-Baum wächst schrittweise, abgesichert durch den Dual-Parse-Differenztest.
 * </p>
 */
public final class JaxbBaseReader {

	private JaxbBaseReader() {
	}

	public static BaseDataDto read(final String path) throws JAXBException {
		final JAXBContext context = JAXBContext.newInstance(BaseDataDto.class);
		final Unmarshaller unmarshaller = context.createUnmarshaller();
		return (BaseDataDto) unmarshaller.unmarshal(new File(path));
	}
}
