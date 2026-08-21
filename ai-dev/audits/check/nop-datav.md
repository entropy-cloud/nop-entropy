# nop-datav 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-datav（service/dao）
- 文件数: 130（src/main/java，非生成物；与任务描述的 231 不符，以实际仓库为准：service 82 + dao 46 + app 1 + core 1）
- 覆盖范围声明:
  - 全量 grep 扫描 130 个文件：空 catch、bare RuntimeException、printStackTrace、synchronized、@Inject private、SQL/字符串拼接、addOrderField 排序方向（逐一对照平台 `QueryBean.addOrderField(name, desc)` 语义验证）。
  - 深读（全文）service 层核心链路约 35 文件：取数链路（PanelSqlBuilder/PanelDataBinder/PanelParamEvaluator/DashboardPanelQueryCache）、看板（NopDatavDashboardBizModel 全文）、分享（NopDatavShareAccessGuard/NopDatavDashboardShareBizModel/NopDatavDashboardOwnerGuard）、导出（PanelDataExporter/NopDatavExportTaskBizModel/NopDatavExportTaskRecovery/NopDatavStuckTaskScanner）、告警（AlertEvaluator/AlertAggregator/AlertThresholdComparator/NopDatavAlertScheduler/NopDatavAlertRuleBizModel）、报告（ReportDeliveryExecutor/NopDatavReportScheduler 节选/NotificationSender 关键段）、ChatBI（ChatBiToolCallingLoop/ChatBiSessionManager/DatavQueryDatasetExecutor/DatavGenerateDashboardExecutor/ChatBiDatasetVisibility/NopDatavChatBiBizModel）、布局/筛选/联动/大屏（DashboardLayoutCodec/DashboardParamParser/DashboardFilterResolver/DashboardFilterUrlCodec/DashboardFilterDefExporter/LinkageExecutor/LinkageConfigParser/FilterStateCodec/ScreenLayoutParser/ScreenThemeParser）、组件注册表、NopDatavConfigs。
  - 配置核查：app-service.beans.xml（bean 显式装配）、app-dao.beans.xml、nop-datav.action-auth.xml / nop-datav.data-auth.xml（权限矩阵）。
  - 抽查 dao 层：实体类为空壳继承 `_gen` 生成类的手写扩展（无手写逻辑）、PanelDataResult 等数据类。
  - 未逐行细读：dao 层 INopDatav*Biz 接口声明、剩余纯数据类（getter/setter）、NopDatavApplication/NopDatavCoreConstants、ToolSchemaConverterInline/ChatBiSystemPrompt/ChatBiTypeConverter/ToolResultHandler 等纯转换类（经 grep 扫描无命中模式）。
  - 关键排除验证：`JdbcTemplateImpl.executeQuery` 确认对 SQL 文本不做宏展开/不加租户过滤（缓存键完整性结论依据）；`OrderFieldBean.forField(name, desc)` 确认 desc=true 为降序（排序类发现依据）；`rollbackDashboard` 只恢复主表字段与 `ai-dev/design/nop-datav/model-design.md:39` 裁定一致（非 bug，未列入发现）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 1 |
| P2 | 4 |
| P3 | 2 |

## 发现列表

### [P1] cancelExportTask 与 worker RUNNING 写入竞态，任务可永久卡在 RUNNING

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java:296-367`（配合 `:221-234`）
- **维度**: D3（并发与线程安全）
- **证据**:
```java
// worker（executeTask）:
if (Boolean.TRUE.equals(cancelFlags.get(taskId))) {   // L296 检查点
    markCancelled(dao, task, operator);
    return null;
}
task.setStatus(NopDatavExportTaskStatus.RUNNING);     // L300-302 写 RUNNING
dao.updateEntityDirectly(task);

// cancel 线程（cancelExportTask）:
cancelFlags.put(taskId, Boolean.TRUE);                // L227
task.setStatus(NopDatavExportTaskStatus.CANCELLED);   // L229
dao.updateEntityDirectly(task);                       // L232 写 CANCELLED

// worker 的 catch 分支（L352-355）:
if (Boolean.TRUE.equals(cancelFlags.get(taskId))) {
    LOG_EXPORT_FAILURE.info("nop.datav.export.cancelled-pre-success:taskId={}", taskId);
    // 不写任何状态——设计假设“cancel 线程已写 CANCELLED”
}
```
- **现状**: 时序竞态：worker 在 L296 检查 cancelFlags 之后、L302 写 RUNNING 落库之前，cancel 线程完成 put + CANCELLED 落库；随后 worker 的 RUNNING 覆盖 CANCELLED。之后 exporter 内首个 cancel 检查点抛出，catch 分支因 `cancelFlags=true` 刻意不写库（保留“cancel 线程已写的终态”），`finally` 移除标志——但终态实际已被 worker 的 RUNNING 覆盖，无人再写。
- **风险**: 任务行停在 RUNNING（非终态）：占用该用户的导出并发额度（`checkConcurrencyLimit` 按 PENDING+RUNNING 计数，默认 max=3），用户在额度内无法发起新导出；仅当 stuck 扫描器可用（宿主注册了 IJobScheduler 且 `nop.datav.stuck-scan.enabled=true`，默认 60min 阈值 + 10min 间隔）才在最长约 70 分钟后被标 FAILED（语义为 FAILED 而非 CANCELLED）；扫描器不可用时永久卡住，直至进程重启由 Recovery 清理。
- **建议**: worker 在 catch 分支 cancelFlags=true 时改为条件回写（重读行，若为非终态则写 CANCELLED，幂等）；或 worker 写 RUNNING 前用条件 UPDATE（`where status=PENDING`）并在 0 行更新时重新读取终态退出。
- **误报排除**: 逐一推演了三种时序（cancel 在 worker 读行前 / 在 RUNNING 写入后 / 在检查点与 RUNNING 写入之间），仅第三种窄窗口触发；前两种最终态正确。已确认 catch 分支“不写状态”的注释依赖“cancel 线程必然落库 CANCELLED”这一在覆盖竞态下不成立的假设。非误报。

### [P2] exportDashboard 面板排序方向与看板渲染/批量查询相反（desc/asc 误用）

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/export/PanelDataExporter.java:162`
- **维度**: D1/D8（正确性、契约一致性）
- **证据**:
```java
// PanelDataExporter.exportDashboard（L160-164）:
query.addFilter(io.nop.api.core.beans.FilterBeans.eq("dashboardId", dashboardId));
query.addOrderField("sortOrder", true);   // desc=true → 降序（sortOrder 大的在前）

// 对照 NopDatavDashboardBizModel.findRelatedEntities（L906-907）:
if (orderField != null) {
    query.addOrderField(orderField, false);  // 升序（sortOrder 小的在前）
}
```
- **现状**: 平台 `QueryBean.addOrderField(name, desc)` 第二参数 desc=true 为降序（已核实 `OrderFieldBean.forField`）。看板渲染（getDashboardData）、布局导出（exportDashboardLayout）、快照序列化均按 sortOrder 升序加载面板；唯独 `PanelDataExporter.exportDashboard` 用 `true`（降序）。
- **风险**: 导出的多 sheet xlsx（exportDashboard / 报告交付 / 看板级导出任务三条路径共用）sheet 顺序与看板 UI 显示顺序、getDashboardData 条目顺序相反，用户看到的第一屏数据对应看板最后一个面板；对依赖 sheet 顺序做对账的下游是数据错位。
- **建议**: 改为 `query.addOrderField("sortOrder", false)`，与 `findRelatedEntities(..., "sortOrder")` 的既有语义对齐。
- **误报排除**: 已核对模块内全部 16 处 `addOrderField` 调用：其余 sortOrder 均用 false（升序），snapshotVersion 取最新用 true（正确），仅此一处 sortOrder 用 true。无其他调用方对 exportDashboard 的面板顺序做再排序。

### [P2] listChatSessions 会话列表排序与两处契约注释相反（升序 vs 声明降序）

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/ChatBiSessionManager.java:98`（契约见 `NopDatavChatBiBizModel.java:337-345`）
- **维度**: D1/D8（正确性、契约一致性）
- **证据**:
```java
// ChatBiSessionManager.listSessions（L93-99），javadoc: “按 updateTime 降序”
public List<NopDatavChatSession> listSessions(String operator) {
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.eq("userName", operator));
    query.addOrderField("updateTime", false);   // desc=false → 升序（最旧在前）
    return sessionDao().findAllByQuery(query);
}

// NopDatavChatBiBizModel.listChatSessions 直接透传，无再排序（L342-345）
```
- **现状**: `ChatBiSessionManager.listSessions` 与 `NopDatavChatBiBizModel.listChatSessions` 两处 javadoc 均声明“按 updateTime 降序”（最近活跃的会话在前），实现是升序（最旧在前）。
- **风险**: 会话列表第一页是最久未动的旧会话，最近对话排末尾；会话数多时用户找不到刚用的会话（无分页参数时列表全量返回，用户可滚动看到，但顺序语义错误）。
- **建议**: 改为 `addOrderField("updateTime", true)`；或若有意升序，修正两处 javadoc——以前者为宜（会话列表惯例为最近优先）。
- **误报排除**: 已核实调用链（BizModel 直接返回 manager 结果，无前端二次排序依据）；同类查询 `loadMessages`（seq 升序，注释一致正确）与 `nextSeq`（seq 降序取最大，正确）已对照排除。

### [P2] 导出临时文件在取消/失败路径泄漏（仅成功路径删除）

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/export/PanelDataExporter.java:215-258`（配合 `NopDatavExportTaskBizModel.java:349-367`、`ReportDeliveryExecutor.java:279-291`）
- **维度**: D2（资源管理）
- **证据**:
```java
// writeCsv（L218-239）: 先创建临时资源，取消/IO 异常直接抛出，无 resource 清理
IResource resource = ResourceHelper.getTempResource("datav-export");
OutputStream os = null;
...
for (Map<String, Object> row : rows) {
    checkCancelled(cancelChecker);   // 取消 → 抛 NopException，已写的临时文件残留
    output.write(row);
}

// 调用方 executeTask 的 catch 分支（L349-364）只更新任务状态，无 resource 引用可清理
} catch (Exception e) {
    ...
    task.setStatus(NopDatavExportTaskStatus.FAILED);
    dao.updateEntityDirectly(task);
}
// 仅成功路径 saveExportFile 的 finally 删除临时资源（L409-415）
```
- **现状**: `ResourceHelper.getTempResource` 经虚拟文件系统创建实际文件（`getOutputStream`/`saveExcel` 时落盘）。删除只发生在成功路径 `saveExportFile` 的 finally；`writeCsv`/`writeXlsx` 内部抛出（用户取消、rowLimit 超限前的 IO 错误、saveExcel 失败）时 ExportFile 尚未返回，调用方无 resource 引用，文件残留。
- **风险**: 每次取消/失败的导出与报告交付泄漏一个临时文件（大导出可达百 MB 级），磁盘缓慢积累；`ResourceHelper` 的 temp 目录无模块级过期清理。
- **建议**: 在 `writeCsv`/`writeXlsx` 内部以 try/catch 包裹，异常时 `resource.delete()` 后再抛；或将 resource 创建与写出重构为先写内存/由调用方统一 finally 清理。
- **误报排除**: 已核对三条路径（面板导出、看板导出、报告交付）的调用方——`executeTask` 与 `runDeliveryInSession` 的 catch 分支均只写状态；确认无其他清理点。成功路径删除逻辑正常（不算泄漏）。

### [P2] DashboardFilterUrlCodec.decode 对非法百分号编码抛裸 IllegalArgumentException（用户可控输入直达 500）

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/filter/DashboardFilterUrlCodec.java:108-115`（入口 `NopDatavDashboardBizModel.parseFilterFromUrl:305-317`）
- **维度**: D4（错误处理）
- **证据**:
```java
private static String decode(String s) {
    try {
        return URLDecoder.decode(s, StandardCharsets.UTF8.name());
    } catch (UnsupportedEncodingException e) {   // 仅捕获受检异常
        throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)...
    }
}
```
- **现状**: `URLDecoder.decode("%zz", ...)` 抛的是 unchecked `IllegalArgumentException`，此方法只捕获 `UnsupportedEncodingException`（UTF-8 恒可用，实际不可达）。`parseFilterFromUrl` 是 `@BizQuery` 且 `url` 为用户可控参数，`x=%zz` 会让裸 `IllegalArgumentException` 穿透到 GraphQL 层。
- **风险**: 恶意/畸形 URL 使接口以非结构化 500 失败（无错误码、无参数上下文），违背模块错误处理两档策略（该类属公共 API 层，应 NopException + ErrorCode + .param）。
- **建议**: 增加 `catch (IllegalArgumentException e)` 分支转 `ERR_DATAV_INVALID_PARAM_CONFIG`（附 reason=非法 URL 编码）。
- **误报排除**: 已验证 JDK `URLDecoder.decode` 对 `%zz` 抛 `IllegalArgumentException`；调用链上（BizModel→codec）无其他捕获点。

### [P3] NopDatavReportScheduler 使用裸 IllegalArgumentException，与 AlertScheduler 的同类修复不对称

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/report/NopDatavReportScheduler.java:206-208`
- **维度**: D4/D7（错误处理、平台规范）
- **证据**:
```java
Object id = params != null ? params.get(PARAM_REPORT_TASK_ID) : null;
if (id == null) {
    throw new IllegalArgumentException("missing reportTaskId in job params");
}
// 对照 AlertScheduler（L201-204，Dim09-05 修复后）:
throw new NopException(ERR_DATAV_ALERT_RULE_NOT_FOUND)
        .param(ARG_ALERT_RULE_ID, "(absent from job params)");
```
- **现状**: 报告调度入口对缺失 jobParams 抛裸 `IllegalArgumentException`；告警调度同类分支已按 Dim09-05 改为结构化 NopException，报告侧漏改。
- **风险**: 影响有限（异常被本方法 catch Exception 吞掉、转为 failed 结果 Map，不出内部调度边界），但日志与结果体中呈现非结构化消息，与姊妹调度器约定漂移。
- **建议**: 对齐 AlertScheduler，改用 `ERR_DATAV_REPORT_TASK_NOT_FOUND` + param。
- **误报排除**: 确认该异常不外抛（方法内 catch 全覆盖），定级 P3 而非 P2。

### [P3] isUniqueConstraintViolation 仅匹配顶层 message，UK 冲突被包装时漏判

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateScreenExecutor.java:569-580`（Dashboard 侧共用，见 `DatavGenerateDashboardExecutor.java:191-198`）
- **维度**: D1（边界条件）
- **证据**:
```java
static boolean isUniqueConstraintViolation(Exception e) {
    String msg = e.getMessage();
    if (msg == null) {
        return false;
    }
    String lower = msg.toLowerCase();
    return lower.contains("unique") || lower.contains("duplicate");
}
```
- **现状**: 只检查异常顶层 `getMessage()`。并发同名创建窗口下，底层 UK 异常若被 ORM/事务层包装（原 SQL 异常在 cause 链），顶层消息可能不含 unique/duplicate，误判为普通错误。
- **风险**: 并发窗口内 LLM 收到的是通用错误而非“重名”错误，跳过 rename-retry 引导，生成失败率升高；不产生错误数据。
- **建议**: 沿 cause 链遍历匹配（保留现有子串白名单），或改判 `NopException.getErrorCode()` 与底层 UK 错误码映射。
- **误报排除**: 已确认匹配子串白名单本身经过 AR-3 修正（不再误匹配 CHECK/FK），问题仅在 cause 链未检查；属鲁棒性缺口而非确定性 bug。

## 已排查未列入（重要负结论）

- **SQL 注入面（D5）**: 取数链路唯一动态点 `PanelSqlBuilder` 用正则替换 `${param}` → `?` 顺序参数绑定（`sqlWithParams`），占位符缺参显式抛错；ChatBI 的 LLM params 同样只经参数绑定进入；所有手写 JDBC 更新（publish/rollback/thumbnail/visit-stats）全参数化。未发现拼接注入点。
- **查询结果缓存跨租户（D5）**: `DashboardPanelQueryCache.buildKey = datasetId + evaluatedParams + rowLimit`。经核实 `JdbcTemplateImpl.executeQuery` 对 SQL 文本不做宏展开、不注入会话/租户上下文，查询结果完全由（dsText[由 datasetId 唯一决定], params, rowLimit）决定，键完整，当前模型下无跨用户/租户污染路径（该缓存默认关闭）。
- **看板/面板越权（D5）**: `getPanelData/refreshPanel/resolveLinkage/resolveJump` 均有 `requirePanelDashboardAccess`（Dashboard RLS）；`getDashboardData` 走 `requireEntity`；分享管理走 `NopDatavDashboardOwnerGuard`；公共访问 `getSharedDashboard` 的 token/enabled/expire/password/看板存活五重校验 + 两级限流完整；action-auth/data-auth 矩阵将 Panel/Tab/DatasetRef/ScreenWidget 继承 CRUD 收敛 admin。未发现越权路径。
- **rollbackDashboard 只恢复主表字段**: 与 `ai-dev/design/nop-datav/model-design.md:39` 的裁定一致（快照子对象仅供历史浏览），非实现偏差。
- **D7 平台规范**: 无 private 字段注入（全部 protected 字段或 setter/构造器注入）；所有自定义 bean（guard/scheduler/recovery/evaluator/sender/chatbi 系列）在 `app-service.beans.xml` 显式定义；未发现 bare `RuntimeException`（仅上述两处 `IllegalArgumentException`）与 `printStackTrace`。
- **吞异常点复核**: 全部 `catch (Exception ignore/ignored)` 命中点逐一看过，均有注释裁定的容忍语义（解析失败降级、模板变量缺失不阻断送达、handler 失败记 DEBUG 不中断 LLM 循环），多数带日志。
- **并行取数上下文（D3）**: `executePanelQueriesInParallel` 每任务新建 context（`propagateContext` 拷贝 tenant/locale）+ 独立 ORM session + Semaphore 有界并行，Error 与非 NopException 的归集/重抛语义经推演正确。
- **告警聚合边界（D1）**: sum/avg/min/max 对 null 值显式抛错、avg 除零不可能（rowCount>0 前置）、无数据行视为条件不满足为设计文档裁定（count 聚合 0 行返回 null 亦同），未列为发现。
