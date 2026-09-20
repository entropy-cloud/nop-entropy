# 03 SourcePatternCompiler 编译管线（roadmap item 3）

> Plan Status: active
> Last Reviewed: 2026-09-21
> Source: ai-dev/backlog/nop-lint-roadmap.md Wave 1 item 3；ai-dev/design/nop-lint/01-pattern-dsl.md §4（编译管线）、04 §1（ast-grep 对标）、04 §8（pattern 必须是有效代码）
> Related: plan 02（LintNode/LintLanguage，completed）；items 4–7 消费本 plan 产出

## Purpose

交付 `SourcePatternCompiler`：把 pattern 源码文本编译为 `SourcePattern`（PatternNode 树 + 匹配根 + kind 预计算），落地设计 01 §4 五步管线（expando 预处理 → TSParser 解析 → CST→PatternNode 转换 → effective-node 提取 → kind 预计算）。这是 pattern kernel（items 4–7）的数据底座。

## Current Baseline

- plan 02 已完成：`LintLanguage.parse(String)` 产出门面树（`LintTree` 仅有 `of(TSTree)` 工厂）；`LintLanguage.preprocessPattern/kindId` 可用；JavaLanguage 绑定就绪。
- **实验事实（2026-09-21 以 java blob 实测 CST 形态，已删除探针测试）**：
  - `throw new RuntimeException($$$ARGS)` → `program → throw_statement`（尾部带 **missing `;`**）；`return null` 同样带 missing `;`。
  - **裸表达式 pattern 会被 ERROR 包装**：`foo()` → `program → ERROR → method_invocation`；`$A == $B` → `program → ERROR → binary_expression`；`$VAR` → `program → ERROR → identifier`。ERROR 为单子节点且跨度与子节点一致，可透明剥离。
  - `class $C extends X { $$$ }` 的**类体 `$$$` 也是单子 ERROR 包装**（`class_body → ERROR($$$) → identifier`）——ERROR 剥离必须发生在转换期全局，不止根部。
  - `throw new $$$` → **多子节点 ERROR**（`ERROR[throw, new, identifier $$$]`），无法剥离 → 依设计 04 §8 P0（pattern 必须是有效代码）**拒绝编译**；可用的等价写法是 `throw new $$$($$$)`（实测解析干净：`type_identifier=$$$` + `argument_list($$$)`）。设计 01 §3.5 的 `has: pattern: throw new $$$` 示例与 P0 约束冲突，记入 Follow-up（rule library item 11 时修订示例）。
  - `$OBJ.dao().$METHOD($$$ARGS)` 解析干净（expression_statement + missing `;`，无 ERROR）。
  - `$`/`$$`/`$$$`/`$_` 均为合法 Java 标识符，词法保持单 token。
- 提取算法裁定（依据上述实验，**设计偏差声明见下**）：在 **PatternNode 树**上提取；转换期对 missing 节点区分处置——**missing 的 unnamed 节点（尾部 `;` 等）丢弃（零宽，拒绝会使旗舰 pattern 失败）；missing 的 named 节点（实测用例：`void m(int) {}` 的 formal_parameter 内恢复出零宽 missing identifier；注意 `void $M($$$ARGS)` 实际是多子 ERROR 而非 missing，勿混淆）拒绝编译**（pattern 是不完整代码，fail-fast，防匹配期静默零匹配）；单子同跨度 ERROR 透明剥离；其余 ERROR（多子/跨度不一致）拒绝（设计 04 §8 P0）。提取沿"唯一子节点"链下降（Internal 且恰 1 子 → 下降；Terminal/MetaVar/多子 Internal → 停）。MULTI meta-var 出现在单节点槽位（如 `throw new $$$($$$)` 的 type 位置）允许编译，匹配语义由 items 4–6 定义。

**设计偏差声明（对齐 plan 02 先例）**：
1. 设计 01 §4 步骤 4"取最内层 >1 子节点的节点"公式不可执行（旗舰 pattern 的最内层多子节点是 argument_list 而非 throw_statement；叶锚定 pattern 整链无 >1 节点）。实际采用"自根沿唯一子链下降、遇多子/叶即停"，与 ast-grep `is_single_node` 下降等效（其对"2 子但第 2 子 missing"的容差以"先丢 missing"等效实现）。follow-up：item 7 后修订设计 01 §4/04 §1 措辞。
2. 有意收紧 ast-grep：ERROR 剥离要求"单子且同跨度"（ast-grep 无条件穿单子 ERROR）；多子 ERROR 的 `throw new $$$` 拒绝编译（ast-grep 接受为 ERROR 根 pattern）——依设计 04 §8 P0。
3. 有意偏离 ast-grep：meta-var 裸形式（`$`/`$$`/`$$$`/`$_`）接受为非捕获 meta-var（ast-grep 判为字面标识符）——设计示例的类体 `$$$` 依赖；名称强制大写/下划线开头（ast-grep 同）。
4. contextual 排除 context 根（ast-grep 允许 selector 命中根）：防退化 selector="program"；判别测试见 Phase 2。
- 实现层性能纪律：编译器是规则加载冷路径，以可读性优先；热路径无 regex（meta-var 语法用手写字符扫描分类）。

## Goals

- `io.nop.lint.core.pattern`：`PatternNode`（sealed：MetaVar/Terminal/Internal）、`MetaVarSyntax`（`$VAR`/`$_VAR`/`$$VAR`/`$$$VAR` 及裸 `$`/`$$`/`$$$` 分类）、`SourcePattern`（编译产物值对象：root + possibleKindIds + language + 原文）、`SourcePatternCompiler`（`compile(pattern, lang)` + `contextual(selector, context, lang)`）。
- 行为契约：无效 pattern 快速失败（`NopLintException`，英文消息）；`$@TYPE`/`$!LITERAL` 显式拒绝（Phase 2 能力）；多子 ERROR / missing named 拒绝；meta-var 裸形式（`$`/`$$`/`$$$`/`$_`）为非捕获。
- kind 预计算：effective 根为 Internal/Terminal → `possibleKindIds=[kindId]`；根为 MetaVar → 空数组（无 kind 过滤）。
- 拒绝路径（全部抛 `NopLintException` 英文消息）：不可解析（根含未剥离 ERROR）、多子 ERROR、**missing named 节点（任意深度）**、根级 MULTI meta-var、空/空白 pattern、`$@`/`$!` 前缀、contextual selector 不存在。

## Non-Goals

- 不实现匹配语义（MetaVar matcher/ChildMatcher/省略号/严格度 = items 4–7）。
- 不做 autofix、约束、关系规则（Phase 2）。
- 不引入 JMH（deferred → item 13，同 plan 02 裁定）。
- 不修改 nop-treesitter（硬约束）。

## Scope

### In Scope

- `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/pattern/*`（约 6 个文件 + package-info）
- `nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/pattern/SourcePatternCompilerTest.java`
- 新异常类 `NopLintException`（nop-lint-core，**extends NopException**（nop-treesitter 传递依赖可用），`(String message)` 构造对齐 TreeSitterException 先例，英文消息）
- `ai-dev/logs/2026/09-21.md`、roadmap item 3 状态回写

### Out Of Scope

- `docs-for-ai/`（M1 后统一评估）；design 01 §3.5 示例修订（follow-up）
- 匹配 API（`SourcePattern` 本 plan 不含 `match()` 方法——避免空壳，匹配行为随 items 4–7 落地）

## Execution Plan

### Phase 1 - PatternNode 模型与 meta-var 语法分类

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/pattern/`

- Item Types: `Fix`

- [ ] `PatternNode` sealed interface：`MetaVarNode`（shape：SINGLE/ANONYMOUS/DROP/MULTI + name 可空 + 原文本）、`TerminalNode`（kindId + text）、`InternalNode`（kindId + children 列表）
- [ ] `MetaVarSyntax.parse(text)`：返回 MetaVarSpec（shape+name）或 null（非 meta-var，按字面 Terminal 处理）。**判定规则（审查裁定，前两条对齐 ast-grep meta_var.rs）**：(1) 最长前缀顺序 `$$$` → `$$` → `$`，取该前缀后剩余为候选名；(2) 候选名合法性：首字符 [A-Z_]、后续 [A-Z_0-9]，非法（含空、含 `$`、`$1`、`$A$B`、`$$$$VAR`）→ 整体不是 meta-var（字面量）；(3) **有意偏离 ast-grep**：裸形式（`$`、`$$`、`$$$` 为前缀后无字符；`$_` 经规则 4 同样归 DROP name=null）接受为非捕获 meta-var（SINGLE/ANONYMOUS/MULTI/DROP，name=null）——设计 01 §2/§3.5 的类体 `$$$` 依赖裸 MULTI；(4) `$_VAR` → DROP；**名称以 `_` 开头（`$_VAR`/`$$_`/`$$$_`）一律非捕获（不写 env，ast-grep 惯例）——捕获行为属 item 4 匹配语义，此处记录裁定**；(5) `$@`/`$!` 第二字符 → `NopLintException`（typed/literal meta-var not supported yet）
- [ ] `NopLintException`
- [ ] 单元测试：四种 shape + 裸形式 + 非法形式分类断言（纯逻辑，无解析）

Exit Criteria:

- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0，新增测试全绿
- [ ] `No owner-doc update required`（编译管线契约以设计 01 §4 为准，代码级事实归源码）
- [ ] `ai-dev/logs/2026/09-21.md` 已更新

### Phase 2 - CST→PatternNode 转换 + effective 提取 + kind 预计算

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/pattern/`

- Item Types: `Fix`

- [ ] `SourcePatternCompiler.compile(pattern, lang)`：preprocessPattern → `lang.parse` → 递归转换（**missing unnamed 丢弃 / missing named 拒绝**；单子同跨度 ERROR 透明剥离；其余 ERROR → 异常；named+meta-var 文本 → MetaVarNode；named → Internal；unnamed → Terminal）→ effective 提取（Internal 恰 1 子则下降）→ kind 预计算 → `SourcePattern`
- [ ] `contextual(selector, context, lang)`：解析 context（同规则），pre-order 找首个 `kind()==selector` 的节点（**排除 context 根**——有意偏离 ast-grep（其允许命中根），防退化 selector="program"；偏离已入偏差声明），以其子树为转换起点，不再提取（selector 钉死根）；找不到 selector → 异常
- [ ] 根为 MULTI meta-var 的 pattern 拒绝（degenerate）
- [ ] 单元测试（解析真实 java blob）：
  - 旗舰 pattern：`throw new RuntimeException($$$ARGS)` → effective 根 kind=throw_statement，possibleKindIds 单元素且等于 `lang.kindId("throw_statement")`；`$$$ARGS` 为 MULTI named ARGS
  - ERROR 剥离：`foo()` → 根 method_invocation；`$A == $B` → 根 binary_expression（含 2 MetaVar SINGLE + 1 Terminal `==`）；`$VAR` → 根 MetaVar SINGLE；`class $C extends CrudBizModel { $$$ }` → 根 class_declaration、类体含裸 MULTI
  - missing 丢弃：throw_statement 子节点中无 zero-width 节点
  - 拒绝路径：`throw new $$$`（多子 ERROR）、`class {{{`（不可解析）、`$@Type`、根级 `$$$`、空/空白 pattern、`void m(int) {}`（missing named identifier 零宽节点）
  - contextual：selector=method_declaration + context 类壳 → 根 method_declaration 且 `$M` 被捕获；selector 不存在 → 异常
  - ANONYMOUS/DROP 覆盖：`$$L + $R` → binary_expression（ANONYMOUS + Terminal `+` + SINGLE）；`if ($_COND) { $$$ }` → DROP 命名 COND + block 体裸 MULTI
  - 叶锚定/Terminal 根：`0` → Internal(decimal_integer_literal, 0 子)（named 语言构造以 Internal 叶收尾，不产生 Terminal 根）；`;` → Terminal 根
  - meta-var 名单一致性：同名 `$A` 两处 → 两个 MetaVarNode 同 name（一致性匹配是 item 4 语义，此处只验证编译保真）
  - contextual：shell 固定为 `class Demo { void target() { throw new RuntimeException(); } }`，selector=`method_declaration` → 根 method_declaration 且捕获就位；**判别用例：同一 shell、selector=`class_declaration` → 必须抛异常（证明排除 context 根）**；selector 不存在（如 `not_a_kind`）→ 异常

Exit Criteria:

- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0，新增测试全绿
- [ ] **接线验证**：编译产物 root 树完全由 plan 02 的 LintNode 遍历构建（`lang.parse` 产物唯一入口），测试内对同一 pattern 断言 kindId 与 `lang.kindId(kind)` 相等
- [ ] **无静默跳过**：所有拒绝路径抛 `NopLintException`（英文消息，含 pattern 片段），不静默返回 null
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/2026/09-21.md` 已更新

### Phase 3 - roadmap 回写与收口

Status: planned
Targets: `ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Follow-up`

- [ ] 独立 closure audit（针对 Phase 1–2）通过后：roadmap item 3 `todo` → `done`（附 plan 编号）
- [ ] `ai-dev/logs/2026/09-21.md` 收口记录

Exit Criteria:

- [ ] roadmap item 3 标记 `done`
- [ ] `ai-dev/logs/2026/09-21.md` 收口记录已更新

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（如有执行中发现记录于此）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（`throw new $$$` 示例冲突已记 Follow-up，属设计示例层面非本 plan 漂移）
- [ ] 行为/契约结果已达成：旗舰 pattern 编译产物结构与实验事实一致
- [ ] 必要 focused verification 已完成：Phase 1–2 Exit Criteria 全勾
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs：`No owner-doc update required`（已裁定，理由见各 Phase）
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session）
- [ ] Anti-Hollow Check：closure audit 追踪 `compile → convert → extract → kindIds` 调用链在测试中被真实断言；`scan-hollow-implementations.mjs --module nop-lint --severity high` 退出码 0 且记录扫描文件数
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/03-source-pattern-compiler.md --strict` 退出码 0
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0
- [ ] 代码规范：导入分组、sealed/record 使用得当、无 raw type；checkstyle 非门禁（plan 01 裁定）

## Deferred But Adjudicated

### design 01 §3.5 `throw new $$$` 示例修订

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 该示例是 Phase 2 规则设计示意，P0 约束（pattern 必须有效代码）已由设计 04 §8 明文规定，本 plan 的拒绝行为正是执行该约束；示例修订随 rule library（item 11）规则落地时一并处理
- Successor Required: `yes`
- Successor Path: roadmap item 11 plan（届时改用 `throw new $$$($$$)` 或等价可解析 pattern 并修订设计示例）

### 编译器性能基准

- Classification: `optimization candidate`
- Why Not Blocking Closure: 编译是规则加载冷路径（每规则一次），无热路径；匹配期性能归 item 13 JMH 基线
- Successor Required: `yes`
- Successor Path: roadmap item 13

## Non-Blocking Follow-ups

- `$@TYPE`/`$!LITERAL` 类型化/字面量 meta-var：roadmap item 26（L2 类型推导）承接
- 设计 01 §4/04 §1 提取公式措辞修订（"沿唯一子链下降"）+ §3.5 `throw new $$$` 示例修订：item 7 完成、item 11 开始前执行
- CST/Signature/Template 严格度：roadmap item 7（v1 只做 Smart/AST）

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent closure audit 时填写）
- Evidence: （逐条 Exit Criterion / Closure Gate 验证结果）

Follow-up:

- （closure 时填写，或写 no remaining plan-owned work）
