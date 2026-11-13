# C5 内存有界性与大数据量 — nop-wf + nop-task 第 2 轮深挖（盲审）

- 审计单元：`nop-wf`（10 jar）+ `nop-task`（10 jar），379 files / 36,107 lines
- 仓库：nop-entropy-concurrency-audit，分支 concurrency-audit-remediation（HEAD be0cbbb2a3，与第 1 轮同基线，核心文件无漂移）
- 审计维度：C5 内存有界性与大数据量（第 2 轮：C5-001/C5-003 失效面扩展、流程变量大 payload 面 F11、有界判定抽查复核）
- 日期：2026-10-15；审计人：并发审计子代理（盲审；除第 1 轮 C5 报告外未读 ai-dev/audits、plans、bugs、lessons 下任何文件）
- 前序输入：`C5-memory-bounds.md`（第 1 轮，C5-001~010）——已完整读取

---

## 1. 本轮发现

### [nop-wf-nop-task-C5-011] getActionRecords 无 LIMIT 全量载入 + wf 级复合方法 N+1 全量物化（第 1 轮无界 API 盘点漏计站点）
1. **编号与标题**：C5-011 步骤 action 记录读路径无 LIMIT 全量载入，wf 级复合方法对全集步骤逐条发起 action 全量查询（N+1 + 全量物化）。
2. **严重度**：P3（站点无界但生产主链无内建调用者；wf 级复合调用叠加 C5-006 放大；低频可达路径）。
3. **缺陷族**：F7（无 LIMIT 全量载入 + N+1 放大）。系 C5-001 无界查询家族的失效面扩展。
4. **位置**：
   - `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:208-213`（getActionRecords(stepRecord)）
   - `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/IWorkflowStore.java:123-133`（default getActionRecords(wfRecord, filter) 复合方法）
   - `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowStepImpl.java:162-163`、`WorkflowImpl.java:252-253`（接口实现）
5. **证据片段**：
   ```java
   // DaoWorkflowStore.java:209-213 —— 单步骤全部 action 无 LIMIT 全量载入
   public List<? extends IWorkflowActionRecord> getActionRecords(IWorkflowStepRecord stepRecord) {
       QueryBean query = new QueryBean();
       query.addFilter(FilterBeans.eq(NopWfAction.PROP_NAME_stepId, stepRecord.getStepId()));
       return actionDao().findAllByQuery(query);   // 无 limit；平台 findAllByQuery 无隐式上限
   }
   ```
   ```java
   // IWorkflowStore.java:124-133 —— wf 级复合：全集步骤 × 每步一次 action 全量查询，filter 在内存侧施加
   default List<? extends IWorkflowActionRecord> getActionRecords(IWorkflowRecord wfRecord,
                                                                  Predicate<? super IWorkflowActionRecord> filter) {
       List<IWorkflowActionRecord> ret = new ArrayList<>();
       for (IWorkflowStepRecord stepRecord : getStepRecords(wfRecord, true, null)) {   // includeHistory=true → C5-006 全集
           for (IWorkflowActionRecord actionRecord : getActionRecords(stepRecord)) {   // 每步一次无 LIMIT SQL
               if (filter == null || filter.test(actionRecord)) { ret.add(actionRecord); }
           }
       }
       return ret;
   }
   ```
6. **驻留结构与增长源**：单步版本驻留 = 该 stepId 的全部 NopWfAction 行（会签/多次转办步骤的人工操作账本）；复合版本驻留 = 全 wf action 账本（O(Σ每步 action 数)）+ 每步一次独立 SQL（N+1）。增长源 = 步骤人工操作数与 wf 步骤行数（后者即 C5-006 的无界增长源）。filter 谓词无法下推 SQL，必须在全集物化后内存过滤。
7. **触发链**：wf XPL 模型脚本调用 `wf.getActionRecords()`（IWorkflow 接口方法对脚本可见）× 长 wf 实例（2 万步骤行、每步含转办记录）→ 单次调用 = 2 万次 `findAllByQuery` SQL + 全部 action 实体 + ret 列表三重驻留同一 ORM 会话。测试路径（TestWorkflowActionRecordFacade）确认行为可达；grep 全单元 xbiz/xpl/xmeta/UI 资源无内建调用者——生产暴露仅限模型作者显式调用。
8. **声明上界与保证者**：无。QueryBean 未设 limit，平台 findAllByQuery 不补偿（第 1 轮已核实 `OrmEntityDao.findAllByQuery` → `orm().findAll(sql)` 无隐式上限）。
9. **规模包络对照**：普通步骤（几次操作）包络内无感；会签大步骤或长 wf 全账本导出超包络。
10. **峰值叠加**：单请求 O(全 wf action 账本) × 并发模型脚本调用数；与 C5-006 同会话叠加（复合方法前置触发步骤全集载入）。
11. **误报校准**：非平台设施自身；但生产主链无内建调用者、单步 action 有界于人工操作数、wf 级调用为模型作者显式选择 → 降 P3。若后续版本将该接口暴露到 GraphQL/AMIS（如 wf 详情页 action 历史），应升级复评。
12. **修复方向**：(a) getActionRecords(stepRecord) 设 orderBy(execTime)+limit 或沿用平台分页；(b) wf 级复合方法改单条 SQL（wfId join stepId 下推 filter）消除 N+1，或删除该 default 方法（当前无生产调用者）；(c) 修复应与 C5-001/C5-002 共享的"QueryBean 无 LIMIT 查询族"一次性收口。
13. **覆盖度备注**：**盘点勘误**——第 1 轮 grep 计数记录"DaoWorkflowStore×3"，实际主代码 4 处（:177/:185/:193/:212），本轮补齐第 6 处站点；`WorkflowDefinitionDO.countByQuery`（:53-63）为 SQL 聚合有界，不属此族。第 1 轮清单表 3 行（findActivatedStepsByOwner/findDue/findRemind）结论不变。
- **复核状态**：已保留（P3。复核属实：getActionRecords(stepRecord) 无 limit（DaoWorkflowStore.java:209-213）、wf 级 default 方法 N+1 + 内存侧 filter（IWorkflowStore.java:124-135）、接口实现（WorkflowStepImpl.java:162-163 / WorkflowImpl.java:252-253）均与引文一致；grep 核实生产主链（xpl/xbiz/xmeta/UI）无内建调用者，P3 恰当。勘误部分核实：main 实际 4 处 findAllByQuery + 2 处 findFirstByQuery（键定位有界）= 6 站点，round2"共 6 处 findAllByQuery"的分类措辞不精确（6 为含 findFirstByQuery 的总数），但补站结论与各站点有界性判定正确。现状：新发现）

### [nop-wf-nop-task-C5-012] WfAiHelper.decide 将 LLM 输出键直写 wf 全局变量 KV 表 —— 变量名键空间无界的具体写入通道
1. **编号与标题**：C5-012 AI 决策步骤的动态变量名直通持久化 KV 表（第 1 轮残留边界 #1 的具体化：从"理论风险"升级为"具体写入源存在"）。
2. **严重度**：P3（per-wf 域、单值列宽有界、cascade-delete 可回收；但键空间无界且无 schema 门禁，读路径全集放大）。
3. **缺陷族**：F4-DB 变体（per-wf KV 行随 AI 调用累积）+ C5-003 同构（动态键键空间无界），但域为 per-wf KV 表而非全局注册表；读路径 F7（KvTableVarSet 全集懒加载）。
4. **位置**：
   - `nop-wf/nop-wf-ai/src/main/java/io/nop/wf/ai/WfAiHelper.java:34-38`（decide 写入 LLM 变量键）、`:60-70`（route 写入固定键 `__aiRouteResult`，有界）
   - `nop-wf/model/nop-wf.orm.xml:294-299`（globalVars → NopWfVar KV 子表）、`:666-681`（fieldName VARCHAR(100)、STRING_VALUE VARCHAR(4000) 为唯一定界）
   - `nop-persistence/nop-orm/src/main/java/io/nop/orm/kv/KvTableVarSet.java:21-58`（平台：getVar/setVar 经 IOrmEntitySet 全集）
   - `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WfRuntime.java:90`（VAR_WF_VARS 注入求值作用域）
5. **证据片段**：
   ```java
   // WfAiHelper.java:34-38 —— 变量名来自 LLM 返回 JSON 的任意键，不经模型 schema 白名单
   Object variables = result.get("variables");
   if (variables instanceof Map) {
       Map<?, ?> vars = (Map<?, ?>) variables;
       vars.forEach((k, v) -> wfRt.getWf().getGlobalVars().setVar(String.valueOf(k), v));
   }
   ```
   ```java
   // KvTableVarSet.java:38-42 —— 首次访问即触发该 wf 全部变量行的关系懒加载
   public Object getVar(String varName) {
       IOrmKeyValueTable item = (IOrmKeyValueTable) set.prop_get(varName);
       return item == null ? null : item.getValue();
   }
   ```
6. **驻留结构与增长源**：写入侧——每个新 LLM 键在 nop_wf_var 插入一行（wfId+fieldName 主键），行数随"AI 步骤调用次数 × 每次出现的键名"累积，wf 生命周期内无归档/裁剪；读取侧——任一步骤访问 wfVars（WfRuntime 作用域注入后一次 getVar/setVar）触发 NopWfInstance.globalVars 关系全集载入（该 wf 全部变量行驻留 ORM 会话）。对照引擎侧合法通道：`WorkflowEngineImpl.java:587` persist arg 的键 = 模型声明 arg 名（有界，有 schema 门禁），AI 通道无同款门禁。
7. **触发链**：wf 模型含 AI decide 步骤（LLM 输出 JSON）× 长生命周期流程跨月运行 × LLM 输出键名不稳定（如把业务实体 id 拼入键名）→ 单 wf 累积 10^5+ 变量行 × 每行 ≤4000 字符 → 每次 AI 决策/变量访问全量载入 10^5 行实体（含 stringValue）驻留会话，DB 侧账本同步膨胀。
8. **声明上界与保证者**：仅列宽定界（fieldName 100 / stringValue 4000，DB 侧）；行数无上界、无归档保证者、无键名白名单校验（xdef/xpl 构建期与执行期均未拦）。
9. **规模包络对照**：稳定键名（approved/risk 等有限集合）包络内有界；键名随调用漂移即超包络。owner doc（nop-wf.md）未声明变量名键空间约束。
10. **峰值叠加**：单请求 O(该 wf 变量行数) × 该 wf 并发动作数（WorkflowImpl 每请求新建、每请求各载一份全集，与 C5-006 同构叠加）。
11. **误报校准**：非平台设施自身（KvTableVarSet 为平台正确实现，本条不否定其机制）；触发依赖模型使用 AI 步骤 + 键名漂移两条件 → P3；DB 侧受 cascade-delete（wf 删除回收）约束，单独标注 F4-DB 受控子案。
12. **修复方向**：(a) decide 写入前按模型声明的 var/arg 名集合白名单过滤（对齐引擎 persist arg 门禁），拒绝名单外键并 WARN；(b) 或对单 wf 变量行数设上限（超限硬失败）；(c) route/extract 的 `__aiRouteResult` 固定键不受影响，无需处理。
13. **覆盖度备注**：第 1 轮残留边界 #1（"变量名有界性依赖模型作者纪律，未见构建期校验，P3 理论风险"）由本条升级：具体写入通道已存在且无门禁；"step 实例 ID 作为限流键"假设经核查不成立（见 2.2），C5-003 键源唯 keyExpr 求值一路。
- **复核状态**：已保留（P3。复核属实：decide 将 LLM variables 键直写 globalVars（WfAiHelper.java:34-38 引文一致）、route/extract 固定键（:60-70，另有 `__aiRouteDecision` 同为固定键有界）、KvTableVarSet 全集懒加载、WfRuntime.java:90 VAR_WF_VARS 作用域注入、nop-wf.orm.xml fieldName VARCHAR(100)/STRING_VALUE VARCHAR(4000) 与 globalVars cascadeDelete 均核实。P3（per-wf 域 + 列宽定界 + cascade-delete 可回收 + 双条件触发）恰当。现状：已知未修复——同点位已于 2026-09-22 code-review-full 立案（ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.1-wf-deep-audit.md P2-3.1-011/AU-10，安全维度：LLM variables 无过滤直写可覆盖 `__aiRouteDecision`），live 至今无白名单门禁、无修复提交（git log -S aiRouteDecision 仅特征提交 8ce94e9d69）；C5 键空间无界面为该点位新增维度，非重复立案）

---

## 2. 排除项/定向核查

**2.1 C5-001 失效面扩展（其他无 LIMIT 全量查询站点——remind/清理/统计类）**

- 全单元重扫 `findAllByQuery|findFirstByQuery|findAll(`（main 代码）：共 6 处 findAllByQuery（第 1 轮计 5 处，差 1 处即 C5-011）、1 处 findFirstByQuery（`DaoWorkflowModelLoader.java:74`，按 wfName+version 键定位，有界）、1 处 findFirstByQuery（`DaoTaskStateStore.java:384`，按 taskId+stepPath 键定位，有界）、test 代码 6 处（不计）。
- remind 扫描：`wf-remind-task-scan.job.yaml`（cron `0 0/5 * * * ?`、缺省 disabled、invoker=wfTaskScanner.scanRemindTasks）与 due 扫描同构同族，失败步骤 remandTime 不清除同样滞留扫描集——已在第 1 轮 C5-001 "findRemindActivatedSteps 同构同修"范围内，无新站点。
- 统计类：`WorkflowDefinitionDO.java:53-63` 两处 `dao.countByQuery(query)`——SQL count 聚合返回 long，无实体物化，有界；两单元无其他 dashboard/统计物化端点（nop-wf-service 全部 entity BizModel 均为纯 CrudBizModel，分页由平台 QueryBean 强制）。
- 清理类：两单元**不存在**任何清理/归档 job（nop-wf-scheduler 仅 2 个 yaml 均为扫描非清理）——与 C5-006 第 8 字段"缺历史归档机制"结论一致，无新站点。
- **C8-001 对账**：C8-001 与 C5-001 同点（WfTaskScanner 扫描族，对账依据为任务指令声明，未读 C8 维度文件）。本轮重扫确认扫描族之外无新增并发敏感的无 LIMIT 站点；C5-011（getActionRecords）为纯内存物化面，单请求单线程执行、无锁交互，无 C8 形态，不产生新的跨维度对账点。

**2.2 C5-003 失效面扩展（动态键其他来源）**

- **用户输入直通 keyExpr 的面（确认存在，并入 C5-003 既有触发链，不另立 ID）**：`TaskStepExecution.initInputs`（TaskStepExecution.java:493-507）将调用方 task inputs 写入 `stepRt.setValue(name, value)`，`ThrottleTaskStepWrapper/RateLimitTaskStepWrapper.getKey`（ThrottleTaskStepWrapper.java:59-64）在 stepRt 作用域求值 keyExpr——即 `<throttle keyExpr="${input.requestId}">` 使请求体字段直通全局注册表键。每请求新值 → 永久注册表条目（强引用、永不驱逐），较第 1 轮的 `${userId}` 示例更直接：**键基数 = 历史请求输入值域，攻击者可主动撑爆**。修复方向 (a)（构建期禁止高基数动态 key）因此升级为建议优先项。
- **step 实例 ID 作为键源（核查不成立）**：键唯一来源为 keyExpr 求值，缺省回落 `stepRt.getStepPath()`（模型有界）；step 实例 ID 不进入键路径。
- **静态注册表全扫**（`computeIfAbsent|putIfAbsent` main 全量 + static 集合字段全量）：`WfModelAnalyzer`（模型解析期，模型作用域）、`DefaultWorkflowDOProvider.java:32`（per-NopWfDefinition 实体缓存，1:1 有界）、`TaskRuntimeImpl.java:204`（per-runtime attrs）、`WfExecGroupType/WfTransitionToType.textMap`（枚举封闭）、`GraphTaskStep.STEP_RESULTS`（见 2.5 抽查）——无新的动态键全局注册表，C5-003 键源面收敛完毕。

**2.3 流程变量大 payload 面（vars/args 内存-DB 往返，F11）**

- **globalVars/outputs 采用 KV 子表 per-row 更新设计**（nop_wf_var/nop_wf_output，每变量一行、按 (wfId, fieldName) 主键 UPDATE）——不存在"每次 transition 全量重序列化变量集"的 O(N²) 形态（与 nop-task stateBean 的整体 JSON 形成对照），设计本身正确；读侧全集懒加载的放大已并入 C5-012 第 6/10 字段。
- **wfParams 往返（排除项，C5-008 同族扩展）**：`NopWfInstance.setParams`（NopWfInstance.java:142-148）`JsonTool.stringify(params)` 全量序列化后才落 4000 列——调用方传超大 start 参数时先物化完整 JSON String 再于 DB 层失败（瞬态 1× 载荷，无预算检查）；读侧 `getParams()`（:137-139）parseMap 无缓存，但 main 代码无生产调用者（grep 仅接口定义与 WorkflowRecordBean 覆写）。瞬态强度低于 C5-008 的 3×（P3），不另立 ID；建议 C5-008 修复（序列化前预算检查）时一并覆盖此站点。
- **TaskStepStateBean.getStateBean（确认既有修复有效）**：DB 往返后 Map→typed bean 转换结果写回字段（TaskStepStateBean.java:212-227，plan 364 [维度03-04]），resume 后每迭代重入不再反复 serialize+parse——第 1 轮 C5-005 的"每迭代全量重序列化"在同 runtime 进程内场景已由该写回缓解，跨 saveState（DB）路径仍按第 1 轮结论成立。
- **dump 路径（排除项）**：`TaskStepExecution.java:504-507,521-524` `dump=true` 时 `getDumpValue`（TaskStepHelper.java:398-406，DataBean 全量 serialize）进 LOG.info——无界输入进日志行，属日志体积/可观测性维度（F11 日志形态），非进程内存驻留，不立案。

**2.4 低覆盖模块补查**

- **nop-task-queue**：仅 `ITaskQueue.java`（4 行空方法接口），无实现、无状态，干净。
- **nop-task-ext**（7 文件逐一核读）：retry 装饰器构建期校验 maxRetryCount/retryDelay/maxRetryDelay ≥0（RetryTaskStepDecorator.java:27-52）后委托 RetryTaskStepWrapper（第 1 轮已核 wrapper 族）；timeout/ratelimit/orm/transaction 装饰器均无状态字段、无缓存；dao/orm 装饰器为会话级包装。无 C5 发现。
- **nop-wf-ai**：`WfAiHelper` 全静态方法、无静态字段、无对话历史累积（call 每次新建 ChatRequest，WfAiHelper.java:81-89）；route 的 `__aiRouteResult` 缓存于 per-wf globalVars（固定键，有界）。模块内唯一发现即 C5-012 的 variables 通道。
- **nop-wf-scheduler**：仅 2 个 job yaml（due/remind，均属 C5-001 族）+ WfTaskScanner（本轮全读复核：due/remind 两扫描的失败隔离注释与第 1 轮证据一致）。
- **NopWfWork（维度集外观察，不立案）**：`nop-wf.orm.xml:302-307` works 关系（子流程工作记录）**无 cascadeDelete 标记**（steps/outputs/globalVars 均有）——删除 wf 实例将遗留孤儿 NopWfWork 行。无 C5 内存读路径（grep 确认 engine 不物化 works 集合，仅 CrudBizModel 分页访问），属 F4-DB/数据完整性 P3 级观察，按 overlay 纪律（仅 P0/P1 跨维登记）不立案，留 F4 维度参考。

**2.5 第 1 轮有界判定抽查复核（抽 3 项独立验证保证者，全部通过）**

| 抽查项 | 第 1 轮判定 | 独立验证证据 | 结论 |
|---|---|---|---|
| TaskFlowMetricsImpl meter 键空间 | "按 (task.name, task.version, status) 内聚，模型有界" | TaskFlowMetricsImpl.java:28-48：tags 仅 taskNameTag/taskVersionTag/statusSuccess/Failure 四枚举化常量；beginStep 的 stepPath/stepType 参数**未**进入任何 tag（:71-78 仅 Sample 启停）；beginTask 返回的 Timer.Sample 由 endTask 必然 stop，无 meter 滞留 | 判定成立 |
| WorkflowImpl 每请求新建 | "无跨请求驻留注册表" | WorkflowManagerImpl.java:81-88 `getWorkflow(wfId)` 每次 `new WorkflowImpl(...)`；:68-74 newWorkflow 同构；`steps`/`commandQueue` 为实例字段（WorkflowImpl.java:41,46）；全单元无 static WorkflowImpl 集合 | 判定成立 |
| GraphTaskStep.STEP_RESULTS | "键 = 图节点名，模型有界（per-execution）" | GraphTaskStep.java:429-438 makeResults 存于 `stepRt.setValue(VAR_STEP_RESULTS, ...)`（task 执行作用域，非 static）；put 站点 :288/:321/:332 键均为 node.getStepName()/stepName（图模型节点名），无迭代后缀键 | 判定成立 |

**2.6 跨单元关联点（证据备查）**

- `nop-auth/nop-auth-dao/.../DaoUserDelegateService.java:40-59`：`getDelegateOwnerIds` 全量懒加载单用户 substitutionMappings 关系（nop-auth 模块，本单元集外）——C5-009 同族（org 配置数据天然有界），供 nop-auth 维度参考，不在本单元立案。

---

## 3. 终止声明

本轮深挖登记新发现 2 条（C5-011、C5-012，均 P3），未发现新的 P1/P2 级高价值问题。第 1 轮 P1×2（C5-001、C5-003）的失效面经本轮定向扩展后已收敛：无界查询族全量重扫补齐最后 1 处站点（C5-011），动态键源唯 keyExpr 求值一路且用户输入直通面已并入 C5-003 证据链；流程变量往返面确认为 KV per-row 正确设计；有界判定抽查 3/3 通过；低覆盖模块（task-queue/task-ext/wf-ai/scheduler）全部核读干净。

**未发现新的高价值问题。C5 维度深挖终止。**

---

## 4. 覆盖声明

**本轮新增覆盖**：
1. 无界 API 全量重扫（main 8 处站点逐一核实，修正第 1 轮计数 5→6）——C5-001 家族失效面闭合。
2. 静态注册表全扫（computeIfAbsent/putIfAbsent + static 集合字段两类 grep 全量核对）——C5-003 键源面闭合。
3. 流程变量大 payload 面定向：globalVars/outputs KV 表读写路径、wfParams 序列化往返、TaskStepStateBean 写回修复有效性、AI 变量写入通道（C5-012）。
4. 低覆盖模块 4 个（task-queue/task-ext/wf-ai/scheduler resources）逐一核读。
5. 第 1 轮有界判定抽查复核 3 项（TaskFlowMetricsImpl / WorkflowImpl / GraphTaskStep.STEP_RESULTS），全部通过。
6. 统计（countByQuery 聚合）与清理（无清理 job）类查询面核查；C8-001 同点对账（未读 C8 文件，按任务指令声明对账）。

**维持第 1 轮结论不变的项**：C5-001~010 全部条目及定级；五项检查点结论；残留边界 #2~#5（AMIS/GraphQL 投影、ResourceComponentManager 平台设施、fork 共享 stepPath 已声明边界、容量告警通路）。

**转出线索汇总**：(a) NopWfWork 缺 cascadeDelete → F4/DB 完整性维度（P3）；(b) DaoUserDelegateService substitution 全量载入 → nop-auth 单元 C5-009 同族；(c) dump=true 日志体积 → 可观测性维度；(d) wfParams stringify 站点 → 并入 C5-008 修复面。

**误报自我校准声明**：本轮 2 条新发现均给出具体触发链或明确降级理由（生产调用面核查 + 域有界性）；C5-011 含对第 1 轮盘点计数的勘误说明；C5-012 为第 1 轮残留边界的具体化升级而非重复立案；排除项均附独立验证证据。
