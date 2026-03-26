# Week 02

## Цель

Реализовать агента на Flask и добавить сохранение контекста между перезапусками.

## Чеклист

- [ ] Поднять и активировать `.venv`
- [ ] Установить зависимости проекта (`python3 -m pip install flask`)
- [ ] Task 1: запустить `task1/task_01_flask_agent.py`
- [ ] Task 1: проверить базовый агент (UI, system prompt, обработка ошибок)
- [ ] Task 2: запустить `task2/task_02_flask_agent_persistent.py`
- [ ] Task 2: начать диалог и отправить 2-3 сообщения
- [ ] Task 2: перезапустить приложение
- [ ] Task 2: продолжить диалог и проверить, что контекст восстановился
- [ ] Task 3: запустить `task3/task_03_flask_agent_tokens.py`
- [ ] Task 3: сравнить короткий и длинный диалог по токенам
- [ ] Task 3: упереться в лимит токенов диалога и проверить обработку переполнения
- [ ] Task 4: запустить `task4/task_04_flask_agent_context_compression.py`
- [ ] Task 4: сравнить качество без сжатия и со сжатием истории
- [ ] Task 4: сравнить расход токенов/стоимости full vs compressed
- [ ] Зафиксировать вывод и заметки

## Файлы недели

- `task1/agent.py` — инкапсулированная логика агента и вызова API.
- `task1/task_01_flask_agent.py` — Flask-приложение (роуты и хранение истории).
- `task1/templates/index.html` — простой чат-интерфейс.
- `task2/agent.py` — агент с передачей истории в `messages`.
- `task2/storage.py` — загрузка/сохранение истории в JSON.
- `task2/task_02_flask_agent_persistent.py` — Flask-приложение с восстановлением контекста.
- `task2/templates/index.html` — UI для persistent-агента.
- `task2/chat_history.json` — файл истории между запусками.
- `task3/agent.py` — агент с возвратом usage-токенов API.
- `task3/storage.py` — хранение истории и метрик токенов.
- `task3/task_03_flask_agent_tokens.py` — Flask-чат с лимитом токенов и графиком.
- `task3/templates/index.html` — split UI: чат + график токенов/стоимости.
- `task3/chat_history.json` — история сообщений для task3.
- `task3/metrics_history.json` — история токенов/стоимости по turn.
- `task4/agent.py` — агент с подстановкой summary в context.
- `task4/storage.py` — хранение истории, метрик и summary state.
- `task4/task_04_flask_agent_context_compression.py` — Flask-чат с параллельным full/compressed сравнением.
- `task4/templates/index.html` — два чата side-by-side + график сравнения.
- `task4/chat_history_full.json` — история сообщений для режима без сжатия.
- `task4/chat_history_compressed.json` — история сообщений для режима со сжатием.
- `task4/metrics_history.json` — метрики токенов/стоимости по режимам.
- `task4/summary_state.json` — сжатый summary и позиция свертки.

## Запуск

```bash
python3 weeks/week02/task1/task_01_flask_agent.py
```

После запуска откройте:

- http://127.0.0.1:5000

### Task 2

```bash
python3 weeks/week02/task2/task_02_flask_agent_persistent.py
```

После запуска откройте:

- http://127.0.0.1:5001

### Task 3

```bash
python3 weeks/week02/task3/task_03_flask_agent_tokens.py
```

После запуска откройте:

- http://127.0.0.1:5002

### Task 4

```bash
python3 weeks/week02/task4/task_04_flask_agent_context_compression.py
```

После запуска откройте:

- http://127.0.0.1:5003

## Требования к окружению

Должны быть заданы переменные:

- `YANDEX_API_KEY`
- `YANDEX_FOLDER_ID`
- `YANDEX_MODEL` (опционально, по умолчанию используется `yandexgpt-lite`)
