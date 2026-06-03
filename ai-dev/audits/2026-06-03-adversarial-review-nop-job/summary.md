# Adversarial Review Summary: nop-job (2026-06-03)

## 基本信息

- **审核模块**: nop-job
- **审核日期**: 2026-06-03
- **审核类型**: 对抗性审查（开放式发现导向，6 轮深挖）
- **审查方法**: 从代码异常信号出发，重点验证 2026-05-18 审计修复质量及 R1~R5 修复状态，并在第 6 轮从先前审查盲区（CronExpression 内部、Trigger 语义、PartitionResolver、RPC Invoker、Worker 错误路径、ORM 索引）出发发现新问题

## 修复验证结果（累计）

### R1~R5 修复验证（第 6 轮重新验证）

先前 AR-1~AR-35 的 35 项发现中，**30/35 已修复**：

| Round | 修复状态 | 已修复 IDs | 未修复 IDs |
|-------|---------|-----------|-----------|
| R1 | 7/7 | AR-1, AR-2, AR-3, AR-4, AR-5, AR-6(P3), AR-7(P3) | — |
| R2 | 6/9 | AR-8, AR-9, AR-10, AR-11, AR-12, AR-13 | AR-14(P3), AR-15(P3), AR-16(P3) |
| R3 | 2/2 | AR-17, AR-18 | — |
| R4 | 4/6 | AR-19, AR-20, AR-21, AR-22 | AR-23(P2→已修复), AR-24(P2) |
| R5 | 9/11 | AR-25,26,28,29,30,33,34,35 | AR-27(P2→部分修复), AR-32(P3) |

**总修复率**: 30/35 (86%)

### 仍未修复（5 项，均为 P3 或已知设计限制）

- **AR-6 (P3)**: Planner parallel 路径 setActiveFireCount(0) 死写
- **AR-7/Prior F4 (P3)**: maxFailedCount 硬编码为 0 — 无 ORM 列提供值
- **AR-14 (P3)**: copyMap 返回原始引用而非副本
- **AR-15 (P3)**: findFirstErrorTask 优先级不一致
- **AR-16 (P3)**: RpcBroadcastTaskBuilder 不设置 taskPayload

### 部分修复（2 项）

- **AR-27 (P2)**: CronCalendar skip-ahead 逻辑存在但仍无最大迭代保护
- **AR-31 (P2)**: updateTask 失败有 WARN 日志但 silent early-return 路径仍无日志

### 已知设计限制（2 项）

- **AR-24/Prior F9 (P2)**: NopRetryJobRetryBridge 返回 null — 异步重试无法同步获取真实 ID
- **Prior F14 (P3)**: JobFireResult.CONTINUE 字段/方法名冲突

## 新发现汇总（6 轮共 47 项）

### Round 6 发现（盲区深挖：CronExpression、Trigger 语义、PartitionResolver、RPC Invoker、Worker 错误路径、ORM 索引）

| ID | 严重程度 | 一句话摘要 |
|----|---------|-----------|
| AR-36 | **P1** | handleExecutionResult 不检查 fire 状态 — 已取消的 fire 可被标记为 SUCCESS |
| AR-37 | **P1** | RpcJobInvoker RPC 调用传入 null 超时 — 无客户端超时保护 |
| AR-38 | **P1** | nop_job_fire 缺少 (jobScheduleId, fireStatus) 复合索引 — 活跃 fire 查询全表扫描 |
| AR-39 | P2 | Unique key 阻止同一时间不同 triggerSource 的 fire |
| AR-40 | P2 | HandleMisfireTrigger 对 OnceTrigger 无效 — misfire 阈值被忽略 |
| AR-41 | P2 | invoker.invokeAsync 返回 null 被静默当作 SUCCESS |
| AR-42 | P2 | JobPartitionResolver 每次扫描都查询 naming service — 无缓存 |
| AR-43 | P2 | CronExpression.getTimeAfter 每次创建新 GregorianCalendar — GC 压力 |
| AR-44 | P2 | JobScheduleStoreImpl 内部 helper 仍使用 updateEntityDirectly — cancel/reset 无版本保护 |
| AR-45 | P3 | CronExpression.equals() 忽略 timeZone — 不同时区比较为相等 |
| AR-46 | P2 | JobPartitionResolver 首次调用信任 naming service — 启动时集群不稳 |
| AR-47 | P3 | RpcBroadcastTaskBuilder.emptyIfNull() 死代码 |

## 合并严重程度分布（Round 1~6）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 3 (新) | fire 状态竞态, RPC 无超时, 缺失索引 |
| P2      | 7 (新) | unique key 误拦截, OnceTrigger misfire, null promise, naming 缓存, GC 压力, helper 版本保护, 首次信任 |
| P3      | 3 (新) | equals 忽略时区, 死代码, CronCalendar 遗留 |

## 总评

nop-job 模块经过六轮对抗性审查，R1~R5 的 35 项中 30 项已修复（修复率 86%），R6 新增 12 项。模块在事务安全性（乐观锁覆盖）、状态机正确性（cancel/dispatch 版本检查）、Calendar 防御编码等方面显著提升。R6 的发现主要集中在先前审查未触及的盲区。

### 最值得关注的 3 个方向

**1. Worker 结果处理与 fire 状态的竞态 (AR-36, P1)**

Worker 的 `handleExecutionResult` 只检查 task 状态不看 fire 状态。在 cancel + worker result 的时序窗口下，已取消的 fire 可能被重新标记为 SUCCESS。这是 AR-20 修复（dispatch 状态机覆盖）之后的状态机正确性残留——dispatch 路径已有版本保护，但 worker 的异步回调路径没有。

**2. RPC 调用无超时 (AR-37, P1) + 缺失索引 (AR-38, P1)**

两个独立的 P1 问题：RPC 调用传入 null 超时导致资源泄漏（hang 的 RPC 占用连接池），活跃 fire 查询缺少 `(jobScheduleId, fireStatus)` 复合索引导致全表扫描。两者都在 10x 规模下显著影响可用性，且修复成本很低。

**3. CronExpression 性能 (AR-43, P2) + PartitionResolver 无缓存 (AR-42, P2)**

CronExpression 每次调用创建 GregorianCalendar（重量级对象），PartitionResolver 每次扫描查询远程 naming service。两者叠加在 10k+ schedule 部署下会产生显著的 GC 压力和网络开销。

## 优先修复建议

### 高优先级（P1）

1. **[AR-36]**: `handleExecutionResult` 在写入 task 结果前检查 fire 状态
2. **[AR-37]**: `RpcJobInvoker` 传入 `schedule.getTimeoutSeconds()` 作为 RPC 超时
3. **[AR-38]**: 添加索引 `IX_NOP_JOB_FIRE_SCHEDULE_STATUS (jobScheduleId, fireStatus)`

### 中优先级（P2）

4. **[AR-40]**: HandleMisfireTrigger 对 ONCE 触发器检查返回值是否在可接受窗口
5. **[AR-41]**: 将 null promise 当作错误而非成功
6. **[AR-42]**: PartitionResolver 添加短期缓存（5-10 秒 TTL）
7. **[AR-43]**: CronExpression 复用 Calendar 实例
8. **[AR-44]**: 内部 helper 改用 `tryUpdateManyWithVersionCheck`
9. **[AR-39]**: hasWaitingFire 检查 triggerSource 或调整 unique key
10. **[AR-46]**: 首次调用返回不稳定

### 低优先级（P3）

11. **[AR-45]**: CronExpression.equals() 加入 timeZone 比较
12. **[AR-47]**: 删除死代码 emptyIfNull
13. **[AR-27 residual]**: CronCalendar 添加最大迭代保护

## 去重信息

- 2026-05-18-adversarial-review-nop-job (R1+R2): 31 项发现，已验证状态
- 2026-05-18-deep-audit-nop-job-full: 154 项发现（系统审计）
- 2026-06-03-deep-audit-nop-job: 36 项发现（21 维度系统审计）
- 本报告第 6 轮覆盖先前 5 轮明确标注的盲区：CronExpression 内部、HandleMisfireTrigger、JobPartitionResolver、RPC Invoker、Worker 错误路径、ORM 索引
