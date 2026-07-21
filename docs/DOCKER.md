# Betrieb als Docker-Container

Der SolvisSmartHomeServer lässt sich als Container betreiben — er ist eine reine
Java-Anwendung ohne native Abhängigkeiten und braucht keinen Bildschirm
(die OCR arbeitet auf über HTTP geladenen Bildern). Dieses Verzeichnis liefert
ein [`Dockerfile`](../Dockerfile) (Zwei-Stufen-Build), eine
[`docker-compose.yml`](../docker-compose.yml) und diese Anleitung.

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
RUN apt-get update && apt-get install -y --no-install-recommends default-jdk-headless ant \
    && rm -rf /var/lib/apt/lists/*
# … ant clean && ant …

FROM debian:bookworm
RUN apt-get update && apt-get install -y --no-install-recommends default-jre-headless \
    && rm -rf /var/lib/apt/lists/*
# … wie in Stufe 2 des Haupt-Dockerfile …
```

## Inbetriebnahme (Schritt für Schritt)

1. **`base.xml`** aus `base.xml.new` erstellen und anpassen; darin
   `writablePathLinux="/data"` setzen. Die Datei wird neben das Jar gemountet
   (Default-Suchort) — alternativ per `--base-xml=/pfad` überschreiben.
2. **Passwort verschlüsseln** und als `passwordCrypt` eintragen:
   ```
   docker compose run --rm solvis --string-to-crypt=DEINPASSWORT
   ```
3. **Lernphase** einmalig fahren (schreibt nach `/data`):
   ```
   docker compose run --rm solvis --server-learn
   ```
4. **Dauerbetrieb:**
   ```
   docker compose up -d
   ```

## TLS/mTLS (Fork-Feature)

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
