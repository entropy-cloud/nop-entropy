# nop-batch 审查审计记录 Round 1

> Date: 2026-08-16
> 方式：4 个独立 general subagent 并行分片核查（互不可见彼此结论），对主报告每条断言给出 CONFIRMED/PARTIAL/REFUTED + 源码证据。
> 主报告：`../2026-08-16-nop-batch-design-and-concurrency-review.md`

## Agent A：Bug 1 / Bug 2 / 缺陷 11a（计数器、监听器、retry null policy）

| 断言 | 判定 | 备注 |
|---|---|---|
| Bug 1 setHistoryItemCount/incHistoryItemCount 写错字段 | CONFIRMED | 补充影响面：仅 history store + 恢复模式场景受影响 |
| Bug 2 onConsumeEnd 注册到 onChunkTryEnd | CONFIRMED（含修正） | 无 retryPolicy 时监听器完全不会触发（fireChunkTryEnd 只在 RetryConsumeHelper 中调用）；有 retryPolicy 时每次重试错误触发一次 |
| 缺陷 11a retryPolicy==null 无限热重试 | CONFIRMED（可达性收窄） | builder 层有 null 保护，仅直接 new 消费者类传 null 触发 |

补充发现：fireChunkTryBegin/End 与 retry 消费者强耦合（无 retry 场景下死代码）；retryConsume 空 retryItems 静默吞异常。

## Agent B：Bug 3 / 缺陷 6（skip 双消费、resume 语义）

| 断言 | 判定 | 备注 |
|---|---|---|
| Bug 3 skip() aggregator 模式双倍消费 | CONFIRMED（影响升级） | resume 时行 N+1..2N 静默丢失；文件剩余 ≤N 整段丢失；奇数剩余抛 IllegalStateException；正确写法 aggregate(item) |
| 缺陷 6（原编缺陷 5）completedIndex at-least-once | PARTIAL→升级 | 发现失败 chunk 行仍被标记 done + 兄弟 chunk 压实越过 → at-most-once 数据丢失路径（round-2 完整确认） |

补充发现：filter+saveState 行号空间错位（completedIndex 统计过滤后行，skip 按原始行跳，欠跳→重复处理）；onConsumeEnd bug（独立复现，与 Agent A 交叉印证）。

## Agent C：缺陷 4 / 缺陷 8（fail-fast、state store 竞态）

| 断言 | 判定 | 备注 |
|---|---|---|
| 缺陷 4 chunk 失败不通知兄弟线程 | CONFIRMED（影响修正） | 有限 loader：继续处理剩余数据（非无限循环）；阻塞 loader：FAILED 永不落库+线程泄漏；仅 concurrency>1 显现。allOf 语义经 JDK 实测 |
| 缺陷 8 DaoBatchStateStore 双实例竞态 | CONFIRMED | 新任务路径双 INSERT（SID seq 无冲突、无唯一索引）；已有路径 version 乐观锁兜底（GenSqlHelper WHERE version）；synchronized 单例锁跨任务瓶颈 |

补充发现：**CompletionException 包装使 getTaskStatus 的 BatchCancelException 分支成为死代码**（cancel/suspend 状态永不落库）→ 升级为独立缺陷（round-2 确认）；setHistoryItemCount bug（独立复现，与 Agent A 交叉印证，但其对 inc 行的判定有误，主 agent 复核源码纠正：set 与 inc 均写错）。

## Agent D：缺陷 9 / 缺陷 11d、12（partition dispatch、次要项）

| 断言 | 判定 | 备注 |
|---|---|---|
| 缺陷 9（原编缺陷 7）fetcher 并发 + 跨页乱序 | CONFIRMED | 补充：内置 loader 均有内部 synchronized(state) 兜底，风险集中在自定义/DSL loader；safeLoad 的 synchronized(ctx) 在异步模式每次 load 新建 ctx——完全无效 |
| Q1 async processor 异常挂起 | PARTIAL | 同步抛异常直接冲出 consume()，不会挂起；超时仅保护"返回但未 countDown"场景 |
| Q2 EtlTaskStateStore 全量写 | CONFIRMED（升级） | processItemCount 双重累加被持久化为 processedCount 再恢复 → 跨 resume 累积膨胀 |
| Q3 history 与事务原子性 | REFUTED（方向反了） | consume scope 下 saveProcessed 在事务提交后（非提交前）；且 **saveProcessed 本身是空实现**——"resume 跳过未成功记录"不可能发生 |
| Q4 limitHash | CONFIRMED（降级） | IntHashMap 支持负 key，功能无实害 |
| Q5 失败 chunk 计数 | CONFIRMED（收窄） | 默认配置失败 chunk 贡献 0，膨胀仅 retry/skip 链场景 |

补充发现：**historyStore saveProcessed 空实现 = 记录级幂等写路径整体未实现**（升级为独立缺陷）；AsyncFetch fetch 线程在 semaphore.acquire 永久阻塞（取消时线程泄漏）；removePartition 是 dead code；WithHistory contains O(n²)；PartitionDispatchQueue 账目/锁经核查无问题（验证性结论）。

## Round 1 共识与分歧处理

- 14/16 断言获得确认（含 4 处影响面修正）。
- 分歧 1：Agent C 声称 incHistoryItemCount 正确 → 主 agent 直接读源码复核（BatchTaskContextImpl.java:312-314 写 processItemCount），确认 Agent A 与原报告正确。
- 升级 3 项进入 round-2 复核：CompletionException 状态映射、失败行压实数据丢失、historyStore 空实现。
