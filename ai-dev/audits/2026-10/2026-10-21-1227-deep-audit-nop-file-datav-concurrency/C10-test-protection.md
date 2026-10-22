# C10 并发正确性的测试保护力 — nop-file / nop-datav

- 方法：既有并发相关测试盘点（88 测试文件中 14 个并发/资源相关类逐方法核对）→ 对全部 18 个保留发现做变异推演（补上正确实现后，现有哪个测试会红？）
- 结论统计：保护力矩阵 18 行——**会红 0 / 假保护 4 / 部分保护 1 / 无测试 13**；反向钉死 1（测试把缺陷行为钉为"正确"）；时序敏感测试 0（fake clock/latch/seam 范式健康）；泄漏检测钩子 0；规模边界测试 5（已声明上界覆盖良好，缺失上界恰为本轮新发现）

## 既有并发测试盘点

| 测试类 | 保护范围 | 范式质量 |
|---|---|---|
| TestNopDatavDashboardParallelQuery | 并行=顺序基线等价、并发真实执行（decorator latch）、并行度上界不被突破、非 NopException 看板级传播、max-panels 先于查询、executor 跨请求复用 identity | 确定性（seam 注入，无 sleep）— 优 |
| TestNopDatavDashboardQueryCache | 命中/参数失配/TTL/开关关/失败与无数据集不入缓存/max-rows 准入/键区分 | 确定性 — 优 |
| TestNopDatavAsyncSubmitTransactionPath | afterCommit 延迟提交/回滚不触发/无事务立即提交/GraphQL 全链 worker 消费已提交行（AR-2） | 时序锚点 seam — 优 |
| TestNopDatavShareRateLimit | 爆破锁定/窗口恢复/失败预算/按 IP 隔离/开关关等价/正常访问零误报 | fake clock（禁 sleep 硬要求兑现）— 优 |
| TestNopDatavStuckTaskScan | 阈值内外标记/幂等/吞错/表缺失跳过/fireNow 接线/慢执行竞态终态 | 播种时间戳（无真实 sleep 等待）— 优 |
| TestNopDatavExportE2E | 创建-轮询-下载全链/cancel 状态迁移（含执行中 cancel + awaitCancelFlagCleared 轮询）/限额拒绝/失败落 errorMsg | 轮询等待（可接受） |
| TestNopDatavAlertNotifyTransactionBoundary | afterCommit 延迟通知/失败保持 rearm 契约/无事务同步路径（P1-06） | seam — 优 |
| TestNopDatavChatBiTransactionBoundary | LLM 循环事务挂起/补偿删除（P1-05） | seam — 优 |
| TestPanelDataExporter(+PanelCap) | CSV/xlsx 内容、行超限取数阶段抛错、面板数上界、无数据集面板 | — |
| TestNopDatavReportE2E | 手动触发全链/register-unregister 联动/吞错/渠道显式失败 | — |

## 保护力矩阵（缺陷 × 测试 × 变异结论）

| CL | 缺陷 | 相关既有测试 | 补正确实现后谁红 | 结论 |
|---|---|---|---|---|
| CL-001 | C1-001 cancelFlags 竞态 | ExportE2E.testCancelDuringExecution（顺序 cancel） | 无——竞态窗口未被任何测试触及 | **无测试**（登记 P2 缺保护） |
| CL-002 | C1-002 版本号竞态 | TestNopDatavUniqueKeyEnforcement（他表 UK） | 无——无并发 publish 测试 | **无测试** |
| CL-003 | C1-003 终态无 fencing | StuckTaskScan.testSlowExecutionRaceWorkerSuccessOverwritesScanFailed | **该测试会红**——但它把无 fencing 行为断言为"final state correct"（钉死缺陷语义）；face(a) scanner 假阳性按该测试属已声明接受，face(b) FAILED→RUNNING 复活无测试 | **假保护（反向钉死）**——修复须同步改写本测试 |
| CL-004 | C1-004 告警并发评估 | AlertNotifyTransactionBoundary / AlertE2E | 无——无双触发并发测试 | **无测试** |
| CL-005 | C1-005 限额 TOCTOU | ExportE2E.testConcurrencyLimitRejects（单线程） | 无——顺序通过≠竞态受控 | **假保护（顺序测试伪装竞态保护）** |
| CL-006 | C1-006 双读 NPE | 无相关测试 | 无 | **无测试** |
| CL-007 | C1-007 Semaphore 占池 | ParallelQuery.testParallelismBoundNotExceeded + testExecutorSharedReuse | 并行度上界测试仍绿（上界未被破坏）——池占用/队头阻塞面无断言 | **部分保护**（界正确性有保护，跨工作负载影响无） |
| CL-008 | C1-008 hook 可见性 | — | 可见性缺陷不可确定性测试；直接修 | **无测试**（豁免登记，修 volatile 一行） |
| CL-019 | C1-R2-001 注销回滚窗口 | ReportE2E.testSchedulerRegisterUnregister / DeleteLifecycle | 无——均为 happy path，无"回滚后 job 恢复"断言 | **无测试** |
| CL-020 | C1-R2-002 注册失败停摆 | StuckTaskScan.testScanStuckSkipsWhenTableNotExists | 无——无 init 失败→重试/健康位测试 | **无测试** |
| CL-009 | C5-001 无行上界 | DashboardBatchQuery / DashboardDataBatchE2E（小数据） | 无——无行数放大测试（正确实现新增配置+抛错路径需新测试） | **无测试** |
| CL-010 | C5-002 会话历史无界 | ChatBiSessionManagement | 无——注入裁剪有测，加载侧无界无断言 | **无测试** |
| CL-011 | C5-003 workbook 乘积 | PanelDataExporter.testRowLimitExceeded（单面板） | 无——组合上界不存在故无测试对象 | **无测试** |
| CL-012 | C5-004 队列积压 | — | 无 | **无测试** |
| CL-013 | C5-005 账本零保留 | — | 无 | **无测试** |
| CL-021 | C5-R2-001 快照历史全量 | ScreenBizModel（publish/rollback 功能） | 无——历史查询规模面无测试 | **无测试** |
| CL-022 | C5-R2-002 会话列表无界 | ChatBiSessionManagement | 无 | **无测试** |
| CL-014 | C6-001 去重竞态 | — | 无——copy×detach 并发不存在于任何测试 | **无测试**（P1 缺陷零保护，最高优先补测） |
| CL-015 | C6-002 临时文件泄漏 | PanelDataExporter（happy path） | 无——失败路径资源断言缺失（无 temp 目录清空钩子） | **无测试** |
| CL-016 | C6-003 孤儿文件 | ExportE2E.testCancelDuringExecution（仅断言状态） | 无——文件记录/物理文件存在性无断言；状态测试掩盖孤儿 | **假保护** |
| CL-017 | C6-004 removeResource 失败 | — | 无 | **无测试** |
| CL-018 | C6-005 崩溃窗口 | — | 崩溃注入不在单测能力内 | **无测试**（watch 豁免候选） |
| CL-023 | C6-R2-001 SMTP 半程窗口 | ReportE2E.testE2eManualTrigger（happy path） | 无——无崩溃注入/对账测试 | **无测试** |
| CL-024 | C6-R2-002 删除×在途 | — | 无 | **无测试** |
| CL-025 | C6-R2-003 回补双失败 | — | 无 | **无测试** |

## 反向钉死登记（1 条）

- **StuckTaskScan.testSlowExecutionRaceWorkerSuccessOverwritesScanFailed**：断言 "worker SUCCEEDED overwrites scanner false-positive FAILED (final state correct)"——把 C1-003 的 scanner-vs-worker 面从缺陷升格为已钉死语义。含义：该面的修复（终态条件更新）**必须**同 diff 改写此测试的期望终态（如 worker 条件更新 0 行 → 保持 FAILED + 结构化冲突日志），否则修复无法落地。face(a)（scanner 误杀在途任务本身）依此测试属"已声明接受"，C1-003 的保留面收敛为 face(b) FAILED→RUNNING 复活（积压场景）与 cancel 竞态面（CL-001 独立登记）。

## 时序敏感测试审查

- Thread.sleep 同步断言：**0**（ShareRateLimit fake clock、StuckTaskScan 播种时间戳、ParallelQuery decorator latch——平台"禁止 sleep 盲等"纪律在本模块兑现良好）。
- 未等待异步结果即断言：**0**（ExportE2E awaitCancelFlagCleared 轮询等待后再断言）。
- superficially-pass：CL-005/CL-016 两处顺序测试对竞态/泄漏面构成伪保护（已在矩阵登记）。

## 资源泄漏测试钩子

- **无任何泄漏检测钩子**（无 temp 目录清空断言、无池空/注册表空断言、无 NopFileRecord 残留断言）。建议形态：ExportE2E 补 `@AfterEach` 断言 temp 目录无 `datav-export*` 残留 + NopFileRecord 表行数守恒；并发测试用 two-thread latch 模板（ParallelQuery 已有现成 decorator seam 可复用）。

## 建议补测清单（P2 立项口径，与缺陷本体分开计数）

1. **CL-014**（P1 零保护）：两线程 latch 测试——T2 copyFile 读记录后 latch、T1 detach 完成、T2 提交，断言物理文件仍存在。
2. **CL-009**：大结果集（>max-rows 配置）看板查询显式抛错 + 错误码断言（实现落地后）。
3. **CL-003 配套**：终态条件更新落地后改写 testSlowExecutionRace 期望（scanner FAILED 保持 + worker 写失败可观测）。
4. **CL-016**：cancel-after-save 场景断言 NopFileRecord 无残留行。
5. **CL-001**：cancel-vs-complete 交错（executionStartHook seam 已具备同步点，扩展断言 cancelFlags 空且终态不被覆盖）。
