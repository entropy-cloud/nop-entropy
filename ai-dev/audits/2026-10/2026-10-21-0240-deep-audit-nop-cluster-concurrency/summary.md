# 深度审核汇总报告 — nop-cluster 全族并发·异步·资源专项审计（CRA WI 4.2）

## 基本信息

- **审核单元**: `nop-cluster`（6 子模块族 7 源码根：core 54f/3,807 + rpc-cluster 9f/646 + nacos 4f/564 + sentinel 2f/299 + hazelcast-core 1f/50 + hazelcast-cache 1f/187 + admin 3f/24，合计 74 files/5,577 lines；手写口径不含 `_` 前缀与 target/，`_gen/` 残留 0）
- **审核日期**: 2026-10-21
- **执行维度**: C1 共享可变状态、C2 锁与同步、C3 异步链路、C4 线程池治理、C8 背压流控（roadmap 4.2 裁剪集）+ C10 测试保护力（C5/C6/C7/C9 不在维度集，overlay 线索 CL×21）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 120ff53d0d（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2478-cra-wi4.2-nop-cluster.md`（两轮对抗性审查通过后执行；R1 2 Major + 4 Minor 全修——**hazelcast 嵌套双根路径订正/CL-024 同族形态证伪→漂移复查口径/owner doc 零覆盖预注/锚点底稿与并发原语计数清单回填**；R2 1 Major + 1 Minor 全修——⑧口径与附录 C C2 基线矛盾/源码根 6→7 计数）
- **执行说明**: 全部批次串行派发（运营者要求）——C1+C2 合并派发 + C3/C4/C8 单批 + 深挖 1 轮 + 复核 2（合并批）+ C10 + 裁定；**owner doc 零覆盖**（module-groups.md 对三模块 0 提及，按 drift 登记收口）；sentinel/hazelcast/admin 自管原语全 0 预校准（不凑数扩面）。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 8（P1×4/P2×4） | +2（P3×2） | 10 | 2（C1-002/C2-001 同根同判 P1→P2、C1-007 P2→P3） | 0 |
| C2 | 2 | 4（P1×1/P2×2/P3×1） | +1（P2） | 5 | 1（C2-001 同根同判） | 0 |
| C3 | 2 | 11（P1×2/P2×5/P3×4） | +1（P3） | 12 | 1（C3-002 Broadcast 零接线 P1→P2） | 0 |
| C4 | 2 | 4（P1×1/P2×3） | +1（P3） | 5 | 2（C4-001 同案双轨 P1→P2 并入 C1-004 主登记、C4-002 P2→P3） | 0 |
| C8 | 2 | 10（P2×8/P3×2） | +1（P3） | 11 | 0（驳回 4 全为同案并入） | 4（C8-004/008/009/010 同案并入主登记，缺陷本体成立） |
| C10 | 1 + 独立复核 | —（输入=复核后缺陷清单） | — | 43 行矩阵全维持 + 缺保护 14（复核并入执行后 P2×4/P3×10） | 0 | 0 |
| **合计** | — | **37** | **+6 = 43** | **43** | 6 | 4（同案并入） |

R2 深挖五维度全部显式终止（+6 全 P2×1/P3×5 且收敛证据齐备——round2 逐文件清点、schedule 站点穷举复核、Sentinel slot 链对称性/DynamicLoadBalance 负结果）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 43 条保留缺陷 → 合并后 **22 张工单 + watch 1 = 9 缺陷族（MA-01…09，覆盖 29 条 = 主登记 9 + 吸收 20：复核已登记 7 + 本裁定新增扩员 13）+ 13 独立立案**。逐条落位无遗漏。
- 工单判级：**P0×0 / P1×4 / P2×13 / P3×5**；冲突裁定 4 处；7.1 移交 6 项（C4-001 双轨终裁、C2-005 潜在面复认、Assigner 零权重 owner 裁决、C8-001 活跃计数形态、globalTimer 族修复归属、owner doc 零覆盖收口）。
- 跨单元对账：与 cra-index WI 1.1–4.1 台账（133 条 P1）**零同站点重复**；家族关联 6 处单列（globalTimer 族/自续链断链族/future 永不完成族/监视器内外部代码族/shutdown 懒重建族/重试放大族）+ 闭环 2 项（**WI 2.1 CL-024 无同族形态闭环**、CL-021 点名不含本单元）+ 无同案确认 2 项。
- 裁定新增发现：CL-007=CL-001、CL-016=CL-002 两组复核遗漏的跨文件重复（叠加复核已登记 CL-003=CL-008）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | —（即时通道显式空执行） |
| P1 | 4（2 族 + 2 独立） | MA-01 elector 并发纪律族（C1-004 主：选主 doStop 复活竞态 + C4-001/C2-003/C3-004/C2-005/C4-002 五成员——allowRestart 宽窗口完整重选主/写 lease 行/双回路抢主）；C1-001 Nacos 事件线程原地改写 ServiceInstance（撕裂读 + _host 惰性缓存固化陈旧端点，12 读者站点）；C1-003 订阅初始化旧快照无版本守护回退新事件态；MA-07 ClusterRpcClient 重试链族（C3-001 主：重试回调异常吞没 future 永不完成 + Sentinel entry 泄漏复合，live 默认装配路径） |
| P2 | 13 | MA-02 cleanupTasks 族（条件化回升 P1）、MA-03/04/05、C8-001 LeastActive 恒 0、C8-002 重试零退避风暴、C8-007 Sentinel 规则棘轮、C2-005 等 |
| P3 | 5 | MA-02 尾部、C1-009/010、C3-012、C4-002/005 + C8-011 等 |

C10 另有缺保护登记 14 条（复核并入执行后 P2×4/P3×10，覆盖 43/43 闭合[含 C1-003 并入 C10-005 升 P2 处置]，全部映射随本体工单）：保护力矩阵 43 行**会红 0/假保护 6/无测试 37**——P1×4 全部无保护；反向钉死 4 处（2 硬钉 assertSame/权重语义 + 结构性假保护 + 弱守卫）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 43 条保留发现全部为**新发现**（cra-index WI 1.1–4.1 台账零同站点命中，nop-cluster 首次并发审计）；已知未修复 0、已修复 0。
- 现状分层：live×14、条件化·门控×8、休眠×1、潜伏/掩蔽×2、备查×2、去重并入×1。

## 关键发现摘要（P1 全清单，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C1-004（MA-01 主，成员 C4-001/C2-003/C3-004/C2-005/C4-002） | 选主 doStop 复活竞态：无锁 stop 与 synchronized onElectionCompleted 竞交——stop 后 leaderEpoch/electionPromise 复活「本节点是 leader」、监听器乱序（allowRestart 默认 true + 生产子类 DB I/O 窗口证实） | nop-cluster-core AbstractLeaderElector |
| 2 | C1-001（独立） | Nacos 事件线程原地改写共享 ServiceInstance（copyFromInstance 全字段非 volatile）——请求线程撕裂读 + _host 惰性缓存固化陈旧端点（12 读者站点失效面） | nop-cluster-nacos × core |
| 3 | C1-003（独立） | 订阅初始化旧快照无版本守护——事件 B 落在 getAllInstances 返回与 initInstances 之间即回退新事件态（复核补强：窗口不依赖 SDK 内部乱序） | nop-cluster-core naming |
| 4 | C3-001（MA-07 主，成员 C3-008/C8-008） | ClusterRpcClient.callAsync 重试回调内同步异常被吞（whenComplete 返回值丢弃）——返回 future 永不完成调用方永久挂起 + Sentinel entry 泄漏复合（live 默认装配路径证实） | nop-rpc-cluster |

## 盲区自评

- C5/C6/C7/C9 为 roadmap 4.2 裁剪维度：仅 overlay 线索登记（CL×21：并入工单 7/重复合并 4/独立线索 7/watch 3），未做完整口径审计；若 7.1 需完整面需补审。
- 外部 SDK 黑盒（nacos-client/sentinel/hazelcast client）按适配面审计，SDK 内部队列/线程黑盒。
- 选主测试结构性不可达（TestElector 不自续）——C1-004/C4-001 修复验收应写停机后零断言。
- MA-02 组族为本轮新增结构裁定待 7.1 追认；C4-001 双轨终裁留 7.1。
- R2 深挖五维度全终止（+6 全 P2/P3），无口径偏差。

## 与其他 roadmap 的重叠

- full-code-review-roadmap：无新增重叠面。
- 与 WI 1.1–4.1 台账（133 条 P1）零同站点重复；家族关联 6 处单列 + 闭环 2 项（CL-024 闭环为本单元对 WI 2.1 台账的显式回写输入）。
- security-audit roadmap：无重叠面。
