# C6 资源生命周期与泄漏 — nop-file / nop-datav 初审（单批全量）

- 审计单元：5 源码根全量；主战场 `DaoResourceFileStore`（上传去重/物理文件配对删除）+ datav 导出/交付临时资源链
- 方法：资源获取点配对表（成功/失败/取消/超时路径逐一核对）+ 注册表对称性；盲审
- CL 前缀：`nop-file-datav-CL-0NN`（自 CL-014 起）

## 资源获取点配对表

| 资源 | 获取点 | 释放点（成功） | 失败/取消路径 | 判定 |
|---|---|---|---|---|
| 物理上传文件 + DB 行 | DaoResourceFileStore.saveFile:184-245 | saveResource + dao.save | catch removeResource + finally close/temp.delete | ✓（writeStream 抛错时 removeResource 传 filePath 未变更值——目标未建成，delete 空 path 无害） |
| 上传流（InputStream + Digest/Limited 包装） | saveFile:214-227 | finally IoHelper.safeCloseObject(:241) | 同 finally | ✓（关闭链覆盖最外层包装） |
| 临时上传文件（tempResource） | saveFile:224 | finally tempResource.delete(:243) | 同 finally | ✓ |
| 共享物理文件（dedup） | copyFile 共享 filePath；detach/removeTemp 判 isUniqueRef 后删 | isUniqueRef→removeResource | **check-then-act 竞态 → C6-001 P1** | ✗ |
| 导出临时文件（writeCsv/writeXlsx getTempResource） | PanelDataExporter:218/:250 | 调用方 saveExportFile/saveReportFile finally delete | **导出器内部抛错时无 finally delete → C6-002 P2** | ✗ |
| 导出结果文件（fileStore.saveFile 落盘） | saveExportFile:391-417 / saveReportFile:399-424 | task.fileRecordId 关联 | **落盘成功后 cancel/写库失败 → 孤儿文件 → C6-003 P2** | ✗ |
| ORM session（runInNewSession/runInSession） | executeTask/runDelivery/attachFile 等 | 平台模板闭包 | 平台保证 close | ✓ |
| removeResource 删除失败 | :262-270 | — | log-only，DB 行已删 → 永久孤儿 | C6-004 P3 |
| LocalCache 条目（states/queryResultCache） | computeIfAbsent/put | 平台驱逐/TTL | — | ✓（有界） |
| listener/注册表 | PanelComponentRegistry（构造期静态）；scheduler.addJob ↔ removeJob | registerTask/unregisterTask/disableReportTasks 级联注销 | doRegister 失败清理旧 job（:253-262） | ✓（对称） |
| ChatBi 生成物 | 生成工具短事务落库 | 正常返回保留（草稿） | 循环异常补偿删除（REQUIRES_NEW）；**进程崩溃窗口 → C6-005 P3 watch** | △ |

## 发现条目

### [C6-001] 物理文件去重 check-then-act 竞态：并发 copy 与 detach 导致悬挂引用或提前物理删除

- **文件**: `nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java:362-401`
- **证据片段**:
  ```java
  // detachFileInSession :381-384
  dao.deleteEntity(record);                       // ① 删本记录
  if (isUniqueRef(dao, record)) {                 // ② 查同 originFileId 其他记录（:392-401）
      removeResource(record.getFilePath());       // ③ 无其他引用 → 删物理文件
  }
  // copyFile :442-460 —— 新记录共享同一 filePath/originFileId
  newRecord.setOriginFileId(record.getOriginFileId());
  newRecord.setFilePath(record.getFilePath());
  ```
- **严重程度**: P1
- **并发场景**: T1=detach 请求线程（或 removeTempFileByOwner）；T2=copyFile 请求线程（实体复制流携带附件）。两者操作同一 NopFileRecord 记录，物理文件为多记录共享（dedup 设计）。
- **触发时序**: T2 执行到 :444 `requireEntityById` 读到记录（尚未插入新记录）→ T1 执行 :381 删除原记录 → T1 执行 :382 `isUniqueRef` 查询（此刻 T2 的新记录未提交，不可见）→ 判定"唯一引用" → T1 :383 `removeResource` 删除物理文件 → T2 :459 提交新记录（filePath 指向已删除文件）。后果：新记录永久悬挂——下载 `getFileResource` 返回不存在的 resource；反向交错（T2 先提交、T1 后查）则 isUniqueRef 正确返回 false，无损害。
- **现状**: 去重判定（读）与物理删除（写）之间无串行化保障；copyFile 侧插入也未与 detach 同锁。P1-3.8-002 修复（plan 2386）补了 attach/detach 的权限与会话归属，未覆盖 copy 并发窗口。
- **风险**: 附件数据丢失（引用计数竞态的经典失效：删除方看到过期引用计数）；文件一旦删除不可恢复。属"罕见但可具体化的竞态"（P1 判级标准）。
- **建议**: 物理删除改两段式——isUniqueRef 命中后不在请求路径立即 removeResource，改为登记待清理路径（延迟清理任务再次校验引用后删）；或对 originFileId 维度加应用层互斥（平台 IResourceLockManager 按 originFileId 加锁包裹 detach/copy）。
- **失效面枚举**（F3）: `detachFileInSession`（本条主面）；`removeTempFileByOwner` :164-170（同 isUniqueRef→removeResource 模式，同族同修）；`saveFile`（新路径新 fileId，无 dedup，无涉）；`copyFile`（插入侧，本条触发方）；`OrmFileListComponent/OrmFileComponent`（nop-orm 侧调用方，仅透传，无涉）。
- **信心水平**: 确定（交错序列完整、引用计数语义明确）
- **误报排除**: 非"低频管理操作粗锁合理"豁免——detach/copy 均为业务请求路径可并发触达；非平台 ResourceStore 职责——竞态在应用层判-删序列。
- **复核状态**: 已保留（回码验证：isUniqueRef 读-判-删序列属实；copyFile 经 OrmFileComponent:159 实体复制流可达实测确认；现状：新发现）

### [C6-002] 导出器临时文件失败路径泄漏：writeCsv/writeXlsx 异常时无 finally 删除

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/export/PanelDataExporter.java:215-241, 249-258`
- **证据片段**:
  ```java
  // writeCsv :218-239
  IResource resource = ResourceHelper.getTempResource("datav-export");
  try {
      os = resource.getOutputStream(); ... output.write(row) ...
  } catch (IOException e) {
      throw NopException.adapt(e);        // 无 finally delete → 临时文件残留
  } finally {
      IoHelper.safeCloseObject(output);   // 只关流，不删文件
      IoHelper.safeCloseObject(os);
  }
  // writeXlsx :250-255 —— saveExcel 抛错同样直接 throw，无 resource.delete()
  ```
- **严重程度**: P2
- **并发场景**: globalWorker 线程；单线程即可触发（非竞态，失败路径清理不对称，F8）。
- **触发时序**: 取消检查点命中（行循环 checkCancelled 抛 NopException，:229）或磁盘满/CSV 写出 IOException → writeCsv 抛出 → 调用方 executeTask 的 catch 只更新任务状态，`file` 变量未赋值 → saveExportFile 的 finally delete（:412）**不会执行**（其 finally 只覆盖 saveExportFile 自身 try）→ 临时文件永久残留于 temp 目录。
- **现状**: 成功路径清理对称（调用方 finally 删）；失败路径清理缺失——资源创建点与删除点分属两个方法且异常边界未对齐。
- **风险**: 长时运行下失败导出在 temp 目录线性积累（每次失败一个文件，大小可达百 MB 级）；磁盘耗尽候选。
- **建议**: writeCsv/writeXlsx 增加 `catch → resource.delete() 后 rethrow` 或返回前移除职责内 finally 删除；调用方 saveExportFile/saveReportFile 的 finally 已删除成功产物——两处收口后全路径对称。
- **失效面枚举**（F3）: `writeCsv`（本条）；`writeXlsx(ExcelWorkbook)`（本条同修）；`writeXlsx(singleSheet)`（委托前者，随同修复）；`saveExportFile`/`saveReportFile` finally delete（已修对照——成功+落盘失败路径均有删）；`DaoResourceFileStore.saveFile` tempResource（已修对照）。
- **信心水平**: 确定
- **误报排除**: 非平台 temp 机制自清理假设——`ResourceHelper.getTempResource` 无自动回收契约证据；泄漏链完整可追。
- **复核状态**: 已保留（回码验证：writeCsv/writeXlsx 异常路径无 resource.delete、调用方 finally 仅覆盖自身成功产物属实；checkCancelled 抛 RuntimeException 穿过 IOException catch 后仍走无删 finally 属实；现状：新发现）

### [C6-003] cancel-after-save / fail-after-save 孤儿导出文件：已落盘文件永久不可达

- **文件**: `NopDatavExportTaskBizModel.java:335-349, 350-365`；同族 `ReportDeliveryExecutor.java:266-292`
- **证据片段**:
  ```java
  String fileId = saveExportFile(file, taskId);     // ① NopFileRecord 行 + 物理文件已持久化
  if (Boolean.TRUE.equals(cancelFlags.get(taskId))) {
      return null;                                   // ② cancel-pre-SUCCEEDED：fileId 丢弃，不回滚 ①
  }
  task.setStatus(NopDatavExportTaskStatus.SUCCEEDED);
  task.setFileRecordId(fileId);                      // ③ 唯一关联点——②或异常路径都到不了这里
  ```
- **严重程度**: P2
- **并发场景**: 取消线程与 worker 线程（cancel 窗口），或 saveExportFile 成功后 updateEntityDirectly 抛错（DB 抖动/乐观锁冲突）。
- **触发时序**: 取消变体：T2 cancel 在 ①与③ 之间置位 cancelFlags → worker ② 返回 → NopFileRecord 行（bizObjId=taskId）与物理文件已存在，但 task.fileRecordId 永不写入 → 无任何清理路径触达（`removeTempFileByOwner` 仅清 TEMP_BIZ_OBJ_ID 标记的记录；该记录 bizObjName=nopDatavExportTask）→ 磁盘+DB 行双孤儿。失败变体：③ updateEntityDirectly 抛异常 → catch 写 FAILED（不引用 fileId）→ 同样孤儿。
- **现状**: cancel 兜底点（:339）保住了状态语义但未处理"半个成功"的文件侧（F2）；交付执行体同构（saveReportFile 成功后 :275 update 抛错 → FAILED，fileId 丢弃）。
- **风险**: 用户不可见但永久累积的存储泄漏（百 MB 级文件/次）；伴随 NopFileRecord 脏行。
- **建议**: ②分支与 FAILED 分支补 `fileStore` 侧回收——最小实现：在 catch/cancel 分支调用 `ormTemplate` 删除 NopFileRecord 行 + 物理文件（镜像 detachFileInSession 的 isUniqueRef→removeResource 序列，fileId 尚无共享引用，删除安全）；或延后：saveExportFile 成功后先记 taskId→fileId 于 delivery 行再决策终态。
- **失效面枚举**（F3）: `executeTask` cancel-pre-SUCCEEDED（本条）；`executeTask` catch-FAILED-after-save（本条同修）；`runDeliveryInSession` :275 update 抛错（同族未修）；`sendNotificationOutOfSession` 失败路径（SUCCEEDED 已提交且 fileRecordId 已写——文件可达，无涉）。
- **信心水平**: 确定
- **误报排除**: 非设计豁免——代码注释明示该兜底点语义为"保留 cancel 线件的终态"，未声明接受文件泄漏；触发序列具体。
- **复核状态**: 已保留（回码验证：:339-342 cancel-pre-SUCCEEDED 早退时 fileId 已产生但丢弃属实；removeTempFileByOwner 仅清 TEMP_BIZ_OBJ_ID 属实；现状：新发现）

### [C6-004] removeResource 删除失败 log-only：DB 行已删而物理文件永久孤儿

- **文件**: `nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java:262-270`；触发点 :168/:238/:383
- **证据片段**:
  ```java
  private boolean removeResource(String path) {
      try {
          return resource.delete();
      } catch (Exception e) {
          LOG.error("nop.file.remove-file-fail:path={}", path, e);
          return false;          // 调用方不重试、不登记待清理
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: 非竞态；文件系统瞬态故障（句柄占用/权限/挂载抖动）时的清理路径。
- **触发时序**: detachFileInSession :381 已删 DB 行 → :383 removeResource 失败 → false 被忽略 → 物理文件永久孤儿（无二次扫描/对账机制；removeTempFileByOwner 只按 owner+TEMP 标记查询 DB，孤儿文件无 DB 行不可枚举）。
- **现状**: best-effort 删除 + 错误日志；无重试/无死信登记（对照本仓库 F6/F8 教训中的"清理不对称"画像——此为半对称：DB 侧成功、文件侧失败无补偿）。
- **风险**: 磁盘慢泄漏；无对账手段（引用信息随 DB 行删除而丢失，事后无法定位孤儿归属）。
- **建议**: 删除失败时将 path 记入待清理表（或日志结构化 path 字段供 grep 对账 + 定期按存储目录与 DB filePath 全量对账任务）。
- **失效面枚举**: `removeResource` 三个调用方 :168（removeTempFileByOwner）/ :238（saveFile 失败回滚）/ :383（detach）——共用同一 best-effort 语义，一处收口全体受益。
- **信心水平**: 确定（机制确定，触发依赖文件系统故障）
- **误报排除**: 非平台 resource.delete 语义问题；log-only 是本模块选择而非平台约束。
- **复核状态**: 已保留（回码验证：removeResource catch return false、三调用方均不补偿属实；现状：新发现）

### [C6-005] ChatBI 生成补偿的进程崩溃窗口（已声明边界外延，watch）

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavChatBiBizModel.java:404-414, 466-491`
- **证据片段**:
  ```java
  List<String> createdDashboardIds = new ArrayList<>();   // 补偿清单仅存内存
  try {
      return runLoopOutsideTransaction(() -> loop.run(...));
  } catch (RuntimeException e) {
      compensateGeneratedDashboards(createdDashboardIds);  // 仅覆盖同进程异常
      throw e;
  }
  ```
- **严重程度**: P3
- **并发场景**: 无竞态；循环内每个生成工具已独立短事务提交，JVM 在两工具之间崩溃时已提交的草稿实体残留。
- **触发时序**: 工具 A 创建 dashboard 提交 → JVM 崩溃/kill -9 → 重启后草稿残留（补偿清单丢失）。
- **现状**: "no-partial-artifact contract" 由 javadoc 声明，但契约覆盖域是进程内异常；重启恢复路径（recovery 家族）只管 export/delivery，不含 ChatBI 生成物。按"区分缺陷与已声明边界"纪律：进程内已声明、进程外未声明——登记边界外延 watch。
- **风险**: 低（草稿为用户可见 DRAFT 对象，可手动删除）；存储慢增长。
- **建议**: 如需闭环：生成实体打 createdBy+sessionId 标记，重启恢复扫描"无主草稿"清理；或在 ai-design.md 显式声明崩溃窗口接受。
- **失效面枚举**: `chatToDashboard`（本条）；`chatToScreen`（同构，同族同修）；`chatToQuery`（只读+appendTurn，appendTurn 失败不落库已声明，无涉）。
- **信心水平**: 确定（窗口机制确定；后果轻微故 P3）
- **误报排除**: 非"已声明即豁免"误判——声明仅覆盖进程内，登记的是未声明部分。
- **复核状态**: 已保留（回码验证：补偿清单为请求内存态、recovery 家族不含 ChatBI 生成物属实；现状：新发现）

## 维度复核结论

- 复核口径：5 条初审 + 3 条深挖发现逐条回码验证。
- 三态判定：**保留 8 / 降级 0 / 驳回 0**。
- 现状标注：**新发现 8**；已知未修复 0；已修复 0（注：P1-3.8-002/plan 2386 已修复的 attach/detach 会话归属面与本轮发现正交，登记为已修对照而非本轮现状）。
- 复核关键验证：copyFile 经 OrmFileComponent:159（实体复制流）可达——C6-001 触发面成立；writeCsv 失败路径 checkCancelled 抛 RuntimeException 穿过 catch(IOException) 后仅关流不删文件——泄漏链成立；recovery 状态过滤确认 SUCCEEDED 行不被重启恢复覆盖——C6-R2-001 窗口成立。
- 跨单元关联登记：C6-R2-001 → 半程窗口/幽灵任务族（WI1.6 C3-005 弱关联）；C6-003 与 C6-R2-002 孤儿文件同治理面（本单元内合并，见 merge-adjudication §八）。

## 最终保留项

| CL | 发现 | 严重度 | 现状 | 跨单元关联 |
|---|---|---|---|---|
| CL-014 | C6-001 物理文件去重 check-then-act（悬挂引用/提前删除） | P1 | 新发现 | — |
| CL-015 | C6-002 导出器临时文件失败路径泄漏 | P2 | 新发现 | — |
| CL-016 | C6-003 cancel/fail-after-save 孤儿导出文件 | P2 | 新发现 | — |
| CL-017 | C6-004 removeResource 失败 log-only 永久孤儿 | P3 | 新发现 | — |
| CL-018 | C6-005 ChatBI 生成补偿崩溃窗口（边界外延 watch） | P3 | 新发现 | — |
| CL-023 | C6-R2-001 交付两段式 SMTP 半程窗口静默丢发 | P2 | 新发现 | WI1.6 C3-005（弱） |
| CL-024 | C6-R2-002 删除级联 × 在途交付并发（写丢失+孤儿窗口） | P3 | 新发现 | — |
| CL-025 | C6-R2-003 回补双失败 → SUCCEEDED 误标 | P3 | 新发现 | — |
