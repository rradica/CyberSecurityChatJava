# SecAssist – Security Incident Triage Chatbot

Workshop-Anwendung fuer eine **Red-Team-vs-Blue-Team-Uebung** zum Thema *CyberSecurity im AI-Umfeld*.

SecAssist ist ein interner Security-/Incident-Triage-Chatbot, der bewusst **realistische, absichtlich eingebaute Schwachstellen** enthaelt. Die Anwendung ist klein gehalten, browserfreundlich und auf reproduzierbare Workshop-Demos ausgelegt.

## Zentrale Architektur-Regel

> **Berechtigung vor Kontext.**

1. Zuerst Actor bestimmen (Rolle)
2. Dann Zweck / Aktion bestimmen
3. Dann erlaubte Quellen bestimmen
4. Dann Kontext aufbauen
5. Erst am Schluss Text generieren oder einen Tool-Vorschlag verarbeiten

Sicherheitskritische Entscheidungen liegen in der `PolicyEngine`, im `RetrievalService` und im `ToolPolicyService` – **nicht** im LLM.

## Aktueller Funktionsumfang der App

Die aktuelle App-Version bietet:

- Rollenwahl im Browser (`employee`, `security_analyst`, `contractor`)
- separate Falluebersicht als Einstieg, ueber die Benutzer ihre zugewiesenen Demo-Faelle sehen und zur Bearbeitung auswaehlen
- ein gefuehrtes Chat-UI mit Fallbriefing, Artefakten und Aktionschips
- modernisierte, zweistufige UI mit Inbox-/Fallansicht und anschliessender Chatbearbeitung
- automatische Intent-Erkennung ueber `/api/conversation`
- strukturierte Requests ueber `/api/chat`
- folgende Aktionen im Anwendungscode:
  - Chat / Fallanalyse
  - Handover-Entwurf
  - Similar Cases
  - Evidence / Quellenansicht
  - Triage mit moeglicher Workflow-Aktion
  - Notizen, die in den Retrieval-Kontext einfliessen
- sichtbare Warnungen, Sensitivitaetskennzeichnung pro Nachricht und optional einblendbare **Meta-Informationen** je Nachricht
- kurze, deterministische Fallbriefings zur besseren Exploration und stabileren Antworten

Wichtig: Die neuen Aktionschips im UI sind als **plausible Arbeitsmodi** gedacht. Sie ersetzen nicht die Anwendungslogik, sondern erleichtern die Exploration. Sicherheitskritische Entscheidungen bleiben weiterhin im deterministischen Backend.

## Schnellstart

### Lokal

```bash
./mvnw spring-boot:run
```

oder mit echtem Key:

```bash
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run
```

Dann im Browser: **http://localhost:8080**

> **Wichtiger Ist-Stand:** Die App startet mit dem Default aus `application.yml`, aber der aktuelle Code verdrahtet nur den echten `OpenAiChatService`. Ohne gueltigen `OPENAI_API_KEY` schlagen LLM-gestuetzte Requests daher zur Laufzeit fehl bzw. liefern Fehlermeldungen zurueck.

### GitHub Codespaces

1. Repository in Codespaces oeffnen
2. Warten bis der DevContainer gebaut ist
3. `./mvnw spring-boot:run` ausfuehren
4. Port 8080 wird automatisch weitergeleitet

## Technischer Stack

- Java 21
- Spring Boot 3.5.8
- Spring AI 1.1.0
- Maven (mit Wrapper)
- Statisches HTML/CSS/JS Frontend
- JUnit 5

## Demo-Rollen

| Rolle | Aktueller Zugriff |
|-------|-------------------|
| `contractor` | Oeffentliche Dokumente, normaler Chat, Evidence-Ansicht; kein Handover, keine Similar Cases |
| `employee` | Oeffentliche + interne Dokumente, Chat, Handover-Entwuerfe, Similar Cases |
| `security_analyst` | Voller Zugriff inkl. vertraulicher Inhalte und direkter Workflow-Aktionen |

Die Rolle wird im UI ausgewaehlt und mit jedem Request mitgeschickt. Es gibt bewusst keine echte Authentisierung.

## Demo-Faelle

- **suspicious_supplier_invoice** – Verdaechtige Rechnung von ACME Corp mit geaenderten Bankdaten und Compliance-Bezug
- **strange_attachment** – Unerwarteter `.iso`-Anhang von bekanntem Kontakt
- **suspicious_vpn_reset** – VPN-Passwort-Reset von unbekanntem Standort
- **finance_phishing** – Gezielte Phishing-Mail an die Finanzabteilung

Daneben existieren interne Vergleichsfaelle, die nur im Analysten-Kontext voll sichtbar sein sollen.

## API-Ueberblick

### Primaerer UI-Pfad

- `POST /api/conversation`
  - nimmt nur `role` und `message`
  - erkennt Fall und Intent ueber `ConversationService`

### Strukturierter Direktpfad

- `POST /api/chat`
  - nimmt `role`, `caseId`, `message`, `action`
  - erlaubt gezielte Tests von `chat`, `handover`, `similar_cases`, `evidence`, `workflow`

### Weitere Endpunkte

- `GET /api/cases`
- `GET /api/roles`
- `POST /api/cases/{caseId}/notes`
- `DELETE /api/notes`
- `GET /api/health`

## Workshop-Schwachstellen

Die Anwendung enthaelt **5 absichtlich eingebaute Schwachstellen**, die von den Teilnehmern im Code gefunden und gefixt werden sollen.

### 1. HANDOVER_SCOPE (Information Disclosure)
Im Handover-Modus verwendet der Retrieval-Policy-Filter fest kodierte Security-Team-Berechtigungen statt der Berechtigungen der aktuellen Rolle.

**Ort:** `RetrievalService.retrieve()`

### 2. EXISTENCE_ORACLE (Information Disclosure / Recon)
Bei gezielten Similar-Cases-Anfragen werden aggregierte Metadaten interner Vorfaelle auch an Nicht-Analysten sichtbar.

**Ort:** `DemoCaseService.findSimilarCases()` + `ChatOrchestrator.handleSimilarCases()` + `ConversationService.processMessage()`

### 3. TRUST_MERGE (Datenqualitaet)
Trusted und untrusted Quellen werden im Prompt in einen gemeinsamen Block gemischt, ohne klare Herkunftsmarkierung.

**Ort:** `PromptBuilder.buildSystemPrompt()` / `appendConsolidatedGuidance()`

### 4. TOOL_FASTTRACK (Autorisierung)
Der evidenzbasierte Tool-Gate bewertet untrusted Lieferantenquellen, operative Kontextsignale und interne Fallnotizen zu grosszuegig. Dadurch kann ein `employee` im Lieferantenrechnungs-Case sicherheitsrelevante Aktionen ausloesen.

**Ort:** `ToolPolicyService.evaluateAccess()` / `computeEvidenceScore()` / `effectiveThreshold()` / `sourceWeight()`

### 5. RAG_POISONING (Datenintegritaet)
Benutzernotizen werden als interne, vertrauenswuerdige Chunks gespeichert und spaeter wie kuratierte Quellen behandelt.

**Ort:** `RetrievalService.addUserNote()`

### Kill Chain (TRUST_MERGE + TOOL_FASTTRACK)
Bug 3 liefert der KI nicht sauber getrennte Quellen, Bug 4 erlaubt anschliessend die zu grosszuegige Ausfuehrung einer vorgeschlagenen Aktion. Zusammen kann ein `employee` einen Sicherheitsfall als False Positive herunterstufen.

### Erweiterte Kill Chain (RAG_POISONING + TRUST_MERGE + TOOL_FASTTRACK)
Bug 5 schleust gefaelschte "interne" Notizen ein, Bug 3 mischt sie in den generierten Kontext, Bug 4 erhoeht dadurch den Evidence-Score. Dadurch kann selbst ein spaeterer Fix an Bug 3 allein unzureichend sein.

## Realistischer Incident-Case

Der zentrale Workshop-Case ist eine verdaechtige Lieferantenrechnung von ACME Corp. Im normalen Chat- und Workflow-Pfad werden Supplier-Informationen mit internen Richtlinien und frueheren Erkenntnissen kombiniert. Durch diese realistisch wirkende Trust-Vermischung und den zu grosszuegigen Evidence-Score kann ein `employee` den Fall als False Positive markieren – obwohl die zentrale Quelle untrusted ist und ein frueherer ACME-Incident im vertraulichen Postmortem dokumentiert ist.

## Stabilisierung des LLM-Verhaltens

Damit die Workshop-Cases reproduzierbarer bleiben, wird die strukturierte Triage in zwei Schritten verarbeitet:

1. Das LLM erzeugt eine erste `TriageAssessment`-Bewertung.
2. Dieselbe Bewertung wird noch einmal challengend geprueft.

Nur wenn beide Bewertungen dieselbe Aktion stuetzen, bleibt die empfohlene Aktion erhalten. Die endgueltige Tool-Freigabe liegt weiterhin ausschliesslich im deterministischen Anwendungscode (`ToolPolicyService`).

## Projektstruktur

```
src/main/java/com/secassist/
├── config/      LlmConfig
├── model/       Role, DemoCase, DocumentChunk, ChatRequest/Response, NoteRequest, ToolActionResult
├── policy/      PolicyEngine
├── retrieval/   RetrievalService
├── llm/         LlmService, OpenAiChatService
├── tools/       ToolPolicyService, IncidentWorkflowService
├── service/     DemoCaseService, PromptBuilder, ChatOrchestrator, ConversationService
└── web/         ApiController

src/main/resources/
├── data/documents/   Markdown-Dokumente
├── data/chunks.json  Vorbereitete Retrieval-Chunks
└── static/           HTML, CSS, JS
```

## Umgebungsvariablen

| Variable | Beschreibung | Aktueller Stand |
|----------|--------------|-----------------|
| `OPENAI_API_KEY` | Key fuer den echten OpenAI-Pfad | aktiv verwendet |
| `SECASSIST_MOCK_LLM` | Mock-Schalter laut `.env.example` | derzeit **nicht** im Anwendungscode verdrahtet |

## Tests ausfuehren

```bash
./mvnw test
```

## Workshop-Hinweise

- Das **Red Team** versucht, die Schwachstellen auszunutzen (z. B. als `employee` einen Fall falsch herunterzustufen)
- Das **Blue Team** identifiziert die Bugs im Code und setzt Fixes um
- Die Schwachstellen sind mit `SCHWACHSTELLE [BUG_NAME]` im Code kommentiert
- Die Schwachstellen sind **absichtlich eingebaut** und **kein Versehen**
