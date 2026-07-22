package de.sgollmer.solvismax.xml.jaxb.grafics;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;

/**
 * JAXB-DTO für die Grafik-Lerndaten {@code graficData.xml}
 * (MODERNISIERUNG.md 3.3 — vierter Baum). Struktur:
 *
 * <pre>
 * &lt;SolvisGrafics controlResourceHashCode=".." controlFileHashCode=".."&gt;
 *   &lt;System id=".." configurationMask=".." baseConfigurationMask=".."&gt;
 *     &lt;Features&gt;&lt;Feature id=".." value=".."/&gt;…&lt;/Features&gt;
 *     &lt;ScreenGrafic id=".." isPattern="…"&gt;Base64-PNG&lt;/ScreenGrafic&gt;…
 *   &lt;/System&gt;…
 * &lt;/SolvisGrafics&gt;
 * </pre>
 *
 * <p>
 * Die Hash-Attribute sind als {@link Long}-Wrapper gebunden ({@code null} =
 * Attribut fehlt) — der alte Parser unterschied das ebenfalls (unbekannter
 * Hash ⇒ Lerndaten verwerfen). Die Bild-Daten sind der Base64-codierte
 * PNG-Textinhalt ({@code @XmlValue}); das Decodieren in {@code MyImage}/
 * {@code Pattern} liegt in {@code ScreenGraficData.of} (Domänen-Seite).
 * </p>
 */
@XmlRootElement(name = "SolvisGrafics")
@XmlAccessorType(XmlAccessType.FIELD)
public class GraficsDto {

	@XmlAttribute(name = "controlResourceHashCode")
	public Long controlResourceHashCode;

	@XmlAttribute(name = "controlFileHashCode")
	public Long controlFileHashCode;

	@XmlElement(name = "System")
	public List<SystemDto> system;

	/** JAXB-Bean für {@code <System>} — die Lerndaten einer Solvis-Anlage. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class SystemDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "configurationMask") public long configurationMask;
		@XmlAttribute(name = "baseConfigurationMask") public long baseConfigurationMask;

		@XmlElement(name = "Features")
		public FeaturesDto features;

		@XmlElement(name = "ScreenGrafic")
		public List<ScreenGraficDto> screenGrafic;
	}

	/** JAXB-Bean für {@code <Features>} (Feature-Stand zum Lernzeitpunkt). */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class FeaturesDto {
		@XmlElement(name = "Feature")
		public List<FeatureDto> feature;
	}

	/** JAXB-Bean für {@code <Feature id="..." value="..."/>}. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class FeatureDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "value") public boolean value;
	}

	/** JAXB-Bean für {@code <ScreenGrafic>} — Base64-PNG als Textinhalt. */
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ScreenGraficDto {
		@XmlAttribute(name = "id") public String id;
		@XmlAttribute(name = "isPattern") public boolean isPattern;

		@XmlValue
		public String base64Png;
	}
}
