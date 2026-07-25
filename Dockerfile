# ---------------------------------------------------------------------------
# SolvisSmartHomeServer — Container-Image (Fork)
#
# Zwei-Stufen-Build:
#   1. "build"   — JDK + Maven-Wrapper (./mvnw) kompilieren aus den Quellen das Uber-Jar.
#   2. Laufzeit  — schlankes JRE-Image, das nur das Jar ausfuehrt.
#
# Warum diese Struktur:
#   * Reine Java-Anwendung ohne native Abhaengigkeiten -> Bytecode ist
#     architekturunabhaengig; das Image laeuft auf amd64 und arm64
#     (eclipse-temurin liefert beide). Fuer 32-bit-Raspberry (armhf) siehe
#     docs/DOCKER.md (alternatives Basisimage).
#   * Die OCR-Bilderkennung nutzt java.awt.image/ImageIO. Diese liegen im
#     JDK-Modul "java.desktop", das im vollen temurin-JRE-Image enthalten ist.
#     ACHTUNG: Ein per jlink/distroless minimiertes Runtime OHNE java.desktop
#     wuerde zur Laufzeit brechen.
# ---------------------------------------------------------------------------

# ---- Stufe 1: Build ----
FROM eclipse-temurin:21-jdk AS build

WORKDIR /src
# Nur die fuer den Build noetigen Teile werden per .dockerignore einbezogen
# (src, rsc, pom.xml, Maven-Wrapper, vendored local-maven-repo). Der Rest
# (docu, Windows-Setup, Git) bleibt aussen vor und haelt den Kontext klein.
COPY . .

# Build ueber den Maven-Wrapper (kein vorinstalliertes Maven noetig; der Wrapper
# laedt die passende Maven-Version). Erzeugt target/SolvisSmartHomeServer.jar
# (Uber-Jar via maven-shade-plugin). -ntp = "no transfer progress" (ruhiges Log).
RUN ./mvnw -B -ntp clean package

# ---- Stufe 2: Laufzeit ----
FROM eclipse-temurin:21-jre

# Nicht als root laufen: dedizierter, unprivilegierter Systemnutzer mit fester
# UID (erleichtert Dateirechte auf gemounteten Volumes).
RUN useradd --system --uid 10001 --create-home --home-dir /home/solvis solvis

# Arbeitsverzeichnis = Ablageort des Jars. Die Anwendung sucht "base.xml"
# standardmaessig NEBEN dem Jar (FileHelper.getJarDir()); daher wird base.xml
# hier hinein gemountet (siehe docker-compose.yml). Alternativ per Argument
# --base-xml=/pfad/base.xml.
WORKDIR /opt/solvis
COPY --from=build /src/target/SolvisSmartHomeServer.jar ./SolvisSmartHomeServer.jar

# Persistente Laufzeitdaten (angelernte Screens "LearnedImages", generierte
# control.xml/Messwerte, Logs) landen unter /data. In base.xml muss
# writablePathLinux="/data" gesetzt sein. Als Volume deklariert, damit die
# einmal angelernten Bilddaten Neustarts ueberleben.
RUN mkdir -p /data && chown -R solvis:solvis /data /opt/solvis
VOLUME ["/data"]

USER solvis

# Headless erzwingen: Es gibt kein Display; die Bildverarbeitung arbeitet auf
# ueber HTTP geladenen Bildern, nicht auf einem Bildschirm. Defensive Absicherung
# gegen versehentliche AWT-Display-Zugriffe.
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"

# Health-Check (SR-1 / Auflage A-4): 'restart: unless-stopped' deckt nur einen
# Prozess-Crash, nicht den Silent-Failure des MQTT-Retry-Loops (Container bleibt
# "Up", waehrend kein solvis/#-Datum den Broker erreicht). Geprueft wird daher die
# FRISCHE des Ready-Tokens, das die Anwendung bei Connect und laufendem Publish
# schreibt (/data/health/ready via HealthToken) — Symptom statt PID (Google SRE
# Kap. 6; Liveness != Readiness != Funktion). Schwelle 300 s liegt ueber dem
# Touch-Intervall (30 s); die start-period deckt OCR-Lernphase/ersten Connect.
# stat/date stammen aus coreutils im temurin-JRE-Image; laeuft als USER solvis.
HEALTHCHECK --interval=60s --timeout=5s --start-period=180s --retries=3 \
    CMD [ -f /data/health/ready ] && [ "$(( $(date +%s) - $(stat -c %Y /data/health/ready) ))" -lt 300 ] || exit 1

# Hinweis: Fuer sauberes Herunterfahren (SIGTERM -> LWT/Disconnect) den Container
# mit "--init" (bzw. compose "init: true") starten, damit ein Init-Prozess (tini)
# die Signalweiterleitung uebernimmt. Das Jar selbst bleibt der Hauptprozess.
ENTRYPOINT ["java", "-jar", "/opt/solvis/SolvisSmartHomeServer.jar"]
