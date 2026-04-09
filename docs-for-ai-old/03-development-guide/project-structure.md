# Nop 项目结构与代码生成流程

本文档说明 Nop 平台中基于 ORM 模型的标准项目结构，以及与源码一致的代码生成流程。

如果你是 AI 助手，请先记住以下默认顺序：

1. 修改 `model/*.orm.xml`
2. 运行 `nop-cli gen` 生成初始工程骨架（仅首次）
3. 后续通过 `*-codegen` / `*-meta` / `*-web` 的构建流程再生成派生文件
4. 只在非生成文件中写定制逻辑

不要先手写 Entity、DAO、Biz 接口或 `_service.beans.xml`。

---

## 一、标准模块结构

典型业务模块通常包含：

```text
{appName}/
├── model/
│   └── {appName}.orm.xml
├── {appName}-codegen/
├── {appName}-dao/
├── {appName}-meta/
├── {appName}-service/
├── {appName}-web/
├── {appName}-app/
└── {appName}-api/
```

可选模块：

- `{appName}-delta/`
- `{appName}-core/`

这些模块并不是手工约定出来的目录，而是 `/nop/templates/orm` 模板可以直接生成的项目骨架。

---

## 二、各模块职责

| 模块 | 职责 | 典型产物 |
|------|------|---------|
| `model/` | 业务源模型 | `{appName}.orm.xml` |
| `*-codegen` | 项目脚手架生成入口 | `postcompile/gen-orm.xgen`、CodeGen 测试类 |
| `*-dao` | ORM、实体、Biz 接口、DAO 相关产物 | `app.orm.xml`、Entity、`I*Biz`、SQLLib |
| `*-meta` | 从 ORM 派生 XMeta 与 i18n | `precompile/gen-meta.xgen`、`postcompile/gen-i18n.xgen` |
| `*-service` | BizModel、xbiz、service beans | `XxxBizModel.java`、`_service.beans.xml`、`*.xbiz` |
| `*-web` | 基于 xmeta 生成页面文件 | `precompile/gen-page.xgen`、`*.view.xml`、`*.page.yaml` |
| `*-app` | 应用打包与启动 | 启动类、配置、Docker 相关文件 |
| `*-api` | 对外接口定义（可选） | API 契约 |

---

## 三、与源码一致的生成流程

### 1. 首次生成项目骨架

首次创建模块时，使用 `nop-cli gen` 基于 `/nop/templates/orm` 生成整个项目骨架：

```bash
nop-cli gen model/{appName}.orm.xml -t=/nop/templates/orm -o=.
```

`/nop/templates/orm` 模板目录会生成：

- `-codegen`
- `-dao`
- `-meta`
- `-service`
- `-web`
- `-app`
- `-api`

以及对应的脚本、beans、xbiz、页面生成入口等基础文件。

### 2. `*-codegen` 负责从源模型生成项目级产物

代表性脚本：`{appName}-codegen/postcompile/gen-orm.xgen`

它会以 `model/{appName}.orm.xml` 为输入，调用 `/nop/templates/orm`，并进一步生成：

- 项目骨架内各模块的基础文件
- `*-dao` 下的 ORM、Biz 接口、SQLLib 等
- `*-service` 下的 BizModel、xbiz、beans 等
- `*-web` 下的页面生成入口脚本

然后再调用 `/nop/templates/orm-entity` 生成实体代码到 `*-dao`。

### 3. `*-meta` 负责生成 XMeta 和 i18n

代表性脚本：

- `*-meta/precompile/gen-meta.xgen`
- `*-meta/postcompile/gen-i18n.xgen`

也就是说，`*-meta` 的职责是：

- 基于 ORM 生成 XMeta
- 生成 i18n 相关文件

**不是**通过 `gen-service.xgen` / `gen-web.xgen` 直接生成 service/web 模块代码。当前仓库中没有这类通用脚本模式。

### 4. `*-web` 再基于 XMeta 生成页面文件

代表性脚本：

- `*-web/precompile/gen-page.xgen`

它会调用 `/nop/templates/orm-web`，根据 xmeta 生成 `view.xml` / `page.yaml` 等页面文件。

因此，实际依赖链路应理解为：

```text
model/{appName}.orm.xml
  -> *-codegen/postcompile/gen-orm.xgen
  -> *-dao / *-service / *-web / *-meta 项目基础文件
  -> *-meta/precompile/gen-meta.xgen
  -> xmeta
  -> *-web/precompile/gen-page.xgen
  -> view/page 文件
```

---

## 四、最小闭环

### 1. 首次创建模块

```bash
nop-cli gen model/myapp.orm.xml -t=/nop/templates/orm -o=.
```

### 2. 修改 ORM 模型后重新生成

推荐直接在项目根目录执行：

```bash
mvn clean install
```

如果需要按模块顺序显式构建，可按下面的依赖顺序：

```bash
cd myapp-codegen && mvn install
cd ../myapp-dao && mvn install
cd ../myapp-meta && mvn install
cd ../myapp-web && mvn install
cd ../myapp-service && mvn install
cd ../myapp-app && mvn install
```

说明：

- `myapp-codegen`：刷新项目级生成产物
- `myapp-meta`：刷新 xmeta 与 i18n
- `myapp-web`：基于 xmeta 刷新页面产物

实际 Maven Reactor 构建时通常不必手工逐个执行，但 AI 需要理解这些生成职责是分层的。

---

## 五、哪些文件不能手改

以下内容默认视为生成物：

- `_gen/` 目录
- `_` 前缀文件
- `*_beans.xml` 中的生成部分
- `app.orm.xml` 对应的 `_app.orm.xml`
- `*_service.beans.xml` / `_service.beans.xml`
- `*.xbiz` 中以下划线开头的基类文件

典型做法是：

- 手写类继承 `_Xxx`
- 非下划线文件 `x:extends` 下划线文件
- 在 `_vfs/_delta/...` 中做 Delta 覆盖

---

## 六、AI 默认修改位置

| 任务 | 默认修改位置 |
|------|-------------|
| 新增表 / 字段 / dict | `model/*.orm.xml` |
| 扩展实体辅助方法 | `*-dao/src/main/java/.../Xxx.java` |
| 新增 BizModel 逻辑 | `*-service/src/main/java/.../XxxBizModel.java` |
| 扩展 xbiz | `*-service/src/main/resources/_vfs/.../Xxx.xbiz` |
| 扩展页面 | 非下划线 `view.xml` / Delta 文件 |
| 定制基础产品 | `_vfs/_delta/...` |

---

## 七、常见坑

1. 误以为 `*-meta` 直接通过 `gen-service.xgen` / `gen-web.xgen` 生成 service/web 代码。
2. 手改 `_gen/`、`_*.java`、`_*.xml`，导致下次构建被覆盖。
3. 跳过 `*-meta` 或 `*-web` 的生成步骤，结果 XMeta 或页面产物不是最新的。
4. 先手写 Entity/DAO/Biz 接口，而不是先改 ORM 模型。
5. 把项目结构文档当成“手工搭项目教程”，而忽略 `/nop/templates/orm` 才是标准骨架来源。

---

## 八、源码锚点

- `/nop/templates/orm`：模板目录中直接包含 `-dao`、`-service`、`-web`、`-meta`、`-app` 等模块骨架
- `*-codegen/postcompile/gen-orm.xgen`：从源模型驱动项目级生成
- `*-meta/precompile/gen-meta.xgen`：生成 XMeta
- `*-meta/postcompile/gen-i18n.xgen`：生成 i18n
- `*-web/precompile/gen-page.xgen`：基于 xmeta 生成页面文件

更多规则见：`../13-reference/source-anchors.md`

---

## 九、相关文档

- `../12-tasks/create-new-entity.md`
- `../12-tasks/add-field-and-validation.md`
- `../01-core-concepts/ai-development.md`
- `../01-core-concepts/delta-basics.md`
