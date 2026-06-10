# AI 默认开发规则

> **受众**：在 nop-entropy 仓库内进行平台开发的 AI agent。如果你在使用 Nop 构建业务应用，可跳过本文件。

> **硬停止规则**
> 不允许手工修改任何生成物。
> 包括所有以下划线开头的文件（如 `_*.xml`、`_*.java`、`_*.xmeta`、`_app.orm.xml`、`_service.beans.xml`）以及 `_gen/` 目录下的所有文件。
> 如需改变这些文件的结果，只能修改源模型、Delta、非下划线保留层文件或 codegen 模板，然后重新生成。

本文档只回答一个问题：

**在当前 `nop-entropy` 仓库里，AI 默认应该怎样做判断、落代码和验证结果。**

## 默认决策顺序

1. 先判断是否能改模型。
2. 不能只靠模型时，再判断是否能用 Delta。
3. 只有模型和 Delta 都不足时，才写 Java 或其他保留层代码。
4. 写 Java 时，优先走 BizModel 的安全路径，而不是先复制底层 DAO 写法。

## 默认查找顺序
1. 再看 `00-start-here/project-context.md`（当前项目状态快照）
2. 再看 `03-runbooks/` 中最贴近当前任务的手册
3. 需要理解默认规则时，看 `02-core-guides/`
4. 需要理解当前仓库结构时，看 `01-repo-map/`
5. 需要确认实现锚点或符号定义时，看 `04-reference/`

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
| 生成物不可直改 | `_gen/`、`_*.xml`、`_*.java`、`_*.xmeta`、`_app.orm.xml`、`_service.beans.xml` 默认都不手改 |
| `_dump/` 是调试输出 | `_dump/{appName}/...` 仅用于查看最终合并结果，不手改、不作为质量修复目标 |
| 服务入口是 BizModel | 普通服务代码默认写在 BizModel，复杂流程再拆 Processor |
| BizModel 返回值 | 默认返回 Entity，由 xmeta 控制字段可见性。不要为了限制字段而改返回 DTO。仅计算结果（无对应实体）才用 DTO |
| 普通实体服务默认基类 | `CrudBizModel<T>`，已内置 `dao()`、`daoProvider()`、`daoFor(clazz)` 等方法，使用前先阅读 `CrudBizModel` 和 `ICrudBiz` |
| 普通查询/取数默认路径 | `requireEntity()`、`doFindList()`、`doFindPage()` |
| 普通写操作默认事务 | `@BizMutation` 已自动包事务 |
| IoC 注入限制 | `@Inject` 字段不能是 `private` |
| 接口注入 → 接口调用 | 通过接口类型注入的依赖，只能调用该接口（含其父接口链）上声明的方法。如果需要转型到实现类或基类才能调用某个方法，说明接口契约不完整——先在接口上补声明，再通过接口调用。**转型被注入的接口是禁止操作，没有例外。** |
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
| 直接编辑 `_gen/` 或 `_` 前缀生成文件（包括 `_*.xmeta`） | 改模型、改 Delta、改非下划线保留文件 |
| 直接修 `_dump/` 下文件 | 把它当调试快照，去修源模型/Delta/模板/运行条件，然后让 debug 输出自动刷新 |
| 直接编辑 `_service.beans.xml`、`_dao.beans.xml`、`_app.orm.xml` 等以 `_` 开头的配置文件 | 这些文件由 codegen 管线从 ORM 模型自动生成，改了会在 `mvn install` 时被覆盖。应改对应的非下划线文件（如 `app-service.beans.xml`）或改源模型文件（如 `model/*.orm.xml`） |
| `dao().getEntityById(id)` 作为 BizModel 模板 | `requireEntity(id, action, context)` |
| `dao().findAllByQuery(query)` 作为 BizModel 模板 | `doFindList(query, this::invokeDefaultPrepareQuery, selection, context)` |
| `dao().findPageByQuery(query)` 作为 BizModel 模板 | `doFindPage(query, this::invokeDefaultPrepareQuery, selection, context)` |
| `IDaoProvider.daoFor(Xxx.class).*` 或 `IOrmTemplate` 在 BizModel 中访问其他实体 | 注入 `I*Biz` 接口（继承 `ICrudBiz`，使用前先阅读 `ICrudBiz`）。仅当 `I*Biz` 确实无法满足需求时才降级到基类内置的 `daoProvider().daoFor(...)`（注释说明原因），最后才用 `IOrmTemplate` 或 `@SqlLibMapper`。每一级降级都绕过了上层管道，必须注释说明原因。**不要重复 `@Inject IDaoProvider`，基类已提供 `daoProvider()`** |
| `@BizMutation @Transactional` | 只保留 `@BizMutation` |
| `@Inject private Foo foo;` | `protected` / package-private / setter 注入 |
| Spring `@Value` | `@InjectValue` |
| `Map<String, Object>` 作为复杂返回 DTO | 定义 `@DataBean` DTO |
| DTO 日期时间字段使用 `String` 类型 | 使用 `java.time` 标准类型（`LocalDateTime`、`LocalDate`、`LocalTime`），框架自动处理序列化 |
| 自定义 BizModel 查询返回 DTO 而不是 Entity | 直接返回 Entity，字段可见性在 xmeta 中配置。仅无对应实体的计算结果（图分析、层级树等）才用 DTO |
| 将 XDSL→运行时 桥接器标 `@Deprecated` 并推荐绕过 DSL 直接用 Java API | Nop 平台 DSL 优先：桥接器有 bug 应修复，不应绕过。任何 Model→Runtime 桥接都是 DSL 体系的核心，不是可废弃的附属品 |
| 直接注入另一个 BizModel 实现类 | 注入 `I*Biz` 接口 |
| 转型被注入的接口到实现类或基类（如 `(XxxBizModel) xxxBiz`、`(CrudBizModel) xxxBiz`） | 禁止转型。需要的方法不在接口上 → 先在接口补声明；已在父接口链上 → 直接通过接口调用。转型被注入的接口是禁止操作，没有例外 |
| `Files.readString(path)` / `new FileInputStream(file)` 直接读写文件 | VFS 层：`IResource.readText()` / `IResource.getInputStream()` |
| `resource.toFile().toPath()` 绕过 VFS 做路径运算 | `IResource.getPath()` / `getStdPath()`；遍历时用 `depthIterator` |
| 一次性 `getAllResources` 全量加载 | `depthIterator` 惰性遍历 + `BatchQueue` 分块处理 |

**VFS 优先原则**：除非需求明确要求使用本地文件（如 `ProjectAnalyzer.analyzeProject(Path)` 处理本地目录），否则默认通过 VFS 抽象层（`VirtualFileSystem.instance()`、`IResource`、`IResourceLoader`）访问资源。VFS 统一了本地文件、classpath、远程存储等来源，直接使用 `java.nio.file` / `java.io` 会丢失这层抽象。

## 例外场景

以下写法可以存在，但必须当成边界场景理解，不要当默认模板传播：

1. store / infra 层直接操作 DAO。
2. `@Transactional(REQUIRES_NEW)`。
3. `daoFor(...)`、`saveEntityDirectly(...)`、`updateEntityDirectly(...)`。
4. 框架内部、调度器、底层引擎、特殊遗留 BizModel。

仓库中确实存在这类实现，例如 `nop-job` 的 store 层和少量已有 BizModel；阅读它们时要区分“存在”与“推荐默认写法”不是一回事。

## 阅读即理解

文档中所有"阅读 X"、"先读 X"、"使用前先阅读 X"的指令，执行时必须做到：

1. **读之前不写代码。** Required Pre-Reading 列出的每一个文档，在写任何业务代码之前必须全部读完。不是扫描标题和表格，是逐段读到能复述关键规则的程度。
2. **读的目的是获得能力，不是收集规则条文。** 读 `ICrudBiz` 不是为了记住"要用 `I*Biz`"这句话，而是为了知道 `ICrudBiz` 提供了 `get()`、`findList()`、`deleteByQuery()` 等方法签名和语义，这样写代码时才有正确的能力可用。
3. **追踪文档内部的二次引用。** 文档中"使用前先阅读 X"、"先读 X"、"阅读 X"等指令，与 Required Pre-Reading 等效——必须在实际使用 X 之前读完，不能当作描述性文字跳过。如果跳过了这些二次引用，就会在不知道接口提供了哪些方法的情况下写代码，导致转型、绕过安全 API、使用底层 `dao()` 等反模式。
4. **遇到障碍先补知识，不绕路。** 如果编译报错让你想违反已读文档中的规则，说明你对某个接口或机制的理解有缺口。此时应该回到文档或源码补上这个缺口，而不是找一个能编译通过但违反规范的方式。每一级降级都绕过了上层管道，必须注释说明原因。

违反这条元规则的典型症状：读完了文档但写代码时仍然用了文档明确禁止的反模式，然后用"我读过了但没注意"来解释。

## 完成前自检

1. 我改的是源文件而不是生成物吗？
2. 这个逻辑真的需要 Java 吗？
3. 这是普通 BizModel 还是 infra/store 边界场景？
4. 如果是查询/修改，我走的是 `CrudBizModel` 的默认能力吗？
5. **BizModel 中访问其他实体时，我是否注入了 `I*Biz` 接口？** 如果用了 `IDaoProvider`（包括 `@Inject` 或基类 `daoProvider()`），说明绕过了数据权限和管道，必须改走 `I*Biz`。
6. 我是否至少做了一次与改动范围匹配的验证？
7. 如果这次任务暴露出 `docs-for-ai/` 不准确或缺失，我是否已经顺手修正文档？
8. 如果这是 significant 变更，我是否已经补 ai-dev/logs/ 当天日志？

## 相关文档

- `../01-repo-map/domain-module-pattern.md`
- `../02-core-guides/model-first-development.md`
- `../02-core-guides/service-layer.md`
- `../02-core-guides/delta-customization.md`
- `../04-reference/source-anchors.md`
