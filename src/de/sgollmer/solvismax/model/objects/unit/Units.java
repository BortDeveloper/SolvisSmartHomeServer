package de.sgollmer.solvismax.model.objects.unit;

import java.util.Collection;

public class Units {

	private final Collection<Unit> units;
	private boolean mailEnabled = false;

	private Units(final Collection<Unit> units) {
		this.units = units;
		for (Unit unit : units) {
			this.mailEnabled |= unit.isMailEnabled();
		}
	}

	/** Öffentliche Konstruktions-Factory für den JAXB-Mapper (MODERNISIERUNG.md 3.3, Weg B). */
	public static Units of(final Collection<Unit> units) {
		return new Units(units);
	}

	public Collection<Unit> getUnits() {
		return this.units;
	}

	public boolean isMailEnabled() {
		return this.mailEnabled;
	}
}
