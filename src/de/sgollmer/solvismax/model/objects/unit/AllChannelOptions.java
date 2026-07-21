package de.sgollmer.solvismax.model.objects.unit;

import java.util.Collection;

import de.sgollmer.solvismax.error.TypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.sgollmer.solvismax.model.Solvis;
import de.sgollmer.solvismax.model.objects.data.DoubleValue;
import de.sgollmer.solvismax.model.objects.data.SingleData;
import de.sgollmer.solvismax.model.objects.data.SolvisData;

public class AllChannelOptions {

	private static final Logger logger = LoggerFactory.getLogger(AllChannelOptions.class);

	private final Collection<ChannelOption> values;

	private AllChannelOptions(final Collection<ChannelOption> values) {
		this.values = values;
	}

	/**
	 * Öffentliche Konstruktions-Factory für den JAXB-Mapper (MODERNISIERUNG.md
	 * 3.3, Weg B). {@code AllChannelOptions}/{@code ChannelOption} bleiben als
	 * Wert-Typen erhalten (die Options-Semantik — {@code modify},
	 * Fix-Werte, PowerOn-Delay — lebt nur hier); der Mapper baut die
	 * Options-Liste aus dem DTO.
	 */
	public static AllChannelOptions of(final Collection<ChannelOption> values) {
		return new AllChannelOptions(values);
	}

	public void initialize(Solvis solvis) {
		for (ChannelOption channelValue : this.values) {
			channelValue.initialize(solvis);
		}
	}

	public void setFixValues(Solvis solvis) {
		for (ChannelOption channelValue : this.values) {
			channelValue.setFixValues(solvis);
		}
	}

	public static class ChannelOption {

		private final String id;
		private final Double fix;
		private final Double factor;
		private final Double offset;
		private final int powerOnDelay;

		public ChannelOption(final String id, final Double fix, final Double factor, final Double offset,
				final Double value, final int powerOnDelay) {
			this.id = id;
			this.fix = fix;
			this.factor = factor;
			this.offset = offset;
			this.powerOnDelay = powerOnDelay;
		}

		public void initialize(Solvis solvis) {
			SolvisData data = solvis.getAllSolvisData().getByName(this.id);
			if (data == null) {
				logger.error("base.xml error: Channel <" + this.id + "> no defined.");
				return;
			}
			try {
				data.setChannelOption(this);
			} catch (TypeException e) {
				logger.error("base.xml error: Channel <" + this.id + "> can't be set by the given format.");
			}

		}

		public void setFixValues(Solvis solvis) {
			SolvisData data = solvis.getAllSolvisData().getByName(this.id);
			if (data == null) {
				logger.error("base.xml error: Channel <" + this.id + "> no defined.");
				return;
			}
			try {
				data.setFixedChannelValue(this);
			} catch (TypeException e) {
				logger.error("base.xml error: Channel <" + this.id + "> can't be set by the given format.");
			}

		}

		public SingleData<?> modify(final SolvisData data) throws TypeException {
			if (this.offset == null && this.factor == null) {
				return data.getSingleData();
			}

			SingleData<?> modified = data.getSingleData();

			if (this.factor != null) {
				modified = modified.mult(getModifyValue(this.factor, data));
			}

			if (this.offset != null) {
				modified = modified.add(getModifyValue(this.offset, data));
			}

			return modified;
		}

		private SingleData<?> getModifyValue(final Double value, final SolvisData data) throws TypeException {
			if (value == null) {
				return null;
			}
			DoubleValue doubleValue = new DoubleValue(value, -1L);
			return data.getDescription().interpretSetData(doubleValue, true);
		}

		public SingleData<?> getFixValue(final SolvisData data) throws TypeException {
			return this.getModifyValue(this.fix, data);
		}

		public int getPowerOnDelay() {
			return this.powerOnDelay;
		}

	}

}
