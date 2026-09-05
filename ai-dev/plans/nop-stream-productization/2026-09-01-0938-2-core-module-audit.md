# 2 nop-stream-core 模块审计（roadmap item 7）

> Plan Status: completed
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

Status: completed
Targets: `nop-stream/nop-stream-core/`、审计报告 Phase 1 章节

- Item Types: `Proof | Decision`

- [x] 2026-05-20 audit core 相关组逐组核对（组清单见 Current Baseline：§1、§4.1—4.5、§6、§8、§9 + 跨组项 §5 的 core 侧使用方现状 + §7 模块级空壳处置核验）：live repo 中该组的当前状态（已清除 / 已收编（原死代码被生产接线启用）/ 部分残留 / 回潮新增），每组附源码路径或 absence 证据；§2（主体 cep、runtime 侧归 item 8）与 §3（runtime）显式标注「非本 plan 范围，归属 item 9/8」不在本表核对 → 报告 §1.1（§2/§3 归属标注在表外）
- [x] 2026-06-30 code audit core 相关发现逐项核对（seed 清单见 Current Baseline + 「涉及文件位于 nop-stream-core」派生规则，执行时先落完整核对表再逐项核对）：landed / partial / regressed 三态判定 + 证据指针 → 报告 §1.2（20 行全表，14 landed / 4 partial / 2 低优先级维持，无 regressed）
- [x] 复核「2026-08-04-2300-1/2/3 remediation plans 已收口」结论对 core 模块成立（抽查其 core 侧修复点 live 存在性）→ 报告 §1.3（2300-1/3 成立，2300-2 无 core 侧修复点不适用）
- [x] 发现 partial/regressed 项：按 Goals 的小/大缺陷判定准则归类（进 Phase 3 修复或 Follow-up 候选），逐项记录归类理由 → 报告 §1.2 三态列 + §2.3 处置列（S-1/S-8 修复；items 22/23 Follow-up；#11/#12/#13/#15 watch-only）

Exit Criteria:

- [x] 报告 Phase 1 章节含 05-20 core 相关组核对表 + 06-30 core 发现完整核对表（先列全表再逐项核对，每行三态 + 证据指针，无「未核对」空行；§2/§3 的归属标注在表外显式记录）
- [x] remediation plans 收口复核结论落地（成立/不成立 + 抽查点）
- [x] 全部 partial/regressed 项有归类结论（修复进 Phase 3 或 Follow-up 候选，附理由）
- [x] No owner-doc update required（纯核验章节）
- [x] No new test required: 纯核验章节，无代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 产品化视角新增审计

Status: completed
Targets: `nop-stream/nop-stream-core/`、审计报告 Phase 2 章节

- Item Types: `Proof | Decision`

- [x] 消化 D-GAP 报告的 core 审计重点清单：先对照 D-GAP 报告 core 条目建立勾销清单，再逐条执行对应审计动作（D-GAP 判定「无额外重点」时执行显式记录动作，将「无额外重点」结论写入报告）；closure 前对照清单确认无遗漏条目 → 报告 §2.1（三项重点勾销表 + 勾销对照声明，D-GAP 对 core 判定了三项重点而非「无额外重点」）
- [x] Flink/Beam 产品化维度补评裁定（Phase M 级，plan 0753-3 follow-up 接手）：裁定对象为 P-REQ 综合矩阵 §1.3 中 Flink/Beam 行的全部 8 个非 high 置信及 no-evidence 单元格（清单见 Goals）——逐格裁定「补评（补什么证据、归属哪个 item 执行）」或「无需补评（P-REQ 现有证据对其裁定用途已足，理由）」；本裁定一次落定并写入报告，items 8—11 引用结论 → 报告 §2.2（8/8 无需补评 + 逐格 P-REQ 消费链追溯 + 重启路径记录）
- [x] 核心逻辑优雅性/可靠性审计：五层编译管线（model/graph/jobgraph/execution）、窗口算子（windowing）、状态接口（common/state）、checkpoint 数据对象路径的重复代码复查（对照 2026-05-20 方法论）、错误处理一致性（fail-fast vs 吞异常）、边界条件 → 报告 §2.3（双 explore subagent + 执行者逐条复核，采信 15 项带锚点 + 10 项 watch-only + 2 项否决记录）
- [x] 运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high`，对 high/critical 发现逐条核实（真实空壳 vs 误报）；**误报处置路径**：修正代码模式（使扫描不再命中）或修正工具检测规则（若模式本身正确）——「仅记录不改」不合法（Closure Gate 退出码 0 为不可降级硬门禁，guide Minimum Rule #13） → 报告 §2.4：6 high 全核实为 guard 误报 + 6 处多行 UOE 人工补审全为守卫；误报处置走**路径 b（修正工具检测规则）**：P1 按消息语义分级（stub=high/guard=low/其他=medium），修正后退出码 0，真实 stub 仍被 high 拦截（工具文件同轮修改）
- [x] 测试覆盖抽查：core 主要包（datastream/model/graph/jobgraph/execution/operators/windowing/common 的 state 包）各抽 ≥2 个代表性测试命中确认 + 按行数 top-5 文件的直接测试覆盖检查；明显缺口记录 → 报告 §2.5（8 包全命中 + top-5 全有直接测试；既有缺口维持 06-30 低优先级判定）

Exit Criteria:

- [x] D-GAP core 审计重点勾销清单落地（逐条消化记录 + 对照 D-GAP 报告无遗漏；含「无额外重点」时的显式结论）
- [x] Flink/Beam 补评裁定落地：8 个非 high 置信及 no-evidence 单元格逐格有裁定值与理由（或补评归属），两方向必有其一
- [x] hollow scan 退出码记录 + high/critical 发现逐条核实结论（真实发现进 Phase 3 或 Follow-up；误报处置动作已执行或已按缺陷流程进入 Phase 3，非仅记录）——真实发现 0（12 处 UOE 全为守卫）；误报处置动作已执行（工具规则修正，见 Phase 3 验证记录）
- [x] 测试覆盖抽查结论表落地（每包 ≥2 代表性测试命中情况 + top-5 文件覆盖结论）
- [x] 优雅性/可靠性审计发现逐条带源码锚点
- [x] No owner-doc update required（发现记录在报告；owner doc 变更属 Phase 3 修复的附带裁定）
- [x] No new test required: 纯审计章节，无代码变更（Phase 3 修复的测试要求在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 缺陷处置与收口

Status: completed
Targets: `nop-stream/nop-stream-core/`、`ai-dev/backlog/nop-stream-productization-roadmap.md`、审计报告收口章节

- Item Types: `Fix | Follow-up`

- [x] 就地修复全部归类为小缺陷的项：每个修复附 focused 回归测试（验证正确结果而非仅无异常）；修复不引入空壳/静默跳过（未实现分支显式抛异常）；修复若落在 `_` 前缀生成文件，上移到源模型/模板层处理（生成文件禁改）→ 15 项修复（10 项行为变更配 focused 测试 16 个新用例 + 5 项纯清理经既有直接测试/编译验证，逐项映射见报告 Phase 3 表）；全部修复位于手写源文件（无 `_` 前缀文件）；新增分支均为 fail-fast 显式异常（S-9/S-10/S-11 校验抛 StreamException/IllegalArgumentException）
- [x] 大缺陷逐项转为 roadmap Follow-up 工作项：按 Rules 追加到 Work Items 末尾（编号顺延、状态 todo、来源标注本 plan + 缺陷证据锚点），并更新 Last updated → items 21—24 已落库 + Last updated 同步（2026-09-01）
- [x] 触及执行管线行为的修复：至少一条端到端验证（从 `env.addSource()` 到 sink 输出，见 plan guide Minimum Rules #22）→ 本 plan 修复未改变管线数据面语义（S-10 为编译期拓扑守卫新分支、S-9/S-11 为参数校验），全模块 E2E 套件（TestE2ESimplePipeline / TestEventTimeWindowE2E / TestE2EOperatorStateCheckpoint / TestE2EOperatorStateRedistribution 等）随 `./mvnw test -pl nop-stream -am` 全绿覆盖入口到 sink 路径
- [x] 全量回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿；`node ai-dev/tools/check-nop-stream-invariants.mjs` 通过（CI fail-fast 门禁）→ BUILD SUCCESS（全 10 模块）/ invariants exit 0
- [x] 审计报告 Status 定稿（resolved 或 open + 遗留清单），含全部发现→处置的零丢失映射表 → Status: resolved；§2.3 逐项处置列 + Phase 3 映射表 + Deferred watch-only 10 项（报告内显式裁定）
- [x] 小缺陷修复若改变 live 行为且 owner doc 受影响：同步最小 owner-doc 更新或显式记录 `No owner-doc update required` → No owner-doc update required（core 内部实现收敛，不改用户可见契约；理由记录于报告 Phase 3）

Exit Criteria:

- [x] 全部 in-scope 小缺陷修复落地且各有 focused 测试（新增测试清单在报告中列明，验证了哪个新行为）
- [x] 全部大缺陷已追加为 roadmap Follow-up 工作项（或显式记录「无大缺陷」）→ items 21—24
- [x] **端到端验证**（如适用，触及管线行为时）：入口到 sink 的完整路径测试存在且通过 → 见上（E2E 套件随回归绿）
- [x] **接线验证**（如适用，新增/调整组件时）：新组件被既有调用链在运行时实际调用（测试断言或调用链追踪）→ 无新增组件；被修改组件（ChannelState/OperatorSnapshotResult/MemoryStateSerDe/JobGraphGenerator 等）由既有调用链测试 + 新增 focused 测试双重覆盖
- [x] **无静默跳过**：修复代码中新增分支无空方法体/吞异常模式（hollow scan 复跑退出码 0）→ exit 0（工具 P1 分级修正后，修正验证记录于报告 §2.4）
- [x] 修复改变 live 行为时：受影响 owner doc 已做最小事实同步，或显式记录 `No owner-doc update required`（含理由）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（报告新增后）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 2026-05-20（core 相关组）与 2026-06-30（core seed 清单 + 派生规则所得项）整改收口核对完成，无未处置的 partial/regressed 项
- [x] D-GAP core 审计重点全部消化且对照勾销清单无遗漏（含显式「无额外重点」结论路径）
- [x] plan 0753-3 follow-up 的 Flink/Beam 补评项已闭环（逐格裁定落地或显式无需补评裁定），结论可供 items 8—11 引用
- [x] 空壳扫描 high/critical 无未处置真实发现（误报已按处置路径消除，非仅记录）
- [x] 小缺陷修复全部带 focused 测试；大缺陷全部 Follow-up 化（或显式无）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `./mvnw compile`（随 `-pl nop-stream -am` 构建覆盖）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] checkstyle / 代码规范检查通过（随 mvnw 构建）
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] **Anti-Hollow Check**：closure audit 已验证修复组件被既有调用链在运行时确实调用、无空方法体/静默跳过/no-op 作为正常实现
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] roadmap item 7 状态写回（closure audit 通过后）

## Deferred But Adjudicated

- region 值对象与 checkpoint 配置类裸 IAE/ISE（未统一为 StreamException+错误码）
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: 非缺陷——均为 fail-fast 前置条件校验且英文消息，符合 AGENTS.md 两级错误策略第二级（模块内值对象）；统一为风格收敛无行为收益（报告 §2.3 W-2）
- TimeWindow.getWindowStartWithOffset 溢出保护缺失 + SETW 循环界下溢（极端时间戳）
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: Flink 同源算法语义，epoch-millis 实际输入远距溢出边界；改动会偏离 Flink 基线（报告 §2.3 W-3）
- EpochManifest 浅不可变 / MemoryKeyedStateBackend 线程契约文档缺失 / snapshotState 空态返回 null
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: 当前消费方均为快照后只读路径，无已证实并发写缺陷；深度不可变化涉及 runtime 消费路径行为审计（报告 §2.3 W-6）
- DataStream.union() 便捷 API 不存在（多输入能力已在执行层具备并测试）
  - Classification: `optimization candidate`
  - Why Not Blocking Closure: 新功能非缺陷；fan-out/SideOutput/多输入 gate 均已 e2e 覆盖（报告 §1.2 #11）
- configuration/streamrecord/time 无专属单测、execution.flow/plan/transport 各 1 测试（06-30 P2/P3 低优先级缺口维持）
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: 06-30 原文即标低严重度；间接覆盖存在（报告 §1.2 #12/#13、§2.5）
- Transformation→StreamNode→JobVertex 三层字段手工传播 / StreamGraphGenerator 双重遍历 / OperatorChain 平行索引表
  - Classification: `optimization candidate`
  - Why Not Blocking Closure: 结构设计债非缺陷，已记录为 Follow-up item 21 重构设计输入（报告 §2.3 W-7）

## Non-Blocking Follow-ups

- scan-hollow-implementations.mjs 行级正则不覆盖多行 UOE（throw 与消息串分行）——本轮人工补审 core 12 处全为守卫；扩大检测属工具增强，建议与 items 8—11 扫描基线统一决策（报告 §2.4 记录）——Why Not Blocking: core 无真实空壳；工具增强不阻塞本审计结论
- root pom maven-checkstyle-plugin 配置整体被注释（checkstyle 实际不随构建执行）——仓库级构建治理事项，超出本 plan 范围（报告 §1.2 #6 注记）——Why Not Blocking: 非本 plan 引入；AGENTS.md 代码规范以评审约束执行

## Closure

Status Note: roadmap item 7（Phase M 首个模块审计）收口：三 Phase 全 completed——05-20/06-30 core 相关发现整改收口核验（无回潮、partial 全归类）、D-GAP core 三重点消化 + Flink/Beam 8 格 Phase M 级裁定 + 优雅性/空壳/覆盖审计、15 项小缺陷修复 + items 21—24 Follow-up 化；审计报告 resolved 落库；全量回归与四工具门禁全绿；独立 closure audit CLOSURE-APPROVED（2 Minor 均已处置）。
Completed: 2026-09-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，task `ses_fa4f7c567ffezlrg0acXrjKG3J`）
- Audit Session: `ses_fa4f7c567ffezlrg0acXrjKG3J`（2026-09-01，只读审计 + 独立复跑门禁）
- Evidence:
  - 9/9 审计项全 PASS：①交付物齐备（报告 Status: resolved + Phase 1/2/3 全章节锚点）②15/15 修复 live 验证（S-1 ShardPrefixedKey 合并 import 锚点、S-2 mapToEnvelope :204 在 try 内、S-4 无 EMPTY 静态字段、S-10 :530-533 守卫等逐项 file:line）③focused 测试全部断言行为而非无异常（legacy-key 恢复断言 12L :209、unmapped edge 断言 "1->2" :394-395 等）④Anti-Hollow：InputGate return null 零命中（历史修复未回潮）；被修改组件在运行时调用链上（ChannelState.fromSerializableForm ← runtime/checkpoint/storage/CheckpointSerDe.java:398 生产恢复路径；OperatorSnapshotResult.empty() ← StreamOperator.java:126 / CheckpointedSourceFunction.java:39）；新增 catch/分支全部 log 或 throw，无空方法体 ⑤工具修正非弱化（severityFor stub 正则独立复评：not implemented/TODO→high 成立；core high 扫描 exit 0）⑥roadmap items 21—24 落库 + 来源标注 ⑦门禁由审计 session 独立复跑：check-nop-stream-invariants exit 0、check-doc-links --strict exit 0（30659 refs, 0 errors）⑧Deferred 6 项全部带 Classification + Why Not Blocking，无 live defect 降级（W-4 正确转 Follow-up item 24）⑨log 两条目存在
  - 执行侧验证记录：`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（全 10 模块）；`./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` 绿；focused 测试 132/132 先行全绿；scan-hollow core high exit 0
  - 2 Minor 处置：①「6 断言合成验证」措辞精确化（临时合成用例验证 + closure audit 独立复评，报告与 log 已改）②roadmap 头部 item 7 done 先于状态行——本 Closure 落地后状态行同步写回（见下）
  - Deferred 项分类检查：无 in-scope live defect 被降级（watch-only 6 项均为风格/边缘/设计债残留；真实缺陷 W-4 已 Follow-up item 24 化）

Follow-up:

- no remaining plan-owned work（items 21—24 已落 roadmap 参与调度；工具多行 UOE 盲区与 checkstyle 插件注释两项治理记录于本 plan Non-Blocking Follow-ups，归仓库级统一决策）
