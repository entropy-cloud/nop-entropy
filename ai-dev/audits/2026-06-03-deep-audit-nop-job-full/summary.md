# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-job
- **审核日期**: 2026-06-03
- **执行维度**: 全部 21 个维度
- **目标范围**: nop-job 下 11 个子模块（api, core, dao, service, coordinator, worker, meta, web, app, codegen, retry-adapter）

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|-----------|------|------|------|
| 01-依赖图 | 1 | 6 | 0 | 6 | 0 | 0 |
| 02-模块职责 | 1 | 4 | 0 | 3 | 1 | 0 |
| 03-API表面积 | 1 | 2 | 0 | 2 | 0 | 0 |
| 04-ORM模型 | 1 | 2 | 0 | 1 | 1 | 0 |
| 05-生成管线 | 1 | 1 | 0 | 1 | 0 | 0 |
| 06-Delta定制 | 1 | 0 | 0 | 0 | 0 | 0 |
| 07-BizModel规范 | 1 | 3 | 0 | 3 | 0 | 0 |
| 08-IoC/Bean | 1 | 2 | 0 | 2 | 0 | 0 |
| 09-错误处理 | 1 | 4 | 0 | 4 | 0 | 0 |
| 10-XDSL | 1 | 1 | 0 | 1 | 0 | 0 |
| 11-XMeta对齐 | 1 | 3 | 0 | 2 | 1 | 0 |
| 12-GraphQL | 1 | 0 | 0 | 0 | 0 | 0 |
| 13-安全权限 | 1 | 0 | 0 | 0 | 0 | 0 |
| 14-异步事务 | 1 | 2 | 0 | 2 | 0 | 0 |
| 15-类型安全 | 1 | 3 | 0 | 3 | 0 | 0 |
| 16-测试覆盖 | 1 | 4 | 0 | 4 | 0 | 0 |
| 17-代码风格 | 1 | 3 | 0 | 3 | 0 | 0 |
| 18-文档一致性 | 1 | 5 | 0 | 5 | 0 | 0 |
| 19-命名一致性 | 1 | 2 | 0 | 2 | 0 | 0 |
| 20-跨模块契约 | 1 | 2 | 0 | 2 | 0 | 0 |
| 21-测试有效性 | 1 | 3 | 0 | 3 | 0 | 0 |
| **合计** | **21** | **52** | **0** | **49** | **3** | **0** |

## 按严重程度分布（复核后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 2 | 错误码语义错误(09)、文档示例与实现不符(18) |
| P2 | 28 | 未使用依赖(01)、Store层业务逻辑渗透(02,07)、安全字段限制(11,15)、乐观锁处理(14)、跨模块契约(03,20)、命名不一致(19)、文档错误(18)、测试覆盖不足(16,21) |
| P3 | 22 | 硬编码版本(01)、代码重复(02,07,17)、i18n不完整(04)、死代码(15,17)、bean命名(08)、测试弱断言(21) |

## 关键发现摘要

### P1 发现

| 编号 | 文件 | 摘要 |
|------|------|------|
| 09-01 | LocalJobScheduler.java:78-83 | `addJob` job已存在时误用 `ERR_JOB_UNKNOWN_JOB`（"未知的任务"），错误码与实际语义完全相反 |
| 18-01 | concurrency-and-transactions.md:89-103 | 文档示例使用 `updateEntityDirectly`，实际代码用 `tryUpdateManyWithVersionCheck` + 乐观锁重检，遗漏了关键并发安全步骤 |

### P2 发现（关键项）

| 编号 | 文件 | 摘要 |
|------|------|------|
| 07-01 | NopJobFireBizModel.java:48-79 | `cancelFire`/`rerunFire` 通过 store.loadFire() 绕过 `requireEntity()` 的数据权限校验 |
| 02-03 | JobFireStoreImpl.java:176-236 | dao store 层包含业务决策逻辑（状态机判断、阻塞策略），层边界渗透 |
| 11-01 | NopJobTask.xmeta | 5 个引擎管控字段(progress等)缺少 insertable=false/updatable=false 限制 |
| 15-01 | JobWorkerScannerImpl.java:205-207 | `Integer == int` 自动拆箱，`getTaskStatus()` 为 null 时 NPE |
| 14-01 | JobWorkerScannerImpl.java:238-246 | 乐观锁失败且非终态时静默丢弃任务结果 |
| 03-01 | NopRetryJobRetryBridge.java:31-70 | `onFireFailed` 始终返回 null，retryRecordId 关联断裂 |
| 18-02 | architecture-principles.md:97 | 文档错误引用 `@BizAction`，实际应为 `IJobInvoker` 注册机制 |
| 18-03 | architecture-principles.md:26 | 错误将 NopJobScheduleBizModel 归为"无独立表"的编排型聚合根 |
| 18-04 | where-things-live.md:65 | 遗漏 8 个 nop-job 关键子模块 |
| 19-01 | IJobScheduler.java:96-133 | 同一接口 suspend/pause 术语不一致 |
| 05-01 | nop-job-web/pages/ | 4 个幽灵 Web 页面目录引用不存在的 xmeta |

## 总评

nop-job 模块整体架构设计清晰，11 个子模块分层合理，生成管线完整闭合，Delta 定制合规，安全权限遵循平台标准，GraphQL API 层无问题。核心的调度引擎（Planner/Dispatcher/Completion/Timeout 四扫描器架构）实现成熟，乐观锁 + REQUIRES_NEW 事务 + @SingleSession 的并发控制模式在测试中验证充分。

主要问题集中在以下几个方面：

1. **错误码语义错误（P1）**：`LocalJobScheduler.addJob` 使用了与实际语义完全相反的错误码，会误导调用方和运维排查。

2. **文档与实现脱节（P1）**：`concurrency-and-transactions.md` 中的代码示例遗漏了乐观锁重检步骤，如果按文档编写代码会导致并发安全问题。

3. **Store 层边界渗透（P2）**：dao store 层承担了过多的业务决策逻辑（状态机判断、阻塞策略），与 coordinator 层形成分散的业务逻辑。

4. **数据权限绕过（P2）**：`NopJobFireBizModel` 的 `cancelFire`/`rerunFire` 绕过了 `requireEntity()` 的权限校验。

5. **类型安全风险（P2）**：`Integer` 自动拆箱可能导致 NPE，乐观锁失败时静默丢弃任务结果。

## 优先修复建议

1. **[P1] 修复 ERR_JOB_UNKNOWN_JOB 错误码**：新增 `ERR_JOB_ALREADY_EXISTS` 错误码用于 addJob 冲突场景
2. **[P1] 更新 concurrency-and-transactions.md**：将代码示例替换为实际实现
3. **[P2] NopJobFireBizModel 使用 requireEntity()**：先鉴权再传给 store 执行状态变更
4. **[P2] 修复 Integer 自动拆箱**：添加 null 检查
5. **[P2] handleExecutionResult 增加重试/告警**：乐观锁失败时不要静默丢弃
6. **[P2] NopJobTask.xmeta 补充引擎字段限制**
7. **[P2] 修复文档中的 @BizAction 错误和聚合根分类错误**
8. **[P2] 清理 4 个幽灵 Web 页面目录**
9. **[P2] 修复 IJobScheduler suspend/pause 命名不一致**
10. **[P2] NopRetryJobRetryBridge 回填 retryRecordId**

## 本次审核盲区自评

1. **运行时行为未验证**：审计基于静态代码分析，未实际运行测试或部署验证
2. **前端页面未审计**：view.xml 的 UI 布局和交互逻辑未深入审查
3. **部署配置未覆盖**：deploy/sql/ 下的 DDL 和 Docker 配置未审计
4. **性能调优未涉及**：Scanner 间隔、批量大小、连接池等运行时参数未评估
5. **nop-job-app 的 Quarkus 集成**：仅检查了依赖声明，未深入验证运行时行为
