/**
 * JAXB-Bindungsklassen (DTOs) für das Einlesen der Konfiguration — der
 * Standard-Ersatz für die proprietäre {@code XMLLibrary} (MODERNISIERUNG.md
 * 3.3).
 *
 * <p>
 * Die DTOs spiegeln die XML-Struktur (mutable JAXB-Beans). Ein Mapper überführt
 * sie in die (immutablen) Domänentypen; der Dual-Parse-Differenztest sichert ab,
 * dass das Ergebnis mit dem bisherigen Parser identisch ist. Der Namespace und
 * {@code elementFormDefault=QUALIFIED} entsprechen {@code base.xsd}.
 * </p>
 */
@XmlSchema(namespace = "http://www.example.org/control", elementFormDefault = XmlNsForm.QUALIFIED)
package de.sgollmer.solvismax.xml.jaxb;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
