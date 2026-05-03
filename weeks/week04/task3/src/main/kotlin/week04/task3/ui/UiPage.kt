package week04.task3.ui

fun buildUiPage(): String = """
<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Week04 Task3 - Scheduled MCP Dashboard</title>
  <style>
    :root {
      --bg: #070f22;
      --card: #0f1a33;
      --card2: #101f3d;
      --border: #253b66;
      --muted: #9db2d9;
      --text: #e8eefc;
      --ok: #19c37d;
      --bad: #f05a7b;
      --accent: #2b8cff;
    }
    * { box-sizing: border-box; }
    body { margin: 0; background: var(--bg); color: var(--text); font-family: Inter, -apple-system, sans-serif; }
    .app { max-width: 1160px; margin: 0 auto; padding: 16px; display: grid; grid-template-columns: 1.25fr 1fr; gap: 12px; }
    .card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 14px; }
    .title { margin: 0 0 10px 0; font-size: 20px; font-weight: 700; }
    .hint { color: var(--muted); font-size: 13px; }
    .row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .price { font-size: 56px; line-height: 1; font-weight: 800; letter-spacing: .4px; margin-top: 8px; }
    .delta { margin-left: 8px; font-weight: 700; font-size: 22px; }
    .ok { color: var(--ok); }
    .bad { color: var(--bad); }
    .pill { display: inline-block; border: 1px solid #365d98; border-radius: 999px; padding: 5px 10px; font-size: 12px; color: #cfe0ff; background: #14305a; }
    button { background: var(--accent); color: white; border: 0; border-radius: 9px; padding: 8px 12px; cursor: pointer; font-weight: 600; }
    button.secondary { background: #1a3157; border: 1px solid #385d94; }
    .status-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 12px; }
    .status-item { background: #0b1530; border: 1px solid #2a4676; border-radius: 10px; padding: 10px; min-height: 66px; }
    .status-label { color: var(--muted); font-size: 12px; }
    .status-value { margin-top: 4px; font-size: 14px; line-height: 1.35; }
    .chart-wrap { margin-top: 14px; background: #081126; border: 1px solid #29456f; border-radius: 12px; padding: 10px; }
    .chart-title { color: var(--muted); font-size: 13px; margin-bottom: 6px; display: flex; justify-content: space-between; gap: 8px; }
    .chart-period { color: #b7c8e8; font-size: 12px; }
    svg { width: 100%; height: 240px; display: block; }
    .line { fill: none; stroke: #2dd1a4; stroke-width: 3; }
    .area { fill: rgba(45, 209, 164, .12); stroke: none; }
    .grid { stroke: #1c3457; stroke-width: 1; }
    .axis-text { fill: #93abd0; font-size: 11px; font-family: Inter, -apple-system, sans-serif; }
    pre { margin: 0; background: #060e23; border: 1px solid #253f6b; border-radius: 10px; padding: 10px; overflow: auto; max-height: 500px; font-size: 12px; white-space: pre-wrap; }
    @media (max-width: 980px) { .app { grid-template-columns: 1fr; } .price { font-size: 44px; } }
  </style>
</head>
<body>
  <div class="app">
    <section class="card">
      <h2 class="title">Bitcoin price <span class="hint">BTC</span></h2>
      <div class="row">
        <span id="summaryPill" class="pill">Ожидание данных...</span>
        <span id="schedulePill" class="pill">interval: -</span>
      </div>
      <div class="row">
        <div id="price" class="price">$-</div>
        <div id="delta" class="delta"></div>
      </div>
      <div class="chart-wrap">
        <div class="chart-title">
          <span>Bitcoin to USD (last points)</span>
          <span id="periodLabel" class="chart-period">period: -</span>
        </div>
        <svg viewBox="0 0 800 240">
          <line class="grid" x1="56" y1="30" x2="780" y2="30"></line>
          <line class="grid" x1="56" y1="110" x2="780" y2="110"></line>
          <line class="grid" x1="56" y1="190" x2="780" y2="190"></line>
          <line class="grid" x1="56" y1="214" x2="780" y2="214"></line>
          <line class="grid" x1="56" y1="30" x2="56" y2="214"></line>
          <polygon id="area" class="area" points=""></polygon>
          <polyline id="line" class="line" points=""></polyline>
          <text id="yMax" class="axis-text" x="8" y="34">$-</text>
          <text id="yMid" class="axis-text" x="8" y="114">$-</text>
          <text id="yMin" class="axis-text" x="8" y="194">$-</text>
          <text id="xStart" class="axis-text" x="56" y="232">start</text>
          <text id="xEnd" class="axis-text" x="720" y="232">end</text>
        </svg>
      </div>
    </section>

    <aside class="card">
      <h3 class="title">Scheduler control</h3>
      <div class="row" style="margin-bottom:10px;">
        <button onclick="runNow()">Run now</button>
        <button class="secondary" onclick="refresh()">Refresh</button>
        <label class="hint"><input type="checkbox" id="auto" checked /> auto (4s)</label>
      </div>
      <div class="status-grid">
        <div class="status-item"><div class="status-label">MCP connected</div><div id="mcpConnected" class="status-value">-</div></div>
        <div class="status-item"><div class="status-label">Last status</div><div id="lastStatus" class="status-value">-</div></div>
        <div class="status-item"><div class="status-label">Last run</div><div id="lastRun" class="status-value">-</div></div>
        <div class="status-item"><div class="status-label">Next run</div><div id="nextRun" class="status-value">-</div></div>
        <div class="status-item"><div class="status-label">Run count</div><div id="runCount" class="status-value">-</div></div>
        <div class="status-item"><div class="status-label">Duration</div><div id="duration" class="status-value">-</div></div>
      </div>
      <h4 style="margin:14px 0 8px 0;">Latest API payload</h4>
      <pre id="debugOut"></pre>
    </aside>
  </div>

<script>
const money = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 });
let latestPayload = null;

function fmtTs(ts) {
  if (!ts) return '-';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  return d.toLocaleString();
}

function renderLine(items) {
  const line = document.getElementById('line');
  const area = document.getElementById('area');
  const yMax = document.getElementById('yMax');
  const yMid = document.getElementById('yMid');
  const yMin = document.getElementById('yMin');
  const xStart = document.getElementById('xStart');
  const xEnd = document.getElementById('xEnd');
  const periodLabel = document.getElementById('periodLabel');

  const chartLeft = 56;
  const chartRight = 780;
  const chartTop = 30;
  const chartBottom = 190;

  if (!items || items.length < 2) {
    line.setAttribute('points', '');
    area.setAttribute('points', '');
    yMax.textContent = '$-';
    yMid.textContent = '$-';
    yMin.textContent = '$-';
    xStart.textContent = 'start';
    xEnd.textContent = 'end';
    periodLabel.textContent = 'period: -';
    return;
  }
  const values = items.map(i => Number(i.price)).filter(v => Number.isFinite(v));
  if (values.length < 2) {
    line.setAttribute('points', '');
    area.setAttribute('points', '');
    return;
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  const spread = Math.max(max - min, 1e-9);
  const pts = values.map(function(v, idx) {
    const x = chartLeft + (idx / (values.length - 1)) * (chartRight - chartLeft);
    const y = chartTop + (1 - ((v - min) / spread)) * (chartBottom - chartTop);
    return x.toFixed(2) + ',' + y.toFixed(2);
  });
  line.setAttribute('points', pts.join(' '));
  area.setAttribute('points', pts[0] + ' ' + pts.join(' ') + ' ' + pts[pts.length - 1].split(',')[0] + ',214');

  yMax.textContent = '$' + money.format(max);
  yMid.textContent = '$' + money.format((max + min) / 2);
  yMin.textContent = '$' + money.format(min);

  const firstTs = items[0] && items[0].timestamp ? new Date(items[0].timestamp) : null;
  const lastTs = items[items.length - 1] && items[items.length - 1].timestamp ? new Date(items[items.length - 1].timestamp) : null;
  xStart.textContent = firstTs && !Number.isNaN(firstTs.getTime()) ? firstTs.toLocaleTimeString() : 'start';
  xEnd.textContent = lastTs && !Number.isNaN(lastTs.getTime()) ? lastTs.toLocaleTimeString() : 'end';
  if (firstTs && lastTs && !Number.isNaN(firstTs.getTime()) && !Number.isNaN(lastTs.getTime())) {
    const minutes = Math.max(1, Math.round((lastTs.getTime() - firstTs.getTime()) / 60000));
    periodLabel.textContent = 'period: last ' + minutes + ' min';
  } else {
    periodLabel.textContent = 'period: -';
  }
}

function render(status, summary, history) {
  latestPayload = { status, summary, history };
  document.getElementById('debugOut').textContent = JSON.stringify(latestPayload, null, 2);

  const state = status.jobState || {};
  document.getElementById('mcpConnected').textContent = status.mcpConnected ? 'yes' : 'no';
  document.getElementById('lastStatus').textContent = state.lastStatus || '-';
  document.getElementById('lastRun').textContent = fmtTs(state.lastRunAt);
  document.getElementById('nextRun').textContent = fmtTs(state.nextRunAt);
  document.getElementById('runCount').textContent = state.runCount ?? '-';
  document.getElementById('duration').textContent = state.lastDurationMs != null ? (state.lastDurationMs + ' ms') : '-';

  document.getElementById('summaryPill').textContent =
    summary && summary.summary ? ('updated: ' + fmtTs(summary.summary.generatedAt)) : 'Ожидание данных...';
  document.getElementById('schedulePill').textContent =
    'interval: ' + Math.max(20, Number(state.intervalSeconds || 20)) + ' sec';

  const snap = summary && summary.summary ? summary.summary : null;
  if (snap) {
    document.getElementById('price').textContent = '$' + money.format(snap.latestPrice);
    const pct = snap.changeFromFirstPct;
    const delta = document.getElementById('delta');
    if (pct == null) {
      delta.textContent = '';
      delta.className = 'delta';
    } else {
      const sign = pct > 0 ? '+' : '';
      delta.textContent = sign + pct.toFixed(2) + '%';
      delta.className = 'delta ' + (pct >= 0 ? 'ok' : 'bad');
    }
  }

  renderLine(history.items || []);
}

async function refresh() {
  const [statusRes, summaryRes, historyRes] = await Promise.all([
    fetch('/api/task3/status'),
    fetch('/api/task3/summary'),
    fetch('/api/task3/history?limit=96'),
  ]);
  const status = await statusRes.json();
  const summary = await summaryRes.json();
  const history = await historyRes.json();
  render(status, summary, history);
}

async function runNow() {
  const btn = event && event.target ? event.target : null;
  if (btn) btn.disabled = true;
  try {
    const res = await fetch('/api/task3/run-now', { method: 'POST' });
    const payload = await res.json();
    if (!payload.ok) {
      alert('Run failed: ' + (payload.error || 'unknown error'));
    }
    await refresh();
  } finally {
    if (btn) btn.disabled = false;
  }
}

setInterval(() => {
  if (!document.getElementById('auto').checked) return;
  refresh();
}, 4000);

refresh();
</script>
</body>
</html>
""".trimIndent()
