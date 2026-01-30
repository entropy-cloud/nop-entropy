# 扩展 CRUD 钩子（defaultPrepareSave/defaultPrepareQuery...）

## 适用场景

- 你只想在保存/更新/删除/查询前后追加逻辑，不想自己重写完整 CRUD
- 你希望保留平台内置：数据权限、回调、字段校验/转换等行为

## AI 决策提示

- ✅ 优先：继承 `CrudBizModel<T>` 并重写扩展点
- ✅ 避免：直接 `dao().saveEntity(...)` 绕过 `CrudBizModel` 的内置流程（除非你明确知道后果）
- ✅ 参数：Biz 方法入参用 `Map`/`QueryBean`，不要自定义 DTO

## 最小闭环

```java
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {

    @Override
    protected void defaultPrepareSave(EntityData<User> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        User user = entityData.getEntity();
        // TODO: 在此修改/补充字段
    }
}
```

## 常见坑

- 不要用 `@Inject private Foo foo;`（NopIoC 不支持 private 字段注入）
- 不要写 `createUser(UserDto dto)` 一类 DTO 入参；改为 `Map` 或 `@RequestBean`（以平台约定为准）

## 源码锚点

- `CrudBizModel`：`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java`
