> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-datav
> Remediation: P0+P1 findings drafted into plans `ai-dev/plans/nop-datav/2026-08-15-2146-1-panel-subentity-auth-rbac-closure.md`（P0-01, P0-02, P1-01, P1-02, P1-03, P1-10）、`ai-dev/plans/nop-datav/2026-08-15-2146-2-transaction-boundary-async-resource-bounds.md`（P1-04, P1-05, P1-06）、`ai-dev/plans/nop-datav/2026-08-15-2146-3-ddl-integrity-cascade-hygiene.md`（P0-03, P1-07, P1-08, P1-09, P1-11, P1-12）。前置决策 P2-14 随 plan 3 Phase 1（D1）消化；P2-11 随 plan 3 Phase 2 收敛。其余 P2 findings triaged to `ai-dev/backlog/nop-datav-audit-followups.md`（#18-65）。

# nop-datav 多维度深度审计报告

- **审核日期**: 2026-08-15
- **审核范围**: `nop-datav/` 全部 7 个构建模块 + 未接线目录（model/deploy/core）— 代码、配置、测试、公开契约（GraphQL API 面、I*Biz、AI tool 导出）；交叉对照 `ai-dev/design/nop-datav/` 8 份架构设计文档与 `docs-for-ai/` 平台规范
- **执行方式**: 按 `ai-dev/skills/deep-audit-prompts.md` 调度架构执行 — 阶段一 9 组维度初审子 agent 并行派发（覆盖维度 01/02/03/04/05/07/08/09/10/11/12/13/14/15/16/18/19/20/21）；阶段二主 agent 对全部 P0/P1 发现逐条独立复核（grep/read 实证）；阶段三本汇总
- **审计基线**: live code；禁止读取 ai-dev/audits、plans、bugs、lessons

## 执行统计

| 维度组 | 初审发现 | 独立复核结果 |
|--------|---------|-------------|
| 01+02 依赖图/模块边界 | P2×5 | 保留 |
| 03+07 API 面/BizModel | P1×1, P2×10 | 保留（03-02 并入 04-03） |
| 04 ORM 模型 | P0×1, P1×2, P2×5 | P0/P1 全部实证保留 |
| 05 生成管线 | P2×1 | 保留 |
| 08+12 IoC/GraphQL | P1×1, P2×6 | P1 实证保留 |
| 09+15 错误处理/类型安全 | P1×1, P2×9 | P1 实证保留 |
| 10+11 XDSL/XMeta 对齐 | P0×1, P1×2, P2×1 | P0/P1 全部实证保留 |
| 13+14 安全/异步事务 | P0×2, P1×4, P2×4 | P0/P1 全部实证保留 |
| 16+21 测试覆盖/有效性 | P1×2, P2×6 | P1 实证保留 |
| 18+19+20 文档/命名/跨模块 | P1×1, P2×8 | P1 实证保留（与 11-01 同根因合并） |

**去重合并后总分布**: P0 × 3、P1 × 12、P2 × 48

---

# P0 发现（阻塞级，必须修复）

### [P0-01] NopDatavPanel 标准 CRUD mutation 绑定 `admin,user` 且无 RLS/无 owner 校验 — 任意用户可篡改/删除任何用户的面板

- **优先级依据**: 契约破裂 + 越权数据损坏（user 角色对任意 panelId 可写可删），且直接违背设计文档明文裁定
- **文件**: `nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml:112-113`；`nop-datav/nop-datav-service/src/main/resources/_vfs/nop/datav/auth/nop-datav.data-auth.xml`（全文仅 6 个 obj，无 NopDatavPanel）；`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavPanelBizModel.java:44-55`
- **证据片段**:
  ```xml
  <resource id="FNPT:NopDatavPanel:query" roles="admin,user"/>
  <resource id="FNPT:NopDatavPanel:mutation" roles="admin,user"/>   <!-- 唯一 mutation=admin,user 的实体 -->
  ```
  ```java
  // NopDatavPanelBizModel —— 仅覆写 doDeleteEntity（alert 级联停用），save/update/delete 无任何 owner guard
  public class NopDatavPanelBizModel extends CrudBizModel<NopDatavPanel> implements INopDatavPanelBiz {
  ```
  平台机制（`nop-auth/.../DefaultDataAuthChecker.java:176-180`）：`objAuth == null → return true` — 无 data-auth 条目即全放行。
- **现状**: 权限链完整可达：`NopDatavPanel__update/__delete/__save` → `FNPT:NopDatavPanel:mutation`（admin,user）→ `CrudBizModel.checkDataAuth` 恒通过。设计文档 `ai-dev/design/nop-datav/permission-sharing-design.md:51` 明文裁定"其余 14 实体 mutation→`admin`"，并写明理由："实体无 RLS，user 角色放开 CRUD 后水平越权即刻生效"（该理由对 Share/ExportTask 已执行收敛，Panel 被遗漏）。
- **风险**: 任意 `user` 可篡改他人面板 panelConfig（植入恶意 jump 外链、改绑 datasetRefId）或直接删除 — 跨用户数据篡改/破坏。
- **建议**: (1) `FNPT:NopDatavPanel:mutation` 收敛为 `admin`（对齐设计 §51 与其余 14 实体）；(2) 面板编辑走 Dashboard owner guard 自定义 action（镜像 Share 管理模式），或补 owner 间接 RLS。
- **信心水平**: 确定（权限推导链每一环已独立复核：action-auth 行号、data-auth obj 清单、BizModel 无覆写、DefaultDataAuthChecker 放行逻辑）
- **误报排除**: 已排除 xmeta 层 graphql:authObjName 重定向（17 个 xmeta grep 0 命中）、BizModel prepareQuery 覆写（0 命中）、delta 覆盖（仓库无 datav _delta）、data-auth 未启用（application.yaml `enable-data-auth: true`）。测试缺口佐证：TestNopDatavRbacAuth/TestNopDatavDataAuth 均未覆盖 Panel。

### [P0-02] getPanelData/refreshPanel/resolveLinkage/resolveJump 以 Panel（无 RLS 实体）为权限锚点 — 绕过 Dashboard RLS 执行任意看板的数据集 SQL

- **优先级依据**: 越权读取底层数据库业务数据（读取面），与 P0-01 同根因但独立成立（即使 mutation 收敛 admin，query 路径仍暴露）
- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavPanelBizModel.java:93-144`
- **证据片段**:
  ```java
  @BizQuery
  @Auth(permissions = "NopDatavPanel:getPanelData")     // 角色绑定 admin,user
  public PanelDataResult getPanelData(@Name("id") String id, ...) {
      NopDatavPanel panel = requireEntity(id, "getPanelData", context);  // Panel 无 RLS → 恒通过
      return new PanelDataBinder(daoProvider, jdbcTemplate).queryPanelData(id, panel, requestParams);
  }
  ```
  对照批量路径正确锚定有 RLS 的 Dashboard（`NopDatavDashboardBizModel.java:399`）；正确先例：`NopDatavExportTaskBizModel.java:399` `checker.isPermitted("NopDatavDashboard", ...)`。
- **现状**: 单面板数据查询以 Panel 为锚点，而 Panel 无行级权限（P0-01）；panelId 可经 `NopDatavPanel__findPage`（query=admin,user，无过滤）全量枚举。
- **风险**: 任意 `user` 可对**他人私有（未发布）看板**的每个面板调用 getPanelData → 执行其数据集 SQL → 读取底层数据库业务数据。Dashboard 的 `createdBy OR publishStatus=10` RLS 被面板级入口完全绕过。设计文档 §53-65 声称"Panel 归属 Dashboard 已覆盖行级校验"与 live 行为不符。
- **建议**: 四个自定义 action 在 requireEntity(Panel) 后补 `panel → dashboard` 归属校验（镜像 `NopDatavExportTaskBizModel.requireSourceAccess:377-402` 的既有正确实现）。
- **信心水平**: 确定（已独立复核代码；导出路径的正确实现证明修法在仓库内有先例）
- **误报排除**: 已排除"getDashboardData 是唯一数据出口"（前端逐面板刷新走 getPanelData/refreshPanel）；已排除导出路径同样绕过（requireSourceAccess 已正确校验）。

### [P0-03] 全部 8 个 unique-key 未物化到三方言部署 DDL（`constraint` 属性缺失，生成器静默跳过）— 数据库层唯一性完全失守

- **优先级依据**: 模型声明的数据完整性契约在物理库断裂，多个并发路径依赖 UK 兜底，将产生重复行数据损坏
- **文件**: `nop-datav/model/nop-datav.orm.xml:172,423,485,558,696,834,1184,1306`；`nop-datav/deploy/sql/{mysql,oracle,postgresql}/_create_nop-datav.sql`（全文 0 个 unique 约束、0 个索引）；根因：`nop-orm/.../ddl.xlib:81-91` 仅当 `uniqueKey.constraint` 属性存在才输出约束
- **证据片段**:
  ```xml
  <!-- orm.xml:558 —— 8 个 UK 全部只有 name+columns，无 constraint 属性 -->
  <unique-key name="UK_NOP_DATAV_SHARE_TOKEN" columns="shareToken" .../>
  ```
  ```sql
  -- _create_nop-datav.sql nop_datav_share 建表只有 PK，无任何 UNIQUE 约束
  ```
  正例对照：`nop-auth/model/nop-auth.orm.xml:176` `constraint="UK_NOP_AUTH_USER_NAME"` → DDL 正确生成 3 个 `constraint UK_` 约束。
- **现状**: 受影响 UK 及其代码依赖：
  - `shareToken` 唯一 → 匿名公共访问按 token 定位分享行可能命中多行（安全面）
  - `(sessionId,seq)` → ORM 注释明言"UK 兜底并发追加冲突显式失败"，该兜底实际不存在
  - `(userName,dashboardId)`（FilterState）→ find-then-insert upsert 并发产生重复行，状态读取闪烁
  - `(dashboardId,snapshotVersion)` / `(screenId,snapshotVersion)` → max+1 版本号竞态写出重复版本，`findLatestSnapshot` 解析歧义
  - `alertRuleId`（AlertState 1:1）→ find-then-insert 竞态产生多状态行，告警状态分裂
  - `dashboardName`/`screenName` → 见 P2-31（作用域需先裁定）
- **风险**: 数据库层唯一性零强制；上列 5 类并发场景直接产生脏数据，部分场景（shareToken 多行命中）有安全影响。
- **建议**: 为 8 个 `<unique-key>` 补 `constraint="UK_..."` 属性，重新生成三方言 DDL，并为存量库补 `ALTER TABLE ... ADD CONSTRAINT ... UNIQUE` 迁移脚本（参照 `nop-auth/deploy/sql/mysql/_add_ext_login_unique.sql` 先例）。**修复前必须先裁定 dashboardName/screenName 的唯一作用域**（当前未生效，物化后会立刻变成"全系统看板名不可重名"的用户可见行为变更）。
- **信心水平**: 确定（orm.xml 属性、三方言 DDL 全文检索、ddl.xlib 生成条件、nop-auth 正例、nop-job 同病反例五重证据已独立复核）
- **误报排除**: 已确认平台无 auto-DDL 机制（deploy SQL 为唯一物化途径）；`_app.orm.xml` 生成产物中 UK 元数据保留，排除模型解析丢失。

---

# P1 发现（实质性缺陷/契约漂移，必须修复）

### [P1-01] 5 个子实体 CRUD query 绑定 `admin,user` 且无 RLS — 他人未发布看板的完整内容与数据集绑定泄露

- **优先级依据**: 跨用户私有内容（草稿）读取泄露，绕过设计声明的"owner + 已发布可见"语义
- **文件**: `nop-datav.action-auth.xml:73,80,87,194,201`；`nop-datav.data-auth.xml`（无对应 obj）
- **证据片段**:
  ```xml
  <resource id="FNPT:NopDatavDashboardSnapshot:query" roles="admin,user"/>  <!-- :73 -->
  <resource id="FNPT:NopDatavDashboardTab:query" roles="admin,user"/>       <!-- :80 -->
  <resource id="FNPT:NopDatavDatasetRef:query" roles="admin,user"/>         <!-- :87 -->
  <resource id="FNPT:NopDatavScreenWidget:query" roles="admin,user"/>       <!-- :194 -->
  <resource id="FNPT:NopDatavScreenSnapshot:query" roles="admin,user"/>     <!-- :201 -->
  ```
- **现状/风险**: 父实体 Dashboard/Screen 有 RLS（user 仅见自建+已发布），但子行全量可读：他人**草稿**看板的 tabConfig、DatasetRef（refDatasetId/paramMapping）、widgetConfig、snapshotContent（看板全量序列化，含 panelConfig 与 datasetRefs）可被任意 user 读取。mutation 均 admin-only，无写越权。
- **建议**: 五个只读子实体 query 收敛为 `admin`（镜像 Share/ExportTask 的 D1 裁定先例），或补 `dashboardId/screenId → 父表` 间接 RLS。
- **信心水平**: 确定（5 处行号已独立复核；mutation admin-only 已确认）
- **误报排除**: 已排除 xmeta graphql:filter/transFilter（生成层 0 命中）与 BizModel prepareQuery 覆写（0 命中）。

### [P1-02] NopDatavFilterState query+mutation 绑定 `admin,user`，通用 CRUD 绕过设计声明的 userName 隔离

- **优先级依据**: 跨用户读写删 + 与设计文档直接冲突（文档声明"仅查询/更新自己的记录"）
- **文件**: `nop-datav.action-auth.xml:94-95`；`NopDatavFilterStateBizModel.java`（103 行无 CRUD 覆写）；设计声明 `permission-sharing-design.md:69`
- **证据片段**:
  ```xml
  <resource id="FNPT:NopDatavFilterState:query" roles="admin,user"/>
  <resource id="FNPT:NopDatavFilterState:mutation" roles="admin,user"/>  <!-- 偏离设计 §51 mutation→admin -->
  ```
- **现状/风险**: userName 隔离仅在自定义 action（saveFilterState/getFilterState）实现；user 可经继承 `__findPage/__get/__save/__delete` 读写删他人 stateContent。低敏感个人偏好数据，但与设计契约冲突且 Share/ExportTask 同类场景已收敛。
- **建议**: 配 data-auth（`eq name="userName" value="${$context.userName}"`）或将 query/mutation 收敛 admin-only。
- **信心水平**: 确定（BizModel 全文已通读确认无覆写；实体有 userName 列可用）
- **误报排除**: 非误报——设计文档明文 userName 隔离，继承 CRUD 无任何隔离实现。

### [P1-03] nop-report 数据集级权限在 datav 全部查询路径未被消费；ChatBI 可枚举并执行任意数据集 SQL

- **优先级依据**: 数据集 ACL 契约未接线 + 注释不实陈述（"owner RLS 由 DAO 层处理"，实际 nop-report data-auth 为空）
- **文件**: `chatbi/DatavQueryDatasetExecutor.java:86-108`；`chatbi/DatavListDatasetsExecutor.java:94-116`；`query/PanelDataBinder.java:133-179`
- **证据片段**:
  ```java
  // DatavListDatasetsExecutor.java:97-98 —— 注释为不实陈述
  // owner RLS 由 DAO 层/查询上下文处理。
  QueryBean query = new QueryBean();
  query.addFilter(FilterBeans.eq(NopReportDataset.PROP_NAME_status, STATUS_ACTIVE));
  ```
  nop-report 定义了 `NopReportDatasetAuth` 数据集权限实体但 nop-datav 不消费；`chatToQuery` 权限为 admin,user。
- **现状/风险**: 任何 user 经 ChatBI `datav-list-datasets` 枚举全部数据集（sid+名称），再经 `datav-query-dataset` 直接执行任意数据集 SQL — 若数据集覆盖敏感表即未授权读取；面板查询路径同样以数据集 SQL 全量 DB 权限执行。
- **建议**: ChatBI 两个执行器接入数据集可见性校验（NopReportDatasetAuth 或 createdBy/admin 过滤）；修正不实注释。
- **信心水平**: 绕过事实确定；实际风险取决于部署中数据集敏感度（中）
- **误报排除**: 已排除 SQL 注入面（PanelSqlBuilder 全参数 `?` 绑定，见维度13误报排除清单）— 问题在数据集选择权而非注入。

### [P1-04] ChatBI `datav-query-dataset` 工具 maxRows 由 LLM 入参控制且无上限钳制 — 防 OOM 配置可被绕过

- **优先级依据**: 安全上限文档化但未在服务端强制执行；LLM 可被提示注入诱导传 0/负数/巨值
- **文件**: `chatbi/DatavQueryDatasetExecutor.java:104-108,158-159`；`datav-query-dataset.tool.xml`（schemaJson 无 maximum）
- **证据片段**:
  ```java
  Integer maxRows = input.get("maxRows") instanceof Number
          ? ((Number) input.get("maxRows")).intValue()
          : CFG_DATAV_CHATBI_MAX_ROWS.get();       // 配置只是缺省值而非上限
  // ...
  maxRows != null && maxRows > 0 ? LongRangeBean.longRange(0, maxRows.longValue()) : null,  // <=0 → 完全不限行
  ```
- **现状/风险**: 配置 `nop.datav.chatbi.max-rows`（默认 1000，描述"跨方言防 OOM"）可被 LLM 传参覆盖；传 0/负数时 range=null 全表物化进内存（`extractRows` 全行 List 化）→ OOM / 上下文 token 爆炸。对照：导出/告警路径的 max-rows 均为服务端硬上限，唯独 ChatBI 路径不对称。
- **建议**: `maxRows` 钳制到 `[1, CFG_DATAV_CHATBI_MAX_ROWS]`，`<=0`/null 落配置缺省；tool.xml schemaJson 补 `maximum`。
- **信心水平**: 确定（代码已独立复核）
- **误报排除**: 已排除 tool schema 层已约束（schemaJson 无 maximum）；已排除 LongRangeBean 内部有独立钳制。

### [P1-05] chatToDashboard/chatToScreen 为 @BizMutation — 完整 AI tool-calling 循环（多次远程 LLM 调用）运行在数据库事务内

- **优先级依据**: 长事务 + 远程调用在事务内（审计口径明确 P1）；并发下连接池耗尽放大为全局 DoS
- **文件**: `NopDatavChatBiBizModel.java:208-224,266-282`；平台机制 `nop-biz/.../TransactionActionDecoratorCollector.java:43-51`（mutation 默认 transactional=REQUIRED）
- **证据片段**:
  ```java
  @BizMutation                                     // ← 默认事务化
  @Auth(permissions = "NopDatavChatBi:chatToDashboard")
  public ChatBiResult chatToDashboard(...) {
      ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);
      return loop.run(description, ..., maxIterations, DASHBOARD_RESULT_HANDLER);  // 循环内 chatService.call × N 次远程调用
  }
  ```
- **现状/风险**: 单轮 LLM 调用可达数十秒 × maxIterations 全部在 REQUIRED 事务内；首轮工具写库后连接持续占用到循环结束。并发 chatToDashboard 请求可占尽连接池。
- **建议**: AI 循环移出事务（先跑循环取 spec，实体落库收敛到独立短事务），或标注 propagation=SUPPORTS。
- **信心水平**: 确定（@BizMutation 注解已独立复核；mutation→事务的平台机制已核实）
- **误报排除**: 已排除 chatToQuery 同样在事务内（它是 @BizQuery 无事务）；已排除 AI 循环在 worker 线程（`toolManager.callTool(...).join()` 阻塞调用线程）。

### [P1-06] evaluateAlertNow @BizMutation 在事务内做面板 SQL 查询 + 同步 SMTP/IM 远程发送

- **优先级依据**: 与已修复的 ReportDeliveryExecutor SMTP-in-session 同类缺陷在告警手动触发路径复现
- **文件**: `NopDatavAlertRuleBizModel.java:149-157`；`alert/AlertEvaluator.java:100-158,239-243`
- **证据片段**:
  ```java
  @BizMutation
  @Auth(permissions = "NopDatavAlertRule:evaluateAlertNow")
  public NopDatavAlertState evaluateAlertNow(...) {
      alertEvaluator.evaluate(alertRuleId);   // 内部：面板 SQL 查询 + sendAlertNotification（同步 SMTP，典型 30-60s 超时）
  ```
- **现状/风险**: 手动触发路径（user 可调）整链运行于 mutation 事务：取数 + 同步 SMTP + 多次状态写。cron 路径不受影响（BeanMethodJobInvoker 纯反射无事务装饰，已核实）。
- **建议**: 改 @BizQuery 或通知发送移到 `txn().afterCommit`/独立异步（对齐 ReportDeliveryExecutor Part A/B 拆分模式）。
- **信心水平**: 确定
- **误报排除**: 已排除 cron 主路径同样中招（反射 invoker 无事务包装）。

### [P1-07] NopDatavErrors 全部 80+ 错误码描述为英文 — 违反平台"ErrorCode 描述用中文"的文档化契约

- **优先级依据**: 文档化契约漂移（error-handling.md:119 规定中文，英文白名单仅 nop-ai-* 六个模块，nop-datav 不在其中）；zh-CN 用户收到英文错误
- **文件**: `NopDatavErrors.java:66-686`；`docs-for-ai/02-core-guides/error-handling.md:119-121`
- **证据片段**:
  ```java
  ErrorCode ERR_DATAV_DASHBOARD_NOT_FOUND = define(
          "nop.err.datav.dashboard-not-found",
          "Dashboard not found: {dashboardId}",      // 英文描述
          ARG_DASHBOARD_ID
  );
  ```
- **现状/风险**: 错误码命名/define/ARG 用法全部规范，但描述语言与平台惯例割裂（nop-auth/nop-wf 均中文）；无 i18n 键，后续本地化需补 80+ 键。
- **建议**: 按平台惯例改中文描述并补 i18n en 键；或团队裁定英文路线后在 error-handling.md 白名单显式登记 nop-datav（消除文档-代码漂移）。
- **信心水平**: 确定（规则行号与白名单内容已独立复核）
- **误报排除**: 已核对 nop-auth 实际代码为中文描述，非仅文档规定。

### [P1-08] 22 个 ORM index 均未在物理库创建，外键列全部无索引

- **优先级依据**: 模型承诺→物理缺失的契约漂移；只增表查询/级联删除随数据量线性劣化
- **文件**: `nop-datav/model/nop-datav.orm.xml`（22 处 `<index>`）；`deploy/sql/*/ _create_nop-datav.sql`（0 个 CREATE INDEX）
- **证据片段**: ddl.xlib CreateTable 路径从不输出索引（AddIndex 仅差量迁移调用），且模块未提供任何手工索引迁移脚本。
- **现状/风险**: dashboardId/screenId/panelId/reportTaskId/sessionId/alertRuleId 等 FK 列全无索引 — 级联删除、ChatBi loadMessages、告警按 panelId 扫规则等全表扫描。
- **建议**: 补 `_add_index_nop-datav.sql` 迁移脚本物化 22 个索引（与 P0-03 修复一并处理，UK 可覆盖部分 FK 场景）。
- **信心水平**: 确定（DDL 全文检索已独立复核）
- **误报排除**: 这是平台生成器普遍行为（nop-wf 同样），但 nop-datav 声明了 22 个索引形成模型-物理漂移，按模块缺陷记录。

### [P1-09] 级联删除覆盖不齐：ReportTask/AlertRule 删后子表孤儿；ChatSession 标准 CRUD 不级联消息（与自定义路径语义不一致）；Tab 删除致 Panel.tabId 悬挂

- **优先级依据**: 数据一致性缺陷（孤儿行、悬挂引用），四处已对照正确先例（Dashboard/Screen 级联）确认遗漏
- **文件**: `NopDatavReportTaskBizModel.java:88-96`（doDeleteEntity 仅注销 cron）；`NopDatavAlertRuleBizModel.java:98-107`（同）；`NopDatavChatSessionBizModel.java`（15 行裸 CRUD，无覆写 — 对照 `ChatBiSessionManager.deleteSession` 有级联）；`NopDatavDashboardTabBizModel.java`（裸 CRUD）
- **证据片段**:
  ```java
  // ReportTaskBizModel.doDeleteEntity：只注销 cron，不删 ReportDelivery
  super.doDeleteEntity(entity, refNamesToCheck, prepareDelete, context);
  if (reportScheduler != null) { reportScheduler.unregisterTask(entity.getReportTaskId()); }
  ```
- **现状/风险**: ① 删 ReportTask → ReportDelivery 孤儿；② 删 AlertRule → AlertState 孤儿（与 P0-03 叠加，状态分裂）；③ `NopDatavChatSession__delete`（admin 标准 CRUD）产生孤儿 ChatMessage，仅自定义 deleteChatSession 级联 — 双删除路径语义不一致；④ Tab 标准 delete → Panel.tabId 悬挂。
- **建议**: 四处补 doDeleteEntity 级联/解绑（与 Dashboard/Screen 同模式）；或 xbiz 层裁剪 ChatSession/Tab 的标准 mutation。
- **信心水平**: 确定（4 个 BizModel 全文已独立复核）
- **误报排除**: Dashboard 删时 AlertRule 仅停用保留为已有 plan 裁定，不重复报告；无双级联风险（ORM 侧无 cascade 配置）。

### [P1-10] 快照表/告警状态表薄 BizModel 暴露全量 CRUD mutation（admin），绕过领域唯一写入点

- **优先级依据**: API 表面积与领域不变量错位：快照 append-only、告警状态机无 API 层强制，且伪造快照可污染匿名公开访问路径（getSharedDashboard 消费 snapshotContent）
- **文件**: `NopDatavDashboardSnapshotBizModel.java:10-16`、`NopDatavScreenSnapshotBizModel.java:10-15`、`NopDatavAlertStateBizModel.java:10-15`；`action-auth.xml:74,202,272`（mutation=admin）
- **证据片段**:
  ```java
  @BizModel("NopDatavDashboardSnapshot")
  public class NopDatavDashboardSnapshotBizModel extends CrudBizModel<NopDatavDashboardSnapshot>
          implements INopDatavDashboardSnapshotBiz {
      public NopDatavDashboardSnapshotBizModel() { setEntityName(NopDatavDashboardSnapshot.class.getName()); }
  }   // save/update/delete/batchDelete 全量暴露；手写 xbiz 定制层为空壳未裁剪
  ```
- **现状/风险**: `NopDatavDashboardSnapshot__save` 可伪造快照行、`__update` 可篡改发布历史（publishedBy/Time 审计链可覆写）；AlertState `__update` 可越过状态机。这些数据 feeds `getSharedDashboard`（公共匿名访问）与 `rollbackDashboard`。
- **建议**: 手写 xbiz 层删除快照表/AlertState 的 mutation action，或 xmeta 禁写。
- **信心水平**: 确定（薄 BizModel 与空壳 xbiz 已核实）
- **误报排除**: 与 P0-01 不同维度：即使授权正确（admin-only），表面积本身与领域写入点冲突；admin 后门会绕过 publish 单写点语义。

### [P1-11] IM 渠道部分成功聚合分支（anySent）无任何测试覆盖 — 改错实现测试仍通过

- **优先级依据**: 测试无保护力（mutate 验证：anySent→allSent 语义改动后全部现有测试仍通过，但生产行为改变）
- **文件**: `report/NotificationSender.java:364-382`；mock 能力 `MockChannelMessageService.java:65-67`（`setNoBindingUsers` 全仓零调用，已独立 grep 复核）
- **证据片段**:
  ```java
  boolean anySent = false;
  for (String userId : userIds) {
      SendResult result = channelMessageService.sendToUser(userId, msg);
      if (result == SendResult.SENT) { anySent = true; }
  ```
  现有测试仅覆盖全 SENT 与全 NO_BINDING 两端，"u1 NO_BINDING + u2 SENT" 中间分支缺失。
- **现状/风险**: 部分成功被误判为全失败时已成功投递被回滚标记 — 无测试拦截。
- **建议**: 新增测试 `setNoBindingUsers(Set.of("u1"))` + recipients `["u1","u2"]` → 断言 deliveredChannels 含 im。mock 能力已备，成本极低。
- **信心水平**: 确定（grep 零调用已复核）
- **误报排除**: 非 P-3 只测 happy path（两端有覆盖），是特定中间分支缺口。

### [P1-12] PanelSqlBuilder 零直接单测：错误分支与注入回归无覆盖

- **优先级依据**: 安全关键纯静态函数（防 SQL 注入的唯一屏障）无注入回归测试——任何重构（如改字符串拼接）只有碰巧走到 `${region}` 查询的集成测试会拦截
- **文件**: `query/PanelSqlBuilder.java:42-62`；测试侧仅 `TestDatavQueryDatasetExecutor.java:25` 注释提及
- **证据片段**:
  ```java
  if (dsText == null || dsText.isEmpty()) {
      throw new NopException(ERR_DATAV_QUERY_FAILED).param("panelId", panelId);   // 分支A：零覆盖
  }
  String paramName = matcher.group(1);
  if (!params.containsKey(paramName)) {
      throw new NopException(ERR_DATAV_QUERY_FAILED).param("panelId", panelId);   // 分支B：零覆盖
  }
  ```
- **现状/风险**: 空 SQL、未声明占位符两分支零覆盖；无恶意值（`north' OR '1'='1`）绑定回归断言。
- **建议**: 补纯 JUnit 单测（无依赖，~30 行）：两错误分支 + 多占位符顺序绑定 + 恶意参数值进 `getParams()` 而不出现在 SQL 文本。
- **信心水平**: 确定
- **误报排除**: 未评 P0：参数化绑定主干被 3 个集成测试间接锚定（`${region}` 查询成功即证明值走绑定）。

---

# P2 发现（记录在案，不单独驱动修复计划）

### 结构与构建（维度 01/02/05）

- **[P2-01] nop-datav-core 孤儿目录**（271 行 dict 常量，无 pom、不在 `<modules>`、全仓零引用，但 codegen 每次构建持续再生）。根因 `model/nop-datav.orm.xml:6` `ext:useCoreModule="true"` 与构建拓扑不一致。建议改 `false` 删目录，或补 pom 接线（对照 nop-job-core/nop-dyn 先例）。文件: `nop-datav/nop-datav-core/`；判定: 三重事实（无 pom/不在 modules/零引用）已独立复核。
- **[P2-02] dao 对空 api 模块的 compile 依赖空转**（nop-datav-api 仅 pom；gen-crud-api.xgen 显式禁用是记录在案的决策）。建议移除依赖或加说明。文件: `nop-datav-dao/pom.xml:26-29`。
- **[P2-03] 11 个手写 DTO 与 17 个 I*Biz 混放 dao 的 `io.nop.datav.biz` 包**（有设计文档记录，偏离平台 dto 包惯例）。建议排期迁 `dao.dto`。文件: `nop-datav-dao/src/main/java/io/nop/datav/biz/`。
- **[P2-04] dict 值常量四份平行定义**（BizModel 内嵌 publish-status、Exporter 内嵌 export-format、report 包常量类、孤儿 core 全集）。建议以 NopDatavReportTaskStatus 模式收敛单一来源。文件: `NopDatavDashboardBizModel.java:85-91` 等。
- **[P2-05] model-design.md 声称与"既有 nop-datav-chart 共存于同一父 pom"**——该模块仓库不存在。文件: `ai-dev/design/nop-datav/model-design.md:78-80`。

### 权限/安全加固（非阻塞残余）

- **[P2-06] 外部跳转 URL 无 scheme 白名单、模板值未编码**（javascript: 面 + 参数注入；与 P0-01 组合时升级）。文件: `linkage/LinkageExecutor.java:122-126,184-193`。
- **[P2-07] 分享限流单节点 LocalCache + 容量驱逐可冲掉锁定 + `nop-client-addr` 可伪造**（设计文档 R1/R2 已声明为接受残余/部署边界）。文件: `share/NopDatavShareAccessGuard.java:31-34`、`NopDatavDashboardShareBizModel.java:239-242`。
- **[P2-08] CSV 导出无公式注入防护**（`=cmd|`/`=HYPERLINK` 形式数据经导出扩散）。文件: `export/PanelDataExporter.java:195-221`。
- **[P2-09] docs-for-ai 全目录零收录 nop-datav**（module-groups/INDEX/where-things-live/source-anchors 均 0 命中，已独立 grep 复核）— 17 实体、48 action 的模块无 AI 路由锚点。建议按 Mandatory Updates 补录。文件: `docs-for-ai/01-repo-map/module-groups.md`。

### ORM 细项（维度 04）

- **[P2-10] 三个外键列无 relation 定义**（Panel.tabId、Panel.datasetRefId、ScreenWidget.datasetRefId — 后者跨聚合，Dashboard 删 DatasetRef 时悬挂）。文件: `model/nop-datav.orm.xml:201-205,727-729`。
- **[P2-11] AlertState 冗余索引 + UK 命名前缀不一致**（UQ_ vs 其余 7 个 UK_；物化后物理冗余）。文件: `orm.xml:1183-1193`。
- **[P2-12] 快照内容列 mandatory 不一致**（DashboardSnapshot 可空 vs ScreenSnapshot 非空，三方言一致）。文件: `orm.xml:387-389` vs `798-800`。
- **[P2-13] 17 实体全量声明 delFlag 列但均未接线 deleteFlagProp**（物理删除策略下列+Java 置 0 样板形同虚设）。文件: `orm.xml` 全部 entity 头部。
- **[P2-14] dashboardName/screenName 全局唯一键作用域待裁定**（修复 P0-03 前置决策：全表唯一 vs (tenant,createdBy,name)；当前未生效暂无线上影响）。文件: `orm.xml:171-174,695-698`。

### 错误处理/类型卫生（维度 09/15）

- **[P2-15] existsTable 辅助方法 catch(Exception)→return false 静默吞异常 ×4**（调度器初始化可被 DB 瞬时故障静默跳过，INFO 日志误导）。文件: `alert/NopDatavAlertScheduler.java:143-152` 等 4 处。
- **[P2-16] 容错解析路径丢异常不留证 ×8**（5 处零日志 + 2 处仅 DEBUG；AlertEvaluator.parseParams 非法 JSON 降级为无参评估有错误触发风险）。文件: `NopDatavChatBiBizModel.java:248-250` 等。
- **[P2-17] codec/parser rethrow 丢失异常链 ×7**（无 `.cause(e)`；同模块存在大量正确先例）。文件: `DashboardFilterUrlCodec.java:99-115` 等。
- **[P2-18] 4 个 ErrorCode 死码 + 2 处 javadoc 错误码漂移 + 1 处 ARG 死声明**。文件: `NopDatavErrors.java:185-189` 等。
- **[P2-19] getDashboardData 链 4 处 IllegalStateException 防御性包装**（checked 异常路径现实不可达，cause 保留）。文件: `NopDatavDashboardBizModel.java:597,620,632,655`。
- **[P2-20] PanelSqlBuilder 占位符缺参错误不带 paramName 且错误码语义错配**。文件: `PanelSqlBuilder.java:52-55`。
- **[P2-21] 导出任务非 NopException 失败无堆栈日志**（仅 NopException 分支记日志）。文件: `NopDatavExportTaskBizModel.java:276-291`。
- **[P2-22] findAllByQuery 系统性冗余强转 + @SuppressWarnings ×11 + 游荡 suppression ×4**（API 已返回 List<T>）。文件: `NopDatavAlertScheduler.java:126-127` 等。

### IoC/API 层（维度 08/12）

- **[P2-23] app-service.beans.xml 使用 `ioc:` 属性但未声明 xmlns:ioc**（20 处使用 0 声明；XNodeParser 按裸名匹配故运行时无影响，偏离仓库惯例）。文件: `app-service.beans.xml:2-13`。
- **[P2-24] 2 个 bean 缺 ioc:default + 4 个通用名 bean 无模块前缀**（宿主定制场景冲突面）。文件: `app-service.beans.xml:21,28,45-46,110-111`。
- **[P2-25] refreshPanel 纯读操作标记 @BizMutation**（审计噪音 + 语义错位，模块内唯一错位项）。文件: `NopDatavPanelBizModel.java:107-118`。
- **[P2-26] 5 个自定义列表 action 无分页无上限**（ReportDelivery/ChatSession 列表为持续增长表）。文件: `NopDatavReportTaskBizModel.java:150-153` 等。
- **[P2-27] publish/rollback 主表 JDBC 直更不递增乐观锁 VERSION**（与 setScreenThumbnail 的 VERSION+1 自相矛盾；并发编辑+发布交错可静默覆盖发布状态）。文件: `NopDatavDashboardBizModel.java:971-990`、`NopDatavScreenBizModel.java:435-456` vs `:295-300`。
- **[P2-28] 交互式面板查询路径无行数上限**（导出/告警/ChatBI 三路径均有 max-rows，交互路径无界 — javadoc 明示为设计决定，防护不对称）。文件: `query/PanelDataBinder.java:74-86,174-180`。

### BizModel/契约细项（维度 03/07）

- **[P2-29] 9 个 dao 层 GraphQL 返回 DTO 缺 @DataBean**（与 ChatBiResult 规范不一致；白名单/序列化收紧机制若启用会拦截）。文件: `io.nop.datav.biz/DashboardDataResult.java` 等 9 个。
- **[P2-30] saveDashboardLayout 等弱类型 Map 契约**（5 返回 + 8 参数；动态键值类合理，layout 入参可 DTO 化）。文件: `NopDatavDashboardBizModel.java:294-703`。
- **[P2-31] rollbackDashboard 契约表漂移**（实现恢复 paramConfig，model-design.md:39 漏列；子对象不回滚未明示）。文件: `NopDatavDashboardBizModel.java:941-958`。
- **[P2-32] parseFilterFromUrl 丢弃 requireEntity 返回值二次加载**（二次 getEntityById 无 null 防御，孤例笔误）。文件: `NopDatavDashboardBizModel.java:305-317`。
- **[P2-33] ExportTask 实体直接 new 而非 dao.newEntity()**（绕过实体工厂钩子，模块内两种方式并存）。文件: `NopDatavExportTaskBizModel.java:446`。
- **[P2-34] 3 个大 BizModel 全限定注入注解/字段类型**（风格噪音）。文件: `NopDatavDashboardBizModel.java:93-103` 等。
- **[P2-35] chatToDashboard/chatToScreen 错误 param 名 question 承载 description**（复制粘贴未改名）。文件: `NopDatavChatBiBizModel.java:210-214,268-272`。
- **[P2-36] triggerReportNow 返回裸 String 无 javadoc；getAlertState 可返回 null 未标注**。文件: `NopDatavReportTaskBizModel.java:133-140`、`NopDatavAlertRuleBizModel.java:159-169`。
- **[P2-37] 6 个调度类 action 零测试零消费者**（enable/disable×4、getAlertState、getReportDeliveryHistory — 有设计记录的"待接线"，但 owner 校验与注册时序无保护）。文件: `NopDatavAlertRuleBizModel.java:121-169` 等。
- **[P2-38] NotificationSender 直接 import nop-integration-api 5 类型但 pom 未显式声明**（经 nop-file-dao 传递，契约卫生）。文件: `report/NotificationSender.java:12-16`。
- **[P2-39] dashboardType 列 + dict 零消费零文档**（死配置位）。文件: `orm.xml:10-13,134-136`。

### 测试细项（维度 16/21）

- **[P2-40] 4 个调度注册测试静默跳过模式**（`if==null return` — 环境退化即 no-op；同仓有 assertNotNull 正确模式）。文件: `TestNopDatavAlertE2E.java:762-782` 等。
- **[P2-41] 5 处 E2E 错误路径断言未锚定错误码**（只断言类型+message contains）。文件: `TestNopDatavAlertE2E.java:243-246` 等。
- **[P2-42] 报告邮件附件内容零验证**（断言止步 size==1，不读字节）。文件: `TestNopDatavReportE2E.java:171-172`。
- **[P2-43] TestPanelComponentRegistry 元数据镜像断言**（命中 P-2/P-4 轻度：assertEquals(14)、映射表逐行镜像）。文件: `TestPanelComponentRegistry.java:63-111`。
- **[P2-44] TestChatBiToolCallingLoop 纯逻辑测试继承重量级集成基类**（每方法全量建表，违反 testing.md 基类选择规则）。文件: `TestChatBiToolCallingLoop.java:47`。

### 文档漂移（维度 18/20）

- **[P2-45] permission-sharing-design.md 错误码漂移**（承诺 ERR_DATAV_PANEL_NOT_FOUND，实际 ERR_DATAV_EXPORT_NO_EXPORTABLE_PANELS）。文件: `permission-sharing-design.md:375`。
- **[P2-46] permission-sharing-design.md 重启清理机制漂移**（文档说 BizModel 实现 IInitializer，实际为独立 Recovery bean）。文件: `permission-sharing-design.md:337`。
- **[P2-47] permission-sharing-design.md 测试配置路径漂移**（/nop/datav/auth/* vs 实际 /test/datav/auth/*）。文件: `permission-sharing-design.md:267-269`。
- **[P2-48] 设计目录 README 状态表全量 stale**（7 份文档全标 stub，实际全 final）。文件: `ai-dev/design/nop-datav/README.md:5-13`。

---

# 攻击面与防护矩阵（安全维度汇总）

| # | 攻击面 | 现有防护 | 结论 |
|---|--------|----------|------|
| 1 | SQL 模板执行（PanelSqlBuilder） | 全参数 `?` 绑定、无标识符拼接、无未命中透传回退 | ✅ 闭合（注入面） |
| 2 | 数据集选择权（谁能让哪条 SQL 跑） | Panel 入口无 RLS（P0-01/02）；dataset ACL 未消费（P1-03） | ❌ P0/P1 缺口 |
| 3 | Panel 标准 CRUD 写删 | mutation=admin,user + 无 RLS | ❌ P0-01 |
| 4 | 子资源只读 CRUD | query=admin,user + 无 RLS | ❌ P1-01 |
| 5 | 公开分享（唯一匿名口） | UUID token + BCrypt + 两级限流/锁定 + 五重校验 + 快照只读 | ✅ 闭合（单节点残余 P2-07） |
| 6 | passwordHash 泄漏 | xmeta published=false + mask-on-return + admin-only CRUD 三层闭环 | ✅ 闭合（有测试） |
| 7 | 导出/下载 | requireOwner + safeFileName + LIMIT 双保险 + 流关闭 | ✅ 闭合（CSV 公式注入 P2-08） |
| 8 | 跳转 URL | 无 scheme 白名单/无编码 | ⚠️ P2-06（与 #3 组合升级） |
| 9 | AI 工具循环 | maxIterations 上限；但 mutation 事务包裹（P1-05）+ maxRows 无钳制（P1-04） | ⚠️ P1 |
| 10 | 定时调度/恢复 | per-item try/catch + FAILED-brick 契约 + stuck 时间阈值 | ✅ 闭合 |
| 11 | 并行查询/缓存 | 每任务新 context+新 session；仅成功入缓存；三上界 | ✅ 闭合 |
| 12 | 数据完整性（唯一约束） | ORM 声明 8 UK 但 DDL 零物化 | ❌ P0-03 |

# 总评

nop-datav 的**工程纪律显著高于一般水平**：生成管线六维度零漂移（模型→ORM→实体→xmeta→xbiz→页面→i18n→DDL 全链闭合、39 commit 无手改生成物痕迹）、依赖图无环且跨模块依赖全部收敛在接口层、I*Biz 41 方法签名 100% 对齐（编译级验证）、@Inject 零违规、throw 语句 96.6% 为 NopException 且 99.4% 带参数、测试错误路径覆盖文化强（无 P-1/P-6/P-7 反模式）。

真正的风险集中在两个系统性主题：

1. **权限收口不闭环**（P0-01/02、P1-01/02/03/10 同根因）：设计文档 §49/§51 对 Share/ExportTask 识别并修复了"无 RLS 实体放开 CRUD 即水平越权"的模式，但对 Panel、FilterState、5 个子实体、快照表未落实 — action-auth 角色绑定与 data-auth 行级规则之间存在系统性缺口，单面板查询路径的权限锚点选错实体。
2. **模型声明的数据契约未物化**（P0-03、P1-08、P2-10~14）：ORM 模型作为唯一事实源声明了 8 个唯一键、22 个索引，但物理库零物化 — 并发兜底路径（upsert、版本号、1:1 初始化）全部裸奔。

# 优先修复建议

1. **立即**（P0）: P0-01 + P0-02 一次修复闭合（Panel mutation 收敛 admin + 四个数据查询 action 补 dashboard 归属校验，仓库内有 requireSourceAccess 正确先例）；P0-03 需先裁定 P2-14 名字唯一键作用域，再补 constraint 属性 + 重建 DDL + 存量迁移。
2. **短期**（P1）: 权限组（P1-01/02/03/10 随 P0 模式批量收口）；事务组（P1-05/06 对齐 ReportDeliveryExecutor 拆分模式）；资源上界（P1-04 maxRows 钳制一行改动级）；测试（P1-11/12 低成本补写）；P1-07 错误码语言一次性机械替换；P1-08/09 与 P0-03 DDL 修复同批出脚本。
3. **排期**（P2）: 结构清理组（孤儿 core 目录 + 常量收敛一次清理）、文档同步组（设计文档 5 处漂移 + docs-for-ai 补录）、测试卫生组。

# 本次审核盲区自评

- 未实际运行 `./mvnw test`（采用静态证据 + 编译验证）；E2E 测试通过性以仓库当前状态为准
- 前端消费面（nop-datav-web pages 为生成物、flux 前端未接线）导致"死 API"判断只能以 test+design 双方交叉，可能低估接线计划中的 action
- 大屏（Screen）侧 flux 编辑器路径尚缺（观察项，非缺陷）
- 分享限流多节点部署形态、nop-report DatasetAuth 的运维启用状态无法从代码判定，P1-03/P2-07 的实际风险取决于部署
- 维度 22（工作流）未执行：模块无 xwf 文件，无适用对象

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
