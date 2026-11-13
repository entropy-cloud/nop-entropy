# nop-metadata 在线 API 层 Roadmap（nop-metadata-open）

> Last updated: 2026-10-27（初版：设计定稿 `ai-dev/design/nop-metadata/15-online-api-layer-design.md`，WI0-WI8 全部 todo）
> Sources: `ai-dev/design/nop-metadata/15-online-api-layer-design.md`（设计权威）、`ai-dev/design/nop-metadata/14-entity-methods-design.md`（§四 动态注册再评估触发条件的承接）、`docs-for-ai/03-modules/nop-metadata.md`（M1-M4 既有能力契约）
> 书写约定：未来交付物路径用普通文本书写、不加反引号；已存在的文档路径用反引号，持续受 check-doc-links 保护。

## Purpose

本 roadmap 编排 nop-metadata 在线 API 层（新 Maven 子模块 nop-metadata-open）的实现：API 定义存数据库、发布即生效（免重启）、输出类型声明与 Map 实现分离、无实体服务函数、权限发布时自动登记到 nop-auth。

**范围硬边界**（继承设计 §四 拒绝清单，改动须先改设计文档）：

- **首版只读**：functionKind=mutation 执行器显式拒绝；不写外部库、不做 webhook 出站
- **不做 xpl/java 实现**：任意代码执行面不进入数据驱动的 API 模型
- **不做动态 ORM / 外部源 querySpace 化**：类型化经 xbiz return 声明承载，动态注册复用 nop-dyn 机制但不复用其 ORM 路径
- **不建平行权限系统**：权限资源登记到 NopAuthResource，校验走 nop-auth 既有链
- **不触碰 nop-metadata.orm.xml**（Protected Area）：新表放 nop-metadata-open 自带 orm，跨模块引用一律软引用
- 多版本并存路由、租户化、调用成功审计属 follow-up，不进本 roadmap 工作项

不含实现细节。每个 todo WI 由其执行计划承载（`ai-dev/plans/00-plan-authoring-and-execution-guide.md`）。

## Work Item Status

> **全文件唯一的动态状态区。更新状态只改这里。**
> 状态流转：todo（无计划）→ planned（已有通过 draft review 的 plan）→ done（独立 closure audit 通过）。WI 编号全文件递增不重排；deps 是唯一并行屏障。

### M1 — 基础发布线（定义 → 发布 → 泛化访问 → 权限登记）

- [ ] WI0 类型声明与动态注册机制 spike：验证 xbiz `<return>` 内嵌 `<schema>`/graphql:type 声明输出类型 + 运行时 List<Map> 承载下 GraphQL 字段级选择端到端可用（锚点：BizModelToGraphQLDefinition.java:328-348 三级解析、xbiz.xdef:64-67、TypeRegistry 命名类型）；验证不依赖 nop-dyn 模块、独立复用其注册链路（内存 VFS 层生成 xbiz + bizObjectManager 缓存清除 + introspection 即时包含）的最小代码路径；产出 spike 报告落 ai-dev/analysis/2026-10/，推翻设计假设时须先改设计 §3.3/§3.5（Deliverable: spike 报告 + 可运行 proof 代码；deps: 无；Item Type: Decision + Proof）
- [ ] WI1 定义模型落地：nop-metadata-open 模块骨架（pom + 自带 orm + codegen 接线）；NopMetaApiObject / NopMetaApiFunction / NopMetaApiFunctionArg / NopMetaApiExposure 四表模型与 dict（meta/api-status、meta/api-function-kind、meta/api-impl-kind、meta/api-expose-kind、meta/api-source）；BizModel + save 守卫（apiObjectName 标识符与保留前缀校验、functionName 保留名单复用、sql 类必绑 ACTIVE 数据源、mutation 首版拒绝、sourceKind 注入）+ I*Biz 接口 + CRUD 管理页面（Deliverable: 模块 + 模型 + 页面 + 测试；deps: WI0；Item Type: Feature）
- [ ] WI2 执行器 MetaApiInvoker：sql implKind（MetaMethodSqlValidator 同源沙箱三道水位：save/publish/query、参数绑定顺序契约沿 M3、绑定数据源 withConnection 分派）；args 校验（missing/unknown/default）与行级 roles 检查复用 M3 实现；泛化兜底入口 NopMetaApiObject__invokeApi；错误面不泄漏契约（param 只带 apiObjectName/functionName/类别，日志 sqlHash）（Deliverable: 执行器 + 泛化入口 + 单元/e2e 测试；deps: WI1；Item Type: Feature）
- [ ] WI3 发布状态机：发布快照组装（自包含 JSON：成员定义 + 派生类型 + 权限点清单 + 版本）；publish 五步序（校验→快照→权限登记→注册→单行事务提交）与 unpublish；per-API 发布锁（并发发布 fail-fast）；失败不改变现网（1-4 步无副作用 + 第 5 步原子提交）；NopMetaModelChangedEvent 扩展 changeSource=api-publish/api-offline（Deliverable: publish/unpublish action + 事件 + 故障路径测试；deps: WI2；Item Type: Feature）
- [ ] WI6 权限接线：发布时 NopAuthResource 功能点登记器（权限串 apiObjectName:query|apiObjectName__functionName 形态）+ unpublish 停用；执行面 roles 复查接线；部署侧文档（enable-action-auth 开启、权限点清单消费方式、nop-auth-web 复用说明）落 docs-for-ai（Deliverable: 登记器 + 部署文档 + 测试；deps: WI3（可与 WI4/WI5 并行）；Item Type: Feature + Doc）

### M2 — 类型化暴露线（catalog-query + 薄壳 schema）

- [ ] WI4 catalog-query implKind：querySpec 结构化定义（TreeBean 过滤 + joinId + measures/dimensions 选择）+ 参数按名绑定进 filter + 经 queryJoinData/queryAggregation 既有执行器取数（继承 AR-09 limit 契约与跨库裁定）（Deliverable: implKind 执行 + 测试；deps: WI2；Item Type: Feature）
- [ ] WI5 类型派生与薄壳暴露：输出字段派生（sql 复用 SqlViewFieldTypeInferrer 思路 LIMIT 0 实测推断；catalog-query 结构化派生；outputFields 显式声明优先；推断失败显式发布失败）；薄壳 xbiz 生成（xgen 模板，单行委托 MetaApiInvoker）+ 注册 + 缓存清除 + bizObj 名冲突校验；暴露清单入口（std-crud-ro 合成只读面 / methods 实体方法暴露）；introspection 端到端测试（发布即出现、下线即消失、字段级选择可用）（Deliverable: 派生器 + 模板 + e2e；deps: WI3；Item Type: Feature）
- [ ] WI8 硬化与回归：全部故障路径显式化核查（无静默跳过）；错误码 ARG_* 声明面一致性门禁接入模块不变式脚本；审计事件面核查；docs-for-ai 新模块 owner doc（03-modules/nop-metadata-open.md 或并入 nop-metadata.md 章节的裁定）+ INDEX/source-anchors 更新；全量回归（Deliverable: 门禁 + 文档 + 回归证据；deps: WI4、WI5、WI6、WI7；Item Type: Quality + Doc）

### M3 — 表达力线

- [ ] WI7 composite implKind：步骤编排（每步 sql/catalog-query/expression）+ 步骤变量命名传递 + 发布时 DAG 无环与引用闭合校验 + expression 步 ExprFeatures.SIMPLE 特性集 + 内存边界沿跨库合并上限配置；每步独立沙箱（Deliverable: 编排执行 + 校验 + 测试；deps: WI2（建议在 M2 完成后启动，避免与类型化暴露线并行争用执行器改造）；Item Type: Feature）

## Cross-Cutting 不变式

- **无静默跳过**：发布校验失败、类型推断失败、引用解析失败全部显式报错，不降级不伪造（错误码前缀 nop.err.metadata.open.*，归入 NopMetadataErrors 聚合或新模块 Errors 文件，ARG_* 声明面一致性受既有门禁约束）
- **现网稳定**：publish 失败不改变已发布契约；unpublish 后快照保留可审计
- **沙箱单点**：SQL 校验只允许复用 MetaMethodSqlValidator 单点实现，禁止复制黑名单
- **Protected Area**：nop-metadata.orm.xml 零改动；跨模块引用一律软引用（checkpointId 先例）
- **测试纪律**：每个 WI 交付单元 + e2e（Nop AutoTest，沿 nop-metadata 既有测试基建）；WI5 的 introspection 端到端是 M2 出口判据
- **Reconcile-on-touch**：实现落地时同步修订设计文档与 docs-for-ai owner doc；roadmap 只维护本文件状态区
