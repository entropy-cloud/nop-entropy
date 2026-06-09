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

如果只想理解生成职责，可以按模块顺序看：

1. `{appName}-codegen` 刷新项目级生成产物。
2. `{appName}-dao` 接收 ORM、Entity、接口等结果。
3. `{appName}-meta` 生成 XMeta 与 i18n。
4. `{appName}-meta` 也会生成模块级 `module-meta.json`，供 web 层读取模块图标等元数据。
5. `{appName}-web` 基于 XMeta 与 `module-meta.json` 生成页面相关文件；如果 web 侧 i18n 依赖第一轮生成出的 `action-auth.xml`，则需要在 `precompile2` 中二次生成。
6. `{appName}-service` / `{appName}-app` 接收对应派生产物并参与构建。

## 真实链路

```text
model/{app}.orm.xml
  -> {app}-codegen/postcompile/gen-orm.xgen
  -> {app}-dao / {app}-service / {app}-meta / {app}-web
  -> {app}-meta/precompile/gen-meta.xgen
  -> {app}-meta/postcompile/gen-i18n.xgen
  -> {app}-web/precompile/gen-page.xgen
  -> {app}-web/precompile2/gen-i18n.xgen  (当 web i18n 依赖最终 action-auth.xml 时)
```

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

- `_gen/` 目录。
- `_app.orm.xml`。
- `_service.beans.xml`。
- `_*.xbiz`、`_*.view.xml`、`_*.java`。

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

当 ORM 模型的 column 满足以下任一条件时（由 `orm-gen.xlib` 的 `JsonComponentSupport` 在元编程阶段处理）：

1. `stdDomain="json"`
2. `tagSet` 包含 `"json"`

框架自动为该字段生成一个 `JsonOrmComponent`，命名为 `{fieldName}Component`。代码中可直接操作 JSON 对象：

```java
// 不需要手动序列化
entity.getExtDataComponent().set_jsonValue(Map.of("key", "value"));
// 读取时自动反序列化
Object data = entity.getExtDataComponent().get_jsonValue();
```

同时生成的 XMeta 中会自动设置 `graphql:jsonComponentProp`，GraphQL API 直接暴露结构化 JSON 而非原始字符串。

类似的自动组件还有：`stdDomain="xml"` → `XmlOrmComponent`，`stdDomain="file"` / `"file-list"` → `OrmFileComponent` / `OrmFileListComponent`。

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
