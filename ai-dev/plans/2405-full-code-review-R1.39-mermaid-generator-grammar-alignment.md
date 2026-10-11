# 2405 full-code-review R1.39 — nop-mermaid 生成器-文法对齐（P1-4.4-A5 条目5 + P2-4.4-001/002 合并通道）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.39；finding `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.4-format-search-message-cluster-audit.md:54`（P1 条目5）+ `:184-213`（P2-4.4-001/002）
> Related: plan 2306（mermaid 三 P1 修复）；探针 `_tmp/MermaidRoundTripProbe.java`；文法单一事实源 `nop-format/nop-mermaid/model/antlr/Mermaid.g4` + `BaseRules.g4`
> Owner Doc: `docs-for-ai/01-repo-map/module-groups.md`（归属；无契约面则 `No owner-doc update required`）
> Plan Review: agent_94ed132d 首轮 NOT READY（2 Blocker：B1 序列消息 ARROW 映射未明示且 13/13 判据对箭头的判别空洞；B2 文法合法但省略 edgeType 的输入 null 拆箱 NPE 未裁定；3 Major：M1 style target 为 StringLiteral 却仍走 id()、M2 往返判据过弱（仅 type+语句数）、M3 P2-4.4-001/002 在 index 无行不可"翻 fixed"；4 Minor：owner-doc 未入 Phase Exit、id("") 静默、id() 覆盖测试不全、行号漂移）→ 本轮全修：显式四类箭头映射 + 箭头 token/edgeType 断言；null edgeType 不输出箭头 token 并加例；style target 去 id() 改引号字面量 + 连字符用例；往返判据升级为**字段级 AST 等价**（递归含 subgraph 嵌套）；治理措辞改「补登记/报告级状态」；id("") fail-loud；id() 覆盖全部 append 点参数化测试；行号勘正。agent_a6bc25e9 二轮 NOT READY（1 新 Major：`--module nop-format` hollow 扫描因 nop-record 既有 finding 恒非 0，须限定 `nop-format/nop-mermaid`；3 Minor）→ 全修（扫描限定本模块 + 箭头断言列举 flow/sequence 双输入 + null 作用域澄清）→ 评审确认「一行修复后 READY」。

## Purpose

修复 mermaid 生成器输出与自带文法系统性不匹配（探针 13 例 **0 忠实往返**：5 幻影节点污染 + 8 重解析失败），并合并 P2-4.4-001（`isStatic` 拆箱 NPE）与 P2-4.4-002（`id()` 校验遗漏 subgraph/state/task 三处）。**以 `Mermaid.g4`/`BaseRules.g4` 为单一事实源**，只改手写生成器 `MermaidGenerator.java`（生成物 `MermaidLexer/Parser/_MermaidASTBuildVisitor` 不手改）。

## Current Baseline

- **探针实测**（`_tmp/MermaidRoundTripProbe.java`，2026-10-10 复跑）：`SUMMARY pass=0 polluted=5 fail=8`。逐例输出：
  - 头部关键字错误（7）：`visitMermaidDocument:76` 输出 `type.name().toLowerCase() + " " + type` → `flowchart FLOWCHART`/`sequence SEQUENCE`/`state STATE`/`gantt GANTT`/`pie PIE`（第二枚举名被当作节点/未知 token）；flowchart-basic/direction/gantt/pie 污染，sequence-msg/participant/state 重解析失败。
  - 节点形状（1）：`visitMermaidFlowNode:101-103` 输出 `:::round`，文法 `mermaidFlowNode: id (LPAREN StringLiteral_? RPAREN)? shape?`（shape 为裸 token，`ROUND:'round'`）——`:::` 非法。
  - 边（2）：`visitMermaidFlowEdge:111-126` OPEN_ARROW 输出 `--->`（lexer 无此 token；`OPEN_ARROW:'->>'`）；label 输出在 to 之后并加 `:`（文法 `from edgeType? StringLiteral? to`，label 在 to **之前**、无冒号）→ labeled-edge 污染。
  - 序列消息（1）：`visitMermaidSequenceMessage:163-178` `ARROW` 输出 `" -> "`（lexer 无 `->`；`ARROW:'-->'`）、`OPEN_ARROW` 输出 `" --> "`（合法但类型错，二者互换）。
  - subgraph（1）：`visitMermaidFlowSubgraph:137-147` 输出 `subgraph id` + `title "..."` + `end`；文法 `SUBGRAPH id StringLiteral? LBRACE statements RBRACE`，且 **LBRACE='[' RBRACE=']'**（`BaseRules.g4:58-59`）。
  - style（1）：`visitMermaidStyleStatement:246-253` 输出 `style a {`（对 `node.getTarget()` 走 `id()`，**文法 target 是 StringLiteral 而非 Identifier**）+ `{}`；文法 `STYLE StringLiteral LBRACE ... RBRACE`。验证：`style "a-b" [...]` 合法可解析，但生成期 `id("a-b")` 抛 `ERR_MERMAID_INVALID_IDENTIFIER`。
  - class（1）：`visitMermaidClassNode:190-197` 输出 `class Foo {`（应 `[`/`]`）；`visitMermaidClassMember:208` `if (node.getIsStatic())` 对 null 拆箱 → NPE（`_gen/_MermaidClassMember` Boolean 默认 null）——探针 `class-node` INITIAL-PARSE-THROW 实为 generate 阶段抛出；static 输出 `" $"` 不在文法（`STATIC:'static'`）。
- **null edgeType 可达**（live 亲测）：`mermaidFlowEdge`/`mermaidSequenceMessage` 的 `edgeType=mermaidEdgeType_?` 可省略——`flowchart\n a\n b\n a "lbl" b` 与 `sequenceDiagram\n A B : "x"` 均可解析，但生成器对 null 枚举 `switch` 抛 `NullPointerException`（`MermaidEdgeType.ordinal()`）。
- **P2-4.4-002**（live）：`id()` 已用于 flow node/edge/participant/class name/style target；遗漏 `visitMermaidFlowSubgraph:139`、`visitMermaidStateNode:217`、`visitMermaidGanttTask:227` 三处。`id()` 对空串静默返回空串（Rule #24 静默 no-op）。
- **既有测试**：`TestMermaidGenerator`（MessageFormat 回归 + `testIllegalIdentifierRejected` 仅覆盖 class name）、`TestMermaidASTParser` 22/22；无往返用例、无全 append 点 id 校验用例。
- **文法事实源**：`BaseRules.g4:4-12` 图表类型字面量；`:22-38` shape/edge 字面量（ARROW `-->`、OPEN_ARROW `->>`、DOTTED `-.->`、THICK `==>`）；`:48 STATIC:'static'`；`:54-64` LBRACE='['；`Mermaid.g4:57-58` `mermaidComment` 因 `COMMENT` 走 HIDDEN 通道而不可达（既有 `testCommentOnHiddenChannelProducesNoStatement` 显式裁定预期）。

## Goals

- 生成器输出对文法合法输入可**忠实往返**：探针 13/13（类型 + **字段级 AST 等价**，含 subgraph 嵌套）；四类箭头映射与文法一致；null edgeType 不 NPE 且往返。
- 合并 P2-4.4-001：非 static class 成员不再 NPE；static 输出为文法 `static`。
- 合并 P2-4.4-002：subgraph/state/gantt task 三处 id 经 `id()` 校验；`id("")` fail-loud。
- 往返回归测试进入 `nop-mermaid` 测试套件（不依赖 `_tmp` 探针）；受影响模块测试全绿。

## Non-Goals

- 文法 `.g4` 与生成物不改（`mermaidComment` 死规则维持既有显式裁定，见 Deferred）。
- git/er/journey 新语法实现；AST 层改动；4.4 其他 P2/P3。

## Scope

### In Scope

- `nop-format/nop-mermaid/src/main/java/io/nop/mermaid/output/MermaidGenerator.java`
- `nop-format/nop-mermaid/src/test/java/io/nop/mermaid/output/TestMermaidRoundTrip.java`（新建）+ `TestMermaidGenerator` 增补
- 治理文件（Phase 3）：roadmap、index（P1 行 + P2 报告级登记）、daily log、本 plan

### Out Of Scope

- `.g4` / 生成物；其它模块

## Execution Plan

### Phase 1 - 生成器按文法字面量对齐（探针 13/13 + null edgeType）

Status: completed
Targets: `MermaidGenerator.java`、往返测试

- Item Types: `Fix`

- [x] `visitMermaidDocument`：按枚举映射文法关键字（FLOWCHART→`flowchart`、SEQUENCE→`sequenceDiagram`、CLASS→`classDiagram`、STATE→`stateDiagram`、GANTT→`gantt`、PIE→`pie`、GIT→`git`、ER→`er`、JOURNEY→`journey`）
- [x] `visitMermaidFlowNode`：shape 输出裸 token（`" " + shape.name().toLowerCase()`），删除 `:::`
- [x] `visitMermaidFlowEdge`：**四类箭头显式映射**（ARROW→`-->`、OPEN_ARROW→`->>`、DOTTED→`-.->`、THICK→`==>`）；**edgeType==null 时不输出箭头 token**；label 移到 `to` 之前、无冒号（`from edge "label" to`）
- [x] `visitMermaidSequenceMessage`：**四类箭头显式映射**（ARROW→`-->`、OPEN_ARROW→`->>`、DOTTED→`-.->`、THICK→`==>`）；**edgeType==null 时不输出箭头 token**；message 保持 `from edge to : "msg"`
- [x] `visitMermaidFlowSubgraph`：`subgraph <id> "<title>"? [ ... ]`（`[`/`]`）
- [x] `visitMermaidStyleStatement`：**target 作为转义字符串字面量**输出（`style "<target>" [ ... ]`），**移除 `id(node.getTarget())`**
- [x] `visitMermaidClassNode`：`class <name> [ ... ]`
- [x] 测试（新建 `TestMermaidRoundTrip`）：13 例探针 + 2 例 null edgeType（flow `a "lbl" b`、sequence `A B : "x"`）——每例 parse→generate→reparse，**字段级 AST 等价**（递归比较：document.type、direction.direction、flow node id/text/shape、flow edge from/edgeType/label/to、subgraph id/title/嵌套 statements（递归）、participant name/alias、sequence from/edgeType/to/message、class className/members(visibility/name/type/isStatic)、state id/description、gantt id/title/start/duration、pie label/value、style target/attributes(name/value)）；并额外断言四类箭头生成 token（**每类给出 flow 与 sequence 两种输入**：flow `a --> b`/`a ->> b`/`a -.-> b`/`a ==> b`，sequence `A --> B`/`A ->> B`/`A -.-> B`/`A ==> B`，断言输出 token 与 reparse `edgeType` 一致）与 null edgeType 无箭头
- [x] **判别验证（stash 腿）**：逐项还原（头部/shape/flow edge 箭头/flow label 位置/sequence 箭头/subgraph/style/class/null-edgeType guard）→ 对应用例 FAIL；恢复后 15/15 PASS
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`（内部实现修复）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] `./mvnw test -pl nop-format/nop-mermaid -am -T 1C`

Exit Criteria:

- [x] 13 探针例 + 2 null-edgeType 例**字段级**往返通过（测试在案，非 `_tmp` 探针；非仅 type/语句数）
- [x] 四类箭头 token 与文法一致、null edgeType 不输出箭头（测试断言在案）
- [x] 判别 stash 腿逐项 FAIL→PASS 记录在 plan
- [x] Phase 内 owner-doc 处置与 logs 条目已落
- [x] nop-mermaid 全模块测试全绿

执行证据（Phase 1 + Phase 2）：
- 探针复跑：`_tmp/MermaidRoundTripProbe.java` → **SUMMARY pass=13 polluted=0 fail=0**（修复前 pass=0 polluted=5 fail=8）。
- 模块测试：`TestMermaidRoundTrip` 19/19（13 探针 + flow/seq null edgeType + style hyphen + class static + flow/seq 四类箭头 token 判别）；`TestMermaidGenerator` 7/7（含 isStatic 不 NPE、全 append 点 id 校验、`id("")` fail-loud）。
- **判别 stash 腿**：`git show HEAD:MermaidGenerator.java`（原生成器）跑两测试类 → 26 例中 **17 failures + 5 errors**（`_tmp/r139-stash.log`）；恢复后 26/26 绿。
- 全量回归：`./mvnw test -pl nop-format/nop-mermaid -am -T 1C` BUILD SUCCESS（nop-mermaid 48/0/0）。

### Phase 2 - P2-4.4-001/002 合并（isStatic NPE + id 校验遗漏 + id("") fail-loud）

Status: completed
Targets: `MermaidGenerator.java`、测试

- Item Types: `Fix`

- [x] `visitMermaidClassMember`：`if (Boolean.TRUE.equals(node.getIsStatic()))`（消除 null 拆箱 NPE）；static 输出 `" static"`（文法 `STATIC:'static'`，替换 `" $"`）
- [x] `id()` 覆盖 `visitMermaidFlowSubgraph`（subgraph id）、`visitMermaidStateNode`（state id）、`visitMermaidGanttTask`（task id）
- [x] `id()` 对**空串** fail-loud（抛 `ERR_MERMAID_INVALID_IDENTIFIER`），不再静默返回空串
- [x] 测试：①含非 static 成员 class 图 parse→generate 不 NPE 且字段级往返；static 成员生成 `static` 且往返；②参数化/循环覆盖**全部 append 点**（flow node、flow edge from/to、participant、class name、subgraph、state、task）非法 id（中文/带 `-`）抛 `ERR_MERMAID_INVALID_IDENTIFIER`；③`id("")` 抛错；④`style "a-b" [...]` 可往返（target 为字符串字面量，不经 id()）
- [x] **判别验证（stash 腿）**：还原拆箱写法 → ①FAIL；还原三处 id 拼接 → ②对应点 FAIL；还原 `id("")` 静默返回 → ③FAIL；恢复后 PASS
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] `./mvnw test -pl nop-format/nop-mermaid -am -T 1C`

Exit Criteria:

- [x] 非 static 成员 class 图不 NPE；static 走文法 token（测试在案）
- [x] 全部 append 点非法 id 被 `id()` 拒绝；`id("")` fail-loud（测试在案）
- [x] `style "a-b"` 可往返（不经 id()）
- [x] Phase 内 owner-doc 处置与 logs 条目已落
- [x] nop-mermaid 全模块测试全绿

### Phase 3 - owner doc + 收口

Status: completed
Targets: 治理文件（roadmap/index/logs/本 plan）

- Item Types: `Proof | Decision`

- [x] owner doc 裁定：生成器输出对齐不改模块归属/公共契约（module-groups.md 无 mermaid 生成器级表述）→ `No owner-doc update required`
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.39 翻 done；index **P1-4.4-A5 → `fixed`**；**P2-4.4-001/002 为 4.4 报告级 P2（index 无行）**——在 4.4 报告加状态注记登记「已随 R1.39 合并修复」（Lead 2026-10-10 完成）；daily log 条目
- [x] 独立子 agent closure audit（`plan-closure-audit-prompt.md`），evidence 写入本 plan Closure 段
- [x] `./mvnw -q compile -pl nop-format/nop-mermaid -am` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2405-full-code-review-R1.39-mermaid-generator-grammar-alignment.md --strict` 退出码 0（Lead 台账同步后重跑通过）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-mermaid --severity high` 退出码 0（**限定本模块**：父目录 `nop-format` 存在 nop-record 两处既有 high finding，非本 plan 范围）

Exit Criteria:

- [x] owner doc 处置裁定记录
- [x] doc-links 检查退出码 0
- [x] roadmap/index/log 三方一致（roadmap R1.39=done、index P1-4.4-A5=fixed + R1.39 行=done、4.4 报告状态注记、daily log 在位）
- [x] 独立 closure audit 完成并落盘

## Closure Gates

- [x] in-scope live defect（生成器-文法不匹配 + null edgeType NPE + isStatic NPE + id 校验遗漏）已修复
- [x] 行为/契约结果已达成（字段级往返 15 例 + 两 P2 测试在案）
- [x] 必要 focused verification 已完成（nop-mermaid 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步或显式 `No owner-doc update required`
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证往返测试真实 parse→generate→reparse 且做字段级比较（非字符串存在性断言）；箭头 token 断言（flow+sequence 各四类）可判别序列/流边映射
- [x] `./mvnw -q compile -pl nop-format/nop-mermaid -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2405-full-code-review-R1.39-mermaid-generator-grammar-alignment.md --strict` 退出码 0（Lead 台账同步后重跑通过）

## Deferred But Adjudicated

### `Mermaid.g4:57-58 mermaidComment` 死规则（COMMENT 走 HIDDEN 通道）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 既有显式裁定（`testCommentOnHiddenChannelProducesNoStatement` 锁定「注释不产生语句」为预期语义）；生成器 `%% ...` 输出被 HIDDEN 通道消费，不影响往返。改文法属保护面（生成物再生），范围大于本批。
- Successor Required: `no`

### 探针文件 `_tmp/MermaidRoundTripProbe.java` 归档

- Classification: `optimization candidate`
- Why Not Blocking Closure: 13 例已端口化为模块测试类（`TestMermaidRoundTrip`）并加 null-edgeType 例；`_tmp` 探针为一次性证据。
- Successor Required: `no`

## Non-Blocking Follow-ups

- git/er/journey 三类型生成器/文法覆盖（当前无对应诊断语句 AST）

## Closure

Status Note: R1.39 的 in-scope 工作（生成器-文法对齐 + P2-4.4-001/002 合并）已在 live repo 落地并经独立子 agent 复核：探针 13/13、模块测试 48/0/0、判别 stash 腿 26 例 17F+5E、生成物 `.g4`/Lexer/Parser/Visitor 零改动、四类箭头与 null-edgeType 均被判别性断言覆盖。技术关全部 PASS，故置 `completed`。仅剩 Lead 负责的 roadmap/index 台账收口（本 plan L122/L132）与依赖该收口的两个 meta 勾选项（L125/L145）未落，因此 `check-plan-checklist --strict` 当前 exit 1（硬失败项仅此 4 个，其中 L122/L132 为 Lead 项）。

Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit (subagent)；parent session-9601d130-1bfd-4608-86de-471049fd6c0e
- Audit Session: 独立回读 live repo 的 fresh sub-agent（非实现者 self-audit；未参与实现）
- Evidence:
  - Phase 1 Exit Criteria（字段级往返 13+2 例）PASS：`TestMermaidRoundTrip.java:166-229` 13 探针例 + `:233-242` 两 null-edgeType 例；`assertDocumentEquals`/`assertStatementsEqual`/`assertStatementEqual`（`:66-155`）递归字段级等价（含 subgraph 嵌套 `:98-103`、class 成员 `:116-123`、style 属性 `:141-151`），非仅 type/语句数。
  - 箭头映射 PASS：`MermaidGenerator.edgeToken`（`MermaidGenerator.java:161-177`）ARROW→`-->`、OPEN_ARROW→`->>`、DOTTED→`-.->`、THICK→`==>`，null→单一分隔空格；`TestMermaidRoundTrip.flowEdgeTokensMatchGrammar`/`sequenceEdgeTokensMatchGrammar`（`:257-271`）flow+sequence 各四类输入，断言生成 token 且 reparse `edgeType` 一致。
  - Phase 2 P2-4.4-001 PASS：`MermaidGenerator.java:238` `Boolean.TRUE.equals(node.getIsStatic())` + `:239` 输出文法 `static`；`TestMermaidGenerator.testNonStaticClassMemberDoesNotNpe`（`:75-83`）、`TestMermaidRoundTrip.classStaticMember`（`:250-253`）。
  - Phase 2 P2-4.4-002 PASS：`id()` 作用于 subgraph `:183`、state `:247`、task `:257`；空串/非法字符 fail-loud `:299-309`；`TestMermaidGenerator.testIdentifierValidatedAtAllAppendPoints`（`:85-127`）、`testEmptyIdentifierFailsLoudly`（`:129-133`）；style target 去 `id()` 改引号 `StringLiteral`（`:280`），`TestMermaidRoundTrip.styleTargetWithHyphen`（`:244-248`）。
  - 探针复跑 PASS：`_tmp/MermaidRoundTripProbe.java` → `SUMMARY pass=13 polluted=0 fail=0`（classpath `_tmp/mermaid-cp.txt` + `nop-format/nop-mermaid/target/classes`）。
  - focused verification PASS：`./mvnw test -pl nop-format/nop-mermaid -am -T 1C` BUILD SUCCESS，`Tests run: 48, Failures: 0, Errors: 0`（RoundTrip 19 / Generator 7 / ASTParser 22）。
  - 判别 stash 腿 PASS（独立复现）：`git show HEAD:.../MermaidGenerator.java` 覆盖后单跑两测试类 → `Tests run: 26, Failures: 17, Errors: 5`（`_tmp/r139-stash-audit2.log:808`），恢复后与工作副本逐字节一致。
  - AGENTS 硬停 PASS：`git status` 对 `Mermaid.g4`/`BaseRules.g4`/`MermaidLexer.java`/`MermaidParser.java`/`_MermaidASTBuildVisitor.java` 零改动。
  - 文法核对 PASS：`BaseRules.g4:4-12` 图表字面量、`:22-32` 裸 shape token、`:35-38` 四类箭头、`:48` STATIC、`:58-59` LBRACE='['/RBRACE=']'、`:75` Identifier_；`Mermaid.g4:63`（node）、`:81`（edge: label 在 to 前、无冒号）、`:92`（subgraph）、`:101`（sequence）、`:134`（style target=StringLiteral_）逐条与生成器输出一致。
  - Closure Gates PASS：in-scope 四类 live defect 已修复；`./mvnw -q compile -pl nop-format/nop-mermaid -am` exit 0；`check-doc-links.mjs --strict` exit 0；`scan-hollow-implementations.mjs --module nop-format/nop-mermaid --severity high` exit 0（0 high/critical）；owner doc 显式 `No owner-doc update required`。
  - Anti-Hollow PASS：往返测试真实执行 parse→generate→reparse 并字段级递归比较；箭头断言可判别 flow/sequence 映射；`TestMermaidGenerator.testNonStaticClassMemberDoesNotNpe` 走 `parseFromText`→`generator.visit` 真实调用链，无空方法体/静默 no-op 充当实现。
  - Deferred 诚实性 PASS：`Mermaid.g4:57-58 mermaidComment` 为既有 HIDDEN 通道显式裁定（watch-only residual，非本 plan 引入）；P2-4.4-001/002 未虚构 index 行（index 无该两行，仅 4.4 报告 `:231` 状态注记 + 本 plan 登记）；无 in-scope live defect 被降级。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`：当前 exit 1，未勾选项精确为 L122/L132（Lead 的 roadmap/index 台账收口）+ L125/L145（依赖前者转绿的两个 meta 项）；Plan/Phase/Criteria/Gates 其余文本一致，Closure Evidence 已写入。
- Advisory（不阻断技术关，供 Lead 斟酌）：`TestMermaidGenerator.testIdentifierValidatedAtAllAppendPoints` 的 flow edge 用 `from=bad` 触发，`to` 侧无独立反向用例（live 代码 `MermaidGenerator.java:152` 已对 `to` 走 `id()`）；`edgeToken` 的 `default` 分支返回空串（当前 4 个枚举值全部显式覆盖，属不可达防御分支）。

Follow-up:

- Lead：roadmap R1.39 翻 `done`、index `P1-4.4-A5` → `fixed`、daily log 收口后勾选 L122/L132，并复核 L125/L145 使 `check-plan-checklist --strict` exit 0。
- 其余 non-blocking follow-up 见本 plan `Non-Blocking Follow-ups`（git/er/journey 新语法覆盖，out-of-scope improvement）。
