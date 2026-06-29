# 模型优先开发

当前仓库的默认开发顺序不是“先写 Java”，而是：

**先模型，再生成，再补保留层代码。**

## 默认流程

1. 修改 `model/*.orm.xml`。
2. 首次建模块时使用 `nop-cli gen` 生成骨架。
3. 后续变更优先用 `./mvnw` 触发再生成与构建。
4. 只在非生成文件中写定制逻辑。

## 首次生成骨架

```bash
nop-cli gen model/{appName}.orm.xml -t=/nop/templates/orm -o=.
```

这个命令的用途是生成标准业务骨架，而不是日常每次改模型都重跑一遍。

## 后续模型变更

默认优先在项目根目录执行：

```bash
./mvnw clean install -T 1C
```

## 模型加载后的 Java 初始化

不是所有派生逻辑都适合继续放在 XML / imp / codegen 里。

当 source 模型在加载完成后，还需要：

1. 回填父上下文
2. 构建运行期索引
3. 规范化类名/类型名
4. 递归初始化子模型

优先考虑在模型类中实现 `INeedInit`，把这些逻辑收口到 Java `init()`。

标准 XDSL 解析链会自动调用 `INeedInit.init()`；但如果你是手工 new、手工 merge、手工转换模型，通常需要自己补 `init()`。

详见：`./model-init-and-ineedinit.md`

## 实体菜单图标约定

当 ORM 实体会参与标准后台页面与 `action-auth.xml` 生成时，可以在 `entity` 上声明 `ext:icon`：

```xml
<entity ... ext:icon="user-round">
```

规则如下：

1. `ext:icon` 配置在 `model/*.orm.xml` 的 `entity` 节点。
2. 值优先使用 kebab-case 的 Lucide 图标名，例如 `user-round`、`building-2`、`git-branch`。
3. 该值会传播到生成的 `xmeta` 根节点 `ext:icon`。
4. ORM web 模板生成 `_*.action-auth.xml` 时，会优先使用 `objMeta['ext:icon']` 作为菜单图标；未配置时回退到默认图标。
5. 当前仓库约定下，source `model/*.orm.xml` 中参与标准菜单生成的实体都应显式设置 `ext:icon`；不要再把 entity icon 留给默认值兜底。

这个约定只影响基于 ORM 标准模板生成的菜单资源；手工维护的 `*.action-auth.xml` 覆盖文件需要单独调整。

## 模块菜单图标约定

当 ORM 模块会参与标准后台页面与 `action-auth.xml` 生成时，可以在根 `<orm>` 上声明 `ext:icon`：

```xml
<orm ... ext:icon="shield">
```

规则如下：

1. 根 `ext:icon` 配置在 source `model/*.orm.xml` 的 `<orm>` 节点。
2. `{appName}-meta` 会生成 `/{moduleId}/model/_module-meta.json`，并生成 `module-meta.json` 通过 `x:extends` 继承它。
3. `{appName}-web` 的 ORM 页面模板通过 `loadDeltaJson("/{moduleId}/model/module-meta.json")` 读取模块级 meta，而不是直接依赖 `/{moduleId}/orm/app.orm.xml`。
4. ORM web 模板生成 `_*.action-auth.xml` 时，会优先使用 `moduleMeta.icon` 作为 TOPM 图标；未配置时回退到 `ion:grid-outline`。
5. `entity ext:icon` 的传播链保持不变，仍然用于生成 SUBM 图标。
6. 当前仓库约定下，每个 source `model/*.orm.xml` 都应显式设置根 `<orm ext:icon>`；不要依赖 `ion:grid-outline` 作为模块菜单的长期默认图标。

这个约定的目的，是让 `*-web` 只依赖 `*-meta` 输出，不要求 `*-web` 一定能访问 `*-dao` 下的 ORM 产物。

## 手写菜单图标约定

如果在 source `*.action-auth.xml` 中手写 `resourceType="TOPM"` 或 `resourceType="SUBM"` 的菜单资源，也必须显式提供 `icon`。

规则如下：

1. 这条规则适用于 source `*.action-auth.xml`，不适用于 generated `_*.action-auth.xml`。
2. 只要资源显式声明为 `TOPM` 或 `SUBM`，就不要省略 `icon`。
3. 手写菜单不能假设生成链会自动替它补 icon；需要在 source 文件中自行给出。

## 跨模块实体引用与 `biz:moduleId` 约定

多模块项目里，当一个域跨模块引用另一个域的实体（通过 `<entity notGenCode="true">` 外部实体引用），被引用实体必须在声明上标注它真正归属的模块，否则生成的页面会按"消费方域"推导路径，找不到 owner 域的页面。

### `biz:moduleId` 是什么

`biz:moduleId` 是扩展属性（`biz:` 命名空间），声明"本实体/本关联字段引用的目标对象归属哪个 VFS 模块"：

- 写在 ORM `<entity>` 上（`nop/schema/orm/entity.xdef` 的 `biz:moduleId`），声明该实体本身归属的模块。这是 codegen 读取的权威源。
- 写在 xmeta 的 to-one/to-many `<prop>` 上（`nop/schema/xui/disp.xdef` 的 `biz:moduleId`），由 codegen 从被引用实体的 `biz:moduleId` 透传而来，供前端运行时读取。

### 取值格式

两级斜杠 VFS 模块路径，如 `erp/md`、`nop/auth`、`erp/purchase`——对应 `_module-meta.json` 的 `moduleId` 字段（注意不是 `moduleName` 的 `-` 形式，也不是 Maven 包名）。例如 `nop-auth` 模块生成的资源在 `/_vfs/nop/auth`，moduleId 是 `nop/auth`。

### 如何影响 picker 路径

运行时 `XuiHelper.getRelationPickerUrl` 按 `biz:moduleId` + `bizObjName` 拼出关联字段的 picker 页面路径：

```
"/" + biz:moduleId + "/pages/" + bizObjName + "/picker.page.yaml"
```

例如 `biz:moduleId="erp/md"`、`bizObjName="ErpMdPartner"` → `/erp/md/pages/ErpMdPartner/picker.page.yaml`。

推导有三层优先级：
1. 若 `<prop>` 或 `<disp>` 上显式设了 `ui:pickerUrl`（完整路径），直接用，跳过 `biz:moduleId` 推导。
2. 否则用 `<prop>` 上的 `biz:moduleId`（codegen 透传来）。
3. 若 `biz:moduleId` 为空，回退为从当前 xmeta 文件路径取前两段（`ResourceHelper.getModuleIdFromStdPath`）。

### 为什么多模块跨域引用必须显式设置

跨域引用的外部实体（`<entity notGenCode="true">`）若不声明 `biz:moduleId`，codegen 模板 `_{shortName}.xmeta.xgen` 中 `biz:moduleId="${rel.refEntityModel['biz:moduleId']}"` 取到空值，生成的 prop 上 `biz:moduleId` 为空。运行时落入回退分支 3，用消费方域的 xmeta 路径推导，于是 picker 指向消费方域（例如 purchase 域引用 ErpMdPartner，picker 被错误拼成 `/erp/pur/pages/ErpMdPartner/picker.page.yaml`），而 owner 域的页面（`/erp/md/...`）找不到，启动期 `PageModelValidator` 报 `parse-missing-resource`。

单模块项目不受影响，因为回退分支 3 推出的前缀恰好就是 owner 模块。

### 正确写法

在消费方域的 `model/*.orm.xml` 里，外部实体引用必须带上 `biz:moduleId` 指向 owner 域（根 `<orm>` 需声明 `xmlns:biz="biz"`）：

```xml
<orm ... xmlns:biz="biz">
  <entity displayName="往来单位" name="app.erp.md.dao.entity.ErpMdPartner"
          notGenCode="true" biz:moduleId="erp/md" tableName="erp_md_partner">
    <columns>...</columns>
  </entity>
</orm>
```

改完后重新生成 meta/web，生成的 xmeta 的 to-one prop 会带上 `biz:moduleId="erp/md"`，picker 路径随之正确。个别需要指向非标准页面的关联，用 `ui:pickerUrl` 精确覆盖即可。

如果只想理解生成职责，可以按模块顺序看：

1. `{appName}-codegen` 刷新项目级生成产物。
2. `{appName}-dao` 接收 ORM、Entity、接口等结果。
3. `{appName}-meta` 生成 XMeta 与 i18n。
4. `{appName}-meta` 也会生成模块级 `module-meta.json`，供 web 层读取模块图标等元数据。
5. `{appName}-web` 基于 XMeta 与 `module-meta.json` 生成页面相关文件；如果 web 侧 i18n 依赖第一轮生成出的 `action-auth.xml`，则需要在 `precompile2` 中二次生成。
6. `{appName}-service` / `{appName}-app` 接收对应派生产物并参与构建。

## 真实链路

### 模块级（谁生成谁）

```text
model/{app}.orm.xml
  -> {app}-codegen/postcompile/gen-orm.xgen
  -> {app}-dao / {app}-service / {app}-meta / {app}-web 的基础产物
  -> {app}-meta/precompile/gen-meta.xgen
  -> XMeta
  -> {app}-meta/postcompile/gen-i18n.xgen
  -> i18n
  -> {app}-web/precompile/gen-page.xgen
  -> view/page 文件
```

### 文件级（gen-orm.xgen 内部三步）

`{app}-codegen/postcompile/gen-orm.xgen` 内部依次执行三次 `renderModel`，这一段是“改了 model 之后到底生成哪些文件”的核心。以 nop-job 为例（其他业务模块同理）：

```text
第 1 步：  model/{app}.orm.xml
            -- 模板 /nop/templates/orm -->
          {app}-dao/_vfs/.../orm/_app.orm.xml   （聚合 ORM，生成物）
          以及 beans、api 骨架等项目级产物

第 2 步：  {app}-dao/_vfs/.../orm/app.orm.xml   （x:extends _app.orm.xml）
            -- 模板 /nop/templates/orm-entity -->
          {app}-dao/src/main/java/.../entity/_gen/_Nop*.java   （实体类，生成物）

第 3 步：  app.orm.xml
            -- 模板 /nop/templates/orm-model -->
          其他模型派生产物
```

要点：

1. **唯一的手编辑入口是 `model/{app}.orm.xml`**。改数据结构（表、字段、dict、关系）只改这里。
2. **`_app.orm.xml` 是第 1 步的生成物，不是源**。它聚合了 model 的内容，并加上 `orm-gen` 宏展开的标准列。手改它会在下次生成时被覆盖。
3. **`app.orm.xml` 通过 `x:extends="_app.orm.xml"` 继承** `_app.orm.xml`，自身通常只有一个空 `<entities/>`，用于运行时承载 delta。第 2 步从它出发生成实体，实际内容仍来自 `_app.orm.xml`。
4. **`_gen/_Nop*.java` 是第 2 步的生成物**，含 `PROP_NAME_*` 常量、字段、getter/setter。需要新字段的 getter/setter，就改 model 重新生成，不要手改 `_gen`。
5. 改完 model 后，实体的新 getter/setter 不是立刻出现的，必须触发 gen-orm.xgen（重新构建）之后才能在代码里引用。

> **易错点**：`_app.orm.xml` 包含完整的实体/字段/dict 定义，看起来像“源”，但它是从 `model/*.orm.xml` 生成的。判断一个 ORM 文件是不是源，只看路径：在 `model/` 目录下的是源；在 `_vfs/.../orm/` 下且以 `_` 开头的是生成物。

## AI 的默认修改顺序

| 目标 | 默认修改位置 |
|------|-------------|
| 表、字段、关系、字典 | `model/*.orm.xml` |
| 页面或元数据派生能力 | 先看 model / xmeta / xbiz |
| 升级友好的产品定制 | Delta |
| 实体辅助方法 | 保留层 Entity |
| 业务接口逻辑 | BizModel / Processor |

## 什么时候不要直接写 Java

以下情况先不要手写服务或 DAO：

1. 只是新增字段、校验、字典、关系。
2. 标准 CRUD 已经够用。
3. 页面和 API 结构可以由模型派生。
4. 只是要在现有产品上做差量定制。

## 不能手改的典型文件

以下文件都是 codegen 生成物，手改会在下次 `mvn install` 时被覆盖。改数据结构一律回到 `model/*.orm.xml`：

- `_gen/` 目录（`_Nop*.java` 等实体类，gen-orm.xgen 第 2 步产物）。
- `_app.orm.xml`（聚合 ORM，gen-orm.xgen 第 1 步产物）。**这是最容易被误当成“源”去手改的文件**——它含完整实体/字段/dict 定义，但都是从 `model/*.orm.xml` 生成的。
- `_service.beans.xml`。
- `_*.xbiz`、`_*.view.xml`、`_*.java`。

判断一个 ORM 文件能不能改，只看路径：`model/*.orm.xml` 是源（可改）；`_vfs/.../orm/` 下以 `_` 开头的是生成物（不可改）。

## ORM 列类型与 JSON 组件

### VARCHAR precision → 实际 SQL 类型的自动选择

框架在 `SqlDataTypeMapping` 中根据 dialect 配置自动将 `stdSqlType="VARCHAR"` + `precision` 映射为最合适的数据库类型（以 MySQL 为例）：

| precision 范围 | 实际 SQL 类型 |
|---|---|
| ≤ 16383 | `VARCHAR(N)` |
| 16384 ~ 65535 | `TEXT` |
| 65536 ~ 16777215 | `MEDIUMTEXT` |
| 更大或无限制 | `LONGTEXT`（即 CLOB） |

H2 等 embedded 数据库没有中间类型，超过 VARCHAR 上限直接映射为 `CLOB`。

**设计建议**：按实际数据大小设置 precision，框架会自动选类型。不要为了避免截断而设置过大的值——过大会导致 MySQL 用 TEXT/MEDIUMTEXT 而非 VARCHAR，影响索引和查询性能。

### stdDomain="json" 自动生成 JsonOrmComponent

#### 触发条件

当 ORM 模型的 column 满足以下任一条件时（由 `orm-gen.xlib` 的 `JsonComponentSupport` 在元编程阶段处理）：

1. **`stdDomain="json"`**（推荐，全仓库主流用法）
2. **`tagSet` 包含 `"json"`**

> **推荐优先使用 `stdDomain="json"`**，配合 domain 使用可复用预定义 precision。`tagSet="json"` 仅为测试代码使用，生产代码不推荐。

#### ORM 模型配置方式

**方式一：直接声明 stdDomain（简单场景）**

```xml
<column name="extConfig" code="EXT_CONFIG" displayName="扩展配置"
        stdDomain="json" stdSqlType="VARCHAR" precision="4000"/>
```

**方式二：通过 domain 复用预定义 precision（推荐）**

框架在 `default.orm.xml` 中预定义了以下 JSON domain：

| domain 名 | precision | 适用场景 |
|-----------|-----------|---------|
| `json-1000` | 1000 | 小型配置 JSON |
| `json-4k` | 4000 | 中等配置 JSON |

使用方式：

```xml
<!-- 在 orm.xml 根 <domains> 中引用（如已声明则省略） -->

<column name="jobParams" code="JOB_PARAMS" displayName="任务参数"
        domain="json-1000" stdSqlType="VARCHAR"/>
```

如果预定义 domain 不满足需求，可在 orm.xml 的 `<domains>` 中自定义：

```xml
<domains>
    <domain name="json-128K" precision="131072" stdDomain="json" stdSqlType="VARCHAR"/>
</domains>
```

#### 自动生成产物

框架在元编程阶段（`JsonComponentSupport`）自动注入一个 `<component>` 到 ORM 实体模型：

```xml
<!-- 自动生成，不需要手写 -->
<component name="extConfigComponent" needFlush="true"
           className="io.nop.orm.component.JsonOrmComponent">
    <prop name="_jsonText" column="extConfig"/>
</component>
```

同时 codegen 生成的 `_Meta` 类中会自动完成组件绑定：

```java
// 自动生成的 _Meta 类（不需要手写）
private JsonOrmComponent _extConfigComponent;

public JsonOrmComponent getExtConfigComponent() {
    if (_extConfigComponent == null) {
        _extConfigComponent = new JsonOrmComponent();
        _extConfigComponent.bindToEntity(this);
    }
    return _extConfigComponent;
}
```

#### Java 代码中使用

不需要手动序列化/反序列化，直接操作 JSON 对象：

```java
// 写入 JSON
entity.getExtConfigComponent().set_jsonValue(Map.of("key", "value"));

// 读取 JSON（自动反序列化，结果为 Map 或 List）
Object data = entity.getExtConfigComponent().get_jsonValue();

// 动态属性访问（像操作普通属性一样）
entity.getExtConfigComponent().prop_set("someKey", "someValue");
Object val = entity.getExtConfigComponent().prop_get("someKey");
```

#### XMeta 与 GraphQL 配置

codegen 生成的 XMeta 中会自动设置两个关键属性：

```xml
<!-- 原始字符串字段 -->
<prop name="extConfig" graphql:jsonComponentProp="extConfigComponent">
    <schema type="java.lang.String"/>
</prop>

<!-- JsonOrmComponent 组件（自动注册，不需要手写） -->
<prop name="extConfigComponent" ext:kind="component" internal="true">
    <schema type="io.nop.orm.component.JsonOrmComponent"/>
</prop>
```

- **`graphql:jsonComponentProp`**：指向对应的 `JsonOrmComponent` prop 名。运行时 `XuiViewAnalyzer` 读取此属性，自动将 component prop 加入 GraphQL field selection，确保查询返回组件所需的原始数据，前端可直接获取结构化 JSON。
- **不需要手动配置**：整个过程由 codegen 自动完成，开发者无需干预。

#### 类似的自动组件

| stdDomain | 组件类 | 说明 |
|-----------|--------|------|
| `json` | `JsonOrmComponent` | JSON 对象操作 |
| `xml` | `XmlOrmComponent` | XML 对象操作 |
| `file` | `OrmFileComponent` | 单文件附件 |
| `file-list` | `OrmFileListComponent` | 多文件附件列表 |

## 修改包名的影响与迁移步骤

`ext:basePackageName` 和 `ext:entityPackageName` 不仅影响 orm.xml 中的实体名，还决定了 codegen 生成 Java 类的包名和目录位置。修改这两个属性后，必须同步迁移已有的 Java 源文件，否则编译失败。

### 影响范围

| 影响项 | 说明 |
|--------|------|
| orm.xml 内引用 | 所有 `entity` 的 `name`、`className`、`relations` 中的 `refEntityName` |
| Java 源文件目录 | `src/*/java/{old_pkg_path}/` → `src/*/java/{new_pkg_path}/` |
| Java package 声明 | `package {old_pkg}.` → `package {new_pkg}.` |
| Java import 语句 | `import {old_pkg}.` → `import {new_pkg}.` |
| `_gen/` 生成文件 | 旧包名的 `_gen/` 文件全部失效，需删除后通过 codegen 重新生成 |
| beans.xml / xmeta 等引用 | 若包含完整类名引用也需更新（通常 codegen 生成物会自动更新） |

### 迁移步骤

1. **修改 orm.xml**：更新 `ext:basePackageName`、`ext:entityPackageName` 及所有 entity 的 `name`/`className`/`refEntityName`。
2. **迁移 Java 目录**：将 `src/*/java/{old_path}/` 整棵目录树移动到 `src/*/java/{new_path}/`（例如 `nop/ai/` → `io/nop/ai/`）。涉及所有子模块（`*-dao`、`*-service`、`*-app`、`*-codegen`、`*-web` 等）。
3. **更新 package 和 import**：全局替换 `package {old_pkg}.` → `package {new_pkg}.` 和 `import {old_pkg}.` → `import {new_pkg}.`。排除 `_gen/` 和 `target/`。
4. **删除旧 `_gen/`**：删除 `*-dao/src/main/java/{old_path}/**/_gen/` 目录。
5. **触发 codegen 重新生成**：执行 `./mvnw clean install -T 1C`，codegen 会按新包名重新生成 `_gen/` 文件。
6. **验证**：`./mvnw test -pl {module} -am` 全部通过。

### 注意事项

- **不要手动编辑 `_gen/` 文件**：即使发现 `_gen/` 里的包名是旧的，也不要手动改——删除后让 codegen 重新生成。
- **`_app.orm.xml` 等 `_` 前缀文件**：这些是 codegen 生成物，会在步骤 5 中自动更新，不需要手动改。
- **beans.xml 中如果用了完整类名**（如 `class="nop.ai.dao.entity.Xxx"`），也需要同步更新。如果只用了 bean id 或短名，则不需要。
- **全平台标准包名**：`io.nop.{module}`（如 `io.nop.auth`、`io.nop.job`、`io.nop.wf`）。新建模块务必从第一次就使用标准包名，避免后续迁移。

## 常见误区

1. 先手写 Entity / DAO / Biz 接口，再回头补模型。
2. 把 `*-meta` 误解为直接生成全部 service / web 代码。
3. 只改生成物，不回到源模型。
4. 改了模型却没有触发上游生成链。
5. 只改 orm.xml 的包名，没有同步迁移 Java 源文件目录和 package 声明。
6. 手动编辑 `_gen/` 里的包名，而不是删除后让 codegen 重新生成。

## 相关文档

- `./orm-model-design.md` — ORM 模型设计规范（stdDataType/stdSqlType 分离、主键策略、字段设计、关系设计的概念知识归该文档所有）
- `../01-repo-map/domain-module-pattern.md`
- `../03-runbooks/create-new-entity.md`
- `../03-runbooks/add-field-and-validation.md`
- `../03-runbooks/change-model-and-regenerate.md`
- `../04-reference/source-anchors.md`
