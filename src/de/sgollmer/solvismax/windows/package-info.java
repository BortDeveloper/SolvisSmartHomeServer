/**
 * Optionale, <b>Windows-spezifische</b> Hilfsfunktion — vom Kern getrennt.
 *
 * <p>
 * {@link de.sgollmer.solvismax.windows.Task} erzeugt eine XML-Datei für den
 * Windows-Task-Scheduler (Autostart des Servers), aufgerufen über die
 * CLI-Option {@code --create-task-xml}. Der Code nutzt ausschließlich
 * JDK-Bordmittel (StAX) und hat <b>keine</b> Windows-nativen Abhängigkeiten;
 * er ist auf anderen Plattformen schlicht ungenutzt.
 * </p>
 *
 * <p>
 * Bewusste Abgrenzung (Fork 3.4): Der container-/Linux-first-Betrieb nutzt
 * diese Funktion nicht (dort erfolgt der Autostart über systemd bzw. den
 * Container-Restart). Der Baustein bleibt für Windows-Nutzer erhalten, ist aber
 * klar in dieses Paket isoliert; der zugehörige Windows-Installer liegt unter
 * {@code SmartHome/Windows/} (InnoSetup, nicht Teil des Maven-Builds).
 * </p>
 */
package de.sgollmer.solvismax.windows;
