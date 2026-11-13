# PHPStan Baseline Highlighter

Плагин для IDE на IntelliJ Platform показывает записи PHPStan baseline возле объявлений классов и методов: значок в гутере, подсказка и переход к записи в NEON. Работает без запуска PHPStan, PHP CLI, Docker и других внешних процессов.

PHP-поддержка подключается опциональными адаптерами: **JetBrains PHP** (`com.jetbrains.php`) и **PHP for OpenIDE** (`ru.openide.openphp`, в том числе в Ракете). Общая часть не использует API конкретного PHP-плагина. Без обоих адаптеров доступны индекс, настройки и список записей файла; для маркеров объявлений нужен один из поддерживаемых PHP-плагинов.

Baseline фиксирует исключения, поэтому маркер **не подтверждает наличие нарушения в текущем коде**. Плагин не изменяет baseline и не подсвечивает точные строки ошибок.

## Установка и настройка

1. Соберите проект или возьмите `build/distributions/phpstan-baseline-highlighter-0.1.1.zip` из текущей сборки.
2. Откройте **Settings → Plugins → ⚙ → Install Plugin from Disk** и выберите ZIP целиком.
3. В проекте откройте **Settings → Languages & Frameworks → PHPStan Baseline Highlighter**.
4. Кнопкой **+** добавьте нужные `.neon` baseline. При необходимости отредактируйте пути в таблице. Нажмите **Apply**.
5. Проверьте диагностику и примеры вычисленных путей. При пустой базовой директории относительные `path` разрешаются относительно каталога baseline.

Подключайте только baseline нужной PHPStan-конфигурации. Автоматического обхода `includes` нет. Несколько источников и произвольные имена файлов поддерживаются.

Пример для проекта с расположением:

```text
project/
  config/phpstan-baseline.neon   # path: ../src/Service.php
  src/Service.php
```

Достаточно добавить `config/phpstan-baseline.neon`, оставив базовую директорию пустой. Настройки хранятся локально в workspace проекта; пути внутри проекта сохраняются относительно его корня.

Для Ракеты добавляйте baseline из `app/raketa/code_quality`, подключённые к нужному `.phpstan*.neon`. У них базовую директорию обычно можно оставить пустой. Абсолютные контейнерные пути `/web/...` не преобразуются автоматически.

## Работа в редакторе

- Маркер класса объединяет его записи, включая записи методов.
- Маркер метода показывает записи, в которых метод явно назван владельцем диагностики.
- При единственной записи клик открывает и выделяет её сообщение. При нескольких появляется список источников.
- Действие **Show PHPStan Baseline Entries for File** доступно через контекстное меню PHP-файла и Find Action. Оно показывает и записи без определённого владельца.
- Изменения baseline и PHP-кода подхватываются, в том числе до сохранения файлов. Ошибка формата убирает записи некорректного источника до исправления.
- Кнопка перечитывания в настройках обновляет применённые источники и диагностику. Видимость значков также регулируется через **Editor → General → Gutter Icons**.

Например, `Method App\Service::save() has ...` относится к объявлению `save()`. Сообщение `Call to an undefined method App\Other::run()` не позволяет определить метод, в котором сделан вызов: плагин не ставит маркер на объявление вызываемого `run()`.

Если владелец неизвестен, запись может отображаться возле единственного класса файла с соответствующим пояснением. В файле с несколькими классами она остаётся доступна через действие для файла. Если указанный метод отсутствует, запись остаётся в сводке указанного класса.

Поле `count` показывается как ожидаемое количество совпадений анализатора. Это не число записей и не количество отмеченных строк.

## Поддерживаемый формат

Ограниченный структурный парсер читает стандартные генерируемые NEON baseline:

```neon
parameters:
    ignoreErrors:
        -
            message: '#^Method App\\Service\:\:save\(\) has no return type specified\.$#'
            identifier: missingType.return
            count: 1
            path: ../src/Service.php
```

Обязательны однострочные `message` и `path`; `identifier` и положительный `count` необязательны. Поддерживаются комментарии, табы/пробелы, LF/CRLF, Unicode и одинарные/двойные кавычки NEON. Пустой baseline может содержать `ignoreErrors: []`.

Не поддерживаются маски и подстановки параметров в путях, `paths`, `messages`, `rawMessage`, многострочные скаляры, PHP-конфигурации и другие формы NEON. Если источник содержит ошибку или неподдерживаемую форму, его записи не включаются в индекс, а причина появляется в настройках.

Для определения владельца распознаются буквальные шаблоны сообщений, включая определения методов, сообщения о свойствах и архитектурное `should not depend on`. Произвольные PCRE не исполняются; сообщения с непонятными выражениями остаются записями уровня файла. Свойства агрегируются на уровне класса.

## Разработка

Нужны JDK 21 и доступ к репозиториям Gradle/Maven/JetBrains при первом запуске. В репозитории есть Gradle Wrapper; системный Gradle не требуется. Kotlin 2.3.0, IntelliJ Platform Gradle Plugin 2.16.0.

```bash
./gradlew test buildPlugin
./gradlew runIde
```

По умолчанию сборка использует SDK PhpStorm 2025.2.6.2 и запускает тесты JetBrains-адаптера. Это SDK сборки, а не обязательное требование к пользовательской IDE. Объявленный диапазон IDE: ветки 252–263. Проверка API более новой IDE выполняется отдельно и не заменяет ручную проверку интерфейса.

Для компиляции OpenIDE-адаптера сборка скачивает PHP for OpenIDE 0.9.9 с сайта поставщика и проверяет SHA-256. SDK сохраняется в `.intellijPlatform/sdk`; его JAR используются только как `compileOnly` и **не включаются в ZIP плагина**. Вместо загрузки можно передать `-PopenPhpPluginPath=/path/to/php-for-openide`, указав установленный плагин с каталогом `lib`.

Можно использовать установленную IDE вместо загрузки дистрибутива:

```bash
./gradlew buildPlugin -PlocalIdePath=/absolute/path/to/PhpStorm
./gradlew verifyPlugin -PverificationIdePath=/absolute/path/to/PhpStorm
```

Для сборок IDE на JDK 25 запускайте Gradle соответствующим JDK. Опция `localIdePath` меняет SDK сборки, а `verificationIdePath` — только IDE для Plugin Verifier.

В окружении с уже загруженной IDEA Ultimate и PHP-плагином поддерживается вариант:

```bash
./gradlew test buildPlugin --offline \
  -PplatformType=IU -PplatformVersion=2025.2.6.2 \
  -PphpPluginVersion=252.28539.13
```

Он использован для первой проверки fixture-тестов. Зависимости Ultimate и JSON для такой тестовой IDE добавляются сборкой; пользовательский PhpStorm не изменяется.

### Проверка альтернативного PHP-плагина

`phpBackend` выбирает тестовое окружение: `jetbrains` (по умолчанию), `openphp` или `none`. Каждый выпускаемый ZIP содержит оба опциональных адаптера независимо от выбранного режима тестов.

```bash
./gradlew test -PlocalIdePath=/path/to/compatible/IDE \
  -PphpBackend=openphp -PopenPhpPluginPath=/path/to/php-for-openide \
  -PjetBrainsPhpPluginPath=/path/to/JetBrainsPHP
```

В этом режиме JetBrains PHP нужен только как SDK для компиляции второго адаптера; в тестовую IDE он не устанавливается. Для режима `none` задайте те же пути SDK, `-PphpBackend=none` и обычную IDE: отдельный тест проверяет загрузку и чтение baseline при отсутствии обоих PHP-плагинов.

Gradle-плагин JetBrains не узнаёт код продукта `RK`. Для тестов на установленной Ракете есть вспомогательный скрипт:

```bash
python3 tools/prepare-local-sdk.py /path/to/Raketa .intellijPlatform/local-sdk
./gradlew test -PlocalIdePath="$PWD/.intellijPlatform/local-sdk" \
  -PphpBackend=openphp -PopenPhpPluginPath=/path/to/php-for-openide \
  -PjetBrainsPhpPluginPath=/path/to/JetBrainsPHP \
  -PtestFrameworkVersion=262.11879.100
```

Скрипт создаёт локальное представление SDK с симлинками и совместимыми для структурного анализатора Gradle метаданными. Установленная Ракета не изменяется. Версию test framework нужно выбирать под конкретную сборку платформы; указанная версия использована с Raketa 2026.2.4 / 262.11879.SNAPSHOT. Для неё Gradle запускается на JDK 25.

### Проверки

```bash
./gradlew test
./gradlew verifyPluginStructure verifyPluginProjectConfiguration
./gradlew verifyPlugin -PverificationIdePath=/absolute/path/to/PhpStorm
./gradlew benchmarkBaselines -PbaselineDirectory=/path/to/directory/with/baselines
```

`test` проверяет парсер, владельцев сообщений, пути, PHP PSI, обновление индекса и переходы в изолированной IDE. Отчёты разделены по режимам: `build/reports/tests/jetbrains/index.html`, `openphp/index.html` и `none/index.html`. Benchmark читает файлы и измеряет JVM-ядро; он не запускает анализатор и не является замером памяти всего IDE-процесса. Для benchmark выбирайте каталог baseline без больших деревьев зависимостей.

Результаты первой проверки, измерения и оставшиеся ручные проверки: [VALIDATION.md](VALIDATION.md). План и критерии приёмки: [plan.md](plan.md).
