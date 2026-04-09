# Nop Platform AI Documentation Index

> Deprecated: `docs-for-ai-old/` 仅作为旧资料归档与本次重写的信息来源保留。当前仓库的有效 AI 文档入口是 `docs-for-ai/INDEX.md`。

`docs-for-ai` 的推荐查找顺序只有一条：

1. 先看本页
2. 再进 `12-tasks/` 对应任务手册
3. 再看规范主干文档
4. 最后才看示例和 quick reference

如果你是 AI 助手，请默认遵循：

- **先模型，再 Delta，最后 Java**
- **不要修改 `_gen/` 或 `_` 前缀生成物**
- **普通 BizModel 默认使用 `CrudBizModel`、`requireEntity()`、`doFindList()`、`doFindPage()`**
- **`@BizMutation` 默认已带事务，不要再叠加 `@Transactional`**
- **`@Inject` 不支持 `private` 字段，配置值用 `@InjectValue`**

源码锚点见：`13-reference/source-anchors.md`

---

## 一、先做什么

| 任务 | 首选文档 |
|------|---------|
| 新建实体 / 新建表 | `12-tasks/create-new-entity.md` |
| 新增字段 / 新增校验 | `12-tasks/add-field-and-validation.md` |
| 编写 BizModel 方法 | `12-tasks/write-bizmodel-method.md` |
| 自定义查询 | `12-tasks/custom-query-with-querybean.md` |
| 扩展 CRUD 钩子 | `12-tasks/extend-crud-with-hooks.md` |
| 扩展 API 返回字段 | `12-tasks/extend-api-with-delta-bizloader.md` |
| 事务边界与 afterCommit | `12-tasks/transaction-boundaries.md` |
| 错误码与 NopException | `12-tasks/error-codes-and-nop-exception.md` |
| 编写单元/集成测试 | `12-tasks/write-unit-test.md` |

如果 `12-tasks/` 里没有命中任务，再按下面的规范主干查找。

---

## 二、规范主干

### 1. 服务层 / BizModel

- `03-development-guide/bizmodel-guide.md`
- `03-development-guide/crud-development.md`
- `03-development-guide/service-layer.md`
- `03-development-guide/processor-development.md`

### 2. 项目结构 / 代码生成

- `03-development-guide/project-structure.md`
- `01-core-concepts/ai-development.md`

### 3. IoC / 配置 / 事务 / 异常

- `04-core-components/ioc-container.md`
- `04-core-components/transaction.md`
- `04-core-components/exception-handling.md`

### 4. 测试

- `11-test-and-debug/autotest-guide.md`
- `07-best-practices/testing.md`

### 5. Delta / XLang / 原理

- `01-core-concepts/delta-basics.md`
- `01-core-concepts/overview.md`
- `05-xlang/xdef-core.md`
- `05-xlang/xlang-guide.md`

---

## 三、全局反模式

以下写法在普通 AI 生成代码场景下应默认避免：

| 反模式 | 推荐做法 | 原因 |
|--------|---------|------|
| `dao().getEntityById(id)` | `requireEntity(id, action, context)` | 绕过数据权限与框架流程 |
| `dao().findAllByQuery(query)` | `doFindList(query, selection, context)` | 绕过 query 预处理与权限过滤 |
| `dao().findPageByQuery(query)` | `doFindPage(query, selection, context)` | 绕过 query 预处理与权限过滤 |
| `@BizMutation @Transactional` | 只用 `@BizMutation` | 普通 BizModel 写操作已自动事务 |
| `@Inject private Foo foo;` | `protected` / package-private / setter 注入 | NopIoC 不支持 private 注入 |
| Spring `@Value` | `@InjectValue` | Nop 配置注入约定不同 |
| 编辑 `_gen/`、`_*.java`、`_*.xml` | 改模型、继承扩展类、Delta 覆盖 | 生成物会被覆盖 |
| `Map<String, Object>` 作为复杂返回 DTO | 定义 `@DataBean` DTO | GraphQL 无法稳定推断类型 |
| 直接注入另一个 BizModel 实现类 | 注入 `I*Biz` 接口 | 保持跨模块调用与可替换性 |

---

## 四、目录角色

| 目录 | 角色 |
|------|------|
| `12-tasks/` | AI 首选 runbook |
| `03-development-guide/` | 规范主干 |
| `04-core-components/` | Nop 特有组件规则 |
| `01-core-concepts/`、`02-architecture/`、`05-xlang/` | 原理与架构 |
| `08-examples/`、`09-quick-reference/`、`06-utilities/` | 辅助参考，不是默认规范 |
| `13-reference/` | 源码锚点与辅助索引 |

---

## 五、使用规则

1. 能通过模型解决，就不要先写 Java。
2. 能通过 Delta 解决，就不要改基础实现。
3. 需要写 BizModel 时，先看 `12-tasks/write-bizmodel-method.md`。
4. 需要理解“为什么这样做”时，再去看 `03-development-guide/`。
5. 示例和 quick reference 只能辅助理解，不能反向覆盖规范主干。
