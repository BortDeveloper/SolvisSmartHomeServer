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
  Wirksam sind allein `chmod 600 base.xml` + Owner = Dienstnutzer (UID 10001) —
  Pflichtschritt [../INBETRIEBNAHME.md](../INBETRIEBNAHME.md) Phase 2.
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
Broker-ACL `readwrite solvis/#`; SSOT im Vertragspartner-Repo `ccu2mqtt`). Drei
bewusst akzeptierte Restrisiken mit je einer dokumentierten Entscheidung
([../ARCHITECTURE.md](../ARCHITECTURE.md) §5):

| ID | Risiko | Entscheidung / Kompensation |
|---|---|---|
| S-2 | `passwordCrypt` = Obfuskation, kein Schutz | Dateirechte als realer Schutz; Krypto-Umbau Roadmap 4.6 |
| S-3 | Proprietärer TCP-/JSON-Server (Port 10735), unauth. | Default-Bind `127.0.0.1`, abschaltbar (`SOLVIS_TCPSERVER_ENABLE=false`), Ports nicht veröffentlicht |
| S-8 | SolvisRemote nur HTTP (kein HTTPS) | Akzeptiertes Restrisiko; Anlagen-/IoT-Segment, kein Routing aus untrusted Netzen |
