# 服务层开发指南

Nop 平台的服务层默认就是 BizModel 层。

本文档只保留服务层架构层面的结论，不再重复展开全部编码细节。

---

## 一、默认服务层模型

1. 服务入口：BizModel
2. 标准实体服务：`CrudBizModel<T>`
3. 复杂流程：BizModel + Processor
4. 跨模块调用：`I*Biz` 接口

---

## 二、无需手写服务代码的场景

以下场景应优先通过模型与默认能力解决：

| 场景 | 默认做法 |
|------|---------|
| 标准 CRUD | 直接使用 `CrudBizModel` 内置方法 |
| 字段验证 | XMeta / ORM 模型 |
| 数据权限 | 元数据与框架默认流程 |
| 页面字段扩展 | Delta / BizLoader |

---

## 三、需要写服务代码的场景

| 场景 | 默认做法 |
|------|---------|
| 自定义查询 | `@BizQuery` + `QueryBean` |
| 自定义领域动作 | `@BizMutation` |
| 跨聚合协调 | Processor |
| 外部系统编排 | Processor / 边界服务 |

---

## 四、服务层默认规则

1. 普通写操作：`@BizMutation`
2. 普通查询：`@BizQuery`
3. 普通数据访问：`requireEntity()` / `doFindList()` / `doFindPage()`
4. 普通依赖注入：`@Inject protected ...`
5. 配置注入：`@InjectValue`

不要把原始 DAO 或显式事务模板当成普通服务层默认写法。

---

## 五、边界层与服务层的区别

以下通常不属于普通服务层默认模式：

1. `IDaoProvider` + `IEntityDao`
2. `saveEntityDirectly()` / `updateEntityDirectly()`
3. `@Transactional(REQUIRES_NEW)`
4. 框架内部或 store 层调度逻辑

这些更接近 infra/store 层。

---

## 六、相关文档

- `./bizmodel-guide.md`
- `./crud-development.md`
- `./processor-development.md`
- `../12-tasks/write-bizmodel-method.md`
- `../13-reference/source-anchors.md`
