# nop-batch 审查审计记录 Round 2

> Date: 2026-08-16
> 方式：3 个全新独立 general subagent（与前轮不同实例）对升级/修正后的断言复核。主 agent 对分歧点直接读源码仲裁。
> 主报告：`../2026-08-16-nop-batch-design-and-concurrency-review.md`

## Agent R2-1：缺陷 5（CompletionException 状态映射）— CONFIRMED

- JDK 21 源码+运行时实证：`allOf.whenComplete` 的 err 必为 `CompletionException`（`encodeThrowable` 包装）；chunk future 上存的原始异常在 allOf 之后被包装。
- 精确化：
  - 主执行路径 SUSPENDED/CANCELLED/KILLED 三分支完全不可达；
  - `CANCEL_REASON_SUSPEND`/`CANCEL_REASON_SKIP`（ICancellable.java:14-15）全仓库无写点 → 两子分支无条件死代码；
  - setup 失败路径（BatchTask.java:128-131）传原始异常、结构可达但现实零触发；
  - 当前全仓库无外部 cancel 入口（BatchTaskGlobals 无 cancel API、NopBatchTaskBizModel 裸 CRUD）→ 潜伏缺陷。
- 修复建议：`onTaskComplete` 内 saveTaskState 之前 unwrap（仅入库判定用解包值；completeExceptionally/future 完成保留原值）。

## Agent R2-2：缺陷 6（失败行压实数据丢失）— CONFIRMED（P0）

六环节逐一验证全部成立：
1. `completeExceptionally` 同步内联触发 onAfterComplete（ExecutionContextImpl.java:121-127 → :151-167 调用线程直接 for 循环）；
2. :263 无条件 put true；压实循环只看 value 不看 exception → 失败 chunk 行被物理移出 TreeMap；
3. 失败不触发任何 cancel → 兄弟 chunk 继续压实，头部即兄弟行，越过已失踪失败行推进 completedRow；
4. 持久化两条路径都通（per-chunk save 读越界索引；最终 saveTaskState(true, err) 无条件写盘，EtlTableStateStore:111 / DaoBatchStateStore:161）；
5. 无阻断机制（"processingItems must be empty" 检查只在 err==null 路径触发）；
6. 附带确认：per-chunk save 先于 chunkContext.complete() → 盘上索引滞后一个 chunk。

完整时序推演（concurrency=2，chunk A 行1-100 失败、chunk B 行101-200 成功 → 落盘 index=200 → 重启跳过 1-200 → 行 1-100 永不重处理）成立。
边界条件：失败 chunk 需为 map 头部（主导交错场景）。

## Agent R2-3：缺陷 7（historyStore 写路径）— CONFIRMED（影响面收窄）

- `DaoBatchRecordHistoryStore.saveProcessed`（:76-79）空方法确认；`filterProcessed` 查 resultStatus=0 确认；
- 全仓库 grep 穷举（NopBatchRecordResult 93 处、nop_batch_record_result 55 处）：无任何写入方（BizModel 是管理端 CRUD 不在执行链路；sql-lib/deploy 无 INSERT）→ `nop_batch_record_result` 表无生产者；
- **重要修正：`JdbcKeyDuplicateFilter` 的空 saveProcessed 不算缺陷**——其 filterProcessed 查业务目标表 key，业务写入即 key 落库，去重闭环成立。缺陷仅限 DaoBatchRecordHistoryStore 路径；
- **文档 bug**：`docs-for-ai/03-modules/nop-batch.md:11` 宣称"记录级幂等：NopBatchRecordResult 表追踪每条记录状态"与实际不符；
- 断言 B（事务时序）CONFIRMED：consume scope（默认）下 saveProcessed 在事务提交后；process scope 下在事务内；
- 次要：buildRecordMap 相同 recordKey 静默覆盖去重。

## 两轮总结（共识达成）

- Round 1：14/16 断言确认，1 项推翻（Q3 方向），分歧 1 项由主 agent 源码仲裁。
- Round 2：3 项升级断言全部确认，附带 2 项影响面收窄、1 项文档 bug 发现。
- 最终清单：3 个 copy-paste bug + 3 条 P0 缺陷（fail-fast 缺失、取消状态映射失效、并发失败数据丢失）+ 1 条 P1（history 写路径空实现）+ 8 项 P2。
- 所有判定均有 file:line 证据，两轮 agent 相互独立（不同 task 实例），关键分歧由主 agent 直接读源码仲裁解决。
