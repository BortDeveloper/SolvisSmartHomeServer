package de.sgollmer.solvismax.model.objects.screen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import de.sgollmer.solvismax.imagepatternrecognition.image.MyImage;
import de.sgollmer.solvismax.imagepatternrecognition.pattern.Pattern;

/**
 * Gelernte Grafik eines Bildschirmbereichs (Inhalt der
 * {@code graficData.xml}). Die Persistenzform ist ein Base64-codiertes PNG;
 * seit der JAXB-Umstellung (MODERNISIERUNG.md 3.3) liegt die Codierung/
 * Decodierung als eigene API hier ({@link #of}, {@link #toBase64Png}) — vorher
 * steckte sie im XML-Creator bzw. in {@code writeXml}.
 */
public class ScreenGraficData {
	private final String id;
	private final MyImage image;

	public ScreenGraficData(String id, MyImage image) {
		this.id = id;
		this.image = image;
	}

	/**
	 * Konstruktion aus der Persistenzform (JAXB-Weg): decodiert das Base64-PNG
	 * in ein {@link MyImage}; {@code isPattern} kennzeichnet gelernte
	 * {@link Pattern}-Grafiken (identisch zur alten Creator-Semantik).
	 */
	public static ScreenGraficData of(final String id, final String base64Png, final boolean isPattern)
			throws IOException {
		final byte[] bytes = Base64.getDecoder().decode(base64Png.trim());
		final BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
		MyImage image = new MyImage(bufferedImage);
		if (isPattern) {
			image = new Pattern(image);
		}
		return new ScreenGraficData(id, image);
	}

	/** Persistenzform: das Bild als Base64-codiertes PNG (wie der alte Writer). */
	public String toBase64Png() throws IOException {
		final BufferedImage bufferedImage = this.image.createBufferdImage();
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(bufferedImage, "png", bos);
		return Base64.getEncoder().encodeToString(bos.toByteArray());
	}

	/** Kennzeichnung für die Persistenz (wie das alte {@code isPattern}-Attribut). */
	public boolean isPattern() {
		return this.image instanceof Pattern;
	}

	public String getId() {
		return this.id;
	}

	/**
	 * @return the image
	 */
	MyImage getImage() {
		return this.image;
	}

}
