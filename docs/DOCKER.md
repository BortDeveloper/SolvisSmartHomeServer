# Betrieb als Docker-Container

Der SolvisSmartHomeServer lässt sich als Container betreiben — er ist eine reine
Java-Anwendung ohne native Abhängigkeiten und braucht keinen Bildschirm
(die OCR arbeitet auf über HTTP geladenen Bildern). Dieses Verzeichnis liefert
ein [`Dockerfile`](../Dockerfile) (Zwei-Stufen-Build), eine
[`docker-compose.yml`](../docker-compose.yml) und diese Anleitung.

> ℹ️ **Docker ist nicht der Produktivweg dieser Installation.** Seit dem
> Cutover am 2026-08-13 läuft der Fork **nativ als systemd-Dienst** auf dem
> headless RPi4 `ransible`; der Container-Weg bleibt als Alternative für
> Entwicklung und Test dokumentiert. Produktivbetrieb und Day-2-Aufgaben:
> [INBETRIEBNAHME.md](INBETRIEBNAHME.md) und
> [runbooks/README.md](runbooks/README.md).

## Warum es funktioniert (und die Fallstricke)

- **Architekturunabhängig:** Java-Bytecode; das Image läuft auf **amd64** und
  **arm64** (Raspberry Pi 4/5). Basis: `eclipse-temurin`.
- **`java.desktop` nötig:** Die Bilderkennung nutzt `java.awt.image`/`ImageIO`
  aus dem JDK-Modul `java.desktop`. Das volle `eclipse-temurin:21-jre`-Image
  enthält es. Ein per `jlink`/distroless minimiertes Runtime **ohne**
  `java.desktop` würde zur Laufzeit brechen.
- **Headless:** kein Display erforderlich; das Image setzt
  `-Djava.awt.headless=true`.
- **Persistenz:** Die OCR-**Lernphase** und laufende Daten (`LearnedImages`,
  `control.xml`, Logs) schreiben in `writablePathLinux`. Dieser Pfad muss auf
  ein **Volume** zeigen (`/data`), sonst geht die Lernphase bei jedem Neustart
  verloren.
- **Signale:** Für sauberes Herunterfahren (Last-Will/Disconnect) den Container
  mit `--init` bzw. `init: true` starten (tini als PID 1).

## armhf (32-bit-Raspberry)

`eclipse-temurin` deckt amd64/arm64 ab. Für **armhf** in beiden Stufen ein
Debian-Basisimage mit OpenJDK verwenden, z. B.:

```dockerfile
FROM debian:bookworm AS build
RUN apt-get update && apt-get install -y --no-install-recommends default-jdk-headless \
    && rm -rf /var/lib/apt/lists/*
# … ./mvnw -B clean package …  (der Maven-Wrapper laedt Maven selbst)

FROM debian:bookworm
RUN apt-get update && apt-get install -y --no-install-recommends default-jre-headless \
    && rm -rf /var/lib/apt/lists/*
# … wie in Stufe 2 des Haupt-Dockerfile …
```

## Inbetriebnahme (Schritt für Schritt)

1. **`base.xml`** aus der Vorlage `rsc/de/sgollmer/solvismax/data/base.xml`
   erstellen und anpassen; darin `writablePathLinux="/data"` setzen und die
   SolvisRemote-**IP pinnen** (`url="192.168.1.35"`; nicht `solvis.fritz.box` —
   verwaister Fritzbox-Alt-Eintrag, Begründung in
   [INBETRIEBNAHME.md](INBETRIEBNAHME.md)). Die Datei wird neben
   das Jar gemountet (Default-Suchort) — alternativ per `--base-xml=/pfad`
   überschreiben.
2. **Passwort verschlüsseln** und als `passwordCrypt` eintragen:
   ```
   docker compose run --rm solvis --string-to-crypt=DEINPASSWORT
   ```
3. **Lernphase** einmalig fahren (schreibt nach `/data`):

   > ⚠️ **Vor jeder Lernphase gilt die Cutover-Checkliste aus
   > [INBETRIEBNAHME.md](INBETRIEBNAHME.md) Phase 5.** Die Lernphase klickt
   > real auf der Anlagen-GUI. Es darf zu keinem Zeitpunkt ein **zweiter
   > OCR-Client** gegen `192.168.1.35` laufen — weder der native Dienst auf
   > `ransible`, noch die Alt-Software auf `mon-dg`, noch eine Testinstanz auf
   > einem Arbeitsrechner. Zwei Clients auf derselben Oberfläche verklicken
   > sich gegenseitig, bis hin zu realen Fehlsteuerungen an der Heizung.

   ```
   docker compose run --rm solvis --server-learn
   ```
4. **Dauerbetrieb:**
   ```
   docker compose up -d
   ```

## TLS/mTLS (Variante B — deprecated, nicht unterstützt)

> ⛔ **Der native `<Ssl>`-mTLS-Pfad (Variante B) ist deprecated und wird nicht
> unterstützt.** Der Start bricht bei `<Ssl enable="true">` per Fail-Fast-Guard
> ab (Paho-v3-Fehler 32105). Produktivvariante ist **Variante A** (lokaler Broker
> + mTLS-Bridge) — siehe [ARCHITECTURE.md](ARCHITECTURE.md) §4 und
> [INBETRIEBNAHME.md](INBETRIEBNAHME.md) Phase 3. Der folgende Abschnitt ist nur
> noch historisch/analytisch: [mtls-behebung-vorschlag.md](mtls-behebung-vorschlag.md).

Zertifikate unter `./ssl` bereitstellen (read-only nach `/certs` gemountet) und
in `base.xml` im `<Mqtt>`-Element konfigurieren:

```xml
<Mqtt enable="true" brokerUrl="broker.example" port="8883" …>
    <Ssl enable="true"
         caFilePath="/certs/ca.crt"
         clientCrtFilePath="/certs/client.crt"
         clientKeyFilePath="/certs/client.key" />
</Mqtt>
```

Der private Schlüssel muss im **PKCS#8**-Format vorliegen
(`-----BEGIN PRIVATE KEY-----`; ggf. `openssl pkcs8 -topk8 -nocrypt …`).
Details und Verifikation: [CHANGELOG-fork.md](../CHANGELOG-fork.md).
