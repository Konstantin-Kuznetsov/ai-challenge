package week03.task3

fun renderTask3Page(): String {
    return """
        <!doctype html>
        <html lang="ru">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>Week03 Task3 Task State Machine</title>
          <style>
            body { margin: 0; font-family: Arial, sans-serif; background: #f5f6fa; color: #1f2430; }
            .app { display: grid; grid-template-columns: 58% 42%; gap: 12px; height: 100vh; padding: 12px; box-sizing: border-box; }
            .panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 10px rgba(20,20,30,0.08); display: flex; flex-direction: column; overflow: hidden; }
            .header { padding: 10px 12px; border-bottom: 1px solid #eceff5; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
            .title { font-size: 14px; font-weight: 700; }
            .grow { flex: 1; }
            select, input, button { border: 1px solid #cfd6e4; border-radius: 8px; padding: 7px; font-size: 13px; }
            button { cursor: pointer; background: #4d63ff; color: #fff; border: none; }
            button.secondary { background: #6b7280; }
            .chat-log { flex: 1; overflow: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
            .msg { max-width: 90%; padding: 10px; border-radius: 10px; white-space: pre-wrap; line-height: 1.35; }
            .msg.user { align-self: flex-end; background: #e9eeff; }
            .msg.assistant { align-self: flex-start; background: #f1f3f7; }
            .msg.stage-planning { background: #f3e8ff; border-left: 4px solid #9333ea; }
            .msg.stage-execution { background: #dbeafe; border-left: 4px solid #2563eb; }
            .msg.stage-validation { background: #fef3c7; border-left: 4px solid #d97706; }
            .msg.stage-done { background: #dcfce7; border-left: 4px solid #16a34a; }
            .msg .stage-badge { display: inline-block; font-size: 11px; padding: 2px 6px; border-radius: 999px; background: rgba(15,23,42,0.12); margin-bottom: 6px; }
            .composer { display: flex; gap: 8px; padding: 12px; border-top: 1px solid #eceff5; }
            .composer input { flex: 1; }
            .hint { padding: 0 12px 8px; font-size: 12px; color: #616b82; }
            .state-body { overflow: auto; padding: 12px; display: grid; gap: 10px; }
            .block { border: 1px solid #e7ebf3; border-radius: 8px; padding: 10px; }
            .subtitle { font-weight: 700; margin-bottom: 6px; font-size: 13px; }
            pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-size: 12px; }
            .pill { padding: 4px 8px; border-radius: 999px; font-size: 12px; background: #eef2ff; }
          </style>
        </head>
        <body>
          <div class="app">
            <section class="panel">
              <div class="header">
                <div class="title">Task3 Chat</div>
                <div class="grow"></div>
                <button id="pauseBtn" class="secondary" type="button">Pause</button>
                <button id="resumeBtn" class="secondary" type="button">Resume</button>
                <button id="resetWorkingBtn" class="secondary" type="button">Reset working</button>
                <button id="resetShortBtn" class="secondary" type="button">Reset short</button>
              </div>
              <div id="chatLog" class="chat-log"></div>
              <div class="hint">Команды в чате: <code>дальше</code>, <code>назад</code>, <code>пауза</code>, <code>продолжай</code>, <code>заверши</code></div>
              <form id="chatForm" class="composer">
                <input id="messageInput" placeholder="Введите сообщение..." autocomplete="off" />
                <button type="submit">Отправить</button>
              </form>
            </section>
            <section class="panel">
              <div class="header">
                <div class="title">Task State + Memory</div>
                <span class="pill" id="stageBadge">STAGE</span>
                <span class="pill" id="stepBadge">STEP</span>
                <span class="pill" id="pauseBadge">RUNNING</span>
                <div class="grow"></div>
                <label for="profileSelect">Профиль</label>
                <select id="profileSelect"></select>
              </div>
              <div class="state-body">
                <div class="block"><div class="subtitle">Task state</div><pre id="taskStateView">loading...</pre></div>
                <div class="block"><div class="subtitle">Short term</div><pre id="shortView">loading...</pre></div>
                <div class="block"><div class="subtitle">Working memory</div><pre id="workingView">loading...</pre></div>
                <div class="block"><div class="subtitle">Long term profile</div><pre id="longView">loading...</pre></div>
              </div>
            </section>
          </div>
          <script>
            const chatLog = document.getElementById('chatLog');
            const chatForm = document.getElementById('chatForm');
            const messageInput = document.getElementById('messageInput');
            const stageBadge = document.getElementById('stageBadge');
            const stepBadge = document.getElementById('stepBadge');
            const pauseBadge = document.getElementById('pauseBadge');
            const taskStateView = document.getElementById('taskStateView');
            const shortView = document.getElementById('shortView');
            const workingView = document.getElementById('workingView');
            const longView = document.getElementById('longView');
            const profileSelect = document.getElementById('profileSelect');
            function appendMessage(role, content) {
              const div = document.createElement('div');
              div.className = 'msg ' + role;
              const parsed = parseStageCard(content);
              if (parsed) {
                div.classList.add('stage-' + parsed.stage.toLowerCase());
                const badge = document.createElement('div');
                badge.className = 'stage-badge';
                badge.textContent = `${'$'}{parsed.stage} ${'$'}{parsed.step}`;
                const body = document.createElement('div');
                body.textContent = parsed.text;
                div.appendChild(badge);
                div.appendChild(body);
              } else {
                div.textContent = content;
              }
              chatLog.appendChild(div);
              chatLog.scrollTop = chatLog.scrollHeight;
            }

            function parseStageCard(content) {
              const match = content.match(/^\[(PLANNING|EXECUTION|VALIDATION|DONE)\s+(\d+\/\d+)\]\s*(.*)$/s);
              if (!match) return null;
              return { stage: match[1], step: match[2], text: match[3] || '' };
            }

            async function loadHistory() {
              const res = await fetch('/api/history');
              const data = await res.json();
              chatLog.innerHTML = '';
              (data.history || []).forEach((m) => appendMessage(m.role, m.content));
            }

            async function loadProfiles() {
              const res = await fetch('/api/profiles');
              const data = await res.json();
              profileSelect.innerHTML = '';
              data.profiles.forEach((p) => {
                const opt = document.createElement('option');
                opt.value = p.id;
                opt.textContent = `${'$'}{p.title} (${'$'}{p.specialization})`;
                if (data.active_profile_id === p.id) opt.selected = true;
                profileSelect.appendChild(opt);
              });
            }

            async function loadStateAndMemory() {
              const [stateRes, memRes] = await Promise.all([fetch('/api/task-state'), fetch('/api/memory')]);
              const state = await stateRes.json();
              const mem = await memRes.json();
              taskStateView.textContent = JSON.stringify(state, null, 2);
              shortView.textContent = JSON.stringify(mem.short_term, null, 2);
              workingView.textContent = JSON.stringify(mem.working_memory, null, 2);
              longView.textContent = JSON.stringify(mem.long_term_memory, null, 2);
              stageBadge.textContent = state.stage;
              stepBadge.textContent = `step ${'$'}{state.currentStep}/${'$'}{state.totalSteps}`;
              pauseBadge.textContent = state.paused ? 'PAUSED' : 'RUNNING';
            }

            profileSelect.addEventListener('change', async () => {
              await fetch('/api/profiles/select', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ profileId: profileSelect.value })
              });
              await loadStateAndMemory();
            });

            document.getElementById('pauseBtn').addEventListener('click', async () => {
              messageInput.value = 'пауза';
              chatForm.dispatchEvent(new Event('submit'));
            });

            document.getElementById('resumeBtn').addEventListener('click', async () => {
              messageInput.value = 'продолжай';
              chatForm.dispatchEvent(new Event('submit'));
            });

            document.getElementById('resetWorkingBtn').addEventListener('click', async () => {
              await fetch('/api/reset?layer=working', { method: 'POST' });
              await loadStateAndMemory();
            });

            document.getElementById('resetShortBtn').addEventListener('click', async () => {
              await fetch('/api/reset?layer=short', { method: 'POST' });
              await Promise.all([loadHistory(), loadStateAndMemory()]);
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
              appendMessage('assistant', data.reply || '(empty)');
              if (data.transitionMessage) appendMessage('assistant', `[SYSTEM] ${'$'}{data.transitionMessage}`);
              await loadStateAndMemory();
            });

            async function init() {
              await Promise.all([loadProfiles(), loadHistory(), loadStateAndMemory()]);
            }

            init().catch((err) => {
              console.error(err);
              appendMessage('assistant', '[SYSTEM] Ошибка загрузки UI состояния.');
            });
          </script>
        </body>
        </html>
    """.trimIndent()
}
