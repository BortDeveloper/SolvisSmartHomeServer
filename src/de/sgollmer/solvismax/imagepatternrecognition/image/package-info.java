/**
 * Bildmodell des OCR-Kerns: Zugriff auf Pixel/Helligkeit, Ausschnitte,
 * Histogramme und Bild-Metadaten.
 *
 * <p>
 * Zentrale Klasse ist
 * {@link de.sgollmer.solvismax.imagepatternrecognition.image.MyImage}: sie
 * kapselt ein {@link java.awt.image.BufferedImage} und liefert die fuer die
 * Erkennung noetigen Operationen (Teilbilder, Schwarz/Weiss-Histogramme,
 * Aktiv-/Hell-Erkennung). {@code ImageMeta} und {@code Maxima} sind Hilfstypen.
 * </p>
 *
 * <p>
 * Teil des OCR-Moduls; Ueberblick und Modulgrenze siehe
 * {@link de.sgollmer.solvismax.imagepatternrecognition.ocr}. Das Modul kennt
 * bewusst nur JDK-Bildklassen, die geteilten Geometrie-Typen und das Logging —
 * insbesondere <b>keine</b> Mail-/MQTT-/Modell-Abhaengigkeiten. {@code MyImage}
 * liefert Bilddaten als {@code byte[]} ({@code getImageBytes()}); das Einpacken
 * z. B. fuer einen Mail-Anhang ist Sache des Aufrufers.
 * </p>
 */
package de.sgollmer.solvismax.imagepatternrecognition.image;
