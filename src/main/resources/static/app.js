// SecAssist – Conversational Chat Frontend

const API = '/api';
let sending = false;
let activeCaseId = null;

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('chatInput');
    const roleSelect = document.getElementById('roleSelect');

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

    // Show initial greeting
    showGreeting();
});

// --- Public Functions ---

function sendSuggestion(text) {
    document.getElementById('chatInput').value = text;
    sendMessage();
}

function showGreeting() {
    const container = document.getElementById('messages');
    const div = document.createElement('div');
    div.className = 'chat-msg assistant';

    const label = document.createElement('div');
    label.className = 'label';
    label.textContent = 'SecAssist';
    div.appendChild(label);

    const content = document.createElement('div');
    content.innerHTML = simpleMarkdown(
        'Guten Tag! Ich bin SecAssist, Ihr Assistent f\u00fcr Sicherheitsvorf\u00e4lle.\n\n'
        + 'Wie kann ich Ihnen helfen? Beschreiben Sie einfach, was passiert ist, '
        + 'oder w\u00e4hlen Sie eines der Beispiele:');
    div.appendChild(content);

    const suggestions = document.createElement('div');
    suggestions.className = 'suggestions';
    suggestions.innerHTML = `
        <button onclick="sendSuggestion('Ich habe eine verd\u00e4chtige Rechnung von einem Lieferanten erhalten, der seine Bankdaten \u00e4ndern will.')">
            Verd\u00e4chtige Lieferantenrechnung
        </button>
        <button onclick="sendSuggestion('Ein Kollege hat eine E-Mail mit einem unbekannten Anhang von einem bekannten Kontakt erhalten.')">
            Seltsamer E-Mail-Anhang
        </button>
        <button onclick="sendSuggestion('Wir haben eine VPN-Passwort-Zur\u00fccksetzung von einem unbekannten Standort erhalten.')">
            Verd\u00e4chtige VPN-Anfrage
        </button>`;
    div.appendChild(suggestions);

    container.appendChild(div);
}

function newConversation() {
    document.getElementById('messages').innerHTML = '';
    activeCaseId = null;
    // User-Notizen auf dem Server zuruecksetzen
    fetch(`${API}/notes`, { method: 'DELETE' }).catch(() => {});
    showGreeting();
}

async function sendMessage() {
    if (sending) return;
    const input = document.getElementById('chatInput');
    const message = input.value.trim();
    if (!message) return;

    // Remove suggestion buttons when user sends first message
    const suggestions = document.querySelector('.suggestions');
    if (suggestions) suggestions.remove();

    appendMessage('user', message);
    input.value = '';
    input.style.height = 'auto';

    const body = {
        role: document.getElementById('roleSelect').value,
        message: message
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
        // Case-ID aus Response ableiten
        if (!activeCaseId) {
            activeCaseId = detectCaseFromResponse(data);
        }
        appendResponse(data);
    } catch (e) {
        removeTyping(typing);
        appendMessage('error', 'Verbindungsfehler. Bitte pr\u00fcfen Sie Ihre Netzwerkverbindung.');
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
    // Heuristik: bekannte Fall-IDs aus Quellnamen ableiten
    const knownCases = [
        'suspicious_supplier_invoice', 'strange_attachment',
        'suspicious_vpn_reset', 'finance_phishing'
    ];
    const text = (data.reply || '') + ' ' + (data.sources || []).join(' ');
    for (const c of knownCases) {
        const keywords = c.split('_');
        if (keywords.some(kw => text.toLowerCase().includes(kw) && kw.length > 3)) {
            return c;
        }
    }
    return null;
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
