# Week 01

## Цель

Подготовить окружение и сделать первое практическое задание на Python.

## Чеклист

- [ ] Поднять и активировать `.venv`
- [ ] Запустить `task_01_intro.py`
- [ ] Запустить `task_02_yandexgpt_api.py`
- [ ] Поэкспериментировать с `temperature`, `top_p`, `top_k`, `max_tokens`
- [ ] Запустить `task_03_response_control.py`
- [ ] Сравнить ответ без ограничений и с ограничениями формата/длины/завершения
- [ ] Запустить `task_04_reasoning_strategies.py`
- [ ] Сравнить 4 подхода к рассуждению на одной задаче
- [ ] Запустить `task_05_temperature_comparison.py`
- [ ] Сравнить ответы при temperature 0.0 / 0.7 / 1.0
- [ ] Зафиксировать вывод и заметки

## Файлы недели

- `task_01_intro.py` — стартовый скрипт.
- `task_02_yandexgpt_api.py` — подключение к YandexGPT по API и параметры генерации.
- `task_03_response_control.py` — сравнение ответа без контроля и с контролем формата.
- `task_04_reasoning_strategies.py` — сравнение 4 стратегий рассуждения на одной задаче.
- `task_05_temperature_comparison.py` — сравнение ответов при разных temperature.

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

```bash
python3 weeks/week01/task_03_response_control.py \
  --prompt "Объясни, как работает метод градиентного спуска" \
  --format-instruction "JSON с ключами summary и steps" \
  --max-chars 450 \
  --end-marker "<END>"
```

```bash
# если хотите попробовать stop sequence через API
python3 weeks/week01/task_03_response_control.py \
  --prompt "Объясни, как работает метод градиентного спуска" \
  --format-instruction "JSON с ключами summary и steps" \
  --max-chars 450 \
  --end-marker "<END>" \
  --use-stop-sequence
```

```bash
python3 weeks/week01/task_04_reasoning_strategies.py \
  --task "У вас есть ограниченный бюджет и 3 недели до релиза AI-ассистента. Нужно выбрать главный приоритет: точность ответов, скорость генерации или стоимость инфраструктуры. Какой приоритет выбрать и почему?"
```

```bash
# для задач с однозначным ответом можно включить автопроверку
python3 weeks/week01/task_04_reasoning_strategies.py \
  --task "Петя дал младшему брату половину запаса яблок и еще одно яблоко, и у него не осталось ни одного яблока. Сколько яблок было у Пети?" \
  --expected 2
```

```bash
python3 weeks/week01/task_05_temperature_comparison.py \
  --prompt "Ты продакт-менеджер AI-стартапа. За 2 недели и с бюджетом 100 000 рублей нужно повысить удержание пользователей. Предложи 3 разные гипотезы улучшения продукта, для каждой укажи: ожидаемый эффект, риск и простую метрику проверки."
```
