# 深度审核汇总报告：nop-job

## 基本信息

- **审核模块**: nop-job（定时任务调度模块）
- **审核日期**: 2026-06-04
- **执行维度**: 全部 21 个维度
- **目标范围**: nop-job 下的 11 个子模块（api, core, codegen, dao, coordinator, worker, meta, service, web, app, retry-adapter）

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|-----------|------|------|------|
| 01 依赖图 | 1 | 1 | 0 | 1 | 0 | 0 |
| 02 模块职责 | 1 | 1 | 0 | 1 | 0 | 0 |
| 03 API表面积 | 1 | 2 | 0 | 2 | 0 | 0 |
| 04 ORM模型 | 1 | 2 | 0 | 2 | 0 | 0 |
| 05 生成管线 | 1 | 0 | 0 | 0 | 0 | 0 |
| 06 Delta定制 | 1 | 0 | 0 | 0 | 0 | 0 |
| 07 BizModel | 1 | 7 | 0 | 7 | 0 | 0 |
| 08 IoC/Beans | 1 | 2 | 0 | 2 | 0 | 0 |
| 09 错误处理 | 1 | 8 | 0 | 8 | 0 | 0 |
| 10 XDSL | 1 | 0 | 0 | 0 | 0 | 0 |
| 11 XMeta对齐 | 1 | 0 | 0 | 0 | 0 | 0 |
| 12 GraphQL | 1 | 1 | 0 | 1 | 0 | 0 |
| 13 安全 | 1 | 2 | 0 | 2 | 0 | 0 |
| 14 异步事务 | 1 | 3 | 0 | 3 | 0 | 0 |
| 15 类型安全 | 1 | 1 | 0 | 1 | 0 | 0 |
| 16 测试覆盖 | 1 | 3 | 0 | 3 | 0 | 0 |
| 17 代码风格 | 1 | 3 | 0 | 3 | 0 | 0 |
| 18 文档一致性 | 1 | 1 | 0 | 1 | 0 | 0 |
| 19 命名一致性 | 1 | 1 | 0 | 1 | 0 | 0 |
| 20 跨模块契约 | 1 | 3 | 0 | 3 | 0 | 0 |
| 21 单元测试有效性 | 1 | 2 | 0 | 2 | 0 | 0 |
| **合计** | **21** | **43** | **0** | **43** | **0** | **0** |

注：本审计为单轮初审（21维度并行），未进行深挖追加轮次。复核状态为"未复核"。

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 1 | 错误码分层 |
| P2 | 22 | BizModel规范、事务模式、API契约、错误处理 |
| P3 | 20 | 代码风格、测试质量、命名、文档 |

## 关键发现摘要

### P1 发现

| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| 09-07 | NopJobErrors.java | 公共API层BizModel错误码定义在service而非api模块，违反分层原则 |

### P2 发现（按优先级排序）

| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| 04-01 | NopJobScheduleBizModel.java | Schedule delete未防护，删除后子Fire记录成为孤儿 |
| 07-01 | NopJobScheduleBizModel.java | persistSchedule绕过CrudBizModel标准管线 |
| 07-04 | NopJobFireBizModel/TaskBizModel | delete() override丢掉@BizMutation注解 |
| 14-03 | JobWorkerScannerImpl.java | handleExecutionResult更新task失败后未重试 |
| 20-01 | IJobRetryBridge.java | onFireFailed返回String但实现始终返回null |
| 20-02 | NopRetryJobRetryBridge.java | fireAndForget模式与接口返回值语义不一致 |
| 09-01 | Calendar类群 | 使用裸IllegalArgumentException而非NopException |
| 09-04 | JobCoreErrors.java | 4个ErrorCode使用非标准命名（有兼容性原因） |
| 09-05 | LocalJobScheduler.java | checkActive()缺少.param()上下文 |
| 12-01 | NopJobSchedule.xmeta | scheduleStatus insertable未限制 |
| 19-01 | JobState.java | 枚举值与ORM字典值不一致（疑似遗留代码） |
| 20-03 | NopJobConfigs.java | 空接口但23处配置项分散 |
| 03-01 | JobPlannerScannerImpl.java | copyMap方法名误导（未拷贝） |
| 01-01 | nop-job-dao/pom.xml | dao编译期依赖core，违反分层规则2 |
| 08-01 | coordinator/retry-adapter | 缺少_module文件 |
| 16-01 | TestNopJobTaskBizModel.java | 手动构造BizModel而非通过IoC容器 |
| 17-01 | TestTrigger.java | System.out.println |
| 17-02 | 多个测试文件 | setExecutorKind被调用两次 |
| 21-01 | TestDefaultJobTaskBuilder.java | 测试价值偏低（P-1反模式） |
| 14-01 | JobCompletionProcessorImpl.java | @SingleSession批量处理（REQUIRES_NEW已隔离） |
| 14-02 | NopJobScheduleBizModel.java | 乐观锁重试风险（实际安全） |
| 02-01 | 5个Store/Coordinator文件 | defaultLong/defaultInt重复定义 |

## 总评

nop-job 是一个设计良好、质量较高的业务模块。核心调度引擎（coordinator/worker）架构清晰，Store 层的乐观锁重试机制健壮，BizModel 层的状态机约束合理。生成管线完整闭合，ORM 模型设计规范，测试覆盖质量较高（特别是 E2E 测试和并发竞争测试）。

主要改进方向：

1. **错误码分层**（P1）：NopJobErrors 应迁移至 api 模块。
2. **Schedule 删除防护**（P2）：NopJobScheduleBizModel 应 override delete()。
3. **重试桥接契约**（P2）：IJobRetryBridge 接口返回值与实现不一致。
4. **Calendar 异常统一**（P2）：从 Quartz 移植的 IllegalArgumentException 应迁移到 NopException。
5. **配置项集中管理**（P2）：23 处 @InjectValue 应在 NopJobConfigs 中声明。

## 优先修复建议

1. **立即**（P1）：将 NopJobErrors 迁移至 nop-job-api。
2. **短期**（P2 高收益）：NopJobScheduleBizModel 添加 delete() override。
3. **短期**（P2 高收益）：IJobRetryBridge 返回值改为 void 或让实现返回实际值。
4. **中期**（P2）：Calendar 类群迁移到 NopException。
5. **中期**（P2）：persistSchedule 重构为通过 doSaveEntity 实现或补充缺失管线步骤。
6. **中期**（P2）：handleExecutionResult 添加 RUNNING 状态下的重试逻辑。
7. **低优先级**（P3）：测试代码清理、配置项集中、风格统一。

## 本次审核盲区自评

1. 未执行 `./mvnw test -pl nop-job` 验证所有测试是否通过。
2. 未进行深挖追加轮次（第 2-10 轮），某些维度可能存在更深层次的问题。
3. 未进行独立复核（维度复核和子项复核），所有发现状态为"未复核"。
4. 未检查 nop-job-app 的 Quarkus 启动配置和运行时集成。
5. 未检查 deploy/ 目录下的部署配置（Docker 等）。
6. JobState 枚举的使用情况未完全追踪（可能是遗留代码或被外部模块使用）。
7. 未检查 nop-job 与 nop-wf（工作流）或 nop-task（任务）模块的实际交互。
