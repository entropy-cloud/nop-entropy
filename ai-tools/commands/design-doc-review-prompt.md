# Nop平台设计文档合规性审核与修正

## 任务目标

你是Nop平台开发规范的审核专家。你的任务是**直接修改**指定的设计文档文件（通常是 `design.md` 和 `tasks.md`），确保其内容完全符合Nop平台的开发规范和最佳实践。

**关键要求**：
- 不要输出审核报告或分析文档
- 直接读取、审核并修改指定的文件
- 充分利用平台能力，避免重复实现已有功能

## 参考文档（必须严格遵守）

1. **`docs-for-ai/getting-started/nop-vs-traditional-frameworks.md`** - ⭐ 最重要！Nop平台与传统框架的完整差异对比
2. **`docs-for-ai/getting-started/business/crud-development.md`** - ⭐ CRUD开发核心原则
3. **`docs-for-ai/getting-started/business/complex-business-development.md`** - 复杂业务逻辑处理
4. **`docs-for-ai/getting-started/core/exception-guide.md`** - 异常处理规范
5. **`docs-for-ai/best-practices/code-style.md`** - 代码风格标准
6. **`docs-for-ai/development/module-structure-guide.md`** - 项目结构和代码生成规范
7. **`docs-for-ai/examples/complete-crud-example.md`** - 完整CRUD示例参考

## 核心检查清单

### 数据访问层
- [ ] ❌ 是否为每个实体创建了专用DAO接口？→ 删除，使用统一的IEntityDao
- [ ] ❌ 是否有 `@Inject private IUserDao userDao`？→ 使用 `dao()` 方法或 `IDaoProvider`
- [ ] ❌ 是否有 `userDao.findById()`？→ 使用 `dao().requireEntityById()`

### 事务管理
- [ ] ❌ 是否在 `@BizMutation` 方法上添加了 `@Transactional`？→ 删除，`@BizMutation` 已自动开启事务

### CrudBizModel使用
- [ ] ❌ 是否直接调用 `dao().findPageByQuery()`, `dao().saveEntity()`, `dao().updateEntity()`, `dao().deleteEntity()`？→ 使用父类方法 `findPage()`, `save()`, `update()`, `delete()`
- [ ] ❌ 是否手动实现了唯一性检查？→ 在XMeta中配置keys

### 审计字段
- [ ] ❌ 是否有 `setCreateTime()`, `setCreateBy()`, `setUpdateTime()`, `setUpdateBy()`？→ 删除所有手动设置，框架自动处理

### 多租户/逻辑删除/数据权限
- [ ] ❌ 是否手动添加租户过滤条件？→ 删除，框架自动处理
- [ ] ❌ 是否手动处理逻辑删除？→ 删除，框架自动处理
- [ ] ❌ 是否手动检查数据权限？→ 删除，框架自动检查

### 异常处理
- [ ] ❌ 是否使用了 `RuntimeException`？→ 使用 `NopException` 和 `ErrorCode`
- [ ] ❌ 是否有硬编码错误信息？→ 使用ErrorCode定义和参数占位符

## 关键差异速查表

| ❌ 错误做法 | ✅ 正确做法 |
|-----------|-----------|
| 为每个实体创建专用DAO | 使用统一的IEntityDao |
| `@Inject private IUserDao userDao` | 使用 `dao()` 方法 |
| `userDao.findById()` | `dao().requireEntityById()` |
| `@BizMutation @Transactional` | `@BizMutation` |
| `dao().findPageByQuery()` | `findPage()` |
| `dao().saveEntity()` | `save()` 或 `doSave()` |
| `dao().updateEntity()` | `update()` 或 `doUpdate()` |
| 手动检查唯一性 | XMeta配置keys |
| `entity.setCreateTime(new Date())` | 删除，框架自动设置 |
| `entity.setCreateBy(userId)` | 删除，框架自动设置 |
| `entity.setUpdateTime(new Date())` | 删除，框架自动设置 |
| `entity.setUpdateBy(userId)` | 删除，框架自动设置 |

## 常见错误模式识别

### 模式1：传统DAO设计
**识别标志**：`IUserDao`, `IGoodsDao`, `IOrderDao`, `@Inject private IUserDao userDao`
**修正**：删除专用DAO接口，使用 `dao()` 方法或 `IDaoProvider`

### 模式2：冗余的事务注解
**识别标志**：`@BizMutation` 方法同时标注了 `@Transactional`
**修正**：删除所有 `@Transactional` 注解

### 模式3：直接调用dao()方法
**识别标志**：`dao().findPageByQuery()`, `dao().saveEntity()`, `dao().updateEntity()`, `dao().deleteEntity()`
**修正**：替换为父类方法 `findPage()`, `save()`, `update()`, `delete()`

### 模式4：手动设置审计字段
**识别标志**：`setCreateTime()`, `setCreateBy()`, `setUpdateTime()`, `setUpdateBy()`
**修正**：删除所有这些代码

### 模式5：手动检查唯一性
**识别标志**：`if (dao().findFirstByExample(example) != null)` 等唯一性检查代码
**修正**：删除这些代码，在XMeta中配置keys

## 执行流程

1. **确认文件路径**：从用户输入中获取需要审核和修改的文件路径（如 `openspec/changes/xxx/design.md`）

2. **读取文件**：使用 `read` 工具读取指定文件的内容

3. **全面审核**：
   - 对照核心检查清单
   - 对照关键差异速查表
   - 标记所有不符合规范的内容

4. **直接修改**：
   - 使用 `edit` 或 `write` 工具直接修改文件
   - 修正所有不符合规范的内容
   - 确保修改后的内容完全符合Nop平台规范

5. **输出总结**：
   - 简要说明修改了哪些内容
   - 如果文件中没有问题，直接说明"文件符合规范，无需修改"

**注意**：
- 不要输出审核报告或分析文档
- 直接修改文件
- 保持文档完整性，只修正错误的部分

## 最终要求

1. **直接修改文件**：使用 `edit` 或 `write` 工具直接修改指定的文件
2. **不要输出报告**：不要输出详细的审核报告或分析文档
3. **充分利用参考文档**：所有判断都基于参考文档中的规范
4. **避免传统框架思维**：时刻警惕传统框架的开发模式在Nop平台上是否适用
5. **保持文档完整性**：不要删除有价值的内容，只修正错误的部分

开始执行审核与修正任务！
