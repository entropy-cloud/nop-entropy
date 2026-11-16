# Wave 7 / Phase 2 瓶颈画像（spring-batch-core + zeebe-gateway-grpc）

- Date: 2026-11-16
- Status: open
- 口径: 带时间戳 debug 流（`date +%s` 行前缀 + `nop.ib.debugPerClass`），spring-batch-core 全模块 906 tests / 157 类 / 4+ worker；zeebe-gateway-grpc 取 6B-3 同口径 debugPerClass 数据。Raw: `_tmp/w7-profile-sb.log`（会话 /tmp 快照，摘要入库）。

## spring-batch-core 阶段分解（wall 818s）

| 阶段 | 耗时 | 占比 | 说明 |
|---|---|---|---|
| selection+index+startup | <10s | ~1% | model 哈希 + 索引加载，可忽略 |
| worker 执行（157 类并行） | ~270s | ~33% | 逐类 timeMs 总和 2099s，有效并行 ~7.8x |
| 差分重跑（rerun-gate） | ~270s | ~33% | exit=1 → 全模块 fresh fork（157 类完整重跑） |
| exit-2 重试重执行 | ~549s | ~34%* | 5 个抽象基类持续 missing → Maven test-compile + 全模块再 fork 一次（152 个已执行类重复执行） |

\* 阶段有重叠/缓冲抖动，量为级判断。

单类病态：`MongoDefaultBatchConfiguration` 268s = worker 阶段的 ~50%（预存 Mongo 环境红，双侧同红）。

## zeebe-gateway-grpc 分解

- worker 逐类总和 411s / 69 类，wall ≈ 8-10min（含 fork 启动 69+ worker 轮换），top: LongPollingActivateJobsTest 26.2s（健康长尾，非病态）。
- 零 rerun（exit=0，缺陷 29 修复后）：无重跑阶段开销。

## Top-3 瓶颈结论（数据背书）

1. **rerun/retry 家族（重跑 + exit-2 重试重执行）**：占失败模块 wall 的 ~66%（549+270 / 818）。E1（failed-only 子集，Mongo 场景子集即复现 → 跳过全量重跑，预算节省 ~270s）与 exit-2 重试只补缺失类（而非全模块重跑）是两个候选杠杆；后者按 roadmap 属 E1/E5 家族的延伸，登记为 E1 附带发现。
2. **病态单类**：单类占 worker 阶段 50%（Mongo 268s）。并行度已饱和；任何调度实验（E2）对此无解——这是环境失败，不属于调度优化面。
3. **健康模块无瓶颈**：zeebe 零 rerun、长尾健康 → 快路径开销（selection/index）<1%，E3（增量索引）与 E6（discovery 缓存）的目标开销在实测中已不可见。

## 对 E1–E6 的先验

- E1: 高潜力（spring-batch 场景节省 ~33% wall）——实测裁定。
- E2: worker 网格在 zeebe（健康长尾）与 spring-batch（病态类）上预期影响小——网格仍实测。
- E3: 全量索引重扫实测 <10s（383 模块 4s 基线），优化目标不可见 → 预期拒绝（数据结论）。
- E4: 并行已默认启用（WR-D6）；串行对照量化收益即可。
- E5: 分批 rerun 只针对"大批单 fork 崩溃"场景（zeebe-engine 8k 先例）；spring-batch 的 rerun 269s 不崩——分批收益需崩溃场景，实测一次对照。
- E6: 单元索引本身就是跨运行的 discovery 缓存（测试面=f(产物哈希)）；fork 内 platform bootstrap ~0.5-1s/fork 属 worker 常驻（design 06）已优化面 → 裁定"已被既有实现覆盖"。
