# C5 内存有界性与大数据量 — nop-file / nop-datav 初审（单批全量）

- 审计单元：5 源码根全量（97f/16,931 实测，src/main 手写口径）
- 方法：无界 API 使用点 + 随输入增长容器 + 物化 vs 流式 + 缓存淘汰 + 峰值叠加逐路径核对；盲审
- CL 前缀：`nop-file-datav-CL-0NN`（自 CL-009 起）

## 无界风险清单（路径 × 增长源 × 声明上界 × 判定）

| 路径 | 增长源 | 声明上界 | 判定 |
|---|---|---|---|
| getDashboardData（批量面板查询） | `extractRows` 全量物化（rowLimit=null） | max-panels=50（仅限面板数） | **C5-001 P1：行数无界** |
| getPanelData 单面板运行时 | 同上（rowLimit=null 重载） | 无 | 同族并入 C5-001 失效面 |
| exportDashboard/exportPanel | binder 限行 maxRows+1 探测 + 全量内存 workbook | max-rows=100,000、max-panels=50 | C5-003 P3（边界已声明） |
| runDeliveryInSession 取数 | 同导出 | report.max-rows=100,000 | 有界 ✓ |
| AlertEvaluator.evaluate | eval-max-rows=1,000 | 有界 ✓ |
| ChatBI datav-query-dataset | clampMaxRows→chatbi.max-rows=1,000 | 有界 ✓（P1-04 修复后无绕过面） |
| chatToQuery 多轮历史 | loadMessages 全量加载 | 注入侧双上界（turns/chars），**加载侧无界** | **C5-002 P2** |
| DashboardPanelQueryCache | LocalCache maxEntries=200 + TTL 30s + 单条目 1000 行准入 | 有界 ✓（内存上界公式已入配置 Description） |
| NopDatavShareAccessGuard.states | max-keys=10,000 驱逐 | 有界 ✓ |
| DaoResourceFileStore.saveFile | LimitedInputStream 超限抛 ERR_IO_STREAM_SIZE_EXCEED_LIMIT（实测 raiseError 语义，非静默截断）+ file-max-length=100MB | 有界 ✓ |
| removeTempFileByOwner | findAllByQuery 按 owner 全量 | owner 临时文件数（有界面弱但场景受限） | 观察，不编号 |
| globalWorker 队列 | 导出/交付提交速率 vs 处理速率 | maxPoolSize=30；队列策略平台默认 | C5-004 P3 |
| NopDatavReportDelivery / NopDatavExportTask 行 | 每 cron tick 插行 | stuck 扫描仅改状态不删行 | C5-005 P3（F4 watch） |
| writeCsv/writeXlsx 临时文件 | 每次导出建临时资源 | 成功路径调用方删除；失败路径泄漏 | 归 C6-002（资源维度） |

## 发现条目

### [C5-001] getDashboardData 主查询路径无行数上界：50 面板 × 无界行全量物化

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/query/PanelDataBinder.java:170-180, 207-214`；调用方 `NopDatavDashboardBizModel.java:583`
- **证据片段**:
  ```java
  // NopDatavDashboardBizModel.newPanelQueryTask :583 —— 批量路径传 rowLimit=null
  PanelDataResult result = binder.queryPanelData(panel.getPanelId(), panel, resolvedParams, null, cache);
  // PanelDataBinder.extractRows :207-213 —— 无界物化
  List<Map<String, Object>> rows = new ArrayList<>();
  while (ds.hasNext()) { rows.add(ds.next().toMap()); }
  ```
- **严重程度**: P1
- **并发场景**: HTTP 请求线程（顺序路径）或 globalWorker 线程 ×并行度（并行路径，默认 4/请求）；多请求叠加。
- **触发时序**: 无需竞态——确定性触发：任一面板的数据集 SQL 返回大结果集（如明细表百万行）→ `jdbcTemplate.executeQuery` 以 `range=null`（:175 rowLimit==null 分支）流式游标被 `extractRows` 全量拉入堆内 `List<Map>` → 50 面板逐个执行（并行时峰值 = 4 × 单面板行集）→ 单请求峰值 ≈ Σ(每面板行数 × 行宽 × Map 装箱放大)。并发 K 个请求线性放大。
- **现状**: 模块其余全部查询路径均有显式行上界配置（export 100k / report 100k / alert 1k / chatbi 1k），唯 getDashboardData/getPanelData 主查询路径刻意传 null（:81 javadoc 自述"null 表示不限制（运行时 getPanelData 路径）"）；缓存准入上界（max-rows-per-entry=1000）只约束"是否缓存"，不约束物化。
- **风险**: 超出任何声明包络的确定性 OOM 通道：一次误配的数据集（缺 LIMIT 的明细查询）+ 用户打开看板即触发；与 F7（OOM 同族）画像一致。因"必然触发"依赖用户数据集形态而非代码路径选择，判 P1（负载/数据上升必然触发，非声明包络内必然）。
- **建议**: 为批量查询路径增设 `nop.datav.dashboard-query.max-rows-per-panel`（对齐既有 4 个限额先例，默认如 10k），getDashboardData/getPanelData 传入 rowLimit；超限显式抛 ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED（对齐导出 ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED 语义）。
- **失效面枚举**（F3）: `newPanelQueryTask`（:583，本条主面）；`NopDatavPanelBizModel.getPanelData/refreshPanel`（同传 null，同族未修）；`exportPanel/exportDashboard`（maxRows+1，已修对照）；`AlertEvaluator.evaluate`（rowLimit，已修对照）；`DatavQueryDatasetExecutor.clampMaxRows`（已修对照）。全部同族调用点已列全。
- **信心水平**: 确定
- **误报排除**: 非"有界缓存已存在"误报——准入上界不约束物化；非平台 JDBC 职责——rowLimit 参数由本模块显式传 null。
- **复核状态**: 已保留（回码验证：DashboardBizModel :583 与 PanelBizModel :136/:150 三处传 null rowLimit 实测确认；其余四路径限额配置实测存在；现状：新发现）

### [C5-002] ChatBI 会话历史全量加载无上界：每轮线性放大 + resultJson 行数据放大

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/ChatBiSessionManager.java:124-129, 138-160`；调用方 `NopDatavChatBiBizModel.java:296-304`、`:353-358`
- **证据片段**:
  ```java
  // loadMessages :124-129 —— 无 LIMIT
  QueryBean query = new QueryBean();
  query.addFilter(FilterBeans.eq("sessionId", sessionId));
  query.addOrderField("seq", false);
  return daoProvider.daoFor(NopDatavChatMessage.class).findAllByQuery(query);
  // appendTurn :145-150 —— 每轮 assistant 消息持久化结构化结果
  resultJson.put("rows", result.getRows());   // 行数可达 chatbi.max-rows=1000/轮
  ```
- **严重程度**: P2
- **并发场景**: 非竞态问题，长时运行累积 + 每请求成本增长（单线程即可触发）。
- **触发时序**: 用户持续使用同一会话（每轮 2 条消息，assistant 消息含最多 1000 行的 rows JSON）→ 第 N 轮请求 `loadMessages` 全量载入 2N 条消息（含全部历史 resultJson 字符串）到堆内 → `buildHistoryContext` 再在内存中做轮数/字符预算裁剪（裁剪发生在全量加载**之后**）→ 每请求内存与耗时随会话长度线性增长，无上界。
- **现状**: 双上界（max-turns=10 / max-chars=20000）只约束注入 LLM 的上下文；`getChatSessionHistory` action 直接全量返回（用户可见用途），加载与裁剪次序颠倒（先全量后裁剪）。
- **风险**: 长会话（数千轮）下单请求物化数十万行等价 JSON，OOM/延迟劣化；F7 家族（一次载入全量 vs 按需）。
- **建议**: loadMessages 增加按 max-turns 推导的 LIMIT（seq 降序取最近 2×maxTurns+1 条）；`getChatSessionHistory` 可保留全量但加分页参数。
- **失效面枚举**: `chatToQuery` 多轮路径（:296，本条）；`getChatSessionHistory`（:357，同族同修）；`buildHistoryContext`（纯函数，无涉）；`deleteSession`/`appendTurn`（有 UK/条件语义，无涉）。
- **信心水平**: 确定
- **误报排除**: 非"已声明边界"——ai-design.md §10 双上界声明针对注入上下文，非加载路径；非低频管理路径——每轮对话必经。
- **复核状态**: 已保留（回码验证：loadMessages 无 LIMIT、裁剪发生在全量加载后属实；现状：新发现，关联 C5-R2-002 同根因）

### [C5-003] 导出全量内存 workbook：50 面板 × 100k 行的已声明包络核验

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/export/PanelDataExporter.java:157-211, 276-301`
- **证据片段**:
  ```java
  // exportDashboard :189-207 —— 单个 ExcelWorkbook 累积全部面板
  ExcelWorkbook workbook = new ExcelWorkbook();
  for (NopDatavPanel panel : exportable) {
      PanelDataResult result = dataBinder.queryPanelData(..., maxRows + 1);
      workbook.addSheet(toSheet(sheetName, result.getColumns(), result.getRows(), cancelChecker));
  }
  ```
- **严重程度**: P3
- **并发场景**: globalWorker 线程，多导出任务并发（受 per-user=3 限额部分约束）。
- **触发时序**: 确定性：50 面板 × 100,000 行 × 每行 N 列 ExcelCell 对象 → 单任务堆内单元格对象可达 5×10⁶ 级（每 cell 一对象 + 装箱值），峰值数百 MB 至 GB 级；导出行数上限"防 OOM"的声明仅覆盖单面板行数，未覆盖面板乘积。
- **现状**: AR-3 注释显式声明"全量内存 workbook"——**已声明边界**，按审计纪律不判缺陷；但声明包络的乘积效应（max-rows × max-panels）未见算术上界登记，按 C9/C5 边界核验登记观察。
- **风险**: 默认配置组合下的合法导出可触发 OOM；叠加 C1-007（池共享）会放大为多任务并发 OOM。
- **建议**: 在 NopDatavConfigs 增加组合上界（如 total-cells ≤ 2×10⁶）并在 exportDashboard 入口校验；或流式写出（SXSSF 风格）。
- **失效面枚举**: `exportDashboard`（本条）；`exportPanel`（单面板，受 maxRows 直接约束，风险低）；`runDeliveryInSession`（复用 exportDashboard，同族并入）。
- **信心水平**: 确定（算术确定，触发依赖用户数据规模）
- **误报排除**: 已声明边界按纪律本应豁免——登记为 P3 是因为乘积上界未声明（声明的是"每面板行数"而非"总量"），属边界声明不完备而非重复报已声明项。
- **复核状态**: 已保留（回码验证：maxRows × maxPanels 乘积无组合校验属实；现状：新发现）

### [C5-004] globalWorker 队列无界积压：交付/导出提交速率与处理速率解耦

- **文件**: `ReportDeliveryExecutor.java:173-186`；`NopDatavExportTaskBizModel.java:270-282`；平台 `GlobalExecutors.java:85-97`（使用方式视角）
- **证据片段**:
  ```java
  // cron 每次触发：插 pending 行 + 提交 runnable，job 体即刻返回（不等处理完成）
  GlobalExecutors.globalWorker().submit(() -> {
      try { runDelivery(...); }   // 单次交付 = 导出（可达数分钟）+ SMTP（30-60s）
  ```
- **严重程度**: P3
- **并发场景**: cron 触发线程（每任务每 tick 一提交）vs globalWorker 消费（默认 30 线程，且被 C1-007 的面板查询排队侵占）。
- **触发时序**: 100 个 ENABLED 报告任务、cron 每分钟同刻触发 → 100 runnable/分钟入队 → 单交付耗时 ~90s → 消费 ~20/分钟 < 100/分钟 → 队列单调增长；积压交付行由 stuck 扫描 60min 后标 FAILED（有界兜底），但 runnable 仍会出队执行（见 C1-003 复活变体），且队列内 runnable 对象持续累积。
- **现状**: 无任务级重叠防护（"同任务上一 tick 未完成则跳过"检查缺失）；misfire grace 只在出队后生效（跳过执行但行已插入）。
- **风险**: 极端配置下队列内存增长 + 大量无效执行（出队即 skipped/复活）；常规规模（几十任务）影响有限。
- **建议**: execute 入口加"该 reportTaskId 存在 pending 交付行则跳过本轮"检查（复用 grace 语义），或 per-task 信号量。
- **失效面枚举**: `executeScheduledReport`（cron 路径，本条）；`triggerReportNow`（手动单发，用户触发频率低，豁免）；`createExportTask`（有 per-user 限额，部分缓解）。
- **信心水平**: 很可能（依赖任务规模与 cron 配置画像）
- **误报排除**: 非平台队列实现问题——平台只审使用方式；堆积根因是本模块无重叠防护。
- **复核状态**: 已保留（回码验证：execute 无 pending 存在性检查、grace 检查在出队后属实；现状：新发现）

### [C5-005] 交付/导出历史行只增不删（F4 watch）

- **文件**: `NopDatavReportDeliveryRecovery.java:99-131`；`NopDatavExportTaskRecovery.java:103-133`
- **证据片段**:
  ```java
  // doScanStuck —— 仅状态改写，无删除/归档路径
  delivery.setStatus(NopDatavReportDeliveryStatus.FAILED);
  dao.updateEntityDirectly(delivery);
  ```
- **严重程度**: P3
- **并发场景**: 无竞态；cron 每 tick 插入交付行（append-only 账本），无任何清理/TTL/归档路径。
- **触发时序**: 长时运行（天/周级）→ NOP_DATAV_REPORT_DELIVERY 行数 = Σ(任务数 × 频率 × 时长) 单调增长；扫描查询 `findAllByQuery(status in pending,running)` 本身有状态过滤（不随历史膨胀），故主要是存储增长与全表查询面。
- **现状**: F4 家族（无界账本）的存储形态；内存不增长（查询有状态谓词），故 P3 watch 而非 P1。
- **风险**: 表膨胀拖慢统计/排障查询；无声明保留期。
- **建议**: 增加 retain-days 配置 + 定期 deleteByQuery 终态老行（镜像 stuck scanner 注册范式）。
- **失效面枚举**: NopDatavReportDelivery（本条）；NopDatavExportTask（同族同修）；NopDatavChatMessage（随 C5-002 一并考虑保留期）；物理文件侧（DaoResourceFileStore 附件）由业务删除路径管，无涉。
- **信心水平**: 确定
- **误报排除**: 非审计/合规要求下的合理保留（无任何 owner doc 声明保留策略）。
- **复核状态**: 已保留（回码验证：两 recovery 类仅状态改写无删除路径属实；现状：新发现，跨单元关联 cra-index WI1.6 C7-001 / WI1.7 C7-003 账本零保留族）

## 维度复核结论

- 复核口径：5 条初审 + 2 条深挖发现逐条回码验证。
- 三态判定：**保留 7 / 降级 0 / 驳回 0**。
- 现状标注：**新发现 7**；已知未修复 0；已修复 0。
- 复核关键验证：getDashboardData/getPanelData/refreshPanel 三站点传 null rowLimit 实测确认（PanelBizModel :136/:150）；LimitedInputStream 实测 raiseError 抛错语义（C5 无界清单中上传链判定由"注意"升级为确认有界）；导出 workbook 乘积上界确未声明。
- 跨单元关联登记：C5-005/C5-R2-002 → 账本零保留族（WI1.6 C7-001 / WI1.7 C7-003）；C5-R2-001/C5-002 → 无 LIMIT 全量载入族（WI1.5 C5-001）。

## 最终保留项

| CL | 发现 | 严重度 | 现状 | 跨单元关联 |
|---|---|---|---|---|
| CL-009 | C5-001 getDashboardData 主路径无行数上界全量物化 | P1 | 新发现 | — |
| CL-010 | C5-002 ChatBI 会话历史全量加载 | P2 | 新发现 | WI1.5 C5-001（族） |
| CL-011 | C5-003 导出全量内存 workbook 乘积上界未声明 | P3 | 新发现 | — |
| CL-012 | C5-004 globalWorker 队列无重叠防护积压 | P3 | 新发现 | — |
| CL-013 | C5-005 交付/导出账本行零保留（F4 watch） | P3 | 新发现 | WI1.6 C7-001, WI1.7 C7-003 |
| CL-021 | C5-R2-001 getScreenSnapshotHistory 全列全量加载 | P2 | 新发现 | WI1.5 C5-001（族） |
| CL-022 | C5-R2-002 listChatSessions 全量加载无分页 | P3 | 新发现 | — |
