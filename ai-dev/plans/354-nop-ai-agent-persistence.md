# 354 nop-ai-agent 持久化归一与能力复用裁定

> Plan Status: completed
> Last Reviewed: 2026-09-13
> Source: `ai-dev/audits/2026-09/2026-09-12-2130-nop-platform-conformance/03-nop-ai-findings.md`（AI-2/16/17/19 + AI-8/9/10/11 裁定）
> Related: 350/351/352/353（已完成）

## Purpose

消除审计 P1 的 nop-ai-agent raw-JDBC 双写（AI-2 核心项：`DbModelSwitchedMessageWriter` 直写 ORM 管理的 `nop_ai_session_message` 表）；为 agent 运行时 store 层（13 个自建表类）建立显式边界契约；收口 AI-16/17 DAO 反模式与 AI-19 文件 IO；登记 AI-8/9/10/11 能力复用裁定。

## Current Baseline

- **AI-2 双写**：`DbModelSwitchedMessageWriter`（nop-ai-agent/session，159 行）经 raw JDBC `INSERT INTO nop_ai_session_message`（11 列）写 ORM 实体 `NopAiSessionMessage` 管理的同一表，且构造器 `initSchema()` 执行 `CREATE TABLE IF NOT EXISTS` DDL（与 ORM schema 管理冲突）。模块约束：nop-ai-agent **不能**依赖 nop-ai-dao（类注释明言）。生产接线：`DefaultAgentEngine.Builder` 默认 `NoOpModelSwitchedMessageWriter`，Db 实现仅测试实例化（4 处）+ 集成方 Builder 注入。
- **store 层 13 类**：DBSessionStore（ai_agent_session 表——与 NopAiSession 概念区分：业务会话记录 vs 运行时状态 JSON）、DBMessageService（ai_agent_message）、DbTeamTaskStore/DbTeamManager（team task）、DBCheckpointManager、DbUsageRecorder、DBDenialLedger、DbSessionTakeoverLock、DbDaemonCoordinator、3 个 recovery handler、ScheduledRecoveryManager（部分仅持有 Connection 类型）。
- AI-16：`AiModelCredentialResolverImpl`（nop-ai-service/credential 包）`:154-159` `daoProvider().daoFor(NopAiModel.class)` + `findAllByQuery`（有注释：LLM 运行链无请求上下文）。
- AI-17：`NopAiModelBizModel:96/149` `dao().getEntityById`（有注释：save 前事务内读旧值 / delete 前捕获 credentialId）。
- AI-19：`CheckpointSnapshotReader:41`、`CheckpointJournalReader:109`、`SessionFileReader:57` 用 `Files.readString(file, UTF_8)`——checkpoint/journal/session 快照为本地文件契约（reliability 目录，路径来自本地 checkpoint 目录配置）。
- 裁定项现状：AI-8 自建调度守护（TeamTaskSchedulerDaemon/ScheduledRecoveryManager/DBMessageService poller）；AI-9 自建重试+熔断（nop-ai-core/reliability 20 类）；AI-10 自建向量/嵌入检索（agent/memory 17 文件，InMemory 实现，注释自认生产实现待做）；AI-11 DBMessageService 660 行自建消息传输（实现 IMessageService 接口）。

## Goals

- **AI-2 核心**：新增 `OrmModelSwitchedMessageWriter`（nop-ai-service，可依赖 nop-ai-dao）经 `IEntityDao<NopAiSessionMessage>` 写入（newEntity + 列映射 + ORM 自动时间戳）；`DbModelSwitchedMessageWriter` 标 `@Deprecated(forRemoval)` + javadoc（DDL 与 ORM schema 冲突警告、仅限 embedded 无 ORM 部署、ORM 部署必须用 Orm 实现）；测试迁移/新增。
- **store 层边界契约**：新建 `ai-dev/design/nop-ai-agent/store-layer-contract.md`（13 类的表清单、与 ORM 的关系、租户/软删不适用理由、清理责任）+ 13 类 javadoc 统一补裁定引用 + owner doc（03-modules/nop-ai.md）补一段。
- AI-16：包移动 `credential` → `infra`（或类内定位注释升级 + ErrorCode 内联定义收进 NopAiErrors）——按现场可行性二选一。
- AI-17：`:96` 改 `doSave` prepareSave 回调内取旧实体（EntityData）；`:149` delete 前读旧值属合理捕获（改 requireEntity 或保留+注释升级）。
- AI-19：三个 reader 属本地 checkpoint/journal 文件契约（并行 stream LocalFileCheckpointStorage 豁免先例）→ **裁定保留** + 注释；`JavaMethodReplacer` 已在 plan 350 修复，无剩余。
- AI-8/9/10/11 裁定登记：AI-8 迁移成本与嵌入性权衡（保留 + 理由）；AI-9 LLM 调用级重试的领域特殊性（错误分类/账号链/failover，nop-retry 是持久化分布式重试）+ 通用退避可下沉备注；AI-10 保留内存实现（生产向 ISearchEngine 适配器留 successor）；AI-11 实现平台 IMessageService SPI 的传输层 sibling（保留 + 与 nop-message-core 关系说明）。
- 全部裁定写入 design doc（store-layer-contract.md 或独立 section）+ owner doc 索引。

## Non-Goals

- 不将 13 个自建表注册进 ORM 模型（审计允许的边界文档路线；表注册属架构演进，登记 successor）。
- 不重构 ReActAgentExecutor（AI-1 属 plan 355）。
- 不处理 AI-14/15 异常（plan 356）。

## Scope

### In Scope

- `nop-ai-agent/session`（writer 弃用 + 13 类 javadoc）、`nop-ai-service`（新 Orm writer + AI-16/17）
- `ai-dev/design/nop-ai-agent/store-layer-contract.md`（新）、`docs-for-ai/03-modules/nop-ai.md`

### Out Of Scope

- memory/reliability/message 代码重构（仅裁定）、quickstart、web。

## Execution Plan

### Phase 1 - OrmModelSwitchedMessageWriter（AI-2 核心）

Status: completed
Targets: `nop-ai-service` 新类 + `DbModelSwitchedMessageWriter` 弃用 + 测试

- Item Types: `Fix`

- [x] 新建 `io.nop.ai.service.agent.OrmModelSwitchedMessageWriter implements IModelSwitchedMessageWriter`：`@Inject IDaoProvider`（nop-ai-service 可依赖 nop-ai-dao），`daoFor(NopAiSessionMessage.class)` + newEntity 映射 11 列（role=80、seq、content、metadata、version、createdBy/updatedBy 从调用方元数据、createTime/updateTime 走 ORM domain 自动）——列集合对照 NopAiSessionMessage 实体属性核对
- [x] `DbModelSwitchedMessageWriter` 标 `@Deprecated` + javadoc（DDL 冲突警告/embedded-only/指向 Orm 实现）
- [x] 新增 `TestOrmModelSwitchedMessageWriter`（nop-ai-service，ORM 测试基建）断言：写入后经 dao 读回字段一致、role=80、seq 单调；Db 测试保留（embedded 路径回归）
- [x] `./mvnw test -pl nop-ai/nop-ai-service -am`

Exit Criteria:

- [x] Orm writer 落地且测试通过；Db writer 带 @Deprecated 与冲突警告
- [x] New test required: TestOrmModelSwitchedMessageWriter（验证 ORM 管道写入 + 字段完整）
- [x] owner doc `03-modules/nop-ai.md` 补双写修复说明（生产装配建议 Orm 实现）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - store 层边界契约（AI-2 其余 13 类）

Status: completed
Targets: `ai-dev/design/nop-ai-agent/store-layer-contract.md`（新）+ 13 类 javadoc + owner doc

- Item Types: `Decision | Fix`

- [x] design doc：表清单（ai_agent_session/message/team_task/checkpoint/journal/lock/ledger/usage 等）、每表与 ORM 的关系（独立 store 层，非业务数据）、租户/软删不适用理由（引擎内部运行时状态，生命周期由 runtime 管理，宿主负责会话级隔离）、清理责任、`ai_agent_session` vs `NopAiSession` 的概念区分裁定
- [x] 13 类 javadoc 头部统一补「store-layer 边界（审计 AI-2 裁定，见 design doc）」引用
- [x] owner doc 补 store 层段落
- [x] 文档链接检查

Exit Criteria:

- [x] design doc 落地（含表清单与裁定）；13 类 javadoc 带引用；owner doc 更新
- [x] `check-doc-links --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - AI-16/17 DAO 反模式收口

Status: completed
Targets: `AiModelCredentialResolverImpl`、`NopAiModelBizModel`

- Item Types: `Fix | Decision`

- [x] AI-16：类迁移至 `infra` 包（同模块内包移动，更新 beans 装配与 import）或保留 + 定位注释升级 + 内联 ErrorCode 收进 NopAiErrors——现场判定（优先包移动）
- [x] AI-17 :96：save 路径改 `doSave` 的 prepareSave 回调（EntityData 携带旧实体或重读）或 `findFirstByExample` 强类型等价；:149 delete 捕获旧值保留 + 注释升级为裁定引用
- [x] `./mvnw test -pl nop-ai/nop-ai-service -am`

Exit Criteria:

- [x] `rg 'daoProvider\(\)\.daoFor' nop-ai-service` 仅剩裁定注释项；`dao().getEntityById` 同
- [x] 模块测试全绿
- [x] No owner-doc update required（代码内裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - AI-19 裁定 + AI-8/9/10/11 能力复用裁定

Status: completed
Targets: 三个 reader 注释 + design doc 裁定段

- Item Types: `Decision`

- [x] AI-19：三个 reader 补本地文件契约裁定注释（并行 stream LocalFileCheckpointStorage 豁免先例）
- [x] AI-8/9/10/11 四项裁定写入 design doc（理由 + successor）：调度守护嵌入性权衡/LLM 重试领域特殊性/向量内存实现与 ISearchEngine successor/DBMessageService 为 IMessageService SPI 传输层 sibling
- [x] 文档链接检查

Exit Criteria:

- [x] 四项裁定 + AI-19 裁定有文字落点且可追溯
- [x] `check-doc-links --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 全量验证与收口

Status: completed
Targets: nop-ai 模块组

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-service -am` 全绿
- [x] 复扫：deprecated 双写类带警告、13 类 javadoc 引用、AI-16/17 反模式归零
- [x] 独立 closure audit + checklist --strict

Exit Criteria:

- [x] 验证与 audit 证据写入 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/354-nop-ai-agent-persistence.md --strict` 退出码 0

## Closure Gates

- [x] AI-2 双写消除（Orm 路径 + Db 弃用警告）；store 层边界契约落地
- [x] AI-16/17 反模式收口；AI-19/8/9/10/11 裁定登记
- [x] 模块测试全绿；无 in-scope live defect 降级（自建表保留走"文档化边界"审计允许路线并登记 successor）
- [x] 独立 closure audit 完成且证据已写入

## Deferred But Adjudicated

### 13 个自建表注册进 ORM 模型

- Classification: `optimization candidate`
- Why Not Blocking Closure: 审计允许的替代路线（store 层文档化）；表为引擎内部运行时状态非业务数据；注册需模型+迁移+双写切换，独立演进。
- Successor Required: no
- Successor Path: 记录于 design doc（后续架构演进候选）

### AI-10 生产向量检索 ISearchEngine 适配器

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 InMemory 实现是注释自认的显性占位；生产实现时必须落 ISearchEngine 适配器而非扩展自建接口。
- Successor Required: no

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: 全部 5 Phase 完成（Phase 4 的裁定随 Phase 2 文档落地）。AI-2 双写经 SPI 下沉 nop-ai-core + Orm writer（nop-ai-service）修复；store 层 13 类 + 3 reader 边界契约文档化。
Completed: 2026-09-13

Closure Audit Evidence:

- Reviewer / Agent: agent_1ef144a9（独立 closure audit，1-5 维度全 PASS；2 项诚实性发现[日志缺条目/FINAL 标记形式]已同日修补）
- Evidence:
  - AI-2：IModelSwitchedMessageWriter 下沉 nop-ai-core（service 的 core 依赖 test→compile，理由入 pom 注释）；OrmModelSwitchedMessageWriter（IEntityDao 管道 + bean nopOrmModelSwitchedMessageWriter 注册 + TestOrmModelSwitchedMessageWriter 回归绿）；DbModelSwitchedMessageWriter @Deprecated + DDL 冲突警告；agent 5 文件 import 更新
  - store 层：ai-dev/design/nop-ai-agent/store-layer-contract.md（表清单/租户软删不适用理由/AI-8/9/10/11 四裁定/AI-19 裁定/拒绝的替代方案）；16 类 javadoc 带契约引用；owner doc 03-modules/nop-ai.md 补 store 层段
  - AI-16：包移动 credential→infra（beans/测试同步，clean 后全绿）；AI-17：readOldCredentialId 改 findFirstByExample 强类型 + delete 捕获裁定注释
  - 最终回归：nop-ai-agent（3387/0）+ nop-ai-service（37/0）+ core（379/0）surefire 全绿（audit 经时间戳核证；plan354-final.log 截断无标记，证据以 surefire 为准）；check-doc-links --strict 0 errors
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/354-nop-ai-agent-persistence.md --strict` 退出码 0

Follow-up:

- no remaining plan-owned work
