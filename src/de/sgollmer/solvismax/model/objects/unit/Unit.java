package de.sgollmer.solvismax.model.objects.unit;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

import de.sgollmer.solvismax.connection.IAccountInfo;
import de.sgollmer.solvismax.crypt.CryptAes;
import de.sgollmer.solvismax.model.objects.AllDurations;
import de.sgollmer.solvismax.model.objects.ChannelAssignment;
import de.sgollmer.solvismax.model.objects.Duration;

// Erfüllt die schmale Config-Sicht UnitConfig (Weg B): Konsumenten der flachen
// Skalarwerte hängen an der Sicht, nicht am ganzen Aggregat.
public class Unit implements IAccountInfo, UnitConfig {

	private final String id;
	private final boolean admin;
	private final Configuration configuration;
	private final Collection<String> urls;
	private final String url;
	private final String account;
	private final CryptAes password;
	private final int defaultAverageCount;
	private final int measurementHysteresisFactor;
	private final int defaultMeasurementsInterval_ms;
	private final int defaultMeasurementsIntervalFast_ms;
	private final int forceUpdateAfterFastChangingIntervals;
	private final int forcedUpdateInterval_ms;
	private final int doubleUpdateInterval_ms;
	private final int bufferedInterval_ms;
	private final int watchDogTime_ms;
	private final int releaseBlockingAfterUserAccess_ms;
	private final int releaseBlockingAfterServiceAccess_ms;
	private final int reheatingNotRequiredActiveTime_ms;
	private final int resetErrorDelayTime_ms;
	private final boolean delayAfterSwitchingOnEnable;
	private final boolean fwLth2_21_02A;
	private final Features features;
	private final int ignoredFrameThicknesScreenSaver;
	private final Collection<Pattern> ignoredChannels;
	private final Map<String, ChannelAssignment> assignments;
	private Long forcedConfigMask = null;
	private final boolean csvUnit;
	private final AllDurations durations;
	private final AllChannelOptions channelOptions;

	private Unit(final String id, final Configuration configuration, final Collection<String> urls, final String url,
			final String account, final CryptAes password, final int defaultAverageCount,
			final int measurementHysteresisFactor, final int defaultMeasurementsInterval_ms,
			final int defaultMeasurementsIntervalFast_ms, final int forceUpdateAfterFastChangingIntervals,
			final int forcedUpdateInterval_ms, final int doubleUpdateInterval_ms, final int bufferedInterval_ms,
			final int watchDogTime_ms, final int releaseBlockingAfterUserAccess_ms,
			final int releaseBlockingAfterServiceAccess_ms, final int clearNotRequiredTime_ms,
			final int resetErrorDelayTime_ms, final boolean delayAfterSwitchingOn, final boolean fwLth2_21_02A,
			final Features features, final int ignoredFrameThicknesScreenSaver,
			final Collection<Pattern> ignoredChannels, final Map<String, ChannelAssignment> assignments,
			final boolean csvUnit, final AllDurations durations, final AllChannelOptions channelOptions) {
		this.id = id;
		this.configuration = configuration;
		this.url = url;
		this.urls = urls;
		this.account = account;
		this.password = password;
		this.defaultAverageCount = defaultAverageCount;
		this.measurementHysteresisFactor = measurementHysteresisFactor;
		this.defaultMeasurementsInterval_ms = defaultMeasurementsInterval_ms;
		this.defaultMeasurementsIntervalFast_ms = defaultMeasurementsIntervalFast_ms;
		this.forceUpdateAfterFastChangingIntervals = forceUpdateAfterFastChangingIntervals;
		this.forcedUpdateInterval_ms = forcedUpdateInterval_ms;
		this.doubleUpdateInterval_ms = doubleUpdateInterval_ms;
		this.bufferedInterval_ms = bufferedInterval_ms;
		this.watchDogTime_ms = watchDogTime_ms;
		this.releaseBlockingAfterUserAccess_ms = releaseBlockingAfterUserAccess_ms;
		this.releaseBlockingAfterServiceAccess_ms = releaseBlockingAfterServiceAccess_ms;
		this.resetErrorDelayTime_ms = resetErrorDelayTime_ms;
		this.reheatingNotRequiredActiveTime_ms = clearNotRequiredTime_ms;
		this.delayAfterSwitchingOnEnable = delayAfterSwitchingOn;
		this.fwLth2_21_02A = fwLth2_21_02A;
		this.features = features;
		this.ignoredFrameThicknesScreenSaver = ignoredFrameThicknesScreenSaver;
		this.ignoredChannels = ignoredChannels;
		this.assignments = assignments;
		this.csvUnit = csvUnit;
		this.durations = durations;
		this.channelOptions = channelOptions;
		this.admin = features.isAdmin();
		;
	}

	/**
	 * Öffentliche Konstruktions-Factory für den JAXB-Mapper (MODERNISIERUNG.md
	 * 3.3, Weg B). Bewusst schlank: reicht nur den (privaten) Konstruktor durch;
	 * sämtliche Interpretationslogik (Einheiten-Ableitungen, passwordCrypt,
	 * Pattern-Kompilierung, Defaults) liegt im Mapper.
	 */
	public static Unit of(final String id, final Configuration configuration, final Collection<String> urls,
			final String url, final String account, final CryptAes password, final int defaultAverageCount,
			final int measurementHysteresisFactor, final int defaultMeasurementsInterval_ms,
			final int defaultMeasurementsIntervalFast_ms, final int forceUpdateAfterFastChangingIntervals,
			final int forcedUpdateInterval_ms, final int doubleUpdateInterval_ms, final int bufferedInterval_ms,
			final int watchDogTime_ms, final int releaseBlockingAfterUserAccess_ms,
			final int releaseBlockingAfterServiceAccess_ms, final int reheatingNotRequiredActiveTime_ms,
			final int resetErrorDelayTime_ms, final boolean delayAfterSwitchingOnEnable, final boolean fwLth2_21_02A,
			final Features features, final int ignoredFrameThicknesScreenSaver,
			final Collection<Pattern> ignoredChannels, final Map<String, ChannelAssignment> assignments,
			final boolean csvUnit, final AllDurations durations, final AllChannelOptions channelOptions) {
		return new Unit(id, configuration, urls, url, account, password, defaultAverageCount,
				measurementHysteresisFactor, defaultMeasurementsInterval_ms, defaultMeasurementsIntervalFast_ms,
				forceUpdateAfterFastChangingIntervals, forcedUpdateInterval_ms, doubleUpdateInterval_ms,
				bufferedInterval_ms, watchDogTime_ms, releaseBlockingAfterUserAccess_ms,
				releaseBlockingAfterServiceAccess_ms, reheatingNotRequiredActiveTime_ms, resetErrorDelayTime_ms,
				delayAfterSwitchingOnEnable, fwLth2_21_02A, features, ignoredFrameThicknesScreenSaver,
				ignoredChannels, assignments, csvUnit, durations, channelOptions);
	}

	public String getId() {
		return this.id;
	}

	public Configuration getConfiguration() {
		return this.configuration;
	}

	public Collection<String> getUrls() {
		return this.urls;
	}

	public String getUrl() {
		return this.url;
	}

	@Override
	public String getAccount() {
		return this.account;
	}

	public Features getFeatures() {
		return this.features;
	}

	public int getWatchDogTime_ms() {
		return this.watchDogTime_ms;
	}

	@Override
	public char[] cP() {
		return this.password.cP();
	}

	public int getDefaultAverageCount() {
		return this.defaultAverageCount;
	}

	public int getMeasurementHysteresisFactor() {
		return this.measurementHysteresisFactor;
	}

	public int getMeasurementsInterval_ms() {
		return this.defaultMeasurementsInterval_ms;
	}

	public int getMeasurementsIntervalFast_ms() {
		return this.defaultMeasurementsIntervalFast_ms;
	}

	public int getForcedUpdateInterval_ms() {
		return this.forcedUpdateInterval_ms;
	}

	public int getBufferedInterval_ms() {
		return this.bufferedInterval_ms;
	}

	public boolean isBuffered() {
		return this.bufferedInterval_ms > 0;
	}

	public boolean isDelayAfterSwitchingOnEnable() {
		return this.delayAfterSwitchingOnEnable;
	}

	public boolean isFwLth2_21_02A() {
		return this.fwLth2_21_02A;
	}

	public int getDoubleUpdateInterval_ms() {
		return this.doubleUpdateInterval_ms;
	}

	public int getIgnoredFrameThicknesScreenSaver() {
		return this.ignoredFrameThicknesScreenSaver;
	}

	public int getReleaseBlockingAfterUserAccess_ms() {
		return this.releaseBlockingAfterUserAccess_ms;
	}

	public int getReleaseBlockingAfterServiceAccess_ms() {
		return this.releaseBlockingAfterServiceAccess_ms;
	}

	public int getReheatingNotRequiredActiveTime_ms() {
		return this.reheatingNotRequiredActiveTime_ms;
	}

	public boolean isChannelIgnored(final String channelId) {
		for (Pattern regEx : this.ignoredChannels) {
			if (regEx.matcher(channelId).matches()) {
				return true;
			}
		}
		return false;
	}

	public int getForceUpdateAfterFastChangingIntervals() {
		return this.forceUpdateAfterFastChangingIntervals;
	}

	public ChannelAssignment getChannelAssignment(final String id) {
		if (this.assignments == null) {
			return null;
		} else {
			return this.assignments.get(id);
		}
	}

	public boolean isCsvUnit() {
		return this.csvUnit;
	}

	public boolean isAdmin() {
		return this.admin;
	}

	public Long getForcedConfigMask() {
		return this.forcedConfigMask;
	}

	public void setForcedConfigMask(final Long forcedConfigMask) {
		this.forcedConfigMask = forcedConfigMask;
	}

	public String getComment() {
		return this.configuration.getComment();
	}

	public Duration getDuration(final String id) {
		if (this.durations == null) {
			return null;
		} else {
			return this.durations.get(id);
		}
	}

	public AllChannelOptions getChannelOptions() {
		return this.channelOptions;
	}

	public boolean isMailEnabled() {
		return this.features.isSendMailOnError();
	}

	public int getResetErrorDelayTime() {
		return this.resetErrorDelayTime_ms;
	}

}