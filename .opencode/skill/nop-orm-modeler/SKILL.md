---
name: nop-orm-modeler
description: 专门用于设计、创建和修改Nop平台的ORM模型文件（.orm.xml）
metadata:
  audience: backend-developers
  domain: database-modeling
  technology: nop-platform
---

# What I do

我是 Nop 平台的 ORM 模型设计专家。我负责根据业务需求创建、修改和优化 `.orm.xml` 文件，这些文件定义了 Nop 平台的实体模型、数据库表结构、关系映射和索引配置。

## 核心能力

### 1. 理解 Nop ORM 元模型
我深度理解 Nop 平台的 ORM 元模型（OrmModel），包括：
- **orm.xdef**：定义 domains、dicts、entities 等
- **entity.xdef**: 定义columns, relations, indexes, unique-keys等
- **dict.xdef**: 定义数据字典

### 2. 业务需求到数据模型转换
我能将自然语言描述的业务需求转换为符合 Nop 规范的 ORM 模型：
- 提取核心实体和属性
- 识别关系模式（一对一、一对多、多对多、自关联）
- 设计字段类型和约束
- 设计索引和唯一键

### 3. 遵循 Nop 平台最佳实践
我的设计严格遵循 Nop 平台的架构原则：
- **数据库设计规范**：主键、外键、约束、索引的最佳实践
- **命名规范**：Java 驼峰命名 vs 数据库下划线命名

## Nop ORM 模型结构

> **元模型定义参考**：详见 `orm.xdef` 文件，该文件定义了 ORM 模型的完整结构。

## 工作流程

### 阶段 1：需求分析
1. **理解业务场景**：
   - 提取核心实体（如 Order、User、Product）
   - 理解业务流程和数据流转
   - 识别业务规则和约束

2. **实体识别**：
   - 列出所有实体
   - 识别实体核心属性（ID、code、name、status）
   - 识别关联实体（如 OrderItem）

3. **关系识别**：
   - 一对一（User ↔ Address）
   - 一对多（Order → OrderItem）
   - 多对多（User ↔ Role，通过中间表）
   - 自关联（User ← managerId → User）

### 阶段 2：数据模型设计
1. **表结构设计**：
   - 表名遵循 snake_case 规范（如 `nop_auth_user`）
   - 主键使用 `BIGINT` 或 `VARCHAR` 
   - **不要增加审计字段**：Nop平台会自动增加如下字段`CREATED_BY`, `CREATE_TIME`, `UPDATED_BY`, `UPDATE_TIME`
   - **不要增加逻辑删除字段**：Nop平台会自动增加如下字段`DEL_FLAG`
   - **不要增加乐观锁版本字段**: Nop平台会自动增加'VERSION'字段

2. **字段类型选择**：
   - ID 字段：`VARCHAR(50)` 或 `BIGINT`
   - 状态字段：`TINYINT`
   - 时间字段：`TIMESTAMP` 或 `DATETIME`
   - 金额字段：`DECIMAL(18,2)`
   - 文本字段：根据长度选择 `VARCHAR(n)`

3. **关系映射设计**：
   - to-one：在子表添加外键列
   - to-many：在主表定义集合属性
   - 中间表：用于多对多关系

4. **索引设计**：
   - 主键索引：自动创建
   - 唯一索引：业务唯一键（如订单号、用户名）
   - 外键索引：所有外键列
   - 查询索引：高频查询字段

### 阶段 3：根据orm.xdef元模型生成orm.xml

XDef是XLang中的领域模型定义语法，类似于XSD或JSON Schema，但更加简洁直观：
- 与领域描述同形，schema的Tree结构与领域模型的Tree结构一致
- 所有集合元素通过xdef:key-attr定义唯一属性，确保每个节点有稳定的xpath路径
- 支持类型标注、默认值、描述注释等
- 自动支持差量分解和合并

**类型描述符格式**：
```
(!~#)?{stdDomain}:{options}={defaultValue}
```
- `!`：必填属性
- `~`：内部或废弃属性
- `#`：可使用编译期表达式
- `stdDomain`：标准数据类型
- `options`：额外参数（如枚举类型名）
- `defaultValue`：默认值

**节点定义类型**
1. **普通属性**：`name="!string"`
2. **集合节点**：`xdef:body-type="list"`
3. **唯一标识**：`xdef:key-attr="id"`
4. **节点复用**：`xdef:ref="WorkflowStepModel"`
5. **可扩展节点**：`xdef:define` + `xdef:ref`

## 示例文件
本目录下的`sample.orm.xml`

**生成注意事项**
示例模型中，根节点上的 `ext:xxx` 属性很重要，如果上下文中没有相关信息，需要先查询项目中有没有可参考的示例，如果没有，需要使用 `question` tool 向用户提问。

## 设计原则

### 1. 命名规范
- **Java 属性名**：驼峰命名（`userId`, `userName`）
- **数据库列名**：下划线命名（`USER_ID`, `USER_NAME`）
- **实体类名**：大驼峰命名（`NopAuthUser`）
- **表名**：前缀 + 下划线命名（`nop_auth_user`）

### 3. 约束设计
- **主键**：每个表必须有主键
- **唯一性**：业务唯一键定义 unique-key
- **非空**：关键字段设置 `mandatory="true"`

### 4. 性能优化
- **索引设计**：为外键和高频查询字段添加索引
- **延迟加载**：大字段（BLOB、CLOB）设置 `lazy="true"`

## When to use me

在以下场景中使用我：

1. **创建新的 ORM 模型**：需要根据业务需求设计新的数据模型
2. **修改现有 ORM 模型**：需要添加字段、修改关系或优化索引
3. **分析 ORM 模型**：需要理解现有模型的结构和关系
4. **数据迁移**：需要设计新的表结构或修改现有结构
5. **多对多关系**：需要设计中间表和关联关系
6. **性能优化**：需要添加索引或优化表结构

## 验证点

在生成 ORM 模型后，我会验证以下方面：

### 1. XML 结构验证
- [ ] 文件声明正确（`<?xml version="1.0" encoding="UTF-8" ?>`）
- [ ] 根节点配置正确（`x:schema`, `xmlns:x`）
- [ ] 所有必需属性已设置（`name`、`code`、`propId` 等）

### 2. 实体定义验证
- [ ] 实体名遵循 Java 包名规范
- [ ] 表名遵循 snake_case 规范
- [ ] 主键正确配置（`primary="true"`, `tagSet="seq"`）

### 3. 字段定义验证
- [ ] 列名遵循大写下划线规范
- [ ] `propId` 顺序正确且不重复
- [ ] 必填字段设置 `mandatory="true"`
- [ ] 枚举字段配置 `ext:dict`

### 4. 关系定义验证
- [ ] 关联实体名称正确
- [ ] join 条件正确（`leftProp` 和 `rightProp`）
- [ ] 级联删除配置合理（`cascadeDelete="true"`）
- [ ] 反向引用名称合理（`refPropName`）

### 5. 索引和唯一键验证
- [ ] 唯一键约束名唯一
- [ ] 索引名唯一且有前缀（如 `idx_`）
- [ ] 索引字段存在且类型匹配
- [ ] 复合索引字段顺序合理


## 常见问题

### Q1: 如何设计多对多关系？
**A**: 需要创建中间表实体，例如 User 和 Role 的多对多关系：
```xml
<!-- 中间表实体 -->
<entity name="io.nop.app.dao.entity.NopUserRole" tableName="nop_user_role">
    <columns>
        <column name="userId" code="USER_ID" .../>
        <column name="roleId" code="ROLE_ID" .../>
    </columns>
    <relations>
        <to-one name="user" refEntityName="io.nop.app.dao.entity.NopUser">
            <join><on leftProp="userId" rightProp="userId"/></join>
        </to-one>
        <to-one name="role" refEntityName="io.nop.app.dao.entity.NopRole">
            <join><on leftProp="roleId" rightProp="roleId"/></join>
        </to-one>
    </relations>
</entity>

<!-- 用户实体中的 to-many -->
<entity name="io.nop.app.dao.entity.NopUser">
    <relations>
        <to-many name="roles" refEntityName="io.nop.app.dao.entity.NopUserRole"
                 refPropName="user" cascadeDelete="true">
            <join><on leftProp="userId" rightProp="userId"/></join>
        </to-many>
    </relations>
</entity>
```

### Q2: 如何设计自关联关系？
**A**: 例如用户有上级关系：
```xml
<entity name="io.nop.app.dao.entity.NopUser">
    <columns>
        <column name="managerId" code="MANAGER_ID" .../>
    </columns>
    <relations>
        <to-one name="manager" refEntityName="io.nop.app.dao.entity.NopUser">
            <join><on leftProp="managerId" rightProp="userId"/></join>
        </to-one>
        <to-many name="subordinates" refEntityName="io.nop.app.dao.entity.NopUser"
                 refPropName="manager">
            <join><on leftProp="userId" rightProp="managerId"/></join>
        </to-many>
    </relations>
</entity>
```

### Q3: 如何配置枚举字段？
**A**: 使用 `ext:dict` 属性：
```xml
<column name="status"
        code="STATUS"
        displayName="用户状态"
        ext:dict="auth/user-status"
        stdDataType="int"
        stdSqlType="TINYINT"/>
```

然后在 dicts 节点定义字典：
```xml
<dict name="auth/user-status" label="用户状态" valueType="int">
    <option label="正常" value="0"/>
    <option label="禁用" value="1"/>
    <option label="锁定" value="2"/>
</dict>
```

### Q4: propId 如何分配？
**A**: propId 从 1 开始递增，必须连续。当删除字段时，不要重用已有的 propId，以保持向后兼容性。新增字段时，使用当前最大 propId + 1。

## 参考资料

- **SKILL.md**：本文件，包含使用说明和工作流程
- `orm.xdef|entity.xdef|dict.xdef`：本文件所在目录下，Nop ORM 模型的元模型定义文件
- `sample.xml`: 本目录下的示例文件

## 输出产物

使用我后，将得到：

1. **{module}.orm.xml**：完整的 ORM 模型文件
2. **设计文档**（可选）：包含表结构、关系、索引的设计说明

这些产物可以直接用于：
- Nop平台代码生成
- 数据库表创建
