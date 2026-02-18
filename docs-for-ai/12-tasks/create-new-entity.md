# 创建新实体（ORM 定义 → 代码生成 → BizModel）

## 适用场景

- 需要新建数据库表对应的实体
- 需要为新实体提供 CRUD API

## AI 决策提示

- ✅ 优先：定义 ORM 模型 → mvn install 生成 → 继承 CrudBizModel
- ✅ 无需手写 Entity/DAO 代码，全部由代码生成器生成
- ✅ 简单 CRUD 无需写任何代码，CrudBizModel 内置即可

## 最小闭环

### 1. 定义 ORM 模型

在 `model/xxx.orm.xml` 中定义：

```xml
<orm appName="myapp" defaultSchema="myapp">
    <entities>
        <entity name="Order" tableName="t_order" className="app.mall.dao.entity.LitemallOrder">
            <columns>
                <column name="orderId" stdDomain="string" primary="true"/>
                <column name="userId" stdDomain="string"/>
                <column name="orderStatus" stdDomain="int" defaultValue="101"/>
                <column name="totalPrice" stdDomain="decimal" precision="10" scale="2"/>
                <column name="createTime" stdDomain="createTime"/>
                <column name="updateTime" stdDomain="updateTime"/>
            </columns>
            <relations>
                <to-many name="items" refEntityName="OrderItem" joinKey="orderId"/>
            </relations>
        </entity>
    </entities>
</orm>
```

### 2. 生成代码

```bash
# 首次生成（创建项目结构）
cd myapp
nop-cli gen model/myapp.orm.xml -t=/nop/templates/orm -o=.

# 后续模型变更后重新生成
cd myapp-codegen && mvn install
cd ../myapp-dao && mvn install
cd ../myapp-meta && mvn install   # 生成 service/web 模块代码
```

### 3. BizModel（简单 CRUD 无需代码）

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<LitemallOrder> {
    // CrudBizModel 已提供：findPage, get, save, update, delete
    // 无需写任何代码即可使用 CRUD API
}
```

### 4. 验证

- GraphQL: `/graphql` 查询 `Order__findPage`
- REST: `/r/Order__findPage`

## 生成文件位置

| 文件类型 | 位置 | 说明 |
|---------|------|------|
| Entity.java | `xxx-dao/_gen/.../entity/_Order.java` | 自动生成，不编辑 |
| EntityExt.java | `xxx-dao/.../entity/Order.java` | 手写扩展，继承 _Order |
| XMeta | `xxx-meta/_vfs/.../model/Order/_Order.xmeta` | 自动生成 |
| BizModel | `xxx-service/_gen/.../biz/_OrderBizModel.java` | 自动生成 |
| BizModelExt | `xxx-service/.../biz/OrderBizModel.java` | 手写扩展 |

## 常见坑

- ❌ 编辑 `_gen/` 目录或 `_` 前缀文件 → 自动覆盖丢失
- ❌ 跳过 `xxx-meta && mvn install` → service/web 代码未生成
- ❌ 忘记设置 `className` → Entity 类名与预期不符

## 相关文档

- `03-development-guide/project-structure.md`
- `03-development-guide/crud-development.md`
- `01-core-concepts/ai-development.md`（跨模块代码生成详解）
