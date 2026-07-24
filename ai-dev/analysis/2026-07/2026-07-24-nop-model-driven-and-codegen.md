# nop-entropy 模型驱动开发、代码生成与 Delta 定制

> Status: resolved
> Date: 2026-07-24
> Scope: 各业务模块 `model/*.orm.xml`（model-first 起点）、`nop-kernel/nop-codegen`（生成引擎与模板）、各 `*-codegen`/`*-meta`/`*-web` 模块（分层生成执行）、根 `pom.xml`（Maven phase 绑定）、`nop-kernel/nop-core` + `nop-xlang`（Delta 合并与 value resolver）；从 ORM 源模型到生成物到 Delta 定制的端到端链路 + 5 方向联网对标代码生成生态
> Conclusion: nop-entropy 的开发范式是「**唯一手编辑入口是 `model/*.orm.xml`，全链路产物由 codegen 在 Maven build 期持续再生，`_` 前缀产物不可手改，定制一律走 Delta 叠加**」。其差异化定位在于"**生成即一等公民 + 持续再生（非一次性）+ Delta 叠加可升级 + 生成层与保留层（`_` 与非 `_`）文件级分离**"——使它同时区别于 JHipster/Spring Initializr（一次性脚手架）、OpenAPI Generator（单维度 spec→stub）、Annotation Processor（编译期局部增强）、MPS（projectional 语言工作台）。
> Mission: nop-deep-analysis（Work Item A3）
> Superseded By: （本分析为 A4 服务层如何消费生成产物、A5 模块矩阵如何基于生成链路组装提供模型驱动参照；若 A7 capstone 重新组织模型驱动章节，则被替代）

## Context

- **要回答的问题**：「先模型、再 Delta、最后 Java」的开发范式在 nop-entropy 中如何落地？从 `model/*.orm.xml` 到生成物（`_gen/`、`_*.java`、`_*.xml`、`_*.xmeta`）再到 Delta 定制的完整链路是怎样的？每个生成阶段的输入模型、输出产物、Maven 触发时机是什么？Delta 如何在不修改生成物的前提下实现业务定制？与主流代码生成生态（JHipster / OpenAPI Generator / Spring Initializr / Annotation Processor / MPS）的差异化定位在哪？
- **涉及模块/子系统**：`nop-kernel/nop-codegen`（生成引擎、模板）、各业务模块的 `*-codegen`/`*-meta`/`*-web` 子模块、根 `pom.xml` 的 `exec-maven-plugin` 配置、`nop-kernel/nop-core`（VFS/Delta 资源）、`nop-kernel/nop-xlang`（`XDslExtender`、value resolver）。
- **约束**：仅模型驱动与代码生成链路梳理——不剖析核心引擎内部逐行实现（A2）、不涉及 GraphQL CRUD 自动暴露（A4）、不涉及具体业务实体字段设计（数据库设计规范由 `nop-database-design` skill 承担）、不产出代码变更（仅分析文档）。
- **来源基线**：`docs-for-ai/02-core-guides/model-first-development.md`、`docs-for-ai/02-core-guides/delta-customization.md`、`docs-for-ai/04-reference/source-anchors.md`（GEN-001~009 / EXT-002~005 / RESOLVE-001~002）。本分析通过对 **15+ 个 source-anchors 锚点**做源码交叉核对（全部 PASS）建立，并综合引用 A1（`ai-dev/analysis/2026-07/2026-07-24-nop-theory-foundation.md`）、A2（`.../2026-07-24-nop-core-engine-deep-dive.md`）已建立的词汇表。

## 1. Model-First 开发范式：ORM 模型即项目骨架

nop-entropy 的默认开发顺序不是「先写 Java」，而是 **先模型，再生成，再补保留层代码**（`docs-for-ai/02-core-guides/model-first-development.md:3-6`）。

### 1.1 唯一手编辑入口：`model/*.orm.xml`

每个业务模块的源头是 `model/{appName}.orm.xml`。以 `nop-job` 为例，源模型 `nop-job/model/nop-job.orm.xml` 是表、字段、关系、字典的唯一权威源。判断一个 ORM 文件是否为源，**只看路径**：在 `model/` 目录下的是源（可改）；在 `_vfs/.../orm/` 下且以 `_` 开头的是生成物（不可改）（`model-first-development.md:197,325`）。

### 1.2 首次生成骨架：`nop-cli gen`

首次建模块时使用 `nop-cli gen` 生成标准业务骨架（GEN-001，source-anchors）：

```bash
nop-cli gen model/{appName}.orm.xml -t=/nop/templates/orm -o=.
```

模板 `/nop/templates/orm`（位于 `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm/`）是项目骨架和多模块生成的起点。它的 `@init.xrun` 入口为 `<gen:DefineLoopForOrm xpl:lib="/nop/codegen/xlib/gen.xlib"/>`（`orm/@init.xrun:1`），从 ORM 模型的 `ext:*` 属性提取 `appName`/`moduleId`/`basePackageName`（`gen.xlib:116-144`），调用 `ResourceHelper.checkValidModuleName(appName)`（`gen.xlib:131`）校验模块名后，一次性生成整个多模块骨架：

- `{appName}-dao`/`-service`/`-web`/`-app`/`-meta`/`-api`/`-codegen` 的 `pom.xml`
- DAO 层 `_dao.beans.xml` + `app.orm.xml`（保留层）+ `_app.orm.xml`（生成物）
- service 层 `_service.beans.xml` + `_*.xbiz`
- app 层 `app.action-auth.xml` + `app.data-auth.xml`
- 部署 SQL `_create_*.sql`
- 各子模块的 `gen-orm.xgen`/`gen-meta.xgen`/`gen-i18n.xgen` 骨架

### 1.3 后续变更：`./mvnw` 触发再生

首次骨架生成后，**日常每次改模型不再重跑 `nop-cli gen`**，而是通过 `./mvnw clean install -T 1C` 触发 codegen 再生与构建（`model-first-development.md:23-28`）。codegen 任务绑定 Maven phase（见 §3），在每次 build 时自动扫描各模块的 `precompile/`/`precompile2/`/`postcompile/` 目录下的 `.xgen` 文件并执行。

> **关键设计**：生成是**持续的、可重复的**，而非一次性。这是 nop 区别于 JHipster / Spring Initializr（一次性脚手架）的核心差异（见 §6）。

## 2. 分层生成链：codegen / meta / web 三层职责

一个业务模块的生成职责被拆分到 4 类子模块，各自绑定不同 Maven phase、读取不同输入、产出不同产物（GEN-002~007，source-anchors）。

### 2.1 模块级生成链路（谁生成谁）

以 `nop-job` 为例（其他业务模块同理，`model-first-development.md:158-168`）：

```text
model/{app}.orm.xml
  -> {app}-codegen/postcompile/gen-orm.xgen            [GEN-002]
  -> {app}-dao / {app}-service / {app}-meta / {app}-web 的基础产物
  -> {app}-meta/precompile/gen-meta.xgen               [GEN-003]
  -> XMeta
  -> {app}-meta/postcompile/gen-i18n.xgen              [GEN-004]
  -> i18n
  -> {app}-web/precompile/gen-page.xgen                [GEN-005]
  -> view/page 文件
```

### 2.2 各层职责与输入/输出

| 层 | 执行模块 | `.xgen` 位置 | 输入模型 | 模板 | 输出产物 |
|---|---|---|---|---|---|
| 项目级生成 | `*-codegen` | `postcompile/gen-orm.xgen` | `model/{app}.orm.xml` + 合并后的 `app.orm.xml` | `/nop/templates/orm`、`/nop/templates/orm-entity`、`/nop/templates/orm-model` | 多模块骨架、`_app.orm.xml`、`_gen/_Nop*.java`、`_service.beans.xml`、`_*.xbiz`、`app.action-auth.xml` |
| 元数据生成 | `*-meta` | `precompile/gen-meta.xgen` | `/nop/{module}/orm/app.orm.xml`（VFS 路径） | `/nop/templates/meta` | `_*.xmeta`、`{shortName}.xmeta`、`_module-meta.json`、`module-meta.json` |
| 国际化生成 | `*-meta` | `postcompile/gen-i18n.xgen` | xmeta 产物 | `/nop/templates/i18n` | `_i18n/{locale}/_{moduleName}.i18n.yaml`、`{moduleName}.i18n.yaml` |
| 页面生成 | `*-web` | `precompile/gen-page.xgen` | xmeta 产物 + `module-meta.json` | `/nop/templates/orm-web` | `*.view.xml`、`*.page.yaml`、`_*.action-auth.xml` |

**`gen-orm.xgen` 内部三次 `renderModel`**（`nop-job/nop-job-codegen/postcompile/gen-orm.xgen:4-8`，GEN-002）：

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

**`gen-meta.xgen`**（`nop-job/nop-job-meta/precompile/gen-meta.xgen:3`，GEN-003）从 VFS 路径 `/nop/job/orm/app.orm.xml` 读取合并后的 ORM，用 `/nop/templates/meta` 生成每个实体的 `_*.xmeta` 及模块级 `_module-meta.json`。

**`gen-page.xgen`**（`nop-job/nop-job-web/precompile/gen-page.xgen:4`，GEN-005/007）通过 `codeGenerator.withTplDir('/nop/templates/orm-web').execute("/",{ moduleId: "nop/job" },$scope)` 驱动。`orm-web` 的 `@init.xrun`（`orm-web/@init.xrun:3-30`）通过 `loadDeltaJson("/{moduleId}/model/module-meta.json")` 读取模块级 meta（GEN-006/007），并通过 `VirtualFileSystem.instance().getAllResources(...)` 收集所有 `.xmeta`（过滤 `_` 前缀和 `no-web`/`not-pub` tag）后生成页面。

### 2.3 模块级 meta：web 层的稳定边界

`*-meta` 生成 `_module-meta.json`（生成物）+ `module-meta.json`（保留层，`x:extends:"_module-meta.json"`），为 web 层暴露稳定的模块元数据边界（GEN-006）。`_module-meta.json.xgen`（`nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/meta/src/main/resources/_vfs/{moduleId}/model/_module-meta.json.xgen:5-13`）产出 4 个字段：

```javascript
const moduleMeta = {
    moduleId: moduleId,
    moduleName: moduleName,
    appName: appName,
    icon: ormModel['ext:icon']
};
```

保留层 `module-meta.json.xgen` 仅一行（`module-meta.json.xgen:1-5`）：`{ "x:extends": "_module-meta.json" }`。这是「保留层 `x:extends` 生成层」文件级分离的范式案例。

> **设计意图**（`model-first-development.md:81-82`）：让 `*-web` 只依赖 `*-meta` 输出，不要求 `*-web` 直接访问 `*-dao` 下的 ORM 产物。模块级 meta 是 web 层与 dao 层之间的稳定接口。

## 3. Maven Phase 绑定与触发时序

codegen 任务通过根 `pom.xml` 的 `exec-maven-plugin`（`pluginManagement` 段）绑定到 Maven 生命周期（GEN-009，`pom.xml:323-412`）。所有模块共享这套配置。

### 3.1 四个 execution 绑定

| execution id | Maven phase | 传入参数 | `addResourcesToClasspath` | `addOutputToClasspath` | 说明 |
|---|---|---|---|---|---|
| `precompile` | `generate-sources` | `precompile` | `false` | `false` | 第一轮生成，**不含**当前项目资源和已编译类（避免加载尚未编译的 `ICoreInitializer`，`pom.xml:340-343`） |
| `precompile2` | `generate-sources` | `precompile2` | `true` | `true` | 第二轮生成，**含**当前项目资源和已编译类 |
| `aop` | `compile` | `aop` | —（`classpathScope=test`） | — | AOP 代理类生成（源码生成式 AOP，见 A2 分析） |
| `postcompile` | `generate-test-resources` | `postcompile` | `true` | `true` | 编译后生成，含资源和类 |

入口 mainClass 为 `io.nop.codegen.task.CodeGenTask`（`pom.xml:401`），插件依赖 `nop-codegen`（`pom.xml:405-411`）。

### 3.2 CodeGenTask 如何发现 `.xgen` 文件

`CodeGenTask.main(String[] args)`（`nop-kernel/nop-codegen/src/main/java/io/nop/codegen/task/CodeGenTask.java:139`）的关键逻辑：

- **第二个参数即目录名**：`args[1]`（`precompile`/`precompile2`/`postcompile`）被**原样当作模块下的子目录名**，不是固定枚举（`CodeGenTask.java:169-173`）：

  ```java
  String tplRoot = "precompile";
  if (args.length > 1) {
      tplRoot = args[1];
  }
  File tplRootPath = new File(projectPath, tplRoot);
  ```

- **空目录则跳过**（`CodeGenTask.java:174-178`）：若 `{project.basedir}/{tplRoot}/` 不存在或为空，打印 `nop.skip-codegen-when-tpl-dir-is-empty` 并返回。这就是为什么不是每个模块都需要 4 个目录——只有放了对 应 `.xgen` 的目录才会执行。
- **`aop` 是唯一硬编码特例**（`CodeGenTask.java:164-168`）：走 `genAopProxy(projectPath)` 单独路径。
- **classpath 可见性由参数名前缀控制**（`CodeGenTask.java:191-197`）：`precompile`（精确匹配）设 `CFG_INCLUDE_CURRENT_PROJECT_RESOURCES=false`；任何 `precompile*` 前缀（含 `precompile2`）设为 `true`。这与 `pom.xml` 中 `addResourcesToClasspath`/`addOutputToClasspath` 的差异呼应——`precompile` 在资源和类尚未就绪时运行，`precompile2`/`postcompile` 在已就绪后运行。

### 3.3 目录扫描与 `@init.xrun`

目录遍历由父类 `TemplateFileGenerator`（`nop-kernel/nop-core/src/main/java/io/nop/core/resource/tpl/TemplateFileGenerator.java`）负责：

- `executeWithLoop`（`TemplateFileGenerator.java:185-192`）：`tplPath` 为 `""` 或 `"/"` 时递归处理整个 `tplRoot`。
- `processDir`（`TemplateFileGenerator.java:233-240`）：遍历子资源递归处理。
- **`@` 前缀文件被忽略**（`TemplateFileGenerator.java:242-244`）：`@init.xrun` 不参与普通遍历，而是由 `XCodeGenerator.runInit`（`nop-kernel/nop-codegen/src/main/java/io/nop/codegen/XCodeGenerator.java:226-236`）专门加载并 `xpl.invoke(scope)` 执行（`INIT_FILE_NAME = "@init.xrun"`，`CodeGenConstants.java:31`）。

### 3.4 触发时序总览

`./mvnw clean install -T 1C` 的 Maven 生命周期中，codegen 的执行时序（同一模块内按 phase 顺序，跨模块按 reactor 依赖拓扑）：

```text
generate-sources (precompile + precompile2)
  -> {app}-meta/precompile/gen-meta.xgen     生成 _*.xmeta + _module-meta.json
  -> {app}-web/precompile/gen-page.xgen       生成 *.view.xml / *.page.yaml
compile
  -> aop                                      生成 __aop 代理子类
generate-test-resources (postcompile)
  -> {app}-codegen/postcompile/gen-orm.xgen   生成 _app.orm.xml + _gen/_Nop*.java
  -> {app}-meta/postcompile/gen-i18n.xgen     生成 _*.i18n.yaml
```

> **注意 classpath 可见性**：`precompile` 在 `generate-sources` 早期运行，此时当前模块的资源（`src/main/resources`）和编译产物（`target/classes`）**尚未就绪**——它只能看到上游依赖模块的产物。`precompile2` 和 `postcompile` 才能看到当前模块资源与类。这决定了哪些生成步骤能放在哪个 phase：依赖上游产物的放 `precompile`，依赖当前模块资源的放 `precompile2`/`postcompile`。

## 4. 生成物约束：`_` 前缀不可手改

### 4.1 `_` 前缀的强制覆盖语义

`_` 前缀是 codegen 产物的硬约定，对应「总是覆盖」的写入规则。常量定义于 `nop-kernel/nop-core/src/main/java/io/nop/core/CoreConstants.java:146-155`：

```java
String XGEN_FILE_PREFIX = "_";              // _-prefix => always overwrite
String XGEN_FILE_DIR = "/_gen/";            // _gen/ dir => always overwrite
String XGEN_MARK_FORCE_OVERRIDE = "__XGEN_FORCE_OVERRIDE__";
String XGEN_MARK_TPL_FORCE_OVERRIDE = "__XGEN_TPL_FORCE_OVERRIDE__";
```

覆盖判定在 `TemplateFileGenerator.isAllowWrite`（`TemplateFileGenerator.java:483-520`）：`_` 前缀文件名（`492-494`）、`_gen/` 路径（`496-497`）、或头部带 `__XGEN_FORCE_OVERRIDE__`/`__XGEN_TPL_FORCE_OVERRIDE__` 标记（`504-516`）均允许覆盖。**手改这些文件会在下次 `mvn install` 时被静默覆盖**（AGENTS.md Hard Stop 规则）。

### 4.2 生成物清单与产出层

| 生成物 | 产出模板 / 步骤 | source-anchor |
|---|---|---|
| `_gen/_Nop*.java`（实体类、PkBuilder） | `/nop/templates/orm-entity/{...}/_gen/_{shortName}.java.xgen` | GEN-001/002 |
| `_app.orm.xml`（聚合 ORM） | `/nop/templates/orm/{appName}-dao/.../orm/{deltaDir}/_app.orm.xml.xgen` | GEN-001 |
| `_*.xmeta`（实体元数据） | `/nop/templates/meta/.../model/{shortName}/{deltaDir}/_{shortName}.xmeta.xgen` | GEN-003 |
| `_module-meta.json`（模块级 meta） | `/nop/templates/meta/.../model/_module-meta.json.xgen` | GEN-006 |
| `_*.action-auth.xml` / `_*.data-auth.xml` | `/nop/templates/orm/{appName}-app/.../auth/app.action-auth.xml.xgen` | GEN-007 |
| `_service.beans.xml` | `/nop/templates/orm/{appName}-service/.../beans/_service.beans.xml.xgen` | GEN-002 |
| `_*.xbiz` | `/nop/templates/orm/{appName}-service/.../model/{shortName}/_{shortName}.xbiz.xgen` | GEN-002 |
| `_*.i18n.yaml` | `/nop/templates/i18n/src/main/resources/_vfs/i18n/{locale}/_{moduleName}.i18n.yaml.xgen` | GEN-004 |

### 4.3 生成层与保留层的文件级分离

nop 的 codegen 产物与用户可编辑代码通过**文件级命名约定**分离，而非同一文件内的生成区/手写区（如 `// GENERATED BELOW`）：

- **生成层**：`_` 前缀文件（`_app.orm.xml`、`_gen/_Nop*.java`、`_*.xmeta`），每次 build 全量再生。
- **保留层**：同目录下非 `_` 前缀文件（`app.orm.xml`、`Nop*.java`、`{shortName}.xmeta`），通过 `x:extends="_xxx"` 继承生成层，承载手写 delta。

典型范式（`module-meta.json.xgen` → `module-meta.json`）：生成层 `_module-meta.json.xgen` 产出 `_module-meta.json`；保留层 `module-meta.json` 内容仅 `{ "x:extends": "_module-meta.json" }`，用户在保留层追加定制。实体类同理：`_gen/_NopAuthUser.java`（生成）+ `NopAuthUser.java`（手写，`extends _NopAuthUser`）。

> **为什么不用部分类（partial class）/ 注解处理器**：文件级分离使生成与手写完全解耦——生成可全量覆盖而不触碰手写代码，手写代码可独立版本化、独立审计。这与 Annotation Processor（在同编译单元内增强）和 partial class（合并同文件）的策略不同。

## 5. Delta 定制机制：在不修改生成物的前提下实现业务定制

当目标是「在不破坏升级路径的前提下定制现有产品或生成结果」时，默认走 Delta（`docs-for-ai/02-core-guides/delta-customization.md:1-4`）。Delta 机制的理论基础见 A1 分析（`2026-07-24-nop-theory-foundation.md` §1-2），本节聚焦其在模型驱动/代码生成链路中的工程用法。

### 5.1 三阶段 extends 与 8 种 override

XDSL 提供三阶段编译期扩展（`XDslExtender.java`，EXT-002；A1 分析 §2.2）：

| 属性 | 角色 | 对应 GRC |
|---|---|---|
| `x:extends` | 继承已有 DSL 文件，两模型在 Tree 上分层合并 | Delta 叠加 |
| `x:gen-extends` | 用 Xpl 动态生成多个 Tree 节点再逐一合并 | Generator `F(X)` |
| `x:post-extends` | 同 Xpl 机制，但在已合并结果上后处理 | 后置差量 |

**完整合并顺序**：`F -> E -> Model -> D -> C -> B -> A`（A 最深基；post-extends 最后应用）。合并执行在 `XDslExtender.extendNode`（`XDslExtender.java:198-249`），线性化 extends 列表（`buildSource`，`264-311`）后**由内向外逐层 fold**（`229-237`）。`super` 关键字在 `XDslExtender.java:282-293` 被重写为 `super:{currentPath}` 名字空间。

**8 种 `x:override` 合并模式**（`XDefOverride.java:19-50`，默认 `merge`，`xdsl.xdef:70`）：

| 模式 | 行 | 值 | 语义 |
|---|---|---|---|
| REMOVE | 22 | `remove` | 删除基类中的节点（逆元） |
| REPLACE | 26 | `replace` | 完全覆盖原有节点 |
| PREPEND | 30 | `prepend` | 合并属性，前插子节点 |
| APPEND | 34 | `append` | 合并属性，后插子节点 |
| MERGE | 38 | `merge` | 合并属性，按标签名合并子节点（**默认**） |
| MERGE_REPLACE | 42 | `merge-replace` | 合并属性，覆盖子节点 |
| BOUNDED_MERGE | 46 | `bounded-merge` | 只保留派生节点中定义过的子节点 |
| MERGE_SUPER | 50 | `merge-super` | 合并属性，嵌入 super |

### 5.2 `super:` 与分层资源（Delta 层）

Delta 文件位于 `src/main/resources/_vfs/_delta/{deltaDir}/...`（`delta-customization.md:14-25`）。`x:extends="super"` 表示继承**上一 Delta 层**的模型；若 delta 文件**不设** `x:extends`，则**覆盖**（overwrite）而非合并（A1 分析 §2.2）。

资源层解析在 `DeltaResourceStore`（EXT-003/VFS-001）：

- **层解析顺序**（`DeltaResourceStore.java:120-149`）：tenant 层优先（`122-133`）→ 各 `deltaLayerIds` 自顶向下（`135-145`）→ base（`147-148`）。类文档（`DeltaResourceStore.java:35-42`）示例：layerIds=`app,product,platform` → 依次尝试 `/_delta/app/...`、`/_delta/product/...`、`/_delta/platform/...`、base。
- **`getSuperResource`**（`DeltaResourceStore.java:251-294`）：定位调用方所在 delta 层索引（`256-270`），从 `deltaIndex+1` **向下**搜索（`282-290`）：

  ```java
  for (int i = deltaIndex + 1, n = deltaLayerIds.size(); i < n; i++) {
      String deltaLayerId = deltaLayerIds.get(i);
      String fullPath = ResourceHelper.buildDeltaPath(deltaLayerId, path);
      IResource resource = store.getResource(fullPath);
      if (resource.exists()) return resource;
  }
  ```

### 5.3 真实模块案例（≥3 个）

**案例 A — `nop-demo/nop-quarkus-demo`（ORM + xmeta + page 三层定制）**：

- `nop-demo/nop-quarkus-demo/src/main/resources/_vfs/_delta/default/nop/sys/orm/app.orm.xml:1`：`x:extends="super"`，为 `NopSysNoticeTemplate` 实体新增 `extFields.fldA.string`/`fldB.int` 别名字段。
- `.../_delta/default/nop/sys/model/NopSysNoticeTemplate/NopSysNoticeTemplate.xmeta:1`：`x:extends="super"`，新增两个扩展 prop `extFldA`（String/email domain）和 `extFldB`（Integer, mandatory）。
- `.../_delta/default/nop/sys/pages/NopSysNoticeTemplate/NopSysNoticeTemplate.view.xml`：页面视图定制。
- **效果**：在不修改 `nop-sys` 平台源码的前提下，为平台实体扩展字段、元数据与页面。

**案例 B — `nop-demo/nop-delta-demo`（beans + xbiz + xlib 三种 Delta 模式）**：

- `.../_delta/default/nop/auth/beans/auth-service.beans.xml:8`：`x:extends="super"`，新增自定义 bean `LoginApiBizModelDelta`。
- `.../_delta/default/nop/auth/model/LoginApi/LoginApi.xbiz:5-13`：新增 biz action `myMethod(msg)` 返回 `"hello:" + msg`（**无** `x:extends`，整体追加/覆盖语义）。
- `.../_delta/default/nop/core/xlib/meta-gen.xlib:1`：`x:extends="super"` 覆盖 `DefaultMetaGenExtends` 标签，注入 `DisablePropSortable`（使所有 prop `sortable=false`）。
- **效果**：演示了 Delta 可定制 IoC bean、业务动作、甚至**编译期标签库**——这是 nop Delta 区别于普通配置覆盖的强能力。

**案例 C — `nop-job/nop-job-worker`（beans 定制）**：

- `nop-job/nop-job-worker/src/main/resources/_vfs/_delta/default/nop/job/beans/app-engine.beans.xml:6`：`x:extends="super"`，注册 5 个 worker engine bean（`IJobInvokerResolver`、`IJobExecutionContextBuilder`、`IJobWorkerScanner`、`JobWorker`、`IWorkerCapacityProvider`）。
- **效果**：worker 模块通过 Delta 替换默认 job 引擎实现，无需修改 `nop-job-core`。

> **测试用例佐证**（`delta-customization.md` Exit Criteria 要求 ≥2 个真实案例）：以上 A/B/C 均为 `src/main/resources` 生产代码。另有测试 delta 如 `nop-auth/nop-auth-service/src/test/resources/_vfs/_delta/default/nop/auth/orm/app.orm.xml:2`（`x:extends="super"`，为 `NopAuthGroup` 加 `shardProp`/`useShard`）佐证机制一致性。

### 5.4 Delta 扩展已有实体：4 步生成管线

当需要在已有平台实体上增加业务字段时，标准三步流水线扩展为 4 步（`model-first-development.md:199-296`）。以 `nop-app-mall` 扩展平台 `NopAuthUser` 为例：

```text
第 1 步：  model/app-mall.orm.xml  -- /nop/templates/orm -->            标准产物
第 2 步：  app.orm.xml             -- /nop/templates/orm-entity -->       标准实体类
第 3 步：  model/nop-auth-delta.orm.xml  -- /nop/templates/orm-delta -->  _app.orm.xml + _NopAuthUserEx.java
第 4 步：  app.orm.xml             -- /nop/templates/meta-delta -->       NopAuthUserEx.xmeta
```

生成的继承链：`NopAuthUser`（平台）→ `_NopAuthUserEx`（生成，`ext:baseClass` 决定继承）→ `NopAuthUserEx`（手写，`@BizObjName("NopAuthUser")`）。`/nop/templates/orm-delta` 与 `/nop/templates/meta-delta` 是 Delta 模式专用的两个模板（其 `@init.xrun` 均为 `<gen:DefineLoopForOrm .../>`）。

### 5.5 value resolver：加载期求值

XDSL delta 加载阶段支持 `@` 前缀 value resolver，在**加载期一次性求值**（非 reactive）。注册表在 `ValueResolverCompilerRegistry`（RESOLVE-001），`BIND_EXPR_SYMBOL = '@'`（`IValueResolverCompiler.java:13`），前缀检测在 `ValueResolverCompiler.java:110-127`。6 个内置 resolver（`ValueResolverCompilerRegistry.java:25-32`）：

| 前缀 | resolver | 用途 |
|---|---|---|
| `@cfg:` | `ConfigValueResolver` | 注入配置值 |
| `@i18n:` | `I18nTextResolver` | 国际化文本 |
| `@var:` | `ScopeVarResolver` | 作用域变量 |
| `@uuid:` | `UuidResolver` | 生成 UUID |
| `@load:` | `LoadTextResolver` | 加载资源文本 |
| `@empty:` | `EmptyTextResolver` | 空值 |

JSON/YAML 模型同样可复用 Delta 机制（EXT-005）：`GlobalFunctions.loadDeltaJson`（`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java:432-436`）委托 `JsonTool.loadDeltaBeanFromResource`（`nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonTool.java:191-203`），后者装配 `ValueResolverCompilerRegistry.DEFAULT` + `EvalExprProvider` + `getDeltaExtendsGenerator()` + `getFeaturePredicateEvaluator()`。这使得 `scheduler.yaml` 等 YAML 文件中的 `@cfg:` 也能在加载期求值。

> **注意双实现**（A2 分析）：IoC 容器中 `*.beans.xml` 与 `@InjectValue` 的 `@cfg:`/`@r-cfg:`（reactive）是**另一套独立实现**（`ConfigExpressionProcessor`，RESOLVE-003），写法相同但实现独立于 DSL 加载期那套。

## 6. 联网对标与差异化定位

五个方向的「该工具做什么 / nop 做什么 / 差异点」对照。

### 6.1 JHipster（全栈脚手架生成器）

| | JHipster | nop-entropy |
|---|---|---|
| **做什么** | 开发平台，从 JDL（JHipster Domain Language）或交互式问答**一次性生成**完整的 Spring Boot + Angular/React/Vue 全栈应用与微服务架构；支持 Micronaut/Quarkus/.NET 后端、Docker/K8s 部署、CI/CD | 从 ORM 模型 `model/*.orm.xml` 经 codegen **持续生成**多模块骨架（dao/service/web/app/meta）+ 元数据 + 页面；Delta 叠加实现产品定制 |
| **生成时机** | **一次性**：`jhipster` 命令生成项目后，代码归开发者所有，后续手写维护；模型变更需手动重跑或手改 | **持续再生**：每次 `mvn install` 自动触发 codegen，`_` 前缀产物全量覆盖 |
| **可定制性** | 生成后代码即"冻结"，定制靠手改生成代码或用 blueprint（生成器扩展） | **Delta 叠加**：`_vfs/_delta/` 差量覆盖，不动生成物，平台升级时 delta 可重新叠加 |
| **差异点** | (a) JHipster 是「**一次性脚手架**」，nop 是「**持续再生 + Delta**」；(b) JHipster 的 JDL 只在初始化时用，nop 的 ORM 模型是贯穿全生命周期的唯一源；(c) JHipster 定制靠改生成代码（升级困难），nop 定制靠 Delta 差量（升级友好，结合律保证可重新叠加） |

> 来源：JHipster 官网 — https://www.jhipster.tech/（访问 2026-07-24）；JHipster GitHub — https://github.com/jhipster/generator-jhipster（访问 2026-07-24）

### 6.2 OpenAPI Generator（API spec → client/server 生成器）

| | OpenAPI Generator | nop-entropy |
|---|---|---|
| **做什么** | 从 OpenAPI 2.0/3.x 文档生成 50+ 客户端 SDK、40+ 服务端 stub、数据库 schema（MySQL）、文档（HTML/Cwiki）；模板可替换（Mustache） | 从 ORM 模型生成实体类、元数据（xmeta）、页面、beans、xbiz、i18n、action-auth 等多维度产物 |
| **生成范围** | **单维度（API）**：输入是 API spec，输出是 API 层代码（client/server stub）。部分 generator 支持 IoC 模式避免覆盖 domain 层 | **全链路**：输入是数据模型（ORM），输出覆盖 dao→service→web→app 全栈，且产物间有依赖链（xmeta 依赖 orm，page 依赖 xmeta） |
| **差异点** | (a) OpenAPI Generator 是「**spec → stub 单维度**」，nop 是「**ORM → 全链路多维度**」；(b) OpenAPI Generator 模板可替换但生成逻辑固定，nop 的生成模板本身是 Xpl/xlib（编译期元编程，可 `x:gen-extends`）；(c) OpenAPI Generator 解决「API 契约→代码」，nop 解决「数据模型→完整可运行应用」 |

> 来源：OpenAPI Generator 官网 — https://openapi-generator.tech/（访问 2026-07-24）；Generators 列表 — https://openapi-generator.tech/docs/generators/（访问 2026-07-24）

### 6.3 Spring Initializr（项目初始化器）

| | Spring Initializr | nop-entropy |
|---|---|---|
| **做什么** | 官方工具，通过 Web UI / CLI 选择依赖（Spring Boot starters）后生成**最小可运行项目骨架**（pom.xml + 主类 + 配置文件） | `nop-cli gen` 首次生成多模块骨架，后续 `mvn install` 持续再生 |
| **生成时机** | **一次性初始化**：生成后几乎不含业务代码，后续全部手写 | **首次生成 + 持续再生**：骨架含完整 CRUD 骨架，且每次 build 再生 |
| **差异点** | (a) Spring Initializr 是「**空骨架初始化**」，nop 是「**含业务逻辑骨架 + 持续再生**」；(b) Spring Initializr 生成后与模型无关，nop 的 ORM 模型始终驱动生成；(c) Spring Initializr 不解决"定制/升级"问题，nop 的 Delta 机制专为产品线定制与升级设计 |

> 来源：Spring Boot Getting Started — https://spring.io/guides/gs/spring-boot（访问 2026-07-24）；Spring Initializr — https://start.spring.io/（访问 2026-07-24）

### 6.4 Annotation Processor / 编译期 codegen（AutoValue / Immutables / RecordBuilder）

| | Annotation Processor (AutoValue 等) | nop-entropy |
|---|---|---|
| **做什么** | 在 `javac` 编译期读取注解（如 `@AutoValue`），生成增强类（immutable value class、builder）。AutoValue「在 javac 内作为标准 annotation processor 运行，读取抽象类并推断实现类」（Google AutoValue 用户指南） | 在 Maven `generate-sources`/`compile`/`generate-test-resources` phase 由 `CodeGenTask` 执行 `.xgen` 模板，生成实体类、元数据、页面等 |
| **生成范围** | **局部增强**：针对单个类/接口，生成辅助类（builder、equals/hashCode、immutable wrapper）。不生成跨模块结构 | **全链路骨架**：从 ORM 模型生成整个多模块项目结构 + 各层产物 |
| **差异点** | (a) APT 是「**类内局部增强**」（同编译单元），nop 是「**模型驱动全链路生成**」（跨模块）；(b) APT 输入是 Java 注解，nop 输入是独立的结构化模型（ORM XML）；(c) APT 生成物通常对开发者透明（IDE 自动识别），nop 的生成物是显式文件（`_` 前缀，可审计、可 Delta 定制） |

> 来源：Google AutoValue 用户指南 — https://github.com/google/auto/blob/master/value/userguide/index.md（访问 2026-07-24）；AutoValue（chromium.googlesource.com）— https://chromium.googlesource.com/external/github.com/google/auto/+/auto-value-1.0/value/README.md（访问 2026-07-24）

### 6.5 JetBrains MPS / Meta-Programming System（projectional 语言工作台）

| | JetBrains MPS | nop-entropy |
|---|---|---|
| **做什么** | 语言工作台，用 **projectional editor**（直接编辑 AST 而非文本，绕过解析器限制）设计 DSL；支持语言结构、语法、类型系统、编辑器、生成器 | XDef 元模型驱动 + 文本/XML DSL（解析式，非 projectional）；XDSL 可叠加，xpl/xlib 编译期元编程 |
| **生成机制** | MPS 的 generator 把 DSL AST 转换为基础语言（如 Java），支持多级生成 | nop 的 codegen 模板（`.xgen`）从 ORM 模型生成 Java/XML/JSON；xlib 标签在编译期展开为 DSL 节点（**总是规约回基础 DSL 形式**，A1 分析 §3.2） |
| **差异点** | (a) MPS 聚焦「**语言组合 + projectional 编辑**」，nop 聚焦「**模型差量组合 + Delta 叠加**」；(b) MPS 扩展难以规约回基础形式，nop 的 xpl/xlib 元编程总是规约回基础 DSL（保 Delta 可叠加性）；(c) MPS 依赖专用编辑器（学习曲线高），nop 用通用文本/XML 编辑器（学习曲线低，AI 友好） |

> 来源：JetBrains MPS 官网 — https://www.jetbrains.com/mps/（访问 2026-07-24）；MPS Concepts — https://www.jetbrains.com/mps/concepts/（访问 2026-07-24）；Wikipedia: JetBrains MPS — https://en.wikipedia.org/wiki/JetBrains_MPS（访问 2026-07-24）；MPS GitHub — https://github.com/JetBrains/MPS（访问 2026-07-24）

### 6.6 差异化定位总结

nop-entropy 的代码生成独特性可浓缩为一句话：**「生成即一等公民 + 持续再生（非一次性）+ Delta 叠加可升级 + 生成层与保留层文件级分离」**。这四点组合使其同时具备：

- 比 JHipster / Spring Initializr 更强的**持续性与可演化性**（每次 build 再生，Delta 可重新叠加到新基线）
- 比 OpenAPI Generator 更广的**生成范围**（数据模型→全链路，非单维度 spec→stub）
- 比 Annotation Processor 更高的**生成层级**（模型驱动跨模块，非类内局部增强）
- 比 MPS 更轻的**元模型驱动**（文本 DSL + XDef，无需 projectional 编辑器，且生成规约回基础 DSL 保 Delta 可叠加）

核心支撑是可逆计算的 `Y = F(X) ⊕ Δ`（A1 分析）：codegen 即 `F(X)`，Delta 即 `⊕ Δ`，结合律（A1 公理 C）保证「平台升级（新 `F(X)`）+ 客户定制（`Δ`）」可独立演进后重新叠加。

## 7. Open Questions

- [ ] **`precompile` 与 `precompile2` 的实际使用分布**：`CodeGenTask.java:191-197` 显示 `precompile`（精确）禁用 `INCLUDE_CURRENT_PROJECT_RESOURCES`，`precompile*`（前缀）启用。当前仓库各模块的 `precompile/` 与 `precompile2/` 目录使用分布未做全量统计——是否所有 `*-meta`/`*-web` 都用 `precompile`，`precompile2` 的典型场景是什么，可由后续工程化审计补充。
- [ ] **`aop` execution 与 codegen 的关系**：`pom.xml:365-378` 的 `aop` execution 绑定 `compile` phase、`classpathScope=test`，走 `genAopProxy`（A2 分析已覆盖 AOP 是源码生成式）。本分析仅点到为止，AOP 生成管线的完整剖析归属 A2。
- [ ] **Delta 定制对编译期 xlib 的影响边界**：案例 B 显示 Delta 可覆盖 `meta-gen.xlib`（编译期标签库）。这种"定制编译期元编程"的能力边界、与生成物一致性的关系，值得 A4（服务层）或 A7（capstone）深入评估。
- [ ] **生成链路的增量构建**：当前 `mvn install` 每次全量再生所有 `_` 前缀产物。是否支持基于模型哈希的增量生成（model 未变则跳过 codegen）以加速大型项目构建，是工程化优化的潜在方向（A6 工程化主题）。

## Conclusion

- 本分析建立了从 `model/*.orm.xml` 到生成物到 Delta 定制的完整链路映射：model-first 起点（§1）→ 分层生成链 codegen/meta/web（§2）→ Maven phase 绑定与触发时序（§3）→ `_` 前缀生成物约束与生成层/保留层分离（§4）→ Delta 定制机制 extends/super/value-resolver（§5），并用 15+ 个 source-anchor 源码交叉核对（全部 PASS）验证文档论断与实际代码一致。
- 联网对标（5 方向：JHipster / OpenAPI Generator / Spring Initializr / Annotation Processor / MPS）明确了 nop 的差异化定位：**生成即一等公民 + 持续再生 + Delta 叠加可升级 + 文件级分离**。
- 3 个真实模块 Delta 案例（`nop-quarkus-demo` / `nop-delta-demo` / `nop-job-worker`）佐证了 Delta 机制在 ORM、xmeta、page、beans、xbiz、xlib 多层次的定制能力，且均不修改平台源码。
- 后续工作：A4（GraphQL/服务层）将展开「生成的 `_*.xbiz`/`_*.xmeta` 如何被服务层消费」；A5（模块矩阵）将基于生成链路组装模块全景；A7（capstone）综合评估是否将本分析迁移到 `docs-for-ai/`。

## References

### 平台内部（file 锚点）

- 使用规范：`docs-for-ai/02-core-guides/model-first-development.md`、`docs-for-ai/02-core-guides/delta-customization.md`
- 实现锚点：`docs-for-ai/04-reference/source-anchors.md`（GEN-001~009、EXT-002~005、RESOLVE-001~002、VFS-001~003）
- 代码：
  - `pom.xml:323-412`（`exec-maven-plugin` 四 execution 绑定）
  - `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/task/CodeGenTask.java:139,164-197`（main 入口、tplRoot 目录扫描）
  - `nop-kernel/nop-core/src/main/java/io/nop/core/resource/tpl/TemplateFileGenerator.java:185-520`（目录遍历、`_` 前缀覆盖规则）
  - `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/XCodeGenerator.java:226-236`（`@init.xrun` 加载）
  - `nop-kernel/nop-core/src/main/java/io/nop/core/CoreConstants.java:146-155`（`_` 前缀常量）
  - `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/`（`orm`/`orm-entity`/`orm-model`/`meta`/`orm-web`/`i18n`/`orm-delta`/`meta-delta`）
  - `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/meta/.../model/_module-meta.json.xgen`、`module-meta.json.xgen`（GEN-006）
  - `nop-job/nop-job-codegen/postcompile/gen-orm.xgen`、`nop-job/nop-job-meta/precompile/gen-meta.xgen`、`nop-job/nop-job-web/precompile/gen-page.xgen`、`nop-wf/nop-wf-meta/postcompile/gen-i18n.xgen`（GEN-002~005）
  - `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslExtender.java:198-311`（EXT-002）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefOverride.java:19-50`（8 override 模式）
  - `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStore.java:35-42,120-149,251-294`（EXT-003/VFS-001）
  - `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/bind/ValueResolverCompilerRegistry.java:25-32`（RESOLVE-001）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java:432-436`（EXT-005/RESOLVE-002）、`nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonTool.java:191-203`
- Delta 真实案例：`nop-demo/nop-quarkus-demo/src/main/resources/_vfs/_delta/default/nop/sys/`、`nop-demo/nop-delta-demo/src/main/resources/_vfs/_delta/default/`、`nop-job/nop-job-worker/src/main/resources/_vfs/_delta/default/nop/job/beans/app-engine.beans.xml`
- 前序分析（词汇表基线）：`ai-dev/analysis/2026-07/2026-07-24-nop-theory-foundation.md`（A1）、`ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md`（A2）
- 可复用既有分析：`ai-dev/analysis/2026-06-15-maven-local-repo-customization-vs-nop-delta.md`（Delta vs Maven Chained LRM）、`ai-dev/analysis/2026-06-15-nubase-vs-nop-comparison.md`（codegen 全链路 walkthrough）、`ai-dev/analysis/2026-07/2026-07-15-nop-orm-model-management-and-bi-metadata-analysis.md`（Delta 作为模型版本管理）
- 计划：`ai-dev/plans/nop-deep-analysis/2026-07-24-1907-3-a3-model-driven-codegen.md`
- 路线图：`ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md`（Work Item A3）
- 交叉核对记录：`ai-dev/logs/2026/07-24.md`

### 外部（联网调研，访问日期 2026-07-24）

- JHipster 官网: https://www.jhipster.tech/
- JHipster GitHub: https://github.com/jhipster/generator-jhipster
- OpenAPI Generator 官网: https://openapi-generator.tech/
- OpenAPI Generator 列表: https://openapi-generator.tech/docs/generators/
- Spring Boot Getting Started: https://spring.io/guides/gs/spring-boot
- Spring Initializr: https://start.spring.io/
- Google AutoValue 用户指南: https://github.com/google/auto/blob/master/value/userguide/index.md
- AutoValue (chromium): https://chromium.googlesource.com/external/github.com/google/auto/+/auto-value-1.0/value/README.md
- JetBrains MPS 官网: https://www.jetbrains.com/mps/
- JetBrains MPS Concepts: https://www.jetbrains.com/mps/concepts/
- Wikipedia: JetBrains MPS: https://en.wikipedia.org/wiki/JetBrains_MPS
- JetBrains MPS GitHub: https://github.com/JetBrains/MPS
