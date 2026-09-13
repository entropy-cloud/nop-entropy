# nop-ai-agent Store 层边界契约

> Status: active
> Created: 2026-09-13
> Source: `ai-dev/audits/2026-09/2026-09-12-2130-nop-platform-conformance/03-nop-ai-findings.md` AI-2/AI-8/9/10/11（裁定记录）

## 决策

nop-ai-agent 的运行时持久化（13 个 raw-JDBC 类，下表）**保留独立 store 层形态，不注册进 nop-ai-dao ORM 模型**；唯一例外已修复：`DbModelSwitchedMessageWriter` 直写 ORM 管理的 `nop_ai_session_message` 表（双写），已弃用并由 `OrmModelSwitchedMessageWriter`（nop-ai-service，IEntityDao 管道）替代生产路径。

## 边界依据

1. **模块依赖约束**：nop-ai-agent 是可嵌入运行时引擎，不能依赖 nop-ai-dao（类注释与模块 pom 长期约束）。SPI `IModelSwitchedMessageWriter` 已下沉到 nop-ai-core（agent/service 共同可见层），ORM 侧实现在 nop-ai-service——这是"agent 运行时 + ORM 部署"协作的既定模式。
2. **表性质**：自建表为引擎内部运行时状态（会话状态快照/检查点/日志/锁/账本/团队任务/用量），非业务领域数据。宿主应用负责会话级隔离（session id 由宿主命名空间化）；引擎内部表不参与业务多租户与逻辑删除管道——生命周期由 runtime 创建/清理（recovery handler 负责孤儿清理）。

## 表清单与责任

| 类 | 表 | 用途 | 清理责任 |
|---|---|---|---|
| DBSessionStore | ai_agent_session | Agent 运行时状态 JSON 快照（与 NopAiSession 概念区分：后者是业务会话记录/状态字典，前者是引擎执行态） | 超时 handler + 宿主运维 |
| DBMessageService | ai_agent_message | 引擎内部消息传输（IMessageService SPI 传输层 sibling，见裁定 4） | stale sweep |
| DbTeamTaskStore / DbTeamManager | ai_agent_team_task 等 | 多代理团队任务队列 | 状态机终态清理 |
| DBCheckpointManager | checkpoint 表 | 可靠性检查点 | 保留清单收敛 |
| DbUsageRecorder | 用量表 | LLM 用量记录（审计/计费数据源） | 保留（追加型） |
| DBDenialLedger / DbSessionTakeoverLock / DbDaemonCoordinator | ledger/lock/daemon 表 | 安全账本/会话接管锁/守护互斥 | 过期清扫 |
| 3 个 recovery handler + ScheduledRecoveryManager | （复用上表） | 孤儿/超时恢复 | 自身 |

## 能力复用裁定（AI-8/9/10/11）

1. **AI-8 自建调度守护（TeamTaskSchedulerDaemon/ScheduledRecoveryManager/DBMessageService poller）**：保留。agent 引擎自包含嵌入运行（可独立于 nop-job 部署），守护线程随引擎生命周期；宿主集成 nop-job 时可外置触发（successor 候选）。DBDaemonCoordinator 的 DB 互斥选主为嵌入场景必需（无 nop-job 依赖时）。
2. **AI-9 自建重试+熔断（nop-ai-core/reliability 20 类）**：保留。LLM 调用级重试带错误分类/账号链/跨 provider failover 的领域语义，nop-retry 是持久化分布式重试任务（语义不匹配）；通用退避算法下沉为平台 helper 属 optimization candidate。
3. **AI-10 自建向量/嵌入检索（agent/memory 17 文件）**：保留 InMemory 实现（显性占位，类注释自认）。生产向量检索实现时**必须**落 nop-search ISearchEngine 适配器而非扩展自建接口（successor 必须遵守）。
4. **AI-11 DBMessageService 自建 DB 消息传输（660 行）**：保留。实现了平台 IMessageService SPI（接口已复用），传输层为 DB 轮询 sibling——与 nop-message-core 的 local/JDBC 实现并列；引擎嵌入场景无消息中间件时必需。若宿主部署 nop-message 中间件实现，可经 beans 覆盖注入（SPI 可替换性保留）。

## AI-19 裁定

CheckpointSnapshotReader/CheckpointJournalReader/SessionFileReader 的 `Files.readString(path)`：checkpoint/journal/session 快照为**本地文件契约**（路径来自本地 checkpoint 目录配置，类名即契约——并行 nop-stream LocalFileCheckpointStorage 豁免先例），保留 java.nio 直读。

## 拒绝的替代方案

- **13 表全量注册 ORM**：需模型 + 迁移 + 双写切换 + 打破 agent 无 dao 依赖的嵌入性，收益（管道一致性）针对的是引擎内部状态而非业务数据——成本收益不成立。登记为后续架构演进候选（若 agent 运行时状态需要纳入统一治理时重评）。
