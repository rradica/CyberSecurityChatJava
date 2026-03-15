const API = '/api';

const state = {
    sending: false,
    activeCaseId: null,
    activeCaseBriefing: null,
    availableCases: [],
    currentView: 'cases',
    messageCounter: 0,
    messageBuffers: {},
    activeBufferKey: null
};

document.addEventListener('DOMContentLoaded', async () => {
    const input = document.getElementById('chatInput');
    const roleSelect = document.getElementById('roleSelect');

    input.addEventListener('input', autoResizeInput);
    input.addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            sendMessage();
        }
    });

    roleSelect.addEventListener('change', () => {
        persistCurrentMessages();
        updateRolePresentation();
        renderCaseInbox();
        if (state.activeCaseId) {
            renderActiveCaseSummary();
            renderChatHeader();
            renderActionChips();
            restoreMessagesForActiveContext();
        }
    });

    await loadCases();
    updateRolePresentation();
    renderCaseInbox();
    renderActionChips();
    setComposerEnabled(false);
    switchView('cases');
});

function resetApp() {
    state.sending = false;
    state.activeCaseId = null;
    state.activeCaseBriefing = null;
    state.currentView = 'cases';
    state.messageCounter = 0;
    state.messageBuffers = {};
    state.activeBufferKey = null;

    const input = document.getElementById('chatInput');
    input.value = '';
    autoResizeInput();

    fetch(`${API}/notes`, { method: 'DELETE' }).catch(() => {});

    renderCaseInbox();
    renderActiveCaseSummary();
    renderChatHeader();
    renderActionChips();

    const messages = document.getElementById('messages');
    messages.innerHTML = '';

    switchView('cases');
}

function autoResizeInput() {
    const input = document.getElementById('chatInput');
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, 120)}px`;
}

function updateRolePresentation() {
    const roleSelect = document.getElementById('roleSelect');
    const role = roleSelect.value;
    const label = roleSelect.selectedOptions[0]?.textContent || role;

    document.getElementById('roleBadge').textContent = label;

    const queueCopy = {
        employee: {
            title: 'Ihnen zugewiesene Faelle',
            description: 'Die folgenden Meldungen liegen aktuell in Ihrer Bearbeitung oder warten auf Ihre erste Einschaetzung.'
        },
        security_analyst: {
            title: 'Aktive Faelle im Security Desk',
            description: 'Sie sehen den aktuellen Eingang fuer die Security-Triage und koennen direkt in die Fallbearbeitung wechseln.'
        },
        contractor: {
            title: 'Fuer Sie freigegebene Faelle',
            description: 'Diese Faelle wurden fuer Ihre aktuelle Mitarbeit freigegeben. Sie koennen dazu externe Status- und Verifikationsrueckmeldungen in den freigegebenen Fallkanal einstellen.'
        }
    };

    const copy = queueCopy[role] || queueCopy.employee;
    document.getElementById('queueTitle').textContent = copy.title;
    document.getElementById('queueDescription').textContent = copy.description;
}

async function loadCases() {
    const response = await fetch(`${API}/cases`);
    if (!response.ok) {
        state.availableCases = [];
        return;
    }
    state.availableCases = await response.json();
}

function renderCaseInbox() {
    renderQueueMetrics();

    const list = document.getElementById('caseList');
    list.innerHTML = '';

    if (!state.availableCases.length) {
        list.innerHTML = '<div class="summary-card"><h2>Keine Faelle verfuegbar</h2><p>Momentan konnten keine Demo-Faelle geladen werden.</p></div>';
        return;
    }

    for (const demoCase of state.availableCases) {
        const card = document.createElement('article');
        card.className = `case-card ${demoCase.id === state.activeCaseId ? 'active' : ''}`;

        const active = demoCase.id === state.activeCaseId;
        const statusText = active ? 'In Bearbeitung' : 'Neu zugewiesen';
        const buttonText = active ? 'Bearbeitung fortsetzen' : 'Fall oeffnen';

        card.innerHTML = `
            <div class="case-card-header">
                <div>
                    <div class="case-title">${escHtml(demoCase.title)}</div>
                    <p class="case-description">${escHtml(demoCase.description)}</p>
                </div>
                <span class="severity-badge severity-${escHtml(demoCase.severity)}">${escHtml(translateSeverity(demoCase.severity))}</span>
            </div>

            <div class="case-card-meta">
                <span class="chip">${escHtml(translateCaseType(demoCase.type))}</span>
                <span class="status-chip">${statusText}</span>
            </div>

            <div class="case-card-footer">
                <div class="message-caption">Fall-ID: ${escHtml(demoCase.id)}</div>
                <button class="btn-primary" type="button">${buttonText}</button>
            </div>
        `;

        card.querySelector('button').addEventListener('click', () => openCase(demoCase.id));
        list.appendChild(card);
    }
}

function renderQueueMetrics() {
    const metrics = document.getElementById('queueMetrics');
    const total = state.availableCases.length;
    const highPriority = state.availableCases.filter(item => item.severity === 'high' || item.severity === 'critical').length;
    const active = state.activeCaseId ? 1 : 0;

    metrics.innerHTML = `
        <div class="metric-card">
            <div class="metric-label">Offene Faelle</div>
            <div class="metric-value">${total}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Priorisierte Bearbeitung</div>
            <div class="metric-value">${highPriority}</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Aktiver Arbeitsplatz</div>
            <div class="metric-value">${active ? '1 Fall' : 'Bereit'}</div>
        </div>
    `;
}

async function openCase(caseId) {
    persistCurrentMessages();

    if (caseId === state.activeCaseId && state.activeCaseBriefing) {
        switchView('chat');
        restoreMessagesForActiveContext();
        return;
    }

    const response = await fetch(`${API}/cases/${caseId}/briefing`);
    state.activeCaseBriefing = response.ok ? await response.json() : null;
    state.activeCaseId = caseId;

    renderCaseInbox();
    renderActiveCaseSummary();
    renderChatHeader();
    renderActionChips();
    switchView('chat');

    const targetKey = getMessageBufferKey();
    if (hasBufferedMessages(targetKey)) {
        restoreMessagesForActiveContext();
    } else {
        newConversation();
    }
}

function returnToInbox() {
    switchView('cases');
}

function switchView(viewName) {
    state.currentView = viewName;
    document.getElementById('caseView').classList.toggle('active', viewName === 'cases');
    document.getElementById('chatView').classList.toggle('active', viewName === 'chat');
}

function renderActiveCaseSummary() {
    const container = document.getElementById('activeCaseSummary');
    if (!state.activeCaseBriefing) {
        container.innerHTML = `
            <div class="summary-empty">
                <h2>Kein Fall geoeffnet</h2>
                <p>Waehlen Sie zuerst einen zugewiesenen Fall aus Ihrer Uebersicht aus.</p>
            </div>`;
        return;
    }

    const facts = (state.activeCaseBriefing.initialFacts || [])
        .map(fact => `<li>${escHtml(fact)}</li>`)
        .join('');

    const artifacts = (state.activeCaseBriefing.artifacts || [])
        .map(artifact => `
            <div class="artifact-item">
                <span class="chip">${escHtml(formatArtifactType(artifact.type))}</span>
                <strong>${escHtml(artifact.title)}</strong>
                <p>${escHtml(artifact.preview)}</p>
            </div>`)
        .join('');

    container.innerHTML = `
        <div class="section-headline">Aktiver Fall</div>
        <h2>${escHtml(state.activeCaseBriefing.title)}</h2>
        <p class="summary-lead">${escHtml(state.activeCaseBriefing.summary)}</p>
        <div class="case-card-meta">
            <span class="chip">${escHtml(state.activeCaseBriefing.department)}</span>
            <span class="severity-badge severity-${escHtml(getActiveCaseSeverity())}">${escHtml(translateSeverity(getActiveCaseSeverity()))}</span>
        </div>

        <div class="section-headline" style="margin-top:16px;">Bekannte Fakten</div>
        <ul class="facts-list">${facts}</ul>

        <div class="section-headline" style="margin-top:16px;">Sichtbare Artefakte</div>
        <div class="artifact-list">${artifacts}</div>
    `;
}

function renderActionChips() {
    const container = document.getElementById('actionChips');
    const role = document.getElementById('roleSelect').value;
    const actions = role === 'contractor'
        ? [{ key: 'note', label: 'Partner-Update senden' }]
        : [
            { key: 'chat', label: 'Fall analysieren' },
            { key: 'evidence', label: 'Quellen anzeigen' },
            { key: 'similar_cases', label: 'Aehnliche Faelle' },
            { key: 'handover', label: 'Uebergabe vorbereiten' },
            { key: 'workflow', label: 'Triage neu bewerten' },
            { key: 'note', label: 'Rueckmeldung erfassen' }
        ];

    container.innerHTML = '';
    for (const action of actions) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = action.label;
        button.disabled = !state.activeCaseId || state.sending;
        button.addEventListener('click', () => {
            if (action.key === 'note') {
                prefillNote();
                return;
            }
            runCaseAction(action.key);
        });
        container.appendChild(button);
    }
}

function renderChatHeader() {
    const title = document.getElementById('chatTitle');
    const subtitle = document.getElementById('chatSubtitle');
    const badge = document.getElementById('caseSensitivityBadge');
    const input = document.getElementById('chatInput');
    const role = document.getElementById('roleSelect').value;

    if (!state.activeCaseBriefing) {
        title.textContent = 'Bitte zuerst einen Fall auswaehlen';
        subtitle.textContent = 'Der Chat wird geoeffnet, sobald ein zugewiesener Fall gestartet wurde.';
        badge.className = 'sensitivity-badge sensitivity-neutral';
        badge.textContent = 'Keine Auswahl';
        setComposerEnabled(false);
        return;
    }

    title.textContent = state.activeCaseBriefing.title;
    subtitle.textContent = role === 'contractor'
        ? `${state.activeCaseBriefing.department} · Externer Rueckkanal fuer Partner-Updates`
        : `${state.activeCaseBriefing.department} · Chat zur laufenden Fallbearbeitung`;
    badge.className = `sensitivity-badge sensitivity-${severityToSensitivity(getActiveCaseSeverity())}`;
    badge.textContent = `Fallstufe: ${translateSeverity(getActiveCaseSeverity())}`;
    input.placeholder = role === 'contractor'
        ? 'Partner-Update zum ausgewaehlten Fall senden...'
        : 'Frage zum ausgewaehlten Fall stellen oder Einschaetzung anfordern...';
    setComposerEnabled(true);
}

function setComposerEnabled(enabled) {
    document.getElementById('chatInput').disabled = !enabled;
    document.getElementById('btnSend').disabled = !enabled || state.sending;
    renderActionChips();
}

function newConversation(options = {}) {
    const { preserveNotes = false } = options;
    clearActiveBuffer();
    const messages = document.getElementById('messages');
    messages.innerHTML = '';
    if (!preserveNotes) {
        fetch(`${API}/notes`, { method: 'DELETE' }).catch(() => {});
    }
    if (state.activeCaseBriefing) {
        showGreeting();
    }
}

function showGreeting() {
    const questionButtons = (state.activeCaseBriefing?.recommendedQuestions || []).map(question => {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = question;
        button.addEventListener('click', () => sendSuggestion(question));
        return button;
    });

    appendMessageCard({
        type: 'assistant',
        sender: 'SecAssist',
        text: `Der Fall **${state.activeCaseBriefing.title}** ist zur Bearbeitung geoeffnet. Ich unterstuetze Sie bei Einordnung, Evidenzsichtung und der Vorbereitung naechster Schritte.`,
        sensitivity: deriveGreetingSensitivity(),
        meta: buildMetaInfo({
            role: translateRole(document.getElementById('roleSelect').value),
            action: 'Fallstart',
            caseId: state.activeCaseId,
            note: 'Initiale UI-Systemnachricht'
        }),
        extraNodes: questionButtons.length ? [renderSuggestions(questionButtons)] : []
    });
}

function sendSuggestion(text) {
    const input = document.getElementById('chatInput');
    input.value = text;
    autoResizeInput();
    sendMessage();
}

async function sendMessage() {
    if (state.sending || !state.activeCaseId) {
        return;
    }

    const input = document.getElementById('chatInput');
    const message = input.value.trim();
    if (!message) {
        return;
    }

    appendMessageCard({
        type: 'user',
        sender: 'Sie',
        text: message,
        sensitivity: { level: 'neutral', label: 'Benutzereingabe' },
        meta: buildMetaInfo({
            role: translateRole(document.getElementById('roleSelect').value),
            action: 'Benutzernachricht',
            caseId: state.activeCaseId,
            note: 'Freitextanfrage im Fallchat'
        })
    });

    input.value = '';
    autoResizeInput();

    state.sending = true;
    setComposerEnabled(true);
    const typingNode = showTypingIndicator();

    try {
        const response = await fetch(`${API}/conversation`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                role: document.getElementById('roleSelect').value,
                message,
                caseId: state.activeCaseId
            })
        });

        removeTypingIndicator(typingNode);

        if (!response.ok) {
            appendError('Es ist ein Fehler aufgetreten. Bitte versuchen Sie es erneut.');
            return;
        }

        const data = await response.json();
        appendAssistantResponse(data);
    } catch (_error) {
        removeTypingIndicator(typingNode);
        appendError('Verbindungsfehler. Bitte pruefen Sie Ihre Netzwerkverbindung.');
    } finally {
        state.sending = false;
        setComposerEnabled(true);
    }
}

function prefillNote() {
    if (!state.activeCaseId) {
        return;
    }
    const input = document.getElementById('chatInput');
    input.value = document.getElementById('roleSelect').value === 'contractor'
        ? 'Partner-Update: '
        : 'Rueckmeldung: ';
    autoResizeInput();
    input.focus();
}

async function runCaseAction(action) {
    if (!state.activeCaseId || state.sending) {
        return;
    }

    const title = state.activeCaseBriefing?.title || state.activeCaseId;
    const presetMap = {
        chat: {
            label: 'Schnellaktion: Fall analysieren',
            message: `Bitte analysiere den Fall "${title}" und nenne die wichtigsten Risiken sowie naechste Schritte.`
        },
        evidence: {
            label: 'Schnellaktion: Quellen anzeigen',
            message: ''
        },
        similar_cases: {
            label: 'Schnellaktion: Aehnliche Faelle',
            message: `Bitte zeige aehnliche Faelle zu "${title}".`
        },
        handover: {
            label: 'Schnellaktion: Uebergabe vorbereiten',
            message: `Bitte erstelle einen Uebergabe-Entwurf fuer den Fall "${title}".`
        },
        workflow: {
            label: 'Schnellaktion: Triage neu bewerten',
            message: 'Bitte bewerte das Risiko dieses Falls und schlage die passende naechste Triage-Aktion vor.'
        }
    };

    const preset = presetMap[action];
    if (!preset) {
        return;
    }

    appendMessageCard({
        type: 'user',
        sender: 'Sie',
        text: preset.label,
        sensitivity: { level: 'neutral', label: 'Arbeitsaktion' },
        meta: buildMetaInfo({
            role: translateRole(document.getElementById('roleSelect').value),
            action: translateAction(action),
            caseId: state.activeCaseId,
            note: 'Ausgeloest ueber Schnellaktion'
        })
    });

    state.sending = true;
    setComposerEnabled(true);
    const typingNode = showTypingIndicator();

    try {
        const response = await fetch(`${API}/chat`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                role: document.getElementById('roleSelect').value,
                caseId: state.activeCaseId,
                message: preset.message,
                action
            })
        });

        removeTypingIndicator(typingNode);

        if (!response.ok) {
            appendError('Die Aktion konnte nicht verarbeitet werden. Bitte versuchen Sie es erneut.');
            return;
        }

        const data = await response.json();
        appendAssistantResponse(data);
    } catch (_error) {
        removeTypingIndicator(typingNode);
        appendError('Verbindungsfehler. Bitte pruefen Sie Ihre Netzwerkverbindung.');
    } finally {
        state.sending = false;
        setComposerEnabled(true);
    }
}

function appendAssistantResponse(data) {
    const sensitivity = deriveResponseSensitivity(data);
    const preNodes = [];
    const extraNodes = [];

    if (data.warnings && data.warnings.length) {
        preNodes.push(renderWarnings(data.warnings));
    }

    if (data.toolResult) {
        extraNodes.push(renderToolResult(data.toolResult));
    }

    const autoExpand = hasViolationWarning(data.warnings);

    appendMessageCard({
        type: 'assistant',
        sender: 'SecAssist',
        text: data.reply || 'Es konnte keine Antwort erzeugt werden.',
        sensitivity,
        meta: buildAssistantMeta(data, sensitivity),
        preNodes,
        extraNodes,
        autoExpandMeta: autoExpand
    });

    if (state.activeCaseId) {
        refreshCaseStateBanner();
    }
}

function appendError(text) {
    appendMessageCard({
        type: 'error',
        sender: 'System',
        text,
        sensitivity: { level: 'high', label: 'Hinweis' },
        meta: buildMetaInfo({
            role: translateRole(document.getElementById('roleSelect').value),
            action: 'Fehlerhinweis',
            caseId: state.activeCaseId || '—',
            note: 'Clientseitige Fehlermeldung'
        })
    });
}

function appendMessageCard({ type, sender, text, sensitivity, meta, preNodes = [], extraNodes = [], autoExpandMeta = false }) {
    const container = document.getElementById('messages');
    const article = document.createElement('article');
    article.className = `message-card ${type} sensitivity-${sensitivity.level}`;
    article.dataset.messageId = `msg-${++state.messageCounter}`;

    const header = document.createElement('div');
    header.className = 'message-card-header';
    header.innerHTML = `
        <div>
            <div class="message-author">${escHtml(sender)}</div>
            <div class="message-timestamp">${formatTimestamp(new Date())}</div>
        </div>
        <span class="sensitivity-badge sensitivity-${escHtml(sensitivity.level)}">${escHtml(sensitivity.label)}</span>
    `;
    article.appendChild(header);

    for (const node of preNodes) {
        article.appendChild(node);
    }

    const body = document.createElement('div');
    body.className = 'message-body';
    body.innerHTML = simpleMarkdown(text);
    article.appendChild(body);

    for (const node of extraNodes) {
        article.appendChild(node);
    }

    const footer = document.createElement('div');
    footer.className = 'message-card-footer';

    const caption = document.createElement('div');
    caption.className = 'message-caption';
    caption.textContent = type === 'assistant'
        ? 'Arbeitsansicht fuer den aktuellen Fall'
        : 'Nachricht im aktiven Fallkontext';
    footer.appendChild(caption);

    const metaButton = document.createElement('button');
    metaButton.type = 'button';
    metaButton.className = 'meta-toggle';
    metaButton.textContent = 'Meta-Infos einblenden';
    footer.appendChild(metaButton);
    article.appendChild(footer);

    const metaPanel = renderMetaPanel(meta);
    if (!autoExpandMeta) {
        metaPanel.classList.add('hidden');
    } else {
        metaPanel.classList.remove('hidden');
        metaButton.textContent = 'Meta-Infos ausblenden';
    }
    article.appendChild(metaPanel);

    metaButton.addEventListener('click', () => {
        const hidden = metaPanel.classList.toggle('hidden');
        metaButton.textContent = hidden ? 'Meta-Infos einblenden' : 'Meta-Infos ausblenden';
    });

    container.appendChild(article);
    scrollToBottom();
}

function getMessageBufferKey() {
    const role = document.getElementById('roleSelect')?.value || 'employee';
    const caseId = state.activeCaseId || 'no-case';
    return `${role}::${caseId}`;
}

function ensureMessageBuffer(key) {
    if (!state.messageBuffers[key]) {
        state.messageBuffers[key] = document.createElement('div');
    }
    return state.messageBuffers[key];
}

function persistCurrentMessages() {
    const messages = document.getElementById('messages');
    if (!messages) {
        return;
    }

    const key = state.activeBufferKey || getMessageBufferKey();
    const buffer = ensureMessageBuffer(key);
    while (messages.firstChild) {
        buffer.appendChild(messages.firstChild);
    }
}

function restoreMessagesForActiveContext() {
    const messages = document.getElementById('messages');
    if (!messages) {
        return;
    }

    const key = getMessageBufferKey();
    state.activeBufferKey = key;
    const buffer = ensureMessageBuffer(key);
    messages.innerHTML = '';

    while (buffer.firstChild) {
        messages.appendChild(buffer.firstChild);
    }

    if (!messages.childElementCount && state.activeCaseBriefing) {
        showGreeting();
    } else {
        scrollToBottom();
    }
}

function hasBufferedMessages(key) {
    const buffer = state.messageBuffers[key];
    return !!buffer && buffer.childElementCount > 0;
}

function clearActiveBuffer() {
    const key = getMessageBufferKey();
    state.messageBuffers[key] = document.createElement('div');
    state.activeBufferKey = key;
}

function renderMetaPanel(meta) {
    const wrapper = document.createElement('div');
    wrapper.className = 'message-meta';

    const grid = document.createElement('div');
    grid.className = 'meta-grid';

    for (const row of meta.rows) {
        const item = document.createElement('div');
        item.className = 'meta-row';
        item.innerHTML = `<strong>${escHtml(row.label)}</strong><span>${escHtml(row.value)}</span>`;
        grid.appendChild(item);
    }

    wrapper.appendChild(grid);

    if (meta.sources && meta.sources.length) {
        const title = document.createElement('div');
        title.className = 'section-headline';
        title.style.marginTop = '14px';
        title.textContent = 'Quellen im Verarbeitungskontext';
        wrapper.appendChild(title);

        const sourceList = document.createElement('div');
        sourceList.className = 'source-meta-list';

        for (const source of meta.sources) {
            const item = document.createElement('div');
            item.className = 'source-meta-item';
            item.innerHTML = `
                <div class="source-meta-header">
                    <div>
                        <div class="source-meta-title">${escHtml(source.title || source.docId)}</div>
                        <div class="source-meta-subtitle">${escHtml(source.docId)} · ${escHtml(source.sourceType || 'Quelle')}</div>
                    </div>
                    <div class="case-card-meta">
                        <span class="classification-pill classification-${escHtml(normalizeClassification(source.classification))}">${escHtml(translateClassification(source.classification))}</span>
                        <span class="trust-pill trust-${escHtml(source.trustLevel || 'medium')}">${escHtml(translateTrust(source.trustLevel))}</span>
                    </div>
                </div>
            `;
            sourceList.appendChild(item);
        }

        wrapper.appendChild(sourceList);
    }

    return wrapper;
}

function buildAssistantMeta(data, sensitivity) {
    const ctx = data.securityContext || {};
    const sources = Array.isArray(ctx.retrievedSources) ? ctx.retrievedSources : [];

    return {
        rows: [
            { label: 'Rolle', value: translateRole(ctx.role || document.getElementById('roleSelect').value) },
            { label: 'Modus', value: translateAction(ctx.action || 'chat') },
            { label: 'Fall', value: ctx.caseId || state.activeCaseId || '—' },
            { label: 'Sensitivitaet', value: sensitivity.label },
            { label: 'Quellen', value: sources.length ? `${sources.length} eingebunden` : 'Keine Metadaten hinterlegt' }
        ],
        sources
    };
}

function buildMetaInfo({ role, action, caseId, note }) {
    return {
        rows: [
            { label: 'Rolle', value: role || '—' },
            { label: 'Modus', value: action || '—' },
            { label: 'Fall', value: caseId || '—' },
            { label: 'Hinweis', value: note || '—' }
        ],
        sources: []
    };
}

function deriveGreetingSensitivity() {
    const activeSeverity = getActiveCaseSeverity();
    return {
        level: severityToSensitivity(activeSeverity),
        label: `Fallkontext: ${translateSeverity(activeSeverity)}`
    };
}

function deriveResponseSensitivity(data) {
    const classifications = (data.securityContext?.retrievedSources || []).map(source => normalizeClassification(source.classification));

    if (classifications.includes('confidential')) {
        return { level: 'high', label: 'Vertraulich' };
    }
    if (classifications.includes('internal')) {
        return { level: 'medium', label: 'Intern' };
    }
    if ((data.warnings || []).length) {
        return { level: 'medium', label: 'Pruefhinweis' };
    }
    return { level: 'low', label: 'Oeffentlich' };
}

function renderToolResult(toolResult) {
    const node = document.createElement('div');
    const status = toolResult.status || 'unknown';
    node.className = `tool-result status-${status}`;
    node.innerHTML = `
        <span class="status-chip">${escHtml(translateStatus(status))}</span>
        <strong>${escHtml(toolResult.action || 'Aktion')}</strong>
    `;
    return node;
}

function renderWarnings(warnings) {
    const wrapper = document.createElement('div');
    wrapper.className = 'warning-list';

    for (const warning of warnings) {
        const item = document.createElement('div');
        item.className = `warning-item ${classifyWarning(warning)}`;
        item.textContent = warning;
        wrapper.appendChild(item);
    }

    return wrapper;
}

function renderSuggestions(buttons) {
    const wrapper = document.createElement('div');
    wrapper.className = 'suggestions';
    buttons.forEach(button => wrapper.appendChild(button));
    return wrapper;
}

function showTypingIndicator() {
    const container = document.getElementById('messages');
    const article = document.createElement('article');
    article.className = 'message-card assistant typing sensitivity-neutral';
    article.innerHTML = `
        <div class="message-card-header">
            <div>
                <div class="message-author">SecAssist</div>
                <div class="message-timestamp">${formatTimestamp(new Date())}</div>
            </div>
            <span class="sensitivity-badge sensitivity-neutral">Bearbeitung</span>
        </div>
        <div class="message-body">
            <div class="typing-indicator"><span></span><span></span><span></span></div>
        </div>
    `;
    container.appendChild(article);
    scrollToBottom();
    return article;
}

function removeTypingIndicator(node) {
    if (node && node.parentNode) {
        node.parentNode.removeChild(node);
    }
}

function scrollToBottom() {
    const container = document.getElementById('messages');
    container.scrollTop = container.scrollHeight;
}

function getActiveCaseSeverity() {
    return state.availableCases.find(item => item.id === state.activeCaseId)?.severity || 'medium';
}

function classifyWarning(text) {
    if (!text) return 'warning-info';
    if (text.includes('\uD83D\uDD34') || text.includes('KLASSIFIZIERUNGSVERLETZUNG') || text.includes('RECHTE-ESKALATION')) return 'warning-violation';
    if (text.includes('\uD83D\uDFE0') || text.includes('METADATEN-LEAK') || text.includes('TRUST-GRENZE')) return 'warning-anomaly';
    if (text.includes('Zugriff verweigert') || text.includes('\u26D4')) return 'warning-danger';
    if (text.includes('\u26A0') || text.includes('UNTERDRUECKT') || text.includes('NIEDRIG')) return 'warning-warning';
    return 'warning-info';
}

function hasViolationWarning(warnings) {
    if (!warnings || !warnings.length) return false;
    return warnings.some(w => classifyWarning(w) === 'warning-violation');
}

function translateStatus(status) {
    return {
        executed: 'Ausgefuehrt',
        rejected: 'Abgelehnt',
        pending: 'Ausstehend'
    }[status] || status;
}

function translateRole(role) {
    return {
        employee: 'Mitarbeiter',
        security_analyst: 'Security-Analyst',
        contractor: 'Externer',
        system: 'System'
    }[role] || role;
}

function translateAction(action) {
    return {
        chat: 'Chat',
        triage: 'Triage',
        handover: 'Uebergabe',
        similar_cases: 'Aehnliche Faelle',
        evidence: 'Quellenansicht',
        workflow: 'Workflow',
        add_note: 'Rueckmeldung'
    }[action] || action;
}

function translateSeverity(severity) {
    return {
        low: 'Niedrig',
        medium: 'Mittel',
        high: 'Hoch',
        critical: 'Kritisch'
    }[severity] || severity;
}

function translateCaseType(type) {
    return {
        phishing: 'Phishing',
        malware: 'Malware',
        account_compromise: 'Account-Risiko',
        supply_chain: 'Supply Chain',
        insider_threat: 'Insider-Risiko',
        restricted_match: 'Eingeschraenkte Treffer'
    }[type] || type;
}

function translateTrust(level) {
    return {
        high: 'Vertrauenswuerdig',
        medium: 'Mittel',
        low: 'Niedrig',
        untrusted: 'Untrusted'
    }[level] || (level || 'Unbekannt');
}

function translateClassification(level) {
    return {
        public: 'Oeffentlich',
        internal: 'Intern',
        confidential: 'Vertraulich'
    }[normalizeClassification(level)] || (level || 'Unbekannt');
}

function normalizeClassification(level) {
    return ['public', 'internal', 'confidential'].includes(level) ? level : 'public';
}

function severityToSensitivity(severity) {
    return {
        low: 'low',
        medium: 'medium',
        high: 'high',
        critical: 'high'
    }[severity] || 'neutral';
}

function formatArtifactType(type) {
    return {
        email: 'E-Mail',
        note: 'Notiz',
        policy_excerpt: 'Richtlinie',
        attachment_hint: 'Anhang-Hinweis'
    }[type] || type;
}

function formatTimestamp(date) {
    return new Intl.DateTimeFormat('de-DE', {
        hour: '2-digit',
        minute: '2-digit'
    }).format(date);
}

async function refreshCaseStateBanner() {
    const banner = document.getElementById('caseStateBanner');
    if (!banner || !state.activeCaseId) {
        if (banner) banner.classList.add('hidden');
        return;
    }
    try {
        const response = await fetch(`${API}/cases/${state.activeCaseId}/state`);
        if (!response.ok) { banner.classList.add('hidden'); return; }
        const caseState = await response.json();
        const effects = [];
        if (caseState.escalationSuppressed) effects.push('Eskalation UNTERDRUECKT');
        if (caseState.priorityLow) effects.push('Prioritaet auf NIEDRIG gesetzt');
        if (caseState.routedToFinance) effects.push('An Finanzabteilung weitergeleitet (Security entfernt)');
        if (caseState.trustNoteAttached) effects.push('Lieferanten-Vertrauensnotiz angehaengt');
        if (!effects.length) { banner.classList.add('hidden'); return; }
        banner.innerHTML = `<strong>FALLZUSTAND VERAENDERT</strong><div class="case-state-effects">${effects.map(e => `<span>${escHtml(e)}</span>`).join('')}</div>`;
        banner.classList.remove('hidden');
    } catch (_) {
        banner.classList.add('hidden');
    }
}

function escHtml(text) {
    if (text === null || text === undefined) {
        return '';
    }
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

function simpleMarkdown(text) {
    if (!text) {
        return '';
    }

    return escHtml(text)
        .replace(/^## (.+)$/gm, '<h4>$1</h4>')
        .replace(/^### (.+)$/gm, '<h5>$1</h5>')
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/^- (.+)$/gm, '&bull; $1<br>')
        .replace(/\n/g, '<br>');
}