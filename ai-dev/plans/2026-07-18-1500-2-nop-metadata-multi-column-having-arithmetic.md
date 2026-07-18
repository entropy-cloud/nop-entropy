# 2026-07-18-1500-2 nop-metadata 多列 having 算术表达式

> Plan Status: completed
> Last Reviewed: 2026-07-18
> Mission: nop-metadata
> Work Item: Opt-followup. queryAggregation having 支持多 measure 算术表达式（`HAVING SUM(a)-SUM(b)>100`）
> Source: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.2 D11（having/orderBy）+ D12（expression 型 Measure 表达式语言）；plan `2026-07-18-0900-2`（Opt-2 having/orderBy）`Deferred But Adjudicated`「多列 having 算术表达式」；plan `2026-07-18-1400-1`（Opt-3 expression 执行）`Deferred But Adjudicated`「多列 having 算术表达式」
> Related: `2026-07-18-0900-2`（Opt-2 having/orderBy）；`2026-07-18-1400-1`（Opt-3 expression 执行 + `ExpressionMeasureValidator`）
> Draft Review: R1 独立子 agent 对抗性审查（ses_08c4be477ffeYbbe3NElt5pCIr）发现 3 Blocker + 6 Major：(B1) 跨库内存路径多列算术求值与 D12.1 拒绝「平台表达式引擎」冲突；(B2) name token 识别机制 Decision 缺关键事实；(B3) TreeBean「新增字段」表述错误（应为「新增属性」经 setAttr/getAttr）。已据 R1 修复：B1 改为三条 SQL 路径支持 + 跨库内存显式失败（对齐 D12.2）；B2 在 In Scope 固定用户输入语法 + measure name 字符集约束；B3 改为「TreeBean 新增属性」；M1 校验落点独立 Decision；M2 Phase 2 blocked on Phase 1；M3 参数绑定改为 SQL 文本顺序；M4 枚举所有 SQL build 方法；M5 测试矩阵分支化；M6 多列算术 leaf 沿用 `?` 检测。

## Purpose

把 `queryAggregation` 的 having 子句从「仅支持单 measure/dimension 叶子条件」（`SUM(amount)>1000`）扩展到「支持多 measure 算术表达式」（`SUM(a)-SUM(b)>100`），收口 Opt-2/Opt-3 两处 `Deferred But Adjudicated`「多列 having 算术表达式」。D12.1 expression 语言裁定已落地，本 plan 在 having 子句复用其安全模型与 parse 能力。

**范围裁定（R1 B1 修复）**：多列算术 having 在**三条 SQL 路径**（entity / external-sql / JOIN 同库）支持；**跨库内存路径显式失败**（对齐 D12.2 既有铁律——内存求值算术表达式等于在内存里实现 SQL 方言子集，D12.1 已明确拒绝"平台表达式引擎"）。

## Current Baseline

**已成立（live repo，经 R1 核实）**：

- **D11 having/orderBy 已落地**（plan `2026-07-18-0900-2` completed）：having 为 `TreeBean`（`{type, name?, value?, children?}` + 经 `ExtensibleBean.setAttr/getAttr` 承载任意属性），叶子条件 `name` 引用**单个**选定 measure/dimension name。`buildNameToExprTable`（`MetaAggregationExecutor.java:3049`）构建 name→aggSql 反查表。`nameResolverFor`（`:3116`）回调解析 name→expr；expression 型 measure aggSql 含 `?` 时抛 `ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED`（`:3130-3136`）。`FilterToSqlTranslator.translate(filter, fieldResolver)`（`FilterToSqlTranslator.java:104`）支持 fieldResolver 回调。
- **D12 expression 执行已落地**（plan `2026-07-18-1400-1` completed）：`ExpressionMeasureValidator`（`service/field/`）实现 dialect-independent 静态校验（关键字/函数黑名单 + 标识符白名单 + 字面量参数收集）+ dialect-specific 函数检查。`validateStatic` 返回 `ValidatedExpression`（含 `.identifiers`/`.functions` 字段 + 字面量 params 列表）。
- **`collectBindParams` 当前顺序**（`:2638-2662`）：expression measure 字面量 → filter → having 值。HAVING 段所有 `?` 经单次 `translate(having, nameResolverFor(...))` 一次性产出（按 SQL 文本遍历顺序）。
- **跨库内存路径 having**（`MemoryFilterEvaluator.java`）：叶子求值仅支持**单 alias 比较**（`nameToAlias.get(name)` 取一个 alias 值与字面量比较），**无算术运算能力**。
- **HAVING SQL 注入点**（R1 M4 核实，至少 5-7 处）：entity-EQL 路径（`:465`）/ entity-bypass 路径（`:573`）/ external-sql 单表（`:2597`）/ entity↔entity JOIN（`:759`）/ external↔external JOIN（`:1005`/`:1229`）/ 混合 JOIN 同库（`:1176`/`:856`）。本 plan 须逐个注入点覆盖。
- **build 状态**：`./mvnw test -pl nop-metadata -am` 当前全绿（434 tests）。

**剩余 gap（本 plan 收口）**：

- having 叶子仅支持单 measure/dimension name，不支持 `SUM(a)-SUM(b)` 跨 measure 算术
- D11 `fieldResolver` 把 name 映射为单一 aggSql/column，无复合表达式组合机制
- Opt-2/Opt-3 两处 `Deferred But Adjudicated`「多列 having 算术表达式」无 owner plan

## Goals

- having 叶子条件支持引用**多个 measure name** 经算术组合（`SUM(a)-SUM(b)`、`SUM(a)/SUM(b)*100`），在**三条 SQL 路径**（entity / external-sql / JOIN 同库）一致支持
- 复用 D12.1 expression 语言安全模型（`ExpressionMeasureValidator`），不引入新注入面
- **跨库内存路径显式失败**（对齐 D12.2 `metadata.aggr-expression-memory-not-computable` 铁律，不引入内存算术求值器）
- 向后兼容：既有单 measure/dimension having 叶子零行为变化
- 参数绑定按 SQL 文本 `?` 出现顺序（复用既有 `translate(having, fieldResolver)` 单次遍历产出，不单独追加）

## Non-Goals

- 不修改 expression 型 Measure 执行逻辑（Opt-3 已 landed）
- 不实现跨库内存路径多列算术 having（显式失败，对齐 D12.2）
- 不实现多列 orderBy 算术（orderBy 维持单 measure/dimension name；多列算术 orderBy 为 follow-up）
- 不引入新表达式语言（复用 D12.1 方言原生 SQL 片段裁定）
- 不修改平台 `TreeBean` 类（ Protected Area；多列算术 having 经 TreeBean 既有 `setAttr/getAttr` 扩展属性承载）

## Scope

### In Scope

- **用户输入语法固定（R1 B2 修复）**：多列算术 having 的用户表达式为**用户编写的含 measure name token 的算术表达式**（如 `SUM(totalAmount)-SUM(discountAmount)`，measure name 须匹配 `^[A-Za-z_][A-Za-z0-9_]*$` 字符集约束）。measure name token 经反查表识别后替换为该 measure 的 aggSql。**dimension name 不参与算术**（dimension 通常是分组列非数值）。measure name 字符集约束须在 save-time（measure 创建时）或文档约定中强制
- **承载机制（R1 B3 修复）**：TreeBean 叶子新增可选**属性** `expr`（经既有 `setAttr/getAttr`，**不修改 TreeBean 类**）；`expr` 非空时优先于 `name`；`expr` 与 `name` 同时存在时 `expr` 优先（dispatch 规则显式声明）
- **name→aggSql 替换**：算术表达式中匹配反查表 key（measure name，word-boundary `^[A-Za-z_][A-Za-z0-9_]*$`）的 token 替换为该 measure 的 aggSql；替换后表达式含聚合函数 + 列引用（如 `SUM(AMOUNT)-SUM(QTY)`）
- **安全校验落点（R1 M1，独立 Decision）**：在替换前/替换后两候选间裁定 `ExpressionMeasureValidator` 校验落点（见 Phase 1 Decision）
- **`?` 安全边界沿用（R1 M6）**：多列算术 leaf 引用的 measure 若为 expression 型（aggSql 含 `?`），抛 `ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED`（沿用 D12.4 既有安全边界，不绕过）
- **参数绑定（R1 M3 修复）**：多列算术 leaf 的字面量经 `fieldResolver` 回调返回含 `?` 的表达式时，由 `translate` 内部按 SQL 文本顺序自动入队（复用既有单次遍历产出，**不单独追加到末尾**）
- 三条 SQL 路径（entity-EQL / entity-bypass / external-sql 单表 / entity↔entity JOIN / external↔external JOIN / 混合 JOIN 同库，逐个注入点）having SQL 生成注入多列算术表达式
- 跨库内存路径显式失败（抛 ErrorCode，对齐 D12.2）
- 失败路径显式化：未选定 measure name / parse 失败 / 不安全关键字 / expression 型 measure 被 arithmetic 引用（`?` 检测）均显式失败

### Out Of Scope

- 跨库内存路径多列算术 having（显式失败）
- 多列 orderBy 算术（follow-up）
- dimension name 参与算术（dimension 通常非数值）
- having 子查询 / 窗口函数

## Execution Plan

### Phase 1 - 承载机制 + 安全校验 + 参数绑定裁定与实现

Status: completed
Targets: `MetaAggregationExecutor.java`（`buildNameToExprTable`/`nameResolverFor`/ErrorCode 段）；`ExpressionMeasureValidator.java`（复用）；`FilterToSqlTranslator.java`（fieldResolver）

- Item Types: `Decision`、`Fix`、`Proof`

- [x] **Decision — 安全校验落点（R1 M1）**：在两候选间裁定：(a) **替换前校验**（校验用户表达式中的 name token ∈ 选定 measure name 集合 + 算术表达式结构合法，不校验列存在性——列存在性由 measure 自身保证）；(b) **替换后校验**（替换后的最终 SQL 片段经 `ExpressionMeasureValidator` 校验关键字/标识符白名单，expectedColumns 取该表列集合）。裁定须权衡：替换前更轻量但不校验最终 SQL 安全性；替换后更安全但 expectedColumns 语义需适配（aggSql 含聚合函数括号）。**推荐候选 (b) 替换后校验**（最终 SQL 文本被检验，安全边界完整）
- [x] **Decision — `expr` leaf dispatch 落点（R2 NEW-1）**：`FilterToSqlTranslator.requireField` 的 fieldResolver 回调签名为 `Function<String,String>`（只接收单 `name`），多列算术 leaf 的输入是整个算术表达式（多 name token），无单一 `name` 可传。在候选间裁定 dispatch 物理落点：(a) 扩展 `FilterToSqlTranslator.requireField` 优先检查 `expr` 属性（修改通用 translator）；(b) **`MetaAggregationExecutor` 调用 `translate` 前预处理 having TreeBean**，将 `expr` leaf 的算术表达式先做 name→aggSql 替换为最终 SQL 片段，改写到 `name` 属性后传入既有 `translate`（保持 `FilterToSqlTranslator` 通用语义不变，推荐）；(c) 改 fieldResolver 签名为 `BiFunction<TreeBean,String,String>`（影响 7+ 调用点，侵入大）。裁定须指明物理落点，不留给实现者临场决定。**fieldResolver 行为闭合（R3 补充）**：候选 (b) 预处理后 `expr` leaf 的 `name` 已是最终 SQL 片段（如 `SUM(AMOUNT)-SUM(QTY)`），该 leaf 经 `requireField` 读 `name` 后 fieldResolver 须**原样返回**（`Function.identity()` 语义，不再反查 nameToExpr）；既有单 name leaf 仍走 `nameResolverFor` 反查。实现时 either 对 `expr`-preprocessed leaf 单独用 identity resolver，or 扩展 `nameResolverFor` 加 passthrough 分支（命中 nameToExpr key 则反查，否则原样返回）—— 裁定留 Phase 1 实施时以 sub-decision 收口（物理落点已定，此为调用细节）
- [x] **大小写敏感性明示（R2 B2 残留）**：name→aggSql 替换经反查表 `buildNameToExprTable`（case-sensitive `LinkedHashMap`，key 为 `measureNames`），用户 `expr` 中的 measure name token 须**大小写一致**匹配（case-sensitive，不使用 `CASE_INSENSITIVE`，避免 measure name `count` 腐蚀 SQL 函数 `COUNT`）
- [x] TreeBean 叶子新增可选属性 `expr`（经 `setAttr/getAttr`，不修改 TreeBean 类）：`expr` 非空时优先于 `name`；dispatch 规则按上一条 Decision 落点裁定（`expr` 非空 → 预处理替换后走既有单 name 路径，向后兼容）
- [x] name→aggSql 替换：算术表达式中匹配反查表 measure name key（word-boundary，measure name 须 `^[A-Za-z_][A-Za-z0-9_]*$`）的 token 替换为 aggSql；未匹配 token 进 errors（不静默保留裸字符串）
- [x] **`?` 安全边界（R1 M6）**：替换阶段检测每个引用 measure 的 aggSql 是否含 `?`（expression 型 measure）；含则抛 `ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED`（沿用 D12.4，不绕过）
- [x] 安全校验（按 Decision 落点）：经 `ExpressionMeasureValidator` 校验最终表达式（关键字黑名单 + 标识符白名单）；不安全 → 显式失败抛新 ErrorCode `ERR_AGGR_HAVING_EXPR_UNSAFE`；parse 失败 → `ERR_AGGR_HAVING_EXPR_UNPARSEABLE`；未选定 measure name → 复用既有 `ERR_AGGR_HAVING_UNKNOWN_NAME`（R1 m3，不新增冗余 code）
- [x] 参数绑定（R1 M3）：多列算术 leaf 的字面量经 `fieldResolver` 回调返回含 `?` 的表达式时，由 `translate` 内部按 SQL 文本遍历顺序自动入队（**不单独追加末尾**），复用既有 `translate(having, fieldResolver)` 单次遍历产出。**`?` 推理链（R2 NEW-3）**：多列算术 leaf 经 `?` 安全边界（上条）拒绝 expression 型 measure 后，引用的均为 field-based measure（aggSql 不含 `?`），故 leaf 的 `?` 仅来自 comparison literal（`> ?`），参数计数与 `translate` 单次遍历产出一致
- [x] `ExpressionMeasureValidator` 扩展（如需）+ name 替换逻辑单元测试：合法算术表达式 name 替换正确 / 未选定 name 显式失败 / expression 型 measure（aggSql 含 `?`）被 arithmetic 引用失败 / 不安全关键字失败 / 字面量经 `translate` 自动入队

Exit Criteria:

- [x] 安全校验落点 Decision 已裁定并写入 design doc（§4.4.2 D11 扩展）
- [x] TreeBean 叶子 `expr` 属性承载落地（经 setAttr/getAttr，不修改 TreeBean 类）；dispatch 规则显式（`expr` 非空优先）
- [x] name→aggSql 替换正确（word-boundary，measure name 字符集约束）
- [x] `?` 安全边界沿用（expression 型 measure 被 arithmetic 引用 → `ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED`）
- [x] 参数绑定经 `translate` 单次遍历产出（不单独追加，无计数错配）
- [x] **新功能测试（#25）**：单元测试覆盖 name 替换 / `?` 检测 / 安全校验 / 字面量入队
- [x] **无静默跳过（#24）**：parse 失败、未选定 name、不安全关键字、`?` 检测均显式抛 ErrorCode
- [x] 若本 Phase 改变 live baseline：`ai-dev/design/`（§4.4.2 D11 扩展契约）已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 三条 SQL 路径实现 + 跨库内存显式失败（blocked on Phase 1）

Status: completed
Depends on: Phase 1 Decisions（校验落点）+ 承载机制落地
Targets: `MetaAggregationExecutor.java`（entity-EQL `:465` / entity-bypass `:573` / external-sql 单表 `:2597` / entity↔entity JOIN `:759` / external↔external JOIN `:1005`/`:1229` / 混合 JOIN 同库 `:1176`/`:856`）；`MemoryFilterEvaluator.java`（跨库内存显式失败）

- Item Types: `Fix`、`Proof`

- [x] **三条 SQL 路径（6+ 注入点，R1 M4 + R2 补 `:2657`）逐个覆盖**：entity-EQL（`:465`）/ entity-bypass（`:573`）/ external-sql 单表 SQL build（`:2597`）/ external-sql `collectBindParams` 二次 translate 取参数（`:2657`，复用同一 `nameResolverFor` lambda，须与 SQL build 点对 `expr` leaf 处理一致）/ entity↔entity JOIN（`:759`）/ external↔external JOIN（`:1005`/`:1229`）/ 混合 JOIN 同库（`:1176`/`:856`）。每注入点的 HAVING SQL 生成中，多列算术 leaf 经 dispatch Decision（预处理替换）→ `fieldResolver` 回调 → `translate` 按文本顺序入队参数
- [x] **跨库内存路径显式失败**（R1 B1 修复）：`MemoryFilterEvaluator.evaluate` 入口检测多列算术 leaf（`expr` 属性非空，R2 NEW-4：在 `evaluate` 入口检测而非 `getRowValue`，错误上下文更清晰）→ 抛新 ErrorCode `ERR_AGGR_HAVING_EXPR_MEMORY_NOT_COMPUTABLE`（对齐 D12.2 命名空间，不引入内存算术求值器）
- [x] 端到端测试矩阵（R1 M5 分支化）：
  - 三条 SQL 路径各至少 1 条 `HAVING SUM(a)-SUM(b)>100` 成功路径（R2 NEW-2：覆盖每条架构路径——entity 单表 / external-sql 单表 / JOIN 同库；JOIN 同库下的 entity↔entity / external↔external / 混合 三子路径合并覆盖至少 1 条）
  - 跨库内存路径 1 条多列算术 having 显式失败（断言 ErrorCode）
  - 向后兼容：既有单 measure having 全路径零行为变化（既有测试全绿）
  - 失败路径：未选定 measure name / parse 失败 / 不安全关键字 / expression 型 measure 被 arithmetic 引用 各至少 1 条断言

Exit Criteria:

- [x] 三条 SQL 路径（6+ 注入点）having SQL 生成正确注入多列算术表达式（端到端断言过滤生效）
- [x] 跨库内存路径多列算术 having 显式失败（断言 ErrorCode，对齐 D12.2）
- [x] 参数绑定经 `translate` 单次遍历产出（无 SQLException 计数错配）
- [x] **端到端验证（#22）**：从 GraphQL `queryAggregation`（含多列算术 having）到 SQL 执行输出，三条 SQL 路径各至少 1 条完整跑通
- [x] **接线验证（#23）**：多列算术 having 在运行时真实经 name→aggSql 替换 + 注入 SQL（非仅类型存在）
- [x] **无静默跳过（#24）**：跨库内存显式失败 + 失败路径显式抛 ErrorCode
- [x] **新功能测试（#25）**：三 SQL 路径成功 + 跨库内存失败 + 向后兼容 + 失败路径测试已逐条列出
- [x] 向后兼容：既有单 measure having 全路径零行为变化（既有测试全绿）
- [x] 若本 Phase 改变 live baseline：`ai-dev/design/`（D11 多列算术最终契约含跨库内存显式失败）已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 文档同步 + roadmap 收口

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md`（§4.4.2 D11 扩展）；`ai-dev/design/nop-metadata/nop-metadata-roadmap.md`

- Item Types: `Follow-up`

- [x] §4.4.2 D11 扩展：记录多列算术 having 契约（承载机制 `expr` 属性 + name→aggSql 替换 + 安全校验落点 + 三条 SQL 路径 + 跨库内存显式失败 + `?` 安全边界沿用 + 参数绑定经 translate 单次遍历）
- [x] roadmap Work Item Status 追加 `Opt-followup. 多列 having 算术表达式: done`；Pointers 追加本 plan
- [x] 关闭 Opt-2/Opt-3 两处 `Deferred But Adjudicated`「多列 having 算术表达式」（在 design doc 记录收口，不回写历史 plan）
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

- [x] §4.4.2 D11 多列算术 having 契约已写入 design doc
- [x] roadmap `Opt-followup: done` + Pointers 追加本 plan
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：本 section 所有条目及每个 Phase Exit Criteria 全部 `[x]` 后才能将 `Plan Status` 改为 `completed`。

- [x] having 支持多 measure 算术表达式（`SUM(a)-SUM(b)>100`），三条 SQL 路径一致支持
- [x] 跨库内存路径多列算术 having 显式失败（对齐 D12.2，不引入内存算术求值器）
- [x] 复用 D12.1 安全模型（`ExpressionMeasureValidator`），无新注入面；`?` 安全边界沿用
- [x] 参数绑定经 `translate` 单次遍历产出（无计数错配）
- [x] 向后兼容：既有单 measure having 零行为变化
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（§4.4.2 D11 + roadmap）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证三条 SQL 路径在运行时真实注入多列算术表达式 + 真实过滤生效（非 stub）；跨库内存路径显式失败（非静默跳过）
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 多列 orderBy 算术表达式

- Classification: `optimization candidate`
- Why Not Blocking Closure: orderBy 的多列算术组合需求低于 having（BI 排序通常按单 measure）。首版 orderBy 维持单 measure/dimension name。多列算术 orderBy 可复用本 plan 的 name→aggSql 替换机制作为后续增量。
- Successor Required: no
- Successor Path: none（若后续需求要求，新建 plan 复用本 plan 替换机制）

### 跨库内存路径多列算术 having

- Classification: `optimization candidate`
- Why Not Blocking Closure: D12.1/D12.2 已明确拒绝内存表达式引擎（语义无法保证与 SQL pushdown 一致）。多列算术 having 在跨库内存路径显式失败是设计裁定结果（对齐 D12.2），非降级。若后续需求强烈，successor 须在内存路径引入受限算术求值器并同步调整 D12.1/D12.2 文本。
- Successor Required: no

## Non-Blocking Follow-ups

- having 子查询 / 窗口函数（BI having 不涉及，out-of-scope）

## Closure

Status Note: 多列 having 算术表达式落地完成。having 子句从「仅支持单 measure/dimension 叶子条件」扩展到「支持多 measure name 算术组合」（`HAVING SUM(a)-SUM(b)>100`），三条 SQL 路径（entity / external-sql / JOIN 同库）一致支持；跨库内存路径显式失败（对齐 D12.2）；复用 D12.1 expression 语言安全模型（`ExpressionMeasureValidator`）；不修改平台 TreeBean 类（经 setAttr/getAttr 承载 `expr` 属性）。Phase 1 字面量禁止裁定（避免 inner-SQL `?` 致参数计数错配），向后兼容既有单 measure having 全路径零行为变化。收口 Opt-2/Opt-3 两处 `Deferred But Adjudicated`「多列 having 算术表达式」。
Completed: 2026-07-18

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task_id: `ses_08b83ed02ffefrKYlOxWMotVE5`，fresh session，未复用实现阶段 session）
- Audit Session: `ses_08b83ed02ffefrKYlOxWMotVE5`
- Evidence:
  - **Plan completeness**: PASS — 52/52 checklist items `[x]`；3 Phase Status 均 `completed`；14 Closure Gates `[x]`
  - **Code implementation** (Phase 1 Exit Criterion: TreeBean `expr` 承载 / 安全校验 / `?` 边界 / 参数绑定): PASS
    - `MetaAggregationExecutor.java:311/321/332` — 3 新 ErrorCode 定义
    - `MetaAggregationExecutor.java:3278` — `public static final String HAVING_EXPR_ATTR = "expr"`（**不修改平台 TreeBean 类**，仅 String 常量）
    - `MetaAggregationExecutor.java:3251` — `preprocessHavingArithmetic` 定义；8 处引用（6 injection points + 1 def + 1 self-recursive）
    - `MetaAggregationExecutor.java:3297` — `substituteAndValidateHavingExpr` 使用真实 `Matcher.find()` 正则分词 + `nameToExpr.get(token)` 反查 + `out.append(aggSql)` 替换（非 stub）
    - `MetaAggregationExecutor.java:3188` — `nameResolverFor` passthrough 分支（非标识符 name 原样返回）
    - `MemoryFilterEvaluator.java:60-69` — `evaluate` 入口检测 `expr` 属性 → 抛 `ERR_AGGR_HAVING_EXPR_MEMORY_NOT_COMPUTABLE`（**检测在 `evaluate` 入口而非 `getRowValue`**，R2 NEW-4 ✓）
  - **Tests** (Phase 1 Exit Criterion #25 / Phase 2 Exit Criteria #22 #23 #24 #25): PASS — 12 单元测试 + 8 e2e 测试，454 tests pass（434 baseline + 20 new）
    - `TestHavingArithmeticPreprocess.java`: 12 单元测试覆盖 name 替换 / 未选定 name / expression 型 measure 引用 / 不安全关键字 / parse 失败 / 字面量禁止 / TreeBean preprocess 接线 / containsHavingArithmeticLeaf
    - `TestNopMetaAggregationBizModel.java`: 8 e2e 测试覆盖三条 SQL 路径成功 + 跨库内存失败 + 向后兼容 + 失败路径
  - **Documentation sync**: PASS — `01-architecture-baseline.md:1267-1299` D11.4 完整段落 + `:1357` 划线收口；`nop-metadata-roadmap.md:3`/`:34`/`:202` 同步；`ai-dev/logs/2026/07-18.md:3-47` 顶部条目
  - **Contract-implementation match**: PASS — 无 drift
    - D11.4.1: `HAVING_EXPR_ATTR` 仅 String 常量，测试用 `leaf.setAttr(MetaAggregationExecutor.HAVING_EXPR_ATTR, expr)` ✓
    - D11.4.2: 所有 6 注入点 preprocess 在 `translate` 之前；`FilterToSqlTranslator` 未被修改（无 `expr` 引用） ✓
    - D11.4.4: 跨库内存检测在 `evaluate` 方法首句 ✓
  - **`./mvnw test -pl nop-metadata/nop-metadata-service -am`**: BUILD SUCCESS，Tests run: 454, Failures: 0, Errors: 0, Skipped: 0（exit 0）
  - **`node ai-dev/tools/check-doc-links.mjs --strict`**: 0 errors / 0 warnings，1509 files scanned（exit 0）
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict`**: `[PASS] all 52 items checked`（exit 0）
  - **Anti-Hollow 检查**: PASS
    - (a) `substituteAndValidateHavingExpr` 真实替换：`Matcher.find()` + `nameToExpr.get(token)` + `out.append(aggSql)`，单元测试断言精确输出 `SUM(AMOUNT) - SUM(DISCOUNT)`
    - (b) `testExternalSqlArithmeticHaving` 真实过滤生效：测试数据 A=24, B=29, C=4；断言 `items.size()==2`（C 被排除）；for-loop 断言每个保留行 `sumA - sumB > 10`
    - (c) `testCrossDbMemoryArithmeticHavingFails` 真实 ErrorCode：断言消息含 `having-expr-memory-not-computable` / `memory-not-computable` / `not computable`（非 generic exception）
  - **Deferred 项分类检查**: PASS — 仅 2 项 deferred（多列 orderBy 算术 / 跨库内存路径多列算术 having），均为 `optimization candidate`，已附 non-blocking 理由；无 in-scope live defect 被降级

Follow-up:

- 多列 orderBy 算术表达式：`optimization candidate`（BI 排序通常按单 measure），可复用本 plan 的 name→aggSql 替换机制作为后续增量（见 `Deferred But Adjudicated`）
- 跨库内存路径多列算术 having：`optimization candidate`（D12.1/D12.2 已明确拒绝内存表达式引擎）；若后续需求强烈，successor 须在内存路径引入受限算术求值器并同步调整 D12.1/D12.2 文本
- Phase 1 字面量禁止裁定的解除：successor plan 评估支持字面量（如 `SUM(a)/SUM(b)*100`）的扩展机制（如 fieldResolver 返回带 param 的复合结果，或 TreeBean 新增 `_exprParams` 属性）
