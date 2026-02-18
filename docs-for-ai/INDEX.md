# Nop Platform AI Documentation Index

## Core Principle

Nop platform is a low-code platform based on Reversible Computation: `App = Delta x-extends Generator<DSL>`.

- **Model-driven**: DSL models define business structure → generate code (entities, APIs, etc.)
- **Delta customization**: modify/extend WITHOUT changing base source code
- **Framework-agnostic**: runs on Spring/Quarkus/Solon
- **Incremental code generation**: `_gen/` and `_`-prefixed files auto-overwritten; hand-written code in separate files with inheritance

**Key**: Before coding, check if code can be derived from models. Extend generated code via Delta/inheritance.

---

## 🚀 AI 驱动的完整开发流程

### 开发流程总览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        AI 驱动的 Nop 开发流程                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1️⃣ 定义实体 ORM 模型                                                        │
│     └── 在 model/xxx.orm.xml 中定义数据结构                                  │
│                                                                             │
│  2️⃣ 生成基础代码                                                             │
│     └── 在xxx-codegen模块下运行 mvn install，生成 Entity/XMeta/BizModel          │
│                                                                             │
│  3️⃣ 规划 BizModel 和 IXXBiz 接口                                             │
│     ├── 确定哪些方法需要在 IXXBiz 接口中定义（被其他 BizModel 调用）            │
│     └── 确定哪些方法只在 BizModel 类中定义（仅 GraphQL/REST 调用）             │
│                                                                             │
│  4️⃣ 确定代码放置位置                                                         │
│     ├── Entity（聚合根）：只读帮助函数、状态查询                               │
│     ├── BizModel：可定制的修改操作、跨聚合操作                                 │
│     └── Processor/Step：复杂业务流程、可复用逻辑                              │
│                                                                             │
│  5️⃣ AI 自动回顾设计                                                          │
│     ├── 检查是否符合 DDD 原则                                                │
│     ├── 检查是否遵循平台规范                                                  │
│     └── 识别潜在问题和优化点                                                  │
│                                                                             │
│  6️⃣ 修正设计并制定开发计划                                                    │
│     ├── 根据回顾结果调整设计                                                  │
│     └── 拆分为具体开发任务                                                    │
│                                                                             │
│  7️⃣ 执行开发计划                                                             │
│     ├── 实现 Entity 方法                                                     │
│     ├── 实现 BizModel 方法                                                   │
│     ├── 实现 Processor/Step（如需要）                                        │
│     └── 编写测试                                                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 步骤详解

#### 1️⃣ 定义实体 ORM 模型

在 `model/xxx.orm.xml` 中定义数据结构：

```xml
<orm appName="myapp" defaultSchema="myapp">
    <entities>
        <entity name="Order" tableName="t_order">
            <columns>
                <column name="orderId" stdDomain="string" primary="true"/>
                <column name="orderStatus" stdDomain="int" defaultValue="101"/>
                <!-- 更多字段... -->
            </columns>
            <relations>
                <to-many name="items" refEntityName="OrderItem" joinKey="orderId"/>
            </relations>
        </entity>
    </entities>
</orm>
```

#### 2️⃣ 生成基础代码

```bash
# 首次生成（仅需一次）
cd myapp
nop-cli gen model/myapp.orm.xml -t=/nop/templates/orm -o=.

# 后续模型变更后重新生成
cd myapp-codegen && mvn install
```

#### 3️⃣ 规划 BizModel 和 IXXBiz 接口

**IXXBiz 接口定义规则**：

| 场景 | 处理方式 |
|------|---------|
| 方法需要被**其他 BizModel 调用** | ✅ 在 IXXBiz 接口中定义 |
| 只通过 GraphQL/REST 调用 | ❌ 直接在 BizModel 类中定义 |
| 需要在 Delta 模块中覆盖 | ✅ 在 IXXBiz 接口中定义 |

```java
// dao 模块中的接口
public interface ILitemallOrderBiz extends ICrudBiz<LitemallOrder> {
    // 被其他 BizModel 调用的方法
    LitemallOrder cancel(@Name("orderId") String orderId, IServiceContext context);
    List<LitemallOrder> getOrdersByUser(@Name("userId") String userId,
                                         FieldSelectionBean selection,
                                         IServiceContext context);
}
```

#### 4️⃣ 确定代码放置位置

| 逻辑类型 | 放置位置 | 原因 |
|---------|---------|------|
| 纯函数，读取字段/关联 | **Entity** | 稳定的领域事实 |
| 状态查询 (canXxx, isXxx) | **Entity** | 稳定的领域事实 |
| 简单修改操作 | **BizModel** | 可定制的业务行为 |
| 跨聚合操作 | **BizModel** | 需要协调多个实体 |
| 调用外部服务 | **BizModel** | 易变的集成逻辑 |
| 复用性高的业务规则 | **Processor** | 多处复用 |
| 复杂流程/多步骤 | **Processor** | 降低 BizModel 复杂度 |

#### 5️⃣ AI 自动回顾设计

AI 应检查以下方面：

- [ ] **Entity 方法**：是否只包含只读操作？是否有修改操作？
- [ ] **BizModel 方法**：是否正确使用 `@BizQuery`/`@BizMutation` 注解？
- [ ] **参数规范**：最后一个参数是否为 `IServiceContext`？所有参数是否都有 `@Name` 注解？
- [ ] **接口定义**：被其他 BizModel 调用的方法是否在接口中定义？
- [ ] **数据访问**：是否使用 `requireEntity()`/`doFindList()` 而非 `dao().xxx()`？
- [ ] **事务管理**：`@BizMutation` 方法中是否冗余使用 `@Transactional`？
- [ ] **职责划分**：方法是否过长（>50行）需要拆分为 Processor？

#### 6️⃣ 修正设计并制定开发计划

根据回顾结果调整设计，然后制定具体开发任务：

```
任务清单示例：
1. [Entity] Order.canBeCancelled() - 判断订单是否可取消
2. [Entity] Order.calculateTotal() - 计算订单总价
3. [IXXBiz] 定义 ILitemallOrderBiz 接口
4. [BizModel] LitemallOrderBizModel.cancel() - 取消订单
5. [BizModel] LitemallOrderBizModel.submitOrder() - 提交订单
6. [Processor] LitemallOrderSubmitProcessor - 订单提交流程
7. [Step] InventoryDeductStep - 库存扣减
8. [Test] 编写单元测试
```

#### 7️⃣ 执行开发计划

按照任务清单逐一实现，每个任务完成后验证。

> **BizModel 编写规范**: 详见 `03-development-guide/bizmodel-guide.md`

---

## Development Scenarios

### Scenario 1: XDef Meta-Model Development (No Database)

For DSL-based systems like `nop-gateway` without database persistence.

**Workflow:** `Define XDef → Compile nop-xdefs → precompile script → mvn install`

1. **Define XDef schema** in `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/`
   ```xml
   <gateway xdef:bean-package="io.nop.gateway.model" xdef:name="GatewayModel">
       <routes xdef:body-type="list" xdef:key-attr="id">
           <route id="!string" xdef:name="GatewayRouteModel">...</route>
       </routes>
   </gateway>
   ```

2. **Compile nop-xdefs**: `cd nop-kernel/nop-xdefs && mvn install`

3. **Add precompile script** in `precompile/gen-xxx-ast.xgen`:
   ```xml
   <c:script>
       codeGenerator.renderModel('/nop/schema/gateway.xdef','/nop/templates/xdsl', '/',$scope);
   </c:script>
   ```

4. **Run mvn install** - parent POM's `exec-maven-plugin` auto-executes precompile

5. **Generated files** in `_gen/` - extend with non-underscored class:
   ```java
   public class GatewayRouteModel extends _GatewayRouteModel { ... }
   ```

**Reference:** `05-xlang/xdef-core.md`

---

### Scenario 2: Database-Backed Module Development (ORM)

For modules with database persistence.

**Workflow:** `Create ORM model → nop-cli gen (once) → mvn install codegen for changes`

1. **Define ORM model** in `model/nop-xxx.orm.xml`

2. **Generate scaffold** (only once):
   ```bash
   cd nop-xxx
   .opencode/scripts/nop-cli.sh gen model/nop-xxx.orm.xml -t=/nop/templates/orm -o=.
   ```

3. **Model changes**: `cd nop-xxx-codegen && mvn install` - auto-regenerates

**Key Points:**
- `_gen/` and `_`-prefixed files: AUTO-OVERWRITTEN, never edit
- Non-underscored files: YOUR code, preserved
- Use `x:extends` (XML/JSON/YAML) or Java inheritance

**Reference:** `03-development-guide/project-structure.md`

---

### Scenario 3: Feature Development

When models are stable, focus on business logic.

**Key Principles:**
1. **CRUD** - NO coding needed, inherited from `CrudBizModel`, uses `Map<String, Object>` + xmeta validation
2. **DDD** - Entity: read-only helpers; BizModel: mutable logic; Complex: `XXXProcessor`
3. **Testing** - Use `nop-autotest` (auto-records snapshots)

**Reference:** `03-development-guide/service-layer.md`, `03-development-guide/ddd-in-nop.md`

---

## Nop DDD 代码划分策略

### 三层代码组织

```
┌─────────────────────────────────────────────────────────────────┐
│                        Entity (实体类)                           │
│  - 位置：dao 模块                                                 │
│  - 职责：稳定的领域结构 + 只读帮助函数                              │
│  - 特点：不可通过 Delta 定制                                       │
│  - 示例：canBeCancelled(), calculateTotal()                      │
├─────────────────────────────────────────────────────────────────┤
│                      BizModel (业务模型)                          │
│  - 位置：service 模块                                             │
│  - 职责：可定制的业务逻辑、修改操作                                  │
│  - 特点：可通过 Delta/xbiz 定制                                    │
│  - 示例：cancel(), ship(), checkout()                             │
├─────────────────────────────────────────────────────────────────┤
│                   Processor (复杂处理器)                          │
│  - 位置：service 模块，通过 beans.xml 配置                         │
│  - 职责：复用性高的业务逻辑、复杂流程                                │
│  - 特点：可 Inject 到多个 BizModel                                 │
│  - 示例：PaymentProcessor, InventoryProcessor                     │
└─────────────────────────────────────────────────────────────────┘
```

### 代码放置判断规则

| 逻辑类型 | 放置位置 | 原因 |
|---------|---------|------|
| 纯函数，读取字段/关联 | **Entity** | 稳定的领域事实 |
| 状态查询 (canXxx, isXxx) | **Entity** | 稳定的领域事实 |
| 简单修改操作 | **BizModel** | 可定制的业务行为 |
| 跨聚合操作 | **BizModel** | 需要协调多个实体 |
| 调用外部服务 | **BizModel** | 易变的集成逻辑 |
| 复用性高的业务规则 | **Processor** | 多处复用 |
| 复杂流程/多步骤 | **Processor** | 降低 BizModel 复杂度 |

### 何时拆分 Processor

当 BizModel 方法出现以下情况时，应考虑拆分 Processor：
1. 单个方法超过 50 行
2. 需要在多个 BizModel 间复用
3. 涉及外部服务调用（支付、库存、风控等）
4. 业务规则复杂且可能变化

```java
// BizModel 中注入 Processor
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> {
    
    @Inject  // import jakarta.inject.Inject;
    PaymentProcessor paymentProcessor;  // 通过 beans.xml 配置
    
    @BizMutation
    public Order pay(@Name("orderId") String orderId, IServiceContext context) {
        Order order = requireEntity(orderId, "update", context);
        paymentProcessor.processPayment(order);  // 委托给 Processor
        updateEntity(order, context);
        return order;
    }
}
```

**详细指南:** `03-development-guide/bizmodel-guide.md`, `03-development-guide/processor-development.md`

---

## 快速参考

### By Task

| Task | Reference |
|------|-----------|
| **BizModel 编写** | `03-development-guide/bizmodel-guide.md` |
| **Processor 开发** | `03-development-guide/processor-development.md` |
| **DTO 规范** | `04-core-components/dto-standards.md` |
| CRUD / Service | `03-development-guide/service-layer.md` |

---

### Scenario 4: Delta Customization

For customizing base products without modifying source.

```xml
<meta x:extends="super,_NopAuthUser.xmeta">
    <props>
        <prop name="customField" displayName="Custom Field"/>
    </props>
</meta>
```

**Reference:** `01-core-concepts/delta-basics.md`

---

## Quick Reference

### By Task

| Task | Reference |
|------|-----------|
| CRUD / Service | `03-development-guide/service-layer.md` |
| Queries | `03-development-guide/data-access.md`, `03-development-guide/querybean-guide.md` |
| DDD patterns | `03-development-guide/ddd-in-nop.md` |
| CRUD hooks | `12-tasks/extend-crud-with-hooks.md` |
| Transactions | `04-core-components/transaction.md` |
| Exceptions | `04-core-components/exception-handling.md` |
| Testing | `07-best-practices/testing.md`, `11-test-and-debug/autotest-guide.md` |

### By Component

| Component | Reference |
|-----------|-----------|
| IoC | `04-core-components/ioc-container.md` |
| Config (@InjectValue) | `04-core-components/config-management.md` |
| Error codes | `04-core-components/error-codes.md` |
| XDef/XMeta | `05-xlang/xdef-core.md`, `05-xlang/meta-programming.md` |
| ORM advanced | `03-development-guide/orm-advanced-features.md` |

---

## Code Patterns

### CrudBizModel

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {
    // Built-in: findPage, get, save, update, delete - NO need to implement

    @BizQuery
    public List<User> findActiveUsers(FieldSelectionBean selection, IServiceContext context) {
        QueryBean query = new QueryBean();
        query.setFilter(FilterBeans.eq("status", 1));
        return doFindList(query, selection, context);
    }

    @BizMutation  // Auto-transaction
    public void activateUser(@Name("userId") String userId, IServiceContext context) {
        User user = requireEntity(userId, "update", context);
        user.setStatus(UserConstants.ACTIVE);
        updateEntity(user, context);
    }
}
```

**Preferred:** `requireEntity()`, `doFindList()`, `doFindPage()`, `save()`, `update()`
**Avoid:** `dao().xxx()` - bypasses data permissions

### Entity (DDD)

```java
public class Order extends OrmEntity {
    // ✅ Read-only helper
    public boolean canBeCancelled() {
        return OrderConstants.PENDING.equals(this.status);
    }

    // ✅ Master-detail: use orm.xml associations (getItems)
    public BigDecimal calculateTotal() {
        return getItems().stream()
            .map(OrderItem::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ✅ Counting: requireBiz + findCount
    public long getIncompleteTaskCount() {
        IServiceContext context = IServiceContext.requireCtx();
        IOrderTaskBiz taskBiz = requireBiz(IOrderTaskBiz.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("orderId", this.getId()));
        return taskBiz.findCount(query, context);
    }
}
```

**Rules:**
- Master-detail (aggregate): `getItems()` from orm.xml
- Outside aggregate / counting: `requireBiz` + `findCount`/`findPage`

### Exception

```java
throw new NopException(MyErrors.ERR_FIELD_REQUIRED).param("field", "name");
```

---

## Critical Rules

### Code Generation
1. `_gen/` directories: **ALWAYS overwritten** - never edit
2. `_`-prefixed files: **ALWAYS overwritten** - never edit
3. Non-underscored files: **Preserved** - your custom code

### Entity vs BizModel

| | Entity | BizModel |
|--|--------|----------|
| Read-only helpers | ✅ | - |
| Mutable logic | ❌ | ✅ |
| Customizable | ❌ (stable) | ✅ (Delta) |

### CRUD
- Simple CRUD: No code needed
- Input: `Map<String, Object>` + xmeta validation
- Transaction: `@BizMutation` auto-enables

---

## Directory Mapping

| Directory | Purpose |
|-----------|---------|
| `00-quick-start/` | Getting started |
| `01-core-concepts/` | Platform fundamentals |
| `02-architecture/` | System architecture |
| `03-development-guide/` | Development guides |
| `04-core-components/` | Core components |
| `05-xlang/` | XLang language |
| `06-utilities/` | Utility classes |
| `07-best-practices/` | Best practices |
| `08-examples/` | Code examples |
| `09-quick-reference/` | Quick references |
| `11-test-and-debug/` | Testing & debugging |
| `12-tasks/` | Task-based guides |
