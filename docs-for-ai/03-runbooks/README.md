# 任务手册

本目录是当前仓库里 AI 执行具体开发任务时的首选入口。

## 使用顺序

1. 先看 `docs-for-ai/INDEX.md`
2. 再进入本目录最贴近当前任务的手册
3. 如果还需要理解默认规则，再跳到 `02-core-guides/`

## 当前手册

### 优先入口

| 文档 | 任务场景 |
|------|---------|
| `admin-page-development-roadmap.md` | 后台页面开发路线图与文档导航 |
| `../00-start-here/application-project-defaults.md` | 在外部 Nop 应用项目中判断默认开发闭环 |
| `../02-core-guides/application-project-docs-and-domain-design.md` | 维护应用项目本地 requirement/design/architecture/model/plans/logs 边界 |

### 数据与模型

| 文档 | 任务场景 |
|------|---------|
| `create-new-entity.md` | 新建实体 |
| `add-field-and-validation.md` | 新增字段与校验 |
| `add-dict-and-constants.md` | 新增字典与常量 |
| `generate-business-code.md` | 生成业务编码 / 单据编号（CodeRule + Sequence） |
| `change-model-and-regenerate.md` | 模型变更后重新生成 |
| `debug-codegen-and-generated-files.md` | 调试生成链路与生成文件 |

### 服务与业务逻辑

| 文档 | 任务场景 |
|------|---------|
| `write-bizmodel-method.md` | 编写 BizModel 方法 |
| `add-cross-module-biz-interface.md` | 新增跨模块 Biz 接口 |
| `choose-entity-bizmodel-processor.md` | 判断逻辑该放在哪一层 |
| `implement-complex-business-flow.md` | 实现多步骤复杂业务流程 |
| `custom-query-with-querybean.md` | 自定义查询 |
| `extend-crud-with-hooks.md` | 扩展 CRUD 钩子 |
| `audit-field-changes.md` | 记录字段级变更日志（实体加 `tagSet="audit"`） |
| `orm-interceptor-trigger.md` | 用 ORM 拦截器实现应用层 trigger（IOrmInterceptor / orm-interceptor.xml） |
| `generate-report.md` | 生成报表 / 单据打印（XPT 模板 + 三种输出 + 套打） |
| `add-bizloader-field.md` | 给返回类型新增 BizLoader 字段 |
| `extend-api-with-delta-bizloader.md` | 用 Delta 扩展既有 API 字段 |
| `create-request-response-dto.md` | 创建 Request / Response DTO |
| `transaction-boundaries.md` | 事务边界 |
| `error-codes-and-nop-exception.md` | 错误码与异常 |

### 页面与前端

| 文档 | 任务场景 |
|------|---------|
| `make-field-reach-page.md` | 让字段从模型落到页面 |
| `replace-field-with-complex-control.md` | 给字段换复杂自定义控件 |
| `add-child-table-editor-to-page.md` | 给页面增加子表编辑 |
| `add-page-business-action.md` | 给页面增加业务动作按钮 |
| `build-tree-crud-page.md` | 构建树形 CRUD 页面 |
| `build-related-drawer-page.md` | 构建关联子表 Drawer 页面 |
| `add-runtime-or-related-page.md` | 给页面增加运行态或关联页 |
| `build-tabs-workspace-page.md` | 构建 Tabs 工作台页面 |
| `add-export-or-batch-operations.md` | 给后台页增加导出或批量操作 |
| `build-admin-workspace-page.md` | 构建完整后台工作台页面 |
| `build-designer-or-special-page.md` | 构建设计器或专用编辑器页面 |
| `override-platform-page-with-delta.md` | 用 Delta 覆盖平台页面 |
| `prefer-delta-over-direct-modification.md` | 用 Delta 替代直接修改 |

### 单元/集成测试

| 文档 | 任务场景 |
|------|---------|
| `write-tests.md` | 编写测试 |
| `write-integration-test-with-noptestconfig.md` | 编写容器内集成测试 |
| `add-test-mock-bean.md` | 在测试中补 mock bean |

### E2E 测试

| 文档 | 任务场景 |
|------|---------|
| `../02-core-guides/e2e-testing.md` | E2E 测试模式（Playwright、RPC 调用、浏览器测试） |

## 默认规则

1. 先模型 / 元数据 / 生成。
2. 再 Delta。
3. 最后才写 Java。
4. 普通 BizModel 优先走安全 API。

## 相关文档

- `../INDEX.md`
- `../00-start-here/application-project-defaults.md`
- `../02-core-guides/application-project-docs-and-domain-design.md`
- `../02-core-guides/page-dsl-pattern-catalog.md`
- `../02-core-guides/debugging-and-diagnostics.md`
- `../02-core-guides/domain-logic-and-ddd.md`
- `../02-core-guides/service-layer.md`
- `../04-reference/source-anchors.md`
