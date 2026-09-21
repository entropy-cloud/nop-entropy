# 04 Pattern 匹配内核（roadmap items 4–7，M1 收口）

> Plan Status: draft
> Last Reviewed: 2026-09-21
> Source: ai-dev/backlog/nop-lint-roadmap.md Wave 1 items 4–7 + 里程碑 M1；ai-dev/design/nop-lint/04-ast-grep-alignment.md §2–§4（meta-var/子节点/严格度算法）、01 §5（匹配器草图）
> Related: plan 02/03（门面与编译器，completed）；M1 = 本 plan 的 Closure 形态

## Purpose

实现 ast-grep 对标的匹配内核并接通 `SourcePattern` 的匹配入口，使纯 pattern 规则可经 JUnit 运行（M1 验收形态）。四个 roadmap item 作为四个 Phase 顺序交付：MetaVar 匹配与环境（item 4）→ lockstep 子节点匹配（item 5）→ 省略号回溯（item 6）→ 严格度 v1（item 7）→ 端到端集成。

## Current Baseline

- plan 02/03 已完成：`LintNode`（children/namedChildren/kindId/text/equals 值语义）、`LintLanguage`、`SourcePatternCompiler`（PatternNode sealed 树 + effective 提取 + possibleKindIds）。core 47 tests 全绿。
- design 04 §2 关键算法（meta-var）：`match_variable` 同名第二次出现时 `does_node_match_exactly(existing, candidate)`——同 node_id 短路 true；叶节点比文本；内部节点比 kind_id + 递归子节点。`$_VAR` 丢弃捕获不写 env。**metavar 候选跳过（审查 M-B 对齐上游 strictness.rs L103-105）**：metavar 前只跳**注释**（`should_skip_cand_for_metavar = should_skip_comment && is_extra`）；SINGLE 对未命名候选 = NoMatch 整体失败（上游 match_node.rs 行为，非跳过）——design 04 §2 该行系误标，Phase 4 一并修复。
- design 04 §3 关键算法（子节点）：lockstep 遍历；`match_single_node_while_skip_trivial` 循环跳过不匹配匿名候选；省略号 lookahead probe（克隆 aggregator 尝试下一 goal，失败扩展范围）；尾部剩余候选必须可跳过。
- design 04 §4 严格度矩阵（v1 只做 Smart/AST）：
  - **Smart**（默认）：goal 未命名 Terminal 参与匹配（kind+text）；候选未命名节点不匹配时跳过（skip_goal=false, skip_candidate=!is_named）；注释（isExtra）跳过。
  - **AST**：goal 未命名 Terminal 直接跳过（双方未命名都跳，skip_goal=!is_named）；文本仍比较。
  - CST/Signature/Template 明确 deferred（roadmap item 7 原文）。
- 设计偏差声明：
  1. 设计 01 §5 草图 `SourcePattern.match(root, source)` 签名——实现取 `PatternMatcher.findMatches(SourcePattern, LintNode)`：source 已由门面持有（LintNode.text()），重复传参易漂移；SourcePattern 保持值对象不变。
  2. design 04 §3 伪代码的 Aggregator（Cow 引用克隆）以 `MetaVarEnv.clone()` 值克隆实现（probe 隔离），语义一致（probe 失败不污染真实 env）。
  3. MULTI meta-var 的序列消费归 Phase 3（design 04 §3 省略号算法的一部分）；Phase 1 只交付 MetaVarEnv 的 multi 写入/读取 API。
- **MULTI 单槽位语义裁定（plan 03 遗留）**：`throw new $$$($$$)` 的 type 位置 MULTI 在单节点槽位出现时，匹配语义 = 捕获该单个节点进 multi 列表（列表长度 1），与 ast-grep 的"ellipsis 匹配单节点"行为一致。
- **模型契约修订（审查 B-1，对 plan 03 产物的有意变更，不回写已完成 plan 文本）**：plan 03 把 named 叶编译为 `InternalNode(kindId, [])` 丢失文本——实测 `$OBJ.dao()` 的 `dao` 与任意 identifier 同 kindId，按 kind-only 匹配 `dao` 会错配 `get`（上游 ast-grep 把一切无 meta-var 叶节点编译为 `Terminal{text, is_named, kind_id}`，named terminal 强制文本相等）。修订：`TerminalNode` 增 `named` 标志并承接 named 叶（`TerminalNode(kindId, text, named)`）；`matchNode` 叶分支 = kindId 相等 && text 相等（named/unnamed 一律比文本；AST 严格度只影响 goal 未命名 Terminal 是否被跳过）。plan 03 的 `numericLiteralIsInternalLeafRoot` 测试相应更新为 Terminal 叶断言。
- **AST 严格度注释行为裁定（审查 M-2）**：跟上游 `strictness.rs`（`should_skip_comment: Cst|Ast => false`）——**AST 不跳注释**（注释是 named 节点，参与匹配）；design 04 §4 表格 AST 行"注释：跳过"系对上游误标，本 plan 执行时同步修订该表格（owner doc 修复）。deviation：SMART 的候选可跳集合 = 未命名 || isExtra；AST = 仅未命名。
- **NodeExactEquality 收紧声明（审查 Minor 4）**：design §2/上游的叶节点一致性只比文本；本实现加 `kindId 相等 &&`（更严，防异 kind 同文本误判一致）——有意收紧。
- **尾部处理偏严声明（审查 Minor 1）**：上游 Smart 的 `should_skip_trailing => true`（源码自注 TODO workaround，任意尾候选可跳）；本实现要求尾部候选全部 `canSkipCandidate`（design 04 §3 明文），更严、fail-closed。
- **未命名 Terminal 文本比较声明（审查 Minor 6）**：上游对 unnamed goal Terminal 只比 kind（TSX span workaround）；本实现 kind+text 都比——Java 匿名 token kind 决定文本，实际等价，偏严无行为差。
- **已知行为钉死（审查 Minor 5）**：effective 根为 MetaVar（`$VAR`）时每个 named 节点产出一个 Match（与 ast-grep 一致；无 kind 可过滤，复杂度线性于节点数）；effective 根为 Terminal 时严格度不影响根匹配（根永远直接比对 kindId+text，skip 语义只作用于子节点 lockstep）。

## Goals

- `io.nop.lint.core.pattern` 内新增：`MetaVarEnv`（single/multi 捕获 + 同名一致性 + clone 隔离）、`NodeExactEquality`（does_node_match_exactly）、`Strictness`（SMART/AST 枚举 + 决策矩阵）、`PatternMatcher`（findMatches 入口 + matchNode + lockstep matchChildren + 省略号 lookahead probe + 尾部跳过）。
- 端到端（M1 形态）：编译旗舰 pattern → 对真实 Java 源码树匹配 → 捕获 env 断言，全部经 JUnit 验证。
- 性能纪律：kindId int 比较优先；候选 children 单次物化；无 regex/无 stream 在热路径；clone 仅发生在省略号 probe（设计要求）。

## Non-Goals

- 关系/组合规则、约束、autofix（Phase 2，items 21–25）。
- CST/Signature/Template 严格度（item 7 明确 deferred）。
- JMH 基线（item 13，M1 后立即执行——本 plan 的 perf 纪律为 item 13 做准备但不做测量）。
- xscript、规则 DSL（items 8+）。

## Scope

### In Scope

- `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/pattern/`：MetaVarEnv、NodeExactEquality、Strictness、PatternMatcher、Match
- `SourcePattern` 增加 match 入口委托（保持值对象，行为在 PatternMatcher）
- 测试：MetaVarEnvTest、PatternMatcherTest（单元）+ M1KernelAcceptanceTest（端到端）
- `ai-dev/logs/2026/09-2x.md`、roadmap items 4–7 与 M1 状态回写

### Out Of Scope

- 规则 YAML 加载（item 8）、LintEngine（item 9）
- docs-for-ai（M1 后内核可用性文档统一评估）

## Execution Plan

### Phase 1 - MetaVarEnv 与单节点 meta-var 匹配（roadmap item 4）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/pattern/`

- Item Types: `Fix`

- [ ] `Strictness` 枚举（自 Phase 4 前移，审查 M-1）：SMART（默认）/ AST；`skipGoalUnnamed()`（SMART=false / AST=true）、`canSkipCandidate(node)`（SMART=未命名||isExtra / AST=仅未命名——AST 不跳注释，见 Current Baseline 裁定）
- [ ] `MetaVarEnv`：`insert(name, node)`（同名二次 → `NodeExactEquality.isExact`，失败返回 false）、`insertMulti(name, List<LintNode>)`（追加语义：多次 insertMulti 同名按序拼接）、`getCapture(name)` / `getMultiCapture(name)`、`clone()`（深拷贝内部 map，probe 隔离）
- [ ] `NodeExactEquality.isExact(a, b)`：同位置（equals）→ true；都无子 → kindId 相等 && text 相等（比上游严格，已声明）；都有子 → kindId 相等 && 子数量相等 && 逐子递归；其余 false
- [ ] `TerminalNode` 增 `named` 标志（B-1 模型修订）；`SourcePatternCompiler.convertNamed` 的 named 叶改产 `TerminalNode(kindId, text, true)`；plan 03 的 `numericLiteralIsInternalLeafRoot` 测试同步更新
- [ ] 单元测试：首插捕获、同名一致（同节点 true / 异节点 false）、leaf-by-text / internal-by-kind+children 精确相等、clone 隔离（probe 写入不泄漏）、multi 追加次序、Strictness 两档的 skipGoalUnnamed/canSkipCandidate 矩阵、named 叶 Terminal 化（`0` 与 `dao` 场景）

Exit Criteria:

- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0，新增测试全绿
- [ ] **接线验证**：一致性检查消费 plan 02 的 LintNode.equals（同位置短路路径有测试）
- [ ] **无静默跳过**：insert 失败返回 false 而非静默 true；getCapture 未捕获名返回 null
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - lockstep 子节点匹配 + trivial 跳过 + 尾部处理（roadmap item 5）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/pattern/PatternMatcher.java`

- Item Types: `Fix`

- [ ] `matchNode(goal, cand, env, strictness)` 三分支：**MetaVar**——SINGLE 候选必须 named 且 env.insert 成功；ANONYMOUS named/unnamed 均可 env.insert；DROP 恒 true 不写 env；**Terminal**（named 叶与未命名 token 统一）——kindId 相等 && text 相等（B-1 修复语义；根节点同样适用，严格度不影响根）；**Internal**——kindId 相等 → matchChildren
- [ ] `matchChildren(goalChildren, candChildren, env, strictness)` lockstep 逐迭代决策（对齐上游 match_terminal 结果族，审查 B-1'）：
  1. goal Terminal vs 候选：kindId+text 相等 → **MatchedBoth**（双方前进）
  2. 失败且候选为注释（isExtra）且严格度跳注释（SMART）→ **SkipCandidate**（候选前进）
  3. 失败且 goal 为未命名 Terminal：SMART → **SkipCandidate**（goal 原地重试下一候选）；AST → **SkipBoth**（双方前进）
  4. 失败且 goal 为 MetaVar：候选注释且 SMART → SkipCandidate；SINGLE 候选未命名 → **NoMatch 整体失败**（M-B 上游语义）；ANONYMOUS/DROP 未命名可消费（见 matchNode）
  5. 失败且 goal 为 Internal → **NoMatch 整体失败**
  6. goal 耗尽：剩余候选全部 `canSkipCandidate` → 成功，否则失败（**偏严声明**见 Current Baseline）
  7. 候选耗尽仍有 goal：剩余为 AST 下未命名 Terminal（SkipGoal）或 MULTI（Phase 3，零宽合法）→ 成功；否则失败（上游 `matched("print($A,)", "print(123)", Ast)` 依赖此路径）
- [ ] 候选子列表物化一次（List），lockstep 用索引不用迭代器重扫
- [ ] 单元测试：顺序匹配成功/失败、Smart 未命名候选跳过、Smart 注释（isExtra）跳过、尾部 `;` 跳过、非可跳过尾部导致失败、AST 下 goal 未命名 Terminal SkipBoth（`$A + $B` 跨 `<<` 命中）、AST 尾部未命名 goal 耗尽后成功（`print($A,)` vs `print(123)`）、`dao` 不匹配 `get`（B-1 negative）、goal Internal 失败即整体失败、SINGLE 对未命名候选整体失败（`f($A)` vs `f(+x)`）

Exit Criteria:

- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0，新增测试全绿
- [ ] **接线验证**：matchChildren 消费 Phase 1 的 env/匹配语义（集成测试内断言 env 捕获）
- [ ] **无静默跳过**：匹配失败返回 false，无吞错
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 已更新

### Phase 3 - 省略号 lookahead probe + 回溯（roadmap item 6）

Status: planned
Targets: `PatternMatcher.matchChildren` 扩展

- Item Types: `Fix`

- [ ] goal MULTI meta-var：先 `env.clone()` probe 匹配下一 goal——成功则 `insertMulti(name, 已累积候选)` 并前进；失败则把当前候选累积进列表继续扩展（design 04 §3 伪代码直译）；**最后一个 goal 为 MULTI** 时吞掉全部剩余候选（豁免尾部可跳过约束——MULTI 允许吞 named）；**非尾 MULTI 候选耗尽 → 整体失败**（防死循环）；上游"省略号后跳过 trivial goal"分支不存在于 design 伪代码，直译已覆盖常规用例，记为已接受偏差
- [ ] probe 失败时真实 env 回滚（clone 隔离验证）；**非捕获 MULTI（裸 `$$$`/`$_` 前缀）**消费序列但不写 env——`MetaVarEnv.insertMulti` 对 null/非捕获名直接跳过写入（钉死，`class $C { $$$ }` 端到端依赖）
- [ ] MULTI 单槽位（type 位置等单节点序列位）按 Current Baseline 裁定捕获单节点（multi 列表长度 1）
- [ ] 单元测试（捕获内容钉死，审查 Minor 2）：`f($$$A)` 对 `f()` A=[]、`f(1,2)` A=[1, `,`, 2]（**含分隔符 Terminal**）；`f($$$A, $B)` lookahead 停界正确；`f($A, $$$B)` 对 `f(x)` → A=[x] B=[]（设计语义，非上游的耗尽不匹配）；连续 MULTI `f($$$A, $$$B)` 对 `f(1,2,3)` → A=[1]（**probe 下一 goal 为 MULTI 时当前 MULTI 只消费一个候选即闭合**，上游 match_node.rs L155-172 consume-one 语义）B=[2, `,`, 3]；probe 回滚（capture 不泄漏）；`throw new RuntimeException($$$ARGS)` 捕获完整实参

Exit Criteria:

- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0，新增测试全绿
- [ ] **接线验证**：MULTI 捕获结果可经 `env.getMultiCapture` 读取且次序与源序一致
- [ ] **无静默跳过**：probe 失败必须回滚，不允许部分绑定残留（测试断言）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 已更新

### Phase 4 - 严格度 v1 收口：Smart/AST 全参数化（roadmap item 7）

Status: planned
Targets: `SourcePattern`/`PatternMatcher` 的严格度贯通 + design 04 §4 表格修复

- Item Types: `Fix`

- [ ] 严格度已在 Phase 1–2 全链路参数化（matchNode/matchChildren/测试）；入口（matchIn/findMatches）参数化归 Phase 5，本 Phase 不提前建入口（审查 M-A）
- [ ] **owner doc 修复（审查 M-2/M-B）**：design 04 §4 表格 AST 行"注释：跳过"→"不跳过"；§2 表 `skip_cand_for_metavar` 行改为"仅跳注释；SINGLE 对未命名候选失败"；§2/§3 实现列类名（MetaVarMatcher/MultiVarMatcher/ChildMatcher/EllipsisMatcher/TrivialSkipper/Strictness.shouldSkipTrailing）标注为语义映射非交付类名（实际归 MetaVarEnv/PatternMatcher/Strictness）——三处均属上游误标/docs 漂移修复
- [ ] 单元测试：SMART 下 `$A + $B` 的 `+` 必须匹配加号 Terminal（kind+text）；AST 下 goal 序列退化为 named-only（`$A + $B` 忽略 `+`，跨任意 token 匹配）；AST 下注释候选不可跳过（named 节点必须被 goal 显式匹配）；Terminal 根 pattern（`;`）在两档严格度下行为一致（根不受 skip 影响）

Exit Criteria:

- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0，新增测试全绿
- [ ] **无静默跳过**：严格度只影响跳过策略，不吞错
- [ ] **owner doc**：design 04 §4 AST 注释行已修订（本 Phase 改变设计文档表述）
- [ ] `ai-dev/logs/` 已更新

### Phase 5 - 端到端集成：findMatches 入口（M1 收口形态）

Status: planned
Targets: `PatternMatcher.findMatches` + `SourcePattern` 委托

- Item Types: `Proof`

- [ ] `PatternMatcher.findMatches(SourcePattern, LintNode root, Strictness)`：pre-order 遍历（plan 02 NodeIterator）+ `mayMatchKind` O(1) 过滤 + 对每个候选 matchNode（fresh env）→ 命中收集 `Match(node, env)`
- [ ] `SourcePattern.matchIn(LintNode root)` 便捷委托（默认 SMART）
- [ ] `M1KernelAcceptanceTest`（端到端，Anti-Hollow 主证据；断言落在当前 v1 能力面——关系规则属 item 23）：
  - `throw new RuntimeException($$$ARGS)` 命中 snippet 的 throw_statement（Match 节点 kind 断言）并按源序捕获 ARGS（含分隔符）
  - `$OBJ.dao().$METHOD($$$ARGS)` 命中链式调用且 $OBJ/$METHOD 捕获正确；**negative：`$OBJ.get()` 不命中**（B-1 文本语义）
  - `class $C extends CrudBizModel { $$$ }` 命中继承声明（$C 捕获）；**negative：extends 其他类不命中**
  - `throw new RuntimeException($$$ARGS)` 对 `throw new IOException(...)` 零命中（B-1 negative）
  - 同名一致性端到端：`$X == $X` 匹配 `a == a` 而不匹配 `a == b`
  - MetaVar 根 pinning：`$VAR` pattern 对小 snippet 产出的 Match 数 == 该树 **named 节点总数（含根；pre-order 含根，Current Baseline 口径一致）**

Exit Criteria:

- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0
- [ ] **端到端验证**：上述 5 组断言全部从 `SourcePatternCompiler.compile` 到 `findMatches` 捕获断言完整跑通（guide rule #22）
- [ ] **接线验证**：findMatches 的候选过滤确实消费 possibleKindIds（对照：no-filter 与 filtered 遍历命中集合一致）
- [ ] `No owner-doc update required`（M1 后 docs-for-ai 评估另立）
- [ ] `ai-dev/logs/` 已更新

### Phase 6 - roadmap 回写与收口

Status: planned
Targets: `ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Follow-up`

- [ ] 独立 closure audit（针对 Phase 1–5）通过后：roadmap items 4/5/6/7 `todo` → `done`（附本 plan 编号），**M1 里程碑核验**：1–7 全 done 后 M1 `todo` → `done`
- [ ] `ai-dev/logs/` 收口记录

Exit Criteria:

- [ ] roadmap items 4–7 与 M1 标记 `done`
- [ ] `ai-dev/logs/` 收口记录已更新

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（如有执行中发现记录于此）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（偏差声明三条已在 Current Baseline 记录）
- [ ] 行为/契约结果已达成：匹配内核对标 design 04 §2–§4 算法，M1"纯 pattern 规则经 JUnit 运行"成立
- [ ] 必要 focused verification 已完成：Phase 1–5 Exit Criteria 全勾
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs：design 04 §4 AST 注释行、§2 metavar 跳过行与类名标注已按 Phase 4 修复（design 语义勘误，非新契约）
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session）
- [ ] Anti-Hollow Check：端到端测试从 compile → findMatches → 捕获断言完整连通；`scan-hollow-implementations.mjs --module nop-lint --severity high` 退出码 0 且记录扫描文件数
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/04-pattern-matcher-kernel.md --strict` 退出码 0
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am clean test -T 1C` 退出码 0
- [ ] 代码规范：热路径无 regex/stream；导入分组；checkstyle 非门禁

## Deferred But Adjudicated

### CST/Signature/Template 严格度

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap item 7 原文明确 "CST/Signature/Template deferred"；v1 只做 Smart/AST 是 roadmap 决策非本 plan 降级
- Successor Required: `yes`
- Successor Path: roadmap 后续 Wave（需要时立项）

### 内核性能基准

- Classification: `optimization candidate`
- Why Not Blocking Closure: roadmap item 13 在 M1 后立即建立 JMH 基线（含 matcher 微基准与 ast-grep CLI 同规则对比），本 plan 已按 perf 纪律实现
- Successor Required: `yes`
- Successor Path: roadmap item 13

## Non-Blocking Follow-ups

- 关系规则（inside/has/follows）与组合规则（all/not/any 嵌套）：items 21–24
- TypeAware 严格度：item 26（L2 类型推导）

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent closure audit 时填写）
- Evidence: （逐条 Exit Criterion / Closure Gate 验证结果）

Follow-up:

- （closure 时填写，或写 no remaining plan-owned work）
