# nop-data-modeler Skill

## Skill 概述

**名称**: nop-data-modeler（数据建模师）

**定位**: 基于领域专家的领域模型草案，设计数据库模型和ORM模型，生成Nop平台的Excel ORM模型

**输入**:
1. 领域模型草案（`domain-model-draft.xml`）
2. 需求分析报告（`domain-analysis.md`）
3. 业务规则列表（`business-rules.md`）

**输出**:
1. Nop平台Excel ORM模型（`{module}.orm.xlsx`）
2. 数据库DDL脚本（`create_{module}_tables.sql`）
3. 数据库设计文档（`database-design-{module}.md`）

**能力**:
- 将领域模型转换为数据库模型
- 实体结构设计
- 字段类型与约束定义
- 关系建模（一对一、一对多、多对多）
- 索引设计
- Excel模型文件格式生成

**依赖**:
- Nop平台ORM模型文档（docs-for-ai/getting-started/dao/entitydao-usage.md）
- Nop平台XDSL规范（docs-for-ai/getting-started/xlang/）

## 核心原则

### 1. View DDD原则（Nop特色）
- **实体**：只包含稳定的数据属性（字段、关联）
- **不在实体上增加易变的业务方法**：如`calcOrder()`
- **领域逻辑通过get方法暴露**：如`order.getItems()`
- **易变业务逻辑放在XMeta**：通过`getter`、`domain`、`computed`等属性

### 2. 数据库设计最佳实践
- **主键策略**：推荐使用`BIGINT`自增ID
- **字段类型选择**：
  - ID字段：`BIGINT`
  - 金额字段：`DECIMAL(18,2)`
  - 时间字段：`DATETIME`
  - 状态字段：`TINYINT`
  - 文本字段：根据长度选择`VARCHAR`
- **约束设计**：
  - 主键：`PRIMARY KEY`
  - 唯一性约束：`UNIQUE`
  - 非空约束：`NOT NULL`
  - 检查约束：`CHECK`
  - 外键：`FOREIGN KEY`

### 3. 索引设计
- **为外键添加索引**
- **为高频查询字段添加索引**
- **为排序字段添加索引**
- **避免过度索引**

### 4. 性能优化
- **避免大表**（考虑分表策略）
- **字段类型选择**（根据数据量选择合适类型）
- **避免N+1查询**（合理设置关联加载策略）

## 工作流程

### 阶段1：领域模型分析

**步骤1.1：解析领域模型草案**
```
读取domain-model-draft.xml，提取：
- 聚合根列表
- 实体列表
- 实体属性列表
- 实体关系列表
- 值对象列表
- 业务规则列表
```

**步骤1.2：识别数据库实体**
```
将领域实体映射为数据库表：
- 聚合根 → 数据库表
- 实体 → 数据库表
- 值对象 → 列或关联表（取决于是否需要查询）
```

**步骤1.3：识别表关系**
```
将领域关系映射为数据库关系：
- 一对一 → 外键（双向）
- 一对多 → 外键（多端）
- 多对多 → 中间表
```

### 阶段2：数据库表设计

**步骤2.1：设计表结构**
```
为每个实体设计表结构：
- 表名：遵循snake_case命名规范
- 字段名：遵循snake_case命名规范
- 主键：BIGINT自增ID
- 审计字段：createTime, updateTime
```

**步骤2.2：设计字段定义**
```
为每个实体设计字段：
- 字段名称
- 字段类型（根据属性类型选择）
- 字段长度和精度
- 约束（PRIMARY、UNIQUE、NOT NULL、CHECK）
- 默认值
- 注释
```

**步骤2.3：设计关系**
```
设计实体间的关系：
- 定义外键
- 设置关联加载策略（lazy、eager、join）
- 定义级联规则（CASCADE、RESTRICT）
```

**步骤2.4：设计索引**
```
设计索引策略：
- 主键索引
- 唯一索引（UNIQUE约束）
- 外键索引
- 复合索引（多字段索引）
```

### 阶段3：Excel模型生成

**步骤3.1：生成Entities工作表**
```
定义所有实体：
| Name | Comment |
|-------|--------|
| Order | 订单表 |
| OrderItem | 订单项表 |
| User | 用户表 |
| Product | 商品表 |
```

**步骤3.2：生成Fields工作表**
```
定义所有字段：
| Entity | FieldName | ColumnName | DataType | Length | PrimaryKey | NotNull | Comment |
|-------|---------|------|--------|-------|-----------|---------|--------|
| Order | id | order_id | BIGINT | - | YES | YES | 订单ID |
| Order | orderNo | order_no | VARCHAR | 32 | NO | YES | 订单号 |
| Order | userId | user_id | BIGINT | - | NO | YES | 用户ID |
| Order | orderStatus | order_status | TINYINT | - | NO | YES | 订单状态 |
| Order | totalAmount | total_amount | DECIMAL | 18,2 | NO | YES | 订单总金额 |
| Order | createTime | create_time | DATETIME | - | NO | YES | 创建时间 |
| Order | updateTime | update_time | DATETIME | - | NO | YES | 更新时间 |
```

**步骤3.3：生成Indexes工作表**
```
定义所有索引：
| IndexName | Columns | Unique | Type | Comment |
|-----------|----------|----------|---------|-----------|
| idx_order_id | order_id | YES | BTREE | 订单ID索引 |
| idx_order_no | order_no | YES | BTREE | 订单号索引 |
| idx_user_id | user_id | NO | BTREE | 用户ID索引 |
| idx_order_status | order_status | NO | BTREE | 订单状态索引 |
```

**步骤3.4：生成Relations工作表**
```
定义所有关系：
| RelationName | LeftEntity | LeftKey | RightEntity | RightKey | Type | Comment |
|--------------|--------|--------------|----------|----------|---------|-----------|
| User.orders | User | user_id | Order | order_id | ONE_TO_MANY | 用户订单 |
| Order.items | Order | order_id | OrderItem | order_item_id | ONE_TO_MANY | 订单项 |
| OrderItem.product | OrderItem | product_id | Product | product_id | MANY_TO_ONE | 商品 |
```

### 阶段4：DDL脚本生成

**步骤4.1：生成建表语句**
```sql
CREATE TABLE `order` (
  `order_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `order_status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态',
  `total_amount` DECIMAL(18,2) NOT NULL COMMENT '订单总金额',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `idx_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_status` (`order_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
```

**步骤4.2：生成外键约束**
```sql
ALTER TABLE `order` ADD CONSTRAINT `fk_order_user`
  FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT;
```

**步骤4.3：生成索引**
```sql
CREATE INDEX `idx_order_status` ON `order` (`order_status`);
```

### 阶段5：设计文档生成

生成设计说明文档，包括：
- 需求分析总结
- 表结构设计
- 字段类型选择理由
- 索引设计理由
- 性能优化建议

## AI推理策略

### 1. 领域模型到数据库模型的映射
- **聚合根 → 数据库表**：
  - 每个聚合根对应一个数据库表
  - 表名遵循snake_case命名规范

- **实体 → 数据库表**：
  - 聚合内的实体对应独立的数据库表
  - 通过外键关联到聚合根

- **值对象 → 列或关联表**：
  - 如果值对象不需要查询，则映射为列
  - 如果值对象需要查询，则映射为关联表

### 2. 字段类型选择推理
- **整数类型**：
  - ID字段：`BIGINT`
  - 数量字段：`INT`
  - 状态字段：`TINYINT`

- **小数类型**：
  - 金额字段：`DECIMAL(18,2)`
  - 价格字段：`DECIMAL(10,2)`

- **文本类型**：
  - 短文本：`VARCHAR(255)`
  - 长文本：`TEXT`
  - 固定长度：`CHAR`

- **时间类型**：
  - 日期时间：`DATETIME`
  - 日期：`DATE`
  - 时间：`TIME`

### 3. 关系设计推理
- **一对一关系**：
  - 在主表添加外键
  - 双向外键（如果需要双向导航）

- **一对多关系**：
  - 在"多"端添加外键
  - 外键关联到"一"端的主键

- **多对多关系**：
  - 创建中间表
  - 中间表包含两个外键

### 4. 索引设计推理
- **主键索引**：自动创建
- **唯一索引**：UNIQUE约束
- **外键索引**：为外键创建索引
- **查询优化索引**：为高频查询字段创建索引
- **复合索引**：为多字段查询创建复合索引

## 验证点

### 1. Excel模型验证
- [ ] Excel文件结构是否正确
- [ ] Entities工作表是否完整
- [ ] Fields工作表是否完整
- [ ] Indexes工作表是否完整
- [ ] Relations工作表是否完整

### 2. DDL规范遵循
- [ ] 表名是否遵循snake_case命名规范
- [ ] 字段名是否遵循snake_case命名规范
- [ ] 主键是否为BIGINT自增
- [ ] 字段类型选择是否合理
- [ ] 约束定义是否完整

### 3. View DDD原则遵循
- [ ] 实体是否只包含数据属性
- [ ] 是否避免在实体上添加业务方法
- [ ] 领域逻辑是否通过get方法暴露

## 输出产物

### 1. Excel ORM模型（`.orm.xlsx`）
包含以下工作表：
- Entities工作表
- Fields工作表
- Indexes工作表
- Relations工作表

### 2. DDL脚本（`create_{module}_tables.sql`）
包含：
- 建表语句
- 外键约束
- 索引定义

### 3. 设计文档（`database-design-{module}.md`）
包含：
- 需求分析总结
- 表结构设计
- 字段类型选择理由
- 索引设计理由
- 性能优化建议

## 下一步工作

当前skill完成数据库设计，生成以下产物：
1. Excel ORM模型（`{module}.orm.xlsx`）
2. DDL脚本（`create_{module}_tables.sql`）
3. 设计文档（`database-design-{module}.md`）

这些产物将传递给下一个skill（nop-service-architect）用于服务层设计。

