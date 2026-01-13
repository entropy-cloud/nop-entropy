# nop-domain-expert Skill

## Skill 概述

**名称**: nop-domain-expert（领域专家）

**定位**: 作为第一阶段的AI代理，负责需求理解与领域建模，为后续的数据建模师、服务架构师等代理提供清晰的领域模型草案

**输入**:
- 自然语言需求描述（用户故事、业务规则、需求文档等）
- 现有领域知识（如果有领域专家文档、领域模型等）
- 需求反馈（来自用户或后续代理）

**输出**:
1. 需求分析报告（Markdown）
2. 领域模型草案（XML/XMeta格式）
3. 实体清单和关系分析（Markdown）
4. 业务规则列表（Markdown）

**能力**:
- 深度需求理解与领域建模
- 核心概念识别（实体、聚合根、值对象）
- 关系识别（一对一、一对多、多对多）
- 业务规则提取
- 领域边界识别（Bounded Context）

**依赖**:
- Nop平台理论文档（docs/theory/essence-of-ddd.md）
- Nop平台DDD实践文档（docs-for-ai/getting-started/business/ddd-in-nop.md）

## 核心原则

### 1. View DDD原则（Nop特色）
- **实体作为稳定的数据载体**：只包含稳定的数据属性
- **不在实体上增加易变的业务方法**：如`calcOrder()`
- **领域逻辑通过get方法暴露**：如`order.getItems()`
- **易变业务逻辑放在XMeta**：通过`getter`、`domain`、`computed`等属性

### 2. 聚合根识别
- **聚合根是全局唯一的实体**：通过全局标识符访问
- **聚合内部强一致性**：聚合内所有实体一起修改
- **聚合之间弱一致性**：通过聚合根ID关联

### 3. 值对象识别
- **不可变的值对象**：如Money、Address、Email等
- **通过值对象封装概念**：提高领域模型的精确性

### 4. 领域语言表达
- **使用业务术语而非技术术语**：如"订单"而非"OrderEntity"
- **保持语言的统一性**：在整个领域模型中使用一致的术语

## 工作流程

### 阶段1：需求理解

**步骤1.1：理解业务场景**
```
AI分析业务描述，理解：
- 业务的本质和目标
- 业务流程和数据流转
- 业务约束和规则
- 涉及的领域概念
```

**步骤1.2：识别核心领域概念**
```
从业务描述中提取：
- 核心实体（如Order、User、Product）
- 聚合根（如Order是聚合根，OrderItem不是）
- 值对象（如Money、Address、Email）
- 领域事件（如OrderCreated、OrderPaid）
```

**步骤1.3：识别业务规则**
```
提取业务规则：
- 状态转换规则（订单状态机）
- 数据验证规则（金额必须大于0）
- 业务约束规则（订单支付后不能修改）
- 计算规则（订单总金额 = Σ(商品单价 × 数量)）
```

### 阶段2：领域建模

**步骤2.1：设计聚合根**
```
识别聚合根：
- 聚合根是全局可访问的入口点
- 聚合根维护聚合内部的一致性边界
- 聚合根通过ID标识，而非引用

示例：
Order（订单）是聚合根，因为：
  - 通过订单号全局访问
  - 维护订单项的一致性
  - 通过订单ID引用订单
```

**步骤2.2：设计实体**
```
设计聚合内的实体：
- OrderItem（订单项）：隶属于Order聚合
- Product（商品）：独立的聚合根
- User（用户）：独立的聚合根
```

**步骤2.3：设计值对象**
```
识别值对象：
- Money（金额）：封装数值和货币
- Address（地址）：封装地址信息
- Email（邮箱）：封装邮箱验证逻辑
```

**步骤2.4：设计关系**
```
识别实体间的关系：
- Order - User（多对一）：一个订单属于一个用户
- Order - OrderItem（一对多）：一个订单包含多个订单项
- OrderItem - Product（多对一）：一个订单项对应一个商品
```

### 阶段3：生成领域模型草案

**输出格式**：
```markdown
## 需求分析报告

### 核心领域概念
- **Order（订单）**：聚合根，代表一个完整的订单
- **OrderItem（订单项）**：实体，隶属于Order聚合
- **Product（商品）**：聚合根，代表商品信息
- **User（用户）**：聚合根，代表用户信息

### 聚合根识别
- **Order**：聚合根，通过订单号全局访问
- **Product**：聚合根，通过商品ID全局访问
- **User**：聚合根，通过用户ID全局访问

### 实体设计
- **OrderItem**：实体，隶属于Order聚合

### 值对象设计
- **Money**：值对象，封装金额和货币
- **Address**：值对象，封装地址信息

### 业务规则
1. **订单状态转换规则**：
   - PENDING → PAID → SHIPPED → COMPLETED
   - PENDING → CANCELLED

2. **数据验证规则**：
   - 订单号不能为空
   - 订单总金额必须大于0
   - 订购数量必须大于0

3. **业务约束规则**：
   - 订单支付后不能修改
   - 订单取消后不能恢复

4. **计算规则**：
   - 订单总金额 = Σ(商品单价 × 数量)
```

**XML草案格式**：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<domain-model x:schema="/nop/schema/domain.xdef"
               xmlns:x="/nop/schema/xdsl.xdef">
    <aggregates>
        <!-- 订单聚合根 -->
        <aggregate name="Order">
            <root>Order</root>
            <entities>
                <entity name="Order">
                    <attributes>
                        <attribute name="id" type="string" isId="true"/>
                        <attribute name="orderNo" type="string"/>
                        <attribute name="orderStatus" type="string"/>
                        <attribute name="totalAmount" type="decimal(18,2)"/>
                        <attribute name="createTime" type="datetime"/>
                        <attribute name="updateTime" type="datetime"/>
                    </attributes>
                    <relations>
                        <relation name="user" type="many-to-one" target="User"/>
                        <relation name="items" type="one-to-many" target="OrderItem"/>
                    </relations>
                </entity>

                <entity name="OrderItem">
                    <attributes>
                        <attribute name="id" type="string" isId="true"/>
                        <attribute name="productId" type="string"/>
                        <attribute name="productPrice" type="decimal(10,2)"/>
                        <attribute name="quantity" type="int"/>
                        <attribute name="subtotal" type="decimal(10,2)"/>
                    </attributes>
                    <relations>
                        <relation name="product" type="many-to-one" target="Product"/>
                    </relations>
                </entity>
            </entities>
        </aggregate>

        <!-- 商品聚合根 -->
        <aggregate name="Product">
            <root>Product</root>
            <entities>
                <entity name="Product">
                    <attributes>
                        <attribute name="id" type="string" isId="true"/>
                        <attribute name="name" type="string"/>
                        <attribute name="price" type="decimal(10,2)"/>
                        <attribute name="stock" type="int"/>
                    </attributes>
                </entity>
            </entities>
        </aggregate>

        <!-- 用户聚合根 -->
        <aggregate name="User">
            <root>User</root>
            <entities>
                <entity name="User">
                    <attributes>
                        <attribute name="id" type="string" isId="true"/>
                        <attribute name="name" type="string"/>
                        <attribute name="email" type="string"/>
                        <attribute name="status" type="string"/>
                    </attributes>
                </entity>
            </entities>
        </aggregate>
    </aggregates>

    <value-objects>
        <value-object name="Money">
            <attributes>
                <attribute name="amount" type="decimal(18,2)"/>
                <attribute name="currency" type="string"/>
            </attributes>
        </value-object>

        <value-object name="Address">
            <attributes>
                <attribute name="province" type="string"/>
                <attribute name="city" type="string"/>
                <attribute name="district" type="string"/>
                <attribute name="detail" type="string"/>
            </attributes>
        </value-object>
    </value-objects>

    <business-rules>
        <rule name="orderStatusTransition">
            <description>订单状态转换规则</description>
            <states>
                <state name="PENDING">
                    <transition to="PAID" event="pay"/>
                    <transition to="CANCELLED" event="cancel"/>
                </state>
                <state name="PAID">
                    <transition to="SHIPPED" event="ship"/>
                </state>
                <state name="SHIPPED">
                    <transition to="COMPLETED" event="confirm"/>
                </state>
                <state name="COMPLETED"/>
                <state name="CANCELLED"/>
            </states>
        </rule>

        <rule name="orderAmountCalculation">
            <description>订单金额计算规则</description>
            <formula>totalAmount = Σ(items.productPrice × items.quantity)</formula>
        </rule>
    </business-rules>
</domain-model>
```

## AI推理策略

### 1. 需求理解的逐步推理
**第一步**：提取核心领域概念
- 使用正则表达式或关键词识别实体
- 提取聚合根、实体、值对象

**第二步**：识别聚合边界
- 识别全局唯一的概念（聚合根）
- 识别隶属于聚合的概念（实体）
- 识别不可变的概念（值对象）

**第三步**：理解业务规则
- 识别状态转换规则（状态机）
- 识别数据验证规则（约束）
- 识别业务约束规则（不变性）
- 识别计算规则（派生属性）

### 2. 聚合设计推理
- **判断是否为聚合根**：
  - 是否通过全局ID访问？
  - 是否维护一致性边界？
  - 是否是外部引用的入口点？

- **判断是否为实体**：
  - 是否有生命周期？
  - 是否有唯一标识？
  - 是否属于某个聚合？

- **判断是否为值对象**：
  - 是否不可变？
  - 是否通过属性值相等？
  - 是否封装了业务概念？

### 3. 关系设计推理
- **关系类型识别**：
  - 一对一：User ↔ Address
  - 一对多：Order ↔ OrderItem
  - 多对多：Order ↔ Tag（通过中间表）

### 4. View DDD遵循推理
- **检查点1**：不在实体上添加业务方法
  - 确保所有实体只包含数据属性和get方法
  - 业务逻辑将通过XMeta配置实现

- **检查点2**：领域逻辑通过get方法暴露
  - 确保提供便捷的get方法访问领域信息

## 验证点

### 1. 领域模型验证
- [ ] 聚合根识别是否正确
- [ ] 实体和值对象区分是否清晰
- [ ] 关系定义是否完整
- [ ] 业务规则是否准确

### 2. View DDD原则遵循
- [ ] 实体是否只包含数据属性
- [ ] 是否避免在实体上添加业务方法
- [ ] 领域逻辑是否通过get方法暴露

## 输出产物

### 1. 需求分析报告（Markdown）
```markdown
## 需求分析报告

### 核心领域概念
- **Order（订单）**：聚合根，代表一个完整的订单
...

### 聚合根识别
- **Order**：聚合根，通过订单号全局访问
...

### 实体设计
- **OrderItem**：实体，隶属于Order聚合
...

### 值对象设计
- **Money**：值对象，封装金额和货币
...

### 业务规则
1. **订单状态转换规则**：
   - PENDING → PAID → SHIPPED → COMPLETED
...
```

### 2. 领域模型草案（XML）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<domain-model x:schema="/nop/schema/domain.xdef"
               xmlns:x="/nop/schema/xdsl.xdef">
    <aggregates>
        <!-- 聚合根定义 -->
    </aggregates>
    <value-objects>
        <!-- 值对象定义 -->
    </value-objects>
    <business-rules>
        <!-- 业务规则定义 -->
    </business-rules>
</domain-model>
```

### 3. 实体清单（Markdown）
```markdown
## 实体清单

| 实体名 | 类型 | 聚合根 | 描述 |
|-------|------|--------|------|
| Order | 实体 | Order | 订单聚合根 |
| OrderItem | 实体 | Order | 订单项 |
| Product | 聚合根 | Product | 商品聚合根 |
| User | 聚合根 | User | 用户聚合根 |
```

### 4. 业务规则列表（Markdown）
```markdown
## 业务规则列表

### 状态转换规则
1. 订单状态转换：PENDING → PAID → SHIPPED → COMPLETED

### 数据验证规则
1. 订单号不能为空
2. 订单总金额必须大于0

### 业务约束规则
1. 订单支付后不能修改
```

## 下一步工作

当前skill完成需求分析与领域建模，生成以下产物：
1. 需求分析报告（`domain-analysis.md`）
2. 领域模型草案（`domain-model-draft.xml`）
3. 实体清单（`entity-list.md`）
4. 业务规则列表（`business-rules.md`）

这些产物将传递给下一个skill（nop-data-modeler）用于数据库设计。

