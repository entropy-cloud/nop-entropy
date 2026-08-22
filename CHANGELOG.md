# 更新日志

## 变更 2026-08-23
* **破坏性变更**: nop-plugin 公共契约从"定义级 + 实例级"两层状态机收缩为单层六态状态机（一个定义至多一个激活）(commit: 617a80d881)
  - 删除 `IPluginInstance`、`InstanceState` 及实例机制 API 面：`IPluginManager.createInstance/destroyInstance/getInstance/getInstances`、`IPlugin.getInstance/getInstances`；`IPluginContext` 重写为插件级契约
  - `PluginState` 收敛为单组六态枚举 `UNLOADED/LOADED/ACTIVATING/ACTIVATED/DEACTIVATING/FAILED`，定义（load/unload）与激活（activate/deactivate）在同一对象演进；实例生命周期实现 `PluginInstanceImpl` 并入 `VfsPluginDefinition`
  - `IPlugin` 直接承载 `activate()/deactivate()/getService()/getState()`；`IPluginManager` 新增 `activatePlugin/deactivatePlugin(pluginId)`；兼容接口收敛为 `start = load + activate`、`stop = deactivate + unload`
  - **迁移指南**: 原 `createInstance(key, config, parent)` 改用 `activatePlugin(pluginId)`（配置经定义级通道传播，不再有实例 key / parent 层级）；原实例对象操作改用 `IPlugin.activate()/deactivate()/getService()`
  - 兼容性: 仓内核验无外部消费者（nop-quarkus-demo 仅引用 `IPluginCommand/IPluginCancelToken`，不在被删面上）；存量非 aware 第三方插件走兼容路径零感知

## 修复 2026-08-20
* 修复 nop-ioc 并发初始化同一 bean 时的死锁（ctx↔P 锁序反转）(commit: 428a196557)
  - 根因：`ProducedBeanInstance.runUntil` 持 monitor 执行 init 回调、`BeanCreationContext.flushInit` 持 ctx monitor 执行 action，跨线程形成循环等待（2026-08-20 现场在 nop-metadata-service 测试 fork 中表现为永久挂起）
  - 修复：`runUntil` 重构为 owner 线程 + wait/notify 状态机（新增 `PROPERTY_SET` 中间状态），回调在锁外执行；`BeanCreationContext` 改锁内快照 + 锁外执行；`getBean0` 的 beanDef 锁收缩到 scope 读取/创建/注册，属性赋值与动作登记移到锁外
  - 行为契约不变：bean 初始化完成前对容器可见（循环依赖/自引用）、属性先于 init、init 只执行一次、`getBean(name,false)` 返回完整初始化结果、异常完整传播
  - 新增确定性并发回归测试 `TestBeanConcurrentInitDeadlock`：旧代码必死锁（30s 超时复现），新代码通过（全模块 55 tests 零回归）

## 特性 2026-08-16
* nop-credential 新增 OAuth 流程引擎（W9，`authType=oauth2` 出站 OAuth 2.0 客户端）(commits: 385aa44ea, e18a87982)
  - `credential-type.xdef` `authType` 收敛为枚举 `none|apiKey|basic|oauth2`；oauth2 类型声明 `<oauth2>` 元数据（authorizationEndpoint/tokenEndpoint 必填，scopes/refreshWindowSeconds 可选），registry 加载期校验 fail-closed
  - 引擎保留字段契约：`accessToken`/`refreshToken`/`expiresAt`/`tokenType`/`scope` 归引擎独占，类型文件占用与 `saveCredential` 输入均拒绝
  - 授权码闭环：`CredentialOAuthApi__beginOAuthFlow`（登录态）→ 单一公开回调 `GET /r/CredentialOAuthApi__oauthCallback`（publicAccess，返回 HTML 跳转页，不携带 token 明文）→ token 集加密回写
  - 取用时惰性刷新：accessToken 临期自动以 refreshToken 刷新（DB 行级锁跨副本互斥，并发刷新收敛为一次），失败 fail-closed
  - 新表 `nop_credential_oauth_state`（state 一次性消费 + TTL；存量部署执行 `deploy/sql/{mysql,oracle,postgresql}/_add_oauth_state_nop-credential.sql`）
  - 新配置项 `nop.credential.oauth.*`：`callback-base-url`（必配）/ `result-page-url` / `state-ttl-seconds`（600）/ `refresh-window-seconds`（300）
  - oauth2 类型 `status=disabled` 发起/回调/刷新/取用全路径拒绝；非 OAuth 类型语义不变

## 特性 2026-08-01
* flux-web 页面级 tabs 修复：输出字段由 `tabs` 改为 `items`（Flux `TabsSchema` 契约），tab 项补 `key` ← name，修复 Flux 下 tab 内容 silent no-op (commit: 95e9cbcca)
* flux-web 新增 tab/step 内嵌 body 容器渲染：内容优先级 `page` > `body` > `name` 兜底，共享分派标签 `GenContainerModel` 支持 crud/simple/tabs/wizard/group 五类容器 (commit: 95e9cbcca)
* flux-web 新增 wizard 页面类型（Flux `WizardSchema`）：steps 数组、step 内容优先级 page/body/name、mode/action*Label 透传 (commit: 95e9cbcca)
* flux-web 新增 group 页面类型（Flux `GridSchema`）：columns/gap/autoFlow 映射、alignItems/justifyItems 过滤 normal/baseline、子容器 colSpan/rowSpan 透传 (commit: 95e9cbcca)
* form 级 `layoutControl="tabs"` 修复：tab 内容移入 `items[].body` 并补 `key`，修复 Flux 下 form tabs 内容 silent no-op (commit: 95e9cbcca)

## 变更 2026-07-21
* **破坏性变更**: nop-search-lucene 的错误码 `nop.err.lucene.vector-search-not-implemented` 重命名为 `nop.err.lucene.invalid-query-vector`，并删除死代码常量 `ERR_LUCENE_HYBRID_SEARCH_NOT_IMPLEMENTED`
  - 旧错误码字面意义（"向量搜索未实现"）与实际行为不符——`vectorSearch()` 已基于 Lucene `KnnFloatVectorQuery` 实现真正的 kNN 搜索，错误码只在 query vector 解析为空/无效时抛出
  - 同时删除 `nop.err.lucene.hybrid-search-not-implemented`：该常量在仓内从未被 throw（`hybridSearch()` 在 query vector 为空时静默降级为纯文本搜索），属于死代码
  - **迁移指南**: 若有下游按字符串匹配 `nop.err.lucene.vector-search-not-implemented`，需更新为 `nop.err.lucene.invalid-query-vector`。Java 引用方使用常量 `ERR_LUCENE_INVALID_QUERY_VECTOR`
  - 兼容性: 错误码字符串变化，但抛出条件不变（仍是 query vector 为空/无效时触发）

## 变更 2026-07-13
* **破坏性变更**: nop-job 的 `scheduler.yaml` 每个 job 必须显式启用，job 定义改为按文件扫描 (commits: 即将提交)
  - `LocalJobConfig` 新增 `enabled` 字段，**Java 默认值为 `false`**
  - `LocalJobConfigLoader` 加载时跳过 `enabled != true` 的 job（记 INFO 日志 `nop.job.local-config-loader.job-disabled`），不注册到 scheduler
  - **job 定义改为按文件扫描**：`/nop/job/conf/<jobName>.job.yaml`，每个文件 = 一个 job。`scheduler.yaml` 仅保留全局 `enabled` 开关。`LocalJobConfigLoader.init()` 同时支持旧机制（`scheduler.yaml.jobs` 内联）和新机制（目录扫描），同名 job 第二次出现时记 WARN 跳过
  - 平台内置 job 全部迁移到 `.job.yaml` 形式：`sys-event-batch-consumer`、`wf-due-task-scan`、`wf-remind-task-scan`
  - 每个 job 的 `enabled` 与 `trigger.cronExpr` 统一用 `@cfg:nop.job.<jobName>.<field>|<default>` 绑定到独立配置项，缺省值在 `|` 后给出
  - **迁移指南**: 升级后所有内置 job 默认不运行。应用层必须在 `application.yaml` 显式添加：
    ```yaml
    nop:
      job:
        sys-event-batch-consumer:
          enabled: true   # jobName 对应的配置 key
        wf-due-task-scan:
          enabled: true
    ```
  - 兼容性: 自定义 `scheduler.yaml` 中未写 `enabled` 字段的 job 也将默认不启用；如需保留旧行为（自动启用），显式写 `enabled: true`。旧的 `scheduler.yaml.jobs` 内联写法仍可工作但不推荐，新加 job 一律走 `.job.yaml`

## 变更 2026-03-31
* **数据库方言与迁移能力统一**: `IJdbcTemplate` 新增对象存在性 API，并下沉到 dialect 模板驱动 (commits: c11b5dff1, 279e02f1b, 85a59606a, b7969c80d)
  - 新增接口: `existsTable(querySpace, schemaName, tableName)`, `existsColumn`, `existsIndex`, `existsForeignKey`, `existsSequence`, `existsView`
  - 方言扩展: `dialect.xdef` 与主流 `*.dialect.xml` 增加 `tableExists/columnExists/indexExists/foreignKeyExists/sequenceExists/viewExists` 模板
  - 调用迁移: `nop-db-migration` precondition checker 改为统一调用 `IJdbcTemplate` 新 API，不再在 main 路径手写 `INFORMATION_SCHEMA` exists SQL
  - 兼容性: 旧接口 `existsTable(querySpace, tableName)` 保持可用，内部委托到新重载（`schemaName=null`）

## 变更 2026-03-04
* **数据库变更**: nop-sys模块VERSION字段从INTEGER升级为BIGINT (commit: af4dbaf54)
  - 迁移指南: 执行ALTER TABLE修改字段类型，代码中version从Integer改为Long
  - 兼容性: 数据完全向后兼容，但JDBC返回类型变为Long

## 特性 2026-03-04
* 新增nop.cluster.name配置项，支持物理机房隔离 (commit: 19b1f98b0)
* 新增配置表达式解析器，支持${...}语法引用其他配置 (commit: 5bb9ffbcb)
* 新增路由**通配符语法糖，自动转换为{*path} (commit: d73f6ead7)
* 增强EQL集合操作符，支持AND/OR条件组合 (commit: 2ddbeee37)


# 更新日志

## 特性 2023-11-2
* 增加启用租户机制的全局开关nop.orm.enable-tenant-by-default
* 增加NopSpringTransactionFactory，继承Spring的事务管理

## 变更 2023-08-19
* 重构项目结构，将meta文件保存到独立的meta模块中。已经生成的代码需要按照NopMigrateTask中的做法进行迁移