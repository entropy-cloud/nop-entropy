# 深度审核汇总报告 — nop-persistence 外围并发·异步·资源专项审计（CRA WI 2.5）

## 基本信息

- **审核单元**: `nop-persistence-peri`（nop-persistence 聚合器下九模块：nop-dbtool 7f/1,832 + nop-db-migration 72f/3,877 + nop-orm-migration 20f/3,462 + nop-cdc 1f/11 + nop-nosql 37f/3,460 + nop-orm-drivers 6f/726 + nop-orm-geo 12f/700 + nop-orm-pdm 9f/1,654 + nop-orm-rpc 1f/913，合计 165 files/16,635 lines；手写口径不含 `_` 前缀与 target/，`_gen/` 内无非下划线残留）
- **审核日期**: 2026-10-19
- **执行维度**: C1 共享可变状态、C3 异步链路、C6 资源生命周期、C7 长时运行、C8 背压流控（roadmap 2.5 裁剪集）+ C10 测试保护力（C2/C4/C5/C9 不在维度集；维度集外线索 overlay 登记 CL×26）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 458897d249（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2472-cra-wi2.5-persistence-peri.md`（两轮对抗性审查通过后执行；R1 修 4 Major——五状态机错挂订正/差异链归 dbtool/降档 carve-out，R2 零 Major 置 active）
- **执行说明**: 全部批次串行派发（运营者要求）；nop-cdc 实测零审计面（CdcConstants 空接口 11 行 + nop-cdc-debezium 纯 pom）；nop-orm-migration 为运行期触发的多线程流水线（启动期降档 carve-out，计划预注①）；基线初版 glob 缺陷于 C1 批 1 后修复，全部批次按口径纪律人工复现兜底。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2（4 初审批次） | 14（P1×7/P2×6/P3×1） | +1（C1-015 P3） | 15 | 0 | 0 |
| C3 | 4（单批） | 7（P1×4/P2×3） | +5（C3-008/009 P1、C3-010/011/012 P2） | 12 | 0 | 0 |
| C6 | 2（单批） | 5（**P0×1**/P1×3/P2×1） | +0 | 5 | 0 | 0 |
| C7 | 3（单批） | 12（P1×5/P2×6/P3×1） | +3（C7-014 P1、C7-013/015 P2） | 15 | 1（C7-005 P1→P2，NosqlCache 零生产实例化） | 0 |
| C8 | 2（单批） | 6（P1×1/P2×4/P3×1） | +1（C8-007 P2） | 7 | 0 | 0 |
| C10 | 1 + 独立复核 | —（输入=复核后缺陷清单） | — | 54 行矩阵全维持 + 缺保护 39（全 P2） | 0 | 0 |
| **合计** | — | **44** | **+10 = 54** | **54** | 1 | 0 |

C3 第 4 轮为矩阵穷举收敛轮（标识符对齐审计 32 站点）；全部维度显式终止。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 54 条保留缺陷 → 合并后 **35 张本单元工单 = 12 缺陷族（吸收 17 条，覆盖 29 条）+ 23 独立立案**；另 watch-only 1 条（C8-006）+ 跨单元随案 1 条（C7-012 随 WI 1.2 MA-05）。逐条落位无遗漏（54 = 29+23+1+1）。
- 与复核提议不一致/冲突裁定 2 处：①C1-005 双主张（C7-003⊃ vs C3-001 指针）裁归 MA-05，两族共享 runningJobs 站点登记同 diff 协调；②线索 C1-CL-007 双主张主处置归 C7-002 工单。裁定员新增合并 3 组（C6-003+C1-004 停止协议族、C8-003+C8-004 同批、C1-014+C1-015 P3 族）。
- 跨单元对账：与 cra-index WI 1.1–2.4 台账（91 条 P1）台账级同案仅 2 处（C7-012→WI 1.2 MA-05 随案、C6-005↔WI 2.1 C1-001 设施/使用双轨）；历史前案同案 2 处（MA-08 收口 nosql D03-02、MA-01 收口 check/nosql-cdc 2026-08-19 [P2]）；家族关联单列 11 项；其余零重复立项。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | MA-03 成功路径不停链（DONE 后链线程/binlog 连接永久滞留 + 幽灵写者回写已晋级目标表） |
| P1 | 13（6 族 + 7 独立） | MA-01 PubSub 订阅管理竞态、MA-02 PubSub 投递 IO 线程内联（开放裁定暂计 P1）、MA-04 链停止协议不可验证、MA-05 状态行守卫与编排单飞、MA-06 链死亡不可见/fail-fast 无实现、MA-10 startFallbackChains 部分启动；独立：C1-003 迁移历史表 check-then-act、C1-007 shardSelector 非 volatile 热替换、C3-008 lag 探测键错位、C3-009 relocate 路由 querySpace 错位、C7-002 五状态机零自环、C7-004 写门单 JVM 开门、C7-014 断线重连丢订阅 |
| P2 | 20（5 族 + 15 独立） | MA-07 VERIFY 全量 checksum 规模包络、MA-08 写-TTL 两步写非原子（收口 D03-02）、MA-09 computeIfAbsent 非原子、MA-11 驱动层批写无 chunk 上界；C3-010/011/012 错位同族、C7-005/006/008/009/013/015、C1-001/002/008/010、C6-005、C8-005/007 等 |
| P3 | 1 | MA-12 可变集合经 public getter 逃逸族 |

C10 另有缺保护登记 39 条（全 P2，4 条随本体可降 P3，全部映射随本体工单不独立立项）：保护力矩阵 54 行**会红 0/假保护 30/无测试 24**——唯一 P0 与全部链生命周期 P1 保护力为零（增量链子系统零测试区）；nosql-lettuce 行为测试 docker-gated 默认归零；弱反向钉死 2 处（RP-1 钉住「冲突必须覆盖」、RP-2 钉住「静止=追平」）。

## P0 记录（即时通道）

本单元 **P0 = 1**：**C6-002（MA-03 主登记）**——搬迁成功路径（VERIFY/SWITCH/DONE）从不调用 `stopAll`（全模块唯一调用点在 `abort`），与 `IncrementalJobManager` javadoc「阶段推进由 stopAll 停止」直接矛盾；DONE 后 M 条链线程、binlog/JDBC 连接、engine/offset store/checkpoint 执行器永久滞留，且写门仅拦本 JVM ORM DML，源库域外写入经存活链以 keyed upsert 持续回写已晋级的线上目标表。复核四环穷举核验维持 P0（含补强证据：SWITCH 相位唯一出边为 DONE，confirmSwitch 后 abort 被相位机拒绝——成功路径自 SWITCH 起链作业在进程内彻底无停止手段）。与 C3-003 同案合并（同根因同修复点），合并工单取 P0。处置：按 plan Phase 7 即时通道修复。

## 现状标注摘要

- 54 条保留发现中：新发现 48 + 已知未修复 6 + 已修复 0。
- 已知未修复：C1-013/C3-007/C7-011 ← `2026-05-20-deep-audit-nop-nosql-post-plan23` D03-02（写-TTL 两步写非原子，三面同案）；C6-004/C6-005 ← `check/nosql-cdc.md` 2026-08-19 前案（cancel/subscribe 竞态 [P2] 未修 + 缩容面）+ C6-005 另涉 WI 2.1 C1-001 设施主责；C7-012 sink 面 ← WI 1.2 MA-05（跨单元随案）。
- git log 证实本单元九模块无审计前修复提交。

## 关键发现摘要（P0 + P1 全清单，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 0 | C6-002（MA-03，成员 C3-003） | 成功路径永不停止增量链作业：DONE 后资源永久滞留 + 幽灵写者回写目标表 | nop-orm-migration 编排器 × 链管理器 |
| 1 | C1-011/C3-004/C6-004（MA-01，主登记 C6-004 已知未修复） | PubSub 订阅管理竞态：cancel/suspend/resume 绕过监视器，订阅静默失效/复活双投递 | nop-nosql-lettuce |
| 2 | C8-001（MA-02，成员 C3-005/C7-010；开放裁定暂计 P1） | PubSub 消费回调在共享 Redis IO 线程内联执行：慢消费者队头阻塞→断链→静默丢消息 | nop-nosql-lettuce × lettuce-core |
| 3 | C6-003+C1-004（MA-04） | 链停止协议不可验证：thread 字段晚发布使 join 空转、abort 读 stale-null 跳过 stopAll、stopAll 仅 cancel 不等待 | nop-orm-migration |
| 4 | C7-003（MA-05，成员 C1-005/006/009） | 状态行全列覆盖无版本谓词 + 7 编排入口零单飞/fencing：abort×confirmSwitch 背离终态、双跑双链 | nop-orm-migration |
| 5 | C7-001（MA-06 主登记，成员 C3-002；先修 C3-008） | 链死亡不可见：getFailure 零调用方，lag=0 门禁把死链判追平，VERIFY 放行缺数目标 | nop-orm-migration |
| 6 | C3-001+C6-001（MA-10） | startFallbackChains 部分启动无补偿 + jobName 丢 querySpace 句柄覆盖：孤儿链不可停 | nop-orm-migration |
| 7 | C1-003（独立 #3） | 迁移历史表全守卫非原子 check-then-act：多节点并发启动 DDL 重复执行 + 永久重试循环 | nop-db-migration |
| 8 | C1-007（独立 #4） | rebuildRouting 非 volatile 热替换 shardSelector：SWITCH 开门后请求线程无限期读旧路由 | nop-orm-migration × nop-orm |
| 9 | C3-008（独立 #7） | lag 探测 connector 名与链注册名错位：rescale 主场景 lag=0 门禁观测量恒空 | nop-orm-migration |
| 10 | C3-009（独立 #8） | relocate 路由 querySpace 硬编码空串：增量链 filter 恒 false 100% 丢事件、FULL 落回源侧 | nop-orm-migration |
| 11 | C7-002（独立 #13） | 五状态机 ALLOWED 零自环：javadoc 承诺的 fix-forward 路径自身走不通，一次瞬时故障全量报废 | nop-orm-migration |
| 12 | C7-004（独立 #14） | 写门 updateConfigValue 进程内裸写：多实例拓扑下门状态不收敛 | nop-orm-migration |
| 13 | C7-014（独立 #20） | PubSub 断线自动重连后 Redis 侧订阅全丢且无重订阅无检测：静默失联直至重启 | nop-nosql-lettuce |

## 盲区自评

- `_gen/` 与 target/ 按纪律排除；nop-cdc 零审计面（空接口占位）与 nop-orm-drivers 三纯 pom（es/mongo/redis）显式登记。
- C2/C4/C5/C9 为 roadmap 2.5 裁剪维度：仅以 overlay 线索登记（CL×26：并入工单 16/独立线索 10/驳回 0），未做完整口径审计；若 7.1 需完整面需补审。
- orm-migration 的并发 P1 均为时序推演到行级 + 相位机/句柄失效面论证，未做并发复现实验；lettuce-core 判定两处采信字节码反汇编（7.5.2.RELEASE）。
- MA-02 严重度为开放裁定（单元内建议 P1；若采信零生产接线降档先例则 P2）——7.1 终裁。
- relocate 形态零测试覆盖（e2e 仅 no-incremental rescale）；docker-gated 测试未在本次执行（`nop.test.docker.enabled` 缺省关）。
- dbtool/geo/pdm/rpc/tdengine 五根初审+深挖两轮干净（C3/C6/C8 干净判定含行为性论证），不排除更深层组合形态。

## 与其他 roadmap 的重叠

- full-code-review-roadmap：无新增重叠面（C6-005 双前案已登记设施/使用双轨）。
- 与 WI 1.1–2.4 台账（91 条 P1）零新增重复立项（台账级同案仅 C7-012 随案、C6-005 双轨，均单列不重复计数）。
- security-audit roadmap：无重叠面。
