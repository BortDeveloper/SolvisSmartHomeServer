package de.sgollmer.solvismax.model.objects.backup;

import java.util.ArrayList;
import java.util.Collection;

import de.sgollmer.solvismax.helper.Helper.Reference;
import de.sgollmer.solvismax.model.Solvis;

public class SystemBackup {

	private final String id;
	private final Collection<IValue> values;
	private Solvis owner;
	private final Reference<Long> timeOfLastBackup;

	SystemBackup(final String id, final Reference<Long> timeOfLastBackup) {
		this.id = id;
		this.values = new ArrayList<>();
		this.timeOfLastBackup = timeOfLastBackup;
	}

	public Collection<IValue> getValues() {
		return this.values;
	}

	public void add(final IValue value) {
		this.values.add(value);
	}

	String getId() {
		return this.id;
	}

	Solvis getOwner() {
		return this.owner;
	}

	void setOwner(final Solvis owner) {
		this.owner = owner;
	}

	public void clear() {
		this.values.clear();

	}

	public long getTimeOfLastBackup() {
		return this.timeOfLastBackup.get();
	}

	public interface IValue {

		public String getId();

	}
}
