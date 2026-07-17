# 300 Table Validator 实现

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Source: `ai-dev/design/nop-core/02-table-validator-design.md`

## Purpose

实现 `table-validator.xdef` 及其编译运行时 `ModelBasedTableValidator<T>`，使 Nop 平台具备对表格数据集的三层验证能力：逐行检查、列统计量检查、全表属性检查。参考 `ValidatorModel` → `ModelBasedValidator` 的模式。

## Current Baseline

- `validator.xdef` + `ModelBasedValidator` 已存在（`nop-core`）
- `filter.xdef` 定义了 filter-bean 条件元素：`ge/le/gt/lt/eq/ne/between/isNull/regex` 等
- `nop-utils/` 是现有 utils 模块集合
- **不存在** `table-validator.xdef`
- **不存在** `ITableValidator<T>` / `ITableDataAdaptor<T>` / `ModelBasedTableValidator<T>`
- **不存在** `TableValidatorEngine`（push-based 内部引擎）
- **不存在** per-column 统计累加器

## Goals

- 定义 `table-validator.xdef`，包含三层检查的语义
- 参考 ValidatorModel 编译模式：`TableValidatorModel` + `ITableDataAdaptor<T>` → `ModelBasedTableValidator<T>`
- 泛型接口 `ITableValidator<T>` + 行适配器 `ITableDataAdaptor<T>`
- row-level 复用 `validator.xdef` + `ModelBasedValidator`
- stat-level 内联统计累加 + shortcut 映射到 filter-bean 的 `ge/le/gt/lt/between`
- `TablesawTableDataAdaptor` 适配器（`nop-tablesaw`）

## Non-Goals

- 不依赖任何外部数据结构
- 不做 undo/历史模型 / Profiler / 标准化断言库 / 报告渲染

## Scope

### In Scope

- `nop-xdefs`: `table-validator.xdef` 定义
- `nop-core`: bean 模型 / register-model.xml
- `nop-utils/nop-table-validator`: `ITableValidator<T>` / `ITableDataAdaptor<T>` / `StatResult` / `TableMeta`
- `nop-utils/nop-table-validator`: `TableValidatorEngine`（push-based 内部引擎）
- `nop-utils/nop-table-validator`: `ModelBasedTableValidator<T>`（编译验证器）
- `nop-utils/nop-table-validator`: 单元测试
- `nop-tablesaw`: `TablesawTableDataAdaptor` + pom.xml 新增依赖
- `nop-tablesaw`: 集成测试

### Out Of Scope

- 标准化断言命名库
- Profiler 自动生成
- 验证结果报告渲染

## Execution Plan

### Phase 1 - xdef + bean 模型 + register-model

Status: completed
Targets: `nop-kernel/nop-xdefs`, `nop-kernel/nop-core`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [x] 创建 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/table-validator.xdef`
- [x] 创建 `nop-kernel/nop-core/src/main/resources/_vfs/nop/core/registry/table-validator.register-model.xml`（格式参照 `validator.register-model.xml`）

Exit Criteria:

- [x] `table-validator.xdef` 存在，包含三层检查（rowValidators/statChecks/tableChecks）和 columns 定义
- [x] `table-validator.register-model.xml` 存在，loader 配置正确
- [x] bean 模型自动生成：`TableValidatorModel` / `TableStatCheckModel` / `TableGlobalCheckModel` / `TableColumnMeta` 在 `io.nop.core.model.table.validator` 包
- [x] `./mvnw compile -pl nop-kernel/nop-xdefs,nop-kernel/nop-core -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - nop-table-validator 模块

Status: completed
Targets: `nop-utils/nop-table-validator`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [x] 创建 `nop-utils/nop-table-validator/pom.xml`（依赖 `nop-core` + junit5，参照 `nop-utils/nop-match/pom.xml`）
- [x] 更新 `nop-utils/pom.xml` modules 列表新增 `nop-table-validator`
- [x] `ITableValidator<T>` 接口：`List<ErrorBean> validate(T data)`
- [x] `ITableDataAdaptor<T>` 接口：`getRowCount` / `getColumnNames` / `getCellValue`
- [x] `StatResult` bean：columnName/type/size/missing/distinctCount/min/max/mean/stdDev/value
- [x] `TableMeta` bean：rowCount/columnCount
- [x] `TableValidatorEngine`（push-based 内部引擎）：
  - `beginTable(List<TableColumnMeta>)` → 初始化 per-column 累加器
  - `beginRow()` → 初始化行上下文
  - `setValue(String colName, Object value)` → 暂存列值
  - `endRow()` → 构建 rowScope → rowValidators (ModelBasedValidator)；更新累加器
  - `endTable()` → 计算统计量 → statChecks + tableChecks → `List<ErrorBean>`
- [x] 内联统计累加器：`Map<String, Accumulator>` 跟踪 per-column count/nullCount/sum/sumOfSquares/min/max/distinctSet
- [x] `ModelBasedTableValidator<T>`：实现 `ITableValidator<T>`，使用 adaptor 遍历数据并委托 `TableValidatorEngine`
- [x] 行号注入：包装 `IValidationErrorCollector` 在 endRow 时拦截 addError 并注入 rowIndex
- [x] 错误输出：row-level 含 rowIndex；stat-level 含 column/statValue
- [x] NaN 保护：count=0 时 mean/stdDev/min/max=null，filter-bean 不误报

Exit Criteria:

- [x] `pom.xml` 存在，`nop-utils/pom.xml` 已注册新模块
- [x] 接口 `ITableValidator<T>` 存在
- [x] 接口 `ITableDataAdaptor<T>` 存在（getRowCount/getColumnNames/getCellValue）
- [x] 类 `ModelBasedTableValidator<T>` 实现 `ITableValidator<T>`
- [x] 引擎 `TableValidatorEngine` push-based API 完整
- [x] row-level: ModelBasedValidator 委托正确，错误含行号
- [x] stat-level: shortcut 映射到 filter-bean 的 `ge/le/gt/lt/between` 正确
- [x] stat-level: 自定义 `<condition>` 中 `stat.distinctCount` 正确求值
- [x] stat-level: `<statExpr>` XPL 表达式正确执行
- [x] stat-level: 累加器正确（含 sumOfSquares → stdDev）
- [x] table-level: rowCount/columnCount shortcut 正确
- [x] NaN 保护: count=0 时统计值 null，条件不误报
- [x] `./mvnw compile -pl nop-utils/nop-table-validator -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 单元测试

Status: completed
Targets: `nop-utils/nop-table-validator`

- Item Types: `Fix | Proof | Follow-up`

- [x] row-level: not-null，错误含 rowIndex
- [x] stat-level: between / condition(stat.distinctCount) / statExpr / sumOfSquares→stdDev
- [x] table-level: rowCount between
- [x] edge case: 空表、全 null 列、无 rowValidators/statChecks/tableChecks
- [x] 统计累加器: 与手动计算值对比

Exit Criteria:

- [x] row-level 测试通过
- [x] stat-level 测试通过（between/ge/le/custom condition/statExpr/sumOfSquares）
- [x] table-level: rowCount between 对空表报错
- [x] edge case: 空表、全 null 列、缺层验证器
- [x] 累加器正确性: 内联 == 手动计算结果
- [x] **接线验证**: endRow → ModelBasedValidator, endTable → shortcut 展开
- [x] **无静默跳过**: empty row set 不 NPE；空列统计值 null 不误报
- [x] `./mvnw test -pl nop-utils/nop-table-validator -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Tablesaw 适配器 + 集成测试

Status: completed
Targets: `nop-format/nop-tablesaw`

- Item Types: `Fix | Proof | Follow-up`

- [x] `nop-format/nop-tablesaw/pom.xml` 新增 `nop-table-validator` 依赖
- [x] `TablesawTableDataAdaptor` 实现 `ITableDataAdaptor<Table>`
- [x] 集成测试: 从 Table 输入 → ModelBasedTableValidator → 验证结果

Exit Criteria:

- [x] pom.xml 包含 `nop-table-validator` 依赖
- [x] `TablesawTableDataAdaptor` 存在且工作正常
- [x] 集成测试通过: row/stat/table 三层从 Table 输入完整验证
- [x] **端到端验证**: Table → ModelBasedTableValidator.validate() → 结果
- [x] `./mvnw test -pl nop-format/nop-tablesaw -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 Phase Exit Criteria 全部勾选
- [x] 三层验证全部可用
- [x] `./mvnw compile -pl nop-utils/nop-table-validator,nop-format/nop-tablesaw -am` 通过
- [x] `./mvnw test -pl nop-utils/nop-table-validator,nop-format/nop-tablesaw -am` 通过
- [x] 无 in-scope defect 被降级
- [x] `ai-dev/design/nop-core/02-table-validator-design.md` 是最新
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### 标准化断言命名库

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: filter-bean 条件已足够表达断言逻辑
- Successor Required: no

### Profiler 自动生成

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 不阻塞验证模型的可用性
- Successor Required: no

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: 所有四个 Phase 均已完成实现。独立审计 25/25 项全部 PASS。三层验证（row/stat/table）全部可用。
| 类别 | PASS | FAIL |
|------|------|------|
| Phase 1 (XDEF + Registry) | 6 | 0 |
| Phase 2 (Core Java) | 7 | 0 |
| Phase 3 (Unit Tests) | 3 | 0 |
| Phase 4 (Integration) | 5 | 0 |
| Closure Gates | 2 | 0 |
| Doc Updates | 2 | 0 |

Completed: 2026-07-17

Closure Audit Evidence: 独立审计 agent (`ses_0903ef02effeIoy4hkWa64FRja`) 已验证所有 25 项 exit criteria，全部 PASS。详见 audit report: row-level (ModelBasedValidator 复用)，stat-level (filter-bean shortcuts ge/le/gt/lt/between + custom condition)，table-level (rowCount/columnCount bounds)。Tablesaw 适配器及集成测试通过。

Follow-up:

- 无剩余 plan-owned work
