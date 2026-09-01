# 1 nop-stream-cep 模块审计（roadmap item 9）

> Plan Status: active
> Last Reviewed: 2026-09-01
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 9（Phase M 第三个审计项，deps: item 6 done）；Phase M 审计统一模式（roadmap Work Items 分组说明）
> Related: `2026-09-01-0938-1-design-productization-gap-analysis.md`（item 6，前置依赖，其 §3.1 产出 item 9 审计重点输入）；`2026-09-01-0938-2-core-module-audit.md`（item 7，**前序 sibling 审计**：其审计方法论与报告结构本 plan 复用；其报告 §2.2 Flink/Beam 8 格 Phase M 级裁定、§1.1 §7 空壳模块结论本 plan 仅引用不重复裁定；其 Follow-up item 22 含 cep 15 个 test 文件通配符导入，本 plan 路由不修）；`2026-09-01-0938-3-runtime-module-audit.md`（item 8，sibling 审计：其报告 §1.1 已核 05-20 §2 runtime 侧四文件全部删除，cep 主体侧核对归本 plan；2300-2 的 runtime 侧已复核，cep 侧归本 plan）
> Mission: nop-stream-productization
> Work Item: roadmap item 9

## Purpose

对 `nop-stream-cep` 按产品标准完成模块审计并收口：验证 2026-05-20 duplicate-code audit 与 2026-06-30 code audit 中 cep 相关发现的整改收口、复核 2026-08-04-2300-2 remediation plan 的 cep 侧修复点 live 存在性、执行产品化视角新增审计（D-GAP 判定 item 9 无额外重点——本 plan 显式记录该结论并按既有审计模式执行）、复查 NFA/SharedBuffer/模式编译核心路径的重复代码与优雅性/可靠性；小缺陷就地修复（含回归测试），大缺陷转为 roadmap Follow-up 工作项。

## Current Baseline

（2026-09-01 live 核对）

- `nop-stream/nop-stream-cep`：77 个 main Java 文件 / 55 个 test Java 文件（`find -name "*.java"` 计数）——main 包：`configuration`、`functions`（含 `adaptors`）、`model`（含 `model/_gen` 4 个生成文件 `_CepPatternGroupModel/_CepPatternModel/_CepPatternPartModel/_CepPatternSingleModel` + `builder`）、`nfa`（含 `aftermatch`/`compiler`/`sharedbuffer`）、`operator`（含 `CepOperator`）、`pattern`（含 `conditions`）、`time`（含 `cep.time.TimerService`）
- 2026-05-20 duplicate-code audit（Status: resolved）：与本 plan 直接相关仅 **§2（CepOperator vs CepWindowOperator ~85% 重复）**——其 runtime 侧四文件（CepWindowOperator/CepWindowTrigger/CepWindowAssigner + TestCepWindowOperator）已由 item 8 报告 §1.1 核对为**已清除**（2026-09-01 live `find` 零命中）；**cep 主体侧（CepOperator 及其 state 初始化问题）核对归本 plan**；§7 空壳模块结论（api/checkpoint/flink 删、flow 实现）由 item 7 统一核验，本 plan 引用不重复核验
- 2026-06-30 code audit（全 9 模块）cep 相关发现 seed 清单（本 plan Phase 1 核对基准）：§1.1 模块统计（时点 72 main/46 test，现 77/55）+ §1.2 TimerService 名称冲突（core.time 侧已由 item 7 S-8b 修正注解；`cep.time.TimerService:34` Javadoc 引用 core 版——core 侧修正后该引用语义应恢复正确，本 plan 核验）+ §1.3 依赖方向（cep → core + nop-xlang，时点 ✅；**live pom 已无 nop-xlang 依赖**（仅 core + guava）——该漂移属改善，Phase 1 核对表按「时点值 → live 变化」记录，不判 regressed）+ §2.1 UOE 桩 `GroupPattern.where/or/subtype`（原文判定「合法的禁止调用设计」，低）+ §2.4 `_gen` 生成代码仅在 cep 存在（4 文件，生成纪律不手改）+ §3.1/§3.2 测试覆盖（46 测试类 272 @Test，NFA 引擎覆盖充分）；**派生规则**：报告中其余发现按「涉及文件位于 `nop-stream-cep`」准则纳入核对表
- 2026-08-04-2300-2 remediation plan（checkpoint-state-backend-cep-correctness）**cep 侧修复点**：`SharedBufferAccessor.releaseNode` null 分支补 `versionsToExamine.pop()`（恢复栈 lockstep 不变量）+ 回归测试 `testReleaseNodePopsVersionOnNullEntry` + E2E `testFollowedByAnyBranchingWithSkipPastLastEvent`——item 8 报告 §1.3 已复核其 runtime 侧并注明「cep 侧归 item 11/9」，**本 plan 复核 cep 侧 live 存在性**（2300-1/3 无 cep 侧修复点，执行时逐条核对确认）
- D-GAP 报告（`ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md` §3.1 item 9 行）：**「无额外重点，按既有审计模式执行」**（D-GAP 裁定条目不触及 CEP 子系统；`MalformedPatternException` fail-fast 路径属既有异常层级审计范围）——本 plan Phase 2 显式记录该结论并执行对应勾销动作
- item 7 报告 §2.2：Flink/Beam 8 个低置信格 Phase M 级裁定**一次落定（全部无需补评）**，本 plan 引用不重复裁定；item 7 报告 §1.1 §7 行：空壳模块结论已收口，勿重复立项
- 工具：`ai-dev/tools/scan-hollow-implementations.mjs`（item 7 修正后具备消息语义分级：stub=high/guard=low/其他=medium；high/critical 发现退出码非 0）；`ai-dev/tools/check-nop-stream-invariants.mjs`（CI fail-fast 门禁）；工具多行 UOE 行级正则盲区为 plan 0938-2 Non-Blocking Follow-up 记录的仓库级治理项（items 8—11 扫描基线统一决策）——item 8 未触发工具修改，本 plan 沿同一基线执行（扫描 + 人工补审多行 UOE），不做工具增强
- 前序 production roadmap 已 shipped 的 CEP 能力（roadmap Current baseline）：NFA + Guava SharedBuffer、CEP.pattern() 公共 API（TestCepPublicApiE2E）、CEP 状态恢复（TestCepOperatorStateRecovery）
- 验证基线：roadmap Cross-cutting concerns 约定 `./mvnw test -pl nop-stream -am -T 1C` 全绿

## Goals

- 整改收口验证：05-20 §2 cep 主体侧（CepOperator 与已删 runtime 侧的重复关系收口、state 初始化问题现状）+ 06-30 cep 相关发现（seed 清单 + 派生规则所得项）逐项核对 live 状态（landed / partial / regressed），每项有证据指针；2300-2 cep 侧修复点 live 存在性复核
- 产品化新增审计：D-GAP item 9「无额外重点」结论显式记录（对照勾销清单无遗漏）+ NFA 状态机（nfa/ + nfa/aftermatch/ 跳过策略）、SharedBuffer（nfa/sharedbuffer/ 引用计数与锁语义）、模式编译（pattern/ + nfa/compiler/ + model/builder/）核心路径的重复代码/错误处理一致性/边界条件审计 + `MalformedPatternException` 为代表的异常层级 fail-fast 一致性 + hollow scan（含人工补审多行 UOE）+ 测试覆盖抽查（主要包各 ≥2 代表性测试 + 行数 top-5 文件直接覆盖）+ `_gen` 生成纪律核验（4 文件无手改痕迹、源自模型）
- 缺陷处置：单 plan 范围内可收敛的小缺陷就地修复（每个修复配 focused 回归测试）；跨模块/大规模缺陷转 roadmap Follow-up 工作项（编号顺延、来源标注本 plan）。小/大缺陷判定准则与 items 7/8 一致：修复限于 cep 模块内、不改公共契约、无需新测试基建 → 小缺陷；否则 → 大缺陷转 Follow-up
- 审计报告落地 `ai-dev/analysis/{执行当月}/`（遵循 `00-analysis-writing-guide.md`；结构对齐 items 7/8 报告）并完成 roadmap item 9 写回

## Non-Goals

- 跨模块重构（需 Follow-up 立项）
- 修复位于 core/runtime 但影响 CEP 路径的发现（如 windowing 集成）——只记录并路由（items 7/8 已裁定的结论或 Follow-up）
- 实施 D-GAP go 裁定项的功能开发（本 plan 只审计与提供证据；D-GAP 对 cep 无 go 项）
- cep 15 个 test 文件通配符导入清理——路由 Follow-up item 22（跨模块统一 sweep）
- 重复裁定 Flink/Beam 8 格补评（item 7 报告 §2.2 已一次落定）、重核空壳模块结论（item 7 §1.1 §7 行已落定）
- hollow scan 工具增强（多行 UOE 检测扩大）——仓库级统一决策事项，本 plan 仅人工补审
- connectors/rocksdb/flow/fraud-example 模块审计（items 10/11）
- 2026-05-20/2026-06-30 报告正文的历史重写

## Scope

### In Scope

- 审计与报告：`nop-stream/nop-stream-cep/` 全模块 + 新增审计报告一份（`ai-dev/analysis/{执行当月}/`，命名遵循 writing guide）
- 修复：cep 模块内小缺陷（含 focused 测试）
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（仅 Follow-up 追加 + Last updated + closure 后 item 9 写回）
- 修改（条件性）：`ai-dev/tools/scan-hollow-implementations.mjs` 检测规则——仅当 hollow scan 误报经核实源于工具检测缺陷时允许修正（误报处置路径的载体；多行 UOE 盲区扩检测不在范围内）
- 修改（条件性）：受修复影响的最小 owner-doc 同步（见 Phase 3；`ai-dev/design/nop-stream/cep-design.md` 若因修复产生事实漂移，仅做最小事实同步，不扩写、不重构）
- 只读输入：两份历史审计报告、2300-2 plan、D-GAP 报告、items 7/8 审计报告（方法论/报告结构复用 + 引用其落定结论）、`~/sources` 竞品源码（flink cep 实现如需对照）

### Out Of Scope

- 其他 9 个子模块的审计与修复
- `ai-dev/design/nop-stream/` 的结构性重写与历史回写
- CEP 新功能开发（新 pattern 语义、迭代检测等）

## Execution Plan

### Phase 1 - 历史审计整改收口验证

Status: completed
Targets: `nop-stream/nop-stream-cep/`、审计报告 Phase 1 章节

- Item Types: `Proof`

- [x] 05-20 §2 cep 主体侧核对：`CepOperator` 现状（生产/测试引用、state 初始化问题是否已修复、与已删除 runtime 侧的重复是否随之归零），附源码锚点或 absence 证据；runtime 侧四文件清除结论引用 item 8 报告 §1.1（不重复核对）→ 报告 §1.1（生产接线 PatternStreamBuilder:146-160 + fraud-example NFA 消费；state 初始化 open():259-379 完整修复；重复归零 + find 抽查零命中）
- [x] 06-30 cep 相关发现逐项核对（seed 清单见 Current Baseline + 「涉及文件位于 nop-stream-cep」派生规则，执行时先落完整核对表再逐项核对）：landed / partial / regressed 三态 + 证据指针；位于其他模块的发现按跨模块规则记录并路由 → 报告 §1.2 完整核对表 10 行（9 landed/unchanged + 1 partial 路由 item 22，无 regressed，无未核对空行）
- [x] 复核 2300-2 cep 侧修复点 live 存在性（SharedBufferAccessor.releaseNode null 分支 pop 修复 + 两个回归测试），并确认 2300-1/2300-3 无 cep 侧修复点（Targets 逐条核对）→ 报告 §1.3（三点全 live 成立 + 同族路径复查 + 2300-1/3 不适用确认）
- [x] partial/regressed 项归类：按小/大缺陷判定准则进 Phase 3 或 Follow-up 候选，逐项记录理由 → 唯一 partial（test 通配符）路由 Follow-up item 22（报告 §1.2 #6）；无 regressed

Exit Criteria:

- [x] 报告 Phase 1 章节含 05-20 §2 cep 主体侧核对结论 + 06-30 cep 发现完整核对表（先列全表再逐项核对，每行三态 + 证据指针，无「未核对」空行；跨模块路由记录在表外显式）+ 2300-2 cep 侧复核结论（成立/不成立 + 抽查点）→ 报告 §1.1/§1.2/§1.3
- [x] 全部 partial/regressed 项有归类结论（修复进 Phase 3 或 Follow-up 候选，附理由）
- [x] No owner-doc update required（纯核验章节）
- [x] No new test required: 纯核验章节，无代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 产品化视角新增审计

Status: completed
Targets: `nop-stream/nop-stream-cep/`、审计报告 Phase 2 章节

- Item Types: `Proof`

- [x] 消化 D-GAP 报告 §3.1 item 9 条目：建立勾销清单，「无额外重点，按既有审计模式执行」结论显式写入报告（对照 D-GAP item 9 行确认无遗漏条目）→ 报告 §2.1（单条结论行 + MalformedPatternException 并入 B 维度执行：21 处 throw 全带 ERR_CEP_MALFORMED_PATTERN + ARG_PATTERN_DETAIL）
- [x] 核心路径优雅性/可靠性审计：NFA 状态机（含 aftermatch 跳过策略家族）、SharedBuffer/SharedBufferAccessor（引用计数正确性、锁与并发语义、2300-2 修复点的同族路径复查）、模式编译链（pattern/ API → nfa/compiler → model/builder，含 `_gen` 模型类）的重复代码复查（对照 05-20 方法论）、错误处理一致性（fail-fast vs 吞异常；`MalformedPatternException` 异常层级与 core StreamException 体系的关系一致性）、边界条件（事件时间乱序/timer 语义）→ 报告 §2.2（双 explore subagent 初审 + 执行者逐条复核：验证干净 8 维度 + 采信缺陷 8 项 + watch-only 14 组 + 否决 2 项）
- [x] 运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-cep --severity high`，high/critical 发现逐条核实（真实空壳 vs 误报），并人工补审多行 UOE（行级正则盲区，沿 items 7/8 同一人工补审基线）；误报处置路径：修正代码模式或修正工具检测规则——「仅记录不改」不合法（退出码 0 为不可降级硬门禁）→ 报告 §2.3（exit 0，无 high/critical 发现、无误报、无需处置动作；多行 UOE + 空方法体人工补审完成，唯一空方法体 W-5 为零调用方死接口合规）
- [x] 测试覆盖抽查：cep 主要包（nfa、nfa/sharedbuffer、nfa/compiler、pattern、operator、functions）各抽 ≥2 个代表性测试命中确认 + 按行数 top-5 文件的直接测试覆盖检查；明显缺口记录（`functions` 包 live 无专属测试文件——该包抽查结论按缺口记录处理而非抽查失败）→ 报告 §2.4（5 包命中 + functions 缺口记录 + top-5 全覆盖表）
- [x] `_gen` 生成纪律核验：4 个 `_gen` 文件与模型源（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/pattern.xdef`，见 `_gen` 文件头注释披露；cep 模块内无 `.xmeta`）的派生关系存在、无手改痕迹（与生成物重新生成的 diff 一致性或等效证据）→ 报告 §2.5（头注释披露 + xdef live + git 历史全为 codegen 提交的等效证据）

Exit Criteria:

- [x] D-GAP item 9 勾销清单落地（「无额外重点」结论显式记录 + 对照 D-GAP 报告 item 9 条目无遗漏）
- [x] hollow scan 退出码记录 + high/critical 发现逐条核实结论 + 多行 UOE 人工补审结论（真实发现进 Phase 3 或 Follow-up；误报处置动作已执行或已按缺陷流程进入 Phase 3，非仅记录）
- [x] 核心路径审计发现逐条带源码锚点（含严重度与处置归类）
- [x] 测试覆盖抽查结论表落地（每包 ≥2 代表性测试命中情况 + top-5 文件覆盖结论）
- [x] `_gen` 生成纪律核验结论落地（4 文件派生关系 + 无手改证据）
- [x] No owner-doc update required（发现记录在报告；owner doc 变更属 Phase 3 修复的附带裁定）
- [x] No new test required: 纯审计章节，无代码变更（Phase 3 修复的测试要求在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 缺陷处置与收口

Status: completed
Targets: `nop-stream/nop-stream-cep/`、`ai-dev/backlog/nop-stream-productization-roadmap.md`、审计报告收口章节

- Item Types: `Fix | Follow-up`

- [x] 就地修复全部归类为小缺陷的项：每个修复附 focused 回归测试（验证正确结果而非仅无异常）；修复不引入空壳/静默跳过；修复若落在 `_` 前缀生成文件，上移到源模型/模板层处理（生成文件禁改）→ 报告 §3.1：CE-0a/0b/0c（前置会话断点三件套）+ CE-1..CE-8（8 项审计缺陷）；行为修复 CE-1/CE-2/CE-4/CE-5 配 TestPatternAuditFixes（8 用例）+ TestSharedBufferAuditFixes（2 用例），CE-1 经 fix-revert 验证（禁用守卫 5/8 FAIL）；CE-3 防御守卫触发路径被公共 API 排除（无独立行为测试可行，注释说明 + 全集 359 回归，沿 items 7/8 先例）；CE-6/CE-7/CE-8 为纯日志/死代码/文档级（No new test required per guide Rule #25，显式豁免注明）；无修复落在 `_` 前缀文件
- [x] 大缺陷逐项转为 roadmap Follow-up 工作项：按 Rules 追加到 Work Items 末尾（编号顺延、状态 todo、来源标注本 plan + 证据锚点），并更新 Last updated（或显式记录「无大缺陷」）→ 报告 §3.2：**显式无大缺陷**（全部采信项满足小缺陷准则；W-1..W-14 watch-only residual 逐条附 Why Not Blocking；roadmap 无 Follow-up 追加，既有 item 22 承接 cep test 通配符）
- [x] 触及 CEP 匹配/状态语义的修复：至少一条从 `CEP.pattern(...)` 公共 API 到匹配输出的端到端验证（既有 TestCepPublicApiE2E 家族扩展或新增），并确认 SharedBuffer 相关修复不破坏既有 CEP 测试全集（06-30 时点 272 @Test，live 计数以执行时为准）→ TestCepPublicApiE2E 修复并强化（CEP.pattern 入口 → followedByAny 分支 → 恰 2 匹配输出 → 远水位后 SharedBuffer 零残留双断言，5/5 绿）；live 全集 323→修复后 359 @Test（+10 新 −2 scratch），0 failures
- [x] 全量回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿；`node ai-dev/tools/check-nop-stream-invariants.mjs` 通过（CI fail-fast 门禁）→ 全模块 BUILD SUCCESS（EXIT=0）+ invariants exit 0（CE-0c 重钉后）
- [x] 审计报告 Status 定稿（resolved + 遗留清单），含全部发现→处置零丢失映射表（结构对齐 items 7/8 报告）→ 报告 Status: resolved；§2.2 三表（采信 8 + watch-only 14 + 否决 2）+ Phase 1 三表 + §2.1/§2.3/§2.4/§2.5 全发现→处置映射
- [x] 小缺陷修复若改变 live 行为且 owner doc（cep-design.md）受影响：同步最小 owner-doc 更新或显式记录 `No owner-doc update required` → 报告 §3.3：No owner-doc update required（全部为模块内 fail-fast 加固与内部收敛，CE-1/CE-2 为新增前置校验不改合法输入契约；cep-design.md 无事实漂移）

Exit Criteria:

- [x] 全部 in-scope 小缺陷修复落地且各有 focused 测试（新增测试清单列明：每个测试验证了哪个新行为；纯清理类修复按 guide Rule #25 显式豁免并注明）
- [x] 全部大缺陷已追加为 roadmap Follow-up 工作项（或显式记录「无大缺陷」）
- [x] **端到端验证**（如适用，触及匹配语义时）：`CEP.pattern()` 入口到匹配输出的完整路径测试存在且通过
- [x] **无静默跳过**：修复代码无空方法体/吞异常模式（hollow scan 复跑退出码 0）
- [x] 修复改变 live 行为时：受影响 owner doc 已做最小事实同步，或显式记录 `No owner-doc update required`（含理由）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（报告新增后）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 2026-05-20 §2（cep 主体侧）与 2026-06-30（seed 清单 + 派生规则所得项）整改收口核对完成，无未处置的 partial/regressed 项；跨模块路由项有明确去向
- [ ] 2300-2 cep 侧修复点复核结论落地；D-GAP item 9「无额外重点」勾销结论落地（对照清单无遗漏）
- [ ] 空壳扫描 high/critical 无未处置真实发现（误报已按处置路径消除，非仅记录）
- [ ] 小缺陷修复全部带 focused 测试；大缺陷全部 Follow-up 化（或显式无）
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] `./mvnw compile`（随 `-pl nop-stream -am` 构建覆盖）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [ ] checkstyle / 代码规范检查通过（随 mvnw 构建；root pom checkstyle 配置整体注释的事实沿 items 7/8 先例按现状通过）
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-cep --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] **Anti-Hollow Check**：closure audit 已验证修复组件被既有调用链在运行时确实调用、无空方法体/静默跳过/no-op 作为正常实现
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] roadmap item 9 状态写回（closure audit 通过后）

## Deferred But Adjudicated

（执行中按需追加：仅允许 `watch-only residual | optimization candidate | out-of-scope improvement` 三类，逐条附 Why Not Blocking Closure）

## Non-Blocking Follow-ups

（执行中按需追加）

## Closure

Status Note: （完成或关闭时填写：为什么这个 plan 可以关闭）
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<每条 Exit Criterion 与 Closure Gate 的验证结果 + 门禁退出码>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
