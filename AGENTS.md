# AGENTS.md

## Projektzweck

Dieses Repository enthaelt eine kleine, bewusst ueberschaubare Workshop-Anwendung namens **SecAssist**.

SecAssist ist ein interner Security-/Incident-Triage-Chatbot fuer eine Red-Team-vs-Blue-Team-Uebung zum Thema **CyberSecurity im AI-Umfeld**.

Die Anwendung muss:
- als **Spring-Boot-Anwendung** umgesetzt werden
- **Spring AI** fuer Chat- und Tool-Integration verwenden
- direkt in **GitHub Codespaces im Browser** lauffaehig sein
- einfach verstaendlichen Code haben
- bewusst klein und wartbar bleiben
- gezielt einige realistische Schwachstellen enthalten, die im Workshop gefunden und gefixt werden sollen
- fuer Teilnehmer schnell lesbar und aenderbar sein
- fuer einen Workshop robust und reproduzierbar funktionieren

---

## Harte Workshop-Anforderung: vorbereitete Schwachstellen muessen im Code vorhanden sein

Dieses Repository ist **bewusst keine vollstaendig gehaertete Anwendung**.

Die Anwendung ist eine **Workshop-App fuer eine Red-Team-vs-Blue-Team-Uebung**.
Daher ist es eine **zentrale Anforderung**, dass mehrere **realistische, nicht triviale, absichtlich eingebaute Schwachstellen** bereits **im Code vorhanden** sind.

Diese Schwachstellen sind **kein Versehen** und **kein technischer Schuldenrest**, sondern ein **Pflichtbestandteil des Designs**.

WICHTIG:
- Entferne diese Schwachstellen nicht automatisch.
- "Verbessere" sie nicht unaufgefordert.
- Baue nicht versehentlich eine vollstaendig sichere Standardloesung.
- Implementiere die Schwachstellen so, dass sie im Workshop von Teilnehmern gefunden, verstanden und behoben werden koennen.
- Die Schwachstellen muessen **zur Ankuendigung des Workshops passen**:
  - RAG-/Kontext-bezogene Risiken
  - untrusted content
  - Trust-Boundary-Fehler
  - ueberprivilegierter Kontext
  - unsichere Tool-/Workflow-Ausloesung
  - Security-Probleme, die **nicht** durch gutes Prompting allein geloest werden

Die Schwachstellen duerfen **nicht trivial oder cartoonhaft** sein.

Nicht erwuenscht sind zum Beispiel:
- offensichtliche Backdoors
- plumpe Demo-Leaks ohne plausible Produktgeschichte
- laecherliche Debug-Endpunkte
- reine „ignore all instructions"-Beispiele als Hauptmechanik
- komplett fehlende Autorisierung ohne fachlichen Hintergrund

Erwuenscht sind stattdessen:
- plausible Produktfehler
- inkonsistente Policy-Durchsetzung
- fehlerhafte Trust-Zusammenfuehrung
- uebermaechtige Incident-/Handover-Pfade
- Existence-Oracles
- zu schwach abgesicherte Tool-/Action-Freigaben
- Fehler, die aus Hilfsbereitschaft, UX, operativer Effizienz oder Produktkompromissen entstanden wirken

Die Schwachstellen muessen:
- fachlich glaubwuerdig sein
- technisch nachvollziehbar sein
- im Code wirklich vorhanden sein
- reproduzierbar ausloesbar sein
- unabhaengig voneinander fixbar sein
- vom Blue Team mit ueberschaubarem Aufwand fixbar sein

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
- Das LLM darf **nicht** ueber Berechtigung entscheiden.
- Das LLM darf **nicht** ueber Quellenfreigabe entscheiden.
- Das LLM darf **nicht** ueber Policy-Grenzen entscheiden.
- Sicherheitskritische Entscheidungen muessen in deterministischem Anwendungscode liegen.

Wenn du Code erzeugst oder aenderst, pruefe immer:
**Wird hier erst Policy geprueft und danach Kontext gebaut?**
Wenn nicht, ist die Loesung falsch.

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
- sehr einfache statische Frontend-Dateien unter `src/main/resources/static`
- REST-Endpunkte fuer das Frontend unter `@RestController`
- JUnit 5
- moeglichst wenige zusaetzliche Bibliotheken

### Verwende nicht
- Spring WebFlux
- Kafka
- Messaging
- Docker-Pflicht im Laufzeitpfad
- externe Datenbank
- Vector-DB
- LangChain4j
- komplizierte Security-Framework-Setups
- unnoetige Architekturmuster

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
- einfache, klar lesbare Prompt-Erzeugung
- optional Advisors nur dann, wenn sie die Architektur klarer machen

### Aktueller Ist-Stand
- Das Repository nutzt aktuell primaer `ChatClient` ueber `OpenAiChatService`
- Tool- und Workflow-Entscheidungen liegen im Anwendungscode, nicht in Spring-AI-Tool-Callbacks
- Die App verwendet derzeit keinen verdrahteten Mock-LLM-Pfad

### Nicht erwuenscht
- komplexe Advisor-Ketten ohne Mehrwert
- tiefe Spring-AI-Magie, die Teilnehmer schwer verstehen
- automatische Retrieval-/Vector-Store-Integration
- komplizierte mehrstufige Agent-Schleifen

### Sehr wichtig
Das LLM soll primaer:
- Antworttext formulieren
- strukturierte Empfehlungen erzeugen
- einen Tool-Vorschlag liefern

Die App soll:
- Rollen pruefen
- Quellen auswaehlen
- Tool-Freigaben pruefen
- Bugs deterministisch ausloesen
- Incident-Effekte simulieren

---

## Ziel der Anwendung

Die App soll einen kleinen Security-Chatbot bereitstellen, mit dem Benutzer:
- einen Demo-Fall auswaehlen
- Fragen stellen
- einen **handover draft for security** erzeugen
- **similar cases** anfordern
- **evidence / sources** anzeigen
- interne Fallnotizen erfassen
- eingehende externe Rueckmeldungen in einen Fall uebernehmen
- einen sicherheitsrelevanten Workflow-Schritt vorbereiten oder ausloesen koennen

Es handelt sich um eine **Workshop-App**, nicht um ein Produktivsystem.

---

## Wichtigste fachliche Story

Der Chatbot verarbeitet Security-relevante Anfragen rund um:
- verdaechtige Lieferanten-E-Mails
- Anhaenge
- Phishing-Meldungen
- Incident-Handover
- aehnliche fruehere Faelle
- operative Security-Hinweise

Die App soll zeigen:
- wie untrusted content gefaehrlich werden kann
- wie RAG-/Kontextfehler entstehen
- wie Tool- oder Workflow-Aktionen unsicher werden
- warum Prompting allein keine Security Boundary ist

---

## Realistischer Incident-Case

Die App muss mindestens **einen realistischen Tool-/Workflow-Case** enthalten, der zur Workshop-Beschreibung passt.

### Zielbild
Eine **manipulierte, aber plausibel wirkende Quelle** beeinflusst das System so, dass ein **legitimer Workflow-Schritt** falsch ausgeloest wird und dadurch ein **Security Incident** entsteht.

### Bevorzugter Case
Nicht direkte Allowlist-Freigabe, sondern ein realistischerer Triage-Fall:

- Der Bot analysiert einen Fall zu einer verdaechtigen Lieferanten-Mail.
- Der Bot kann einen Fall klassifizieren oder weiterleiten.
- Eine untrusted Quelle beeinflusst die Triage zu stark.
- Die App stoeßt daraufhin einen legitimen Workflow-Schritt an, z. B.:
  - `mark_case_likely_false_positive`
  - `set_case_priority_low`
  - `route_case_to_finance_queue`
  - `attach_supplier_trust_note`

Der Incident entsteht im zweiten Schritt:
- Der Fall wird falsch behandelt
- Die Security-Eskalation unterbleibt oder verzoegert sich
- Ein Folgefall rutscht spaeter leichter durch

Das soll realistischer wirken als eine plumpe „direkte Freigabe".

---

## Rollenmodell

Die App verwendet nur wenige Demo-Rollen:
- `contractor`
- `employee`
- `security_analyst`

Keine echte Authentisierung noetig.
Eine einfache Rollenauswahl im UI reicht.

WICHTIG:
Jeder Request muss mit der **aktuell gewaehlten Rolle** ausgewertet werden.
Keine impliziten Rechte nur aus Falltyp oder Konversation ableiten.

---

## Demo-Faelle

Die Anwendung soll vorbereitete Demo-Faelle enthalten, zum Beispiel:
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
4. dann nur wenige Chunks zurueckgeben

Keine semantische Magie noetig.
Einfache und robuste Logik ist besser.

---

## Tool- und Workflow-Logik

Die App darf kleine, simulierte Tool-/Workflow-Aktionen haben, damit der Workshop zur Ankuendigung passt.

Beispiele:
- `create_handover_draft`
- `show_similar_cases`
- `show_evidence`
- `mark_case_likely_false_positive`
- `set_case_priority_low`
- `route_case_to_finance_queue`

WICHTIG:
- Tools sind **simulierte App-Aktionen**
- keine echte Infrastruktur veraendern
- keine externen Systeme ansprechen
- sicherheitsrelevante Tools nur lokal simulieren
- Wirkung z. B. in Memory, JSON oder einfacher lokaler Persistenz modellieren

### Wichtige Regel
Tool-Entscheidungen muessen app-seitig kontrolliert werden.
Das Modell darf hoechstens einen Vorschlag liefern.
Die Anwendung entscheidet, ob etwas erlaubt ist.

---

## Absichtlich eingebaute Schwachstellen sind Pflicht

Diese Anwendung muss **mehrere realistische, nicht triviale Schwachstellen im Code enthalten**.

Das ist keine optionale Idee, sondern eine **harte funktionale Anforderung** des Projekts.

Die Schwachstellen muessen so implementiert sein, dass sie:
- zum angekuendigten Workshop-Inhalt passen
- durch ein Red Team aktiv ausgenutzt werden koennen
- vom Blue Team im Code verstanden und behoben werden koennen
- nicht von Zufall oder reinem Modellverhalten abhaengen
- in der Anwendungsschicht liegen, damit sie reproduzierbar funktionieren

### Ganz wichtig
Die Schwachstellen duerfen **nicht** nur theoretisch denkbar sein.
Sie muessen **konkret im Code vorhanden und tatsaechlich ausloesbar** sein.

Die Anwendung soll sich zunaechst wie ein **plausibler, halbwegs ordentlich gebauter AI-Use-Case** anfuehlen, der erst bei genauerem Hinsehen kippt.

### Gewuenschte Schwachstellen

#### `BUG_HANDOVER_SCOPE`
Im Handover-Modus verwendet der Policy-Filter fest kodierte Security-Team-Berechtigungen statt der Berechtigungen der aktuellen Rolle.

#### `BUG_EXISTENCE_ORACLE`
Bei gezielten Suchanfragen ueber die Konversations-API leaken aggregierte Metadaten interner Vorfaelle (Anzahl, Kategorie, Schweregrad) auch an Nicht-Analysten.

#### `BUG_TRUST_MERGE`
Trusted und untrusted Quellen werden bei Retrieval oder Kontextbildung zu stark vermischt.

#### `BUG_TOOL_FASTTRACK`
Eine manipulative oder nur schwach validierte Quelle beeinflusst einen Tool-/Workflow-Pfad zu stark, sodass ein sicherheitsrelevanter Workflow-Schritt zu leicht ausgeloest oder vorbereitet wird.

#### `BUG_RAG_POISONING`
User-Notizen werden als vertrauenswuerdige interne Dokumente gespeichert und koennen so gefaelschte Einschaetzungen in die Wissensdatenbank einschleusen.

### Anforderungen an die Umsetzung
Diese Bugs muessen:
- realistisch wirken
- im normalen Anwendungscode leben
- nicht in obskuren Hilfsdateien versteckt sein
- mit klaren Triggern reproduzierbar sein
- unabhaengig voneinander fixbar sein (kein Bug-Fix darf automatisch einen anderen Bug beheben)
- mit einfachen, nachvollziehbaren Blue-Team-Fixes korrigierbar sein
- **keine Bug-Flags** verwenden (die Teilnehmer sollen die Schwachstellen im Code finden und fixen, nicht per Flag abschalten)

### Nicht akzeptabel
Nicht akzeptabel sind:
- triviale Demo-Hintertueren
- extrem offensichtliche Admin-Leaks
- laecherlich plumpe Sicherheitsfehler
- Bugs, die nur funktionieren, wenn das Modell sich voellig irrational verhaelt
- Bugs, die nur durch Zufall oder unzuverlaessige Modellreaktionen ausloesbar sind


### Dokumentation
- Bitte den Code ausfuehrlich mit JavaDocs versehen
- die absichtlichen Schwachstellen mit Kommentaren markieren, die das Verhalten beschreiben (z.B. `SCHWACHSTELLE [BUG_NAME]`) – aber KEINE Bug-Flags oder Toggles im Code

---

## Qualitaetsziel

Der Code muss:
- fuer Workshop-Teilnehmer schnell lesbar sein
- gut benannt sein
- kleine Klassen und kleine Methoden haben
- klar getrennte Verantwortlichkeiten haben
- nachvollziehbar statt clever sein
- mit wenig Vorwissen aenderbar sein
- testbar sein
- robust genug fuer Live-Demos sein

Bevorzuge:
- einfache POJOs / Records
- explizite Logik
- fruehe Rueckgaben
- kleine Services
- wenige Abhaengigkeiten
- klare Typen
- verstaendliche Controller

Vermeide:
- uebertriebene Abstraktionen
- tiefe Vererbung
- komplexe generische Typmagie
- verschachtelte Kontrollfluesse
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
- `PolicyEngine`
- `RetrievalService`
- `PromptBuilder`
- `ToolPolicyService`
- `OpenAiChatService`
- `DemoCaseService`
- `IncidentWorkflowService`
- `ChatOrchestrator`
- `ConversationService`
- `ApiController`

---

## UI-Regeln

Die UI muss funktional und leicht verstaendlich sein.

Benoetigt:
- Rollenwahl
- Auswahl des Demo-Falls
- Chat-Feld
- Buttons fuer:
  - Chat
  - Handover Draft
  - Similar Cases
  - Evidence
  - Workflow Action / Triage Decision
- sichtbare Anzeige:
  - aktuelle Rolle
  - aktueller Fall
- klare Ergebnisdarstellung
- keine unnoetig verspielte Oberflaeche

Die UI soll fuer den Workshop hilfreich sein, nicht beeindrucken.

---

## API-/Web-Regeln

Die App darf klassisch mit Spring MVC gebaut sein.

Bevorzugt:
- `@RestController` fuer JSON-Endpunkte
- kleine Request-/Response-DTOs
- klare Validierung
- einfache Exception-Behandlung
- statisches Frontend unter `src/main/resources/static`

Aktueller Ist-Stand:
- die Anwendung nutzt derzeit ein statisches Browser-Frontend
- die UI spricht primaer JSON-Endpunkte wie `/api/chat` und `/api/conversation` an
- zusaetzlich existieren Endpunkte fuer Fallbriefings, Notizen, externe Rueckmeldungen und Healthchecks

Nicht erwuenscht:
- komplexe SPA-Architektur
- aufwendige JavaScript-Build-Pipeline
- unnoetige Frontend-Framework-Komplexitaet

---

## Konfiguration

Konfiguration so einfach wie moeglich halten.

Bevorzugt:
- `application.yml`
- `@ConfigurationProperties` fuer Anwendungskonfiguration
- `.env.example`
- sinnvolle Defaults

Wichtig:
- die Anwendung startet mit Default-Konfiguration aus `application.yml`
- fuer echte LLM-gestuetzte Requests ist aktuell ein gueltiger `OPENAI_API_KEY` noetig
- ein Mock-Modus ist im aktuellen Stand **nicht** verdrahtet und sollte nicht als bereits vorhanden beschrieben werden

---

## Tests

Wichtige Tests sind Pflicht, aber klein halten.

Erwarte mindestens:
- Policy-Engine-Tests
- Retrieval-Filter-Tests
- Schwachstellen-Verhalten
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
- keine zusaetzlichen Dienste, die separat gestartet werden muessen
- einfacher Startbefehl

Wenn moeglich:
- nach dem Oeffnen in Codespaces direkt startbar
- Healthcheck verfuegbar
- keine nativen Bibliotheken noetig

---

## Arbeitsweise fuer Coding Agents

Wenn du Aenderungen vornimmst:

1. Verstehe zuerst die zentrale Regel **Berechtigung vor Kontext**
2. Pruefe die bestehende Struktur
3. Waehle die einfachste brauchbare Loesung
4. Aendere nur so viel wie noetig
5. Halte Klassen klein
6. Ergaenze oder passe Tests an
7. Aktualisiere README, wenn Verhalten sich aendert

Wenn sich Workshop-Material aendert, aktualisiere bei Bedarf auch:
- `exploits/README.md`
- `exploits/BLUE_TEAM_LEITFADEN.md`
- `exploits/RED_TEAM_LEITFADEN.md`

### Bevorzugte Reihenfolge bei groeßeren Aenderungen
1. Model / DTOs
2. Policy
3. Retrieval
4. Tool-/Workflow-Kontrolle
5. Controller / Endpoint
6. UI
7. Tests
8. README

---

## Verhalten bei Aenderungen durch Coding Agents

Wenn du an bestehendem Code arbeitest, pruefe immer:
- Ist die Schwachstelle weiterhin vorhanden, wenn sie laut Workshop-Design vorhanden sein soll?
- Habe ich versehentlich eine vorbereitete Luecke wegverbessert?
- Ist die Schwachstelle noch realistisch und reproduzierbar?
- Ist sie noch einfach genug, damit Teilnehmer sie verstehen und fixen koennen?

Verbessere vorbereitete Schwachstellen nur dann, wenn die Aufgabe ausdruecklich ein Blue-Team-Fix ist.
Ansonsten muessen sie erhalten bleiben.

---

## README-Anforderungen

Wenn neue Dateien oder Features ergaenzt werden, muss das README die folgenden Dinge klar erklaeren:
- Projektidee
- Architektur
- zentrale Regel „Berechtigung vor Kontext"
- lokale Nutzung
- Nutzung in Codespaces
- Umgebungsvariablen
- Demo-Rollen
- Demo-Faelle
- Schwachstellen-Uebersicht
- Workshop-Hinweise

Wenn sich die Workshop-Unterlagen veraendern, sollten ausserdem die Exploit-Leitfaeden
und Team-Dokumente unter `exploits/` konsistent zum aktuellen App-Stand bleiben.

---

## Abschlussregel

Im Zweifel gilt immer:

**Einfachheit, Verstaendlichkeit und deterministische Security-Logik sind wichtiger als technische Raffinesse.**
