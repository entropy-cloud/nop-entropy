# 05 null-flow v1 正式口径 — 豁免面锁定 + 已知命中集对照（roadmap item 6, Wave 2, M2a）

> Plan Status: completed
> Last Reviewed: 2026-09-29
> Source: [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md) Wave 2 item 6 + [substrate-adjudication.md](../../design/nop-bytecode/substrate-adjudication.md) §五（降配→正式口径差异）+ [plan 24](../../plans/nop-lint/24-null-flow-adjudication.md)（源码 lane 空指针面裁定——零修改参照）+ [gap-ledger](../../../nop-bytecode/docs/gap-ledger.md) G1
> Related: [03-kernel-cfg-dataflow-nullflow.md](03-kernel-cfg-dataflow-nullflow.md)（内核 v1 降配口径在库）、[04-discovery-channel-cli.md](04-discovery-channel-cli.md)（通道在库）

## Purpose

把 null-flow 分析器从内核 v1 降配口径升级为 **item 6 正式口径**，锁定豁免面（assert / requireNonNull / NopException 前置检查），并以已知命中集对照（SpotBugs 同语料实跑 + plan 24 案例集）完成证据闭环。完成后 M2a 达成。

## Current Baseline

- 内核 v1（plan 03）：分支敏感面 = IFNULL/IFNONNULL 两条 opcode（边序号精化局部变量，srcs 溯源）；requireNonNull 语义门控在档；其余分支 join-insensitive——**降配口径与正式口径差异三处注明**（javadoc×2 + perf-baseline）。
- 通道 v1（plan 04）：`nullflow/may-null-deref` 单 ruleId，report-only CLI。
- 语料事实：SpotBugs 对 nop-jq 的既有 qa 实跑在档（plan 01，6 findings 全为 SF/UPM/IC/EQ 族——**零 NP 族命中**，即 SpotBugs 在该语料的 null-deref 对照基线为空集）。
- plan 24 案例集：pattern 面 5 条（throw-null/equals-null/no-throw-npe/catch-npe/no-return-null）= 源码 lane 既有；null-flow Deferred 归因（路径敏感资源追踪需新引擎能力）——本 plan 即其字节码通道承接，不修改 plan 24 任何内容。
- 平台 NopException 前置检查形态：`if (x == null) throw new NopException(...)` / `NopException.assertXxx`？——**未盘点**（本 plan Phase 1 盘点后锁定豁免形态清单）。
- roadmap item 6 = `planned`（本 plan）。

## Goals

- 正式口径（**归因已按 javac 实验修正**）：(a) 豁免面三形态的字节码 = IFNULL/IFNONNULL + INVOKESTATIC 门控（`assert x != null` → IFNONNULL；`if (x == null) throw new NopException` → IFNULL+ATHROW）——**内核 v1 已覆盖，本 plan 以测试锁定**（非能力新增）；(b) **新增 ACMP null 侧精化**（IF_ACMPEQ/IF_ACMPNE 一侧为 NULL 常量时精化另一侧局部），服务 `null == x`（javac 不折叠字面量在前形态）、`x == (Object) null`（cast 阻止折叠）、三元 null 比较——**本对照语料实测 ACMP-null 零命中**（仅 3 处 enum 比较），其验证只能来自 javac fixture，对照记录须如实注明。
- 已知命中集对照：SpotBugs 同语料（nop-jq）NP 族 vs 本通道 nullflow 的 delta 逐条裁定（重复/互补/一方误报/工具局限），对照记录落 `nop-bytecode/docs/nullflow-comparison.md`。
- 误报控制数据：豁免面在语料上的豁免计数 + 已知 FP 面声明（对照准入判据 1）。
- M2a 达成标注。

## Non-Goals

- 跨过程/注解契约传播（Wave 5 item 10/11）。
- 资源配对分析器（item 7，plan 06）。
- SpotBugs 接线任何改动（只读实跑，HC1）。
- nop-lint 引擎与规则任何改动（plan 24 裁定零修改）。
- CI 接线（item 9）。

## Scope

### In Scope

- `nop-bytecode/src/main/java/io/nop/bytecode/analysis/nullflow/**`（正式口径升级）
- `nop-bytecode/src/main/java/io/nop/bytecode/kernel/dataflow/Frame.java`（ACMP 取证访问器）
- `nop-bytecode/src/test/java/**`（豁免面/升级口径测试）
- `nop-bytecode/docs/nullflow-comparison.md`（对照记录）、`docs/perf-baseline.md`（正式口径重测数字替换）
- `_tmp/nop-bytecode-nullflow-compare/`（对照原始数据，gitignore）
- `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`、`ai-dev/logs/{执行当日}.md`、`gap-ledger.md`（G1 行 closed）

### Out Of Scope

- nop-lint / SpotBugs 接线 / 其他模块；通道层结构变更（ruleId 不变）

## Execution Plan

### Phase 1 — 正式口径升级与豁免面锁定

Status: completed
Targets: `analysis/nullflow/NullnessSemantics.java`、`kernel/dataflow/Frame.java`（新增第二槽位 provenance 访问器 `srcOfSecondFromTop()`——ACMP 两侧取证所需）、测试

- Item Types: `Fix`

- [x] draft review 通过后：roadmap item 6 状态确认 `planned`（本 plan）+ `Last updated` 刷新
- [x] **ACMP null 侧精化**：`IF_ACMPEQ/IF_ACMPNE` 一侧为 NULL 常量（ACONST_NULL 经 provenance 溯源或已知 NULL 局部）时，另一侧局部按边精化（EQ→NULL 侧、NE→NONNULL 侧，边序号口径与 IFNULL 同构）；两侧均非 NULL 常量时不精化（保持 join-insensitive）
- [x] **NopException 前置检查形态盘点与锁定**：盘点平台代码中 NopException 系判空形态（`if (x == null) throw new NopException` / 静态 assert 工具方法），确认其字节码形态落在本口径覆盖内；若存在方法调用式判空（如 `CheckUtils.notNull(x)`）→ 作为语义门控加入（名单写死在 `NullnessSemantics` 常量，与 requireNonNull 同机制）
- [x] 豁免面三形态测试锁定（javac fixture）：`assert x != null` 后解引用不报；requireNonNull 后解引用不报（既有）；`if (x == null) throw new NopException(...)` 后解引用不报；`CheckUtils` 式门控不报（若盘点收录）
- [x] javadoc/perf-baseline 的降配口径声明同步移除或改写为历史注记（正式口径已在档）
- [x] owner-doc 裁定：00-overview 分析器层行按最终形态微调（如需）；其余 No owner-doc update required

Exit Criteria:

- [x] `./mvnw test -pl nop-bytecode -am` 全绿（含新增豁免面/ACMP 测试；既有 23 项不回归）
- [x] oracle 形状复核对升级后引擎仍零分歧（ACMP 精化只改局部写入，不改栈形状——测试断言兜底）
- [x] `ai-dev/logs/{执行当日}.md` 条目已更新

### Phase 2 — 已知命中集对照与误报控制数据

Status: completed
Targets: `analysis/nullflow/**`（统计接口）、`nop-bytecode/docs/nullflow-comparison.md`、`_tmp/nop-bytecode-nullflow-compare/`

- Item Types: `Proof | Fix`

- [x] SpotBugs 同语料只读实跑：`./mvnw -pl nop-jq -Pqa spotbugs:spotbugs`（failOnError=false 现状），提取 NP 族 findings；本通道 CLI 实跑 nop-jq `target/classes`（`--json`）提取 nullflow findings——两侧原始输出留 `_tmp/nop-bytecode-nullflow-compare/`
- [x] delta 逐条裁定表：按 class#method 聚合，每条标注【重复 / 互补 / 一方误报 / 工具局限】+ 一句理由（SpotBugs 基线若为空集，则记录"对照基线空集 + 本通道命中规模与抽检结论"——抽检 ≥10 条人工定性真假阳性并留方法学说明）
- [x] 误报控制数据：豁免计数经 Phase 1 统计接口产出（guard-refined/requireNonNull 门控/ACMP 精化三类计数，语料实跑数字）+ 已知 FP 面声明（字段载荷/方法返回跨方法不可知 → MAYNULL 保守面，误报方向声明；**抽检方法学**：按 ref 类别分层——字段载荷/方法返回/参数/数组各抽 ≥3 条，逐条留 source-line 依据，定性标准 = 可证非空即 FP / 真可空即 TP / 未建模豁免形态即豁免候选）
- [x] **plan 24 案例集对照腿**：源码 lane pattern 面 5 条（throw-null / equals-null / no-throw-npe / catch-npe / no-return-null）逐条过本通道裁定去重归属——每条记录【本通道是否产出该形态命中（fixture 实证）/ 归属（源码 lane 单报 / 本通道单报 / 双报归 item 8）】
- [x] **ACMP 语料零命中注记**：对照记录明写"ACMP 精化在本对照语料零命中（实测 3 处 ACMP 全为 enum 比较），其验证来自 javac fixture + 手搓用例"（HC3：对照数据不虚标口径覆盖）
- [x] 对照记录 `nullflow-comparison.md` 落档（口径/命令/数字/裁定表/FP 声明/重估触发）

Exit Criteria:

- [x] 对照记录在档：两侧数字可复现（命令在档）+ 每条 delta 有裁定 + FP 声明明确
- [x] 原始数据留档 `_tmp/nop-bytecode-nullflow-compare/`
- [x] `ai-dev/logs/{执行当日}.md` 条目已更新

### Phase 3 — 收口（M2a）

Status: completed
Targets: roadmap、gap-ledger、本 plan

- Item Types: `Proof | Follow-up`

- [x] roadmap item 6 `planned`→`done`（指针 + audit id）；**Milestone M2a 行标注达成**
- [x] gap-ledger G1 行按账本规则两步翻转：立项时 `claimed`（plan 指针）→ 对照在档后 `closed`；**误报控制面格细化回填**（G1 声明的"Wave 2 v1 落地时细化"：豁免面 = assert/requireNonNull/if-null-throw-NopException 三形态 + ACMP null 侧精化；FP 方向 = 跨方法不可知保守 MAYNULL）+ 对照记录指针
- [x] 文本一致性五处核对
- [x] 终门禁：check-plan-checklist --strict 0 / check-doc-links --strict 0 / scan-hollow --module nop-bytecode 0
- [x] 独立子代理 closure audit + evidence 写入
- [x] 单提交（选择性 add）

Exit Criteria:

- [x] roadmap item 6 done + M2a 标注；G1 closed
- [x] 三门禁 0；`./mvnw test -pl nop-bytecode -am` 收口复跑全绿
- [x] closure evidence 写入
- [x] `ai-dev/logs/{执行当日}.md` 收口条目

## Closure Gates

- [x] 正式口径落地：ACMP null 侧精化 + 豁免面三形态测试锁定 + 降配声明清理
- [x] 对照记录在档：SpotBugs 同语料 delta 裁定 + FP 控制数据 + 命令可复现
- [x] gap-ledger G1 closed；roadmap item 6 done 带指针与 audit id；M2a 标注
- [x] 独立 closure audit 完成并记录证据
- [x] 三门禁 0 + `./mvnw test -pl nop-bytecode -am` 全绿

## Deferred But Adjudicated

（无——item 6 范围内不接受延期。）

## Non-Blocking Follow-ups

- 全程序注解推导（@Nullable 传播）——Wave 5 item 10/11 范围（Why Not Blocking Closure: NullAway 形态已由 plan 24/ADR 定为窄桥接候选，与本 plan 方法内口径正交）
- 对照语料扩展至更多模块——item 8 并行对照期统一定口径（Why Not Blocking Closure: 单模块对照已满足"已知命中集对照在档"的 M2a 判据）

## Closure

Status Note: item 6 收口：null-flow v1 正式口径（ACMP-null 精化 + 断言恒启用建模 + 豁免面三形态测试锁定 + 豁免计数统计接口）+ 已知命中集对照落档（SpotBugs 同语料空集基线 + plan 24 案例集 5 条逐条裁定 + 分层抽检 9 条含 source-line 定性 + FP 四面声明）。G1 closed。M2a 达成。
Completed: 2026-09-29

Closure Audit Evidence:

- Reviewer / Agent: agent_16d0ce33-99a3-40dc-a0fa-33bbdd4c5c30（独立 fresh-session 子代理，两轮：首轮 closure audit REJECT → 修复 → 第二轮 fix-verification 代码面 APPROVE）
- Audit Session: sess_49c9956b-0ccc-48c1-ba6b-d6a1bd2eb130 / agent_16d0ce33
- Evidence:
  - Draft review: 独立审查 agent_af095eda（含 javac 实验/全仓 NopException 盘点/SpotBugs xml 核对——1 Blocker 归因错误 + 5 Major 修复后按其预授权进入执行）
  - **首轮 closure audit REJECT**（auditor 实跑反例 + javap 实证）：Blocker 1 = IF_ACMPEQ 真边极性反转（eqNull 误用 IFNULL 判定——`null != x` 形态假阳性）；Blocker 2 = ACMP provenance 在 pop(2) 后读陈旧槽位（精化恒落 local 0，非 0 局部豁免失效 + this 污染风险）；Major 1/2 = ADR §五 与 gap-ledger G1「已同步/已回填」虚称（grep 证伪）；Phase 2 对照面/Anti-Hollow/纯增量/Deferred 全 PASS
  - **修复**：极性与 IFNULL 解耦（switch 按 opcode：IF_ACMPEQ/IFNULL→true=NULL，IF_ACMPNE/IFNONNULL→true=NONNULL）；provenance pop 前捕获（op1/op2/src1/src2）；**双极性×构型锁定测试**（4 失败轴方法 + ne×local0 补全 = 5 方法）；ADR §五 68 行正式口径句**实际编辑**；gap-ledger G1 控制面实际回填；Frame.secondFromTop 与 pendingRefineIsAcmp 死代码移除；ExemptionFaceTest 循环冗余简化
  - **第二轮 fix-verification（同 auditor）**：代码面 APPROVE——极性逐边推演正确 + 首轮原反例独立回归归零 + 锁定测试可抓两反例；语料不变性 2791/86/4/0；文档面 4 处小修（已全部落实：ADR 实编辑/两轮记录/日志更正/死字段）
  - 对照记录: nop-bytecode/docs/nullflow-comparison.md（SpotBugs 6 findings 零 NP 族=空集基线；本通道 2791；分层抽检 9 条逐条 source-line 定性，主导 FP 面=字段/静态保守 MAYNULL；豁免计数 86/4/0；ACMP 注记已随修复同步）
  - 终门禁：`./mvnw test -pl nop-bytecode -am` 26/26；check-plan-checklist --strict 0；check-doc-links --strict 0；scan-hollow --module nop-bytecode 0（最终回填后复跑）

Follow-up:

- FP 收敛（字段/静态非空推导、catch-NPE 豁免）——以 nullflow-comparison.md 为对照基线的后续优化
- 对照语料扩展——item 8 统一口径

