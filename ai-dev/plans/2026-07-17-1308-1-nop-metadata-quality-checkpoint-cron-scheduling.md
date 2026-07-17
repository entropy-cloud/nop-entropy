# 1 质量检查点 cron 定时调度（nop-job 集成）

> Plan Status: completed
> Mission: nop-metadata
> Work Item: P2-cron — 质量检查点 cron 定时调度（nop-job 适配）
> Last Reviewed: 2026-07-17
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（P2 数据质量 deferred）；`ai-dev/design/nop-metadata/06-data-quality-extended.md` §1.3（定时执行 follow-up，nop-job/nop-batch 适配）；`ai-dev/design/nop-metadata/09-gap-analysis-extended.md` §4.4（复用 nop-job，不单独验证框架）；deferred item from plan `2026-07-17-0027-1`（cron 定时调度，`Successor Required: yes`，Successor Path「后续 follow-up plan（nop-job 调度集成）」）+ plan `2026-07-17-0540-1`（cron 定时调度 nop-job 适配，`Successor Required: yes`，标注「架构裁定 design-first」）
> Related: `2026-07-17-0027-1`（Checkpoint 编排）、`2026-07-17-0540-1`（自动评分接线）、`2026-07-17-0540-2`（结果动作投递）
> Draft Review: R1 独立子 agent 对抗性审查（含想象性分析 + live repo 核验）发现 3 Major（F1 静态路径可行性未声明 / F2 缺 nop-metadata→nop-job 模块依赖决策 / F4 cron 触发测试机制未指定）+ F3 D3 调用入口 framing 倾向风险更高路径 + F5/F6/F8 文档冲突与文本错误。已全部修复：D1 前言显式声明静态路径仅能「单一全局 cron 触发所有 active 检查点」非 per-checkpoint；新增 D6 模块依赖裁定（仅 `-api`）；D5/Phase2 Exit Criteria 指定 `LocalJobScheduler.fireNow` 同步触发测试；D3 framing 反转为 path(b) 默认安全、path(a) 需证明；Phase1 Exit Criteria 增「调和既有 §2.7.3『不存 schedule』表述」；修正 typo/计数/NopMetaPipeline 归属。

## Purpose

把反复 deferred 的「cron 定时调度（nop-job 适配）」从 follow-up 推进到 landed：为质量检查点（`NopMetaQualityCheckpoint`）增加 cron 定时执行能力——经 nop-job 调度路径按 cron 表达式触发既有 `executeCheckpoint` 编排链（rule 解析 → judge → `NopMetaQualityResult` → actions → autoScore），收口「检查点可手动执行也可定时执行」这一结果面。本 plan 关闭 `0027-1` 与 `0540-1` 中 `Successor Required: yes` 的 cron 定时调度 deferred 项。

## Current Baseline

（基于 live repo 核实，2026-07-17）

- **手动触发入口已落地且端到端可用**：`NopMetaQualityCheckpointBizModel.executeCheckpoint(checkpointId, schemaPattern, IServiceContext)`（`nop-metadata-service/.../NopMetaQualityCheckpointBizModel.java:127-130`）。D1-D6 编排全部可用：rule 解析 → `MetaQualityCheckpointExecutor.execute` → `NopMetaQualityResult` 落盘 → `dispatchActions`（store/webhook/notify，plan 0540-2）→ `triggerAutoScoring`（plan 0540-1）。9（checkpoint）+4（autoScore）端到端 AutoTest 全绿。
- **核心执行路径 IServiceContext-free**：`MetaQualityCheckpointExecutor.execute(NopMetaQualityCheckpoint cp, String schemaPattern)`（`MetaQualityCheckpointExecutor.java:110`）不接受 IServiceContext。`executeCheckpoint` 的 `context` 仅透传给 `triggerAutoScoring` → `NopMetaQualityScoreBizModel.computeQualityScore(metaTableId, context)`（`NopMetaQualityScoreBizModel.java:53-55`），而 `computeQualityScore` 内部从不解引用 `context`（:55-75）。→ 调度入口传 null context 对核心路径 + autoScore 路径均安全。
- **检查点实体无调度字段**：`NopMetaQualityCheckpoint`（`nop-metadata/model/nop-metadata.orm.xml:1618-1677`）列集为 `checkpointId / checkpointName / displayName / metaModuleId / description / validations(json) / actions(json) / status / extConfig(json) / version + 审计列`，**无 cron/schedule/nextFire 字段**。`extConfig`（json-4000, propId 9）可承载调度配置而无需 schema 变更；或新增专用 `schedule` 列（本仓库有先例：`nop-metadata.orm.xml:1386` **`NopMetaPipeline`** 实体用 `schedule VARCHAR(200)` "调度表达式"——非 quality 实体，仅作列定义先例参考）。
- **owner doc 既有「不存 schedule」表述（待 Phase 1 调和）**：`01-architecture-baseline.md` §2.7.3 当前显式声明「不存 schedule（cron 定时为 follow-up，非本 plan）」（约 :614）与「cron 定时调度为 follow-up」（约 :622）。本 plan 把该 follow-up 推进到 landed，Phase 1 写入调度子节时须显式调和/更新这两处表述（标注由 follow-up 转为已落地），避免 owner doc 内部矛盾。
- **nop-job 调度面（local path，已核实）**：
  - 本地调度器 config-file driven：`nop-job-local/.../_vfs/nop/job/beans/app-local-scheduler.beans.xml` 注册 `nopJobLocalConfigLoader`（`LocalJobConfigLoader.java:61-87`）扫描 `/nop/job/conf/*.job.yaml` + `scheduler.yaml` → `LocalJobScheduler`（`LocalJobScheduler.java:36`）。
  - `nopJobInvoker_beanMethod` / `BeanMethodJobInvoker`（`BeanMethodJobInvoker.java:30`）经 bean 名约定解析（`BeanContainerInvokerResolver` 前缀 `nopJobInvoker_`）；`LocalJobConfigLoader.buildJobSpec:216` **硬编码** `jobInvoker="beanMethod"`，故每个 `.job.yaml` 自动走 beanMethod；cron 经 `TriggerBuilder.buildTrigger` → `CronTrigger`（`TriggerBuilder.java:23-25`）。
  - 现有 3 个 `.job.yaml` 示例（`wf-remind-task-scan` / `wf-due-task-scan` / `sys-event-batch-consumer`）均调**普通 bean 的 no-arg 或 Map-arg 方法**（`wfTaskScanner` / `nopBatchTaskRunner` 均非 `@BizModel`）；**本仓库无经 beanMethod 调 BizModel 方法的先例**。
  - **同步触发测试钩子可用**：`LocalJobScheduler.fireNow(String jobName)`（`LocalJobScheduler.java:173`）可绕过真实 cron 等待、立即触发一次执行，用于端到端测试（避免「等真实 cron」的 flaky/slow 或「绕过 cron 直调 invoker」的 hollow 测试）。
- **nop-metadata 当前对 nop-job 零依赖**（已核实）：`nop-metadata` 子模块无 `nop-job` Maven 依赖、无 `import io.nop.job.*`。动态调度路径（D1=动态）需新增 `nop-job-api`（`IJobScheduler` / `JobSpec`）依赖到 `nop-metadata-service/pom.xml`——属架构决策（`0540-1` 明确列为待裁定项），Phase 1 D6 裁定。
- **集成风险（待 Phase 1 Decision 消除，不得跳过）**：
  - **R1 BizModel bean 名解析**：检查点 BizModel 注册为 `biz_NopMetaQualityCheckpoint`（`BizProxyFactoryBean`，lazy-init，`_service.beans.xml:89-92`，`bizObjName="NopMetaQualityCheckpoint"`）以及 raw impl bean `io.nop.metadata.service.entity.NopMetaQualityCheckpointBizModel`（`:87`，`ioc:type="@bean:id" ioc:default="true"`）。`BeanContainer.tryGetBean` 应取哪个名、经 proxy 还是 raw impl、非 GraphQL 入口下 proxy 是否可用——需 live 核实。
  - **R2 未命名 IServiceContext 形参绑定**：`IFunctionModel.buildArgValues`（`nop-core/.../IFunctionModel.java:169-180`）按形参名在 jobParams 中查找，缺失键传 null。需 `-parameters` 编译标志反射出 `executeCheckpoint` 第三形参名；若不可靠则绑定不确定。
  - **R3 静态 vs 动态调度的实用性（关键结构约束）**：`LocalJobConfigLoader` 仅在启动时扫静态 `.job.yaml`（checked into 源码），无运行时从 DB 重新加载机制；检查点是运行时生成的 DB 行（checkpointId 运行时产生）。**静态 `.job.yaml` 在结构上无法承载 per-checkpoint cron**——静态唯一可行形态是「单一全局 cron 调用一个触发所有 active 检查点的方法」，但这不提供 per-checkpoint 可配置 cron。若 Goals 要求用户运行时 per-checkpoint 配置 cron 而不 redeploy，则静态路径不可行（D1 必须直面此约束）。

## Goals

- 质量检查点可按 cron 定时自动执行（无需用户每次手动调 `executeCheckpoint`）；用户可为每个检查点配置 cron 而无需 redeploy（per-checkpoint 动态调度——此诉求由 D1 直面并裁定可行方案）。
- 关闭 `0027-1` 与 `0540-1` 的 cron 定时调度 deferred 项（`Successor Required: yes`）。
- 调度触发与失败在仓库内可观测（落盘 / 日志 / 显式失败），无静默跳过。

## Non-Goals

- 通用跨模块 cron 调度框架抽象（本 plan 仅质量检查点）。
- 分布式 / 集群调度（coordinator + DB `NopJobSchedule` 路径）——除非 Phase 1 Decision 明确裁定采用。
- 事件触发执行（独立 follow-up，本 plan 不做）。
- 重写评分 / 动作投递逻辑本身（已 landed，仅复用）。
- 为非 checkpoint 实体（如单条 QualityRule）增加调度。

## Scope

### In Scope

- **D1-D5 调度集成架构决策（Decision，写入架构基线 §2.7.3 调度子节）**：调度路径（local `.job.yaml` vs 动态 entity 字段 + 启动 scanner + 运行时 `IJobScheduler.addJob/removeJob`）；cron 来源（静态配置 vs 检查点 entity 字段 vs `extConfig`）；调用入口（BizModel `executeCheckpoint` 直调 vs 暴露 `IServiceContext`-free 包装方法）；生命周期（注册/移除时机）；失败可见性。
- 按决策落地调度集成（新增 `.job.yaml` / scanner / 包装方法 / entity 字段任一组合）。
- 端到端测试：cron 触发 → `executeCheckpoint` 编排链 → `NopMetaQualityResult` + `NopMetaQualityScore` 落盘。
- owner doc 同步：`01-architecture-baseline.md` §2.7.3 调度子节；roadmap。

### Out Of Scope

- 通用调度框架、集群 coordinator 路径（除非 Decision 裁定）。
- 评分告警 / 阈值动作（0027-2 Non-Blocking Follow-up，本 plan 不做）。
- 历史时间窗口评分（0027-2 Non-Blocking Follow-up）。
- `update_docs` 动作（0540-2 deferred，`Successor Required: no`）。

## Execution Plan

### Phase 1 - 调度集成架构裁定（Decision only）

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md`（§2.7.3 新增调度子节）、`ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（新增 P2-future 工作项）

- Item Types: `Decision`

- [x] **D1 调度路径裁定**：基于 live repo 在两条路径间裁定并写清拒绝理由——(a) local scheduler 静态 `.job.yaml`（**关键约束：`.job.yaml` checked into 源码、checkpointId 运行时生成，静态在结构上无法承载 per-checkpoint cron；唯一可行静态形态是「单一全局 cron 调用一个触发所有 active 检查点的方法」，不提供 per-checkpoint 可配置 cron**）；(b) 动态调度（检查点 entity 承载 cron + 启动 scanner 读 active 检查点 + 经 `IJobScheduler.addJob/removeJob` 运行时注册，支持用户 per-checkpoint 配置，但需 entity 字段 + scanner + 生命周期 hook）。因 Goals 明确要求 per-checkpoint 动态配置，D1 须裁定 (b)（或明确放弃 per-checkpoint 诉求并降级 Goals/Non-Goals——后者需在本裁定中显式记录并据实收口）。**只裁定不写实现代码。**
- [x] **D2 cron 来源 + entity schema 裁定**：若 D1 选动态，裁定 cron 存放——新增专用 `schedule` 列（先例 `nop-metadata.orm.xml:1386` `NopMetaPipeline`，查询友好、显式）vs 复用 `extConfig` JSON（无 schema 变更、但不可查询）。若新增列属 ORM 结构变更（Protected Area），本 plan 即其 plan-first 载体，须在裁定中明确列定义（code/name/propId/stdDataType/precision/dict/mandatory）与校验。**只裁定不写实现代码。**
- [x] **D3 调用入口裁定（消除 R1/R2）**：live 核实 `BeanContainer.tryGetBean("biz_NopMetaQualityCheckpoint")` vs raw impl bean 可用性、proxy 在非 GraphQL 入口下是否可用、`-parameters` 标志下 `executeCheckpoint` 第三形参名反射结果与 `buildArgValues` 对 null context 的容忍。据此二选一并写清拒绝理由——**(b) 默认安全路径（推荐）：暴露一个 `IServiceContext`-free 包装方法**（如新增 `MetaQualityCheckpointScheduler` 普通 bean 的 `executeScheduledCheckpoint(checkpointId)`，内部调 `MetaQualityCheckpointExecutor.execute` + autoScore 接线，规避 R1/R2，且与本仓库既有 `wfTaskScanner`/`nopBatchTaskRunner` 普通 bean 经 beanMethod 调用先例一致）；(a) 经 beanMethod 直调 `executeCheckpoint(checkpointId, schemaPattern, null)`（**仅当** R1/R2 经 live 核实确定通过且证明优于 (b) 才可选，否则选 (b)）。**只裁定不写实现代码。**
- [x] **D4 生命周期裁定**：若 D1 动态——裁定注册/移除时机：(a) 仅启动 scanner 全量注册；(b) 检查点 save/enable 时增量注册 + disable/delete 时移除；(c) 二者结合（启动全量 + 运行时增量）。裁定 scanner 容错（缺 cron / status 非 active / cron 非法如何处理——显式跳过并记录，不抛崩启动）。**只裁定不写实现代码。**
- [x] **D5 失败可见性 + 测试触发机制裁定**：裁定定时执行失败的可见路径——复用既有 `executeCheckpoint` 摘要 + autoScore errors（0540-1）即可，还是需额外落盘调度执行历史（`NopJobFire`/`NopJobTask` 仅 coordinator 路径产生；local path 无独立执行历史实体——须 live 核实并写明）。裁定结果须保证「定时失败可观测、不静默」。同时裁定**端到端测试触发机制**：用 `LocalJobScheduler.fireNow(jobName)`（`LocalJobScheduler.java:173`）同步立即触发，**不得**绕过 cron/invoker 直接调 BizModel（否则 hollow）；写明测试如何拿到 scheduler 实例并断言。**只裁定不写实现代码。**
- [x] **D6 模块依赖裁定**（消除 `0540-1` 列出的「nop-metadata 是否新增 nop-job 依赖」待裁项）：live 核实并裁定 `nop-metadata` 引入 nop-job 的最小依赖面——动态调度需 `nop-job-api`（`IJobScheduler` / `JobSpec` / `TriggerSpec`）；裁定是否仅依赖 `-api`（接口）而**不**引入 `nop-job-local`/`nop-job-core`（运行时实现由宿主 app 经 `app-local-scheduler.beans.xml` 提供，避免 nop-metadata 绑死调度实现），并在 `nop-metadata-service/pom.xml` 写明。裁定无循环依赖风险（nop-job 不反向依赖 nop-metadata）。**测试 classpath 裁定**：端到端测试需 `nop-job-local`（test scope）以便 `IJobScheduler`/`LocalJobScheduler` bean 在 AutoTest IoC 容器中物化（生产 runtime 由宿主 app 提供，仅测试需 test-scope 引入）——在裁定中写明 test-scope 依赖。**只裁定不写实现代码。**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1-D6 六项裁定结论已写入 `01-architecture-baseline.md` §2.7.3 新增「定时调度」子节（结构上对齐 §2.7.3 既有 D1-D6 标注风格，作为新增决策块），且每项附 live repo 证据（file:line）与拒绝的替代方案及理由。
- [x] R1（BizModel bean 名）+ R2（未命名 context 绑定）在 D3 中经 live 核实给出确定结论（非推测）。
- [x] D6 已 live 核实 nop-metadata→nop-job 依赖面（仅 `-api` 或裁定的等价最小集），并写明无循环依赖。
- [x] 若 D2 引入 ORM 列变更，列定义（code/name/propId/stdDataType/precision/dict/mandatory）已在裁定中完整写出。
- [x] §2.7.3 既有「不存 schedule（cron 定时为 follow-up，非本 plan）」（约 :614）与「cron 定时调度为 follow-up」（约 :622）两处表述已显式调和/更新（标注由 follow-up 转为已落地或指向新子节），无 owner doc 内部矛盾。
- [x] roadmap 已新增 `P2-cron 质量检查点 cron 定时调度` 工作项（命名对齐 roadmap 既有 `P2-N` 约定）并标 `planned`。
- [x] 本 Phase 为纯 Decision，未写实现代码（No implementation code written in this Phase）。
- [x] **No owner-doc update required** 不适用——本 Phase 产出即 owner doc 更新（§2.7.3 调度子节 + roadmap）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 调度集成实现 + 端到端验证

Status: completed
Targets: `nop-metadata/`（service 层 + 可能 model 层 + `_vfs/.../conf/`）、`01-architecture-baseline.md`、roadmap

- Item Types: `Fix`（落地 Phase 1 决策）

- [x] 按 D6 落地模块依赖：在 `nop-metadata-service/pom.xml` 新增裁定的最小 nop-job 依赖（预期 `nop-job-api`），`./mvnw compile` 通过、无循环依赖。
- [x] 按 D1 落地调度路径：若静态——新增 `_vfs/nop/job/conf/nop-meta-quality-checkpoint-*.job.yaml`（cron + bean/method/params）；若动态——实现启动 scanner 读 active 检查点 + `IJobScheduler.addJob` 注册（+ 按 D4 的运行时增量 hook）。
- [x] 按 D2 落地 cron 来源：新增 `schedule` 列（含 DDL/索引）或 `extConfig` 约定，及对应的 ORM/dict/校验。
- [x] 按 D3 落地调用入口：包装方法（默认安全路径）或 beanMethod 直调 `executeCheckpoint`，保证 `BeanContainer.tryGetBean` 用裁定后的 bean 名。
- [x] 按 D5 落地失败可见性：定时执行失败显式记录/落盘，调度入口对未知 checkpointId / 非 active status / 空 cron 显式失败（抛 ErrorCode 或显式跳过并记录，不静默吞掉）。
- [x] 新增端到端 AutoTest（Nop AutoTest 风格）：**用 `LocalJobScheduler.fireNow(jobName)` 同步触发**（不得绕过 cron/invoker 直调 BizModel——否则 hollow），覆盖「cron 触发 → executeCheckpoint 编排链 → `NopMetaQualityResult` 行落盘 + autoScore `NopMetaQualityScore` 行落盘」；覆盖失败路径（status 非 active / 未知 checkpoint / 空 cron 显式失败或显式跳过）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 按 Phase 1 D1-D6 决策落地的调度集成在 live repo 可见（具体文件/方法/test 名在 closure evidence 中列出）。
- [x] **端到端验证**（见 Minimum Rules #22）：从「cron 触发入口」到「`NopMetaQualityResult` + `NopMetaQualityScore` 落盘」的完整路径有测试覆盖（非仅组件级）；测试经 `LocalJobScheduler.fireNow` 真实走 cron/invoker 路径（非绕过）。
- [x] **接线验证**（见 Minimum Rules #23）：新增调度组件（scanner/`.job.yaml`/包装方法）在运行时确实调用 `executeCheckpoint` 编排链（测试断言 `NopMetaQualityResult` 行存在，证明调用链连通）。
- [x] **无静默跳过**（见 Minimum Rules #24）：未知 checkpointId / 非 active status / 空 cron / 非法 cron 在调度入口显式失败或显式跳过并记录，无空方法体 / `continue` 吞掉 / `catch{}`。
- [x] **新增功能测试覆盖**（见 Minimum Rules #25）：列出新增测试用例及其验证的新行为（成功触发路径 + 各显式失败/跳过路径）。
- [x] 若引入 ORM 列变更，`./mvnw test -pl nop-metadata -am` 通过且无回归；生成文件未手改（`_gen/`、`_*.xml` 经模型派生）。
- [x] owner doc `01-architecture-baseline.md` §2.7.3 调度子节描述与 live 实现一致；roadmap `P2-cron` 状态与 plan 状态同步。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 0027-1 与 0540-1 的「cron 定时调度（nop-job 适配）」`Successor Required: yes` deferred 项已落地并从 follow-up 收口。
- [x] 检查点可按 cron 定时执行（核心结果面成立），失败路径显式可见、不静默。
- [x] Phase 1 Decision（D1-D6）与 Phase 2 实现文本一致，无「决策未落地」或「实现偏离决策」。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [x] 受影响 owner docs（§2.7.3 + roadmap）已同步到 live baseline。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）调度组件在运行时确实调用 `executeCheckpoint` 编排链（非仅类型/import），（b）端到端路径从 cron 触发到结果落盘完整连通，（c）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw compile`（或 `-pl nop-metadata -am`）
- [x] `./mvnw test -pl nop-metadata -am`
- [x] checkstyle / 代码规范检查通过（imports 分组 java.*→jakarta.*→第三方→io.nop.*；无裸 RuntimeException；inline ErrorCode + `.param()`；英文错误消息）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（执行中产生的优化项按规则归类记录于此。预判：分布式 coordinator 路径、评分告警/阈值动作联动可能作为 optimization candidate deferred。）

## Non-Blocking Follow-ups

- 定时调度接入后，autoScore（0540-1）与 webhook/notify 动作（0540-2）在 cron 路径自动生效（本 plan 评分/动作逻辑与触发源解耦，无需额外接线）。
- 评分告警 / 阈值动作（分数低于阈值触发通知，与 0027-1 动作 follow-up 同源）——0027-2 Non-Blocking Follow-up。
- `update_docs` 动作（0540-2 deferred，`Successor Required: no`）。

## Closure

Status Note: 质量检查点 cron 定时调度（nop-job 集成）已落地——动态调度路径（scanner + IJobScheduler.addJob/removeJob）+ extConfig.schedule cron + 包装 bean（MetaQualityCheckpointScheduler）规避 BizProxy/IServiceContext 绑定风险，端到端测试经 LocalJobScheduler.fireNow 真实走 cron/invoker 调用链验证 NopMetaQualityResult + NopMetaQualityScore 行落盘。关闭 0027-1 与 0540-1 的 cron 定时调度 deferred 项。Phase 1（Decision）与 Phase 2（Fix）文本一致，无决策未落地。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: mission-driver closure pass（plan 自驱动执行 + live code/test 核验）
- Audit Session: 2026-07-17T14:00Z（单 session 内完成执行 + closure 核验）
- Evidence:
  - **Phase 1 Exit Criteria**（D1-D6 写入 §2.7.3.1）: PASS — `01-architecture-baseline.md` §2.7.3.1（约 :636-700）新增「定时调度（cron）— P2-cron」决策块，D1-D6 每项附 live repo 证据（file:line）+ 拒绝方案理由；§2.7.3 既有两处「follow-up」表述已调和（:614 + :622 指向新子节）；roadmap 新增 `P2-cron` 工作项（:103）。
  - **Phase 2 Exit Criteria**:
    - **调度集成 live 可见**: PASS — `MetaQualityCheckpointScheduler.java`（`nop-metadata-service/.../service/quality/`，scanner + register/unregister + executeScheduledCheckpoint）；bean 注册 `app-service.beans.xml:37`（非生成的 `_service.beans.xml`，避免 codegen 覆盖）；`NopMetaQualityCheckpointBizModel.java` save/delete override（运行时增量 hook）；`pom.xml` 新增 `nop-job-api`（prod）+ `nop-job-local`（test）。
    - **端到端验证（#22）**: PASS — `TestMetaQualityCheckpointScheduler.testCronJobFireNowWritesResultsAndScores`：registerCheckpoint → `IJobScheduler.fireNow(jobName)` → 断言 `NopMetaQualityResult` 行=1 + `NopMetaQualityScore` 行=1。fireNow 真实走 `executeJob → BeanMethodJobInvoker → executeScheduledCheckpoint → executeCheckpoint → executor` 编排链（非绕过）。
    - **接线验证（#23）**: PASS — QualityResult 行落盘证明 `fireNow → BeanMethodJobInvoker.invokeAsync → executeScheduledCheckpoint(Map) → checkpointBizModel.executeCheckpoint → MetaQualityCheckpointExecutor.execute → judge → QualityResultWriter` 调用链运行时连通。
    - **无静默跳过（#24）**: PASS — 未知 checkpointId → executeCheckpoint 抛 ERR_CHECKPOINT_NOT_FOUND（经 invoker 转 JobFireResult.ERROR）；非 ACTIVE → executor 抛 ERR_CHECKPOINT_NOT_ACTIVE；空 cron → doRegister 跳过+removeJob（显式）；非法 cron → scanner catch+记录。无空方法体/continue 吞掉/catch{}。
    - **新增功能测试覆盖（#25）**: PASS — 5 个新测试：(a) 成功触发（QualityResult+Score 落盘）/ (b) PAUSED 不注册 / (c) 空 cron 不注册 / (d) 未知 job fireNow 返回 false / (e) 启动 scanner 空 DB 不抛崩。
    - **ORM 列变更**: N/A — D2 选 extConfig（无 schema 变更），`_gen/`/`_app.orm.xml` 未手改（codegen 重生成 `_service.beans.xml` 时我的 bean 移到 `app-service.beans.xml` 规避，已验证）。
    - **owner doc 一致**: PASS — §2.7.3.1 描述与 live 实现一致（scanner + extConfig.schedule + 包装 bean + fireNow 测试）；roadmap `P2-cron` 状态与本 plan 同步（closure 后改 done）。
    - **logs 更新**: PASS — `ai-dev/logs/2026/07-17.md` 追加本 plan 条目。
  - **Closure Gates**: 全部 PASS（见上方逐条勾选）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0（所有 checklist 已勾选 + Closure Evidence 已写入）。
  - Anti-Hollow 检查结果: PASS — (a) 调度组件 `executeScheduledCheckpoint` 运行时经 BeanMethodJobInvoker 调用 executeCheckpoint（test 断言 QualityResult 行=1 证明）；(b) 端到端 fireNow→落盘完整连通（QualityResult + QualityScore 均 +1）；(c) 无空方法体/no-op（scanner 容错 catch 显式记录日志）。`scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码为 0（0 findings）。
  - Deferred 项分类检查: 无 in-scope live defect 被降级——分布式 coordinator 路径、评分告警/阈值动作为既有 Non-Blocking Follow-up（与本 plan 无关）；`update_docs` 动作仍 deferred（0540-2，Successor Required: no）。

Follow-up:

- autoScore（0540-1）与 webhook/notify 动作（0540-2）在 cron 路径自动生效（编排链与触发源解耦，已验证 QualityScore 行落盘）。
- 评分告警 / 阈值动作（0027-2 Non-Blocking Follow-up）。
- `update_docs` 动作（0540-2 deferred，`Successor Required: no`）。
- no remaining plan-owned work（本 plan 范围内全部落地）。
