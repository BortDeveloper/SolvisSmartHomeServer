/**
 * JAXB-Bindungsklassen für die Mess-/Backup-Datei {@code measurements.xml}
 * (MODERNISIERUNG.md 3.3).
 *
 * <p>
 * <b>Eigenes Paket, bewusst ohne {@code @XmlSchema}:</b> Das Eltern-Paket ist
 * auf den {@code base.xsd}-Namespace qualifiziert; die Backup-Datei wird vom
 * Server aber <em>ohne</em> Namespace geschrieben (so tat es schon der alte
 * {@code XMLStreamWriter}-Pfad), während Alt-Dateien mit dem
 * measurements-Namespace existieren können. Gebunden wird daher unqualifiziert;
 * beim Lesen entfernt ein SAX-Filter etwaige Namespaces — die
 * Namespace-Toleranz des alten, localPart-basierten Parsers.
 * </p>
 */
package de.sgollmer.solvismax.xml.jaxb.backup;
