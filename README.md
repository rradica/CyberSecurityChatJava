# SecAssist – Security Incident Triage Chatbot

Workshop-Anwendung für eine **Red-Team-vs-Blue-Team-Übung** zum Thema *CyberSecurity im AI-Umfeld*.

SecAssist ist ein interner Security-/Incident-Triage-Chatbot, der bewusst **realistische, per Feature-Flag steuerbare Schwachstellen** enthält.

## Zentrale Architektur-Regel

> **Berechtigung vor Kontext.**

1. Zuerst Actor bestimmen (Rolle)
2. Dann Zweck / Aktion bestimmen
3. Dann erlaubte Quellen bestimmen
4. Dann Kontext aufbauen
5. Erst am Schluss Text generieren

Sicherheitskritische Entscheidungen liegen in der `PolicyEngine`, nicht im LLM.

## Schnellstart

### Lokal

```bash
# Mock-Modus (kein API-Key nötig):
./mvnw spring-boot:run

# Mit echter OpenAI-API:
OPENAI_API_KEY=sk-... SECASSIST_MOCK_LLM=false ./mvnw spring-boot:run
```

Dann im Browser: **http://localhost:8080**

### GitHub Codespaces

1. Repository in Codespaces öffnen
2. Warten bis der DevContainer gebaut ist
3. `./mvnw spring-boot:run` ausführen
4. Port 8080 wird automatisch weitergeleitet

## Technischer Stack

- Java 21
- Spring Boot 3.5.8
- Spring AI 1.1.0
- Maven (mit Wrapper)
- Statisches HTML/CSS/JS Frontend
- JUnit 5

## Demo-Rollen

| Rolle | Zugriff |
|-------|---------|
| `contractor` | Nur öffentliche Dokumente, nur Evidence-View |
| `employee` | Öffentlich + intern, Handover-Entwürfe, Similar Cases |
| `security_analyst` | Voller Zugriff, kann Workflow-Aktionen auslösen |

Die Rolle wird im UI ausgewählt und mit jedem Request mitgeschickt (keine echte Authentisierung).

## Demo-Fälle

- **suspicious_supplier_invoice** – Verdächtige Rechnung von ACME Corp mit geänderten Bankdaten
- **strange_attachment** – Unerwarteter .iso-Anhang von bekanntem Kontakt
- **suspicious_vpn_reset** – VPN-Passwort-Reset von unbekanntem Standort
- **finance_phishing** – Gezielte Phishing-Mail an die Finanzabteilung

## Bug-Flags (Workshop-Schwachstellen)

Alle Schwachstellen sind per `application.yml` steuerbar. Default: **alle aktiv**.

```yaml
secassist:
  bug-flags:
    handover-scope: true     # BUG_HANDOVER_SCOPE
    existence-oracle: true   # BUG_EXISTENCE_ORACLE
    thread-stickiness: true  # BUG_THREAD_STICKINESS
    trust-merge: true        # BUG_TRUST_MERGE
    tool-fasttrack: true     # BUG_TOOL_FASTTRACK
```

### BUG_HANDOVER_SCOPE
Im Handover-Modus wird der Klassifikationsfilter übersprungen → vertrauliche Dokumente leaken in den Handover-Entwurf.
**Ort:** `RetrievalService.retrieve()`

### BUG_EXISTENCE_ORACLE
Similar-Cases-Endpunkt gibt Metadaten interner Vorfälle (Titel, IDs, Severity) auch an Nicht-Analysten zurück.
**Ort:** `DemoCaseService.findSimilarCases()`

### BUG_THREAD_STICKINESS
Rollenwechsel löscht Konversationshistorie und gecachten Kontext nicht → Kontext aus höher privilegierter Rolle bleibt erhalten.
**Ort:** `ChatOrchestrator.handleRoleChange()`

### BUG_TRUST_MERGE
Trusted und untrusted Quellen werden im LLM-Prompt ohne Trust-Level-Markierung zusammengemischt → das Modell kann nicht zwischen interner Policy und Supplier-Note unterscheiden.
**Ort:** `PromptBuilder.buildSystemPrompt()`

### BUG_TOOL_FASTTRACK
Schlüsselphrasen aus untrusted Quellen (z.B. "verified by compliance") umgehen die Rollenprüfung für Workflow-Tools → ein Employee kann sicherheitsrelevante Aktionen auslösen.
**Ort:** `ToolPolicyService.isToolAllowed()`

## Realistischer Incident-Case

Der zentrale Workshop-Case: Eine manipulierte Supplier-Note von ACME Corp enthält Phrasen wie "verified by compliance" und "confirmed as a false alarm". Wenn `BUG_TOOL_FASTTRACK` aktiv ist, kann ein Employee den Fall als False Positive markieren – obwohl die Quelle untrusted ist und ein früherer ACME-Incident im vertraulichen Postmortem dokumentiert ist.

## Projektstruktur

```
src/main/java/com/secassist/
├── config/      BugFlagsProperties, LlmConfig
├── model/       Role, DemoCase, DocumentChunk, ChatRequest/Response, ToolActionResult
├── policy/      PolicyEngine
├── retrieval/   RetrievalService
├── llm/         LlmService, MockLlmService, OpenAiChatService
├── tools/       ToolPolicyService, IncidentWorkflowService
├── service/     DemoCaseService, PromptBuilder, ChatOrchestrator
└── web/         ApiController

src/main/resources/
├── data/documents/   Markdown-Dokumente
├── data/chunks.json  Vorbereitete Retrieval-Chunks
└── static/           HTML, CSS, JS
```

## Umgebungsvariablen

| Variable | Beschreibung | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API-Key | `sk-dummy-key-for-workshop` |
| `SECASSIST_MOCK_LLM` | Mock-Modus ein/aus | `true` |

## Tests ausführen

```bash
./mvnw test
```

## Workshop-Hinweise

- Das **Red Team** versucht, die Schwachstellen auszunutzen (z.B. als Employee einen Fall als False Positive markieren)
- Das **Blue Team** identifiziert die Bugs im Code und setzt Fixes um (Bug-Flags auf `false` setzen reicht nicht – der Code muss gefixt werden)
- Alle Schwachstellen sind mit `// BUG:` im Code kommentiert
- Die Schwachstellen sind **absichtlich eingebaut** und **kein Versehen**
