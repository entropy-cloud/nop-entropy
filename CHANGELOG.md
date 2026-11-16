# 更新日志

## 修复 2026-11-16
* 修复 nop-ai-core 流式桥接首包静默丢失——SubmissionPublisher 的 onSubscribe 经 executor 异步投递，`subscribe()` 同步注册后与 onSubscribe 落地之间的窗口内 offer 元素被 best-effort 语义静默丢弃 (commit: 本次)
  - 满载下流式首包全部丢失，gRPC/WS/消息/文件入口的流式请求 100% 返回零交付（单元测试实测 `texts=[] completed=true`）
  - 修复：新增 `BoundedStreamPublisher`（同步 onSubscribe + 自有 FIFO 缓冲 + 受管 executor 异步 demand 门控投递），`ChatServiceImpl` 换用；缓冲满 onDrop fail-loud、异常终态立即交付、成功终态等缓冲清空等既有契约保持
* 修复 GraphQL 五入口（gRPC/quarkus WS/spring WS/消息/文件）自建 context 时任何 `executeRpcAsync`/`executeGraphQLAsync` 抛 `nop.err.api.context-already-closed` 替换业务响应 (commit: 本次)
  - `newRpcContext` 自建分支未绑定 service context，`invokeWithBindingCtx` 的 restoreCtx 落在请求终态关闭钩子之后抛错；修复 = 自建 context 创建即 `bindToContext`（无可回退绑定）
  - HTTP 入口（复用分支）不受影响；回归钉 2 条（自建 context 上 RPC 成功路/失败路）

## 修复 2026-10-18
* 修复 MDX 查询（`io.nop.orm.mdx`）主子表维度对齐的静默失效与结果污染 (commit: c58753ea78)
  - **行为变更**：显式 JOIN 未声明 `dimFields` 时不再抛 `nop.err.orm.dim-fields-mismatch`；主/子表字段 label 冲突、`expression`/`formula`/`aggregates` 由静默忽略改为抛异常（`nop.err.orm.query-duplicate-field-label` / `nop.err.orm.query-unsupported-field-attr`）；`distinct` 恢复生效后，依赖 `SELECT DISTINCT` 的查询结果行数会变化（去重正确）
  - **显式 JOIN 全废**：`getSubQuery` 算出的 `dimFields` 从不回写 bean，`dimFieldsMatch` 拿 `null` 比对主表维度必抛 mismatch；同时 `QuerySourceBean.conditions` 全仓零消费者，`leftJoin/rightJoin/innerJoin` 声明的关联键被整体丢弃——现按主表维度顺序取配对 `rightField`
  - **主查询语义被静默削弱**：`getMainQuery` 只复制 14 个属性中的 7 个，`distinct`/`leftJoinProps`/`disableLogicalDelete`/`name` 全丢。`SELECT DISTINCT o.deptName … left join o.parent` 退化为 `SELECT o.deptName,o.deptId`；`SysDaoNamingService.getServices()` 因此返回重复服务名
  - **子表数据静默丢失**（多列关联）：关联属性子表维度沿用 `rel.getJoin()` 顺序（目标实体主键序），与主表（本实体主键序）不一致时位置键对不上，仅打日志不抛异常；现按主表下标逐位定位
  - **结果污染**：主/子表字段写入同一 `Map` 而 `QueryFieldBean.getLabel()` 不含 owner，同名字段由子表值静默覆盖主表值——现直接报错（给任一侧加 `alias` 即可消歧）
  - **重复维度被吞**：主表不加 `GROUP BY` 时多行可共享同一维度键，`dimIndex.put()` 覆盖导致子表值只回填最后一行；改为多值映射回填所有匹配行
  - **NULL 维度污染 SQL**：维度键曾把 NULL 归一为空串并塞进 `IN`（数值列报 `invalid input syntax`），而复合维度的 `eq(col,"")` 又被改写成 `is null`，同一语义两条分支行为相反；现保留 `null` 并统一用 `isNull`，`IN` 列表按 `nop.core.sql.in-list-max-size` 分片
  - **调用方对象被改写**：拆分不再共享 `groupBy` 列表、不回写 `sourceName`、`findFirst` 先 `cloneInstance()` 再设 limit；`query.fields` 为 null 时不再 NPE

## 特性 2026-10-13
* EQL 新增中立语法 `upsert` 语句与 `INSERT/DELETE ... RETURNING`，方言差异下沉 SQL 生成层（plan 2380）(commits: 14527e2da0, b9358d66f6)
  - `upsert into T (…) values (…) on conflict (…) do update set …`：取 PG 家族习惯形态；方言能力位 `supportUpsert` 门控，取值限定 PostgreSQL 族 / MySQL·MariaDB 族 / H2 族，其余方言编译期响亮报错，不做静默降级
  - `INSERT/DELETE ... RETURNING` 补齐（与既有 `UPDATE ... RETURNING` 并列），新增独立能力位 `supportReturningForInsert`/`supportReturningForDelete`，既有 `supportReturningForUpdate` 零改动；`returning *` 编译期展开为实体全部列投影
  - **v1 边界**：不提供 `excluded` 伪表——PG 惯用 `DO UPDATE SET c = EXCLUDED.c` 在 EQL 里写成"同一新值再写一次"（参数为位置标记 `?`、按出现次数独立绑定，values 与 do update set 各占一个）；`upsert … select …` 与 `insert … select … returning` 不支持，编译期报 `nop.err.eql.*`（含新错误码 `insert-select-returning-not-supported`）
  - 各方言参数位数不同：PG/MySQL = `values` 位 + `do update set` 位；H2 仅 `values` 位（`MERGE … KEY … VALUES` 无更新子句）。详见 `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` upsert/returning 小节

## 修复 2026-10-13
* 修复 nop-stream-runtime checkpoint 重试账本 remove-then-put 瞬时窗口，abort 门控可能提前删除终端标记 (commit: 9c72018af9)
  - `CheckpointCoordinator.retryFailedCommits` 的 else 分支先 `remove` 再 `put`，两步之间仍在重试的 epoch 从账本瞬时消失；abort 路径的 `containsKey` 门控在该窗口内会把有失败者误判为无失败者、提前删除终端标记——恢复重放将默认 success=true，破坏 exactly-once 语义
  - 修复：改为 `ConcurrentSkipListMap` 原位 `put`（替换 value 不摘条目），窗口消除；行为面无其他变化
* 修复 nop-ai-gateway 并发首派发下桥消费者重复订阅导致消息重复消费 (commit: 3db616f46d)
  - `ChannelMessageServiceImpl` 的订阅检查与注册非原子（check-then-act），本地广播总线不按消费者身份去重，并发首派发下同一桥消费者可被注册 N 次
  - 修复：实例级 synchronized + 双检串行化首订阅；单线程行为不变
* 修复 nop-code Java 调用图按限定名整层丢失——被索引模块自身类型解析失败无兜底 (commit: 76db42b33f)
  - `JavaFileAnalyzer` 的 TypeSolver 只含反射求解器，被索引项目自身的类型 `resolve()` 全部失败，`calleeQualifiedName` 恒为 null 而下游调用图只认该字段—— outgoing 调用边整层丢失
  - 修复：分析结束后新增同类隐式调用后置归属（无 scope 且 caller 所在类型声明同名方法时归属 enclosing 类型）；静态导入/继承方法不误归属
* 修复 nop-quarkus-web 无 Host 头请求（HTTP/1.0 客户端）`getHost()` NPE (commit: 5ad84f7f82)
  - 根因：Quarkus 4/Vert.x 5 迁移（plan 2431 WI5）把 `getHost()` 从 `request().host()` 改为 `request().authority().host()`，但 Vert.x 5 的 `authority()` 在请求无 Host 头时返回 null（合法状态），直接解引用抛 NPE；该适配器服务所有 Quarkus 入口的 `IHttpServerContext` 消费方
  - 修复：`VertxHttpServerContext.getHost()` 对 `authority()` 判空，返回 null（语义：host 未知，非失败）；唯一生产调用方 `AuthHttpServerFilter` 经 `StringHelper.replace` null 安全
  - 兼容性：仅消除异常路径，有 Host 头的正常请求返回值不变（Host 头原串去端口后的 authority host）

## 变更 2026-09-20
* **行为变更**: nop-biz `CrudBizModel` 批量与 by-query 变更入口增加资源上限，消除静默截断（plan 2269）
  - `deleteByQuery`/`updateByQuery`（@BizMutation）前置计数检查：命中数超过有效 limit（显式 limit 与 maxPageSize 归一化的较小者）时抛 `nop.err.biz.by-query-exceeds-limit`，且在任何变更发生之前失败（旧行为：命中 maxPageSize 封顶后仅 LOG.warn、部分执行并返回误导性的已处理数）
  - 批量集合入参上限：`batchGet`/`batchUpdate`/`batchDelete`（ids）、`batchModify`（data 与 delIds）、`add/remove/updateManyToManyRelations`（relValues）超过上限抛 `nop.err.biz.batch-size-exceeds-limit`；新配置 `nop.biz.max-batch-size`（缺省 500），xmeta `ext:maxBatchSize` 可按对象抬高（仅允许抬高）
  - `asDict` 字典表行数超过 maxPageSize 时抛 `nop.err.biz.dict-options-exceeds-limit`（旧行为：静默返回不完整字典选项）
  - **后台不受影响**：`doDeleteByQuery`/`doUpdateByQuery` 行为不变（后台逃生通道）；新增 `doBatchGet`（@BizAction）承载 `batchGet` 原实现，树查询内部路径（`findListForTree`/`findPageForTree`）改走 `doBatchGet`，不受批量上限约束
  - **迁移指南**：依赖"超限静默截断"旧行为的前端调用将开始收到明确错误；按错误提示缩小过滤条件或分批操作；服务端内部大批量操作改用 `do*` 方法或 nop-batch。注意三点：① 此前"显式传小 limit 循环调用 deleteByQuery 分批清空"的模式已失效（超限按命中总数判定，与传入 limit 无关），分批须按业务键分段过滤或走服务端通道；② `deleteByQuery`/`updateByQuery` 每次调用多一次 COUNT 计数查询（命中数恰好等于 limit 时完整执行，但会记一条 result-truncated warn，属已知噪音未截断）；③ 直接构造 `CrudBizModel` 子类做单测的应用现在需要注册 BizObject（`getMaxBatchSize()` 依赖 `getThisObj()`），参照 `TestCrudBizModelWriteLimits` 的 fixture 写法

## 特性 2026-08-26
* nop-autotest-core `TestClock` 新增锚定仿真毫秒线（faketime 模型）(commit: 47f76f135c)
  - 新增公共 API：`installAnchor(long)` / `clearAnchor()` / `isAnchorActive()`；锚定后毫秒线目标 = `anchorMillis + (真实now − 安装时墙钟)`，时钟拨回指定时刻并随真实时间自然前进
  - 单调性契约不变：毫秒线严格递增永不重复；`currentDate()/currentDateTime()` 由同一毫秒线派生，日期与时间戳单源一致
  - 注意：暖实例不回拨（lastTime 高水位继续直至墙钟追平）；锚点为类级静态、跨测试类持久，消费方须在 `@AfterEach/@AfterAll` 调 `clearAnchor()`
  - 兼容性: 不安装锚点时代码路径与原实现逐字节等价，零默认行为变化

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