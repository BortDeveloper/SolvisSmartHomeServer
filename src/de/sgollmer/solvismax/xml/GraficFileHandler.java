package de.sgollmer.solvismax.xml;

import java.io.File;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import de.sgollmer.solvismax.Constants;
import de.sgollmer.solvismax.error.FileException;
import de.sgollmer.solvismax.helper.FileHelper;
import de.sgollmer.solvismax.log.Diagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.sgollmer.solvismax.log.Diagnostics.Level;
import de.sgollmer.solvismax.model.Solvis;
import de.sgollmer.solvismax.model.objects.AllSolvisGrafics;
import de.sgollmer.solvismax.model.objects.GraficsMapper;
import de.sgollmer.solvismax.xml.jaxb.grafics.JaxbGraficsReader;
import de.sgollmer.xmllibrary.XmlException;

/**
 * Liest und schreibt die Grafik-Lerndaten {@code graficData.xml} — seit der
 * JAXB-Umstellung (MODERNISIERUNG.md 3.3) über {@code JaxbGraficsReader} +
 * {@code GraficsMapper}. Fehlerverhalten unverändert: eine fehlende oder
 * unlesbare Datei führt zu leeren Lerndaten (Neu-Lernen), nie zum Abbruch.
 */
public class GraficFileHandler {

	private static final Logger logger = LoggerFactory.getLogger(Solvis.class);
	private static final String NAME_XSD_GRAFICSFILE = "graficData.xsd";
	private static final String NAME_XML_GRAFICSFILE = "graficData.xml";

	private final File parent;

	public GraficFileHandler(final File path) {

		File writePath;

		if (path == null) {
			String pathName = System.getProperty("user.home");
			if (System.getProperty("os.name").startsWith("Windows")) {
				pathName = System.getenv("APPDATA");
			}
			writePath = new File(pathName);
		} else {
			writePath = path;
		}

		this.parent = new File(writePath, Constants.Files.RESOURCE_DESTINATION);
	}

	private void copyFiles() throws IOException, FileException {

		boolean success = true;

		if (!this.parent.exists()) {
			success = FileHelper.mkdir(this.parent);
		}

		if (!success) {
			throw new FileException("Error on creating directory <" + this.parent.getAbsolutePath() + ">");
		}

		File xsd = new File(this.parent, NAME_XSD_GRAFICSFILE);

		FileHelper.copyFromResourceText(Constants.Files.RESOURCE + '/' + NAME_XSD_GRAFICSFILE, xsd);

	}

	public AllSolvisGrafics read() throws IOException, XmlException, XMLStreamException, FileException {

		this.copyFiles();

		File xml = new File(this.parent, NAME_XML_GRAFICSFILE);

		if (!xml.exists()) {
			AllSolvisGrafics grafics = new AllSolvisGrafics();
			return grafics;
		}

		AllSolvisGrafics result;

		try {

			result = GraficsMapper.toDomain(JaxbGraficsReader.read(xml));

		} catch (IOException | jakarta.xml.bind.JAXBException e1) {
			// Wie zuvor: defekte Lerndaten sind kein Abbruchgrund - es wird
			// einfach neu gelernt.
			logger.error("Warning: Read error on grafics.xml file. A new one will be created.");
			result = new AllSolvisGrafics();
		} catch (Throwable e2) {
			Diagnostics.out(logger, Level.ERROR,
					"Unexpected error found on reading the grafics.xml, a new on will be cerated.\n"
							+ "If the error still exits, please conact the developer.",
					e2.getStackTrace());
			result = new AllSolvisGrafics();
		}

		return result;
	}

	public void write(final AllSolvisGrafics grafics) throws IOException, XMLStreamException, FileException {
		this.copyFiles();

		File output = new File(this.parent, NAME_XML_GRAFICSFILE);

		try {
			JaxbGraficsReader.write(GraficsMapper.toDto(grafics), output);
		} catch (jakarta.xml.bind.JAXBException e) {
			throw new IOException("JAXB writing of " + output.getName() + " failed: " + e.getMessage(), e);
		}
	}

}
