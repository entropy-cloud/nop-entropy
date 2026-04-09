# Nop 平台 AI 默认开发原则

本文档只回答一个问题：

**AI 在 Nop 平台上默认应该怎样思考和落代码。**

如果你是 AI 助手，请先记住：

1. **先模型，再 Delta，最后 Java**
2. **先 runbook，再规范，再原理，再示例**
3. **默认不要修改生成物**
4. **普通 BizModel 默认使用 `CrudBizModel` 安全路径，而不是直接 `dao()`**

---

## 一、默认查找顺序

推荐顺序：

1. `docs-for-ai/INDEX.md`
2. `docs-for-ai/12-tasks/` 对应任务手册
3. `docs-for-ai/03-development-guide/` 与 `04-core-components/` 规范文档
4. `docs-for-ai/01-core-concepts/`、`02-architecture/`、`05-xlang/`
5. `docs-for-ai/08-examples/`、`09-quick-reference/`、`06-utilities/`

也就是说：

**示例和 quick reference 只能辅助理解，不能反向覆盖规范主干。**

---

## 二、源码支持的硬规则

| 规则 | 说明 |
|------|------|
| 模型是源头 | `/nop/templates/orm` 和 `*-codegen/postcompile/gen-orm.xgen` 说明项目骨架和大量业务产物都来自模型 |
| 生成物不可直改 | 手写类继承 `_Xxx`、非下划线文件扩展下划线文件是平台主流模式 |
| 实体型服务默认基于 `CrudBizModel<T>` | `io.nop.biz.crud.CrudBizModel` 和大量真实 `XxxBizModel` 实现证明这是标准模式 |
| BizModel 查询/取数默认走安全 API | `CrudBizModel#requireEntity`、`#prepareFindPageQuery` 会处理权限、逻辑删除、对象元数据过滤 |
| `@BizMutation` 默认自动事务 | `io.nop.biz.service.BizActionInvoker` 对非 query 操作自动包事务 |
| `private` 字段不能注入 | `io.nop.core.reflect.impl.ClassModelBuilder` 会跳过 `private` 字段 |
| 跨 BizModel 协作走 `I*Biz` 接口 | `io.nop.orm.biz.ICrudBiz` 与真实 `INopJobScheduleBiz`/`NopJobScheduleBizModel` 是标准组合 |
| 直接 DAO 写法属于边界场景 | 例如 store / infra 层可用 `daoFor()`、`saveEntityDirectly()`、`@Transactional(REQUIRES_NEW)`，但这不是普通 BizModel 默认模式 |

源码锚点见：`../13-reference/source-anchors.md`

---

## 三、AI 默认开发流程

### 1. 改模型

优先修改：

- `model/*.orm.xml`
- xmeta / xbiz / view / delta 文件

### 2. 重新生成

首次初始化：

```bash
nop-cli gen model/{appName}.orm.xml -t=/nop/templates/orm -o=.
```

后续变更优先：

```bash
mvn clean install
```

如果需要理解生成职责，请看：`../03-development-guide/project-structure.md`

### 3. 优先写可保留层

优先顺序：

1. 模型 / xmeta / xbiz
2. Delta
3. 手写保留类或非下划线资源文件

### 4. 最后才写 Java 业务逻辑

普通实体型服务默认写法：

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {
    public OrderBizModel() {
        setEntityName(Order.class.getName());
    }
}
```

### 5. 验证

至少做以下一项：

1. 对应模块构建通过
2. 进程内集成测试通过
3. AutoTest 快照录制/校验通过

---

## 四、代码放置默认规则

| 逻辑类型 | 默认位置 |
|---------|---------|
| 数据结构、字典、字段校验 | ORM / XMeta |
| 现有产品差量定制 | Delta |
| 只读领域辅助方法 | Entity |
| 普通业务查询/修改 | BizModel |
| 跨聚合协调、复杂流程 | Processor |
| 直接 DAO、显式新事务、版本锁、批量底层操作 | store / infra 层 |

---

## 五、普通 BizModel 的默认写法

### 查询

- `@BizQuery`
- `QueryBean + doFindList()` / `doFindPage()`
- 最后一个参数必须是 `IServiceContext`

### 修改

- `@BizMutation`
- `requireEntity()` 获取实体
- `updateEntity()` / `save()` / `delete()` 执行持久化
- 需要提交后副作用时用 `txn().afterCommit(...)`

### 跨模块调用

- 注入 `I*Biz` 接口
- 不直接注入另一个 BizModel 实现类

---

## 六、普通 AI 生成代码时应避免的写法

1. `dao().getEntityById(id)`
2. `dao().findAllByQuery(query)`
3. `@BizMutation @Transactional`
4. `@Inject private Foo foo;`
5. Spring `@Value`
6. 编辑 `_gen/` 和 `_` 前缀生成物
7. 用 `Map<String, Object>` 作为复杂返回 DTO
8. 直接注入其他 BizModel 实现类

---

## 七、什么时候可以用原始 DAO / `@Transactional`

以下是**边界场景**，不是普通 BizModel 默认模式：

1. store / infra 层
2. 需要 `REQUIRES_NEW`
3. 需要版本锁、底层批量操作、直接 ORM 行为
4. 框架内部或底层调度组件

这类场景应明确写清楚其边界，不要把它们写成服务层默认模板。

---

## 八、相关文档

- `../INDEX.md`
- `../12-tasks/create-new-entity.md`
- `../12-tasks/write-bizmodel-method.md`
- `../03-development-guide/project-structure.md`
- `../03-development-guide/bizmodel-guide.md`
- `../04-core-components/ioc-container.md`
- `../13-reference/source-anchors.md`
