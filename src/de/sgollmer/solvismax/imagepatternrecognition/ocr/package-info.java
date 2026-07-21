/**
 * OCR-Kern des SolvisSmartHomeServer: Erkennung einzelner Zeichen (Ziffern,
 * {@code + - : . / % ° [ ] h C} u. a.) aus den Bildausschnitten der grafischen
 * SolvisRemote-Oberflaeche.
 *
 * <h2>Fachlicher Ursprung</h2>
 * Die zugrunde liegende Idee — eine Solvis-Anlage ohne native Schnittstelle
 * ueber ihre grafische Web-Oberflaeche per OCR auszulesen und zu steuern —
 * stammt vom urspruenglichen Autor <b>Stefan Gollmer (GollmerSt)</b>. Dieser
 * Fork behaelt den Ansatz bewusst bei und schreibt ihn nicht um; er wird
 * lediglich <em>isoliert</em> und durch Tests abgesichert (siehe FORK.md /
 * MODERNISIERUNG.md).
 *
 * <h2>Oeffentliche Schnittstelle (Modulgrenze)</h2>
 * Einstiegspunkt ist {@link de.sgollmer.solvismax.imagepatternrecognition.ocr.Ocr}:
 * <pre>
 *   MyImage bild = new MyImage(bufferedImage);   // Bild einlesen
 *   char zeichen = new Ocr(bild).toChar();       // ein Zeichen erkennen
 * </pre>
 * {@link de.sgollmer.solvismax.imagepatternrecognition.ocr.OcrRectangle} erkennt
 * einen rechteckigen Ausschnitt; das eigentliche Bildmodell liegt in
 * {@link de.sgollmer.solvismax.imagepatternrecognition.image}, die
 * Muster-/Formerkennung in
 * {@link de.sgollmer.solvismax.imagepatternrecognition.pattern}.
 *
 * <h2>Abhaengigkeitsgrenze</h2>
 * Das Modul ist bewusst schlank gekoppelt: es nutzt nur JDK-Bordmittel
 * ({@code java.awt.image}, {@code javax.imageio}, {@code java.io/util}), die
 * geteilten Geometrie-Wertetypen {@code objects.Coordinate}/{@code objects.Rectangle}
 * und das Logging ({@code log.Diagnostics}/SLF4J). Es haengt <b>nicht</b> vom
 * Solvis-Modell, der Konfiguration, der MQTT- oder der Mail-Schicht ab. (Die
 * fruehere Kopplung an {@code javax.mail} im Bildmodul wurde entfernt, Fork 3.1.)
 *
 * <h2>Regressionsschutz</h2>
 * Die Zeichenerkennung ist durch Golden-Tests abgesichert:
 * {@code OcrGoldenTest} prueft eine Reihe von Referenzbildern
 * ({@code testFiles/images/}) gegen das erwartete Zeichen. Aenderungen am Modul
 * muessen diese Tests gruen halten.
 */
package de.sgollmer.solvismax.imagepatternrecognition.ocr;
