# nop-stream vs Flink 2.3.0 对标——质量充分性、设计目标达成与最优性裁定

> Status: resolved
> Date: 2026-09-30
> Scope: nop-stream 全部 10 子模块（HEAD = plan 368 收口后）对比 apache/flink release-2.3.0（c0f8d1a1e09，~/sources/flink 已从 1.20.0 更新）；按 vision 定位（声明式图模型驱动、可分布式、嵌入式/中小规模、最小控制面）评判
> Conclusion: **设计目标：在 vision 边界内达成（5 条成功标准成立，2 条带未收口保留项）；质量：定位内 4/5——接近充分但尚差一步，本轮对比新发现 5 项 P2 修复后可判定充分；最优性：架构选型在其定位下总体最优且有 Flink 2.3 自身演进的反向佐证，非全局最优，存在三个已知代价集中点与约 10 项低成本可采纳的 Flink 机制。**

## Context

- 回答三个问题：nop-stream 是否达到充分质量、是否满足设计目标、设计与实现是否最优。
- 对比基线：nop-stream 当前 HEAD（R4 审计 125 项发现、plan 368 修复 P0×1+P1×15+P2×11+治理 13 项之后）；Flink 最新稳定版 2.3.0（本次从 1.20.0 更新，含 2025-2026 的 2.x 演进：Sink V2 落地、DataStream V2 实验、CPSP/网络层重构）。
- 五个子系统对比报告（逐文件精读两侧 live 代码）：`09a`（checkpoint 容错 4/5）、`09b`（状态后端 4/5）、`09c`（网络/执行/编排 4/5）、`09d`（时间/窗口/CEP 3.5/5）、`09e`（API/连接器 3.5/5）。

## Analysis

### 子系统裁定汇总

| 子系统 | 评分（定位内） | 核心结论 |
|--------|------|---------|
| Checkpoint 容错 | 4/5 | 协议骨架与 Flink `SingleCheckpointBarrierHandler` 形态趋同；"single vs multi in-flight"差异比文档宣传小（nop 的 aligned 多 in-flight ≈ Flink coordinator 流水线）。差距集中在 4 个生命周期端点（C1-C4） |
| 状态后端 | 4/5 | key-group 模型语义等价且 nop 契约更强（框架强制 stableHash vs Flink 依赖用户 hashCode 契约）；JSON 中心取舍定位内成立，但 R4 全部状态类缺陷都落在「JSON 类型退化 × restore」接缝——该取舍的价格已实测现形 |
| 网络/执行/编排 | 4/5 | 所有简化均有显式裁决出处（无"忘了做"型差距）；LOCAL 背压与 Flink 等价；结构性差距集中在 JDBC-lease HA 与 ZK 级成熟度代差（定位内可接受） |
| 时间/窗口/CEP | 3.5/5 | 窗口语义还原度最高（allowedLateness/cleanup 公式与 Flink 2.3.0 逐字符一致）；CEP 内核逐类同构且 Flink 2.3 未裁剪 CEP（佐证 nop 保留正确）；扣分在 timer 无 key-group 分片、per-window state 泄漏（G-3）、迟到 side output 无公共 API（G-2） |
| API/连接器 | 3.5/5 | 「XDSL+Delta+canonical StreamModel」差异化价值真实成立（Flink V1→V2 双轨迁移反向证明模型层契约更稳）；语义门禁（capability 矩阵）是 Flink 没有的资产，但默认 STRICT 是负担；结构病根是「声明面 > 运行时面」 |

### 设计目标达成核对（vision §二 成功标准）

1. **Java API 完整管线正确执行**：达成（Phase 1 修复后路由/属主一致；示例经实际运行验证）。
2. **StreamModel 唯一 canonical 入口**：达成，且经 09e 对比确认为 Flink 不具备的真实差异化。
3. **Checkpoint 任意 durable epoch 恢复 + 端到端 exactly-once 可验证**：**带保留达成**——主路径成立，但 C1（savepoint 无对齐豁免）、C2（终止触发无队列化可跳过 terminal savepoint）、C3（完成后 ACK 集合不收缩→有界作业 checkpoint 超时循环）三个生命周期端点缺口未收口。
4. **DISTRIBUTED 多 TaskManager 三面分离**：达成（真实多 JVM 测试在位）；HA 面为 JDBC-lease 级而非 ZK 级，定位内登记。
5. **CEP 独立可用 + 统一状态后端**：达成（CEP-01 修复后 PT/ET 双模式排水闭环）。

### 对比新发现问题（不在 R4 清单，建议下轮修复立项）

**P2（影响"充分质量"判定）**：
- **G-3**：`WindowOperator` cleanup/purge 从不调用 `processContext.clear()` → `ProcessWindowFunction.clear` 死接口，per-window `ctx.windowState()` 随 checkpoint 永久泄漏（Flink `clearAllState` 有此调用）。
- **C3**：任务完成后 ACK 集合不收缩（`unregisterTask` 零生产调用）→ 多源有界作业后续每周期 checkpoint 撞超时 abort 循环。
- **C1**：savepoint 无对齐豁免——背压>1s 时静默降级 unaligned，破坏纯一致切点。
- **C2**：终止/保存点触发无队列化——撞 in-flight 周期 checkpoint 时跳过 terminal savepoint 直接完成。
- **NEW-A**：operator state 四模式重分布能力**无生产调用方**（rescale 路径 1:1、扩容置空）。

**P3 择要**：G-2 迟到 side output 无公共 API；NEW-B BROADCAST 重分布非并集语义；NEW-C remote 边 barrier 无插队（aligned 恒降级，正确性无损）；C4 输出侧在途数据无 transport 持久性校验条目；G-1 缺 ProcessingTimeSessionWindows；09e 的 9 项声明面缺口（union/sideOutput/FL-1 字段等 xdef 声明即拒绝）。

**低成本可采纳（约 10 项，Flink 已证机制）**：savepoint 强制 aligned（~15 行）；终止触发排队重试（Flink `CheckpointRequestDecider` 简化版）；per-checkpoint 排除已完成任务；`processContext.clear()` 接线；`WindowedStream.sideOutputLateData` 直通；RocksDB 内存管控预设（block cache/WriteBufferManager）；memory TTL 定期 sweep；BROADCAST 改并集（一行级）；commit 重试最小协议；`getSideOutput` API 闭合。

### 最优性评估

**成立的部分**：
- 全部简化有显式裁决出处（vision Non-Goals、stage 裁决记录），无"漂移型缺失"；
- Flink 2.3 自身演进反向佐证多项 nop-stream 选择：sink2 移除 GlobalCommitter（nop 裁剪正确）、DataStream V2 五年仍是 Experimental 双轨（模型层契约优于 API 层契约）、CEP 保留并补齐 serializer（nop 保留正确）；
- nop 独有资产真实：XDSL+Delta+canonical StreamModel、ProcessingGuarantee+capability 门禁（Flink 静默语义降级 vs nop 启动期 typed error）；
- key-group 属主公式 nop 比 Flink 更强（框架强制 vs 用户契约）。

**不最优的部分（三个已知代价集中点）**：
1. **JSON 中心序列化**：性能/类型安全代价在 restore 接缝集中爆发（R4 状态类缺陷全部在此族）；定位内可辩护，但需把 keyType/元素类型收敛为快照一等字段防同族再发。
2. **声明面 > 运行时面**：xdef 声明的能力宽于运行时接受（09e 列 9 项），每次撞上都是 fail-fast——方向正确但欠账持续产生验证成本。
3. **CEP 第二份簿记**：per-key timer 台账与主状态双写（CEP-01 正是其断链产物）——已裁定不回退，需 CI 不变式钉死。

## Conclusion

- **是否满足设计目标**：**是**——vision 5 条成功标准在定位内成立（2 条带保留项）；11 条设计不变量经本轮与 R4 双重复核无违反（C2 与"manifest durable 前 sink 不 commit"的张力已在 09a 标注为文档级澄清项，非违反）。
- **质量是否充分**：**接近充分（定位内 4/5），尚未可判充分**——R4 轮修复后核心语义面（窗口公式、CEP 内核、checkpoint 协议骨架、key-group 模型）已达到与 Flink 同级语义正确；但本轮对比新发现 5 项 P2（G-3/C3/C1/C2/NEW-A）属于"会咬人的生命周期端点"，修复并回归后可判定充分。这些项与低成本可采纳清单构成下一修复波次（R6）的自然范围，预计 1 个 plan 可收口。
- **设计实现是否最优**：**在其明确定位内：总体最优，非全局最优**——每一处对 Flink 的偏离都有记录在案的裁决理由，且多处被 Flink 2.3 的演进方向反向印证；但存在三个已知代价集中点（JSON 序列化接缝、声明面欠账、CEP 双簿记）与约 10 项"低成本、已证、值得抄"的 Flink 机制。最优性是动态的：不采纳上述低成本项，当前优势会逐步转为维护负债。

## Open Questions

- [ ] stableHash murmur 加扰是否值得做：改公式 = 全量 key-group 迁移，需 hashPolicy 版本字段绑定；小集群定位下可能不值得（09b Open Question）。
- [ ] Java API 默认 guarantee 是否从 STRICT 降档/warning-first：属 owner 决策（涉及 vision 约束 4 的语义声明哲学），09e 已给出正反两面。
- [ ] operator state 重分布（NEW-A）：生产接线还是显式降档为"rescale 时 operator state 仅 1:1"，需 owner 裁定后立项。

## References

- 本轮对比章节：`ai-dev/analysis/nop-stream/09a..09e-flink2.3-compare-*.md`（基线 release-2.3.0 @ c0f8d1a1e09）
- 前轮基线：`ai-dev/analysis/nop-stream/08-gap-analysis.md`（G1-G68，已混入 closed/deferred 注记，以本轮 09x 为准）；`ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/`
- 设计基线：`ai-dev/design/nop-stream/00-vision.md`（成功标准/约束/Non-Goals/不变量）
- 修复计划：`ai-dev/plans/368-nop-stream-audit-r5-defects-governance.md`（completed）
