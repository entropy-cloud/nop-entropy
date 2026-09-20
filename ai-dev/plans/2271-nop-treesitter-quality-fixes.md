# 2271 nop-treesitter 质量收口：ThreadLocal 泄漏修复 + 死代码清理 + 文档同步

> Plan Status: completed
> Last Reviewed: 2026-09-20
> Source: `ai-dev/analysis/2026-09/2026-09-20b-nop-treesitter-code-quality-review.md`
> Related: `ai-dev/backlog/nop-treesitter-roadmap.md`（已 17/17 done，本计划不重开 roadmap 项）

> Draft review：独立子 agent 两轮对抗性审查（第一轮 2 Major + 6 Minor，全部修订后第二轮逐项复核 8/8 PASS，结论"可以直接执行"）。审查 agent：`agent_3529c675-6b80-4276-937a-b27f2bbba7c4`。

## Purpose

把 nop-treesitter 质量审查发现的 1 个 P0 缺陷（ThreadLocal 持有已解析 tree/source 的内存泄漏）、1 组死代码、2 处 owner-doc drift 和 3 项低风险热路径问题收口到"修复且有测试背书、文档与 live baseline 一致"的状态。全部为非语义变更（不改任何解析行为契约）。

## Current Baseline

- `./mvnw test -pl nop-treesitter -am` 405 个测试全绿（2026-09-20 基线，含 benchmark smoke）。
- roadmap 17/17 done、mission 关闭；`nop-code-lang-python/typescript` 已依赖 compat 层，compat 是活跃契约面。
- 已裁定遗留（本计划不动）：多轮恢复树形残差（4 corpus watch-only 节）、per-subtree refcount 回收（successor design, not scheduled）。
- 审查确认的缺陷与问题清单见 source analysis 的 TS-0 ~ TS-7；死代码零调用方均已 grep 验证。

## Goals

- TS-0：`TSNode.SCRATCH` 与 `ScannerVM.POOL` 两个 ThreadLocal 不再在空闲时持有 `TSTree` / source 字节，且复用路径行为不变，有回归测试。
- TS-1：删除全部已确认死代码（`TreeSitterBootstrap` 及其测试、`GLRParser.parseError`、`PausedToken.isEof`、`Iter.pending`、`Lexer.next`、`TreeNavigator` 分配式导航死链），编译期与 grep 双重验证无残留引用。
- TS-3/6/7：外部扫描 validSymbols 走 memoized 入口；GLR debug 开关类加载期固化；UTF-8 解码三处重复合并为 `io.nop.treesitter.util.Utf8`。
- TS-4：`TSTree` canonical sexp 渲染路径消除 per-child 的结构性索引重复线性扫描（`collectVisible` 直传已维护的索引）。
- TS-2：三处 owner-doc 与 live baseline 一致（README 内置语法含 python、docs-for-ai 模块页性能数字更新为 09-13 收口值、provider javadoc 六语法）。
- 全程不引入任何解析输出/错误恢复/增量复用的行为变化（现有 405 测试 + corpus 断言不动且全绿）。

## Non-Goals

- 不做 TreeNavigator 计数缓存/结构化导航优化（优化候选，需独立 perf 验证）。
- 不做 TSQuery step-machine 重写、`estimateRenderLength` 策略调整。
- 不做 per-subtree refcount / dead-branch 回收（既有 successor design，未排期）。
- 不动 corpus 4 个 watch-only 裁定节、不动 compat 层 API、不改任何 public API 语义。
- 不处理 TS-8 良性竞态与 TS-9 递归深度限制（审查已裁定记录即可）。

## Scope

### In Scope

- `nop-treesitter/src/main/java/io/nop/treesitter/TSNode.java`、`cursor/TSTreeCursor.java`、`scanner/ScannerVM.java`（TS-0）
- `TreeSitterBootstrap.java`、`parser/glr/GLRParser.java`、`lexer/Lexer.java`、`cursor/TreeNavigator.java`（TS-1/3/6/7）
- `TSTree.java`、`util/`（新增 `Utf8.java`）（TS-4/7）
- `nop-treesitter/README.md`、`docs-for-ai/03-modules/nop-treesitter.md`、`DefaultTreeSitterLanguageProvider.java` javadoc（TS-2）
- 对应新增/调整的测试与 `ai-dev/logs/2026/09-20.md`

### Out Of Scope

- `docs/`、`nop-code/nop-code-lang-python`、`nop-code/nop-code-lang-typescript`、benchmark 数值复测（行为无变化，无需新基准）
- blob 格式、codegen（`ParserCExtractor`/`BlobWriter`/`BlobReader`）改动

## Execution Plan

### Phase 1 - P0：ThreadLocal 引用泄漏修复

Status: completed
Targets: `TSNode.java`、`cursor/TSTreeCursor.java`、`scanner/ScannerVM.java`、新增回归测试

- Item Types: `Fix`、`Proof`

- [x] `TSTreeCursor` 增加释放引用的方法：清空对 `TSTree`/`TreeNavigator` 的持有（内部缓冲数组保留以便复用）；`resetTo` 在 navigator 缺失时按现有逻辑重建，保证释放后的 cursor 可安全复用。两包不同（`io.nop.treesitter` vs `.cursor`），方法须为 `public`，javadoc 必须写明契约：释放后的 cursor 须先 `resetTo` 才能继续使用（审查 m4）
- [x] `TSNode.releaseScratch` 在 depth 归零时对 scratch cursor 执行上述释放；`ScannerVM.run` 用 try/finally 包裹解释与 Result 构造，在 finally 中清空 VM 对 `program`/`source`/`validSymbols` 的持有——必须覆盖全部退出路径，包括 `execute()` 抛 `TreeSitterException`（step budget 超限 / 未知 opcode / 栈溢出）的异常路径（审查 M1）。已知并接受的 tradeoff：释放后每次顶层 `TSNode` accessor 会重建一个小 `TreeNavigator` 对象（行为输出不变，分配敏感史见 perf-tuning，回归测试断言两字段均 null 可拦截只清一半的实现）（审查 m5）
- [x] 新增回归测试：通过反射断言 (a) 调用过 `TSNode` 结构 accessor 后，scratch cursor 不再持有 tree 引用；(b) `ScannerVM.run` 完成后 POOL 中 VM 不再持有 program/source；(c) 释放后的 scratch cursor 再次访问节点时结果正确（复用路径不回归）
- [x] 验证泄漏场景语义：`TSNode` 深层嵌套 accessor（嵌套 borrow，depth ≥ 1）行为不变（现有 `TSNode`/query/cursor 测试覆盖）

Exit Criteria:

- [x] 新增回归测试覆盖上述 (a)(b)(c) 三点并通过，其中 (b) 含异常路径用例（触发 `execute()` 抛异常后 POOL 中 VM 字段仍为 null）——`ThreadLocalRetentionTest` 4/4 通过
- [x] `grep -rn "ThreadLocal" src/main/java` 复查：命中恰为 3 处——`TSNode.SCRATCH`、`TSNode.SCRATCH_DEPTH`（int[] 计数器，无泄漏面）、`ScannerVM.POOL`，且 SCRATCH/POOL 空闲态不持有 tree/source（由测试断言背书）
- [x] `./mvnw test -pl nop-treesitter -am` 全绿（基线 405 中本 Phase 不删测试，总数 = 405 + 新增）——exit 0（2026-09-20 后台全量跑，407 tests 0 fail）
- [x] **无静默跳过**：释放路径是确定性清理而非空方法；`resetTo` 重建分支有测试
- [x] `TSTreeCursor` 新 public 方法的契约（释放后须 resetTo）已写入 javadoc；`No owner-doc update required`（docs-for-ai/README 未承诺 TSTreeCursor 方法级 API 面，compat 层不受影响）
- [x] `ai-dev/logs/` 对应日期条目已更新（统一收口条目覆盖 Phase 1-4）

### Phase 2 - 死代码清理

Status: completed
Targets: `TreeSitterBootstrap.java`、`TreeSitterBootstrapTest.java`、`GLRParser.java`、`Lexer.java`、`TreeNavigator.java`

- Item Types: `Fix`

- [x] 删除前逐项 grep 复核零引用（含 `nav.aliasAt` 是否有调用方，有则保留）：`TreeSitterBootstrap`+测试、`GLRParser.parseError`、`PausedToken.isEof`、`Iter.pending`（含构造参数与两处 `new Iter(...)` 传参收敛）、`Lexer.next`、`TreeNavigator.childRef/scan/ChildRef/stepVisible/namedRelevant/foundVisibleGrandchildCount`
- [x] 顺带清理同文件相邻噪音：`GLRParser.popCount` 重复 javadoc（:1855-1868 两段连排）合并为一段
- [x] 确认 `Lexer` 类 javadoc 在删除 `next()` 后仍准确（`nextForParse` 是 C `ts_parser__lex` 的对应物）

Exit Criteria:

- [x] 上述符号在 `src/` 下用 `grep -E -rn 'TreeSitterBootstrap|parseError\(|isEof\(|\.pending|Lexer\.next\(|childRef|foundVisibleGrandchildCount'` 检索零残留（注意 `-E`：BRE 下 `|` 是字面量，会假阳性通过；审查 m1）
- [x] 编译通过 + `./mvnw test -pl nop-treesitter -am` 全绿（本 Phase 删除 `TreeSitterBootstrapTest` 的 2 个测试，总数 = 405 − 2 + Phase 1 新增，0 fail 0 error，skip 数不变）
- [x] Doc-sync 裁定：死代码不在任何文档契约面中；唯 `ai-dev/backlog/nop-treesitter-roadmap.md` 的历史叙述引用过已删类（原相对路径在删除前即已解析失败），已随收口修复（5659ec2a60 与本次 roadmap:176 补修），strict link check 归零

### Phase 3 - 热路径小修与去重

Status: completed
Targets: `lexer/Lexer.java`、`parser/glr/GLRParser.java`、`TSTree.java`、`scanner/ScannerVM.java`、新增 `util/Utf8.java`

- Item Types: `Fix`、`Proof`

- [x] TS-3：`Lexer.externalScan` 改用 `Language.validSymbols(parseState)`（memoized），删除 `ScannerVM.validSymbols(Language,int)` 公共静态入口（审查已复核全仓唯一调用点为 Lexer.java:213、无测试调用；执行时如发现新调用方则保留入口并收敛调用）
- [x] TS-6：GLRParser 四处 `Boolean.getBoolean("ts.debug")` 固化为类加载期一次读取的静态常量（行为差异：属性需在类加载前设置——注释写明）
- [x] TS-4：`TSTree.collectVisible` 直传遍历中已维护的 structuralIndex，消除 `effectiveSymbol→structuralIndexOf` 的 per-child 线性扫描；`structuralIndexOf` 若无其余调用方一并删除
- [x] TS-7：新增 `io.nop.treesitter.util.Utf8` 承载共享 codepoint 解码；`Lexer.decodeCodepoint`、`ScannerVM.decodeCodepoint`、`Lexer.JavaScanContext` 改用之；`Lexer.decodePacked`（热路径 packed 变体）保留并注明与 `Utf8` 的关系
- [x] 新增 `Utf8` 单元测试（ASCII/2/3/4 字节/截断序列/EOF），替代原散落的隐式覆盖

Exit Criteria:

- [x] `Utf8Test` 通过；lexer/scanner 相关既有测试（`LexerTest`、`ScannerVMTest`、`LexerExternalScanTest`、Python/JS/TS corpus）全绿
- [x] `./mvnw test -pl nop-treesitter -am` 全绿
- [x] `grep -c "private static int\[\] decodeCodepoint" src/main/java -r` 只剩 0 处（实现归一至 `util/Utf8`）
- [x] No owner-doc update required（纯内部实现归一，无契约变化；perf-tuning 的 optimization candidate 记录不因本项过期——它记录的是更大的结构优化）

### Phase 4 - owner-doc 同步与收口

Status: completed
Targets: `nop-treesitter/README.md`、`docs-for-ai/03-modules/nop-treesitter.md`、`provider/DefaultTreeSitterLanguageProvider.java`、`ai-dev/logs/`

- Item Types: `Fix`

- [x] README 内置语法列表补 `python`（与 `BUILTIN_BLOBS` 六条一致，注明 python 经 provider 自动接线 `PythonScanner`）
- [x] `DefaultTreeSitterLanguageProvider` 类 javadoc "five shipped grammars" 改为六语法口径
- [x] `docs-for-ai/03-modules/nop-treesitter.md` 性能节更新为 09-13 perf-closure 收口数字（四基准 ≤3x），与 `perf-tuning.md` 一致；如 docs-for-ai 存在其他页引用旧数字（`04-reference/source-anchors.md` 等）一并核对
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0——过程中发现 roadmap 对已删 `TreeSitterBootstrap.java` 的失效引用并修复（含执行前就已解析失败的相对路径），最终 0 errors / 24 warnings（09-14 已记录的存量）

Exit Criteria:

- [x] 三处文档与 live baseline 一致，数字有 perf-tuning.md 佐证（另发现并修复 roadmap 失效引用一处）
- [x] doc link checker 退出码 0
- [x] `ai-dev/logs/2026/09-20.md` 收口条目完成（含基线、改动、验证结果）

## Closure Gates

> 所有 Phase Exit Criteria 全部 `[x]` 后才可进入关闭流程；独立 closure audit 由单独子 agent 执行。

- [x] TS-0 泄漏修复已落地且有回归测试（空闲态不持有 tree/source 由测试断言）——`ThreadLocalRetentionTest` 4/4，audit A1 PASS
- [x] TS-1 死代码全部移除且 grep 零残留——audit A2 PASS（TreeNavigator 死链 92 行确认删除）
- [x] TS-3/6/7 热路径修复落地，UTF-8 解码单点化——audit A3 PASS
- [x] TS-4 渲染平方复杂度消除——audit A4 PASS
- [x] TS-2 三处 owner-doc drift 收敛——audit A5 PASS（audit 另发现 source-anchors.md TS-002 五→六残留，已补修）
- [x] 无 in-scope live defect 被降级到 deferred / follow-up——audit 指出 TS-5（TSTreeCursor O(1) javadoc 诚实性）曾被 plan 遗漏，已补修（javadoc 已改为如实描述 locateChild 线性扫描 + 优化候选出处），非 deferred
- [x] 解析行为零变化：现有 corpus/增量/恢复测试全数通过且未修改其断言——audit A6 PASS（diff 仅 +2 新测试/−1 计划内删除，无既有断言与 corpus 改动）
- [x] 独立子 agent closure audit 完成并记录证据——见 Closure 段
- [x] **Anti-Hollow Check**：audit 验证泄漏修复路径真实执行（测试断言字段为 null，非仅编译通过）；无新增空方法/静默跳过——audit C PASS
- [x] `./mvnw compile -pl nop-treesitter -am` 通过——audit 实测 exit 0
- [x] `./mvnw test -pl nop-treesitter -am` 通过——414 tests / 0 fail / 0 error / 2 skip（既有 watch-only corpus 节）；audit 抽查 retention/Utf8 单类实测通过
- [x] checkstyle：`./mvnw checkstyle:check -Pqa` 可执行（注：checkstyle 在父 pom 默认构建中被注释、仅 qa profile 启用且 failOnViolation=false；规则集为仓库根 `checkstyle.xml`。本计划改动不引入新违规即可，不追求清偿 2026-09-20 全仓审计已记录的存量 IAE/ISE——那是独立的 P2/T4 事项）——实测 `-pl nop-treesitter` EXIT=0

## Deferred But Adjudicated

### TreeNavigator 计数缓存 / O(1) 导航结构优化

- Classification: `optimization candidate`
- Why Not Blocking Closure: 现有实现正确且有 corpus 背书；性能优化的收益需要专门基准与回归面，审查已确认四基准均 ≤3x 达标
- Successor Required: `no`（若未来 perf 目标收紧，另立 plan）

### TSQuery step-machine 化

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前子集语义正确、查询引擎无性能投诉面；重写属结构性工程
- Successor Required: `no`

### per-subtree refcount / dead-branch 回收

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 已有 successor design 记录在 `perf-tuning.md`，roadmap 明确 not scheduled
- Successor Required: `no`

## Non-Blocking Follow-ups

- `docs-for-ai` 其余页面对 nop-treesitter 旧性能数字的引用可在例行 docs 巡检中顺带核对（Phase 4 已核对 source-anchors 与模块页）。
- `compat/TreeSitterTypescript` 用 typescript blob 服务 .tsx 的语义边界说明可补充进 compat javadoc（现 README 已声明 tsx 独立 blob 走 provider）。

## Closure

Status Note: 分析报告 TS-0~TS-7 中裁定为本计划 scope 的全部条目（TS-0 泄漏、TS-1 死代码、TS-2 文档 drift、TS-3/6/7 热路径、TS-4 渲染平方、TS-5 javadoc 诚实性）已落地并经独立子 agent closure audit 逐项复核 PASS；解析行为零变化（corpus/增量/恢复断言零改动，414 tests 全绿）。TS-8/TS-9 与三项 Deferred 均为审查裁定的 non-blocking（优化候选/既有 successor design/良性竞态），无 in-scope live defect 被降级。
Completed: 2026-09-20

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）`agent_d46d3490-3c6d-429d-ae9b-70f728d1fa75`
- Audit Session: agent_d46d3490-3c6d-429d-ae9b-70f728d1fa75
- Evidence:
  - A1~A6（六个 Phase Exit Criteria 维度）全部 PASS：release()/try-finally 全退出路径实测存在、`ThreadLocalRetentionTest` 为反射 assertNull 断言（含 opcode 0xFF 异常路径用例）、死代码 grep 零残留（TreeNavigator 死链 92 行 diff 确认）、Utf8 单点化 + `Utf8Test` 5/5、collectVisible structuralIndex 直传、三处 owner-doc 与 live 一致、`git diff 66f114d5bd..HEAD -- nop-treesitter` 证明测试断言与 corpus 零改动（仅 +2 新测试/−1 计划内删除）。
  - Closure Gates 12 条全 PASS（其中 `./mvnw compile -pl nop-treesitter`、ThreadLocalRetentionTest+Utf8Test 单类、`./mvnw checkstyle:check -Pqa -pl nop-treesitter` 由 auditor 本地实测）。
  - Anti-Hollow：释放路径有测试断言背书；`TSTreeCursor.release` javadoc 契约完整；新增代码无 TODO/空方法体/静默跳过。
  - Audit 发现并已修复（全部文档级）：Major-1 TS-5 javadoc O(1) 声明与实现不符（plan 曾遗漏该条目）→ TSTreeCursor 类 javadoc 已改为如实描述；Minor-2 Phase 2 doc-sync 勾选项措辞与事实不符 → 已勾选并改写；Minor-3 plan/log 更新未提交 → 本收口提交解决；Minor-4 `source-anchors.md` TS-002 五语法残留 → 已补 python；Minor-5 roadmap:176 对已删测试类的引用 → 已改写。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-treesitter --severity high` 退出码 0。

Follow-up:

- TreeNavigator 计数缓存 / TSQuery step-machine / per-subtree refcount：见 Deferred But Adjudicated（均为已裁定的优化候选或既有 successor design，非本计划遗留 defect）
- `docs-for-ai` 其余页面对 nop-treesitter 旧性能数字的引用可在例行 docs 巡检中顺带核对
- `compat/TreeSitterTypescript` 的 .tsx 语义边界说明可补充进 compat javadoc
