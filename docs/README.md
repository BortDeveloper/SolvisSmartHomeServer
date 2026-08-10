# Doku-Index

> **Sprache:** Deutsch · **Status:** aktiv · **Bezug:** [../README.md](../README.md)

Einstieg über den Wegweiser [Nach Zielgruppe](../README.md#nach-zielgruppe) im
Haupt-README. Dieser Index verlinkt die Ziel-Artefakte der sechs Lenses und die
weiteren Fork-Dokumente.

## Nach Zielgruppe (Lens-Ziele)

| Lens | Ziel-Artefakt |
|---|---|
| **ANWENDER** | [../README.md#quick-start](../README.md#quick-start) |
| **TECHNIKER** | [ARCHITECTURE.md](ARCHITECTURE.md) (mit Topologie-Diagramm) |
| **PROGRAMMIERER** | [../CONTRIBUTING.md](../CONTRIBUTING.md) |
| **OPERATEUR** | [runbooks/](runbooks/) · [INBETRIEBNAHME.md](INBETRIEBNAHME.md) |
| **ARCHITEKTUR** | [../README.md#architektur](../README.md#architektur) → [ARCHITECTURE.md](ARCHITECTURE.md) |
| **COMPLIANCE** | [../README.md#compliance](../README.md#compliance) → [security/](security/) |

## Alle Doku-Dateien

### Betrieb

- [INBETRIEBNAHME.md](INBETRIEBNAHME.md) — Erstinbetriebnahme Schritt für Schritt (Container).
- [runbooks/](runbooks/) — Day-2-Standardaufgaben (Deploy, Update, Backup, Restore, Lernphase, Stop).
- [DOCKER.md](DOCKER.md) — Container-Grundlagen und Fallstricke.

### Architektur

- [ARCHITECTURE.md](ARCHITECTURE.md) — konsolidierte Fork-Sicht (Lineage, System-Kontext, Betriebsmodell, Grenzen).
- [jaxb-exploration.md](jaxb-exploration.md) — XML-Binding-Ablösung (Explorations-Erkenntnisse).
- [mtls-behebung-vorschlag.md](mtls-behebung-vorschlag.md) — Analyse des nativen mTLS-Pfads (Variante B).
- [g2.6-clone-drill.md](g2.6-clone-drill.md) — Reproduzierbarkeits-/Clone-Drill.

### Sicherheit & Compliance

- [security/](security/) — Secret-/PII-Handhabung, `gitleaks`-Befund, Restrisiken (S-2/S-3/S-8).

### Weiteres im Repo-Wurzelverzeichnis

- [../CONTRIBUTING.md](../CONTRIBUTING.md) — Build, Tests, Code-Layout, Beitrags-Regeln.
- [../FORK.md](../FORK.md) — warum dieser Fork existiert.
- [../MODERNISIERUNG.md](../MODERNISIERUNG.md) — Umbau-Roadmap.
- [../CHANGELOG-fork.md](../CHANGELOG-fork.md) — durchgeführte Anpassungen.
- [../TESTPLAN.md](../TESTPLAN.md) — phasenweiser Prüfplan.
