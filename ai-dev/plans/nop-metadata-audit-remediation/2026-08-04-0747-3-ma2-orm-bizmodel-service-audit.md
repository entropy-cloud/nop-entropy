# MA2 ORM/BizModel/服务层审计（dao + meta + service）

> Plan Status: active
> Last Reviewed: 2026-08-04
> Draft Review: 3 轮独立子 agent 对抗性审查通过（1 Blocker + 3 Major + 4 Minor 全部修复；final round 无 Blocker/Major）。Session: ses_035f7f6a9ffekamsvbN2Pk4FYC / ses_035f01a1effeyUoJVChA28OIYM / ses_035e90415ffePF1hTXAhQbyYkg。
> Mission: nop-metadata-audit-remediation
> Work Item: MA2（2.1 ORM 模型 / 2.2 生成管线 / 2.3 BizModel / 2.4 IoC）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MA2 里程碑）、`ai-dev/skills/deep-audit-prompts.md`（维度 05/07/08）、`ai-dev/skills/orm-model-audit-prompt.md`
> Related: 执行顺序 `{3}` of 3 — 硬前置：M0（0.1-0.4）全部 done，以 M0.3 未闭包清单作为历史发现对照输入，以 M0.4 结果为基线；产物（P0/P1 发现）是 MR1 批量修复的输入。

## Purpose

对 nop-metadata 的 ORM 模型（39 实体）、生成管线、BizModel（service 全模块 41 处 @BizModel）与 IoC 配置执行审计（roadmap MA2 的 4 个工作项），产出审计报告并更新 arm-index，为 MR1 批量修复提供输入。

## Current Baseline

经 2026-08-04 live repo 核对（引用均与 roadmap 一致，已二次确认文件与计数）：

- `nop-metadata/model/nop-metadata.orm.xml` 39 实体（`grep -c "<entity"` = 39）
- @BizModel 计数按范围分层：`service/entity/` 下 39 文件、service main 全量 41 处注解（含 `quality/MetaQualityCheckpointScheduler`、`search/NopMetaSearchBizModel` 等 entity/ 外文件）、含测试文件 42（roadmap 记 42 对应含测试口径）。**Phase 3 审计范围 = service main 全量 41**（`grep -rl "@BizModel" nop-metadata/nop-metadata-service/src/main/java/`），覆盖 entity/ 外的 search/、quality/；`dao/biz/` 下 I*Biz 接口（无 @BizModel 注解，为独立核对项，不计入 41）
- `nop-metadata/nop-metadata-dao/src/main/java` 120 main 文件；`nop-metadata-service` 128 main + 94 test 文件
- 历史审计基线（9 个来源，multi+open 双轨）：07-19/07-20-1554/07-20-1816/07-21-2039/07-23-0714
- **07-23 文档内部矛盾已确认**：`summary.md` 的关键发现列表（维度04-01 双FK互斥、04-02 wf/approve-status dict、04-03 entityTableId；07-01 delete 缺 @Name、07-02 requireEntity 绕过、07-03 BeanContainer、07-04 命名规范）**不出现在**分维度报告 `04-orm-model.md`（04-001..007）与 `07-bizmodel-conformance.md`（07-001..008）中，ID 体系不同（04-01 vs 04-001）。**对照清单以 `arm-unclosed-findings-nop-metadata.md`（M0.3 产出，轮次限定 ID）为权威来源**，summary 独有的条目（04-01/02/03、07-01 等）列为额外核对项，历史文件矛盾在报告中记录不仲裁
- 07-23 维度 05 `05-codegen-pipeline.md` 实际 11 个 finding（9 信息性 + 05-08 P2 + 05-11 P3）；summary 表记"13(含8信息性)"——**以分维度报告 11 为准**，差异在报告中记录
- 2026-07-31 抽查验证：I*Biz 接口已补齐（executeReconciliation/confirmMatch/batchConfirmMatches 已在接口声明）；MetaAggregationExecutor 3468→264 行已拆 Processor
- 已 completed 修复 plan：`18-nop-metadata-orm-model-polish.md`、`17-nop-metadata-bizmodel-compliance-remediation.md`、`09-nop-metadata-orm-data-integrity.md`（ORM 相关修复已 landing；07-23 的部分 finding 可能已被这些 plan 修复，核对时需区分"已修复"与"待复核"）
- `docs-for-ai/02-core-guides/orm-model-design.md` 存在（ORM 审计参考基线）
- 测试基线：833+ tests / 0 failures（2026-07-23 记录），M0.4 将刷新基线

## Goals

- 产出 MA2 审计报告（4 份：2.1 ORM / 2.2 生成管线 / 2.3 BizModel / 2.4 IoC），发现一律使用轮次限定 ID 或 `P<级别>-<里程碑>-<序号>`
- 每个发现标注 P 级 + 修复归属（MR1/MR2/即时通道/非阻塞）
- 更新 arm-index-nop-metadata.md 报告清单与 P0/P1 追踪
- 无 P0 时保持绿色基线；发现 P0 走即时通道

## Non-Goals

- 不修复审计发现（修复归 MR1 批量修复，P0 例外走即时通道）
- 不审计运行时/安全/工程质量（MA3/MA4 承接）
- 不改任何 `src/` 代码或 `model/*.orm.xml`（纯审计计划）

## Scope

### In Scope

- 2.1 ORM 模型与实体设计审计（orm-model-audit-prompt.md + 补充清单）：39 实体字段类型、关系、cascade-delete、域使用、displayName、dict 跨模块引用（prompt 未覆盖 displayName 检查项与完整 cascade-delete 判定，执行时以补充清单补齐）
- 2.2 生成管线完整性审计（维度 05）：model→codegen→dao→meta→service→web 生成链路一致性回归（39:39:39:39）
- 2.3 BizModel 规范遵循审计（维度 07）：service main 全量 41 处 @BizModel（entity/ 39 + search/quality 等 2）注解、继承、setEntityName、requireEntity 数据鉴权、delete 覆盖 @Name；含 entity/ 目录外的对照项（NopMetaSearchBizModel、I*Biz 接口）
- 2.4 IoC 与 Bean 配置审计（维度 08）：beans.xml 注入方式（无 private 字段注入）、生成文件边界、CRUD 生成禁用意图复核
- 审计报告（`ai-dev/audits/YYYY-MM-DD-HHMM-arm-MA2.<n>-nop-metadata-<dimension>.md`）+ arm-index 更新

### Out Of Scope

- MA1/MA3-MA7 审计（后续计划）
- 任何修复（MR1/MR2/MR3 承接）
- `docs-for-ai/` 文档修改（MA5 覆盖；审计发现记录为 finding 即可）

## Execution Plan

### Phase 1 - MA2.1 ORM 模型与实体设计审计

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`

- Item Types: `Proof`

- [ ] 执行 orm-model-audit-prompt.md 审计 7 维度（类型/长度/字典/标准字段/关系/跨模块引用/命名）+ 补充清单（displayName 完整性、cascade-delete 头-行关系判定）
- [ ] 历史对照：以 M0.3 未闭包清单中维度 04 相关条目逐一核对（07-23 报告 04-001..007 + summary 独有 04-01/02/03；07-20-1554 维度 04 = 4 发现、07-20-1816 维度 04 = 11 发现）；已由 plan 09/17/18 修复的项记 fixed，不得重复报告
- [ ] 产出审计报告 `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA2.1-nop-metadata-orm-model.md`

Exit Criteria:

- [ ] 报告包含：39 实体建模合规性逐项核对（字段类型/关系/cascade-delete/域/displayName/dict 跨模块引用）
- [ ] 07-23 维度 04 对照完整（报告 04-001..007 + summary 独有项），07-20 双报告均核对（1554 = 4 发现 / 1816 = 11 发现）；历史矛盾在"对照说明"段记录
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`（审计报告为证据层）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MA2.2 生成管线完整性审计

Status: planned
Targets: `nop-metadata/model/` + `_gen/` 产物目录

- Item Types: `Proof`

- [ ] 执行维度 05 审计（生成管线完整性）：model→codegen→dao→meta→service→web 生成链路一致性回归（39:39:39:39）
- [ ] 运行 `find nop-metadata \( -name "_*.xml" -o -name "_*.java" \) -not -path "*/target/*"` 获取生成产物清单基线（注意 `-o` 需括号分组，否则排除只作用于 `_*.java` 分支；live 结果：127 命中中 43 个在 target/，剔除后 84）
- [ ] 历史对照：以 M0.3 未闭包清单中维度 05 条目核对，**以分维度报告 11 个 finding 为准**（summary"13"为错误口径，记录差异）
- [ ] 产出审计报告 `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA2.2-nop-metadata-pipeline.md`

Exit Criteria:

- [ ] 报告包含：生成链路各层实体/BizModel 计数一致性结论、`_gen/` 产物与模型同步性结论（基线含排除 target 的说明）
- [ ] 07-23 维度 05 的 11 个 finding 逐一对照并给出复核结论；差异口径（13 vs 11）已记录
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - MA2.3 BizModel 规范遵循审计

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/`（`grep -rl "@BizModel"` 全量 41 处，含 entity/、search/、quality/）

- Item Types: `Proof`

- [ ] 执行维度 07 审计（BizModel 规范遵循）：service main 全量 41 处 @BizModel 注解、继承、setEntityName、requireEntity 数据鉴权、delete 覆盖 @Name
- [ ] 历史对照：以 M0.3 未闭包清单中维度 07 条目核对（07-23 报告 07-001..008 + summary 独有 07-01 delete @Name / 07-02 requireEntity / 07-03 BeanContainer / 07-04 命名规范）；已由 plan 17 修复的项记 fixed
- [ ] 产出审计报告 `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA2.3-nop-metadata-bizmodel.md`

Exit Criteria:

- [ ] 报告包含：41 处 @BizModel 规范遵循逐项核对（含 entity/ 外文件：search/NopMetaSearchBizModel、quality/MetaQualityCheckpointScheduler、dao/biz/ I*Biz 接口）
- [ ] 07-23 维度 07 对照完整（报告 + summary 独有项）；计数口径（39/41/42）在报告中记录
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - MA2.4 IoC 与 Bean 配置审计

Status: planned
Targets: `nop-metadata/*/src/main/resources/_vfs/**/*.beans.xml`

- Item Types: `Proof`

- [ ] 执行维度 08 审计（IoC 与 Bean 配置）：beans.xml 注入方式（无 private 字段注入扫描）、生成文件边界、CRUD 生成禁用意图复核
- [ ] 历史对照：以 M0.3 未闭包清单中维度 08 相关条目核对（07-20-1554 `08-ioc-beans.md` 的 08-01 xmlns:ioc 缺失 P2、07-21-2039 维度 08 正向确认 08-01..07）
- [ ] 产出审计报告 `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA2.4-nop-metadata-ioc.md`

Exit Criteria:

- [ ] 报告包含：beans.xml 注入方式扫描（private 字段注入清单）、生成文件边界核对、CRUD 生成禁用意图复核结论
- [ ] 07-20-1554 与 07-21-2039 维度 08 条目逐一对照并给出复核结论
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 纯审计计划（不改代码），构建验证以绿色基线保持为准。

- [ ] 4 份 MA2 审计报告全部产出且含 P 级标注 + 修复归属
- [ ] 以 M0.3 未闭包清单为对照源，维度 04/05/07/08 相关条目逐一核对（07-23 + 07-20-1554 + 07-20-1816 + 07-21-2039 + 07-19-1118），无遗漏；summary 独有项已覆盖；历史文件矛盾已在报告中记录
- [ ] arm-index-nop-metadata.md 报告清单 + P0/P1 追踪已更新
- [ ] P0 发现已走即时通道（若存在）；P1 发现已归入 MR1 修复清单
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步，或明确写明 `No owner-doc update required`
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证报告非空壳（有实际发现清单与可追溯引用，非模板占位）
- [ ] `./mvnw compile -pl nop-metadata -am`（绿色基线保持验证）
- [ ] `./mvnw test -pl nop-metadata -am`（绿色基线保持验证）
- [ ] checkstyle / 代码规范检查通过（无代码变更，以 mvn 默认检查为准）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（修改 `ai-dev/` 下文件后执行）

## Deferred But Adjudicated

### MA2 P2/P3 finding 的修复

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 规则 1 明示本 roadmap 只处理 P0/P1；P2/P3 记录为 deferred successor，由后续批次另行规划。
- Successor Required: `no`（后续批次另行规划）

## Non-Blocking Follow-ups

- 若审计发现 ORM 模型问题需改 `model/*.orm.xml`，修复归 MR1（model-first 流程，遵循 AGENTS.md 生成管线约束）
- @BizModel 计数口径（39/41/42）差异在 MA4.3 测试覆盖审计中一并核实，本 plan 已按范围分层记录

## Closure

Status Note: 未关闭
Completed: 

Closure Audit Evidence:

- Reviewer / Agent: 待独立子 agent 执行

Follow-up:

- 待 closure audit 后填写
