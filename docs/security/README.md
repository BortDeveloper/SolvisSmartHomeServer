# Sicherheit & Compliance (COMPLIANCE-Lens)

> **Sprache:** Deutsch · **Status:** aktiv · **Zielgruppe:** Programmierer,
> Verantwortliche · **Bezug:** [../ARCHITECTURE.md](../ARCHITECTURE.md) §5
> (Erbschafts-Inventar), [../INBETRIEBNAHME.md](../INBETRIEBNAHME.md) Phase 2
> (Dateirechte), [../../README.md](../../README.md) (Compliance-Kurzsicht)

Konsolidierte Nachweis-Lage. Diese Seite **navigiert**; die tragende Risiko-
und Entscheidungssicht bleibt SSOT in
[../ARCHITECTURE.md §5](../ARCHITECTURE.md) und wird hier nicht dupliziert.

## Secret-/PII-Handhabung

- **Keine realen Betriebs-Secrets im Repo.** Zertifikate, private Schlüssel und
  Anlagen-Zugangsdaten sind nicht-versionierter Operator-State: gemountete
  Dateien (`/certs`, nur für Sonderfälle) und `passwordCrypt`-Werte in einer
  lokalen, **nicht eingecheckten** `base.xml` (aus der Vorlage
  `rsc/de/sgollmer/solvismax/data/base.xml`).
- **Realer Schutz = Dateirechte.** `passwordCrypt` ist Obfuskation (ECB,
  hartkodierter, aus dem Quellcode ableitbarer Schlüssel), **kein** Schutz.
  Wirksam sind allein die Dateirechte der installierten `base.xml`: Soll seit
  der S-5-Härtung `640 root:solvis` (Dienst liest, kann nicht schreiben —
  sonst könnte ein kompromittierter Dienst Broker-URL und Zugangsdaten
  umschreiben), bei jedem `installSolvis`-/`updateSolvis`-Lauf durch das
  Makefile-Ziel `enforceOwnership` durchgesetzt. `chmod 600` gilt nur für die
  Arbeitskopie beim Bearbeiten; im Container-Fall gehört die gemountete Datei
  der UID 10001. Nach einem Restore ist der `chown root:solvis`/`chmod 640`-
  Block aus [../runbooks/README.md](../runbooks/README.md) (Aufgabe Restore,
  Schritt 3) Pflicht. Beleg: [../INBETRIEBNAHME.md](../INBETRIEBNAHME.md)
  Phase 2 und Update-Abschnitt, `CHANGELOG-fork.md` Eintrag S-5.
- **Kein PII** im Repo (kein Personenbezug in Code/Doku/Testdaten).

## `gitleaks`-Befund (Nachweis)

Ein Scan über den Fork-Stand meldet ausschließlich Upstream-Alt-Artefakte, keine
Live-Credentials:

```bash
gitleaks detect --no-banner --redact -s .
```

| Fundstelle | Art | Bewertung |
|---|---|---|
| `rsc/de/sgollmer/solvismax/data/control.xml` | `generic-api-key` (32×) | Muster-/Screen-Daten, keine Credentials |
| `rsc/de/sgollmer/solvismax/data/base.xml` | `generic-api-key` (2×) | Beispiel-`passwordCrypt` in der Template-`base.xml` |
| `test/de/sgollmer/solvismax/crypt/SslTest.java` | `private-key` (1×) | PKCS#8-**Test**schlüssel (Fixture) |

## Berührte Standards & Restrisiken

Transportsicherheit nach außen über die **mTLS-Bridge** (Client-Zertifikate +
Broker-ACL `readwrite solvis/#`). Diese Gegenseite ist **nicht** Gegenstand
dieses Repos: SSOT ist das as-built-Runbook
`ccu2mqtt:docs/runbooks/solvis-bridge-ransible.md`, das den Ist-Zustand seit
2026-08-13 als Soll führt (Bridge `solvis-bridge-to-primary`, Login
`solvis-bridge`, PKI und Deploy-Pfad der Zertifikate). Drei
bewusst akzeptierte Restrisiken mit je einer dokumentierten Entscheidung
([../ARCHITECTURE.md](../ARCHITECTURE.md) §5):

| ID | Risiko | Entscheidung / Kompensation |
|---|---|---|
| S-2 | `passwordCrypt` = Obfuskation, kein Schutz | Dateirechte als realer Schutz; Krypto-Umbau Roadmap 4.6 |
| S-3 | Proprietärer TCP-/JSON-Server (Port 10735), unauth. | Default-Bind `127.0.0.1`, abschaltbar (`SOLVIS_TCPSERVER_ENABLE=false`), Ports nicht veröffentlicht |
| S-8 | SolvisRemote nur HTTP (kein HTTPS) | Akzeptiertes Restrisiko; Anlagen-/IoT-Segment, kein Routing aus untrusted Netzen |
