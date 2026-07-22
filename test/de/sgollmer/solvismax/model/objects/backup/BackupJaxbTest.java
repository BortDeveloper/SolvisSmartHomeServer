package de.sgollmer.solvismax.model.objects.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.sgollmer.solvismax.helper.Helper.Reference;
import de.sgollmer.solvismax.model.objects.data.BooleanValue;
import de.sgollmer.solvismax.model.objects.data.IntegerValue;
import de.sgollmer.solvismax.model.objects.data.ModeValue;
import de.sgollmer.solvismax.model.objects.data.StringData;
import de.sgollmer.solvismax.xml.jaxb.backup.BackupDto;
import de.sgollmer.solvismax.xml.jaxb.backup.JaxbBackupReader;

/**
 * Charakterisierung des JAXB-Pfads der {@code measurements.xml}
 * (MODERNISIERUNG.md 3.3 — zweiter umgestellter Baum, inkl. Schreiben):
 * beide Wurzel-Generationen (legacy {@code SolvisMeasurements} mit Namespace,
 * aktuell {@code SolvisBackup} ohne) müssen dieselben Werte liefern; der
 * Schreib-/Wieder-Lese-Roundtrip muss verlustfrei sein und die aktuelle
 * Wurzelform erzeugen. Der Test liegt im backup-Paket (paketprivater Zugriff
 * auf {@code BackupMapper}/{@code getSystemBackups}).
 */
class BackupJaxbTest {

	private static final String LEGACY = "testFiles/xml/measurements-legacy.xml";
	private static final String AKTUELL = "testFiles/xml/measurements-backup.xml";

	private static AllSystemBackups lese(final String datei) throws Exception {
		Assumptions.assumeTrue(new File(datei).isFile(), "Fixture fehlt: " + datei);
		final AllSystemBackups backups = new AllSystemBackups(new Reference<>(-1L));
		BackupMapper.merge(JaxbBackupReader.read(new File(datei)), backups);
		return backups;
	}

	private static Map<String, Object> werte(final AllSystemBackups backups, final String systemId) {
		final Map<String, Object> werte = new LinkedHashMap<>();
		for (final SystemBackup system : backups.getSystemBackups()) {
			if (systemId.equals(system.getId())) {
				for (final SystemBackup.IValue value : system.getValues()) {
					werte.put(value.getId(), ((Measurement) value).getData());
				}
			}
		}
		return werte;
	}

	private static void pruefeInhalt(final AllSystemBackups backups) {
		final Map<String, Object> werte = werte(backups, "mySolvis");
		assertEquals(4, werte.size());
		assertEquals(true, ((BooleanValue) werte.get("A1")).get());
		assertEquals(42, ((IntegerValue) werte.get("A2")).get());
		assertEquals("abc", ((StringData) werte.get("A3")).get());
		assertEquals("Auto", ((ModeValue<?>) werte.get("A4")).get().getName());
	}

	@Test
	void legacyFormGelesen() throws Exception {
		pruefeInhalt(lese(LEGACY));
	}

	@Test
	void aktuelleFormGelesen() throws Exception {
		pruefeInhalt(lese(AKTUELL));
	}

	/** Schreiben + Wieder-Lesen: verlustfrei und in der aktuellen Wurzelform. */
	@Test
	void roundTripVerlustfrei(@TempDir final File tempDir) throws Exception {
		final AllSystemBackups gelesen = lese(LEGACY);

		final File ausgabe = new File(tempDir, "measurements.xml");
		JaxbBackupReader.write(BackupMapper.toDto(gelesen), ausgabe);

		final String inhalt = Files.readString(ausgabe.toPath());
		assertTrue(inhalt.contains("<SolvisBackup>"), "aktuelle Wurzelform erwartet");
		assertTrue(inhalt.contains("<SystemBackup id=\"mySolvis\">"), "aktuelle Kindform erwartet");

		final AllSystemBackups wiederGelesen = new AllSystemBackups(new Reference<>(-1L));
		BackupMapper.merge(JaxbBackupReader.read(ausgabe), wiederGelesen);
		pruefeInhalt(wiederGelesen);
	}

	/** Integrationspfad: {@code BackupHandler} liest die Datei beim Konstruieren. */
	@Test
	void backupHandlerLiestDatei(@TempDir final File tempDir) throws Exception {
		Assumptions.assumeTrue(new File(AKTUELL).isFile(), "Fixture fehlt: " + AKTUELL);
		final File dataDir = new File(tempDir, "SolvisServerData");
		assertTrue(dataDir.mkdirs());
		Files.copy(new File(AKTUELL).toPath(), new File(dataDir, "measurements.xml").toPath());

		final BackupHandler handler = new BackupHandler(tempDir, Integer.MAX_VALUE);
		final SystemBackup system = handler.getSystemBackup("mySolvis");
		assertNotNull(system);
		assertEquals(4, system.getValues().size());
	}
}
