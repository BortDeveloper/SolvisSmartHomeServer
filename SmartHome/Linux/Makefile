SHELL = /bin/sh

baseDir = /opt


# SolvisSmartHomeServer settings

accountSolvis = solvis
solvisDir = $(baseDir)/$(accountSolvis)
serverDir = $(solvisDir)/SolvisSmartHomeServer
solvisJar = SolvisSmartHomeServer.jar
baseXml = base
writablePath = `sed -n 's/.*\?writablePathLinux="\([^"]*\)".*/\1/gp' base.xml`

systemD = /etc/systemd/system
service = SolvisSmartHomeServer
serviceDebug = DebugSolvisSmartHomeServer
serviceSources = $(service).service $(serviceDebug).service
serviceFiles = $(systemD)/$(service).service $(systemD)/$(serviceDebug).service

javaPath = /usr/bin/java
# Fork: Mindest-Java-Major (Bytecode major 61 = Java 17); Guard: Target checkJava.
javaVersionMin = 17
# Fork: Quelle der Build-/Vorlagen-Artefakte (Maven-Build im Repo-Root).
repoRoot = ../..
rscData = $(repoRoot)/rsc/de/sgollmer/solvismax/data
javaDebugPort = 0.0.0.0:10737
javaAgentlib = jdwp=transport=dt_socket,suspend=n,server=y,address=$(javaDebugPort)

solvisJarPath = $(serverDir)/$(solvisJar)
start = sudo -u $(accountSolvis) $(javaPath) -jar $(solvisJarPath)
startDebug = sudo -u $(accountSolvis) $(javaPath) -agentlib:$(javaAgentlib) -jar  $(solvisJarPath)
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
	cnt=`find $(solvisDir) -type f | wc -l` ; \
	if [ $$cnt -eq 0 ]; then \
		userdel -r $(accountSolvis) ; \
	fi



install: installSolvis installFHEM



$(solvisDir): Makefile
	grep $(accountSolvis) /etc/passwd >/dev/null ; \
	if [ $$? -ne 0 ] ; then \
		set -x ; \
		useradd  -r -b $(baseDir) -s /bin/false $(accountSolvis) ; \
	fi
	mkdir -p $@ ; \
	chown -c $(accountSolvis) $@ ; \



uninstall: uninstallSolvis uninstallFHEM




uninstallAll: uninstallSolvis eraseGenerated uninstallFHEM



copyService: $(serviceFiles) $(EnvironmentFilePath)



installService: installSolvis
	systemctl enable $(service)
	systemctl start $(service)



installDebugService: installSolvis
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
	rm $(systemD)/$(serviceDebug).service
	rm $(serverDir)/$(baseXml).xml
	rm $(serverDir)/$(baseXml).xsd
	


installSolvis: checkJava $(serverDir)/$(solvisJar) $(serverDir)/$(baseXml).xml  $(serverDir)/$(baseXml).xsd copyService
	@echo "Empfohlen: 'make checkCredentials' vor dem ersten Start/Lernphase (Credential-Pruefung, F-120)."



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



updateDebug: stopServices installSolvis
	systemctl start $(serviceDebug)


learn: stopServices installSolvis
	$(start) --server-learn
	systemctl start $(service)



testmail:
	$(startLocal) --test-mail



crypt:
	@read -p "Enter the word to be encrypted:" password; \
	$(startLocal) --string-to-crypt=$$password



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
	sudo -u $(accountSolvis) $(javaPath) -jar $(serverDir)/$(solvisJar)



$(serverDir): $(solvisDir)
	mkdir -p $@
	chown -c $(accountSolvis) $@
		


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



$(serverDir)/$(solvisJar): $(solvisJar) $(serverDir)
	cp $< $@
	chown -c $(accountSolvis) $@



$(serverDir)/$(baseXml).xml : $(baseXml).xml
	if [ -f "$@" ] ; then \
		mv $@ $<.old ; \
		chown -c `ls -l $< | awk '{ print $$3":"$$4 }'` $<.old ; \
	fi 
	cp $< $@
	chown -c $(accountSolvis) $@
	chmod 600 $@



$(serverDir)/$(baseXml).xsd : $(baseXml).xsd
	cp $< $@
	chown -c $(accountSolvis) $@



$(serviceFiles) : $(serviceSources) Makefile
	sed \
		-e 's/\$$(accountSolvis)/$(accountSolvis)/' \
		-e 's/\$$(solvisJarPath)/$(subst /,\/,$(solvisJarPath))/' \
		-e 's/\$$(javaPath)/$(subst /,\/,$(javaPath))/' \
		-e 's/\$$(javaAgentlib)/$(javaAgentlib)/' \
	$(@F) > $@
	systemctl daemon-reload
