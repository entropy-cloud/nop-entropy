# 2 nop-stream-core 模块审计（roadmap item 7）

> Plan Status: active
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 7（Phase M 首个审计项，deps: item 6）；Phase M 审计统一模式（roadmap Work Items 分组说明）
> Related: `2026-09-01-0938-1-design-productization-gap-analysis.md`（item 6，**本 plan 的执行前置依赖**，其 Phase 3 产出 core 模块审计重点输入）；`2026-09-01-0938-3-runtime-module-audit.md`（后续 sibling 审计）
> Mission: nop-stream-productization
> Work Item: roadmap item 7

## Purpose

对 `nop-stream-core` 按产品标准完成模块审计并收口：验证 2026-05-20 duplicate-code audit 与 2026-06-30 code audit 的整改收口、执行产品化视角新增审计（含 D-GAP 下发的 core 审计重点）、复查重复代码与核心逻辑优雅性/可靠性；小缺陷就地修复（含回归测试），大缺陷转为 roadmap Follow-up 工作项。

## Current Baseline

（2026-09-01 live 核对）

- `nop-stream/nop-stream-core`：364 个 main Java 文件 / 214 个 test Java 文件（`find -name "*.java"` 计数；`*Test*` 命名 211 个）——main 包含 datastream、model、graph、jobgraph、execution（含 `InputGate`/`ResultPartition`/`RecordWriter`/`RecordReader`/`TaskExecutor`/`Task`/`SubtaskTask` 等图执行/传输/任务执行类）、operators、windowing（`windowing` 包）、common/state（状态接口；05-20 §8 的 `core/state` 包 live 已不存在、`PriorityComparable` 等已并入 `common/state`——§8 整改落地状态由 Phase 1 正式核验）、checkpoint、common/eventtime、configuration、transformation 等
- 2026-05-20 duplicate-code audit（`ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md`，Status: resolved，时点 344 文件/10 模块）：报告以 **§1—§9 编号组**组织（§4 含 4.1—4.5 子组），其中与本 plan（core）直接相关：§1（operator vs operators 包级重复）、§4.1—4.5（core 死代码：孤立图执行路径/未使用 Trigger-Evictor/Accumulator/Function 接口/其他零引用文件如 `StreamConstants.java`）、§6（孤立图执行路径）、§8（`core/state` vs `core/common/state` 包分裂）、§9（`core/sink` 单文件包）；跨组项：§5（TimerService 实现重复——实现层位于 runtime，本 plan 仅核对其 core 侧使用方现状）、§7（空壳模块——模块级处置，本 plan 统一核验一次，结论供 items 8—11 引用）；**非本 plan**：§2（CepOperator 重复，主体在 cep 模块、runtime 侧 CepWindowOperator 归 item 8 → item 9/8）、§3（runtime 死代码 → item 8）——整改 live 收口状态尚未逐组核验
- 2026-06-30 code audit（`ai-dev/analysis/2026-06-30-nop-stream-code-audit.md`，全 9 模块，无独立 core 章节）：core 相关发现的 seed 清单（本 plan Phase 1 核对基准）：§6.1 三项必修复（`InputGate` Optional 返回 null ×6、`WindowedStreamImpl` 废弃 API 回退默认路径、DataStream API 窗口聚合实际路径/`WindowOperatorFactory` 注册验证）+ §2.1 UOE 桩中 core 项（`forceNonParallel()`、`ICheckpointExecutorFactory.executeWithCheckpoint(StreamModel)`）+ §2.2 core 项（78 个通配符导入中的 core 文件、`InputGate` 硬编码轮询/对齐超时）+ §6.2 core 项（execution 包 26 文件膨胀、`ShardPrefixedKey` 重复）+ §6.3 core 测试缺口（P0 Operator State e2e、P1 分支/合并多链管线、P2 execution.flow/plan/transport、P3 configuration/streamrecord/time）；**派生规则**：报告中其余发现按「涉及文件位于 `nop-stream-core`」准则纳入核对表
- 2026-08-06 audit baseline（`ai-dev/analysis/2026-08/2026-08-06-nop-stream-audit-baseline-and-roadmap-analysis.md`）：提供审计方法论（审计语义/capability matrix/证据分级）；其 3 份 remediation plans（`ai-dev/plans/nop-stream-production/2026-08-04-2300-1/2/3-*.md`）已由前序 production roadmap 收口（P-REQ 综合报告 §2.3 #6 记录）——本 plan 需复核该收口结论对 core 成立
- 空壳扫描工具已存在：`ai-dev/tools/scan-hollow-implementations.mjs`（plan guide Closing 规则 5b 引用；对 high/critical 发现退出码非 0，无白名单机制）；另有 nop-stream 不变式检查 `ai-dev/tools/check-nop-stream-invariants.mjs`（CI fail-fast，`.github/workflows/maven.yml`）
- D-GAP 报告：**尚未存在**，由 plan `2026-09-01-0938-1`（item 6）产出——本 plan 执行时从 roadmap item 6 done 记录中取其路径（item 6 写回规则已明确含报告路径），消费其「items 7—11 逐模块审计重点清单」中 core 条目；若该路径缺失或 item 6 未 done，本 plan 置 `blocked` 并上报 mission engine，不得凭空假设审计重点
- 前序 deferred 接手：plan 0753-3（Non-Blocking Follow-ups 节）「既有 Flink/Beam 对比报告产品化维度正式补评」——其裁定对象是 P-REQ 综合矩阵 §1.3 中 Flink/Beam 行的非 high 置信（low/medium）及 no-evidence 单元格；本 plan 作为 Phase M 首个审计 plan 落定该 **Phase M 级裁定**（覆盖 items 7—11），items 8—11 引用其结论不重复裁定；若本 plan blocked/未完成而 items 8+ 先行执行，该裁定 fallback 至最先执行的 Phase M 审计 plan 落定
- 验证基线：roadmap Cross-cutting concerns 约定 `./mvnw test -pl nop-stream -am -T 1C` 全绿

## Goals

- 整改收口验证：2026-05-20 core 相关组（§1/§4.1—4.5/§6/§8/§9 + 跨组项 §5 core 侧/§7 模块级）与 2026-06-30 core 相关发现（Current Baseline seed 清单 + 派生规则）逐组/逐项核对 live 状态（landed / partial / regressed），每组/项有证据指针
- 产品化新增审计：D-GAP core 审计重点逐条消化 + 核心路径（执行管线/窗口/checkpoint 数据对象）优雅性与可靠性审计 + 空壳/静默跳过扫描 + 测试覆盖抽查
- Flink/Beam 产品化维度补评裁定落地：对 P-REQ 矩阵中 Flink/Beam 行的全部非 high 置信及 no-evidence 单元格（共 8 格：Flink CONN 3@medium、Flink PERF 3@low、Flink DOC 3@low、Beam DEPL 2@medium、Beam OPS 2@medium、Beam FT 2@medium、Beam DOC 2@low、Beam PERF no-evidence）逐格裁定「补评（补什么证据、归属哪个 item）」或「无需补评（现有证据对 P-REQ 裁定用途已足，理由）」，双向必居其一；作为 Phase M 级裁定一次落定
- 缺陷处置：单 plan 范围内可收敛的小缺陷就地修复（每个修复配 focused 回归测试）；跨模块/大规模缺陷转 roadmap Follow-up 工作项（编号顺延、来源标注本 plan）。小/大缺陷判定准则：修复限于 core 模块内、不改公共契约、无需新测试基建 → 小缺陷；否则（跨模块、改公共契约、需大规模测试基建）→ 大缺陷转 Follow-up
- 审计报告落地 `ai-dev/analysis/`（遵循 `00-analysis-writing-guide.md`）并完成 roadmap item 7 写回

## Non-Goals

- 跨模块重构（需 Follow-up 立项，超出单 plan 范围）
- 实施 D-GAP 裁定中的 go 项功能开发（dry-run 入口、异常策略等属其归属执行项；本 plan 只审计与提供证据）
- 新连接器开发、runtime/cep/connector/rocksdb/flow/fraud-example 模块审计（items 8—11）
- 2026-05-20/2026-06-30 报告正文的历史重写（只引用与核验，不回写）

## Scope

### In Scope

- 审计与报告：`nop-stream/nop-stream-core/` 全模块 + 新增审计报告一份（`ai-dev/analysis/{执行当月}/`，命名遵循 writing guide）
- 修复：core 模块内小缺陷（含 focused 测试）
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（仅 Follow-up 追加 + Last updated + closure 后 item 7 写回）
- 修改（条件性）：`ai-dev/tools/scan-hollow-implementations.mjs` 检测规则——仅当 hollow scan 误报经核实源于工具检测缺陷时允许修正（误报处置路径 b 的载体）
- 修改（条件性）：受修复影响的最小 owner-doc 同步（见 Phase 3；与 Out Of Scope 的「不顺手重写」边界见该节修正表述）
- 只读输入：两份历史审计报告、2026-08-06 baseline、D-GAP 报告、`~/sources` 竞品源码（如需对照）

### Out Of Scope

- 其他 9 个子模块的审计与修复
- `ai-dev/design/nop-stream/` 的结构性重写与历史回写（若修复致 owner doc drift，仅做 Phase 3 约定的最小事实同步，不扩写、不重构）

## Execution Plan

### Phase 1 - 历史审计整改收口验证

Status: planned
Targets: `nop-stream/nop-stream-core/`、审计报告 Phase 1 章节

- Item Types: `Proof | Decision`

- [ ] 2026-05-20 audit core 相关组逐组核对（组清单见 Current Baseline：§1、§4.1—4.5、§6、§8、§9 + 跨组项 §5 的 core 侧使用方现状 + §7 模块级空壳处置核验）：live repo 中该组的当前状态（已清除 / 已收编（原死代码被生产接线启用）/ 部分残留 / 回潮新增），每组附源码路径或 absence 证据；§2（主体 cep、runtime 侧归 item 8）与 §3（runtime）显式标注「非本 plan 范围，归属 item 9/8」不在本表核对
- [ ] 2026-06-30 code audit core 相关发现逐项核对（seed 清单见 Current Baseline + 「涉及文件位于 nop-stream-core」派生规则，执行时先落完整核对表再逐项核对）：landed / partial / regressed 三态判定 + 证据指针
- [ ] 复核「2026-08-04-2300-1/2/3 remediation plans 已收口」结论对 core 模块成立（抽查其 core 侧修复点 live 存在性）
- [ ] 发现 partial/regressed 项：按 Goals 的小/大缺陷判定准则归类（进 Phase 3 修复或 Follow-up 候选），逐项记录归类理由

Exit Criteria:

- [ ] 报告 Phase 1 章节含 05-20 core 相关组核对表 + 06-30 core 发现完整核对表（先列全表再逐项核对，每行三态 + 证据指针，无「未核对」空行；§2/§3 的归属标注在表外显式记录）
- [ ] remediation plans 收口复核结论落地（成立/不成立 + 抽查点）
- [ ] 全部 partial/regressed 项有归类结论（修复进 Phase 3 或 Follow-up 候选，附理由）
- [ ] No owner-doc update required（纯核验章节）
- [ ] No new test required: 纯核验章节，无代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 产品化视角新增审计

Status: planned
Targets: `nop-stream/nop-stream-core/`、审计报告 Phase 2 章节

- Item Types: `Proof | Decision`

- [ ] 消化 D-GAP 报告的 core 审计重点清单：先对照 D-GAP 报告 core 条目建立勾销清单，再逐条执行对应审计动作（D-GAP 判定「无额外重点」时执行显式记录动作，将「无额外重点」结论写入报告）；closure 前对照清单确认无遗漏条目
- [ ] Flink/Beam 产品化维度补评裁定（Phase M 级，plan 0753-3 follow-up 接手）：裁定对象为 P-REQ 综合矩阵 §1.3 中 Flink/Beam 行的全部 8 个非 high 置信及 no-evidence 单元格（清单见 Goals）——逐格裁定「补评（补什么证据、归属哪个 item 执行）」或「无需补评（P-REQ 现有证据对其裁定用途已足，理由）」；本裁定一次落定并写入报告，items 8—11 引用结论
- [ ] 核心逻辑优雅性/可靠性审计：五层编译管线（model/graph/jobgraph/execution）、窗口算子（windowing）、状态接口（common/state）、checkpoint 数据对象路径的重复代码复查（对照 2026-05-20 方法论）、错误处理一致性（fail-fast vs 吞异常）、边界条件
- [ ] 运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high`，对 high/critical 发现逐条核实（真实空壳 vs 误报）；**误报处置路径**：修正代码模式（使扫描不再命中）或修正工具检测规则（若模式本身正确）——「仅记录不改」不合法（Closure Gate 退出码 0 为不可降级硬门禁，guide Minimum Rule #13）
- [ ] 测试覆盖抽查：core 主要包（datastream/model/graph/jobgraph/execution/operators/windowing/common 的 state 包）各抽 ≥2 个代表性测试命中确认 + 按行数 top-5 文件的直接测试覆盖检查；明显缺口记录

Exit Criteria:

- [ ] D-GAP core 审计重点勾销清单落地（逐条消化记录 + 对照 D-GAP 报告无遗漏；含「无额外重点」时的显式结论）
- [ ] Flink/Beam 补评裁定落地：8 个非 high 置信及 no-evidence 单元格逐格有裁定值与理由（或补评归属），两方向必有其一
- [ ] hollow scan 退出码记录 + high/critical 发现逐条核实结论（真实发现进 Phase 3 或 Follow-up；误报处置动作已执行或已按缺陷流程进入 Phase 3，非仅记录）
- [ ] 测试覆盖抽查结论表落地（每包 ≥2 代表性测试命中情况 + top-5 文件覆盖结论）
- [ ] 优雅性/可靠性审计发现逐条带源码锚点
- [ ] No owner-doc update required（发现记录在报告；owner doc 变更属 Phase 3 修复的附带裁定）
- [ ] No new test required: 纯审计章节，无代码变更（Phase 3 修复的测试要求在 Phase 3）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 缺陷处置与收口

Status: planned
Targets: `nop-stream/nop-stream-core/`、`ai-dev/backlog/nop-stream-productization-roadmap.md`、审计报告收口章节

- Item Types: `Fix | Follow-up`

- [ ] 就地修复全部归类为小缺陷的项：每个修复附 focused 回归测试（验证正确结果而非仅无异常）；修复不引入空壳/静默跳过（未实现分支显式抛异常）；修复若落在 `_` 前缀生成文件，上移到源模型/模板层处理（生成文件禁改）
- [ ] 大缺陷逐项转为 roadmap Follow-up 工作项：按 Rules 追加到 Work Items 末尾（编号顺延、状态 todo、来源标注本 plan + 缺陷证据锚点），并更新 Last updated
- [ ] 触及执行管线行为的修复：至少一条端到端验证（从 `env.addSource()` 到 sink 输出，见 plan guide Minimum Rules #22）
- [ ] 全量回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿；`node ai-dev/tools/check-nop-stream-invariants.mjs` 通过（CI fail-fast 门禁）
- [ ] 审计报告 Status 定稿（resolved 或 open + 遗留清单），含全部发现→处置的零丢失映射表
- [ ] 小缺陷修复若改变 live 行为且 owner doc 受影响：同步最小 owner-doc 更新或显式记录 `No owner-doc update required`

Exit Criteria:

- [ ] 全部 in-scope 小缺陷修复落地且各有 focused 测试（新增测试清单在报告中列明，验证了哪个新行为）
- [ ] 全部大缺陷已追加为 roadmap Follow-up 工作项（或显式记录「无大缺陷」）
- [ ] **端到端验证**（如适用，触及管线行为时）：入口到 sink 的完整路径测试存在且通过
- [ ] **接线验证**（如适用，新增/调整组件时）：新组件被既有调用链在运行时实际调用（测试断言或调用链追踪）
- [ ] **无静默跳过**：修复代码中新增分支无空方法体/吞异常模式（hollow scan 复跑退出码 0）
- [ ] 修复改变 live 行为时：受影响 owner doc 已做最小事实同步，或显式记录 `No owner-doc update required`（含理由）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（报告新增后）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 2026-05-20（core 相关组）与 2026-06-30（core seed 清单 + 派生规则所得项）整改收口核对完成，无未处置的 partial/regressed 项
- [ ] D-GAP core 审计重点全部消化且对照勾销清单无遗漏（含显式「无额外重点」结论路径）
- [ ] plan 0753-3 follow-up 的 Flink/Beam 补评项已闭环（逐格裁定落地或显式无需补评裁定），结论可供 items 8—11 引用
- [ ] 空壳扫描 high/critical 无未处置真实发现（误报已按处置路径消除，非仅记录）
- [ ] 小缺陷修复全部带 focused 测试；大缺陷全部 Follow-up 化（或显式无）
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] `./mvnw compile`（随 `-pl nop-stream -am` 构建覆盖）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] checkstyle / 代码规范检查通过（随 mvnw 构建）
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] **Anti-Hollow Check**：closure audit 已验证修复组件被既有调用链在运行时确实调用、无空方法体/静默跳过/no-op 作为正常实现
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] roadmap item 7 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（执行中按需追加：仅允许 `watch-only residual | optimization candidate | out-of-scope improvement` 三类，逐条附 Why Not Blocking Closure）

## Non-Blocking Follow-ups

（执行中按需追加：不影响 core 审计 contract closure 的治理项或优化项）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:

- （closure 时填写）
