# C1 深挖第 2 轮 — 共享可变状态与可见性

- 挖掘面：第 1 轮未覆盖的调度器生命周期（register/unregister 配对）、@PostConstruct 注册失败路径、BizModel 联动副作用
- 纪律：仅登记第 1 轮未发现的新问题；与 C1-001~008 不同根因、不同失效面
- CL 续编：`nop-file-datav-CL-019` 起

### [C1-R2-001] 调度器 job 注销为非事务副作用：事务回滚后 DB 行 ENABLED 而 cron job 已消失

- **文件**: `NopDatavReportTaskBizModel.java:71-79, 96-110`；`NopDatavDashboardBizModel.java:126-162`；`NopDatavReportScheduler.java:185-190`
- **证据片段**:
  ```java
  // DashboardBizModel.handleDeleteCascade（delete 事务内）
  disableReportTasksForDashboard(...)   // ① DB 置 DISABLED + reportScheduler.unregisterTask(job)
  revokeSharesForDashboard(...)         // ② 若此处或后续 deleteByQuery 抛错 → 整个删除事务回滚
  // unregisterTask：scheduler.removeJob(jobName(id)) —— 进程内调度器状态，不随 DB 事务回滚
  ```
- **严重程度**: P2
- **并发场景**: 非双线程竞态，而是"事务原子性 vs 进程内副作用"的失败路径不一致（F2 半个成功）；单线程失败即可触发。
- **触发时序**: 用户删除 dashboard → ① unregisterTask 完成（job 永久移除，LocalJobScheduler 语义）→ ② `deleteAllByDashboard`（如 Snapshot 删除）抛错 → 事务回滚，dashboard/ReportTask 行全部恢复 → 结果：ReportTask status=ENABLED 但 cron job 已不存在 → 定时报告**静默停摆**直至下次 save/enable 手动触发 registerTask。对称窗口同样存在于 `saveDashboardLayout` 移除面板时 `disableAlertRulesForPanels`（:772 注销告警 job，后续 panelDao 写失败回滚 → 告警规则 ENABLED 而无 job）。
- **现状**: 注释声明"注销失败异常传播回滚整个删除"（只覆盖注销本身失败方向），未覆盖"注销成功后其余步骤失败"方向。
- **风险**: 静默停摆（用户以为报告在发，实际已停）；与"周期任务停摆族"同谱。恢复仅靠用户再 save 一次（无人知晓需要）。
- **建议**: 副作用后置——把 unregisterTask/registerTask 挂 `transactionTemplate.afterCommit`（模块内 AR-2 已有先例：ExportTask submitExecutionAfterCommit），回滚即不注销；或失败补偿——catch 回滚路径重新 registerTask。
- **失效面枚举**（F3）: `handleDeleteCascade.disableReportTasksForDashboard`（本条）；`disableAlertRulesForPanels`（DashboardBizModel :167-189 与 saveDashboardLayout :772 两个调用面，同族同修）；`NopDatavReportTaskBizModel.doDeleteEntity`（:96-110 unregister 后还有 deleteByQuery(deliveries)——回滚窗口同构）；`NopDatavAlertRuleBizModel.doDeleteEntity/afterEntityChange`（同族）；`PanelBizModel.disableAlertRulesForPanel`（同族）；`rollbackDashboard`（无调度副作用，无涉）。
- **信心水平**: 确定
- **误报排除**: 非平台事务职责——scheduler 是本模块自持进程内状态，副作用编排放本模块；有 AR-2 afterCommit 先例证明模式可用。
- **复核状态**: 已保留（回码验证：handleDeleteCascade 中 unregister 先于后续 deleteByQuery、无 afterCommit 包裹属实；现状：新发现）

### [C1-R2-002] 周期任务停摆族：@PostConstruct 注册失败仅记日志，周期恢复/调度整个进程生命周期缺失

- **文件**: `NopDatavStuckTaskScanner.java:91-110`；`NopDatavReportScheduler.java:116-147`（per-task）；`NopDatavAlertScheduler.java:110-141`（per-task）
- **证据片段**:
  ```java
  // NopDatavStuckTaskScanner.init :101-109
  try {
      scheduler.activate();
      scheduler.addJob(buildJobSpec(), true);
  } catch (Exception e) {
      LOG.error("nop.datav.stuck-scanner.register-failed: jobName={}", JOB_NAME, e);  // 无重试、无降级告警、无健康位
  }
  ```
- **严重程度**: P2
- **并发场景**: 启动线程 vs 后续全部业务线程；启动期 DB/调度器瞬态故障窗口。
- **触发时序**: 容器启动时 DB 短暂不可用（或 scheduler 未激活完成）→ addJob 抛异常 → catch 仅记 ERROR → 周期 stuck 扫描整个进程生命周期不运行（LocalJobScheduler FAILED-brick 语义下也不会自我复活）→ 之后任何一次 worker 线程被 JVM Error 杀死留下的 RUNNING 记录永久滞留（F5：挂死/滞留不可见）；对照 Report/AlertScheduler 的 per-task 注册失败：该任务 cron 永久不排程（同族），且无"注册失败清单 + 补注册"机制。
- **现状**: 失败可观测（ERROR 日志）但不可恢复、不可检测（无健康指标暴露 registered=false）。
- **风险**: 恢复基础设施的单点静默缺失；stuck 记录滞留 RUNNING → 用户误以为任务在跑；C7 口径的"失败可自愈"缺失。
- **建议**: 注册失败时启动延迟重试（平台定时器一次重试即可）；或暴露 `scanRegistered` 健康位进 CoreMetrics；周期扫描的注册失败权重高于 per-task（基础设施级），建议至少 WARN 级指标。
- **失效面枚举**（F3）: `NopDatavStuckTaskScanner.init`（基础设施级，本条主面）；`NopDatavReportScheduler.init` per-task 循环（同族，失败隔离正确但无补注册）；`NopDatavAlertScheduler.init`（同族）；recovery 双 bean 的 @PostConstruct init（catch warn——重启恢复失败同样仅日志，同族）。
- **信心水平**: 确定（机制确定；触发依赖启动期故障）
- **误报排除**: 非"启动失败应快速失败"范畴——模块显式选择不抛崩启动（多租户宿主场景合理），缺的是失败后的自愈/可观测，不是失败策略本身。
- **复核状态**: 已保留（回码验证：init catch 仅 LOG.error 无重试/健康位属实；现状：新发现，跨单元关联 cra-index WI2.1 nop-kernel-concurrency C3-001 周期任务绝缘层平台族 / WI1.6 C8-001 扫描循环停摆族）

## 第 2 轮复核通过项（正向证据，不编号）

1. `NopDatavSingleWriterCrudBizModel`（125 行全文）：13 个标准 mutation 入口全部 fail-fast 拒绝——快照表/AlertState 单写点契约的 F10 守卫，正向先例。
2. `triggerReportNow` 与 cron 并发：每次触发独立插入 delivery 行并独立执行——按设计每次触发=一条交付记录，非重复计数缺陷。
3. `generateUniqueToken`（TOKEN_MAX_RETRIES=8）token 冲突 check-then-retry：随机 UUID 空间 + 显式重试上限后抛错——可接受。
4. `waitForTaskRow`/`waitForDeliveryRow` 200ms×3 短退避：有界阻塞、中断响应（restore interrupt）——合规。

## 维度复核结论

- 2 条深挖发现逐条回码验证：**保留 2 / 降级 0 / 驳回 0**；现状均为新发现。
- C1-R2-001 复核补验：unregister 调用点均直接位于 mutation 方法体内、无 afterCommit 包裹（对照 ExportTaskBizModel.submitExecutionAfterCommit 先例）。
- C1-R2-002 复核补验：与 cra-index WI2.1 C3-001（周期任务设施 29 站点无统一绝缘层）属同族跨单元面——本条为本单元 4 个注册站点（scanner/report/alert/recovery×2）的单元级登记，平台级统一修复应归 C3-001 工单，本条作为接入点。

## 最终保留项

| CL | 发现 | 严重度 | 现状 | 跨单元关联 |
|---|---|---|---|---|
| CL-019 | C1-R2-001 调度 job 注销非事务副作用 | P2 | 新发现 | — |
| CL-020 | C1-R2-002 周期任务注册失败永久停摆 | P2 | 新发现 | WI2.1 C3-001, WI1.6 C8-001 |
