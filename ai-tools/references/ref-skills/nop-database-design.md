# nop-database-design Skill（完整实现版）

## Skill 概述

**名称**: nop-database-design（数据库设计完整版）

**定位**: 据自然语言需求，生成Nop平台的Excel ORM模型，支持从简单到复杂的数据库设计

**输入**:
- 自然语言需求描述（用户故事、业务规则、需求文档等）
- 现有数据模型（如有遗留数据库、Excel模板）
- 领域知识（如果有领域专家或专家文档）

**输出**:
1. Nop平台Excel ORM模型文件（`{module}.orm.xlsx`）
2. 数据库DDL脚本（可选，`create_{module}_tables.sql`）
3. 数据库设计文档（`database-design-{module}.md`）
4. 实体设计建议（基于Nop平台最佳实践）

**能力**:
- 需求理解与领域建模
- 实体识别与属性定义
- 关系建模（一对一、一对多、多对多、自关联等）
- 洢引策略
- Excel模型文件格式生成
- 字段类型与约束定义
- 红束与验证
- 性能优化考虑
- 数据库适配（MySQL、PostgreSQL等）

**依赖**:
- Nop平台理论文档（docs/theory/）
- Nop平台代码生成指南（docs-for-ai/getting-started/）
- XDSL相关文档（docs-for-ai/getting-started/xlang/）

## 核心原则

### 1. View DDD（Nop特色）
- **实体**：只包含稳定的数据属性
- **领域逻辑**：通过get方法暴露
- **不在实体上**：不增加易变的业务语义函数

### 2. 稳定性
- **实体作为稳定的数据载体**
- 领域模型是独立的，可以无限制地演化
- 通过XMeta配置验证规则和数据权限

### 3. 表驱动
- Excel模型优先，避免手动建表错误
- 使用Nop平台标准格式和工具

### 4. 约束与验证
- 主键、唯一性、空约束、默认值设置
- 避免常见数据错误

## 详细工作流程

### 阶段1：需求分析

**步骤1.1：理解业务场景**
```
AI分析业务描述，提取核心概念：
- 识别涉及的领域对象（实体、聚合根）
- 理解业务流程和数据流转
- 识别业务规则和约束
- 识别用户、时间、金额等核心属性
```

**步骤1.2：实体识别**
```
AI分析业务描述，提取实体清单：
- 识别核心实体（如Order、User、Product）
- 识别实体的核心标识属性（如id、code、name、status）
- 识别关联实体（如OrderItem）
- 识别关键业务属性（如createTime、updateTime、totalAmount）
```

**步骤1.3：属性提取**
```
为每个实体提取属性清单：
- 属性名（如id、name、email）
- 数据类型（如string、int、datetime、decimal）
- 字段类型（VARCHAR(36)、BIGINT、DECIMAL(10,2)等）
- 约束（PRIMARY、UNIQUE、NOT NULL、CHECK）
- 默认值设置
- 字段长度和精度（length、precision）
- 注释说明
```

**步骤1.4：关系识别**
```
AI分析实体间的关系，识别关系类型：
- 一对一（User ↔ Address）
- 一对多（Order ↔ Product）
- 多对多（Order ↔ OrderItem）
- 自关联（User - Role）
- 中间表（OrderItem）
```

**步骤1.5：业务规则识别**
```
AI分析业务描述，识别业务规则和约束：
- 状态转换规则（订单状态机、状态枚举）
- 金额计算规则（小计规则、大计规则）
- 数据验证规则（非空、唯一性、金额>0等）
```

### 阶段2：实体设计

**目标**：为每个实体设计完整的数据结构

**工作表1：Entities工作表**
```xml
<entities xdef:key-attr="name" xdef:body-type="list">
    <!-- 订单实体示例 -->
    <entity name="io.nop.app.User">
        <columns>
            <column name="id" type="string" primary="true">
                <comment>用户ID，BIGINT自增主键</comment>
            </column>
            <column name="name" type="string" length="100">
                <comment>用户名，最大长度255字符</comment>
            </column>
            <column name="email" type="string" length="200">
                <comment>邮箱，最大长度200字符，用于测试</comment>
            </column>
            <column name="status" type="tinyint">
                <comment>状态，TINYINT，最小整数类型</comment>
            </column>
        </columns>
    </entity>
    
    <!-- 多对多关系示例 -->
    <entity name="io.nop.app.Order">
        <columns>
            <column name="id" type="string" primary="true">
                <comment>订单ID，BIGINT自增主键</comment>
            </column>
            <column name="userId" type="BIGINT">
                <comment>用户ID，BIGINT类型</comment>
                <column name="orderStatus" type="tinyint">
                <comment>订单状态，TINYINT</comment>
            </column>
            <column name="totalAmount" type="decimal(18,2)">
                <comment>订单总金额，DECIMAL(18,2)</comment>
            </column>
            <column name="createTime" type="datetime">
                <comment>创建时间，DATETIME类型</comment>
            </column>
            <column name="updateTime" type="datetime">
                <comment>更新时间，DATETIME类型</comment>
            </column>
        </columns>
    </entity>
    
    <!-- 一对一关系示例 -->
    <entity name="io.nop.app.User">
        <columns>
            <column name="id" type="string" primary="true">
                <comment>用户ID，BIGINT自增主键</comment>
            </column>
            <column name="addressId" type="string" foreignKey="address.id">
                <comment>地址ID，外键引用Address表的id</comment>
            </column>
        </columns>
    </entity>
    
    <!-- 多对多关系示例 -->
    <entity name="io.nop.app.OrderItem">
        <columns>
            <column name="id" type="string" primary="true">
                <comment>订单项ID，BIGINT自增主键</comment>
            </column>
            <column name="orderId" type="string" foreignKey="order.id">
                <comment>订单ID，外键引用Order表的id</comment>
            </column>
            <column name="productId" type="string" foreignKey="product.id">
                <comment>商品ID，外键引用Product表的id</comment>
            </column>
            <column name="productPrice" type="decimal(10,2)">
                <comment>购买时的价格，DECIMAL(10,2)</comment>
            </column>
            <column name="quantity" type="int">
                <comment>数量，INT</comment>
            </column>
            <column name="subtotal" type="decimal(10,2)">
                <comment>小计金额，DECIMAL(10,2)</comment>
            </column>
        </columns>
    </entity>
</entities>
```

**工作表2：Indexes工作表**
```xml
<indexes xdef:key-attr="name" xdef:body-type="list">
    <!-- 主键索引 -->
    <index name="idx_user_id">
        <columns>
            <column name="userId" type="BIGINT"/>
            <comment>用户ID索引</comment>
        </column>
    </index>
    
    <!-- 复合索引 -->
    <index name="idx_user_email">
        <columns>
            <column name="email" type="string"/>
            <comment>用户邮箱索引</comment>
        </column>
    </index>
    
    <!-- 唯一索引 -->
    <index name="idx_user_status">
        <columns>
            <column name="status" type="tinyint"/>
            <comment>订单状态索引</comment>
        </column>
    </index>
    
    <!-- 普能索引 -->
    <index name="idx_create_time">
        <columns>
            <column name="createTime" type="datetime"/>
            <comment>创建时间索引</comment>
        </column>
    </index>
    
    <!-- 外键索引 -->
    <index name="idx_update_time">
        <columns>
            <column name="updateTime" type="datetime"/>
            <comment>更新时间索引</comment>
        </column>
    </index>
</indexes>
```

**工作表3：Relations工作表**
```xml
<relations xdef:key-attr="name" xdef:body-type="list">
    <!-- 一对一关系 -->
    <relation name="user.address" type="one-to-one">
        <leftEntity="User" leftKey="userId" rightEntity="Address" leftKey="id"/>
        <comment>一个用户有一个地址</comment>
        </relation>
    
    <!-- 一对多关系 -->
    <relation name="order.items" type="many-to-many">
        <leftEntity="Order" leftKey="id" rightEntity="OrderItem" leftKey="orderId"/>
        <comment>一个订单有多个订单项</comment>
        </relation>
    
    <!-- 自关联 -->
    <relation name="user.roles" type="many-to-many">
        <leftEntity="User" leftKey="id" rightEntity="Role"/>
        <comment>一个用户可以有多个角色</comment>
        </relation>
</relations>
```

## AI推理策略

### 1. 需求理解的逐步推理
**第一步**：提取核心概念
- 使用正则表达式或关键词识别实体和属性
- 提取属性清单（字段名、类型、约束等）

**第二步**：识别关系
- 识别"一对一"、"一对多"、"多对多"等关系模式
- 提取关联字段（如用户ID、订单号等）

**第三步**：理解业务规则
- 识别验证约束（如状态必须有效、金额必须大于0）
- 识别业务逻辑（如订单完成后不能修改）

### 2. 实体设计的迭代优化
- **迭代策略1**：从核心实体开始
  - 先设计主实体（如Order、User）
  - 设计辅助实体（如Product、Address、Role）
- **迭代策略2**：添加关联实体（如OrderItem）

**迭代策略3**：设计中间表
  - 对于复杂的多对多关系，考虑是否需要中间表

### 3. 约束与验证
- **主键**：`PRIMARY KEY`、`UNIQUE`、`NOT NULL`、`CHECK`
- **外键**：`FOREIGN KEY`
- **空约束**：`NOT NULL`
- **默认值**：通过`default`属性设置
- **长度限制**：通过`length`、`precision`设置

### 4. 性能优化
- **索引设计**：为外键和常用查询字段添加索引
- 为排序字段添加索引
- 避免大表（考虑分表策略）
- 字段类型选择（根据数据量选择合适类型）

## 验证点

### 1. Excel模型验证
- [ ] Excel文件结构是否正确
- [ ] 主键配置是否合理
- [ ] 关系定义是否完整
- [ ] 字段命名是否遵循规范
- [ ] 数据类型是否正确
- [ ] 约束配置是否合理

### 2. DDL规范遵循
- [ ] x:schema声明是否正确
- [ ] xmlns:x命名空间是否正确
- [ ] 字段命名是否遵循规范
- [ ] 主键策略是否推荐BIGINT自增ID
- [ ] 字段类型选择是否优化
- [ ]

### 3. View DDD原则遵循
- [ ] 实体是否只包含数据属性
- [ ] 是否避免在实体上添加业务方法
- [ ] 领域逻辑是否通过XMeta配置实现
- [ ]

## 下一步工作

当前skill完成数据库设计和DDD建模，生成以下产物：
1. `{module}.orm.xlsx` - Excel ORM模型文件
2. `database-design-{module}.md` - 设计文档
3. `{module}.xmeta.xml` - XMeta元数据配置

这些产物将传递给下一个skill（nop-service-layer）用于服务层设计。

