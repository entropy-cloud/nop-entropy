# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-job（定时任务调度模块）
- **审核日期**: 2026-05-18
- **执行维度**: 全部 20 个维度
- **目标范围**: nop-job 模块 11 个子模块（api, core, codegen, dao, meta, service, coordinator, worker, web, app, retry-adapter）的所有手写代码和模型定义

## 模块概况

nop-job 是 Nop 平台的标准业务模块之一，实现定时任务调度功能。模块包含约 20,000 行 Java 代码（其中约 4,800 行为生成代码），11 个子模块，8 个 ORM 实体，3 个 BizModel 类。

非标准子模块（超出标准 domain-module-pattern）：
- **coordinator**: 调度协调器（扫描、分发、超时检测、完成处理）
- **worker**: 任务执行器（任务认领、执行、回报）
- **retry-adapter**: 重试引擎集成适配器

## 执行统计

| 维度 | 名称 | 深挖轮次 | 初审发现数 | 备注 |
|------|------|---------|-----------|------|
| 01 | 依赖图与模块边界 | 1 | 3 | 1×P1, 2×P2 |
| 02 | 模块职责与文件边界 | 1 | 2 | 2×P2 |
| 03 | API 表面积与契约一致性 | 1 | 7 | 含类型安全问题 |
| 04 | ORM 模型与实体设计 | 1 | 8 | 1×P1, 7×P2/P3 |
| 05 | 生成管线完整性 | 1 | 4 | 1×P1, 3×确认 |
| 06 | Delta 定制合规性 | 1 | 4 | 1×P2 发现 |
| 07 | BizModel 规范遵循 | 1 | 2 | 2×P2 |
| 08 | IoC 与 Bean 配置 | 1 | 6 | 6×P2 |
| 09 | 错误处理与错误码 | 1 | 5 | 1×P1, 4×P2 |
| 10 | XDSL 与 XLang 正确性 | 1 | 12 | 2×P1, 10×P2/P3 |
| 11 | XMeta 与 BizModel 对齐 | 1 | 6 | 1×P1, 5×P2 |
| 12 | GraphQL 与 API 层 | 1 | 6 | 主要为确认项 |
| 13 | 安全与权限模型 | 1 | 6 | 2×P1, 4×P2 |
| 14 | 异步与事务模式 | 1 | 8 | **2×P0**, 2×P1, 4×P2/P3 |
| 15 | 类型安全与泛型使用 | 1 | 10 | 主要为安全模式确认 |
| 16 | 测试覆盖与质量 | 1 | 11 | 1×P1, 10×P2 |
| 17 | 代码风格与规范 | 1 | 24 | 全部 P2/P3 |
| 18 | 文档-代码一致性 | 1 | 13 | **2×P0**, 4×P1, 7×P2 |
| 19 | 命名与术语一致性 | 1 | 8 | 1×P1, 7×P2 |
| 20 | 跨模块契约一致性 | 1 | 9 | 含硬编码契约问题 |

**合计**: 约 154 条发现项（含确认项和正面观察），其中：
- P0: 4 条
- P1: 约 17 条
- P2: 约 52 条
- P3/确认: 其余

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 4 | 事务竞态条件(2)、文档-代码严重不一致(2) |
| P1 | 17 | 契约漂移、依赖违规、安全缺失、测试覆盖缺口 |
| P2 | 52 | 类型安全、代码风格、测试覆盖、命名一致性 |
| P3/确认 | 81 | 代码风格细节、正面模式确认、低优先级改进建议 |

## 关键发现摘要

### P0 发现

| 编号 | 维度 | 文件 | 一句话摘要 |
|------|------|------|-----------|
| [14-01] | 异步与事务 | JobPlannerScannerImpl.java | tryLock 事务边界设计不合理导致竞态条件风险 |
| [14-02] | 异步与事务 | JobDispatcherScannerImpl.java | 存在相同的竞态条件风险（与 14-01 同模式） |
| [18-01] | 文档一致性 | docs-for-ai/ 多个文档 | 设计文档与实际实现严重不一致 |
| [18-02] | 文档一致性 | docs-for-ai/ 多个文档 | 设计文档中描述的功能状态与代码不匹配 |

### P1 发现

| 编号 | 维度 | 文件 | 一句话摘要 |
|------|------|------|-----------|
| [01-01] | 依赖图 | nop-job-dao/pom.xml | DAO 层反向依赖 Core 层，违反标准分层架构 |
| [04-01] | ORM 模型 | nop-job.orm.xml | 外键列缺少独立索引 |
| [04-03] | ORM 模型 | nop-job.orm.xml | NopJobTask 缺少到 NopJobFire 的关系定义 |
| [05-01] | 生成管线 | gen-orm.xgen | codegen 脚本引用生成产物而非源模型 |
| [09-01] | 错误处理 | CronExpression.java | 构造函数丢失原始异常链 |
| [10-01] | XDSL | 5 个 xbiz 文件 | entityName 引用不存在的 Java 实体类 |
| [10-02] | XDSL | NopJobPlan view.xml | 引用不存在的 xmeta 路径 |
| [11-01] | XMeta 对齐 | 多个 xmeta | 状态字段通过通用 save 可直接修改 |
| [13-01] | 安全 | NopJobScheduleBizModel | BizModel 方法缺少权限注解 |
| [13-02] | 安全 | NopJobFireBizModel | BizModel 方法缺少权限注解 |
| [14-03] | 异步与事务 | 多个 Scanner | @SingleSession 与 @Transactional(REQUIRES_NEW) 混用 |
| [14-04] | 异步与事务 | JobTaskStoreImpl | tryLockTasksForExecute 事务边界问题 |
| [16-01] | 测试 | DefaultJobCancelHandler | 取消通道完全未验证（零测试覆盖） |
| [18-xx] | 文档一致性 | 多个 docs-for-ai 文档 | 文档中引用的文件路径/签名与实际不一致 |
| [19-01] | 命名 | JobCoreErrors | 错误码前缀与模块名不一致 |

## 总评

nop-job 模块整体质量处于**中等偏上**水平。作为 Nop 平台的标准业务模块，它在以下方面表现良好：

1. **生成管线完整**: model → codegen → dao → meta → service → web 链路正确闭合，生成产物与源模型一致。
2. **BizModel 基本合规**: 3 个 BizModel 类正确继承了 CrudBizModel，构造函数正确调用了 setEntityName()。
3. **IoC 注入规范**: 所有 @Inject 字段均为 protected，无 Spring 注解误用，无 private 字段注入。
4. **测试覆盖广度好**: coordinator 和 worker 模块有较多的测试文件，覆盖了核心调度逻辑。
5. **Delta 使用克制**: 仅有少量 Delta 文件，使用方式正确。

主要风险集中在：

1. **事务竞态条件（P0）**: coordinator 和 dispatcher 的 Scanner 中，tryLock 操作的事务边界设计存在竞态条件风险。这是整个模块最需要优先修复的问题。
2. **文档-代码不一致（P0）**: ai-dev/design/nop-job/ 中有 7 个设计文档，部分文档描述的功能状态与实际实现严重不一致，可能误导后续开发。
3. **DAO→Core 反向依赖（P1）**: dao 模块依赖 core 模块中的错误码和触发器计算类，违反标准分层规则。虽然当前可以工作，但增加了模块间耦合。
4. **测试覆盖缺口（P1）**: DefaultJobCancelHandler（取消通道）和 RpcBroadcastTaskBuilder（广播任务构建）零测试覆盖，批处理场景未被测试。
5. **安全注解缺失（P1）**: BizModel 方法缺少权限注解，依赖全局权限控制而非方法级权限。

## 优先修复建议

### 立即修复（P0）

1. **修复 tryLock 竞态条件**: 重构 JobPlannerScannerImpl 和 JobDispatcherScannerImpl 的事务边界，确保 lock 和后续操作在同一事务内完成。
2. **更新设计文档**: 审查 ai-dev/design/nop-job/ 下所有设计文档，将状态标记与实际实现对齐。

### 短期修复（P1）

3. **DAO→Core 依赖**: 将 dao 中使用的 core 类（错误码、常量）下沉到 api 模块，或将 Store 实现移至 core 模块。
4. **ORM 模型补全**: 为外键列添加索引，补充 NopJobTask→NopJobFire 关系定义。
5. **补充测试**: 为 DefaultJobCancelHandler 和 RpcBroadcastTaskBuilder 添加单元测试。
6. **权限注解**: 为 BizModel 的公开方法添加 @BizPermission 注解。
7. **xbiz 修复**: 修正 5 个 xbiz 文件中引用不存在的 entityName。

### 排期修复（P2）

8. **错误处理规范化**: CronExpression 中补全异常链，消除 IllegalArgumentException。
9. **状态常量去重**: JobScheduleStoreImpl 和 JobFireStoreImpl 中的状态常量应引用 dict 或 ErrorCode。
10. **代码风格**: 修复 import 分组顺序、行宽等风格问题（可批量处理）。

## 深挖与复核统计

### 深挖轮次

| 维度 | 深挖轮次 | 初审发现 | 追加发现 |
|------|---------|---------|---------|
| 01 | 2 | 3 | 1 (retry-adapter 不必要依赖 nop-ioc) |
| 04 | 2 | 8 | 0 (未发现新问题) |
| 09 | 2 | 5 | 3 (catch 块吞异常等) |
| 10 | 2 | 12 | 若干 (beans.xml bean 类验证) |
| 11 | 2 | 6 | 2 (Store 绕过 xmeta 校验) |
| 13 | 2 | 6 | 0 (无新发现) |
| 14 | 2 | 8 | 4 (worker 异步回调事务、超时检查事务) |
| 16 | 2 | 11 | 0 (无新发现) |
| 18 | 2 | 13 | 若干 (invoker-design 不一致、source-anchors 失效) |
| 19 | 2 | 8 | 0 (无新发现) |

### 独立复核结果（Oracle）

经 Oracle 独立复核后的重大调整：

| 原始编号 | 原始级别 | 复核结论 | 调整后级别 | 复核理由 |
|---------|---------|---------|-----------|---------|
| [14-01] | P0 | **降级** | P1 | tryLock 使用乐观锁，nextFireTime 作为预留信号实际降低了竞态风险。风险真实但低于原评估 |
| [14-02] | P0 | **降级** | P1 | 同 14-01 模式，乐观锁机制缓解了竞态风险 |
| [18-01] | P0 | **降级** | P1 | rewrite-design.md 已在 Git commit b7375746d 中更新，引用不存在的文件问题已部分修复 |
| [18-02] | P0 | **降级** | P1 | JobPartitionResolver 存在动态分区逻辑（通过 INamingService），但 beans.xml 未完全注入，属于配置完善问题 |
| [04-03] | P1 | **驳回** | — | Oracle 验证 ORM 模型第 404-410 行确认 NopJobTask→NopJobFire 关系存在，原发现不成立 |
| [13-01] | P1 | **降级** | P2 | CrudBizModel 基类已提供默认权限检查，缺少的是操作级细分权限 |
| [13-02] | P1 | **降级** | P2 | 同上 |
| [16-02] | P1 | **降级** | P3 | RpcBroadcastTaskBuilder 有 fallback 到 DefaultJobTaskBuilder（已有测试） |
| [11-01] | P1 | **保留** | P1 | scheduleStatus 通过 save mutation 可设为任意值，OrmEntityCopier 不检查 updatable |

### 复核后最终严重程度分布

| 严重程度 | 复核前 | 复核后 | 变化 |
|---------|-------|-------|------|
| P0 | 4 | **0** | 4 项全部降级为 P1 |
| P1 | 17 | **14** | 1 项驳回，2 项降级为 P2 |
| P2 | 52 | **~55** | 接收降级项 |
| P3/确认 | 81 | **~83** | 接收降级项 |

## 复核后关键发现摘要

### 最高优先级（P1，经复核保留/确认）

| 编号 | 维度 | 文件 | 一句话摘要 | 复核状态 |
|------|------|------|-----------|---------|
| [14-01] | 异步与事务 | JobPlannerScannerImpl | tryLock 乐观锁预留机制需关注边界情况 | 降级自P0 |
| [14-02] | 异步与事务 | JobDispatcherScannerImpl | 同 14-01 模式 | 降级自P0 |
| [18-01] | 文档一致性 | rewrite-design.md | 文档引用不存在文件（部分已修复） | 降级自P0 |
| [18-02] | 文档一致性 | cluster-ha-design.md | HA 功能部分实现但未完全注入 | 降级自P0 |
| [01-01] | 依赖图 | nop-job-dao/pom.xml | DAO 层反向依赖 Core 层 | 保留 |
| [11-01] | XMeta 对齐 | 多个 xmeta | scheduleStatus 通过 save 可设为任意值 | 保留 |
| [09-01] | 错误处理 | CronExpression | 构造函数丢失原始异常链 | 保留 |
| [16-01] | 测试覆盖 | DefaultJobCancelHandler | 取消通道零测试覆盖 | 保留 |
| [14-03] | 异步与事务 | 多个 Scanner | @SingleSession 与 REQUIRES_NEW 混用 | 保留 |
| [14-04] | 异步与事务 | JobTaskStoreImpl | tryLockTasksForExecute 事务边界 | 保留 |

## 本次审核盲区自评

1. **深挖轮次**: P0/P1 维度执行了 2 轮深挖，P2/P3 维度仅 1 轮初审。部分维度可能有更深层次的问题未被发现。
2. **并发测试未覆盖**: 未实际运行 `./mvnw test -pl nop-job` 验证测试基线。
3. **运行时行为未验证**: 事务相关发现基于代码静态分析，未经实际负载测试验证。
4. **跨模块交互未完全覆盖**: retry-adapter 与 nop-retry-engine 的接口契约仅做了初步检查。
5. **Oracle 复核受限于上下文**: Oracle 复核基于代码快照进行，未运行实际代码验证行为。

## 优先修复建议（复核后更新）

### 高优先级（复核确认的 P1）

1. **[14-01/02] 事务边界审视**: 虽然 Oracle 判定为乐观锁+预留信号机制，但建议增加监控和日志，确保并发场景下无调度丢失
2. **[11-01] scheduleStatus 字段保护**: 在 xmeta 中将 scheduleStatus 标记为 `updatable=false`，通过专用 mutation 方法修改
3. **[18-01/02] 文档更新**: 更新 rewrite-design.md 和 cluster-ha-design.md，反映实际实现状态
4. **[16-01] 补充测试**: 为 DefaultJobCancelHandler 添加单元测试
5. **[09-01] 异常链修复**: CronExpression 构造函数中保留原始异常 cause

### 中优先级（P2，排期处理）

6. **[01-01] DAO→Core 依赖重构**: 考虑将 Store 中使用的 Core 类下沉到 API 模块
7. **[04-01] ORM 外键索引**: 为高频查询的外键列添加索引
8. **[08-xx] IoC setter 注入**: 为缺少 @Inject 的 setter 方法添加注解
9. **[09-xx] 错误码规范化**: 消除 IllegalArgumentException，统一使用 NopException
10. **[17-xx] 代码风格**: 批量修复 import 分组和行宽问题

## 修复执行记录（2026-05-18）

执行计划: `ai-dev/plans/20-nop-job-audit-remediation.md`

### 修复结果

| 编号 | 修复内容 | 状态 | 修复方式 |
|------|---------|------|---------|
| [09-1] | CronExpression 异常链丢失 | ✅ 已修复 | 构造函数添加 `.cause(e)` 保留原始异常链 |
| [09-2] | IllegalArgumentException 逃逸 | ✅ 已修复 | 10 处 IAE 替换为 NopException + parse() 方法签名更新 |
| [10-1] | 5 个 xbiz 死代码 | ✅ 已修复 | 删除 NopJobPlan/Definition/Instance/InstanceHis/Assignment 目录（共 10 个 xbiz 文件） |
| [10-2] | NopJobPlan view.xml 悬空 xmeta | ✅ 已修复 | 删除 NopJobPlan pages 目录（5 个文件） |
| [11-1] | scheduleStatus 状态保护 | ✅ 已修复 | NopJobSchedule xmeta 11 个状态字段标记 insertable=false updatable=false |
| [11-3] | delta xmeta 空壳 | ✅ 已修复 | NopJobSchedule/Fire/Task 三个 delta xmeta 补充状态字段覆盖 |
| [14-09] | cancelFire/completeFire 竞态 | ✅ 已修复 | cancelFire 改用 tryUpdateManyWithVersionCheck；completeFire 添加终端状态二次校验 |
| [14-10] | 批量超时扫描中断 | ✅ 已修复 | scanTaskTimeouts/scanDispatchTimeouts 循环体添加 try-catch + 2 个新测试 |
| [16-01] | DefaultJobCancelHandler 零覆盖 | ✅ 已修复 | 新增 TestDefaultJobCancelHandler（11 个测试用例） |
| [18-3] | retry-integration-design.md 签名 | ✅ 已修复 | onFireFailed 签名与实际 IJobRetryBridge 接口对齐 |
| [18-5] | invoker-design.md executorSnapshot | ✅ 已修复 | 5 处 executorSnapshot 引用替换为 executorKind |
| [01-01] | DAO→Core 反向依赖 | ⏳ Deferred | 需独立计划，涉及 core/api/dao 三模块重构 |

### 验证

- `./mvnw clean test -pl nop-job`: **BUILD SUCCESS**（全部测试通过，零失败）
- 新增测试: TestDefaultJobCancelHandler (11 tests), TestJobFireStoreRace, TestJobTimeoutChecker (+2 tests)
- 回归验证: 修复前通过的所有测试仍通过
