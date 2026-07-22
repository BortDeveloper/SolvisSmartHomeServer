/**
 * JAXB-Bindungsklassen für die Grafik-Lerndaten-Datei {@code graficData.xml}
 * (MODERNISIERUNG.md 3.3).
 *
 * <p>
 * <b>Eigenes Paket, bewusst ohne {@code @XmlSchema}:</b> Wie bei der
 * Backup-Datei schreibt der Server die Datei <em>ohne</em> Namespace (so tat
 * es der alte {@code XMLStreamWriter}-Pfad); gebunden wird daher
 * unqualifiziert, beim Lesen entfernt der gemeinsame
 * {@code SaxNamespaceStripper} etwaige Namespaces (Toleranz des alten,
 * localPart-basierten Parsers).
 * </p>
 */
package de.sgollmer.solvismax.xml.jaxb.grafics;
