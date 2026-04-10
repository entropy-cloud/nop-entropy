# 任务手册

本目录是当前仓库里 AI 执行具体开发任务时的首选入口。

## 使用顺序

1. 先看 `docs-for-ai/INDEX.md`
2. 再进入本目录最贴近当前任务的手册
3. 如果还需要理解默认规则，再跳到 `02-core-guides/`

## 当前手册

| 文档 | 任务场景 |
|------|---------|
| `create-new-entity.md` | 新建实体 |
| `add-field-and-validation.md` | 新增字段与校验 |
| `add-dict-and-constants.md` | 新增字典与常量 |
| `change-model-and-regenerate.md` | 模型变更后重新生成 |
| `debug-codegen-and-generated-files.md` | 调试生成链路与生成文件 |
| `write-bizmodel-method.md` | 编写 BizModel 方法 |
| `add-cross-module-biz-interface.md` | 新增跨模块 Biz 接口 |
| `choose-entity-bizmodel-processor.md` | 判断逻辑该放在哪一层 |
| `implement-complex-business-flow.md` | 实现多步骤复杂业务流程 |
| `custom-query-with-querybean.md` | 自定义查询 |
| `extend-crud-with-hooks.md` | 扩展 CRUD 钩子 |
| `add-bizloader-field.md` | 给返回类型新增 BizLoader 字段 |
| `extend-api-with-delta-bizloader.md` | 用 Delta 扩展既有 API 字段 |
| `prefer-delta-over-direct-modification.md` | 用 Delta 替代直接修改 |
| `create-request-response-dto.md` | 创建 Request / Response DTO |
| `build-tree-crud-page.md` | 构建树形 CRUD 页面 |
| `build-related-drawer-page.md` | 构建关联子表 Drawer 页面 |
| `build-tabs-workspace-page.md` | 构建 Tabs 工作台页面 |
| `build-designer-or-special-page.md` | 构建设计器或专用编辑器页面 |
| `make-field-reach-page.md` | 让字段从模型落到页面 |
| `add-child-table-editor-to-page.md` | 给页面增加子表编辑 |
| `add-page-business-action.md` | 给页面增加业务动作按钮 |
| `override-platform-page-with-delta.md` | 用 Delta 覆盖平台页面 |
| `transaction-boundaries.md` | 事务边界 |
| `error-codes-and-nop-exception.md` | 错误码与异常 |
| `write-tests.md` | 编写测试 |
| `write-integration-test-with-noptestconfig.md` | 编写容器内集成测试 |
| `add-test-mock-bean.md` | 在测试中补 mock bean |

## 默认规则

1. 先模型 / 元数据 / 生成。
2. 再 Delta。
3. 最后才写 Java。
4. 普通 BizModel 优先走安全 API。

## 相关文档

- `../INDEX.md`
- `../02-core-guides/debugging-and-diagnostics.md`
- `../02-core-guides/domain-logic-and-ddd.md`
- `../02-core-guides/service-layer.md`
- `../04-reference/source-anchors.md`
