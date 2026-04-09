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
mvn clean install
```

如果需要按模块顺序显式构建，可用：

```bash
cd myapp-codegen && mvn install
cd ../myapp-dao && mvn install
cd ../myapp-meta && mvn install
cd ../myapp-web && mvn install
cd ../myapp-service && mvn install
cd ../myapp-app && mvn install
```

说明：

- `myapp-codegen`：从源 ORM 模型刷新项目级生成产物
- `myapp-meta`：生成 XMeta / i18n
- `myapp-web`：基于 xmeta 生成页面文件

### 3. BizModel（简单 CRUD 无需代码）

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<LitemallOrder> {
    public OrderBizModel() {
        setEntityName(LitemallOrder.class.getName());
    }

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
| Biz接口 | `xxx-dao/.../biz/IOrderBiz.java` | 自动生成 |
| XMeta | `xxx-meta/_vfs/.../model/Order/_Order.xmeta` | 自动生成 |
| BizModel | `xxx-service/.../entity/OrderBizModel.java` | 生成初始骨架并保留 |
| xbiz 基类 | `xxx-service/_vfs/.../model/Order/_Order.xbiz` | 自动生成 |
| xbiz 扩展 | `xxx-service/_vfs/.../model/Order/Order.xbiz` | 非下划线扩展文件 |

## 常见坑

- ❌ 编辑 `_gen/` 目录或 `_` 前缀文件 → 自动覆盖丢失
- ❌ 误以为 `xxx-meta` 直接生成 service/web 代码 → 当前仓库实际是 `codegen` 刷新项目级产物，`meta` 生成 xmeta/i18n，`web` 基于 xmeta 生成页面
- ❌ 忘记设置 `className` → Entity 类名与预期不符

## 相关文档

- `03-development-guide/project-structure.md`
- `03-development-guide/crud-development.md`
- `01-core-concepts/ai-development.md`（跨模块代码生成详解）
