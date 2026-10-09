# 2367 full-code-review R1.4 — nop-bytecode 双缺陷修复（P1-1.10-001/002）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.4 行；finding `P1-1.10-001`/`P1-1.10-002`（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.10-bytecode-duckdb-first-audit.md`，修复精算在案）
> Related: P2-1.10-002 的「分析期逃逸清单 5 类完备化」与 P1-1.10-001 同批承载（审计原文）；P3 死代码/文档漂移不在本计划

## Purpose

修复 nop-bytecode 两个 P1：①分析层异常逃逸使 CLI exit 1 违反「2=analysis failure」契约（与 nop-lint 退出码语义互换）；②`ObligationToken` 缺 equals/hashCode 导致 resources 族 worklist 死循环（主流 jar 必然命中、javadoc 虚假终止契约）。含审计指定的 CLI 超时防线。

## Current Baseline

- **退出码契约**：`NopBytecodeMain.run` 的 catch 集为 `NopBytecodeException | IOException | UncheckedIOException`（:136）→ exit 2；逃出该集的 RuntimeException（分析期 AIOOBE/NPE/CCE/ISE/IAE 等）直穿 `run` → JVM 默认处理器 exit 1。审计实证：截断 class（TOCTOU hash 校验通过）经 `reader.accept()` 深解析抛 AIOOBE → exit 1；同仓 nop-lint `NopLintCli:38/43/48` 定义 1=violations/2=internal——语义恰好互换。
- **分析器入口**：`NullflowAnalyzer.analyze(byte[])`（:29-39）与 `ResourceLeakAnalyzer.analyze(byte[])`（:28-38）均为裸体（无 try/catch）；`analyze(InputStream)` 变体只捕 IOException。
- **死循环根因**：`ObligationToken`（final 类，字段 kind/nullState/openSites，无 equals/hashCode）；`Frame.mergeFrom`（`kernel/dataflow/Frame.java:107-120`）以 `Objects.equals(m, slots[i])` 判 changed → TRACKED merge 恒 false → changed 恒 true → `ForwardSolver` while(!work.isEmpty())（:49）死循环。审计实测 commons-io FileSystemUtils 卡死 463s；补丁版 equals/hashCode +13 行 29ms 收敛 exit 0。
- **虚假 javadoc**：`ObligationToken` 类注释「monotone, bounded → fixpoint terminates」——merge 单调为真，但终止还需相等性变化检测，javadoc 未声明 equals/hashCode 前置，构成误导公开面。
- **CLI 防线现状**：无任何超时/预算机制（唯一防线是人工盯梢）。
- **测试基建**：ASM ClassWriter 生成合成类的先例在仓（`bench/CorpusGenerator.java`，COMPUTE_MAXS 不 COMPUTE_FRAMES）；`ResourceLeakAnalysisTest` 有分支/路径敏感 shape 先例；`NopBytecodeMainTest` 有 CLI 级测试先例。
- nop-bytecode 测试基线：28 tests 全绿（1.10 审计 2026-10-04 实跑锚点）。

## Goals

- 截断/损坏 class 的分析失败 → exit 2（契约恢复），不再 exit 1。
- ObligationToken 相等性语义正确：merge 收敛，resources 族 worklist 终止。
- 虚假 javadoc 修正为真实终止契约（equals/hashCode 前置显式化）。
- CLI 具备运行时限防线：默认墙钟预算，超限 fail-fast exit 2。

## Non-Goals

- 不修 P2-1.10-001（ATHROW 误报定量/dedup 口径）、P2-1.10-002 的 major 闸门与 pom 注释项、P2-1.10-003/004/005（duckdb 面，另属 P2 台账通道）。
- 不修 P3（死代码、文档漂移簇、manifest 抖动等）。
- 不改报告格式/finding 语义。

## Scope

### In Scope

- `NullflowAnalyzer.analyze(byte[])` / `ResourceLeakAnalyzer.analyze(byte[])`：整体 catch(Exception) → NopBytecodeException（分析失败响亮化，exit 2）。
- `NopBytecodeMain.run`：catch 集补 `| RuntimeException` 兜底 → exit 2。
- `ObligationToken`：equals/hashCode（kind+nullState+openSites）+ javadoc 修正。
- `kernel/dataflow/ForwardSolver.java`：solve 循环内 deadline 检查（一行时钟比较）；`NopBytecodeMain`：`--max-runtime-seconds <n>` 选项（默认 600，合法域 ≥0）经分析器传入求解器。
- 回归测试：损坏 class exit 2（非 1）、ObligationToken equals 语义、merge 收敛终止（资源开在循环前、循环体内引用的回边 shape）、CLI 超时防线。

### Out Of Scope

- InputStream 变体收口（CLI 主链只走 byte[] 变体；两变体行为对齐属 P2-1.10-002 完备化项）。
- ForwardSolver/Frame 的 lattice 与数据流结构改动（deadline 检查是 solve 循环内一行时钟比较，不属结构改动；equals 修复后无需动 worklist 逻辑）。

## Execution Plan

### Phase 1 - 双 P1 修复

Status: completed
Targets: `nop-bytecode/src/main/java/io/nop/bytecode/analysis/nullflow/NullflowAnalyzer.java`、`.../resources/ResourceLeakAnalyzer.java`、`.../resources/ObligationToken.java`、`.../cli/NopBytecodeMain.java`

- Item Types: `Fix`

- [x] 两分析器 `analyze(byte[])` 整体 try/catch：**先 `catch (NopBytecodeException e) { throw e; }` 重抛卫兵**（保住 NullnessSemantics:34 声明的单层上下文契约，防双层包装），再 `catch (Exception e)` → `throw new NopBytecodeException("analysis failure: <analyzer>", e)`（fail-loud 包装，非吞）
- [x] `NopBytecodeMain.run` 在既有 multi-catch **之后新增独立顺序 catch 子句** `catch (RuntimeException e)`（multi-catch 加 RuntimeException 与 NopBytecodeException 子类关系冲突无法编译——审查实证；先具体后兜底 → err + exit 2）
- [x] `ObligationToken` 补 equals/hashCode（kind+nullState+openSites 三字段；@Override + Objects hash 组合）
- [x] `ObligationToken` javadoc 修正：终止契约 = merge 单调 + equals 基变化检测（补 equals/hashCode 前置说明）
- [x] 运行时限防线：**deadline 检查下沉进 `ForwardSolver.solve()` 的 while 循环**（一处 `System.nanoTime()` 比较——审查实证：故障形态是单类内单方法 solve 不收敛，逐 class 前置检查永远防不住 in-flight 挂死）；deadline 来源=`NopBytecodeMain` 新 `--max-runtime-seconds` 选项（默认 600；合法域 ≥0，0=立即超时即确定性测试钩子；非法值 usage exit 2），经调用链传入分析器/求解器；超限抛 NopBytecodeException 走既有 exit 2 路径

Exit Criteria:

- [x] 截断 class（手工构造非法字节）经两分析器 → NopBytecodeException（测试证据）；CLI 级截断输入须截在 collect 层之后（常量池/this_class 完好——审查指出 garbage 先例走的是 collect 层拒绝，证明不了本条）
- [x] `run` 对分析期 RuntimeException → 返回 2（独立顺序 catch 子句；测试证据）
- [x] ObligationToken equals/hashCode 语义测试绿（同字段相等/不同字段不等/hash 一致）
- [x] 死循环回归测试：**资源开在 for/while 循环之前、循环体内引用**的合成方法（审查 live-code 实证该 shape 修复前 >8s 无进展即事实挂死、修复后收敛出 finding；纯 if/else 菱形无回边不会死循环——实证勘误）在 `assertTimeoutPreemptively` 有界时间内终止并收敛；shape 构建用仓内 `TestCompiler.compile` 先例（ResourceLeakAnalysisTest 风格）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - CLI 集成回归与收口

Status: completed
Targets: `nop-bytecode/src/test/java/io/nop/bytecode/cli/NopBytecodeMainTest.java`（扩展）、roadmap、轮次 index、本 plan

- Item Types: `Proof`

- [x] CLI 级回归：损坏 jar/class 输入 → exit 2；超时防线生效（--max-runtime-seconds 极小值 + 真实输入）→ exit 2；正常输入 → exit 0 保持
- [x] `./mvnw test -pl nop-bytecode -am` 全绿（基线 28+新增）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-bytecode --severity high` 退出码 0
- [x] roadmap R1.4 行 → done；index P1-1.10-001/002 → fixed + MR 表 R1.4 行
- [x] owner doc 更新：退出码契约收口 + `--max-runtime-seconds` 登记写入 `docs-for-ai/03-modules/nop-bytecode.md`（:16 已逐字记载 0/2 契约——审查核实存在）
- [x] 文本一致性核对五处一致；check-plan-checklist --strict 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] CLI 三态回归测试绿（正常 0 / usage 2 / 分析失败 2）
- [x] roadmap 与 index 回填一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P1-1.10-001 修复落地：分析失败路径 exit 2，exit 1 逃逸面闭合（两分析器收口 + CLI 兜底）
- [x] P1-1.10-002 修复落地：ObligationToken equals/hashCode + javadoc 真实契约 + 死循环 shape 终止测试
- [x] CLI 超时防线落地且三态回归绿
- [x] focused verification：nop-bytecode 全量测试绿（28 基线 + 新增）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（P2 项走台账通道）
- [x] owner docs 已同步或显式记录 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：catch 收口为 fail-loud 包装（非吞）；equals 修复经死循环 shape 终止测试证明（非仅编译）
- [x] `./mvnw test -pl nop-bytecode -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### 审计修复方向中的「访问上限」防线

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 审计 P1-1.10-002 修复方向列有「CLI 超时/访问上限防线」，本计划交付墙钟 deadline（solve 循环内，防 in-flight 挂死——已覆盖审计实测的故障形态）；访问/计数上限（如 per-class 分析预算）属纵深增强，equals 修复消除死循环根因后风险已收敛
- Successor Required: `no`

### P2-1.10-002 的 major 闸门/pom 注记/InputStream 变体对齐

- Classification: `out-of-scope improvement`（P2 台账通道承载）
- Why Not Blocking Closure: 审计将这三项归 P2-1.10-002（P2 裁决例外通道），非本 P1 修复的组成部分；本计划的逃逸收口已闭合 exit 1 面
- Successor Required: `no`（P2 台账通道既有记录）

## Non-Blocking Follow-ups

- 取消集成测试的类似强化不适用本项；无其他 plan-owned work

## Closure

Status Note: P1-1.10-001/002 双 P1 修复全部落地：分析层异常经重抛卫兵+catch(Exception) 包装收口、CLI 顺序 catch(RuntimeException) 兜底——exit 2 契约恢复（exit 1 逃逸面闭合）；ObligationToken equals/hashCode + 真实终止契约 javadoc（回边 shape 死循环回归测试锁定）；ForwardSolver.solve() 循环内 deadline 检查 + --max-runtime-seconds 防线。nop-bytecode 34/34 全绿（28 基线+6 新增），owner doc 已登记。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_5733c548-8309-4185-99db-6ffb737bf811
- Evidence:
  - **P1-1.10-001 收口**：PASS——两分析器 `catch(NopBytecodeException) 重抛卫兵 + catch(Exception) → NopBytecodeException`（NullflowAnalyzer:49-53 / ResourceLeakAnalyzer:48-52）；CLI multi-catch(:162) 后顺序 catch(RuntimeException)(:167-173) → err+return 2。catch 顺序编译合法性核验（NopBytecodeException extends RuntimeException，multi-catch 并列子类非法——先具体后兜底为唯一合法形态）。
  - **P1-1.10-002 修复**：PASS——equals/hashCode 三字段（ObligationToken:101-111）+ javadoc 真实终止契约（:14-20）。
  - **Anti-Hollow**：三项全 PASS——(a) 收口为响亮包装非吞（重抛卫兵在位、转抛非返回空）；(b) equals 运行时效果由审查轮 live 探针（_tmp/plan2367-review/：回边 shape 修复前 8s 无进展实证）+ 本轮 `loopBackEdgeShapeTerminates` 复跑通过锁定；(c) deadline 检查真实在 solve while 循环内（ForwardSolver:57-61，逐 worklist step）。
  - **契约核验**：PASS——--max-runtime-seconds 默认 600（:51）/负值 usage 2（:70-77）/0 立即超时（:115-117）；wiring CLI→分析器 setter→solver 运行时连通由 zeroRuntimeBudgetFailsFastWithExitTwo 走真实管线证明。
  - **测试复跑**：PASS——auditor 亲测 `./mvnw test -pl nop-bytecode` → **34/0/0** BUILD SUCCESS（28 基线+6 新增）；hollow scan exit 0 零发现。
  - **owner doc**：PASS——nop-bytecode.md:16 R1.4 收口注记 + :17 运行时限条目。
  - **状态回填四处一致**：PASS——roadmap R1.4(:133) / index P1-1.10-001/002(:88-89) / index MR R1.4(:257) / daily log。
  - **Deferred 分类**：PASS——访问上限=out-of-scope improvement（deadline 已覆盖实测故障形态）；P2-1.10-002 各项台账有记录。
  - Advisory 处置：A1 随本 closure 提交（已做）；A2 try 前参数解析面 InvalidPathException/SecurityException 理论逃逸（不在本 plan scope，归 P2-1.10-002 逃逸清单完备化通道）；A3 ForwardSolver.methodNameHint P3 死代码项因 deadline 消息使用而事实上消解（P3 台账 R2.0 对账时勘注）。

Follow-up:

- P2-1.10-002 通道：major 闸门/pom 注记/InputStream 变体对齐/逃逸清单完备化（含 A2 参数解析面）——P2 台账承载
- methodNameHint P3 死代码项事实上消解：R2.0 对账时勘注
