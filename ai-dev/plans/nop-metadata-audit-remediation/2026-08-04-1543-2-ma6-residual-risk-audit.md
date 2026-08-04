# MA6 残留风险审计专项（全模块）

> Plan Status: active
> Last Reviewed: 2026-08-04
> Draft Review: 3 轮独立子 agent 对抗性审查通过（第 1 轮 1 Blocker + 6 Minor 全部修复；第 2 轮 1 Major（扫描阳性对照）+ 3 Minor 全部修复；第 3 轮最终验证 0 Blocker / 0 Major / 3 Minor，其中 3 Minor 全部修复，裁定可执行）。Session: ses_034422f74ffeZTZqAZUGsU4mIM / ses_034397f1bffeSatluounSeuuiR / ses_03430426affeQ1s8c6hoJKwe5z。
> Mission: nop-metadata-audit-remediation
> Work Item: MA6（6.1 空壳实现扫描 / 6.2 静默跳过检测 / 6.3 接线完整性验证 / 6.4 敏感信息泄露扫描 / 6.5 测试隔离性审查 / 6.6 既有修复验证）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MA6 里程碑）、`ai-dev/skills/open-ended-adversarial-review-prompt.md`、`ai-dev/skills/closure-audit-prompt.md`、`ai-dev/audits/arm-unclosed-findings-nop-metadata.md`（M0.3，6.6 输入）
> Related: 执行顺序 `{2}` of 3 — 硬前置：M0（0.1-0.4）全部 done（含 M0.4 绿色基线 813/0）、MA1-MA4 全部 done、MR1 completed；MA3.4 初步复核的 `2026-07-20-1554#RACE` 终局定论归本计划 6.6；产物（P0/P1 发现）是 MR3 批量修复的输入；本计划为纯审计零代码变更。

## Purpose

对 nop-metadata 全模块执行残留风险专项审计（roadmap MA6 的 6 个工作项：空壳实现、静默跳过、接线完整性、敏感信息泄露、测试隔离性、既有修复验证），覆盖 07-23 自评盲区之外的对抗性审查维度，产出审计报告并更新 arm-index，为 MR3 批量修复提供输入；其中 6.6 对 2026-07-19~07-23 历史审计中已标修复项做修复到位性抽样核验，并对 RACE 竞态项给出终局定论。

## Current Baseline

经 2026-08-04 live repo 核对（引用均与 roadmap 一致，已二次确认文件与计数）：

- roadmap MA6 六行（6.1-6.6）状态为 `todo`；Deps（0.4 done）已满足；MA1-MA4 全部 completed（MA4.1 错误处理报告 / MA4.6+4.7 测试有效性报告为本计划交叉对照源）
- **已知空壳/静默跳过类 finding（live 已确认，归 MR2，本计划验证与补充，不重复修复）**：
  - P1-MA4-601：6 个 AggregationProcessor 测试类 18 个空壳测试（instanceof/canInstantiate/NPE-on-null，execute() 改错仍通过）——MA6.1/6.2 做全量扫描确认无同类遗漏
  - P2-MA4-301：3 个 JOIN AggregationProcessor 单元层空壳（execute() 零保护，与 601 同源）
  - P2-MA4-001/002：静默吞异常 2 处（NopMetaTagLabelBizModel:106-112 getWfNameFromMeta catch-all 无日志、SqlViewFieldTypeInferrer:198-202 safeProductName 唯一无日志实现）——MA6.2 全量扫描对照
  - 07-23 维度09 家族（**行号以 MA4.1 报告修正值为准**：MetaQualityRuleExecutor catch 600/607、MetaDataSourceConnectionProcessor 311-316、CheckpointActionDispatcher 323-328；**AggregationContext.safeProductName 经 MA4.1 复核确认不存在，不列入抽查对象**）——MA4.1 已复核登记，MA6.2 抽查确认
- **6.6 输入（M0.3 未闭包清单 + MA3/MA4 复核登记）**：
  - 已修复项抽样核验对象：P0 三连（AR-01 schemaPattern 注入 / AR-02 JDBC 白名单 / 11-01 搜索 xmeta）、dao→core 依赖（c3162d4da）、I*Biz 接口补齐、xmlns:ioc、MetaAggregationExecutor 拆分（3468→264 行）、16-09 Thread.sleep 修复（**MA4.7 已确认**，MA4.7:140-142,166）、P1-MA1-001 xmeta 修复（MR1）、**07-003 的 DataSource/DataContract 侧已改 requireEntity（live 核实）、16-04 存在性闭包（activateContract/deprecateContract/retireContract live 零命中已移除）、MR1 R1.2/R1.3（P2-MA2-01/02/03 显式 to-many + 保留字改名）**——核验方式 = 抽样 + 回归测试存在性（roadmap 6.6 定义）；抽样矩阵以本清单为基线，避免系统性偏向
  - `2026-07-20-1554#RACE`（upsertExternalTable 读-写竞态）：MA3.4 初步复核完成（UK_NOP_META_TABLE_MODULE_NAME 已阻止并发重复；无 catch-duplicate+re-read 非幂等；新增 P2-MA3-03 schema 维度未进 UK 归 MR2）——**终局定论归本计划 6.6**
  - `2026-07-23-0714#维度20-01`（currentTimeMillis 残余 2 处）→ MR2 机械修复 + MA6.6 复核
  - `2026-07-23-0714#维度16-07`（data-auth 测试只验证 XML 结构不验证框架强制）→ MA6.6 审计
- **工具（live 存在）**：`ai-dev/tools/scan-hollow-implementations.mjs`（6.1 自动化扫描入口）。**⚠️ 调用方式（live 实测）**：工具 `--module` 按 `repoRoot/<module>` 拼路径且不支持 glob——顶层 `nop-metadata-api/core/dao/service` 目录不存在（模块实际在 `nop-metadata/` 下），逐模块调用**必须用嵌套路径**（如 `--module nop-metadata/nop-metadata-service`）或**整组一次调用 `--module nop-metadata`**（实测有效：8 子模块，`--severity high` = 0 high / `--severity info` 桶 = 1 low + 111 medium（工具无 info 级 pattern，0 high 属正常期望结果）；**无效路径静默返回空 + exit 0，与"真干净"形态不可区分**——因此 Phase 1 必须记录阳性对照证据（见 Phase 1 执行项）；**MR1 closure 记录的同款 `--module nop-metadata-service` 运行为空转（路径无效，0 findings 不可采信），本计划不得引用其作为工具验证证据**
- **接线面（6.3 对象）**：`nop-metadata/*/src/main/resources/_vfs/**/*.beans.xml`（MA2.4 已审计 IoC 配置本身；6.3 聚焦 bean class 存在性 / @Inject 目标 bean 可解析 / 生成文件与手写文件边界 / 组件间运行时调用链连通性，不重复审计配置规范）
- **测试隔离面（6.5 对象）**：live 测试文件 100 个（整组 `find nop-metadata -path "*/src/test/java/*.java" -not -path "*/target/*"`；service 单模块 97；MA4.3+4.4 审计口径 94 与 live 有演化差，以 live 实测为准）；已知登记：16-05 并发测试无共享状态验证（TestCheckpointActionDispatcherConcurrency）、MockMessageService static 状态（**MA4.7 登记 P3-MA4-705，MockMessageService.java:31-37**）
- 审计方式约束（沿用 MA1-MA4 先例）：纯审计零代码变更；发现一律使用轮次限定 ID 或 `P<级别>-<里程碑>-<序号>` 并标注修复归属
- **执行顺序约束（防批次串扰）**：本 plan（`{2}`）执行时 MR2（`{3}`）必须未启动——6.2/6.6 对 P2-MA4-001/002、20-01、P1-MA4-601 等的"修复前现状/交叉核对"以 MR2 未改代码为前提；若 MR2 已先行启动，相关条目改为复核 MR2 修复后状态并注明
- 绿色基线：813 tests / 0 failures（M0.4，2026-08-04 实测；范围 `-pl nop-metadata -am -T 1C`）

## Goals

- 产出 MA6 审计报告（6 份：6.1-6.6），每份含 P 级标注 + 修复归属 + 可追溯 file:line
- 6.6 对历史已修复项抽样核验到位性 + 回归测试存在性；RACE 终局定论（维持/升级/降级 + 依据）
- 更新 arm-index-nop-metadata.md 报告清单与 P0/P1 追踪；roadmap 6.1-6.6 → done
- 无 P0 时保持绿色基线；发现 P0 走即时通道

## Non-Goals

- 不修复审计发现（修复归 MR2/MR3 批量修复，P0 例外走即时通道；MR2 已归属项如 P2-MA4-301 只在报告中交叉引用）
- 不重新审计 IoC 配置规范（MA2.4 已覆盖）、域特有安全深挖（MA7 承接）
- 不改任何 `src/` 代码或测试（纯审计计划）
- 不处理 P3（deferred successor，roadmap 规则 1）

## Scope

### In Scope

- 6.1 空壳实现扫描（H01）：接口有声明无实现、空方法体、`throw UnsupportedOperationException` 作为正常路径；入口 = `scan-hollow-implementations.mjs --module nop-metadata --severity high`（整组目录，工具不支持 glob）+ 对抗性人工扫描
- 6.2 静默跳过检测（H02）：空 catch、catch-and-swallow、catch→return null 类模式（MetaQualityScorer 先例）、条件不满足静默返回、`// TODO`/`// FIXME` 标记当作完成
- 6.3 接线完整性验证（H03）：beans.xml bean class 存在性、@Inject 对应 bean 可解析、生成文件与手写文件边界、组件间运行时调用链连通性（从入口点追踪到出口点）
- 6.4 敏感信息泄露扫描（H05）：JDBC URL/密码/令牌/API Key 在日志、错误消息、配置、测试夹具中泄露；连接串脱敏链路回归核对（凭据管理深挖归 MA7.2，本项只做泄露面扫描）
- 6.5 测试隔离性审查（H06）：测试间共享状态、静态字段污染、并发测试共享状态（16-05 复核）、MockMessageService static 状态（MA4.7 登记）
- 6.6 既有修复验证（H07）：对 M0.3 清单与 MA1-MA4 报告中已标修复项抽样核验（fix 到位性 + 回归测试存在性，抽样比例与方法写入报告）；RACE 终局定论；16-07 data-auth 测试有效性裁定
- 审计报告（`ai-dev/audits/2026-08-04-{HHmm}-arm-MA6.<n>-nop-metadata-<dimension>.md`）+ arm-index 更新 + roadmap 6.1-6.6 → done

### Out Of Scope

- MA7 审计（后续计划）
- 任何修复（MR2/MR3 承接）
- `docs-for-ai/` 文档修改（MA5 覆盖；审计发现记录为 finding 即可）

## Execution Plan

### Phase 1 - MA6.1 空壳实现扫描（H01）

Status: planned
Targets: nop-metadata 8 子模块 main Java（~283 文件）

- Item Types: `Proof`

- [ ] **启动门禁核查**：确认 M0/MA1-MA4 已 done、MR1 completed（roadmap 对应行 + arm-index 报告清单）、**MR2 未启动**（执行顺序约束，见 Current Baseline）；未满足则不启动并上报
- [ ] 运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high`（整组 8 子模块，实测有效；**逐模块调用必须用嵌套路径如 `nop-metadata/nop-metadata-service`，顶层模块名不存在且工具不支持 glob**），记录退出码与扫描结果；**阳性对照（强制，防空转伪证）**：同组再跑一次 `--severity info` 并记录文件遍历计数/finding 明细（实测 = 1 low + 111 medium），证明工具真实扫到了文件（空转形态 = 0 findings + exit 0，与真干净不可区分，必须用阳性对照区分）——两者一并作为自动化证据基线写入报告
- [ ] 对抗性人工扫描（`open-ended-adversarial-review-prompt.md`）：接口有声明无实现、空方法体、`throw UnsupportedOperationException` 作为正常路径、stub/placeholder 填充；与 P1-MA4-601/P2-MA4-301 已知空壳交叉核对确认无同类遗漏
- [ ] 产出审计报告 `ai-dev/audits/2026-08-04-{HHmm}-arm-MA6.1-nop-metadata-hollow-scan.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：自动化扫描结果（工具 + 退出码 + 模块范围 + **阳性对照证据（--severity info 运行的 finding 明细/文件遍历计数）**）+ 人工扫描清单 + 空壳发现（file:line + P 级 + 归属）
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`（审计报告为证据层）
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MA6.2 静默跳过检测（H02）

Status: planned
Targets: nop-metadata 8 子模块 main Java（~283 文件）

- Item Types: `Proof`

- [ ] 执行静默跳过检测：空 catch、catch-and-swallow、catch→return null 类模式（MetaQualityScorer 先例）、条件不满足静默返回、`// TODO`/`// FIXME` 标记当作完成；与 P2-MA4-001/002 已知 2 处 + 07-23 维度09 家族登记交叉核对
- [ ] 产出审计报告 `ai-dev/audits/2026-08-04-{HHmm}-arm-MA6.2-nop-metadata-silent-noop.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：静默跳过发现清单（file:line + 模式类型 + P 级 + 归属），含与已知登记项的交叉核对表
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - MA6.3 接线完整性验证（H03）

Status: planned
Targets: `nop-metadata/*/src/main/resources/_vfs/**/*.beans.xml` + main Java 注入面

- Item Types: `Proof`

- [ ] 接线完整性验证：beans.xml bean class 存在性（与 Java 类逐一比对）、@Inject 对应 bean 可解析、生成文件与手写文件边界（`_gen/` 与保留层）、组件间运行时调用链连通性（从 BizModel 入口点追踪到执行器/DAO 出口点，抽查关键链路：query/aggregation/lineage/sqlview/import）
- [ ] 复用 MA2.4（IoC 配置）与 MA3.1（XDSL 解析）结论，只做接线连通性核对，不重复审计配置规范
- [ ] 产出审计报告 `ai-dev/audits/2026-08-04-{HHmm}-arm-MA6.3-nop-metadata-wiring.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：beans.xml class 存在性核对表 + @Inject 解析核对 + 关键链路调用链追踪记录（入口→出口）
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - MA6.4 敏感信息泄露扫描（H05）

Status: planned
Targets: nop-metadata 8 子模块 main + test + 配置文件

- Item Types: `Proof`

- [ ] 敏感信息泄露扫描：JDBC URL/密码/令牌/API Key/连接串在日志、错误消息、配置、测试夹具中的泄露；连接串脱敏链路回归核对（connectionConfig 凭据加密/脱敏实现现状；凭据管理深挖归 MA7.2，本项只做泄露面扫描）
- [ ] 产出审计报告 `ai-dev/audits/2026-08-04-{HHmm}-arm-MA6.4-nop-metadata-sensitive-leak.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：泄露面扫描范围 + 发现清单（file:line + 泄露类型 + P 级 + 归属）+ 脱敏链路核对结论
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - MA6.5 测试隔离性审查（H06）

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/test/`（live 97 个测试文件；整组含 dao/test 等共 100 个）

- Item Types: `Proof`

- [ ] 测试隔离性审查：测试间共享状态、静态字段污染、并发测试共享状态（16-05 TestCheckpointActionDispatcherConcurrency 复核）、MockMessageService static 状态（MA4.7 登记）等；与 MA4.6/4.7 报告交叉核对
- [ ] 产出审计报告 `ai-dev/audits/2026-08-04-{HHmm}-arm-MA6.5-nop-metadata-test-isolation.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：隔离性发现清单（file:line + 模式类型 + P 级 + 归属）+ 16-05/MockMessageService 复核结论
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - MA6.6 既有修复验证（H07）

Status: planned
Targets: `ai-dev/audits/arm-unclosed-findings-nop-metadata.md`（M0.3）+ 已标修复项的 live 代码与测试

- Item Types: `Proof | Decision`

- [ ] 对 M0.3 清单与 MA1-MA4 报告中已标修复项抽样核验：fix 到位性（live 代码验证）+ 回归测试存在性；抽样矩阵以 Current Baseline 6.6 输入清单为基线（覆盖：P0 三连（AR-01/AR-02/11-01）、dao→core 依赖、I*Biz 接口补齐、**xmlns:ioc**、MetaAggregationExecutor 拆分、16-09 Thread.sleep（MA4.7 确认）、P1-MA1-001（MR1）、07-003 DataSource/DataContract 侧、16-04 存在性闭包、MR1 R1.2/R1.3（P2-MA2-01/02/03））；抽样比例与方法写入报告
- [ ] **RACE 终局定论（Decision）**：基于 MA3.4 初步复核（UK 已阻止并发重复；无 catch-duplicate+re-read 非幂等；P2-MA3-03 schema 维度未进 UK 归 MR2）给出终局裁定（维持 watch-only / 升级 / 降级）+ 依据；终局结论登记 arm-index
- [ ] 16-07 data-auth 测试有效性裁定（Decision）：测试只验证 XML 结构不验证框架强制是否有意限制（注释声明），给出维持 watch-only 或要求补强的裁定
- [ ] 维度20-01（currentTimeMillis 2 处）MR2 修复前的现状复核登记
- [ ] 产出审计报告 `ai-dev/audits/2026-08-04-{HHmm}-arm-MA6.6-nop-metadata-fix-verification.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：抽样核验表（每项：fix 到位性 PASS/FAIL + 回归测试存在性 + 证据 file:line）+ RACE 终局定论 + 16-07 裁定 + 20-01 现状登记
- [ ] RACE 终局定论与 16-07 裁定有明确依据记录（Decision）
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 6 份 MA6 审计报告产出（6.1-6.6），每份含 P 级标注 + 修复归属 + 可追溯 file:line 引用
- [ ] arm-index-nop-metadata.md 报告清单 +6 行、P0/P1 追踪更新；roadmap 6.1-6.6 → done
- [ ] RACE 终局定论与 16-07 裁定已登记（arm-index 或报告）
- [ ] 所有 in-scope confirmed live defects / owner-doc drift 均有明确归属（MR3 修复 / watch-only + Why Not Blocking Closure），无静默降级
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（evidence 写入本 plan Closure 段）
- [ ] **Anti-Hollow Check**：closure audit 已验证 6 份报告为真实审计产物（实际发现清单 + 可追溯 file:line + 历史对照），非模板空壳；**6.1 报告含阳性对照证据（--severity info 运行的 finding 明细/文件遍历计数，证明扫描真实执行而非空转伪证）**
- [ ] `./mvnw compile -pl nop-metadata -am -q` 通过（纯审计零代码变更，确认无回归）
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 绿色基线保持（813/0 或重新实测记录）
- [ ] checkstyle / 代码规范检查通过（无代码变更，以 mvn 默认检查为准；历史计划惯例记 "checkstyle N/A"）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（修改 `ai-dev/` 下文件后执行）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时，Minimum Rule #26）

## Deferred But Adjudicated

### MA6 P2/P3 finding 的修复

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 规则 1 明示本 roadmap 只处理 P0/P1；P2/P3 记录为 deferred successor，由后续批次另行规划（同 MA1-MA4 裁定）。
- Successor Required: `no`（后续批次另行规划，非本批 scope）

## Non-Blocking Follow-ups

- watch-only 项复核维持原裁定者，登记结论即可（不产生修复债务）
- 若 6.3 接线验证发现生成文件与手写文件边界违规需要生成管线侧联动修改，作为 MR4 跨维度裁决输入

## Closure

Status Note: <<完成或关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session id>>
- Evidence: <<每条 Exit Criterion 与 Closure Gate 的验证结果；check-plan-checklist 退出码；Anti-Hollow 检查结果；Deferred 项分类检查>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
