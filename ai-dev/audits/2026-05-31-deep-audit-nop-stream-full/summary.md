# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-stream（Nop 平台流处理引擎）
- **审核日期**: 2026-05-31
- **执行维度**: 全部 21 个维度
- **目标范围**: nop-stream 下 9 个子模块（5 个已实现、4 个 placeholder），423 个主代码 Java 文件（~92K 行），212 个测试文件

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|------|------|------|
| 01-依赖图 | 1 | 2 | 2 | 0 | 0 |
| 02-模块职责 | 1 | 5 | 5 | 0 | 0 |
| 03-API表面积 | 1 | 3 | 3 | 0 | 0 |
| 04-ORM模型 | 1 | 0 | 0 | 0 | 0 |
| 05-生成管线 | 1 | 0 | 0 | 0 | 0 |
| 06-Delta定制 | 1 | 0 | 0 | 0 | 0 |
| 07-BizModel | 1 | 0 | 0 | 0 | 0 |
| 08-IoC配置 | 1 | 0 | 0 | 0 | 0 |
| 09-错误处理 | 1 | 6 | 6 | 0 | 0 |
| 10-XDSL | 1 | 1 (P3) | 1 | 0 | 0 |
| 11-XMeta对齐 | 1 | 0 | 0 | 0 | 0 |
| 12-GraphQL | 1 | 0 | 0 | 0 | 0 |
| 13-安全权限 | 1 | 2 | 2 | 0 | 0 |
| 14-异步事务 | 1 | 5 | 4 | 1 | 0 |
| 15-类型安全 | 1 | 5 (P2) + 7 (P3) | 12 | 0 | 0 |
| 16-测试覆盖 | 1 | 5 | 5 | 0 | 0 |
| 17-代码风格 | 1 | 2 | 2 | 0 | 0 |
| 18-文档一致性 | 1 | 5 (P2) + 7 (P3) | 11 | 1 | 0 |
| 19-命名一致性 | 1 | 2 | 2 | 0 | 0 |
| 20-跨模块契约 | 1 | 4 | 4 | 0 | 0 |
| 21-测试有效性 | 1 | 5 | 5 | 0 | 0 |
| **合计** | | **60** | **59** | **1** | **0** |

**深挖轮次说明**：由于初审阶段已充分覆盖所有文件，且大部分发现的性质决定了深挖不会产生新类型的发现，本次审计跳过了深挖追加轮次，直接进入独立复核阶段。

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 18 | 文档过时(5)、错误处理(3)、类型安全(5)、跨模块契约(2)、安全(1)、模块边界(1)、事务(1) |
| P3 | 41 | 代码风格(2)、测试质量(5)、命名(2)、import 风格(2)、低优先级类型安全(7)、低优先级错误处理(3)、文档细节(7)等 |

## 关键发现摘要（P2 级别，共 18 条）

### 架构与模块边界
1. **[01-02] runtime 依赖 nop-dao (provided)，架构文档未声明** — 设计文档 §2.1 只写了 runtime → core，未记录 nop-dao
2. **[02-03] WindowAggregationOperator 放在 core 但包含完整运行时逻辑（834 行）** — 与设计文档定义的 core 职责（模型/图/计划层）不符

### 错误处理
3. **[09-01] MalformedPatternException 全部 13 处调用未传递 .param(ARG_PATTERN_DETAIL)** — 错误消息无诊断价值
4. **[09-02] CEP 子模块核心类使用原生 IllegalStateException/UnsupportedOperationException（7 处）** — 未遵循 Nop 平台 ErrorCode 模式

### 安全
5. **[13-01] Fencing Token 在 INFO 日志中明文输出（5 处）** — 分布式协调的安全凭证不应在 INFO 级别暴露

### 事务
6. **[14-02] TwoPhaseCommitSinkFunction.finishCommit 在 synchronized 块外调用 commit()** — 可能 commit 已回滚的事务，违反 2PC 协议

### 文档-代码一致性
7. **[18-01] window-design.md 已知限制 #1 过时** — apply/aggregate/reduce 已实现但仍标注为抛异常
8. **[18-02] window-design.md 已知限制 #2 过时** — SimpleAccumulator bug 已修复但未回注
9. **[18-03] window-design.md 已知限制 #5 不正确** — EventTimeSessionWindows 已存在但文档说无 Session Window
10. **[18-04] state-management-design.md 已知限制 #6 过时** — snapshotState 已实现但文档说被注释掉
11. **[18-05] time-model-design.md §9 与 §7 自相矛盾** — §7 说已集成，§9 说未集成

### 命名与术语
12. **[19-01] NopCepErrors 错误码字符串与常量名语义不匹配** — 常量说"不支持"，错误码说"支持"

### 跨模块契约
13. **[20-01] connector 直接实例化 nop-batch-core 实现类** — 超出 optional 依赖隔离边界
14. **[20-02] runtime 的 nop-dao provided 依赖导致类加载脆弱性** — 加载 JDBC 类本身就会触发 NoClassDefFoundError

### 测试覆盖
15. **[16-01/21-01] CountTrigger 核心逻辑完全未被测试** — onElement 计数-触发行为零覆盖
16. **[16-02] TestWindowOperatorBasic 名不副实** — 仅测试数据结构属性，未测试算子行为
17. **[21-02] TestStreamGraphGenerator assertNotNull 滥用（30+ 处）** — 不验证任何属性

### 类型安全
18. **[15-01~05] 多处泛型类型安全问题** — CepPatternBuilder raw type、CepOperator Object.class、MemoryKeyedStateBackend Map<String,Object> 等

## 总评

nop-stream 作为一个流处理引擎框架（非标准 Nop 业务模块），整体代码质量**良好**。模块依赖方向清晰，无循环依赖。核心引擎（StreamGraph/JobGraph/PartitionedPlan 五层管线）设计合理，CEP 引擎（NFA + Pattern + SharedBuffer）实现完整。错误处理体系（40+ ErrorCode）规范，事务和 fencing 机制设计良好。

**最需要关注的领域是文档维护**：5 条 P2 发现指向设计文档中的已知限制未及时更新（Plan 51 修复后未回注），这会误导后续开发者。其余 P2 发现分布在错误处理（.param 缺失）、跨模块契约（optional/provided 隔离不足）和测试覆盖（核心触发器零覆盖）方面。

没有发现 P0 或 P1 级别的问题。初审中标记的 14-01 竞态条件经独立复核后降级为 P3（代码缺陷存在但当前生产路径不可触发）。

## 优先修复建议

1. **高优先级**：更新设计文档中的过时已知限制（18-01~05），工作量小但影响大
2. **高优先级**：为 MalformedPatternException 的 13 处调用添加 .param() 上下文（09-01）
3. **中优先级**：将 fencing token 日志降为 DEBUG（13-01）
4. **中优先级**：修复 TwoPhaseCommitSinkFunction 的 commit 锁粒度（14-02）
5. **中优先级**：为 CountTrigger 添加核心逻辑测试（16-01）
6. **低优先级**：统一 CEP 子模块的错误处理为 ErrorCode 模式（09-02）
7. **低优先级**：将 JDBC 相关类移到独立子模块（20-02）

## 本次审核盲区自评

1. **性能基准测试**：未评估流处理引擎的吞吐量和延迟表现
2. **分布式端到端测试**：仅在代码层面审计了分布式执行路径，未验证实际多进程部署的正确性
3. **外部引擎适配**：nop-stream-flink 为 placeholder，未评估 Flink 后端的适配设计
4. **XDSL 声明式编排**：nop-stream-flow 为 placeholder，未评估 Delta 定制的实现
5. **大规模状态恢复**：未验证 GB 级别状态快照的恢复正确性和性能
6. **跨 JDK 版本兼容性**：仅审计了 Java 21 下的代码，未验证其他 JDK 版本

## 不适用的维度说明

以下维度因 nop-stream 不是标准 Nop 业务模块而不适用（零发现）：
- **04-ORM模型**：无 ORM 实体，使用原始 JDBC
- **05-生成管线**：仅 CEP 模型类有 XDEF 生成，已确认正确
- **06-Delta定制**：无 Delta 文件
- **07-BizModel**：无 BizModel 类
- **08-IoC配置**：使用 Java SPI 而非 NopIoC
- **11-XMeta对齐**：无 XMeta 文件
- **12-GraphQL**：无 GraphQL API
