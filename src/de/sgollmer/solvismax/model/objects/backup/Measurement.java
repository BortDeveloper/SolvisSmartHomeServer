package de.sgollmer.solvismax.model.objects.backup;

import de.sgollmer.solvismax.model.objects.data.IMode;
import de.sgollmer.solvismax.model.objects.data.ModeValue;
import de.sgollmer.solvismax.model.objects.data.SingleData;

public class Measurement implements SystemBackup.IValue {

	private final String id;
	private final SingleData<?> data;

	public Measurement(final String id, final SingleData<?> data) {
		this.id = id;
		this.data = data;
	}

	/**
	 * @return the data
	 */
	public SingleData<?> getData() {
		return this.data;
	}

	/**
	 * Backup-eigener Modus-Wert (nur der Name ist persistiert). War vor der
	 * JAXB-Umstellung eine innere Klasse des {@code ValueCreator}; wird jetzt
	 * vom {@link BackupMapper} genutzt.
	 */
	static class Mode implements IMode<Mode> {

		private final String data;

		Mode(final String data) {
			this.data = data;
		}

		@Override
		public int compareTo(final Mode o) {
			return this.data.compareTo(o.data);
		}

		@Override
		public String getName() {
			return this.data;
		}

		@Override
		public ModeValue<?> create(final long timeStamp) {
			return new ModeValue<>(this, timeStamp);
		}

		@Override
		public boolean equals(final Object obj) {
			if (!(obj instanceof IMode)) {
				return false;
			}
			return this.getName().equals(((IMode<?>) obj).getName());
		}

		@Override
		public int hashCode() {
			return this.data.hashCode();
		}

		@Override
		public Handling getHandling() {
			return null;
		}

		@Override
		public String getCvsMeta() {
			return null;
		}
	}

	/**
	 * @return the id
	 */
	@Override
	public String getId() {
		return this.id;
	}

}
