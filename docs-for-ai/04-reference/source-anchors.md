# 实现锚点

本页的作用不是让开发 AI 去大范围读源码，而是给 `docs-for-ai/` 中的关键规则提供最小实现锚点。

正常开发时，走到本页通常已经是最后一步：

1. 先读完相关 guide / runbook。
2. 如果还需要确认定义，优先对这里列出的类、接口、方法做 LSP / definition lookup。
3. 不要因为这里给了路径，就默认转成大范围源码阅读。

## 核心锚点表

| 规则 ID | 锚点 | 说明 |
|---------|------|------|
| `GEN-001` | `/nop/templates/orm` | ORM 模型是项目骨架和多模块生成的起点 |
| `GEN-002` | `*-codegen/postcompile/gen-orm.xgen` | `*-codegen` 负责从源模型驱动项目级生成 |
| `GEN-003` | `*-meta/precompile/gen-meta.xgen` | `*-meta` 负责生成 XMeta |
| `GEN-004` | `*-meta/postcompile/gen-i18n.xgen` | `*-meta` 负责生成 i18n |
| `GEN-005` | `*-web/precompile/gen-page.xgen` | `*-web` 基于 XMeta 生成页面文件 |
| `GEN-006` | `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/meta/src/main/resources/_vfs/{moduleId}/model/_module-meta.json.xgen` + `.../module-meta.json.xgen` | `*-meta` 负责生成模块级 `module-meta.json`，为 web 层暴露稳定的模块元数据边界 |
| `GEN-007` | `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-web/@init.xrun` + `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-web/src/main/resources/_vfs/{moduleId}/auth/_{moduleName}.action-auth.xml.xgen` + `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java` (`loadDeltaJson`) | ORM web 模板通过 `loadDeltaJson("/{moduleId}/model/module-meta.json")` 读取模块级 meta，TOPM icon 取 `moduleMeta.icon` |
| `GEN-008` | 维护脚本 | 仓库内固定检查 source `model/*.orm.xml` 的 root/entity icon，以及 source `*.action-auth.xml` 中显式 `TOPM` / `SUBM` 资源的 icon；该脚本属于维护层，不作为普通开发 AI 的源码阅读入口 |
| `GEN-009` | `pom.xml` (`exec-maven-plugin` 的 `precompile` / `precompile2` / `postcompile` executions) | 根 POM 定义 codegen 任务绑定的 Maven phase、classpath 可见性与 `CodeGenTask` 入口；排查“为什么没执行/为什么读不到资源”时先校准这里 |
| `BIZ-001` | `nop-persistence/nop-orm/src/main/java/io/nop/orm/biz/ICrudBiz.java` | 标准 CRUD 业务接口契约 |
| `BIZ-002` | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java` | 实体型服务默认基类 |
| `BIZ-003` | `CrudBizModel#requireEntity` | 普通 BizModel 获取实体的安全路径 |
| `BIZ-004` | `CrudBizModel#doFindList` / `doFindPage` | 普通查询应优先走的安全 API |
| `BIZ-007` | `CrudBizModel#prepareFindPageQuery` / `appendOrderByPk` | 查询预处理会追加数据权限、默认 filter / orderBy，并在需要时补主键排序 |
| `BIZ-005` | `nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobScheduleBiz.java` + `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java` | 跨 BizModel 协作通常通过 `I*Biz` 接口 |
| `BIZ-006` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/biz/BizLoader.java` + `nop-demo/nop-delta-demo/src/main/java/io/nop/demo/biz/LoginApiBizModelDelta.java` | 扩展 GraphQL 返回字段优先 `@BizLoader`；新增字段时可配合 `autoCreateField=true` 和 `@LazyLoad` |
| `DDD-001` | `nop-service-framework/nop-biz-auth-api/src/main/java/io/nop/auth/api/messages/LoginRequest.java` + `LoginResult.java` | 仓库内真实存在 `@DataBean + ExtensibleBean + @PropMeta + Jackson 注解` 的 message bean 模式 |
| `DDD-002` | `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonTool.java` | JSON / YAML 默认统一入口应校准到 `JsonTool` |
| `DDD-003` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/ConvertHelper.java` | 类型转换默认统一入口应校准到 `ConvertHelper` |
| `DDD-004` | `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/StringHelper.java` | 字符串 helper 默认应优先校准到 `StringHelper` |
| `DDD-005` | `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/DateHelper.java` | 日期 helper 默认应优先校准到 `DateHelper` |
| `DDD-006` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/time/CoreMetrics.java` | 获取当前时间戳/日期/时间一律使用 `CoreMetrics`，禁止 `System.currentTimeMillis()` |
| `MODEL-INIT-001` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/INeedInit.java` + `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/DslModelParser.java` | `INeedInit` 是模型加载后的延迟初始化约定；标准 XDSL 解析链会在反序列化后自动调用 `init()`，除非显式 `disableInit(true)` |
| `MODEL-INIT-002` | `nop-network/nop-rpc/nop-rpc-model/src/main/java/io/nop/rpc/model/ApiModel.java` + `ApiServiceModel.java` + `ApiMethodModel.java` | 典型上下文回填模式：根模型级联 `init()` 子模型，为子节点补父引用和派生信息 |
| `MODEL-INIT-003` | `nop-utils/nop-fsm/src/main/java/io/nop/fsm/model/StateMachineModel.java` + `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/RuleModel.java` | 典型运行期索引/映射构建模式：在 `init()` 中把 DSL 列表整理成高频访问所需的 map / 派生结构 |
| `MODEL-INIT-004` | `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/loader/OrmModelLoader.java` | 手工 merge / 组装模型后，通常需要显式 `model.init()`，然后再进入 `freeze(true)` 等后续阶段 |
| `TXN-001` | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/service/BizActionInvoker.java` | 非 query 的 Biz 操作默认会进入事务 |
| `IOC-001` | `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/ClassModelBuilder.java` | private 字段不会成为可靠注入点 |
| `CFG-001` | `nop-core-framework/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java` | 配置系统会加载 `bootstrap.yaml`、`application.yaml` 与 profile 配置 |
| `IOC-002` | `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/AppBeanContainerLoader.java` + `nop-kernel/nop-core/src/main/java/io/nop/core/module/ModuleManager.java` | 模块通过 `/_module` 发现，app bean 默认按 `app.beans.xml` / `app-*.beans.xml` 文件规则加载 |
| `INFRA-001` | `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java` | 原始 DAO、`saveEntityDirectly`、`REQUIRES_NEW` 属于 infra/store 边界模式 |
| `TEST-001` | `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitAutoTestCase.java` | 快照测试基类与 `@NopTestConfig` 约束 |
| `TEST-002` | `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitBaseTestCase.java` | 普通容器内测试基类 |
| `TEST-003` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/autotest/NopTestConfig.java` | 测试配置注解的真实属性 |
| `TEST-004` | `nop-ai/nop-ai-toolkit/src/test/resources/_vfs/nop/ai/beans/test-mock.beans.xml` + `nop-ai/nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/HttpRequestExecutorTest.java` | 测试专用 beans + `testBeansFile` 是仓库里的真实 mock bean 模式 |
| `TEST-005` | `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/AutoTestCase.java` + `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitAutoTestCase.java` | AutoTest 常用 `input/request/output/outputText` helper，且录制完成会抛专用结束异常 |
| `DBG-001` | `nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml` | `nop.debug=true` 时会注册 DevDoc / DevTool 相关调试能力 |
| `DBG-002` | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevDocBizModel.java` | DevDoc 提供 beans、configVars、graphql 等调试查询 |
| `DBG-003` | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevToolBizModel.java` | DevTool 提供刷新 VFS 与清理缓存能力 |
| `DBG-004` | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DumpNamespaceHandler.java` | `_dump/{appName}/...` 是最终合并结果的重要调试出口 |
| `GQL-001` | `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/GraphQLConstants.java` + `nop-service-framework/nop-graphql/nop-graphql-orm/src/main/java/io/nop/graphql/orm/OrmFetcherBuilder.java` + `nop-service-framework/nop-graphql/nop-graphql-orm/src/main/java/io/nop/graphql/orm/fetcher/OrmEntityPropConnectionFetcher.java` | relation 字段可通过 `graphql:filter` / `graphql:orderBy` 补默认查询元数据 |
| `GQL-002` | `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/service/SpringGraphQLWebService.java` + `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/service/QuarkusGraphQLWebService.java` + `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/utils/GraphQLNameHelper.java` | 通用 REST adapter 暴露 `/r/{operationName}`、`/p/{query}`，BizModel operationName 默认为 `{bizObj}__{method}` |
| `IOC-003` | `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/beans/dao-defaults.beans.xml` + `nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml` + `nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml` | 平台内置 bean 广泛使用 `nop*` 命名，这是仓库强约定，但不是 IoC 层面的保留前缀规则 |
| `UI-001` | `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthResource/NopAuthResource.view.xml` | 树形 CRUD、`@TreeChildren`、`add-child`、`gen-control`、`loadDataOnce` 的综合参考 |
| `UI-002` | `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobSchedule/NopJobSchedule.view.xml` | row action 直连业务 API、运行态 drawer + vertical tabs、关联子页组合的综合参考 |
| `UI-003` | `nop-rule/nop-rule-web/src/main/resources/_vfs/nop/rule/pages/NopRuleNode/NopRuleNode.view.xml` + `nop-rule/nop-rule-web/src/main/resources/_vfs/nop/rule/pages/NopRuleNode/ref-ruleDefinition.page.yaml` | `gen-control` 高阶控件、`add-child` 上下文传递、`fixedProps` 关联子表页参考 |
| `UI-004` | `nop-wf/nop-wf-web/src/main/resources/_vfs/nop/wf/designer/designer.page.yaml` | `x:gen-extends` 与大块手写 page schema 混合的专用设计器页面参考 |
| `XLANG-001` | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xpl.xdef` | XPL 模板文件的基础 schema |
| `XLANG-002` | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xlib.xdef` | XLib 文件的基础 schema |
| `XLANG-003` | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef` | 通用 XDSL 扩展语法 schema |
| `XLANG-004` | `nop-wf/nop-wf-web/src/main/resources/_vfs/nop/wf/xlib/dingflow-gen/impl_GenComponents.xpl` | `c:unit`、`c:for`、`c:if`、`${...}` 的真实模板例子 |
| `XLANG-005` | `nop-task/nop-task-core/src/main/resources/_vfs/nop/task/xlib/task.xlib` | `xpl:is` 与 XLib 入口的真实例子 |
| `XLANG-006` | `nop-runner/nop-cli-core/tasks/gen-web.xrun` | `xpl:lib` 调用 XLib 的 runner 任务例子 |
| `XLANG-007` | `nop-kernel/nop-core/precompile/src/main/java/io/nop/core/type/PredefinedGenericTypes.java.xgen` | 文本输出型 `.xgen` 模板例子 |
| `XLANG-008` | `nop-kernel/nop-xlang/precompile/gen-xlang-xdsl.xgen` + `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/schema/obj-schema.xdef` + `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/schema/schema.xdef` | XDef schema 变更会驱动 `nop-xlang` 的 XDSL 生成链；共享 prop 定义常同时分布在 `obj-schema.xdef` / `schema.xdef` |
| `AUTH-001` | `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml` (bean `nopAuthFilterConfig`) | HTTP 路径认证配置：`defaultPublic`/`publicPaths`/`authPaths`/`servicePaths` |
| `AUTH-002` | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthFilterConfig.java` | 认证配置类，`isPublicPath()` 判定逻辑 |
| `AUTH-003` | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java` | 认证过滤器实现，token 解析、OAuth、cookie 处理 |
| `INT-001` | `nop-integration/nop-integration-api/src/main/java/io/nop/integration/api/sms/ISmsSender.java` + `SmsMessage.java` | 外发短信默认接口与消息对象 |
| `INT-002` | `nop-integration/nop-integration-api/src/main/java/io/nop/integration/api/email/IEmailSender.java` | 外发邮件默认接口 |
| `RPT-001` | `nop-report/model/nop-report.orm.xml` + `nop-report/nop-report-web/src/main/resources/_vfs/nop/report/auth/_nop-report.action-auth.xml` | `nop-report` 的数据集、报表定义、结果文件和后台页面入口 |

## 当前最重要的校准点

1. 不要把 `gen-service.xgen` / `gen-web.xgen` 写成当前仓库的通用生成链路。
2. 不要在普通 BizModel 示例里把直接 `dao()` 访问写成默认做法。
3. 不要把 `@BizMutation @Transactional` 写成普通服务层模板。
4. 不要在 IoC 示例里出现 `@Inject private Foo foo;`。
5. 不要把 infra/store 层的 DAO 直接操作误写成通用业务层模式。
6. 不要把固定调试 URL 当作平台不变量；优先按 DevDoc / DevTool 的 GraphQL 能力理解。
7. 不要把 ORM 模块级 TOPM icon 的来源误写成 `/{moduleId}/orm/app.orm.xml`；当前默认链路是 `model/*.orm.xml` 根 `<orm ext:icon>` -> `*-meta/module-meta.json` -> `*-web`。

## 当 `docs-for-ai` 仍有歧义时

1. 先回到这里找锚点。
2. 优先对锚点对应的类、接口、方法做 LSP / definition lookup。
3. 只有在文档维护或阻塞性例外场景下，才直接读取少量源码。
4. 一旦发现 `docs-for-ai` 有误，先修正文档，不要把“去读源码”变成默认工作流。

## 相关文档

- `../INDEX.md`
- `../00-start-here/ai-defaults.md`
- `../02-core-guides/domain-logic-and-ddd.md`
- `../02-core-guides/dto-json-and-message-beans.md`
- `../02-core-guides/model-first-development.md`
- `../02-core-guides/service-layer.md`
