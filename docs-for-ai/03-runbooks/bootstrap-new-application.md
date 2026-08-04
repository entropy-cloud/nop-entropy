# 冷启动：从零创建外部 Nop 应用项目

> **用途：** 当目标不是改 `nop-entropy` 内部模块，而是**从零创建一个独立的 Nop 业务应用**（类似 `nop-app-erp` 这样的工程）时，本 runbook 给出从环境准备到应用启动的端到端步骤。
>
> 已有项目内做增量开发（加字段、改 BizModel、定制页面）不要读本页，直接看 `00-required-reading-backend.md` / `00-required-reading-frontend.md`。

本页所有结构、命令、配置都从真实项目 `nop-app-erp`（18 个业务域、150+ 模块）抽取，不是示意伪代码。

---

## 前置条件

| 项 | 要求 |
|---|---|
| JDK | 17+（推荐 21，与 CI 一致） |
| Maven | 项目自带 `./mvnw`（wrapper 4.0.0-rc-5），无需全局 Maven |
| `nop-entropy` | 必须**先在本地 `mvn install`**，因为外部应用 parent 指向它 |
| 数据库 | 开发期默认 H2（零配置）；生产用 MySQL/Oracle/PostgreSQL |

### 第 0 步：构建 nop-entropy 到本地仓库

```bash
cd /path/to/nop-entropy
./mvnw clean install -DskipTests -T 1C
```

外部应用的 `<parent>` 是 `io.github.entropy-cloud:nop-entropy`，本地仓库必须有对应版本（当前 `2.0.0-SNAPSHOT`），否则外部应用 pom 无法解析。

---

## nop-cli：获取与三种用法

`nop-cli` 是一个 uber-jar，来自 `nop-entropy/nop-runner/nop-cli/`，提供三个子命令。**外部应用只有在「首次生成项目骨架」和「ORM 模型 orm.xml↔xlsx 互转」时才需要它**；日常迭代不需要。

### 获取 nop-cli

```bash
cd /path/to/nop-entropy
./mvnw install -pl nop-runner/nop-cli -am -DskipTests
# 产出：nop-runner/nop-cli/target/nop-cli-2.0.0-BETA.1.jar（uber-jar，自带所有依赖）
```

或者直接用仓库根目录的封装脚本 `scripts/nop-cli.cmd`：

```bash
# scripts/nop-cli.cmd 内部等价于：
java -jar nop-runner/nop-cli/target/nop-cli-2.0.0-BETA.1.jar %*
```

后续示例统一写作 `java -jar nop-cli.jar ...`，请把 `nop-cli.jar` 替换为上面的真实路径。

### 三种用法

| 子命令 | 用途 | 触发频率 |
|--------|------|---------|
| `gen` | 从 ORM 模型生成项目骨架（多模块结构） | **一次性**，项目初始化时 |
| `gen` | 从 ORM 模型增量生成 dao/entity/xbiz | 也可日常用，但日常迭代推荐走 Maven（见下文「两种 codegen 路径」） |
| `convert` | `orm.xml` ↔ `orm.xlsx` 双向转换 | 按需（要 Excel 人工编辑时） |

#### `gen` 命令语法（源码：`nop-runner/nop-cli-core/.../CliGenCommand.java`）

```bash
java -jar nop-cli.jar gen -t=/nop/templates/orm model/app-xxx.orm.xml -o=.
# 可选：
#   -F / --force         强制覆盖输出目录已有文件
#   -i / --input 'JSON'  注入生成参数
#   -P name=value        动态参数（可多次）
#   -t 可多次传入多个模板
```

#### `convert` 命令语法（源码：`CliConvertCommand.java`）

```bash
# orm.xml（代码生成源头）→ xlsx（人工编辑视图）
java -jar nop-cli.jar convert model/app-xxx.orm.xml -o=model/app-xxx.orm.xlsx

# 编辑后再转回 orm.xml（成为新的生成源头）
java -jar nop-cli.jar convert model/app-xxx.orm.xlsx -o=model/app-xxx.orm.xml
```

> **关键约定**：**`orm.xml` 是代码生成的唯一源头**。`xlsx` 仅用于人工编辑维护（列宽、批注、下拉更友好），编辑完必须 `convert` 回 `orm.xml`，再触发 `gen` 或 Maven。不要把 `xlsx` 直接作为 `gen` 输入（虽然技术上可行，但会让两份模型并存导致真相分裂）。

---

## 两种 codegen 路径（重要）

外部应用存在两条并行的代码生成路径，**不要混淆**：

### 路径 A：`nop-cli gen`（初始骨架，一次性）

用于**项目第一次创建**时生成完整的 Maven 多模块结构（parent pom、codegen/api/dao/meta/service/web/app 七个子模块、`postcompile/*.xgen`、`ErpXxxCodeGen.java` 等）。

```bash
java -jar nop-cli.jar gen -t=/nop/templates/orm model/app-xxx.orm.xml -o=.
```

跑完后会得到一个完整的 `module-xxx/` 目录骨架，把它放进你的应用工程根 pom 的 `<modules>` 即可。

### 路径 B：Maven `postcompile/gen-orm.xgen`（日常迭代，每次 `mvn install`）

**这是日常开发的主路径，不需要 nop-cli。** 模型变更后只需：

```bash
./mvnw clean install -T 1C   # 或单模块 ./mvnw install -pl module-xxx -am
```

Maven 的 `exec-maven-plugin`（parent pom 已配置）会自动执行每个 `*-codegen` 模块下的 `postcompile/gen-orm.xgen`，从 `model/*.orm.xml` 重新生成 dao/entity/xbiz等到各子模块。

**典型 `postcompile/gen-orm.xgen`** 内容（来自 `nop-app-erp/module-master-data/erp-md-codegen/postcompile/gen-orm.xgen`）：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<c:script>
// 从源模型生成多模块骨架（dao/api/meta/service/web 各层）
codeGenerator.withTargetDir("../").renderModel(
    '../../model/app-erp-master-data.orm.xml','/nop/templates/orm', '/',$scope);
// 从生成出的 app.orm.xml 再生成 Java entity 类
codeGenerator.withTargetDir("../erp-md-dao/src/main/java").renderModel(
    '../../erp-md-dao/src/main/resources/_vfs/erp/md/orm/app.orm.xml',
    '/nop/templates/orm-entity','/',$scope);
</c:script>
```

**调试单步生成**：每个 `*-codegen` 模块都有一个 `src/test/java/.../XxxCodeGen.java`，IDE 里直接 `main()` 运行即可触发一次生成（不经过 Maven），方便调试模板：

```java
public class ErpMdCodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(
            CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
            CoreConstants.INITIALIZER_PRIORITY_ANALYZE);
        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(ErpMdCodeGen.class);
            XCodeGenerator.runPostcompile(projectDir, "/", false);
        } finally {
            CoreInitialization.destroy();
        }
    }
}
```

### 两条路径的边界

| 场景 | 用哪条 |
|------|--------|
| 第一次创建项目骨架 | 路径 A（`nop-cli gen`） |
| 已有项目里改字段、加实体、改关系 | 路径 B（`./mvnw install`） |
| 想在 IDE 里单步调试模板 | `XxxCodeGen.main()` |
| 要在 Excel 里改模型 | `nop-cli convert` 转 xlsx → 编辑 → convert 回 orm.xml → 路径 B |

---

## 标准项目结构（基于 nop-app-erp）

一个真实的 Nop 外部应用工程（多业务域）目录长这样：

```
app-erp/                                    ← 工程根
├── pom.xml                                 ← parent = nop-entropy，列 modules
├── model/                                  ← （可选）聚合模型，单域项目不放这
├── module-master-data/                     ← 一个业务域 = 一个目录
│   ├── pom.xml                             ← parent = nop-entropy，列本域子模块
│   ├── model/
│   │   └── app-erp-master-data.orm.xml     ← 【源模型】代码生成的源头
│   ├── erp-md-codegen/                     ← 代码生成模块
│   │   ├── pom.xml
│   │   ├── postcompile/gen-orm.xgen        ← Maven 自动执行的生成脚本
│   │   └── src/test/java/.../ErpMdCodeGen.java  ← IDE 调试入口
│   ├── erp-md-api/                         ← 对外/跨模块契约（I*Biz 接口、DTO、ErrorCode）
│   ├── erp-md-dao/                         ← entity / dao / app.orm.xml
│   │   └── src/main/resources/_vfs/erp/md/orm/app.orm.xml  ← 生成物，x:extends _app.orm.xml
│   ├── erp-md-meta/                        ← XMeta、i18n 生成
│   ├── erp-md-service/                     ← BizModel 实现层
│   │   └── src/main/resources/_vfs/erp/md/beans/app-service.beans.xml  ← 自定义 bean 注册
│   ├── erp-md-web/                         ← view.xml / page.yaml / action-auth 生成
│   └── erp-md-app/                         ← 域级打包（可选）
├── module-sales/
├── module-inventory/
├── ... （更多业务域）
├── module-common-service/                  ← （可选）跨域共享 Processor/工具
├── module-common-test/                     ← （可选）跨域测试基类
└── app-erp-all/                            ← 【顶层聚合】单体部署入口
    ├── pom.xml                             ← 依赖所有 *-web 模块 + 系统模块
    └── src/main/
        ├── java/app/erp/all/ErpApplication.java   ← @QuarkusMain 启动类
        └── resources/
            ├── application.yaml            ← 主配置（数据源、端口、auth 等）
            ├── bootstrap.yaml              ← 启动前配置（如 nop.codegen.trace.enabled）
            └── _vfs/
                ├── app/all/_module         ← 模块声明（moduleId 等）
                ├── app/all/beans/app-service.beans.xml   ← 应用级 bean 注册
                └── nop/main/auth/app.action-auth.xml      ← 菜单聚合文件
```

### 命名约定（关键）

| 概念 | 格式 | 例子 |
|------|------|------|
| Maven artifactId | `{appPrefix}-{domain}-{layer}` | `app-erp-master-data-codegen` |
| 短前缀（子模块目录） | `{shortPrefix}-{layer}` | `erp-md-codegen`（`erp-md` = master data） |
| Java 包 | `{group}.{app}.{domain}.{layer}` | `app.erp.md.dao.entity` |
| VFS 路径 | `/{app}/{shortDomain}/...` | `/erp/md/orm/app.orm.xml` |
| ORM 源模型文件 | `app-{app}-{domain}.orm.xml` | `app-erp-master-data.orm.xml` |
| tableName | `{shortDomain}_{entity}` | `erp_md_partner` |

> 单域小项目可省略 `module-xxx/` 一层，直接在根用 `app-xxx-codegen/api/dao/...` 七个模块。

---

## 最小可运行配置

### 1. 根 pom（工程根目录的 `pom.xml`）

```xml
<project>
    <parent>
        <artifactId>nop-entropy</artifactId>
        <groupId>io.github.entropy-cloud</groupId>
        <version>2.0.0-SNAPSHOT</version>
    </parent>
    <groupId>io.nop.app</groupId>
    <artifactId>app-erp</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <modules>
        <module>module-master-data</module>
        <!-- 更多业务域 -->
        <module>app-erp-all</module>   <!-- 顶层聚合，必须放在最后 -->
    </modules>
</project>
```

> **关键**：parent 必须是 `nop-entropy`，这样能继承所有 plugin 管理（`exec-maven-plugin` 绑定 codegen、`quarkus-maven-plugin` 等）。

### 2. 应用入口（`app-erp-all/src/main/java/.../ErpApplication.java`）

```java
package app.erp.all;

import io.nop.boot.NopApplication;
import io.nop.core.initialize.CoreInitialization;
import io.nop.quarkus.core.QuarkusIntegration;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.event.Observes;

@QuarkusMain
public class ErpApplication {
    static String[] globalArgs;

    public void start(@Observes StartupEvent event) {
        QuarkusIntegration.start();          // 先集成 Quarkus
        new NopApplication().run(globalArgs); // 再启动 Nop 容器
    }

    public void stop(@Observes ShutdownEvent event) {
        CoreInitialization.destroy();
    }

    public static void main(String... args) {
        globalArgs = args;
        Quarkus.run(args);
    }
}
```

### 3. 主配置（`app-{name}-all/src/main/resources/application.yaml`）

完整最小可运行示例见 `../05-examples/application.yaml`，核心字段：

```yaml
nop:
  debug: true                        # 开发期开启，生成 _dump 调试输出
  auth:
    jwt:
      enc-key: ${JWT_ENC_KEY:随机32位hex}   # 生产必须用环境变量
    login:
      allow-create-default-user: false     # 生产关闭；dev profile 开启
    site-map:
      static-config-path: /nop/main/auth/app.action-auth.xml  # 菜单唯一聚合文件
  orm:
    init-database-schema: true       # 启动时按模型自动建表（开发期 H2）
  datasource:
    driver-class-name: org.h2.Driver
    jdbc-url: jdbc:h2:./db/erp       # 文件型 H2，重启不丢
    username: sa
    password:

quarkus:
  http:
    host: 0.0.0.0
    port: 8011
    cors: true

"%dev":                              # dev profile 覆盖
   nop:
     auth:
       login:
         allow-create-default-user: true
```

### 4. app-all 的 pom 依赖（聚合全部模块）

`app-{name}-all` 模块的 `pom.xml` 中 `<dependencies>` 必须：

1. 依赖 `io.github.entropy-cloud:nop-quarkus-web-orm-starter`（Web + ORM 基础）。
2. 依赖每个业务域的 `*-web` 模块（会传递依赖 service/dao/api）。
3. 依赖需要的系统模块：`nop-auth-web`、`nop-auth-service`、`nop-sys-web`、`nop-sys-service`。
4. 按需依赖：`nop-wf-service`/`nop-wf-web`（工作流）、`nop-report-service`/`nop-report-web`（报表）、`nop-job-local`（定时任务本地模式）、`nop-batch-dsl`（批处理）、`nop-web-site`（前端站点）。
5. 加数据库驱动：`quarkus-jdbc-h2`（开发）、`quarkus-jdbc-mysql`（生产）等。
6. 测试：`nop-autotest-junit`（scope=test）。

完整范例参考外部项目 `nop-app-erp` 的 `app-erp-all` 模块 `pom.xml`。

---

## 启动与验证

### 启动

```bash
cd app-erp-all
../mvnw quarkus:dev    # 开发模式（热部署，但 Nop 的 _vfs 增量推荐用下面的方式）

# 或打包后运行
../mvnw clean install -Dquarkus.package.type=uber-jar
java -jar app-erp-all/target/app-erp-all-1.0-SNAPSHOT-runner.jar
```

### 验证清单

启动成功后逐项确认：

| 验证项 | 命令/操作 | 预期 |
|--------|----------|------|
| HTTP 起来了 | `curl http://localhost:8011/r/DevTool__ping`（如果启用了 devtool）或访问 `http://localhost:8011/` | 200 / 前端首页 |
| 表已建 | 看 H2 文件 `./db/erp.*` 已生成；或接 MySQL 用客户端看表 | tableName 与 orm.xml 一致 |
| 菜单聚合了 | 看 `_dump/{appName}/nop/main/site/zh-CN-menu.yaml` | 包含你的业务菜单 |
| GraphQL 暴露了 | `POST http://localhost:8011/graphql` 带 `{ NopAuthUser__findList{id} }` | 返回数据（需登录） |
| 默认用户 | dev profile 下自动创建 default user；生产需自行 SQL/初始化 | 能登录 |

> 如果「改了菜单/模型没生效」：先看 `02-core-guides/debugging-and-diagnostics.md` 的「_dump 调试」章节，再调 `DevTool__clearComponentCache`。

---

## 完整端到端流程（一张图）

```
┌─────────────────────────────────────────────────────────────┐
│ 0. cd nop-entropy && ./mvnw install  （构建平台到本地仓库）  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. 写 model/app-xxx.orm.xml （见 05-examples/orm-model.orm.xml）│
│    需要时：nop-cli convert ... -o=....orm.xlsx 编辑再转回    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. nop-cli gen -t=/nop/templates/orm model/app-xxx.orm.xml  │  ← 一次性
│    生成 module-xxx/ 七个子模块骨架 + postcompile/gen-orm.xgen│
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. 创建 app-xxx-all/ 聚合模块（pom + ErpApplication +       │
│    application.yaml + _vfs/app/all/_module）                │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. ./mvnw clean install -T 1C                               │
│    → Maven 自动跑 postcompile/gen-orm.xgen 生成 entity/dao  │
│    → 自动跑 *-meta/gen-meta.xgen 生成 xmeta/i18n            │
│    → 自动跑 *-web 生成 view/action-auth                      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. cd app-xxx-all && ../mvnw quarkus:dev                    │
│    访问 http://localhost:8011/                              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
   后续每次改 model/*.orm.xml → 回到第 4 步（不再用 nop-cli）
   自定义 BizModel → 在 *-service/_vfs/.../beans/app-service.beans.xml 注册
                    （见 05-examples/beans-registration.beans.xml）
```

---

## 常见卡点

1. **parent 解析失败**：忘了 `mvn install` nop-entropy；或版本对不上（外部应用 parent version 必须等于本地仓库里的 nop-entropy version）。
2. **生成没跑**：检查 `*-codegen/pom.xml` 是否声明了 `exec-maven-plugin`（parent 已管理，但需声明使用）；检查 `postcompile/gen-orm.xgen` 路径是否指向真实 `model/*.orm.xml`。
3. **bean not found**：自定义 BizModel 没在 `app-service.beans.xml` 注册（见 `05-examples/beans-registration.beans.xml`）。生成的 CRUD bean 自动注册在 `_service.beans.xml`（下划线开头，勿手改）。
4. **菜单不出现**：`app.action-auth.xml` 没引用你模块的 `_*.action-auth.xml`；或 `nop.auth.site-map.static-config-path` 没指向聚合文件。详见 `02-core-guides/auth-and-permissions.md`。
5. **端口被占**：改 `quarkus.http.port`。
6. **H2 文件锁**：多实例启动会冲突，改 `jdbc:h2:mem:xxx` 或切 MySQL。

---

## 相关文档

- `../02-core-guides/external-app-development.md` — 外部应用模块开发总览
- `../02-core-guides/model-first-development.md` — 模型优先开发流程（含 codegen 链路细节）
- `../02-core-guides/orm-model-design.md` — ORM 模型设计规范
- `../05-examples/orm-model.orm.xml` — 完整最小 ORM 源模型示例
- `../05-examples/beans-registration.beans.xml` — 自定义 BizModel bean 注册示例
- `../05-examples/application.yaml` — 最小可运行应用配置示例
- `create-new-entity.md` — 已有项目内新建实体（增量场景）
- `change-model-and-regenerate.md` — 模型变更后重新生成
