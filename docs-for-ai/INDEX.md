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

> **硬停止规则**
> 不允许手工修改任何生成物。
> 包括所有以下划线开头的文件（如 `_*.xml`、`_*.java`、`_*.xmeta`、`_app.orm.xml`、`_service.beans.xml`）以及 `_gen/` 目录下的所有文件。
> 如需改变这些文件的结果，只能修改源模型、Delta、非下划线保留层文件或 codegen 模板，然后重新生成。

## 推荐查找顺序

1. 先看本页
2. 在 `nop-entropy` 仓库内开发平台时看 `00-start-here/project-context.md`；在外部应用项目中工作时看 `00-start-here/application-project-defaults.md`
3. 再看 `03-runbooks/` 中最贴近当前任务的手册
4. 需要理解默认规则时，看 `02-core-guides/`
5. 需要理解当前仓库结构时，看 `01-repo-map/`
6. 需要确认实现锚点或符号定义时，看 `04-reference/`

## 默认规则

- 先模型，再 Delta，最后 Java。
- 默认不要修改 `_gen/`、`_*.java`、`_*.xml`、`_*.xmeta`、`_app.orm.xml`、`_service.beans.xml`。
- BizModel 方法实体能表达的优先返回 Entity，汇总/简化/组合数据用 `@DataBean` DTO。字段可见性在 xmeta 中控制。详见 `02-core-guides/service-layer.md`。
- 标准实体服务默认使用 `CrudBizModel<T>`。
- 普通取数优先 `requireEntity()`、`doFindList()`、`doFindPage()`，不要先写原始 `dao()` 模板。
- 普通 `@BizMutation` 已自动进入事务，不要默认叠加 `@Transactional`。
- `@Inject` 不支持 `private` 字段，配置值使用 `@InjectValue`。
- 构建和再生成优先走 Maven Reactor 与 `./mvnw`，不要手改生成物。

## 按开发阶段入口

| 开发阶段 | 必读入口 | 核心规范 |
|----------|---------|---------|
| **模型设计** | **`00-required-reading-model-design.md`** | `02-core-guides/orm-model-design.md` |
| **后台开发** | **`00-required-reading-backend.md`** | `02-core-guides/service-layer.md` |
| **前台开发** | **`00-required-reading-frontend.md`** | `02-core-guides/view-and-page-customization.md` |
| **权限开发** | **`02-core-guides/auth-and-permissions.md`** | 同左（操作权限、数据权限、菜单资源） |
| **单元/集成测试** | **`00-required-reading-testing.md`** | `02-core-guides/testing.md` |
| **E2E 测试** | **`00-required-reading-e2e-testing.md`** | `02-core-guides/e2e-testing.md` |

## 快速路由

| 任务 | 首选文档 |
|------|----------|
| **模型设计** | **`00-required-reading-model-design.md`**（模型设计必读总入口） |
| **后端服务开发** | **`00-required-reading-backend.md`**（后端必读总入口） |
| **前端页面开发** | **`00-required-reading-frontend.md`**（前端必读总入口） |
| **单元/集成测试** | **`00-required-reading-testing.md`**（测试必读总入口） |
| **调试诊断** | **`04-reference/debugging-checklist.md`**（按症状索引根因） |
| **E2E 测试** | **`00-required-reading-e2e-testing.md`**（E2E 测试必读总入口） |
| 获取项目当前状态快照 | `00-start-here/project-context.md` |
| 在外部 Nop 应用项目中工作 | `00-start-here/application-project-defaults.md` |
| 理解业务应用的完整开发闭环 | `02-core-guides/application-development-workflow.md` |
| 理解文档冲突优先级和 stale 处理 | `00-start-here/truth-and-precedence.md` |
| 理解整体仓库结构 | `01-repo-map/module-groups.md` |
| 判断一个业务模块怎么分层 | `01-repo-map/domain-module-pattern.md` |
| 找模型、页面、测试、模块入口 | `01-repo-map/where-things-live.md` |
| 从模型开始开发 | `02-core-guides/model-first-development.md` |
| 理解 ORM 模型设计规范（stdDataType/stdSqlType、主键策略、关系设计） | `02-core-guides/orm-model-design.md` |
| 理解 EQL 语法、`<eql>` 与 `<sql>` 区别、数据库兼容性（空字符串转 NULL、VARCHAR 自动提升 CLOB、Dialect 特性标志） | `02-core-guides/eql-and-database-compatibility.md` |
| 理解逻辑删除（delFlag / delVersion / 恢复 / 唯一键冲突处理） | `02-core-guides/logical-deletion.md` |
| 理解外部应用从 design 到模型、生成、BizModel、测试、联调的默认顺序 | `02-core-guides/application-development-workflow.md` |
| 判断应用项目本地 docs 与 docs-for-ai 的边界 | `02-core-guides/application-project-docs-and-domain-design.md` |
| 理解 ORM 模块级菜单图标 / `module-meta.json` / TOPM 传播 | `02-core-guides/model-first-development.md` |
| 编写 BizModel / 服务层逻辑 | `02-core-guides/service-layer.md` |
| 理解 GraphQL / API 暴露方式 | `02-core-guides/api-and-graphql.md` |
| 理解 API 模型（*.api.xml）与代码生成（API 接口 vs SPI、生成物清单） | `02-core-guides/api-model-and-codegen.md` |
| 判断领域逻辑和 DDD 落位 | `02-core-guides/domain-logic-and-ddd.md` |
| 理解跨切面架构原则（聚合根与表、模块依赖方向、DSL优先等） | `02-core-guides/architecture-principles.md` |
| 判断 DTO / JSON / message bean 写法 | `02-core-guides/dto-json-and-message-beans.md` |
| 判断报表和通知集成默认路线 | `02-core-guides/reporting-and-notification-integration.md` |
| 判断 IoC 注入和配置写法 | `02-core-guides/ioc-and-config.md` |
| 理解模型加载后的 `INeedInit` 初始化约定 | `02-core-guides/model-init-and-ineedinit.md` |
| 判断错误处理和错误码写法 | `02-core-guides/error-handling.md` |
| 判断并发控制与事务边界 | `02-core-guides/concurrency-and-transactions.md` |
| 查询当前仓库代码风格 | `02-core-guides/code-style.md` |
| 理解外部应用模块开发 | `02-core-guides/external-app-development.md` |
| 编写或维护应用项目业务 owner docs | `02-core-guides/application-project-docs-and-domain-design.md` |
| 定制 view / page 页面 | `02-core-guides/view-and-page-customization.md` |
| 查复杂页面 DSL 配置模式 | `02-core-guides/page-dsl-pattern-catalog.md` |
| 查外部应用页面 DSL 示例代码片段 | `02-core-guides/external-app-examples.md` |
| 理解认证与权限控制 | `02-core-guides/auth-and-permissions.md` |
| 调试与排障 | `02-core-guides/debugging-and-diagnostics.md` |
| 理解 VFS 路径解析与资源加载 | `02-core-guides/vfs-and-resource-resolution.md` |
| 做 Delta 定制 | `02-core-guides/delta-customization.md` |
| 编写 XDef / XDSL 文件 | `02-core-guides/xdef-and-xdsl.md` |
| 理解 XLang / XPL / xrun / xgen 基本写法 | `02-core-guides/xlang-and-xpl-basics.md` |
| 定制 index.html 扩展注入 | `02-core-guides/index-html-extensions.md` |
| 编写测试 | `02-core-guides/testing.md` |
| 编写 E2E 测试 | `02-core-guides/e2e-testing.md` |
| 修改流处理引擎（nop-stream） | `01-repo-map/module-groups.md`（nop-stream 子模块） |
| CEP 模式匹配开发 | `01-repo-map/module-groups.md`（nop-stream-cep） |
| 检查点/状态管理机制 | `01-repo-map/module-groups.md`（nop-stream-runtime） |
| 新建实体 | `03-runbooks/create-new-entity.md` |
| 新增字段或校验 | `03-runbooks/add-field-and-validation.md` |
| 新增字典或常量 | `03-runbooks/add-dict-and-constants.md` |
| 模型变更后重新生成 | `03-runbooks/change-model-and-regenerate.md` |
| 调试生成链路或生成文件 | `03-runbooks/debug-codegen-and-generated-files.md` |
| 排查 `module-meta.json`、页面菜单或 TOPM 图标生成结果 | `03-runbooks/debug-codegen-and-generated-files.md` |
| 查后台页面开发路线图 | `03-runbooks/admin-page-development-roadmap.md` |
| 写 BizModel 方法 | `03-runbooks/write-bizmodel-method.md` |
| **功能实现总流程（端到端 checklist）** | **`03-runbooks/feature-implementation-checklist.md`** |
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
| 非 BizModel 场景访问 ORM（定时任务、监听器等） | `03-runbooks/non-bizmodel-orm-access.md` |
| 处理错误码和异常 | `03-runbooks/error-codes-and-nop-exception.md` |
| 写测试用例 | `03-runbooks/write-tests.md` |
| 写容器内集成测试 | `03-runbooks/write-integration-test-with-noptestconfig.md` |
| 在测试中补 mock bean | `03-runbooks/add-test-mock-bean.md` |
| 查询常用 Java helper | `04-reference/common-java-helpers.md` |
| 查询安全 API | `04-reference/safe-api-reference.md` |
| **选择可复用业务模块** | **`03-modules/reusable-modules-overview.md`**（总览 + 场景→模块路由） |
| 理解 nop-auth（认证/权限/多租户） | `03-modules/nop-auth.md` |
| 理解 nop-sys（字典/序列号/锁/事件） | `03-modules/nop-sys.md` |
| 理解 nop-report（报表引擎） | `03-modules/nop-report.md` |
| 理解 nop-rule（规则引擎） | `03-modules/nop-rule.md` |
| 理解 nop-task（任务/逻辑流） | `03-modules/nop-task.md` |
| 理解 nop-wf（工作流/BPM） | `03-modules/nop-wf.md` |
| 理解 nop-batch（批处理） | `03-modules/nop-batch.md` |
| 理解 nop-job（定时任务：本地模式 + 分布式模式） | `03-modules/nop-job.md` |
| 理解 nop-ai（AI 集成/LLM/Agent/RAG） | `03-modules/nop-ai.md` |
| 理解 nop-dyn（动态表单/实体） | `03-modules/nop-dyn.md` |
| 理解 nop-file（文件上传下载与存储机制） | `03-modules/nop-file.md` |
| 理解 nop-retry（分布式重试） | `03-modules/nop-retry.md` |
| 理解 nop-tcc（TCC 分布式事务） | `03-modules/nop-tcc.md` |
| 理解 nop-code 模块（代码索引与分析） | `03-modules/nop-code.md` |
| 查实现锚点 / 符号定义 | `04-reference/source-anchors.md` |
| 查看代码示例（Entity/BizModel/IBiz/beans/Delta） | **`05-examples/README.md`** |
| 维护 AI 文档 | `90-maintenance/maintenance-rules.md` |

## 目录角色

| 目录 | 作用 |
|------|------|
| `00-start-here/` | AI 默认规则与全局反模式 |
| `01-repo-map/` | 当前仓库结构、模块分组、文件位置 |
| `02-core-guides/` | 规范主干，回答“默认应该怎么做” |
| `03-modules/` | 可复用业务模块文档，回答"这个模块能做什么、怎么用" |
| `03-runbooks/` | 任务型手册，回答“这件事具体怎么做” |
| `04-reference/` | 速查与实现锚点，回答“需要看哪个类/方法定义” |
| `90-maintenance/` | 文档治理规则，不是日常开发入口 |
| `05-examples/` | 精简代码示例，回答"各类文件实际长什么样" |

## 当前项目的关键认知

- 这是一个根 `pom.xml` 驱动的大型 Maven 多模块仓库。
- 业务模块普遍遵循 `model -> codegen -> dao -> meta -> service -> web -> app -> api` 的骨架。
- `nop-auth`、`nop-job`、`nop-task`、`nop-wf`、`nop-ai` 是最适合用来理解这套骨架的代表模块。
- `nop-kernel`、`nop-core-framework`、`nop-persistence`、`nop-service-framework` 是框架主干。
- `nop-runner/` 和 `scripts/nop-cli.cmd` 是 CLI / runner 入口；`nop-demo/` 和 `demo/` 是示例入口。
- `docs/theory/` 下的论文与技术报告属于研究/论证材料，不是开发 AI 的默认规范入口；出现解释歧义时，不要把其中术语直接当作 `docs-for-ai/` 级别的开发规则。
- `nop-stream/` 是流处理引擎子模块组，包含 `nop-stream-core`（核心 API、状态、算子）、`nop-stream-cep`（CEP 复杂事件处理）、`nop-stream-runtime`（运行时、检查点、协调器）、`nop-stream-connector`（消息源/汇连接器）、`nop-stream-checkpoint`（检查点存储）、`nop-stream-flow`（流控）、`nop-stream-flink`（Flink 兼容层）、`nop-stream-fraud-example`（欺诈检测示例）。

## 外部应用项目规则

- 在 `nop-app-*` 这类外部应用项目中，业务事实、产品语义、计划、日志、验证命令和项目级约束留在应用项目本地 `docs/`。
- Nop 应用开发通用规则、owner-doc 边界、领域设计方法和实现落位规则集中维护在本目录。
- 默认先读应用项目本地 requirement/design/architecture 确认业务事实，再回到本目录选择 Nop 实现路径。
- 入口：`00-start-here/application-project-defaults.md` 与 `02-core-guides/application-project-docs-and-domain-design.md`。

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
