package de.sgollmer.solvismax.model.objects.backup;

import java.util.ArrayList;
import java.util.Collection;

import de.sgollmer.solvismax.helper.Helper.Reference;

public class AllSystemBackups {

	private final Collection<SystemBackup> systemBackups = new ArrayList<>();
	private final Reference<Long> timeOfLastBackup;

	public AllSystemBackups(final Reference<Long> timeOfLastBackup) {
		this.timeOfLastBackup = timeOfLastBackup;
	}

	SystemBackup get(final String id) {
		SystemBackup result = null;
		for (SystemBackup measurements : this.systemBackups) {
			if (id.equals(measurements.getId())) {
				result = measurements;
				break;
			}
		}
		if (result == null) {
			result = new SystemBackup(id, this.timeOfLastBackup);
			this.systemBackups.add(result);
		}
		return result;
	}

	/**
	 * @return the systemBackups
	 */
	Collection<SystemBackup> getSystemBackups() {
		return this.systemBackups;
	}

	public long getTimeStamp() {
		return this.timeOfLastBackup.get();
	}
}
