package de.sgollmer.solvismax.model.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import de.sgollmer.solvismax.xml.ControlFileReader.Hashes;

/**
 * Gesamtheit der gelernten Grafik-Daten ({@code graficData.xml}): je Anlage
 * ein {@link SystemGrafics}-Eintrag plus die Hashes der {@code control.xml},
 * mit der gelernt wurde. Stimmen die Hashes nicht mehr mit der aktuellen
 * control.xml überein, sind die Lerndaten ungültig und werden verworfen
 * ({@link #get}). Persistiert über den JAXB-Pfad
 * ({@code GraficsMapper}/{@code JaxbGraficsReader}, MODERNISIERUNG.md 3.3).
 */
public class AllSolvisGrafics {

	private final Collection<SystemGrafics> systems;
	private Long controlResourceHashCode;
	private Long controlFileHashCode;

	private AllSolvisGrafics(final Collection<SystemGrafics> systems, final Long controlResourceHashCode,
			final Long controlFileHashCode) {
		this.systems = systems;
		this.controlResourceHashCode = controlResourceHashCode;
		this.controlFileHashCode = controlFileHashCode;
	}

	public AllSolvisGrafics() {
		this.systems = new ArrayList<>();
		this.controlResourceHashCode = null;
		this.controlFileHashCode = null;
	}

	/** Paketinterne Konstruktions-Factory für den {@code GraficsMapper} (JAXB-Weg). */
	static AllSolvisGrafics of(final Collection<SystemGrafics> systems, final Long controlResourceHashCode,
			final Long controlFileHashCode) {
		return new AllSolvisGrafics(systems, controlResourceHashCode, controlFileHashCode);
	}

	/** Paketinterner Zugang für den {@code GraficsMapper} (Persistenz). */
	Collection<SystemGrafics> getSystems() {
		return this.systems;
	}

	/**
	 * Liefert die Lerndaten der Anlage {@code unitId}; passt der gespeicherte
	 * control.xml-Stand (Hashes) nicht mehr, werden alle Lerndaten verworfen
	 * und ein leerer Eintrag geliefert (Neu-Lernen nötig).
	 */
	public SystemGrafics get(final String unitId, final Hashes hashes) {
		if (this.controlResourceHashCode == null || !this.controlResourceHashCode.equals(hashes.getResourceHash())
				|| this.controlFileHashCode == null || !this.controlFileHashCode.equals(hashes.getFileHash())) {
			this.systems.clear();
			this.controlResourceHashCode = hashes.getResourceHash();
			this.controlFileHashCode = hashes.getFileHash();
		}
		SystemGrafics result = null;
		boolean finish = false;
		for (Iterator<SystemGrafics> it = this.systems.iterator(); it.hasNext() && !finish;) {
			SystemGrafics system = it.next();
			if (system.getId().equals(unitId)) {
				result = system;
				finish = true;
			}
		}
		if (result == null) {
			result = new SystemGrafics(unitId);
			this.systems.add(result);
		}

		return result;
	}

	public Hashes getControlHashCodes() {
		return new Hashes(this.controlResourceHashCode, this.controlFileHashCode);
	}

}
