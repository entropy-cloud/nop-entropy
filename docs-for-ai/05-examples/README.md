# 05-examples — 应用开发代码示例

> 从真实项目（nop-app-erp 等）抽取并**大幅精简**的参考模板。类名/方法名已泛化，与任何具体业务无关。
> 不是可直接编译的代码，而是展示关键模式和约定的最小骨架。
>
> **从零创建外部应用项目时**，配合 `../03-runbooks/bootstrap-new-application.md` 阅读以下配置/模型示例。

## 文件清单

| 文件 | 展示的模式 |
|------|-----------|
| `orm-model.orm.xml` | **完整最小 ORM 源模型**（代码生成源头）：`<orm>` 根属性 + `<domains>` + `<dicts>` + 主子表实体（主键策略、逻辑删除、字典、to-one/to-many 关系、唯一键、索引） |
| `beans-registration.beans.xml` | `app-service.beans.xml` 写法：导入生成物 + 自定义 BizModel 注册（`ioc:type="@bean:id"`）+ 普通 bean 注册 + 条件注册 |
| `application.yaml` | 最小可运行 Quarkus 应用配置：H2 数据源、auth/jwt、site-map、ORM 自动建表、端口、dev profile |
| `entity-class.java` | 简单实体 + 领域方法 + `requireBiz` 只读查询关联实体 + `computeIfAbsent` 缓存 |
| `ibiz-and-bizmodel.java` | IBiz 接口 + BizModel：Order（@RequestBean/@Name/@Optional/@BizAction）+ Product（defaultPrepareSave/Update/Delete 钩子 + sql-lib mapper） |
| `dto-and-errors.java` | `@DataBean` DTO + `ErrorCode.define()` 错误码（含 `.param()` 参数） |
| `test-examples.java` | 简单测试 + 快照录制回放 + 多步骤流程 + 复杂断言，四种测试模式 |
| `sql-lib-and-mapper.java` | `<eql>`（实体属性名）vs `<sql>`（数据库列名）+ `@SqlLibMapper` 接口 |
| `delta-customization.java` | 继承平台 BizModel + beans 替换注册 |

## 核心速记

1. **ORM 源模型**: `<orm ext:appName ext:basePackageName ext:entityPackageName ...>` + `<domains>` 复用公共字段 + `<dicts>` + `<entity>` 带主键/stdSqlType/logicDelete/审计字段
2. **Entity**: `@BizObjName` + 继承 `_gen` 基类，只写领域方法；`requireBiz` 只读查询，不能写
3. **IBiz + BizModel**: `ICrudBiz<T>` + `CrudBizModel<T>` + `setEntityName()`；`@Inject` 不能 private；**自定义 BizModel 必须在 `app-service.beans.xml` 注册**
4. **参数**: 少用 `@Name`，多用 `@RequestBean` + DTO；`@Optional` 可不传，非 Optional 必传
5. **DTO**: `@DataBean`，放 dao 模块 dto 包；仅汇总/简化/组合数据用 DTO，实体能表达的优先返回 Entity
6. **错误码**: `ErrorCode.define("a.b.c", "消息含{param}")` + `throw new NopException(ERR_XXX).param(...)`
7. **Delta**: `_delta/default/模块名/` 下放覆盖文件，`x:extends="super"` 继承原模块
8. **配置**: `application.yaml` 是主配置；`@cfg:` / `@i18n:` 等 `@`-prefix resolver 在加载期求值
