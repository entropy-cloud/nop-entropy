# Nop Entropy AI 文档索引

> **受众与定位**
> 本目录描述 Nop 平台的 API 模式、约定和默认规则。主要面向**使用 Nop 平台构建业务应用**的开发者和 AI，也适用于需要理解平台使用模式的平台开发者。文中出现的源码路径为 nop-entropy 仓库内部锚点参考。
>
> 如果你是在**开发 nop-entropy 平台本身**，过程记录和开发计划在 `ai-dev/` 目录。

`docs-for-ai/` 是 nop-entropy 仓库中唯一有效的平台使用文档目录。

对于日常开发任务：

1. 只读 `docs-for-ai/`。
2. 不读 `docs/`。
3. 不读其他非 `docs-for-ai/` 文档目录。
4. 一般也不直接读源码；如果 `docs-for-ai/` 仍不足以回答问题，优先基于 `04-reference/` 提供的锚点做 LSP / definition lookup。

## 推荐查找顺序

1. 先看本页
2. 再看 `00-start-here/project-context.md`（当前项目状态快照）
3. 再看 `03-runbooks/` 中最贴近当前任务的手册
4. 需要理解默认规则时，看 `02-core-guides/`
5. 需要理解当前仓库结构时，看 `01-repo-map/`
6. 需要确认实现锚点或符号定义时，看 `04-reference/`

## 默认规则

- 先模型，再 Delta，最后 Java。
- 默认不要修改 `_gen/`、`_*.java`、`_*.xml`、`_app.orm.xml`、`_service.beans.xml`。
- BizModel 方法默认返回 Entity，不需要 DTO。字段可见性在 xmeta 中控制，不是靠改返回类型。详见 `02-core-guides/service-layer.md`。
- 标准实体服务默认使用 `CrudBizModel<T>`。
- 普通取数优先 `requireEntity()`、`doFindList()`、`doFindPage()`，不要先写原始 `dao()` 模板。
- 普通 `@BizMutation` 已自动进入事务，不要默认叠加 `@Transactional`。
- `@Inject` 不支持 `private` 字段，配置值使用 `@InjectValue`。
- 构建和再生成优先走 Maven Reactor 与 `./mvnw`，不要手改生成物。

## 快速路由

| 任务 | 首选文档 |
|------|----------|
| 获取项目当前状态快照 | `00-start-here/project-context.md` |
| 理解文档冲突优先级和 stale 处理 | `00-start-here/truth-and-precedence.md` |
| 理解整体仓库结构 | `01-repo-map/module-groups.md` |
| 判断一个业务模块怎么分层 | `01-repo-map/domain-module-pattern.md` |
| 找模型、页面、测试、模块入口 | `01-repo-map/where-things-live.md` |
| 从模型开始开发 | `02-core-guides/model-first-development.md` |
| 编写 BizModel / 服务层逻辑 | `02-core-guides/service-layer.md` |
| 理解 GraphQL / API 暴露方式 | `02-core-guides/api-and-graphql.md` |
| 判断领域逻辑和 DDD 落位 | `02-core-guides/domain-logic-and-ddd.md` |
| 理解跨切面架构原则（聚合根与表、模块依赖方向、DSL优先等） | `02-core-guides/architecture-principles.md` |
| 判断 DTO / JSON / message bean 写法 | `02-core-guides/dto-json-and-message-beans.md` |
| 判断 IoC 注入和配置写法 | `02-core-guides/ioc-and-config.md` |
| 判断错误处理和错误码写法 | `02-core-guides/error-handling.md` |
| 判断并发控制与事务边界 | `02-core-guides/concurrency-and-transactions.md` |
| 查询当前仓库代码风格 | `02-core-guides/code-style.md` |
| 理解外部应用模块开发 | `02-core-guides/external-app-development.md` |
| 定制 view / page 页面 | `02-core-guides/view-and-page-customization.md` |
| 查复杂页面 DSL 配置模式 | `02-core-guides/page-dsl-pattern-catalog.md` |
| 调试与排障 | `02-core-guides/debugging-and-diagnostics.md` |
| 做 Delta 定制 | `02-core-guides/delta-customization.md` |
| 编写 XDef / XDSL 文件 | `02-core-guides/xdef-and-xdsl.md` |
| 理解 XLang / XPL / xrun / xgen 基本写法 | `02-core-guides/xlang-and-xpl-basics.md` |
| 编写测试 | `02-core-guides/testing.md` |
| 新建实体 | `03-runbooks/create-new-entity.md` |
| 新增字段或校验 | `03-runbooks/add-field-and-validation.md` |
| 新增字典或常量 | `03-runbooks/add-dict-and-constants.md` |
| 模型变更后重新生成 | `03-runbooks/change-model-and-regenerate.md` |
| 调试生成链路或生成文件 | `03-runbooks/debug-codegen-and-generated-files.md` |
| 查后台页面开发路线图 | `03-runbooks/admin-page-development-roadmap.md` |
| 写 BizModel 方法 | `03-runbooks/write-bizmodel-method.md` |
| 创建 Request / Response DTO | `03-runbooks/create-request-response-dto.md` |
| 新增跨模块 Biz 接口 | `03-runbooks/add-cross-module-biz-interface.md` |
| 选择 Entity / BizModel / Processor | `03-runbooks/choose-entity-bizmodel-processor.md` |
| 实现复杂业务流程 | `03-runbooks/implement-complex-business-flow.md` |
| 自定义查询 | `03-runbooks/custom-query-with-querybean.md` |
| 扩展 CRUD 钩子 | `03-runbooks/extend-crud-with-hooks.md` |
| 新增 BizLoader 字段 | `03-runbooks/add-bizloader-field.md` |
| 通过 Delta 扩展 API 返回字段 | `03-runbooks/extend-api-with-delta-bizloader.md` |
| 构建树形 CRUD 页面 | `03-runbooks/build-tree-crud-page.md` |
| 构建关联子表 Drawer 页面 | `03-runbooks/build-related-drawer-page.md` |
| 构建 Tabs 工作台页面 | `03-runbooks/build-tabs-workspace-page.md` |
| 构建设计器或专用编辑器页面 | `03-runbooks/build-designer-or-special-page.md` |
| 让字段从模型落到页面 | `03-runbooks/make-field-reach-page.md` |
| 给页面增加子表编辑 | `03-runbooks/add-child-table-editor-to-page.md` |
| 给页面增加业务动作按钮 | `03-runbooks/add-page-business-action.md` |
| 用 Delta 覆盖平台页面 | `03-runbooks/override-platform-page-with-delta.md` |
| 给后台页增加导出或批量操作 | `03-runbooks/add-export-or-batch-operations.md` |
| 给字段换复杂自定义控件 | `03-runbooks/replace-field-with-complex-control.md` |
| 给页面增加运行态或关联页 | `03-runbooks/add-runtime-or-related-page.md` |
| 构建完整后台工作台页面 | `03-runbooks/build-admin-workspace-page.md` |
| 用 Delta 替代直接修改 | `03-runbooks/prefer-delta-over-direct-modification.md` |
| 处理事务边界 | `03-runbooks/transaction-boundaries.md` |
| 处理错误码和异常 | `03-runbooks/error-codes-and-nop-exception.md` |
| 写测试用例 | `03-runbooks/write-tests.md` |
| 写容器内集成测试 | `03-runbooks/write-integration-test-with-noptestconfig.md` |
| 在测试中补 mock bean | `03-runbooks/add-test-mock-bean.md` |
| 查询常用 Java helper | `04-reference/common-java-helpers.md` |
| 查询安全 API | `04-reference/safe-api-reference.md` |
| 查实现锚点 / 符号定义 | `04-reference/source-anchors.md` |
| 维护 AI 文档 | `90-maintenance/maintenance-rules.md` |

## 目录角色

| 目录 | 作用 |
|------|------|
| `00-start-here/` | AI 默认规则与全局反模式 |
| `01-repo-map/` | 当前仓库结构、模块分组、文件位置 |
| `02-core-guides/` | 规范主干，回答“默认应该怎么做” |
| `03-runbooks/` | 任务型手册，回答“这件事具体怎么做” |
| `04-reference/` | 速查与实现锚点，回答“需要看哪个类/方法定义” |
| `90-maintenance/` | 文档治理规则，不是日常开发入口 |

## 当前项目的关键认知

- 这是一个根 `pom.xml` 驱动的大型 Maven 多模块仓库。
- 业务模块普遍遵循 `model -> codegen -> dao -> meta -> service -> web -> app -> api` 的骨架。
- `nop-auth`、`nop-job`、`nop-task`、`nop-wf`、`nop-ai` 是最适合用来理解这套骨架的代表模块。
- `nop-kernel`、`nop-core-framework`、`nop-persistence`、`nop-service-framework` 是框架主干。
- `nop-runner/` 和 `scripts/nop-cli.cmd` 是 CLI / runner 入口；`nop-demo/` 和 `demo/` 是示例入口。

## 当 `docs-for-ai` 仍有歧义时

1. 先继续在 `docs-for-ai/` 内查找对应 guide、runbook 和 reference。
2. 再看 `04-reference/source-anchors.md` 里的实现锚点。
3. 如果还不够，优先通过 LSP / definition lookup 查看锚点对应的类、接口、方法签名。
4. 只有在文档维护或阻塞性例外场景下，才直接读取少量源码，并应回补 `docs-for-ai/`。

## 下一步

- 只想开始干活：先看 `00-start-here/ai-defaults.md`。
- 需要定位该改哪里：看 `01-repo-map/where-things-live.md`。
- 已经知道任务类型：先看 `03-runbooks/README.md`，前端页面任务优先看 `03-runbooks/admin-page-development-roadmap.md`。
- 需要继续维护这套文档：看 `90-maintenance/maintenance-rules.md`。
