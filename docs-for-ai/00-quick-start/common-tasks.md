# 常见开发任务快速参考

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
    private IDaoProvider daoProvider;

    protected IProductDao dao() {
        return daoProvider.dao(Product.class);
    }
}
```

---

## 任务2：新增查询API

### 简单查询

```java
@BizQuery
public Product getProduct(@Name("productId") String productId) {
    return dao().requireEntityById(productId);
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
    return dao().findAllByQuery(query);
}
```

### 分页查询

```java
@BizQuery
public PageBean<Product> searchProducts(@Name("keyword") String keyword, @Name("pageNo") int pageNo, @Name("pageSize") int pageSize) {
    QueryBean query = new QueryBean();

    if (StringHelper.isNotEmpty(keyword)) {
        query.setFilter(FilterBeans.contains("productName", keyword));
    }

    return findPage(query, pageNo, pageSize);
}
```

---

## 任务3：新增变更API

### 创建

```java
@BizMutation
public Product createProduct(@Name("product") Product product) {
    return dao().saveEntity(product);
}
```

### 更新

```java
@BizMutation
public Product updateProduct(@Name("product") Product product) {
    Product existing = dao().requireEntityById(product.getProductId());
    existing.setProductName(product.getProductName());
    existing.setPrice(product.getPrice());
    existing.setStock(product.getStock());
    return dao().saveEntity(existing);
}
```

### 删除

```java
@BizMutation
public void deleteProduct(@Name("productId") String productId) {
    Product product = dao().requireEntityById(productId);
    dao().deleteEntity(product);
}
```

---

## 任务4：添加字段

### ORM模型

```xml
<entity name="Product" className="com.example.myapp.dao.entity.Product"
        displayName="产品" tableName="t_product">
    <columns>
        <!-- 已有字段 -->
        <column name="productId" stdDomain="string" primary="true"
                displayName="产品ID" length="32" />
        <column name="productName" stdDomain="string" displayName="产品名称"
                        length="200" />

        <!-- 新增字段 -->
        <column name="description" stdDomain="text" displayName="产品描述" />
        <column name="category" stdDomain="string" displayName="产品分类"
                        length="100" />
        <column name="status" stdDomain="int" displayName="状态"
                        defaultValue="1" />
    </columns>
</entity>
```

### 重新生成

```bash
cd myapp-codegen
mvn clean install
cd ../myapp-dao
mvn clean install
```

### 数据库

```sql
ALTER TABLE t_product ADD COLUMN description TEXT;
ALTER TABLE t_product ADD COLUMN category VARCHAR(100);
ALTER TABLE t_product ADD COLUMN status INT DEFAULT 1;
```

---

## 任务5：定制内置模块

### Delta ORM模型

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

                <column name="mobile" stdDomain="string" displayName="手机号"
                        length="20" />
                <column name="wechat" stdDomain="string" displayName="微信号"
                        length="50" />
            </columns>
        </entity>
    </entities>
</orm>
```

### Delta BizModel

```java
@BizModel("NopAuthUser")
public class NopAuthUserExBizModel extends NopAuthUserBizModel {

    @Override
    protected void defaultPrepareSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

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

### Delta Bean配置

`myapp-delta/src/main/resources/_vfs/_delta/default/nop/auth/auth-service.beans.xml`:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">

    <bean id="io.nop.auth.service.entity.NopAuthUserBizModel"
          class="com.example.myapp.delta.biz.NopAuthUserExBizModel" />

</beans>
```

---

## 任务6：复杂查询

### 多条件查询

```java
@BizQuery
public PageBean<Product> searchProducts(@Name("request") ProductSearchRequest request) {
    QueryBean query = new QueryBean();

    List<TreeBean> filters = new ArrayList<>();

    if (StringHelper.isNotEmpty(request.getKeyword())) {
        filters.add(FilterBeans.or(
            FilterBeans.contains("productName", request.getKeyword()),
            FilterBeans.contains("description", request.getKeyword())
        ));
    }

    if (request.getMinPrice() != null) {
        filters.add(FilterBeans.ge("price", request.getMinPrice()));
    }
    if (request.getMaxPrice() != null) {
        filters.add(FilterBeans.le("price", request.getMaxPrice()));
    }

    if (StringHelper.isNotEmpty(request.getCategory())) {
        filters.add(FilterBeans.eq("category", request.getCategory()));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }

    if (StringHelper.isNotEmpty(request.getSortField())) {
        OrderFieldBean order = new OrderFieldBean();
        order.setName(request.getSortField());
        order.setDesc(request.isDesc());
        query.setOrders(Collections.singletonList(order));
    }

    return findPage(query, request.getPageNo(), request.getPageSize());
}
```

---

## 任务7：使用事务

### 简单事务

```java
@BizMutation
public Product createProduct(@Name("product") Product product) {
    return dao().saveEntity(product);
}
```

### 复杂事务

```java
@BizMutation
public void transferStock(@Name("fromProductId") String fromProductId, @Name("toProductId") String toProductId, @Name("quantity") int quantity) {
    // 注意：@BizMutation 已自动开启事务，无需使用 txn()
    Product from = dao().requireEntityById(fromProductId);
    Product to = dao().requireEntityById(toProductId);

    if (from.getStock() < quantity) {
        throw new NopException(ERR_INSUFFICIENT_STOCK)
            .param("productId", fromProductId);
    }

    from.setStock(from.getStock() - quantity);
    to.setStock(to.getStock() + quantity);

    dao().saveEntity(from);
    dao().saveEntity(to);
}
```

---

## 任务8：异常处理

### 定义错误码

```java
@Locale("zh-CN")
public interface MyErrors {
    String ARG_PRODUCT_ID = "productId";

    ErrorCode ERR_PRODUCT_NOT_FOUND = define("my.err.product.not-found",
        "产品[{productId}]不存在", ARG_PRODUCT_ID);
    ErrorCode ERR_INSUFFICIENT_STOCK = define("my.err.stock.insufficient",
        "产品[{productId}]库存不足", ARG_PRODUCT_ID);
}
```

### 抛出异常

```java
@BizMutation
public Product updateStock(@Name("productId") String productId, @Name("deltaStock") int deltaStock) {
    Product product = dao().requireEntityById(productId);

    if (product.getStock() + deltaStock < 0) {
        throw new NopException(MyErrors.ERR_INSUFFICIENT_STOCK)
            .param(MyErrors.ARG_PRODUCT_ID, productId);
    }

    product.setStock(product.getStock() + deltaStock);
    return dao().saveEntity(product);
}
```

---

## 相关文档

- [10分钟快速上手](./10-min-quickstart.md)
- [Delta定制基础](../delta/delta-basics.md)
- [代码生成概念](../codegen/codegen-concepts.md)

---

