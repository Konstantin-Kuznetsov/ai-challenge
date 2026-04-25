package week03.task1

fun renderTask1Page(): String {
    return """
        <!doctype html>
        <html lang="ru">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>Week03 Task1 Memory Layers</title>
          <style>
            body { margin: 0; font-family: Arial, sans-serif; background: #f5f6fa; color: #1f2430; }
            .app { display: grid; grid-template-columns: 58% 42%; gap: 12px; height: 100vh; padding: 12px; box-sizing: border-box; }
            .panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 10px rgba(20,20,30,0.08); display: flex; flex-direction: column; overflow: hidden; }
            .header { padding: 12px; border-bottom: 1px solid #eceff5; display: flex; gap: 10px; align-items: center; }
            .title { font-size: 14px; font-weight: 700; }
            .grow { flex: 1; }
            select, input, button, textarea { border: 1px solid #cfd6e4; border-radius: 8px; padding: 8px; font-size: 14px; }
            button { cursor: pointer; background: #4d63ff; color: #fff; border: none; }
            button.secondary { background: #6b7280; }
            .chat-log { flex: 1; overflow: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
            .msg { max-width: 86%; padding: 10px; border-radius: 10px; white-space: pre-wrap; line-height: 1.4; }
            .msg.user { align-self: flex-end; background: #e9eeff; }
            .msg.assistant { align-self: flex-start; background: #f1f3f7; }
            .composer { display: flex; gap: 8px; padding: 12px; border-top: 1px solid #eceff5; }
            .composer input { flex: 1; }
            .memory-body { overflow: auto; padding: 12px; display: grid; gap: 10px; }
            .mem-block { border: 1px solid #e7ebf3; border-radius: 8px; padding: 10px; }
            .mem-title { font-size: 13px; font-weight: 700; margin-bottom: 8px; }
            pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-size: 12px; }
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
                <input id="messageInput" placeholder="Введите сообщение..." autocomplete="off" />
                <button type="submit">Отправить</button>
              </form>
            </section>
            <section class="panel">
              <div class="header">
                <div class="title">Память</div>
                <div class="grow"></div>
                <label for="profileSelect" class="status">Профиль</label>
                <select id="profileSelect"></select>
              </div>
              <div class="memory-body">
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

            function appendMessage(role, content) {
              const el = document.createElement('div');
              el.className = 'msg ' + role;
              el.textContent = content;
              chatLog.appendChild(el);
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
              metricsView.textContent = JSON.stringify(data.memory || {}, null, 2);
              await loadMemory();
            });

            async function init() {
              await Promise.all([loadProfiles(), loadHistory(), loadMemory()]);
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
