# 维度 04：安全性（Security）— nop-task 深度审核

- **审核日期**: 2026-09-25
- **轮次**: 第 1 轮（初审）完成，待深挖与复核
- **基线**: `_tmp/audit-baseline.txt`
- **口径**: live code + 源模型（`model/*.orm.xml`、`*.xdef`、auth 配置）；生成物仅作对照

## 第 1 轮（初审）

### [维度04-01] 四个实例/定义实体均为裸 CrudBizModel 且 ORM 列全部可更新 → 持有 `mutation` 权限即可伪造任务终态、绕过乐观锁、改写归属字段

- **文件**: `nop-task/nop-task-service/src/main/java/io/nop/task/service/entity/{NopTaskInstance,NopTaskStepInstance,NopTaskDefinition,NopTaskDefinitionAuth}BizModel.java`（各 15 行）、`nop-task/model/nop-task.orm.xml`、`nop-task/nop-task-api/.../crud/NopTaskInstanceApi.java:10-13`
- **证据片段**:
  ```java
  @BizModel("NopTaskInstance")
  public class NopTaskInstanceBizModel extends CrudBizModel<NopTaskInstance> implements INopTaskInstanceBiz {
      public NopTaskInstanceBizModel(){ setEntityName(NopTaskInstance.class.getName()); }
  }
  // NopTaskInstanceApi extends ICrudApi<...> → 通用 save/update/loadFetch/fetchPage 全部经 GraphQL 暴露
  // nop-task.orm.xml：全文件 0 处 updatable="false"（rg 'updatable="false"' → No files found）
  // status 列：line 173-174 <column ... name="status" stdDataType="INTEGER" ext:dict="task/task-status"/>
  // stepStatus 列：line 276-278 同样无任何写入端保护；dict 仅为 UI 提示，无取值校验
  ```
- **严重程度**: P1
- **现状**: `update`/`save` 入参直通实体全列写入。可写字段包括 `status`（taskStatus 字典 40/50/60/70 终态）、`stepStatus`、`taskInputs`/`stateBeanData`（VARCHAR 4000 JSON）、`version`（乐观锁列，可显式指定期望值）、`taskVersion`、`createdBy/updatedBy`。权限面仅 `_nop-task.action-auth.xml` 的 `NopTaskInstance:mutation` 一档，无字段级/取值域约束。
- **风险**: ① 有 `mutation` 权限者（不需 deploy/执行权限）可把运行中任务直接改成 COMPLETED/FAILED，绕过引擎 first-terminal-wins 驱动，下游消费方读到伪造终态；② 直接伪造 `stateBeanData`/`taskInputs` 后触发 resume，注入引擎恢复上下文（循环下标、nextStepName、persistVars）；③ 指定 `version` 可配合多次读取定向覆写他人并发修改。04-02 的 data-auth 缺失使其失去行级兜底。
- **建议**: 状态机列（status/stepStatus/异常列）设 `updatable="false"` 或收敛为引擎独占写路径（`DaoTaskStateStore` 经 `saveEntityDirectly` 不受影响）；`stateBeanData/taskInputs/taskVersion` 同理；CRUD 入口仅保留展示性字段的 update。
- **信心水平**: 确定（裸 BizModel×4、0 处 `updatable="false"`、ICrudApi 暴露面三方核实；无字段级校验代码）
- **误报排除**: 非"ORM 允许更新是平台惯例"——`nop-task.orm.xml:101,149` 证明该文件知道 `updatable` tag 的存在（仅用于 to-many 关系），实例表关键列未使用；非"mutation 权限仅 admin"——权限粒度与职责分离无关，engine-reserved 列本不应经 CRUD 可写。
- **复核状态**: 未复核

### [维度04-02] `app.data-auth.xml` 引用的 `GenFromModules` 标签已在 2025-02 被改名删除且全仓未同步 → nop-task-app 的 data-auth 加载失败或退化为空模型（fail-open）

- **文件**: `nop-task/nop-task-app/src/main/resources/_vfs/nop/task/auth/app.data-auth.xml:2-7`、`nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/nop/auth/xlib/auth-gen.xlib:27,57`、`nop-auth/nop-auth-service/.../DefaultDataAuthChecker.java:86-96,179-205`
- **证据片段**:
  ```xml
  <!-- nop-task-app 的 data-auth（注意：无 xpl:lib，与 docs/codegen 模板写法也不一致） -->
  <data-auth ... xmlns:auth-gen="auth-gen">
      <x:gen-extends><auth-gen:GenFromModules/></x:gen-extends>
  </data-auth>
  ```
  ```xml
  <!-- 全仓唯一的 auth-gen.xlib 实际定义（find . -name auth-gen.xlib → 仅此一个）： -->
  <GenDataAuthFromModules outputMode="node"> ... </GenDataAuthFromModules>
  <GenActionAuthFromModules outputMode="node"> ... </GenActionAuthFromModules>
  <!-- git show 8438c69a2f（2025-02-21）引入改名，--name-only 中 app.data-auth 命中数 = 0 -->
  ```
  ```java
  // DefaultDataAuthChecker.loadDataAuthModel：parseFromResource 无 try/catch
  // isPermitted: ObjDataAuthModel objAuth = authModel.getObj(bizObj); if (objAuth == null) return true;   // fail-open
  // getFilter:   if (objAuth == null) return null;                                                        // 无过滤
  ```
- **严重程度**: P1
- **现状**: 三个独立事实叠加：① 标签 `GenFromModules` 自 commit `8438c69a2f` 起不存在于唯一 auth-gen.xlib（改名为 `GenDataAuthFromModules`），15 个 app 配置 + docs + codegen 模板全部未同步，nop-task-app 属受影响名单；② nop-task-app 的写法还缺 `xpl:lib="/nop/auth/xlib/auth-gen.xlib"`（多数 app 与模板都有）；③ `XplLibHelper/XplCompiler` 语义：ns 默认 enabled（`isNsEnabled` 默认 true）→ 未知标签在 `allowUnknownTag=false` 时抛 `ERR_XPL_NOT_ALLOW_UNKNOWN_TAG`，为 true 时静默产出空 `<data-auth>`；`DefaultDataAuthChecker` 对空模型按 `objAuth == null → return true / return null` **fail-open**。
- **风险**: 两种结局均不可接受——(a) 首次 data-auth 检查即抛异常（缓存 `check-changed` 每次重解析同样失败）；(b) data-auth 静默整体失效：所有实体无行级过滤、无角色匹配校验，04-01 的通用 CRUD 完全失去数据权限兜底。该断裂已存在约 19 个月且**全仓 0 测试**加载过 nop-task 的 app.data-auth（`rg data-auth --glob '*Test*'` 仅命中 nop-datav/nop-auth）。
- **建议**: 改为 `<auth-gen:GenDataAuthFromModules xpl:lib="/nop/auth/xlib/auth-gen.xlib"/>`（与 codegen 模板同步升级）；同批修复其余 14 个 app 与 `docs/dev-guide/auth/auth.md:260`；补一条 `DslModelParser.parseFromResource("/nop/task/auth/app.data-auth.xml")` 的回归测试断言解析成功且 gen 后含模块 `<objs>`。
- **信心水平**: 根因确定（tag 不存在、改名 commit、0 同步文件均可复现）；精确失败模式（抛错 vs 空模型）很可能，需运行时复核——`allowUnknownTag` 在 gen-extends 上下文的取值未静态定论。
- **误报排除**: 非"配置永不用到"——`nop-task-app/application.yaml:16` 显式配置 `data-auth-config-path` 指向该文件；非"空 objs 是惯例"——惯例模块（nop-sys/nop-job/nop-file 等 7 个）已改为纯 `<objs/>`，nop-task-app 保留的是坏 tag 而非空配置。
- **复核状态**: 未复核（P1，阶段二必须运行时验证失败模式）

### [维度04-03] `NopTaskDefinitionAuth`（任务定义权限）是授权摆设：全仓零执行点

- **文件**: `nop-task/model/nop-task.orm.xml:110-146`、`nop-task/nop-task-service/.../NopTaskDefinitionAuthBizModel.java`、`nop-task/nop-task-web/.../pages/NopTaskDefinitionAuth/NopTaskDefinitionAuth.view.xml`
- **证据片段**:
  ```bash
  $ rg -n "DefinitionAuth|definitionAuth" --glob '*.java' --glob '!*nop-task*'   # 仓库其它模块
  (无命中——仅 nop-task 自身)
  $ rg -n "NopTaskDefinitionAuth|definitionAuth" nop-task/{nop-task-core,nop-task-dao,nop-task-ext}/src/main   # 执行/引擎侧
  仅命中：entity 定义、ICrudBiz 接口、api Input/OutputBean 字段
  # 引擎入口无授权参数：
  public ITask getTask(String taskName, long taskVersion)   // TaskFlowManagerImpl:132 — 无 IAuthorization 检查
  ```
- **严重程度**: P2
- **现状**: 该表 + AMIS 管理页 + CRUD 接口齐备，管理页文案为"逻辑流定义权限"，暗示按人/角色配置谁能使用某任务定义；但引擎加载与执行路径（`TaskFlowManagerImpl.getTask/execute`、service 层）没有任何代码读取 `definitionAuths` 或据此拦截。同模式也出现在 nop-wf/nop-report（codegen 模板产物），全仓无消费者。
- **风险**: 部署方按 UI 预期配置"仅角色 A 可执行任务 X"，实际配置不产生任何强制——形成**授权假象**，比没有该功能更危险（审计与运维会信任它）。叠加 04-02 时，任务定义的可用性控制只剩粗粒度 action-auth。
- **建议**: 在任务执行入口（service 层 wrapper 或 `getTaskRuntime`）接入 definitionAuth 校验；或短期先在 view/文档标注"当前仅登记用途，不参与执行鉴权"。
- **信心水平**: 很可能（引擎/service/其它模块三侧 grep 零执行点；不排除定义被设计为"由上层应用自行消费"的扩展点——但随附管理 UI 无任何说明）
- **误报排除**: 非"生成物不作对象"——表、页面、BizModel 全部为手写/生成的活代码；非"引擎不能依赖 dao"——鉴权可放 service 层（现有 service 未做即为缺口）。
- **复核状态**: 未复核

### [维度04-04] 异常诊断三列（errMsg/errorStack/errorBeanData）落库后经通用 query 权限可读，堆栈与 XPL 路径对外泄露

- **文件**: `nop-task/nop-task-dao/.../DaoTaskStateStore.java:197-216,450-469,594-630`、`nop-task/model/nop-task.orm.xml`（step/task 实体 error 列）
- **证据片段**:
  ```java
  // extractErrorStack: ERROR_STACK_MAX_LEN = 4000（top-level 写 errorStack 列）
  //                   PER_CAUSE_STACK_MAX_LEN = 800（编入 errorBeanData 各 cause）
  // ErrorBean = errorCode + description + params + errorStack（含类名、XplStack、文件路径）
  // 暴露面：NopTaskInstanceApi/NopTaskStepInstanceApi 继承 ICrudApi → loadFetch/fetchPage 返回全字段
  ```
- **严重程度**: P3
- **现状**: 引擎把完整诊断（截断后仍 4000 字符的 stack + 各 cause 链 params）持久化到实例行；通用 CRUD 的 query 权限（`NopTaskInstance:query`）即可整行读出，无字段裁剪。error params 可能携带任务入参片段（`ARG_TASK_NAME`、step 表达式上下文等）。
- **风险**: 内部类名/文件路径/XPL 调用栈帮助攻击者测绘框架版本与代码结构；异常 params 可能含业务敏感值；query 权限通常授予运维/查询角色，超出"看板只需状态"的最小需求。
- **建议**: query 出口对 errorStack/errorBeanData 做字段级裁剪（detail 仅 admin 或单独 `:queryError` 权限）；或 errorStack 不入库、仅落日志。
- **信心水平**: 确定（写入、列定义、无裁剪的读出面均可直读）
- **误报排除**: 非"诊断落库是设计"——owner 文档已声明 errorStack 持久化设计（plan 265），本条报的是**读出面无分级**，与写入设计正交。
- **复核状态**: 未复核

### [维度04-05] 任务实例表族无 tenantId/namespaceId 列 → data-auth 层无法对任务实例做租户隔离

- **文件**: `nop-task/model/nop-task.orm.xml`（全部 4 个实体）
- **证据片段**:
  ```bash
  $ rg -n "tenantId|namespaceId" nop-task/model/nop-task.orm.xml
  (无命中)
  $ rg -n 'domain="(tenantId|namespaceId|owner)"' nop-task/model/nop-task.orm.xml
  (无命中)
  $ rg -c "tenantId" nop-wf/model/nop-wf.orm.xml   # 对照模块
  0
  # 对照：nop-auth 的 NopAuthUser 有 tenantId，其 data-auth 规则正是 <eq name="tenantId" .../>
  ```
- **严重程度**: P3
- **现状**: `nop_task_instance` / `nop_task_step_instance` 无任何租户/命名空间列，`getFilter` 即便生成 `<eq name="tenantId" .../>` 过滤条件也无列可下推；任务实例的行级隔离只能靠调用方在业务层自觉过滤。
- **风险**: 多租户部署下，任一持有 `NopTaskInstance:query` 的主体可见全部租户的任务实例、入参（taskInputs）与结果（outputs/stateBeanData 中的业务数据）。与 nop-wf/nop-report 同模式，可能属平台级既定裁定（tenant 隔离上移到应用层），但 nop-task owner 文档未声明此边界。
- **建议**: owner 文档"已知边界"补记"任务实例表无租户列，多租户场景需应用层过滤"；或在模型加 `namespaceId`（domain 现成）并配 data-auth 规则。
- **信心水平**: 很可能（列缺失确定；"是否平台裁定"未找到文档，故 P3）
- **误报排除**: 明确对照 nop-auth 有租户列+规则，证明平台具备该能力；本条以"未声明的边界"入账而非直接要求改模型。
- **复核状态**: 未复核

### [维度04-06] 全局限流/信号量 key 不含租户维度 → 跨租户资源耦合（公平性与可用性）

- **文件**: `nop-task/nop-task-core/.../TaskFlowManagerImpl.java:170-173,188-190`
- **证据片段**:
  ```java
  String cacheKey = taskRt.getTaskName() + ":" + key;                       // rate limiter
  return globalSemaphores.computeIfAbsent(taskRt.getTaskName() + ":" + key,  // semaphore
          k -> new DefaultSemaphore(maxPermits));
  // global=true 时跨 taskRuntime 共享；key 中无 tenant/user/namespace 维度
  ```
- **严重程度**: P3
- **现状**: `global="true"` 的 `<rateLimit>`/`<throttle>` 以 `taskName:key` 为全局共享单元。同名任务被多个租户执行时共享同一 permit 池。
- **风险**: 一个租户的高频执行可耗尽全局限额 → 其它租户同名任务被限流拒绝（跨租户 DoS/降级耦合）；`<throttle maxConcurrency>` 同理，一个租户占满并发位后其它租户排队等待。属公平性而非越权问题，故 P3。
- **建议**: cacheKey 注入 tenant 维度（`taskName + ":" + tenantId + ":" + key`）或在文档明确 `global` 即"刻意跨租户共享"的运维语义。
- **信心水平**: 确定（key 构造直读）
- **误报排除**: `global` 属性本意是跨实例共享，但"共享范围不区分租户"是另一维度——单租户部署无影响，多租户下才暴露，且无文档说明该副作用。
- **复核状态**: 未复核

## 检查范围清单（第 1 轮）

### 扫描过的关键面
| 检查项 | 命令口径与结论 |
|---|---|
| 注入（SQL/DQL） | `rg "executeSql\|queryEntityByDql\|\"SELECT \|String.format.*SELECT"` → **0 命中**；全部查询经 QueryBean/ORM 参数化 |
| 列级写保护 | `rg 'updatable="false"' nop-task/model` → No files found；仅 to-many tagSet 提及 updatable |
| 裸 CRUD 暴露 | 4 个 BizModel 全读（各 15 行，无字段守卫）；4 个 Api 接口全读（均为 `extends ICrudApi`，0 自定义 action） |
| data-auth 链路 | app/service 两个 data-auth 全读；auth-gen.xlib 全读；`XplCompiler.getTagCompiler`/`XplLibHelper.getTag`/`isNsEnabled` 语义核实；`DefaultDataAuthChecker` 全读（fail-open 语义）；git 历史定位改名 commit |
| 授权执行点 | 全仓 `DefinitionAuth` Java 引用枚举（仅 nop-task 自身）；`TaskFlowManagerImpl.getTask` 无授权参数 |
| 诊断信息泄露 | `extractErrorStack`/`serializeErrorBeanData` 写入链 + ICrudApi 读出面 |
| 租户隔离 | 4 实体列扫描 vs nop-auth/nop-wf 对照 |
| 全局限流公平性 | `getRateLimiter`/`getSemaphore` key 构造 |
| 公开执行入口 | `@BizModel`/`@BizAction` 全列（服务层 4 个、api 4 个，均 CRUD）；无 GraphQL 任务执行/kill/resume 动作 |
| 存储代码执行 | `nop_task_definition.modelText`（mediumtext）→ `rg modelText` 主源码：**仅 CRUD Input/OutputBean**，引擎 `parseTask(IResource)` 只吃 VFS 资源 → DB 模型文本在本模块内不被执行 |
| 密钥硬编码 | 无凭据类配置；任务模型为部署期信任内容 |
| 生成物排除 | `_nop-task.action-auth.xml`、`_service.beans.xml`、api beans 仅作对照面引用 |

### 零发现项
1. SQL/DQL 注入：0 个字符串拼接查询
2. 任务执行无公开入口：GraphQL 仅 CRUD；`task.execute` 为嵌入式 API（调用方即宿主代码，属信任边界内）
3. `modelText` 不在本模块执行链上（若未来接执行，需重新评估为 P1）
4. IoC 注入可见性符合规范（`@Inject protected`，无 private 注入）
5. 错误消息为英文 ErrorCode 体系（`TaskErrors`/`TaskExtErrors`），无裸 RuntimeException
6. 无硬编码密钥/凭据
