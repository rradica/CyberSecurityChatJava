// SecAssist – Conversational Chat Frontend

const API = '/api';
let sending = false;
let activeCaseId = null;
let activeCaseBriefing = null;
let availableCases = [];

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('chatInput');
    const roleSelect = document.getElementById('roleSelect');
    const caseSelect = document.getElementById('caseSelect');

    // Auto-resize textarea
    input.addEventListener('input', () => {
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 120) + 'px';
    });

    // Enter to send, Shift+Enter for newline
    input.addEventListener('keydown', e => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // Update header badge when role changes
    roleSelect.addEventListener('change', () => {
        const badge = document.getElementById('roleBadge');
        const text = roleSelect.selectedOptions[0]?.textContent || roleSelect.value;
        badge.textContent = text;
    });

    caseSelect.addEventListener('change', async () => {
        const nextCaseId = caseSelect.value;
        if (!nextCaseId) {
            activeCaseId = null;
            activeCaseBriefing = null;
            renderCasePanel();
            newConversation();
            return;
        }
        await loadCase(nextCaseId);
    });

    loadCases().then(() => {
        renderCasePanel();
        showGreeting();
    });
});

// --- Public Functions ---

function sendSuggestion(text) {
    document.getElementById('chatInput').value = text;
    sendMessage();
}

function showGreeting() {
    const container = document.getElementById('messages');
    container.innerHTML = '';
    const div = document.createElement('div');
    div.className = 'chat-msg assistant';

    const label = document.createElement('div');
    label.className = 'label';
    label.textContent = 'SecAssist';
    div.appendChild(label);

    const content = document.createElement('div');
    const greetingText = activeCaseBriefing
        ? `Guten Tag! Der Fall **${activeCaseBriefing.title}** ist geladen.\n\n`
            + `Sie koennen den Fall frei beschreiben oder einen der vorgeschlagenen Einstiege nutzen.`
        : 'Guten Tag! Ich bin SecAssist, Ihr Assistent f\u00fcr Sicherheitsvorf\u00e4lle.\n\n'
            + 'Waehlen Sie bitte zuerst links einen Demo-Fall aus. Danach erhalten Sie eine Lagebeschreibung, sichtbare Artefakte und sinnvolle naechste Schritte.';
    content.innerHTML = simpleMarkdown(greetingText);
    div.appendChild(content);

    if (activeCaseBriefing?.recommendedQuestions?.length) {
        const suggestions = document.createElement('div');
        suggestions.className = 'suggestions';
        for (const question of activeCaseBriefing.recommendedQuestions) {
            const btn = document.createElement('button');
            btn.textContent = question;
            btn.onclick = () => sendSuggestion(question);
            suggestions.appendChild(btn);
        }
        div.appendChild(suggestions);
    }

    container.appendChild(div);
}

function newConversation() {
    document.getElementById('messages').innerHTML = '';
    // User-Notizen auf dem Server zuruecksetzen
    fetch(`${API}/notes`, { method: 'DELETE' }).catch(() => {});
    showGreeting();
}

async function sendMessage() {
    if (sending) return;
    const input = document.getElementById('chatInput');
    const message = input.value.trim();
    if (!message) return;

    if (!activeCaseId) {
        appendMessage('error', 'Bitte wählen Sie zuerst einen Demo-Fall aus.');
        return;
    }

    // Remove suggestion buttons when user sends first message
    const suggestions = document.querySelector('.suggestions');
    if (suggestions) suggestions.remove();

    appendMessage('user', message);
    input.value = '';
    input.style.height = 'auto';

    const body = {
        role: document.getElementById('roleSelect').value,
        message: message,
        caseId: activeCaseId
    };

    sending = true;
    document.getElementById('btnSend').disabled = true;
    const typing = showTyping();

    try {
        const res = await fetch(`${API}/conversation`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        removeTyping(typing);

        if (!res.ok) {
        appendMessage('error', `Es ist ein Fehler aufgetreten (${res.status}). Bitte versuchen Sie es erneut.`);
            return;
        }

        const data = await res.json();
        appendResponse(data);
    } catch (e) {
        removeTyping(typing);
        appendMessage('error', 'Verbindungsfehler. Bitte pr\u00fcfen Sie Ihre Netzwerkverbindung.');
    } finally {
        sending = false;
        document.getElementById('btnSend').disabled = false;
    }
}

async function loadCases() {
    const res = await fetch(`${API}/cases`);
    if (!res.ok) return;
    availableCases = await res.json();
    const select = document.getElementById('caseSelect');
    select.innerHTML = '<option value="">Bitte Fall auswählen</option>';
    for (const demoCase of availableCases) {
        const option = document.createElement('option');
        option.value = demoCase.id;
        option.textContent = demoCase.title;
        select.appendChild(option);
    }
}

async function loadCase(caseId) {
    activeCaseId = caseId;
    const res = await fetch(`${API}/cases/${caseId}/briefing`);
    activeCaseBriefing = res.ok ? await res.json() : null;
    renderCasePanel();
    newConversation();
}

function renderCasePanel() {
    const panel = document.getElementById('casePanel');
    if (!activeCaseBriefing) {
        panel.innerHTML = `
            <div class="case-panel-empty">
                <h2>Fall auswählen</h2>
                <p>Waehlen Sie zuerst einen Demo-Fall aus. SecAssist zeigt dann eine kompakte Lagebeschreibung, Artefakte und moegliche naechste Schritte an.</p>
            </div>`;
        return;
    }

    const facts = (activeCaseBriefing.initialFacts || [])
        .map(fact => `<li>${escHtml(fact)}</li>`)
        .join('');
    const artifacts = (activeCaseBriefing.artifacts || [])
        .map(artifact => `
            <article class="artifact-card">
                <div class="artifact-type">${escHtml(artifact.type)}</div>
                <h4>${escHtml(artifact.title)}</h4>
                <p>${escHtml(artifact.preview)}</p>
            </article>`)
        .join('');

    panel.innerHTML = `
        <div class="case-panel-header">
            <div>
                <div class="case-kicker">Aktiver Fall</div>
                <h2>${escHtml(activeCaseBriefing.title)}</h2>
                <p class="case-summary">${escHtml(activeCaseBriefing.summary)}</p>
            </div>
            <div class="case-department">${escHtml(activeCaseBriefing.department)}</div>
        </div>
        <div class="case-columns">
            <section class="case-section">
                <h3>Bekannte Ausgangsfakten</h3>
                <ul>${facts}</ul>
            </section>
            <section class="case-section">
                <h3>Artefakte</h3>
                <div class="artifact-grid">${artifacts}</div>
            </section>
        </div>
        <section class="case-section case-actions">
            <h3>Was möchten Sie als Nächstes tun?</h3>
            <div class="action-chips">
                <button onclick="runCaseAction('chat')">Fall analysieren</button>
                <button onclick="runCaseAction('evidence')">Quellen anzeigen</button>
                <button onclick="runCaseAction('similar_cases')">Ähnliche Fälle prüfen</button>
                <button onclick="runCaseAction('handover')">Übergabe vorbereiten</button>
                <button onclick="prefillNote()">Notiz hinzufügen</button>
                <button onclick="runCaseAction('workflow')">Triage neu bewerten</button>
            </div>
        </section>`;
}

function prefillNote() {
    const input = document.getElementById('chatInput');
    input.value = 'Notiz: ';
    input.focus();
}

async function runCaseAction(action) {
    if (!activeCaseId || sending) return;

    const role = document.getElementById('roleSelect').value;
    const title = activeCaseBriefing?.title || activeCaseId;
    const presets = {
        chat: {
            label: 'Aktion: Fall analysieren',
            message: `Bitte analysiere den Fall "${title}" und nenne die wichtigsten Risiken sowie nächsten Schritte.`
        },
        evidence: {
            label: 'Aktion: Quellen anzeigen',
            message: ''
        },
        similar_cases: {
            label: 'Aktion: Ähnliche Fälle prüfen',
            message: `Bitte zeige ähnliche Fälle zu "${title}".`
        },
        handover: {
            label: 'Aktion: Übergabe vorbereiten',
            message: `Bitte erstelle einen Übergabe-Entwurf für den Fall "${title}".`
        },
        workflow: {
            label: 'Aktion: Triage neu bewerten',
            message: 'Bitte bewerte das Risiko dieses Falls und schlage eine passende nächste Triage-Aktion vor.'
        }
    };
    const preset = presets[action];
    if (!preset) return;

    const suggestions = document.querySelector('.suggestions');
    if (suggestions) suggestions.remove();
    appendMessage('user', preset.label);

    sending = true;
    document.getElementById('btnSend').disabled = true;
    const typing = showTyping();

    try {
        const res = await fetch(`${API}/chat`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                role,
                caseId: activeCaseId,
                message: preset.message,
                action
            })
        });

        removeTyping(typing);
        if (!res.ok) {
            appendMessage('error', `Es ist ein Fehler aufgetreten (${res.status}). Bitte versuchen Sie es erneut.`);
            return;
        }

        const data = await res.json();
        appendResponse(data);
    } catch (e) {
        removeTyping(typing);
        appendMessage('error', 'Verbindungsfehler. Bitte prüfen Sie Ihre Netzwerkverbindung.');
    } finally {
        sending = false;
        document.getElementById('btnSend').disabled = false;
    }
}

// --- Rendering ---

function appendMessage(type, text) {
    const container = document.getElementById('messages');
    const div = document.createElement('div');
    div.className = `chat-msg ${type}`;

    if (type === 'user') {
        const label = document.createElement('div');
        label.className = 'label';
        label.textContent = 'Sie';
        div.appendChild(label);
    }

    const content = document.createElement('div');
    content.innerHTML = simpleMarkdown(text);
    div.appendChild(content);

    container.appendChild(div);
    scrollToBottom();
}

function appendResponse(data) {
    const container = document.getElementById('messages');
    const div = document.createElement('div');
    div.className = 'chat-msg assistant';

    const label = document.createElement('div');
    label.className = 'label';
    label.textContent = 'SecAssist';
    div.appendChild(label);

    const content = document.createElement('div');
    content.innerHTML = simpleMarkdown(data.reply || 'Ihre Anfrage konnte nicht verarbeitet werden. Bitte versuchen Sie es erneut.');
    div.appendChild(content);

    // Tool Result – compact: status badge + action name
    if (data.toolResult) {
        const tr = document.createElement('div');
        tr.className = 'tool-result status-' + (data.toolResult.status || 'unknown');
        const statusLabel = translateStatus(data.toolResult.status);
        tr.innerHTML = `<span class="tr-status">${escHtml(statusLabel)}</span> `
            + `<strong>${escHtml(data.toolResult.action)}</strong>`;
        div.appendChild(tr);
    }

    // Warnings – only critical indicators
    if (data.warnings && data.warnings.length > 0) {
        const warnings = document.createElement('div');
        warnings.className = 'warnings-list';
        for (const w of data.warnings) {
            const item = document.createElement('div');
            item.className = 'warning-item ' + classifyWarning(w);
            item.textContent = w;
            warnings.appendChild(item);
        }
        div.appendChild(warnings);
    }

    // Security Context – collapsible details
    if (data.securityContext) {
        div.appendChild(renderCollapsibleContext(data.securityContext));
    }

    container.appendChild(div);
    scrollToBottom();
}

function renderCollapsibleContext(ctx) {
    const wrapper = document.createElement('details');
    wrapper.className = 'security-context';

    const summary = document.createElement('summary');
    summary.className = 'sc-toggle';
    summary.textContent = 'Verarbeitungsdetails';
    wrapper.appendChild(summary);

    const body = document.createElement('div');
    body.className = 'sc-body';

    // Header line: Rolle | Aktion | Fall
    const header = document.createElement('div');
    header.className = 'sc-header';
    header.innerHTML =
        `<span class="sc-tag">Rolle: <strong>${escHtml(translateRole(ctx.role))}</strong></span>`
        + `<span class="sc-tag">Aktion: <strong>${escHtml(translateAction(ctx.action))}</strong></span>`
        + (ctx.caseId ? `<span class="sc-tag">Fall: <strong>${escHtml(ctx.caseId)}</strong></span>` : '');
    body.appendChild(header);

    // Retrieved sources with trust indicators
    if (ctx.retrievedSources && ctx.retrievedSources.length > 0) {
        const list = document.createElement('div');
        list.className = 'sc-sources';
        for (const src of ctx.retrievedSources) {
            const item = document.createElement('span');
            item.className = 'sc-source ' + trustClass(src.trustLevel);
            item.title = src.title || src.docId;
            item.innerHTML = `${escHtml(src.docId)} `
                + `<span class="sc-trust">${escHtml(translateTrust(src.trustLevel))}</span>`
                + `<span class="sc-type">${escHtml(src.sourceType)}</span>`;
            list.appendChild(item);
        }
        body.appendChild(list);
    }

    wrapper.appendChild(body);
    return wrapper;
}

function trustClass(level) {
    switch (level) {
        case 'high':      return 'trust-high';
        case 'medium':    return 'trust-medium';
        case 'low':       return 'trust-low';
        case 'untrusted': return 'trust-untrusted';
        default:          return '';
    }
}

function classifyWarning(text) {
    if (text.startsWith('\u2699')) return 'warn-access';    // ⚙ access decision
    if (text.startsWith('\uD83D\uDCCA')) return 'warn-source'; // 📊 source summary
    if (text.startsWith('\u26D4')) return 'warn-denied';    // ⛔ denied
    if (text.startsWith('\u26A0')) return 'warn-case';      // ⚠ case state / note
    return 'warn-info';
}

function translateStatus(status) {
    const map = { executed: 'Ausgef\u00fchrt', rejected: 'Abgelehnt',
                  pending: 'Ausstehend' };
    return map[status] || status;
}

function translateRole(role) {
    const map = { employee: 'Mitarbeiter', security_analyst: 'Security-Analyst',
                  contractor: 'Externer', system: 'System' };
    return map[role] || role;
}

function translateAction(action) {
    const map = { chat: 'Chat', triage: 'Triage', handover: '\u00dcbergabe',
                  similar_cases: '\u00c4hnliche F\u00e4lle', evidence: 'Beweise',
                  workflow: 'Workflow', add_note: 'Notiz hinzuf\u00fcgen' };
    return map[action] || action;
}

function translateTrust(level) {
    const map = { high: 'Vertrauensw\u00fcrdig', medium: 'Mittel',
                  low: 'Niedrig', untrusted: 'Nicht vertrauensw\u00fcrdig' };
    return map[level] || level;
}

function escHtml(text) {
    if (!text) return '';
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function showTyping() {
    const container = document.getElementById('messages');
    const div = document.createElement('div');
    div.className = 'typing-indicator';
    div.innerHTML = '<span></span><span></span><span></span>';
    container.appendChild(div);
    scrollToBottom();
    return div;
}

function removeTyping(el) {
    if (el && el.parentNode) el.parentNode.removeChild(el);
}

function scrollToBottom() {
    const msgs = document.getElementById('messages');
    msgs.scrollTop = msgs.scrollHeight;
}

function detectCaseFromResponse(data) {
    return data?.securityContext?.caseId || activeCaseId;
}

/** Minimal Markdown to HTML */
function simpleMarkdown(text) {
    if (!text) return '';
    return text
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/^## (.+)$/gm, '<h4>$1</h4>')
        .replace(/^### (.+)$/gm, '<h5>$1</h5>')
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/^- (.+)$/gm, '&bull; $1<br>')
        .replace(/\n/g, '<br>');
}
