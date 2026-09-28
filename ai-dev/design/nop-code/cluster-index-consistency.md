# 契约：集群分片构建的原子发布与一致性语义

> Status: adopted（2026-09-28，N6.4）
> Decided in: plan `ai-dev/plans/nop-code/21-n6-4-atomic-publish-consistency.md`
> Consumers: 集群 worker 编排层（后续 WI）、N6.3 基座（indexFileSet/ShardPlanner/WorkspaceManager）、查询面（BUILDING 状态语义）

## 1. 构件

| 构件 | 职责 |
|------|------|
| `IndexShardPlanner`（N6.3） | 确定性分片清单（互斥/完备） |
| `ClusterWorkspaceManager`（N6.3） | repo checkout 工作区 |
| `ICodeIndexService.indexFileSet`（N6.3） | 单分片执行原语（单事务子集索引） |
| `ShardBuildCoordinator`（N6.4） | 构建状态机 + 分片完成登记 + 原子发布 |
| `nop_code_shard_ledger`（N6.4） | 分布式共享的分片完成登记（唯一键 indexId+shardId） |

## 2. 事务边界

- **单分片事务**：`indexFileSet` 在一个事务内完成其文件子集的 delete-before-reindex——分片级原子；**不存在跨分片事务**。
- **发布点事务**：`completeShard` 检测到全部分片 COMPLETED 时，在同一事务内翻转 `nop_code_index.status`（BUILDING→COMPLETED）并物化全局度量（`nop_code_graph_metric`）——已发布索引必然带完整度量。
- 登记写入（ledger）与分片数据写入分属不同事务；ledger 是**意图与进度记录**，不是数据一致性机制。

## 3. 可见性语义（BUILDING 期间）

- 分片写入**即时可见**（共享 DB），无暂存区——BUILDING 期间查询读到的是"部分但自洽"的索引：每个已写入分片的数据完整、未写入分片的数据缺席。
- `nop_code_index.status = BUILDING` 是辨识面：查询方（编排层/调用方）据其判定索引未发布。平台不屏蔽 BUILDING 索引的查询（屏蔽属产品裁定，见 Non-Goals of plan 21）。
- 状态机：`COMPLETED/（无状态）→ BUILDING（begin）→ COMPLETED（全部分片完成，原子发布）`。重开构建（begin）从任何状态进入 BUILDING 并重置 ledger。

## 4. 故障恢复

- **分片重跑幂等**：`indexFileSet` 对同文件集 delete-before-reindex；`completeShard` 幂等（重复登记无害，已发布的完成不再触发副作用）。
- **worker 崩溃**：其分片在 ledger 中保持 PENDING——编排层重新派发该分片（indexFileSet + completeShard）即可，无需回滚。
- **僵尸 BUILDING**：构建被永久放弃时，编排层重新 `beginShardBuild`（重置 ledger）或直接全量 `indexDirectory`（其置 COMPLETED 覆盖 BUILDING）。
- **并发 completeShard**：唯一键 (indexId, shardId) upsert 保证登记安全；最后完成者执行发布（事务串行化保证恰好一次翻转+物化）。

## 5. 与既有裁定的衔接

- N6.1（graph-db-backend-decision）：全局算法不下推——发布点物化即其"索引期计算"边界在集群形态的延伸。
- N6.3：分片执行原语不变；发布协调为正交步骤（indexFileSet 不感知 ledger）。
- N1.2/N1.3：`nop_code_graph_metric` 物化与失效语义不变；发布点的物化与既有自愈路径同机制。

## 6. 明确不做

- 跨分片事务（2PC）；BUILDING 查询屏蔽；worker 编排协议。分别属：不可能单机保证的分布式边界（文档钉死即可）、产品裁定、后续集群 WI。
