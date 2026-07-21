package de.sgollmer.solvismax.xml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

import de.sgollmer.solvismax.BaseData;
import de.sgollmer.solvismax.Constants;
import de.sgollmer.solvismax.Main;
import de.sgollmer.solvismax.error.AssignmentException;
import de.sgollmer.solvismax.error.ReferenceException;
import de.sgollmer.solvismax.helper.FileHelper;
import de.sgollmer.solvismax.log.Diagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.sgollmer.solvismax.log.Diagnostics.Level;
import de.sgollmer.solvismax.xml.jaxb.JaxbBaseReader;
import de.sgollmer.solvismax.xml.jaxb.Mapper;
import de.sgollmer.xmllibrary.XmlException;

import jakarta.xml.bind.JAXBException;

public class BaseControlFileReader {

	private static final Logger logger = LoggerFactory.getLogger(BaseControlFileReader.class);

	private static final String NAME_XML_BASEFILE = "base.xml";
	private static final String NAME_XSD_BASEFILE = "base.xsd";

	private final File parent;
	private final File baseXml;

	public BaseControlFileReader(final String baseXmlString) {
		this.parent = FileHelper.getJarDir(BaseControlFileReader.class);
		if (baseXmlString == null) {
			this.baseXml = new File(this.parent, NAME_XML_BASEFILE);
		} else {
			this.baseXml = new File(de.sgollmer.solvismax.helper.Helper.replaceEnvironments(baseXmlString));
		}
	}

	/**
	 * Liest die {@code base.xml} — seit dem <b>Reader-Umstieg</b>
	 * (MODERNISIERUNG.md 3.3, Weg B) über den Standard-Stack: XSD-Validierung
	 * per {@code javax.xml.validation}, Parsen per JAXB
	 * ({@link JaxbBaseReader}), Konstruktion des Domänengraphen per
	 * {@link Mapper#toBaseData}. Fehlerverhalten wie zuvor: fehlende XSD oder
	 * invalide Datei → FATAL-Diagnose und {@code null}.
	 */
	public BaseData read()
			throws IOException, XMLStreamException, AssignmentException, ReferenceException, XmlException {

		String resourcePath = Constants.Files.RESOURCE + '/' + NAME_XSD_BASEFILE;
		InputStream xsd = Main.class.getResourceAsStream(resourcePath);

		if (xsd == null) {
			Diagnostics.record(logger, Level.FATAL, "Getting of " + NAME_XSD_BASEFILE + " fails", null,
					Constants.ExitCodes.BASE_XML_ERROR);
			return null;
		}

		try (InputStream xsdStream = xsd) {
			SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = schemaFactory.newSchema(new StreamSource(xsdStream));
			schema.newValidator().validate(new StreamSource(this.baseXml));
		} catch (SAXException e) {
			logger.error("XSD validation of " + this.baseXml.getName() + " failed: " + e.getMessage());
			Diagnostics.record(logger, Level.FATAL, "Reading of " + NAME_XML_BASEFILE + " not successfull", null,
					Constants.ExitCodes.BASE_XML_ERROR);
			return null;
		}

		try {
			return Mapper.toBaseData(JaxbBaseReader.read(this.baseXml.getPath()));
		} catch (JAXBException e) {
			throw new XmlException("JAXB parsing of " + this.baseXml.getName() + " failed: " + e.getMessage());
		}
	}

	public static void main(final String[] args)
			throws IOException, XmlException, XMLStreamException, AssignmentException, ReferenceException {

		BaseControlFileReader reader = new BaseControlFileReader(null);
		reader.read();
	}

}
