# 模型优先开发

当前仓库的默认开发顺序不是“先写 Java”，而是：

**先模型，再生成，再补保留层代码。**

## 默认流程

1. 修改 `model/*.orm.xml`（**orm.xml 是代码生成的唯一源头**）。
2. 首次建模块时使用 `nop-cli gen` 生成骨架（一次性）。
3. 后续变更优先用 `./mvnw` 触发再生成与构建（日常主路径）。
4. 只在非生成文件中写定制逻辑。

> **从零创建外部应用项目**（不是已有项目内加实体）：完整步骤见 `../03-runbooks/bootstrap-new-application.md`，包含 nop-cli 获取、项目骨架、最小配置、启动验证。

## 两种 codegen 路径（不要混淆）

| 场景 | 命令/机制 | 触发频率 |
|------|----------|---------|
| 首次生成项目骨架（多模块结构） | `nop-cli gen` | **一次性** |
| 已有项目内改字段/加实体/改关系 | `./mvnw install`（自动跑 `postcompile/gen-orm.xgen`） | 日常每次 |
| 想在 Excel 里改模型 | `nop-cli convert` orm.xml→xlsx → 编辑 → convert 回 orm.xml → 再 `./mvnw install` | 按需 |
| IDE 里单步调试生成模板 | 跑 `*-codegen` 模块的 `XxxCodeGen.main()` | 调试 |

### 首次生成骨架（一次性）

```bash
nop-cli gen -t=/nop/templates/orm model/{appName}.orm.xml -o=.
```

`nop-cli` 是一个 uber-jar，来自 `nop-entropy/nop-runner/nop-cli/`。获取方式：

```bash
cd /path/to/nop-entropy
./mvnw install -pl nop-runner/nop-cli -am -DskipTests
# 产出 nop-runner/nop-cli/target/nop-cli-2.0.0-BETA.1.jar
```

这个命令用于生成标准业务骨架（codegen/api/dao/meta/service/web/app 七个子模块 + `postcompile/gen-orm.xgen` + `XxxCodeGen.java` 调试入口），**不是日常每次改模型都重跑一遍**。

### 后续模型迭代（日常主路径，不需要 nop-cli）

模型已经存在后，只需 `./mvnw clean install -T 1C`。Maven 的 `exec-maven-plugin`（parent pom 已配置）会自动执行每个 `*-codegen` 模块下的 `postcompile/gen-orm.xgen`，从 `model/*.orm.xml` 重新生成 entity/dao/xbiz 等到各子模块。

典型 `postcompile/gen-orm.xgen`：

```xml
<c:script>
codeGenerator.withTargetDir("../").renderModel('../../model/app-xxx.orm.xml','/nop/templates/orm', '/',$scope);
codeGenerator.withTargetDir("../xxx-dao/src/main/java").renderModel('../xxx-dao/src/main/resources/_vfs/.../app.orm.xml','/nop/templates/orm-entity','/',$scope);
</c:script>
```

完整两条路径对比、`nop-cli convert` 用法、`XxxCodeGen.main()` 调试入口见 `../03-runbooks/bootstrap-new-application.md` 的「两种 codegen 路径」章节。

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

### 文件级（gen-orm.xgen 内部：标准实体三步）

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

### 扩展已有实体：Delta 模式的生成管线

当你想在**已有实体（平台模块或其他模块的实体）基础上增加业务字段**时，标准的三步流水线不够用，需要引入 Delta 扩展模式。以 `nop-app-mall` 扩展平台 `NopAuthUser` 为例：

**源模型**（如 `nop-auth-delta.orm.xml`）：

```xml
<entity className="app.mall.delta.dao.entity.NopAuthUserEx"
        name="io.nop.auth.dao.entity.NopAuthUser"              <!-- 同名，运行时替换平台实体 -->
        registerShortName="true"                                  <!-- 用新类替换旧类的 ORM 注册 -->
        tableName="nop_auth_user"
        ext:baseClass="io.nop.auth.dao.entity.NopAuthUser">      <!-- 生成类继承平台实体 -->
    <columns>
        <!-- 继承自基类的字段：标记 notGenCode，不从基类重新生成 -->
        <column name="userId" notGenCode="true" propId="1" .../>
        <!-- 新增字段：propId 从 101 开始，避免与基类冲突 -->
        <column name="picUrl" propId="101" .../>
        <column name="lastLoginTime" propId="102" .../>
    </columns>
</entity>
```

**生成的继承链**：

```
NopAuthUser (平台模块实体)
    └── _NopAuthUserEx (生成，在 _gen/，extends NopAuthUser — ext:baseClass 决定)
            └── NopAuthUserEx (手写，@BizObjName("NopAuthUser"))
```

**四个关键属性**：

| 属性 | 作用 | 必须 |
|------|------|------|
| `ext:baseClass="..."` | 生成的 `_Xxx` 类继承此基类（替代默认的 `DynamicOrmEntity`） | ✅ |
| `registerShortName="true"` | 用新 `className` 替换原实体名的 ORM 注册，运行时所有引用 `NopAuthUser` 的地方拿到 `NopAuthUserEx` | ✅ |
| `name="原实体全名"` | 与平台实体同名，Delta 覆盖时替换原定义 | ✅ |
| 继承列加 `notGenCode="true"` | 跳过基类已有字段的生成，这些字段由基类提供 | 继承列都需要 |
| 新增列 `propId` 从 **101** 起 | 避免与基类的 propId 冲突 | 强烈建议 |

**Delta 模式在 `gen-orm.xgen` 中需要额外的两步**（共 4 步）：

```text
第 1 步：  model/app-mall.orm.xml
             -- 模板 /nop/templates/orm -->
           app-mall-dao 等标准产物

第 2 步：  app-mall-dao/.../app.orm.xml
             -- 模板 /nop/templates/orm-entity -->
           标准实体 Java 类

第 3 步：  model/nop-auth-delta.orm.xml                            ← 新增
             -- 模板 /nop/templates/orm-delta -->                   ← 新增
           app-mall-delta/.../default/_app.orm.xml                  ← 生成物
           app-mall-delta/.../_gen/_NopAuthUserEx.java              ← 生成物

第 4 步：  app-mall-delta/.../app.orm.xml
             -- 模板 /nop/templates/meta-delta -->                  ← 新增
           app-mall-delta/.../NopAuthUserEx.xmeta 等元数据           ← 生成物
```

**Delta 入口文件** `app-mall-delta/src/main/resources/_vfs/_delta/default/nop/auth/orm/app.orm.xml`：

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"
     x:extends="raw:/nop/auth/orm/app.orm.xml,default/_app.orm.xml">
    <entities/>
</orm>
```

- `raw:` 前缀绕过 Delta 解析，加载平台原始的 `app.orm.xml`
- `_app.orm.xml`（位于 `default/` 下）是第 3 步生成的 delta 模型

**Maven 依赖**（`app-mall-delta/pom.xml`）：

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-auth-dao</artifactId>      <!-- 编译时引用基类 NopAuthUser -->
</dependency>
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-auth-service</artifactId>   <!-- 运行时需要 -->
</dependency>
```

**codegen 模块**（`app-mall-codegen/pom.xml`）也需要 `nop-auth-dao` 依赖，否则 codegen 运行时无法解析 `refEntityName`。

**生成操作顺序**：

1. 在 `model/` 下创建 Delta ORM 模型文件（如 `nop-auth-delta.orm.xml`）
2. 在 `app-mall-codegen/postcompile/gen-orm.xgen` 中添加第 3、4 步的 `renderModel` 调用
3. 手写 Delta 入口 `app.orm.xml`（`_vfs/_delta/...` 下的非下划线文件）
4. 运行 codegen（`mvn exec:java -pl app-mall-codegen` 或直接运行 `AppMallCodeGen.main()`）
5. codegen 生成 `_NopAuthUserEx.java` 和 delta 模块下的 `_app.orm.xml`
6. 若 `NopAuthUserEx.java`（保留层）还不存在，手写一个继承 `_NopAuthUserEx` 的空类
7. `mvn install` 编译

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

### 凭证/敏感字段的多层收敛约定

含 apiKey/secret/password/token 语义的字段存在完整暴露链：**ORM 源模型 → 生成 xmeta → Delta xmeta → GraphQL DTO/API 响应**。限制必须写在 ORM 源模型（`model/*.orm.xml`），写在任何生成物（`_*.xmeta`、Delta 兜底）上的限制会在下次 codegen 时被覆盖或漂移。

**标准写法**（列上同时标注）：

```xml
<column name="apiKey" code="API_KEY" displayName="API密钥"
        tagSet="enc,not-query,not-sort,not-pub" ui:show="X"/>
```

各 tag 的含义与覆盖链路：

| 标注 | 作用层 | 效果 |
|------|--------|------|
| `enc` | ORM 列 | 列级 AES 加密（`DefaultOrmColumnBinderEnhancer`），DB 不存明文；`toString()` 等输出自动掩码 |
| `not-query` | 生成 xmeta | `queryable="false"`，不能作为查询条件 |
| `not-sort` | 生成 xmeta | `sortable="false"`，不能参与排序 |
| `not-pub` | 生成 xmeta | `published="false" internal="true"`，不进入 GraphQL schema 输出 |
| `ui:show="X"` | 生成 xmeta/view | 不在 UI 表单/列表中展示 |

链路验证：限制写在 ORM 源模型后，codegen 生成的 `_NopAiModel.xmeta` 自动继承；Delta xmeta 只需补充 `insertable="false" updatable="false"`（GraphQL 级写入限制）；DTO（OutputBean）生成时自动剔除凭证字段。**判定以运行时最终可见性为准**——GraphQL 响应/API DTO 实际输出的字段集合，而不是某层模型的属性标注。

标准回归测试：`nop-ai/nop-ai-meta/src/test/java/io/nop/ai/meta/TestNopAiModelApiKeyXmeta.java`（base xmeta 限制、merged xmeta 完全限制、非凭证字段不受影响三断言）。

> **历史教训（nop-ai MR4 §1）**：凭证字段曾被 3 个 MR 分头修复（xmeta 限制、Delta 兜底、DTO 剔除），但限制未下沉 ORM 源模型时生成的 xmeta 仍暴露——任何"只改生成物/Delta"的凭证收敛都按脆弱修复处理，必须回源。

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

## 直接 SQL 的使用边界（raw SQL vs EQL / 实体写）

业务代码访问数据库的默认顺序：**实体写（dao）→ EQL → QueryBean（列表查询）→ raw SQL（仅限豁免场景）**。
判定规则见下；完整保留/收敛清单与逐点裁定记录在 `ai-dev` 的调研文档（plan 2255 落档）。

### 关键事实：同一段 SQL 文本有两条执行路径

`SQL` 对象传给不同执行器语义不同：

| 执行器 | 语义 | 名称要求 |
|---|---|---|
| `dao.updateEntity / saveEntity` 等 | 实体写：dirty 列更新 + 乐观锁 version + 审计列自动维护 | 实体 |
| `orm().findAll/executeUpdate(SQL)` 等 | **EQL**（经 `EqlCompiler` 编译） | 实体全名/短名；物理下划线表名需 `SQL.begin().allowUnderscoreName()`（缺省 false，否则抛 `ERR_EQL_UNKNOWN_ENTITY_NAME`） |
| `jdbcTemplate.xxx(SQL)` | **原生 SQL**（直连物理表） | 物理表名/列名 |

**不要把物理表名 SQL 交给 `orm()` 执行**（除非显式 `allowUnderscoreName`）——编译期解析失败，且测试若用 jdbcTemplate 执行同一段 SQL 会掩盖该路径错配（测试必须与生产同路径）。

### raw SQL 的豁免场景（业务代码中允许直接 SQL 的仅有情形）

1. **单语句原子性是契约**：affected-row 裁决的条件更新/删除（一次性消费 `WHERE USED=0`、CAS `WHERE COUNT < ?`）、数据库端自增 `SET C = C + 1`。仓库规范样例：EQL 形式见 `SysDaoResourceLockManager`（实体名 + `session.executeUpdate` 返回 affected rows）；raw SQL 形式见 `DbMfaChallengeStore`（需绕过 ORM 会话缓存/独立事务时）。
2. **必须绕过 ORM 会话**：REQUIRES_NEW 独立事务中先行落库（业务异常回滚后仍需持久的计数）、必须读到并发提交新值的探测读（一级缓存会返回旧实体）。
3. **产品功能即执行 SQL**：用户定义的数据集查询（datav 面板取数）、DDL/迁移、无实体模型的基础设施表（选主租约、checkpoint）、`existsTable` 探测。

不满足上述情形的普通实体读写一律用实体写：绕过会话的 raw UPDATE 会造成会话实体与库脱同步（返回值命中一级缓存旧实例）、丢失乐观锁/审计列维护、物理列名硬编码。

### 事务与连接的并发契约（与独立事务/REQUIRES_NEW 相邻）

- **事务注册协议是原子的**：同一 context 内 get→建→注册事务收敛为注册表单步原子操作，REQUIRED 组语义在并行异步分支下不退化为独立事务；事务注销（`runWithoutTransaction`/`NOT_SUPPORTED`）以挂起标记实现、不留注销窗口；清理时若注册表身份比对不符（并发方已接管）降级告警而非误报「事务未注册」——提交成功后不应出现事务注册失败。组事务（subTransactions）提交/回滚对注册期集合持锁快照迭代，快照外的迟到子事务显式拒绝，不存在静默不提交丢写。同一 context 并行异步分支共享外层事务是设计场景，不是未定义行为。
- **JdbcTransaction 连接生命周期以事务对象自身为 monitor 串行化**：取连接/提交/回滚/释放/关闭五触点不并发交错，双取池连接泄漏与提交后并发重取僵尸连接不成立；已释放连接上的 commit 显式告警留痕（eagerRelease 后 null 连接为合法终态，但必须可观测）。禁止在事务 monitor 临界区内再获取注册表等全局并发结构锁。
- **队列消费不得横跨事务持有连接**：以 `IBlockingSource` 为载体的轮询消费（如 `drainTo` 等待路径）禁止在 `REQUIRES_NEW` 事务内做无限等待轮询——等待窗口必须在事务外，每轮「抢占 + 读取」以独立短事务包络，且抢占条件 UPDATE（CAS）与轮询读必须在同一短事务内（拆开即抢占失效）。空闲轮询期不应持有池连接。
- **抢占 CAS 谓词对 NULL 初始态显式化**：抢占字段按声明全集生成 WHERE 谓词——源值为 NULL 的字段生成 `col IS NULL` 而非被静默丢弃，NULL 初始态同样参与 CAS；任何路径下抢占 UPDATE 都不得退化为按 id 的无条件更新（否则同一任务会被多消费者重复投递）。

会话与事务并发契约的完整裁定（锁域、锁序、守卫指标与拒绝方案清单）见 AI 开发记录区 concurrency 设计集 orm-session-txn-atomicity-and-model-cache-generation.md（平台内部文档——按 docs-for-ai 边界规则不直接链接），本节为其使用知识投影，以该文档为权威。

### EQL 书写注意（易踩坑）

- **EQL 运算符优先级（2026-08-23 已修复，plan 2256/2257）**：历史缺陷两处——①算术：`sqlExpr_bit` 文法备选顺序曾与标准相反（`|` 最紧、`/` 接近最松），多个 `/` 夹 `+`/`-` 的表达式被静默重排（`A/B + C/D` 编译为 `(A/B+C)/D`）且 `+` 错误紧于 `-`；②逻辑：NOT 备选曾排在 AND/OR 之后（最松），`not a=1 and b='x'` 解析为 `not(a=1 and b='x')`。修复后为标准语义：算术六级左结合（`^` > `* / %` > `+ -` > `<< >>` > `&` > `|`，与 MySQL 一致）、逻辑 `NOT > AND > OR`。回归测试 `TestEqlArithmeticPrecedence` / `TestEqlLogicalPrecedence`（nop-orm）固化。**升级注意**：从旧版本迁移时，此前"歪打正着"依赖错误分组的无括号表达式（含 `not` 前缀无括号 where）语义会变化；混合算术加显式括号仍是推荐写法（无害且自文档化）。
- **投影别名与行映射（两种组合二选一）**：EQL 结果集字段名大小写敏感且别名原样保留。(a) `BeanRowMapper.of(clazz, true)`（camelCase 模式）时投影别名必须 snake_case——该模式先整体小写化 key（`as totalPromptTokens` 会变成 `totalprompttokens` 而 miss 属性）；(b) `BeanRowMapper.of(clazz, false)`（缺省模式，key 原样精确匹配）时 camelCase 别名可直接命中同名属性。无别名的裸列名字段名即属性名原样。
- 条件更新/删除可用 `SQL.begin().update(实体名).set()...where()...` + `orm().executeUpdate`，affected rows 原样返回；`update o set o.x = o.x + 1` 自引用算术与 `where ... is null` 条件守卫均支持（探针实证，记录见 ai-dev 调研文档）。结构化 EQL 管理优先用 `sql-lib.xml`（`eql` 类型 item 走同一编译链，debug 模式自动语法校验，经 mapper 接口调用）。

## detach 与 evict 的会话契约（R1.5 / P1-2.1-001）

`IOrmSession` 上有两个「移出会话」入口，缓存语义相同：

| 入口 | 关联处理 | 语义 |
|---|---|---|
| `evict(entity)` | 不级联 | 单实体从一级缓存移除 |
| `detach(entity, selection)` | 按 selection 级联 detach **已加载**关联实体（未初始化 proxy 关联保持原状、留在 session） | 主实体与级联实体均移出一级缓存 |

两者共同契约：移出后**不再参与 flush**（移出前的未提交修改与移出后的新修改均不落库）、`contains`/`get` 不再命中、已关闭 session 上调用响亮失败。需要「丢弃未提交修改并脱离会话」时用任一入口均可；需要保留未提交修改请先 flush 再 detach。

## ORM 会话并发与模型发布契约

同 context 的并发使用（并行异步分支、异步完成线程触达 session）是受支持的运行形态，会话与模型层提供以下保证；完整裁定权威见 AI 开发记录区 concurrency 设计集 orm-session-txn-atomicity-and-model-cache-generation.md（按 docs-for-ai 边界规则不直接链接），本节为使用知识投影。

### 会话生命周期并发契约

- **session 全生命周期互斥**：`OrmSessionImpl` 的 flush、close 与写路径以 session 级可重入锁串行化——并发 flush 不再静默跳过（静默跳过会让事务带着未刷写提交），双 flusher 并发遍历不可能；写入口（save/update/delete/lock 族）与读路径同样校验执行 context，跨线程误用 fail-fast。
- **异步会话收尾回 context 队列**：`runInNewSessionAsync` 等异步入口的收尾（flush/注册表恢复/close）经完成线程投递回 context 任务队列执行，完成线程只投递不直接操作 session；队列不可用时降级为投递线程内联执行，session 互斥兜底残留窗口。
- **会话兜底关闭**：异步入口注册新会话时在 context 生命周期挂兜底关闭登记；上游异步操作永不完成时，context 关闭会兜底关闭滞留会话，注册表不无限期滞留脏会话。
- **context 注册表 get-or-create 原子化**：`OrmSessionRegistry`/`TransactionRegistry` 的按 context 首触创建为单步原子操作，并发首触恰好一实例。`IContext.getAttrs()` 实现方须返回并发安全 Map——返回非并发 Map 的替身 context 上该原子性不成立。
- **flush 失败后可重试**：脏标记在批 DML 队列成功刷出之后才清除——flush 中途失败后捕获-重试编排再次 flush 会真实重放，不会因脏标记已清而静默 no-op。

### 模型热刷新可见性契约

- **模型发布 volatile 可见**：`DefaultOrmModelProvider`/`TenantAwareOrmModelProvider` 的 `ormModel` 与 `LoadedOrmModel` 的 `ormInterceptor` 为 volatile——`reloadModel`/`clearCache` 后新模型对新读者可见，不存在旧模型无限期可见；`clearCache` 先清 interceptor 缓存再发布新模型引用，混合态单向化（唯一残留窗为旧模型 + 新 interceptor 的极窄降级窗，非结构错位）。
- **组件列映射解析期预计算**：`OrmComponentModel` 的 `colPropIds`/`propIds` 在模型解析期一次性计算完成，运行期只读——并发首触不会读到空/部分填充的列映射，实体状态不会按错误 propId 错位写入。

### 全局实体缓存的世代围栏契约

全局实体/集合缓存（`useGlobalCache`）的 read-repair 回填带世代围栏：读库发起时快照世代，回填前经 fence CAS 相等性判定（readGen==fenceGen），evict 推进世代——**evict 之后任何迟到的 read-repair 回填一律放弃**，缓存中不会出现「缓存=旧值、DB=新值且不自愈」的无界驻留。世代语义（进程内 per-persister 单调 fence、版本化 value、非 DB 列/非时间戳）为单一权威设计，失效判据世代的后续演进须引用同一裁定，不得另建第二套世代语义。

### 事务状态机与批队列契约（2.4 切片束 b，plan 2553）

- **事务生命周期为 CAS 状态机**：`AbstractTransaction` 以 `AtomicReference<TxnState>`（NEW/OPEN/COMMITTING/COMMITTED/ROLLING_BACK/ROLLED_BACK/CLOSED）门控 open/commit/rollback/close——open 双开 fail-fast；**二次 commit = no-op-with-WARN**（监听器不二次通知）；`markRollbackOnly` 后 commit **自动走 rollback 语义 + WARN**（语义裁定：不再抛 `ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT`，依赖旧抛错行为的调用方须显式迁移）；`isTransactionOpened()` 为纯状态映射（仅 OPEN/COMMITTING 为 true）；commit 提交失败时终态尽力置 ROLLED_BACK 并透传异常。未 open（NEW）事务的 commit 保留既有可观测路径（`JdbcTransaction.doCommit` 对 null 连接 WARN 留痕 no-op）。
- **事务监听器注册原子**：`addListener` 惰性 init 与去重收进事务监视器域，`listeners` volatile——并发注册不丢监听器。
- **实体缓存 evict 事务级聚合**：同一事务内多次 evict 仅注册**一个聚合 listener**（`pendingEvictKeys` 并发集聚合 key，commit 后批量 removeAsync + 完成后自注销）——不存在每实体一监听器的 O(n²) 注册与 late-listener-lost 扩窗。
- **批动作队列 swap-then-capture**：`BatchActionQueueImpl.flushAsync` 先置新空容器再捕获旧快照（三捕获槽：collectionActions/delayTasks/actionMap）——flush 窗口内新 enqueue 不落入被丢弃容器；装载队列重入 flush 以 pending 标记由当前 flush 完成尾循环重放（不静默丢弃装载项）。
- **两阶段拓扑 flush 的同步完成契约**：`batchExecuteAsync` 返回的 future 在进入下一阶段前必须已完成，未完成即 `ERR_ORM_BATCH_FLUSH_ABORTED` fail-fast（带 entityName/phase）——异步持久化驱动不得假设阶段阻塞等待。
- **动态模型锁外加载**：`LazyLoadOrmModel` 的 DB+codegen 加载移出模型映射锁——并发同 key 共享单次加载；加载体内回环 `getEntityModel`（同 key 或跨 key 环）fail-fast（`circular dynamic entity load detected`），provider 实现禁止在加载体内同步回环。
- **模型换代延迟销毁**：`clearCache` 换代时旧 `LoadedOrmModel` 调 `close()`——零在途 session 即刻销毁、有在途 session 服务至末 session decRef 归零（`tryIncRefIfOpen` 拒新）。
- **findAll 全量物化行数上界（可选档）**：`nop.dao.find-all-max-rows`（缺省 0=不设上界）——`ISqlExecutor.findAll` 抽取器级迭代期超限抛 `ERR_DAO_FIND_ALL_ROW_LIMIT_EXCEEDED`；`findAllByQuery` 对 limit>0 转 findPage 语义（不再静默丢弃 limit）。大结果集应使用 findPage/findFirst。
- **查询缓存单条目行数上界（可选档）**：`nop.dao.query-cache-max-rows`（缺省 0=不设上界）——超限放弃缓存 + WARN；瞬态物化分配不设界（守卫约束持久缓存内存）。
- **flush 载体隔离可装配点**：`OrmTemplateImpl.setFlushExecutor`（缺省 null=调用线程现行语义）——注入后 `runInNewSessionAsync` finalize 任务投递注入执行器；作用域仅 finalize 路径，用户直调 `session.flush()` 不在 seam 内。
- **伪异步载体契约**：`JdbcEntityPersistDriver`/`JdbcCollectionPersistDriver`/`AbstractTransaction` 的 Async API 同步执行于调用线程，返回 future 仅作完成信号，调用方不得假设载体切换；`JdbcTransaction.getConnection` 懒取点可能落异步完成线程，池等待 >1s WARN 留痕。
- **驱动层批写 chunk 化（2.5 切片，plan 2554）**：tdengine 按「行数 × 子表声明 × 估算字节」三因子切分（`nop.orm.td.batch-max-rows`/`-bytes`），同子表 TAGS 分歧显式 WARN（首行 wins 行为保持）；rpc 按 `nop.orm.rpc.batch-size` 拆分并 block-join。**chunk 化后 `batchExecuteAsync` 返回 future 必须同步完成**——两阶段 flush 守卫对未完成 future fail-fast（2553 裁定 H 不变式）。
- **RPC 载体线程契约**：`RpcEntityPersistDriver` 的装配回调经调用方 context 出口泵投递（显式判空，无 context 内联=同步等待线程）——会话状态变更不落 RPC 传输线程。

## ORM 迁移编排并发契约

ORM 阶段迁移编排（增量链作业、阶段状态机、迁移历史表）在多节点/并发运维操作下提供以下保证；实现锚点见 `../04-reference/source-anchors.md` 的 `PUBSUB-CC-001` 与 `ORM-ORCH-001`。

### 阶段状态行 fencing 与编排单飞

- **状态行 UPDATE 带期望相位谓词**：`MigrationJobStateStore.update` 以 `WHERE job_id=? AND phase=?` 条件写（期望相位谓词，零 DDL 变更）；`updated==0` 时编排器二次 load 消歧——行缺失按「作业不存在」语义、行存在且相位≠期望按**跨进程 fencing 拒绝**（类型化异常携带期望/实际相位，不静默）；相位相等（理论不可达）时显式重试一次后仍失败即抛。abort×confirmSwitch 等并发编排恰好一写成功、另一写显式失败，终态背离窗口闭合。
- **编排 API per-jobId 进程内单飞**：runIncremental/runVerify/confirmSwitch/completeSwitch/abort（resume 同域）以 per-jobId 单飞域互斥，同 jobId 两入口并发时败者类型化拒绝（fail-fast，不排队等待——编排 API 为运维显式低频调用，排队掩盖误用）；跨 jobId 零互斥。跨节点同相位同窗口并发推进为显式接受的残余窗（谓词拒绝已把后果收敛为显式失败）。
- **惰性访问器同步化**：编排器内的惰性访问器（incrementalManager 等）以 double-check 收敛，并发首触不产生双实例。

### 增量链停止协议可验证性

- **链句柄发布先于线程启动**：链线程句柄构造期注入运行句柄，`join` 无 stale-null 空转窗；join 遇缺失句柄显式告警留痕，不静默跳过。
- **stop 五段式**：cancel → 有界 join → 超时 interrupt → 句柄保留至线程退出 → 退出确认复核（interrupt 后有界宽限再等，仍存活即置「未退出」标记，后续编排重入 gateFailed 类型化拒绝）——防半终止链与新链双写同目标表。
- **stopAll 汇聚**：逐链串行执行五段式 stop，全部 join 完成后才清空运行注册表；单链 join 上界由编排器 setter 注入（缺省常量，零新配置键）；总时间预算 = 单链上界 × 链数。

### 死链 fail 门禁

链死亡必须可见且必须 fail：runVerify 前置检查逐链消费链 failure（任一链 `getFailure()` 非空即 gateFailed 类型化拒绝，gate 标识 `incremental-chain-failed:*`）；停链后逐链残留 failure 复核留痕；进程内句柄消亡（manager==null）且状态行相位仍为 INCREMENTAL 时 runVerify 类型化拒绝重入（`incremental-manager-missing-restart-reentry`，要求显式 resume 或重入 runIncremental 重建链），仅无增量形态路径才显式留痕跳过。lag 观测量（冻结=追平语义）不得单独作为 VERIFY 放行依据——死链冻结位点必须经 failure 门禁拦截。

### fix-forward 受控自环

五状态机允许两条**白名单自环边**：`INCREMENTAL→INCREMENTAL`、`VERIFY→VERIFY`（SWITCH/DONE/ABORT 不增）。自环仅经既有编排 API 显式重入触发（不新增 API 面），带进程内重试计数上界（缺省 3，超限 gateFailed 拒绝——非全开、非无限重试）；自环写经期望相位谓词协同（期望相位=当前自环相位，abort 竞写将相位推离时自环写显式失败，不覆盖竞写终态）；自环重入前先回收旧链，旧链句柄未确认退出即拒绝重入。

### qs 身份键派生契约（SSOT）

connector 名、jobName、relocate 路由 querySpace 三类身份键的派生为**单点设计**：connector 名派生函数（base / base-i 全枚举）单点产出有序名清单，runVerify 按运行链快照逐链探测、M>1 全链 offsets 指纹聚合判 lag（任一链指纹或 ledger 前进即 lag≠0），不再以原始 base 名直传探测；jobName 纳入 querySpace 派生（多 querySpace 同名目标表不撞键）；relocate 场景路由 querySpace 从分片映射派生（单源取唯一目标，多源按 querySpace 映射派生，源 qs 不可得显式失败不猜测），FULL 侧 routeRow/routeShardValue 同型派生——增量链 filter 不因空串 querySpace 恒 false 丢事件。链注册表写入为 putIfAbsent fail-fast（同名重入类型化拒绝，不静默顶替旧句柄）；startFallbackChains 中途单链启动失败时回收已启动链（逐链五段式 stop）并显式相位后翻 ABORT，不滞留孤儿链。键派生语义演进须在本契约同一单点扩展，不得另建第二套派生。

### 迁移历史表跨进程互斥契约

- **DDL 守卫与版本落账互斥**：`ensureHistoryTableExists`（check-then-act DDL 段）与 `recordMigration`（按版本键域的 UPDATE→INSERT 复合写段）经跨进程互斥原语串行化——mysql `GET_LOCK` / postgresql `pg_advisory_lock`（连接级，同连接 finally 释放），H2 及未列方言回落**条件 INSERT 单行守卫表**（持锁=插入成功；finally 带 owner 比对 DELETE 本 owner 守卫行释放；进程崩溃残留守卫行须人工删除——H2 为测试方言可接受，生产方言连接断开自动释放无残留面）。获取失败类型化异常，不静默继续。
- **upsert 覆盖语义保留**：`recordMigration` 保留 UPDATE→0→INSERT 顺序，INSERT 条件捕获唯一约束冲突后回落 UPDATE 一次——并发下恰一 INSERT 成功、败者回落覆盖，终态单行；**「冲突即拒绝/insert-only」方向禁用**：重试成功必须替换已存在行（UPDATE 覆盖），写失败必须保留既有行。

本节编排并发契约的完整裁定（锁域/锁序总表、残余窗逐条理由、被拒方案清单、守卫指标名）见 AI 开发记录区 concurrency 设计集 pubsub-link-lifecycle-and-chain-orchestration-fencing.md（平台内部文档——按 docs-for-ai 边界规则不直接链接），本节为其使用知识投影，以该文档为权威。

## Redis PubSub 消息投递并发契约

Redis Pub/Sub 为 fire-and-forget 传输（无消费组、无持久化），lettuce 实现层提供以下投递与订阅生命周期保证；实现锚点见 `../04-reference/source-anchors.md` 的 `PUBSUB-CC-001`。

### 消费线程契约

- **消费回调禁止在 Redis IO/event-loop 线程内联执行**：IO 线程回调只允许把消息投递（入队）至模块内专用投递线程，消费回调（onMessage/onConsumeFailure）在投递线程执行——慢消费者队列头阻塞不会拖垮共享 Redis IO 线程导致服务端断链丢消息。投递 executor 为模块内私有单线程有界队列（保持与 Redis 单连接一致的 FIFO 投递序），队列满时经 `onConsumeFailure` 类型化回调留痕拒绝（禁静默丢弃）；`onConsumeFailure` 实现方契约 = 不阻塞、不发起 Redis 调用（仅日志/计数语义）。
- **lettuce IO/event-loop 线程硬条款（全路径）**：禁取服务监视器、禁发阻塞 Redis 命令、禁执行消费者回调；连接激活等事件回调只投递专用线程。
- **消费返回值契约**：onMessage 返回 `CompletionStage` 时挂有界完成观测（超时未完成 WARN 留痕，不取消不重试不阻塞投递线程——投递线程内同步等待即复活队头阻塞）；其他非 null 返回值在 fire-and-forget 传输无消费语义，warn-once 登记。**ack 语义不可承载**：需要可靠投递应选 nop-message 持久化通道，不应依赖 Redis Pub/Sub 返回值。

### 订阅生命周期并发契约

- **监视器串行**：subscribe/cancel/suspend/resume 与停机对账收敛于服务实例单锁域——订阅三步变更（移除 listener → 空判 → unsubscribe+移除注册）在锁内原子化，并发订阅管理不产生孤儿订阅或复活双投递；锁内阻塞 Redis 往返（subscribe/unsubscribe）显式接受队头阻塞代价，受命令超时预算约束。
- **属主校验**：一个订阅句柄只管理自己注册的 listener，不提供按 topic 取消他方订阅的路径；取消整个 topic = 逐订阅句柄 cancel（最后一个属主 cancel 触发锁内 unsubscribe）。cancel 后对同一句柄的重入为显式 no-op 留痕，resume 对已 cancel 恒拒绝。
- **停机对账**：服务 stop 时锁内遍历注册表逐 topic unsubscribe 并清空注册表后再关连接——停机后 Redis 侧订阅注册与进程内注册表一致；停机排空投递队列，残余未投递消息逐条经 onConsumeFailure 留痕后丢弃（禁静默消失）。
- **断线重连恢复可观测**：断线重连后的端点重订阅由 lettuce 自主完成（activated→resubscribe），服务不代做；服务侧职责为可观测——连接激活事件投递专用对账线程后，对注册表快照**锁外**发送幂等确认 SUBSCRIBE（已订阅通道再订阅仅确认语义），确认失败 WARN + 失败计数（`nop.lettuce.pubsub.resubscribe-failed`）。确认只订快照内条目——已 cancel 订阅结构性不复活；断线窗竞态可产生 Redis 侧幽灵通道（无 listener 零回调零双投递，连接 close 兜底），**接受不处置**，实施者不得代清。

### Redis 原子性与重启契约（2.5 切片，plan 2554）

- **写-TTL 原子化（无 TTL 永久键不可达）**：hash 三入口（putEx/putIfAbsentEx/getAndSetEx）与 session 入口的「写→PEXPIRE」两步链收敛为单 Lua 脚本原子执行——第二跳丢失或进程两步间死亡不再产生无 TTL 永久键（string 键 psetex/SET NX PX 原子兄弟面对齐）。
- **computeIfAbsent 单写者契约**：KV/hash computeIfAbsent 族以 SET NX/HSETNX 原子路径回写——并发加载下「仅有一个结果生效」（ICache 契约），败者回读已生效值，不再无条件 SET 覆盖。
- **停机即复位连接句柄**：PubSub 服务 stop 时锁内置空 link——stop→start 后首个 subscribe 走新建链路，不复用已关闭连接（消息面不因重启死亡）。

本节契约的完整裁定（锁序总表、残余窗登记、被拒方案）见 AI 开发记录区 concurrency 设计集 pubsub-link-lifecycle-and-chain-orchestration-fencing.md（平台内部文档——按 docs-for-ai 边界规则不直接链接），本节为其使用知识投影，以该文档为权威。

## ORM 模型运行期可见性与发布安全契约（plan 2552，R4-18）

并发审计 C1 束 a（15 工单）落定的模型运行期契约（实现锚点见 `../04-reference/source-anchors.md` `ORM-A`）：

- **LoadedOrmModel 生命周期**：close/decRef/incRef/tryIncRefIfOpen 四侧同监视器——close 后 `tryIncRefIfOpen` 返回 false（会话创建放弃，TOCTOU 闭合）。
- **工厂可见性**：`SessionFactoryImpl.interceptors` COW volatile；`daoListeners` 常驻 `MultiOrmDaoListener` 容器（读侧恒非 null，消费方 null 检查容忍空容器）。
- **静态槽**：`OrmTimestampHelper._instance`/`DaoProvider._instance` volatile（安全发布）。
- **批量装载队列**：`loadQueue`/`afterFlushCallbacks` volatile；捕获-清空同锁域，**任务体出锁执行**（afterFlush 的 task.run 与 invokeCallback 不持监视器）。
- **模型热刷新**：`reloadModel` 同步清除查询计划缓存（`clearQueryPlanCache`）——旧模型查询计划不跨世代驻留；细粒度计划级世代校验=watch-only。
- **EQL 解析器**：`EqlParseTreeParser.instance()` 为共享 xgen 模板生成物，**单线程契约面**（勿跨线程共享；仓内零生产调用方）。
- **CompiledSql 冻结**：`EqlCompiler.compile` 返回前 `freeze()`——19 setter 前置 `checkFrozen()`（frozen 后 `ERR_EQL_FROZEN`）；缓存发布面=冻结点。
- **C1-016 HB**：`OrmEntityModel.dependByOtherEntity` volatile（运行期 topo 重建写点 ↔ persist 读点）；`setDependByOtherEntity` **豁免冻结守卫**（sanctioned 运行期写点）。零弱化钉：`TestLazyLoadOrmModel:61`。
- **冻结守卫族**：`addProp` 守卫前置（先污染后守卫面消除）；mutator 族逐项 `checkAllowChange`。
- **LazyLoadOrmModel.putEntityModel**：短名冲突只读预检先于索引写入（无半注册残留）；同名重复注册覆盖 name 键=范围外。
- **init 原子化**：`OrmModel`/`OrmEntityModel.inited` volatile 守卫 + 同步块双检。
- **事务状态**：`AbstractTransaction.opened/rollbackOnly/error` volatile。
- **查询缓存世代围栏**（`JdbcTemplateImpl`）：每 cacheName 局部 AtomicLong epoch——clear/evict bump，miss 时点捕获，回填前比对（读期间失效→放弃回填，`nop.dao.query-cache-stale-miss-abandon`）。

测试锚点：`TestLoadedOrmModelCloseRace`/`TestOrmSliceAVisibility`（nop-orm）、`TestOrmEqlSliceA`（nop-orm-eql）、`TestOrmModelSliceA`（nop-orm-model）、`TestDaoSliceA`（nop-dao）。

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
