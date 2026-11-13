# 可观测性补齐清单（R-G 治理批产出物③）

> 定位：汇总 CRA campaign 各批新增/修复的指标与 gauge 面（指针化到各批 owner doc / 设计文档），并登记遗留缺口（G10/G16/G20/G44——四条 R-G successor 可观测项的权威登记位）。
> 落档日期：2026-11-12（plan 2567）

## 1. 各批新增指标/gauge 汇总（指针化）

| 批次（roadmap→plan） | 指标/gauge 面 | 权威指针 |
|---|---|---|
| R1-03（8.6→2498）Debezium/WS 卸载 | 心跳/超时预算、WS 推送共享池卸载后的池观测 | `ai-dev/design/concurrency/pool-facility-hardening.md` + `docs-for-ai/03-modules/nop-stream-connectors.md` |
| R1-05（8.7→2500）缓存失效链 | verdict 记忆化/maxAge 门观测、宽限判据 | `ai-dev/design/concurrency/cache-invalidation-single-point.md`；R4-32 墙钟换源后续见 plan 2566 |
| R2-05（8.14→2508）SSO/准入 | `nop.gateway.upstream-max-inflight` 许可池占用（UpstreamAdmission）+ 拒绝按落点 | `ai-dev/design/concurrency/hotpath-sso-refresh-async-and-gateway-retry-admission.md`；`docs-for-ai/02-core-guides/service-layer.md` |
| R2-10（8.19→2513）job rpcPoll | gauge 五面（失败预算/deadline 兜底/DB 回传/PollEntry 精简/gauge） | `docs-for-ai/03-modules/nop-job.md`；`ai-dev/design/concurrency/job-rpcpoll-termination-and-invoker-lock.md` |
| R4-08（8.48→2541）闸门束 | MA-06 remind 退避 gauge + WARN | `docs-for-ai/03-modules/nop-task.md`/`nop-wf.md`；plan 2541 |
| R4-09（8.49→2542）扫描 infra 束 | C4-007 scan-round-failures gauge；C7-010 SUSPENDED 滞留 gauge（all\|stale） | plan 2542；`ai-dev/design/concurrency/graph-scan-resilience-task-fencing-and-ledger-concurrency-contract.md` |
| R4-20（8.62→2554）peri 束 | 链 lag 探测/健康观测（C7-008 前提推翻后形态） | plan 2554；`ai-dev/design/concurrency/persistence-peri-chain-pubsub-batch.md` |
| R4-31（8.71→2565）code-index 束 | loader isTruncated 被动标志+结构化 WARN；CODEINDEX-RT-001/002/003 契约 | `docs-for-ai/03-modules/nop-code.md` 新契约节 |
| R4-32（8.72→2566）墙钟批 | 诊断时间戳保留墙钟（1.6 :199/:207/:328）、TimeOut nanoTime 期限 | `docs-for-ai/04-reference/source-anchors.md` R4-32 锚点行；plan 2566 |
| R4-25（8.65→2559）cluster 束 | MA-05 异常统计新断言面（rejected future+traceEntry） | `ai-dev/design/concurrency/cluster-lb-sentinel-batch.md` |

> 说明：完整红绿/钉证据在各批 `_tmp/red-green-ledger-*.md` 总册；本清单不复制，逐项可经 owner doc/设计文档回溯。

## 2. 遗留缺口（R-G successor 可观测四项——权威登记位）

| Finding | 组 | 缺口内容 | 处置裁定 |
|---|---|---|---|
| 1.2-C8-008 | G10 | 连接器积压深度零遥测 | **维持 deferred**——登记本清单为唯一承接形态；实施归 C9/可观测专项轮（性能取向成员 1.2-C8-011 同走 CL/域留档轨， cra-index P2-G10 行） |
| 1.5-MA-09 / 1.5-MA-17 / 1.5-C8-015 | G16 | 闸门 warn-once+stats 零消费、javadoc 失真、nop-wf/nop-task 全模块零指标注册 | **维持 deferred**——登记本清单；指标注册属增强，无缺陷本体，实施随 1.5 后续治理轮 |
| 1.6-C8-008 | G20 | nop-retry/nop-tcc 过载信号零指标（伞形缺口） | **维持 deferred**——登记本清单；实施随 1.6 后续治理轮 |
| 2.3-C8-006 / 2.3-C9-006 | G44 | gateway 子面零指标家族、INFO 全量 query 日志口径 | **维持 deferred**——登记本清单；日志口径治理无产品行为变更诉求前不动 |

> 缺口承接声明：本批为上述四组 successor 的**登记收口**（「不存在被静默降级的 deferred」——对账逐条留痕见 `r-g-successor-reconciliation.md`）；不在本批实施指标新增（Non-Goal：产品行为零变更）。
