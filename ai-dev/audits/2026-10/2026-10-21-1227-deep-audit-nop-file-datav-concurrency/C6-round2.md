# C6 深挖第 2 轮 — 资源生命周期与泄漏

- 挖掘面：session 拆分后的 SMTP 半程窗口、删除级联与在途执行体并发、补偿回写双失败路径
- 纪律：仅登记新发现；与 C6-001~005 不同失效面
- CL 续编：`nop-file-datav-CL-024` 起

### [C6-R2-001] 交付两段式半程窗口：SUCCEEDED 已提交、JVM 在 SMTP 前死亡 → 邮件静默丢失无重发路径

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/report/ReportDeliveryExecutor.java:201-209, 299-316`
- **证据片段**:
  ```java
  // runDelivery :203-208
  PreparedDelivery prepared = ormTemplate.runInNewSession(session ->
          runDeliveryInSession(...));        // Part A：SUCCEEDED 事务已提交
  if (prepared != null) {
      sendNotificationOutOfSession(prepared, ...);   // Part B：进程死亡即永不执行
  }
  ```
- **严重程度**: P2
- **并发场景**: worker 线程 Part A 提交与 Part B 之间的进程崩溃/部署重启窗口；非多线程竞态，是两段式设计的故障模型缺口（F2）。
- **触发时序**: Part A 提交 SUCCEEDED → JVM 崩溃/发布重启 → Part B（sendReport SMTP）未执行 → 重启后恢复路径把非终态行标 FAILED，但本行已是 SUCCEEDED（终态，recovery 跳过）→ 结果：交付记录显示成功、deliveredChannels 为空、邮件永未发出且无任何重发/对账机制。
- **现状**: Dim14-02 修复声明消除的是"邮件已发但记录未更新"正向窗口；反向窗口（记录已成功、邮件未发）未声明、未覆盖。对照告警侧：AlertEvaluator 的 afterCommit 通知失败/进程死亡 → lastNotifiedTime 未写 → 下轮评估 rearm 自动重试（有自愈），报告交付侧无等价机制。
- **风险**: 定时报告静默丢发（收件人无感知、发送方无告警）；deliveredChannels 空是唯一事后线索。
- **建议**: 交付行加 `notifyState`（PENDING_NOTIFY/NOTIFIED）与 Part A 一并提交，重启恢复把 SUCCEEDED+PENDING_NOTIFY 的行重新入队发送；或最简：恢复扫描把"deliveredChannels 为空的 SUCCEEDED 行"列对账清单输出 WARN。
- **失效面枚举**（F3）: `runDelivery` Part A→B 窗口（本条）；`sendNotificationOutOfSession` 异常路径（已有 rollbackSucceededToFailed 补偿——进程内同族已修对照）；`AlertEvaluator.notifyWithRearmContract`（rearm 自愈对照，已修）；`recordDeliveredChannels` 失败（仅 cosmetic 字段丢失，见 C6-R2-003）。
- **信心水平**: 确定（窗口机制确定；触发依赖进程重启时点）
- **误报排除**: 非"重启恢复已覆盖"误判——recovery 只处理 pending/running 非终态行，SUCCEEDED 行明确跳过（NopDatavReportDeliveryRecovery :111-112 状态过滤）。
- **复核状态**: 已保留（回码验证：Part A 提交后 Part B 无持久化中间态、deliveredChannels 空为唯一线索属实；告警侧 rearm 自愈对照属实；现状：新发现）

### [C6-R2-002] 任务删除级联与在途交付执行体并发：写丢失 + 孤儿文件窗口

- **文件**: `NopDatavReportTaskBizModel.java:96-110`；`ReportDeliveryExecutor.java:215-292`
- **证据片段**:
  ```java
  // doDeleteEntity（删除事务）
  reportScheduler.unregisterTask(...);
  daoProvider().daoFor(NopDatavReportDelivery.class).deleteByQuery(deliveryQuery);  // 物理删交付行
  // 在途 worker：runDeliveryInSession 持已加载的 delivery 实体继续执行
  deliveryDao.updateEntityDirectly(delivery);      // 行已被删 → UPDATE 落空/异常
  ...
  String fileId = saveReportFile(file, reportTaskId);  // 若执行到此处 → 文件落盘但行已不存在
  ```
- **严重程度**: P3
- **并发场景**: HTTP 删除线程（deleteByQuery 物理删交付行）与 globalWorker 在途交付线程（早前 cron 触发、正在取数/落盘）。
- **触发时序**: T2 cron 交付在途（已过 RUNNING 写、正在 exportDashboard）→ T1 用户删除报告任务 → 交付行被物理删除 → T2 完成取数 `saveReportFile` 落盘成功 → T2 `deliveryDao.updateEntityDirectly` 目标行不存在（0 行更新/抛错，按 ORM 语义分支）→ 若抛错则进 catch 再写仍失败 → markFailedSafe 同样落空 → 生成的 NopFileRecord + 物理文件孤儿（bizObjId=已删除的 reportTaskId，无任何行引用）。
- **现状**: 删除级联声明"任一步骤失败异常传播"，但未声明与在途执行体的互斥；执行体对"行已消失"无检测。
- **风险**: 低概率（需删除与在途窗口重叠）但后果为存储孤儿 + 日志噪声；文件孤儿与 C6-003 同治理面。
- **建议**: 执行体在落盘前重查交付行存在性（一行 getEntityById 判空即退）；或交付行软删（delFlag）+ 执行体写前检查。
- **失效面枚举**（F3）: 报告交付删除窗口（本条）；`createExportTask` 导出无删除 API 暴露？标准 CrudBizModel delete 面存在（NopDatavExportTask 继承 CrudBizModel 未覆写 doDeleteEntity）→ 导出任务删除与在途 executeTask 同构窗口（同族同修）；`cancelExportTask`（不删行，无涉）。
- **信心水平**: 很可能（UPDATE 落空的确切 ORM 行为未逐行验证 nop-orm，但两种分支后果均为写丢失+孤儿）
- **误报排除**: 非平台级联职责——执行体检测行存活性是本模块语义（级联删除是本模块代码）。
- **复核状态**: 已保留（回码验证：doDeleteEntity 物理删交付行 + 执行体无行存活复查属实；updateEntityDirectly 对已删行的确切行为未逐行验证，信心标注"很可能"；现状：新发现）

### [C6-R2-003] 邮件失败回补自身失败 → SUCCEEDED 终态误标（双失败路径 log-only）

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/report/ReportDeliveryExecutor.java:340-363`
- **证据片段**:
  ```java
  private void rollbackSucceededToFailed(String deliveryId, String reportTaskId, String reason) {
      try {
          ormTemplate.runInNewSession(session -> { ... SUCCEEDED → FAILED ... });
      } catch (Exception e) {
          LOG.error("nop.datav.report.rollback-failed-fail:deliveryId={}", deliveryId, e);
          // 双失败：DB 抖动导致回补写失败 → 终态停留 SUCCEEDED
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: 邮件发送失败（外部 SMTP 故障）与 DB 瞬态故障叠加时；单线程双失败。
- **触发时序**: SMTP 抛错 → rollbackSucceededToFailed 执行中 DB 连接抖动 → 回补失败仅 ERROR 日志 → 交付行停留 SUCCEEDED（邮件实际未发出）→ task.lastRunStatus 同样停留 success。与 C6-R2-001 同谱（状态与事实背离），本条为进程内双失败变体。
- **现状**: 注释声明回补失败"已尽最大努力"仅对 markFailedSafe；本方法 catch 无等价声明亦无重试。
- **风险**: 统计/审计失真（成功率虚高）；触发概率低（双故障叠加）。
- **建议**: 回补失败时将该 deliveryId 写入结构化对账日志（供人工/脚本补偿）；重试一次低成本。
- **失效面枚举**（F3）: `rollbackSucceededToFailed`（本条）；`markFailedSafe` ×2（export/delivery，同构 log-only，同族同修）；`recordDeliveredChannels`（cosmetic 字段，声明豁免）。
- **信心水平**: 确定（机制确定；触发为双故障叠加）
- **误报排除**: 非吞异常豁免范畴——log-only 后状态与事实背离持续存在，非"失败对外可见"。
- **复核状态**: 已保留（回码验证：rollbackSucceededToFailed catch 仅 LOG.error、无重试属实；现状：新发现）

## 第 2 轮核验通过项（正向证据）

1. **`changePublic` 落库语义**：调用链 `OrmFileComponent.changePublic`（nop-orm :44-52）处于调用方 ORM 会话内，`requireEntityById` 挂接当前会话、脏检查随会话 flush——无更新丢失（第 1 轮存疑点澄清，不立项）。
2. **attach/detach 会话归属**：P1-3.8-002 修复后两者均 `runInSession` 包裹，load/save 同会话——会话泄漏面已闭合。
3. **生成补偿事务隔离**：`runCompensationInShortTransaction` 用 REQUIRES_NEW 防补偿随回滚事务复活——方向正确（对照注释论证）。

## 维度复核结论

- 3 条深挖发现逐条回码验证：**保留 3 / 降级 0 / 驳回 0**；现状均为新发现。
- C6-R2-001 复核补验：recovery 状态过滤（pending/running）确认 SUCCEEDED 行重启后不被触碰——半程窗口无自愈路径成立；告警侧 rearm 自愈为已修对照。
- C6-R2-002 信心水平维持"很可能"：updateEntityDirectly 对已删行的确切 ORM 行为未逐行验证 nop-orm 源码，两种分支（抛错/静默）后果均为写丢失+孤儿窗口，不影响 P3 定级。

## 最终保留项

| CL | 发现 | 严重度 | 现状 | 跨单元关联 |
|---|---|---|---|---|
| CL-023 | C6-R2-001 交付两段式 SMTP 半程窗口静默丢发 | P2 | 新发现 | WI1.6 C3-005（弱） |
| CL-024 | C6-R2-002 删除级联 × 在途交付并发 | P3 | 新发现 | — |
| CL-025 | C6-R2-003 回补双失败 → SUCCEEDED 误标 | P3 | 新发现 | — |
