# 深度审核汇总报告 — nop-stream 状态与连接器并发·异步·资源专项审计（CRA WI 1.2）

## 基本信息

- **审核单元**: `nop-stream-state-connectors`（nop-stream-cep 74 files/11,750 lines + nop-stream-rocksdb 19/4,523 + nop-stream-connector 19/3,416 + connector-batch 9/1,238 + connector-jdbc 4/1,483 + connector-debezium 8/1,824 + connector-kafka 4/943 + format-avro 3/281，合计 140 files/25,458 lines）
- **审核日期**: 2026-10-14
- **执行维度**: C1 共享可变状态、C2 锁与同步、C3 异步链路、C4 线程池治理、C5 内存有界性、C6 资源生命周期、C7 长时运行、C8 背压流控、C10 测试保护力（C9 按 roadmap 1.2 裁剪）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 4d522b00ae（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2462-cra-wi1.2-nop-stream-state-connectors.md`
- **执行说明**: 初审 8 维度与部分深挖轮次由前一会话完成；因会话中断遗留的 round 产物按 append-only 原则合并保留（C3-013、C5-008~010、C6-010/011、C7-010/011、C8-006~010），续轮独立重推导收敛后并入。续派深挖/复核按运营者要求串行执行（一次一个子代理）。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留（含降级） | 降级 | 驳回 |
|------|---------|---------|---------|-------------------|------|------|
| C1 | 3 | 4 | +2 | 6 | 0 | 0 |
| C2 | 3 | 9 | +2 | 10 | 1（C2-004→P2） | 0 |
| C3 | 3 | 8 | +6（r2×4、r3×2） | 14 | 0 | 0 |
| C4 | 3 | 3 | +3 | 6 | 0 | 0 |
| C5 | 2 | 7 | +3 | 9 | 1（C5-003→P3） | 0 |
| C6 | 2 | 9 | +5 | 12 | 0 | 2（C6-002/003 泄漏链证伪） |
| C7 | 2 | 9 | +4 | 13 | 0 | 0 |
| C8 | 2 | 5 | +8 | 13 | 0 | 0 |
| C10 | 1 | —（输入=复核后缺陷清单） | — | 16（缺保护登记，全 P2） | 0 | 0 |
| **合计** | — | **54** | **+33 = 87** | **85 缺陷（含降级 2）+ 16 缺保护** | 2 | 2 |

全部 8 个缺陷维度均有显式终止声明（深挖轮次上限 10 轮内收敛，无触顶）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 85 条缺陷 → 合并后 **46 项 = 20 缺陷族（MA-01~MA-20）+ 26 独立立案**；另跨单元关联 2 条（C2-003、C2-010，挂 WI1.1 台账）不重复立项。
- 自洽核对：20 族吸纳 57 成员（37 个 ID 族内消号）+ 26 独立 + 2 跨单元 = 85 ✓。
- 裁定与复核提议的 4 处实质差异：① C2-008 = C7-012 同一缺陷（复核双方漏登，裁定新建 MA-17 归并）；② MA-18 新建同族（C8-010 + C8-13 checkpoint 时长预算两面）；③ MA-12 判同族相邻而非归并（C2-004 与 C1-003 修复不互盖）；④ C7-007 拆分消号（manifest 面→MA-05、锁面→MA-19）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | MA-01 kafka-2pc producer 全终止路径不关闭（正常成功路径即确定性泄漏） |
| P1 | 10 | gauge native UAF、CEP copyForSubtask 引用透传三面、registry 无超时×锁内 HTTP、2PC 持久化登记无保留策略、Debezium 绑定 globalWorker 池、Debezium 停止契约无终结保证、RocksDB 全量 JSON checkpoint 物化、Debezium 枚举器恢复双登记、Debezium offset 偏斜静默丢数、kafka 事务超时零裕度 |
| P2 | 26（含 10 族 + 16 独立） | gauge 无 tag 注册/WARN 无守卫、Avro HttpClient 泄漏/静默回落、reader 并发入口、2PC 缓冲无旋钮、RocksDB 迁移物化、manifest 锁无超时、CEP timer ledger 增长、checkpoint 预算无耦合、取消响应点缺失、关闭路径不对称、背压零遥测等 |
| P3 | 9 | 锁表只增不删、counter 标签无注销、持锁 I/O、理论风险、静默吞异常等 |

C10 另有缺保护登记 16 条（全 P2，与缺陷族重映射见 merge-adjudication.md §4.4）：P0/P1 缺陷 15 行矩阵中仅 1 行有钉死测试（C1-006，修复须同步反转断言）、5 行假保护、8 行无测试；现有测试集对本单元并发缺陷面保护力接近于零。

## P0 记录（即时通道）

| ID | 缺陷 | 修复路径 | 状态 |
|----|------|---------|------|
| C6-001（MA-01，成员 C3-001/C4-001/C7-002） | `KafkaTwoPhaseCommitSink` 的 transactional producer 在 DRAIN/CANCEL/FAILED 全部终止路径上无任何 close 调用（类不实现 AutoCloseable、基类与 runtime teardown 链均不可达 producer）——每个 kafka-2pc 子任务每次作业终止泄漏 1 个 producer（daemon network 线程 + broker 连接 + buffer.memory 缓冲池） | plan 2462 Phase 7 即时就地修复 + focused 回归测试（回退必红） | open（Phase 7 执行中） |

## 现状标注摘要（复核阶段对照历史审计与 git log）

- 已知未修复（历史已登记，本轮保留）：C1-001（←r4 R5-CEP-05）、C7-001/C3-002 族（←r6 AR-06）、C6-005（←r6 ST-11）、C6-007（←r6 CON-04）、C6-009（←2026-09-26 审计）、C3-011（2026-06-05 r2 修复的响应语义残留面）。
- 跨单元已知根因（挂 WI1.1 台账）：C2-003、C2-010（WI1.1-C1-001/C3-009 的受害者侧）。
- 已修复：0 条（基线 commit 后无触及 8 模块 src/main 的修复提交）。
- 其余均为新发现（本单元首次立项）。

## 关键发现摘要（P0 + P1 全清单，详见 merge-adjudication.md §4.2）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C6-001（P0） | kafka-2pc producer 全终止路径不关闭，确定性泄漏 | connector-kafka sink 生命周期 |
| 2 | C1-002 | RocksDB gauge 锁外取 dbRef→native UAF（SIGSEGV 窗口） | rocksdb metrics |
| 3 | C1-001 | CEP copyForSubtask 引用透传共享（NFA 条件图/ctx 槽位/udf 生命周期三面） | cep 算子复制 |
| 4 | C2-002 | Avro registry 无请求超时 × computeIfAbsent 锁内 HTTP（挂死+锁毒化） | format-avro 热路径 |
| 5 | C3-002 | 2PC 持久化登记无保留策略（JDBC 台账只增不删 + file manifest O(E) 放大） | connector-jdbc/file |
| 6 | C4-004 | Debezium 引擎无限阻塞任务常驻平台共享 globalWorker 池 | message-debezium 载体 |
| 7 | C3-014 | Debezium 停止/启动契约无终结保证（幽灵引擎三面） | message-debezium 载体 |
| 8 | C5-002 | RocksDB 默认全量 JSON checkpoint 整库物化进堆 | rocksdb snapshot |
| 9 | C3-004 | Debezium 枚举器恢复双登记→同表双引擎重复消费 | connector-debezium enumerator |
| 10 | C8-001 | Debezium reader checkpoint offset 领先已发射→恢复静默丢 ≤256 条/检查点 | connector-debezium reader |
| 11 | C8-006 | Kafka 2PC 事务存活期与 checkpoint 间隔零裕度→触界失败循环 | connector-kafka 事务 |

## 盲区自评

- `_gen/` 生成类按纪律排除；cep `model/` XLang 模型 bean 判定单线程装配但未逐行通读（三轮 C1 已 grep 排除写点）。
- 跨模块载体仅作上下文未立案：nop-message-debezium（DebeziumEngineWrapper/NopStreamOffsetBackingStore 本体——MA-06/MA-07 的修复首选落点）、nop-stream-core 基类（TwoPhaseCommitSinkFunction/MemoryKeyedStateBackend——已报送对应模块维度）。
- Debezium 引擎内部语义经 javap 反编译钉定 3.7.0.Final，未逐行读源码；上游版本升级可能改变 C3-014/C4-006 的前提（已在条目标注版本耦合）。
- C2-004 降级 P2 依赖「生产 addSplits 入口仅 SourceReaderOperator 两处桥接」的全量 grep；7.1 裁定时如发现其他接线或 DISTRIBUTED 落地应回升 P1。
- C8-006 的 Kafka 协调器惰性过期粒度未做实测（触发概率非机制问题）。

## 与其他 roadmap 的重叠

- nop-stream-independent-audit-roadmap（流引擎专项）：本单元 MA-06/MA-07/C3-004/C8-001 的载体修复落点在 nop-message-debezium，与该 roadmap 及 nop-message 模块审计存在潜在重叠，7.1 合并裁定时 cross-reference。
- security-audit / full-code-review roadmap：无重叠面。
