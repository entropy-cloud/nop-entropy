# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-job
- **审核日期**: 2026-06-03
- **执行维度**: 全部 21 个维度
- **目标范围**: nop-job 模块全部 11 个子模块（api/core/dao/service/web/app/coordinator/worker/codegen/meta/retry-adapter）的手写源代码、模型定义、配置文件和测试代码

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|-----------|------|------|------|
| 01-依赖图 | 1 | 0 | 0 | 0 | 0 | 0 |
| 02-模块职责 | 1 | 2 | 0 | 2 | 0 | 0 |
| 03-API表面 | 1 | 2 | 0 | 2 | 0 | 0 |
| 04-ORM模型 | 1 | 2 | 0 | 2 | 0 | 0 |
| 05-生成管线 | 1 | 0 | 0 | 0 | 0 | 0 |
| 06-Delta | 1 | 0 | 0 | 0 | 0 | 0 |
| 07-BizModel | 1 | 2 | 0 | 2 | 0 | 0 |
| 08-IoC配置 | 1 | 0 | 0 | 0 | 0 | 0 |
| 09-错误处理 | 1 | 4 | 0 | 4 | 0 | 0 |
| 10-XDSL | 1 | 0 | 0 | 0 | 0 | 0 |
| 11-XMeta对齐 | 1 | 0 | 0 | 0 | 0 | 0 |
| 12-GraphQL | 1 | 0 | 0 | 0 | 0 | 0 |
| 13-安全权限 | 1 | 2 | 0 | 2 | 0 | 0 |
| 14-异步事务 | 1 | 3 | 0 | 3 | 0 | 0 |
| 15-类型安全 | 1 | 0 | 0 | 0 | 0 | 0 |
| 16-测试覆盖 | 1 | 2 | 0 | 2 | 0 | 0 |
| 17-代码风格 | 1 | 2 | 0 | 2 | 0 | 0 |
| 18-文档一致 | 1 | 0 | 0 | 0 | 0 | 0 |
| 19-命名一致 | 1 | 2 | 0 | 2 | 0 | 0 |
| 20-跨模块契约 | 1 | 0 | 0 | 0 | 0 | 0 |
| 21-测试有效性 | 1 | 0 | 0 | 0 | 0 | 0 |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 9 | 事务并发(3)、错误码中文(2)、命名不一致(1)、输入验证(1)、测试维护(1)、死代码(1) |
| P3 | 14 | 代码重复(2)、API风格(2)、BizModel模式(2)、测试风格(2)、命名(2)、权限(1)、生成产物(1)、其他(2) |

## 关键发现摘要

### P2 发现

| 编号 | 维度 | 文件 | 一句话摘要 |
|------|------|------|-----------|
| 04-01 | ORM模型 | nop-job.orm.xml:300 | NopJobFire UK在快速连续手动触发时可能冲突 |
| 04-02 | ORM模型 | nop-job.orm.xml:403 | NopJobTask缺少到NopJobSchedule的直接关系，需两跳 |
| 09-01 | 错误处理 | JobCoreErrors.java:16-43 | 4个ErrorCode描述使用中文（含@Locale("zh-CN")） |
| 09-02 | 错误处理 | JobApiErrors.java:17-19 | 2个公共API ErrorCode描述使用中文 |
| 13-01 | 安全权限 | NopJobScheduleBizModel.java:97 | triggerNow的overrideParams无输入验证 |
| 14-01 | 异步事务 | JobFireStoreImpl.java:117 | completeFireAndUpdateSchedule对schedule使用updateEntityDirectly绕过乐观锁 |
| 14-02 | 异步事务 | NopJobFireBizModel.java:47 | cancelFire TOCTOU多次加载fire实体 |
| 14-03 | 异步事务 | JobScheduleStoreImpl.java:125 | overlay操作吞掉异常后仍按全量计数 |
| 16-02 | 测试覆盖 | 多个coordinator测试 | Mock Store实现在4+个测试文件中重复定义 |
| 17-02 | 代码风格 | 多个测试文件 | setExecutorKind连续调用两次，第一次无效 |
| 19-01 | 命名一致 | NopJobErrors.java:50 | ERR_RPC_INVOKER_MISSING_PARAM缺少JOB_前缀 |

### P3 发现

| 编号 | 维度 | 文件 | 一句话摘要 |
|------|------|------|-----------|
| 02-01 | 模块职责 | 两个BizModel | resolveTriggeredBy方法完全重复(12行) |
| 02-02 | 模块职责 | 两个BizModel | Fire对象构建逻辑70%重复 |
| 03-01 | API表面 | NopJobFireBizModel.java:45 | @BizMutation注解接口有value实现类无value |
| 03-02 | API表面 | INopJobTaskBiz.java | delete方法在接口暴露但BizModel抛异常禁止 |
| 07-01 | BizModel | NopJobScheduleBizModel.java:132 | persistSchedule使用updateEntityDirectly绕过标准流程 |
| 07-02 | BizModel | NopJobFireBizModel.java:47 | cancelFire/rerunFire未使用requireEntity加载实体 |
| 09-03 | 错误处理 | JobCoreErrors.java:24-36 | 5个状态标记ErrorCode不遵循nop.err.*命名 |
| 09-04 | 错误处理 | Calendar类(14处) | 使用IllegalArgumentException而非NopException |
| 13-02 | 安全权限 | data-auth.xml | 数据权限配置为空 |
| 16-01 | 测试覆盖 | TestTrigger.java:97 | 测试中System.out.println调试遗留 |
| 17-01 | 代码风格 | NopJobErrors.java:12 | 类声明缺少空格 |
| 19-02 | 命名一致 | JobCoreErrors.java:24-36 | 状态标记ErrorCode使用裸字符串 |

## 总评

nop-job 模块整体质量**较高**。模块架构清晰，职责划分合理，生成管线闭合无断点，IoC 配置完全合规，跨模块契约实现正确，测试覆盖在分布式调度器领域属于高质量水平（130+ 测试方法，覆盖并发竞争、状态机转换、错误路径等关键场景）。

主要问题集中在：
1. **事务并发模式**（3个P2）：Store 层 `completeFireAndUpdateSchedule` 对 schedule 绕过乐观锁是最值得关注的问题，可能在并发场景下丢失更新。
2. **错误码国际化**（2个P2）：公共 API 层（JobApiErrors）和 core 层的 ErrorCode 描述使用中文，违反项目英文消息规范。
3. **代码维护性**：Mock Store 重复、Fire 构建逻辑重复等属于渐进式改善项。

## 优先修复建议

1. **[P2-高] 修复 schedule 乐观锁绕过**：`JobFireStoreImpl.completeFireAndUpdateSchedule` 中将 `scheduleDao().updateEntityDirectly(schedule)` 改为 `tryUpdateManyWithVersionCheck`。这是唯一可能导致数据不一致的发现。
2. **[P2-中] 公共 API 错误码国际化**：将 `JobApiErrors.java` 中的 2 个中文描述改为英文。这是跨模块公共 API 层，影响面最广。
3. **[P2-中] Core 层错误码国际化**：将 `JobCoreErrors.java` 中的 4 个中文描述改为英文。
4. **[P2-低] overlay 计数修复**：`JobScheduleStoreImpl.overlayFireAndAdvanceSchedule` 中跟踪实际成功取消数替代全量计数。
5. **[P2-低] 测试基础设施抽取**：将重复的 Mock Store 实现抽取到共享测试工具类。

## 本次审核盲区自评

1. 维度01（依赖图）的初审子agent输出被截断，完整依赖图分析基于主agent收集的基线数据，可能遗漏深层依赖问题。
2. 未实际运行 `./mvnw test -pl nop-job` 验证测试是否全部通过（基于已有 CI 信任）。
3. 维度15（类型安全）未独立执行，相关检查被维度03/07覆盖。
4. 未对 Calendar 类层次（从 Quartz 移植代码）做深入审计。
5. 未检查 nop-job 与 nop-retry-engine 之间的版本兼容性。
