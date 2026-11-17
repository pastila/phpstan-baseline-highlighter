# Привязки baseline Ракеты по идентификаторам

Снимок от 17 ноября 2026 года на коде плагина 0.1.13: 27 подключённых baseline.
Всего записей: 22 142;
379 разных `identifier` и 358 записей без идентификатора.
Общие результаты, примеры формулировок и ограничения — в
[списке поддержки](RAKETA_BASELINE_SUPPORT.md).

«Без владельца» — число сообщений без имени класса или функции в подсказке владельца.
Остальные шесть столбцов описывают результат сопоставления с сохранённым PHP:
«Функция» — существующая именованная функция; «Метод» — существующий метод;
«Класс» — класс без метода;
«Нет метода» — класс найден, метод отсутствует среди собственных методов;
«По файлу» — условная привязка к единственному типу (`file_only`);
«Без маркера» — все причины отсутствия привязки, включая отсутствующую функцию.
Сумма этих шести столбцов равна «Всего». «Без владельца» с ними пересекается.

Ссылка в последнем столбце ведёт к примеру записи в соседнем checkout
`../raketa`, с номером строки `message` на дату снимка. Если у идентификатора
есть сообщение без распознанного владельца, пример выбран среди таких сообщений.
Наличие имени в каталоге PHPStan означает доступность ссылки на справку,
а не поддержку привязки: она определяется формулировкой сообщения.

## Идентификаторы из каталога PHPStan

Идентификаторов: 110.

| identifier | Всего | Без владельца | Функция | Метод | Класс | Нет метода | По файлу | Без маркера | Пример |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `argument.invalidPregQuote` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [2449](../raketa/app/raketa/code_quality/phpstan-baseline-legacy-phpstan_v2.neon#L2449) |
| `argument.type` | 502 | 502 | 0 | 0 | 0 | 0 | 239 | 263 | [16](../raketa/api/avia/phpstan-baseline.neon#L16) |
| `argument.unresolvableType` | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | [3118](../raketa/app/raketa/code_quality/phpstan-baseline-presentation.neon#L3118) |
| `arguments.count` | 12 | 12 | 0 | 0 | 0 | 0 | 1 | 11 | [3100](../raketa/app/fcc/phpstan-baseline.neon#L3100) |
| `arrayFilter.same` | 15 | 15 | 0 | 0 | 0 | 0 | 15 | 0 | [40](../raketa/api/profile-sync/phpstan-baseline.neon#L40) |
| `arrayValues.list` | 17 | 17 | 0 | 0 | 0 | 0 | 16 | 1 | [700](../raketa/app/fcc/phpstan-baseline.neon#L700) |
| `assign.propertyType` | 104 | 104 | 0 | 0 | 0 | 0 | 104 | 0 | [88](../raketa/api/profile-sync/phpstan-baseline.neon#L88) |
| `assignOp.invalid` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [16314](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L16314) |
| `binaryOp.invalid` | 12 | 12 | 0 | 0 | 0 | 0 | 2 | 10 | [6844](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L6844) |
| `booleanAnd.alwaysFalse` | 4 | 4 | 0 | 0 | 0 | 0 | 3 | 1 | [6724](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L6724) |
| `booleanAnd.alwaysTrue` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [26642](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L26642) |
| `booleanAnd.leftAlwaysFalse` | 6 | 6 | 0 | 0 | 0 | 0 | 0 | 6 | [6484](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L6484) |
| `booleanAnd.leftAlwaysTrue` | 17 | 17 | 0 | 0 | 0 | 0 | 9 | 8 | [196](../raketa/app/fcc/phpstan-baseline.neon#L196) |
| `booleanAnd.rightAlwaysFalse` | 6 | 6 | 0 | 0 | 0 | 0 | 0 | 6 | [6490](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L6490) |
| `booleanAnd.rightAlwaysTrue` | 6 | 6 | 0 | 0 | 0 | 0 | 5 | 1 | [2788](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L2788) |
| `booleanNot.alwaysFalse` | 70 | 70 | 0 | 0 | 0 | 0 | 30 | 40 | [358](../raketa/app/fcc/phpstan-baseline.neon#L358) |
| `booleanNot.alwaysTrue` | 10 | 10 | 0 | 0 | 0 | 0 | 9 | 1 | [3838](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L3838) |
| `booleanOr.alwaysFalse` | 3 | 3 | 0 | 0 | 0 | 0 | 2 | 1 | [2242](../raketa/app/fcc/phpstan-baseline.neon#L2242) |
| `booleanOr.alwaysTrue` | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1 | [676](../raketa/app/fcc/phpstan-baseline.neon#L676) |
| `booleanOr.leftAlwaysTrue` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [22027](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L22027) |
| `booleanOr.rightAlwaysTrue` | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | [19775](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L19775) |
| `catch.neverThrown` | 6 | 6 | 0 | 0 | 0 | 0 | 6 | 0 | [34](../raketa/app/raketa/api/agency/phpstan-baseline-phpstav_v2.neon#L34) |
| `class.nameCase` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [334](../raketa/api/transfer/phpstan-baseline.neon#L334) |
| `class.notFound` | 114 | 85 | 1 | 10 | 18 | 0 | 44 | 41 | [2656](../raketa/app/fcc/phpstan-baseline.neon#L2656) |
| `classConstant.unused` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [12382](../raketa/app/raketa/code_quality/phpstan-baseline.neon#L12382) |
| `closure.unusedUse` | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1 | [2074](../raketa/app/fcc/phpstan-baseline.neon#L2074) |
| `constructor.unusedParameter` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [1222](../raketa/app/raketa/code_quality/phpstan-baseline.neon#L1222) |
| `deadCode.unreachable` | 12 | 12 | 0 | 0 | 0 | 0 | 0 | 12 | [16](../raketa/app/fcc/phpstan-baseline.neon#L16) |
| `elseif.alwaysFalse` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [16334](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L16334) |
| `elseif.alwaysTrue` | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | [33462](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L33462) |
| `empty.expr` | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | [31492](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L31492) |
| `empty.property` | 3 | 3 | 0 | 0 | 0 | 0 | 3 | 0 | [11479](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L11479) |
| `empty.variable` | 7 | 7 | 0 | 0 | 0 | 0 | 2 | 5 | [2020](../raketa/app/fcc/phpstan-baseline.neon#L2020) |
| `equal.alwaysTrue` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [23627](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L23627) |
| `foreach.emptyArray` | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 2 | [3826](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L3826) |
| `foreach.nonIterable` | 14 | 14 | 0 | 0 | 0 | 0 | 8 | 6 | [3544](../raketa/app/fcc/phpstan-baseline.neon#L3544) |
| `function.alreadyNarrowedType` | 31 | 31 | 0 | 0 | 0 | 0 | 18 | 13 | [34](../raketa/api/profile-sync/phpstan-baseline.neon#L34) |
| `function.impossibleType` | 29 | 29 | 0 | 0 | 0 | 0 | 15 | 14 | [100](../raketa/app/fcc/phpstan-baseline.neon#L100) |
| `function.notFound` | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 2 | [7342](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L7342) |
| `greater.alwaysFalse` | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 2 | [4342](../raketa/app/fcc/phpstan-baseline.neon#L4342) |
| `greater.alwaysTrue` | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | [23922](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L23922) |
| `greaterOrEqual.alwaysFalse` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [2278](../raketa/app/raketa/code_quality/phpstan-baseline.neon#L2278) |
| `identical.alwaysFalse` | 45 | 45 | 0 | 0 | 0 | 0 | 18 | 27 | [10](../raketa/api/avia/phpstan-baseline.neon#L10) |
| `identical.alwaysTrue` | 5 | 5 | 0 | 0 | 0 | 0 | 5 | 0 | [322](../raketa/api/transfer/phpstan-baseline.neon#L322) |
| `if.alwaysFalse` | 12 | 12 | 0 | 0 | 0 | 0 | 1 | 11 | [4348](../raketa/app/fcc/phpstan-baseline.neon#L4348) |
| `if.alwaysTrue` | 37 | 37 | 0 | 0 | 0 | 0 | 14 | 23 | [4](../raketa/app/fcc/phpstan-baseline.neon#L4) |
| `include.fileNotFound` | 503 | 503 | 0 | 0 | 0 | 0 | 0 | 503 | [10](../raketa/app/fcc/phpstan-baseline.neon#L10) |
| `instanceof.alwaysFalse` | 4 | 4 | 0 | 0 | 0 | 0 | 2 | 2 | [8749](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L8749) |
| `instanceof.alwaysTrue` | 61 | 61 | 0 | 0 | 0 | 0 | 41 | 20 | [130](../raketa/api/profile-sync/phpstan-baseline.neon#L130) |
| `isset.initializedProperty` | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | [106](../raketa/app/raketa/code_quality/phpstan-baseline-phpstan_v2.neon#L106) |
| `isset.offset` | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | [2459](../raketa/app/raketa/code_quality/phpstan-baseline-legacy-phpstan_v2.neon#L2459) |
| `isset.property` | 48 | 48 | 0 | 0 | 0 | 0 | 48 | 0 | [64](../raketa/api/profile-sync/phpstan-baseline.neon#L64) |
| `isset.variable` | 20 | 20 | 0 | 0 | 0 | 0 | 5 | 15 | [682](../raketa/app/fcc/phpstan-baseline.neon#L682) |
| `match.alwaysTrue` | 3 | 3 | 0 | 0 | 0 | 0 | 3 | 0 | [1000](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L1000) |
| `method.childReturnType` | 5 | 0 | 0 | 5 | 0 | 0 | 0 | 0 | [9719](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L9719) |
| `method.nonObject` | 3 | 3 | 0 | 0 | 0 | 0 | 0 | 3 | [2622](../raketa/app/raketa/code_quality/phpstan-baseline-legacy-phpstan_v2.neon#L2622) |
| `method.notFound` | 82 | 82 | 0 | 0 | 0 | 0 | 57 | 25 | [874](../raketa/app/fcc/phpstan-baseline.neon#L874) |
| `method.unused` | 23 | 0 | 0 | 23 | 0 | 0 | 0 | 0 | [19760](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L19760) |
| `method.void` | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1 | [2980](../raketa/app/fcc/phpstan-baseline.neon#L2980) |
| `missingType.generics` | 30 | 1 | 0 | 13 | 16 | 0 | 1 | 0 | [2389](../raketa/app/raketa/code_quality/phpstan-baseline-legacy-phpstan_v2.neon#L2389) |
| `missingType.iterableValue` | 5 029 | 65 | 37 | 4 316 | 420 | 182 | 10 | 64 | [304](../raketa/app/fcc/phpstan-baseline.neon#L304) |
| `missingType.parameter` | 683 | 6 | 95 | 554 | 0 | 28 | 6 | 0 | [6610](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L6610) |
| `missingType.property` | 1 747 | 0 | 0 | 0 | 1 747 | 0 | 0 | 0 | [6064](../raketa/app/fcc/phpstan-baseline.neon#L6064) |
| `missingType.return` | 807 | 0 | 53 | 730 | 0 | 21 | 0 | 3 | [100](../raketa/api/profile-sync/phpstan-baseline.neon#L100) |
| `new.static` | 5 | 5 | 0 | 0 | 0 | 0 | 5 | 0 | [4](../raketa/app/raketa/code_quality/phpstan-baseline.neon#L4) |
| `notIdentical.alwaysFalse` | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | [178](../raketa/app/raketa/api/raketa/phpstan-baseline.neon#L178) |
| `notIdentical.alwaysTrue` | 23 | 23 | 0 | 0 | 0 | 0 | 16 | 7 | [4](../raketa/api/avia/phpstan-baseline.neon#L4) |
| `nullCoalesce.expr` | 35 | 35 | 0 | 0 | 0 | 0 | 18 | 17 | [370](../raketa/app/fcc/phpstan-baseline.neon#L370) |
| `nullCoalesce.initializedProperty` | 3 | 3 | 0 | 0 | 0 | 0 | 3 | 0 | [328](../raketa/app/raketa/code_quality/phpstan-baseline.neon#L328) |
| `nullCoalesce.offset` | 14 | 14 | 0 | 0 | 0 | 0 | 13 | 1 | [58](../raketa/api/static/phpstan-baseline.neon#L58) |
| `nullCoalesce.property` | 43 | 43 | 0 | 0 | 0 | 0 | 41 | 2 | [70](../raketa/api/profile-sync/phpstan-baseline.neon#L70) |
| `nullCoalesce.variable` | 10 | 10 | 0 | 0 | 0 | 0 | 0 | 10 | [1372](../raketa/app/fcc/phpstan-baseline.neon#L1372) |
| `nullsafe.neverNull` | 11 | 11 | 0 | 0 | 0 | 0 | 7 | 4 | [706](../raketa/app/fcc/phpstan-baseline.neon#L706) |
| `offsetAccess.invalidOffset` | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | [28](../raketa/api/avia/phpstan-baseline.neon#L28) |
| `offsetAccess.nonArray` | 5 | 5 | 0 | 0 | 0 | 0 | 5 | 0 | [22](../raketa/api/avia/phpstan-baseline.neon#L22) |
| `offsetAccess.notFound` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [10](../raketa/api/static/phpstan-baseline.neon#L10) |
| `parameter.defaultValue` | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1 | [3916](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L3916) |
| `parameter.notFound` | 26 | 26 | 0 | 0 | 0 | 0 | 25 | 1 | [4780](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L4780) |
| `parameter.phpDocType` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [1054](../raketa/app/raketa/code_quality/phpstan-baseline-presentation.neon#L1054) |
| `parameter.unresolvableType` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [13884](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L13884) |
| `parameterByRef.type` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [274](../raketa/api/transfer/phpstan-baseline.neon#L274) |
| `parameterByRef.unusedType` | 6 | 0 | 0 | 6 | 0 | 0 | 0 | 0 | [340](../raketa/api/transfer/phpstan-baseline.neon#L340) |
| `phpDoc.parseError` | 23 | 23 | 0 | 0 | 0 | 0 | 3 | 20 | [4](../raketa/api/profile-sync/phpstan-baseline.neon#L4) |
| `property.defaultValue` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [16764](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L16764) |
| `property.notFound` | 109 | 109 | 0 | 0 | 0 | 0 | 84 | 25 | [4474](../raketa/app/fcc/phpstan-baseline.neon#L4474) |
| `property.onlyRead` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [16](../raketa/app/raketa/code_quality/phpstan-baseline.neon#L16) |
| `property.onlyWritten` | 6 | 0 | 0 | 0 | 6 | 0 | 0 | 0 | [5818](../raketa/app/fcc/phpstan-baseline.neon#L5818) |
| `property.protected` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [25337](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L25337) |
| `property.unusedType` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [5014](../raketa/app/fcc/phpstan-baseline.neon#L5014) |
| `require.fileNotFound` | 87 | 87 | 0 | 0 | 0 | 0 | 0 | 87 | [136](../raketa/app/fcc/phpstan-baseline.neon#L136) |
| `return.empty` | 5 | 0 | 1 | 4 | 0 | 0 | 0 | 0 | [5248](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L5248) |
| `return.missing` | 3 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | [27102](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L27102) |
| `return.type` | 109 | 0 | 5 | 104 | 0 | 0 | 0 | 0 | [34](../raketa/api/avia/phpstan-baseline.neon#L34) |
| `return.unusedType` | 11 | 0 | 0 | 11 | 0 | 0 | 0 | 0 | [250](../raketa/api/transfer/phpstan-baseline.neon#L250) |
| `staticClassAccess.privateConstant` | 79 | 79 | 0 | 0 | 0 | 0 | 79 | 0 | [32652](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L32652) |
| `staticClassAccess.privateMethod` | 53 | 53 | 0 | 0 | 0 | 0 | 53 | 0 | [20395](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L20395) |
| `staticClassAccess.privateProperty` | 36 | 36 | 0 | 0 | 0 | 0 | 36 | 0 | [8089](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L8089) |
| `staticMethod.resultUnused` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [2194](../raketa/app/raketa/code_quality/phpstan-baseline-legacy-phpstan_v2.neon#L2194) |
| `staticMethod.void` | 5 | 5 | 0 | 0 | 0 | 0 | 1 | 4 | [592](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L592) |
| `staticProperty.notFound` | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 1 | [4882](../raketa/app/fcc/phpstan-baseline.neon#L4882) |
| `ternary.alwaysFalse` | 3 | 3 | 0 | 0 | 0 | 0 | 0 | 3 | [7192](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L7192) |
| `ternary.alwaysTrue` | 30 | 30 | 0 | 0 | 0 | 0 | 11 | 19 | [208](../raketa/app/fcc/phpstan-baseline.neon#L208) |
| `throws.notThrowable` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [5806](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L5806) |
| `throws.unusedType` | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | [472](../raketa/app/raketa/api/raketa/phpstan-baseline.neon#L472) |
| `trait.unused` | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | [2559](../raketa/app/raketa/code_quality/phpstan-baseline-legacy-phpstan_v2.neon#L2559) |
| `varTag.differentVariable` | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 2 | [1762](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L1762) |
| `varTag.misplaced` | 2 | 2 | 0 | 0 | 0 | 0 | 1 | 1 | [694](../raketa/app/raketa/code_quality/phpstan-baseline-legacy.neon#L694) |
| `varTag.nativeType` | 16 | 16 | 0 | 0 | 0 | 0 | 12 | 4 | [10](../raketa/api/profile-sync/phpstan-baseline.neon#L10) |
| `variable.undefined` | 904 | 904 | 0 | 0 | 0 | 0 | 25 | 879 | [40](../raketa/api/avia/phpstan-baseline.neon#L40) |
| `whitespace.fileEnd` | 7 | 7 | 0 | 0 | 0 | 0 | 0 | 7 | [484](../raketa/app/fcc/phpstan-baseline.neon#L484) |

## Идентификаторы PHPat

Идентификаторов: 264.

| identifier | Всего | Без владельца | Функция | Метод | Класс | Нет метода | По файлу | Без маркера | Пример |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `phpat.actionCanImplementButShouldNotDependOn` | 243 | 0 | 0 | 0 | 239 | 0 | 0 | 4 | [3832](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L3832) |
| `phpat.actionCanOnlyImplementOnDomain` | 22 | 0 | 0 | 0 | 22 | 0 | 0 | 0 | [3958](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L3958) |
| `phpat.actionShouldBeNamed` | 18 | 0 | 0 | 0 | 18 | 0 | 0 | 0 | [4018](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L4018) |
| `phpat.actionShouldHaveOnlyOnePublicMethod` | 51 | 0 | 0 | 0 | 51 | 0 | 0 | 0 | [3826](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L3826) |
| `phpat.architectureRulesClientClientContractCommandDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [34](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L34) |
| `phpat.architectureRulesClientClientPersistenceActionDependencies` | 5 | 0 | 0 | 0 | 5 | 0 | 0 | 0 | [40](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L40) |
| `phpat.architectureRulesClientClientPersistenceRepositoryDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [64](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L64) |
| `phpat.architectureRulesClientClientPresentationDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [88](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L88) |
| `phpat.architectureRulesClientClientUseCaseDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [100](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L100) |
| `phpat.architectureRulesClientConsultantDomainServiceDependencies` | 18 | 0 | 0 | 0 | 18 | 0 | 0 | 0 | [118](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L118) |
| `phpat.architectureRulesClientConsultantPersistenceActionDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [232](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L232) |
| `phpat.architectureRulesClientConsultantPresentationDependencies` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [268](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L268) |
| `phpat.architectureRulesClientConsultantUseCaseDependencies` | 23 | 0 | 0 | 0 | 23 | 0 | 0 | 0 | [322](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L322) |
| `phpat.architectureRulesClientContractsPersistenceActionDependencies` | 6 | 0 | 0 | 0 | 6 | 0 | 0 | 0 | [460](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L460) |
| `phpat.architectureRulesClientContractsPresentationDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [496](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L496) |
| `phpat.architectureRulesClientEdoDomainEntityDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [532](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L532) |
| `phpat.architectureRulesClientEdoDomainInterfaceDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [538](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L538) |
| `phpat.architectureRulesClientEdoPersistenceActionDependencies` | 20 | 0 | 0 | 0 | 20 | 0 | 0 | 0 | [562](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L562) |
| `phpat.architectureRulesClientEdoPersistenceActionImplements` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [592](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L592) |
| `phpat.architectureRulesClientEdoPersistenceActionNaming` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [598](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L598) |
| `phpat.architectureRulesClientEdoPersistenceActionSpecial0` | 12 | 0 | 0 | 0 | 12 | 0 | 0 | 0 | [544](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L544) |
| `phpat.architectureRulesClientEdoPersistenceRepositoryDependencies` | 11 | 0 | 0 | 0 | 11 | 0 | 0 | 0 | [760](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L760) |
| `phpat.architectureRulesClientEdoPresentationDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [826](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L826) |
| `phpat.architectureRulesClientEdoUseCaseDependencies` | 21 | 0 | 0 | 0 | 21 | 0 | 0 | 0 | [856](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L856) |
| `phpat.architectureRulesClientFinanceDomainEventHandlerDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [982](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L982) |
| `phpat.architectureRulesClientFinanceDomainServiceDependencies` | 69 | 0 | 0 | 0 | 69 | 0 | 0 | 0 | [1006](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L1006) |
| `phpat.architectureRulesClientFinanceDomainValueObjectDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [136](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L136) |
| `phpat.architectureRulesClientFinancePersistenceRepositoryDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [1288](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L1288) |
| `phpat.architectureRulesClientFinancePresentationDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [1294](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L1294) |
| `phpat.architectureRulesClientProfileDomainEntityDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [1300](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L1300) |
| `phpat.architectureRulesClientProfileDomainFactoryDependencies` | 17 | 0 | 0 | 0 | 17 | 0 | 0 | 0 | [1318](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L1318) |
| `phpat.architectureRulesClientProfileDomainFactoryOnePublicMethod` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [1312](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L1312) |
| `phpat.architectureRulesClientProfileDomainInterfaceDependencies` | 21 | 0 | 0 | 0 | 21 | 0 | 0 | 0 | [1426](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L1426) |
| `phpat.architectureRulesClientProfileDomainServiceDependencies` | 44 | 0 | 0 | 0 | 44 | 0 | 0 | 0 | [1552](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L1552) |
| `phpat.architectureRulesClientProfileDomainValueObjectDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [1816](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L1816) |
| `phpat.architectureRulesClientProfilePersistenceActionDependencies` | 132 | 0 | 0 | 0 | 132 | 0 | 0 | 0 | [1828](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L1828) |
| `phpat.architectureRulesClientProfilePersistenceActionImplements` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [2272](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L2272) |
| `phpat.architectureRulesClientProfilePersistenceActionNaming` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [2278](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L2278) |
| `phpat.architectureRulesClientProfilePersistenceActionOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [2284](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L2284) |
| `phpat.architectureRulesClientProfilePersistenceRepositoryDependencies` | 67 | 0 | 0 | 0 | 67 | 0 | 0 | 0 | [142](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L142) |
| `phpat.architectureRulesClientProfilePresentationDependencies` | 254 | 0 | 0 | 0 | 254 | 0 | 0 | 0 | [3172](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L3172) |
| `phpat.architectureRulesClientProfileUseCaseDependencies` | 218 | 0 | 0 | 0 | 218 | 0 | 0 | 0 | [4912](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L4912) |
| `phpat.architectureRulesClientSignInDomainEntityDependencies` | 5 | 0 | 0 | 0 | 5 | 0 | 0 | 0 | [6220](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L6220) |
| `phpat.architectureRulesClientSignInDomainFactoryDependencies` | 10 | 0 | 0 | 0 | 10 | 0 | 0 | 0 | [6250](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L6250) |
| `phpat.architectureRulesClientSignInDomainInterfaceDependencies` | 6 | 0 | 0 | 0 | 6 | 0 | 0 | 0 | [6310](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L6310) |
| `phpat.architectureRulesClientSignInDomainServiceDependencies` | 41 | 0 | 0 | 0 | 41 | 0 | 0 | 0 | [6346](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L6346) |
| `phpat.architectureRulesClientSignInPersistenceActionDependencies` | 56 | 0 | 0 | 0 | 56 | 0 | 0 | 0 | [6598](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L6598) |
| `phpat.architectureRulesClientSignInPersistenceActionNaming` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [6706](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L6706) |
| `phpat.architectureRulesClientSignInPersistenceActionOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [6682](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L6682) |
| `phpat.architectureRulesClientSignInPersistenceRepositoryDependencies` | 18 | 0 | 0 | 0 | 18 | 0 | 0 | 0 | [6988](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L6988) |
| `phpat.architectureRulesClientSignInPresentationDependencies` | 34 | 0 | 0 | 0 | 34 | 0 | 0 | 0 | [7096](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L7096) |
| `phpat.architectureRulesClientSignInUseCaseDependencies` | 111 | 0 | 0 | 0 | 111 | 0 | 0 | 0 | [7246](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L7246) |
| `phpat.architectureRulesClientSignInUseCaseOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [7342](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L7342) |
| `phpat.architectureRulesCommonBannersPersistenceActionDependencies` | 8 | 0 | 0 | 0 | 8 | 0 | 0 | 0 | [7918](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L7918) |
| `phpat.architectureRulesCommonBannersPersistenceFactoryDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [7966](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L7966) |
| `phpat.architectureRulesCommonChatsDomainServiceDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [7972](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L7972) |
| `phpat.architectureRulesCommonHistoryLogPersistenceActionDependencies` | 18 | 0 | 0 | 0 | 18 | 0 | 0 | 0 | [7978](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L7978) |
| `phpat.architectureRulesCommonNotificationSystemDomainEntityDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [8086](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8086) |
| `phpat.architectureRulesCommonNotificationSystemDomainEventHandlerDependencies` | 32 | 0 | 0 | 0 | 32 | 0 | 0 | 0 | [4](../raketa/app/raketa/code_quality/phpstan-baseline-phpstan_v2.neon#L4) |
| `phpat.architectureRulesCommonNotificationSystemDomainInterfaceDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [8248](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8248) |
| `phpat.architectureRulesCommonNotificationSystemPersistenceActionDependencies` | 13 | 0 | 0 | 0 | 13 | 0 | 0 | 0 | [8254](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8254) |
| `phpat.architectureRulesCommonOneCConnectorsPersistenceActionDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [16](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L16) |
| `phpat.architectureRulesCommonOneCConnectorsPresentationDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [10](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L10) |
| `phpat.architectureRulesCommonPaymentGatewayDomainFactoryDependencies` | 8 | 0 | 0 | 0 | 8 | 0 | 0 | 0 | [8320](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8320) |
| `phpat.architectureRulesCommonPaymentGatewayDomainInterfaceDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [8356](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8356) |
| `phpat.architectureRulesCommonPaymentGatewayDomainInterfaceOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [8362](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8362) |
| `phpat.architectureRulesCommonPaymentGatewayDomainServiceDependencies` | 10 | 0 | 0 | 0 | 10 | 0 | 0 | 0 | [8368](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8368) |
| `phpat.architectureRulesCommonPaymentGatewayPersistenceActionDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [8422](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8422) |
| `phpat.architectureRulesCommonPaymentGatewayPersistenceActionOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [8428](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8428) |
| `phpat.architectureRulesCommonPaymentGatewayPersistenceRepositoryDependencies` | 8 | 0 | 0 | 0 | 8 | 0 | 0 | 0 | [8434](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8434) |
| `phpat.architectureRulesCommonPaymentGatewayPresentationDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [8482](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8482) |
| `phpat.architectureRulesCommonPaymentGatewayUseCaseDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [8506](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8506) |
| `phpat.architectureRulesCommonSurveyDomainServiceDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [8512](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8512) |
| `phpat.architectureRulesCommonSurveyPersistenceRepositoryDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [8536](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8536) |
| `phpat.architectureRulesCommonSurveyPresentationDependencies` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [8548](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8548) |
| `phpat.architectureRulesCommonSurveyUseCaseDependencies` | 6 | 0 | 0 | 0 | 6 | 0 | 0 | 0 | [8566](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8566) |
| `phpat.architectureRulesCommonSystemContractPersistenceRepositoryDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [208](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L208) |
| `phpat.architectureRulesCommonTranslationPersistenceActionDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [214](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L214) |
| `phpat.architectureRulesOrderAeroexpressServiceDomainDtoDependencies` | 6 | 0 | 0 | 0 | 6 | 0 | 0 | 0 | [8614](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8614) |
| `phpat.architectureRulesOrderAeroexpressServiceDomainEntityDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [8650](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8650) |
| `phpat.architectureRulesOrderAeroexpressServiceDomainFactoryDependencies` | 32 | 0 | 0 | 0 | 32 | 0 | 0 | 0 | [8656](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8656) |
| `phpat.architectureRulesOrderAeroexpressServiceDomainFactoryNaming` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [8806](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8806) |
| `phpat.architectureRulesOrderAeroexpressServiceDomainInterfaceDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [8854](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8854) |
| `phpat.architectureRulesOrderAeroexpressServiceDomainServiceDependencies` | 25 | 0 | 0 | 0 | 25 | 0 | 0 | 0 | [8860](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L8860) |
| `phpat.architectureRulesOrderAeroexpressServiceDomainValueObjectDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [9010](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L9010) |
| `phpat.architectureRulesOrderAeroexpressServicePersistenceActionDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [9022](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L9022) |
| `phpat.architectureRulesOrderAeroexpressServicePersistenceFactoryDependencies` | 7 | 0 | 0 | 0 | 7 | 0 | 0 | 0 | [9034](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L9034) |
| `phpat.architectureRulesOrderAeroexpressServicePersistenceRepositoryDependencies` | 11 | 0 | 0 | 0 | 11 | 0 | 0 | 0 | [9082](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L9082) |
| `phpat.architectureRulesOrderAeroexpressServicePresentationDependencies` | 94 | 0 | 0 | 0 | 94 | 0 | 0 | 0 | [9148](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L9148) |
| `phpat.architectureRulesOrderAeroexpressServiceUseCaseDependencies` | 82 | 0 | 0 | 0 | 82 | 0 | 0 | 0 | [9784](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L9784) |
| `phpat.architectureRulesOrderAeroexpressServiceUseCaseNaming` | 5 | 0 | 0 | 0 | 5 | 0 | 0 | 0 | [9820](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L9820) |
| `phpat.architectureRulesOrderAeroexpressServiceUseCaseOnePublicMethod` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [9964](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L9964) |
| `phpat.architectureRulesOrderAviaServiceDomainDtoDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [10312](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L10312) |
| `phpat.architectureRulesOrderAviaServiceDomainEntityDependencies` | 29 | 0 | 0 | 0 | 29 | 0 | 0 | 0 | [10336](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L10336) |
| `phpat.architectureRulesOrderAviaServiceDomainFactoryDependencies` | 59 | 0 | 0 | 0 | 59 | 0 | 0 | 0 | [10510](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L10510) |
| `phpat.architectureRulesOrderAviaServiceDomainFactoryOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [10786](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L10786) |
| `phpat.architectureRulesOrderAviaServiceDomainInterfaceDependencies` | 12 | 0 | 0 | 0 | 12 | 0 | 0 | 0 | [10870](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L10870) |
| `phpat.architectureRulesOrderAviaServiceDomainInterfaceOnePublicMethod` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [10876](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L10876) |
| `phpat.architectureRulesOrderAviaServiceDomainServiceDependencies` | 42 | 0 | 0 | 0 | 42 | 0 | 0 | 0 | [10954](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L10954) |
| `phpat.architectureRulesOrderAviaServicePersistenceActionDependencies` | 114 | 0 | 0 | 0 | 114 | 0 | 0 | 0 | [11206](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L11206) |
| `phpat.architectureRulesOrderAviaServicePersistenceActionOnePublicMethod` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [11242](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L11242) |
| `phpat.architectureRulesOrderAviaServicePersistenceRepositoryDependencies` | 32 | 0 | 0 | 0 | 32 | 0 | 0 | 0 | [12166](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L12166) |
| `phpat.architectureRulesOrderAviaServicePresentationDependencies` | 144 | 0 | 0 | 0 | 144 | 0 | 0 | 0 | [12340](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L12340) |
| `phpat.architectureRulesOrderAviaServiceUseCaseDependencies` | 141 | 0 | 0 | 0 | 141 | 0 | 0 | 0 | [13252](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L13252) |
| `phpat.architectureRulesOrderBusServiceContractRequestDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [14098](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14098) |
| `phpat.architectureRulesOrderBusServicePersistenceActionDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [14104](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14104) |
| `phpat.architectureRulesOrderBusServicePersistenceRepositoryDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [14110](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14110) |
| `phpat.architectureRulesOrderBusServicePresentationDependencies` | 20 | 0 | 0 | 0 | 20 | 0 | 0 | 0 | [14122](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14122) |
| `phpat.architectureRulesOrderBusServiceUseCaseDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [14242](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14242) |
| `phpat.architectureRulesOrderCartDomainAggregateDependencies` | 8 | 0 | 0 | 0 | 8 | 0 | 0 | 0 | [14254](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14254) |
| `phpat.architectureRulesOrderCartDomainEntityDependencies` | 6 | 0 | 0 | 0 | 6 | 0 | 0 | 0 | [14302](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14302) |
| `phpat.architectureRulesOrderCartDomainEventDependencies` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [14338](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14338) |
| `phpat.architectureRulesOrderCartDomainFactoryDependencies` | 24 | 0 | 0 | 0 | 24 | 0 | 0 | 0 | [14356](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14356) |
| `phpat.architectureRulesOrderCartDomainFactoryNaming` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [14362](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14362) |
| `phpat.architectureRulesOrderCartDomainServiceDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [14512](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14512) |
| `phpat.architectureRulesOrderCartDomainValueObjectDependencies` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [14524](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14524) |
| `phpat.architectureRulesOrderCartPersistenceActionDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [14536](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14536) |
| `phpat.architectureRulesOrderCartPersistenceFactoryDependencies` | 28 | 0 | 0 | 0 | 28 | 0 | 0 | 0 | [14560](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14560) |
| `phpat.architectureRulesOrderCartPresentationDependencies` | 7 | 0 | 0 | 0 | 7 | 0 | 0 | 0 | [14728](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14728) |
| `phpat.architectureRulesOrderEntityAuthorizationDomainEntityDependencies` | 64 | 0 | 0 | 0 | 64 | 0 | 0 | 0 | [14770](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L14770) |
| `phpat.architectureRulesOrderEntityAuthorizationDomainFactoryDependencies` | 101 | 0 | 0 | 0 | 101 | 0 | 0 | 0 | [15154](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L15154) |
| `phpat.architectureRulesOrderEntityAuthorizationDomainFactoryNaming` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [15148](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L15148) |
| `phpat.architectureRulesOrderEntityAuthorizationDomainFactoryOnePublicMethod` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [15196](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L15196) |
| `phpat.architectureRulesOrderEntityAuthorizationDomainInterfaceDependencies` | 26 | 0 | 0 | 0 | 26 | 0 | 0 | 0 | [15790](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L15790) |
| `phpat.architectureRulesOrderEntityAuthorizationDomainInterfaceNaming` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [15952](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L15952) |
| `phpat.architectureRulesOrderEntityAuthorizationDomainInterfaceOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [15904](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L15904) |
| `phpat.architectureRulesOrderEntityAuthorizationDomainServiceDependencies` | 348 | 0 | 0 | 0 | 348 | 0 | 0 | 0 | [15958](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L15958) |
| `phpat.architectureRulesOrderEntityAuthorizationPersistenceActionDependencies` | 175 | 0 | 0 | 0 | 175 | 0 | 0 | 0 | [17962](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L17962) |
| `phpat.architectureRulesOrderEntityAuthorizationPersistenceActionOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [18502](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L18502) |
| `phpat.architectureRulesOrderEntityAuthorizationPersistenceActionSpecial0` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [18634](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L18634) |
| `phpat.architectureRulesOrderEntityAuthorizationPersistenceRepositoryDependencies` | 15 | 0 | 0 | 0 | 15 | 0 | 0 | 0 | [19186](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L19186) |
| `phpat.architectureRulesOrderEntityAuthorizationPresentationDependencies` | 83 | 0 | 0 | 0 | 83 | 0 | 0 | 0 | [19276](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L19276) |
| `phpat.architectureRulesOrderEntityAuthorizationUseCaseDependencies` | 219 | 0 | 0 | 0 | 219 | 0 | 0 | 0 | [19774](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L19774) |
| `phpat.architectureRulesOrderExpenseReportDomainEventDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [21088](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21088) |
| `phpat.architectureRulesOrderExpenseReportDomainInterfaceDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [21094](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21094) |
| `phpat.architectureRulesOrderExpenseReportPersistenceActionDependencies` | 10 | 0 | 0 | 0 | 10 | 0 | 0 | 0 | [21106](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21106) |
| `phpat.architectureRulesOrderExpenseReportPersistenceRepositoryDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [21166](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21166) |
| `phpat.architectureRulesOrderExpenseReportUseCaseDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [21172](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21172) |
| `phpat.architectureRulesOrderHotelServiceDomainServiceDependencies` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [400](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L400) |
| `phpat.architectureRulesOrderInsuranceServiceContractCommandDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [21178](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21178) |
| `phpat.architectureRulesOrderInsuranceServiceDomainDtoDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [21184](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21184) |
| `phpat.architectureRulesOrderInsuranceServiceDomainEntityDependencies` | 15 | 0 | 0 | 0 | 15 | 0 | 0 | 0 | [21196](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21196) |
| `phpat.architectureRulesOrderInsuranceServiceDomainInterfaceDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [21286](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21286) |
| `phpat.architectureRulesOrderInsuranceServiceDomainServiceDependencies` | 57 | 0 | 0 | 0 | 57 | 0 | 0 | 0 | [21292](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21292) |
| `phpat.architectureRulesOrderInsuranceServicePersistenceActionDependencies` | 23 | 0 | 0 | 0 | 23 | 0 | 0 | 0 | [21622](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21622) |
| `phpat.architectureRulesOrderInsuranceServicePersistenceRepositoryDependencies` | 12 | 0 | 0 | 0 | 12 | 0 | 0 | 0 | [21832](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21832) |
| `phpat.architectureRulesOrderInsuranceServicePresentationDependencies` | 25 | 0 | 0 | 0 | 25 | 0 | 0 | 0 | [21898](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21898) |
| `phpat.architectureRulesOrderInsuranceServiceUseCaseDependencies` | 17 | 0 | 0 | 0 | 17 | 0 | 0 | 0 | [22060](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L22060) |
| `phpat.architectureRulesOrderOfferSuiteDomainEntityDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [22162](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L22162) |
| `phpat.architectureRulesOrderOfferSuiteDomainFactoryDependencies` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [22168](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L22168) |
| `phpat.architectureRulesOrderOfferSuiteDomainServiceDependencies` | 24 | 0 | 0 | 0 | 24 | 0 | 0 | 0 | [22186](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L22186) |
| `phpat.architectureRulesOrderOfferSuiteDomainValueObjectDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [22330](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L22330) |
| `phpat.architectureRulesOrderOfferSuitePersistenceActionDependencies` | 8 | 0 | 0 | 0 | 8 | 0 | 0 | 0 | [22354](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L22354) |
| `phpat.architectureRulesOrderOfferSuitePersistenceRepositoryDependencies` | 62 | 0 | 0 | 0 | 62 | 0 | 0 | 0 | [22408](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L22408) |
| `phpat.architectureRulesOrderOfferSuitePresentationDependencies` | 168 | 0 | 0 | 0 | 168 | 0 | 0 | 0 | [22780](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L22780) |
| `phpat.architectureRulesOrderOfferSuiteUseCaseDependencies` | 12 | 0 | 0 | 0 | 12 | 0 | 0 | 0 | [23872](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L23872) |
| `phpat.architectureRulesOrderOrderContractCommandDependencies` | 5 | 0 | 0 | 0 | 5 | 0 | 0 | 0 | [23944](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L23944) |
| `phpat.architectureRulesOrderOrderDomainDtoDependencies` | 9 | 0 | 0 | 0 | 9 | 0 | 0 | 0 | [23974](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L23974) |
| `phpat.architectureRulesOrderOrderDomainEntityDependencies` | 52 | 0 | 0 | 0 | 52 | 0 | 0 | 0 | [24010](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L24010) |
| `phpat.architectureRulesOrderOrderDomainEventDependencies` | 6 | 0 | 0 | 0 | 6 | 0 | 0 | 0 | [24322](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L24322) |
| `phpat.architectureRulesOrderOrderDomainEventHandlerDependencies` | 9 | 0 | 0 | 0 | 9 | 0 | 0 | 0 | [24352](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L24352) |
| `phpat.architectureRulesOrderOrderDomainFactoryDependencies` | 79 | 0 | 0 | 0 | 79 | 0 | 0 | 0 | [24406](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L24406) |
| `phpat.architectureRulesOrderOrderDomainFactoryNaming` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [24646](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L24646) |
| `phpat.architectureRulesOrderOrderDomainFactoryOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [24754](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L24754) |
| `phpat.architectureRulesOrderOrderDomainInterfaceDependencies` | 36 | 0 | 0 | 0 | 36 | 0 | 0 | 0 | [24898](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L24898) |
| `phpat.architectureRulesOrderOrderDomainInterfaceOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [25024](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L25024) |
| `phpat.architectureRulesOrderOrderDomainServiceDependencies` | 535 | 0 | 0 | 0 | 535 | 0 | 0 | 0 | [544](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L544) |
| `phpat.architectureRulesOrderOrderPersistenceActionDependencies` | 207 | 0 | 0 | 0 | 207 | 0 | 0 | 0 | [28180](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L28180) |
| `phpat.architectureRulesOrderOrderPersistenceActionOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [29123](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L29123) |
| `phpat.architectureRulesOrderOrderPersistenceFactoryDependencies` | 18 | 0 | 0 | 0 | 18 | 0 | 0 | 0 | [29369](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L29369) |
| `phpat.architectureRulesOrderOrderPersistenceRepositoryDependencies` | 34 | 0 | 0 | 0 | 34 | 0 | 0 | 0 | [29615](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L29615) |
| `phpat.architectureRulesOrderOrderPresentationDependencies` | 181 | 0 | 0 | 0 | 181 | 0 | 0 | 0 | [29819](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L29819) |
| `phpat.architectureRulesOrderOrderTestDomainServiceDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [298](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L298) |
| `phpat.architectureRulesOrderOrderUseCaseDependencies` | 112 | 0 | 0 | 0 | 110 | 0 | 0 | 2 | [30905](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L30905) |
| `phpat.architectureRulesOrderPriceDomainEntityDependencies` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [31559](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L31559) |
| `phpat.architectureRulesOrderPriceDomainFactoryDependencies` | 6 | 0 | 0 | 0 | 6 | 0 | 0 | 0 | [31577](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L31577) |
| `phpat.architectureRulesOrderPriceDomainFactoryOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [31613](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L31613) |
| `phpat.architectureRulesOrderPriceDomainServiceDependencies` | 5 | 0 | 0 | 0 | 5 | 0 | 0 | 0 | [31619](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L31619) |
| `phpat.architectureRulesOrderPricePresentationDependencies` | 6 | 0 | 0 | 0 | 6 | 0 | 0 | 0 | [31649](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L31649) |
| `phpat.architectureRulesOrderTaxiLimitServiceDomainEntityDependencies` | 5 | 0 | 0 | 0 | 5 | 0 | 0 | 0 | [31685](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L31685) |
| `phpat.architectureRulesOrderTaxiLimitServiceDomainServiceDependencies` | 47 | 0 | 0 | 0 | 47 | 0 | 0 | 0 | [31715](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L31715) |
| `phpat.architectureRulesOrderTaxiLimitServicePersistenceActionDependencies` | 18 | 0 | 0 | 0 | 18 | 0 | 0 | 0 | [31997](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L31997) |
| `phpat.architectureRulesOrderTaxiLimitServicePersistenceRepositoryDependencies` | 9 | 0 | 0 | 0 | 9 | 0 | 0 | 0 | [32153](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L32153) |
| `phpat.architectureRulesOrderTaxiLimitServicePresentationDependencies` | 66 | 0 | 0 | 0 | 66 | 0 | 0 | 0 | [32207](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L32207) |
| `phpat.architectureRulesOrderTaxiLimitServiceUseCaseDependencies` | 57 | 0 | 0 | 0 | 57 | 0 | 0 | 0 | [32603](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L32603) |
| `phpat.architectureRulesOrderTaxiLimitServiceUseCaseOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [32945](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L32945) |
| `phpat.architectureRulesSelfTestSelfTestContractCommandDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [10](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L10) |
| `phpat.architectureRulesSelfTestSelfTestContractCommandImplements` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [16](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L16) |
| `phpat.architectureRulesSelfTestSelfTestContractCommandOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [22](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L22) |
| `phpat.architectureRulesSelfTestSelfTestContractRequestDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [28](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L28) |
| `phpat.architectureRulesSelfTestSelfTestContractRequestSpecial0` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [40](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L40) |
| `phpat.architectureRulesSelfTestSelfTestContractResponseDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [46](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L46) |
| `phpat.architectureRulesSelfTestSelfTestContractResponseSpecial0` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [52](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L52) |
| `phpat.architectureRulesSelfTestSelfTestDomainAggregateDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [58](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L58) |
| `phpat.architectureRulesSelfTestSelfTestDomainDtoDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [64](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L64) |
| `phpat.architectureRulesSelfTestSelfTestDomainEntityDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [70](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L70) |
| `phpat.architectureRulesSelfTestSelfTestDomainEventDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [76](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L76) |
| `phpat.architectureRulesSelfTestSelfTestDomainEventHandlerDependencies` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [82](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L82) |
| `phpat.architectureRulesSelfTestSelfTestDomainFactoryDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [94](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L94) |
| `phpat.architectureRulesSelfTestSelfTestDomainFactoryNaming` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [100](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L100) |
| `phpat.architectureRulesSelfTestSelfTestDomainFactoryOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [106](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L106) |
| `phpat.architectureRulesSelfTestSelfTestDomainInterfaceNaming` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [112](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L112) |
| `phpat.architectureRulesSelfTestSelfTestDomainInterfaceOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [118](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L118) |
| `phpat.architectureRulesSelfTestSelfTestDomainServiceDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [124](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L124) |
| `phpat.architectureRulesSelfTestSelfTestPersistenceActionDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [310](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L310) |
| `phpat.architectureRulesSelfTestSelfTestPersistenceActionImplements` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [136](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L136) |
| `phpat.architectureRulesSelfTestSelfTestPersistenceActionNaming` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [142](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L142) |
| `phpat.architectureRulesSelfTestSelfTestPersistenceActionOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [148](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L148) |
| `phpat.architectureRulesSelfTestSelfTestPersistenceActionSpecial0` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [130](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L130) |
| `phpat.architectureRulesSelfTestSelfTestPersistenceFactoryDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [172](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L172) |
| `phpat.architectureRulesSelfTestSelfTestPersistenceFactoryOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [178](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L178) |
| `phpat.architectureRulesSelfTestSelfTestPersistenceRepositoryDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [202](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L202) |
| `phpat.architectureRulesSelfTestSelfTestPresentationDependencies` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [226](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L226) |
| `phpat.architectureRulesSelfTestSelfTestUseCaseDependencies` | 4 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | [232](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L232) |
| `phpat.architectureRulesSelfTestSelfTestUseCaseNaming` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [262](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L262) |
| `phpat.architectureRulesSelfTestSelfTestUseCaseOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [268](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L268) |
| `phpat.architectureRulesSharedContractCommandImplements` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [32951](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L32951) |
| `phpat.architectureRulesSharedDomainEventDependencies` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [32963](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L32963) |
| `phpat.architectureRulesSharedPersistenceActionDependencies` | 7 | 0 | 0 | 0 | 7 | 0 | 0 | 0 | [33011](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L33011) |
| `phpat.architectureRulesSharedPersistenceActionImplements` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [33029](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L33029) |
| `phpat.architectureRulesSharedPresentationDependencies` | 237 | 0 | 0 | 0 | 237 | 0 | 0 | 0 | [33059](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L33059) |
| `phpat.architectureRulescommandBanSelfTestSelfTest` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [184](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L184) |
| `phpat.architectureRulescontextDependenciesImplementRequestInterface` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [4](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L4) |
| `phpat.architectureRulescontextGroupRuleOrderDomainServiceForeignInterfaceOrderAviaService` | 3 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | [232](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L232) |
| `phpat.architectureRulescontextGroupRuleOrderDomainServiceForeignInterfaceOrderEntityAuthorization` | 9 | 0 | 0 | 0 | 9 | 0 | 0 | 0 | [346](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L346) |
| `phpat.architectureRulescontextGroupRuleOrderDomainServiceForeignInterfaceOrderInsuranceService` | 8 | 0 | 0 | 0 | 8 | 0 | 0 | 0 | [418](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L418) |
| `phpat.architectureRulescontextGroupRuleOrderDomainServiceForeignInterfaceOrderOfferSuite` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [478](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L478) |
| `phpat.architectureRulescontextGroupRuleOrderDomainServiceForeignInterfaceOrderOrder` | 21 | 0 | 0 | 0 | 21 | 0 | 0 | 0 | [604](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L604) |
| `phpat.architectureRulescontextGroupRuleOrderDomainServiceForeignInterfaceOrderOrderTest` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [304](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L304) |
| `phpat.architectureRulescontextGroupRuleOrderDomainServiceForeignInterfaceOrderTaxiLimitService` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [850](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L850) |
| `phpat.architectureRulescontextStructureClientEdo` | 5 | 0 | 0 | 0 | 5 | 0 | 0 | 0 | [508](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L508) |
| `phpat.architectureRulescontextStructureClientProfile` | 63 | 0 | 0 | 0 | 63 | 0 | 0 | 0 | [2638](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L2638) |
| `phpat.architectureRulescontextStructureClientSignIn` | 8 | 0 | 0 | 0 | 8 | 0 | 0 | 0 | [6940](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L6940) |
| `phpat.architectureRulescontextStructureOrderAeroexpressService` | 14 | 0 | 0 | 0 | 14 | 0 | 0 | 0 | [9070](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L9070) |
| `phpat.architectureRulescontextStructureOrderAviaService` | 53 | 0 | 0 | 0 | 53 | 0 | 0 | 0 | [11902](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L11902) |
| `phpat.architectureRulescontextStructureOrderEntityAuthorization` | 27 | 0 | 0 | 0 | 27 | 0 | 0 | 0 | [19024](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L19024) |
| `phpat.architectureRulescontextStructureOrderInsuranceService` | 14 | 0 | 0 | 0 | 14 | 0 | 0 | 0 | [21760](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L21760) |
| `phpat.architectureRulescontextStructureOrderOfferSuite` | 15 | 0 | 0 | 0 | 15 | 0 | 0 | 0 | [22402](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L22402) |
| `phpat.architectureRulescontextStructureOrderOrder` | 30 | 0 | 0 | 0 | 30 | 0 | 0 | 0 | [29477](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L29477) |
| `phpat.architectureRulescontextStructureOrderTaxiLimitService` | 9 | 0 | 0 | 0 | 9 | 0 | 0 | 0 | [32105](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L32105) |
| `phpat.architectureRulescontextStructureShared` | 74 | 0 | 0 | 0 | 74 | 0 | 0 | 0 | [32957](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L32957) |
| `phpat.architectureRulescrossContextCallOrderOrder` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [24628](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L24628) |
| `phpat.architectureRulescrossContextCallSelfTestSelfTest` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [190](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L190) |
| `phpat.architectureRulescrossContextResponseOrderOrder` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [24634](../raketa/app/raketa/code_quality/phpstan-baseline-architecture.neon#L24634) |
| `phpat.architectureRulescrossContextResponseSelfTestSelfTest` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [196](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L196) |
| `phpat.architectureRulessharedIsolation` | 81 | 0 | 0 | 0 | 81 | 0 | 0 | 0 | [274](../raketa/app/raketa/code_quality/phpstan-baseline-selfTest.neon#L274) |
| `phpat.controllerCanOnlyDependOn` | 26 | 0 | 0 | 0 | 26 | 0 | 0 | 0 | [10](../raketa/app/raketa/api/agency/phpstan-baseline.neon#L10) |
| `phpat.controllerShouldBeNamed` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [58](../raketa/app/raketa/api/agency/phpstan-baseline.neon#L58) |
| `phpat.controllerShouldExtendAbstractController` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [64](../raketa/app/raketa/api/agency/phpstan-baseline.neon#L64) |
| `phpat.controllerShouldHaveOnlyOnePublicMethod` | 20 | 0 | 0 | 0 | 20 | 0 | 0 | 0 | [4](../raketa/app/raketa/api/agency/phpstan-baseline.neon#L4) |
| `phpat.domainCanOnlyDependOn` | 371 | 0 | 0 | 0 | 371 | 0 | 0 | 0 | [1324](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L1324) |
| `phpat.domainEntityCanOnlyDependOn` | 107 | 0 | 0 | 0 | 107 | 0 | 0 | 0 | [1450](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L1450) |
| `phpat.entityCanOnlyDependOn` | 6 | 0 | 0 | 0 | 6 | 0 | 0 | 0 | [9478](../raketa/app/raketa/code_quality/phpstan-baseline.neon#L9478) |
| `phpat.formCanOnlyDependOn` | 503 | 0 | 0 | 0 | 502 | 0 | 0 | 1 | [4](../raketa/app/raketa/api/mobile/phpstan-baseline.neon#L4) |
| `phpat.formShouldExtendForm` | 3 | 0 | 0 | 0 | 2 | 0 | 0 | 1 | [46](../raketa/app/raketa/api/mobile/phpstan-baseline.neon#L46) |
| `phpat.modelCanOnlyDependOn` | 164 | 0 | 0 | 0 | 164 | 0 | 0 | 0 | [172](../raketa/app/raketa/api/agency/phpstan-baseline.neon#L172) |
| `phpat.operationCanOnlyDependOn` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | [262](../raketa/app/raketa/api/mobile/phpstan-baseline.neon#L262) |
| `phpat.operationShouldHaveOnlyOnePublicMethod` | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | [256](../raketa/app/raketa/api/mobile/phpstan-baseline.neon#L256) |
| `phpat.repositoryCanOnlyDependOn` | 63 | 0 | 0 | 0 | 63 | 0 | 0 | 0 | [6154](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L6154) |
| `phpat.requestTranslatorShouldNotDependOn` | 80 | 0 | 0 | 0 | 78 | 0 | 0 | 2 | [304](../raketa/app/raketa/api/agency/phpstan-baseline.neon#L304) |
| `phpat.useCaseCanOnlyDependOn` | 131 | 0 | 0 | 0 | 131 | 0 | 0 | 0 | [10468](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L10468) |
| `phpat.useCaseShouldBeNamed` | 8 | 0 | 0 | 0 | 8 | 0 | 0 | 0 | [10618](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L10618) |
| `phpat.useCaseShouldHaveOnlyOnePublicMethod` | 16 | 0 | 0 | 0 | 16 | 0 | 0 | 0 | [10588](../raketa/app/raketa/code_quality/phpstan-baseline-layer.neon#L10588) |
| `phpat.viewCanOnlyDependOn` | 480 | 0 | 0 | 0 | 478 | 0 | 0 | 2 | [682](../raketa/app/raketa/api/agency/phpstan-baseline.neon#L682) |

## Другие пользовательские идентификаторы

Идентификаторов: 5.

| identifier | Всего | Без владельца | Функция | Метод | Класс | Нет метода | По файлу | Без маркера | Пример |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `method.call` | 113 | 113 | 0 | 0 | 0 | 0 | 47 | 66 | [502](../raketa/app/raketa/code_quality/phpstan-baseline-legacy-mixed.neon#L502) |
| `new.on.mixed` | 11 | 11 | 0 | 0 | 0 | 0 | 10 | 1 | [118](../raketa/app/raketa/code_quality/phpstan-baseline-mixed.neon#L118) |
| `property.fetch` | 1 106 | 1 106 | 0 | 0 | 0 | 0 | 551 | 555 | [4](../raketa/app/raketa/code_quality/phpstan-baseline-legacy-mixed.neon#L4) |
| `public.method.unused` | 241 | 0 | 0 | 236 | 0 | 5 | 0 | 0 | [4](../raketa/app/raketa/api/agency/phpstan-baseline-phpstav_v2.neon#L4) |
| `translate.contextCodeKey` | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 0 | [586](../raketa/app/raketa/api/mobile/phpstan-baseline.neon#L586) |

## Записи без идентификатора

Поле `identifier` отсутствует; поиск по файлу и распознанному владельцу сохраняется.

| identifier | Всего | Без владельца | Функция | Метод | Класс | Нет метода | По файлу | Без маркера | Пример |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Без `identifier` | 358 | 34 | 0 | 208 | 116 | 0 | 34 | 0 | [1774](../raketa/api/rail/phpstan-baseline.neon#L1774) |

## Общие числа

| Всего | Без владельца | Функция | Метод | Класс | Нет метода | По файлу | Без маркера |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 22 142 | 4 710 | 192 | 6 225 | 10 755 | 236 | 1 860 | 2 874 |
