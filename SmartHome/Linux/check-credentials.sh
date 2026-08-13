#!/bin/bash
# check-credentials.sh — Credential-Pruefung vor Installation/GO-Live (F-120)
#
# Beweist VOR der OCR-Lernphase bzw. dem ersten Dienststart, dass die in
# base.xml hinterlegten Zugangsdaten zur realen Umgebung passen:
#
#   P1  SolvisRemote-Anmeldung: HTTP-GET mit --anyauth gegen http://<url>/
#       (WICHTIG: GET verwenden, NICHT 'curl -I' — die SolvisRemote
#       beantwortet HEAD-Requests mit 403; 401/200 kommen nur auf GET.
#       Messbefund 2026-08-13.)
#   P2  Unit-passwordCrypt in base.xml == Crypt(eingegebenes Web-Passwort)
#   P3  Login am lokalen MQTT-Broker (nur falls Mqtt enable="true" und
#       userName gesetzt) — via mosquitto_sub, KEIN Publish
#   P4  Mqtt-passwordCrypt in base.xml == Crypt(eingegebenes MQTT-Passwort)
#
# Aufruf:   ./check-credentials.sh [pfad/zu/base.xml]      (Default ./base.xml)
# Jar:      Env SOLVIS_JAR > ../../target/SolvisSmartHomeServer.jar
#           > ./SolvisSmartHomeServer.jar (liegt nach 'make prepare' daneben)
#
# Ergebnis-Semantik (dreiwertig, Haus-Standard ADR-0024):
#   PASS       — Pruefung durchgefuehrt, Ergebnis wie erwartet
#   FAIL       — Pruefung durchgefuehrt, Zugangsdaten/Konfiguration passen nicht
#   UNVERIFIED — Pruefung nicht durchfuehrbar (Werkzeug fehlt, Ziel nicht
#                erreichbar, unerwartete Antwort) — KEIN Beleg fuer Korrektheit
# Exit-Code: 0 = alle anwendbaren Pruefungen PASS
#            1 = mindestens ein FAIL
#            3 = kein FAIL, aber mindestens ein UNVERIFIED
#
# Read-only: Das Skript aendert NICHTS — keine base.xml-Edits, keine
# MQTT-Publishes (mosquitto_sub statt mosquitto_pub), keine Klicks auf der
# Anlagen-GUI (nur der HTTP-Login der SolvisRemote wird angesprochen).
#
# Sicherheits-Abwaegung (dokumentiert): Passwoerter werden ausschliesslich
# per 'read -rs' unsichtbar erfasst und niemals ausgegeben oder geloggt.
# Beim Aufruf von 'java --string-to-crypt=...' und 'mosquitto_sub -P ...'
# steht das Passwort kurzzeitig in der argv-Liste des Kindprozesses
# (einsehbar via /proc bzw. ps). Auf dem Einzelnutzer-Zielhost ist das
# akzeptiert (F-120); auf Mehrnutzer-Systemen dieses Skript nicht verwenden.

set -u

# Passwort-/Crypt-Variablen beim Verlassen aus der Shell-Umgebung entfernen.
trap 'unset PW_WEB PW_MQTT CALC_WEB CALC_MQTT 2>/dev/null' EXIT

SCRIPT_DIR=$(cd -- "$(dirname -- "$0")" >/dev/null 2>&1 && pwd)
BASE_XML="${1:-./base.xml}"

# --- Jar aufloesen: Env > Repo-Build > Kopie neben dem Skript ---------------
if [ -n "${SOLVIS_JAR:-}" ]; then
    JAR="$SOLVIS_JAR"
elif [ -f "$SCRIPT_DIR/../../target/SolvisSmartHomeServer.jar" ]; then
    JAR="$SCRIPT_DIR/../../target/SolvisSmartHomeServer.jar"
else
    JAR="$SCRIPT_DIR/SolvisSmartHomeServer.jar"
fi

if [ ! -f "$BASE_XML" ]; then
    echo "FEHLER: $BASE_XML nicht gefunden." >&2
    echo "Erst 'make prepare' ausfuehren und die Werte eintragen" >&2
    echo "(docs/INBETRIEBNAHME.md Phase 2/3), oder Pfad als Argument angeben." >&2
    exit 1
fi

# --- base.xml robust parsen -------------------------------------------------
# Die XML-Attribute koennen ueber mehrere Zeilen verteilt sein. Deshalb:
# 1) Datei auf eine Zeile glaetten (CR/LF/Tab -> Leerzeichen),
# 2) XML-Kommentare nicht-gierig entfernen (awk-Schleife; ein gieriges
#    sed 's/<!--.*-->//' wuerde echten Inhalt zwischen zwei Kommentaren
#    mitloeschen),
# 3) den jeweils ersten Start-Tag als Ganzes herausgreifen und daraus die
#    Attribute lesen.
FLAT=$(tr '\r\n\t' '   ' < "$BASE_XML" | awk '{
    while (match($0, /<!--/)) {
        pre  = substr($0, 1, RSTART - 1)
        rest = substr($0, RSTART + 4)
        if (match(rest, /-->/)) { $0 = pre substr(rest, RSTART + 3) }
        else                    { $0 = pre; break }
    }
    print
}')

first_tag() { # $1: Elementname ohne Namespace-Praefix
    printf '%s\n' "$FLAT" | grep -o "<tns:$1[[:space:]][^>]*>" | head -n 1
}

attr_of() { # $1: Start-Tag (eine Zeile), $2: Attributname
    printf '%s\n' "$1" | sed -n "s/.*[[:space:]]$2=\"\\([^\"]*\\)\".*/\\1/p"
}

UNIT_TAG=$(first_tag "Unit")
MQTT_TAG=$(first_tag "Mqtt")

if [ -z "$UNIT_TAG" ]; then
    echo "FEHLER: kein <tns:Unit ...>-Element in $BASE_XML gefunden." >&2
    exit 1
fi

UNIT_ID=$(attr_of "$UNIT_TAG" "id")
UNIT_ACCOUNT=$(attr_of "$UNIT_TAG" "account")
UNIT_URL=$(attr_of "$UNIT_TAG" "url")
UNIT_CRYPT=$(attr_of "$UNIT_TAG" "passwordCrypt")

MQTT_ENABLE=$(attr_of "$MQTT_TAG" "enable")
MQTT_BROKER=$(attr_of "$MQTT_TAG" "brokerUrl")
MQTT_PORT=$(attr_of "$MQTT_TAG" "port")
MQTT_USER=$(attr_of "$MQTT_TAG" "userName")
MQTT_CRYPT=$(attr_of "$MQTT_TAG" "passwordCrypt")

if [ -z "$UNIT_URL" ] || [ -z "$UNIT_ACCOUNT" ]; then
    echo "FEHLER: Unit-Attribute 'url'/'account' fehlen in $BASE_XML." >&2
    exit 1
fi

echo "check-credentials — base.xml: $BASE_XML"
echo "  Unit '$UNIT_ID': account=$UNIT_ACCOUNT url=$UNIT_URL"
if [ "$MQTT_ENABLE" = "true" ] && [ -n "$MQTT_USER" ]; then
    echo "  Mqtt: broker=$MQTT_BROKER:$MQTT_PORT userName=$MQTT_USER"
else
    echo "  Mqtt: nicht aktiv oder ohne userName — P3/P4 entfallen."
fi
echo

# --- Ergebnisverwaltung (dreiwertig) ----------------------------------------
N_FAIL=0
N_UNVER=0
RESULTS=()

report() { # $1 Kennung, $2 PASS|FAIL|UNVERIFIED, $3 einzeilige Begruendung
    local line
    line=$(printf '%-3s %-10s %s' "$1" "$2" "$3")
    echo "$line"
    RESULTS+=("$line")
    case "$2" in
        FAIL)       N_FAIL=$((N_FAIL + 1)) ;;
        UNVERIFIED) N_UNVER=$((N_UNVER + 1)) ;;
    esac
}

crypt_of() { # $1: Klartext -> Crypt-Wert. 'tail -n1' ist das bewiesene
             # Parsing der --string-to-crypt-Ausgabe (Referenzlauf F-120).
    java -jar "$JAR" --string-to-crypt="$1" 2>/dev/null | tail -n 1
}

JAVA_OK=true
if ! command -v java >/dev/null 2>&1; then
    JAVA_OK=false
    JAVA_REASON="'java' nicht im PATH"
elif [ ! -f "$JAR" ]; then
    JAVA_OK=false
    JAVA_REASON="Jar fehlt: $JAR (erst './mvnw -B clean package' bzw. 'make prepare'; Env SOLVIS_JAR moeglich)"
fi

# --- P1: SolvisRemote-Anmeldung ---------------------------------------------
read -rs -p "Solvis-Web-Passwort (Account '$UNIT_ACCOUNT'): " PW_WEB
echo

if [ -z "$PW_WEB" ]; then
    report "P1" "UNVERIFIED" "kein Passwort eingegeben"
    report "P2" "UNVERIFIED" "kein Passwort eingegeben"
else
    # Erreichbarkeits-Vorpruefung OHNE Auth: gesund ist HTTP 401.
    # GET verwenden, NICHT HEAD ('curl -I') — HEAD liefert an der
    # SolvisRemote 403 (Messbefund 2026-08-13).
    HTTP_NOAUTH=$(curl -s -o /dev/null -w '%{http_code}' \
        --connect-timeout 5 --max-time 15 "http://$UNIT_URL/") || HTTP_NOAUTH="000"
    if [ "$HTTP_NOAUTH" != "401" ]; then
        case "$HTTP_NOAUTH" in
            000) report "P1" "UNVERIFIED" "SolvisRemote http://$UNIT_URL/ nicht erreichbar (curl-Fehler)" ;;
            200) report "P1" "UNVERIFIED" "SolvisRemote antwortet 200 ohne Login — Passwort nicht beweisbar" ;;
            *)   report "P1" "UNVERIFIED" "unerwartete Antwort ohne Auth: HTTP $HTTP_NOAUTH (erwartet 401)" ;;
        esac
    else
        HTTP_AUTH=$(curl -s -o /dev/null -w '%{http_code}' \
            --connect-timeout 5 --max-time 15 \
            --anyauth -u "$UNIT_ACCOUNT:$PW_WEB" "http://$UNIT_URL/") || HTTP_AUTH="000"
        case "$HTTP_AUTH" in
            200) report "P1" "PASS" "Anmeldung an http://$UNIT_URL/ akzeptiert (HTTP 200)" ;;
            401) report "P1" "FAIL" "Anmeldung abgelehnt (HTTP 401) — Account/Passwort pruefen" ;;
            000) report "P1" "UNVERIFIED" "Verbindung waehrend Auth-Pruefung fehlgeschlagen" ;;
            *)   report "P1" "UNVERIFIED" "unerwartete Antwort mit Auth: HTTP $HTTP_AUTH" ;;
        esac
    fi

    # --- P2: Unit-Crypt-Abgleich --------------------------------------------
    if [ "$JAVA_OK" != "true" ]; then
        report "P2" "UNVERIFIED" "$JAVA_REASON"
    else
        CALC_WEB=$(crypt_of "$PW_WEB")
        if [ -z "$CALC_WEB" ]; then
            report "P2" "UNVERIFIED" "--string-to-crypt lieferte keine Ausgabe"
        elif [ "$CALC_WEB" = "$UNIT_CRYPT" ]; then
            report "P2" "PASS" "Unit-passwordCrypt in base.xml passt zum eingegebenen Passwort"
        else
            report "P2" "FAIL" "Unit-passwordCrypt in base.xml passt NICHT zum eingegebenen Passwort"
        fi
    fi
fi

# --- P3/P4: lokaler MQTT-Login + Crypt-Abgleich (nur falls konfiguriert) ----
if [ "$MQTT_ENABLE" = "true" ] && [ -n "$MQTT_USER" ]; then
    read -rs -p "MQTT-Passwort (User '$MQTT_USER' @ $MQTT_BROKER:$MQTT_PORT): " PW_MQTT
    echo

    if [ -z "$PW_MQTT" ]; then
        report "P3" "UNVERIFIED" "kein Passwort eingegeben"
        report "P4" "UNVERIFIED" "kein Passwort eingegeben"
    else
        if ! command -v mosquitto_sub >/dev/null 2>&1; then
            report "P3" "UNVERIFIED" "mosquitto_sub nicht installiert (Paket mosquitto-clients)"
        else
            # Read-only-Login-Probe: Subscribe auf ein $SYS-Topic, kein Publish.
            # rc 0 = Nachricht empfangen, rc 27 = Timeout ohne Nachricht —
            # beides heisst: der Broker hat den Login AKZEPTIERT.
            # shellcheck disable=SC2016  # '$SYS' ist ein literales MQTT-Topic, keine Shell-Variable
            mosquitto_sub -h "$MQTT_BROKER" -p "$MQTT_PORT" \
                -u "$MQTT_USER" -P "$PW_MQTT" \
                -t '$SYS/broker/version' -C 1 -W 5 >/dev/null 2>&1
            MQTT_RC=$?
            case "$MQTT_RC" in
                0|27) report "P3" "PASS" "Broker $MQTT_BROKER:$MQTT_PORT akzeptiert den Login (rc=$MQTT_RC)" ;;
                *)    report "P3" "FAIL" "Login abgelehnt oder Broker nicht erreichbar (mosquitto_sub rc=$MQTT_RC)" ;;
            esac
        fi

        if [ "$JAVA_OK" != "true" ]; then
            report "P4" "UNVERIFIED" "$JAVA_REASON"
        elif [ -z "$MQTT_CRYPT" ]; then
            report "P4" "FAIL" "Mqtt-userName gesetzt, aber kein passwordCrypt im <tns:Mqtt>-Element"
        else
            CALC_MQTT=$(crypt_of "$PW_MQTT")
            if [ -z "$CALC_MQTT" ]; then
                report "P4" "UNVERIFIED" "--string-to-crypt lieferte keine Ausgabe"
            elif [ "$CALC_MQTT" = "$MQTT_CRYPT" ]; then
                report "P4" "PASS" "Mqtt-passwordCrypt in base.xml passt zum eingegebenen Passwort"
            else
                report "P4" "FAIL" "Mqtt-passwordCrypt in base.xml passt NICHT zum eingegebenen Passwort"
            fi
        fi
    fi
else
    echo "P3/P4 uebersprungen: Mqtt enable=\"$MQTT_ENABLE\", userName=\"${MQTT_USER:-}\" (nicht anwendbar)."
fi

# --- Zusammenfassung ---------------------------------------------------------
echo
echo "== Zusammenfassung (dreiwertig, ADR-0024) =="
for line in "${RESULTS[@]}"; do
    echo "  $line"
done

if [ "$N_FAIL" -gt 0 ]; then
    echo "GESAMT: FAIL ($N_FAIL FAIL, $N_UNVER UNVERIFIED) — Konfiguration VOR Lernphase/Start korrigieren."
    exit 1
elif [ "$N_UNVER" -gt 0 ]; then
    echo "GESAMT: UNVERIFIED ($N_UNVER Pruefung(en) nicht durchfuehrbar) — kein Beleg, kein Gegenbeleg."
    exit 3
else
    echo "GESAMT: PASS — alle anwendbaren Credential-Pruefungen bestanden."
    exit 0
fi
