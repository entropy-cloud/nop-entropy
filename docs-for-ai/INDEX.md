# Nop Entropy AI 文档索引

`docs-for-ai/` 是当前仓库唯一有效、也是开发 AI 唯一应读取的开发文档目录。

对于日常开发任务：

1. 只读 `docs-for-ai/`。
2. 不读 `docs/`。
3. 不读其他非 `docs-for-ai/` 文档目录。
4. 一般也不直接读源码；如果 `docs-for-ai/` 仍不足以回答问题，优先基于 `04-reference/` 提供的锚点做 LSP / definition lookup。

## 推荐查找顺序

1. 先看本页
2. 再看 `03-runbooks/` 中最贴近当前任务的手册
3. 需要理解默认规则时，看 `02-core-guides/`
4. 需要理解当前仓库结构时，看 `01-repo-map/`
5. 需要确认实现锚点或符号定义时，看 `04-reference/`

## 默认规则

- 先模型，再 Delta，最后 Java。
- 默认不要修改 `_gen/`、`_*.java`、`_*.xml`、`_app.orm.xml`、`_service.beans.xml`。
- 标准实体服务默认使用 `CrudBizModel<T>`。
- 普通取数优先 `requireEntity()`、`doFindList()`、`doFindPage()`，不要先写原始 `dao()` 模板。
- 普通 `@BizMutation` 已自动进入事务，不要默认叠加 `@Transactional`。
- `@Inject` 不支持 `private` 字段，配置值使用 `@InjectValue`。
- 构建和再生成优先走 Maven Reactor 与 `./mvnw`，不要手改生成物。

## 快速路由

| 任务 | 首选文档 |
|------|---------|
| 理解整体仓库结构 | `01-repo-map/module-groups.md` |
| 判断一个业务模块怎么分层 | `01-repo-map/domain-module-pattern.md` |
| 找模型、页面、测试、模块入口 | `01-repo-map/where-things-live.md` |
| 从模型开始开发 | `02-core-guides/model-first-development.md` |
| 编写 BizModel / 服务层逻辑 | `02-core-guides/service-layer.md` |
| 判断领域逻辑和 DDD 落位 | `02-core-guides/domain-logic-and-ddd.md` |
| 判断 DTO / JSON / message bean 写法 | `02-core-guides/dto-json-and-message-beans.md` |
| 定制 view / page 页面 | `02-core-guides/view-and-page-customization.md` |
| 调试与排障 | `02-core-guides/debugging-and-diagnostics.md` |
| 做 Delta 定制 | `02-core-guides/delta-customization.md` |
| 编写 XDef / XDSL 文件 | `02-core-guides/xdef-and-xdsl.md` |
| 编写测试 | `02-core-guides/testing.md` |
| 新建实体 | `03-runbooks/create-new-entity.md` |
| 新增字段或校验 | `03-runbooks/add-field-and-validation.md` |
| 新增字典或常量 | `03-runbooks/add-dict-and-constants.md` |
| 模型变更后重新生成 | `03-runbooks/change-model-and-regenerate.md` |
| 调试生成链路或生成文件 | `03-runbooks/debug-codegen-and-generated-files.md` |
| 写 BizModel 方法 | `03-runbooks/write-bizmodel-method.md` |
| 新增跨模块 Biz 接口 | `03-runbooks/add-cross-module-biz-interface.md` |
| 选择 Entity / BizModel / Processor | `03-runbooks/choose-entity-bizmodel-processor.md` |
| 实现复杂业务流程 | `03-runbooks/implement-complex-business-flow.md` |
| 自定义查询 | `03-runbooks/custom-query-with-querybean.md` |
| 扩展 CRUD 钩子 | `03-runbooks/extend-crud-with-hooks.md` |
| 新增 BizLoader 字段 | `03-runbooks/add-bizloader-field.md` |
| 通过 Delta 扩展 API 返回字段 | `03-runbooks/extend-api-with-delta-bizloader.md` |
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
- 已经知道任务类型：直接进入 `03-runbooks/`。
- 需要继续维护这套文档：看 `90-maintenance/maintenance-rules.md`。
