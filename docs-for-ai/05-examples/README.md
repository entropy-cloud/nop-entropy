# 05-examples — 应用开发代码示例

> 从真实项目抽取并**大幅精简**的参考模板。类名/方法名已泛化，与任何具体业务无关。
> 不是可直接编译的代码，而是展示关键模式和约定的最小骨架。

## 文件清单

| 文件 | 展示的模式 |
|------|-----------|
| `entity-class.java` | 简单实体 + 领域方法 + `requireBiz` 只读查询关联实体 + `computeIfAbsent` 缓存 |
| `ibiz-and-bizmodel.java` | IBiz 接口 + BizModel：Order（@RequestBean/@Name/@Optional/@BizAction）+ Product（defaultPrepareSave/Update/Delete 钩子 + sql-lib mapper） |
| `dto-and-errors.java` | `@DataBean` DTO + `ErrorCode.define()` 错误码（含 `.param()` 参数） |
| `test-examples.java` | 简单测试 + 快照录制回放 + 多步骤流程 + 复杂断言，四种测试模式 |
| `sql-lib-and-mapper.java` | `<eql>`（实体属性名）vs `<sql>`（数据库列名）+ `@SqlLibMapper` 接口 |
| `delta-customization.java` | 继承平台 BizModel + beans 替换注册 |

## 核心速记

1. **Entity**: `@BizObjName` + 继承 `_gen` 基类，只写领域方法；`requireBiz` 只读查询，不能写
2. **IBiz + BizModel**: `ICrudBiz<T>` + `CrudBizModel<T>` + `setEntityName()`；`@Inject` 不能 private
3. **参数**: 少用 `@Name`，多用 `@RequestBean` + DTO；`@Optional` 可不传，非 Optional 必传
4. **DTO**: `@DataBean`，放 dao 模块 dto 包
5. **错误码**: `ErrorCode.define("a.b.c", "消息含{param}")` + `throw new NopException(ERR_XXX).param(...)`
6. **Delta**: `_delta/default/模块名/` 下放覆盖文件，`x:extends="super"` 继承原模块
