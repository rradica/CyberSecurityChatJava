# AGENTS.md

## Projektzweck

Dieses Repository enthält eine kleine, bewusst überschaubare Workshop-Anwendung namens **SecAssist**.

SecAssist ist ein interner Security-/Incident-Triage-Chatbot für eine Red-Team-vs-Blue-Team-Übung zum Thema **CyberSecurity im AI-Umfeld**.

Die Anwendung muss:
- als **Spring-Boot-Anwendung** umgesetzt werden
- **Spring AI** für Chat- und Tool-Integration verwenden
- direkt in **GitHub Codespaces im Browser** lauffähig sein
- einfach verständlichen Code haben
- bewusst klein und wartbar bleiben
- gezielt einige realistische, per Flag aktivierbare Schwachstellen enthalten
- für Teilnehmer schnell lesbar und änderbar sein
- für einen Workshop robust und reproduzierbar funktionieren

---

## Harte Workshop-Anforderung: vorbereitete Schwachstellen müssen im Code vorhanden sein

Dieses Repository ist **bewusst keine vollständig gehärtete Anwendung**.

Die Anwendung ist eine **Workshop-App für eine Red-Team-vs-Blue-Team-Übung**.
Daher ist es eine **zentrale Anforderung**, dass mehrere **realistische, nicht triviale, absichtlich eingebaute Schwachstellen** bereits **im Code vorhanden** sind.

Diese Schwachstellen sind **kein Versehen** und **kein technischer Schuldenrest**, sondern ein **Pflichtbestandteil des Designs**.

WICHTIG:
- Entferne diese Schwachstellen nicht automatisch.
- "Verbessere" sie nicht unaufgefordert.
- Baue nicht versehentlich eine vollständig sichere Standardlösung.
- Implementiere die Schwachstellen so, dass sie im Workshop von Teilnehmern gefunden, verstanden und behoben werden können.
- Die Schwachstellen müssen **zur Ankündigung des Workshops passen**:
  - RAG-/Kontext-bezogene Risiken
  - untrusted content
  - Trust-Boundary-Fehler
  - überprivilegierter Kontext
  - unsichere Tool-/Workflow-Auslösung
  - Security-Probleme, die **nicht** durch gutes Prompting allein gelöst werden

Die Schwachstellen dürfen **nicht trivial oder cartoonhaft** sein.

Nicht erwünscht sind zum Beispiel:
- offensichtliche Backdoors
- plumpe Demo-Leaks ohne plausible Produktgeschichte
- lächerliche Debug-Endpunkte
- reine „ignore all instructions“-Beispiele als Hauptmechanik
- komplett fehlende Autorisierung ohne fachlichen Hintergrund

Erwünscht sind stattdessen:
- plausible Produktfehler
- inkonsistente Policy-Durchsetzung
- fehlerhafte Trust-Zusammenführung
- übermächtige Incident-/Handover-Pfade
- Existence-Oracles
- Conversation-/Case-Stickiness
- zu schwach abgesicherte Tool-/Action-Freigaben
- Fehler, die aus Hilfsbereitschaft, UX, operativer Effizienz oder Produktkompromissen entstanden wirken

Die Schwachstellen müssen:
- fachlich glaubwürdig sein
- technisch nachvollziehbar sein
- im Code wirklich vorhanden sein
- reproduzierbar auslösbar sein
- separat per Flag steuerbar sein
- vom Blue Team mit überschaubarem Aufwand fixbar sein

Wenn du Code erzeugst, gilt daher:
**Baue die Schwachstellen bewusst ein, statt sie wegzuoptimieren.**

---

## Zentrale Architekturregel

**Berechtigung vor Kontext**

Diese Regel ist die wichtigste Leitplanke des Projekts.

Bedeutung:
1. Zuerst Actor bestimmen
2. Dann Zweck / Aktion bestimmen
3. Dann erlaubte Quellen bestimmen
4. Dann Kontext aufbauen
5. Erst am Schluss Text generieren oder einen Tool-Vorschlag erstellen

WICHTIG:
- Das LLM darf **nicht** über Berechtigung entscheiden.
- Das LLM darf **nicht** über Quellenfreigabe entscheiden.
- Das LLM darf **nicht** über Policy-Grenzen entscheiden.
- Sicherheitskritische Entscheidungen müssen in deterministischem Anwendungscode liegen.

Wenn du Code erzeugst oder änderst, prüfe immer:
**Wird hier erst Policy geprüft und danach Kontext gebaut?**
Wenn nicht, ist die Lösung falsch.

---

## Technischer Zielstack

Die Anwendung soll bewusst **einfach und browserfreundlich** sein.

### Verwende
- Java 21
- Spring Boot 3.5.x
- Spring AI 1.1.x
- Maven
- Maven Wrapper
- Spring Web
- Thymeleaf oder einfache serverseitige HTML-Views
- alternativ sehr einfache statische Frontend-Dateien unter `src/main/resources/static`
- JUnit 5
- möglichst wenige zusätzliche Bibliotheken

### Verwende nicht
- Spring WebFlux
- Kafka
- Messaging
- Docker-Pflicht im Laufzeitpfad
- externe Datenbank
- Vector-DB
- LangChain4j
- komplizierte Security-Framework-Setups
- unnötige Architekturmuster

### Grundsatz
Die App soll in **GitHub Codespaces** nach dem Klonen mit sehr wenigen Schritten startbar sein:
- `./mvnw spring-boot:run`
oder
- Run-Konfiguration in VS Code / Codespaces

---

## Spring-AI-spezifische Leitplanken

Verwende Spring AI nur dort, wo es wirklich hilft.

### Bevorzugt
- `ChatClient`
- `ToolCallback`
- einfache, klar lesbare Prompt-Erzeugung
- optional Advisors nur dann, wenn sie die Architektur klarer machen

### Nicht erwünscht
- komplexe Advisor-Ketten ohne Mehrwert
- tiefe Spring-AI-Magie, die Teilnehmer schwer verstehen
- automatische Retrieval-/Vector-Store-Integration
- komplizierte mehrstufige Agent-Schleifen

### Sehr wichtig
Das LLM soll primär:
- Antworttext formulieren
- strukturierte Empfehlungen erzeugen
- einen Tool-Vorschlag liefern

Die App soll:
- Rollen prüfen
- Quellen auswählen
- Tool-Freigaben prüfen
- Bugs deterministisch auslösen
- Incident-Effekte simulieren

---

## Ziel der Anwendung

Die App soll einen kleinen Security-Chatbot bereitstellen, mit dem Benutzer:
- einen Demo-Fall auswählen
- Fragen stellen
- einen **handover draft for security** erzeugen
- **similar cases** anfordern
- **evidence / sources** anzeigen
- einen sicherheitsrelevanten Workflow-Schritt vorbereiten oder auslösen können

Es handelt sich um eine **Workshop-App**, nicht um ein Produktivsystem.

---

## Wichtigste fachliche Story

Der Chatbot verarbeitet Security-relevante Anfragen rund um:
- verdächtige Lieferanten-E-Mails
- Anhänge
- Phishing-Meldungen
- Incident-Handover
- ähnliche frühere Fälle
- operative Security-Hinweise

Die App soll zeigen:
- wie untrusted content gefährlich werden kann
- wie RAG-/Kontextfehler entstehen
- wie Tool- oder Workflow-Aktionen unsicher werden
- warum Prompting allein keine Security Boundary ist

---

## Realistischer Incident-Case

Die App muss mindestens **einen realistischen Tool-/Workflow-Case** enthalten, der zur Workshop-Beschreibung passt.

### Zielbild
Eine **manipulierte, aber plausibel wirkende Quelle** beeinflusst das System so, dass ein **legitimer Workflow-Schritt** falsch ausgelöst wird und dadurch ein **Security Incident** entsteht.

### Bevorzugter Case
Nicht direkte Allowlist-Freigabe, sondern ein realistischerer Triage-Fall:

- Der Bot analysiert einen Fall zu einer verdächtigen Lieferanten-Mail.
- Der Bot kann einen Fall klassifizieren oder weiterleiten.
- Eine untrusted Quelle beeinflusst die Triage zu stark.
- Die App stößt daraufhin einen legitimen Workflow-Schritt an, z. B.:
  - `mark_case_likely_false_positive`
  - `set_case_priority_low`
  - `route_case_to_finance_queue`
  - `attach_supplier_trust_note`

Der Incident entsteht im zweiten Schritt:
- Der Fall wird falsch behandelt
- Die Security-Eskalation unterbleibt oder verzögert sich
- Ein Folgefall rutscht später leichter durch

Das soll realistischer wirken als eine plumpe „direkte Freigabe“.

---

## Rollenmodell

Die App verwendet nur wenige Demo-Rollen:
- `contractor`
- `employee`
- `security_analyst`

Keine echte Authentisierung nötig.
Eine einfache Rollenauswahl im UI reicht.

WICHTIG:
Jeder Request muss mit der **aktuell gewählten Rolle** ausgewertet werden.
Keine impliziten Rechte nur aus Falltyp oder Konversation ableiten.

---

## Demo-Fälle

Die Anwendung soll vorbereitete Demo-Fälle enthalten, zum Beispiel:
- `suspicious_supplier_invoice`
- `strange_attachment`
- `suspicious_vpn_reset`
- `finance_phishing`

Nur vorbereitete Demo-Artefakte verwenden.
Keine freie Upload-Logik bauen, wenn sie das Projekt komplizierter macht.

Stattdessen lieber:
- Demo-Mail A
- Demo-PDF B
- Supplier Note C
- Incident-Hinweis D

Die Inputs sollen deterministisch und reproduzierbar sein.

---

## RAG-/Kontextlogik

Dieses Projekt nutzt **keine externe RAG-Infrastruktur**.

Stattdessen:
- kleine lokale Dokumentbasis unter `src/main/resources/data/documents`
- daraus vorbereitete Chunks in JSON
- einfache app-seitige Retrieval-Logik in Java

### Dokumentarten
Erwarte Dokumente wie:
- `public_policy_*.md`
- `internal_runbook_*.md`
- `supplier_note_*.md`
- `incident_postmortem_*.md`
- `helpdesk_guide_*.md`

### Chunk-Metadaten
Jeder Chunk soll mindestens haben:
- `id`
- `docId`
- `title`
- `text`
- `classification`
- `audience`
- `sourceType`
- `trustLevel`
- `tags`

### Retrieval-Regeln
Retrieval soll:
1. zuerst nach Policy filtern
2. dann nach Rolle / Zweck / Modus filtern
3. dann einfach ranken
4. dann nur wenige Chunks zurückgeben

Keine semantische Magie nötig.
Einfache und robuste Logik ist besser.

---

## Tool- und Workflow-Logik

Die App darf kleine, simulierte Tool-/Workflow-Aktionen haben, damit der Workshop zur Ankündigung passt.

Beispiele:
- `create_handover_draft`
- `show_similar_cases`
- `show_evidence`
- `mark_case_likely_false_positive`
- `set_case_priority_low`
- `route_case_to_finance_queue`

WICHTIG:
- Tools sind **simulierte App-Aktionen**
- keine echte Infrastruktur verändern
- keine externen Systeme ansprechen
- sicherheitsrelevante Tools nur lokal simulieren
- Wirkung z. B. in Memory, JSON oder einfacher lokaler Persistenz modellieren

### Wichtige Regel
Tool-Entscheidungen müssen app-seitig kontrolliert werden.
Das Modell darf höchstens einen Vorschlag liefern.
Die Anwendung entscheidet, ob etwas erlaubt ist.

---

## Absichtlich eingebaute Schwachstellen sind Pflicht

Diese Anwendung muss **mehrere realistische, nicht triviale Schwachstellen im Code enthalten**.

Das ist keine optionale Idee, sondern eine **harte funktionale Anforderung** des Projekts.

Die Schwachstellen müssen so implementiert sein, dass sie:
- zum angekündigten Workshop-Inhalt passen
- durch ein Red Team aktiv ausgenutzt werden können
- vom Blue Team im Code verstanden und behoben werden können
- nicht von Zufall oder reinem Modellverhalten abhängen
- in der Anwendungsschicht liegen, damit sie reproduzierbar funktionieren

### Ganz wichtig
Die Schwachstellen dürfen **nicht** nur theoretisch denkbar sein.
Sie müssen **konkret im Code vorhanden und tatsächlich auslösbar** sein.

Die Anwendung soll sich zunächst wie ein **plausibler, halbwegs ordentlich gebauter AI-Use-Case** anfühlen, der erst bei genauerem Hinsehen kippt.

### Gewünschte Schwachstellen

#### `BUG_HANDOVER_SCOPE`
Im Handover- oder Incident-Modus wird Retrieval oder Kontextauswahl zu weit gefasst.

#### `BUG_EXISTENCE_ORACLE`
Die App verrät über Similar Cases, Titel, Zähler, Tags oder Metadaten die Existenz sensibler interner Vorfälle.

#### `BUG_THREAD_STICKINESS`
Conversation- oder Case-Status bleibt zu stark an Fall oder Session hängen und zu wenig am aktuellen Actor.

#### `BUG_TRUST_MERGE`
Trusted und untrusted Quellen werden bei Retrieval oder Kontextbildung zu stark vermischt.

#### `BUG_TOOL_FASTTRACK`
Eine manipulative oder nur schwach validierte Quelle beeinflusst einen Tool-/Workflow-Pfad zu stark, sodass ein sicherheitsrelevanter Workflow-Schritt zu leicht ausgelöst oder vorbereitet wird.

### Anforderungen an die Umsetzung
Diese Bugs müssen:
- realistisch wirken
- im normalen Anwendungscode leben
- nicht in obskuren Hilfsdateien versteckt sein
- mit klaren Triggern reproduzierbar sein
- per Feature-Flag ein- und ausschaltbar sein
- mit einfachen, nachvollziehbaren Blue-Team-Fixes korrigierbar sein

### Nicht akzeptabel
Nicht akzeptabel sind:
- triviale Demo-Hintertüren
- extrem offensichtliche Admin-Leaks
- lächerlich plumpe Sicherheitsfehler
- Bugs, die nur funktionieren, wenn das Modell sich völlig irrational verhält
- Bugs, die nur durch Zufall oder unzuverlässige Modellreaktionen auslösbar sind


### Dokumentation
- Bitte den Code ausführlich mit JavaDocs versehen
- die absichtlichen Schwachstellen zusätzlich mit einem Kommentare "BUG" versehen

---

## Qualitätsziel

Der Code muss:
- für Workshop-Teilnehmer schnell lesbar sein
- gut benannt sein
- kleine Klassen und kleine Methoden haben
- klar getrennte Verantwortlichkeiten haben
- nachvollziehbar statt clever sein
- mit wenig Vorwissen änderbar sein
- testbar sein
- robust genug für Live-Demos sein

Bevorzuge:
- einfache POJOs / Records
- explizite Logik
- frühe Rückgaben
- kleine Services
- wenige Abhängigkeiten
- klare Typen
- verständliche Controller

Vermeide:
- übertriebene Abstraktionen
- tiefe Vererbung
- komplexe generische Typmagie
- verschachtelte Kontrollflüsse
- implizite Seiteneffekte

---

## Empfohlene Paketstruktur

Bevorzugte Struktur:

- `src/main/java/.../web`
- `src/main/java/.../service`
- `src/main/java/.../policy`
- `src/main/java/.../retrieval`
- `src/main/java/.../llm`
- `src/main/java/.../tools`
- `src/main/java/.../model`
- `src/main/java/.../config`

- `src/main/resources/templates`
- `src/main/resources/static`
- `src/main/resources/data/documents`
- `src/main/resources/data/chunks.json`

### Erwartete Kernklassen
- `BugFlagsProperties`
- `PolicyEngine`
- `RetrievalService`
- `PromptBuilder`
- `ToolPolicyService`
- `MockLlmService`
- `OpenAiChatService`
- `DemoCaseService`
- `IncidentWorkflowService`

---

## UI-Regeln

Die UI muss funktional und leicht verständlich sein.

Benötigt:
- Rollenwahl
- Auswahl des Demo-Falls
- Chat-Feld
- Buttons für:
  - Chat
  - Handover Draft
  - Similar Cases
  - Evidence
  - Workflow Action / Triage Decision
- sichtbare Anzeige:
  - aktuelle Rolle
  - aktueller Fall
  - aktive Bug-Flags
- klare Ergebnisdarstellung
- keine unnötig verspielte Oberfläche

Die UI soll für den Workshop hilfreich sein, nicht beeindrucken.

---

## API-/Web-Regeln

Die App darf klassisch mit Spring MVC gebaut sein.

Bevorzugt:
- `@Controller` für HTML-Seiten
- `@RestController` für JSON-Endpunkte
- kleine Request-/Response-DTOs
- klare Validierung
- einfache Exception-Behandlung

Nicht erwünscht:
- komplexe SPA-Architektur
- aufwendige JavaScript-Build-Pipeline
- unnötige Frontend-Framework-Komplexität

---

## Konfiguration

Konfiguration so einfach wie möglich halten.

Bevorzugt:
- `application.yml`
- `@ConfigurationProperties` für Bug-Flags
- `.env.example`
- sinnvolle Defaults

Wichtig:
- App muss auch ohne echten LLM-Key startbar sein
- Mock-Modus muss vollständig funktionieren

---

## Tests

Wichtige Tests sind Pflicht, aber klein halten.

Erwarte mindestens:
- Policy-Engine-Tests
- Retrieval-Filter-Tests
- Bug-Flag-Verhalten
- Workflow-/Tool-Gate-Tests
- Healthcheck-Smoke-Test

Tests sollen:
- einfach lesbar sein
- den Workshop nicht verkomplizieren
- helfen, Fixes abzusichern

---

## Codespaces- und Browser-Tauglichkeit

Dieses Projekt muss direkt in einer GitHub-Browser-Umgebung gut laufen.

Daher:
- Maven Wrapper verwenden
- `.devcontainer` bereitstellen
- keine lokalen Sondervoraussetzungen außer Java im Container
- keine zusätzlichen Dienste, die separat gestartet werden müssen
- einfacher Startbefehl

Wenn möglich:
- nach dem Öffnen in Codespaces direkt startbar
- Healthcheck verfügbar
- keine nativen Bibliotheken nötig

---

## Arbeitsweise für Coding Agents

Wenn du Änderungen vornimmst:

1. Verstehe zuerst die zentrale Regel **Berechtigung vor Kontext**
2. Prüfe die bestehende Struktur
3. Wähle die einfachste brauchbare Lösung
4. Ändere nur so viel wie nötig
5. Halte Klassen klein
6. Ergänze oder passe Tests an
7. Aktualisiere README, wenn Verhalten sich ändert

### Bevorzugte Reihenfolge bei größeren Änderungen
1. Model / DTOs
2. Policy
3. Retrieval
4. Tool-/Workflow-Kontrolle
5. Controller / Endpoint
6. UI
7. Tests
8. README

---

## Verhalten bei Änderungen durch Coding Agents

Wenn du an bestehendem Code arbeitest, prüfe immer:
- Ist die Schwachstelle weiterhin vorhanden, wenn sie laut Workshop-Design vorhanden sein soll?
- Habe ich versehentlich eine vorbereitete Lücke wegverbessert?
- Ist die Schwachstelle noch realistisch und reproduzierbar?
- Ist sie noch einfach genug, damit Teilnehmer sie verstehen und fixen können?

Verbessere vorbereitete Schwachstellen nur dann, wenn die Aufgabe ausdrücklich ein Blue-Team-Fix ist.
Ansonsten müssen sie erhalten bleiben.

---

## README-Anforderungen

Wenn neue Dateien oder Features ergänzt werden, muss das README die folgenden Dinge klar erklären:
- Projektidee
- Architektur
- zentrale Regel „Berechtigung vor Kontext“
- lokale Nutzung
- Nutzung in Codespaces
- Umgebungsvariablen
- Demo-Rollen
- Demo-Fälle
- Bug-Flags
- Workshop-Hinweise

---

## Abschlussregel

Im Zweifel gilt immer:

**Einfachheit, Verständlichkeit und deterministische Security-Logik sind wichtiger als technische Raffinesse.**