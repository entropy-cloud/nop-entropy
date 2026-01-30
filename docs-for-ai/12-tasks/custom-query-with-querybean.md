# 用 QueryBean 写自定义查询（FilterBeans/分页/排序）

## 适用场景

- 内置 `findPage/findList` 无法表达你的过滤条件或业务规则
- 你需要根据请求动态拼装查询条件

## AI 决策提示

- ✅ 只要能表达：优先使用 `CrudBizModel` 内置 `doFindPage/doFindList` 等能力
- ✅ 查询条件用 `QueryBean` + `FilterBeans`
- ✅ 入参用 `Map`（request）或 `QueryBean` 本身，不要自定义 DTO

## 最小闭环

```java
// 真实示例（来自仓库测试用例）：
// c:\can\nop\nop-entropy\nop-service-framework\nop-graphql\nop-graphql-core\src\test\java\io\nop\graphql\core\engine\MyEntityBizModel.java

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.QueryBean;

import java.util.ArrayList;
import java.util.List;

@BizModel("MyEntity")
public class MyEntityBizModel {

    /**
     * 约定：QueryBean 由前端/调用方传入（filter/limit/offset/orderBy 等），BizModel 直接消费。
     */
    @BizQuery
    @GraphQLReturn(bizObjName = "MyEntity")
    public PageBean<MyEntity> findPage(@Name("query") QueryBean query) {
        List<MyEntity> ret = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            MyEntity entity = new MyEntity();
            entity.setId("entity_" + i);
            // 示例里演示如何读取 query.filter 上的属性
            entity.setName("entity_name_" + i + "_" + query.getFilter().getAttr("value"));
            ret.add(entity);
        }
        PageBean<MyEntity> page = new PageBean<>();
        page.setTotal(100);
        page.setItems(ret);
        return page;
    }
}
```

## 进阶：需要“拼装过滤条件”时怎么做

上面的示例是“直接消费 QueryBean”。如果你确实需要在 BizModel 内部根据 request 动态拼接过滤条件：

- 优先用 `FilterBeans` 来构建 filter 树
- 把拼装逻辑放在一个小函数里（便于复用/测试）
- 最终仍然交给 `CrudBizModel.doFindPage/doFindList` 执行（如果你的 BizModel 继承自 `CrudBizModel`）

## 源码锚点

- `QueryBean`：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java`
- `FilterBeans`：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/FilterBeans.java`
- `CrudBizModel.doFindPage(...)`：`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java`
- `@BizQuery` 真实用法示例：`nop-service-framework/nop-graphql/nop-graphql-core/src/test/java/io/nop/graphql/core/engine/MyEntityBizModel.java`
