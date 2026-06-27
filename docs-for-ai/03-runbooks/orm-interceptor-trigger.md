# 用 ORM 拦截器实现应用层 trigger（orm-interceptor.xml / IOrmInterceptor）

## 适用场景

- 需要**兜底所有写路径**的横切逻辑：字段级审计、强制不变量校验、级联派生、跨表一致性。
- 希望用**配置式回调**（类似数据库 trigger）声明逻辑，而不是在每个 BizModel 里重复。

## AI 决策提示

- ORM 拦截器是平台提供的**应用层 trigger** 机制，在 ORM 层拦截实体保存/更新/删除，覆盖**所有写路径**（含直接 DAO 写、批处理、非 BizModel 场景），比 BizModel 钩子更彻底。
- **字段级审计**不要自己写拦截器——直接用平台内置的 `OrmEntityChangeLogInterceptor`（实体加 `tagSet="audit"`，见 `audit-field-changes.md`）。只有内置满足不了的横切逻辑才自己写。
- 业务校验**优先用 BizModel 钩子**（`defaultPrepareSave/Update`，有 owner doc + 测试支持）；只有需要兜底非 BizModel 写路径时才下沉到 ORM 拦截器。

## 什么是应用层 trigger

数据库 trigger 在 SQL 执行前后自动触发逻辑；nop 的 `orm-interceptor.xml` / `IOrmInterceptor` 是**应用层等价物**——在实体被 ORM 持久化（save/update/delete）前后自动触发 XPL/Java 逻辑。相比数据库 trigger 的优势：

| 维度 | 数据库 trigger | nop ORM 拦截器 |
|------|---------------|---------------|
| 跨数据库 | 每种 DB 方言不同 | **统一**（ORM 层，EQL 兼容多 DB） |
| 可见性 | 隐藏在 DB，难调试 | 应用代码，可断点、可单测 |
| 多租户 | 需手写 | 继承平台多租户上下文 |
| 版本管理 | DB 脚本 | VFS / Delta / Maven |
| 调用方感知 | 无 | 有 `IContext`（用户/操作名/租户） |

> 原则：**不要在数据库写业务 trigger**；需要 trigger 式横切逻辑时，用 nop 的 ORM 拦截器或 BizModel 钩子。

## 两种实现方式

### 方式 1：orm-interceptor.xml（配置式回调，推荐业务用）

按 schema `/nop/schema/orm/orm-interceptor.xdef`，per-entity 声明 8 个 hook 点之一，body 是 XPL（`entity` 变量是当前 `IOrmEntity`）：

```xml
<interceptor x:schema="/nop/schema/orm/orm-interceptor.xdef">
  <entity name="ErpFinVoucher">
    <pre-update id="validate-balance" order="100">
      <source><![CDATA[
        // entity 是 IOrmEntity，可读 dirty 字段做校验
        if (entity.orm_isDirty('amount') && entity.orm_propValue('amount') < 0)
           throw('ERR_FIN_AMOUNT_NEGATIVE', {id: entity.orm_idString()});
      ]]></source>
    </pre-update>
  </entity>
</interceptor>
```

- 文件路径：模块的 `_vfs/{moduleId}/orm/{entityShortName}.orm-interceptor.xml` 或 app 级 `/nop/main/orm/app.orm-interceptor.xml`。
- 同一实体可配多个 action（按 `order` 排序执行）。

### 方式 2：注册 IOrmInterceptor bean（编程式，适合全局拦截器）

实现 `IOrmInterceptor`（`io.nop.orm.IOrmInterceptor`），8 个 hook（default 空，按需 override），在 IoC 注册即生效。`MultiOrmInterceptor` 按 `IOrdered` 顺序聚合所有注册的拦截器：

| Hook | 时机 | 可否中止 |
|------|------|---------|
| `preSave(entity)` / `preUpdate(entity)` / `preDelete(entity)` | 持久化**前** | ✅ 返回 `ProcessResult.STOP` 中止 |
| `postSave` / `postUpdate` / `postDelete` | 持久化**后** | ❌ |
| `postLoad(entity)` | 加载后 | ❌ |
| `postReset(entity)` | 实体 reset 后 | ❌ |
| `preFlush()` / `postFlush(exception)` | flush 前 / 后 | ❌ |

平台内置的 `OrmEntityChangeLogInterceptor`（字段级审计，见 `audit-field-changes.md`）就是这种方式。

## IOrmInterceptor vs BizModel 钩子（选型）

| 维度 | `IOrmInterceptor` / `orm-interceptor.xml` | `CrudBizModel.defaultPrepareSave/Update` / `afterEntityChange` |
|------|-------------------------------------------|-------------------------------------------------------------|
| 覆盖范围 | **所有写路径**（含直接 DAO 写、批处理、非 BizModel） | 仅经 CrudBizModel 的 save/update |
| 层级 | ORM 层（最底，兜底） | 服务层 |
| 典型用途 | 字段级审计、ORM 级强制不变量、级联派生、跨表兜底 | 业务校验、状态机、跨实体聚合、业务流程编排 |
| 测试 | 较底层，需容器/ORM 上下文 | 有 BizModel 测试支持 |
| 可读性 | XPL 配置 / 拦截器类 | 与业务逻辑同处 |

**决策规则**：
1. 业务校验、状态迁移、跨实体聚合 → **BizModel 钩子**（默认）。
2. 需要兜底非 BizModel 写路径 / 字段级审计 / 强制不变量 → **ORM 拦截器**。
3. 字段级审计 → 直接用内置 `OrmEntityChangeLogInterceptor`（加 `tagSet="audit"`），**不要自己写**。

## 仓库里的真实参考

- 接口：`nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmInterceptor.java`
- 聚合器：`nop-persistence/nop-orm/src/main/java/io/nop/orm/support/MultiOrmInterceptor.java`
- schema：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm-interceptor.xdef`
- 内置拦截器（字段审计）：`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/log/OrmEntityChangeLogInterceptor.java`

## 反模式

- ❌ 在数据库写业务 trigger——用 ORM 拦截器替代（跨 DB、可调试、有上下文）。
- ❌ 把业务流程/状态机塞进 ORM 拦截器——那是 BizModel 的职责，拦截器只做横切兜底。
- ❌ 字段级审计自己写拦截器——用内置的 `OrmEntityChangeLogInterceptor`（`tagSet="audit"`）。

## 相关文档

- `audit-field-changes.md` — 字段级变更日志（ORM 拦截器的内置应用）
- `extend-crud-with-hooks.md` — BizModel 钩子（服务层扩展点，对比选型）
- `../03-modules/nop-sys.md`（nop-sys 模块概览）
- `../04-reference/source-anchors.md`（`AUDIT-002`）
