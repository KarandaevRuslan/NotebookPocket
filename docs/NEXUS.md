# Nexus в NotebookPocket

[Nexus](https://github.com/KarandaevRuslan/Nexus) **0.1.20 Core** встроен в приложение и доступен офлайн в каждом блокноте.
Observable Notebook Kit **2.9.1** продолжает компилировать и исполнять ячейки.
Nexus управляет транзакциями, Store, Event и Handler внутри этих ячеек; его можно
использовать самостоятельно для логики и DOM либо передавать значения в граф Observable.

Импортировать библиотеку или скачивать её из сети в блокноте не нужно:

| Имя | Назначение |
| --- | --- |
| `Nexus` | Публичные экспорты `nexus/core`, включая `Dispatcher`, `Handler`, `Scope`, `Store`, `Event` и классы ошибок |
| `nexus.version` | `"0.1.20"` |
| `nexus.dispatcher` | Общий Dispatcher текущего предпросмотра |
| `nexus.scope(invalidation)` | Scope с автоматическим освобождением при пересчёте ячейки и закрытии предпросмотра |
| `nexus.scope()` | Scope до закрытия/перезапуска предпросмотра или явного `dispose()` |
| `nexus.observe(scope, store)` | Асинхронный генератор начального и последующих значений Store для Observable |

## Транзакции Nexus в JavaScript 2.0

Создайте состояние в отдельной ячейке `type="module"`:

```js
const scope = nexus.scope(invalidation);
const counter = scope.store(0);
```

В другой ячейке создайте кнопку:

```js
const button = html`<button>+1</button>`;
const increment = scope.guard(() => {
  nexus.dispatcher.inline(ctx => ctx.set(counter, ctx.read(counter) + 1));
});
button.addEventListener("click", increment);
scope.cleanup(() => button.removeEventListener("click", increment));
display(button);
```

Для собственных реакций наследуйте `Nexus.Handler`, регистрируйте обработчик
через `scope.register(...)` и выполняйте DOM/внешние эффекты через `ctx.effect`.
Таймеры и слушатели отменяйте через `scope.cleanup`; `scope.guard` блокирует
поздние вызовы после освобождения. Для отменяемых команд используйте
`scope.submit(...)` / `scope.submitAndWait(...)`.

## Передача Store в Observable

Добавьте OJS-ячейку в тот же блокнот:

```html
<script id="3" type="application/vnd.observable.javascript">
count = nexus.observe(scope, counter)
</script>
```

После этого `count` — реактивное значение для любых зависимых JS/TS/OJS-ячеек:

```js
display(html`<p>Счётчик: ${count}</p>`);
```

Передавайте в `observe` Scope, владеющий Store. Подписка освобождается как
при остановке генератора Observable, так и при освобождении Scope.
Быстрые последовательные изменения могут объединяться в последнее значение,
как у `Generators.observe`. Если важен каждый переход, используйте Handler Nexus.

В JS 2.0 запись `const count = nexus.observe(scope, counter)` сохраняет объект
генератора в переменную и не разворачивает его значения автоматически.
Используйте приведённую OJS-ячейку для именованного реактивного значения.
У Store стабильная идентичность: чтение через `dispatcher.inline(ctx =>
ctx.read(counter))` даёт разовый снимок, а не подписку Observable.

Полный импортируемый пример с кнопкой, Handler и зависимой JS-ячейкой:
[`examples/nexus-counter.html`](../examples/nexus-counter.html).
Импортируйте этот HTML через обычный импорт документов приложения.

## Жизненный цикл и границы

Каждый рендер создаёт отдельные Dispatcher и набор Scope. Смена документа,
повторный рендер, сброс состояния и закрытие предпросмотра освобождают управляемые
Scope и Observable runtime. Состояние Nexus хранится в памяти: сброс/повторный
рендер возвращает исходные значения, оно не сохраняется в DOM storage.

`new Nexus.Dispatcher()` и остальные исходные классы также доступны. Их
обработчиками, Scope и внешними ресурсами управляйте самостоятельно через
`invalidation` и `dispose()`. Мост `nexus.observe` предназначен для Scope,
созданных через `nexus.scope`, и Store общего `nexus.dispatcher`.

Frida API не включён в браузерный предпросмотр. JavaScript исполняется в том же
изолированном WebView без нативного bridge; разрешения сети и CSP не меняются.

## Проверка разработчиком

```sh
cd web
npm ci
npm test
npm run test:upstream
npm run build
npm run test:built-preview
```

`web/vendor/nexus-0.1.20.tgz` поставляется в проекте, поэтому установка Nexus
не требует доступа к GitHub Releases. Происхождение пакета и сборка из исходников
описаны в [`third_party/nexus/README.md`](../third_party/nexus/README.md).

Результаты выполненных проверок и исходные сбои общего набора:
[`NEXUS-VALIDATION.md`](NEXUS-VALIDATION.md).

## Интерактивная демонстрация в рецепте «Блины»

Встроенная инструкция «Блины» показывает Nexus непосредственно во время приготовления:
кнопка «+ 1 готов» записывает число через `nexus.dispatcher.inline`, а `Handler`
автоматически считает порции по два блина. Две OJS-ячейки с `nexus.observe`
передают значения в Observable, который обновляет счётчики и индикатор прогресса.
Можно изменить число яиц, не потеряв текущий счётчик; кнопки отмены и сброса
также выполняют транзакции. В карточке есть раскрываемая памятка с фрагментами API.

`Scope` создан через `nexus.scope(invalidation)`, обработчики кнопок защищены
`scope.guard`, а подписки/слушатели освобождаются вместе с предпросмотром.
Счётчик не сохраняется между перезапусками; обычный таймер `Pocket.timer` не заменён.
При обновлении приложения неповреждённый старый демонстрационный рецепт можно
обновить автоматически. Изменённые пользователем версии не переписываются.
