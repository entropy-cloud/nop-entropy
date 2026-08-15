# Cycle 2 / I4+I5+I6 — nop-metadata silent-wrong-result 类别清扫修复、全量验证与循环收口

> Plan Status: active
> Last Reviewed: 2026-08-15
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 2 / I4（修复执行：实例 + 类别清扫）+ I5（全量验证与门禁零命中）+ I6（循环收口与下一轮触发判定）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I4–I6 步骤定义、Loop Rule、复触发条件）；Cycle 1 先例 plans `2026-08-13-1930-4` / `2026-08-13-1930-5`
> Related: 前置 `2026-08-15-0820-1`（I1' 门禁）、`2026-08-15-0820-2`（I2'+I3' 正式 red list 与裁决表，**硬前置**）

## Purpose

把 Cycle 2 不变式驱动的审计与裁决结果收口为**修复后的稳态基线**：P1 缺陷类别清扫修复、门禁终态达成（模式 a 零命中 / 模式 b ⊆ 已批准豁免清单）、模块全绿，并把 Cycle 2 收口（统计、稳态判定、复触发条件登记、独立 closure audit）。本计划完成后，silent-wrong-result 族与 Cycle 1 四族一样处于"门禁防回退"稳态。

## Current Baseline

> 事实为 2026-08-15 live repo 实测；P1 项精确清单以 I3' 裁决表为准。

- **前置依赖（硬）**：`2026-08-15-0820-2` 已完成——正式 red list（`formal-red-list-cycle2`）+ 零悬挂裁决表（`adjudication-table-cycle2`）存在，P1 集非空（若 P1 集为空，Phase 1 自动缩小为"No P1 items"显式记录，直接进入 Phase 2/3，plan 不作废）。
  - **取消条款（显式）**：若 I1'/I2'/I3' 链条整体取消（I1' 全族不可机械化），本计划同样 `cancelled` + Supersession Note。
- **已知修复先例（同族已修形态，I4' 按类别沿用）**：
  - AR-12 → `LocalReconciliationProcessor`：`toLowerCase(Locale.ROOT)`（live main 代码已有 10 处 Locale.ROOT 代码站点 + 1 处 javadoc 引用，惯例兼容）
  - AR-01 → `MetaContractChecker`：先乘后取整
  - AR-03 → `AggregationHelper.memoryGroupBy`：结构性 key（`List<Object>`）
  - AR-05 → `MetaTableProfiler.isNumericType`：exact-match `Set.of`
  - AR-10 → `AggregationHelper.toBigDecimal`：整数 longValue 无损 / 浮点 doubleValue / String 解析
- **类别清扫预期面（以 I3' 裁决表为唯一权威分母）**：locale 族 41 rg 站点（含 1 处 javadoc 伪站点，`LocalReconciliationProcessor.java:124`——扫描器口径 40）中裁决为 P1 的子集 + 精度族 2 站点（`MemoryOrderByComparator.java:132` / `MemoryFilterEvaluator.java:356`，若 I3' 推翻 plan `2026-08-14-1133-2` closure 的 optimization-candidate 旧裁定）+ 其余子族 P1 项。**恒等式权威分母 = `adjudication-table-cycle2` 清单**（其自身恒等式：red list = P1 + FP + 优化候选 + 新族登记）；rg/grep 仅作清扫辅助——rg 命中出现裁决表未收录的站点时，必须显式上报并归因（门禁盲区或裁决遗漏），不得静默放过。
- **验证基线**：`./mvnw test -pl nop-metadata -am -T 1C` 最近全绿记录 = 1175 tests / 0 failures（plan `2026-08-14-1448-3` closure）。
- **门禁基线与终态语义（与 0820-1 关键约束 B 预声明一致）**：4（Cycle 1）+ N（I1' 新增）条；模式 b 门禁以 `--baseline` 对账（⊆ 语义，渐进修复期 CI 保持绿）。**I5' 终态 = baseline 重写为"已批准豁免清单"**（= I3' B1b 方式 a 的 FP 条目 + B1 维持的优化候选条目）**或清空**：清空且无放行注释 → 升级模式 a 零命中阻断式；仅剩已批准豁免条目 → 保持模式 b（任何新增命中即红）。**豁免条目是 baseline 的合法驻留项，不是终态异常**——因此本计划"门禁零命中"的准确语义 = "命中集 ⊆ 已批准豁免清单"（无 P1 残留、无新增违规），而非字面零命中。
- **错误处理两段式**（AGENTS.md）：模块内部实现类错误用模块异常类 + 英文消息；对 public ErrorCode 契约有影响的按 NopException + ErrorCode。
- **ORM 模型为保护区域**：本计划预期不改 ORM 模型；若 I3' 裁决出现需 ORM 变更的 P1 项，该项按 roadmap Cross-Cutting 授权规则执行前需人工确认（改源模型非 `_gen/`）。

## Goals

- I3' 裁决表全部 P1 项修复落地，每项有**test-first 回归测试**钉死（新行为先红后绿，或对不可行项显式记录理由并以等价验证替代）。
- **类别清扫完整性**：每个被修子族，同族全部站点一次清完（locale 族：修 1 处 = 按裁决表核对全部站点处置状态；不允许"修 3 处留 38 处且无裁定"）。
- false-positive 站点按 I3' B1b 裁定方式落地处置（baseline 驻留或注释放行，可追溯）。
- 全量验证：模块测试全绿 + 全部门禁（4+N）达到终态（模式 a 零命中 / 模式 b 命中集 ⊆ 已批准豁免清单，按 0820-1 模式归属表）+ 棘轮记录（Cycle 2 red list → 终态）。
- Cycle 2 收口：统计（门禁数 / red list / 修复数 / 新族数）、稳态判定、复触发条件登记、独立 fresh-session closure audit。

## Non-Goals

- **改动门禁检测规则以清零 red list** —— 清零只能来自修复或经 I3' 裁定的 false-positive 处置（B1b 两种方式均使用 0820-1 Phase 2 预实现的机制——baseline 驻留或放行注释——不改检测规则；baseline 终态重写为已批准豁免清单属预声明终态机制，非规则放宽）。
- **Cycle 3 新族门禁实现** —— 若 I3' 登记了新族，其沉淀归 Cycle 3 / I1（复触发后另行起草）。
- **全仓清扫** —— 仅 nop-metadata 模块组。
- **性能优化类非缺陷项** —— I3' 裁定为 optimization candidate 且维持者，不在本计划修复（其状态已在裁决表闭环）。

## Scope

### In Scope

- P1 项修复 + 回归测试 + 类别清扫核对。
- false-positive 站点处置落地（B1b 方式 a 驻留 baseline / 方式 b 放行注释）。
- 全量验证 + 门禁终态（模式 a 零命中 / 模式 b ⊆ 已批准豁免清单）+ 棘轮记录 + CI 门禁模式升级（如适用）。
- Cycle 2 统计、稳态判定、复触发登记、closure audit、roadmap Work Item Status 表同步。

### Out Of Scope

- 门禁规则变更、Cycle 3 工作、全仓扩展、非缺陷优化实现。

## Execution Plan

### Phase 1 — I4' 类别清扫修复（test-first）

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/...`（I3' 裁决 P1 站点）、对应 `src/test/java/...` 回归测试、false-positive 标注

- Item Types: `Fix`

- [ ] **F1 test-first**：每个 P1 子族先落回归/对抗测试（如 locale 族：tr-TR 默认 locale 下 lineage registry 键不漂移的测试，**locale 切换机制钉死为测试内 `Locale.setDefault(Locale.forLanguageTag("tr"))` + `finally` 恢复**——先例 `TestLocalReconciliationProcessorLocale`；端到端先例 `TestNopMetaLineageEdgeBizModel`（@NopTestConfig + GraphQL 路径）；不使用 `-Duser.language` argLine 全局注入），确认对当前实现红或以"现状基线断言 + 修复后翻转"双段记录
- [ ] **F2 类别清扫修复**：按同族已修先例形态修复全部 P1 站点（locale 族 → `Locale.ROOT`（机器比较语义）或按裁决的其他处置；精度族 → 沿 AR-10 无损转换先例）；每修一个子族即以**裁决表（权威分母）**核对全部同族站点处置状态（已修 / FP 已按 B1b 裁定处置 / 裁决表豁免理由 / successor 拆分），rg 命中裁决表未收录站点时显式上报归因（见 Current Baseline 权威分母条）
- [ ] **F3 false-positive 处置落地**：按 I3' B1b 裁定方式落地——方式 (a) baseline 驻留：FP 条目保留在 baseline（不删条目，I5' 时 baseline 重写为该清单）；方式 (b) 放行注释：加 `// invariant-ok: <裁决引用>`（扫描器识别能力由 0820-1 Phase 2 预实现，本计划仅使用该机制）。**P1 集为空时 F1/F2/F4 跳过，F3 仍须按 B1b 执行（如存在 FP 条目）**。保证后续审计可追溯
- [ ] **F4 超时保护**：单个 P1 项修复若超时/受阻，按 Cycle 1 I4 先例拆 successor plan 并在裁决表标注归属，不阻塞其余项

Exit Criteria:

- [ ] I3' 裁决表 P1 集内每项：已修复（含回归测试）或已显式拆 successor（超时机制，附理由）——零第三态；P1 集为空时显式记录 "No P1 items"（F3 如有 FP 条目仍执行，F1/F2/F4 跳过，不作废 plan）
- [ ] 每个被修子族有类别清扫核对记录（**权威分母 = 裁决表清单**，恒等式：裁决表该族条数 = 修复数 + FP 已处置数 + 豁免数 + successor 拆分数；rg 辅助发现的未收录站点已上报归因）
- [ ] **端到端验证**（P1 集非空时适用；P1 集为空时显式标注 N/A）：至少一条测试从用户可见入口（BizModel/GraphQL 路径）到受影响输出走通修复语义（如 lineage 生成在测试内 tr-TR 默认 locale 下端到端正确）——组件级单测不能替代
- [ ] **无静默跳过**：修复不得以吞异常/空实现/静默钳制替代显式失败语义；新增分支未实现时抛异常而非返回默认值
- [ ] **新功能测试规则**：每个修复项对应的新增测试逐项列出（测试类名 + 验证的行为）；纯标注/处置类 FP 项注明 "No new test required: 标注不改行为"
- [ ] 受影响 owner-doc（`docs-for-ai/03-modules/nop-metadata.md` 等）同步，或显式写 No owner-doc update required（逐 Phase 裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — I5' 全量验证与门禁棘轮收口

Status: planned
Targets: `ai-dev/tools/run-nop-metadata-invariants.sh`、`.github/workflows/maven.yml`（如需模式升级）、baseline 文件、`formal-red-list-cycle2`（.md，新建于 `ai-dev/audits/nop-metadata-invariants/`）（棘轮记录）、`docs-for-ai/02-core-guides/invariant-guards.md`

- Item Types: `Proof`

- [ ] **V1 模块全量**：`./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures；上游模块 flaky 如实记录，不掩盖）
- [ ] **V2 门禁收口（按 0820-1 模式归属表逐门禁执行）**：修复后 codebase 上 4+N 门禁达到终态——Cycle 1 四门禁保持零命中；模式 a 新门禁零命中；**模式 b 新门禁完成 baseline 终态重写**（baseline = 已批准豁免清单 = 方式 a FP 条目 + 优化候选维持条目，或清空）。baseline 清空且无放行注释 → 升级模式 a（零命中阻断式）并**同步更新模式归属表**（升级后归属表仍与 CI 接线一致）；baseline 仅剩已批准豁免条目 → 保持模式 b ⊆ 对账（新增命中即红）。升级/重写后做 hard-gate proof（注入 baseline 外违规样例 → 门禁红 → 还原 → 绿，Cycle 1 I5 先例）；**终态 codebase 上聚合入口 `run-nop-metadata-invariants.sh` 整体退出码 0（含模式 b 豁免驻留时）并留证**
- [ ] **V3 棘轮记录**：`formal-red-list-cycle2` 记录 Cycle 2 棘轮（初始命中数 → 终态：P1 修复数清零 + successor 拆分数 + 豁免驻留数（FP baseline 驻留/放行注释 + 优化候选维持）），恒等式可复核
- [ ] **V4 owner-doc 同步**：`docs-for-ai/02-core-guides/invariant-guards.md` 同步 Cycle 2 门禁集合、模式 b 语义与终态（该文档描述"非零即阻断"的既有叙事须与新终态一致）；CI workflow 与本地聚合入口行为一致（如模式升级，workflow 同步且无静默放行）

Exit Criteria:

- [ ] V1 命令输出 BUILD SUCCESS 且 0 failures（记录测试总数）
- [ ] V2 终态达成且与模式归属表一致（含升级场景下归属表同步更新）：无 P1 残留命中（命中集 ⊆ 已批准豁免清单或为空）；聚合入口终态整体退出码 0 已留证；注入/还原 proof 有证据
- [ ] **接线验证**：CI `invariant-gate` job 与本地聚合入口行为一致（模式 a 阻断式 / 模式 b baseline 对账式与归属表一致，无静默放行配置）
- [ ] 棘轮记录含可复现计数（初始 → 终态对比，含豁免处置清单与 successor 拆分）
- [ ] `invariant-guards.md` 已同步（或显式记录无需更新的理由）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — I6' 循环收口与稳态判定

Status: planned
Targets: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`、`ai-dev/audits/nop-metadata-invariants/invariant-catalog.md`（如需）、本 plan Closure 节

- Item Types: `Decision` | `Proof`

- [ ] **C1 统计**：Cycle 2 门禁数（4+N）/ red list 数 / 修复数 / 新族数，来源可追溯（formal-red-list + adjudication-table + 本 plan）
- [ ] **C2 稳态判定**：按 roadmap Loop Rule 判定（全部门禁零命中 + 零新族 → 稳态暂停；有新族 → 登记 Cycle 3 触发）
- [ ] **C3 复触发条件登记**：CI 变红 / 新增或重命名 processor / bizmodel / ORM entity / 周期复探——更新至 roadmap（如措辞已存在则核对仍准确）
- [ ] **C4 roadmap 同步**：Work Item Status 表新增 "Cycle 2 / I1'–I6'（invariant-loop 第二轮）" 行并标状态——**消歧声明**：与既有三行 "Cycle 2 / 再审计 remediation"（2026-08-14 审计修复系列）为不同系列，行名须带 "invariant-loop" 标注或表头注明两系列关系；Follow-up Backlog 核对无未标注残留
- [ ] **C5 独立 closure audit**：fresh-session 子 agent 按 Closure Gates 逐项核验 live repo（含 Anti-Hollow：修复在运行时路径被真实调用、端到端测试真实走通、无空壳/静默跳过），证据写入本 plan Closure 节

Exit Criteria:

- [ ] 统计四元组记录且来源可追溯
- [ ] 稳态判定有结论 + 依据；复触发条件在 roadmap 中最新
- [ ] roadmap Work Item Status 表与实际状态一致（无完成项未标、无未完成项虚标）
- [ ] 独立 closure audit 完成，证据写入 plan 文件（Reviewer/session ID + 逐条 PASS/FAIL）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 对本次修改文件 0 新增 broken link
- [ ] `ai-dev/logs/` 对应日期条目已更新（含 Cycle 2 收口记录）

## Closure Gates

- [ ] Phase 1~3 全部 Exit Criteria 勾选，各 Phase Status = completed
- [ ] I3' 裁决表 P1 集零残留（修复 / 显式 successor 拆分，无第三态）
- [ ] 类别清扫恒等式成立（权威分母 = 裁决表；每族：裁决条数 = 修复 + FP 已处置 + 豁免 + successor 拆分）
- [ ] 模块全绿 + 4+N 门禁终态达成（按模式归属表：零命中或 ⊆ 已批准豁免清单）+ 棘轮记录可复核
- [ ] Cycle 2 统计与稳态判定完成，复触发条件已登记
- [ ] 无 confirmed live defect 被降级（deferred 区仅有已裁定 non-blocking 项）
- [ ] 受影响 owner-doc（含 `invariant-guards.md`）已同步或逐项裁定 No update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证 (a) 修复在运行时路径被真实调用（非仅编译通过），(b) 端到端测试从入口到输出走通（P1 非空时），(c) 无空方法体/静默跳过/no-op
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0

## Deferred But Adjudicated

（起草时无。执行中产生的延期项必须按 Allowed Deferred Classifications 分类并附 Why Not Blocking Closure；I3' 裁定的 optimization candidate 维持者属裁决表闭环状态，不在此重复登记。）

## Non-Blocking Follow-ups

- Cycle 3 / I1 新族门禁实现（仅当 I3' 登记新族且 C2 判定触发时启动，非本计划拥有）。
- 全仓门禁扩展 —— 归各自 mission。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / 每条 Exit Criterion 与 Closure Gate 的验证结果 / Anti-Hollow 检查结果 / deferred 分类检查>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
