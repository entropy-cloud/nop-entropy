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
| `BIZ-001` | `nop-persistence/nop-orm/src/main/java/io/nop/orm/biz/ICrudBiz.java` | 标准 CRUD 业务接口契约 |
| `BIZ-002` | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java` | 实体型服务默认基类 |
| `BIZ-003` | `CrudBizModel#requireEntity` | 普通 BizModel 获取实体的安全路径 |
| `BIZ-004` | `CrudBizModel#doFindList` / `doFindPage` | 普通查询应优先走的安全 API |
| `BIZ-005` | `nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobScheduleBiz.java` + `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java` | 跨 BizModel 协作通常通过 `I*Biz` 接口 |
| `BIZ-006` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/biz/BizLoader.java` + `nop-demo/nop-delta-demo/src/main/java/io/nop/demo/biz/LoginApiBizModelDelta.java` | 扩展 GraphQL 返回字段优先 `@BizLoader`；新增字段时可配合 `autoCreateField=true` 和 `@LazyLoad` |
| `DDD-001` | `nop-service-framework/nop-biz-auth-api/src/main/java/io/nop/auth/api/messages/LoginRequest.java` + `LoginResult.java` | 仓库内真实存在 `@DataBean + ExtensibleBean + @PropMeta + Jackson 注解` 的 message bean 模式 |
| `DDD-002` | `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonTool.java` | JSON / YAML 默认统一入口应校准到 `JsonTool` |
| `DDD-003` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/ConvertHelper.java` | 类型转换默认统一入口应校准到 `ConvertHelper` |
| `DDD-004` | `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/StringHelper.java` | 字符串 helper 默认应优先校准到 `StringHelper` |
| `DDD-005` | `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/DateHelper.java` | 日期 helper 默认应优先校准到 `DateHelper` |
| `TXN-001` | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/service/BizActionInvoker.java` | 非 query 的 Biz 操作默认会进入事务 |
| `IOC-001` | `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/ClassModelBuilder.java` | private 字段不会成为可靠注入点 |
| `CFG-001` | `nop-core-framework/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java` | 配置系统会加载 `bootstrap.yaml`、`application.yaml` 与 profile 配置 |
| `INFRA-001` | `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java` | 原始 DAO、`saveEntityDirectly`、`REQUIRES_NEW` 属于 infra/store 边界模式 |
| `TEST-001` | `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitAutoTestCase.java` | 快照测试基类与 `@NopTestConfig` 约束 |
| `TEST-002` | `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitBaseTestCase.java` | 普通容器内测试基类 |
| `TEST-003` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/autotest/NopTestConfig.java` | 测试配置注解的真实属性 |
| `TEST-004` | `nop-ai/nop-ai-toolkit/src/test/resources/_vfs/nop/ai/beans/test-mock.beans.xml` + `nop-ai/nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/HttpRequestExecutorTest.java` | 测试专用 beans + `testBeansFile` 是仓库里的真实 mock bean 模式 |
| `DBG-001` | `nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml` | `nop.debug=true` 时会注册 DevDoc / DevTool 相关调试能力 |
| `DBG-002` | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevDocBizModel.java` | DevDoc 提供 beans、configVars、graphql 等调试查询 |
| `DBG-003` | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevToolBizModel.java` | DevTool 提供刷新 VFS 与清理缓存能力 |
| `DBG-004` | `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DumpNamespaceHandler.java` | `_dump/{appName}/...` 是最终合并结果的重要调试出口 |

## 当前最重要的校准点

1. 不要把 `gen-service.xgen` / `gen-web.xgen` 写成当前仓库的通用生成链路。
2. 不要在普通 BizModel 示例里把直接 `dao()` 访问写成默认做法。
3. 不要把 `@BizMutation @Transactional` 写成普通服务层模板。
4. 不要在 IoC 示例里出现 `@Inject private Foo foo;`。
5. 不要把 infra/store 层的 DAO 直接操作误写成通用业务层模式。
6. 不要把固定调试 URL 当作平台不变量；优先按 DevDoc / DevTool 的 GraphQL 能力理解。

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
