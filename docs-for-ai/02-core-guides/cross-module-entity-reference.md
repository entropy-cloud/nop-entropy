# 跨模块实体引用与关联查询

> **本页定位**：回答多模块应用中最常见也最容易踩坑的问题——**一个模块如何引用另一个模块的实体？关联查询、条件过滤、显示名带出怎么做？**
>
> 本页基于 nop-entropy 平台代码生成器 `nop/orm/xlib/orm-gen.xlib` 的内置用法、`entity.xdef` 权威定义、以及 `nop-persistence/nop-orm` 测试用例实测归纳。不是语法规则（语法见 `orm-model-design.md`、`api-and-graphql.md`），而是帮助判断"应该怎么做"的推理基础。

## TL;DR

nop 平台处理跨模块实体引用有**四种机制**，按场景选用：

| 机制 | 用途 | 是否生成 Entity 类 | 是否能 `<to-one>` 关联 | 是否支持 EQL 点导航 |
|---|---|---|---|---|
| **A. 本模块实体（默认）** | 同模块内聚合根 ↔ 子表 | ✅ 生成 | ✅ | ✅ |
| **B. `notGenCode="true"` 外部实体引用** | **引用其他模块的表**，本模块不生成 Entity | ❌ 复用其他模块已生成的类 | ✅ | ✅ |
| **C. `ext:baseClass` Delta 扩展** | **给已有实体加业务字段**（不引入新表） | ✅ 生成子类 | ✅（扩展后属本模块视图） | ✅ |
| **D. 纯外键列 + `I*Biz`** | 跨模块只读引用主数据/业务单据，**不建立 ORM 关联** | — | ❌ | ❌（需 EQL 子查询） |

**核心规则**：

- **想让本模块实体能与外部模块表建立 ORM 关联、走 EQL 点导航** → 用**机制 B**（`notGenCode="true"` 外部实体引用）。这是平台代码生成器 `orm-gen.xlib` 自身使用的官方范式。
- **想给已有实体（平台/主数据）加业务字段** → 用**机制 C**（`ext:baseClass` Delta 扩展），**不要**把它误当成"引用外部表"。
- **断开 ORM 关联也能工作**（列表冗余显示名 + 详情 `@BizLoader` + 复杂查询用 EQL 子查询） → 用**机制 D**，但代价是失去 ORM 关联与 EQL 自动 join 的便利。

## 1. `entity.xdef` 的权威定义

平台 `nop/schema/orm/entity.xdef` 对 `notGenCode` 属性的注释原文：

> **`@notGenCode [是否生成代码]`** 如果设置为 true，则代码生成时将跳过本实体对象，不为它生成实体类。**当我们引用其他模块的实体类时应该设置此属性，从而避免在本模块中生成其他模块的实体类。**

这是平台层面对"跨模块引用外部实体"的**官方答案**。注意：

- `notGenCode` 可出现在 **`<entity>`（实体级）、`<column>`（字段级）、`<to-one>`/`<to-many>`（关系级）、`<alias>`、`<compute>`、`<component>`** 上，语义各有不同。本页关注**实体级**——跨模块引用外部表。

## 2. 机制 B：`notGenCode="true"` 外部实体引用（平台内置范式）

### 2.1 平台代码生成器的标准用法

nop-entropy 的 `nop/orm/xlib/orm-gen.xlib`（平台 ORM 代码生成库）内部，在为一个业务实体自动添加"扩展字段"关联时，是这样引用 `nop-sys` 模块的 `NopSysExtField` 表的：

```xml
<!-- 引入外部表定义 -->
<entity displayName="扩展字段"
        name="io.nop.sys.dao.entity.NopSysExtField"   <!-- 外部模块的实体全限定名 -->
        registerShortName="true"
        notGenCode="true"                              <!-- 关键:不在本模块生成 Entity 类 -->
        tableName="nop_sys_ext_field">                 <!-- 外部模块已存在的表 -->
    <columns>
        <column code="ENTITY_NAME" name="entityName" primary="true" .../>
        <column code="ENTITY_ID"   name="entityId"   primary="true" .../>
        <column code="FIELD_NAME"  name="fieldName"  primary="true" .../>
        <!-- 只需声明本模块会用到的列，由 nop-sys 模块负责表的真实 DDL -->
    </columns>
</entity>
```

然后本模块的实体可以正常建立 `<to-many>` 关联：

```xml
<entity name="app.demo.entity.MyOrder">
    <relations>
        <to-many name="extFields" refEntityName="io.nop.sys.dao.entity.NopSysExtField"
                 keyProp="fieldName">
            <join>
                <on leftProp="id" rightProp="entityId"/>
                <on leftValue="app.demo.entity.MyOrder" rightProp="entityName"/>
            </join>
        </to-many>
    </relations>
</entity>
```

### 2.2 机制 B 的语义

| 维度 | 行为 |
|---|---|
| **代码生成** | 本模块 codegen 时**跳过**该实体，不生成 `MyOrder.java`/`_MyOrder.java`。运行时由 nop-sys 模块已生成的 `NopSysExtField.class` 提供 |
| **DDL 生成** | 本模块 codegen 时**不生成**该表的建表 SQL（因为 `notGenCode` 跳过整个实体）。建表由 nop-sys 负责 |
| **ORM 关联** | 本模块实体可以用 `<to-one>`/`<to-many>` 引用它的 `refEntityName`，**ORM 运行时会按 className 加载实体类** |
| **EQL 点导航** | `o.extFields.fieldName` 这种点导航在 EQL 中**自动 LEFT JOIN** 工作正常（因为 ORM 关系已声明） |
| **GraphQL 展开** | `extFields { fieldName }` 在 GraphQL 查询中正常展开 |
| **运行时依赖** | 本模块运行时 classpath 必须有外部模块的 `*-dao` 包（即本模块 pom 依赖外部模块的 `*-dao`） |

### 2.3 机制 B 的 Maven 依赖

机制 B 的依赖分两层，缺一不可：

**dao 层依赖**（让 codegen 找到外部 Entity 类、EQL 能跨模块 join）：

```xml
<!-- 本模块 dao 的 pom.xml -->
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-sys-dao</artifactId>  <!-- 引用外部模块已生成的 Entity 类 -->
</dependency>
```

**web/app 层依赖**（让运行时 VFS 能访问外部模块的页面资源，如 picker.page.yaml / view.xml）：

```xml
<!-- 本模块 app 的 pom.xml -->
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-sys-web</artifactId>  <!-- 引用外部模块的页面资源 -->
</dependency>
```

> dao 层依赖是 codegen 必需（否则 `refEntityName` 找不到 Entity 类）；web 层依赖是运行时必需——to-one 关联字段的 picker 页面在外部模块的 `-web` 里，消费方 app 不依赖它，VFS 就找不到 `/erp/md/pages/ErpMdPartner/picker.page.yaml`，启动期 `PageModelValidator` 报 `parse-missing-resource`。

**配合 `biz:moduleId`**：仅加 Maven 依赖还不够。外部实体声明还必须标注 `biz:moduleId` 指向 owner 域，否则 picker 路径会被回退逻辑推导到消费方域（路径对了依赖也访问不到）。详见 `./model-first-development.md` 的"跨模块实体引用与 biz:moduleId 约定"。

> 这是单向依赖（本模块 → 外部模块），**符合 `core → dao → meta → service → web → app` 的 DAG**。不会形成循环。

### 2.4 何时该用机制 B

| 场景 | 是否用机制 B |
|---|---|
| 本模块实体需要频繁关联查询外部表（如订单→用户对象、订单→扩展字段表） | ✅ **推荐** |
| 想让 EQL 能用 `o.user.userName` 点导航跨模块自动 join | ✅ **必需** |
| 想让 GraphQL 能展开 `order { user { userName } }` 跨模块对象树 | ✅ **必需** |
| 只想在列表显示外部对象的显示名 | ❌ 用机制 D 的冗余字段（更简单） |
| 只是想给外部实体加业务字段 | ❌ 用机制 C（`ext:baseClass`） |

## 3. 机制 C：`ext:baseClass` Delta 扩展（不要混淆）

**机制 C 不是"引用外部表"，是"给外部实体加字段"**。两者完全不同：

```xml
<!-- 机制 C: Delta 扩展(给 NopAuthUser 加业务字段) -->
<entity className="app.mall.delta.dao.entity.NopAuthUserEx"  <!-- 新生成子类 -->
        name="io.nop.auth.dao.entity.NopAuthUser"             <!-- 复用平台实体名 -->
        tableName="nop_auth_user"                             <!-- 复用平台表 -->
        ext:baseClass="io.nop.auth.dao.entity.NopAuthUser">   <!-- 继承平台实体类 -->
    <columns>
        <column code="USER_ID" name="userId" notGenCode="true" tagSet="seq,not-gen" .../>  <!-- 继承字段标 not-gen -->
        <column code="PIC_URL" name="picUrl" .../>            <!-- 新增业务字段 -->
    </columns>
</entity>
```

| 维度 | 机制 B（外部实体引用） | 机制 C（Delta 扩展） |
|---|---|---|
| 目的 | 在本模块"看到"外部表，建立关联 | 给外部实体加业务字段 |
| `name` | 外部实体全限定名 | 外部实体全限定名（同） |
| `className` | 不生成（`notGenCode`） | **新生成子类**（如 `NopAuthUserEx`） |
| `tableName` | 外部表（不建表） | 外部表（不建表） |
| 是否需要 `ext:baseClass` | ❌ 不需要 | ✅ 必需 |
| 字段标注 | 仅声明本模块会用到的列 | 继承字段标 `tagSet="...,not-gen"`，新增字段不标 |
| 表的 DDL 责任 | 外部模块 | 外部模块（机制 C 不改表结构，只加列声明） |

> **常见误解**：把 nop-app-mall 的 `nop-auth-delta.orm.xml`（机制 C）当成"跨模块引用外部表"（机制 B）。实际它是"扩展用户实体加商城业务字段"，不是引用外部表。nop-app-mall **没有**用机制 B，因为商城业务实体（`LitemallOrder` 等）不直接 ORM 关联到 `NopAuthUser`，而是用 `userId` 字符串外键 + `LitemallUser`（本模块独立用户表）。

## 4. 机制 D：纯外键列 + `I*Biz`（断开关联）

详见 `domain-logic-and-ddd.md §2` 与 `eql-and-database-compatibility.md`。核心做法：

- orm.xml 只写外键列，不写 `<to-one>` 关系声明
- 详情展开用 `@BizLoader` + `requireBiz(IXxxBiz.class)` 懒加载
- 列表过滤用 BizModel 的 `QueryBean + FilterBeans.in` 两步查询
- 报表用 EQL 子查询 `WHERE o.supplierId IN (SELECT p.id FROM ErpMdPartner p WHERE ...)`

**何时用机制 D**：主数据引用、列表只需显示名、详情偶尔带出、报表偶尔按外部条件过滤。代价：失去 EQL 点导航与 GraphQL 自动展开。

## 5. 实测：四种机制的源码证据

| 机制 | 源码证据 |
|---|---|
| **A** | 所有模块的本模块 `<to-one>` 关系（如 nop-wf 内部的 `wfInstance` → `wfStepInstance`） |
| **B** | `nop/orm/xlib/orm-gen.xlib:228`（平台代码生成器引用 `NopSysExtField` 的标准范式） + `entity.xdef` 官方注释 |
| **C** | nop-app-mall `model/nop-auth-delta.orm.xml`（扩展 `NopAuthUser`） |
| **D** | nop-wf `NopWfWork.ownerId`+`ownerName`（纯外键+冗余名）；nop-app-mall `LitemallOrderBizModel`（`QueryBean + in`） |

> **关键观察**：实测 9 个 nop-entropy 内置模块源 orm.xml **零跨模块 refEntityName**（`nop-auth`/`nop-sys`/`nop-wf`/`nop-batch`/`nop-rule`/`nop-job`/`nop-tcc`/`nop-report`/`nop-dyn`）。这说明内置模块互不直接关联——它们通过 `I*Biz` 在 service 层协作。但**这并不禁止**应用层用机制 B 引用平台表——`orm-gen.xlib` 自身就演示了这种用法。

## 6. 选择决策树

```
本模块需要关联查询外部表？
├─ 需要 ORM 关联 + EQL 点导航 + GraphQL 展开
│   └─ 【机制 B】 notGenCode="true" 外部实体引用
│       └─ 同时想给外部实体加业务字段？
│           └─ 【机制 B + C 组合】 引用 + Delta 扩展
│
├─ 只在列表显示外部对象的显示名
│   └─ 【机制 D】 纯外键 + 冗余显示名字段
│
├─ 偶尔在详情页带出完整外部对象
│   └─ 【机制 D】 @BizLoader + requireBiz + @LazyLoad
│
└─ 报表按外部条件过滤本表
    └─ 【机制 D】 EQL 子查询 IN
```

## 7. 主数据域（master-data）的处理策略

对于 nop-app-erp，`master-data` 被 8 个业务域 ~120 处引用。**默认推荐机制 D**（纯外键 + 冗余显示名 + `@BizLoader`），原因：

1. 主数据引用极多，全用机制 B 会让业务模块的 `-dao` 强依赖 `master-data-dao`（虽符合 DAG，但增加编译耦合）
2. 列表场景只需显示名，冗余字段零成本
3. 详情场景 `@BizLoader` 按需加载，不影响列表性能

**对高频关联查询场景，用机制 B**。例如 `finance` 凭证行常需按 `subjectId`/`partnerId`/`projectId`/`warehouseId`/`materialId` 多维度关联查询——这时在 finance orm.xml 引用 master-data 的核心表用 `notGenCode="true"`，让 EQL 能点导航：

```xml
<!-- app-erp-finance/model/app-erp-finance.orm.xml -->
<entities>
    <!-- 已有的本模块实体 -->
    <entity className="app.erp.fin.dao.entity.ErpFinVoucherLine" ...>
        <relations>
            <!-- 用机制 B 引用的外部实体建立关联 -->
            <to-one name="subject" refEntityName="app.erp.md.dao.entity.ErpMdSubject">
                <join><on leftProp="subjectId" rightProp="id"/></join>
            </to-one>
        </relations>
    </entity>

    <!-- 【机制 B】 引用外部表,不生成本模块 Entity 类 -->
    <entity displayName="会计科目"
            name="app.erp.md.dao.entity.ErpMdSubject"
            notGenCode="true"
            tableName="erp_md_subject">
        <columns>
            <column name="id" .../>
            <column name="code" .../>
            <column name="name" .../>
            <!-- 只声明 finance 会用到的列 -->
        </columns>
    </entity>
</entities>
```

```xml
<!-- app-erp-finance/pom.xml -->
<dependency>
    <groupId>io.nop.app</groupId>
    <artifactId>app-erp-master-data-dao</artifactId>  <!-- 复用 master-data 的 Entity 类 -->
</dependency>
```

```sql
-- EQL 可以直接点导航
SELECT vl.subject.name, SUM(vl.drAmount)
FROM ErpFinVoucherLine vl
WHERE vl.subject.subjectClass = 10   -- 跨模块条件过滤,自动 LEFT JOIN
GROUP BY vl.subject.name
```

### 主数据分级处理建议

| 引用场景 | 推荐机制 | 理由 |
|---|---|---|
| 列表显示物料/往来单位/仓库的名称 | **D**（冗余字段） | 高频，零 join |
| 详情页带出完整主数据对象 | **D**（`@BizLoader`） | 按需，不污染列表 |
| 财务凭证行按多维主数据（科目/伙伴/项目/仓库/物料）筛选 | **B**（外部实体引用） | EQL 点导航 + GraphQL 展开 |
| 库存流水按物料/仓库/批次多维分析 | **B** | 同上 |
| 业务表之间引用（如凭证反查源单） | **D**（弱指针字符串） | 业务单据有生命周期，不宜强关联 |

## 8. 字段级 / 关系级 notGenCode（顺带说明）

`notGenCode` 不止出现在实体上：

| 出现位置 | 语义 | 实例 |
|---|---|---|
| `<entity notGenCode="true">` | 不生成本模块 Entity 类（**机制 B**） | `orm-gen.xlib:228` 引用 `NopSysExtField` |
| `<column notGenCode="true">` | 不生成该字段的 get/set，作为动态属性存取 | Delta 扩展中继承自父类的字段；测试 orm `SimsExam.extField` |
| `<to-one notGenCode="true">` / `<to-many notGenCode="true">` | 关系在运行时有效，但不生成关联字段的 get/set | 测试 orm `SimsClass.refByName`、`SimsCollege.ext`（`app.orm.xml:57,71`） |
| `<alias notGenCode="true">` / `<compute>` / `<component>` | 同上，运行时有效但不生成代码 | 测试 orm `SimsExam.extFldB`（`app.orm.xml:128`） |

> **共性**：所有 `notGenCode="true"` 的语义都是"运行时模型中有这个定义，但不生成本模块的 Java 代码"。运行时通过动态属性机制（`OrmEntity.orm_propValueByName(name)`）或父类提供访问。

## 9. 反模式

| 反模式 | 后果 | 正确做法 |
|---|---|---|
| 跨模块 `<to-one>` 但被引用实体**不在本模块 orm.xml 声明**、也没 Maven 依赖 | codegen 失败（找不到 refEntityName） | 用机制 B 在本模块声明 `notGenCode` 外部实体，加 Maven 依赖 |
| 用 `ext:baseClass` Delta 扩展去做"引用外部表" | 概念混淆：扩展给外部实体加了字段，没解决关联需求 | 区分机制 B vs C，按场景选 |
| 把 `notGenCode` 当 Delta 用（声明同 className 子类） | 重复生成类冲突 | 机制 B 不设 `className`，机制 C 设新 `className` + `ext:baseClass` |
| 跨模块引用但本模块 pom 不依赖外部模块的 `-dao` | 运行时 `ClassNotFoundException` | 在 pom 加外部模块 `-dao` 依赖 |
| 跨模块引用外部实体做 picker，但消费方 `-app` 不依赖外部模块的 `-web` | 启动期 `parse-missing-resource`（找不到 picker.page.yaml） | 在消费方 `-app` pom 加外部模块 `-web` 依赖 |
| 外部实体声明没标 `biz:moduleId` | picker 路径回退到消费方域，即便依赖齐全也 404 | 声明 `biz:moduleId` 指向 owner 域（见 `model-first-development.md`） |
| 业务表直接 `@Inject` 外部 BizModel 实现类 | 不可替换、不可扩展 | `@Inject IXxxBiz` 接口 |
| 在 Entity 里做跨模块写操作 | 绕过事务/权限管道 | 写操作走 BizModel 的 `@BizMutation` |

## 10. 相关文档

- `orm-model-design.md` — `<to-one>` / `<to-many>` 语法规范（本模块内）
- `model-first-development.md` — **跨模块实体引用与 `biz:moduleId` 约定**（外部实体声明必须标 `biz:moduleId`，否则跨域 picker 路径错）
- `entity.xdef` — `notGenCode` / `ext:baseClass` / `tableView` 等实体属性的权威定义
- `delta-customization.md` — Delta 定制机制（机制 C 的基础）
- `api-and-graphql.md` — `@BizLoader` / `graphql:filter` / 关联字段查询元数据
- `eql-and-database-compatibility.md` — EQL 语法、`<eql>` 与 `<sql>` 区别（点导航依赖机制 B 的关系声明）
- `domain-logic-and-ddd.md §2-3` — `requireBiz()` 跨实体只读查询 + 实体缓存（机制 D 的实现）
- `architecture-principles.md` — 模块依赖单向 DAG 规则
- `../03-runbooks/add-cross-module-biz-interface.md` — 新增跨模块 `I*Biz` 接口（机制 D）
- `../03-runbooks/add-bizloader-field.md` — 给已有字段补 `@BizLoader`（机制 D）
- `../03-runbooks/extend-api-with-delta-bizloader.md` — Delta + `@BizLoader(autoCreateField=true)` 扩展 API
