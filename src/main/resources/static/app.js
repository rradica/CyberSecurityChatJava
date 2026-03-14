// SecAssist Frontend Logic

const API = '/api';

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    loadCases();
    loadBugFlags();

    document.getElementById('roleSelect').addEventListener('change', updateStatusBar);
    document.getElementById('caseSelect').addEventListener('change', updateStatusBar);
    document.getElementById('chatInput').addEventListener('keydown', e => {
        if (e.key === 'Enter') sendChat();
    });
});

async function loadCases() {
    try {
        const res = await fetch(`${API}/cases`);
        const cases = await res.json();
        const sel = document.getElementById('caseSelect');
        sel.innerHTML = '';
        cases.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.id;
            opt.textContent = `${c.title} (${c.severity})`;
            sel.appendChild(opt);
        });
        updateStatusBar();
    } catch (e) {
        console.error('Failed to load cases', e);
    }
}

async function loadBugFlags() {
    try {
        const res = await fetch(`${API}/bug-flags`);
        const flags = await res.json();
        const container = document.getElementById('bugFlagsList');
        container.innerHTML = '';

        const flagNames = {
            handoverScope: 'BUG_HANDOVER_SCOPE',
            existenceOracle: 'BUG_EXISTENCE_ORACLE',
            threadStickiness: 'BUG_THREAD_STICKINESS',
            trustMerge: 'BUG_TRUST_MERGE',
            toolFasttrack: 'BUG_TOOL_FASTTRACK'
        };

        for (const [key, label] of Object.entries(flagNames)) {
            const active = flags[key];
            const span = document.createElement('span');
            span.className = `bug-flag ${active ? 'active' : 'inactive'}`;
            span.textContent = `${label}: ${active ? 'ON' : 'OFF'}`;
            container.appendChild(span);
        }
    } catch (e) {
        console.error('Failed to load bug flags', e);
    }
}

function updateStatusBar() {
    const role = document.getElementById('roleSelect').value;
    const caseId = document.getElementById('caseSelect').value;
    const caseName = document.getElementById('caseSelect').selectedOptions[0]?.textContent || '–';

    document.getElementById('currentRole').textContent = role;
    document.getElementById('currentCase').textContent = caseName;
}

// --- API Calls ---

async function sendChat() {
    const message = document.getElementById('chatInput').value.trim();
    if (!message) return;

    appendMessage('user', message);
    document.getElementById('chatInput').value = '';

    const body = {
        role: document.getElementById('roleSelect').value,
        caseId: document.getElementById('caseSelect').value,
        message: message,
        action: 'chat'
    };

    await callApi(body);
}

async function sendAction(action) {
    appendMessage('system', `Requesting: ${action}...`);

    const body = {
        role: document.getElementById('roleSelect').value,
        caseId: document.getElementById('caseSelect').value,
        message: '',
        action: action
    };

    await callApi(body);
}

async function sendWorkflow(actionName) {
    const label = actionName === 'triage'
        ? 'Requesting triage assessment...'
        : `Workflow action: ${actionName}...`;
    appendMessage('system', label);

    const body = {
        role: document.getElementById('roleSelect').value,
        caseId: document.getElementById('caseSelect').value,
        message: actionName,
        action: 'workflow'
    };

    await callApi(body);
}

async function callApi(body) {
    const chatArea = document.querySelector('.chat-area');
    chatArea.classList.add('loading');

    try {
        const res = await fetch(`${API}/chat`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        if (!res.ok) {
            appendMessage('error', `Error: ${res.status} ${res.statusText}`);
            return;
        }

        const data = await res.json();
        appendResponse(data);
    } catch (e) {
        appendMessage('error', `Request failed: ${e.message}`);
    } finally {
        chatArea.classList.remove('loading');
    }
}

// --- Rendering ---

function appendMessage(type, text) {
    const output = document.getElementById('chatOutput');
    // Remove placeholder
    const placeholder = output.querySelector('.placeholder');
    if (placeholder) placeholder.remove();

    const div = document.createElement('div');
    div.className = `chat-msg ${type}`;

    const label = document.createElement('div');
    label.className = 'label';
    label.textContent = type === 'user' ? 'You' : type === 'system' ? 'System' : 'Error';
    div.appendChild(label);

    const content = document.createElement('div');
    content.innerHTML = simpleMarkdown(text);
    div.appendChild(content);

    output.appendChild(div);
    output.scrollTop = output.scrollHeight;
}

function appendResponse(data) {
    const output = document.getElementById('chatOutput');
    const placeholder = output.querySelector('.placeholder');
    if (placeholder) placeholder.remove();

    const div = document.createElement('div');
    div.className = 'chat-msg assistant';

    const label = document.createElement('div');
    label.className = 'label';
    label.textContent = 'SecAssist';
    div.appendChild(label);

    const content = document.createElement('div');
    content.innerHTML = simpleMarkdown(data.reply || 'No response.');
    div.appendChild(content);

    // Sources
    if (data.sources && data.sources.length > 0) {
        const sources = document.createElement('div');
        sources.className = 'sources';
        sources.textContent = 'Sources: ' + data.sources.join(', ');
        div.appendChild(sources);
    }

    // Tool Result
    if (data.toolResult) {
        const tr = document.createElement('div');
        tr.className = 'tool-result';
        tr.innerHTML = `<strong>${data.toolResult.action}</strong>: ${data.toolResult.description} `
            + `<em>[${data.toolResult.status}]</em>`;
        div.appendChild(tr);
    }

    // Warnings
    if (data.warnings && data.warnings.length > 0) {
        const warnings = document.createElement('div');
        warnings.className = 'warnings';
        warnings.textContent = '⚠ ' + data.warnings.join(' | ');
        div.appendChild(warnings);
    }

    output.appendChild(div);
    output.scrollTop = output.scrollHeight;
}

/** Minimal Markdown → HTML (headers, bold, lists, line breaks). */
function simpleMarkdown(text) {
    if (!text) return '';
    return text
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/^## (.+)$/gm, '<h4>$1</h4>')
        .replace(/^### (.+)$/gm, '<h5>$1</h5>')
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/^- (.+)$/gm, '• $1<br>')
        .replace(/\n/g, '<br>');
}
