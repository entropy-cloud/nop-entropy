# 深度审核汇总报告 — nop-stream-core-runtime 并发·异步·资源专项审计（CRA WI 1.1）

## 基本信息

- **审核单元**: `nop-stream-core-runtime`（`nop-stream/nop-stream-core` 439 files/57,135 lines + `nop-stream/nop-stream-runtime` 132 files/29,922 lines）
- **审核日期**: 2026-10-14
- **执行维度**: C1 共享可变状态、C2 锁与同步、C3 异步链路、C4 线程池治理、C5 内存有界性、C7 长时运行、C8 背压流控、C9 并发高性能、C10 测试保护力（C6 按 roadmap 1.1 裁剪）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit b3a9c76bed（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2461-cra-wi1.1-nop-stream-core-runtime.md`

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级/并入 | 驳回 |
|------|---------|---------|---------|---------|----------|------|
| C1 | 2 | 11 | 0 | 10 | 1 降级 | 0 |
| C2 | 2 | 8 | 3 | 9 | 1 降级 | 1 |
| C3 | 2 | 8 | 1 | 9 | 1 降级 | 0 |
| C4 | 3 | 8 | 5 | 13 | 1 降级 | 0 |
| C5 | 3 | 9 | 3 | 11 | 1 降级 | 0 |
| C7 | 3 | 10 | 9 | 14 | 5 并入（失效面补全） | 0 |
| C8 | 5 | 7 | 8 | 14 | 1 降级 | 0 |
| C9 | 2 | 8 | 2 | 7 | 0 | 3 |
| C10 | 1 | — | —（输入=复核后缺陷清单） | 25（缺保护登记） | 0 | 0 |
| **合计** | — | **69** | **+31 = 100** | **115**（90 缺陷（含降级 4 条）+ 25 缺保护） | 11 降级/并入 | 4 |

## 合并裁定（详见 merge-adjudication.md）

- 合并前 90 条缺陷（C9-009 复核补录后更正；不含 C10 缺保护 25 条）→ 合并后 **61 条**：同一缺陷并入 24 条、同族合并 4 族（FAM-1 materialization 无界、FAM-2 SideOutputRegistry、FAM-3 流控参数契约、FAM-4 线程命名）、独立 40 条。
- 最终分布：**P0×0、P1×10、P2×27、P3×24**（C10 另有缺保护登记 25 条，全 P2，关联 ID 已重映射至主条目/族）。
- 现状标注（复核阶段对照历史审计）：新发现为主体；已知未修复 7 条（C1-003←check/stream-core.md 2026-08-20、C4-001←r5 CC-05、C4-003←r6 CC-05、C5-001←A5 deferred、C5-006←AR-10 残留、C8-013←r5 CC-05、C9-006/008←r1-perf/r6 半边修复）；已修复 0 条（多条历史修复为本轮发现的兄弟残留，逐条见各维度复核结论）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 10 | source coordinator 并发、LOCAL 执行死锁、无界账本×2、共享 poller 饥饿、孤儿协调器、standby 双写、poll 全量物化、CachingTableLookup 刷新无界、materialization 族 |
| P2 | 27 | 注册表泄漏/作用域、checkpoint 序列化峰值、metrics 基数、REST 无界读、缺保护登记（C10）等 |
| P3 | 23 | 命名契约、理论风险、已声明边界的性能面 |

## 关键发现摘要（合并后 P1 全清单，详见 merge-adjudication.md `## 合并后缺陷清单`）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C1-001 | SplitEnumerator 被任务线程无锁直调，快照撕裂/双份分配（←C2-001、C3-001） | core source coordinator |
| 2 | C1-006 | 任务分配账本只增不删（removeTaskAssignment 零调用方，←C7-002） | runtime cluster registry |
| 3 | C4-001 | LOCAL 执行固定池=核数 + 有界输出队列互等死锁（←C8-013，终裁 P1） | core task executor |
| 4 | C4-002 | CachingTableLookup 刷新池无界+无 in-flight 去重（←C3-002、C8-014） | core lookup |
| 5 | C4-009 | OpsJobManager.submit 失败窗口孤儿 coordinator + 同 jobId 双活 | runtime ops |
| 6 | C5-010 | PollingJdbcMessageService 无 LIMIT 全量物化 backlog OOM（←C8-002） | runtime launch |
| 7 | C7-011 | standby 降级不取消在途 checkpoint persist，僵尸第二写者 | runtime checkpoint |
| 8 | C8-003 | msg_queue INSERT-only 无界账本 + WI6 门控不可达（←C7-003） | runtime launch transport |
| 9 | C8-011 | launch 共享 2 线程 poller：数据面积压垄断 → 控制面饥饿（←C4-004） | runtime transport |
| 10 | FAM-1 | materialization store 无界 + overflow-bypass 背压旁路（C5-001 主 ←C7-001、C8-001） | core execution |

## P0 即时通道记录

**本单元无 P0 发现**（初审、深挖、复核、合并裁定全程零 P0；已按 plan 2461 Phase 7 记录为条件分支的空执行）。历史 P1 亦无达到 P0 级的新证据。

## 测试保护力（C10）

- 保护力矩阵覆盖 31 行（16 P1 + 15 关键 P2，按根因去重 24 个）：**有保护 3、假保护 1（TestResultPartitionOverflowBypass 钉死 overflow-bypass 语义，修复须同步改写）、无保护其余全部**。
- 16 条 P1 无一具备完整回归保护；缺保护登记 25 条（C10-001…C10-025，全 P2），修复批次须按 plan 2461 引用清单同步补测试（R4+ 批次口径）。

## 修复方向建议（供 7.1 批次划分）

1. **R1 候选（基础设施族，一次修复多单元受益）**：LocalSourceCoordinator 锁纪律（C1-001）、CachingTableLookup（C4-002）、ClusterRegistry 账本（C1-006）、SideOutputRegistry（FAM-2）——均为 core 平台面，下游 stream 全系受益。
2. **R2 候选（runtime 治理面）**：OpsJobManager 孤儿（C4-009）、standby persist fencing（C7-011）、poller 饥饿（C8-011）、poll 无 LIMIT（C5-010）、msg_queue 账本（C8-003）。
3. **R3 候选（LOCAL 执行模型）**：TaskExecutor 死锁（C4-001）+ materialization 族（FAM-1，含假保护测试改写）。
4. 缺保护登记随各缺陷修复同步补测试（Minimum Rules #25 口径）。

## 本次审核盲区自评

- C6（资源生命周期）按 roadmap 1.1 裁剪，连接器/存储的资源获取-释放面未独立盘点（部分由 C5/C7 覆盖）。
- connector SPI 实现族（nop-stream-connector-*）属 WI 1.2；SysDaoMessageService 超出单元边界未审（C3-008 备注移交）。
- 未做真实大规模压测（按方法论以小规模参数化推演为准）。
- owner doc `nop-stream.md` 缺数值化规模包络节（C5 复核附议），OOM 判断以代码声明 + 设计文档为参照。

## 归档

- 目录：`ai-dev/audits/2026-10/2026-10-14-0130-deep-audit-nop-stream-core-runtime-concurrency/`
- 维度文件 ×9 + merge-adjudication.md + dispatch-log.md + 本 summary
- 台账：`ai-dev/audits/cra-index.md`（P1×10 已登记，P2/P3 裁决归 roadmap 7.1）
