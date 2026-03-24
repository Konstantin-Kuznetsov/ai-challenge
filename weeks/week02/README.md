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

## Требования к окружению

Должны быть заданы переменные:

- `YANDEX_API_KEY`
- `YANDEX_FOLDER_ID`
- `YANDEX_MODEL` (опционально, по умолчанию используется `yandexgpt-lite`)
