---
name: nop-orm-modeler
description: Generate, validate, and modify Nop ORM models from MySQL DDL/SQL or business requirements. Covers entity modeling, relationships, domains, dictionaries, displayName localization, and ORM file organization (Delta mode). Use for database-first or requirements-first ORM development.
---

# 这个 skill 的定位

本 skill 的核心目标是**构建/修改/维护** Nop 平台的 ORM 模型文件（`.orm.xml`）。

**重要原则**：

- 如果 AI 已经能明确判断并完成任务，就不必拘泥于固定流程；以产出正确、可审阅的 `.orm.xml` 为准。
- 优先使用自动化工具处理，避免手动逐个实体生成。

---

## 什么时候用我

当你的需求涉及以下场景时，请**加载此 skill**：

| 场景               | 触发关键词                          | 示例                                    |
| ---------------- | ------------------------------ | ------------------------------------- |
| 从 DDL/SQL 生成 ORM | "DDL", "SQL", "数据库表", "生成 ORM" | "从 {ddl-file.sql} 生成 {appName} 的 ORM" |
| 根据需求建模           | "需求", "实体", "建模", "创建 ORM"     | "根据需求文档生成 {moduleName} 的 ORM"         |
| 修改/补全 ORM        | "displayName", "补全标签", "添加字段"  | "补全 {appName}-delta-1.orm.xml 的中文标签"  |
| 验证 ORM 配置        | "验证", "检查", "review"           | "验证生成的 ORM 文件配置是否正确"                  |



---

## 前置条件

使用本 skill 前，确认以下工具存在：

| 工具                               | 用途           | 必需性      |
| -------------------------------- | ------------ | -------- |
| `generate_orm_from_mysql_ddl.py` | 从 DDL 生成 ORM | DDL 场景必需 |
| `extract_labels_to_markdown.py`  | 抽取多语言标签      | 可选       |
| `apply_labels_from_markdown.py`  | 回填标签到 ORM    | 可选       |
| DDL 文件或需求文档                      | 输入源          | 必需       |

---

# 处理流程

## 场景判断

根据输入类型选择处理方式：

| 输入类型          | 处理方式                                      |
| ------------- | ----------------------------------------- |
| 已有 DDL/SQL 文件 | 使用 `generate_orm_from_mysql_ddl.py` 自动化生成 |
| 无 DDL，只有需求文档  | 根据需求直接建模，生成 ORM 文件                        |

---

## 方式一：从 DDL/SQL 自动生成

当有 DDL/SQL 文件时，必须使用 `generate_orm_from_mysql_ddl.py` 脚本：

```bash
python3 generate_orm_from_mysql_ddl.py \
    --sql <DDL文件路径> \
    --out <输出ORM文件路径> \
    --app-name <应用名> \
    --base-package <基础包名> \
    --entity-package <实体包名> \
    --dialect <数据库类型>
```

**示例**：

```bash
python3 generate_orm_from_mysql_ddl.py \
    --sql ./tables.sql \
    --out ./my-app-delta-1.orm.xml \
    --app-name my-app \
    --base-package com.example.myapp \
    --entity-package com.example.myapp.dao.entity \
    --dialect mysql
```

**脚本生成内容**：

- 实体定义（entity）
- 字段定义（column），含主键识别
- 唯一键（unique-keys）
- 普通索引（indexes）
- 外键关系（to-one 关系）

**脚本不会生成**（需后续补充）：

- displayName / enDisplayName（中文/英文展示名）
- to-many 关系（一对多关系）
- ext:dict（字典配置）
- domain（业务域定义）

---

## 方式二：从需求直接建模（无 DDL）

当没有 DDL，只有需求文档时，直接根据需求生成 ORM：

### 步骤 1：分析需求，确定模块划分

将实体按业务模块分组，每组约 20 个表。

### 步骤 2：生成 Delta 文件

每个模块生成一个 delta 文件（不含 dicts 和 domains）：

```
{appName}-delta-1.orm.xml  （核心业务实体 ~20个）
{appName}-delta-2.orm.xml  （工作流实体 ~20个）
{appName}-delta-3.orm.xml  （扩展功能实体 ~20个）
...
```

### 步骤 3：生成主文件

主文件只包含公共配置（domains、dicts），通过 `x:extends` 引用所有 delta 文件：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<orm ext:registerShortName="true" ext:appName="{appName}"
     ext:entityPackageName="com.example.{appName}.dao.entity"
     ext:basePackageName="com.example.{appName}"
     x:extends="{appName}-delta-1.orm.xml,{appName}-delta-2.orm.xml,{appName}-delta-3.orm.xml"
     x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef" >

    <domains>
        <!-- 公共 domain 定义 -->
    </domains>

    <dicts>
        <!-- 公共 dict 定义 -->
    </dicts>

    <entities>
        <!-- 空，只通过 x:extends 引用 delta 文件 -->
    </entities>

</orm>
```

**注意**：delta 文件必须是完整的 ORM 结构，但**不包含** dicts 和 domains。

---

## 文件命名规范

| 文件类型     | 命名格式                           | 示例                     |
| -------- | ------------------------------ | ---------------------- |
| Delta 文件 | `{appName}-delta-{序号}.orm.xml` | my-app-delta-1.orm.xml |
| 主文件      | `{appName}.orm.xml`            | my-app.orm.xml         |

---

## 生成后处理

### 补全 displayName（可选）

使用标签抽取脚本补全多语言展示名：

```bash
# 抽取标签到 Markdown
python3 extract_labels_to_markdown.py <orm.xml> > labels.md

# AI 填写 displayName/enDisplayName

# 回填到 ORM
python3 apply_labels_from_markdown.py <orm.xml> labels.md
```

**规则**：

- 中文写 `displayName`
- 英文写 `enDisplayName`（回填到 `i18n-en:displayName`）
- 不覆盖已有值

---

## 生成后验证（必须逐项检查）

### 1. 根节点配置

- [ ] `ext:appName` 符合 `{子系统}-{模块}` 格式，全小写 ASCII
- [ ] `ext:basePackageName` 正确设置，如 `com.example.myapp`
- [ ] `ext:entityPackageName` = `{basePackageName}.dao.entity`
- [ ] `ext:mavenGroupId` / `ext:mavenArtifactId` 正确
- [ ] `ext:dialect` 包含目标数据库方言

### 2. 表名规范

- [ ] 所有表名带统一子系统前缀（如 `{prefix}_*`）
- [ ] 不能是简单单词（如 `user`、`order`）
- [ ] 遵循 `{子系统前缀}_{模块}_{实体}` 格式

### 3. 实体定义

- [ ] 实体类名符合大驼峰规范（`DemoEntity`）
- [ ] 完整包名正确（`com.example.myapp.dao.entity.DemoEntity`）
- [ ] 主键配置：`primary="true"`, `tagSet="seq"`
- [ ] **未手动添加**审计字段（CREATED_BY, CREATE_TIME, UPDATED_BY, UPDATE_TIME, DEL_VERSION, VERSION）

### 4. 字段定义

- [ ] `propId` 从 1 开始连续递增，无重复，无空缺
- [ ] 列名符合大写下划线规范（`APPLICATION_ID`、`USER_NAME`）
- [ ] 字段名符合驼峰规范（`applicationId`、`userName`）
- [ ] `stdSqlType` 必须使用 StdSqlType 枚举类的标准值
  - **可用的 stdSqlType 值**：BOOLEAN|TINYINT|SMALLINT|INTEGER|BIGINT|DECIMAL|FLOAT|DOUBLE|DATE|TIME|DATETIME|TIMESTAMP|CHAR|VARCHAR|VARBINARY|CLOB|BLOB
- [ ] 枚举字段配置 `ext:dict="xxx/yyy"`（**注意：boolean 型字段不用设置 dict**）
- [ ] `domain` 使用原则：
  - **不是每个字段都需要 domain**，只有具有重要业务含义、在显示或后台处理方面存在特殊业务规则的字段才需要定义 domain
  - 例如：订单编号（order-no）可能需要特殊编号规则、状态字段需要特殊显示逻辑等
  - **优先复用**已有 domain，domain 是全局的，不能重名
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
- [ ] 约束名唯一且有前缀（如 `UK_{APP_NAME}_USER_NAME`）

---

## Delta 模式说明

### 文件结构

```
{appName}/
├── model/
│   ├── {appName}.orm.xml          # 主文件（包含 domains、dicts、x:extends）
│   ├── {appName}-delta-1.orm.xml   # Delta 文件 1（核心业务实体）
│   ├── {appName}-delta-2.orm.xml   # Delta 文件 2（工作流实体）
│   └── {appName}-delta-3.orm.xml   # Delta 文件 3（扩展功能实体）
```

### 主文件模板

```xml
<?xml version="1.0" encoding="UTF-8"?>
<orm ext:registerShortName="true" ext:appName="{appName}"
     ext:entityPackageName="com.example.{appName}.dao.entity"
     ext:basePackageName="com.example.{appName}"
     ext:mavenGroupId="com.example" ext:mavenArtifactId="{appName}"
     ext:dialect="mysql" ext:useStdFields="true"
     x:extends="{appName}-delta-1.orm.xml,{appName}-delta-2.orm.xml,{appName}-delta-3.orm.xml"
     x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef" >

    <domains>
        <!-- 公共 domain 定义 -->
    </domains>

    <dicts>
        <!-- 公共 dict 定义 -->
    </dicts>

    <entities>
        <!-- 留空，通过 x:extends 引用 -->
    </entities>

</orm>
```

### Delta 文件模板

```xml
<?xml version="1.0" encoding="UTF-8"?>
<orm ext:registerShortName="true" ext:appName="{appName}"
     ext:entityPackageName="com.example.{appName}.dao.entity"
     ext:basePackageName="com.example.{appName}"
     ext:dialect="mysql" ext:useStdFields="true"
     x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef" >

    <!-- 不包含 domains 和 dicts -->

    <entities>
        <entity ...>
            <columns>...</columns>
            <relations>...</relations>
        </entity>
        <!-- 更多实体 -->
    </entities>

</orm>
```

### 合并规则

- Delta 逐层叠加，高层覆盖底层
- 重复定义以高层为准
- 类似 Docker 分层机制

---

## 常见问题

### Q1: 多对多关系？

**A**: 创建中间表实体：

```xml
<!-- 中间表 -->
<entity name="com.xxx.dao.entity.AppUserRole">
    <columns>
        <column name="userId" code="USER_ID" .../>
        <column name="roleId" code="ROLE_ID" .../>
    </columns>
    <relations>
        <to-one name="user" refEntityName="com.xxx.dao.entity.AppUser"
                tagSet="pub">
            <join><on leftProp="userId" rightProp="userId"/></join>
        </to-one>
        <to-one name="role" refEntityName="com.xxx.dao.entity.AppRole"
                tagSet="pub">
            <join><on leftProp="roleId" rightProp="roleId"/></join>
        </to-one>
    </relations>
</entity>

<!-- 用户实体 -->
<to-many name="roles" refEntityName="com.xxx.dao.entity.AppUserRole"
         refPropName="user" tagSet="pub,cascade-delete,insertable,updatable">
    <join><on leftProp="userId" rightProp="userId"/></join>
</to-many>
```

### Q2: 自关联关系？

**A**: 例如用户上级关系：

```xml
<column name="managerId" code="MANAGER_ID" .../>
<to-one name="manager" refEntityName="com.xxx.dao.entity.AppUser"
        tagSet="pub">
    <join><on leftProp="managerId" rightProp="userId"/></join>
</to-one>
<to-many name="subordinates" refEntityName="com.xxx.dao.entity.AppUser"
         refPropName="manager" tagSet="pub,cascade-delete,insertable,updatable">
    <join><on leftProp="userId" rightProp="managerId"/></join>
</to-many>
```

### Q3: 枚举字段？

**A**: 使用 `ext:dict`：

```xml
<!-- 字段定义 -->
<column name="status" code="STATUS" displayName="用户状态"
        ext:dict="{app}/application-status" stdDataType="int" stdSqlType="INTEGER"/>

<!-- 字典定义 -->
<dict name="{app}/application-status" label="申请状态" valueType="int">
    <option code="CREATED" label="已创建" value="10"/>
    <option code="PROCESSING" label="处理中" value="20"/>
    <option code="COMPLETED" label="已完成" value="30"/>
    <option code="CANCELLED" label="已取消" value="40"/>
</dict>
```

**dict 配置规范**：

- **valueType**：优先使用 `int` 类型，便于数值比较和排序
- **value 值**：使用 10、20、30... 递增（而非 1、2、3），便于后期插入新值（如需要在 10 和 20 之间插入可使用 15）
- **stdSqlType**：状态/枚举字段统一使用 `INTEGER`，**不要**使用 `TINYINT` 或 `SMALLINT`
  - 原因：保持一致性，且 INTEGER 范围足够大，避免后期扩展时的类型变更
- **boolean 型字段不用设置 dict**，直接使用 `stdSqlType="BOOLEAN"` 即可

**option code 格式要求**：

- 全大写字母，使用下划线分隔（如 `CREATED`, `AUTO_CANCEL`）
- 语义清晰，能表达状态含义
- 不要使用缩写

### Q4: 如何使用 domain？

**A**: domain 用于定义具有特殊业务规则的字段类型：

- **不是每个字段都需要 domain**，只有具有重要业务含义的字段才需要
- **优先复用**已有 domain（domain 是全局的，不能重名）
- 常见的可复用 domain：
  - `applicationId`（申请ID，50字符 VARCHAR）
  - `userId`（用户ID，255字符 VARCHAR）
  - `creditLimit`（额度，DECIMAL(38,4)）
  - `createTime`/`updateTime`（时间，TIMESTAMP）
  - `version`（版本号，INTEGER）

**示例**：

```xml
<!-- domains 定义 -->
<domain name="applicationId" precision="50" stdSqlType="VARCHAR"/>

<!-- 字段定义 -->
<column name="applicationId" code="APPLICATION_ID" displayName="申请编号"
        domain="applicationId" mandatory="true" stdSqlType="VARCHAR"/>
```

---

## 交互示例

### 示例 1：从 DDL 生成 ORM

User: "从 {ddl-file.sql} 生成 {appName} 的 ORM，按 20 表一个文件拆分"
AI:

1. 分析 DDL 文件，确定表数量和模块划分
2. 按批次运行生成脚本，生成 delta 文件
3. 生成主 ORM 文件，配置 x:extends
4. 输出文件清单

### 示例 2：补全 displayName

User: "补全 {appName}-delta-1.orm.xml 的中文标签"
AI:

1. 运行 `extract_labels_to_markdown.py`
2. 读取生成的 labels.md，根据字段名和业务含义填写 displayName
3. 运行 `apply_labels_from_markdown.py` 回填

### 示例 3：根据需求建模

User: "根据需求文档生成 {moduleName} 的 ORM，实体列表在文档第 5 节"
AI:

1. 读取需求文档第 5 节，提取实体定义
2. 按 20 个实体一组生成 delta 文件
3. 生成主 ORM 文件引用 delta 文件
4. 生成后验证清单

---

## 参考文件

- 本目录：`generate_orm_from_mysql_ddl.py`（DDL 转 ORM 脚本）
- 本目录：`extract_labels_to_markdown.py`（抽取标签脚本）
- 本目录：`apply_labels_from_markdown.py`（回填标签脚本）
- 本目录：`orm.xdef|entity.xdef|dict.xdef`（元模型）
- 本目录：`app-sample.orm.xml`（示例）

---

## 排障清单

生成/修改 ORM 时遇到问题，按以下优先级排查：

1. **DDL 脚本未运行** - 确认 `generate_orm_from_mysql_ddl.py` 存在且参数正确
2. **表数量过多** - 超过 20 表需使用 Delta 模式拆分
3. **displayName 未补全** - 运行标签抽取/回填脚本
4. **外键关系缺失** - 手动添加 to-one/to-many 关系
5. **domain 冲突** - 检查是否复用已有 domain
6. **x:extends 路径错误** - 确认 delta 文件路径与主文件配置一致

---

## 常见错误与解决

| 错误现象            | 可能原因                     | 解决方法                               |
| --------------- | ------------------------ | ---------------------------------- |
| 生成文件为空          | DDL 路径错误或文件格式不对          | 检查 `--sql` 参数指向的文件存在且有效            |
| 缺少外键关系          | 脚本只生成 to-one，不生成 to-many | 手动补充 to-many 关系配置                  |
| displayName 全为空 | 未运行标签回填脚本                | 执行 `apply_labels_from_markdown.py` |
| x:extends 报错    | delta 文件结构不完整            | 确认 delta 文件包含完整的 `<entities>` 根节点  |
| domain 报错       | domain 名称重复或类型不匹配        | 检查 domain 命名唯一性                    |

---

## 模型验证

生成 ORM 文件后，使用 `nop-cli validate` 验证：

```bash
nop-cli validate my-app.orm.xml
```
