package de.sgollmer.solvismax.mail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import de.sgollmer.solvismax.Constants;
import de.sgollmer.solvismax.Constants.Debug;
import de.sgollmer.solvismax.crypt.CryptAes;
import de.sgollmer.solvismax.imagepatternrecognition.image.MyImage;
import de.sgollmer.solvismax.log.Diagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mail {

	private static final Logger logger = LoggerFactory.getLogger(Mail.class);

	enum Security {
		TLS, SSL, NONE
	};

	/** Paketinterne Konstruktions-Factory für {@code ExceptionMail.of} (JAXB-Weg). */
	static Recipient recipientOf(final String name, final String address, final RecipientType type) {
		return new Recipient(name, address, type);
	}

	static class Recipient {
		private final String name;
		private final String address;
		private final RecipientType type;

		private Recipient(final String name, final String address, final RecipientType type) {
			this.name = name;
			this.address = address;
			this.type = type;
		}
	}

	static void send(final String subject, final String text, final String name, final String from,
			final CryptAes password, final Security security, final String provider, final int port,
			final Collection<Recipient> recipients, final MyImage image, final Proxy proxy)
			throws MessagingException, IOException {

		String portString = Integer.toString(port);

		logger.info(security.name() + "Email Start");
		Properties props = new Properties();
		props.put("mail.smtp.host", provider); // SMTP Host
		if (proxy != null) {
			props.put("mail.smtp.proxy.host", proxy.getHost());
			props.put("mail.smtp.proxy.port", Integer.toString(proxy.getPort()));
			if (proxy.getUser() != null) {
				props.put("mail.smtp.proxy.user", proxy.getUser());
			}
			if (proxy.getPassword() != null) {
				props.put("mail.smtp.proxy.password", new String(proxy.getPassword().cP()));
			}
		}
		switch (security) {
			// Fork-Korrektur (MODERNISIERUNG.md 4.1): Frueher stand hier
			// mail.smtp.ssl.trust="*" - damit wurde JEDEM Server-Zertifikat
			// vertraut (anfaellig fuer Man-in-the-Middle). Jetzt gilt die
			// JSSE-Standardpruefung gegen die System-Truststore-CAs; private
			// SMTP-Server mit Eigenzertifikat muessen ihr Zertifikat in den
			// Java-Truststore importieren (siehe CHANGELOG-fork.md).
			case SSL:
				props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory"); // SSL Factory Class
				props.put("mail.smtp.socketFactory.port", portString); // SSL Port
				break;
			case TLS:
				props.put("mail.smtp.starttls.enable", "true");
				break;
			case NONE:
				break;
			default:
				throw new MessagingException("Mail security type \"" + security.name() + "\" unknown.");
		}
		props.put("mail.smtp.auth", "true"); // Enabling SMTP Authentication
		props.put("mail.smtp.port", portString); // SMTP Port

		Authenticator auth = new Authenticator() {

			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				char[] p = password.cP();
				PasswordAuthentication pA = new PasswordAuthentication(from, new String(p));
				Arrays.fill(p, '\0');
				return pA;
			}
		};

		Session session = Session.getDefaultInstance(props, auth);

		System.out.println("Session created");
		MimeMessage message = new MimeMessage(session);
		message.setFrom(new InternetAddress(from, name));
		for (Recipient recipient : recipients) {
			message.addRecipient(recipient.type, new InternetAddress(recipient.address, recipient.name));
		}
		message.setSubject(subject);

		Multipart multipart = new MimeMultipart();

		MimeBodyPart messageBodyPart = new MimeBodyPart();
		messageBodyPart.setText(text);
		multipart.addBodyPart(messageBodyPart);

		if (image != null) {
			messageBodyPart = new MimeBodyPart();
			// Bytes aus dem Bildmodul in einen Mail-Anhang einpacken (die
			// Mail-Typen gehoeren hierher, nicht ins Bildmodul; Fork 3.1).
			ByteArrayDataSource bds = new ByteArrayDataSource(image.getImageBytes(),
					"image/" + Constants.Files.GRAFIC_SUFFIX);
			messageBodyPart.setDataHandler(new DataHandler(bds));
			messageBodyPart.setFileName(Constants.Files.SOLVIS_SCREEN);
			messageBodyPart.setHeader("Content-ID", "<image>");
			multipart.addBodyPart(messageBodyPart);
		}
		message.setContent(multipart);

		logger.info("Send email...");
		if (!Debug.NO_MAIL) {
			Transport.send(message);
		} else {
			logger.info("Text of mail: " + subject);
		}
		logger.info("Email was sent.");
	}

}
