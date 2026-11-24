SHELL = /bin/sh

baseDir = /opt


# SolvisSmartHomeServer settings

accountSolvis = solvis
# Fork: FHS-Ablage (2026-11-24, dienst-ablagestruktur.md): Programm root-eigen
# unter /usr/local/lib, Konfiguration unter /etc, Zustand unter /var/lib,
# Health-Token unter /run (RuntimeDirectory der Unit). Frueher alles unter
# /opt/solvis. Der Server sucht base.xml neben dem Jar; dort liegt ein Link auf
# die Datei in /etc. Bewusst Link statt --base-xml: Restart.java startet den
# Server ohne Programmargumente neu und faende die Datei sonst nicht.
serverDir = /usr/local/lib/$(accountSolvis)
confDir = /etc/$(accountSolvis)
stateDir = /var/lib/$(accountSolvis)
solvisJar = SolvisSmartHomeServer.jar
baseXml = base
writablePath = $(stateDir)

systemD = /etc/systemd/system
service = SolvisSmartHomeServer
serviceDebug = DebugSolvisSmartHomeServer
serviceSources = $(service).service $(serviceDebug).service
# Fork: Debug-Unit (JDWP) nur noch mit installDebugService/updateDebug
serviceFiles = $(systemD)/$(service).service
serviceDebugFile = $(systemD)/$(serviceDebug).service

javaPath = /usr/bin/java
# Netze, die der gehaertete Dienst erreichen darf (IPAddressAllow in
# $(service).service). Vorgabe: alle privaten Bereiche; enger mit
# "make solvisAllowedNet=192.168.x.0/24 ...". Mehrere Netze durch
# Leerzeichen getrennt.
solvisAllowedNet = 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16
# Fork: Mindest-Java-Major (Bytecode major 61 = Java 17); Guard: Target checkJava.
javaVersionMin = 17
# Fork: Quelle der Build-/Vorlagen-Artefakte (Maven-Build im Repo-Root).
repoRoot = ../..
rscData = $(repoRoot)/rsc/de/sgollmer/solvismax/data
# Fork: JDWP nur auf Loopback (nie 0.0.0.0 - der Debug-Port waere sonst im
# LAN offen). Remote-Debugging per SSH-Portforward:
#   ssh -L 10737:127.0.0.1:10737 <host>
javaDebugPort = 127.0.0.1:10737
javaAgentlib = jdwp=transport=dt_socket,suspend=n,server=y,address=$(javaDebugPort)

# Fork: Geraetezwang SolvisRemote - die Firmware kann Digest-Auth nur mit
# MD5, JDK 14+ verweigert das per Default ("Rejecting digest authentication
# with insecure algorithm: MD5" -> 401; Messbefund ransible 2026-08-13,
# Java 21). Gilt fuer alle Starts, die mit der Anlage sprechen (learn,
# foreground, debugServer, ...); wirkt nur auf den jeweiligen Prozess.
javaDeviceOpts = -Dhttp.auth.digest.reEnabledAlgorithms=MD5

solvisJarPath = $(serverDir)/$(solvisJar)
start = sudo -u $(accountSolvis) $(javaPath) $(javaDeviceOpts) -jar $(solvisJarPath)
startDebug = sudo -u $(accountSolvis) $(javaPath) $(javaDeviceOpts) -agentlib:$(javaAgentlib) -jar  $(solvisJarPath)
startLocal = sudo -u $(accountSolvis) $(javaPath) -jar  $(solvisJar)

generatedBaseDir = SolvisServerData
generatedFiles = control.xml* control.xsd graficData.xml graficData.xsd  measurements.xml   measurements.xsd solvis-error.log* solvis.log* solvis-tiny.log* tinylog.properties log4j2.xml
generatedDirs = SolvisErrorImages LearnedImages


# FHEM settings

accountFhem = fhem
groupFhem = dialout
perl = /usr/bin/perl

fhemModul = 73_SolvisClient.pm
fhemDir = $(baseDir)/$(accountFhem)
fhemModulesDir = $(fhemDir)/FHEM
packageFhemDir = FHEM
fhemCommand = ./contrib/commandref_join.pl -noWarnings
fhemCommandRef = $(fhemDir)/docs/commandref.html
serviceFhem = fhem.service
fhemRestart = 0



.SUFFIXES:



# Fork: Bruecke Maven-Build -> Install. Der Fork-Build legt das Jar unter
# target/ ab (nicht hier), und die base.xsd ist eine Jar-Ressource unter
# rsc/de/sgollmer/solvismax/data/ (zur Laufzeit liest der Server sie aus
# dem Klassenpfad; die
# installierte Kopie ist nur Referenz/Altbestand). "make prepare" stellt die
# vom Install erwarteten Dateien in diesem Verzeichnis bereit. Eine bereits
# vorhandene (vom Operator editierte) base.xml wird NICHT ueberschrieben.
prepare:
	@test -f $(repoRoot)/target/$(solvisJar) || { \
		echo "FEHLER: $(repoRoot)/target/$(solvisJar) fehlt." ; \
		echo "Erst frisch bauen: (cd $(repoRoot) && ./mvnw -B clean package)" ; \
		exit 1 ; \
	}
	cp $(repoRoot)/target/$(solvisJar) ./$(solvisJar)
	cp $(rscData)/$(baseXml).xsd ./$(baseXml).xsd
	@if [ -f $(baseXml).xml ] ; then \
		echo "$(baseXml).xml existiert bereits - nicht ueberschrieben." ; \
	else \
		cp $(rscData)/$(baseXml).xml ./$(baseXml).xml ; \
		echo "$(baseXml).xml aus der Vorlage erzeugt - Werte anpassen (INBETRIEBNAHME.md Phase 2)." ; \
	fi



# Fork: Versions-Guard - der Fork braucht Java >= $(javaVersionMin)
# (Bytecode major 61). Bricht ab, bevor ein zu altes Java installiert wird.
checkJava:
	@v=`$(javaPath) -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1` ; \
	if [ -z "$$v" ] || [ "$$v" -lt $(javaVersionMin) ] ; then \
		echo "FEHLER: $(javaPath) ist kein Java >= $(javaVersionMin) (gefunden: $${v:-nichts})." ; \
		echo "Mit make javaPath=/pfad/zu/java-17+ <ziel> wiederholen." ; \
		exit 1 ; \
	fi



eraseGenerated:
	set -x ; \
	cd "$(writablePath)/$(generatedBaseDir)" ; \
	for file in $(generatedFiles) ; do \
		rm $$file ; \
	done ; \
	for dir in $(generatedDirs) ; do \
		rm -r $$dir ; \
	done ; \
	if [ -z "$$(ls -A .)" ]; then \
		cd - ; \
		rm -r "$(writablePath)/$(generatedBaseDir)" ; \
	fi ; \
	cnt=`find $(stateDir) -type f | wc -l` ; \
	if [ $$cnt -eq 0 ]; then \
		userdel -r $(accountSolvis) ; \
	fi



install: installSolvis installFHEM



# Fork: Konto und Zustandsverzeichnis. Nur der Zustand gehoert dem Dienstkonto
# (Standard-Ableitung 1 und 4); Programm und Konfiguration gehoeren root.
$(stateDir): Makefile
	grep '^$(accountSolvis):' /etc/passwd >/dev/null || \
		useradd -r -M -d $(stateDir) -s /usr/sbin/nologin $(accountSolvis)
	mkdir -p $@/$(generatedBaseDir)
	chown -c $(accountSolvis):$(accountSolvis) $@ $@/$(generatedBaseDir)
	chmod 750 $@
	touch $@



$(confDir): Makefile $(stateDir)
	mkdir -p $@
	chown -c root:$(accountSolvis) $@
	chmod 750 $@
	touch $@



uninstall: uninstallSolvis uninstallFHEM




uninstallAll: uninstallSolvis eraseGenerated uninstallFHEM



copyService: $(serviceFiles) $(EnvironmentFilePath)



installService: installSolvis
	systemctl enable $(service)
	systemctl start $(service)



installDebugService: installSolvis $(serviceDebugFile)
	systemctl enable $(serviceDebug)
	systemctl start $(serviceDebug)



stopServices:
	-systemctl stop $(service)
	-systemctl stop $(serviceDebug)



uninstallService: stopServices
	systemctl disable $(service)



uninstallDebugService: stopServices
	systemctl disable $(serviceDebug)



installFHEM: $(fhemCommandRef) 



uninstallFHEM: rmFHEMModul createCommandRef	



rmFHEMModul:
	if [ -d $(fhemModulesDir) ] ; then \
		rm -f $(fhemModulesDir)/$(fhemModul) ; \
	fi



uninstallSolvis: uninstallService uninstallDebugService
	rm $(serverDir)/$(solvisJar)
	rm $(systemD)/$(service).service
	rm -f $(serviceDebugFile)
	rm $(serverDir)/$(baseXml).xml
	rm $(confDir)/$(baseXml).xml
	rm -f $(confDir)/$(baseXml).xml.old
	rm $(serverDir)/$(baseXml).xsd
	


installSolvis: checkJava $(stateDir) $(serverDir)/$(solvisJar) $(confDir)/$(baseXml).xml $(serverDir)/$(baseXml).xml $(serverDir)/$(baseXml).xsd copyService enforceOwnership
	@echo "Empfohlen: 'make checkCredentials' vor dem ersten Start/Lernphase (Credential-Pruefung, F-120)."



# Fork (Welle-1-Auflage MEDIUM, 2026-08-22): Bestands-Migration der
# Dateirechte. Die dateiabhaengigen Install-Regeln unten setzen Owner und
# Modus nur, wenn die Quelldatei neuer ist als das Ziel — eine unveraendert
# installierte base.xml aus der Zeit vor S-5 bliebe sonst dauerhaft auf dem
# alten Rechtestand ("up to date", Abnahme-Repro 2026-08-22), ausgerechnet
# fuer die einzige geheimnistragende Datei. Dieses Phony-Ziel setzt deshalb
# bei JEDEM installSolvis-/updateSolvis-Lauf das S-5-Soll unbedingt durch.
# chown/chmod aendern nur Metadaten (weder Inhalt noch mtime) — es wird
# nichts neu installiert und keine Regel-Kaskade angestossen; -c meldet
# nur tatsaechliche Aenderungen (idempotent still im Normalfall).
# Als letzte Voraussetzung von installSolvis gelistet, damit die
# Durchsetzung nach den Install-Regeln laeuft (make arbeitet hier seriell).
.PHONY: enforceOwnership
enforceOwnership:
	chown -c root:root $(serverDir)
	chmod -c 755 $(serverDir)
	chown -c root:$(accountSolvis) $(confDir)
	chmod -c 750 $(confDir)
	chown -c $(accountSolvis):$(accountSolvis) $(stateDir)
	chmod -c 750 $(stateDir)
	chown -c root:root $(serverDir)/$(solvisJar) $(serverDir)/$(baseXml).xsd
	chmod -c 644 $(serverDir)/$(solvisJar) $(serverDir)/$(baseXml).xsd
	chown -c root:$(accountSolvis) $(confDir)/$(baseXml).xml
	chmod -c 640 $(confDir)/$(baseXml).xml
	if [ -f $(confDir)/$(baseXml).xml.old ] ; then \
		chown -c root:root $(confDir)/$(baseXml).xml.old ; \
		chmod -c 600 $(confDir)/$(baseXml).xml.old ; \
	fi



# Fork: interaktive Credential-Pruefung (F-120) — prueft Solvis-Web-Login,
# MQTT-Login und die passwordCrypt-Werte der base.xml VOR Lernphase/GO-Live.
# Bewusst KEINE harte Abhaengigkeit von installSolvis (interaktiv, fragt
# Passwoerter ab); kein sudo noetig, Skript ist read-only.
checkCredentials:
	@./check-credentials.sh



update: stopServices install
	systemctl start $(service)



updateSolvis: stopServices installSolvis
	systemctl start $(service)



updateDebug: stopServices installSolvis $(serviceDebugFile)
	systemctl start $(serviceDebug)


learn: stopServices installSolvis
	$(start) --server-learn
	systemctl start $(service)



testmail:
	$(startLocal) --test-mail



# Fork (S-5, 2026-08-22): Passwort unsichtbar erfassen (read -rs; dafuer
# bash statt sh, target-lokal) — der Klartext landet so weder sichtbar auf
# dem Terminal noch als Argument in der Shell-Historie. Rest-Risiko wie in
# check-credentials.sh dokumentiert: der Wert steht kurzzeitig im argv des
# java-Kindprozesses (kein stdin-Modus im Server; auf dem
# Einzelnutzer-Zielhost akzeptiert, F-120).
crypt: SHELL = /bin/bash
crypt:
	@read -rs -p "Zu verschluesselndes Passwort (Eingabe unsichtbar): " password; \
	echo ; \
	$(startLocal) --string-to-crypt="$$password"



learnDebug: stopServices installSolvis
	$(startDebug) --server-learn
	systemctl start $(serviceDebug)



documentation:
	$(start) --documentation --csvSemicolon



iobroker:
	$(start) --iobroker



debugServer: stopServices installSolvis
	$(startDebug)



terminate: stopServices installSolvis
	$(start) --server-terminate



foreground: stopServices installSolvis
	sudo -u $(accountSolvis) $(javaPath) $(javaDeviceOpts) -jar $(serverDir)/$(solvisJar)



# Fork (S-5): Programmverzeichnis root-eigen (Standard-Ableitung 1).
$(serverDir): $(stateDir)
	mkdir -p $@
	chown -c root:root $@
	chmod 755 $@



$(fhemCommandRef):$(fhemModulesDir)/$(fhemModul)
	make createCommandRef



$(fhemModulesDir)/$(fhemModul): $(packageFhemDir)/$(fhemModul)
	@set -x ; \
	if [ -d $(fhemModulesDir) ] ; then \
		cp $< $@ ; \
		chown -c $(accountFhem):$(groupFhem) $@ ; \
	fi



createCommandRef:
	@set -x ; \
	if [ -d $(fhemDir) ] ; then \
		cd $(fhemDir); sudo -u $(accountFhem) $(perl) $(fhemCommand) ; \
		echo ; \
		echo "************************************" ; \
		echo "**                                **" ; \
		echo "**     Bitte FHEM neu starten     **" ; \
		echo "**                                **" ; \
		echo "** z.B. mittels                   **" ; \
		echo "**   sudo systemctl restart fhem  **" ; \
		echo "**                                **" ; \
		echo "************************************" ; \
	fi



# Fork (S-5): Jar root-eigen, Dienstkonto nur Leserecht (Standard-Ableitung 1:
# ein kompromittierter Dienst darf sein Binary nicht austauschen koennen).
$(serverDir)/$(solvisJar): $(solvisJar) | $(serverDir)
	cp $< $@
	chown -c root:root $@
	chmod 644 $@



# Fork (S-5, 2026-08-22): Der Vorgaenger-Stand verbleibt als
# $(baseXml).xml.old NEBEN der installierten Datei (root:root, 0600) statt
# im Operator-Build-Verzeichnis. Begruendung: Der Rollback-Wert (Diff gegen
# den letzten funktionierenden Stand) bleibt erhalten, aber das Geheimnis
# verlaesst das Zielverzeichnis nicht mehr — die bisherige Ablage im
# Build-Verzeichnis unterlief die Gap-4-Entruempelung des Operator-Homes
# (F-145) nach jeder Bereinigung aufs Neue. root:root statt Dienstkonto,
# weil der Dienst die .old nie lesen muss (reines Operator-Artefakt).
# Die installierte base.xml selbst: root:solvis 640 — der Dienst liest
# seine Konfiguration, darf sie aber nicht umschreiben (Broker/URL/
# Credentials waeren sonst ein Eskalationspfad).
$(confDir)/$(baseXml).xml : $(baseXml).xml | $(confDir)
	if [ -f "$@" ] ; then \
		mv $@ $@.old ; \
		chown -c root:root $@.old ; \
		chmod 600 $@.old ; \
	fi
	cp $< $@
	chown -c root:$(accountSolvis) $@
	chmod 640 $@



# Fork: Link neben dem Jar, siehe confDir oben.
$(serverDir)/$(baseXml).xml : $(confDir)/$(baseXml).xml | $(serverDir)
	ln -sfn $(confDir)/$(baseXml).xml $@



$(serverDir)/$(baseXml).xsd : $(baseXml).xsd | $(serverDir)
	cp $< $@
	chown -c root:root $@
	chmod 644 $@



$(serviceFiles) $(serviceDebugFile) : $(serviceSources) Makefile
	sed \
		-e 's/\$$(accountSolvis)/$(accountSolvis)/' \
		-e 's/\$$(solvisJarPath)/$(subst /,\/,$(solvisJarPath))/' \
		-e 's/\$$(javaPath)/$(subst /,\/,$(javaPath))/' \
		-e 's/\$$(javaAgentlib)/$(javaAgentlib)/' \
		-e 's/\$$(stateDir)/$(subst /,\/,$(stateDir))/g' \
		-e 's/\$$(serverDir)/$(subst /,\/,$(serverDir))/g' \
		-e 's/\$$(confDir)/$(subst /,\/,$(confDir))/g' \
		-e 's/\$$(solvisAllowedNet)/$(subst /,\/,$(solvisAllowedNet))/' \
	$(@F) > $@
	systemctl daemon-reload
