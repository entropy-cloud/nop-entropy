# 03 内核 v1 — 方法内 CFG + 抽象解释 + nullness 分析 + JMH 基线（roadmap item 4, Wave 1）

> Plan Status: completed
> Last Reviewed: 2026-09-29
> Source: [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md) Wave 1 item 4 + [substrate-adjudication.md](../../design/nop-bytecode/substrate-adjudication.md) §五（tree.analysis 复用 vs 自建 = 自建 + oracle 纪律）+ [00-overview.md](../../design/nop-bytecode/00-overview.md) §3.1 内核层契约 + [nop-lint/docs/perf-baseline.md](../../../nop-lint/docs/perf-baseline.md)（基线文档形态对标）
> Related: [02-module-skeleton.md](02-module-skeleton.md)（模块 + 采集层已落地）

## Purpose

落地方案内 CFG + 抽象解释内核 v1（nullness lattice 优先），以 POC-A 已验证的机制为本（plan 01 `poc-asm`），按 ADR 裁定自建（`tree.analysis` 仅作测试 oracle），并建立内核 JMH 基线文档。完成后 item 5（发现流通道）与 item 6（null-flow v1 分析器）有内核 API 可消费。

## Current Baseline

- plan 02 完成（commit b9464b0851）：`nop-bytecode` 模块在库（零 nop-* 依赖，asm/asm-tree 9.7.1 pin），采集层 v0 落地，6/6 tests。**asm-analysis 未引入**（successor 提示在 plan 02 Follow-up，本 plan 引入）。
- POC-A（`_tmp/nop-bytecode-poc/poc-asm/src/PoCA.java`，730 行）已验证：槽位精确 Frame、指令级 CFG（线性+跳转+switch+try 范围限定 handler 边）、三值 nullness lattice、IFNULL/IFNONNULL 分支敏感（ALOAD 槽位来源精化局部变量）、`Objects.requireNonNull` 语义门控；已知形态学缺陷 6 类及修复均在 POC 调试史中。POC 代码为 plan 01 中本通道自产（非外部源码移植），可直接整理入库（clean-room 约束不受影响）。
- ADR §五裁定：自建 CFG+抽象解释；`Analyzer+BasicVerifier` 作 oracle 纳入测试基建；POC 降配口径（IFNULL/IFNONNULL 限定）与 Wave 2 正式口径（全条件分支敏感）差异须在产物中注明。
- ASM 9.7.1 `BasicValue` 无 nullness 刻度、`Analyzer` join-only（无分支敏感边传播）——实测在档。
- JMH 形态对标：nop-lint `docs/perf-baseline.md`（环境表 + JMH 结果表 + 口径提示 + 复现命令）；nop-treesitter 的 JMH 依赖放 test scope + annotation processor。
- roadmap item 4 = `todo`。

## Goals

- `io.nop.bytecode.kernel.cfg`：方法内指令级 CFG 构建（线性/跳转/switch/异常 handler 边，try 范围限定）。
- `io.nop.bytecode.kernel.dataflow`：槽位精确抽象解释框架（Frame + transfer + worklist 求解器），nullness 三值 lattice 为首个客户端（`io.nop.bytecode.analysis.nullflow`）：解引用命中清单 API + requireNonNull 语义门控。
- `asm-analysis` 9.7.1 依赖引入；oracle 形状复核 + toy 语义用例成为内核测试基建。
- JMH 基线：`docs/perf-baseline.md`（模块内），语料 = ASM `ClassWriter` 确定性生成（不依赖外部模块产物）。
- roadmap item 4 状态流转。

## Non-Goals

- 全条件分支敏感（Wave 2 item 6 v1 口径；内核 v1 保持 POC 降配口径并在 javadoc/基线文档注明差异）。
- 发现流通道 CLI / 诊断输出格式（item 5，plan 04）。
- 资源配对分析器（item 7，plan 06）。
- 类型全解析模型（解析层完整形态随消费需求在 plan 04/05 演进；本 plan 内核直接消费 ASM `MethodNode`）。
- 性能优化迭代（基线只立锚点；收割属后续 perf plan）。
- docs-for-ai 页面（item 5 落地时随 plan 04 补）。

## Scope

### In Scope

- `nop-bytecode/pom.xml`（+asm-analysis 9.7.1、JMH test 依赖与 annotation processor）
- `nop-bytecode/src/main/java/io/nop/bytecode/kernel/**`、`io/nop/bytecode/analysis/nullflow/**`
- `nop-bytecode/src/test/java/**`（oracle 形状复核、toy 语义、语料分析、JMH bench 类）
- `nop-bytecode/docs/perf-baseline.md`
- `ai-dev/design/nop-bytecode/00-overview.md`（仅 §3.1 内核层行终态表述）
- `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`（动态块）、`ai-dev/logs/{执行当日}.md`

### Out Of Scope

- nop-lint / 现有工具 / 其他模块文件；root pom（asm-analysis 版本在模块 pom 内联声明）

## Execution Plan

### Phase 1 — 内核与 nullness 分析入库

Status: completed
Targets: `kernel/cfg`、`kernel/dataflow`、`analysis/nullflow`、pom

- Item Types: `Fix`

- [x] draft review 通过后：roadmap item 4 `todo`→`planned` + `Last updated` 刷新
- [x] pom：+`org.ow2.asm:asm-analysis:9.7.1`（**test scope**——tree.analysis 仅 oracle 测试消费，运行时零使用，HC5 依赖最小化）、+JMH core/generator-annprocess 1.33（test scope）+ compiler 插件 annotationProcessorPaths 配置（nop-treesitter :68-79/:101-139 形态）
- [x] `kernel/cfg/MethodCfg`：不可变 CFG（指令索引为节点，successors 列表；handler 边带 try 范围限定；入口节点）；`MethodCfgBuilder` 从 ASM `MethodNode` 构建（抽象/本地方法跳过；`module-info` 无方法自然空）；javadoc 声明 POC 降配口径
- [x] `kernel/dataflow/Frame`（槽位精确：locals + stack + base 偏移 + **srcs 溯源数组**——IFNULL 精化依赖面；push/pop/merge 由注入的 lattice merge 函数驱动）、`ForwardSolver`（worklist 至不动点）、`DerefRecorder`（命中去重回调接口）。**回调契约语义规格**（拆分 POC-A 的定音点）：(1) solver 对每条指令调用 transfer 一次，transfer 返回**每出边独立的出帧序列**（分支敏感精化经此送达 merge，不做 join-only 合并)；(2) handler 入口帧由 transfer/客户端层构造（清栈至 base + 压入异常引用=NONNULL——NONNULL 是 lattice 客户端知识，框架不发明）；(3) 初始帧由客户端层构造（this=NONNULL、引用参数=MAYNULL、cat2 双槽），solver 只消费；(4) solver 不理解 lattice 取值语义，merge 全部委托注入函数
- [x] `analysis/nullflow/Nullness`（NONNULL/MAYNULL/NULL/TOP）、`NullnessSemantics`（POC-A 全 opcode 形状表 + IFNULL/IFNONNULL 边精化 + requireNonNull 门控；javadoc 注明降配口径与 Wave 2 差异）、`NullflowAnalyzer`（byte[] → 每方法命中清单 `DerefFinding`，字段：class/method/指令索引/操作码/引用描述）
- [x] 异常与静默跳过纪律沿用 plan 02：未处理 opcode 抛 `NopBytecodeException`（消息含 class/method/指令），不吞；**JSR/RET 不支持**（v61+ 语料不出现）——javadoc 显式注明前提
- [x] owner-doc：00-overview §3.1 内核层行"tree.analysis 复用 vs 自建随 ADR 数据裁"同步为终态表述（自建 + oracle 测试基建）

Exit Criteria:

- [x] `./mvnw test -pl nop-bytecode` 全绿；**新增功能的测试覆盖由 Phase 2 四组测试 + 未处理 opcode 异常测试承担**（guide 规则 25 归属声明）
- [x] pom diff 仅 +asm-analysis（test）与 JMH test 依赖及对应 compiler 配置
- [x] `ai-dev/logs/{执行当日}.md` 条目已更新

### Phase 2 — oracle 形状复核 + toy 语义 + 语料测试

Status: completed
Targets: `src/test/java/io/nop/bytecode/**`

- Item Types: `Proof`

- [x] **oracle 形状复核测试**：对 javac fixture 语料与 CorpusGenerator（Phase 2 交付的测试基建）合成语料逐方法比对自建引擎与 ASM `Analyzer+BasicVerifier` 的逐指令栈高（槽位求和口径；null 帧不可达指令跳过），任一分歧即 fail（POC 的 opcode 级审计机制转正为回归测试）
- [x] **toy 语义测试**（编译 fixture 后分析，四用例对 POC 语义锁定）：守卫分支解引用不报（`if (s == null) return;` 后 trim）；未守卫参数解引用必报；`Objects.requireNonNull` 门控不报；空 then 分支 join 后 MAYNULL 必报（真阳性）
- [x] **CorpusGenerator（测试基建，Phase 3 JMH 消费）**：ASM `ClassWriter` 确定性生成 N 个合成类（`COMPUTE_MAXS`——见 Phase 3 前提）；类数/方法数固定
- [x] **语料全量分析测试**：fixture 多类（含循环/try-catch/嵌套分支/数组/字段读写/接口调用）逐方法分析零异常、命中清单规模 > 0 且稳定（两次运行结果相等——确定性回归锚）
- [x] **handler 边测试**：try 内解引用经 catch 路径的可达性正确（对照手算结论断言命中/不命中）
- [x] **未处理 opcode 异常测试**：合法语料无法触发（WIDE 在 ASM 不独立出现，IMPDEP1/2 合法文件不可出现）——手搓 `MethodNode` 注入 `InsnNode(IMPDEP1)` 走 NullflowAnalyzer，断言 `NopBytecodeException` 且消息含 class/method
- [x] fixture 特性清单在循环/try-catch/嵌套分支/数组/字段读写/接口调用基础上**补 switch（TABLESWITCH/LOOKUPSWITCH）与 lambda（INVOKEDYNAMIC）**（POC 72-opcode 覆盖面不回缩）；编译方式沿用 plan 02 基建（ToolProvider + @TempDir + `--release`，v65 用例带 JDK feature assume 门控）

Exit Criteria:

- [x] 上述五组测试（oracle / toy / 语料全量 / handler 边 / 未处理 opcode 异常）全绿且纳入默认 test suite
- [x] oracle 分歧为零（javac fixture 语料与合成语料双覆盖）
- [x] `ai-dev/logs/{执行当日}.md` 条目已更新

### Phase 3 — JMH 基线与文档

Status: completed
Targets: `src/test/java/io/nop/bytecode/bench/**`、`docs/perf-baseline.md`

- Item Types: `Proof`

- [x] 基准语料消费 Phase 2 的 `CorpusGenerator`（前提已在其交付点注明：`COMPUTE_MAXS` 而非 COMPUTE_FRAMES——后者 getCommonSuperClass 会加载内存生成类而失败；本链路不消费 StackMapTable；形状正确性由 Phase 2 oracle 覆盖合成语料保证）；类数/方法数写进基线文档
- [x] JMH 基准（avgt，fork 1，warmup 3×1s，measurement 5×1s，gc profiler——对标 nop-lint 口径）：`parseCorpus`（ClassReader accept 全语料）/ `buildCfg`（全语料 CFG 构建）/ `runNullflow`（全语料 nullness 分析端到端）
- [x] 实际执行 JMH 并将结果写入 `nop-bytecode/docs/perf-baseline.md`（环境表 + 结果表 + 口径提示 + 复现命令，形态对标 nop-lint perf-baseline；降配口径注明）。**复现命令形态写死**：`exec:exec` + `%classpath` 占位符 + `-Dexec.classpathScope=test`（JMH fork 1 的 forked JVM 从 java.class.path 取 classpath——`exec:java` 同 JVM 下该属性是 maven boot classpath，fork 后找不到 BenchmarkList，禁止使用）
- [x] CI 档位口径核对：全语料端到端耗时换算"分钟级预算"结论写进基线文档
- [x] bench smoke 测试：微语料单基准 `-wi 0 -i 1` 一次性运行断言不抛异常（`*Benchmark` 类不进 surefire，无 smoke 则 bench 编译/语料变形永不被发现——nop-lint BenchmarkSmokeTest 先例）

Exit Criteria:

- [x] `docs/perf-baseline.md` 在档：三基准各有 Score 数字 + 语料固定参数 + 复现命令（mvn 命令行可直跑）
- [x] JMH 运行原始输出本地留档 `_tmp/nop-bytecode-bench/`（gitignore，不入 git；蒸馏数字进 perf-baseline——root .gitignore:115 既有惯例）
- [x] owner-doc 裁定：perf-baseline 为模块 docs 新增；`docs-for-ai/` 不涉及（通道未落地）——No owner-doc update required
- [x] `ai-dev/logs/{执行当日}.md` 条目已更新

### Phase 4 — 收口

Status: completed
Targets: roadmap、本 plan

- Item Types: `Proof | Follow-up`

- [x] roadmap item 4 `planned`→`done`（指针 + audit id）+ `Last updated` 刷新
- [x] 文本一致性五处核对
- [x] 终门禁：check-plan-checklist --strict 0 / check-doc-links --strict 0 / `scan-hollow-implementations.mjs --module nop-bytecode --severity high` 0
- [x] 独立子代理 closure audit + evidence 写入
- [x] 单提交（选择性 add）

Exit Criteria:

- [x] roadmap item 4 = done 带指针与 audit id
- [x] 三门禁 0；`./mvnw test -pl nop-bytecode` 收口复跑全绿
- [x] closure evidence 写入
- [x] `ai-dev/logs/{执行当日}.md` 收口条目

## Closure Gates

- [x] 内核 + nullness 分析入库：v61+ 全 opcode 形状表覆盖（JSR/RET 显式不支持已注明；未处理 opcode 显式异常测试在档），oracle 分歧为零，toy 语义四用例锁定
- [x] JMH 基线在档：三基准数字 + 固定语料参数 + 复现命令；原始输出留档
- [x] POC 降配口径与 Wave 2 正式口径的差异在 javadoc 与 perf-baseline 双处注明
- [x] roadmap item 4 done 带指针与 audit id；动态块一致
- [x] 无静默跳过：未处理 opcode 显式异常（测试在档）
- [x] 独立 closure audit 完成并记录证据
- [x] 三门禁 0 + `./mvnw test -pl nop-bytecode` 全绿

## Deferred But Adjudicated

（无——item 4 范围内不接受延期。）

## Non-Blocking Follow-ups

- 全条件分支敏感升级——Wave 2 item 6 v1 的正式口径，随 plan 05 落地（Why Not Blocking Closure: 内核 transfer 的分支精化点是 isolated 扩展，v1 降配口径已在 javadoc/基线注明，不构成隐藏缺陷）
- docs-for-ai 模块页——随 plan 04（Why Not Blocking Closure: 无用户可消费面）

## Closure

Status Note: item 4 收口：内核 v1（方法内 CFG + 槽位精确抽象解释 + 回调契约）+ nullness 分析 + JMH 基线落档。13/13 测试全绿（oracle 双语料形状复核零分歧、toy 语义四用例锁、未处理 opcode 响亮失败、bench smoke）；执行期修复 3 处（伪指令线性后继缺失、空 then 反转的边序号精化、CorpusGenerator IINC-on-long 被 oracle 当场抓获）。降配口径（IFNULL/IFNONNULL）与 Wave 2 正式口径差异在 javadoc/NullflowAnalyzer javadoc/perf-baseline 三处注明。
Completed: 2026-09-29

Closure Audit Evidence:

- Reviewer / Agent: agent_bc4ff98a-100e-4a92-862c-0d75a1a854ef（独立 fresh-session 子代理，实跑测试于审计会话）
- Audit Session: sess_49c9956b-0ccc-48c1-ba6b-d6a1bd2eb130 / agent_bc4ff98a
- Evidence:
  - Phase 1 Exit Criteria 3/3 PASS（auditor 实测 13/13；pom diff 与 EC 逐字吻合；回调契约四点逐条源码验证；JSR/RET 拒绝、未处理 opcode 抛异常、00-overview §3.1 终态行 diff 核对）
  - Phase 2 Exit Criteria 3/3 PASS（五组测试实跑通过；oracle 双语料零分歧；log 条目——首轮缺失经 M1 修复后补写）
  - Phase 3 Exit Criteria 4/4 PASS（perf-baseline 四基准数字与 `_tmp/nop-bytecode-bench/jmh-run2.log` 逐位一致；原始输出留档；smoke 实跑 2.191 ms/op；复现 classpath 文件在档）
  - 首轮审计 REJECT 仅 1 必修（M1：daily log Plan 03 条目缺失——并发流在日志顶部加条目致本流 replace 静默失配；发现后已补写）；Minor m1/m2/m3/m4/m5：m1 MethodCfg javadoc 归属修正、m3 死代码清理（indexOf/insnRefs/恒真断言）、m4/m5 plan 命名与基准数同步——已全部处置；m2 记 Wave 2 备忘
  - Anti-Hollow：auditor 通读 transfer ~72-opcode 形状表（含 DUP2_X2/LSHL 难点）+ 真实 worklist + 5 类解引用命中点；IFNULL 边序方案 reasoning 正确（角案 javac 不生成，记 Wave 2 备忘）；`scan-hollow-implementations.mjs --module nop-bytecode --severity high` EXIT=0
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（evidence 写入后复跑）；`check-doc-links.mjs --strict` 0 errors；`./mvnw test -pl nop-bytecode` 收口复跑 13/13
  - 纯增量约束：auditor git 全量核对——root pom `diff HEAD` 为空、nop-lint 零改动、足迹与 In Scope 精确吻合；范围外脏文件（deepwiki、_vfs 生成物等并发流）已排除出收口提交
  - Deferred 分类：2 条 Follow-up（全条件分支敏感→Wave 2 item 6、docs-for-ai→plan 04）均真 non-blocking；Deferred But Adjudicated = 无

Follow-up:

- 全条件分支敏感升级（Wave 2 item 6 正式口径）+ 条件跳转目标=handler head 的边序角案备忘
- docs-for-ai 模块页随 plan 04 首个用户可消费面补建

