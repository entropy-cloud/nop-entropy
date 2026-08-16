> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-datav

# nop-datav Open-Ended Adversarial Audit（2026-08-16 07:19 批次）

> 处置（2026-08-16）：AR-1（P0）已入 plan `ai-dev/plans/nop-datav/2026-08-16-2137-1-chatbi-generate-dataset-visibility-closure.md`（active）；AR-2/AR-3（P2）与 AR-1 建议 4（独立裁定项）已登记 `ai-dev/backlog/nop-datav-audit-followups.md` #70/#71/#72。

- **审计目标**: `nop-datav/` 全模块（service/dao/meta/web/app/deploy/model）
- **审计基线**: live code（worktree `nop-entropy-feat-nop-datav`，HEAD 60dd27a6e）
- **方法**: `ai-dev/skills/open-ended-adversarial-review-prompt.md` + `AGENTS.md` 全文阅读后，以代码异常信号驱动开放探索。重点覆盖 **2026-08-16 三个修复 commit（2146-1 权限收口 / 2146-2 事务边界 / 2146-3 DDL 级联卫生）引入的最新代码**——这批代码晚于既有全部审计（08-10、08-15 两批 open + 08-15 multi），是本次发现的主要来源。
- **去重**: 对照 `ai-dev/audits/nop-datav/2026-08-10-1516-open-audit`（AR-1..7）、`2026-08-15-1913-open-audit`（AR-1..7）、`2026-08-15-1913-multi-audit`（P0-01..P2-48）与 backlog `nop-datav-audit-followups.md`。旧发现仅在末节确认现状，不重述。
- **使用的启发视角**: GraphQL 契约考古学家（P1-03 可见性接线的消费面盘点）、事务边界追踪者（attached 实体生命周期）、未来破坏者（设计文档承重前提核对）。
- **实证手段**: 纯静态（逐行阅读 + 平台 nop-biz/nop-graphql-core sources jar 核对 CrudBizModel 动作注册与 session 语义）。未运行测试、未构造动态复现。

## 发现总览

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | ChatBI 生成工具绕过数据集可见性边界（P1-03 修复自身的缺口，授权链完整可达） |
| P1 | 0 | — |
| P2 | 2 | 生产路径返回 stale 实体（测试直调盲区家族新成员）；补偿清单登记窄洞 |

---

## 新发现

### [AR-1] `[P0]` datav-generate-dashboard / datav-generate-screen 完全绕过 P1-03 数据集可见性边界 —— 非 admin 可经 ChatBI 构造「引用任意数据集的看板」并经自有看板查询路径外泄数据

- **优先级依据**: 安全违约 + 契约破裂——直接击穿 plan 2026-08-15-2146-1 自己立下的设计不变量（permission-sharing-design.md:169「D2 收敛后 DatasetRef 的绑定与篡改面仅 admin 可达，**非 admin 无法构造『引用他人数据集的看板』**」），且修复批次的负向测试恰好漏掉这两个 executor（absent test for changed behavior）。
- **文件**:
  - `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateDashboardExecutor.java:275-280`（校验只查 `ds != null && status==1`，无可见性判定；`:275` 注释「按 RLS 由 DAO 处理」为不实陈述——nop-report 数据集无 DAO 层 RLS，正是 P1-03 修复自己在 `DatavListDatasetsExecutor.java:108` 明确记录的事实）
  - `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateScreenExecutor.java:176-180, 292-300`（同款：预加载 + 校验均无可见性判定）
  - 对照组（已正确接线）: `DatavQueryDatasetExecutor.java:102-109`、`DatavDescribeDatasetExecutor.java:73-79`、`DatavListDatasetsExecutor.java:110-121`
  - 授权链下游: `nop-datav-service/src/main/java/io/nop/datav/service/query/PanelDataBinder.java:125-147`（无任何数据集归属检查，按 D4 裁定锚定 Dashboard RLS）
  - 权限入口: `nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml:308-318`（`chatToDashboard`/`chatToScreen` roles=**admin,user**）
- **证据片段**:
  ```java
  // DatavGenerateDashboardExecutor.validateAndPlanPanel（P0-02 修复后 Panel CRUD 已收敛 admin，
  // 本 executor 是非 admin 用户唯一的 DatasetRef 绑定构造路径）
  // 加载 + 校验 status=1（活跃）。按 RLS 由 DAO 处理；此处显式判 status。   <-- 不实注释
  NopReportDataset ds = dp.daoFor(NopReportDataset.class).getEntityById(datasetSid);
  if (ds == null || ds.getStatus() == null || ds.getStatus() != STATUS_ACTIVE) {
      return new ValidationError(ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND, ...);
  }
  // 无 ChatBiDatasetVisibility.isVisible 判定 —— operator/admin 身份就在 context 里
  // （本类的 resolveOperator(:197-206) 已经在用它填 createdBy），但从未用于可见性
  ```
  完整攻击链（每一环均已静态核实）:
  1. `user` 角色调用 `chatToDashboard("建一个看板，图表用 datasetSid=ds-xxx 绑定")`——`description` 是用户可控文本，LLM 按工具 schema（`datav-generate-dashboard.tool.xml` 明文宣告「datasetSid MUST reference an existing active (status=1) dataset」即合法）原样透传任意 sid；
  2. executor 校验通过（存在 + 活跃即可）→ 短事务创建 `DatasetRef(refDatasetId=sid)` + `Panel` + `Dashboard(createdBy=请求者)`；
  3. 请求者是生成看板的 owner → `getDashboardData`/`getPanelData`/`exportDashboard` 的 Dashboard RLS（`createdBy==userName OR publishStatus=10`，nop-datav.data-auth.xml:9-16）放行；
  4. `PanelDataBinder` 执行该数据集的 `dsText` SQL 并返回全部行——**他人私有数据集的数据完整外泄**，且可反复查询/导出（maxRows=100k）。
- **严重程度**: P0
- **现状**: P1-03 修复（同日 commit cf0d40970）只给 list/describe/query 三个 executor 接了 `ChatBiDatasetVisibility`，generate 两个 executor 被遗漏。D4 裁定把 `PanelDataBinder` 排除在可达性检查之外**全部依赖**「非 admin 无法构造引用他人数据集的看板」这一前提——该前提被 generate 路径打破后，整个面板路径的授权传递论证失效。sid 可达性放大外泄面：sid 是人类可读业务键（`ds-sales` 等，测试数据全为此形态，可枚举猜测）；且 `publishDashboard` 序列化的快照明文携带 `datasetRefs[].refDatasetId`（`NopDatavDashboardBizModel.java:893-894`），任何登录用户经 `getPublishedDashboard`（admin,user）、甚至匿名者经分享 token 的 `getSharedDashboard` 都能读到他人看板所用数据集的 sid 清单。
- **风险**: P1-03 修复目标的反面陈述「user 只能触达自己拥有的 + 已发布的」在数据维面不成立；同一修复批次的安全闭合声明（plan 172 行 closure 声明、owner doc §6）基于不完整消费面盘点，会误导后续开发与审计以为数据集面已闭合。
- **建议**:
  1. 两个 generate executor 的数据集校验处补 `ChatBiDatasetVisibility.isVisible(ds, operator, admin)` 判定（身份解析代码已存在，`resolveOperator` 同源），不可达返回 `ERR_DATAV_CHATBI_DATASET_NO_ACCESS`（与 describe/query 同语义：显式拒绝）；
  2. 补负向回归：镜像 `TestDatavQueryDatasetExecutor` 的可见性用例——非 owner 非 admin 经 generate 工具绑定他人数据集被拒（executor 直调 + 身份 context）；
  3. 修正 `DatavGenerateDashboardExecutor.java:275` 不实注释；`ai-design.md` §6 与 `permission-sharing-design.md` §D4 增补 generate 工具为可见性消费方，并同步修正 D4 的承重前提表述（或注明前提现在成立是因为 generate 也接线了）；
  4. 顺带评估：快照 `datasetRefs[].refDatasetId` 对匿名分享面的暴露是否需要收敛（独立裁定，不与本修复绑定）。
- **信心水平**: 确定（授权链五环逐行核实：action-auth 角色、executor 校验缺失、Dashboard RLS 过滤器、PanelDataBinder 无检查、快照序列化字段；无一环依赖推测）
- **发现来源视角**: GraphQL 契约考古学家（P1-03 接线的消费面盘点）+ 未来破坏者（设计文档承重前提核对）

---

### [AR-2] `[P2]` setScreenThumbnail 生产路径返回 session 缓存中的 stale 实体——javadoc 声称的「返回最新主表行」在事务装饰器路径下不成立

- **优先级依据**: 真实响应契约漂移但影响局部（单 action、响应字段过期、DB 状态正确、有 refetch 兜底），不构成阻塞
- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavScreenBizModel.java:287-305`；测试侧 `TestNopDatavScreenBizModel.java:651-677`（直调 bean，断言新 thumbnail 可见）
- **证据片段**:
  ```java
  // 仅更新 thumbnail 列（部分列更新，保留乐观锁 version）
  jdbcTemplate.executeUpdate(SQL.begin().name("updateScreenThumbnail")   // 原始 JDBC：绕过 ORM 实体缓存
          .sql("update NOP_DATAV_SCREEN set THUMBNAIL=").param(thumbnail)
          ...
  // 返回最新主表行（含更新后的 thumbnail + version）                        <-- 生产路径不成立
  return daoProvider().daoFor(NopDatavScreen.class).getEntityById(screen.getScreenId());
  ```
- **严重程度**: P2
- **现状**: `setScreenThumbnail` 是 `@BizMutation`——生产路径经 GraphQL 事务装饰器持有 ambient ORM session（08-15 审计 AR-1 的机制链已实证 mutation 内 DAO 加载的实体 attached 进 session）。方法内 `requireEntity` 已把旧行放入 session 缓存；随后的原始 JDBC UPDATE 绕过 ORM 缓存只改 DB；末尾 `getEntityById` 命中一级缓存返回**更新前的 attached 实例**（旧 thumbnail、旧 version）——与 javadoc 声明相反。测试全绿是因为直调 bean 无 ambient session，每次 DAO 调用各开短 session、末次读到新值——正是 08-15 总评点名的「测试直调 bean vs 生产事务装饰器」系统性盲区的又一实例（该盲区此前已产出 P0）。
- **风险**: 生产端调用者拿到 stale 响应（thumbnail 仍旧值/null、version 落后一拍）——前端若信任响应将显示未更新缩略图；version 错位可能触发后续编辑的乐观锁误判。均为响应面问题，无数据损坏。
- **建议**: 出参改为 detached 副本并显式填充新值（镜像 `NopDatavDashboardShareBizModel.toSanitizedView` / `NopDatavExportTaskBizModel.toCreatedTaskView` 先例），或在 JDBC 更新后对 attached 实体同步 setter（两者取一，勿再依赖 `getEntityById` 重读）；补一条经 `graphQLEngine` mutation 路径断言响应含新 thumbnail 的回归。
- **信心水平**: 很可能（机制链与 08-15 AR-1 实证的 session 语义同源；本批次未动态复现）
- **发现来源视角**: 事务边界追踪者

---

### [AR-3] `[P2]` 生成路径补偿清单的登记依赖 handler 端 JSON 解析成功——「失败不落库」契约存在窄洞，且失败仅 DEBUG 可见

- **优先级依据**: 低概率边界（executor 自产 JSON 解析失败）下的契约洞；非阻塞
- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavChatBiBizModel.java:423-447`（dashboard handler）、`:500-524`（screen handler）；对照 `ChatBiToolCallingLoop.java:216-223`（handler 异常 DEBUG 吞）
- **证据片段**:
  ```java
  try {
      Object parsed = JsonTool.parseNonStrict(content);
      if (parsed instanceof Map) {
          Object dashboardId = ((Map<String, Object>) parsed).get("dashboardId");
          if (dashboardId != null) {
              accumulator.setCreatedEntityId(dashboardId.toString());
              if (createdDashboardIdsSink != null) {
                  createdDashboardIds.add(dashboardId.toString());   // <-- 补偿清单仅在此登记
              }
          }
      }
  } catch (Exception ignore) {
      // 解析失败不影响循环（与 query handler 容忍一致）
  }
  ```
- **严重程度**: P2
- **现状**: P1-05 的失败补偿契约（`ai-design.md` §11「循环异常时补偿删除本轮全部生成物（失败不落库承诺）」）依赖 `createdDashboardIds` 清单完整。清单登记发生在 handler 的 try 块内：生成工具**已成功落库**但 handler 解析回执 JSON 失败（或回执非 Map/无 id 字段）时，id 不进清单；此后循环失败触发 `compensateGeneratedDashboards` 时该看板不在清单内——孤儿草稿实体残留，违反落档承诺。解析失败仅 DEBUG 日志（两层吞：handler 自身 catch + loop 层 catch），残留无从追溯。
- **风险**: 罕见但违背显式契约；LLM 循环失败场景本就低频，叠加解析失败概率更低——影响是用户可见的"幽灵草稿看板/大屏"与无法解释的命名占用（dashboardName UK 冲突）。
- **建议**: 补偿登记与结果提取解耦——executor 成功路径的回执 id 可信（同一 producer/consumer 模块），handler 解析失败时至少 WARN 并考虑让 generate executor 在成功落库后直接写补偿清单（如经 context 传递 sink），或把该边界显式记入 §11 契约的已知例外。
- **信心水平**: 确定（代码路径确定；触发概率评估为低）
- **发现来源视角**: 异常路径侦探

---

## 已知问题现状确认（去重引用，非新发现）

以下旧发现经本次独立复核确认**仍未修复**，现状无变化（登记于 `ai-dev/backlog/nop-datav-audit-followups.md`，不展开）：

- **backlog #66**（08-15 AR-4）: `ChatBiSessionManager.nextSeq` 仍全量加载会话消息求 max(seq)（`ChatBiSessionManager.java:253-259`，无 `setLimit(1)`）。
- **backlog #67**（08-15 AR-5）: 多轮 `chatToQuery` 仍以 `@BizQuery` 执行 `appendTurn` 三段写库无原子性（`NopDatavChatBiBizModel.java:295-305`）。
- **backlog #68**（08-15 AR-6）: `nop-datav-app/application.yaml:25-26` 审计 pattern 仍缺 Screen/ChatBI 生成/ReportTask/AlertRule/ExportTask 侧。
- **backlog #69**（08-15 AR-7）: 缓存键前提已落档 `ai-design.md` §6，但 `DashboardPanelQueryCache.buildKey` javadoc（`:52-56`）仍无「查询结果与用户身份无关」前提锚点。
- **08-10 AR-1**: `nop-datav-core` 目录仍在但父 pom `<modules>` 仍不含它（`nop-datav/pom.xml:20-27`），孤儿状态未变。08-10 AR-2（nop-datav-chart 空壳）已随目录删除解决。
- **multi-audit P2-23**: `app-service.beans.xml` 仍使用 `ioc:` 属性而根元素无 `xmlns:ioc` 声明。

**08-15 审计后已修复确认**（本次逐项验证，无需再报）: AR-1（`toSanitizedView` detached 副本 + `TestNopDatavShareToggleTransactionPath` 回归）；AR-2（双 executor afterCommit + `waitForTaskRow` 短退避 + ERROR 日志 + `markFailedSafe`）；AR-3（exportDashboard 面板数上限）；P1-04（`clampMaxRows` 服务端硬钳制）；P0-01/P0-02/P1-01/P1-02/P1-10（2146-1 全套）；P0-03（2146-3 UK 三方言物化 + 存量迁移脚本）；08-10 AR-3（UK 启发式已收敛 unique/duplicate 并留有拒绝理由注释）；AR-5（`NopOperatorFallback` 已删除，统一引用 `NopDatavOperatorResolver.SYSTEM_OPERATOR`）；AR-6（`.join()` 已解包为结构化 `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED`，双层 DEBUG 日志）；AR-7 主体（list 侧 SQL 下推 createdBy+status）。

---

## 总评 —— 最值得关注的 1-3 个方向

1. **同日修复批次内部的消费面闭合缺口是当前最大风险源。** AR-1 表明：P1-03 这类权限收口修复的质量取决于「消费面盘点」的完整性，而 2146-1 的盘点把 generate 工具漏在了清单外，且 D4 的排他论证恰好建立在被漏掉的前提上。修复 AR-1 时值得把「数据集 sid 的全部消费面」（list/describe/query/generate-dashboard/generate-screen/PanelDataBinder/快照序列化）做成 owner doc 里的一张固定清单，否则下一个数据消费工具还会重演。
2. **「测试直调 bean vs 生产事务装饰器」盲区仍在产出新成员。** 2146-2 已经为 share/export 路径建立了 detached-copy 惯例与 graphQLEngine 级回归，但 AR-2（setScreenThumbnail 的 JDBC-后-重读）说明该惯例尚未被系统性扫过全模块——建议以「@BizMutation 内原始 JDBC 写 + 实体重读/返回」为模式做一次全量清点。
3. **模块整体质量趋势良好。** 三批修复的落点（事务边界、可观测性、负向测试、文档落档）与既有审计建议吻合度高，本批新发现数量收敛（1 个 P0 集中在最新未审代码，2 个 P2 为边角），修复-审计回路在有效工作。

## 盲区自评

- AR-1 未做动态复现（需nop-ai chat 栈与 LLM 配合）；授权链各环以静态代码为准，其中「LLM 会透传用户指定的 sid」基于工具 schema 与 prompt 的常规行为推断，非实测。
- 未运行 `./mvnw test`；修复确认基于代码比对与既有测试文件阅读，未验证 578/599 绿的当下状态。
- `deploy/sql` 三方言、`_add_tenant` 的 MySQL `drop constraint` 语法兼容性（需 MySQL ≥ 8.0.19）、web pages/view 层、`_gen` 生成物均未重审（采信 multi-audit 与生成管线零漂移结论）。
- AlertAggregator/AlertThresholdComparator/NotificationSender/ScreenLayoutParser/ScreenThemeParser/codec 族仅抽查；PanelSqlBuilder 注入屏障采信本批新增测试（先红后恢复）。
- 多节点部署形态（限流/缓存单节点语义）沿既有裁定，未重评。

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | ChatBI 生成工具绕过数据集可见性（授权链完整可达，破坏 2146-1 自设不变量） |
| P1 | 0 | — |
| P2 | 2 | setScreenThumbnail 生产路径 stale 响应；补偿清单登记窄洞 |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
