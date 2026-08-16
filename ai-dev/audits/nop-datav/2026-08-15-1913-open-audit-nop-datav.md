> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-datav
> Remediation: P0+P1 findings drafted into plan `ai-dev/plans/nop-datav/2026-08-15-2146-2-transaction-boundary-async-resource-bounds.md`（AR-1 P0, AR-2, AR-3）。AR-7 缓存键耦合登记为 plan 1 Phase 4 Exit Criteria 强制裁定项。P2 findings（AR-4/5/6/7）triaged to `ai-dev/backlog/nop-datav-audit-followups.md`（#66-69）。

# nop-datav Open-Ended Adversarial Audit（2026-08-15 19:13 批次）

- **审计目标**: `nop-datav/` 全模块（service/web/dao/meta/api/app + model/deploy 未接线目录）
- **审计基线**: live code（worktree `nop-entropy-feat-nop-datav`，HEAD 980b9fd5b）
- **方法**: `ai-dev/skills/open-ended-adversarial-review-prompt.md`。先完整读取该 prompt 与 `AGENTS.md`，再全量阅读 nop-datav 手写 service 层（17 BizModel/全部 codec/scheduler/recovery/exporter）、auth 配置（action-auth/data-auth/application.yaml）、beans.xml、代表性测试，并解包平台 nop-orm/nop-bio 源码 jar 逐行核实 ORM 事务/flush 机制。
- **去重**: 对照 `ai-dev/audits/nop-datav/2026-08-10-1516-open-audit-nop-datav.md`（AR-1..AR-7）与 `2026-08-15-1913-multi-audit-nop-datav.md`（P0-01..P2-48）。仅报告未被覆盖的新发现；已知未修复项在末节简要确认现状。
- **使用的启发视角**: 事务边界追踪者、异常路径侦探、IoC 侦探（测试注入 vs 生产装饰器差异）、10x 规模运维者、死代码清道夫。
- **实证手段**: 本审计构建了一个临时验证测试（经 GraphQL engine 以真实事务装饰器路径调用 `toggleShare`/`createShare` mutation，再以裸 JDBC 读 `NOP_DATAV_SHARE.PASSWORD_HASH`），**实证复现了 AR-1 的数据损坏**后删除了该临时测试（工作区已恢复干净）。

## 发现总览

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | 事务 commit dirty-flush 摧毁分享密码哈希（安全契约破裂，已实证） |
| P1 | 2 | 事务未提交即提交异步任务（导出/报告静默不执行竞态）；导出路径缺面板数上界（资源放大） |
| P2 | 4 | nextSeq 全量加载；chatToQuery 写路径无事务原子性；审计 pattern 覆盖不对称；缓存键与未来数据集 ACL 的耦合 |

---

## 新发现

### [AR-1] `[P0]` toggleShare/revokeShare 在事务 commit 时把 `PASSWORD_HASH` 脏检查 flush 成 NULL —— 分享密码保护被静默摧毁（已实证复现）

- **优先级依据**: 安全契约破裂（密码保护静默失效）+ 数据损坏，且直接推翻今日多维度审计攻击面矩阵 #6「passwordHash ✅ 闭合（有测试）」的结论
- **文件**:
  - `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardShareBizModel.java:265-280`（`doToggleShare`：`:277 updateEntityDirectly(share)` → `:278 share.setPasswordHash(null)`）
  - 平台机制链（nop-orm 2.0.0-SNAPSHOT sources）:
    - `io/nop/orm/txn/OrmTransactionListener.java:29-31`（`onBeforeCommit → ormTemplate.flushSession()`）
    - `io/nop/orm/session/OrmSessionImpl.java:520-535`（`updateDirectly` 后实体保持 MANAGED）、`:1158`（`persisterPostUpdate → orm_clearDirty`）
    - `io/nop/orm/support/OrmEntity.java:473-490`（`markPropDirty` 记录 oldValues 并 `enhancer.internalMarkDirty` 进 session 脏缓存）
    - `io/nop/orm/session/CascadeFlusher.java:199-204`（`isManaged() && orm_dirty() → flushUpdate`）
- **证据片段**:
  ```java
  // NopDatavDashboardShareBizModel.doToggleShare（@BizMutation → 生产路径必然事务化）
  share.setEnabled(enabled ? ENABLED_TRUE : ENABLED_FALSE);
  share.setUpdatedBy(operator);
  share.setUpdateTime(new Timestamp(System.currentTimeMillis()));
  daoProvider.daoFor(NopDatavDashboardShare.class).updateEntityDirectly(share); // UPDATE 已执行，实体转为 clean+MANAGED
  share.setPasswordHash(null);   // <-- 对 session 缓存中的 MANAGED 实体置值 → 重新标记 dirty
  return share;                  // 方法返回后事务 commit → flushSession → 补发 UPDATE NOP_DATAV_SHARE SET PASSWORD_HASH=NULL
  ```
  **实证结果**（临时测试经 `graphQLEngine` 以 mutation 调用真实事务路径，裸 JDBC 读库）:
  ```
  toggle before = hash=$2a$10$fakehashfakehash...,enabled=1
  toggleShare response = ApiResponse[status=0, ... enabled=0 ...]   ← 调用成功
  toggle after  = hash=<NULL>,enabled=0                              ← 哈希已被 commit flush 抹掉
  ```
- **严重程度**: P0
- **现状**: `revokeShare` 与 `toggleShare`（共用 `doToggleShare`）在 @BizMutation 事务内，先 `updateEntityDirectly`（SQL 已落、实体清脏），再对同一 attached 实体 `setPasswordHash(null)`（mask-on-return）。该 setter 使实体重新变脏并进入 session 脏缓存；事务提交时 `OrmTransactionListener.onBeforeCommit → flushSession → CascadeFlusher` 对 managed+dirty 实体补发 `UPDATE ... SET PASSWORD_HASH=NULL`。带密码的分享一旦被 owner 执行过任何 toggle（启用/禁用/吊销），密码哈希即被物理抹除；`getSharedDashboard` 的 `verifySharePassword` 对空哈希直接放行 → **凭 token 即可匿名访问原受密码保护的看板**。且模块没有任何 API 可为既有分享重设密码（仅 createShare 时设置），损坏不可自愈。
- **风险**: ① 密码保护静默失效 = 分享安全模型的第二道防线（密码）整体失守，owner 无感知（响应里 passwordHash 本来就被脱敏）；② 该缺陷直接污染「删除生命周期/吊销」语义——owner 出于安全考虑吊销分享的行为反而触发密码剥离；③ 今日多维度审计据测试判「passwordHash 闭合」，会误导修复排序。
- **为什么全部测试都是绿的**: 测试（`TestNopDatavSharePasswordHashMasking`、`TestNopDatavShareManagementBizModel`、`TestNopDatavShareE2E` 等）全部经 `@Inject INopDatavDashboardShareBiz` 直调裸 bean——不经 GraphQL 引擎的 biz action 装饰链，**没有事务包装就没有 commit flush**，损坏路径在测试中物理不可达。这是「测试直调 bean vs 生产走事务装饰器」的系统性盲区（同一盲区还产出 AR-2）。
- **补充（不对称性）**: `createShare` 的同款 mask（`:127-128`）经实证**不**触发 wipe——`saveEntityDirectly` 走 `saveDirectly`，新实体不进入 session 实体缓存，flush 时不在 `forEachDirty` 集合内。但这依赖平台内部实现细节（saveDirectly 不挂缓存）；若平台未来把直接保存的实体也纳入缓存，createShare 会同样中招。修复时应一并处理。
- **建议**:
  1. `doToggleShare`/`createShare` 的脱敏不再改写 attached 实体：返回前构造出参 DTO（或 `daoProvider.detachEntity` / 直接 new 一个仅携带可暴露字段的副本），实体本体不动；
  2. `listShares`（@BizQuery 无事务，当前侥幸无害）一并改为 DTO 出参，消除对「查询无 flush」的隐式依赖；
  3. 补回归测试：经 `graphQLEngine` mutation 路径（真实事务装饰器）调用 toggleShare 后，裸 JDBC/新 session 断言 `PASSWORD_HASH` 仍为 BCrypt 哈希，且 `getSharedDashboard` 错误密码仍被拒——现有测试体系必须至少有一条走事务路径的密码回归。
- **信心水平**: 确定（静态机制链五环逐行核实 + 生产等价路径实证复现）
- **发现来源视角**: 事务边界追踪者 + IoC 侦探（测试注入 vs 生产装饰器差异）

---

### [AR-2] `[P1]` createExportTask/triggerReportNow 在事务提交前把异步任务提交到另一条连接 —— worker 读不到未提交行，导出/报告交付竞态性静默不执行

- **优先级依据**: 真实缺陷（用户请求的导出/报告按概率静默不执行、并发配额被占），静默无日志，与 AR-1 同根因（生产事务装饰器 vs 测试直调）
- **文件**:
  - `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java:150-158`（INSERT pending 任务 → `submitExecution`）、`:216-221`（worker `getEntityById(taskId) == null → return null` **零日志**）
  - `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/report/ReportDeliveryExecutor.java:102-140`（同模式：`:123 saveEntityDirectly(delivery)` → `:127 globalWorker().submit`）、`:173-176`（`delivery == null → return null` 零日志）
- **证据片段**:
  ```java
  // createExportTask（@BizMutation → 生产路径运行在事务内）
  NopDatavExportTask task = newTaskEntity(...);
  daoProvider().daoFor(NopDatavExportTask.class).saveEntityDirectly(task); // INSERT 加入未提交事务
  submitExecution(task.getTaskId(), operator);   // <-- globalWorker 立即开工（另一条连接）

  // executeTask（worker 线程，runInNewSession → 新连接，READ_COMMITTED 下看不到未提交 INSERT）
  NopDatavExportTask task = dao.getEntityById(taskId);
  if (task == null) {
      return null;    // <-- 静默 no-op：不 markFailedSafe、不打日志
  }
  ```
- **严重程度**: P1
- **现状**: 生产路径上 `createExportTask` / `triggerReportNow`（均 @BizMutation）在事务**提交前**就把异步执行体提交到 `GlobalExecutors.globalWorker()`。worker 经 `runInNewSession` 用独立连接读任务/交付行；在创建方事务提交前该行不可见（AR-1 已实证 mutation 事务真实存在且 INSERT 参与其中）。竞态窗口 = 提交异步任务 → 方法返回 → GraphQL 响应序列化 → 事务提交，与 worker 线程启动 + 新 session 建立的首查同为毫秒级，非确定性命中。命中时 worker 走 `task == null / delivery == null` 分支**静默返回**：导出任务永远停在 PENDING（占用 `max-concurrent-per-user=3` 配额，直到 stuck 扫描器（默认 60min 阈值，且宿主需注册 IJobScheduler）把它标成 FAILED 且 reason 是误导性的 "stuck beyond timeout"）；报告交付同理，`triggerReportNow` 返回的 deliveryId 对应的交付永不执行。
- **风险**: ① 用户手动触发的导出/报告按概率"石沉大海"，无任何日志线索（两个 null 分支都不留痕）；② 反复重试会耗尽并发配额把用户锁死；③ 测试全部直调 biz/executor（无事务装饰器 → INSERT 立即可见），竞态在测试中物理不可达——又一处「测试绿、生产坏」。
- **建议**: ① 异步提交移出未提交事务：注册 `txn().afterCommit(() -> submitExecution(...))`（模块内已有 afterCommit 先例语境）或在 worker 首查 null 时以短退避重查一次再判 null；② 两个 null 分支至少补 ERROR 日志 + `markFailedSafe`（把静默 no-op 变成可观测失败）；③ 补一条经 GraphQL mutation 路径 + 人为延迟 commit 的竞态回归测试（可用测试 seam 在提交前阻塞）。
- **信心水平**: 很可能（机制链确定：事务存在性已被 AR-1 实证、隔离级别语义标准；命中率未做量化，未构造竞态复现实验）
- **发现来源视角**: 事务边界追踪者 + IoC 侦探

---

### [AR-3] `[P1]` exportDashboard 无面板数上界 —— 查询/布局路径均有 max-panels=50 防护，导出路径独缺，单请求可放大为海量 SQL + 内存驻留

- **优先级依据**: 防护不对称的真实资源缺陷：未上限面板数 × 每面板 100k 行 × 全 sheet 在内存 ExcelWorkbook 中累积，任一 user 可触发 OOM
- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/export/PanelDataExporter.java:148-191`；对照 `NopDatavDashboardBizModel.java:430-436`（getDashboardData 的 D4 上限）与 `:709-714`（saveDashboardLayout 的上限）
- **证据片段**:
  ```java
  // PanelDataExporter.exportDashboard —— 遍历全部 needsDataset 面板，无数量上限
  for (NopDatavPanel panel : exportable) {
      checkCancelled(cancelChecker);
      PanelDataResult result = dataBinder.queryPanelData(panel.getPanelId(), panel, params, maxRows + 1); // maxRows=100000
      ...
      workbook.addSheet(toSheet(sheetName, result.getColumns(), result.getRows(), cancelChecker)); // 全部 sheet 驻留内存
  }
  ```
  而面板行数的唯一入口上界只有 saveDashboardLayout 的 50；`NopDatavPanel__save`（继承 CRUD，P0-01 下 admin,user 可调）与 ChatBI 生成路径均不设面板数上限。
- **严重程度**: P1
- **现状**: 同一个"防单请求放大"威胁，模块在批量查询路径（`CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS=50`，注释明言"防单请求放大为海量 SQL"）和布局保存路径（50，存储容量界）都设了闸，唯独导出路径（放大系数最大：每面板一条 SQL + 100k 行取数 + 全量行驻留内存建 workbook + xlsx 序列化）没有任何面板数检查。
- **风险**: user 在自有（或经 P0-01 在他人）看板上堆 N 个绑定大数据集的面板后发起看板级导出 → N × 100k 行全量进内存 → globalWorker 线程 OOM / 长时间占用，拖垮同池的导出/报告/面板并行查询任务。10x 数据规模下最先崩的就是这条路径。
- **建议**: `exportDashboard` 入口复用 `CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS`（或独立 export 侧配置）做前置上限校验；与 P0-01（Panel CRUD 无 RLS 无上限）联动修复时一并考虑面板数总量约束。
- **信心水平**: 确定（防护缺失确定；可利用性以 P0-01 未修复为放大器，自有看板亦可触发）
- **发现来源视角**: 10x 规模运维者

---

### [AR-4] `[P2]` ChatBiSessionManager.nextSeq 每轮对话全量加载会话全部消息（含 CONTENT/RESULT_JSON CLOB）只为求 max(seq)+1

- **优先级依据**: 非阻塞性能缺陷（O(n²) CLOB 物化），行为正确；与 08-10 AR-7（DatavListDatasetsExecutor findAll）同族但为不同位点
- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/ChatBiSessionManager.java:253-259`
- **证据片段**:
  ```java
  private int nextSeq(String sessionId) {
      QueryBean query = new QueryBean();
      query.addFilter(FilterBeans.eq("sessionId", sessionId));
      query.addOrderField("seq", true);          // true = DESC（取 max）
      List<NopDatavChatMessage> list = messageDao().findAllByQuery(query);  // 无 limit：全量行 + CLOB
      return list.isEmpty() ? 1 : list.get(0).getSeq() + 1;
  }
  ```
- **现状**: 会话模式 `chatToQuery` 每轮 `appendTurn` 都把该会话**全部历史消息**（含 assistant 消息的结构化结果 JSON CLOB——单条可达 maxRows=1000 行的结果序列化）完整物化，仅为了取最大序号。同文件 `loadMessages`（:123-128）同样无界加载（后者有预算裁剪语义兜底，前者纯浪费）。长会话下每轮成本 O(历史总量)，累计 O(n²)。
- **风险**: 长会话（多轮大结果集）下 chatToQuery 延迟随轮数线性劣化、DB/CLOB 带宽放大；触发 LLM 超时重试进一步放大。
- **建议**: `nextSeq` 改为 `query.setLimit(1) + findFirstByQuery`（同文件 `findLatestSnapshot` 先例），或投影只取 seq 列。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-5] `[P2]` 多轮 chatToQuery 以 @BizQuery 执行三段写库且无事务原子性 —— 半轮持久化破坏「失败轮次不落库」契约，且写路径逃过 mutation 审计

- **优先级依据**: 非阻塞局部缺陷（chat 历史一致性 + 审计盲区），低概率中低影响
- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavChatBiBizModel.java:110-142`（@BizQuery 内 `appendTurn`）；`ChatBiSessionManager.java:137-159`（userMsg INSERT → assistantMsg INSERT → session UPDATE 三段写）；`nop-datav-app/src/main/resources/application.yaml:25`（audit-mutation-patterns 不含 ChatBi 查询侧写）
- **证据片段**:
  ```java
  @BizQuery   // 无事务；方法体内三次 *Directly 写库
  public ChatBiResult chatToQuery(...) {
      ...
      result.setSessionId(sessionId);
      sessionManager.appendTurn(session, question, result, operator); // 2×INSERT + 1×UPDATE，各自独立提交
      return result;
  }
  ```
- **现状**: 类注释声称「失败轮次（循环抛异常）不落库」，但 `appendTurn` 自身三段写之间无原子性：assistant 消息 INSERT 或 session UPDATE 失败时，user 消息已孤行落库；下一轮 `buildHistoryContext` 会注入这条无应答的悬空 user 消息，且 `nextSeq` 基于已落库的 user 消息继续递增，永久留下断裂轮次。另外以 Query 执行的写不进 `audit-mutation-patterns`（该配置仅匹配 mutation），会话数据的变更逃过 op-log。
- **风险**: 半轮数据污染 LLM 上下文（用户问题被无应答重复注入）；chat 数据变更无审计轨迹。触发概率低（需 appendTurn 中途故障）。
- **建议**: `appendTurn` 三段写包进单事务（`txnTemplate.runInTransaction` 或改走一个内部 @BizMutation service 方法），要么全落要么全不落；审计 pattern 评估补 `NopDatavChatBi__chatToQuery` 或在 appendTurn 落库处显式记 op-log。
- **信心水平**: 确定（机制）；影响评级为中低
- **发现来源视角**: 事务边界追踪者

---

### [AR-6] `[P2]` 审计 mutation pattern 覆盖不对称：Dashboard 全量而 Screen/ChatBI 生成/ReportTask/AlertRule/ExportTask mutation 全部缺位

- **优先级依据**: 配置覆盖缺口（审计面漂移），非行为缺陷；与 Dashboard 对称的生命周期操作（Screen 发布/回滚/缩略图）无审计
- **文件**: `nop-datav/nop-datav-app/src/main/resources/application.yaml:25-26`
- **证据片段**:
  ```yaml
  audit-mutation-patterns: NopDatavDashboard__*,NopDatavPanel__*,NopDatavFilterState__*,NopDatavDashboardShare__*
  audit-query-patterns: NopDatavDashboard__getPublishedDashboard
  ```
- **现状**: `NopDatavScreen__publishScreen/rollbackScreen/setScreenThumbnail`（与 Dashboard publish/rollback 完全对称的高敏操作）、`NopDatavChatBi__chatToDashboard/chatToScreen`（创建实体）、`NopDatavReportTask__*`/`NopDatavAlertRule__*` 的 enable/disable、ExportTask cancel 均不在审计 pattern 内；query 侧只审计了 `getPublishedDashboard`，对称的 `getPublishedScreen` 缺席。历史 commit（c67d7d7b6）显示该列表是逐次打补丁追加的（"追加 NopDatavDashboardShare__*"），Screen 一侧被系统性遗漏。
- **风险**: 发布历史/审计链对大屏侧不可追溯；`getSharedDashboard` 消费快照而快照写入方（publishScreen）无审计记录，事件溯源断链。
- **建议**: 补 `NopDatavScreen__*,NopDatavScreenSnapshot__*`（mutation）与 `NopDatavScreen__getPublishedScreen`（query）；评估 ChatBi 生成与调度类 mutation 是否纳入。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫（配置考古）

---

### [AR-7] `[P2]` DashboardPanelQueryCache 缓存键无任何用户维度 —— 当前正确，但与已知未修复的数据集 ACL 缺口（multi-audit P1-03）形成定时耦合

- **优先级依据**: 前瞻性设计耦合记录（防止 P1-03 修复时引入跨用户缓存投毒）；当前无行为缺陷
- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/query/DashboardPanelQueryCache.java:57-61`；`PanelDataBinder.java:159-168`（键构造点）
- **证据片段**:
  ```java
  public static String buildKey(String refDatasetId, Map<String, Object> evaluatedParams, Integer rowLimit) {
      return "ds:" + refDatasetId
              + "|params:" + JsonTool.stringify(evaluatedParams == null ? Map.of() : evaluatedParams)
              + "|limit:" + (rowLimit == null ? NO_ROW_LIMIT : rowLimit);   // <-- 无 user/permission 维度
  }
  ```
- **现状**: 当前数据集 SQL 的全部输入 = 数据集身份 + 求值后参数 + rowLimit（PanelSqlBuilder 只做 `?` 绑定、无会话上下文注入），结果与用户无关，键完整——缓存正确。但 multi-audit P1-03 已裁定数据集级权限（NopReportDatasetAuth）未接线且应修复；一旦数据集可见性/行过滤按用户生效，同样的 (dataset, params, limit) 键会让低权用户命中高权用户回填的缓存条目——跨用户数据泄露，且以 P0 级别显现。
- **风险**: P1-03 的修复者很可能只改查询侧而不知道缓存键契约；该耦合在代码中无任何警示注释（缓存类 javadoc 仅声明"全部影响查询结果的输入"，此论断隐含"用户不影响结果"这一未记录假设）。
- **建议**: 在 `buildKey` javadoc 显式记录"键正确性依赖数据集查询与用户身份无关"这一前提，并标注"接入数据集 ACL 时必须让缓存按用户失效或绕过"；理想情况下把该前提写成一条单元测试注释锚点。
- **信心水平**: 确定（耦合存在）；当前无缺陷
- **发现来源视角**: 未来破坏者

---

## 已知问题现状确认（去重引用，非新发现）

以下问题在 `2026-08-15-1913-multi-audit-nop-datav.md` / `2026-08-10-1516-open-audit` 中已报告，本次独立复核确认**仍未修复**，现状无变化（不展开重述）：

- **[P0-01]** `FNPT:NopDatavPanel:mutation` 仍为 `admin,user` 且无 RLS（`nop-datav.action-auth.xml:113` 实证）。
- **[P0-02]** getPanelData/refreshPanel/resolveLinkage/resolveJump 仍以无 RLS 的 Panel 为权限锚点（`NopDatavPanelBizModel.java:93-144` 实证）。
- **[P0-03]** 8 个 unique-key 仍未物化。**补充新事实**：`nop-datav-app/application.yaml:32` 配置了 `nop.orm.init-database-schema: true`（auto-DDL 路径），该路径与 deploy SQL 走同一个 `ddl.xlib` CreateTable 模板（`uniqueKey.constraint` 属性不存在即跳过约束，`ddl.xlib:81-91` 实证），测试基类 `AbstractNopDatavTest.createAllTables` 用的也是同一 `DdlSqlCreator`——即**所有建表路径（deploy SQL、app auto-DDL、测试建表）产出的表都没有 UK**，多维度审计"deploy SQL 为唯一物化途径"的表述少算了一条路径，但结论（唯一性零强制）不变且更强。
- **[P1-01/02]** 5 个子实体 query 与 FilterState query+mutation 仍绑 `admin,user`（action-auth `:73,80,87,94-95,194,201` 实证）。
- **[P1-04]** ChatBI `datav-query-dataset` 的 maxRows 仍由 LLM 入参直取、无钳制（`DatavQueryDatasetExecutor.java:104-106,159` 实证）。
- **[P1-11]** `MockChannelMessageService.setNoBindingUsers` 仍全仓零调用（IM 部分成功分支仍无测试）。
- **[P2-23]** `app-service.beans.xml` 仍使用 `ioc:` 属性而无 `xmlns:ioc` 声明（`:13` 等 20 处实证）。

**08-10 审计后已修复确认**（现状更新，无需再报）: AR-3（UK 启发式已收敛到 unique/duplicate，`DatavGenerateScreenExecutor.java:521-531`）；AR-5（两 executor 已统一引用 `NopDatavOperatorResolver.SYSTEM_OPERATOR`）；AR-6（`.join()` 已包 CompletionException，handler 吞异常已加 debug 日志）；AR-7 部分修复（status 过滤已下推 QueryBean，keyword 仍内存过滤 + 全行加载残留）。

---

## 总评 —— 最值得关注的 1-3 个方向

1. **"测试直调 bean vs 生产事务装饰器"是本模块的系统性盲区，且已经产生了一个 P0。** AR-1（commit flush 抹掉密码哈希）与 AR-2（未提交事务内提交异步任务）是同一根因的一对孪生缺陷：所有 E2E 测试经 `@Inject` 拿到的是裸 bean，没有 GraphQL 引擎的事务装饰器，于是生产路径独有的 commit-flush 与提交可见性语义在测试中物理不可达。多维度审计的攻击面矩阵据此把 passwordHash 判为"闭合"，恰好反证了这条盲区的威力。**修复 AR-1/AR-2 的同时，应当为关键写路径补"经 graphQLEngine mutation 的回归测试"作为一类固定测试资产**，否则下一次同族缺陷依然全绿通过。
2. **资源上界的防护栅栏只修了热路径。** 模块已经建立了 max-panels / max-rows / parallelism / cache-admission 的上界体系（质量高于平均），但导出路径（AR-3，放大系数最大的一条）独缺面板数闸，`nextSeq`（AR-4）独缺 limit——两个遗漏都在"测试数据规模永远触发不了"的冷路径上，属同一模式。
3. **跨子系统的隐性契约（缓存键、审计 pattern）缺少登记。** AR-7 的缓存键正确性依赖一个未写下来的假设（数据集查询与用户无关），AR-6 的审计 pattern 在增量追加中漏掉了整个 Screen 侧。两者都不会在功能测试中失败，但都会在下一个需求（数据集 ACL、审计合规）到达时变成陷阱。

## 盲区自评

- AR-2 的竞态未做实证复现（需要人为延迟 commit 的测试基建），机制链依赖标准隔离级别语义推定，标记"很可能"而非"确定"；命中率未量化。
- 未运行全量 `./mvnw test`（仅运行了自建验证测试及其编译）；既有测试的通过性以 surefire 报告为准。
- 前端消费面（web pages、flux 编辑器）与生成物 `_gen` 未逐文件审（多维度审计已覆盖生成管线六维度零漂移，本次仅抽查）。
- `deploy/sql` 三方言 DDL 未重新全文核对（采信 multi-audit P0-03/P1-08，仅补查了 auto-DDL 路径）。
- ScreenLayoutParser/ScreenThemeParser/AlertAggregator/AlertThresholdComparator/NotificationSender 及 ChatBI 各 executor 的内部逻辑仅抽查未逐行审——若 AR-1 类"事务内隐式行为"藏在这些类与调用方的组合里，可能漏报。
- 多节点部署形态（限流/缓存的单节点语义）沿设计文档裁定，未重新评估。

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | 事务 commit dirty-flush 数据损坏（分享密码哈希） |
| P1 | 2 | 未提交事务内异步提交竞态；导出路径防护栅栏缺失 |
| P2 | 4 | 查询无界加载；写路径原子性/审计盲区；审计 pattern 不对称；缓存键耦合 |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
