# Wave 7 / Phase 3 E1–E6 实验裁定（数据表）

- Date: 2026-11-16
- Status: adjudicated（数据表在档，裁定见各节）
- Raw: `_tmp/w7-*.log`、`~/sources/*/_tmp/bench/*.json`（五项目基线见 `2026-11-16-wave7-bench-baseline.md`）

## E1 分档重跑（failed-only 子集）— **拒绝（默认关闭保留开关）**

| 场景 | 默认（全模块 fresh rerun） | failed-only |
|---|---|---|
| spring-batch-core wall | 818s | 1219s（复测） |

- 数据：spring-batch 的 rerun 成本由**单个失败类**（MongoDefaultBatchConfiguration，241–270s）主导——failed-only 子集(1 类)与全量 rerun(157 类)耗时几乎相同（子集 241s ≈ 全量 269s），节省 ≤28s，复测在运行方差内反而更慢。
- 假阳性对照：未观察到子集通过/全量失败 divergence（子集即复现失败）——机制 fail-safe 有效，但收益为零。
- 裁定：**拒绝采纳为默认**。开关 `nop.ib.moduleRerunMode=failed-only` 保留（默认关闭），单元语义有钉（fail-safe：子集通过仍由全量裁定）。
- 附带发现（登记 follow-up，非本 wave 采纳）：exit-2 重试全模块重执行 152 个已执行类（549s/818s=67%）是真正的杠杆——"仅补缺失类"需重设计重试语义，登记 Deferred successor。

## E2 worker 批调度网格 — **拒绝（无影响，量级不可见）**

| workerMaxClasses | zeebe-gateway-grpc wall |
|---|---|
| 1 | 34s |
| 5 | 34s |
| 默认 20 | 34s |

- 数据：69 类模块在 maxClasses 1/5/20 全格点 wall 无差（34s 平）——该钮控制 worker 轮换频率，不是并行度；并行度由 workerBudget/slots 决定且已饱和。
- 裁定：**拒绝**（实测语料上无 ≥20% 空间；无 fork-abort 模块可复现，flink-agents-134 为环境缺陷不可用作网格点）。无默认行为变更。

## E3 索引增量刷新 — **拒绝（优化目标不可见）**

- 数据：全量索引扫描基线 383 模块 4s（≈10ms/模块）；spring-batch/zeebe 画像中 selection+index <10s/818s (~1%)，五项目 bench 中不可见。
- 裁定：**拒绝**——增量刷新的收益上限 <1% wall，而正确性对照成本高（新鲜度失效即假绿面）。无实现残留（未写代码）。

## E4 并行模块 sweep — **数据确认默认行为（非新采纳）**

| 模式 | liteflow 4 模块切片（455 tests） | exit |
|---|---|---|
| 并行（默认） | 41s | 0 |
| 串行（moduleSlots=1） | 50s | 0 |

- 数据：1.22x@4 模块小切片；两侧结果集/计数完全一致（零假绿）；产物互斥已有 6A-5 B-6 证明。
- 裁定：并行已是默认（design 06 WR-D6）；实验补齐了 Deferred 27 的量化证据。无需变更。

## E5 大模块分批 rerun — **拒绝（默认关闭保留开关）**

| rerun 方式 | spring-batch-core wall |
|---|---|
| 单 fork（默认） | 818s |
| 32 类/批 ×5 forks | 882s（复测） |

- 数据：无崩溃场景可复现（zeebe-engine 8k 先例的语料不在可测集，fork-134 为环境缺陷）；分批增加 4 次 JVM boot + 唤散热身 → +8%。
- 裁定：**拒绝为默认**；开关 `nop.ib.rerunBatchSize` 保留（默认 0=关闭），合并语义有钉（任一批红则红）。

## E6 类级 discovery 缓存 — **裁定"已被既有实现覆盖"**

- 单元索引本身就是跨运行的 discovery 缓存：测试面=f(产物哈希)（index 持久化于 `.nop-ib/units/`，编译后刷新）。fork 内 platform bootstrap ~0.5–1s/fork 已由常驻 worker 池（design 06 WR-D1）摊薄。
- 裁定：**无新实现需要**（roadmap 的"正确性对照"对象已存在）；bench 数据（flink-agents 5.5x）证明该链路已受益。

## 总结

- 采纳为新默认：无（六项中 E4 确认既有默认、E6 确认既有覆盖、E1/E2/E3/E5 拒绝）。
- 保留实验开关（默认关闭，各有钉）：`nop.ib.moduleRerunMode`、`nop.ib.moduleSlots`、`nop.ib.rerunBatchSize`。
- 新登记 follow-up：exit-2 重试"仅补缺失类"语义重设计（E1 附带发现，potential −67% wall on failing big modules）。
