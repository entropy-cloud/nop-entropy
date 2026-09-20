# 02 LintNode 门面 + Java 语言适配（roadmap item 2）

> Plan Status: active
> Last Reviewed: 2026-09-21
> Source: ai-dev/backlog/nop-lint-roadmap.md Wave 1 item 2；ai-dev/design/nop-lint/01-pattern-dsl.md §5（LintNode 门面）
> Related: plan 01（骨架，completed）；roadmap items 3–7 消费本 plan 产出

## Purpose

交付匹配内核的两大底座并收口 roadmap item 2：
1. **LintNode/LintTree 门面**（nop-lint-core）：统一节点访问 API，封装 TSTreeCursor 遍历、按 byte-range 的源码切片、kind/symbol 映射——后续所有 matcher、规则引擎只面对 LintNode，不直接触碰 nop-treesitter 内部 API（设计 01 §4.1 共享/隔离边界）。
2. **Java 语言适配**（nop-lint-java）：Java grammar 绑定 + `$` expando 规则 + kind 名称映射，作为 item 19（TS/TSX 适配）的 Java 对应物。

## Current Baseline

- plan 01 已完成：nop-lint-core/-java/-nop 骨架在 reactor 中，bootstrap 测试全绿（2026-09-21 独立 audit APPROVE）。
- nop-treesitter 可用 API（已核对源码，含审查子 agent 实验）：`TSParser.parse(Language, String|byte[]) → TSTree`；`io.nop.treesitter.TSNode`（record：tree/id/aliasSymbol，值语义 equals，**equals 含 aliasSymbol**——alias 由 (父 production, structural index) 唯一决定，句柄必须统一经 cursor `currentNode()` 派生、root 用 `rootNode()`，禁止对 alias 节点手造 `new TSNode(tree, id, 0)`）带 `type()/effectiveSymbol()/named()/isExtra()/startByte()/endByte()/child(i)/namedChild(i)/parent()/childCount()/namedChildCount()`；`TSTree.rootNode()/source()/language()`（`root()` 是 int rootId 非节点）；`TSTreeCursor`（可见/命名子节点导航、`gotoChildByFieldName`、`currentFieldName`）；`tree.arena().isMissing(id)`（isMissing 的公开路径，compat/TSNode.isMissing 为先例）。
- **kindId 解析的 alias 盲区（审查实验证实，硬约束）**：`Language.symbolId(name, named)` 只扫 `[0, symbolCount)` 不含 alias 区间——java blob 中 `type_identifier` 是 alias id（symbolCount=320, aliasCount=1），`symbolId("type_identifier", true)` 返回 -1 而实际节点 `effectiveSymbol()`=320。kindId 名称解析**不得**委托 `Language.symbolId`；须用 `symbolName(i)/symbolVisible(i)/symbolNamed(i)`（三者经 `checkSymbolRange` 接受完整 `[0, symbolCount+aliasCount)` 区间）自建全名称表缓存 map。**收表谓词与冲突策略（二轮审查实验证实，硬约束）**：java blob 存在同为 visible 的同名符号对（`throws` id 133 unnamed 关键字 token vs id 277 named 节点；`permits` id 119 vs id 241）——map 只收 `symbolVisible(i)==true` 条目，同名冲突时 **visible+named 的 id 优先**（对齐 pattern kind 匹配的 named=true 语义）；kindId 一致性断言只对 `isNamed()==true` 节点成立（匿名 token 不经 kindId 查名）。内建 ERROR symbol 越过名称表，`kindId("ERROR")` 返回 -1（记录语义即可）。
- **cursor 枚举有界性（审查实验证实）**：`gotoNextSibling/gotoNextNamedChild` 会 bubble up 穿过隐形父帧——子节点枚举必须以该节点自身为 cursor 根（`node.cursor()` 后 `gotoFirstChild/gotoNextSibling`，实验证实有界且与 `namedChildCount`/`parent()` 一致），禁止共享从树根出发的 cursor 逐节点 `resetTo` 后继续枚举。
- 事实约束：nop-treesitter 的 `TSNode.child(i)` 每次调用经 scratch cursor 重定位（第 i 个子节点 O(i)）；整树单遍 walk 用一条从根出发的 cursor 走 `gotoFirstChild/gotoNextSibling`（O(n)，指整树遍历场景；**单节点的 children 表构建仍按下条以该节点自身为 cursor 根**，两者适用范围不同勿混淆）。`compat/TSNode` 的 `hasError()` 是 O(子树) 递归，门面 v1 不暴露（无消费者；避免性能陷阱）。
- Java 语法中 `$` 是合法标识符字符（JLS §3.8），pattern 文本无需预处理即可解析——Java 的 expando 规则为恒等替换（ast-grep 同语义；Python 类语言的替换规则属 item 19/后续）。
- 设计偏差声明：设计 01 §5 草图 `LintNode` 为 5 方法接口；本 plan 按内核（items 4–7）实际需要补充 `kindId/isNamed/isExtra/isMissing/namedChildren/parent/childByField` 与 pre-order 遍历，方向一致、面更完整；设计文档不回写代码级签名（guide rule #14）。

## Goals

- nop-lint-core 提供 `io.nop.lint.core.node`（LintNode/LintTree/SourceRange）与 `io.nop.lint.core.lang`（LintLanguage 契约 + 通用 tree-sitter 适配器）。
- nop-lint-java 提供 `io.nop.lint.java.JavaLanguage`：单例加载 java grammar blob，`preprocessPattern` 恒等（JLS §3.8 `$` 为 Java letter，审查实验证实 tree-sitter-java lexer 将 `$X` 解析为单个 identifier），kindId 经全名称表缓存 map 查询（含 alias 区间）。
- 全部行为有 JUnit 5 焦点测试；门面与绑定为后续 kernel 的唯一节点访问面。

## Non-Goals

- 不实现 pattern 编译/匹配（items 3–7）。
- 不做 nop-treesitter 任何改动（硬约束）。
- 不引入 JMH/JFR：性能基线由 roadmap item 13 统一建立（将覆盖 LintNode 遍历）；本 plan 以实现层性能纪律代替（int kindId 比较、单 cursor 构建子表、惰性文本缓存），不做提前测量。
- 不注册语言发现机制（ServiceLoader/IoC）——规则引擎（item 8–9）需要时再做。

## Scope

### In Scope

- `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/node/*`：LintNode 接口、LintTree、SourceRange、tree-sitter 实现
- `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/lang/*`：LintLanguage 接口、通用适配器
- `nop-lint/nop-lint-java/src/main/java/io/nop/lint/java/JavaLanguage.java`
- 对应测试 ×3 组（node 门面 / lang 适配器 / JavaLanguage）
- `ai-dev/logs/2026/09-21.md`、roadmap item 2 状态回写

### Out Of Scope

- `docs-for-ai/` 新增使用文档（M1 后内核可用时统一评估；本 plan 仅 owner doc 裁定）
- xscript、规则 DSL、autofix

## Execution Plan

### Phase 1 - core：LintTree/LintNode 门面

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/node/`

- Item Types: `Fix`

- [x] `SourceRange`：startByte/endByte（end 独占），record
- [x] `LintNode` 接口：kind（alias 感知名）、kindId（int 快路径）、isNamed/isExtra/isMissing、text（UTF-8 切片）、range、parent、children（可见子节点）、namedChildren、childByField(name)、pre-order 遍历（Iterable，self-first）
- [x] tree-sitter 实现：包装 TSTree+TSNode；children/namedChildren 以**该节点自身为 cursor 根**（`node.cursor()`）用 `gotoFirstChild/gotoNextSibling`（named 变体用 gotoFirstNamedChild/gotoNextNamedChild）有界构建；子节点句柄统一经 cursor `currentNode()` 派生；text 惰性缓存；equals/hashCode 委托底层 TSNode（同树同位置同 alias 即相等）；根节点 parent 为 null
- [x] `LintTree`：持有 TSTree，root() 返回实现节点；工厂方法从 TSTree 构造
- [x] 单元测试：解析 Java 片段（须含 `type_identifier` 与 `throw_statement`）后验证 kind/kindId/text 切片与 byte range、字段访问（如 method_declaration 的 name 字段）、parent 链、children/namedChildren 数量关系、pre-order 遍历计数（`==` 双路径对照）与去重、equals 语义（同位置相等、不同位置不等）、叶子节点 children 为空表。（kindId 名称一致性断言在 Phase 2 适配器就位后执行，本 Phase 只断言 kindId() 为非负 int 且 effectiveSymbol 回路一致）

Exit Criteria:

- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0，新增测试全绿
- [x] **接线验证**：门面 pre-order 遍历产出的节点数 **等于** 独立用 nop-treesitter API（`TSNode.child(i)` 递归）统计的 visible 节点数（测试内双路径对照断言，防 cursor bubble-up 越界多收）
- [x] **无静默跳过**：`childByField` 对不存在字段返回 null（C null-object 语义对齐 TSNode.NULL → 门面 null），不吞错；`kindId()` 对任意解析产物节点返回非负 int（name→id 查名的 -1 语义归 Phase 2 的 LintLanguage.kindId）
- [x] `No owner-doc update required: 设计 01 §5 为架构草图，代码级事实归源码（guide rule #14）`
- [x] `ai-dev/logs/2026/09-21.md` 已更新

### Phase 2 - core：LintLanguage 契约与通用适配器

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/lang/`

- Item Types: `Fix`

- [x] `LintLanguage` 接口：id、treeSitter()（底层 Language）、parse(String)/parse(byte[])→LintTree、preprocessPattern(String)→String、kindId(String)→int（-1 未知）
- [x] 通用适配器（final 类，构造注入 id + Language + expando 函数，JavaLanguage 以**组合/委托**复用而非继承）：parse 经 `TSParser.parse`；preprocessPattern 默认恒等；kindId 经构造时一次性扫描 `[0, symbolCount+aliasCount)` 全名称表（`symbolName/symbolVisible/symbolNamed`）构建的 name→id 不可变缓存 map——**不使用 `Language.symbolId`**（alias 盲区）；内建 ERROR 查名返回 -1
- [x] 单元测试（加载 java blob 构造适配器）：适配器 parse 产出 LintTree 且 root kind 正确；kindId 已知 kind（method_declaration、**type_identifier（alias 区间代表性用例）**、**throws（同名碰撞代表性用例：命名 throws 节点的 kindId 必须等于 kindId("throws")，即 named id 而非关键字 token id）**）>0、未知 kind=-1、`kindId("ERROR")`=-1；**`node.kindId() == adapter.kindId(node.kind())` 对遍历中全部 `isNamed()==true` 节点成立（片段须含 throws 子句；内建 ERROR/_ERROR 节点（builtinErrorSymbol/builtinErrorRepeatSymbol）不在断言范围——其 symbol 越过名称表）**；自定义 expando 函数生效（如把 `$` 换成 `_` 的测试函数证明钩子可用）；恒等默认

Exit Criteria:

- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0，新增测试全绿
- [x] **接线验证**：适配器 parse 产物即 Phase 1 LintTree（同一门面类型，无转换层）；`kindId` 对未知名返回 -1
- [x] **无静默跳过（kindId 语义收口）**：未知 kind 名与内建 ERROR 查名均返回 -1，不抛错不猜测（已含于上方测试项，此处为显式 Exit Criterion）
- [x] **无静默跳过**：构造注入 null id / null Language 抛 `NullPointerException`/`IllegalArgumentException`（消息英文），不静默接受
- [x] `No owner-doc update required`（同 Phase 1 理由）
- [x] `ai-dev/logs/2026/09-21.md` 已更新

### Phase 3 - nop-lint-java：JavaLanguage 绑定

Status: completed
Targets: `nop-lint/nop-lint-java/src/main/java/io/nop/lint/java/`

- Item Types: `Fix`

- [x] `JavaLanguage`：id="java"；惰性单例加载 `/grammars/java/tree-sitter-java-blob.bin`（holder idiom，Language 实例仅解析一次）；preprocessPattern 恒等（JLS §3.8 `$` 合法 + 审查实验证据，附注释说明）；kindId 委托适配器全名称表缓存
- [x] 单元测试：单例两次获取同一实例；parse Java 片段 root kind=program；preprocessPattern 恒等；kindId("method_declaration")>0、kindId("type_identifier")>0、kindId("not_a_kind")=-1

Exit Criteria:

- [x] `./mvnw -pl nop-lint/nop-lint-java -am test -T 1C` 退出码 0，新增测试全绿
- [x] **端到端验证**：`JavaLanguage.parse(snippet).root()` 经门面遍历能找到 `method_declaration` 与 `throw_statement`（blob → Language → parse → LintNode 全链路，Java 侧）；snippet 固定为：`class Demo { java.util.List<String> names; void run(int x) throws Exception { if (x > 0) { throw new RuntimeException("boom"); } } }`（含 type_identifier、throws 子句、throw_statement 三个代表性 kind）
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/2026/09-21.md` 已更新

### Phase 4 - roadmap 回写与收口

Status: planned
Targets: `ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Follow-up`

- [ ] 独立 closure audit（针对 Phase 1–3 的交付与 Closure Gates）通过后：roadmap item 2 `todo` → `done`（附 plan 编号）；本 Phase 是 audit 的执行对象之一，audit 先行、本项随后
- [ ] `ai-dev/logs/2026/09-21.md` 收口记录

Exit Criteria:

- [ ] roadmap item 2 标记 `done`
- [ ] `ai-dev/logs/2026/09-21.md` 收口记录已更新

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（如有执行中发现记录于此）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（设计偏差已在 Current Baseline 声明，属扩展非漂移）
- [ ] 行为/契约结果已达成：门面 + 语言契约 + Java 绑定可用且有焦点测试
- [ ] 必要 focused verification 已完成：Phase 1–3 Exit Criteria 全勾
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs：`No owner-doc update required`（三个 Phase 已裁定，理由见各 Phase）；`module-groups.md` 的 nop-lint 行无需变更（模块职责未变）
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session）
- [ ] Anti-Hollow Check：closure audit 追踪真实调用链——`JavaLanguage.parse → LintTree.root → LintNode.kind/children/childByField` 在测试中被断言消费；`scan-hollow-implementations.mjs --module nop-lint --severity high` 退出码 0 且记录扫描文件数
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/02-lintnode-facade-java-language.md --strict` 退出码 0
- [ ] `./mvnw -pl nop-lint/nop-lint-core,nop-lint/nop-lint-java -am test -T 1C` 退出码 0
- [ ] 代码规范：导入分组（io.nop.* → third-party → java.*，静态导入最后）、4 空格缩进；checkstyle 非门禁（plan 01 已裁定），以人工复核为准

## Deferred But Adjudicated

### 门面性能 JMH 测量

- Classification: `optimization candidate`
- Why Not Blocking Closure: roadmap item 13 统一建立 JMH 基线（覆盖 LintNode 遍历），门面已按性能纪律实现（int kindId、单 cursor 子表构建、惰性文本缓存）；提前零散测量无 baseline 对照价值
- Successor Required: `yes`
- Successor Path: roadmap item 13（Wave 2 benchmark baseline）

### LintNode.hasError 暴露

- Classification: `watch-only residual`
- Why Not Blocking Closure: Phase 1 内核（items 3–7）无消费者；compat 实现为 O(子树) 递归，暴露即埋性能陷阱，待真实需求（如 RuleTester 的 ERROR 节点诊断）出现时按需设计缓存版
- Successor Required: `no`
- Successor Path: 无（出现需求时在消费 plan 中落地）

## Non-Blocking Follow-ups

- 语言发现机制（ServiceLoader/IoC 注册 JavaLanguage）由规则引擎 plan（item 8/9）承接
- Python 等语言的 expando 预处理规则由 item 19 及后续语言适配 plan 承接

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent closure audit 时填写）
- Evidence: （逐条 Exit Criterion / Closure Gate 验证结果）

Follow-up:

- （closure 时填写，或写 no remaining plan-owned work）
