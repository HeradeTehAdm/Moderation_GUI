# Команды Moderation GUI

Все команды используют отдельный корень `/moderationgui`. Мод не переопределяет ванильные `/ban`, `/kick` и другие стандартные команды.

## Информация о редакции

| Команда | Назначение |
|---|---|
| `/moderationgui edition` | Показать редакцию JAR и матрицу доступных возможностей |
| `/moderationgui settings` | Показать текущие настройки сервера |
| `/moderationgui settings set <key> <true|false>` | Изменить настройку сервера |
| `/moderationgui export [limit]` | Pro: экспортировать до 200 последних записей Activity Log в CSV; по умолчанию 200 |

## Профили и история

| Команда | Назначение |
|---|---|
| `/moderationgui profile <player>` | Показать профиль игрока |
| `/moderationgui history <player>` | Показать последние 20 наказаний игрока |
| `/moderationgui note <player> <notes>` | Обновить заметки профиля с записью в аудит |

Игрок должен хотя бы один раз подключиться к серверу, чтобы его профиль появился в базе.

## Модерация

| Команда | Назначение |
|---|---|
| `/moderationgui warn <player> <reason>` | Выдать предупреждение |
| `/moderationgui mute <player> <reason>` | Выдать постоянный MUTE |
| `/moderationgui tempmute <player> <seconds> <reason>` | Выдать временный MUTE |
| `/moderationgui ban <player> <reason>` | Выдать постоянный BAN |
| `/moderationgui tempban <player> <seconds> <reason>` | Выдать временный BAN |
| `/moderationgui kick <player> <reason>` | Отключить игрока без создания наказания |
| `/moderationgui activity` | Показать последние 20 записей Activity Log |
| `/moderationgui unpunish <id>` | Снять наказание по его ID |

Временные сроки указываются в секундах; максимальный срок одной команды — 365 дней. BAN отключает игрока сразу, если тот онлайн, и проверяется при следующем подключении. MUTE блокирует публичный чат и широковещательные командные сообщения. Приватные сообщения, которые реализуют другие моды, этим обработчиком не перехватываются.

## Ранги

| Команда | Назначение |
|---|---|
| `/moderationgui rank list` | Список рангов |
| `/moderationgui rank create <name> <priority> <prefix> <color>` | Создать ранг |
| `/moderationgui rank edit <name> <priority> <prefix> <color>` | Изменить параметры ранга |
| `/moderationgui rank parent <child> <parent|none>` | Настроить наследование ранга |
| `/moderationgui rank delete <name>` | Удалить ранг |
| `/moderationgui rank assign <player> <rank>` | Назначить ранг |
| `/moderationgui rank unassign <player> <rank>` | Снять ранг |

Чтобы создать ранг без префикса, укажите `-` вместо значения `prefix`. При удалении ранга его назначения и групповые права удаляются через внешние ключи SQLite; дочерние ранги теряют ссылку на удалённого родителя.

## Разрешения

| Команда | Назначение |
|---|---|
| `/moderationgui permission rank <rank> <node> grant` | Разрешить узел рангу |
| `/moderationgui permission rank <rank> <node> deny` | Запретить узел рангу |
| `/moderationgui permission rank <rank> <node> remove` | Удалить правило ранга |
| `/moderationgui permission player <player> <node> grant` | Разрешить узел игроку |
| `/moderationgui permission player <player> <node> deny` | Запретить узел игроку |
| `/moderationgui permission player <player> <node> remove` | Удалить персональное правило |
| `/moderationgui permission ranklist <rank>` | Показать правила ранга |
| `/moderationgui permission playerlist <player>` | Показать персональные правила |

Примеры узлов: `moderation.ban`, `moderation.kick`, `moderation.*`, `*`.

## Настройки сервера

Настройки сохраняются в `moderation-gui/settings.properties` рядом с базой данных. Доступны два безопасных переключателя:

- `enforce_bans` — проверять активные BAN при подключении игрока (по умолчанию `true`).
- `enforce_mutes` — блокировать публичные сообщения активных MUTE (по умолчанию `true`).

Изменения применяются сразу и сохраняются атомарной заменой файла. Доступ к просмотру требует `moderation.settings.view`, изменение — `moderation.settings.edit`.

## Проверка прав

Уровень оператора Minecraft 2 и выше может выполнять административные команды. Остальные игроки проверяются сервером по узлам:

- `moderation.players.view`
- `moderation.players.edit`
- `moderation.history.view`
- `moderation.punishments.view`
- `moderation.warn`
- `moderation.mute`
- `moderation.ban`
- `moderation.tempban`
- `moderation.tempmute`
- `moderation.kick`
- `moderation.punishments.revoke`
- `moderation.ranks.view`
- `moderation.ranks.create`
- `moderation.ranks.delete`
- `moderation.ranks.assign`
- `moderation.ranks.edit`
- `moderation.activity.view`
- `moderation.export` (только Pro)
- `moderation.settings.view`
- `moderation.settings.edit`
- `moderation.permissions.view`
- `moderation.permissions.manage`

Консоль сервера может выполнять команды без UUID игрока; её действия записываются с системным UUID `00000000-0000-0000-0000-000000000000`.

## Приоритет правил

1. Персональные правила игрока имеют приоритет над правилами рангов.
2. Более конкретный узел имеет приоритет над wildcard.
3. При одинаковой специфичности прямое правило назначенного ранга важнее унаследованного.
4. Между правилами одинакового уровня учитывается приоритет ранга.
5. При полном равенстве явный запрет имеет преимущество.

Все административные изменения рангов, разрешений и наказаний записываются в Activity Log в той же транзакции, что и изменение данных.
