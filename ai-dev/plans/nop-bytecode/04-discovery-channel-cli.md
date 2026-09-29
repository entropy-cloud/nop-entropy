# 04 发现流通道 — CLI + 诊断结构对齐 + 去重口径落实（roadmap item 5, Wave 1, M1 收口）

> Plan Status: completed
> Last Reviewed: 2026-09-29
> Source: [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md) Wave 1 item 5 + [00-overview.md](../../design/nop-bytecode/00-overview.md) §3.1 通道层契约 + [gap-ledger](../../../nop-bytecode/docs/gap-ledger.md) §三 去重边界 + 准入判据 3
> Related: [03-kernel-cfg-dataflow-nullflow.md](03-kernel-cfg-dataflow-nullflow.md)（内核已落地）

## Purpose

打通从"编译产物输入"到"诊断输出"的完整发现流通道：形态裁定（CLI vs maven goal）、诊断结构与 nop-lint 对齐（统一 AI/开发者消费面）、准入判据 3 的去重口径落实。完成后 M1 达成（内核 + 通道可跑、基线在档），Wave 2/3 分析器有输出面可挂。

## Current Baseline

- plan 03 完成（commit 38ea80063e）：内核 v1 + nullness 分析在库，13/13 tests；`NullflowAnalyzer.analyze(byte[]) → List<DerefFinding>`；采集层 v0（`ClassArtifactCollector`，manifest 增量）。
- nop-lint 诊断结构（对齐目标，只读参照）：`Diagnostic(ruleId, severity, message, range, fix)`，按文件聚组渲染、确定性顺序（rule/match 序）。字节码层无源码行语义——位置结构对齐为"坐标"（class#method@insn）而非行号；fix 轴不适用（范围锚 4：autofix 永不入本通道）。
- gap-ledger §三去重边界已在档：pattern 面 5 条规则归源码 lane、closeable-not-closed 归源码 lane、双报处置归 Wave 4 item 8。**注意（源码事实）**：内核对 MAYNULL 接收者的任意实例方法调用发命中——无守卫的 `x.equals(y)` 与源码 lane equals-null 规则在平凡路径子集上**真实重叠**；通道层去重口径 = **ruleId 命名空间隔离（`nullflow/`）+ 平凡路径重叠按 gap-ledger §三 归 item 8 对照裁决**，而非"零重叠"。同缺陷同位置双报的结构性排除 = 通道级 finding 键去重（同 class 多输入只报一份）。
- roadmap item 5 = `todo`；docs-for-ai 模块页在 plan 02/03 均以"首个用户可消费面随 plan 04 补建"为 deferred 依据——本 plan 落地该消费面，故页面与本 plan 同交付。

## Goals

- 形态裁定：v1 = 独立 CLI（`io.nop.bytecode.cli.NopBytecodeMain`，java -cp 直跑，与采集层/内核同模块）；maven goal 形态显式 Deferred（Follow-up）。
- 通道层 `io.nop.bytecode.cli`：`Finding`（ruleId/severity/message/class/method/insnIndex/ref，与 nop-lint Diagnostic 字段结构对齐的文档化映射）、`FindingRenderer`（console 文本 + `--json` 两形态，确定性排序）、`NopBytecodeMain`（args = 采集输入列表 + `--manifest <path>` 可选 + `--json` 可选；缺失输入响亮失败退出码 2，报告 only 语义退出码恒 0——report-only 起步，Hard constraint 6）。
- 去重口径落实：`RuleIds` 常量（`nullflow/may-null-deref` 单一 ruleId，命名空间=通道面）；README + docs-for-ai 页写明"本通道只上报路径敏感面，pattern 面归源码 lane，双报处置在 Wave 4 item 8"。
- docs-for-ai `03-modules/nop-bytecode.md` + INDEX 路由行。
- roadmap item 5 状态流转；M1 达成标注。

## Non-Goals

- 不做 null-flow 分析器正式版（item 6，plan 05——通道先挂 v1 内核降配口径产出，输出标 ruleId 相同、dialect 差异在文档注明）。
- 不做资源配对分析器（item 7，plan 06）。
- 不接 CI（item 9，plan 08）；任何门禁/hard gate 均不做。
- 不做 suppression/baseline 机制（并行对照期后按需另立）。
- 零修改 nop-lint（对齐 = 结构参照，无代码/格式依赖——不 import nop-lint 任何类）。

## Scope

### In Scope

- `nop-bytecode/src/main/java/io/nop/bytecode/cli/**`（Finding/FindingRenderer/NopBytecodeMain/RuleIds）
- `nop-bytecode/src/test/java/io/nop/bytecode/cli/**`（端到端/渲染/退出码测试）
- `nop-bytecode/README.md`（通道用法节；既有模块 README 的改写——plan 02 已建）
- `docs-for-ai/03-modules/nop-bytecode.md` + `docs-for-ai/INDEX.md`（路由双行，nop-treesitter 先例）+ `docs-for-ai/04-reference/source-anchors.md`（`io.nop.bytecode.cli` 消费面锚点行——AGENTS.md Mandatory Updates #3）
- `ai-dev/design/nop-bytecode/00-overview.md`（§3.1 通道层行终态：CLI 形态裁定 + 去重口径落实）
- `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`（动态块 + M1 标注）、`ai-dev/logs/{执行当日}.md`

### Out Of Scope

- maven goal（`nop-bytecode-maven-plugin`）——Deferred
- nop-lint 任何文件；现有工具接线；CI workflow

## Execution Plan

### Phase 1 — 通道层与形态裁定

Status: completed
Targets: `io.nop.bytecode.cli`、README、00-overview、docs-for-ai

- Item Types: `Decision | Fix`

- [x] draft review 通过后：roadmap item 5 `todo`→`planned` + `Last updated` 刷新
- [x] `RuleIds`（`nullflow/may-null-deref` 唯一常量 + javadoc 去重口径声明）
- [x] `Finding` record：ruleId/severity/message/className/methodName/insnIndex/ref——javadoc 写明与 nop-lint `Diagnostic(ruleId,severity,message,range,fix)` 的结构对齐映射（location=class#method@insn 坐标系；fix 轴不适用=范围锚 4）；**severity 由通道固定 `warning`**（v1 单档；字符串词表与 nop-lint 同形）
- [x] `FindingRenderer`：console 形态（每 finding 一行：`[severity] ruleId class#method@insn (ref) message`，排序确定性：className→method→insn）+ `--json` 形态（无外部依赖：手写 JSON 转义，数组按同序）
- [x] `NopBytecodeMain`：**进程内入口 `run(String[] args, PrintStream out, PrintStream err) → int`**（测试打 run()），`main()` 仅委托 `System.exit(run(...))`。args = `(<dir|jar>...) [--manifest <path>] [--json]`，输入 kind 自动判定（isDirectory→DIRECTORY、文件名 .jar 结尾→JAR、否则 usage error）
- [x] **管线关联机制（B1/N1 裁定）**：`ClassArtifactCollector.collect` **一次传入全量 input 列表**（manifest baseline 语义不变——逐 input 分次调用会被 collector 的 removed 清理互相抹除条目，禁止）；字节读回 = **逐 input 重枚举 × 扁平 artifacts 的 relativePath 集合配对**（目录：root.resolve(relativePath)；jar：按完整路径重开 JarFile 按 entry 名读；重枚举中 relativePath 找不到 = 响亮失败）。读回字节后与 `artifact.sha256()` 比对，不符 = 响亮失败（TOCTOU 闭环）。**跨输入重复 relativePath（同名类经 dir+jar 双输入）在扁平列表上检测 = 响亮失败**（消息含冲突清单）——多报告正确性优先于静默吞并
- [x] **完整退出码表**（判定顺序：任一失败即 fail-fast，**不输出部分报告**——nop-lint「partial report is never printed」先例）：usage error（未知 flag/--manifest 缺参/零输入/kind 歧义）=2；MissingInputException=2；采集层 NopBytecodeException（坏 magic/不可解析/冲突）=2；**分析器 NopBytecodeException（unhandled opcode 等）=2**（错误数据宁可响亮——report-only 的"只报不错"边界在数据完整性，CI 降级策略若需要归 item 9 独立裁定）；正常完成（含有 findings）=0（**report-only，HC6；与 nop-lint findings=1 的哲学分歧须在 README/docs-for-ai 显式注明**）。`--manifest`：run 结束 save 回写；load 时文件损坏 = 响亮失败（manifest 已升级为持久化契约载体——plan 02 审计 m1 的"advisory 静默跳过"口径自本 plan 起废弃，CollectManifest.load 对畸形行抛 NopBytecodeException）
- [x] **通道级 finding 去重**：按 (className, methodName, insnIndex, opcode, ref) 键去重（同 class 经多输入重复分析只报一份）
- [x] **plan 02 路由给本 plan 的两项裁定**：(a) `NopBytecodeException` ErrorCode 面重估——裁定**维持直继 RuntimeException**（CLI 消费面无 ErrorCode 需求；退出码即消费契约）；(b) 多版本 jar（META-INF/versions）通道口径——**按 className 去重**（同名类只分析一份；**取证优先级 = relativePath 不以 META-INF/versions/ 开头者优先**，冲突时非 versions 侧胜出；roadmap Wave 4 item 8 对照期如需细分再裁）
- [x] `00-overview` §3.1 通道层行终态：CLI 形态裁定 + 去重口径已落实（maven goal Deferred）
- [x] docs-for-ai 页面（结构参照 nop-treesitter.md：功能概览/快速开始/架构要点）+ INDEX 双行 + source-anchors 行；README **新建**（模块尚无 README）通道用法节——**写死完整 `java -cp` 命令**（含 asm/asm-tree jar，非单 jar）与 report-only 退出码语义声明

Exit Criteria:

- [x] 新增功能的测试覆盖由 Phase 2 承担（guide 规则 25 归属声明）
- [x] 00-overview 通道层行 + docs-for-ai 页 + INDEX 行在档（grep 可验）
- [x] `check-doc-links.mjs --strict` 0 errors
- [x] `ai-dev/logs/{执行当日}.md` 条目已更新

### Phase 2 — 端到端与渲染测试

Status: completed
Targets: `src/test/java/io/nop/bytecode/cli/**`

- Item Types: `Proof`

- [x] **端到端测试**：@TempDir 编译 toy fixture（含 unguarded/guarded 各一）→ 调 `run(args, out, err)`（注入流，非 System.setOut）→ console 输出含 `[warning] nullflow/may-null-deref ToyNull#derefWithoutCheck@`、不含 guarded 方法命中 → 返回 0
- [x] **JSON 形态测试**：`--json` 输出为合法 JSON（解析回断言字段），排序与 console 一致；**转义专项**：quote `"`、反斜杠、控制字符（\n\t\r、<0x20）、unicode 类名、`<init>`/`<clinit>`、ref 三形态（`[array]`/`Owner#field`/`owner.method`）逐场景 round-trip 相等
- [x] **退出码测试**：缺失输入目录 → 退出码 2 且 stderr 含路径（响亮失败）
- [x] **去重口径测试**：(a) 同一 class 经 dir+jar 双输入 → 每个命中位置只输出一份（通道级去重键）；(b) 输出 ruleId 全部为 `nullflow/` 命名空间
- [x] **响亮失败分支测试**（每个退出码 2 分支一测）：跨输入 relativePath 冲突；sha256 不符（读回字节被篡改）；usage error（未知 flag / 零输入 / kind 歧义文件）；manifest 文件损坏；断言返回 2 且 err 含具体原因

Exit Criteria:

- [x] `./mvnw test -pl nop-bytecode -am` 全绿（含既有 13 项 + 新增 ≥9 项：e2e/JSON+转义/退出码/去重双条/响亮失败五分支）；**Phase 1 不得先于 Phase 2 标 completed**（规则 25 同 Phase 张力：测试在 Phase 2）
- [x] **端到端验证**：从"目录输入"到"stdout 诊断输出"完整跑通（run() 调用即入口点到出口点）
- [x] **接线验证**：CLI 管线确实调用采集层与内核（端到端测试经 collect→analyze 真实路径，非 mock）
- [x] **无静默跳过**：缺失输入退出码 2 + stderr 路径（测试断言）
- [x] owner-doc 裁定：docs-for-ai 页 = 本 Phase 交付；`ai-dev/design` 无新增（00-overview 通道行已列 Phase 1）
- [x] `ai-dev/logs/{执行当日}.md` 条目已更新

### Phase 3 — 收口（M1 达成）

Status: completed
Targets: roadmap、本 plan

- Item Types: `Proof | Follow-up`

- [x] roadmap item 5 `planned`→`done`（指针 + audit id）；**Milestone M1 行尾追加 `——**UNLOCKED {日期}**`（M0 先例格式）**
- [x] 文本一致性五处核对
- [x] 终门禁：check-plan-checklist --strict 0 / check-doc-links --strict 0 / scan-hollow-implementations --module nop-bytecode --severity high 0
- [x] 独立子代理 closure audit + evidence 写入
- [x] 单提交（选择性 add）

Exit Criteria:

- [x] roadmap item 5 = done 带指针与 audit id；M1 标注
- [x] 三门禁 0；`./mvnw test -pl nop-bytecode -am` 收口复跑全绿
- [x] closure evidence 写入
- [x] `ai-dev/logs/{执行当日}.md` 收口条目

## Closure Gates

- [x] 通道层落地：CLI 端到端（输入→诊断输出）测试锁定；console + JSON 双形态确定性
- [x] 形态裁定落档：CLI v1 / maven goal Deferred；去重口径（命名空间 + 路径敏感面单报）在 RuleIds javadoc + docs-for-ai 页 + 00-overview 三处一致
- [x] report-only 退出码语义实现并测试（HC6：升 hard gate 须独立 plan + 误报数据）
- [x] roadmap item 5 done 带指针与 audit id；M1 标注达成
- [x] 独立 closure audit 完成并记录证据
- [x] 三门禁 0 + `./mvnw test -pl nop-bytecode -am` 全绿

## Deferred But Adjudicated

### maven goal 形态（nop-bytecode-maven-plugin）

- Classification: `out-of-scope improvement`（相对本 plan）
- Why Not Blocking Closure: CLI 已满足"独立发现流"的消费面（CI 可用 java -cp 直跑）；maven goal 是工程糖，且 plugin 发行使 pom 依赖面进入消费方——等真实消费需求出现再立项
- Successor Required: `no`
- Successor Path: 需要时另立 plan

## Non-Blocking Follow-ups

- suppression/baseline 机制——并行对照期（item 8）产出的 delta 数据决定形态后另立
- `--fail-on-findings` 开关——升 hard gate（item 9 独立 plan）时随接线形态一并裁定

## Closure

Status Note: item 5 收口：发现流通道 CLI 落地（report-only 退出码 0/2、console+JSON 双形态确定性、诊断结构与 nop-lint Diagnostic 结构对齐）、去重口径三处落实（ruleId 命名空间 `nullflow/` + 通道级发现键去重 + 平凡路径重叠归 item 8）、plan 02 路由两裁定落定（Exception 直继维持、多版本 jar className 去重 base 优先——含实现与测试）。M1 达成（内核+通道可跑、基线在档），Wave 2/3 解锁。
Completed: 2026-09-29

Closure Audit Evidence:

- Reviewer / Agent: agent_176d6bd2-a433-44ff-bc1b-fc0c1122e326（独立 fresh-session 子代理，含真机 CLI smoke）
- Audit Session: sess_49c9956b-0ccc-48c1-ba6b-d6a1bd2eb130 / agent_176d6bd2
- Evidence:
  - 首轮审计 REJECT（4 Major）：M1 manifest 损坏响亮失败未实现（CollectManifest.load 静默跳过）→ 改为畸形行抛 NopBytecodeException（plan 02 审计 m1 的 advisory 口径显式废弃，注释同步）；M2 README/docs 快速开始命令真机失败（build-classpath 输出落模块 basedir）→ 改 target/test-classpath.txt 相对形态并**真机复演通过**（exit 0 + 真实 findings）；M3 多版本 jar 裁定未落地且 flat 排序致 versions 侧反而胜出 → 实现 `preferBaseOverMultiRelease`（className 去重，非 versions 优先）+ `multiReleaseBaseEntryWinsOverVersionsEntry` 测试；M4 测试缺口（sha256 篡改/manifest 损坏分支、≥9 自诺）→ 补 4 测试至通道新增 10 项、23/23 全绿
  - 复审要点全过：Phase 1 结构主体 PASS（pipeline 裁定六要素源码定位 :30-32/:57-83/:92/:103/:119-123/:165-175/:177-203）；Phase 2 实跑 23/23（auditor 实跑 20/20 + 修复后 3 项新增）；Anti-Hollow PASS（无空壳、真机 CLI 独立连通——审计员按 README 思路 java -cp 直跑产出确定性 JSON）；纯增量 PASS（root pom diff 空、nop-lint 零改动、足迹=允许集）
  - 三门禁：`check-plan-checklist.mjs --strict` 退出码 0（evidence 写入后复跑）；`check-doc-links.mjs --strict` 0 errors（含 docs-forai↔ai-dev 边界违规自纠）；`scan-hollow-implementations.mjs --module nop-bytecode --severity high` EXIT=0；`./mvnw test -pl nop-bytecode -am` 复跑 23/23
  - Deferred 分类：maven goal（out-of-scope improvement，CI 可 java -cp 直跑）+ suppression/--fail-on-findings（锚定 item 8/9 successor）均真 non-blocking；无 in-scope defect 降级
  - Minor 处置：m2 JSON 真实 parse-back（test 内置最小解析器）已补；m3 UncheckedIOException 已入退出码契约；m1 plan 措辞已同步

Follow-up:

- suppression/baseline 机制——item 8 对照期 delta 数据定形态后另立
- `--fail-on-findings` 开关——item 9 升 hard gate 的独立 plan 一并裁定
- maven goal（nop-bytecode-maven-plugin）——真实消费需求出现时立项

