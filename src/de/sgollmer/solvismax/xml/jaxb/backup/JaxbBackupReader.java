package de.sgollmer.solvismax.xml.jaxb.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

/**
 * Liest und schreibt die {@code measurements.xml} über JAXB
 * (MODERNISIERUNG.md 3.3).
 *
 * <p>
 * <b>Lesen:</b> per {@code declaredType} (beide Wurzel-Generationen
 * {@code SolvisBackup}/{@code SolvisMeasurements} werden akzeptiert) und mit
 * einem <b>namespace-strippenden SAX-Filter</b> — der alte Parser verglich nur
 * {@code localPart} und war damit namespace-tolerant; Alt-Dateien mit dem
 * measurements-Namespace bleiben so lesbar. <b>Schreiben:</b> immer die
 * aktuelle, namespace-lose Wurzel {@code <SolvisBackup>} — verhaltensgleich
 * mit dem alten {@code XMLStreamWriter}-Pfad.
 * </p>
 */
public final class JaxbBackupReader {

	private JaxbBackupReader() {
	}

	public static BackupDto read(final File file) throws JAXBException, IOException {
		final JAXBContext context = JAXBContext.newInstance(BackupDto.class);
		try (InputStream input = new FileInputStream(file)) {
			final SAXSource source = new SAXSource(de.sgollmer.solvismax.xml.jaxb.SaxNamespaceStripper.create(),
					new InputSource(input));
			return context.createUnmarshaller()
					.unmarshal(source, BackupDto.class)
					.getValue();
		} catch (ParserConfigurationException | SAXException e) {
			throw new JAXBException("SAX setup failed", e);
		}
	}

	public static void write(final BackupDto dto, final File file) throws JAXBException {
		final JAXBContext context = JAXBContext.newInstance(BackupDto.class);
		final Marshaller marshaller = context.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(dto, file);
	}
}
