package de.sgollmer.solvismax.model.objects;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.sgollmer.solvismax.log.Diagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.sgollmer.solvismax.model.objects.screen.ScreenGraficDescription;
import de.sgollmer.xmllibrary.XmlException;

public class AllScreenGraficDescriptions {

	private static final Logger logger = LoggerFactory.getLogger(AllScreenGraficDescriptions.class);

	private final Map<String, ScreenGraficDescription> screenGraficDescriptions = new HashMap<>();

	public void add(final ScreenGraficDescription grafic) {
		ScreenGraficDescription former = this.screenGraficDescriptions.put(grafic.getId(), grafic);
		if (former != null) {
			logger.error("ScreenGraficDescription <" + grafic.getId() + "> is not unique.");
		}
	}

	void addAll(final Collection<ScreenGraficDescription> grafics) {
		for (ScreenGraficDescription grafic : grafics) {
			this.add(grafic);
		}
	}

	public ScreenGraficDescription get(final String id) throws XmlException {
		ScreenGraficDescription description = this.screenGraficDescriptions.get(id);
		if (description == null) {
			throw new XmlException("ScreenGrafic of <" + id + "> not known, check the control.xml");
		}
		return description;
	}

}
