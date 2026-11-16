# Проверка интеграции Nexus 0.1.20

Дата: 16 ноября 2026. Проверки выполнялись на Linux, Node.js 24.22.1.
Минимальная версия Node.js проекта/CI поднята до 22.16.0 по требованию пакета Nexus.

| Проверка | Результат |
| --- | --- |
| `npm ci --ignore-scripts --no-audit --no-fund` | Успешно; Nexus установлен из локального архива, остальные версии зависимостей сохранены |
| `npm run build` | Успешно: editor + preview, аудит локальных ресурсов, копирование бандлов в Android assets |
| `npm run test:built-preview` | Успешно: реальный минифицированный preview исполнил пример, обновил значение через Nexus и сбросил состояние |
| `node --test tests/nexus-runtime.test.mjs` | 7 из 7 проходят |
| `npm test` | 101 проверка: 98 проходят, 3 исходных сбоя (ниже) |
| `npm run test:upstream` | 45 из 45 проходят |
| `python3 scripts/check_repository.py` | Успешно |
| `python3 -m unittest discover -s tests -p '*Test.py'` | 22 из 22 проходят |
| `node tests/ResourceLocalizationTest.mjs` | Успешно |
| `node tests/WorkspaceUXTest.mjs` | Успешно |
| APK / Android unit, lint и device tests | Не запускались: Android SDK 35 / JDK 17 не настроены в окружении проверки |

Новые проверки исполняют настоящий пакет Nexus и Observable runtime в браузерном
окружении JSDOM: начальное значение и изменения Store, откат транзакции,
отмену ожидающих команд, освобождение подписок в обоих порядках, изоляцию
предпросмотров, обработку ошибок очистки, пересчёт ячеек при изменении ввода,
сброс/смену документа, защищённый предпросмотр и подсказки редактора.

## Исходные сбои общего набора

Те же три теста отдельно запущены на распакованном **исходном**
`NotebookPocket(6).zip`, без интеграции Nexus, с теми же установленными
зависимостями. Все три воспроизводятся там:

| Файл / тест | Наблюдение |
| --- | --- |
| `web/tests/acode-host-runtime.test.mjs`: `original scrollbar drag changes scroll without changing a long selection` | JSDOM не находит ожидаемую полосу прокрутки (`assert.ok(bar)`) |
| `web/tests/pancake-demo.test.mjs`: `official Observable step buttons use modern grouped styles and remain interactive` | Ожидается `display: flex`, тест получает `block` |
| `web/tests/protected-state.test.mjs`: `native Preview passes the protection flag into the isolated renderer` | Устаревший regex ожидает `$payload.source`; исходный Kotlin уже передаёт `p.source` после `var p = $payload` |

Эти исходные проверки/модули не менялись в рамках интеграции. Полностью зелёный
общий набор и готовый APK данным отчётом не заявляются.

## Источник зависимости и состав архива

Nexus собран из приложенного исходного архива версии 0.1.20; ограничение полной
сборки его тестов и происхождение пакета описаны в `third_party/nexus/README.md`.
Подключается только браузерное ядро `nexus/core`; сеть, CSP и Android bridge
не расширялись. В проверенном preview отсутствуют Frida-адаптеры.

Архив проекта содержит исходники и локальный пакет Nexus. `node_modules`,
сгенерированные бандлы, локальные настройки, отчётные логи и APK не включены.
Для сборки выполните команды из `docs/BUILDING.md` и `docs/NEXUS.md`.
