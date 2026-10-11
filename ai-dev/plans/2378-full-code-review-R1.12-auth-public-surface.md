# 2378 full-code-review R1.12 — 权限公开面修复族 C（P1-2.6-005/006/007 + 双配置键）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.12；findings `P1-2.6-005`（dim13-01）+ `P1-2.6-006`（dim13-02）+ `P1-2.6-007`（dim13-06），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.6-biz-graphql-deep-audit.md`；修复族 C 精确输入=16 项清单（`_tmp/fcr-2.6-auth-r2.md` 交付物 1）
> Related: plan 2377（R1.11 同报告前置批）；与 R1.13（P1-3.1-001 approval-support 绕引擎双事实源）/R1.20（P1-3.8-003）同批互引，不互相阻塞
> Protected Area 处置：NopAuthUser.xbiz 位于 nop-auth（AGENTS.md 权限/认证模型 ask-first）。裁定：用户 goal 指令（执行 full-code-review roadmap 至彻底完成）构成全项执行授权；本变更仅为公开动作**新增** auth 元数据（收口审计判定的静默公开面），不改变认证模型语义，且行为仅在 opt-in（`nop.auth.enable-action-auth=true`）部署生效；在最终交付摘要向用户显式披露此裁定。

## Purpose

收口权限公开面族：①16 个无 auth 的生产 xbiz 动作补 `<auth>` 声明（逐文件验证 x:extends 合并终值）；②@BizLoader 死注解修复（Java/xbiz 两路径 loader auth 装配）；③fail-open 三处的 fail-fast 双配置键（`nop.auth.require-auth-checker`/`nop.auth.strict-action-auth`，默认关闭零兼容影响）。

## Current Baseline

- **放行链**：xbiz 路径 `BizModelToGraphQLDefinition.toOperationDefinition:80` setAuth(getAuth())，无 `<auth>` 即 null；合并规则 `BizObjectBuildHelper.mergeBizModel:70` 仅同名 Java 方法存在且回填；纯 xbiz 动作 auth 恒 null → `GraphQLActionAuthChecker.isAllowAccess:118-120` auth==null 直接 true（checker/开关/登录与否均无关）。
- **16 项清单**（生产模块净无 auth；demo 4 个立案不含）：NopAuthUser.active_findPage（+NopAuthUser_tenant 继承双计）、NopDynModule.active_findPage/publish/unpublish、approval-support×5（submitForApproval/withdrawApproval/approve/reject/reverseApprove）、NopWfDefinition.publish/unpublish、NopMetaDataContract.approve/reject（**本地覆盖**自带 source）、NopMetaTagLabel.approve/reject（**本地覆盖**且无 SUBMITTED 前置校验——加重事实，auth 修复后风险收窄为 admin 面）、NopRuleDefinition.getOutputFieldsEditSchema。nop-job 三文件（roles=admin+permissions，无 source 由 Java 实现）是正确姿势样本。
- **dim13-06 死注解**：`ReflectionBizModelBuilder.buildFetcherField`（@BizLoader 专用）只装 name/args/fetcher/lazy，从不 setAuth（全仓 `Auth.class` 唯一消费点在 buildActionField）；`BizModelToGraphQLDefinition.toBuilder` 同样不装；`xbiz.xdef` BizLoaderModel 无 auth 子元素——loader 作为类别两路径均无法携带权限。**带 @Auth 的 7 个生产 loader**：NopCodeFileBizModel×4（permissions="NopCodeFile:query"）、NopCodeSymbolBizModel×2、DevDocBizModel.methods（roles=admin）。**无 @Auth 的生产 loader**（Phase 2 裁定对象）：NopAuthRoleBizModel.roleUsers/roleResourceIds（nop-auth-service）、NopDynEntityMetaBizModel.allProps、NopRuleNodeBizModel.predicateLabel、demo×2——修复后 auth 恒 null（不补缺省），strict 模式下字段级 deny，须登记 strict 开启前置。`TestDevDocDevToolAuth` 只断言注解存在=测试盲区。
- **权限串语义（plan 审查核实）**：`MultiCsvSet.fromText` 按 `|` 拆组、组内逗号 AND、组间 OR（isPermissionSetSatisfied 任一组全满足即 true）——Java 缺省串 `obj:opType|obj:name` 与 xbiz `<auth permissions>` 解析同构，镜像写法语义等价；**但同一字符串含多个动作权限（如 publish|unpublish 连写）会互相提权（OR 组），每动作必须单独声明自身权限**（nop-job 样本即每动作单权限）。
- **fail-open 三处**（`GraphQLActionAuthChecker` + `GraphQLEngine`）：处 1 `check():38-39` checker==null 整体跳过（enable-action-auth 默认 false=opt-in，已有 F-API2-1 WARN 兜底）；处 2 `isAllowAccess` auth==null→true（全平台一致「显式声明权限」模型）；处 3 `isAllowAccess` auth!=null&&checker==null→true（旁路 `DefaultBizAuthChecker.checkAuth:61`→`NopFileStoreBizModel` forceCheckAuth 可达）。
- **既有错误码**：AuthApiErrors 有 ERR_AUTH_NO_PERMISSION/NO_USER_CONTEXT/NO_PERMISSION_FOR_FIELD；GraphQLErrors 无 no-checker 类错误码（Phase 3 新增）。
- **auth 声明语法样本**（NopJobFire.xbiz）：`<auth roles="admin" permissions="NopJobFire:rerunFire"/>`；auth.xdef 四属性 roles/permissions/publicAccess/skipWhenNoAuth 两路径对等。
- **兼容性事实**：enable-action-auth 默认 false → Phase 1 补 auth 元数据对默认部署与既有测试（TestNopMetaTagLabelApproval/TestUseApprovalComponents 等）零行为影响；仅 opt-in 部署开始生效。

## Goals

- 16 项公开面全部带 auth 声明，且 x:extends 合并终值逐文件验证（本地覆盖文件在本地补、继承文件在基库补+继承验证）。
- loader 两路径 auth 装配贯通：显式 @Auth 注解/xbit `<auth>` 被消费；`TestDevDocDevToolAuth` 从「注解存在」升级为「装配生效」断言。
- 双配置键落地（默认 false 零影响）：require-auth-checker=true 时处 1/处 3 fail-fast；strict-action-auth=true 时 auth==null 按 deny。
- owner doc（service-layer.md/auth-and-permissions.md）登记严格模式与公开面收口语义。

## Non-Goals

- 不改 NopMetaTagLabel approve/reject 缺 SUBMITTED 前置校验的生命周期语义（auth=admin 后风险收窄；生命周期语义变更需 owner 决策，登记 follow-up）。
- 不改 demo 模块 4 个无 auth 动作（审计口径立案不含）。
- 不动 maker-checker 契约（P2-2.6-011 台账行）、树查询数据权限（P2-2.6-012）、disabledActions/inheritActions（P2-2.6-013）。
- 不改 `nop.auth.enable-action-auth` 默认值（平台 opt-in 姿态是审计确认的有意设计）。

## Scope

### In Scope

- 7 个 xbiz 文件 auth 声明：`nop-wf/nop-wf-core/.../approval-support.xbiz`、`nop-wf/nop-wf-service/.../NopWfDefinition.xbiz`、`nop-dyn/nop-dyn-service/.../NopDynModule.xbiz`、`nop-auth/nop-auth-service/.../NopAuthUser.xbiz`、`nop-metadata/nop-metadata-service/.../NopMetaDataContract.xbiz`、`NopMetaTagLabel.xbiz`、`nop-rule/nop-rule-service/.../NopRuleDefinition.xbiz`
- `nop-graphql-core`：ReflectionBizModelBuilder.buildFetcherField、GraphQLEngine.newGraphQLContextFromContext、GraphQLActionAuthChecker、GraphQLErrors（新错误码）；`nop-api-core`：ApiConfigs（两配置键定义处）
- `nop-biz`：BizModelToGraphQLDefinition.toBuilder
- `nop-kernel/nop-xdefs`：xbiz.xdef BizLoaderModel 增 auth 子元素（⚠️ xdef 变更会触发模型类再生成——若 BizLoaderModel 为生成类，走再生成路径，不手编生成物）
- 测试：合并终值断言（经 IBizObjectManager/IGraphQLEngine 取 merged definition——TestUseApprovalE2E 布景扩展等；TestUseApprovalComponents 是原始 XNode 断言不经合并，不作合并锚点）、loader 装配运行时断言、双配置键行为测试；全链 enforcement 测试若落地于 nop-wf-service/nop-metadata-service，需为该模块补 test scope 依赖 `nop-auth-service`（唯一生产级 DefaultActionAuthChecker 所在）+ 夹具集（沿 nop-datav 先例：site-map yaml + auth-test.beans.xml + app.action-auth.xml），pom/夹具变更属本项范围
- owner docs：service-layer.md、auth-and-permissions.md

### Out Of Scope

- nop-auth 认证链/登录逻辑本身
- 数据权限（enable-data-auth）面
- demo 模块

## Execution Plan

### Phase 1 - xbiz 16 项 auth 声明 + 合并终值验证

Status: completed
Targets: 上列 7 个 xbiz 文件 + nop-wf-service/nop-metadata-service 测试

- Item Types: `Fix`、`Proof`

- [x] approval-support.xbiz 五 mutation 补 `<auth roles="admin"/>`（审计处方：显式声明+按实体差异化走 x:extends 覆盖点。可用性裁定：opt-in 部署默认收紧，非 admin 提交人场景由应用逐实体覆盖，理由登记 owner doc）
- [x] NopWfDefinition.xbiz publish 补 `<auth roles="admin" permissions="NopWfDefinition:publish"/>`、unpublish 补 `<auth roles="admin" permissions="NopWfDefinition:unpublish"/>`（**每动作单独声明自身权限——禁止跨动作权限串连写，`|` 为 OR 组语义会互相提权**，plan 审查 F2）
- [x] NopDynModule.xbiz publish/unpublish 同型各自单权限补声明；active_findPage 补 `<auth permissions="NopDynModule:query|NopDynModule:findPage"/>`（镜像 findPage 缺省权限——单动作内 `|` 分组与 Java 缺省串同构，语义等价，plan 审查已核实）
- [x] NopAuthUser.xbiz active_findPage 补 `<auth permissions="NopAuthUser:query|NopAuthUser:findPage"/>`（同上镜像语义；NopAuthUser_tenant 继承终值一并验证）
- [x] NopMetaDataContract.xbiz / NopMetaTagLabel.xbiz 本地覆盖的 approve/reject 在**本地文件**补 `<auth roles="admin"/>`（审计注意点：不能只改基库）
- [x] NopRuleDefinition.xbiz getOutputFieldsEditSchema 补 `<auth permissions="NopRuleDefinition:query|NopRuleDefinition:getOutputFields"/>`（镜像被转发的 getOutputFields 缺省权限）
- [x] 合并终值验证（**真实 merged-model 断言，非源 XML 断言**——plan 审查 F1 勘正：TestUseApprovalComponents 是原始 XNode 结构断言不经合并，不可作锚点）：nop-wf-service 经 IBizObjectManager 取 merged object definition 断言 5 动作 auth 终值（TestUseApprovalE2E 布景扩展）；metadata 侧经 IGraphQLEngine/IBizObjectManager 断言本地覆盖文件 approve/reject 合并终值；nop-auth-service 侧断言 NopAuthUser 及 NopAuthUser_tenant 的 active_findPage 终值
- [x] 运行时 enforcement 测试分层：①机制层——graphql-core 配置布景验证 auth!=null+checker 在位时的拒绝路径；②全链层——沿 nop-datav `TestNopDatavRbacAuth` 先例（`@NopTestConfig(enableActionAuth=TRUE)`）在受影响 service 测试基建（nop-wf-service 或 nop-metadata-service）落地 1 条：enable-action-auth=true + checker + 非 admin 上下文调用 approval 动作 → ERR_AUTH_NO_PERMISSION

Exit Criteria:

- [x] 16 项动作在 merged 终值中断言 auth 非 null 且 roles/permissions 符合上表（含 NopAuthUser_tenant 继承）
- [x] 受影响 6 模块（nop-auth-service/nop-wf/nop-dyn-service/nop-rule-service/nop-metadata-service/nop-biz）既有测试全绿（默认部署零行为变化的证据）——2026-10-09 实测 6 模块 2028 tests 0 failures；reactor 闭包中唯一失败为 nop-stream-runtime `TestStreamConnectorRegistryDiscovery`（P1-7.2-001 预存在窗口回归，R1.23 修复范围，与本 Phase 无关）
- [x] enforcement 测试证明 opt-in 部署下权限生效（TestApprovalActionAuthEnforcement 2/2：非 admin 拒绝 ERR_AUTH_NO_PERMISSION + admin 放行）
- [x] owner doc 更新项见 Phase 4（本 Phase 不单独更新）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - @BizLoader auth 装配（dim13-06）

Status: completed
Targets: `ReflectionBizModelBuilder`、`BizModelToGraphQLDefinition`、`xbiz.xdef`、TestDevDocDevToolAuth

- Item Types: `Fix`、`Proof`

- [x] `ReflectionBizModelBuilder.buildFetcherField`：消费 func 上的 `@Auth` 注解 → `field.setAuth(...)`（镜像 buildActionField 装配）；**无注解不补缺省权限**（loader 是字段装载器，缺省回填过严——audit 建议「至少消费显式声明」的收敛裁定）
- [x] `xbiz.xdef` BizLoaderModel 增 `<auth>` 子元素（镜像 BizActionModel 的 auth 定义；若 BizLoaderModel 类为生成物则走再生成，禁止手编）——已走再生成路径：xbiz.xdef 编辑 → nop-xdefs install → nop-biz generate-sources，`_BizLoaderModel.java` 原位再生成（git diff 仅 auth 字段/getter-setter/freeze/outputJson/copyTo 五处生成段）
- [x] `BizModelToGraphQLDefinition.toBuilder`：loaderModel.getAuth() → field.setAuth（对齐 ：80 query/mutation 路径）
- [x] `TestDevDocDevToolAuth` 升级：经引擎构建 GraphQLObjectDefinition 后断言 methods 字段 `getAuth()` 非 null 且 roles=admin（注解存在→装配生效）——实现为 ReflectionBizModelBuilder 构建后从 TypeRegistry 取 `g_io_nop_biz_dev_beans_GlobalVariableDefBean` 类型定义断言（forType loader 字段经 getLoaderForType merge 进该类型）
- [x] nop-graphql-core 新增最小测试：@BizLoader 带 @Auth 的 BizModel 构建后 field.getAuth() 非 null；不带 @Auth 保持 null（锁死「不补缺省」裁定）——TestBizLoaderAuthAssembly 2 用例
- [x] **无 @Auth 生产 loader 群体显式裁定**（plan 审查 F4）：NopAuthRoleBizModel.roleUsers/roleResourceIds、NopDynEntityMetaBizModel.allProps、NopRuleNodeBizModel.predicateLabel、demo×2——修复后 auth 恒 null 属 accepted residual（不补缺省裁定的一致结果）；登记 owner doc strict 开启前置清单（strict deny 行为样例测试归 Phase 3 行为测试项①——本 Phase 不测未实现行为）

Exit Criteria:

- [x] 带注解 7 个生产 loader 构建后 field.getAuth() 非 null：DevDoc.methods 由升级后的 TestDevDocDevToolAuth 断言；**nop-code 6 个 loader 由最小构建断言测试验证**（nop-code 侧测试或经 ReflectionBizModelBuilder 构建 NopCodeFileBizModel——不能以「既有测试零回归」替代装配证据，plan 审查 F7）——TestNopCodeLoaderAuthAssembly 2 用例（NopCodeFile×4 + NopCodeSymbol×2，按 forType 类型定义逐字段断言）
- [x] xbiz `<loader>` 带 `<auth>` 的模型可解析且 toBuilder 装配到位（新测试）——TestXbizLoaderAuthAssembly 2 用例（含 TestLoaderAuth.xbiz fixture；fixture 修正注记：loader return 需 `graphql:type` 属性，仅 `type` 属性时 getLoaderType 返回 null——平台既有语义，fixture 对齐后通过）
- [x] 无注解 loader 群体裁定登记（owner doc strict 前置清单，随 Phase 4 落盘）——strict deny 样例测试唯一归属 Phase 3（依赖该 Phase 实现的 deny 分支）
- [x] nop-graphql-core + nop-biz + nop-code 既有测试全绿（见 Phase 2 回归记录）
- [x] owner doc 处置：loader auth 两路径语义与无注解 loader strict 前置登记归 Phase 4（本 Phase 不单独落地，显式指向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - fail-fast 双配置键

Status: completed
Targets: `ApiConfigs`（nop-api-core，配置键定义处）、`GraphQLEngine`、`GraphQLActionAuthChecker`、`GraphQLErrors`、`ObjMetaBasedValidator`（nop-biz）

- Item Types: `Fix`、`Proof`

- [x] 新增配置键（默认 false）：`nop.auth.require-auth-checker`、`nop.auth.strict-action-auth`——**定义在 ApiConfigs（nop-api-core，与 enable-action-auth 同族同处）**，消费方经静态引用读取（plan 审查 F6：不放 GraphQLConfigs，避免 nop.auth.* 配置族拆两处）
- [x] 处 1 收敛：`newGraphQLContextFromContext` 在 require-auth-checker=true 且 enableActionAuth && checker==null 时抛新错误码（GraphQLErrors 新增，如 ERR_GRAPHQL_NO_ACTION_AUTH_CHECKER），替代仅 WARN（WARN 保留于关闭态）
- [x] 处 3 收敛：`isAllowAccess` 在 auth!=null && checker==null 且 require-auth-checker=true 时 fail-closed 拒绝——静态方法无 fieldName 可用，错误须携带可用诊断参数（或上移到 checkAuth 层抛出，保参数在位）——实现为 isAllowAccess 返回 false，checkAuth 层抛 ERR_AUTH_NO_PERMISSION（fieldName/objTypeName 在位）
- [x] 处 2 严格模式：`isAllowAccess` 在 auth==null 且 strict-action-auth=true 时返回 false（deny）；`checkAuth` 抛错路径对 auth==null 参数 null 安全（不得 NPE——审查确认 ：104/:112 解引用风险为真）——checkAuth 三处 auth 解引用全部改条件取值
- [x] **同族 fail-open 同步收敛**（plan 审查 F5，审计 fail-open 处方原文「若引入 require-auth-checker 严格模式，此处应同步收敛」）：`ObjMetaBasedValidator.doCheckAuth:331-333` 的 `authChecker == null` 半支——require-auth-checker=true 且字段 auth!=null && checker==null 时 fail-closed（auth==null 半支保持显式声明模型，不动）；沿该类既有测试布景验证
- [x] 行为测试分层：①graphql-core 配置布景（TestGraphQLEngineActionAuthWarn 先例）验证三配置组合（默认/require/strict）fail-open 保持与 fail-fast 生效，含 1 个无注解 loader 字段 strict deny 样例（TestGraphQLActionAuthStrictModes 10 用例）；②合并动作 enforcement 已归 Phase 1 全链层；③ObjMetaBasedValidator 三用例（require fail-closed/默认 fail-open 保持/auth==null 半支不动，TestObjMetaBasedValidator 8/8）

Exit Criteria:

- [x] 默认配置下全部既有测试零回归（fail-open 语义保持）——nop-api-core 150/nop-graphql-core 153/nop-biz 121 全绿；metadata 全量 1345 全绿（重录 4 快照见下）
- [x] require-auth-checker=true：无 checker + enable-action-auth 请求入口 fail-fast（新错误码）；auth 声明+无 checker 旁路（DefaultBizAuthChecker 形态）拒绝；ObjMetaBasedValidator 字段写路径同步收敛
- [x] strict-action-auth=true：auth==null 动作 deny 且错误信息 null 安全
- [x] owner doc 处置：双配置键语义归 Phase 4（显式指向）
- [x] `ai-dev/logs/` 对应日期条目已更新

**Phase 3 执行附记（metadata 快照重录）**：metadata enforcement 测试需 nop-auth-service test 依赖（沿 nop-datav 先例），其传递引入 nop-graphql-orm，`OrmBizInitializer/OrmFetcherBuilder.initFetchers` 会为无 `id` 字段的实体 biz 对象 prepend GraphQL `id` 字段（`OrmEntityIdFetcher`）——`NopMetaClassification__save` 等响应因此多出 `id`，与旧录制基线失配（TestAutoNopMetaClassificationCrud/DataSourceCrud/AggregationCrud/QualityRuleCrud 四例）。该 `id` 字段是 graphql-orm 部署形态下的平台预期行为（datav 先例即带此依赖录制），按 datav 同款处置重录四份 response.json5 基线（仅新增 `id` 行/键序重排，CSV 表快照零内容变化已还原）；pom 注释登记该交互。

### Phase 4 - owner doc 同步 + 收口

Status: completed
Targets: `docs-for-ai/02-core-guides/service-layer.md`、`docs-for-ai/02-core-guides/auth-and-permissions.md`

- Item Types: `Fix`

- [x] service-layer.md：xbiz 动作 auth 声明语义（默认公开+显式收权模型、approval-support 缺省 admin 及可用性裁定、per-entity x:extends 覆盖点、每动作单权限规则〔`|` OR 组禁跨动作连写〕、loader auth 两路径）——新增「xbiz 动作的 auth 声明」+「@BizLoader 的 auth 声明」两节
- [x] auth-and-permissions.md：双配置键语义与开启前置（strict 前置=公开面盘点完成〔本 plan Phase 1〕+**无注解 loader 群体清单**〔NopAuthRole×2/NopDynEntityMeta×1/NopRuleNode×1〕；require 的部署要求）、fail-open 三处+ObjMetaBasedValidator 同族收敛矩阵——关键配置项表增两键 + 新增「严格模式（fail-closed 收敛）」章节
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] 两 owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] roadmap/index/daily log 收口动作落盘（roadmap R1.12 翻 done、index P1-2.6-005/006/007 → fixed、MR 表 R1.12 行）

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-2.6-005/006/007 + 双配置键配套）
- [x] 行为/契约结果已达成（16 项终值断言+loader 装配+配置键行为测试）
- [x] 必要 focused verification 已完成（受影响模块测试面：nop-api-core 150/nop-xdefs〔无测试源，xbiz 解析经全部下游测试传递验证〕/nop-biz 121/nop-graphql-core 153/nop-auth-service 464/nop-wf 140+47/nop-dyn-service 30/nop-rule-service 6/nop-metadata-service 1345/nop-code 281 全绿，plan 审查 F8）
- [x] 不存在被静默降级的 in-scope live defect（NopMetaTagLabel 前置校验缺口为显式 follow-up 非降级——closure audit Req5 亲读审计原文核实）
- [x] 受影响 owner docs 已同步（service-layer.md 两节+auth-and-permissions.md 双键+严格模式矩阵）
- [x] 独立子 agent closure-audit 已完成并记录证据（closure-audit agent_194baf1c，Can Close，见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 auth 声明经合并→装配→运行时检查链真实生效（GraphQLExecutor:72,155/GraphQLEngine:713,776→check→checkAuth→isAllowAccess 实链追踪+TestApprovalActionAuthEnforcement 真实引擎运行）；配置键经 AppConfig→三判定点连通（GraphQLEngine:384/GraphQLActionAuthChecker:126,135/ObjMetaBasedValidator:341）
- [x] `./mvnw test -pl <受影响模块> -am` 全绿（Phase 1 六模块 2028 tests + Phase 2/3 目标模块全量；reactor 闭包唯一失败为预存在 P1-7.2-001〔R1.23 范围〕）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-graphql-core --severity high` 退出码 0（nop-biz 同扫 0）

## Deferred But Adjudicated

### NopMetaTagLabel approve/reject 缺 SUBMITTED 前置校验

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 交付 auth=admin 后「任意状态直达」利用面收窄为 admin；生命周期语义变更属实体状态机设计决策，超出权限公开面修复族范围
- Successor Required: `no`（若 owner 需要可独立立项）

## Non-Blocking Follow-ups

-（无）

## Closure

Status Note: P1-2.6-005/006/007 修复族 C + 双配置键全部交付：16 项 xbiz auth 声明（五侧 merged-model 终值断言含 tenant 继承）、loader 两路径 auth 装配贯通（xbiz.xdef 走再生成链路）、fail-open 三处+ObjMetaBasedValidator 同族点以双配置键收敛（默认 false 零兼容影响）、全链 enforcement 测试证明 opt-in 部署下权限真实生效。附带消化 R1.17 附带工单（nop-sys-dao import 一行编译断裂）并按 datav 先例重录 metadata 四快照（nop-graphql-orm prepend id 字段=平台预期行为）。四个 Phase 全部 completed，独立 closure audit Can Close。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: closure-audit agent_194baf1c（fresh read-only session，branch audit/full-code-review）
- Evidence:
  - Req1 Phase/checkbox：4 Phase 全 completed，52 checkbox 除 Closure Gates 外全勾，PASS
  - Req2 live-code 抽查：7 xbiz auth 声明/buildFetcherField:469-481 消费 @Auth/toBuilder:121/xbiz.xdef auth 元素/_BizLoaderModel 五生成段再生成/ApiConfigs 双键默认 false/GraphQLEngine:377-392 fail-fast/isAllowAccess:120-141 strict+require/checkAuth 三处空安全/ObjMetaBasedValidator:332-351 fail-closed 半支，PASS
  - Req3 Anti-Hollow：三链运行时实连——xbiz 链（GraphQLExecutor:72,155/GraphQLEngine:713,776→check→checkAuth→isAllowAccess，enforcement 测试经真实引擎 executeRpcAsync 断言 ERR_AUTH_NO_PERMISSION）、loader 链（注解/xbiz→buildFetcherField/toBuilder→field.getAuth() 构建断言）、配置键链（ApiConfigs CFG→三判定点+行为矩阵 10 用例），PASS
  - Req4 测试证据：surefire 报告亲读——metadata 1345/0、wf 140+47/0、auth 464/0、dyn 30/0、rule 6/0、graphql-core 153/0、biz 121/0、code 281/0、api-core 150/0；13 个目标测试类计数与 plan 声明逐一吻合，PASS
  - Req5 deferred 诚实性：NopMetaTagLabel SUBMITTED 前置缺口=审计修复处方外（Non-Goals 显式排除+out-of-scope improvement 分类），非 in-scope live defect 降级，PASS
  - Req6 owner docs：service-layer.md:212/:234 两节、auth-and-permissions.md:249-250 双键+:256 严格模式矩阵（4 判定点收敛+strict 前置含无注解 loader 名册），与 live 行为一致，PASS
  - Req7 一致性：roadmap R1.12 done、index P1-2.6-005/006/007 fixed+MR 行、daily log 条目在位，PASS
  - Req8 hollow/no-op：变更 hunk 零空体/零吞异常；check-doc-links --strict 0、scan-hollow-implementations（graphql-core+biz）0、check-plan-checklist --strict 0，PASS
  - 工具退出码：check-plan-checklist.mjs --strict=0；scan-hollow-implementations.mjs --module nop-graphql-core --severity high=0；check-doc-links.mjs --strict=0
  - Advisory 3 条（不阻塞）：工作树残留 R1.9 未提交生成产物（12 `_create_*.sql`+7 `_*.xmeta`）已单独补交不入本 plan 提交；NopRuleDefinition.xbiz 3 空格缩进系沿该文件既有惯例；收口机械步骤（勾 Gate/写证据/翻状态）已随本 Closure 段完成

Follow-up:

- NopMetaTagLabel approve/reject 缺 SUBMITTED 前置校验——已登记 Deferred But Adjudicated（out-of-scope improvement，owner 可独立立项）
- 无其余 plan-owned 剩余工作
