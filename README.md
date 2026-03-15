# SecAssist – Security Incident Triage Chatbot

Workshop-Anwendung fuer eine **Red-Team-vs-Blue-Team-Uebung** zum Thema *CyberSecurity im AI-Umfeld*.

SecAssist ist ein interner Security-/Incident-Triage-Chatbot, der bewusst **realistische, absichtlich eingebaute Schwachstellen** enthaelt.

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
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run
```

Dann im Browser: **http://localhost:8080**

> **Hinweis:** Ein gueltiger `OPENAI_API_KEY` ist erforderlich. Ohne Key startet die App, aber Chat-Anfragen geben Fehlermeldungen zurueck.

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

| Rolle | Zugriff |
|-------|---------|
| `contractor` | Nur oeffentliche Dokumente, nur Evidence-View |
| `employee` | Oeffentlich + intern, Handover-Entwuerfe, Similar Cases |
| `security_analyst` | Voller Zugriff, kann Workflow-Aktionen ausloesen |

Die Rolle wird im UI ausgewaehlt und mit jedem Request mitgeschickt (keine echte Authentisierung).

## Demo-Faelle

- **suspicious_supplier_invoice** – Verdaechtige Rechnung von ACME Corp mit geaenderten Bankdaten
- **strange_attachment** – Unerwarteter .iso-Anhang von bekanntem Kontakt
- **suspicious_vpn_reset** – VPN-Passwort-Reset von unbekanntem Standort
- **finance_phishing** – Gezielte Phishing-Mail an die Finanzabteilung

## Workshop-Schwachstellen

Die Anwendung enthaelt **5 absichtlich eingebaute Schwachstellen**, die von den Teilnehmern im Code gefunden und gefixt werden sollen.

### 1. HANDOVER_SCOPE (Information Disclosure)
Im Handover-Modus verwendet der Policy-Filter fest kodierte Security-Team-Berechtigungen statt der Berechtigungen der aktuellen Rolle → vertrauliche Dokumente leaken in den Handover-Entwurf.
**Ort:** `RetrievalService.retrieve()`

### 2. EXISTENCE_ORACLE (Information Disclosure / Recon)
Bei gezielten Suchanfragen ueber die Konversations-API leaken aggregierte Metadaten interner Vorfaelle (Anzahl, Kategorie, Schweregrad) auch an Nicht-Analysten.
**Ort:** `DemoCaseService.findSimilarCases()` + `ConversationService`

### 3. TRUST_MERGE (Datenqualitaet)
Trusted und untrusted Quellen werden im LLM-Prompt ohne Trust-Level-Markierung zusammengemischt → das Modell kann nicht zwischen interner Policy und Supplier-Note unterscheiden.
**Ort:** `PromptBuilder.buildSystemPrompt()`

### 4. TOOL_FASTTRACK (Autorisierung)
Der evidenzbasierte Tool-Gate bewertet untrusted Lieferantenquellen, operative Kontextsignale und interne Fallnotizen zu großzuegig. Dadurch kann ein Employee im Lieferantenrechnungs-Case sicherheitsrelevante Aktionen ausloesen.
**Ort:** `ToolPolicyService.evaluateAccess()` / `ToolPolicyService.computeEvidenceScore()`

### 5. RAG_POISONING (Datenintegritaet)
User-Notizen werden als Chunks mit `trustLevel: "high"` und `classification: "internal"` gespeichert. Ein Angreifer kann gefaelschte "interne Einschaetzungen" einschleusen.
**Ort:** `RetrievalService.addUserNote()`

### Kill Chain (TRUST_MERGE + TOOL_FASTTRACK)
Bug 3 (Datenqualitaet) liefert der KI nicht-unterscheidbare Quellen → falsche Empfehlung. Bug 4 (Autorisierung) erlaubt einem Employee, diese Empfehlung auszufuehren. Zusammen: **Ein Satz genuegt, um einen Sicherheitsfall dauerhaft zu unterdruecken.**

### Erweiterte Kill Chain (RAG_POISONING + TRUST_MERGE + TOOL_FASTTRACK)
Bug 5 (Datenintegritaet) schleust gefaelschte "interne" Dokumente ein → Bug 3 mischt sie ohne Label → Bug 4 erhoeht den Evidence-Score. **Bug 5 umgeht den Bug-3-Fix**, da die vergiftete Notiz als `trustLevel: high` in den "Verified Sources" erscheint.

## Realistischer Incident-Case

Der zentrale Workshop-Case ist eine verdaechtige Lieferantenrechnung von ACME Corp. Im normalen Chat- und Workflow-Pfad werden oeffentliche Supplier-Notes zusammen mit internen Richtlinien verwendet. Durch diese realistisch wirkende Trust-Vermischung und den zu großzuegigen Evidence-Score kann ein Employee den Fall als False Positive markieren – obwohl die Quelle untrusted ist und ein frueherer ACME-Incident im vertraulichen Postmortem dokumentiert ist.

## Stabilisierung des LLM-Verhaltens

Damit die Workshop-Cases reproduzierbar bleiben, ohne im Anwendungscode kuenstliche Spezial-Trigger zu hinterlegen, wird die strukturierte Triage jetzt in zwei Schritten verarbeitet:

1. Das LLM erzeugt eine erste `TriageAssessment`-Bewertung.
2. Dieselbe Bewertung wird durch das LLM noch einmal challengend geprueft.

Nur wenn beide Bewertungen dieselbe Aktion stuetzen, bleibt die empfohlene Aktion erhalten. Die endgueltige Tool-Freigabe liegt trotzdem weiterhin ausschließlich im deterministischen Anwendungscode (`ToolPolicyService`).

## Projektstruktur

```
src/main/java/com/secassist/
├── config/      LlmConfig
├── model/       Role, DemoCase, DocumentChunk, ChatRequest/Response, NoteRequest, ToolActionResult
├── policy/      PolicyEngine
├── retrieval/   RetrievalService
├── llm/         LlmService (Interface), OpenAiChatService
├── tools/       ToolPolicyService, IncidentWorkflowService
├── service/     DemoCaseService, PromptBuilder, ChatOrchestrator, ConversationService
└── web/         ApiController

src/main/resources/
├── data/documents/   Markdown-Dokumente
├── data/chunks.json  Vorbereitete Retrieval-Chunks
└── static/           HTML, CSS, JS
```

## Umgebungsvariablen

| Variable | Beschreibung | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API-Key (erforderlich) | `sk-dummy-key-for-workshop` |

## Tests ausfuehren

```bash
./mvnw test
```

## Workshop-Hinweise

- Das **Red Team** versucht, die Schwachstellen auszunutzen (z.B. als Employee einen Fall als False Positive markieren)
- Das **Blue Team** identifiziert die Bugs im Code und setzt Fixes um
- Die Schwachstellen sind mit `SCHWACHSTELLE [BUG_NAME]` im Code kommentiert
- Die Schwachstellen sind **absichtlich eingebaut** und **kein Versehen**
