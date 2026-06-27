# 记录字段级变更日志（ChangeLog）

## 适用场景

- 关键业务表（凭证、发票、订单、资产卡片、结算单等）需要**字段级**审计：谁在何时把哪个字段从什么改成什么。
- 合规/审计驱动，需要可追溯的字段 old→new 历史。

## AI 决策提示

- 平台已内置字段级变更日志（`nop-sys` 的 `NopSysChangeLog` + `OrmEntityChangeLogInterceptor`），**不要自建日志表或在 BizModel 钩子里手写 diff**。
- 标准做法是 ORM 实体加 `tagSet="audit"`，声明式启用，业务代码零侵入。

## 机制

### 存储：NopSysChangeLog

表 `nop_sys_change_log`，**每个变更字段一行**：

| 字段 | 含义 |
|------|------|
| `bizObjName` | 业务对象短名（实体 shortName） |
| `objId` | 记录主键 |
| `operationName` | 业务操作名（来自 `IContext.getCallOperationName()`，无则 `save`/`update`/`delete`） |
| **`propName`** | **变更的属性名** |
| **`oldValue` / `newValue`** | **旧值 / 新值（字符串）** |
| `changeTime` | 变更时间 |
| `operatorId` | 操作人（`IContext.getUserId()`） |
| `appId` | 应用 ID |
| `bizKey` | 业务键（实体 `orm:bizKeyProp` 指定，如单据号） |
| `approverId` | 审核人（实体 `orm:approverIdProp` 指定） |

> old/new 统一存字符串（参考 iDempiere `AD_ChangeLog` 设计，便于跨字段类型统一查询）。比 iDempiere 多了 `bizKey`/`approverId`/`operationName` 业务上下文。

### 拦截：OrmEntityChangeLogInterceptor

实现 `IOrmInterceptor`（见 `orm-interceptor-trigger.md`），三个 hook：

- **`postUpdate`**：用 `entity.orm_forEachDirtyProp()` **只遍历已变更字段**（非全量），每字段写一行 old/new。
- **`postSave`**：记录所有列（version 列除外）的初始值。
- **`postDelete`**：记一条删除标志（`oldValue=0`/`newValue=1`）。

> 关键：拦截在 **ORM 层**，兜底**所有写路径**（含直接 DAO 写、批处理、非 BizModel），不依赖调用方走 CrudBizModel。

### 开关（模型驱动，声明式）

| 配置 | 作用 |
|------|------|
| 实体 `tagSet="audit"` | 记 update/delete |
| 实体 `tagSet="audit-save"` | 额外记 save（初始全量） |
| 列 `tagSet="no-audit"` | 该列不记（密码、密钥、中间计算字段） |
| 实体 `orm:bizKeyProp="字段名"` | 指定业务键来源列（写入 `bizKey`） |
| 实体 `orm:approverIdProp="字段名"` | 指定审核人来源列（写入 `approverId`） |
| 全局 `nop.orm.audit.enabled` | **默认启用**（`app-dao.beans.xml` 的 `feature:on`，空值或 true 即注册拦截器 bean） |

## 用法（声明式，零代码）

```xml
<!-- ORM 模型 -->
<entity name="ErpFinVoucher" ... tagSet="audit" orm:bizKeyProp="voucherNo">
    <column name="amount" .../>
    <column name="voucherNo" .../>
    <!-- 敏感/噪声列排除 -->
    <column name="internalCache" ... tagSet="no-audit"/>
</entity>
```

加 `tagSet="audit"` 后，每次 update/delete 自动写 `nop_sys_change_log`，无需 BizModel 代码。`operationName` 自动取当前 GraphQL/REST 操作名（如 `ErpFinVoucher__save`）。

## 查询变更历史

按业务对象 + 记录 ID 查询字段变更史：

```java
@Inject
IEntityDao<NopSysChangeLog> dao;

List<NopSysChangeLog> history = dao.findListByExample(
    QueryBean.of(NopSysChangeLog.class)
        .addFilter("bizObjName", "ErpFinVoucher")
        .addFilter("objId", voucherId)
        .addOrderBy("changeTime", true));
```

## TTL 清理（应用层补）

平台**未内置** ChangeLog TTL 清理（与 iDempiere/Metasfresh `AD_ChangeLog_Delete_Old` 的 `keepDays` Job 不同）。`nop_sys_change_log` 会无限增长，应用层应按保留期用 nop-job 定期清理：

```java
// 定时任务：删除超过保留期的变更日志
SQL sql = SQL.begin().sql("delete from NopSysChangeLog where changeTime < ?", expireDate).end();
ormTemplate.execute(sql);
```

## 反模式

- ❌ 在 `afterEntityChange` 里手写 diff + 自建日志表——平台 `OrmEntityChangeLogInterceptor` 已在 ORM 层兜底所有写路径，手写会重复且漏覆盖（直接 DAO 写不走 BizModel）。
- ❌ 全表开 audit（写放大）——只对关键单据（合规/审计驱动）开。
- ❌ 期望 TTL 自动清理——平台无内建，必须应用层加 nop-job。

## 仓库里的真实参考

- 拦截器：`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/log/OrmEntityChangeLogInterceptor.java`
- 实体：`nop-sys/model/nop-sys.orm.xml`（`NopSysChangeLog`）
- Bean 注册：`nop-sys/nop-sys-dao/src/main/resources/_vfs/nop/sys/beans/app-dao.beans.xml`（`nopOrmEntityChangeLogInterceptor`，`feature:on`）
- Tag 常量：`OrmModelConstants.TAG_AUDIT`/`TAG_AUDIT_SAVE`/`TAG_NO_AUDIT`

## 相关文档

- `orm-interceptor-trigger.md` — ChangeLog 的底层机制（通用 ORM 拦截器，应用层 trigger）
- `../02-core-guides/orm-model-design.md`（`tagSet`、`orm:bizKeyProp`）
- `../03-modules/nop-sys.md`（nop-sys 模块概览）
- `../04-reference/source-anchors.md`（`AUDIT-001`）
