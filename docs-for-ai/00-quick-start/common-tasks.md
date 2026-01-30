# 常见开发任务快速参考

## 重要说明

### 模型驱动架构（Model-Driven Architecture）

**Nop 平台使用模型驱动架构，无需手动编写简单的 CRUD 代码！**

#### 核心特性

1. **内置 CRUD 操作**：继承 CrudBizModel 后，已经自动内置了完整的 CRUD 操作
2. **参数使用 Map/QueryBean**：不使用自定义 DTO 对象，直接使用 Map 和 QueryBean
3. **元数据驱动**：通过 xmeta 元数据模型定义数据结构和验证规则
4. **字段级别自适应**：修改模型后自动适应，无需重新生成代码

#### 无需编程的场景

以下场景**不需要手动编写代码**：

| 场景 | 解决方案 |
|------|---------|
| 简单查询 | 使用内置方法 `findPage()`, `get()` |
| 保存操作 | 使用内置方法 `save(Map data)` |
| 更新操作 | 使用内置方法 `update(Map data)` |
| 删除操作 | 使用内置方法 `delete(Map id)` |
| 批量操作 | 使用内置方法 `batchSave()`, `batchUpdate()`, `batchDelete()` |
| 字段扩展 | 修改 xmeta 模型即可，自动生效 |

#### 需要编程的场景

以下场景**需要手动编写代码**：

| 场景 | 解决方案 |
|------|---------|
| 复杂业务逻辑 | 重写扩展点（defaultPrepareSave, defaultPrepareQuery 等） |
| 复杂查询 | 自定义 BizQuery 方法，使用 Map/QueryBean 参数 |
| 跨模块调用 | 使用 IBizObjectManager 或定义接口 |
| 事务监听器 | 使用 ITransactionTemplate |

---

## 任务1：新增实体

### ORM模型

```xml
<entity name="Product" className="com.example.myapp.dao.entity.Product"
        displayName="产品" tableName="t_product">
    <columns>
        <column name="productId" stdDomain="string" primary="true"
                displayName="产品ID" length="32" />
        <column name="productName" stdDomain="string" displayName="产品名称"
                        length="200" />
        <column name="price" stdDomain="decimal" displayName="价格"
                        precision="10" scale="2" />
        <column name="stock" stdDomain="int" displayName="库存"
                        defaultValue="0" />
    </columns>
</entity>
```

### 代码生成

```bash
cd myapp-codegen
mvn clean install
```

### BizModel

```java
@BizModel("Product")
public class ProductBizModel extends CrudBizModel<Product> {

    @Inject
    protected IDaoProvider daoProvider;

    protected IProductDao dao() {
        return daoProvider.dao(Product.class);
    }
}
```

**注意**: 继承 CrudBizModel 后，已经自动内置了完整的 CRUD 操作（findPage, save, update, delete 等），通常**不需要手动实现**简单的增删改查方法。

---

## 任务2：使用内置CRUD操作

CrudBizModel 已经内置了完整的 CRUD 操作，可以直接使用。

### 查询操作

```java
// 简单查询：根据ID获取
// ✅ 使用内置方法，无需编程
@BizModel("Product")
public class ProductBizModel extends CrudBizModel<Product> {
    // 前端调用：Product__get(id: "xxx") { productId, productName, price }
    // 参数通过 QueryBean 或 Map 传入，框架自动转换为实体
}
```

### 分页查询

```java
// 分页查询：使用 QueryBean
// 前端调用：Product__findPage(pageNo:1, pageSize:10) { ... }
// 内置方法会自动使用 xmeta 配置的字段信息，无需手动编写代码
```

### 保存操作

```java
// 保存操作：使用内置 save() 方法
// 前端调用：Product__save(data: { productName: "...", price: 100 }) { ... }
// ✅ 使用内置方法，传入 Map 数据
@BizModel("Product")
public class ProductBizModel extends CrudBizModel<Product> {
    // 无需实现，直接继承 CrudBizModel 即可
    
    // ✅ 如需自定义保存逻辑，重写扩展点
    @Override
    protected void defaultPrepareSave(EntityData<Product> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        // 自定义逻辑：价格校验
        Product product = entityData.getEntity();
        if (product.getPrice() == null || product.getPrice() < 0) {
            throw new NopException(Errors.ERR_INVALID_PRICE)
                .param("price", product.getPrice());
        }
    }
}
```

### 更新操作

```java
// 更新操作：使用内置 update() 方法
// 前端调用：Product__update(data: { productId: "xxx", price: 150 }) { ... }
// ✅ 使用内置方法，传入 Map 数据
@BizModel("Product")
public class ProductBizModel extends CrudBizModel<Product> {
    // 无需实现，直接继承 CrudBizModel 即可
    
    // ✅ 如需自定义更新逻辑，重写扩展点
    @Override
    protected void defaultPrepareUpdate(EntityData<Product> entityData, IServiceContext context) {
        super.defaultPrepareUpdate(entityData, context);
        // 自定义逻辑
    }
}
```

### 删除操作

```java
// 删除操作：使用内置 delete() 方法
// 前端调用：Product__delete(id: "xxx") { ... }
// ✅ 使用内置方法，无需手动实现
```

---

## 任务2：新增查询API

### 简单查询

```java
@BizQuery
public Product getProduct(@Name("productId") String productId) {
    // ✅ 使用 getEntity() 而非 dao().getEntityById()，会自动应用数据权限
    return getEntity(productId);
}
```

### 条件查询

```java
@BizQuery
public List<Product> getProductsByPriceRange(@Name("minPrice") BigDecimal minPrice, @Name("maxPrice") BigDecimal maxPrice) {
    QueryBean query = new QueryBean();
    query.setFilter(FilterBeans.and(
        FilterBeans.ge("price", minPrice),
        FilterBeans.le("price", maxPrice)
    ));
    // ✅ 使用 doFindList() 而非 dao().findAllByQuery()，会自动应用数据权限
    return doFindList(query);
}
```

### 分页查询

```java
// ✅ 使用内置 findPage 方法，自动设置 offset/limit
// 前端调用：Product__findPage(pageNo:1, pageSize:10) { ... }
// 框架会自动在 QueryBean 中设置 offset=(pageNo-1)*pageSize 和 limit=pageSize
// 无需在代码中手动设置 offset/limit

// 如果需要自定义查询，可以这样：
@BizQuery
public PageBean<Product> searchProducts(@Name("request") Map<String, Object> request,
                                       FieldSelectionBean selection, IServiceContext context) {
    QueryBean query = new QueryBean();

    if (request.containsKey("keyword")) {
        query.addFilter(FilterBeans.contains("productName", request.get("keyword")));
    }

    // ✅ 使用内置 findPage 方法，QueryBean 中不设置 offset/limit
    // 框架会自动根据前端传入的 pageNo/pageSize 处理
    return findPage(query, selection, context);
}
```

---

## 任务3：新增变更API

### 创建

```java
@BizMutation
public Product createProduct(@Name("product") Product product) {
    // ✅ 使用 doSave() 而非 dao().saveEntity()，会触发默认回调
    return doSave(product);
}
```

### 模型驱动 CRUD 说明

**Nop 平台使用模型驱动架构**：

1. **内置 CRUD 操作**：继承 CrudBizModel 后，已经自动内置了完整的 CRUD 操作
2. **参数使用 Map/QueryBean**：不使用自定义 DTO 对象，直接使用 Map 和 QueryBean
3. **元数据驱动**：通过 xmeta 元数据模型定义数据结构和验证规则
4. **字段级别自适应**：修改模型后自动适应，无需重新生成代码

**内置方法（无需编程）**:

| 操作 | GraphQL 调用 | REST 调用 | 参数类型 |
|------|-------------|-----------|---------|
| 分页查询 | Product__findPage(...) | POST /r/Product__findPage | QueryBean |
| 单条查询 | Product__get(...) | POST /r/Product__get | Map {id} |
| 保存 | Product__save(...) | POST /r/Product__save | Map {数据} |
| 更新 | Product__update(...) | POST /r/Product__update | Map {id, 数据} |
| 删除 | Product__delete(...) | POST /r/Product__delete | Map {id} |
| 批量操作 | Product__batchSave(...) | POST /r/Product__batchSave | Map/QueryBean |

**无需手动编写简单 CRUD 方法**！

---

## 任务3：扩展点使用

当内置 CRUD 操作不能满足业务需求时，通过重写扩展点来自定义行为。

### 保存前处理

```java
@BizModel("Product")
public class ProductBizModel extends CrudBizModel<Product> {

    @Override
    protected void defaultPrepareSave(EntityData<Product> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        // 自定义逻辑：价格校验
        Product product = entityData.getEntity();
        if (product.getPrice() == null || product.getPrice() < 0) {
            throw new NopException(Errors.ERR_INVALID_PRICE)
                .param("price", product.getPrice());
        }
    }
}
```

### 查询前处理

```java
@BizModel("Product")
public class ProductBizModel extends CrudBizModel<Product> {

    @Override
    protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
        super.defaultPrepareQuery(query, context);

        // 自定义逻辑：自动添加租户过滤
        query.addFilter(FilterBeans.eq("tenantId", context.getUserContext().getTenantId()));
    }
}
```

## 任务4：模型驱动字段扩展

**Nop 平台支持模型驱动的字段扩展，无需修改代码！**

### ORM 模型修改

```xml
<!-- myapp-codegen/src/main/resources/_vfs/model/myapp.orm.xml -->
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     appName="myapp" defaultSchema="myapp">

    <entities>
        <entity name="Product" className="com.example.myapp.dao.entity.Product"
                displayName="产品" tableName="t_product">
            <columns>
                <column name="productId" stdDomain="string" primary="true"
                        displayName="产品ID" length="32" />

                <column name="productName" stdDomain="string" displayName="产品名称"
                                length="200" />

                <column name="price" stdDomain="decimal" displayName="价格"
                                precision="10" scale="2" />

                <column name="stock" stdDomain="int" displayName="库存"
                                defaultValue="0" />

                <!-- 新增字段：直接在模型中添加 -->
                <column name="description" stdDomain="text" displayName="产品描述" />
                <column name="category" stdDomain="string" displayName="产品分类"
                                length="100" />
                <column name="status" stdDomain="int" displayName="状态"
                                defaultValue="1" />
            </columns>
        </entity>
    </entities>
</orm>
```

### 重新生成代码

```bash
cd myapp-codegen
mvn clean install
cd ../myapp-dao
mvn clean install
```

### 数据库迁移

```sql
ALTER TABLE t_product ADD COLUMN description TEXT;
ALTER TABLE t_product ADD COLUMN category VARCHAR(100);
ALTER TABLE t_product ADD COLUMN status INT DEFAULT 1;
```

### 立即生效

**无需修改任何业务代码！** 修改模型后：
1. CRUD 接口自动支持新字段
2. 前端可以直接传递新字段
3. Map/QueryBean 参数自动支持新字段
4. 无需重新生成 DTO

**前端调用示例**:

```javascript
// GraphQL
mutation {
  Product__save(data: {
    productName: "新产品",
    price: 100,
    stock: 50,
    description: "这是产品描述",  // 新增字段，立即可用
    category: "电子产品",        // 新增字段，立即可用
    status: 1                    // 新增字段，立即可用
  }) {
    productId
    productName
    description  // 新字段自动返回
  }
}

// REST
POST /r/Product__save
{
  "data": {
    "productName": "新产品",
    "price": 100,
    "stock": 50,
    "description": "这是产品描述",
    "category": "电子产品",
    "status": 1
  }
}
```

---

## 任务5：定制内置模块

### Delta ORM 模型

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/nop-auth.orm.xml`:

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super">

    <entities>
        <entity className="com.example.myapp.delta.dao.entity.NopAuthUserEx"
                displayName="用户" name="io.nop.auth.dao.entity.NopAuthUser"
                tableName="nop_auth_user">
            <columns>
                <column name="userId" stdDomain="string" primary="true"
                        displayName="用户ID" length="32" tag="not-gen" />
                <column name="userName" stdDomain="string" displayName="用户名"
                        length="100" tag="not-gen" />

                <!-- 扩展字段 -->
                <column name="mobile" stdDomain="string" displayName="手机号"
                        length="20" />
                <column name="wechat" stdDomain="string" displayName="微信号"
                        length="50" />
            </columns>
        </entity>
    </entities>
</orm>
```

### Delta BizModel（扩展点重写）

```java
@BizModel("NopAuthUser")
public class NopAuthUserExBizModel extends NopAuthUserBizModel {

    @Override
    protected void defaultPrepareSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        // 自定义逻辑：手机号校验
        NopAuthUser user = entityData.getEntity();
        if (user.getMobile() != null) {
            if (!user.getMobile().matches("^1[3-9]\\d{9}$")) {
                throw new NopException(ERR_INVALID_MOBILE)
                    .param("mobile", user.getMobile());
            }
        }
    }
}
```

### Delta Bean 配置

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/auth-service.beans.xml`:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">

    <bean id="io.nop.auth.service.entity.NopAuthUserBizModel"
          class="com.example.myapp.delta.biz.NopAuthUserExBizModel" />

</beans>
```

---

## 任务6：复杂查询（需要编程的场景）

### 自定义复杂查询（通过 QueryBean）

**注意**: 大部分查询场景可以直接使用内置方法配合 QueryBean/Map 参数。

```java
@BizModel("Product")
public class ProductBizModel extends CrudBizModel<Product> {

    // ✅ 自定义复杂查询：使用 Map/QueryBean 作为参数
    @BizQuery
    public PageBean<Product> searchProducts(@Name("request") Map<String, Object> request,
                                       FieldSelectionBean selection, IServiceContext context) {
        QueryBean query = new QueryBean();

        List<TreeBean> filters = new ArrayList<>();

        // 从 Map 中获取参数
        String keyword = (String) request.get("keyword");
        BigDecimal minPrice = (BigDecimal) request.get("minPrice");
        BigDecimal maxPrice = (BigDecimal) request.get("maxPrice");
        String category = (String) request.get("category");

        if (StringHelper.isNotEmpty(keyword)) {
            filters.add(FilterBeans.or(
                FilterBeans.contains("productName", keyword),
                FilterBeans.contains("description", keyword)
            ));
        }

        if (minPrice != null) {
            filters.add(FilterBeans.ge("price", minPrice));
        }
        if (maxPrice != null) {
            filters.add(FilterBeans.le("price", maxPrice));
        }

        if (StringHelper.isNotEmpty(category)) {
            filters.add(FilterBeans.eq("category", category));
        }

        if (!filters.isEmpty()) {
            query.setFilter(FilterBeans.and(filters));
        }

        // ✅ 使用 doFindPage，自动应用数据权限
        return doFindPage(query, selection, context);
    }
}
```

**前端调用**:
```graphql
query {
  Product__searchProducts(request: {
    keyword: "手机",
    minPrice: 1000,
    maxPrice: 5000,
    category: "电子产品"
  }, pageNo: 1, pageSize: 10) {
    productId
    productName
    price
    description
  }
}
```

---

## 任务7：模型驱动事务操作

### 复杂业务逻辑（使用 Map/QueryBean 参数）

```java
@BizModel("Product")
public class ProductBizModel extends CrudBizModel<Product> {

    // ✅ 复杂业务逻辑：使用 Map/QueryBean 作为参数
    @BizMutation
    public void transferStock(@Name("request") Map<String, Object> request, IServiceContext context) {
        // 注意：@BizMutation 已自动开启事务，无需使用 txn()

        String fromProductId = (String) request.get("fromProductId");
        String toProductId = (String) request.get("toProductId");
        Integer quantity = (Integer) request.get("quantity");

        // ✅ 使用 requireEntity，自动应用数据权限
        Product from = requireEntity(fromProductId);
        Product to = requireEntity(toProductId);

        if (from.getStock() < quantity) {
            throw new NopException(ERR_INSUFFICIENT_STOCK)
                .param("productId", fromProductId);
        }

        from.setStock(from.getStock() - quantity);
        to.setStock(to.getStock() + quantity);

        // ✅ 使用 doUpdate，自动触发回调
        doUpdate(from);
        doUpdate(to);
    }
}
```

**前端调用**:
```graphql
mutation {
  Product__transferStock(request: {
    fromProductId: "xxx",
    toProductId: "yyy",
    quantity: 10
  })
}
```

---

## 任务8：模型驱动异常处理

### 定义错误码（使用 xmeta 元数据）

**Nop 平台使用 xmeta 元数据定义错误码，无需手动创建错误码接口。**

```xml
<!-- myapp-meta/src/main/resources/_vfs/meta/myapp.errors.xdef -->
<errors x:schema="/nop/schema/errors/errors.xdef">
    <error name="ERR_PRODUCT_NOT_FOUND"
           errorCode="my.err.product.not-found"
           defaultLocale="zh-CN"
           message="产品[{productId}]不存在">
        <params>
            <param name="productId" type="string"/>
        </params>
    </error>

    <error name="ERR_INSUFFICIENT_STOCK"
           errorCode="my.err.stock.insufficient"
           defaultLocale="zh-CN"
           message="产品[{productId}]库存不足">
        <params>
            <param name="productId" type="string"/>
        </params>
    </error>
</errors>
```

### 抛出异常（在扩展点中使用）

```java
@BizModel("Product")
public class ProductBizModel extends CrudBizModel<Product> {

    @Override
    protected void defaultPrepareSave(EntityData<Product> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        Product product = entityData.getEntity();

        // 自定义验证
        if (product.getStock() < 0) {
            throw new NopException("my.err.stock.insufficient")
                .param("productId", product.getProductId());
        }
    }
}
```

---

## 相关文档

- [10分钟快速上手](./10-min-quickstart.md)
- [Delta定制基础](../delta/delta-basics.md)
- [代码生成概念](../codegen/codegen-concepts.md)

---

