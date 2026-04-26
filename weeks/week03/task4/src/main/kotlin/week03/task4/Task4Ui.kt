package week03.task4

fun renderTask4Page(): String {
    return """
        <!doctype html>
        <html lang="ru">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>Week03 Task4 — Invariants</title>
          <style>
            body { margin: 0; font-family: Arial, sans-serif; background: #f5f6fa; color: #1f2430; }
            .app { display: grid; grid-template-columns: 52% 48%; gap: 12px; height: 100vh; padding: 12px; box-sizing: border-box; }
            .panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 10px rgba(20,20,30,0.08); display: flex; flex-direction: column; overflow: hidden; }
            .header { padding: 12px; border-bottom: 1px solid #eceff5; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
            .title { font-size: 14px; font-weight: 700; }
            .grow { flex: 1; }
            select, input, button, textarea { border: 1px solid #cfd6e4; border-radius: 8px; padding: 8px; font-size: 13px; }
            button { cursor: pointer; background: #4d63ff; color: #fff; border: none; }
            button.secondary { background: #6b7280; }
            button.warn { background: #b45309; }
            .chat-log { flex: 1; overflow: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
            .msg { max-width: 92%; padding: 10px; border-radius: 10px; white-space: pre-wrap; line-height: 1.4; }
            .msg.user { align-self: flex-end; background: #e9eeff; }
            .msg.assistant { align-self: flex-start; background: #f1f3f7; }
            .msg.assistant-rich { max-width: 96%; }
            .msg.assistant.assistant-validation-error {
              background: #fce8e8;
              border: 1px solid #e8b4b4;
              box-shadow: 0 1px 6px rgba(180, 40, 40, 0.07);
            }
            .pipeline-row { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-bottom: 10px; }
            .pipeline-arrow { color: #9ca3af; font-size: 13px; user-select: none; }
            .pipeline-step { font-size: 11px; font-weight: 600; padding: 6px 10px; border-radius: 8px; border: 2px solid #cfd6e4; background: #fff; transition: opacity 0.2s, border-color 0.2s, background 0.2s; }
            .pipeline-step.pending { opacity: 0.35; }
            .pipeline-step.revealed.st-passed { border-color: #16a34a; background: #ecfdf5; color: #14532d; }
            .pipeline-step.revealed.st-failed { border-color: #dc2626; background: #fef2f2; color: #7f1d1d; }
            .pipeline-step.revealed.st-skipped { border-color: #9ca3af; background: #f3f4f6; color: #4b5563; }
            .graph-integrity { font-size: 11px; color: #6b7280; margin-bottom: 8px; }
            .graph-integrity.bad { color: #b45309; font-weight: 600; }
            .validation-banner { font-size: 12px; border-left: 4px solid #dc2626; padding: 8px 10px; background: #fef2f2; border-radius: 0 8px 8px 0; margin-bottom: 10px; }
            .validation-title { font-weight: 700; margin-bottom: 6px; color: #991b1b; }
            .validation-banner ul { margin: 4px 0 0 18px; padding: 0; }
            .validation-banner li { margin-bottom: 4px; }
            .reply-body { font-size: 14px; white-space: pre-wrap; line-height: 1.45; border-top: 1px solid #e5e7eb; padding-top: 10px; margin-top: 4px; }
            .composer { display: flex; gap: 8px; padding: 12px; border-top: 1px solid #eceff5; }
            .composer input { flex: 1; }
            .right-body { overflow: auto; padding: 12px; display: flex; flex-direction: column; gap: 12px; }
            .mem-block { border: 1px solid #e7ebf3; border-radius: 8px; padding: 10px; }
            .mem-title { font-size: 13px; font-weight: 700; margin-bottom: 8px; }
            pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-size: 12px; }
            .hint { font-size: 12px; color: #6b7280; line-height: 1.45; }
            #invariantsJson { width: 100%; min-height: 180px; font-family: ui-monospace, monospace; }
            .status { font-size: 12px; color: #6b7280; }
          </style>
        </head>
        <body>
          <div class="app">
            <section class="panel">
              <div class="header">
                <div class="title">Чат</div>
                <div class="grow"></div>
                <button id="resetShort" class="secondary" type="button">Очистить short</button>
                <button id="resetWorking" class="secondary" type="button">Очистить working</button>
              </div>
              <div id="chatLog" class="chat-log"></div>
              <form id="chatForm" class="composer">
                <input id="messageInput" placeholder="Спросите решение; попробуйте конфликт с инвариантом…" autocomplete="off" />
                <button type="submit">Отправить</button>
              </form>
            </section>
            <section class="panel">
              <div class="header">
                <div class="title">Инварианты + память</div>
                <div class="grow"></div>
                <label for="profileSelect" class="status">Профиль</label>
                <select id="profileSelect"></select>
              </div>
              <div class="right-body">
                <div class="mem-block">
                  <div class="mem-title">Инварианты (отдельно от диалога, JSON)</div>
                  <p class="hint">
                    Файл <code>weeks/week03/task4/invariants.json</code>. В system prompt — как жёсткие правила.
                    До LLM: проверка запроса (<code>queryTriggers</code>) и что инварианты попали в prompt;
                    после LLM: проверка ответа (<code>responseTriggers</code>). Поле <code>conflictTriggers</code> — для старых JSON: если новые списки пусты, триггеры действуют и на запрос, и на ответ.
                  </p>
                  <textarea id="invariantsJson" spellcheck="false"></textarea>
                  <div style="display:flex; gap:8px; margin-top:8px; flex-wrap:wrap;">
                    <button id="saveInvariants" type="button">Сохранить</button>
                    <button id="reloadInvariants" class="secondary" type="button">Перечитать</button>
                    <button id="resetInvariants" class="warn" type="button">Демо-набор</button>
                  </div>
                  <p class="hint" id="invStatus"></p>
                </div>
                <div class="mem-block">
                  <div class="mem-title">Краткосрочная память (short_term)</div>
                  <pre id="shortTermView">loading...</pre>
                </div>
                <div class="mem-block">
                  <div class="mem-title">Рабочая память (working_memory)</div>
                  <pre id="workingView">loading...</pre>
                </div>
                <div class="mem-block">
                  <div class="mem-title">Долговременная память (long_term_profile)</div>
                  <pre id="longTermView">loading...</pre>
                </div>
                <div class="mem-block">
                  <div class="mem-title">Метрики последнего хода</div>
                  <pre id="metricsView">-</pre>
                </div>
              </div>
            </section>
          </div>
          <script>
            const chatLog = document.getElementById('chatLog');
            const chatForm = document.getElementById('chatForm');
            const messageInput = document.getElementById('messageInput');
            const shortTermView = document.getElementById('shortTermView');
            const workingView = document.getElementById('workingView');
            const longTermView = document.getElementById('longTermView');
            const metricsView = document.getElementById('metricsView');
            const profileSelect = document.getElementById('profileSelect');
            const resetShort = document.getElementById('resetShort');
            const resetWorking = document.getElementById('resetWorking');
            const invariantsJson = document.getElementById('invariantsJson');
            const saveInvariants = document.getElementById('saveInvariants');
            const reloadInvariants = document.getElementById('reloadInvariants');
            const resetInvariants = document.getElementById('resetInvariants');
            const invStatus = document.getElementById('invStatus');

            function appendMessage(role, content) {
              const el = document.createElement('div');
              el.className = 'msg ' + role;
              el.textContent = content;
              chatLog.appendChild(el);
              chatLog.scrollTop = chatLog.scrollHeight;
            }

            function appendAssistantFromChatResponse(data) {
              const wrap = document.createElement('div');
              wrap.className = 'msg assistant assistant-rich';
              const validationFail =
                (data.validation && data.validation.failed === true) ||
                data.provider === 'invariant-request-check' ||
                data.provider === 'invariant-response-check';
              if (validationFail) {
                wrap.classList.add('assistant-validation-error');
              }

              const graphNote = document.createElement('div');
              graphNote.className = 'graph-integrity' + (data.graph_edges_valid ? '' : ' bad');
              graphNote.textContent = data.graph_edges_valid
                ? 'Граф: шаги идут только по разрешённой цепочке PLANNING → проверка запроса → LLM → проверка ответа → итог (проверка на сервере).'
                : 'Внимание: цепочка не совпала с каноническим графом.';
              wrap.appendChild(graphNote);

              const chain = document.createElement('div');
              chain.className = 'pipeline-row';
              const pipeline = data.pipeline || [];
              const stepEls = [];
              pipeline.forEach((s, idx) => {
                if (idx > 0) {
                  const arr = document.createElement('span');
                  arr.className = 'pipeline-arrow';
                  arr.textContent = '→';
                  chain.appendChild(arr);
                }
                const box = document.createElement('span');
                box.className = 'pipeline-step pending st-' + s.status;
                box.title = (s.id || '') + (s.detail ? ': ' + s.detail : '');
                box.textContent = s.label || s.id;
                stepEls.push({ el: box, status: s.status });
                chain.appendChild(box);
              });
              wrap.appendChild(chain);

              stepEls.forEach((item, idx) => {
                setTimeout(() => {
                  item.el.classList.remove('pending');
                  item.el.classList.add('revealed', 'st-' + item.status);
                }, idx * 220);
              });

              if (data.validation && data.validation.failed) {
                const vb = document.createElement('div');
                vb.className = 'validation-banner';
                const title = document.createElement('div');
                title.className = 'validation-title';
                const gateRu = data.validation.gate === 'REQUEST_VALIDATION'
                  ? 'проверка LLM-запроса (до вызова модели: инжект + текст пользователя)'
                  : 'проверка ответа модели (после генерации)';
                title.textContent = 'Ошибка валидации · ' + gateRu;
                vb.appendChild(title);
                const ul = document.createElement('ul');
                (data.validation.violations || []).forEach((v) => {
                  const li = document.createElement('li');
                  let line = (v.phase || '') + (v.category ? ' [' + v.category + ']' : '');
                  line += (v.invariantId ? ' `' + v.invariantId + '`' : '') + ': ' + (v.ruleSummary || '');
                  if (v.matchedTrigger) line += ' — совпадение: «' + v.matchedTrigger + '»';
                  li.textContent = line;
                  ul.appendChild(li);
                });
                vb.appendChild(ul);
                wrap.appendChild(vb);
              }

              const replyBody = document.createElement('div');
              replyBody.className = 'reply-body';
              replyBody.textContent = data.reply || '';
              wrap.appendChild(replyBody);

              chatLog.appendChild(wrap);
              chatLog.scrollTop = chatLog.scrollHeight;
            }

            function renderHistory(history) {
              chatLog.innerHTML = '';
              history.forEach((m) => appendMessage(m.role, m.content));
            }

            async function loadProfiles() {
              const res = await fetch('/api/profiles');
              const data = await res.json();
              profileSelect.innerHTML = '';
              data.profiles.forEach((p) => {
                const option = document.createElement('option');
                option.value = p.id;
                option.textContent = `${'$'}{p.title} (${'$'}{p.specialization})`;
                if (data.active_profile_id === p.id) option.selected = true;
                profileSelect.appendChild(option);
              });
            }

            async function loadInvariants() {
              const res = await fetch('/api/invariants');
              const data = await res.json();
              invariantsJson.value = JSON.stringify(data, null, 2);
              invStatus.textContent = '';
            }

            async function loadMemory() {
              const res = await fetch('/api/memory');
              const data = await res.json();
              shortTermView.textContent = JSON.stringify(data.short_term, null, 2);
              workingView.textContent = JSON.stringify(data.working_memory, null, 2);
              longTermView.textContent = JSON.stringify(data.long_term_memory, null, 2);
            }

            async function loadHistory() {
              const res = await fetch('/api/history');
              const data = await res.json();
              renderHistory(data.history || []);
            }

            profileSelect.addEventListener('change', async () => {
              await fetch('/api/profiles/select', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ profileId: profileSelect.value })
              });
              await loadMemory();
            });

            resetShort.addEventListener('click', async () => {
              await fetch('/api/reset?layer=short', { method: 'POST' });
              await loadHistory();
              await loadMemory();
            });

            resetWorking.addEventListener('click', async () => {
              await fetch('/api/reset?layer=working', { method: 'POST' });
              await loadMemory();
            });

            saveInvariants.addEventListener('click', async () => {
              try {
                const body = JSON.parse(invariantsJson.value);
                const res = await fetch('/api/invariants', {
                  method: 'PUT',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify(body)
                });
                if (!res.ok) throw new Error(await res.text());
                invStatus.textContent = 'Сохранено.';
              } catch (e) {
                invStatus.textContent = 'Ошибка JSON или сервера: ' + e;
              }
            });

            reloadInvariants.addEventListener('click', loadInvariants);

            resetInvariants.addEventListener('click', async () => {
              const res = await fetch('/api/invariants/reset', { method: 'POST' });
              const data = await res.json();
              invariantsJson.value = JSON.stringify(data, null, 2);
              invStatus.textContent = 'Восстановлен демо-набор.';
            });

            chatForm.addEventListener('submit', async (e) => {
              e.preventDefault();
              const message = messageInput.value.trim();
              if (!message) return;
              appendMessage('user', message);
              messageInput.value = '';

              const res = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message })
              });
              const data = await res.json();
              if (data.pipeline && data.pipeline.length) {
                appendAssistantFromChatResponse(data);
              } else {
                const vf =
                  (data.validation && data.validation.failed === true) ||
                  data.provider === 'invariant-request-check' ||
                  data.provider === 'invariant-response-check';
                if (vf) {
                  const el = document.createElement('div');
                  el.className = 'msg assistant assistant-validation-error';
                  el.textContent = data.reply || '(empty)';
                  chatLog.appendChild(el);
                  chatLog.scrollTop = chatLog.scrollHeight;
                } else {
                  appendMessage('assistant', data.reply || '(empty)');
                }
              }
              metricsView.textContent = JSON.stringify(data.memory || {}, null, 2);
              await loadMemory();
            });

            async function init() {
              await Promise.all([loadProfiles(), loadHistory(), loadMemory(), loadInvariants()]);
            }

            setInterval(loadMemory, 2500);
            init().catch((err) => {
              console.error(err);
              metricsView.textContent = 'Ошибка загрузки UI state';
            });
          </script>
        </body>
        </html>
    """.trimIndent()
}
