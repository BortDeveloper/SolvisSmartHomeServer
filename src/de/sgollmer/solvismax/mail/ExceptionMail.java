package de.sgollmer.solvismax.mail;

import java.io.IOException;
import java.util.Collection;

import jakarta.mail.MessagingException;
import javax.xml.namespace.QName;

import de.sgollmer.solvismax.Constants;
import de.sgollmer.solvismax.crypt.CryptAes;
import de.sgollmer.solvismax.error.CryptException;
import de.sgollmer.solvismax.error.ObserverException;
import de.sgollmer.solvismax.imagepatternrecognition.image.MyImage;
import de.sgollmer.solvismax.log.Diagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.sgollmer.solvismax.mail.Mail.Recipient;
import de.sgollmer.solvismax.mail.Mail.Security;
import de.sgollmer.solvismax.model.objects.ErrorState;
import de.sgollmer.solvismax.model.objects.Observer.IObserver;
import de.sgollmer.solvismax.model.objects.unit.Unit;
import de.sgollmer.solvismax.model.objects.unit.Units;
import de.sgollmer.xmllibrary.ArrayXml;
import de.sgollmer.xmllibrary.BaseCreator;
import de.sgollmer.xmllibrary.CreatorByXML;

public class ExceptionMail implements IObserver<ErrorState.Info> {

	private static final Logger logger = LoggerFactory.getLogger(ExceptionMail.class);

	private static final String XML_RECIPIENT = "Recipient";
	private static final String XML_RECIPIENTS = "Recipients";
	private static final String XMl_PROXY = "Proxy";

	private final String name;
	private final String from;
	private final CryptAes password;
	private final Security securityType;
	private final String provider;
	private final int port;
	private final Collection<Recipient> recipients;
	private final Proxy proxy;

	private ExceptionMail(final String name, final String from, final CryptAes password, final Security securityType,
			final String provider, final int port, final Collection<Recipient> recipients, final Proxy proxy) {
		this.name = name;
		this.from = from;
		this.password = password;
		this.securityType = securityType;
		this.provider = provider;
		this.port = port;
		this.recipients = recipients;
		this.proxy = proxy;
	}

	/**
	 * DTO-neutrale Empfänger-Daten für die Factory {@link #of} — hält die
	 * paketprivaten Mail-Typen ({@code Recipient}, {@code Security}) weiterhin
	 * gekapselt.
	 */
	public record RecipientData(String name, String address, jakarta.mail.Message.RecipientType type) {
	}

	/**
	 * Öffentliche Konstruktions-Factory für den JAXB-Mapper (MODERNISIERUNG.md
	 * 3.3, Weg B). {@code securityType} wird — wie beim alten Creator — über
	 * {@code Security.valueOf(toUpperCase())} aufgelöst; ein unbekannter Wert
	 * ist ein {@link Error} (identisches Verhalten).
	 */
	public static ExceptionMail of(final String name, final String from, final CryptAes password,
			final String securityType, final String provider, final int port,
			final Collection<RecipientData> recipients, final Proxy proxy) {
		Security security = null;
		if (securityType != null) {
			try {
				security = Security.valueOf(Security.class, securityType.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new Error("Security type error", e);
			}
		}
		Collection<Recipient> recipientList = null;
		if (recipients != null) {
			recipientList = new java.util.ArrayList<>();
			for (final RecipientData recipient : recipients) {
				recipientList.add(Mail.recipientOf(recipient.name(), recipient.address(), recipient.type()));
			}
		}
		return new ExceptionMail(name, from, password, security, provider, port, recipientList, proxy);
	}

	public static class Creator extends CreatorByXML<ExceptionMail> {

		private String name;
		private String from;
		private CryptAes password = new CryptAes();
		private Security securityType;
		private String provider;
		private int port;
		private Collection<Recipient> recipients;
		private Proxy proxy = null;

		public Creator(final String id, final BaseCreator<?> creator) {
			super(id, creator);
		}

		@Override
		public void setAttribute(final QName name, final String value) {
			try {
				switch (name.getLocalPart()) {
					case "name":
						this.name = value;
						break;
					case "from":
						this.from = value;
						break;
					case "passwordCrypt":
						this.password.decrypt(value);
						break;
					case "securityType":
						try {
							this.securityType = Security.valueOf(Security.class, value.toUpperCase());
						} catch (IllegalArgumentException e) {
							throw new Error("Security type error", e);
						}
						break;
					case "provider":
						this.provider = value;
						break;
					case "port":
						this.port = Integer.parseInt(value);
						break;
				}
			} catch (CryptException e) {
			}
		}

		@Override
		public ExceptionMail create() throws IOException {
			return new ExceptionMail(this.name, this.from, this.password, this.securityType, this.provider, this.port,
					this.recipients, this.proxy);
		}

		@Override
		public CreatorByXML<?> getCreator(final QName name) {
			String id = name.getLocalPart();
			switch (id) {
				case XML_RECIPIENTS:
					return new ArrayXml.Creator<Recipient, Recipient>(id, this.getBaseCreator(), new Recipient(),
							XML_RECIPIENT);
				case XMl_PROXY:
					return new Proxy.Creator(id, this.getBaseCreator());
			}
			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public void created(final CreatorByXML<?> creator, final Object created) {
			switch (creator.getId()) {
				case XML_RECIPIENTS:
					this.recipients = ((ArrayXml<Recipient, Recipient>) created).getArray();
					break;
				case XMl_PROXY:
					this.proxy = (Proxy) created;
					break;
			}
		}
	}

	public void send(final String subject, final String text, final MyImage image)
			throws MessagingException, IOException {
		Mail.send(subject, text, this.name, this.from, this.password, this.securityType, this.provider, this.port,
				this.recipients, image, this.proxy);
	}

//	public void send(SolvisErrorInfo info) throws MessagingException, IOException {
//		this.send(info.getMessage(), "", info.getImage());
//	}
//
	@Override
	public void update(final ErrorState.Info info, final Object source) {
		if ( info == null) {
			return;
		}
		try {
			this.send(info.getMessage(), "", info.getImage());
		} catch (MessagingException | IOException e) {
			logger.error("Mail <" + info.getMessage() + "> couldn't be sent: ", e);
			throw new ObserverException();
		}

	}

	public CryptException getException() {
		return this.password.getException();
	}

	public int sendTestMail(final Units units) {
		try {
			this.send("Test mail", "This is a test mail", null);

			for (Unit unit : units.getUnits()) {
				unit.getFeatures().checkMail(unit.getId());
			}

			return Constants.ExitCodes.OK;
		} catch (Throwable e) {
			logger.error("Mailing error", e);
			return Constants.ExitCodes.MAILING_ERROR;
		}

	}
}
