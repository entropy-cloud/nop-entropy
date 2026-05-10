# AI 默认开发规则

本文档只回答一个问题：

**在当前 `nop-entropy` 仓库里，AI 默认应该怎样做判断、落代码和验证结果。**

## 默认决策顺序

1. 先判断是否能改模型。
2. 不能只靠模型时，再判断是否能用 Delta。
3. 只有模型和 Delta 都不足时，才写 Java 或其他保留层代码。
4. 写 Java 时，优先走 BizModel 的安全路径，而不是先复制底层 DAO 写法。

## 默认查找顺序

1. `docs-for-ai/INDEX.md`
2. `docs-for-ai/03-runbooks/` 中命中的任务手册
3. `docs-for-ai/02-core-guides/` 中对应规范
4. `docs-for-ai/01-repo-map/` 中的结构说明
5. `docs-for-ai/04-reference/` 中的实现锚点和速查

## 文档边界

1. 开发 AI 默认只阅读 `docs-for-ai/`。
2. 开发 AI 默认不阅读 `docs/`。
3. 开发 AI 默认不阅读其他非 `docs-for-ai/` 文档目录。
4. 开发 AI 一般也不直接阅读源码；如果 `docs-for-ai/` 仍不足以回答问题，优先通过 `04-reference/` 中列出的类和方法做 LSP / definition lookup。
5. 只有在文档维护或阻塞性例外场景下，才做少量直接源码阅读，并应把结论补回 `docs-for-ai/`。

## 当前仓库的硬规则

| 规则 | 默认结论 |
|------|---------|
| 模型是源头 | 优先改 `model/*.orm.xml`，项目骨架和大量派生产物都从模型生成 |
| 生成物不可直改 | `_gen/`、`_*.xml`、`_*.java`、`_app.orm.xml`、`_service.beans.xml` 默认都不手改 |
| 服务入口是 BizModel | 普通服务代码默认写在 BizModel，复杂流程再拆 Processor |
| 普通实体服务默认基类 | `CrudBizModel<T>` |
| 普通查询/取数默认路径 | `requireEntity()`、`doFindList()`、`doFindPage()` |
| 普通写操作默认事务 | `@BizMutation` 已自动包事务 |
| IoC 注入限制 | `@Inject` 字段不能是 `private` |
| 配置注入方式 | 使用 `@InjectValue`，不要把 Spring `@Value` 当默认模式 |

## 默认开发流程

1. 先定位模块骨架：看 `../01-repo-map/domain-module-pattern.md`。
2. 改源模型、Delta 或保留层文件。
3. 用 `./mvnw` 触发再生成与构建，而不是手改生成结果。
4. 只在非生成文件里补业务逻辑、测试和扩展资源。
5. 至少完成一次针对性验证：模块测试、AutoTest、或对应模块构建。

## 普通 AI 生成代码时应避免的写法

| 反模式 | 默认替代做法 |
|--------|-------------|
| `System.currentTimeMillis()` / `System.nanoTime()` | `CoreMetrics.currentTimeMillis()` / `CoreMetrics.nanoTime()` |
| 手写 JSON 拼接 / 引入第三方 JSON 库 | `JsonTool.stringify()` / `JsonTool.parseMap()` |
| 手写字符串工具逻辑 / Apache Commons `StringUtils` | `StringHelper`（`isBlank`、`join`、`splitToArray` 等） |
| 手写日期解析格式化 | `DateHelper`（`parseDate`、`formatDate` 等） |
| 直接编辑 `_gen/` 或 `_` 前缀生成文件 | 改模型、改 Delta、改非下划线保留文件 |
| 直接编辑 `_service.beans.xml`、`_dao.beans.xml`、`_app.orm.xml` 等以 `_` 开头的配置文件 | 这些文件由 codegen 管线从 ORM 模型自动生成，改了会在 `mvn install` 时被覆盖。应改对应的非下划线文件（如 `app-service.beans.xml`）或改源模型文件（如 `model/*.orm.xml`） |
| `dao().getEntityById(id)` 作为 BizModel 模板 | `requireEntity(id, action, context)` |
| `dao().findAllByQuery(query)` 作为 BizModel 模板 | `doFindList(query, selection, context)` |
| `dao().findPageByQuery(query)` 作为 BizModel 模板 | `doFindPage(query, selection, context)` |
| `@BizMutation @Transactional` | 只保留 `@BizMutation` |
| `@Inject private Foo foo;` | `protected` / package-private / setter 注入 |
| Spring `@Value` | `@InjectValue` |
| `Map<String, Object>` 作为复杂返回 DTO | 定义 `@DataBean` DTO |
| 直接注入另一个 BizModel 实现类 | 注入 `I*Biz` 接口 |

## 例外场景

以下写法可以存在，但必须当成边界场景理解，不要当默认模板传播：

1. store / infra 层直接操作 DAO。
2. `@Transactional(REQUIRES_NEW)`。
3. `daoFor(...)`、`saveEntityDirectly(...)`、`updateEntityDirectly(...)`。
4. 框架内部、调度器、底层引擎、特殊遗留 BizModel。

仓库中确实存在这类实现，例如 `nop-job` 的 store 层和少量已有 BizModel；阅读它们时要区分“存在”与“推荐默认写法”不是一回事。

## 完成前自检

1. 我改的是源文件而不是生成物吗？
2. 这个逻辑真的需要 Java 吗？
3. 这是普通 BizModel 还是 infra/store 边界场景？
4. 如果是查询/修改，我走的是 `CrudBizModel` 的默认能力吗？
5. 我是否至少做了一次与改动范围匹配的验证？
6. 如果这次任务暴露出 `docs-for-ai/` 不准确或缺失，我是否已经顺手修正文档？
7. 如果这是 significant 变更，我是否已经补 `ai-dev/logs/` 当天日志？

## 相关文档

- `../01-repo-map/domain-module-pattern.md`
- `../02-core-guides/model-first-development.md`
- `../02-core-guides/service-layer.md`
- `../02-core-guides/delta-customization.md`
- `../04-reference/source-anchors.md`
