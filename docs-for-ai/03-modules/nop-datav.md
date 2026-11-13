# nop-datav — 数据可视化模块

## 功能概览

运行时数据可视化与报表交付：

- Dashboard（看板：Panel/Tab/DatasetRef/FilterState 子对象，发布快照）
- Screen（大屏：生成器 executor + 快照历史）
- AlertRule（告警规则：评估调度 + 通知）
- ReportTask（报表任务：cron 调度 + 导出交付）
- ChatBI（自然语言→查询/看板/大屏，工具链 + 会话历史）
- 导出交付（ExportTask/ReportDelivery 异步执行 + stuck 扫描恢复）

> **owner doc 缺口声明兑现（plan 2526 Phase 3）**：本文件为新建最小 owner doc——并发契约节为主（CRA 8.33 R3-03 交付），完整功能文档建设归 R-G 治理批。

## 并发契约（DATAV-CC-001，plan 2526）

### 导出交付终态 fencing（CL-003/CL-001/CL-016）

- **终态写统一乐观锁 CAS 形态**：worker/scanner 对 `NopDatavExportTask`/`NopDatavReportDelivery` 的终态写（SUCCEEDED/FAILED/CANCELLED）一律 `orm_disableVersionCheckError(true)` + 0 行 `orm_readonly()` 判定（先例：nop-sys elector）。0 行 = 终态已被他人先行写定（scanner FAILED 粘滞语义），放弃写并 WARN 可观测；**同一实体实例 0 行后转 readonly，后续写必须 fresh 重载**。
- **0 行放弃分支的 fileId 回收**：worker 条件写失败时已落盘的导出产物文件必须回收（模块内 `daoFor(NopFileRecord)` 镜像 detach 序列），禁孤儿文件残留。
- **cancel 标志与终态写同一收口段**（putIfAbsent 原子登记 + finally 补偿 remove）；收口点在 `submitExecution` lambda 最外层 finally（含提交失败路径；executeTask 内 :367 finally 为幂等冗余）。
- **scanner FAILED 粘滞语义**为反向钉死改写后契约（原「worker SUCCEEDED 覆盖 scanner FAILED」已反转，`TestNopDatavStuckTaskScan` :308 断言即钉）。

### 发布版本号行锁（CL-002/CL-004）

Dashboard/Screen 发布的版本号分配以父行 `SELECT FOR UPDATE` 行锁（`IEntityDao.lockEntity`）承载，全程处于发布事务内；stale 锁 session 校验失败按重试处理。

### 告警评估互斥（CL-004/CL-008）

`AlertEvaluator` 以 **per-ruleId 进程内互斥**覆盖建行/决策/通知+回写三段（`IResourceLockManager` 使用面）；回写以版本 CAS 承载（`lastNotifiedTime` 等值谓词对 NULL 首通知行失效，禁用）。锁持有包络 ≈ 通知超时（横跨 SMTP 发送为已知代价，租约兜底）。

### 导出并发限额（CL-005）

per-user `ISemaphore` 原子层 + DB count 校验二层；许可收口点在 submitExecution 最外层 finally（早退/基建故障路径同样释放）。用户注册表生命周期：用户数有界、重启清零。

### 查询行上界（CL-009/CL-010/CL-021，R02 三范式）

- 主查询行上界：`nop.datav.dashboard-query.max-rows-per-panel`（默认 10000）——cfg+1 探测，超限显式抛 `ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED`（非静默截断）。
- ChatBI `loadMessages`：seq 窗口谓词下推 LIMIT `2×maxTurns+1`（升序语义保持）；`getChatSessionHistory` 保留全量包络（分页被裁定拒绝）。
- 快照历史查询：列投影恰三元数据列（保留数上界归 W-06 后续工单）。

### 调度生命周期（CL-019/CL-020）

- **调度注册/注销副作用 afterCommit 后置**：进程内 cron job 的 register/unregister 一律经 `NopDatavSchedulerAfterCommit.run` 移至事务提交后——回滚即不触发（消除「回滚后行 ENABLED 而 job 已消失」静默停摆窗）；回调内失败结构化 WARN；无事务上下文直行（原语义）。已接线五站点：StuckTaskScanner / ReportScheduler / AlertScheduler / ExportTaskRecovery / ReportDeliveryRecovery。
- **init 注册失败一次性延迟重试**：注册失败 catch 分支经 `NopDatavInitRetry.scheduleOnce` 调度 30s 一次性重试（上限一次，仍失败 WARN 放弃；禁无限重试、禁抛崩启动）。平台级统一绝缘层归 R1-01（五站点接入点清单已登记供其消费）。

### 已知恒红面（非本模块契约缺陷）

ChatBI 工具链 8 测试类 20 用例为跨批遗留恒红面（plan 2501 fail-loud `bafd41970c` 引入，零成员站点关联）——验证排除形态验收，修复归 ChatBI 后续工单。

## 源码锚点

并发契约权威锚点 = `../04-reference/source-anchors.md` 的 `DATAV-CC-001` 行；裁定权威 = AI 开发记录区 concurrency 设计集 file-dedup-datav-terminal-fencing-query-bounds-and-scheduler-concurrency.md（按 docs-for-ai 边界规则此处不直接链接）。
