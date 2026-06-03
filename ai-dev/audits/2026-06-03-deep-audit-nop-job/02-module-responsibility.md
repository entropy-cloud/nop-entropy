# 维度 02：模块职责边界

## 发现

### [02-01] P2 — DAO Store 层重新定义状态常量而非使用 _NopJobCoreConstants

- **文件**: JobScheduleStoreImpl:37-49, JobFireStoreImpl:36-43, JobTaskStoreImpl:28-30
- **现状**: DAO Store 层的三个实现类各自定义了本地状态常量（如 `STATUS_READY`、`STATUS_FIRED` 等），共计约 13 个本地常量在 JobScheduleStoreImpl 中重复定义，其值与 `_NopJobCoreConstants` 中已定义的常量完全一致。
- **风险**: 常量定义分散，未来修改状态值时可能遗漏 Store 层的本地副本，导致不一致。
- **建议**: Store 层应直接引用 `_NopJobCoreConstants` 中已定义的常量，删除本地重复定义。

### [02-02] P2 — 工具方法跨文件复制粘贴

- **文件**: JobScheduleStoreImpl:391-404, JobFireStoreImpl:300-317, JobCompletionProcessorImpl:366-416 等 8 个文件
- **现状**: `defaultLong`、`defaultInt`、`calculateDuration`、`addPartitionFilter` 等工具方法在 DAO、coordinator、worker 的 8 个文件中重复复制粘贴。
- **风险**: 任何 bug 修复或行为变更需要在多处同步修改，维护成本高。
- **建议**: 将这些工具方法提取到 nop-job-core 的共享工具类中。

### [02-03] P2 — DAO Store 层包含应属于 coordinator 的业务逻辑

- **文件**: JobScheduleStoreImpl.overlayFireAndAdvanceSchedule:117-154, JobFireStoreImpl.cancelFire:130-179
- **现状**: DAO Store 层（本应只负责数据持久化操作）包含了阻塞策略选择、取消决策等业务逻辑。例如 `overlayFireAndAdvanceSchedule` 方法中嵌入了调度策略选择逻辑，`cancelFire` 中嵌入了取消条件判断。
- **风险**: 违反分层原则，Store 层职责膨胀，难以独立测试和替换。
- **建议**: 将业务决策逻辑上移到 coordinator 层，Store 层仅保留原子化的数据操作方法。

### [02-04] P2 — calculateFixedDelayNextFireTime 跨层重复

- **文件**: JobFireStoreImpl:280-290, JobCompletionProcessorImpl:373-383
- **现状**: `calculateFixedDelayNextFireTime` 方法在 DAO Store 层和 coordinator 层各有一份完全相同的实现。
- **风险**: 与 [02-02] 相同，维护成本倍增。
- **建议**: 提取到 nop-job-core 共享工具类。

### [02-05] P3 — IBiz 接口含自定义方法放置在 nop-job-dao 模块

- **文件**: INopJobScheduleBiz:14-31, INopJobFireBiz:12-16
- **现状**: IBiz 接口中定义了 `enableSchedule`、`cancelFire` 等自定义业务方法，但这些接口放置在 nop-job-dao 模块而非 nop-job-service。这符合平台约定（IBiz 接口随 ORM 模型放置），但这些方法实际上是手写的 service 层操作。
- **风险**: 低风险。符合平台惯例但可能造成新开发者困惑。
- **建议**: 保持现状（遵循平台约定），在接口头部添加注释说明这些方法由 service 层 BizModel 实现。
