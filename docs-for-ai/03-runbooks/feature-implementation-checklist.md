# 功能实现总流程（End-to-End Checklist）

## 适用场景

- 执行一个 plan 中定义的功能切片。
- 从需求到可验证代码的完整路径。
- 本文档是各 runbook 的串联总纲，不重复各 runbook 的详细内容。

## 强制总顺序

```
1. 确认需求范围
2. 检查/修改 ORM 模型（如需要）
3. 代码生成与构建（如需要）
4. ErrorCode + I*Biz 接口声明（集中完成）
5. 逐 BizModel 实现（每个方法：实现 → 自检）
6. Domain Entity 修复（如需要）
7. 测试
8. 页面定制（如需要）
9. 整体验证
10. 更新文档
```

每步标注"如需要"的可跳过，其余为强制步骤。不得倒序。

---

## Step 1：确认需求范围

- [ ] 读取 plan 中当前切片的交付范围和验收标准
- [ ] 读取对应 requirement 和 owner doc，确认业务语义
- [ ] 读取应用项目的 roadmap 或 backlog（如有），确认 Phase 状态和依赖
- [ ] 列出本切片涉及的所有实体、BizModel、ErrorCode

**路由：** `application-project-defaults.md` → 项目本地 requirement / design / plan

## Step 2：检查/修改 ORM 模型（如需要）

跳过条件：plan 明确说明 ORM 模型已就绪，无需新增/修改。

- [ ] 检查源模型中是否已有需要的实体、字段、关系、字典
- [ ] 如需新增/修改，按 `02-core-guides/orm-model-design.md` 和 `02-core-guides/model-first-development.md` 执行
- [ ] 确认 stdDataType / stdSqlType 分离、主键策略、字典绑定符合规范

**Runbook：** `create-new-entity.md` | `add-field-and-validation.md` | `change-model-and-regenerate.md`

## Step 3：代码生成与构建（如需要）

跳过条件：已有代码生成脚手架，不需要重新生成。

- [ ] 首次建骨架用 `nop-cli gen`；后续迭代用 Maven 触发增量生成
- [ ] 构建通过
- [ ] 确认生成文件未被手动修改（`_` 前缀文件不得手动编辑）

**Runbook：** `create-new-entity.md` | `change-model-and-regenerate.md`

## Step 4：ErrorCode + I*Biz 接口声明（集中完成）

**本步骤在写任何 BizModel 实现之前，集中完成所有 ErrorCode 和 I*Biz 接口声明。**

### 4a. 定义 ErrorCode

- [ ] 在模块 `Errors` 接口中定义本切片需要的所有错误码
- [ ] 错误描述用中文（i18n 处理翻译）

**Runbook：** `error-codes-and-nop-exception.md`

### 4b. 声明 I*Biz 接口方法

- [ ] 在 `I*Biz` 接口中添加所有本切片需要的方法声明
- [ ] 标注 `@BizQuery` / `@BizMutation` / `@BizAction` 之一
- [ ] 所有参数标注 `@Name`（或使用 `@RequestBean`）
- [ ] 最后一个参数为 `IServiceContext`
- [ ] BizModel 已 `implements I*Biz`，编译能通过

**为什么集中而非逐方法：** 接口声明是契约层。先完成所有契约，再逐模块实现，可以避免"写了实现忘补接口"的问题。Plan 中通常也会把接口声明作为独立阶段。

## Step 5：逐 BizModel 实现

**按实体/模块逐个实现。每个 BizModel 内部，按方法逐个实现。每个方法写完立即自检。**

对每个 BizModel：

1. 添加需要的 `@Inject` 依赖（非 `private`）
2. 逐方法实现（每个方法加 `@Override`）
3. 每个方法写完后立即执行写后自检

### 写后自检（每个方法写完立即执行）

参照 `write-bizmodel-method.md` 的"写后自检清单"逐条校验。重点：

- [ ] 数据获取走 `requireEntity()` / `doFindList()` / `doFindPage()`，不走 `dao()`
- [ ] 新建实体走 `newEntity()` / `xxxBiz.newEntity()`，不走 `new XxxEntity()`
- [ ] 跨实体操作通过注入的 `I*Biz` 接口调用，不转型
- [ ] 不加多余的 `@Transactional`（`@BizMutation` 已自带）
- [ ] 方法已在 `I*Biz` 接口上声明（Step 4 已完成，此处确认）
- [ ] 返回值没有使用 `Map<String, Object>`

**Runbook：** `write-bizmodel-method.md`

## Step 6：Domain Entity 修复（如需要）

跳过条件：无 Entity 层需要修复的问题。

- [ ] 修复 plan 中标记的 Entity 层问题（如错误异常类型、缺失字段逻辑等）
- [ ] 修复后对应测试同步更新

## Step 7：测试

- [ ] BizModel 方法通过 `IGraphQLEngine` 测试，不是实体级纯逻辑测试
- [ ] 多步场景用 `@var:` 自动传递数据
- [ ] 测试基类选择正确（`JunitAutoTestCase` / `JunitBaseTestCase`）

**Runbook：** `write-tests.md` | `write-integration-test-with-noptestconfig.md`

> 如果项目的测试基础设施尚未建立 IGraphQLEngine 集成测试模式，可以先写域逻辑测试确保编译通过，但必须标注为 deferred，并在 successor plan 中补齐集成测试。

## Step 8：页面定制（如需要）

跳过条件：本切片不涉及页面变更。

- [ ] 按三层模型（grid/form/page）定制 view
- [ ] 不编辑 `_` 前缀的生成文件

**Runbook：** 参考 `00-required-reading-frontend.md`

## Step 9：整体验证

- [ ] 执行项目构建命令
- [ ] 运行新增和已有测试
- [ ] 构建全绿

## Step 10：更新文档

- [ ] 更新开发日志（应用项目中）
- [ ] 更新相关 owner docs（如本切片影响了设计或架构文档）
- [ ] 如有 plan，更新 plan 状态和 checklist
- [ ] 如有 roadmap，更新对应 Phase 状态

---

## 决策速查

| 问题 | 查哪里 |
|------|--------|
| 逻辑放 Entity 还是 BizModel？ | `choose-entity-bizmodel-processor.md` |
| 需要拆 Processor 吗？ | `implement-complex-business-flow.md` |
| 跨模块怎么调用？ | `add-cross-module-biz-interface.md` |
| Delta 还是新 Java？ | `prefer-delta-over-direct-modification.md` |
| 用哪个安全 API？ | `safe-api-reference.md` |

## 相关文档

- `../00-start-here/ai-defaults.md` — 决策顺序总纲
- `../02-core-guides/application-development-workflow.md` — 应用开发工作规程
- `../00-required-reading-backend.md` — 后端必读索引
