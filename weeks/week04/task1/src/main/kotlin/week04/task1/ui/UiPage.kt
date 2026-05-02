package week04.task1.ui

fun buildUiPage(): String = """
<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Week04 MCP Chat Demo</title>
  <style>
    :root {
      --bg: #070f22;
      --card: #0f1a33;
      --card2: #101f3d;
      --border: #253b66;
      --muted: #9db2d9;
      --text: #e8eefc;
      --accent: #2b8cff;
      --ok: #19c37d;
      --warn: #f3c969;
    }
    * { box-sizing: border-box; }
    body { margin: 0; background: var(--bg); color: var(--text); font-family: Inter, -apple-system, sans-serif; }
    .app { max-width: 1320px; margin: 0 auto; padding: 14px; display: grid; grid-template-columns: 1.7fr 1fr; gap: 12px; }
    .app > * { min-width: 0; }
    .card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 12px; }
    .title { font-size: 18px; font-weight: 700; margin: 0 0 10px 0; }
    .hint { color: var(--muted); font-size: 13px; }
    .chat-layout { display: grid; grid-template-rows: auto 1fr auto; min-height: 78vh; gap: 10px; min-width: 0; }
    .chat-log { min-height: 420px; max-height: 62vh; overflow: auto; display: flex; flex-direction: column; gap: 10px; padding-right: 4px; }
    .msg { max-width: 90%; border-radius: 12px; padding: 10px; white-space: pre-wrap; line-height: 1.35; }
    .msg.user { margin-left: auto; background: #184278; border: 1px solid #2e67ad; }
    .msg.assistant { margin-right: auto; background: var(--card2); border: 1px solid #2c4775; }
    .msg.latest { box-shadow: 0 0 0 2px rgba(104, 174, 255, .33), 0 0 16px rgba(64, 154, 255, .24); }
    .msg-meta { margin-top: 6px; font-size: 12px; color: var(--muted); }
    .composer { border-top: 1px solid var(--border); padding-top: 10px; }
    textarea { width: 100%; min-height: 78px; resize: vertical; background: #09152b; border: 1px solid #2f4a7c; border-radius: 10px; color: var(--text); padding: 10px; }
    .row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
    button { background: var(--accent); color: white; border: 0; border-radius: 9px; padding: 8px 12px; cursor: pointer; font-weight: 600; }
    button.secondary { background: #1a3157; border: 1px solid #385d94; }
    .tokens { margin-left: auto; color: var(--muted); font-size: 13px; }
    .quote-card { margin-top: 8px; background: #0b1530; border: 1px solid #305086; border-radius: 10px; padding: 10px; display: flex; align-items: center; gap: 10px; }
    .coin-img { width: 28px; height: 28px; border-radius: 50%; background: #10244a; object-fit: cover; }
    .quote-main { font-size: 16px; font-weight: 700; }
    .quote-sub { color: var(--muted); font-size: 12px; }
    .flow-wrap { min-height: 78vh; display: grid; grid-template-rows: auto auto 1fr auto; gap: 10px; min-width: 0; }
    .flow-chain { background: #0b1530; border: 1px solid #25416e; border-radius: 12px; padding: 10px; overflow: auto; }
    .flow-node { border: 1px solid #37598f; border-radius: 10px; padding: 8px 10px; background: #0f203f; margin: 0 auto; width: 94%; transition: 0.2s ease; }
    .flow-node-title { font-weight: 700; font-size: 13px; }
    .flow-node-sub { color: var(--muted); font-size: 12px; margin-top: 2px; }
    .flow-node-metrics { color: #b7c8e8; font-size: 11px; margin-top: 5px; }
    .flow-node.active { border-color: #6ec1ff; box-shadow: 0 0 0 2px rgba(110, 193, 255, 0.28), 0 0 18px rgba(64, 154, 255, 0.45); transform: translateY(-1px); }
    .flow-node.done { border-color: #2fce88; }
    .flow-arrow { text-align: center; color: #6f95cb; font-size: 16px; margin: 3px 0; }
    .flow-branch { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; align-items: start; }
    .dim { opacity: 0.35; }
    pre { margin: 0; background: #060e23; border: 1px solid #253f6b; border-radius: 10px; padding: 10px; overflow: auto; max-height: 230px; font-size: 12px; white-space: pre-wrap; word-break: break-word; }
    .req-pill { color: #d4e4ff; background: #122b54; border: 1px solid #345f9f; border-radius: 999px; padding: 4px 9px; font-size: 12px; display: inline-block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .last-ref { margin-top: 7px; color: var(--muted); font-size: 12px; }
    .mcp-badge { font-size: 11px; padding: 2px 7px; border-radius: 999px; display: inline-block; margin-left: 6px; }
    .mcp-on { background: rgba(25,195,125,.2); color: #86f1c2; border: 1px solid rgba(25,195,125,.55); }
    .mcp-off { background: rgba(243,201,105,.2); color: #ffe4a5; border: 1px solid rgba(243,201,105,.55); }
    .tool-chip { display: inline-block; font-size: 11px; padding: 2px 8px; border-radius: 999px; margin-right: 5px; margin-top: 3px; background: #1a3157; border: 1px solid #3f6299; color: #d8e7ff; }
    .tool-chip.selected { background: rgba(25,195,125,.16); border-color: rgba(25,195,125,.72); color: #9ff5ce; }
    .modal-backdrop { position: fixed; inset: 0; background: rgba(2,8,20,.68); display: none; align-items: center; justify-content: center; z-index: 30; padding: 18px; }
    .modal-backdrop.open { display: flex; }
    .modal { width: min(880px, 100%); max-height: 82vh; overflow: hidden; background: #0d1a35; border: 1px solid #2f4f86; border-radius: 14px; display: grid; grid-template-rows: auto 1fr auto; }
    .modal-head, .modal-foot { padding: 10px 12px; border-bottom: 1px solid #28416e; }
    .modal-foot { border-top: 1px solid #28416e; border-bottom: 0; display: flex; justify-content: flex-end; }
    .modal-title { font-weight: 700; }
    .modal-body { padding: 10px 12px; overflow: auto; }
    .status-pill { font-size: 11px; border-radius: 999px; padding: 2px 8px; border: 1px solid #3f659e; background: #163766; color: #dbe8ff; margin-left: 6px; }
    @media (max-width: 1060px) { .app { grid-template-columns: 1fr; } .chat-layout, .flow-wrap { min-height: auto; } }
  </style>
</head>
<body>
  <div class="app">
    <section class="card chat-layout">
      <div>
        <h3 class="title">Чат с агентом (RU)</h3>
        <div class="hint">Пишете как в обычный чат. Агент сам решает: нужен MCP или нет.</div>
      </div>
      <div id="chatLog" class="chat-log"></div>
      <div class="composer">
        <textarea id="chatInput" placeholder="Например: Какой сейчас курс BTC и общий тренд рынка?"></textarea>
        <div class="row" style="margin-top:8px;">
          <button onclick="sendChat()">Отправить</button>
          <button class="secondary" onclick="setSample('Какая сейчас цена BTC?')">Пример: цена BTC</button>
          <button class="secondary" onclick="setSample('Сделай обзор трендов крипторынка')">Пример: тренды</button>
          <button class="secondary" onclick="setSample('кто ты и что умеешь?')">Пример: без MCP</button>
          <div class="tokens" id="llmTokens">LLM tokens: -</div>
        </div>
      </div>
    </section>

    <aside class="flow-wrap">
      <div class="card">
        <h3 class="title">Живой пайплайн</h3>
        <div class="hint">Блоки кратко загораются по мере выполнения шага.</div>
      </div>

      <div class="card">
        <div class="flow-arrow">↓</div>
        <div id="reqSnippet" class="req-pill">Ожидание запроса...</div>
        <div id="flowFor" class="last-ref">Диаграмма пока не привязана к сообщению.</div>
      </div>

      <div class="flow-chain">
        <div id="node-client" class="flow-node">
          <div class="flow-node-title">Клиент / UI</div>
          <div class="flow-node-sub">Сообщение пользователя</div>
        </div>
        <div class="flow-arrow">↓</div>
        <div id="node-decision" class="flow-node">
          <div class="flow-node-title">LLM Router: MCP / NO-MCP</div>
          <div class="flow-node-sub" id="decisionText">Ожидание...</div>
          <div id="metrics-decision" class="flow-node-metrics">tokens: -</div>
        </div>
        <div class="flow-arrow">↓</div>
        <div id="node-tools-list" class="flow-node dim">
          <div class="flow-node-title">MCP tools/list</div>
          <div class="flow-node-sub">Обнаружение доступных инструментов</div>
          <div id="metrics-tools-list" class="flow-node-metrics">tokens: 0</div>
        </div>
        <div class="flow-arrow">↓</div>
        <div id="node-schema" class="flow-node dim">
          <div class="flow-node-title">Schema validation</div>
          <div class="flow-node-sub">Проверка args по inputSchema</div>
          <div id="metrics-schema" class="flow-node-metrics">tokens: 0</div>
        </div>
        <div class="flow-arrow">↓</div>
        <div class="flow-branch">
          <div id="branch-mcp">
            <div id="node-mcp" class="flow-node dim">
              <div class="flow-node-title">MCP Client/Server</div>
              <div class="flow-node-sub">Вызов инструмента</div>
              <div id="metrics-mcp" class="flow-node-metrics">tokens: 0</div>
            </div>
            <div class="flow-arrow">↓</div>
            <div id="node-api" class="flow-node dim">
              <div class="flow-node-title">CoinGecko API</div>
              <div class="flow-node-sub">Получение рыночных данных</div>
              <div id="metrics-api" class="flow-node-metrics">tokens: 0</div>
            </div>
          </div>
          <div id="branch-no-mcp">
            <div id="node-no-mcp" class="flow-node dim">
              <div class="flow-node-title">LLM без MCP</div>
              <div class="flow-node-sub">Ответ без внешнего tool-call</div>
              <div id="metrics-no-mcp" class="flow-node-metrics">tokens: 0</div>
            </div>
          </div>
        </div>
        <div class="flow-arrow">↓</div>
        <div id="node-normalize" class="flow-node dim">
          <div class="flow-node-title">Tool result normalization</div>
          <div class="flow-node-sub">Summary + raw payload для LLM</div>
          <div id="metrics-normalize" class="flow-node-metrics">tokens: 0</div>
        </div>
        <div class="flow-arrow">↓</div>
        <div id="node-llm" class="flow-node">
          <div class="flow-node-title">LLM synthesis (response)</div>
          <div class="flow-node-sub">Формирование финального ответа</div>
          <div id="metrics-llm" class="flow-node-metrics">tokens: -</div>
        </div>
        <div class="flow-arrow">↓</div>
        <div id="node-response" class="flow-node">
          <div class="flow-node-title">Ответ в чат</div>
          <div class="flow-node-sub">Пользователь видит результат</div>
          <div id="metrics-total" class="flow-node-metrics">total tokens: -</div>
        </div>
      </div>

      <div class="card">
        <div class="row" style="margin-bottom:8px;">
          <button class="secondary" onclick="loadTools()">Показать MCP tools</button>
          <button class="secondary" onclick="quickBtc()">Быстрый tool-call</button>
          <label class="hint"><input type="checkbox" id="auto" checked /> Автообновление trace</label>
        </div>
        <pre id="traceOut"></pre>
      </div>
    </aside>
  </div>

  <div id="resultModalBackdrop" class="modal-backdrop" onclick="if(event.target===this) closeResultPopup()">
    <div class="modal">
      <div class="modal-head">
        <span id="resultModalTitle" class="modal-title">Результат</span>
      </div>
      <div class="modal-body">
        <pre id="resultModalBody"></pre>
      </div>
      <div class="modal-foot">
        <button onclick="closeResultPopup()">Закрыть</button>
      </div>
    </div>
  </div>

<script>
const chatMessages = [];
let lastRaw = null;
let activeExchangeId = null;
let sendingInProgress = false;

function openResultPopup(title, payload) {
  document.getElementById('resultModalTitle').textContent = title;
  document.getElementById('resultModalBody').textContent =
    typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2);
  document.getElementById('resultModalBackdrop').classList.add('open');
}

function closeResultPopup() {
  document.getElementById('resultModalBackdrop').classList.remove('open');
}

function updateStepMetrics(trace) {
  const d = trace && trace.llmDecision ? trace.llmDecision.totalTokens : 0;
  const s = trace && trace.llmSynthesis ? trace.llmSynthesis.totalTokens : 0;
  const t = trace && trace.llm ? trace.llm.totalTokens : 0;
  document.getElementById('metrics-decision').textContent = `tokens: ${'$'}{d}`;
  document.getElementById('metrics-llm').textContent = `tokens: ${'$'}{s}`;
  document.getElementById('metrics-total').textContent = `total tokens: ${'$'}{t}`;
  document.getElementById('metrics-tools-list').textContent = 'tokens: 0';
  document.getElementById('metrics-schema').textContent = 'tokens: 0';
  document.getElementById('metrics-mcp').textContent = 'tokens: 0';
  document.getElementById('metrics-api').textContent = 'tokens: 0';
  document.getElementById('metrics-no-mcp').textContent = 'tokens: 0';
  document.getElementById('metrics-normalize').textContent = 'tokens: 0';
}

function renderChat() {
  const log = document.getElementById('chatLog');
  log.innerHTML = '';
  for (const m of chatMessages) {
    const box = document.createElement('div');
    box.className = `msg ${'$'}{m.role}`;
    if (activeExchangeId != null && m.exchangeId === activeExchangeId) {
      box.classList.add('latest');
    }
    const text = document.createElement('div');
    text.textContent = m.text;
    box.appendChild(text);

    if (m.quoteCard) {
      const q = m.quoteCard;
      const card = document.createElement('div');
      card.className = 'quote-card';
      const img = document.createElement('img');
      img.className = 'coin-img';
      img.src = q.imageUrl || 'https://assets.coingecko.com/coins/images/1/small/bitcoin.png';
      img.alt = q.symbol || q.coinId;
      const main = document.createElement('div');
      const p = Number(q.price).toLocaleString();
      const ch = q.change24h == null ? '' : ` (${ '$'}{q.change24h > 0 ? '+' : ''}${ '$'}{q.change24h.toFixed(2)}% 24h)`;
      main.innerHTML = `<div class="quote-main">${'$'}{q.symbol || q.coinId} / ${'$'}{q.currency}: ${'$'}${'$'}{p}${'$'}{ch}</div><div class="quote-sub">${'$'}{q.coinName || q.coinId}</div>`;
      card.appendChild(img);
      card.appendChild(main);
      box.appendChild(card);
    }

    if (m.meta) {
      const meta = document.createElement('div');
      meta.className = 'msg-meta';
      meta.innerHTML = m.meta;
      box.appendChild(meta);
    }
    log.appendChild(box);
  }
  log.scrollTop = log.scrollHeight;
}

function setSample(text) {
  document.getElementById('chatInput').value = text;
}

async function sendChat() {
  if (sendingInProgress) return;
  const input = document.getElementById('chatInput');
  const message = input.value.trim();
  if (!message) return;
  sendingInProgress = true;
  const exchangeId = Date.now();
  activeExchangeId = exchangeId;
  chatMessages.push({ role: 'user', text: message, exchangeId });
  const pendingText = '⏳ Думаю над ответом...';
  chatMessages.push({ role: 'assistant', text: pendingText, meta: 'ожидание ответа сервера', exchangeId });
  renderChat();
  input.value = '';

  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30000);
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({message}),
      signal: controller.signal
    });
    clearTimeout(timer);
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`HTTP ${'$'}{res.status}: ${'$'}{errText || 'empty body'}`);
    }
    const data = await res.json();
    lastRaw = data;
  const tools = data.available_tools || [];
  const toolsChips = tools.length
    ? tools.map(t => `<span class="tool-chip ${'$'}{t===data.tool_used ? 'selected' : ''}">${'$'}{t}</span>`).join('')
    : '<span class="tool-chip">none</span>';
    const meta = data.mcp_used
    ? `MCP <span class="mcp-badge mcp-on">YES</span> • decided by: ${'$'}{data.decision_by}<br/>selected tool: <span class="tool-chip selected">${'$'}{data.tool_used}</span><br/>tools/list: ${'$'}{toolsChips}<br/>reason: ${'$'}{data.decision_reason}`
    : `MCP <span class="mcp-badge mcp-off">NO</span> • decided by: ${'$'}{data.decision_by}<br/>tools/list: ${'$'}{toolsChips}<br/>reason: ${'$'}{data.decision_reason}`;
    const idx = chatMessages.findIndex(m => m.exchangeId === exchangeId && m.text === pendingText);
    if (idx >= 0) chatMessages.splice(idx, 1);
    chatMessages.push({ role: 'assistant', text: data.reply, meta, quoteCard: data.quote_card || null, exchangeId });
    renderChat();
    await playFlow(data, message, exchangeId);
    await refreshLatestTrace();
  } catch (err) {
    const idx = chatMessages.findIndex(m => m.exchangeId === exchangeId && m.text === pendingText);
    if (idx >= 0) chatMessages.splice(idx, 1);
    chatMessages.push({
      role: 'assistant',
      text: 'Не удалось получить ответ от сервера. Попробуйте еще раз.',
      meta: `error: ${'$'}{err && err.message ? err.message : String(err)}`,
      exchangeId
    });
    renderChat();
  } finally {
    sendingInProgress = false;
  }
}

async function loadTools() {
  const res = await fetch('/api/mcp/tools');
  const data = await res.json();
  document.getElementById('traceOut').textContent = JSON.stringify(data, null, 2);
  openResultPopup('Результат MCP tools/list', data);
  await refreshLatestTrace();
}

async function quickBtc() {
  const res = await fetch('/api/mcp/tool-call', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({name: 'getCoinPrice', arguments: {symbol: 'BTC', vsCurrency: 'usd'}})
  });
  const data = await res.json();
  document.getElementById('traceOut').textContent = JSON.stringify(data, null, 2);
  openResultPopup('Результат быстрого tool-call', data);
  await refreshLatestTrace();
}

async function refreshLatestTrace() {
  const res = await fetch('/api/trace/latest');
  const wrapped = await res.json();
  const trace = wrapped.trace;
  const toShow = lastRaw ? { latestChat: lastRaw, latestTrace: wrapped } : wrapped;
  document.getElementById('traceOut').textContent = JSON.stringify(toShow, null, 2);
  if (trace && trace.llm) {
    const d = trace.llmDecision;
    const s = trace.llmSynthesis;
    const details = (d || s)
      ? ` [decision: ${'$'}{d ? d.totalTokens : 0}, synthesis: ${'$'}{s ? s.totalTokens : 0}]`
      : '';
    document.getElementById('llmTokens').textContent =
      `LLM tokens: input=${'$'}{trace.llm.inputTokens}, output=${'$'}{trace.llm.outputTokens}, total=${'$'}{trace.llm.totalTokens}${'$'}{details}`;
    updateStepMetrics(trace);
  }
}

function resetFlowVisual() {
  const ids = ['node-client','node-decision','node-tools-list','node-schema','node-mcp','node-api','node-no-mcp','node-normalize','node-llm','node-response'];
  for (const id of ids) {
    const el = document.getElementById(id);
    el.classList.remove('active', 'done');
  }
  document.getElementById('node-mcp').classList.add('dim');
  document.getElementById('node-api').classList.add('dim');
  document.getElementById('node-no-mcp').classList.add('dim');
  document.getElementById('node-tools-list').classList.add('dim');
  document.getElementById('node-schema').classList.add('dim');
  document.getElementById('node-normalize').classList.add('dim');
}

async function flashNode(id, ms = 420) {
  const el = document.getElementById(id);
  el.classList.add('active');
  await new Promise(r => setTimeout(r, ms));
  el.classList.remove('active');
  el.classList.add('done');
}

function snippetOf(text) {
  const s = text.replace(/\s+/g, ' ').trim();
  return s.length > 64 ? `${'$'}{s.slice(0, 64)}...` : s;
}

async function playFlow(data, message, exchangeId) {
  activeExchangeId = exchangeId;
  resetFlowVisual();
  document.getElementById('reqSnippet').textContent = snippetOf(message);
  document.getElementById('decisionText').textContent = data.decision_reason;
  const rid = data.trace && data.trace.requestId ? data.trace.requestId : 'n/a';
  const mcpMark = data.mcp_used ? 'MCP=YES' : 'MCP=NO';
  const toolsList = (data.available_tools || []).join(', ') || 'n/a';
  const selectedTool = data.tool_used || 'none';
  const whoDecided = data.decision_by || 'unknown';
  document.getElementById('flowFor').textContent =
    `Диаграмма для последнего сообщения (${'$'}{mcpMark}, requestId: ${'$'}{rid}, decidedBy: ${'$'}{whoDecided}, selectedTool: ${'$'}{selectedTool}, tools/list: [${'$'}{toolsList}]).`;
  renderChat();
  updateStepMetrics(data.trace);

  await flashNode('node-client', 280);
  await flashNode('node-decision', 340);
  if (data.mcp_used) {
    document.getElementById('node-tools-list').classList.remove('dim');
    await flashNode('node-tools-list', 280);
  }
  document.getElementById('node-schema').classList.remove('dim');
  await flashNode('node-schema', 220);

  if (data.mcp_used) {
    document.getElementById('node-mcp').classList.remove('dim');
    document.getElementById('node-api').classList.remove('dim');
    await flashNode('node-mcp', 360);
    await flashNode('node-api', 300);
  } else {
    document.getElementById('node-no-mcp').classList.remove('dim');
    await flashNode('node-no-mcp', 360);
  }

  document.getElementById('node-normalize').classList.remove('dim');
  await flashNode('node-normalize', 240);
  await flashNode('node-llm', 300);
  await flashNode('node-response', 260);
}

setInterval(async () => {
  if (!document.getElementById('auto').checked) return;
  await refreshLatestTrace();
}, 4000);

loadTools();
chatMessages.push({ role: 'assistant', text: 'Привет! Я могу отвечать по-русски и сам решаю, когда дергать MCP для данных по крипте.' });
renderChat();
resetFlowVisual();
updateStepMetrics(null);

document.getElementById('chatInput').addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendChat();
  }
});
</script>
</body>
</html>
""".trimIndent()
