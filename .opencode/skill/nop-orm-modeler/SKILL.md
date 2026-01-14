---
name: nop-orm-modeler
description: 专门用于设计、创建、验证和修改Nop平台的ORM模型文件（.orm.xml）
---

# 使用场景

- **新增**: "创建"、"新增"、"增加" → 创建实体/字段/关系
- **更新**: "修改"、"更新"、"调整" → 修改现有定义
- **检查并修正**: "检查并修正"、"检查规范" → 仅修正格式规范，不碰业务逻辑

# What I do

我是Nop 平台 ORM 模型专家，设计/创建/修改 `.orm.xml` 文件。

## 元模型参考

- **orm.xdef**：domains、dicts、entities 定义
- **entity.xdef**：columns、relations、indexes、unique-keys 定义
- **dict.xdef**：数据字典定义

## 根节点配置说明

### ext:appName
- 模块标识名，生成 `{appName}.orm.xml`
- 两级结构：`{xx}-{yy}`，全小写 ASCII
- 例：`app-test` → `app-test.orm.xml`

### ext:basePackageName
- 模块基础包名
- 代码分层：dao, service 等
- 例：`io.app.sample` → `io.app.sample.dao`, `io.app.sample.service`

### ext:entityPackageName
- 实体类包名
- 一般为 `{basePackageName}.dao.entity`
- 例：`io.app.sample.dao.entity`

### 其他 ext 属性
- **mavenGroupId** / **mavenArtifactId**：Maven 坐标
- **dialect**：数据库方言，如 `mysql,oracle,postgresql`
- **useStdFields**：是否使用标准字段，默认 `true`
  - `true`：自动添加 CREATE_TIME 等审计字段（见下方"Nop 平台特定规则"）
  - `false`：不自动添加标准字段

## Nop 平台特定规则

**重要**：以下字段由平台自动添加，无需手动定义：

| 字段 | 类型 | 说明 |
|------|------|------|
| CREATED_BY | VARCHAR(50) | 创建者 |
| CREATE_TIME | TIMESTAMP | 创建时间 |
| UPDATED_BY | VARCHAR(50) | 更新者 |
| UPDATE_TIME | TIMESTAMP | 更新时间 |
| DEL_VERSION | BIGINT | 逻辑删除 |
| VERSION | INTEGER | 乐观锁版本 |

## XDef 基本说明

XDef 是 XLang 的领域模型定义语法，特点：
- 与领域描述同形，Tree 结构一致
- 集合元素通过 `xdef:key-attr` 定义唯一标识
- 支持类型标注、默认值
- 自动支持差量分解和合并

**类型描述符格式**：`(!~#)?{stdDomain}:{options}={defaultValue}`
- `!`：必填
- `~`：内部或废弃
- `#`：支持编译期表达式

**节点类型**：
- 普通属性：`name="!string"`
- 集合节点：`xdef:body-type="list"`
- 唯一标识：`xdef:key-attr="id"`
- 节点复用：`xdef:ref="WorkflowStepModel"`

## 设计原则

### 命名规范
- Java 属性：驼峰（`userId`）
- 数据库列名：大写下划线（`USER_ID`）
- 实体类名：大驼峰（`AppSampleUser`）
- **表名必须带统一子系统前缀**：`{子系统前缀}_{功能模块}_{实体名}`
  - ✅ 正确：`app_sample_user`、`app_order_item`
  - ❌ 错误：`user`、`order`（缺少前缀）
  - 前缀示例：`app_sample`（示例模块）、`app_order`（订单模块）、`app_sys`（系统模块）
- **不要使用 nop 前缀**：`nop` 前缀保留给 Nop 平台内置模块使用，用户自定义模块不应使用
  - ❌ 错误：`nop_auth_user`、`nop-auth.orm.xml`
  - ❌ 错误：`io.nop.auth`（包名）
  - ✅ 正确：`app_auth_user`、`app-auth.orm.xml`
  - ✅ 正确：`io.app.auth`（包名）

### 关系映射
- to-one：子表添加外键列，缺省 `tagSet="pub"`
- to-many：主表定义集合属性，缺省 `tagSet="pub,cascade-delete,insertable,updatable"`
- 多对多：创建中间表实体

### 索引设计
- 主键索引自动创建
- 外键列自动索引
- 业务唯一键定义 unique-key

## 生成完成后的必检项

**重要**：生成 `.orm.xml` 文件后，必须逐项检查以下内容：

### 1. 根节点配置
- [ ] `ext:appName` 符合 `{xx}-{yy}` 格式，全小写 ASCII
- [ ] `ext:basePackageName` 正确设置，如 `io.app.sample`
- [ ] `ext:entityPackageName` = `{basePackageName}.dao.entity`
- [ ] `ext:mavenGroupId` / `ext:mavenArtifactId` 正确
- [ ] `ext:dialect` 包含目标数据库方言

### 2. 表名规范
- [ ] 所有表名带统一子系统前缀（如 `app_sample_user`）
- [ ] 不能是简单单词（如 `user`、`order`）
- [ ] 遵循 `{子系统前缀}_{模块}_{实体}` 格式

### 3. 实体定义
- [ ] 实体类名符合大驼峰规范（`AppSampleUser`）
- [ ] 完整包名正确（`io.app.sample.dao.entity.AppSampleUser`）
- [ ] 主键配置：`primary="true"`, `tagSet="seq"`
- [ ] **未手动添加**审计字段（CREATED_BY, CREATE_TIME, UPDATED_BY, UPDATE_TIME, DEL_VERSION, VERSION）

### 4. 字段定义
- [ ] `propId` 从 1 开始连续递增，无重复，无空缺
- [ ] 列名符合大写下划线规范（`USER_ID`、`USER_NAME`）
- [ ] 字段名符合驼峰规范（`userId`、`userName`）
- [ ] `stdSqlType` 必须使用 StdSqlType 枚举类的标准值
  - **可用的 stdSqlType 值**：BOOLEAN|TINYINT|SMALLINT|INTEGER|BIGINT|DECIMAL|FLOAT|DOUBLE|DATE|TIME|DATETIME|TIMESTAMP|CHAR|VARCHAR|VARBINARY|CLOB|BLOB
- [ ] 枚举字段配置 `ext:dict="xxx/yyy"`（**注意：boolean 型字段不用设置 dict**）
- [ ] `domain` 使用原则：
  - **不是每个字段都需要 domain**，只有具有重要业务含义、在显示或后台处理方面存在特殊业务规则的字段才需要定义 domain
  - 例如：订单编号（order-no）可能需要特殊编号规则、状态字段需要特殊显示逻辑等
  - **优先复用** `app-sample.orm.xml` 中已有的 domain，domain 是全局的，不能重名
  - 能复用的一定要复用，避免重复定义

### 5. 关系定义
- [ ] `refEntityName` 指向的实体存在
- [ ] join 条件正确：`leftProp` 和 `rightProp` 对应的字段存在
- [ ] to-one 关系设置 `tagSet="pub"`
- [ ] to-many 关系设置 `tagSet="pub,cascade-delete,insertable,updatable"`
- [ ] 级联删除配置合理（`cascadeDelete="true"` 用于主从表）
- [ ] 反向引用名称合理（`refPropName`）
- [ ] 一对多关系的 `refPropName` 在子表上存在

### 6. 索引和约束
- [ ] 主键索引已定义（`primary="true"`）
- [ ] 业务唯一键定义了 `unique-key`（如用户名、订单号）
- [ ] 外键列有索引（Nop 自动生成，可显式定义）
- [ ] 约束名唯一且有前缀（如 `UK_APP_SAMPLE_USER_NAME`）

## 常见问题

### Q1: 多对多关系？
**A**: 创建中间表实体：
```xml
<!-- 中间表 -->
<entity name="io.app.sample.dao.entity.AppSampleUserRole">
    <columns>
        <column name="userId" code="USER_ID" .../>
        <column name="roleId" code="ROLE_ID" .../>
    </columns>
    <relations>
        <to-one name="user" refEntityName="io.app.sample.dao.entity.AppSampleUser"
                tagSet="pub">
            <join><on leftProp="userId" rightProp="userId"/></join>
        </to-one>
        <to-one name="role" refEntityName="io.app.sample.dao.entity.AppSampleRole"
                tagSet="pub">
            <join><on leftProp="roleId" rightProp="roleId"/></join>
        </to-one>
    </relations>
</entity>

<!-- 用户实体 -->
<to-many name="roles" refEntityName="io.app.sample.dao.entity.AppSampleUserRole"
         refPropName="user" tagSet="pub,cascade-delete,insertable,updatable">
    <join><on leftProp="userId" rightProp="userId"/></join>
</to-many>
```

### Q2: 自关联关系？
**A**: 例如用户上级关系：
```xml
<column name="managerId" code="MANAGER_ID" .../>
<to-one name="manager" refEntityName="io.app.sample.dao.entity.AppSampleUser"
        tagSet="pub">
    <join><on leftProp="managerId" rightProp="userId"/></join>
</to-one>
<to-many name="subordinates" refEntityName="io.app.sample.dao.entity.AppSampleUser"
         refPropName="manager" tagSet="pub,cascade-delete,insertable,updatable">
    <join><on leftProp="userId" rightProp="managerId"/></join>
</to-many>
```

### Q3: 枚举字段？
**A**: 使用 `ext:dict`：
```xml
<!-- 字段定义 -->
<column name="status" code="STATUS" displayName="用户状态"
        ext:dict="app-sample/user-status" stdDataType="int" stdSqlType="TINYINT"/>

<!-- 字典定义 -->
<dict name="app-sample/user-status" label="用户状态" valueType="int">
    <option code="NORMAL" label="正常" value="0"/>
    <option code="DISABLED" label="禁用" value="1"/>
    <option code="LOCKED" label="锁定" value="2"/>
</dict>
```

**注意**：
- **boolean 型字段不用设置 dict**，直接使用 `stdSqlType="BOOLEAN"` 即可
- dict 主要用于具有多个离散值的枚举类型（如状态、类型等）

**option code 格式要求**：
- 全大写字母，使用下划线分隔（如 `CREATED`, `AUTO_CANCEL`, `GROUPON_EXPIRED`）
- 语义清晰，能表达状态含义（如 `CREATED` 表示已创建，`PAY` 表示已付款）
- 不要使用缩写（如 `AUTO_CANCEL` 而非 `AUTO_CNL`）

### Q4: 如何使用 domain？
**A**: domain 用于定义具有特殊业务规则的字段类型：
- **不是每个字段都需要 domain**，只有具有重要业务含义的字段才需要
- **优先复用** `app-sample.orm.xml` 中已有的 domain（domain 是全局的，不能重名）
- 常见的可复用 domain：
  - `userName`（用户名，50字符 VARCHAR）
  - `email`（邮箱，100字符 VARCHAR）
  - `phone`（电话，100字符 VARCHAR）
  - `userId`（用户ID，50字符 VARCHAR）
  - `deptId`（部门ID，50字符 VARCHAR）
  - `remark`（备注，1000字符 VARCHAR）
  - `createTime`/`updateTime`（时间，TIMESTAMP）
  - `createdBy`/`updatedBy`（操作人，50字符 VARCHAR）

**示例**：订单编号需要特殊格式验证
```xml
<!-- domains 定义 -->
<domain name="orderNo" precision="32" stdSqlType="VARCHAR"/>

<!-- 字段定义 -->
<column name="orderNo" code="ORDER_NO" displayName="订单编号"
        domain="orderNo" mandatory="true" stdSqlType="VARCHAR"/>
```

## 生成注意事项

### 文件保存位置

**默认位置**：`{appName}/model` 目录下
- 例：`app-sample` 模块 → `app-sample/model/app-sample.orm.xml`

**appName 不确定时**：使用 `app-xxx` 格式，根据业务语义推断

### 大文件拆分（Delta 模式）

内容过多时拆分为多个 delta 文件：

**文件结构**：
- Delta 文件：`app-demo-delta-1.orm.xml`、`app-demo-delta-2.orm.xml` ...
  - 每个 delta 都是完整 orm.xml，但**不含 dicts 和 domains**
- 主文件：`app-demo.orm.xml`
  - 通过 `x:extends="app-demo-delta-1.orm.xml,app-demo-delta-2.orm.xml"` 引用
  - 只包含 delta 文件以外的内容

**合并规则**：
- Delta 逐层叠加，高层覆盖底层
- 重复定义以高层为准
- 类似 Docker 分层机制


## 参考文件

- 本目录：`orm.xdef|entity.xdef|dict.xdef`（元模型）
- 本目录：`app-sample.orm.xml`（示例）
