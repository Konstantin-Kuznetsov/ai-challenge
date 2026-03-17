# Week 01

## Цель

Подготовить окружение и сделать первое практическое задание на Python.

## Чеклист

- [ ] Поднять и активировать `.venv`
- [ ] Запустить `task_01_intro.py`
- [ ] Запустить `task_02_yandexgpt_api.py`
- [ ] Поэкспериментировать с `temperature`, `top_p`, `top_k`, `max_tokens`
- [ ] Зафиксировать вывод и заметки

## Файлы недели

- `task_01_intro.py` — стартовый скрипт.
- `task_02_yandexgpt_api.py` — подключение к YandexGPT по API и параметры генерации.

## Примеры запуска

```bash
python3 weeks/week01/task_02_yandexgpt_api.py \
  --prompt "Придумай 3 идеи pet-проекта на Python" \
  --temperature 0.2
```

```bash
python3 weeks/week01/task_02_yandexgpt_api.py \
  --prompt "Придумай 3 идеи pet-проекта на Python" \
  --temperature 0.9 \
  --top-p 0.8 \
  --top-k 40 \
  --max-tokens 500
```
